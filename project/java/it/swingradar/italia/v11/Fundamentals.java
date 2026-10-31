package it.swingradar.italia.v11;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/** Only audited ESEF-derived data; no synthetic fallback to vendor metrics. */
final class Fundamentals {
    private Fundamentals() {}
    private static String read(InputStream in) throws Exception {
        try (InputStream input=in; ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[8192];int n;
            while((n=input.read(b))!=-1){out.write(b,0,n);if(out.size()>2000000)throw new Exception("Dataset eccessivo");}
            return out.toString("UTF-8");
        }
    }
    private static JSONObject validate(String raw) throws Exception {
        JSONObject data=new JSONObject(raw);
        if(data.optInt("schemaVersion")!=1 || data.optJSONObject("values")==null)throw new Exception("Schema ESEF non valido");
        return data;
    }
    static void refresh(Context context,List<String[]> universe) {
        SharedPreferences p=context.getSharedPreferences("fundamentals_esef_v1",0);
        if(System.currentTimeMillis()-p.getLong("checked",0)<12L*3600000)return;
        HttpURLConnection c=null;
        try {
            c=(HttpURLConnection)new URL("https://raw.githubusercontent.com/giorai93/Swing-radar-italia/main/project/assets/fundamentals.json").openConnection();
            c.setConnectTimeout(10000);c.setReadTimeout(15000);
            if(c.getResponseCode()!=200)throw new Exception("HTTP "+c.getResponseCode());
            JSONObject data=validate(read(c.getInputStream()));
            if(data.getJSONObject("values").length()==0)throw new Exception("Dataset vuoto");
            p.edit().putString("data",data.toString()).putString("error","").apply();
        }catch(Exception e){p.edit().putString("error",e.toString()).apply();}
        finally {if(c!=null)c.disconnect();p.edit().putLong("checked",System.currentTimeMillis()).apply();}
    }
    static JSONObject status(Context context) {
        JSONObject state=new JSONObject();
        SharedPreferences p=context.getSharedPreferences("fundamentals_esef_v1",0);
        try {
            JSONObject bundled=validate(read(context.getAssets().open("fundamentals.json")));
            JSONObject data=p.contains("data")?validate(p.getString("data","{}")):bundled;
            if(bundled.optString("updatedAt").compareTo(data.optString("updatedAt"))>0)data=bundled;
            state.put("values",data.getJSONObject("values")).put("count",data.getJSONObject("values").length())
                .put("updatedAt",data.optString("updatedAt")).put("errors",data.optJSONObject("errors"))
                .put("lastError",p.getString("error",""));
        }catch(Exception e){try{state.put("lastError",e.toString());}catch(Exception ignored){}}
        return state;
    }
}
