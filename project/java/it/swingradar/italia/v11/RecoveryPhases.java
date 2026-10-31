package it.swingradar.italia.v11;

/**
 * Experimental EOD lifecycle of a contrarian support watch.
 * Pure arithmetic so it can be tested without market feeds or Android APIs.
 * A technical confirmation is NEVER an order or a statistically validated edge.
 */
final class RecoveryPhases {
    static final String WATCH="OSSERVAZIONE";
    static final String REBOUND="RIMBALZO";
    static final String CONFIRMED="CONFERMATO";
    static final String INVALID="INVALIDATO";
    static final String EXPIRED="SCADUTO";

    private RecoveryPhases(){}

    static boolean supportReclaim(double low,double close,double support,double position,double rsi2){
        return support>0&&low<support*.997&&close>=support*1.005&&
            position>=.45&&rsi2<=30;
    }

    static String initial(double low,double close,double support,double position,double rsi2){
        return supportReclaim(low,close,support,position,rsi2)?REBOUND:WATCH;
    }

    static String next(String previous,double low,double close,double previousHigh,
        double support,double stop,double position,double volumeRatio,double rsi2,int sessionsElapsed){
        if(INVALID.equals(previous)||EXPIRED.equals(previous))return previous;
        // Evaluate invalidation BEFORE a close-based technical confirmation.
        // The stop is anchored to the first observation, never retrospectively moved.
        if(low<=stop||close<support*.995)return INVALID;
        if(CONFIRMED.equals(previous))return CONFIRMED;
        if(sessionsElapsed>12)return EXPIRED;
        if(REBOUND.equals(previous)&&close>previousHigh*1.002&&position>=.60&&volumeRatio>=1.0)
            return CONFIRMED;
        if(WATCH.equals(previous)&&supportReclaim(low,close,support,position,rsi2))return REBOUND;
        return previous;
    }

    static boolean distinctLowerSupport(String before,String after,double oldSupport,double newSupport,
        double low,double close,double position,double rsi2){
        return !INVALID.equals(before)&&INVALID.equals(after)&&oldSupport>0&&
            newSupport<=oldSupport*.99&&
            supportReclaim(low,close,newSupport,position,rsi2);
    }
}
