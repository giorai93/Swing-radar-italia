package it.swingradar.italia.v11;

import android.app.job.JobParameters;
import android.app.job.JobService;

public final class ScanJobService extends JobService{
    @Override public boolean onStartJob(JobParameters params){
        final JobParameters current=params;
        return RadarScanner.start(this,new RadarScanner.Complete(){
            @Override public void onComplete(){jobFinished(current,false);}
        },true);
    }
    @Override public boolean onStopJob(JobParameters params){return RadarScanner.isRunning();}
}
