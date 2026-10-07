package dev.dipa.widgets;

import android.content.*;
import java.util.concurrent.*;

public final class Events extends BroadcastReceiver {
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    @Override public void onReceive(Context c,Intent i) {
        PendingResult result=goAsync();
        Context app=c.getApplicationContext();
        WORK.execute(()->{
            try {
                String action=i.getAction();
                if(Intent.ACTION_POWER_CONNECTED.equals(action)||Intent.ACTION_POWER_DISCONNECTED.equals(action)
                        ||Intent.ACTION_BATTERY_LOW.equals(action)||Intent.ACTION_BATTERY_OKAY.equals(action))
                    Widgets.updateGroup(app,"Battery");
                else Widgets.updateAll(app);
                RefreshJob.reconcile(app);
                if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)||Intent.ACTION_BOOT_COMPLETED.equals(action))
                    BluetoothBatteries.refresh(app);
                if(Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                    // The user resumes foreground sensor services after a reboot.
                    Data.prefs(app).edit().putBoolean("tracking",false).remove("sensor_previous")
                        .putBoolean("compass_tracking",false).apply();
                }
            }finally{if(result!=null)result.finish();}
        });
    }
}
