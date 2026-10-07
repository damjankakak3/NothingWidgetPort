package dev.dipa.widgets;
import android.Manifest;
import android.bluetooth.*;
import android.content.*;
import org.json.*;

/** Records only battery values supplied by Android. No Bluetooth scan loop. */
public final class BluetoothBatteries extends BroadcastReceiver {
    public static final String BATTERY="android.bluetooth.device.action.BATTERY_LEVEL_CHANGED";
    @Override public void onReceive(Context c,Intent intent) {
        if(c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=0)return;
        BluetoothDevice device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,BluetoothDevice.class);
        if(device==null)return;
        try {
            JSONObject devices=new JSONObject(Data.prefs(c).getString("bluetooth_batteries","{}"));
            String address=device.getAddress();
            if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction()))devices.remove(address);
            else if(BATTERY.equals(intent.getAction())) {
                int level=intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL",-1);
                if(level<0||level>100)devices.remove(address);
                else devices.put(address,new JSONObject().put("name",device.getName()==null?"Connected device":device.getName()).put("level",level).put("time",System.currentTimeMillis()));
            }
            Data.prefs(c).edit().putString("bluetooth_batteries",devices.toString()).apply();Widgets.updateGroup(c,"Battery");
        } catch(Exception e){android.util.Log.w("NothingWidget","Device battery unavailable",e);}
    }
    public static BluetoothBatteries listen(Context c) {
        if(c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=0)return null;
        BluetoothBatteries receiver=new BluetoothBatteries();IntentFilter filter=new IntentFilter(BATTERY);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        c.registerReceiver(receiver,filter,Manifest.permission.BLUETOOTH_CONNECT,null,Context.RECEIVER_EXPORTED);return receiver;
    }
}
