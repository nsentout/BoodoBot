import java.util.Calendar;
import java.util.GregorianCalendar;

import net.dv8tion.jda.core.entities.TextChannel;

public class LimitedMessage
{
	private int timeLimit;		// in seconds
	private String message;
	private Calendar calendar;
	
	/******************************************************************************************************************************************/
	
	public LimitedMessage(String message, int timeLimit)
	{
		this.message = message;
		this.timeLimit = timeLimit;
		this.calendar = new GregorianCalendar(0, 0, 0);
	}
	
	/******************************************************************************************************************************************/

	public void send(TextChannel channel)
	{
		long now = Calendar.getInstance().getTimeInMillis();
		
		if (now > calendar.getTimeInMillis() + timeLimit * 1000) {
			channel.sendMessage(message).queue();
			calendar = Calendar.getInstance();
		}
	}
}
