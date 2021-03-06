package boodobot.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageHistory;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.requests.RequestFuture;

public class MessageStatsCommand
{
	private final static Logger logger = LogManager.getLogger(MessageStatsCommand.class);
	
    private final static String SERVER_NAME_KEY = "serverName";
    private final static String CHANNEL_NAME_KEY = "channelName";
    private final static String USER_NAME_KEY = "userName";
    private final static String NB_MESSAGES_KEY = "nbMessages";
    private final static String TOTAL_MESSAGES_CHANNEL_KEY = "totalMessagesChannel";
    private final static String CHANNEL_ID_KEY = "channelId";
    private final static String USER_ID_KEY = "userId";
    private final static String LAST_MESSAGE_READ_ID_KEY = "lastMessageReadId";

    public MongoCollection<Document> collection;

    public HashMap<String, Integer> newMessagesByAuthor;

    /******************************************************************************************************************************************/

    public MessageStatsCommand(MongoCollection<Document> collection)
    {
        this.collection = collection;
        this.newMessagesByAuthor = new HashMap<String, Integer>();
    }

    /******************************************************************************************************************************************/
    
    public void displayStats(TextChannel channelSend, TextChannel channelStats)
    {
        if (channelStats == null) {
        	logger.error("!stats <channel> : this channel doesn't exist");
        	if (channelSend != null)
        		channelSend.sendMessage("ce salon n'existe pas").queue();
            return;
        }
        
    	 boolean success = retrieveChannelStats(channelStats);
         if (success)
             sendStatsAsMessage(channelSend, channelStats.getId());
         else
        	 channelSend.sendMessage("La commande a échoué !").queue();
    }
    
    public void displayStats(TextChannel channelStats)
    {
    	displayStats(channelStats, channelStats);
    }
    
    /******************************************************************************************************************************************/

    // Save stats in the save file
    public void saveMessageStats(TextChannel channel, String lastMsgRead)
    {
        List<Document> documents = new ArrayList<Document>();
        FindIterable<Document> ite = collection.find(Filters.eq(CHANNEL_ID_KEY, channel.getId()));	// find documents with this channel id
        Guild server = channel.getGuild();

        int totalMsg = 0;

        // Add all the messages already in the db to totalMsg
        for (Document doc : ite) {
            totalMsg += (int)doc.get(NB_MESSAGES_KEY);
        }

        for (Map.Entry<String, Integer> entry : newMessagesByAuthor.entrySet())
        {
            // add all the new messages
            totalMsg += entry.getValue();

            // if there is already a document for this channel, search if there is a document for this user on this channel
            if (ite.first() != null) {
                Bson filter = Filters.and(Filters.eq(CHANNEL_ID_KEY, channel.getId()), Filters.eq(USER_ID_KEY, entry.getKey()));
                Document doc = collection.find(filter).first();

                // if there is a document for this user on this channel, update it
                if (doc != null) {
                    ObjectId objectId = (ObjectId) doc.get("_id");
                    int nbMsgSinceLastTime = (int)doc.get(NB_MESSAGES_KEY);

                    collection.updateOne(Filters.eq("_id",objectId),		// update it in the db
                            Updates.combine(Updates.set(NB_MESSAGES_KEY, entry.getValue() + nbMsgSinceLastTime),
                                    Updates.set(LAST_MESSAGE_READ_ID_KEY, lastMsgRead)));;
                }
                // else, create his document
                else {
                    documents.add(new Document(SERVER_NAME_KEY, server.getName())
                            .append(CHANNEL_NAME_KEY, channel.getName())
                            .append(USER_NAME_KEY, server.getMemberById(entry.getKey()).getEffectiveName())
                            .append(NB_MESSAGES_KEY, entry.getValue())
                            .append(TOTAL_MESSAGES_CHANNEL_KEY, 0)
                            .append(CHANNEL_ID_KEY, channel.getId())
                            .append(USER_ID_KEY, entry.getKey())
                            .append(LAST_MESSAGE_READ_ID_KEY, lastMsgRead));
                }
            }

            // else, add a new document
            else {
                String userName;
                if (server.getMemberById(entry.getKey()) != null)
                    userName = server.getMemberById(entry.getKey()).getEffectiveName();
                else	// if a member has left the server but still has messages on it
                    userName = "Déserteur";

                documents.add(new Document(SERVER_NAME_KEY, server.getName())
                        .append(CHANNEL_NAME_KEY, channel.getName())
                        .append(USER_NAME_KEY, userName)
                        .append(NB_MESSAGES_KEY, entry.getValue())
                        .append(TOTAL_MESSAGES_CHANNEL_KEY, 0)
                        .append(CHANNEL_ID_KEY, channel.getId())
                        .append(USER_ID_KEY, entry.getKey())
                        .append(LAST_MESSAGE_READ_ID_KEY, lastMsgRead));
            }
        }

        if (!documents.isEmpty())
            collection.insertMany(documents);

        if (totalMsg > 0)
            collection.updateMany(Filters.eq(CHANNEL_ID_KEY, channel.getId()), Updates.set(TOTAL_MESSAGES_CHANNEL_KEY, totalMsg));
    }
    
