package dev.dipa.widgets;

import android.Manifest;
import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.hardware.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public final class ConfigureActivity extends Activity {
    private int widgetId;
    private String kind,group;
    private LinearLayout page;
    private TextView status;
    private BluetoothBatteries batteryReceiver;
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        widgetId=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1);
        AppWidgetProviderInfo info=widgetId<0?null:AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId);
        if(widgetId>=0&&(info==null||!info.provider.getPackageName().equals(getPackageName()))) { finish();return; }
        kind=info!=null?Widgets.kind(info):getIntent().getStringExtra("kind"); if(kind==null)kind="AnalogBold";
        group=Registry.find(kind)[2];
        setResult(RESULT_CANCELED); show();
    }
    private void show() {
        page=UI.page(this,Registry.find(kind)[1]);
        status=UI.text(this,page,"",14);
        android.content.SharedPreferences prefs=Data.prefs(this);
        if(group.equals("Clocks")&&kind.equals("World")) {
            UI.text(this,page,"Time zones",22);
            UI.text(this,page,"Select one or more cities. Tap the clock to cycle through them.",15);
            EditText zones=UI.field(this,page,"Europe/Budapest,Asia/Tokyo",prefs.getString("zone:"+widgetId,"Europe/Budapest"));
            UI.button(this,page,"Save time zones",()->{
                String value=zones.getText().toString().replace(" ","");
                try { for(String zone:value.split(","))ZoneId.of(zone);prefs.edit().putString("zone:"+widgetId,value).apply();done(); }
                catch(Exception e) { status.setText("Use valid time zones, such as Europe/Budapest or Asia/Tokyo."); }
            });
        } else if(group.equals("Photos")) {
            UI.text(this,page,"A photo of your own",22);
            UI.text(this,page,"Choose up to 16 photos with Android’s file picker. Tap the photo on your home screen to cycle through them. Access stays limited to the images you select.",15);
            UI.button(this,page,"Choose photo",()->{
                if(widgetId<0) { status.setText("Add the photo widget to your home screen first, then choose its photo.");return; }
                Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(pick,71);
            });
        } else if(group.equals("Countdown")) {
            UI.text(this,page,"Count the days",22);
            EditText title=UI.field(this,page,"Event name",prefs.getString("title:"+widgetId,"Looking forward"));
            DatePicker date=new DatePicker(this); date.setCalendarViewShown(false);
            try { LocalDate target=LocalDate.parse(prefs.getString("date:"+widgetId,Data.today()));date.updateDate(target.getYear(),target.getMonthValue()-1,target.getDayOfMonth()); }catch(Exception ignored){}
            page.addView(date,new LinearLayout.LayoutParams(-1,-2));
            UI.button(this,page,"Save countdown",()->{
                if(widgetId<0) { status.setText("Add the countdown widget first to save an event for it.");return; }
                String target=LocalDate.of(date.getYear(),date.getMonth()+1,date.getDayOfMonth()).toString();
                prefs.edit().putString("title:"+widgetId,title.getText().toString()).putString("date:"+widgetId,target).putString("countdown_created:"+widgetId,Data.today()).apply();done();
            });
        } else if(group.equals("Weather")||group.equals("Quick Look")) {
            UI.text(this,page,"Weather city",22);
            UI.text(this,page,"Forecasts from Open-Meteo. Your city is sent to its geocoding and weather service. Updates are cached for one hour.",15);
            EditText city=UI.field(this,page,"City name",prefs.getString("city",""));
            Switch units=new Switch(this);units.setText("Use Fahrenheit");units.setChecked(prefs.getBoolean("fahrenheit",false));page.addView(units);
            UI.button(this,page,"Save and refresh",()->{
                String query=city.getText().toString().trim();
                if(query.isEmpty()) { status.setText("Enter a city name.");return; }
                status.setText("Finding your city…");
                prefs.edit().putBoolean("fahrenheit",units.isChecked()).apply();
                WORK.execute(()->{
                    try { String found=Data.findCity(this,query);Data.refreshWeather(this,true);Widgets.updateAll(this);RefreshJob.reconcile(this);
                        runOnUiThread(()->{status.setText("Updated for "+found);if(widgetId>=0)done();}); }
                    catch(Exception e) {runOnUiThread(()->status.setText(e.getMessage()==null?"Weather unavailable. Try again.":e.getMessage()));}
                });
            });
            if(group.equals("Quick Look")) UI.button(this,page,"Enable calendar",()->requestPermissions(new String[]{Manifest.permission.READ_CALENDAR},72));
        } else if(group.equals("Steps")) {
            UI.text(this,page,"Track your steps",22);
            UI.text(this,page,"Uses the hardware step counter with batched sensor events. A persistent Android notification appears while tracking. Tracking begins when enabled; resume it here after a reboot.",15);
            EditText goal=UI.field(this,page,"Daily step goal",Integer.toString(prefs.getInt("step_goal",6000)));goal.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            UI.button(this,page,"Start tracking",()->{
                try { int n=Integer.parseInt(goal.getText().toString());if(n<1)throw new NumberFormatException();prefs.edit().putInt("step_goal",n).apply(); }
                catch(Exception e) {status.setText("Enter a positive step goal.");return;}
                Sensor sensor=getSystemService(SensorManager.class).getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
                if(sensor==null) {status.setText("This device does not expose a hardware step counter.");return;}
                if(checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION,Manifest.permission.POST_NOTIFICATIONS},73);
                else startTracking();
            });
            UI.button(this,page,"Stop tracking",()->{
                prefs.edit().putBoolean("tracking",false).remove("sensor_previous").apply();stopService(new Intent(this,StepService.class));status.setText("Tracking stopped");Widgets.updateGroup(this,"Steps");
            });
        } else if(group.equals("Screen time")) {
            UI.text(this,page,"Understand your day",22);
            UI.text(this,page,"Android usage access is required to read screen time. Counts screen-interactive time, which may differ from Digital Wellbeing’s app totals.",15);
            EditText goal=UI.field(this,page,"Daily screen goal, minutes",Integer.toString(prefs.getInt("screen_goal",180)));goal.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            UI.button(this,page,"Save daily goal",()->{
                try {int n=Integer.parseInt(goal.getText().toString());if(n<1)throw new NumberFormatException();prefs.edit().putInt("screen_goal",n).apply();Widgets.updateGroup(this,"Screen time");status.setText("Daily goal saved");}
                catch(Exception e){status.setText("Enter a positive number of minutes.");}
            });
            UI.button(this,page,"Open usage access",()->startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).setData(Uri.parse("package:"+getPackageName()))));
        } else if(group.equals("Media")) {
            UI.text(this,page,"Control your music",22);
            UI.text(this,page,"Notification access lets Android expose active media sessions. The app uses their track metadata and playback controls. It does not read or store notification messages.",15);
            UI.button(this,page,"Enable media controls",()->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        } else if(group.equals("Compass")||group.equals("Breathing")) {
            UI.text(this,page,group.equals("Compass")?"Find your direction":"Take a moment",22);
            UI.text(this,page,group.equals("Compass")?"Live widgets use a persistent notification and refresh when your heading changes. Sensors pause while the phone is locked or its screen is off. The arrow shows the top of your phone; the north marker rotates around it.":"Tap the widget to begin a breathing session. Animations run only while the session is visible.",15);
            if(group.equals("Compass")) {
                status.setText(prefs.getBoolean("compass_tracking",false)?"Live compass widgets enabled":"Live compass widgets stopped");
                UI.button(this,page,"Start live widgets",()->{
                    if(!Widgets.hasGroup(this,"Compass")){status.setText("Add a compass widget to your home screen first.");return;}
                    try {CompassService.start(this);status.setText("Live compass widgets enabled");}
                    catch(Exception e){status.setText("Could not start compass: "+e.getMessage());}
                });
                UI.button(this,page,"Stop live widgets",()->{CompassService.stop(this);status.setText("Live compass widgets stopped");});
            }
            UI.button(this,page,"Open session",()->startActivity(new Intent(this,SessionActivity.class).putExtra("kind",kind)));
        } else if(group.equals("Battery")) {
            UI.text(this,page,"Battery status",22);
            UI.text(this,page,"The phone reading refreshes on Android’s power broadcasts, periodic widget updates, and taps. Connected-device percentages are shown only when Android reports them.",15);
            UI.button(this,page,"Refresh now",()->refreshBatteries());
            UI.button(this,page,"Allow connected devices",()->requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},74));
        } else UI.text(this,page,"This widget updates with Android’s native clock and date views.",16);
        UI.button(this,page,"Done",this::done);
    }
    private void startTracking() {
        if(!Data.prefs(this).getBoolean("tracking",false))Data.prefs(this).edit().remove("sensor_previous").apply();
        try {startForegroundService(new Intent(this,StepService.class));status.setText("Step tracking enabled");}
        catch(Exception e){status.setText("Could not start step tracking: "+e.getMessage());}
    }
    private void refreshBatteries() {
        status.setText("Reading connected-device batteries…");
        WORK.execute(()->{BluetoothBatteries.refresh(this);Widgets.updateGroup(this,"Battery");
            runOnUiThread(()->status.setText(Data.prefs(this).getString("bluetooth_status","Battery refreshed")));});
    }
    private void done() {
        Widgets.updateAll(this);
        if(widgetId>=0)setResult(RESULT_OK,new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId));
        finish();
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==71&&result==RESULT_OK&&data!=null) {
            try {
                org.json.JSONArray photos=new org.json.JSONArray();
                ClipData selected=data.getClipData();
                if(selected!=null)for(int index=0;index<Math.min(16,selected.getItemCount());index++) {
                    Uri uri=selected.getItemAt(index).getUri();getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);photos.put(uri.toString());
                }
                else if(data.getData()!=null) {Uri uri=data.getData();getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);photos.put(uri.toString());}
                if(photos.length()==0)throw new IllegalArgumentException("No photo selected");
                Data.prefs(this).edit().putString("photo:"+widgetId,photos.toString()).putInt("page:"+widgetId,0).apply();done();}
            catch(Exception e){status.setText("Photo access failed. Choose another photo.");}
        }
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==73&&checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)==PackageManager.PERMISSION_GRANTED)startTracking();
        else if(request==73)status.setText("Activity recognition permission is needed to count steps.");
        if(request==74&&BluetoothBatteries.allowed(this)) {
            if(batteryReceiver==null)batteryReceiver=BluetoothBatteries.listen(this);
            refreshBatteries();
        }
        Widgets.updateAll(this);
    }
    @Override protected void onResume(){super.onResume();batteryReceiver=BluetoothBatteries.listen(this);BluetoothBatteries.requestRefresh(this);Widgets.updateAll(this);RefreshJob.reconcile(this);}
    @Override protected void onPause(){if(batteryReceiver!=null){unregisterReceiver(batteryReceiver);batteryReceiver=null;}super.onPause();}
}
