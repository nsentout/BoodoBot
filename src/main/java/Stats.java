import java.util.HashMap;
import java.util.Map;

import net.dv8tion.jda.core.entities.TextChannel;

public class Stats
{
	public int nbTotalMessages;
	public HashMap<String, Integer> nbMessagesByAuthor;
	
	public Stats()
	{
		nbTotalMessages = 0;
		nbMessagesByAuthor = new HashMap<String, Integer>();
	}
	
	public void sendStatsAsMessage(TextChannel channel)
	{
		String botMsg = "Nombre total de messages : " + nbTotalMessages + "\n";
		
		for (Map.Entry<String, Integer> entry : nbMessagesByAuthor.entrySet()) {
			botMsg += channel.getGuild().getMemberById(entry.getKey()).getEffectiveName() + " a écrit " + entry.getValue() + " messages";
			botMsg += ", soit " + (entry.getValue() * 100) / nbTotalMessages + "% de tous les messages.\n";
		}
		
		channel.sendMessage(botMsg).queue();
	}
	
	public void addEqual(HashMap<String, Integer> nbMessages)
	{
		for (Map.Entry<String, Integer> entry : nbMessages.entrySet())
		{
			if (this.nbMessagesByAuthor.containsKey(entry.getKey())) {
				this.nbMessagesByAuthor.put(entry.getKey(), this.nbMessagesByAuthor.get(entry.getKey()) + entry.getValue());
			}
			else {
				System.out.println("no: " + nbMessages.get(entry.getKey()));
				this.nbMessagesByAuthor.put(entry.getKey(), nbMessages.get(entry.getKey()));
			}
		}
	}
}
