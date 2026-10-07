package dev.dipa.widgets;

import android.app.*;
import android.content.*;
import android.hardware.*;
import android.os.*;
import java.time.LocalDate;

/** Opt-in hardware step counter. Batches sensor events; there is no polling loop. */
public final class StepService extends Service implements SensorEventListener {
    private SensorManager sensors;
    private long lastPublish;
    private BluetoothBatteries batteryReceiver;
    @Override public void onCreate() {
        super.onCreate();
        batteryReceiver=BluetoothBatteries.listen(this);
        NotificationManager n = getSystemService(NotificationManager.class);
        n.createNotificationChannel(new NotificationChannel("steps", "Step tracking", NotificationManager.IMPORTANCE_LOW));
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 9, open, PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, StepService.class).setAction("stop");
        PendingIntent stopPi = PendingIntent.getService(this, 10, stop, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "steps").setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Step tracking is on").setContentText("Using your phone’s hardware step counter")
            .setContentIntent(pi).addAction(new Notification.Action.Builder(null, "Stop", stopPi).build()).setOngoing(true).build();
        startForeground(42, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);
        sensors = getSystemService(SensorManager.class);
        Sensor counter = sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (counter == null || checkSelfPermission("android.permission.ACTIVITY_RECOGNITION") != 0) {
            Data.prefs(this).edit().putBoolean("tracking", false).putString("step_error", counter == null ? "No hardware step counter" : "Activity permission needed").apply();
            stopSelf(); return;
        }
        boolean registered = sensors.registerListener(this, counter, SensorManager.SENSOR_DELAY_NORMAL, 30000000);
        Data.prefs(this).edit().putBoolean("tracking", registered).apply();
        if (!registered) stopSelf();
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && "stop".equals(intent.getAction())) { Data.prefs(this).edit().putBoolean("tracking", false).apply(); stopSelf(); return START_NOT_STICKY; }
        return START_STICKY;
    }
    @Override public void onSensorChanged(SensorEvent event) {
        long current = (long) event.values[0];
        android.content.SharedPreferences p = Data.prefs(this);
        long eventMillis=System.currentTimeMillis()-(SystemClock.elapsedRealtimeNanos()-event.timestamp)/1000000;
        String day = java.time.Instant.ofEpochMilli(eventMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString();
        long delta = Logic.stepDelta(p.getLong("sensor_previous", -1), current);
        int count = (int) Math.min(Integer.MAX_VALUE, Data.steps(this, day) + delta);
        p.edit().putLong("sensor_previous", current).putInt("steps:" + day, count).apply();
        long now = SystemClock.elapsedRealtime();
        if (now - lastPublish >= 60000) { lastPublish = now; Widgets.updateGroup(this, "Steps"); }
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onDestroy() { if (sensors != null) sensors.unregisterListener(this);if(batteryReceiver!=null)unregisterReceiver(batteryReceiver);super.onDestroy(); }
}
