package dev.dipa.widgets;
import android.content.*;
public final class Events extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i) {
        if(Intent.ACTION_POWER_CONNECTED.equals(i.getAction())||Intent.ACTION_POWER_DISCONNECTED.equals(i.getAction())||Intent.ACTION_BATTERY_LOW.equals(i.getAction())||Intent.ACTION_BATTERY_OKAY.equals(i.getAction()))Widgets.updateGroup(c,"Battery");
        else Widgets.updateAll(c);
        if(Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) {
            RefreshJob.schedule(c,Widgets.hasGroup(c,"Weather")||Widgets.hasGroup(c,"Quick Look"));
            // No background FGS start: Android 16 restricts health-service starts.
            // User resumes step tracking from the app after a reboot.
            Data.prefs(c).edit().putBoolean("tracking",false).remove("sensor_previous").apply();
        }
    }
}
