import java.util.HashMap;
import java.util.Map;

import net.dv8tion.jda.core.entities.TextChannel;

public class Stats
{
	public int nbTotalMessages;
	public HashMap<String, Integer> nbMessages;
	
	public Stats()
	{
		nbTotalMessages = 0;
		nbMessages = new HashMap<String, Integer>();
	}
	
	public void sendStatsAsMessage(TextChannel channel)
	{
		channel.sendMessage("Nombre total de messages sur ce serveur : " + nbTotalMessages).queue();
		
		for (Map.Entry<String, Integer> entry : nbMessages.entrySet()) {
			channel.sendMessage(channel.getGuild().getMemberById(entry.getKey()).getEffectiveName() + " a écrit " + entry.getValue() + " messages").queue();
		}
	}
	
	public HashMap<String, Integer> addEqual(HashMap<String, Integer> nbMessages)
	{
		HashMap<String, Integer> result = nbMessages;
		
		for (Map.Entry<String, Integer> entry : nbMessages.entrySet()) {
			if (this.nbMessages.containsKey(entry.getKey())) {
				this.nbMessages.put(entry.getKey(), entry.getValue() + nbMessages.get(entry.getKey()));
			}
			else {
				this.nbMessages.put(entry.getKey(), nbMessages.get(entry.getKey()));
			}
		}
		
		System.out.println("size : " + this.nbMessages.size());
		
		return result;
	}
}
