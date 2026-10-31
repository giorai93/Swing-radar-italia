package it.swingradar.italia.v11;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;
import org.json.JSONArray;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class MainActivity extends Activity{
    private WebView web;
    private static final String HOME="file:///android_asset/index.html";
    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff071e34);getWindow().setNavigationBarColor(0xfff2f5fa);
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        web=new WebView(this);web.setBackgroundColor(0xfff2f5fa);
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(false);
        s.setAllowFileAccess(true);s.setAllowContentAccess(false);s.setJavaScriptCanOpenWindowsAutomatically(false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                Uri uri=request.getUrl();return !HOME.equals(uri.toString());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,String url){return !HOME.equals(url);}
        });
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(),"Android");setContentView(web);web.loadUrl(HOME);
        schedule();
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},102);
        // Trigger a fresh uncached scan on an engine update without losing local data.
        if(getPreferences(0).getInt("installed_engine_version",0)<21){
            getPreferences(0).edit().putInt("installed_engine_version",21)
                .putBoolean("first_run",false).apply();RadarScanner.start(this,null,true);
        }
    }
    @Override protected void onResume(){
        super.onResume();
        RadarScanner.refreshIfDue(this);
    }
    private void schedule(){
        JobScheduler jobs=(JobScheduler)getSystemService(JOB_SCHEDULER_SERVICE);
        JobInfo job=new JobInfo.Builder(4106,new ComponentName(this,ScanJobService.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
            .setPeriodic(24*60*60*1000L,2*60*60*1000L).build();
        jobs.schedule(job);
    }
    @Override public void onBackPressed(){
        new AlertDialog.Builder(this).setTitle("Swing Radar Italia 2.1")
          .setMessage("La scansione giornaliera rimane programmata. Android può ritardarla per risparmio energetico.")
          .setPositiveButton("Chiudi",new DialogInterface.OnClickListener(){
             @Override public void onClick(DialogInterface d,int which){MainActivity.super.onBackPressed();}
          }).setNegativeButton("Resta",null).show();
    }
    @Override public void onDestroy(){if(web!=null){web.removeJavascriptInterface("Android");web.destroy();}super.onDestroy();}
    private final class Bridge{
        @JavascriptInterface public String getSnapshot(){
            RadarStore db=new RadarStore(getApplicationContext());
            try{return new JSONObject().put("status",RadarScanner.status(getApplicationContext()))
                .put("researchSignals",db.researchSignals()).put("backtestSignals",db.backtestSignals()).put("signals",db.signals()).put("trades",db.trades()).put("cachedStocks",db.cached()).toString();}
            catch(Exception e){return "{\"error\":\"Lettura dati non riuscita\"}";}finally{db.close();}
        }
        @JavascriptInterface public String startScan(){return RadarScanner.start(getApplicationContext(),null,true)?"Scansione aggiornata dal provider":"Scansione già in corso";}
        @JavascriptInterface public boolean refreshIfDue(){return RadarScanner.refreshIfDue(getApplicationContext());}
        /** Serve il grafico direttamente dalla cache SQLite, senza nuove richieste a Yahoo. */
        @JavascriptInterface public String getChartData(String ticker){
            String symbol=ticker==null?"":ticker.toUpperCase().trim();
            if(!symbol.matches("[A-Z0-9]{1,12}"))return "{\"error\":\"Ticker non valido\"}";
            RadarStore db=new RadarStore(getApplicationContext());
            try{
                JSONObject quote=db.quote(symbol);
                if(quote==null)return new JSONObject().put("ticker",symbol).put("error","Prezzi non ancora scaricati: avvia una scansione").toString();
                JSONArray bars=new JSONArray(quote.optString("payload","[]"));
                ZoneId zone=ZoneId.of("Europe/Rome");
                ZonedDateTime now=ZonedDateTime.now(zone);
                ZonedDateTime cutoff=now.toLocalDate().atTime(LocalTime.of(18,15)).atZone(zone);
                if(bars.length()>0){
                    JSONArray last=bars.optJSONArray(bars.length()-1);
                    if(last!=null&&last.optString(0).equals(now.toLocalDate().toString())&&
                       (now.isBefore(cutoff)||quote.optLong("fetchedAt")<cutoff.toInstant().toEpochMilli()))
                        bars.remove(bars.length()-1);
                }
                return new JSONObject().put("ticker",symbol).put("bars",bars)
                    .put("asof",bars.length()>0?bars.getJSONArray(bars.length()-1).optString(0):"")
                    .put("source","Cache Yahoo Milano · EOD non ufficiale").toString();
            }catch(Exception ex){return "{\"error\":\"Storico non leggibile\"}";}
            finally{db.close();}
        }
        @JavascriptInterface public String getStatsSettings(){
            android.content.SharedPreferences p=getPreferences(0);
            try{return new JSONObject().put("capital",p.getInt("stats_capital",1000))
                .put("fee",Double.longBitsToDouble(p.getLong("stats_fee",Double.doubleToLongBits(2.95))))
                .put("slippageBps",p.getInt("stats_slippage_bps",5))
                .put("horizon",p.getInt("stats_horizon",20)).toString();}
            catch(Exception ex){return "{\"capital\":1000,\"fee\":2.95,\"slippageBps\":5,\"horizon\":20}";}
        }
        @JavascriptInterface public boolean saveStatsSettings(String json){
            try{
                JSONObject v=new JSONObject(json);
                int capital=v.getInt("capital"),slip=v.getInt("slippageBps"),horizon=v.getInt("horizon");
                double fee=v.getDouble("fee");
                if(capital<100||capital>1000000||slip<0||slip>300||!Double.isFinite(fee)||fee<0||fee>100||
                    !(horizon==5||horizon==10||horizon==20))return false;
                getPreferences(0).edit().putInt("stats_capital",capital)
                    .putLong("stats_fee",Double.doubleToLongBits(fee))
                    .putInt("stats_slippage_bps",slip).putInt("stats_horizon",horizon).apply();return true;
            }catch(Exception ex){return false;}
        }
        @JavascriptInterface public String addTrade(String json){
            RadarStore db=new RadarStore(getApplicationContext());try{db.saveTrade(new JSONObject(json));return "Operazione registrata";}
            catch(Exception e){return "ERRORE: "+e.getMessage();}finally{db.close();}
        }
        @JavascriptInterface public String closeTrade(int id,double exit,double fees){
            RadarStore db=new RadarStore(getApplicationContext());try{db.closeTrade(id,exit,fees);return "Operazione chiusa";}
            catch(Exception e){return "ERRORE: "+e.getMessage();}finally{db.close();}
        }
    }
}
