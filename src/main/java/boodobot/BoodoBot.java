package boodobot;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import javax.security.auth.login.LoginException;

import org.apache.logging.log4j.*;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import boodobot.commands.InsultCommand;
import boodobot.commands.MessageStatsCommand;
import boodobot.commands.RollCommand;
import boodobot.util.LimitedMessage;

import java.io.*;
import java.util.List;


public class BoodoBot extends ListenerAdapter
{
    /********************************************************************************************************************************/
    private final static Logger logger = LogManager.getLogger(BoodoBot.class);
    
    private final static String BOT_TOKEN_FILE_NAME = "bot_token.txt";
    private final static String MONGODB_AUTHENTIFICATION_DATA_FILE_NAME = "db_auth.txt";
    private final static String MONGODB_DB_NAME = "boodobot";
    private final static String MONGODB_MESSAGE_STATS_NAME = "msgstats";
    private final static String MONGODB_INSULTS_NAME = "insultes";
    public final static String BOT_ID = "504368489415573514";

    // Commands
    private static MessageStatsCommand msgStatsCmd;
    private static InsultCommand insultCmd;
    private static RollCommand rollCmd;
    private static LimitedMessage dddLimitedMsg;
    private static LimitedMessage quandLimitedMsg;

    /********************************************************************************************************************************/

    private String getFile(String fileName) throws IOException
    {
        InputStream in = getClass().getResourceAsStream("/" + fileName);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        return reader.readLine();
    }

    public static void main(String[] args)
    {
        try {
        	// Create the bot
            BoodoBot bot = new BoodoBot();
        	
            // Retrieve the bot token and the mongodb authentification data
            String botToken = bot.getFile(BOT_TOKEN_FILE_NAME);
            String dbAuth = bot.getFile(MONGODB_AUTHENTIFICATION_DATA_FILE_NAME);

            // Build JDA
            JDA jda = new JDABuilder(botToken).addEventListener(bot).build();

            // Connection to MongoDB
            MongoClient mongoClient = MongoClients.create("mongodb://" + dbAuth);
            MongoDatabase database = mongoClient.getDatabase(MONGODB_DB_NAME);

            // Prepare bot commands
            msgStatsCmd = new MessageStatsCommand(database.getCollection(MONGODB_MESSAGE_STATS_NAME));
            insultCmd = new InsultCommand(database.getCollection(MONGODB_INSULTS_NAME));
            rollCmd = new RollCommand();
            quandLimitedMsg = new LimitedMessage("MAINTENAAAAANT ...\n\nhttps://www.youtube.com/watch?v=w1HvkAgr92c", 300);

            // Wait for jda to be ready
            jda.awaitReady();

            logger.info("Finished building JDA!");
        }
        catch (LoginException | InterruptedException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            logger.error("An error occured when reading the file containing the bot token");
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
           logger.info("Call command " + msg);

           rollCmd.roll(channel, author.getName());
        }

        // Display stats for this channel
        else if (msg.equals("!stats"))
        {
        	logger.info("Call command " + msg);

            msgStatsCmd.displayStats(channel);
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
        	logger.info("Call command " + msg);

            String channelName = msg.substring(7);
            TextChannel chosenChannel = null;

            for (TextChannel c : server.getTextChannels()) {
                if (c.getName().equals(channelName)) {
                    chosenChannel = c;
                    break;
                }
            }
            
            msgStatsCmd.displayStats(channel, chosenChannel);
        }

        // Insult a random person on the channel
        else if (msg.equals("!insult"))
        {
        	logger.info("Call command " + msg);

            insultCmd.insultRandom(channel, author.getId());
        }
        
        // Insult a specific perso on the channel
        else if (msg.startsWith("!insult"))
        {
        	logger.info("Call command " + msg);
        	
        	String victimId = "";
        	List<Member> members;
        	
        	if (msg.charAt(8) == '@') {
        		members = server.getMembersByEffectiveName(msg.substring(9), true);
        		if (!members.isEmpty())
        			victimId = members.get(0).getUser().getId();
        		else {
        			logger.error("this user doesn't exit");
        			return;
        		}
        	}
        	else {
        		members = server.getMembersByEffectiveName(msg.substring(8), true);
        		if (!members.isEmpty())
        			victimId = members.get(0).getUser().getId();
        		else {
        			logger.error("this user doesn't exit");
        			return;
        		}
        	}
        	
        	insultCmd.insult(channel, victimId);
        }

        // Private joke
        else if (msg.endsWith("c'est quand ?") || msg.endsWith("depuis quand ?"))
        {
            quandLimitedMsg.send(channel);
        }
        
        // Private joke 2
        else if ((msg.contains("ddd") || msg.contains("approche objet") || msg.contains(" ao ")) && !author.getId().equals(BOT_ID))
        {
        	if (server.getEmotesByName("ddd", true).isEmpty()) {
        		logger.info("this emote doesn't exist on this server");
        		return;
        	}
        	
            Emote emote = server.getEmotesByName("ddd", true).get(0);
            if (emote != null) {
                if (dddLimitedMsg == null)
                    dddLimitedMsg = new LimitedMessage(emote.getAsMention() + " ?", 600);
            }

            dddLimitedMsg.send(channel);
        }

        // If it's an unknown command, do nothing
        else if (msg.startsWith("!")) {
        	logger.info("Unknown command!");
            channel.sendMessage("Cette commande n'existe pas, bolosse").queue();
        }
    }
}