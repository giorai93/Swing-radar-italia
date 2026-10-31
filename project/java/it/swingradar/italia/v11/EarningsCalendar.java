package it.swingradar.italia.v11;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Informational earnings dates; these are often estimated, NOT confirmation of a release. */
final class EarningsCalendar {
    private static final long TWELVE_HOURS=12L*60L*60L*1000L;
    private static final String PREFS="earnings_v15";
    private static final ZoneId ROME=ZoneId.of("Europe/Rome");
    private static final String[] FIELDS={
        "earnings_release_next_date","earnings_release_date",
        "earnings_per_share_forecast_next_fq","revenue_forecast_next_fq"
    };
    private EarningsCalendar(){}
    static void refresh(Context context,List<String[]> universe){
        SharedPreferences p=context.getSharedPreferences(PREFS,0);
        long checked=p.getLong("checkedAt",0);
        long delay=p.getString("lastError","").isEmpty()?TWELVE_HOURS:20L*60L*1000L;
        if(checked>0&&System.currentTimeMillis()-checked<delay)return;
        try{
            JSONObject v=request(universe);
            if(v.length()==0)throw new Exception("Nessuna data pubblicata dal provider");
            p.edit().putString("values",v.toString()).putString("lastError","")
                .putLong("checkedAt",System.currentTimeMillis()).putString("updatedAt",Instant.now().toString()).apply();
        }catch(Exception error){
            p.edit().putString("lastError",error.getClass().getSimpleName()+": "+error.getMessage())
                .putLong("checkedAt",System.currentTimeMillis()).apply();
        }
    }
    private static JSONObject request(List<String[]> universe)throws Exception{
        JSONArray tickers=new JSONArray();for(String[] x:universe)tickers.put("MIL:"+x[0]);
        JSONArray columns=new JSONArray();columns.put("name");for(String x:FIELDS)columns.put(x);
        JSONObject payload=new JSONObject().put("symbols",new JSONObject().put("tickers",tickers)).put("columns",columns);
        HttpURLConnection c=(HttpURLConnection)new URL("https://scanner.tradingview.com/italy/scan").openConnection();
        try{
            c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(11000);c.setReadTimeout(20000);
            c.setRequestProperty("Content-Type","application/json; charset=utf-8");
            c.setRequestProperty("Accept","application/json");
            c.setRequestProperty("User-Agent","Mozilla/5.0 (Android; private equity research)");
            try(OutputStream stream=c.getOutputStream()){stream.write(payload.toString().getBytes(StandardCharsets.UTF_8));}
            int http=c.getResponseCode();if(http!=200)throw new Exception("HTTP "+http);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()){
                byte[] chunk=new byte[8192];int n;
                while((n=in.read(chunk))!=-1){bytes.write(chunk,0,n);if(bytes.size()>600_000)throw new Exception("Risposta troppo grande");}
            }
            JSONArray data=new JSONObject(bytes.toString("UTF-8")).optJSONArray("data");
            if(data==null)throw new Exception("Risposta senza dati");
            JSONObject results=new JSONObject();
            for(int i=0;i<data.length();i++){
                JSONObject row=data.optJSONObject(i);if(row==null)continue;
                String symbol=row.optString("s","");if(!symbol.startsWith("MIL:"))continue;
                JSONArray values=row.optJSONArray("d");if(values==null||values.length()<5)continue;
                JSONObject report=new JSONObject();
                if(!values.isNull(1)&&values.optLong(1)>1_000_000_000L){
                    report.put("nextDate",asRomeDay(values.optLong(1)));
                }
                if(!values.isNull(2)&&values.optLong(2)>1_000_000_000L){
                    report.put("lastDate",asRomeDay(values.optLong(2)));
                }
                if(!values.isNull(3)){
                    double eps=values.optDouble(3,Double.NaN);
                    if(Double.isFinite(eps))report.put("epsForecast",eps);
                }
                if(!values.isNull(4)){
                    double revenue=values.optDouble(4,Double.NaN);
                    if(Double.isFinite(revenue))report.put("revenueForecast",revenue);
                }
                if(report.has("nextDate")||report.has("lastDate"))results.put(symbol.substring(4),report);
            }
            return results;
        }finally{c.disconnect();}
    }
    private static String asRomeDay(long seconds){return Instant.ofEpochSecond(seconds).atZone(ROME).toLocalDate().toString();}
    /** Negative when no reliable upcoming date was returned. */
    static int daysUntil(Context context,String ticker){
        try{
            SharedPreferences p=context.getSharedPreferences(PREFS,0);
            JSONObject value=new JSONObject(p.getString("values","{}")).optJSONObject(ticker);
            if(value==null)return -1;
            String date=value.optString("nextDate","");
            long days=ChronoUnit.DAYS.between(LocalDate.now(ROME),LocalDate.parse(date));
            return days>=0&&days<=14?(int)days:-1;
        }catch(Exception error){return -1;}
    }
    static String nextDate(Context context,String ticker){
        try{
            JSONObject o=new JSONObject(context.getSharedPreferences(PREFS,0).getString("values","{}"))
                .optJSONObject(ticker);
            return o==null?"":o.optString("nextDate","");
        }catch(Exception error){return "";}
    }
    static JSONObject status(Context context){
        SharedPreferences p=context.getSharedPreferences(PREFS,0);
        JSONObject status=new JSONObject();
        try{
            JSONObject values=new JSONObject(p.getString("values","{}"));
            JSONArray upcoming=new JSONArray(),recent=new JSONArray();
            LocalDate today=LocalDate.now(ROME);
            JSONArray names=values.names();
            if(names!=null)for(int i=0;i<names.length();i++){
                String ticker=names.optString(i);
                JSONObject metric=values.optJSONObject(ticker);if(metric==null)continue;
                String next=metric.optString("nextDate",""),last=metric.optString("lastDate","");
                try{
                    long delta=ChronoUnit.DAYS.between(today,LocalDate.parse(next));
                    if(delta>=0&&delta<=14)upcoming.put(new JSONObject(metric.toString()).put("ticker",ticker).put("daysAway",delta));
                }catch(Exception ignored){}
                try{
                    long delta=ChronoUnit.DAYS.between(LocalDate.parse(last),today);
                    if(delta>=0&&delta<=5)recent.put(new JSONObject(metric.toString()).put("ticker",ticker).put("daysAgo",delta));
                }catch(Exception ignored){}
            }
            status.put("values",values).put("upcoming",upcoming).put("recent",recent)
                .put("updatedAt",p.getString("updatedAt",""))
                .put("lastError",p.getString("lastError",""))
                .put("count",values.length());
        }catch(Exception ignored){}
        return status;
    }
}
