import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.requests.RequestFuture;

import javax.security.auth.login.LoginException;

import org.apache.logging.log4j.*;
import org.bson.Document;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


public class BoodoBot extends ListenerAdapter
{
	/********************************************************************************************************************************/
	
	private final static String BOT_TOKEN_FILE_NAME = "bot_token.txt";
	private final static String MONGODB_DB_NAME = "BoodoBot";
	private final static String MONGODB_MESSAGE_STATS_NAME = "MessageStats";
	private final static String MONGODB_INSULTS_NAME = "Insultes";
	private final static String BOT_ID = "504368489415573514";
	
	private final static Logger logger = LogManager.getLogger(BoodoBot.class);
	
	private static MessageStats msgStats;
	
	private static MongoCollection<Document> messageStatsCollection;
	private static MongoCollection<Document> insultsCollection;
	
	/********************************************************************************************************************************/
    
	public static void main(String[] args)
	{
		try {
			// Retrieve the bot token
			String botToken = new String(Files.readAllBytes(Paths.get(BOT_TOKEN_FILE_NAME))); 
			
			// Build JDA
			JDA jda = new JDABuilder(botToken)
					.addEventListener(new BoodoBot()) 
					.build();
			
			// Connection to MongoDB
			MongoClient mongoClient = MongoClients.create();
			MongoDatabase database = mongoClient.getDatabase(MONGODB_DB_NAME);
			messageStatsCollection = database.getCollection(MONGODB_MESSAGE_STATS_NAME);
			insultsCollection = database.getCollection(MONGODB_INSULTS_NAME);
			
			msgStats = new MessageStats(messageStatsCollection);
			
			jda.awaitReady();
            
			System.out.println("Finished building JDA!");
		}
		catch (LoginException | InterruptedException e) {
			e.printStackTrace();
		}
		catch (IOException e) {
			System.err.println("An error occured when reading the file containing the bot token");
		}
	}

	/********************************************************************************************************************************/
    
    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
		User author = event.getAuthor();	
		TextChannel channel = event.getTextChannel();
		Guild server = event.getGuild();
		String msg = event.getMessage().getContentDisplay();
		
		// Roll a 100-sided dice
		if (msg.equals("!roll"))
		{
			System.out.println("Call command " + msg);
			
		    Random rand = new Random();
		    int roll = rand.nextInt(100) + 1;
		    channel.sendMessage(author.getName() + " a lancé un dé 100 ... ").queue();
		    channel.sendMessage("Il a fait " + roll + " !").queueAfter(1500, TimeUnit.MILLISECONDS);
		}
		
		// Display stats for this channel
		else if (msg.equals("!stats"))
		{
			System.out.println("Call command " + msg);
			
			boolean success = retrieveChannelStats(event.getTextChannel());
			if (success)
				msgStats.sendStatsAsMessage(event.getTextChannel());
			else
				channel.sendMessage("La commande a échoué !").queue();
		}
		
		/*
		// Display stats for every channel on the server
		else if (msg.equals("!stats all"))
		{
			System.out.println("Call command " + msg);
			
			for (TextChannel c : server.getTextChannels())
			{
				boolean success = retrieveChannelStats(c);
				if (!success) {
					channel.sendMessage("La commande a échoué !").queue();
					return;
				}
			}
			msgStats.sendStatsAsMessage(channel);
		}
		*/
		
		// Display stats for a chosen channel
		else if (msg.startsWith("!stats"))
		{
			System.out.println("Call command " + msg);
			
			String channelName = msg.substring(7);
			TextChannel chosenChannel = null;
			
			for (TextChannel c : server.getTextChannels()) {
				if (c.getName().equals(channelName)) {
					chosenChannel = c;
					break;
				}
			}
			
			if (chosenChannel == null) {
				System.err.println("!stats <channel> : this channel doesn't exist");
				channel.sendMessage("!stats <channel> : this channel doesn't exist").queue();
				return;
			}
			
			boolean success = retrieveChannelStats(chosenChannel);
			if (success) {
				msgStats.sendStatsAsMessage(channel, chosenChannel.getId());
			}
			else {
				channel.sendMessage("La commande a échoué !").queue();
			}
		}
		
