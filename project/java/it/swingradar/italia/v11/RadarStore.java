package it.swingradar.italia.v11;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

final class RadarStore extends SQLiteOpenHelper {
    RadarStore(Context c){super(c.getApplicationContext(),"swing_radar_italia_v11.db",null,1);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE quotes(ticker TEXT PRIMARY KEY,asof TEXT,fetched_at INTEGER,payload TEXT NOT NULL)");
        db.execSQL("CREATE TABLE signals(id INTEGER PRIMARY KEY AUTOINCREMENT,ticker TEXT,kind TEXT,asof TEXT,payload TEXT,UNIQUE(ticker,kind,asof))");
        db.execSQL("CREATE TABLE trades(id INTEGER PRIMARY KEY AUTOINCREMENT,created_at INTEGER,ticker TEXT,kind TEXT,entry REAL,quantity REAL,exit REAL,fees REAL,note TEXT)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int from,int to){}
    synchronized JSONObject quote(String ticker){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT asof,fetched_at,payload FROM quotes WHERE ticker=?",new String[]{ticker})){
            if(c.moveToFirst())return new JSONObject().put("asof",c.getString(0)).put("fetchedAt",c.getLong(1)).put("payload",c.getString(2));
        }catch(Exception ignored){}return null;
    }
    synchronized void saveQuote(String ticker,String asof,String payload){
        getWritableDatabase().execSQL("INSERT OR REPLACE INTO quotes VALUES(?,?,?,?)",new Object[]{ticker,asof,System.currentTimeMillis(),payload});
    }
    synchronized boolean signal(JSONObject x)throws Exception{
        SQLiteDatabase db=getWritableDatabase();db.execSQL("INSERT OR IGNORE INTO signals(ticker,kind,asof,payload) VALUES(?,?,?,?)",
            new Object[]{x.getString("ticker"),x.getString("kind"),x.getString("asof"),x.toString()});
        try(Cursor c=db.rawQuery("SELECT changes()",null)){return c.moveToFirst()&&c.getInt(0)>0;}
    }
    synchronized JSONArray signals(){
        JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM signals ORDER BY asof DESC,id DESC LIMIT 500",null)){
            while(c.moveToNext())try{a.put(new JSONObject(c.getString(0)));}catch(Exception ignored){}
        }return a;
    }
    synchronized JSONArray signalsForTicker(String ticker){
        JSONArray a=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM signals WHERE ticker=? ORDER BY asof DESC LIMIT 60",new String[]{ticker})){
            while(c.moveToNext())try{a.put(new JSONObject(c.getString(0)));}catch(Exception ignored){}
        }return a;
    }
    /** Suppress repeat alerts, not records: keep raw observations for the audit. */
    synchronized boolean hasRecentOngoingSignal(String ticker,String kind,String asof){
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT asof,payload FROM signals WHERE ticker=? AND kind=? AND asof<? ORDER BY asof DESC LIMIT 15",
            new String[]{ticker,kind,asof})){
            LocalDate current=LocalDate.parse(asof);
            while(c.moveToNext()){
                LocalDate previous=LocalDate.parse(c.getString(0));
                long gap=ChronoUnit.DAYS.between(previous,current);
                if(gap>7)break;
                if(gap<0)continue;
                JSONObject old=new JSONObject(c.getString(1));
                String state=old.optString("lifecycle","ATTIVO");
                String stateAt=old.optString("stateAt","");
                boolean ended=!state.equals("ATTIVO")&&!stateAt.isEmpty()&&stateAt.compareTo(asof)<=0;
                if(!ended)return true;
            }
        }catch(Exception ignored){}
        return false;
    }
    synchronized void updateSignalState(JSONObject signal,String state,String stateAt,double lastPrice)throws Exception{
        if(state.equals(signal.optString("lifecycle"))&&stateAt.equals(signal.optString("stateAt")))return;
        signal.put("lifecycle",state).put("stateAt",stateAt).put("lastPrice",lastPrice);
        getWritableDatabase().execSQL("UPDATE signals SET payload=? WHERE ticker=? AND kind=? AND asof=?",
            new Object[]{signal.toString(),signal.getString("ticker"),signal.getString("kind"),signal.getString("asof")});
    }
    synchronized int cached(){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM quotes",null)){return c.moveToFirst()?c.getInt(0):0;}}
    synchronized void saveTrade(JSONObject x)throws Exception{
        String ticker=x.optString("ticker","").toUpperCase().trim();
        double entry=x.optDouble("entry",-1),qty=x.optDouble("quantity",-1),fees=x.optDouble("fees",0);
        if(!ticker.matches("[A-Z0-9]{1,10}")||!Double.isFinite(entry)||entry<=0||!Double.isFinite(qty)||qty<=0||fees<0||!Double.isFinite(fees))
            throw new Exception("Ticker, quantità, prezzo o commissioni non validi");
        String note=x.optString("note","");if(note.length()>500)note=note.substring(0,500);
        getWritableDatabase().execSQL("INSERT INTO trades(created_at,ticker,kind,entry,quantity,exit,fees,note) VALUES(?,?,?,?,?,?,?,?)",
            new Object[]{System.currentTimeMillis(),ticker,x.optString("kind","MANUALE"),entry,qty,null,fees,note});
    }
    synchronized void closeTrade(int id,double exit,double fees)throws Exception{
        if(id<1||!Double.isFinite(exit)||exit<=0||!Double.isFinite(fees)||fees<0)throw new Exception("Prezzo o commissioni non validi");
        getWritableDatabase().execSQL("UPDATE trades SET exit=?,fees=fees+? WHERE id=? AND exit IS NULL",new Object[]{exit,fees,id});
    }
    synchronized JSONArray trades(){
        JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,ticker,kind,entry,quantity,exit,fees,note,created_at FROM trades ORDER BY id DESC LIMIT 500",null)){
            while(c.moveToNext())try{
                JSONObject t=new JSONObject().put("id",c.getInt(0)).put("ticker",c.getString(1)).put("kind",c.getString(2))
                    .put("entry",c.getDouble(3)).put("quantity",c.getDouble(4)).put("fees",c.getDouble(6))
                    .put("note",c.getString(7)).put("createdAt",c.getLong(8));
                if(c.isNull(5))t.put("exit",JSONObject.NULL).put("pnl",JSONObject.NULL);
                else t.put("exit",c.getDouble(5)).put("pnl",Math.round(((c.getDouble(5)-c.getDouble(3))*c.getDouble(4)-c.getDouble(6))*100.0)/100.0);
                a.put(t);
            }catch(Exception ignored){}
        }return a;
    }
}
