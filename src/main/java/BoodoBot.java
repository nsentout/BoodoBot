import net.dv8tion.jda.client.entities.Group;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.requests.RequestFuture;

import javax.security.auth.login.LoginException;

import org.apache.logging.log4j.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


public class BoodoBot extends ListenerAdapter
{
	/********************************************************************************************************************************/
	
	private final static String BOT_TOKEN_FILE = "bot_token.txt";
	
	final static Logger logger = LogManager.getLogger(BoodoBot.class);
	
	/********************************************************************************************************************************/
    
	public static void main(String[] args)
	{
		try {
			String botToken = new String(Files.readAllBytes(Paths.get(BOT_TOKEN_FILE))); 
			
			JDA jda = new JDABuilder(botToken)
					.addEventListener(new BoodoBot()) 
					.build();
			jda.awaitReady();
            
			System.out.println("Finished Building JDA!");
		}
		catch (LoginException | InterruptedException e) {
			e.printStackTrace();
		}
		catch (IOException e) {
			logger.fatal("An error occured when reading the file containing the bot token");
		}
	}

	/********************************************************************************************************************************/
    
    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
		User author = event.getAuthor();	
		Message message = event.getMessage();
		TextChannel channel = event.getTextChannel();    //This is the MessageChannel that the message was sent to. This could be a TextChannel, PrivateChannel, or Group!
		Guild server = event.getGuild();
			
		String msg = message.getContentDisplay(); 
			
		if (event.isFromType(ChannelType.TEXT))
		{				
			TextChannel textChannel = event.getTextChannel();
			Member member = event.getMember(); 
				
			String name;
			if (message.isWebhookMessage()) {
			    name = author.getName();                //If this is a Webhook message, then there is no Member associated
			}                                           // with the User, thus we default to the author for name.
			else {
			    name = member.getEffectiveName();       //This will either use the Member's nickname if they have one,
			}                                           // otherwise it will default to their username. (User#getName())
			
			System.out.printf("(%s)[%s]<%s>: %s\n", server.getName(), textChannel.getName(), name, msg);
		}
		else if (event.isFromType(ChannelType.GROUP))   //If this message was sent to a Group. This is CLIENT only!
		{
		    //The message was sent in a Group. It should be noted that Groups are CLIENT only.
		    Group group = event.getGroup();
		    String groupName = group.getName() != null ? group.getName() : "";  //A group name can be null due to it being unnamed.
		
		    System.out.printf("[GRP: %s]<%s>: %s\n", groupName, author.getName(), msg);
		}
		
		if (msg.equals("!ping"))
		{
		    channel.sendMessage("pong!").queue();
		}
		
		// Roll a 100-sided dice
		else if (msg.equals("!roll"))
		{			
		    Random rand = new Random();
		    int roll = rand.nextInt(100) + 1;
		    channel.sendMessage(author.getName() + "a lancé un dé 100 ... ").queue();
		    channel.sendMessage("Il a fait " + roll + " !").queueAfter(2000, TimeUnit.MILLISECONDS);
		}
		
		// Display stats for this channel
		else if (msg.equals("!stats"))
		{
			Stats stats = getChannelStats(event.getTextChannel());
			stats.sendStatsAsMessage(channel);
		}
		
		// Display stats for every channel on the server
		else if (msg.equals("!statsall"))
		{
			Stats stats = new Stats();
			for (TextChannel c : server.getTextChannels()) {
				Stats cStats = getChannelStats(c);
				stats.nbTotalMessages += cStats.nbTotalMessages;
				stats.addEqual(cStats.nbMessages);
			}
			
			stats.sendStatsAsMessage(channel);
		}
		
		// Display stats for a chosen channel
		else if (msg.contains("!stats"))
		{
			String channelName = msg.substring(7);
			TextChannel chosenChannel = null;
			
			for (TextChannel c : server.getTextChannels()) {
				if (c.getName().equals(channelName)) {
					chosenChannel = c;
					break;
				}
			}
			
			if (chosenChannel == null) {
				logger.error("!stats <channel> : this channel doesn't exist");
				return;
			}
			
			Stats stats = getChannelStats(chosenChannel);
			stats.sendStatsAsMessage(channel);
		}
		
		// Clear all messages that are not integers (for debug purpose)
		else if (msg.equals("!clear"))
		{
			if (!server.getId().equals("504371617888206866"))	// only doable in the test server
				return;
			
			String last = channel.getLatestMessageId();
			RequestFuture<MessageHistory> request; 
			List<Message> messages;
			List<Message> ToDeleteMessages = new ArrayList<Message>();
				
			try {
				int nbReadMsg = 0;
				
				while (true) {
					request = channel.getHistoryBefore(last, 100).submit();	// get the 100 messages sent before the last read
					nbReadMsg = request.get().size();
					
					if (nbReadMsg == 0)
						break;
					
					messages = request.get().getRetrievedHistory();
					
					for (Message m : messages) {
						if (!isInteger(m.getContentDisplay())) {
							ToDeleteMessages.add(m);
						}
					}

					channel.purgeMessages(ToDeleteMessages);
					last = request.get().getRetrievedHistory().get(nbReadMsg - 1).getId();	// last message read
				}				
			}
			catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
			
			System.out.println("Cleared all messages that are not integers!");
		}
	}
    
    /********************************************************************************************************************************/
    
    private Stats getChannelStats(TextChannel channel)
    {
    	String last = channel.getLatestMessageId();
		RequestFuture<MessageHistory> request;
		List<Message> messagesRetrieved;
		
		int nbReadMsg = 0;
		Stats stats = new Stats();
			
		try {
			while (true) {
				request = channel.getHistoryBefore(last, 100).submit();	// get the 100 messages sent before the <last> message
				nbReadMsg =  request.get().size();	// request.get() is very expansive

				if (nbReadMsg == 0)
					break;
				
				stats.nbTotalMessages += nbReadMsg;
				messagesRetrieved = request.get().getRetrievedHistory();
				
				for (Message m : messagesRetrieved) {
					String authorId = m.getAuthor().getId();
					
					if (!stats.nbMessages.containsKey(authorId)) {
						stats.nbMessages.put(authorId, 0);
					}
					stats.nbMessages.put(authorId, stats.nbMessages.get(authorId) + 1);
				}
						
				last = messagesRetrieved.get(nbReadMsg - 1).getId();	// last message read
			}			
		}
		catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		
		
		return stats;
    }
    
    /********************************************************************************************************************************/
    
    private boolean isInteger(String str)
    {
		if (str == null)
			return false;
		
		int length = str.length();
		
		if (length == 0)
			return false;
		
		int i = 0;
		if (str.charAt(0) == '-') {
			if (length == 1)
				return false;
			i = 1;
		}
		
		for (; i < length; i++) {
			char c = str.charAt(i);
			if (c < '0' || c > '9')
				return false;
		}
		
		return true;
    }
}
