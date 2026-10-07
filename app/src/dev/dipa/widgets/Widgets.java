package dev.dipa.widgets;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.*;
import android.os.*;
import android.net.Uri;
import android.widget.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class Widgets extends AppWidgetProvider {
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static final java.util.Map<RemoteViews,CharSequence> values=java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    public static String kind(AppWidgetProviderInfo info) {
        String name = info.provider.getClassName(); return name.substring(name.lastIndexOf('$') + 1);
    }
    public static java.util.List<AppWidgetProviderInfo> providers(Context c) {
        java.util.List<AppWidgetProviderInfo> result = new ArrayList<>();
        for (AppWidgetProviderInfo info : AppWidgetManager.getInstance(c).getInstalledProviders())
            if (info.provider.getPackageName().equals(c.getPackageName())) result.add(info);
        return result;
    }
    public static void updateAll(Context c) { updateGroup(c, null); }
    public static void updateGroup(Context c, String group) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (AppWidgetProviderInfo info : providers(c)) {
            String name = kind(info);
            if (group != null && !Registry.find(name)[2].equals(group)) continue;
            for (int id : m.getAppWidgetIds(info.provider)) {
                try { m.updateAppWidget(id, render(c, name, id, m.getAppWidgetOptions(id))); }
                catch (Exception e) { android.util.Log.e("NothingWidget", "Update failed for " + name, e); }
            }
        }
    }
    public static boolean hasGroup(Context c, String group) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (AppWidgetProviderInfo info : providers(c)) if (Registry.find(kind(info))[2].equals(group)
                && m.getAppWidgetIds(info.provider).length > 0) return true;
        return false;
    }
    @Override public void onReceive(Context c, Intent i) {
        if ((c.getPackageName()+".ACTION").equals(i.getAction())) {
            String action = i.getStringExtra("action"); int id = i.getIntExtra("id", -1);
            if (id < 0 || AppWidgetManager.getInstance(c).getAppWidgetInfo(id) == null) return;
            if ("page".equals(action)) {
                int page = Data.prefs(c).getInt("page:"+id,0);
                Data.prefs(c).edit().putInt("page:"+id,page+1).apply();
            } else if ("next".equals(action) || "previous".equals(action) || "play".equals(action)) MediaService.control(c, action);
            PendingResult p = goAsync(); WORK.execute(() -> {
                try {AppWidgetManager m=AppWidgetManager.getInstance(c);AppWidgetProviderInfo info=m.getAppWidgetInfo(id);if(info!=null)m.updateAppWidget(id,render(c,kind(info),id,m.getAppWidgetOptions(id)));}
                finally {p.finish();}
            });
        } else super.onReceive(c, i);
    }
    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        PendingResult result = goAsync();
        WORK.execute(() -> {
            try {
                for (int id:ids) { AppWidgetProviderInfo info=m.getAppWidgetInfo(id); if(info!=null) m.updateAppWidget(id,render(c,kind(info),id,m.getAppWidgetOptions(id))); }
                RefreshJob.schedule(c, hasGroup(c,"Weather") || hasGroup(c,"Quick Look"));
            } finally { result.finish(); }
        });
    }
    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle b) {
        AppWidgetProviderInfo info=m.getAppWidgetInfo(id); if(info!=null) m.updateAppWidget(id,render(c,kind(info),id,b));
    }
    @Override public void onDeleted(Context c,int[] ids) {
        SharedPreferences.Editor e=Data.prefs(c).edit();
        for(int id:ids) for(String key:Data.prefs(c).getAll().keySet()) if(key.endsWith(":"+id)) e.remove(key);
        e.apply();
        RefreshJob.schedule(c,hasGroup(c,"Weather")||hasGroup(c,"Quick Look"));
        if(!hasGroup(c,"Steps")) { Data.prefs(c).edit().putBoolean("tracking",false).apply(); c.stopService(new Intent(c,StepService.class)); }
    }
    static int resource(Context c,String type,String name) { return c.getResources().getIdentifier(name,type,c.getPackageName()); }
    static void txt(Context c,RemoteViews v,String id,CharSequence value) {
        int r=resource(c,"id",id);if(r==0)return;
        if(id.equals("subtitle")||id.equals("detail")||id.equals("forecast")) {
            if(!Captions.draw(c,v,id,value,180))v.setTextViewText(r,value);
        }
        else {v.setTextViewText(r,value);if(id.equals("value"))values.put(v,value);}
    }
    static PendingIntent open(Context c,String name,int id) {
        String group=Registry.find(name)[2];
        Class<?> cls=(group.equals("Compass")||group.equals("Breathing"))?SessionActivity.class:ConfigureActivity.class;
        Intent i=new Intent(c,cls).putExtra("kind",name).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
            .setData(Uri.parse("localwidgets://open/"+name+"/"+id));
        return PendingIntent.getActivity(c,id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static PendingIntent action(Context c,String name,int id,String action) {
        Intent i=new Intent(c.getPackageName()+".ACTION").setClassName(c,Widgets.class.getName()+"$"+name)
            .putExtra("id",id).putExtra("action",action).setData(Uri.parse("localwidgets://action/"+id+"/"+action));
        return PendingIntent.getBroadcast(c,id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static RemoteViews render(Context c,String name,int id,Bundle options) {
        java.util.List<android.util.SizeF> sizes=Geometry.sizes(name,options);
        if(sizes.size()==1)return renderSized(c,name,id,sizes.get(0));
        java.util.Map<android.util.SizeF,RemoteViews> views=new java.util.LinkedHashMap<>();
        for(android.util.SizeF size:sizes)views.put(size,renderSized(c,name,id,size));
        return new RemoteViews(views);
    }
    private static RemoteViews renderSized(Context c,String name,int id,android.util.SizeF host) {
        String[] def=Registry.find(name); String group=def[2];
        RemoteViews v=new RemoteViews(c.getPackageName(),resource(c,"layout",def[5]));
        v.setOnClickPendingIntent(R.id.root,open(c,name,id));
        SharedPreferences p=Data.prefs(c);
        float[] box=Geometry.content(name,host);
        float density=c.getResources().getDisplayMetrics().density;
        int width=Math.max(1,Math.round(box[0]*density)),height=Math.max(1,Math.round(box[1]*density));
        Geometry.apply(c,v,name,box[0],box[1]);
        if(group.equals("Steps")||(group.equals("Screen time")&&!name.equals("ScreenFace"))) {
            Bitmap art=LocalArtwork.render(c,name,width,height);
            if(art!=null) {
                v.setImageViewBitmap(R.id.artwork,art);
                String description=group.equals("Steps")?Data.steps(c,Data.today())+" steps today":"Screen time: "+Logic.duration(Data.screenTime(c));
                v.setContentDescription(R.id.artwork,description);
                return v;
            }
        }
        if(group.equals("Battery")) {
            Intent battery=c.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int level=battery==null?-1:battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
            int scale=battery==null?100:battery.getIntExtra(BatteryManager.EXTRA_SCALE,100);
            int percent=level<0?-1:level*100/Math.max(1,scale);
            boolean charging=battery!=null&&battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0;
            JSONArray devices=new JSONArray();
            try {
                JSONObject saved=new JSONObject(p.getString("bluetooth_batteries","{}"));
                for(java.util.Iterator<String> keys=saved.keys();keys.hasNext();) {
                    JSONObject device=saved.getJSONObject(keys.next());
                    if(System.currentTimeMillis()-device.optLong("time",0)<86400000L)devices.put(device);
                }
            }catch(Exception ignored){}
            Bitmap art=name.equals("BatterySmall")?BatteryArtwork.small(c,Math.min(width,height),percent,charging)
                :BatteryArtwork.large(c,width,height,percent,charging,devices);
            v.setImageViewBitmap(R.id.artwork,art);
            v.setContentDescription(R.id.artwork,"Phone battery "+percent+" percent"+(charging?", charging":""));
            return v;
        }
        if(group.equals("Compass")||group.equals("Countdown")||group.equals("Media")||group.equals("Weather")) {
            Bitmap art=group.equals("Compass")?CompassArtwork.render(c,name,width,height,p.getFloat("compass",Float.NaN))
                :group.equals("Countdown")?CountdownArtwork.render(c,width,height,id)
                :group.equals("Weather")?WeatherArtwork.render(c,name,width,height,p.getInt("page:"+id,0))
                :MediaArtwork.render(c,width,height,MediaService.controller(c),p.getInt("page:"+id,0));
            v.setImageViewBitmap(R.id.artwork,art);
            v.setContentDescription(R.id.artwork,Registry.find(name)[1]);
            if(group.equals("Weather")&&p.contains("city")&&(name.equals("WeatherCircle")||name.equals("WeatherSquare")))v.setOnClickPendingIntent(R.id.artwork,action(c,name,id,"page"));
            if(group.equals("Countdown")&&p.contains("date:"+id))v.setOnClickPendingIntent(R.id.artwork,action(c,name,id,"page"));
            if(group.equals("Media")) {
                v.setOnClickPendingIntent(R.id.previous,action(c,name,id,"previous"));
                v.setOnClickPendingIntent(R.id.play,action(c,name,id,"play"));
                v.setOnClickPendingIntent(R.id.next,action(c,name,id,"next"));
                if(MediaService.controller(c)!=null)v.setOnClickPendingIntent(R.id.artwork,action(c,name,id,"page"));
            }
            return v;
        }
        Captions.defaults(c,v,Math.round(box[0]));
        if(group.equals("Clocks")) {
            if(name.equals("World")) {
                String zones=p.getString("zone:"+id,"Europe/Budapest"); String[] choices=zones.split(",");
                String zone=choices[Math.floorMod(p.getInt("page:"+id,0),choices.length)];
                try { ZoneId.of(zone); } catch(Exception e) { zone="UTC"; }
                txt(c,v,"subtitle",zone.substring(zone.lastIndexOf('/')+1).replace('_',' ').toUpperCase(java.util.Locale.getDefault()));
                v.setString(R.id.clock,"setTimeZone",zone);
                v.setOnClickPendingIntent(R.id.clock,action(c,name,id,"page"));
            }
        } else if(group.equals("Quick Look")) {
            JSONObject w=Data.weather(c);
            boolean calendar=p.getInt("page:"+id,0)%2!=0;
            boolean day=w.optInt("is_day",java.time.LocalTime.now().getHour()>=6&&java.time.LocalTime.now().getHour()<18?1:0)==1;
            String condition=w.has("temperature_2m")?Logic.weather(w.optInt("weather_code",-1),day).toUpperCase(java.util.Locale.getDefault()):"—";
            double degrees=w.optDouble("temperature_2m");if(p.getBoolean("fahrenheit",false))degrees=degrees*9/5+32;
            String temp=w.has("temperature_2m")?Math.round(degrees)+"°":"—";
            txt(c,v,"subtitle",calendar?Data.nextEvent(c):name.equals("QuickTransparent")?temp+"  "+condition:condition);
            txt(c,v,"value",temp);
            v.setViewVisibility(R.id.weather_icon,w.has("temperature_2m")&&!calendar?android.view.View.VISIBLE:android.view.View.GONE);
            int icon=WeatherIcons.resource(c,w.optInt("weather_code"),day);if(icon!=0)v.setImageViewResource(R.id.weather_icon,icon);
            if(name.equals("QuickCard"))v.setViewVisibility(R.id.quick_temperature,calendar?android.view.View.GONE:android.view.View.VISIBLE);
            v.setOnClickPendingIntent(R.id.clock,action(c,name,id,"page"));
        } else if(group.equals("Photos")) {
            String photo=p.getString("photo:"+id,null);
            if(photo!=null) try {
                if(photo.startsWith("[")) {JSONArray photos=new JSONArray(photo);photo=photos.getString(Math.floorMod(p.getInt("page:"+id,0),photos.length()));}
                ImageDecoder.Source src=ImageDecoder.createSource(c.getContentResolver(),Uri.parse(photo));
                Bitmap b=ImageDecoder.decodeBitmap(src,(decoder,info,s)->{
                    android.util.Size size=info.getSize();
                    float ratio=Math.min(1f,448f/Math.max(size.getWidth(),size.getHeight()));
                    decoder.setTargetSize(Math.max(1,Math.round(size.getWidth()*ratio)),Math.max(1,Math.round(size.getHeight()*ratio)));
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
                if(name.equals("PhotoCircle")) {
                    int side=Math.min(b.getWidth(),b.getHeight()); Bitmap circle=Bitmap.createBitmap(side,side,Bitmap.Config.ARGB_8888);
                    Canvas cv=new Canvas(circle); Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColor(Color.WHITE); cv.drawCircle(side/2f,side/2f,side/2f,paint);
                    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN)); cv.drawBitmap(b,-(b.getWidth()-side)/2f,-(b.getHeight()-side)/2f,paint); b=circle;
                }
                v.setImageViewBitmap(R.id.image,b); v.setViewVisibility(R.id.subtitle,android.view.View.GONE);
                v.setOnClickPendingIntent(R.id.image,action(c,name,id,"page"));
            } catch(Exception e) { txt(c,v,"subtitle","Choose your photo again"); }
        } else if(name.equals("ScreenFace")) {
            long time=Data.screenTime(c),goal=p.getInt("screen_goal",180)*60000L;
            txt(c,v,"detail",time<0?"Usage access needed":Logic.duration(time));
            v.setImageViewResource(R.id.face,time>=goal?R.drawable.icon_screen_time_overtime_expression:R.drawable.icon_screen_time_expression);
        }
        Captions.flush(c,v);
        CharSequence numeric=values.remove(v);
        Geometry.fitNumber(c,v,name,numeric,box[0],box[1]);
        return v;
    }
    public static final class AnalogBold extends Widgets {} public static final class AnalogScale extends Widgets {}
    public static final class DigitalSquare extends Widgets {} public static final class DigitalWide extends Widgets {}
    public static final class World extends Widgets {} public static final class Date extends Widgets {}
    public static final class QuickTransparent extends Widgets {} public static final class QuickCard extends Widgets {}
    public static final class PhotoCircle extends Widgets {} public static final class PhotoRectangle extends Widgets {}
    public static final class Compass extends Widgets {} public static final class CompassSmall extends Widgets {}
    public static final class Steps extends Widgets {} public static final class StepsProgress extends Widgets {}
    public static final class StepsDaily extends Widgets {} public static final class StepsStreak extends Widgets {}
    public static final class ScreenTime extends Widgets {} public static final class ScreenDuration extends Widgets {}
    public static final class ScreenShape extends Widgets {} public static final class ScreenFace extends Widgets {}
    public static final class Media extends Widgets {} public static final class Battery extends Widgets {}
    public static final class BatterySmall extends Widgets {} public static final class Countdown extends Widgets {}
    public static final class BreathRelax extends Widgets {} public static final class BreathFocus extends Widgets {}
    public static final class BreathCalm extends Widgets {} public static final class WeatherCircle extends Widgets {}
    public static final class WeatherSquare extends Widgets {} public static final class WeatherWide extends Widgets {}
    public static final class WeatherCombo extends Widgets {}
}
