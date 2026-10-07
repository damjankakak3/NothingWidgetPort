package dev.dipa.widgets;

import android.app.job.*;
import android.content.*;
import android.os.*;
import android.util.Log;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/** Keep the hourly job in place. Replacing it on every widget update postpones its window. */
public final class RefreshJob extends JobService {
    public static final int PERIODIC_ID = 41, CATCH_UP_ID = 43;
    private static final long HOUR = 3600000L, FLEX = 600000L;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Integer,Run> runs = new HashMap<>();
    private static final class Run { Future<?> task; volatile boolean cancelled; }

    @Override public boolean onStartJob(JobParameters params) {
        Run run = new Run();
        Run previous = runs.put(params.getJobId(), run);
        if (previous != null) cancel(previous);
        run.task = executor.submit(() -> {
            boolean retry = false;
            try {
                // A flex window can begin before the one-hour cache expires. Periodic
                // runs fetch fresh data instead of skipping a whole extra interval.
                Data.refreshWeather(this, params.getJobId() == PERIODIC_ID);
            } catch (Exception e) {
                retry = !run.cancelled;
                if (retry) {
                    Data.prefs(this).edit().putString("weather_error", e.getMessage() == null ? "Weather unavailable" : e.getMessage()).apply();
                    Log.w("NothingWidget", "Weather refresh failed", e);
                }
            }
            final boolean shouldRetry = retry;
            main.post(() -> {
                if (run.cancelled || runs.get(params.getJobId()) != run) return;
                runs.remove(params.getJobId());
                Widgets.updateGroup(this,"Weather");
                Widgets.updateGroup(this,"Quick Look");
                jobFinished(params, shouldRetry);
            });
        });
        return true;
    }
    private static void cancel(Run run) {
        run.cancelled = true;
        if (run.task != null) run.task.cancel(true);
    }
    @Override public boolean onStopJob(JobParameters params) {
        Run run = runs.remove(params.getJobId());
        if (run != null) cancel(run);
        return true;
    }
    @Override public void onDestroy() {
        for (Run run : runs.values()) cancel(run);
        runs.clear(); executor.shutdownNow(); super.onDestroy();
    }
    public static void schedule(Context c, boolean active) {
        JobScheduler jobs = c.getSystemService(JobScheduler.class);
        if (!active) { jobs.cancel(PERIODIC_ID); jobs.cancel(CATCH_UP_ID); return; }
        if (!Data.prefs(c).contains("lat")) return;
        JobInfo existing = jobs.getPendingJob(PERIODIC_ID);
        if (existing == null || !existing.isPeriodic() || existing.getIntervalMillis() != HOUR
                || existing.getFlexMillis() != FLEX || existing.getExtras().getInt("schema", 0) != 2) {
            PersistableBundle extras = new PersistableBundle(); extras.putInt("schema", 2);
            int result = jobs.schedule(new JobInfo.Builder(PERIODIC_ID, new ComponentName(c, RefreshJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(HOUR, FLEX)
                .setBackoffCriteria(60000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setExtras(extras).setPersisted(true).build());
            if (result != JobScheduler.RESULT_SUCCESS)
                Data.prefs(c).edit().putString("weather_error", "Android could not schedule weather updates").apply();
        }
        if (Data.weatherStale(c) && jobs.getPendingJob(CATCH_UP_ID) == null) {
            jobs.schedule(new JobInfo.Builder(CATCH_UP_ID, new ComponentName(c, RefreshJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(60000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
        }
    }
    public static void reconcile(Context c) {
        schedule(c, Widgets.hasGroup(c,"Weather") || Widgets.hasGroup(c,"Quick Look"));
    }
}