		// Insult a random perso on the channel
		else if (msg.equals("!insult"))
		{
			System.out.println("Call command " + msg);
			
			if (channel.getMembers().size() <= 2)
				return;
			
			Random rand = new Random();
			String memberChosenId = BOT_ID;

			while (memberChosenId.equals(BOT_ID) || memberChosenId.equals(author.getId())) {
				memberChosenId = channel.getMembers().get(rand.nextInt(channel.getMembers().size())).getUser().getId();
			}
			
			String mention = server.getMemberById(memberChosenId).getAsMention();
			
			String insult = insultsCollection.aggregate(Arrays.asList(Aggregates.sample(1))).first().getString("message");
			insult = insult.replaceFirst("@name", mention);
			
			channel.sendMessage(insult).queue();
		}
		
		// Clear all messages that are not integers (for debug purpose)
		else if (msg.equals("!clear"))
		{
			if (!server.getId().equals("504371617888206866"))	// only doable in the test server
				return;
			
			System.out.println("Call command " + msg);
			
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
			
			logger.debug("Cleared all messages that are not integers!");
		}
		
		// Find a message thanks to its id (for debug purpose)
		else if (msg.startsWith("!find"))
		{
			if (!server.getId().equals("504371617888206866"))	// only doable in the test server
				return;
			
			System.out.println("Call command " + msg);
			
			String msgId = msg.substring(6);
			
			Message foundMsg = channel.getMessageById(msgId).complete();
			if (foundMsg != null) {
				System.out.println("message content: " + foundMsg.getContentDisplay());
				System.out.println("creation time: " + foundMsg.getCreationTime());
			}
			else {
				System.out.println("no message with this id");
			}
		}
		
		// If it's an unknown command, do nothing
		else if (msg.startsWith("!")) {
			System.out.println("Unknown command!");
			channel.sendMessage("Cette commande n'existe pas, bolosse").queue();
		}
	}
    
    /********************************************************************************************************************************/
    
    private boolean retrieveChannelStats(TextChannel channel)
    {
    	if (!channel.hasLatestMessage()) {
    		System.out.println("The channel doesn't have a tracked most recent message");
    		return false;
    	}
    	
    	String lastMsgId;	// last message retrieved
    	boolean firstTime = false;	// first time calling the stats command
    	
    	// If first time getting the stats of this channel, retrieve all messages since the creation of the channel
    	if (msgStats.getLastMessageReadId(channel.getId()).isEmpty()) {
    		lastMsgId = channel.getLatestMessageId();
    		firstTime = true;
    	}
    	// Else, retrieve all messages since the last time we get the stats
    	else {
    		lastMsgId = msgStats.getLastMessageReadId(channel.getId());
    	}
    	
		RequestFuture<MessageHistory> request;
		List<Message> messagesRetrieved = null;
		
		int nbReadMsg = 0;
		
		// Retrieve messages
		try {
			while (true) {
				if (firstTime)
					request = channel.getHistoryBefore(lastMsgId, 20).submit();	// get the 100 messages sent before the <lastMsgId> message
				else
					request = channel.getHistoryAfter(lastMsgId, 20).submit();	// get the 100 messages sent after the <lastMsgId> message
				
				nbReadMsg =  request.get().size();	// request.get() is very expansive

				if (nbReadMsg == 0)
					break;
					
				messagesRetrieved = request.get().getRetrievedHistory();
				
				for (Message m : messagesRetrieved) {
					String authorId = m.getAuthor().getId();
					
					if (!msgStats.nbMessagesByAuthor.containsKey(authorId)) {
						msgStats.nbMessagesByAuthor.put(authorId, 0);
					}
					msgStats.nbMessagesByAuthor.put(authorId, msgStats.nbMessagesByAuthor.get(authorId) + 1);
				}
				
				if (firstTime)
					lastMsgId = messagesRetrieved.get(nbReadMsg - 1).getId();	// last message read
				else
					lastMsgId = messagesRetrieved.get(0).getId();	// last message read
			}			
		}
		catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		
		// Save the stats in the db
		if (firstTime)
			lastMsgId = channel.getLatestMessageId();
			
		msgStats.saveMessageStats(channel, lastMsgId);
		
		return true;
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
