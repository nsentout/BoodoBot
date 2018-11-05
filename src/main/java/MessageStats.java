import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;

public class MessageStats
{
	private final static String SERVER_NAME_KEY = "serverName";
	private final static String CHANNEL_NAME_KEY = "channelName";
	private final static String USER_NAME_KEY = "userName";
	private final static String NB_MESSAGES_KEY = "nbMessages";
	private final static String TOTAL_MESSAGES_CHANNEL_KEY = "totalMessagesChannel";
	private final static String CHANNEL_ID_KEY = "channelId";
	private final static String USER_ID_KEY = "userId";
	private final static String LAST_MESSAGE_READ_ID_KEY = "lastMessageReadId";
	
	public MongoCollection<Document> collection;
	
	public HashMap<String, Integer> nbMessagesByAuthor;

	/******************************************************************************************************************************************/
	
	public MessageStats(MongoCollection<Document> collection)
	{
		this.collection = collection;
		this.nbMessagesByAuthor = new HashMap<String, Integer>();
	}
	
	/******************************************************************************************************************************************/
	
	// Save stats in the save file
	public void saveMessageStats(TextChannel channel, String lastMsgRead)
	{
		List<Document> documents = new ArrayList<Document>();
		FindIterable<Document> ite = collection.find(Filters.eq(CHANNEL_ID_KEY, channel.getId()));	// find documents with this channel id
		Guild server = channel.getGuild();

		int totalMsg = 0;
		
		for (Map.Entry<String, Integer> entry : nbMessagesByAuthor.entrySet())
		{
			// if there is already a document for this channel, search if there is a document for this user on this channel
			if (ite.first() != null) {
				Bson filter = Filters.and(Filters.eq(CHANNEL_ID_KEY, channel.getId()), Filters.eq(USER_ID_KEY, entry.getKey()));
				Document doc = collection.find(filter).first();
				
				// if there is a document for this user on this channel, update it
				if (doc != null) {
					ObjectId objectId = (ObjectId) doc.get("_id");
					int nbMsgSinceLastTime = (int)doc.get(NB_MESSAGES_KEY);
					totalMsg += nbMsgSinceLastTime + entry.getValue();
					
					collection.updateOne(Filters.eq("_id",objectId),		// update it in the db
							Updates.combine(Updates.set(NB_MESSAGES_KEY, entry.getValue() + nbMsgSinceLastTime), 
											Updates.set(LAST_MESSAGE_READ_ID_KEY, lastMsgRead)));
					
					nbMessagesByAuthor.put(entry.getKey(), nbMessagesByAuthor.get(entry.getKey()) + nbMsgSinceLastTime);
				}
				// else, create his document
				else {
					documents.add(new Document(SERVER_NAME_KEY, server.getName())
							.append(CHANNEL_NAME_KEY, channel.getName())
							.append(USER_NAME_KEY, server.getMemberById(entry.getKey()).getEffectiveName() )
							.append(NB_MESSAGES_KEY, entry.getValue())
							.append(TOTAL_MESSAGES_CHANNEL_KEY, 0)
							.append(CHANNEL_ID_KEY, channel.getId())
							.append(USER_ID_KEY, entry.getKey())
							.append(LAST_MESSAGE_READ_ID_KEY, lastMsgRead));
					totalMsg += entry.getValue();
				}
			}
			
			// else, add a new document
			else {
				documents.add(new Document(SERVER_NAME_KEY, server.getName())
						.append(CHANNEL_NAME_KEY, channel.getName())
						.append(USER_NAME_KEY, server.getMemberById(entry.getKey()).getEffectiveName() )
						.append(NB_MESSAGES_KEY, entry.getValue())
						.append(TOTAL_MESSAGES_CHANNEL_KEY, 0)
						.append(CHANNEL_ID_KEY, channel.getId())
						.append(USER_ID_KEY, entry.getKey())
						.append(LAST_MESSAGE_READ_ID_KEY, lastMsgRead));
				totalMsg += entry.getValue();
			}
		}
		
		if (!documents.isEmpty())
			collection.insertMany(documents);
		
		if (totalMsg > 0)
			collection.updateMany(Filters.eq(CHANNEL_ID_KEY, channel.getId()), Updates.set(TOTAL_MESSAGES_CHANNEL_KEY, totalMsg));
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
	
	/******************************************************************************************************************************************/
	
	public void sendStatsAsMessage(TextChannel channelMessaged, String channelRequestedId)
	{	
		FindIterable<Document> ite = collection.find(Filters.eq(CHANNEL_ID_KEY, channelRequestedId));
		
		int totalMsg = (int) ite.first().get(TOTAL_MESSAGES_CHANNEL_KEY);
		String botMsg = "Nombre total de messages : " + totalMsg + "\n";
		
		for (Document doc : ite) {
			int nbMsg = (int) doc.get(NB_MESSAGES_KEY);
			botMsg += (String) doc.get(USER_NAME_KEY) + " a écrit " + nbMsg + " messages";
			botMsg += ", soit " + String.format("%.1f", ((float)(nbMsg * 100) / totalMsg)) + "% de tous les messages.\n";
		}

		channelMessaged.sendMessage(botMsg).queue();
		
		nbMessagesByAuthor.clear();
	}
	
	public void sendStatsAsMessage(TextChannel channelMessaged)
	{	
		sendStatsAsMessage(channelMessaged, channelMessaged.getId());
	}
}
