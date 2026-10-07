package dev.dipa.widgets;

import android.app.job.*;
import android.content.*;
import android.util.Log;
import java.util.concurrent.*;

public final class RefreshJob extends JobService {
    private ExecutorService executor;
    private Future<?> task;
    @Override public boolean onStartJob(JobParameters params) {
        executor = Executors.newSingleThreadExecutor();
        task = executor.submit(() -> {
            try { Data.refreshWeather(this, false); }
            catch (Exception e) { Data.prefs(this).edit().putString("weather_error", e.getMessage()).apply(); Log.w("NothingWidget", "Weather refresh failed", e); }
            finally { Widgets.updateGroup(this,"Weather");Widgets.updateGroup(this,"Quick Look");jobFinished(params, false);executor.shutdown(); }
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        if (task != null) task.cancel(true);
        if (executor != null) executor.shutdownNow();
        return true;
    }
    public static void schedule(Context c, boolean active) {
        JobScheduler jobs = c.getSystemService(JobScheduler.class);
        if (!active) { jobs.cancel(41); return; }
        jobs.schedule(new JobInfo.Builder(41, new ComponentName(c, RefreshJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(3600000, 600000).setPersisted(true).build());
    }
}
