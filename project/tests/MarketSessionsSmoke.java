package it.swingradar.italia.v11;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class MarketSessionsSmoke {
    private static final ZoneId ROME=ZoneId.of("Europe/Rome");
    private static ZonedDateTime at(int y,int m,int d,int h,int minute){
        return ZonedDateTime.of(y,m,d,h,minute,0,0,ROME);
    }
    private static void ok(boolean check,String label){if(!check)throw new AssertionError(label);}
    public static void main(String[] args){
        ZonedDateTime fridayMorning=at(2026,10,30,11,20);
        ok(MarketSessions.expectedLastComplete(fridayMorning).toString().equals("2026-10-29"),"Friday morning expects Thursday");
        ok(MarketSessions.expectedLastComplete(at(2026,10,30,18,20)).toString().equals("2026-10-30"),"Friday after safe close expects Friday");
        ok(MarketSessions.expectedLastComplete(at(2026,10,31,12,0)).toString().equals("2026-10-30"),"Saturday expects Friday");
        ok(MarketSessions.expectedLastComplete(at(2026,11,2,11,0)).toString().equals("2026-10-30"),"Monday morning expects Friday");
        ok(MarketSessions.shouldRefresh("2026-10-28","2026-10-29T08:00:00Z",fridayMorning),"outdated 28th should refresh on 30th");
        ok(!MarketSessions.shouldRefresh("2026-10-29","2026-10-29T08:00:00Z",fridayMorning),"current EOD should not refresh");
        ok(!MarketSessions.shouldRefresh("2026-10-28","2026-10-30T09:00:00Z",fridayMorning),"retry throttled under two hours");
        ok(MarketSessions.shouldRefresh("","",fridayMorning),"first launch should refresh");
        System.out.println("OK: Rome EOD cutoff, weekend handling, stale refresh, retry throttle");
    }
}
