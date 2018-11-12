package boodobot.commands;

import java.util.Arrays;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;

import boodobot.BoodoBot;
import net.dv8tion.jda.core.entities.TextChannel;

public class InsultCommand
{
	private final static Logger logger = LogManager.getLogger(InsultCommand.class);
	
    private MongoCollection<Document> collection;
	private Random random;
	
	/******************************************************************************************************************************************/
	
	public InsultCommand(MongoCollection<Document> insultsCollection)
	{
		this.random = new Random();
		this.collection = insultsCollection;
	}
	
	/******************************************************************************************************************************************/
	
	public void insultRandom(TextChannel channel, String authorId)
	{
		 if (channel.getMembers().size() <= 2) {
			 logger.info("can't use this command on a server with less than 3 people");
             return;
		 }

         String memberChosenId = BoodoBot.BOT_ID;

         while (memberChosenId.equals(BoodoBot.BOT_ID) || memberChosenId.equals(authorId)) {
             memberChosenId = channel.getMembers().get(random.nextInt(channel.getMembers().size())).getUser().getId();
         }

         String mention = channel.getGuild().getMemberById(memberChosenId).getAsMention();

         String insult = collection.aggregate(Arrays.asList(Aggregates.sample(1))).first().getString("message");
         insult = insult.replaceFirst("@name", mention);

         channel.sendMessage(insult).queue();
	}
	
	/******************************************************************************************************************************************/
	
	public void insult(TextChannel channel, String victimId)
	{
		String mention = channel.getGuild().getMemberById(victimId).getAsMention();

        String insult = collection.aggregate(Arrays.asList(Aggregates.sample(1))).first().getString("message");
        insult = insult.replaceFirst("@name", mention);

        channel.sendMessage(insult).queue();
	}
}
