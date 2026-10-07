package dev.dipa.widgets;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.hardware.*;
import android.os.*;
import android.view.WindowManager;
import java.util.concurrent.*;

/** User-started live widget sensing. No wake lock; sensors stop while locked or screen-off. */
public final class CompassService extends Service implements SensorEventListener {
    private SensorManager sensors;
    private Sensor sensor;
    private boolean listening;
    private volatile int generation;
    private long lastPublish;
    private float published = Float.NaN;
    private final float[] matrix = new float[9], remapped = new float[9], orientation = new float[3];
    private final ExecutorService render = Executors.newSingleThreadExecutor();
    private BroadcastReceiver screen;
    private WindowManager windows;

    public static void start(Context c) {
        c.startForegroundService(new Intent(c, CompassService.class));
    }
    public static void stop(Context c) {
        Data.prefs(c).edit().putBoolean("compass_tracking", false).apply();
        c.stopService(new Intent(c, CompassService.class));
    }
    @Override public void onCreate() {
        super.onCreate();
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("compass", "Live compass widgets", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 44,
            new Intent(this, ConfigureActivity.class).putExtra("kind", "Compass"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 45,
            new Intent(this, CompassService.class).setAction("stop"), PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "compass")
            .setSmallIcon(android.R.drawable.ic_menu_compass).setContentTitle("Compass widgets are live")
            .setContentText("Sensing pauses while the phone is locked or the screen is off")
            .setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "Stop", stop).build()).build();
        int type = Build.VERSION.SDK_INT >= 34 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE : 0;
        startForeground(44, notification, type);
        sensors = getSystemService(SensorManager.class);
        sensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (sensor == null) sensor = sensors.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
        if (sensor == null) {
            Data.prefs(this).edit().putString("compass_error", "No rotation-vector sensor available").apply();
            stopSelf(); return;
        }
        windows = getSystemService(WindowManager.class);
        screen = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { updateSensing(); }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON); filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED);
        Data.prefs(this).edit().putBoolean("compass_tracking", true).remove("compass_error").apply();
        updateSensing();
    }
    private void updateSensing() {
        if (sensor == null) return;
        boolean active = getSystemService(PowerManager.class).isInteractive()
            && !getSystemService(KeyguardManager.class).isKeyguardLocked();
        if (active && !listening) {
            lastPublish = 0; published = Float.NaN;
            listening = sensors.registerListener(this, sensor, 200000);
            if (!listening) {
                Data.prefs(this).edit().putString("compass_error", "Could not start compass sensor").apply(); stopSelf();
            }
        } else if (!active && listening) {
            generation++; sensors.unregisterListener(this); listening = false; CompassState.save(this);
        }
    }
    @Override public int onStartCommand(Intent i, int flags, int id) {
        if (i != null && "stop".equals(i.getAction())) { stop(this); return START_NOT_STICKY; }
        if (!Widgets.hasGroup(this,"Compass")) { stop(this); return START_NOT_STICKY; }
        return START_STICKY;
    }
    @Override public void onSensorChanged(SensorEvent event) {
        if (!listening) return;
        float value = CompassState.bearing(event.values, windows.getDefaultDisplay().getRotation(), matrix, remapped, orientation);
        CompassState.set(value);
        long now = SystemClock.elapsedRealtime();
        if (Float.isFinite(published) && (now - lastPublish < 1000 || CompassState.difference(value,published) < 2)) return;
        lastPublish = now; published = value;
        int currentGeneration = generation;
        render.execute(() -> {
            if (currentGeneration == generation) Widgets.updateGroup(this,"Compass");
        });
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Override public IBinder onBind(Intent i) { return null; }
    @Override protected void dump(java.io.FileDescriptor fd, java.io.PrintWriter out, String[] args) {
        out.println("listening="+listening+" bearing="+CompassState.read(this)+" published="+published);
    }
    @Override public void onDestroy() {
        generation++; if (sensors != null) sensors.unregisterListener(this);
        if (screen != null) unregisterReceiver(screen);
        render.shutdownNow(); CompassState.save(this);
        Data.prefs(this).edit().putBoolean("compass_tracking",false).apply();
        super.onDestroy();
    }
}
