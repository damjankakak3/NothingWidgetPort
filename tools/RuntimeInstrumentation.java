package dev.dipa.widgets.tests;

import android.app.*;
import android.app.job.*;
import android.appwidget.*;
import android.bluetooth.*;
import android.content.*;
import android.graphics.drawable.BitmapDrawable;
import android.os.*;
import android.util.SizeF;
import android.widget.*;
import dev.dipa.widgets.*;
import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import org.json.*;

/** Stateful regression checks are restricted to an explicitly selected disposable emulator. */
public final class RuntimeInstrumentation extends Instrumentation {
    private Bundle args;
    private Context app;
    private AppWidgetHost host;
    private final List<Integer> ids=new ArrayList<>();
    private final List<String> checks=new ArrayList<>();
    private Activity activity;
    private Observer compass,battery;
    @Override public void onCreate(Bundle args){this.args=args;start();}
    private static final class Observer extends AppWidgetHostView {
        volatile int updates;
        Observer(Context c){super(c);}
        @Override public void updateAppWidget(RemoteViews views){super.updateAppWidget(views);updates++;}
    }
    private void check(boolean value,String label){if(!value)throw new AssertionError(label);checks.add(label);}
    private String shell(String command)throws IOException {
        try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand(command);
            InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))>=0)bytes.write(buffer,0,n);
            return bytes.toString("UTF-8");
        }
    }
    private int bind(String kind)throws Exception {
        AppWidgetManager manager=AppWidgetManager.getInstance(app);
        int id=host.allocateAppWidgetId();ids.add(id);
        getUiAutomation().adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET");
        try {
            check(manager.bindAppWidgetIdIfAllowed(id,new ComponentName(app,Widgets.class.getName()+"$"+kind)),"bind "+kind);
        }finally{getUiAutomation().dropShellPermissionIdentity();}
        Bundle size=new Bundle();
        size.putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,new ArrayList<>(Arrays.asList(new SizeF(110,110))));
        size.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,110);size.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,110);
        size.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,110);size.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,110);
        manager.updateAppWidgetOptions(id,size);
        runOnMainSync(()->{
            Observer view=(Observer)host.createView(app,id,manager.getAppWidgetInfo(id));
            if(kind.equals("CompassSmall"))compass=view;
            if(kind.equals("Battery"))battery=view;
        });
        return id;
    }
    private void waitFor(java.util.function.BooleanSupplier condition,long timeout,String label) {
        long limit=SystemClock.elapsedRealtime()+timeout;
        while(!condition.getAsBoolean()&&SystemClock.elapsedRealtime()<limit)SystemClock.sleep(100);
        check(condition.getAsBoolean(),label);
    }
    private void weather()throws Exception {
        android.content.SharedPreferences p=Data.prefs(app);
        p.edit().putString("city","Budapest, Hungary").putString("lat","47.4979").putString("lon","19.0402")
            .putLong("weather_time",System.currentTimeMillis()).commit();
        RefreshJob.schedule(app,true);
        JobScheduler jobs=app.getSystemService(JobScheduler.class);
        JobInfo existing=jobs.getPendingJob(RefreshJob.PERIODIC_ID);
        check(existing!=null&&existing.isPeriodic(),"hourly weather job registered");
        PersistableBundle extra=new PersistableBundle(existing.getExtras());extra.putBoolean("keep_marker",true);
        jobs.schedule(new JobInfo.Builder(RefreshJob.PERIODIC_ID,existing.getService())
            .setPeriodic(existing.getIntervalMillis(),existing.getFlexMillis())
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setExtras(extra).setPersisted(true).build());
        for(int n=0;n<30;n++)RefreshJob.schedule(app,true);
        check(jobs.getPendingJob(RefreshJob.PERIODIC_ID).getExtras().getBoolean("keep_marker"),"30 widget updates keep the existing hourly job");
        for(int n=0;n<2;n++) {
            long before=p.getLong("weather_time",0);
            String result=shell("cmd jobscheduler run -f dev.dipa.widgets 41");
            check(result.contains("Running job"),"Android dispatched periodic weather job "+n);
            waitFor(()->p.getLong("weather_time",0)>before,30000,"periodic job fetches even while the cache is fresh "+n);
            check(Data.weather(app).has("temperature_2m"),"real forecast stored "+n);
            SystemClock.sleep(150);
        }
        p.edit().putLong("weather_time",System.currentTimeMillis()-7200000L).commit();
        RefreshJob.schedule(app,true);
        check(jobs.getPendingJob(RefreshJob.CATCH_UP_ID)!=null,"stale weather queues a catch-up job");
        RefreshJob.schedule(app,false);
        check(jobs.getPendingJob(RefreshJob.PERIODIC_ID)==null&&jobs.getPendingJob(RefreshJob.CATCH_UP_ID)==null,"removing weather cancels both jobs");
        p.edit().putLong("weather_time",System.currentTimeMillis()).commit();
        new Events().onReceive(app,new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));
        waitFor(()->jobs.getPendingJob(RefreshJob.PERIODIC_ID)!=null,5000,"app update restores weather scheduling without opening the app");
        RefreshJob.schedule(app,false);
    }
    private void bluetooth()throws Exception {
        check(BluetoothBatteries.allowed(app),"Bluetooth connect permission available");
        try {
            BluetoothDevice.class.getMethod("getBatteryLevel");
            checks.add("ROM exposes cached Bluetooth battery getter");
        }catch(NoSuchMethodException e){checks.add("ROM blocks cached getter; broadcast fallback required");}
        BluetoothAdapter adapter=app.getSystemService(BluetoothManager.class).getAdapter();
        BluetoothDevice device=adapter.getRemoteDevice("02:00:00:00:00:01");
        try {
            Object unknown=BluetoothDevice.class.getMethod("getBatteryLevel").invoke(device);
            check(unknown instanceof Number&&((Number)unknown).intValue()<0,"cached getter invocation returns unknown for an unconnected device");
        }catch(NoSuchMethodException ignored){}
        int before=battery.updates;
        new BluetoothBatteries().onReceive(app,new Intent(BluetoothBatteries.BATTERY)
            .putExtra(BluetoothDevice.EXTRA_DEVICE,device).putExtra(BluetoothDevice.EXTRA_NAME,"QA speaker")
            .putExtra("android.bluetooth.device.extra.BATTERY_LEVEL",73));
        waitFor(()->Data.prefs(app).getString("bluetooth_batteries","{}").contains("QA speaker"),5000,"speaker battery report reaches cache");
        check(new JSONObject(Data.prefs(app).getString("bluetooth_batteries","{}")).getJSONObject(device.getAddress()).getInt("level")==73,"speaker level is 73 percent");
        waitFor(()->battery.updates>before,5000,"battery report updates a real widget host");
        new BluetoothBatteries().onReceive(app,new Intent(BluetoothDevice.ACTION_ACL_DISCONNECTED).putExtra(BluetoothDevice.EXTRA_DEVICE,device));
        waitFor(()->!Data.prefs(app).getString("bluetooth_batteries","{}").contains(device.getAddress()),5000,"disconnect removes the speaker");
        new BluetoothBatteries().onReceive(app,new Intent(BluetoothBatteries.BATTERY)
            .putExtra(BluetoothDevice.EXTRA_DEVICE,device).putExtra("android.bluetooth.device.extra.BATTERY_LEVEL",101));
        SystemClock.sleep(300);
        check(!Data.prefs(app).getString("bluetooth_batteries","{}").contains(device.getAddress()),"invalid levels do not create a battery device");
    }
    private void compass()throws Exception {
        check(CompassState.difference(359,1)==2,"compass wraparound uses the short angular distance");
        float[] matrix=new float[9],remapped=new float[9],orientation=new float[3];
        float north=CompassState.bearing(new float[]{0,0,0,1},0,matrix,remapped,orientation);
        float east=CompassState.bearing(new float[]{0,0,-(float)Math.sin(Math.PI/4),(float)Math.cos(Math.PI/4)},0,matrix,remapped,orientation);
        check(Math.abs(north)<.01&&Math.abs(east-90)<.01,"rotation-vector headings map north and east correctly");
        activity=startActivitySync(new Intent(app,ConfigureActivity.class).putExtra("kind","Compass").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        shell("input keyevent 224");shell("wm dismiss-keyguard");
        int before=compass.updates;
        runOnMainSync(()->CompassService.start(app));
        waitFor(()->Float.isFinite(CompassState.read(app))&&compass.updates>before,12000,"live sensor events update a real compass widget host");
        shell("input keyevent 3");
        check(shell("dumpsys activity service dev.dipa.widgets/.CompassService").contains("listening=true"),"compass sensing continues on the home screen");
        shell("input keyevent 223");
        waitFor(()->!app.getSystemService(PowerManager.class).isInteractive(),5000,"phone reaches screen-off state");
        waitFor(()->serviceContains("listening=false"),5000,"screen-off unregisters compass sensing");
        shell("input keyevent 224");shell("wm dismiss-keyguard");
        waitFor(()->serviceContains("listening=true"),5000,"unlocked screen resumes compass sensing");
        CompassService.stop(app);
    }
    private boolean serviceContains(String text) {
        try{return shell("dumpsys activity service dev.dipa.widgets/.CompassService").contains(text);}
        catch(IOException e){return false;}
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        int resultCode=Activity.RESULT_OK;
        Map<String,?> backup=null;
        try {
            if(args==null||!"true".equals(args.getString("isolated"))||!Build.HARDWARE.contains("ranchu"))
                throw new IllegalArgumentException("Run only on a disposable emulator with -e isolated true");
            app=getTargetContext();backup=new HashMap<>(Data.prefs(app).getAll());
            host=new AppWidgetHost(app,99213){@Override protected AppWidgetHostView onCreateView(Context c,int id,AppWidgetProviderInfo info){return new Observer(c);}};
            host.startListening();bind("CompassSmall");bind("Battery");bind("WeatherSquare");
            weather();bluetooth();compass();
            result.putString("stream","PASS: "+checks.size()+" runtime checks\n"+String.join("\n",checks)+"\n");

        }catch(Throwable e) {
            result.putString("stream","FAIL: "+e+"\nCompleted:\n"+String.join("\n",checks)+"\n");resultCode=Activity.RESULT_CANCELED;
        }finally {
            if(app!=null){CompassService.stop(app);RefreshJob.schedule(app,false);}
            if(activity!=null)runOnMainSync(()->activity.finish());
            if(host!=null){for(int id:ids)host.deleteAppWidgetId(id);host.stopListening();}
            if(app!=null&&backup!=null) {
                android.content.SharedPreferences.Editor edit=Data.prefs(app).edit().clear();
                for(Map.Entry<String,?> item:backup.entrySet()) {
                    Object value=item.getValue();String key=item.getKey();
                    if(value instanceof String)edit.putString(key,(String)value);
                    else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);
                    else if(value instanceof Integer)edit.putInt(key,(Integer)value);
                    else if(value instanceof Long)edit.putLong(key,(Long)value);
                    else if(value instanceof Float)edit.putFloat(key,(Float)value);
                }
                edit.commit();
            }
        }
        finish(resultCode,result);
    }
}
