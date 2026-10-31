package it.swingradar.italia.v11;

/** Run with java com.sun.tools.javac.Main followed by java -cp build/test-classes. */
public final class RecoveryPhasesSmoke {
    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args){
        double support=4.100,stop=4.038;
        double rwayPosition=(4.170-4.040)/(4.315-4.040);
        check(RecoveryPhases.REBOUND.equals(RecoveryPhases.initial(4.040,4.170,support,rwayPosition,5.1)),
            "Rai Way 30 Oct should be a formation rebound, not a confirmed entry");
        check(RecoveryPhases.WATCH.equals(RecoveryPhases.initial(4.105,4.170,support,rwayPosition,5.1)),
            "A close above support without an intraday break is only watch");
        check(RecoveryPhases.REBOUND.equals(RecoveryPhases.next(RecoveryPhases.WATCH,
            4.040,4.170,4.300,support,stop,rwayPosition,.8,5.1,1)),
            "A fresh support reclaim changes watch to rebound");
        check(RecoveryPhases.CONFIRMED.equals(RecoveryPhases.next(RecoveryPhases.REBOUND,
            4.190,4.350,4.315,support,stop,.75,1.2,31,1)),
            "A later strong close above previous high with volume is technical confirmation");
        check(RecoveryPhases.REBOUND.equals(RecoveryPhases.next(RecoveryPhases.REBOUND,
            4.190,4.350,4.315,support,stop,.75,.7,5,1)),
            "A close with weak volume cannot confirm the entry");
        check(RecoveryPhases.INVALID.equals(RecoveryPhases.next(RecoveryPhases.REBOUND,
            4.030,4.350,4.315,support,stop,.75,2.0,5,2)),
            "Intraday stop breach invalidates even if the close recovers");
        check(RecoveryPhases.INVALID.equals(RecoveryPhases.next(RecoveryPhases.WATCH,
            4.085,4.075,4.200,support,stop,.60,2,5,2)),
            "Close under original support invalidates without touching the stop");
        check(RecoveryPhases.EXPIRED.equals(RecoveryPhases.next(RecoveryPhases.WATCH,
            4.100,4.160,4.220,support,stop,.30,1,30,13)),
            "Unconfirmed watches expire after twelve completed bars");
        check(RecoveryPhases.INVALID.equals(RecoveryPhases.next(RecoveryPhases.INVALID,
            4.190,4.400,4.300,support,stop,.80,2,5,3)),
            "Invalidated episodes do not silently reactivate");
        check(RecoveryPhases.distinctLowerSupport(RecoveryPhases.WATCH,RecoveryPhases.INVALID,
            4.175,4.100,4.040,4.170,rwayPosition,5.1),
            "Rai Way can start a new rebound episode after an older higher support fails");
        check(!RecoveryPhases.distinctLowerSupport(RecoveryPhases.WATCH,RecoveryPhases.INVALID,
            4.125,4.100,4.040,4.170,rwayPosition,5.1),
            "Tiny support revisions cannot bypass an invalidation");
        System.out.println("OK: Rai Way rebound, confirmation, volume gate, stop priority, expiry, persistence");
    }
}
