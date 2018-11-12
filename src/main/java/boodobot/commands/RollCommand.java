package boodobot.commands;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.core.entities.TextChannel;

public class RollCommand
{
	private Random random;
	
	/******************************************************************************************************************************************/
	
	public RollCommand()
	{
		this.random = new Random();
	}
	
	/******************************************************************************************************************************************/
	
	public void roll(TextChannel channel, String userName)
	{
		int roll = random.nextInt(100) + 1;
		channel.sendMessage(userName + " a lancé un dé 100 ... ").queue();
		channel.sendMessage("Il a fait " + roll + " !").queueAfter(1500, TimeUnit.MILLISECONDS);
	}
}
