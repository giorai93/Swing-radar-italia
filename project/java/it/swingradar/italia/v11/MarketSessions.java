package it.swingradar.italia.v11;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/** Expected EOD freshness, based on Rome weekdays; public holidays may delay it. */
final class MarketSessions {
    private static final LocalTime SAFE_CLOSE=LocalTime.of(18,15);

    private MarketSessions(){}

    static LocalDate expectedLastComplete(ZonedDateTime romeNow){
        LocalDate day=romeNow.toLocalDate();
        if(romeNow.toLocalTime().isBefore(SAFE_CLOSE))day=day.minusDays(1);
        while(day.getDayOfWeek()==DayOfWeek.SATURDAY||day.getDayOfWeek()==DayOfWeek.SUNDAY)
            day=day.minusDays(1);
        return day;
    }

    static boolean shouldRefresh(String lastSession,String lastRunIso,ZonedDateTime romeNow){
        LocalDate expected=expectedLastComplete(romeNow);
        try{if(lastSession!=null&&!lastSession.isEmpty()&&
            !LocalDate.parse(lastSession).isBefore(expected))return false;}
        catch(Exception ignored){}
        try{
            Instant previous=Instant.parse(lastRunIso);
            // Avoid repeatedly hitting a delayed provider on every screen open.
            return Duration.between(previous,romeNow.toInstant()).toMinutes()>=120;
        }catch(Exception ignored){return true;}
    }
}
