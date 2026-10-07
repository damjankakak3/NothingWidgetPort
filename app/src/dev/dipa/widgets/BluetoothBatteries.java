package dev.dipa.widgets;

import android.Manifest;
import android.bluetooth.*;
import android.content.*;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.concurrent.*;
import org.json.*;

/** Read Android's existing reports and connection events. No scan or battery polling loop. */
public final class BluetoothBatteries extends BroadcastReceiver {
    public static final String BATTERY="android.bluetooth.device.action.BATTERY_LEVEL_CHANGED";
    private static final String LEVEL="android.bluetooth.device.extra.BATTERY_LEVEL";
    private static final Object LOCK=new Object();
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    private static Method batteryMethod, connectedMethod;
    private static boolean methodsChecked;
    private static synchronized void methods() {
        if(methodsChecked)return;
        methodsChecked=true;
        // This system cache getter is not in the public SDK. Some ROMs expose it;
        // others block it. Broadcast reports remain usable when it is unavailable.
        try {batteryMethod=BluetoothDevice.class.getMethod("getBatteryLevel");}
        catch(ReflectiveOperationException e){Log.i("NothingWidget","Bluetooth cached battery getter unavailable");}
        try {connectedMethod=BluetoothDevice.class.getMethod("isConnected");}
        catch(ReflectiveOperationException ignored){}
    }
    public static boolean allowed(Context c) {
        return c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==0;
    }
    @Override public void onReceive(Context c,Intent intent) {
        PendingResult result=goAsync();
        Context app=c.getApplicationContext();
        WORK.execute(()->{
            try {
                if(!allowed(app))return;
                String action=intent.getAction();
                if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                    if(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,BluetoothAdapter.ERROR)==BluetoothAdapter.STATE_OFF)clear(app);
                    else refresh(app);
                    return;
                }
                BluetoothDevice device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,BluetoothDevice.class);
                if(device==null)return;
                if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action))record(app,device.getAddress(),"",-1);
                else if(BATTERY.equals(action)) {
                    String name=intent.getStringExtra(BluetoothDevice.EXTRA_NAME);
                    if(name==null)name=device.getName();
                    record(app,device.getAddress(),name,intent.getIntExtra(LEVEL,-1));
                } else refresh(app);
            }catch(Exception e){Log.w("NothingWidget","Device battery update unavailable",e);}
            finally{if(result!=null)result.finish();}
        });
    }
    private static JSONObject saved(Context c) throws JSONException {
        return new JSONObject(Data.prefs(c).getString("bluetooth_batteries","{}"));
    }
    static void record(Context c,String address,String name,int level) throws JSONException {
        boolean changed;
        synchronized(LOCK) {
            JSONObject devices=saved(c), previous=devices.optJSONObject(address);
            if(level<0||level>100) {changed=devices.has(address);if(!changed)return;devices.remove(address);}
            else {
                if(name==null||name.isEmpty())name=previous==null?"Connected device":previous.optString("name","Connected device");
                changed=previous==null||previous.optInt("level",-1)!=level||!name.equals(previous.optString("name"));
                // Refresh unchanged cache timestamps at most hourly, not on every UI resume.
                if(!changed&&System.currentTimeMillis()-previous.optLong("time",0)<3600000L)return;
                devices.put(address,new JSONObject().put("name",name).put("level",level).put("time",System.currentTimeMillis()));
            }
            Data.prefs(c).edit().putString("bluetooth_batteries",devices.toString()).apply();
        }
        if(changed)Widgets.updateGroup(c,"Battery");
    }
    private static void clear(Context c) {
        Data.prefs(c).edit().putString("bluetooth_batteries","{}").putString("bluetooth_status","Bluetooth is off").apply();
        Widgets.updateGroup(c,"Battery");
    }
    public static void requestRefresh(Context c) {
        Context app=c.getApplicationContext();WORK.execute(()->refresh(app));
    }
    public static void refresh(Context c) {
        if(!allowed(c))return;
        try {
            BluetoothManager manager=c.getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
            if(adapter==null||!adapter.isEnabled()){clear(c);return;}
            methods();
            if(batteryMethod==null) {
                Data.prefs(c).edit().putString("bluetooth_status","Reconnect the speaker once to receive its battery report. This ROM blocks reading the existing Bluetooth battery cache.").apply();
                return;
            }
            int reported=0;
            for(BluetoothDevice device:adapter.getBondedDevices()) {
                boolean connected=connectedMethod==null||Boolean.TRUE.equals(connectedMethod.invoke(device));
                int level=connected?((Number)batteryMethod.invoke(device)).intValue():-1;
                record(c,device.getAddress(),device.getName(),level);
                if(level>=0&&level<=100)reported++;
            }
            Data.prefs(c).edit().putString("bluetooth_status",reported==0
                ?"No connected device has reported a battery level yet."
                :reported+" connected-device battery "+(reported==1?"level":"levels")+" available").apply();
        }catch(Exception e) {
            Data.prefs(c).edit().putString("bluetooth_status","Could not read the existing battery report. Reconnect the speaker to receive a new report.").apply();
            Log.w("NothingWidget","Device battery snapshot unavailable",e);
        }
    }
    public static BluetoothBatteries listen(Context c) {
        if(!allowed(c))return null;
        BluetoothBatteries receiver=new BluetoothBatteries();IntentFilter filter=new IntentFilter(BATTERY);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        c.registerReceiver(receiver,filter,Manifest.permission.BLUETOOTH_CONNECT,null,Context.RECEIVER_EXPORTED);
        return receiver;
    }
}
