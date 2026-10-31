package it.swingradar.italia.v11;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Daily discovery of Italian equities. Local CSV always remains a fallback. */
final class ItalianUniverse {
    private static final long CACHE_AGE=24L*60L*60L*1000L;
    private static final String PREFS="universe_v14";
    private ItalianUniverse(){}

    static List<String[]> discover(Context context){
        SharedPreferences p=context.getSharedPreferences(PREFS,0);
        LinkedHashMap<String,String[]> all=base(context);
        JSONArray dynamic=null;
        long now=System.currentTimeMillis();
        try{
            long last=p.getLong("last_success",0);
            if(now-last<CACHE_AGE)dynamic=new JSONArray(p.getString("symbols","[]"));
            else{
                dynamic=request();
                if(dynamic.length()==0)throw new Exception("Nessun titolo liquido dal listino dinamico");
                p.edit().putString("symbols",dynamic.toString()).putLong("last_success",now)
                    .putString("last_error","").putString("source","Screener Milano").apply();
            }
        }catch(Exception ex){
            p.edit().putString("last_error",ex.getClass().getSimpleName()+": "+ex.getMessage()).apply();
            // Retain last successful discovery; never treat an outage as 0 available stocks.
            try{dynamic=new JSONArray(p.getString("symbols","[]"));}catch(Exception ignored){}
        }
        if(dynamic!=null){
            for(int i=0;i<dynamic.length();i++){
                JSONArray a=dynamic.optJSONArray(i);if(a==null||a.length()<3)continue;
                String ticker=a.optString(0),name=a.optString(1),segment=a.optString(2);
                if(!ticker.matches("[A-Z0-9]{1,12}")||!validSegment(segment))continue;
                String[] existing=all.get(ticker);
                if(existing!=null){existing[1]=name;existing[2]=segment;}
                else all.put(ticker,new String[]{ticker,name,segment});
            }
        }
        int large=0,mid=0,small=0;
        for(String[] x:all.values()){
            if("LARGE".equals(x[2]))large++;else if("MID".equals(x[2]))mid++;
            else if("SMALL".equals(x[2]))small++;
        }
        p.edit().putInt("added",Math.max(0,all.size()-84)).putInt("total",all.size())
            .putInt("large",large).putInt("mid",mid).putInt("small",small).apply();
        return new ArrayList<String[]>(all.values());
    }

    private static boolean validSegment(String value){
        return "LARGE".equals(value)||"MID".equals(value)||"SMALL".equals(value);
    }

    private static LinkedHashMap<String,String[]> base(Context context){
        LinkedHashMap<String,String[]> result=new LinkedHashMap<String,String[]>();
        loadAsset(context,"italian_stocks.csv",result);
        // Frozen list of liquid Milano stocks from 29 October 2026; works if discovery is blocked.
        loadAsset(context,"italian_expanded.csv",result);
        return result;
    }
    private static void loadAsset(Context context,String asset,LinkedHashMap<String,String[]> result){
        try(BufferedReader r=new BufferedReader(new InputStreamReader(context.getAssets().open(asset),StandardCharsets.UTF_8))){
            String line;boolean header=true;
            while((line=r.readLine())!=null){
                if(header){header=false;continue;}
                String[] x=line.split(",",3);
                if(x.length>=2&&x[0].matches("[A-Z0-9]{1,12}")){
                    String tier=x.length==3&&validSegment(x[2])?x[2]:"BASE";
                    result.put(x[0],new String[]{x[0],x[1],tier});
                }
            }
        }catch(Exception ignored){}
    }

    /** Unofficial TradingView screener. Cap tiers are indicative, NOT FTSE index membership. */
    private static JSONArray request()throws Exception{
        JSONObject body=new JSONObject();JSONArray filters=new JSONArray();
        filters.put(new JSONObject().put("left","type").put("operation","equal").put("right","stock"));
        filters.put(new JSONObject().put("left","exchange").put("operation","equal").put("right","MIL"));
        body.put("filter",filters).put("columns",new JSONArray()
            .put("name").put("description").put("close").put("market_cap_basic").put("average_volume_10d_calc"))
            .put("range",new JSONArray().put(0).put(750));
        HttpURLConnection conn=(HttpURLConnection)new URL("https://scanner.tradingview.com/italy/scan").openConnection();
        try{
            conn.setConnectTimeout(11000);conn.setReadTimeout(23000);
            conn.setRequestMethod("POST");conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type","application/json; charset=utf-8");
            conn.setRequestProperty("Accept","application/json");
            conn.setRequestProperty("User-Agent","Mozilla/5.0 (Android; private equity research)");
            try(OutputStream out=conn.getOutputStream()){
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int http=conn.getResponseCode();if(http!=200)throw new Exception("HTTP "+http);
            ByteArrayOutputStream buf=new ByteArrayOutputStream();
            try(InputStream in=conn.getInputStream()){
                byte[] chunk=new byte[8192];int size;
                while((size=in.read(chunk))!=-1){buf.write(chunk,0,size);if(buf.size()>2_000_000)throw new Exception("Elenco troppo grande");}
            }
            JSONObject response=new JSONObject(buf.toString("UTF-8"));
            JSONArray data=response.optJSONArray("data");
            if(data==null)throw new Exception("Risposta non valida");
            JSONArray out=new JSONArray();
            for(int i=0;i<data.length();i++){
                JSONObject row=data.optJSONObject(i);if(row==null)continue;
                String symbol=row.optString("s","");if(!symbol.startsWith("MIL:"))continue;
                String ticker=symbol.substring(4);if(!ticker.matches("[A-Z0-9]{1,12}"))continue;
                JSONArray v=row.optJSONArray("d");if(v==null||v.length()<5||v.isNull(2)||v.isNull(3)||v.isNull(4))continue;
                double close=v.optDouble(2),marketCap=v.optDouble(3),averageVolume=v.optDouble(4);
                if(!Double.isFinite(close)||!Double.isFinite(marketCap)||!Double.isFinite(averageVolume)
                    ||close<1||marketCap<100_000_000||averageVolume<=0)continue;
                double dailyTurnover=close*averageVolume;
                String segment;double minTurnover;
                if(marketCap>=10_000_000_000L){segment="LARGE";minTurnover=500_000;}
                else if(marketCap>=2_000_000_000L){segment="MID";minTurnover=500_000;}
                else{segment="SMALL";minTurnover=250_000;}
                if(dailyTurnover<minTurnover)continue;
                out.put(new JSONArray().put(ticker).put(v.optString(1,ticker)).put(segment));
            }
            return out;
        }finally{conn.disconnect();}
    }

    static JSONObject status(Context context){
        SharedPreferences p=context.getSharedPreferences(PREFS,0);
        JSONObject o=new JSONObject();
        try{
            o.put("source",p.getString("source","Lista locale"))
                .put("total",p.getInt("total",0)).put("added",p.getInt("added",0))
                .put("large",p.getInt("large",0)).put("mid",p.getInt("mid",0))
                .put("small",p.getInt("small",0))
                .put("lastSuccess",p.getLong("last_success",0)>0?
                    Instant.ofEpochMilli(p.getLong("last_success",0)).toString():"")
                .put("lastError",p.getString("last_error",""));
        }catch(Exception ignored){}
        return o;
    }
}
