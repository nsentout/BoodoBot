import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

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
import java.util.Arrays;
import java.util.Random;
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
	private static LimitedMessage dddLimitedMsg;
	private static LimitedMessage quandLimitedMsg;
	
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
			
			// Prepares bot features
			msgStats = new MessageStats(messageStatsCollection);
			dddLimitedMsg = new LimitedMessage(":ddd: ?", 180);
			quandLimitedMsg = new LimitedMessage("MAINTENAAAAANT ...\n\nhttps://www.youtube.com/watch?v=w1HvkAgr92c", 600);
			
			// Wait for jda to be ready
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
			
			boolean success = msgStats.retrieveChannelStats(event.getTextChannel());
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
				channel.sendMessage("!stats <salon> : ce salon n'existe pas").queue();
				return;
			}
			
			boolean success = msgStats.retrieveChannelStats(chosenChannel);
			if (success) {
				msgStats.sendStatsAsMessage(channel, chosenChannel.getId());
			}
			else {
				channel.sendMessage("La commande a échoué !").queue();
			}
		}
		
		// Insult a random person on the channel
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
		
		// Private joke
		else if (msg.endsWith("c'est quand ?") || msg.endsWith("depuis quand ?"))
		{
			quandLimitedMsg.send(channel);
		}
		
		// Private joke 2
		else if ((msg.contains("ddd") || msg.contains("approche objet") || msg.contains(" ao ")) && !author.getId().equals(BOT_ID))
		{
			dddLimitedMsg.send(channel);
		}

		// If it's an unknown command, do nothing
		else if (msg.startsWith("!")) {
			System.out.println("Unknown command!");
			channel.sendMessage("Cette commande n'existe pas, bolosse").queue();
		}
	}
}