    /******************************************************************************************************************************************/

    public boolean retrieveChannelStats(TextChannel channel)
    {
        if (!channel.hasLatestMessage()) {
            logger.error("The channel doesn't have a tracked most recent message");
            return false;
        }

        String lastMsgId;	// last message retrieved
        boolean firstTime = false;	// first time calling the stats command

        // If first time getting the stats of this channel, retrieve all messages since the creation of the channel
        if (getLastMessageReadId(channel.getId()).isEmpty()) {
            firstTime = true;
            lastMsgId = channel.getLatestMessageId();
            String authorLastMsgId = channel.getMessageById(lastMsgId).complete().getAuthor().getId();
            newMessagesByAuthor.put(authorLastMsgId, 1);	// count the last message
        }
        // Else, retrieve all messages since the last time we get the stats
        else {
            lastMsgId = getLastMessageReadId(channel.getId());
        }

        RequestFuture<MessageHistory> request;
        List<Message> messagesRetrieved = null;
        int nbReadMsg = 0;

        // Retrieve messages
        try {
            while (true) {
                if (firstTime)
                    request = channel.getHistoryBefore(lastMsgId, 100).submit();	// get the 100 messages sent before the <lastMsgId> message
                else
                    request = channel.getHistoryAfter(lastMsgId, 100).submit();	// get the 100 messages sent after the <lastMsgId> message

                nbReadMsg =  request.get().size();	// request.get() is very expansive

                if (nbReadMsg == 0)
                    break;

                messagesRetrieved = request.get().getRetrievedHistory();

                for (Message m : messagesRetrieved) {
                    String authorId = m.getAuthor().getId();

                    if (!newMessagesByAuthor.containsKey(authorId)) {
                        newMessagesByAuthor.put(authorId, 0);
                    }
                    newMessagesByAuthor.put(authorId, newMessagesByAuthor.get(authorId) + 1);
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

        saveMessageStats(channel, lastMsgId);

        return true;
    }
    
    /******************************************************************************************************************************************/

    public void sendStatsAsMessage(TextChannel channelMessaged, String channelRequestedId)
    {
        FindIterable<Document> ite = collection.find(Filters.eq(CHANNEL_ID_KEY, channelRequestedId)).sort(Sorts.descending(NB_MESSAGES_KEY));

        int totalMsg = (int) ite.first().get(TOTAL_MESSAGES_CHANNEL_KEY);
        String botMsg = "Nombre total de messages : " + totalMsg + "\n";

        for (Document doc : ite) {
            int nbMsg = (int) doc.get(NB_MESSAGES_KEY);
            botMsg += (String) doc.get(USER_NAME_KEY) + " : " + nbMsg + " messages";
            botMsg += ", soit " + String.format("%.1f", ((float)(nbMsg * 100) / totalMsg)) + "%\n";
        }

        channelMessaged.sendMessage(botMsg).queue();

        newMessagesByAuthor.clear();
    }
    
    /******************************************************************************************************************************************/

    // Returns the ID of the last message read by the previous !stats command
    public String getLastMessageReadId(String channelId)
    {
        FindIterable<Document> ite = collection.find(Filters.eq(CHANNEL_ID_KEY, channelId));

        Document doc = ite.first();
        String lastMsgRead = "";

        if (doc != null)
            lastMsgRead = (String) doc.get(LAST_MESSAGE_READ_ID_KEY);

        return lastMsgRead;
    }

}