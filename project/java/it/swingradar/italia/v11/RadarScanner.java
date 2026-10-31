package it.swingradar.italia.v11;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.UnknownHostException;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class RadarScanner {
    private static final ZoneId ROME=ZoneId.of("Europe/Rome");
    private static final AtomicBoolean BUSY=new AtomicBoolean(false);
    private static volatile int done=0,total=0,good=0,failed=0,newSignals=0,historicalSignals=0;
    private static volatile String current="",lastError="";
    interface Complete{void onComplete();}
    static boolean isRunning(){return BUSY.get();}

    static JSONObject status(Context context){
        JSONObject a=new JSONObject();
        try{
            android.content.SharedPreferences p=context.getSharedPreferences("scan",0);
            JSONObject report=new JSONObject(p.getString("last_report","{}"));
            String expected=MarketSessions.expectedLastComplete(java.time.ZonedDateTime.now(ROME)).toString();
            a.put("running",BUSY.get()).put("done",done).put("total",total).put("good",good).put("failed",failed)
             .put("newSignals",newSignals).put("historicalSignals",historicalSignals).put("current",current).put("lastError",lastError)
             .put("lastRun",p.getString("last_run","mai")).put("lastInfo",p.getString("last_info",""))
             .put("report",report).put("expectedSession",expected)
             .put("sessionStale",report.optString("lastSession","").compareTo(expected)<0)
             .put("candidates",new JSONArray(p.getString("last_candidates","[]")))
             .put("recoveryZones",new JSONArray(p.getString("last_recovery_zones","[]")))
             .put("eventMomentum",new JSONArray(p.getString("last_event_momentum","[]")))
             .put("universeInfo",ItalianUniverse.status(context))
             .put("earnings",EarningsCalendar.status(context))
             .put("fundamentals",Fundamentals.status(context));
        }catch(Exception ignored){}return a;
    }
    static boolean start(Context context,Complete completion){
        return start(context,completion,false);
    }
    static boolean start(Context context,Complete completion,boolean forceFresh){
        if(!BUSY.compareAndSet(false,true))return false;
        final Context app=context.getApplicationContext();final Complete end=completion;
        final boolean force=forceFresh;
        new Thread(new Runnable(){@Override public void run(){
            try{runScan(app,force);}finally{BUSY.set(false);if(end!=null)end.onComplete();}
        }},"swing-italia-scanner").start();return true;
    }
    static boolean refreshIfDue(Context context){
        android.content.SharedPreferences p=context.getSharedPreferences("scan",0);
        String day="";
        try{day=new JSONObject(p.getString("last_report","{}")).optString("lastSession","");}catch(Exception ignored){}
        if(!MarketSessions.shouldRefresh(day,p.getString("last_run",""),java.time.ZonedDateTime.now(ROME)))return false;
        return start(context,null,true);
    }
    private static class Bar{
        String day;double o,h,l,c,v;
        Bar(String day,double o,double h,double l,double c,double v){this.day=day;this.o=o;this.h=h;this.l=l;this.c=c;this.v=v;}
    }
    private static class FeedBlocked extends Exception{
        final int code;FeedBlocked(int code){super("HTTP "+code);this.code=code;}
    }
    private static void runScan(Context context,boolean forceFresh){
        done=good=failed=newSignals=historicalSignals=0;current=lastError="";
        List<String[]> universe=ItalianUniverse.discover(context);total=universe.size();
        JSONArray errors=new JSONArray();List<JSONObject> candidates=new ArrayList<JSONObject>();
        List<JSONObject> recoveries=new ArrayList<JSONObject>();
        List<JSONObject> eventMovers=new ArrayList<JSONObject>();
        LinkedHashMap<String,JSONObject> diagnostics=new LinkedHashMap<String,JSONObject>();
        RadarStore store=new RadarStore(context);int dnsInARow=0;String lastSession="";
        try{
            // Upcoming financial reports are fetched BEFORE the long daily-chart scan.
            EarningsCalendar.refresh(context,universe);
            for(String[] item:universe){
                if(Thread.currentThread().isInterrupted())break;
                String ticker=item[0],name=item[1],segment=item[2];current=ticker;
                JSONObject cached=store.quote(ticker);
                try{
                    List<Bar> bars;
                    long fetchedAt=System.currentTimeMillis();
                    boolean reuse=!forceFresh&&cached!=null&&fetchedAt-cached.optLong("fetchedAt")<6*60*60*1000L;
                    if(reuse&&needsCloseRefresh(cached))reuse=false;
                    if(reuse){
                        bars=barsFromJson(new JSONArray(cached.optString("payload","[]")));
                        fetchedAt=cached.optLong("fetchedAt");
                    }else{
                        bars=downloadWithRetry(ticker);
                        if(!bars.isEmpty())store.saveQuote(ticker,bars.get(bars.size()-1).day,toJson(bars).toString());
                        try{Thread.sleep(450);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
                    }
                    bars=completedDailyBars(bars,fetchedAt);
                    if(bars.size()<210){
                        failed++;recordError(errors,ticker,"Storico insufficiente: "+bars.size()+" barre");
                    }else{
                        good++;String day=bars.get(bars.size()-1).day;
                        if(day.compareTo(lastSession)>0)lastSession=day;
                        // Backfill all completed daily candles in a rolling 35-session window.
                        // Each prefix is evaluated using ONLY data available at that historical close.
                        // This restores genuine past setup dates even on a fresh installation.
                        JSONObject latest=null,lastRecovery=null;
                        int recoveryStart=-1;
                        boolean latestInserted=false;
                        JSONObject lastEventMover=null;
                        for(int end=Math.max(210,bars.size()-35);end<=bars.size();end++){
                            List<Bar> prefix=bars.subList(0,end);
                            JSONObject recovery=recoveryZone(prefix,ticker,name,segment);
                            if(lastRecovery==null){
                                if(recovery!=null){
                                    lastRecovery=startRecoveryState(recovery,prefix);
                                    recoveryStart=end;
                                }
                            }else if((RecoveryPhases.INVALID.equals(lastRecovery.optString("phase"))||
                                      RecoveryPhases.EXPIRED.equals(lastRecovery.optString("phase")))&&
                                      shouldRestartRecovery(lastRecovery,recovery,prefix,end-recoveryStart)){
                                // A separate later observation can open a new research episode.
                                lastRecovery=startRecoveryState(recovery,prefix);
                                recoveryStart=end;
                            }else{
                                String phaseBefore=lastRecovery.optString("phase");
                                updateRecoveryState(lastRecovery,recovery,prefix,end-recoveryStart);
                                // A genuine NEW lower support can create a distinct rebound
                                // on the same day that an older, higher-support watch fails.
                                // Do not relabel the failed old episode as successful.
                                if(recovery!=null){
                                    Bar bar=prefix.get(prefix.size()-1);
                                    double pos=bar.h>bar.l?(bar.c-bar.l)/(bar.h-bar.l):.5;
                                    if(RecoveryPhases.distinctLowerSupport(phaseBefore,lastRecovery.optString("phase"),
                                        lastRecovery.optDouble("support"),recovery.optDouble("support"),
                                        bar.l,bar.c,pos,recovery.optDouble("rsi2"))){
                                        lastRecovery=startRecoveryState(recovery,prefix);
                                        recoveryStart=end;
                                    }
                                }
                            }
                            JSONObject mover=eventMomentum(prefix,ticker,name,segment);
                            if(mover!=null)lastEventMover=mover;
                            JSONObject s=evaluate(prefix,ticker,name,segment);
                            if(s==null)continue;
                            boolean inserted=store.signal(s);
                            if(end==bars.size()){
                                latest=s;
                                if(inserted){newSignals++;latestInserted=true;}
                            }else if(inserted)historicalSignals++;
                        }
                        if(latest==null){
                            JSONArray watches=watch(bars,ticker,name,segment);
                            for(int i=0;i<watches.length();i++)candidates.add(watches.getJSONObject(i));
                        }
                        if(lastRecovery!=null){
                            recoveries.add(lastRecovery);
                            // Reminder only when the watch zone is valid on the most recent close.
                            // This never calls the watch zone a confirmed trade.
                            if(lastRecovery.optString("lastObservedAt").equals(day)&&age(day)<=3&&
                                lastRecovery.optInt("matched")==4&&
                                !RecoveryPhases.INVALID.equals(lastRecovery.optString("phase"))&&
                                !RecoveryPhases.EXPIRED.equals(lastRecovery.optString("phase"))){
                                int days=EarningsCalendar.daysUntil(context,ticker);
                                if(days>=0&&days<=3)notifyUpcomingEarnings(context,lastRecovery,days);
                            }
                        }
                        if(lastEventMover!=null){
                            eventMovers.add(lastEventMover);
                            if(lastEventMover.optString("asof").equals(day)&&age(day)<=3){
                                int days=EarningsCalendar.daysUntil(context,ticker);
                                if(days>=0&&days<=3)notifyEventWatch(context,lastEventMover,days);
                            }
                        }
                        diagnostics.put(ticker,diagnose(bars,ticker,name,segment,latest,lastRecovery,lastEventMover));
                        reconcileSignals(store,ticker,bars);
                        if(latestInserted&&latest!=null&&age(latest.optString("asof"))<=3&&
                            !store.hasRecentOngoingSignal(ticker,latest.optString("kind"),latest.optString("asof")))
                            notifyNew(context,latest);
                    }
                    dnsInARow=0;
                }catch(FeedBlocked blocked){
                    failed++;lastError="Feed Yahoo BLOCCATO (HTTP "+blocked.code+") su "+ticker+". Non si aggirano i blocchi.";
                    recordError(errors,ticker,lastError);done++;break;
                }catch(Exception e){
                    failed++;lastError=ticker+": "+e.getClass().getSimpleName()+" — "+e.getMessage();
                    recordError(errors,ticker,lastError);
                    if(e instanceof UnknownHostException)dnsInARow++;else dnsInARow=0;
                    // Unreliable quotes remain in the cache, but are never notified as fresh.
                    if(dnsInARow>=3){lastError="Connessione Yahoo indisponibile su 3 ticker consecutivi. Riprova più tardi.";done++;break;}
                }
                if(!diagnostics.containsKey(ticker)){
                    try{diagnostics.put(ticker,new JSONObject().put("ticker",ticker).put("status","DATI_NON_DISPONIBILI")
                        .put("reason",lastError.isEmpty()?"Storico insufficiente o richiesta non riuscita":lastError));}
                    catch(Exception ignored){}
                }
                done++;
            }
        }finally{
            current="";
            // One batch of actual financial metrics; cached for twelve hours.
            // A provider outage must not alter or fabricate technical signals.
            Fundamentals.refresh(context,universe);
            Collections.sort(candidates,new Comparator<JSONObject>(){
                @Override public int compare(JSONObject a,JSONObject b){return b.optInt("proximity")-a.optInt("proximity");}
            });
            Collections.sort(recoveries,new Comparator<JSONObject>(){
                @Override public int compare(JSONObject a,JSONObject b){
                    int cmp=b.optString("relevantAt",b.optString("asof"))
                        .compareTo(a.optString("relevantAt",a.optString("asof")));
                    return cmp!=0?cmp:b.optInt("proximity")-a.optInt("proximity");
                }
            });
            Collections.sort(eventMovers,new Comparator<JSONObject>(){
                @Override public int compare(JSONObject a,JSONObject b){
                    int cmp=b.optString("asof").compareTo(a.optString("asof"));
                    return cmp!=0?cmp:Double.compare(b.optDouble("rvol"),a.optDouble("rvol"));
                }
            });
            JSONArray recoveryTop=new JSONArray();
            for(int i=0;i<Math.min(60,recoveries.size());i++)recoveryTop.put(recoveries.get(i));
            JSONArray eventTop=new JSONArray();
            for(int i=0;i<Math.min(80,eventMovers.size());i++)eventTop.put(eventMovers.get(i));
            JSONArray diagnosticList=new JSONArray();
            JSONArray staleList=new JSONArray();
            String expected=MarketSessions.expectedLastComplete(java.time.ZonedDateTime.now(ROME)).toString();
            for(String[] item:universe){
                JSONObject d=diagnostics.get(item[0]);
                if(d==null)try{d=new JSONObject().put("ticker",item[0]).put("status","NON_ANALIZZATO")
                    .put("reason","Scansione terminata prima di questo titolo");}catch(Exception ignored){}
                if(d!=null){
                    diagnosticList.put(d);
                    String quoteDay=d.optString("asof","");
                    if(!quoteDay.isEmpty()&&quoteDay.compareTo(expected)<0)
                        try{staleList.put(new JSONObject().put("ticker",item[0]).put("asof",quoteDay));}
                        catch(Exception ignored){}
                }
            }
            JSONArray top=new JSONArray();int forces=0,weaknesses=0;
            for(JSONObject w:candidates){
                if(w.optString("kind").equals("FORZA")&&forces<20){top.put(w);forces++;}
                if(w.optString("kind").equals("DEBOLEZZA")&&weaknesses<20){top.put(w);weaknesses++;}
            }
            JSONObject report=new JSONObject();
            try{report.put("attempted",done).put("universe",total).put("usable",good).put("withoutHistory",failed)
                .put("incomplete",done<total).put("errors",errors).put("completedAt",Instant.now().toString())
                .put("lastSession",lastSession).put("historicalAdded",historicalSignals)
                .put("expectedSession",expected).put("staleQuoteCount",staleList.length())
                .put("staleQuotes",staleList)
                .put("diagnostics",diagnosticList);}
            catch(Exception ignored){}
            String info=good+" titoli analizzati su "+total+"; ultima seduta "+lastSession+
                "; "+newSignals+" nuovi setup su ultima candela, "+historicalSignals+" storici recuperati.";
            context.getSharedPreferences("scan",0).edit().putString("last_run",Instant.now().toString())
                .putString("last_info",info).putString("last_report",report.toString())
                .putString("last_candidates",top.toString())
                .putString("last_recovery_zones",recoveryTop.toString())
                .putString("last_event_momentum",eventTop.toString()).apply();
            store.close();
        }
    }
    private static void recordError(JSONArray errors,String ticker,String reason){
        try{errors.put(new JSONObject().put("ticker",ticker).put("reason",reason));}catch(Exception ignored){}
    }
    private static List<Bar> downloadWithRetry(String ticker)throws Exception{
        UnknownHostException last=null;
        for(int attempt=0;attempt<2;attempt++){
            try{return yahoo(ticker);}catch(UnknownHostException dns){last=dns;
                if(attempt==0)try{Thread.sleep(900);}catch(InterruptedException e){Thread.currentThread().interrupt();throw dns;}
            }
        }throw last;
    }
    private static List<Bar> yahoo(String ticker)throws Exception{
        String url="https://query1.finance.yahoo.com/v8/finance/chart/"+ticker+".MI?range=2y&interval=1d&events=div%2Csplits";
        HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(9000);conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent","SwingRadarItalia/1.1 (Android; EOD research)");
        conn.setRequestProperty("Accept","application/json");
        try{
            int code=conn.getResponseCode();if(code==403||code==429)throw new FeedBlocked(code);
            if(code!=200)throw new Exception("HTTP "+code);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=conn.getInputStream()){
                byte[] buf=new byte[8192];int n;
                while((n=in.read(buf))>0){bytes.write(buf,0,n);if(bytes.size()>2_000_000)throw new Exception("JSON troppo grande");}
            }
            JSONObject chart=new JSONObject(bytes.toString("UTF-8")).getJSONObject("chart");
            if(!chart.isNull("error"))throw new Exception(chart.getJSONObject("error").optString("description","Errore Yahoo"));
            JSONObject result=chart.getJSONArray("result").getJSONObject(0);
            JSONArray time=result.getJSONArray("timestamp");
            JSONObject quote=result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0);
            JSONArray os=quote.getJSONArray("open"),hs=quote.getJSONArray("high"),ls=quote.getJSONArray("low"),cs=quote.getJSONArray("close"),vs=quote.getJSONArray("volume");
            List<Bar> out=new ArrayList<Bar>();
            for(int i=0;i<time.length();i++){
                if(os.isNull(i)||hs.isNull(i)||ls.isNull(i)||cs.isNull(i)||vs.isNull(i))continue;
                double o=os.optDouble(i),h=hs.optDouble(i),l=ls.optDouble(i),c=cs.optDouble(i),v=vs.optDouble(i);
                if(!Double.isFinite(o)||!Double.isFinite(c)||o<=0||c<=0||v<=0||h<l)continue;
                String day=Instant.ofEpochSecond(time.getLong(i)).atZone(ROME).toLocalDate().toString();
                out.add(new Bar(day,o,h,l,c,v));
            }return out;
        }finally{conn.disconnect();}
    }
    private static JSONArray toJson(List<Bar> bars)throws Exception{JSONArray arr=new JSONArray();for(Bar b:bars)arr.put(new JSONArray().put(b.day).put(b.o).put(b.h).put(b.l).put(b.c).put(b.v));return arr;}
    private static List<Bar> barsFromJson(JSONArray arr){List<Bar> bars=new ArrayList<Bar>();for(int i=0;i<arr.length();i++){
        JSONArray a=arr.optJSONArray(i);if(a!=null&&a.length()==6)bars.add(new Bar(a.optString(0),a.optDouble(1),a.optDouble(2),a.optDouble(3),a.optDouble(4),a.optDouble(5)));
    }return bars;}
    /** Yahoo can expose an unfinished intraday candle even when interval=1d. */
    private static boolean needsCloseRefresh(JSONObject cached){
        java.time.ZonedDateTime now=java.time.ZonedDateTime.now(ROME);
        java.time.ZonedDateTime cutoff=now.toLocalDate().atTime(18,15).atZone(ROME);
        return !now.isBefore(cutoff)&&cached.optString("asof").equals(now.toLocalDate().toString())
            &&cached.optLong("fetchedAt")<cutoff.toInstant().toEpochMilli();
    }
    private static List<Bar> completedDailyBars(List<Bar> bars,long fetchedAt){
        if(bars.isEmpty())return bars;
        java.time.ZonedDateTime now=java.time.ZonedDateTime.now(ROME);
        LocalDate today=now.toLocalDate();
        if(!bars.get(bars.size()-1).day.equals(today.toString()))return bars;
        java.time.ZonedDateTime cutoff=today.atTime(LocalTime.of(18,15)).atZone(ROME);
        if(now.isBefore(cutoff)||fetchedAt<cutoff.toInstant().toEpochMilli())
            return new ArrayList<Bar>(bars.subList(0,bars.size()-1));
        return bars;
    }
    private static double mean(List<Bar> x,int from,int to){double sum=0;for(int i=from;i<to;i++)sum+=x.get(i).c;return sum/(to-from);}
    private static double medianTurnover(List<Bar> x,int from,int to){List<Double> vals=new ArrayList<Double>();for(int i=from;i<to;i++)vals.add(x.get(i).c*x.get(i).v);Collections.sort(vals);return (vals.get(9)+vals.get(10))/2;}
    private static double rsi2(List<Bar> x){double g=0,l=0;for(int i=1;i<=2;i++){double d=x.get(i).c-x.get(i-1).c;g+=Math.max(d,0);l+=Math.max(-d,0);}g/=2;l/=2;for(int i=3;i<x.size();i++){
        double d=x.get(i).c-x.get(i-1).c;g=(g+Math.max(d,0))/2;l=(l+Math.max(-d,0))/2;
    }return l==0?100:100-100/(1+g/l);}
    private static double atr14(List<Bar> x){double a=0;for(int i=1;i<=14;i++)a+=tr(x,i);a/=14;for(int i=15;i<x.size();i++)a=(a*13+tr(x,i))/14;return a;}
    private static double tr(List<Bar> x,int i){Bar b=x.get(i),p=x.get(i-1);return Math.max(b.h-b.l,Math.max(Math.abs(b.h-p.c),Math.abs(b.l-p.c)));}
    private static int age(String day){try{return (int)ChronoUnit.DAYS.between(LocalDate.parse(day),LocalDate.now(ROME));}catch(Exception e){return 999;}}
    private static double round(double n,int places){double p=Math.pow(10,places);return Math.round(n*p)/p;}

    /** Small-cap liquidity is deliberately lower than large-cap, but still screened. */
    private static double minimumTurnover(String segment){return "SMALL".equals(segment)?250_000:500_000;}

    private static JSONObject evaluate(List<Bar> bars,String ticker,String name,String segment)throws Exception{
        int n=bars.size();if(n<210)return null;Bar now=bars.get(n-1),prev=bars.get(n-2);
        double c=now.c;if(c<1||medianTurnover(bars,n-21,n-1)<minimumTurnover(segment))return null;
        double sma20=mean(bars,n-20,n),sma50=mean(bars,n-50,n),sma200=mean(bars,n-200,n);
        if(!(c>sma200&&sma50>sma200))return null;
        double hi20=0,lo20=Double.MAX_VALUE,hiPrev=0;
        for(int i=n-21;i<n-1;i++){hi20=Math.max(hi20,bars.get(i).h);lo20=Math.min(lo20,bars.get(i).l);}
        for(int i=n-22;i<n-2;i++)hiPrev=Math.max(hiPrev,bars.get(i).h);
        List<Double> volumes=new ArrayList<Double>();for(int i=n-21;i<n-1;i++)volumes.add(bars.get(i).v);Collections.sort(volumes);
        double med=(volumes.get(9)+volumes.get(10))/2,rvol=med>0?now.v/med:0;
        double position=now.h>now.l?(c-now.l)/(now.h-now.l):.5;
        double recent60=c/bars.get(n-61).c-1,pullback=1-c/hi20,rsi=rsi2(bars);
        double nearMA=Math.min(Math.abs(c/sma20-1),Math.abs(c/sma50-1));
        boolean force=(hi20-lo20)/hi20<=.22&&c>=hi20*1.005&&prev.c<=hiPrev*1.015&&rvol>=1.3&&position>=.65;
        boolean weak=recent60>.04&&pullback>=.035&&pullback<=.14&&rsi<=25&&nearMA<=.035;
        if(!force&&!weak)return null;
        double atr=atr14(bars),stop,t1,t2,score;String kind,reason;
        if(force){kind="FORZA";stop=Math.min(hi20-.25*atr,c-1.3*atr);double r=c-stop;t1=c+2*r;t2=c+3*r;
            score=51+Math.min(rvol,3)*9+position*9+Math.min(recent60,.35)*22-((hi20-lo20)/hi20)*45;
            reason="Breakout EOD sopra il massimo delle precedenti 20 sedute; volumi in aumento.";
        }else{kind="DEBOLEZZA";double low10=Double.MAX_VALUE;for(int i=n-10;i<n;i++)low10=Math.min(low10,bars.get(i).l);
            stop=Math.min(low10-.2*atr,c-1.15*atr);double r=c-stop;t1=Math.max(hi20,c+1.5*r);t2=Math.max(hi20+r,c+2.5*r);
            score=53+Math.min(25-rsi,25)*.7+Math.min(recent60,.3)*24-Math.abs(pullback-.065)*70+Math.min(1,rvol)*4;
            reason="Pullback su trend crescente: prezzo vicino a SMA20/SMA50, RSI(2) basso.";
        }
        return new JSONObject().put("ticker",ticker).put("name",name).put("market","IT").put("segment",segment)
            .put("kind",kind).put("asof",now.day).put("price",round(c,3)).put("entry",round(c,3))
            .put("stop",round(stop,3)).put("target1",round(t1,3)).put("target2",round(t2,3))
            .put("score",(int)Math.max(0,Math.min(100,Math.round(score))))
            .put("rvol",round(rvol,2)).put("rsi2",round(rsi,1)).put("sma20",round(sma20,3))
            .put("sma50",round(sma50,3)).put("sma200",round(sma200,3))
            .put("resistance",round(hi20,3)).put("riskPct",round((c-stop)/c*100,2))
            .put("dataAge",age(now.day)).put("reason",reason)
            .put("source","Yahoo Milano (feed non ufficiale)")
            .put("fundamentals","N/D — fondamentali non integrati")
            .put("validated",false);
    }

    /** A watch-only, independent contrarian setup. Never published as a broker order. */
    private static JSONObject recoveryZone(List<Bar> bars,String ticker,String name,String segment)throws Exception{
        int n=bars.size();if(n<210)return null;
        Bar today=bars.get(n-1);double c=today.c;
        if(c<1||medianTurnover(bars,n-21,n-1)<minimumTurnover(segment))return null;
        double sma20=mean(bars,n-20,n),sma50=mean(bars,n-50,n),sma200=mean(bars,n-200,n);
        double return60=c/bars.get(n-61).c-1;
        if(c>=sma200||return60>-.08)return null;
        double support=Double.MAX_VALUE;
        for(int i=n-121;i<n-1;i++)support=Math.min(support,bars.get(i).l);
        if(support<=0||c<support*.995||c>support*1.20)return null;
        double distance=c/support-1;
        double rsi=rsi2(bars),position=today.h>today.l?(c-today.l)/(today.h-today.l):.5;
        double atr=atr14(bars);
        // The protective level must also sit below the actual signal-day low;
        // otherwise a tiny intraday dip could invalidate the idea immediately.
        double stop=Math.min(support-Math.max(.25*atr,support*.015),
            today.l-Math.max(.10*atr,c*.002));
        double risk=c-stop;
        if(risk<=0||risk/c>.18)return null;
        boolean[] matched={distance<=.12,return60<=-.12,rsi<=70,position>=.45};
        String[] rules={"Prezzo oltre +12% dal supporto a 120 sedute","Discesa a 60 sedute meno pronunciata del 12%",
            "RSI(2) sopra 70","Chiusura nella metà bassa della candela"};
        int pass=0;StringBuilder missing=new StringBuilder();
        for(int i=0;i<matched.length;i++){
            if(matched[i])pass++;else{if(missing.length()>0)missing.append(" · ");missing.append(rules[i]);}
        }
        if(pass<3)return null;
        double target1=Math.max(sma20,c+1.5*risk),target2=Math.max(sma50,c+2.5*risk);
        return new JSONObject().put("ticker",ticker).put("name",name).put("segment",segment)
            .put("kind","RECUPERO").put("asof",today.day).put("price",round(c,3))
            .put("support",round(support,3)).put("zoneLow",round(support*1.015,3))
            .put("zoneHigh",round(support*1.12,3))
            .put("stop",round(stop,3)).put("target1",round(target1,3)).put("target2",round(target2,3))
            .put("riskPct",round(risk/c*100,1)).put("return60Pct",round(return60*100,1))
            .put("distSupportPct",round(distance*100,1)).put("rsi2",round(rsi,1))
            .put("sma200",round(sma200,3)).put("matched",pass).put("required",matched.length)
            .put("proximity",(int)Math.round(pass*25-distance*25))
            .put("missing",missing.length()>0?missing.toString():"Zona tecnica idonea; manca una conferma di inversione")
            .put("note","Zona controtrend di sola osservazione, non ingresso confermato")
            .put("dataAge",age(today.day));
    }

    /** Replays the recovery lifecycle candle-by-candle, without future bars. */
    private static JSONObject startRecoveryState(JSONObject zone,List<Bar> bars)throws Exception{
        Bar today=bars.get(bars.size()-1);
        double position=today.h>today.l?(today.c-today.l)/(today.h-today.l):.5;
        String phase=RecoveryPhases.initial(today.l,today.c,zone.optDouble("support"),position,zone.optDouble("rsi2"));
        return zone.put("phase",phase).put("phaseAt",today.day)
            .put("firstObservedAt",today.day).put("lastObservedAt",today.day)
            .put("lastChecked",today.day).put("relevantAt",today.day)
            .put("lastPrice",round(today.c,3))
            .put("phaseReason",RecoveryPhases.REBOUND.equals(phase)?
                "Minimo sotto il supporto, poi chiusura di nuovo sopra: possibile falso breakdown.":
                "Vicino al supporto, senza recupero o conferma tecnica misurabile.");
    }

    private static boolean shouldRestartRecovery(JSONObject previous,JSONObject candidate,
        List<Bar> bars,int sessionsElapsed){
        if(candidate==null)return false;
        if(sessionsElapsed>=18)return true;
        Bar today=bars.get(bars.size()-1);
        double position=today.h>today.l?(today.c-today.l)/(today.h-today.l):.5;
        return candidate.optDouble("support")<=previous.optDouble("support")*.99&&
            RecoveryPhases.supportReclaim(today.l,today.c,candidate.optDouble("support"),
                position,candidate.optDouble("rsi2"));
    }

    /** Existing support, stop and targets are intentionally NOT recalculated. */
    private static void updateRecoveryState(JSONObject state,JSONObject candidate,List<Bar> bars,int sessionsElapsed)throws Exception{
        int n=bars.size();Bar today=bars.get(n-1),yesterday=bars.get(n-2);
        state.put("lastChecked",today.day).put("lastPrice",round(today.c,3));
        if(candidate!=null){
            state.put("lastObservedAt",today.day).put("relevantAt",today.day);
        }
        String before=state.optString("phase",RecoveryPhases.WATCH);
        double position=today.h>today.l?(today.c-today.l)/(today.h-today.l):.5;
        double priorVolume=0;
        for(int i=n-21;i<n-1;i++)priorVolume+=bars.get(i).v;
        double averageVolume=priorVolume/20;
        double volumeRatio=averageVolume>0?today.v/averageVolume:0;
        String after=RecoveryPhases.next(before,today.l,today.c,yesterday.h,
            state.optDouble("support"),state.optDouble("stop"),position,
            volumeRatio,rsi2(bars),sessionsElapsed);
        if(after.equals(before))return;
        String reason;
        if(RecoveryPhases.INVALID.equals(after)){
            reason=today.l<=state.optDouble("stop")?
                "Minimo della seduta oltre lo stop indicativo originario.":
                "Chiusura sotto il supporto originario: zona non più valida.";
        }else if(RecoveryPhases.EXPIRED.equals(after)){
            reason="Nessuna conferma entro 12 sedute dalla prima osservazione.";
        }else if(RecoveryPhases.REBOUND.equals(after)){
            reason="Minimo sotto il supporto e chiusura sopra: possibile falso breakdown.";
        }else{
            reason="Chiusura oltre il massimo della seduta precedente, con volume e posizione di candela sufficienti.";
            state.put("confirmationPrice",round(today.c,3));
        }
        state.put("phase",after).put("phaseAt",today.day)
            .put("relevantAt",today.day).put("phaseReason",reason);
    }

    /** Independent acceleration watch. Not an entry signal and does not require a bullish SMA50/200 crossover. */
    private static JSONObject eventMomentum(List<Bar> bars,String ticker,String name,String segment)throws Exception{
        int n=bars.size();if(n<210)return null;
        Bar now=bars.get(n-1),prev=bars.get(n-2);double c=now.c;
        double turnover=medianTurnover(bars,n-21,n-1);
        if(c<1||turnover<minimumTurnover(segment))return null;
        List<Double> vols=new ArrayList<Double>();
        for(int i=n-21;i<n-1;i++)vols.add(bars.get(i).v);
        Collections.sort(vols);
        double medVolume=(vols.get(9)+vols.get(10))/2;
        double rvol=medVolume>0?now.v/medVolume:0;
        double dayPct=(c/prev.c-1)*100,twoDayPct=(c/bars.get(n-3).c-1)*100;
        double position=now.h>now.l?(c-now.l)/(now.h-now.l):.5;
        // 26 Oct MAIRE: +5.38% day; 27 Oct: +3.91%, 2.46M shares.
        // These thresholds define a research watch, NOT a verified profitable strategy.
        if(!(dayPct>=3.5||(dayPct>=1.5&&twoDayPct>=6))||rvol<1.15||position<.60)return null;
        double sma50=mean(bars,n-50,n),sma200=mean(bars,n-200,n);
        return new JSONObject().put("ticker",ticker).put("name",name).put("segment",segment)
            .put("kind","EVENT_MOMENTUM").put("asof",now.day).put("price",round(c,3))
            .put("dayPct",round(dayPct,2)).put("twoDayPct",round(twoDayPct,2))
            .put("rvol",round(rvol,2)).put("position",round(position,2))
            .put("sma50Above200",sma50>sma200).put("dataAge",age(now.day))
            .put("reason","Accelerazione EOD con volumi e chiusura forte; catalizzatore da verificare. Non costituisce ingresso.");
    }

    private static JSONObject diagnose(List<Bar> bars,String ticker,String name,String segment,
        JSONObject signal,JSONObject recovery,JSONObject eventMover)throws Exception{
        int n=bars.size();Bar now=bars.get(n-1);double c=now.c;
        double sma50=mean(bars,n-50,n),sma200=mean(bars,n-200,n);
        double turnover=medianTurnover(bars,n-21,n-1);
        boolean volume=turnover>=minimumTurnover(segment),trend=c>sma200&&sma50>sma200;
        String outcome;
        if(signal!=null)outcome="SETUP_"+signal.optString("kind");
        else if(eventMover!=null&&eventMover.optString("asof").equals(now.day))outcome="MOMENTUM_DA_VERIFICARE";
        else if(recovery!=null&&recovery.optString("asof").equals(now.day))outcome="ZONA_RECUPERO";
        else if(eventMover!=null)outcome="MOMENTUM_STORICO";
        else if(recovery!=null)outcome="ZONA_RECUPERO_STORICA";
        else outcome="NESSUN_SETUP";
        JSONArray causes=new JSONArray();
        if(!volume)causes.put("Turnover mediano 20 sedute sotto "+(int)minimumTurnover(segment)+" €/giorno");
        if(c<=sma200)causes.put("Chiusura sotto SMA200");
        if(sma50<=sma200)causes.put("SMA50 non sopra SMA200 (blocca forza e debolezza)");
        if(!trend&&eventMover!=null)causes.put("La watch di accelerazione è indipendente dal trend lungo");
        if(trend&&signal==null)causes.put("Manca almeno una conferma specifica di forza/debolezza");
        if(recovery==null)causes.put("Nessuna zona controtrend valida nella seduta corrente");
        return new JSONObject().put("ticker",ticker).put("name",name).put("segment",segment)
            .put("status",outcome).put("asof",now.day).put("price",round(c,3))
            .put("sma50",round(sma50,3)).put("sma200",round(sma200,3))
            .put("turnover",Math.round(turnover)).put("turnoverRequired",(int)minimumTurnover(segment))
            .put("hasEventMomentum",eventMover!=null)
            .put("lastEventDay",eventMover!=null?eventMover.optString("asof"):"")
            .put("hasRecoveryZone",recovery!=null)
            .put("reason",causes.length()>0?causes.join(" · "):"Tutte le condizioni applicabili rispettate");
    }

    /** Revalue historical paper setup states without claiming any real trade was executed. */
    private static void reconcileSignals(RadarStore store,String ticker,List<Bar> bars){
        JSONArray signals=store.signalsForTicker(ticker);
        for(int k=0;k<signals.length();k++)try{
            JSONObject signal=signals.getJSONObject(k);String asof=signal.optString("asof");
            int start=-1;for(int i=0;i<bars.size();i++)if(bars.get(i).day.equals(asof)){start=i;break;}
            if(start<0)continue;
            double stop=signal.optDouble("stop",Double.NaN),target1=signal.optDouble("target1",Double.NaN);
            double target2=signal.optDouble("target2",Double.NaN);
            if(!Double.isFinite(stop)||!Double.isFinite(target1)||!Double.isFinite(target2))continue;
            String state="ATTIVO",at=bars.get(bars.size()-1).day;
            double price=bars.get(bars.size()-1).c;
            for(int i=start+1;i<bars.size();i++){
                Bar bar=bars.get(i);price=bar.c;at=bar.day;
                // Conservative assumption if both stop and target were touched in one daily candle.
                if(bar.l<=stop){state="STOP_TOUCHED";break;}
                if(bar.h>=target2){state="TARGET2_TOUCHED";break;}
                if(bar.h>=target1){state="TARGET1_TOUCHED";break;}
                if(i-start>=20){state="SCADUTO";break;}
            }
            store.updateSignalState(signal,state,at,round(price,3));
        }catch(Exception ignored){}
    }

    /** Independent watch candidates by direction. Each reports precisely which rules are missing. */
    private static JSONArray watch(List<Bar> bars,String ticker,String name,String segment)throws Exception{
        JSONArray result=new JSONArray();
        int n=bars.size();Bar now=bars.get(n-1);double c=now.c;
        if(c<1||medianTurnover(bars,n-21,n-1)<minimumTurnover(segment))return result;
        double sma20=mean(bars,n-20,n),sma50=mean(bars,n-50,n),sma200=mean(bars,n-200,n);
        if(c<=sma200||sma50<=sma200)return result;
        double hi=0,lo=Double.MAX_VALUE,hiPrev=0;
        for(int i=n-21;i<n-1;i++){hi=Math.max(hi,bars.get(i).h);lo=Math.min(lo,bars.get(i).l);}
        for(int i=n-22;i<n-2;i++)hiPrev=Math.max(hiPrev,bars.get(i).h);
        List<Double> volumes=new ArrayList<Double>();for(int i=n-21;i<n-1;i++)volumes.add(bars.get(i).v);Collections.sort(volumes);
        double rvol=now.v/((volumes.get(9)+volumes.get(10))/2);
        double pb=1-c/hi,rsi=rsi2(bars),near=Math.min(Math.abs(c/sma20-1),Math.abs(c/sma50-1));
        double recent=c/bars.get(n-61).c-1;
        double position=now.h>now.l?(c-now.l)/(now.h-now.l):.5;
        double depth=(hi-lo)/hi;
        boolean nearBreakout=pb>=-.04&&pb<=.10;
        if(nearBreakout){
            boolean[] pass={c>=hi*1.005,depth<=.22,rvol>=1.3,position>=.65,bars.get(n-2).c<=hiPrev*1.015};
            String[] fail={"Chiusura sotto breakout (+0,5%)","Base troppo profonda (>22%)","Volume sotto 1,3×","Candela chiusa sotto 65% del range","Breakout già esteso ieri"};
            result.put(candidate(ticker,name,segment,"FORZA",now,hi,pb,rsi,rvol,pass,fail));
        }
        if(pb>=.015&&pb<=.20&&rsi<=75&&recent>-.02){
            boolean[] pass={recent>.04,pb>=.035&&pb<=.14,rsi<=25,near<=.035};
            String[] fail={"Forza a 60 sedute sotto +4%","Pullback fuori dall'intervallo 3,5–14%","RSI(2) sopra 25","Prezzo distante da SMA20/50 più del 3,5%"};
            result.put(candidate(ticker,name,segment,"DEBOLEZZA",now,hi,pb,rsi,rvol,pass,fail));
        }
        return result;
    }
    private static JSONObject candidate(String ticker,String name,String segment,String kind,Bar now,double hi,double pb,double rsi,
        double rvol,boolean[] passed,String[] failures)throws Exception{
        int count=0;StringBuilder missing=new StringBuilder();
        for(int i=0;i<passed.length;i++){
            if(passed[i])count++;
            else{if(missing.length()>0)missing.append(" · ");missing.append(failures[i]);}
        }
        return new JSONObject().put("ticker",ticker).put("name",name).put("kind",kind).put("segment",segment)
            .put("asof",now.day).put("price",round(now.c,3)).put("resistance",round(hi,3))
            .put("rvol",round(rvol,2)).put("rsi2",round(rsi,1)).put("pullbackPct",round(pb*100,1))
            .put("proximity",(int)Math.round(100.0*count/passed.length))
            .put("matched",count).put("required",passed.length)
            .put("missing",missing.length()==0?"Verifica manuale richiesta":missing.toString());
    }
    private static void notifyNew(Context context,JSONObject s){
        NotificationManager m=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channel="swing-radar-v11";
        if(Build.VERSION.SDK_INT>=26)m.createNotificationChannel(new NotificationChannel(channel,"Segnali swing Milano",NotificationManager.IMPORTANCE_DEFAULT));
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        PendingIntent open=PendingIntent.getActivity(context,0,new Intent(context,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notice=new Notification.Builder(context,channel).setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(s.optString("ticker")+" · "+s.optString("kind"))
            .setContentText("Ranking "+s.optInt("score")+" · chiusura €"+s.optDouble("price"))
            .setContentIntent(open).setAutoCancel(true).build();
        m.notify((s.optString("ticker")+s.optString("asof")).hashCode(),notice);
    }
    private static void notifyUpcomingEarnings(Context context,JSONObject z,int days){
        String ticker=z.optString("ticker"),date=EarningsCalendar.nextDate(context,ticker);
        if(date.isEmpty())return;
        String key=ticker+"_"+date;
        android.content.SharedPreferences p=context.getSharedPreferences("earnings_reminders_v15",0);
        if(p.getBoolean(key,false))return;
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        NotificationManager m=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channel="swing-radar-events-v15";
        if(Build.VERSION.SDK_INT>=26)m.createNotificationChannel(new NotificationChannel(channel,"Zone e trimestrali · ricerca",NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open=PendingIntent.getActivity(context,0,new Intent(context,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notice=new Notification.Builder(context,channel).setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(ticker+" · zona debolezza + trimestrale")
            .setContentText("Risultati "+(days==0?"oggi":days==1?"domani":"tra "+days+" giorni")+
                " (data da verificare). SOLO watch, non acquisto.")
            .setContentIntent(open).setAutoCancel(true).build();
        m.notify(key.hashCode(),notice);
        p.edit().putBoolean(key,true).apply();
    }
    private static void notifyEventWatch(Context context,JSONObject watch,int days){
        String ticker=watch.optString("ticker"),day=watch.optString("asof");
        String key="EVENT_"+ticker+"_"+day;
        android.content.SharedPreferences p=context.getSharedPreferences("event_reminders_v16",0);
        if(p.getBoolean(key,false))return;
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        NotificationManager m=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channel="swing-radar-watch-v16";
        if(Build.VERSION.SDK_INT>=26)m.createNotificationChannel(new NotificationChannel(channel,"Momentum e trimestrali · watch",NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open=PendingIntent.getActivity(context,0,new Intent(context,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notice=new Notification.Builder(context,channel).setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(ticker+" · accelerazione prima dei risultati")
            .setContentText("Vol. "+watch.optDouble("rvol")+"×, "+watch.optDouble("dayPct")+"% giornata. Trimestrale "
                +(days==0?"oggi":days==1?"domani":"tra "+days+" giorni")+" (data da verificare). SOLO watch.")
            .setContentIntent(open).setAutoCancel(true).build();
        m.notify(key.hashCode(),notice);p.edit().putBoolean(key,true).apply();
    }
}
