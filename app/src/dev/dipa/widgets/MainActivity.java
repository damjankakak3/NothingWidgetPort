package dev.dipa.widgets;
import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity {
    private BluetoothBatteries batteryReceiver;
    @Override public void onCreate(Bundle b) { super.onCreate(b); show(); }
    @Override protected void onResume(){super.onResume();batteryReceiver=BluetoothBatteries.listen(this);BluetoothBatteries.requestRefresh(this);RefreshJob.reconcile(this);}
    @Override protected void onPause(){if(batteryReceiver!=null){unregisterReceiver(batteryReceiver);batteryReceiver=null;}super.onPause();}
    private void show() {
        LinearLayout page=UI.page(this,"NothingWidget");
        UI.text(this,page,"Your home screen, a little Nothing.",28);
        UI.text(this,page,"Choose a widget to preview or add. Setup and permissions stay with the features that need them.",15);
        LinkedHashMap<String,java.util.List<String[]>> groups=new LinkedHashMap<>();
        for(String[] item:Registry.ITEMS) groups.computeIfAbsent(item[2],k->new ArrayList<>()).add(item);
        for(Map.Entry<String,java.util.List<String[]>> entry:groups.entrySet()) {
            LinearLayout previews=new LinearLayout(this); previews.setOrientation(LinearLayout.VERTICAL); previews.setVisibility(View.GONE);
            Button heading=UI.button(this,page,entry.getKey()+"  ·  "+entry.getValue().size(),()->{
                if(previews.getVisibility()==View.VISIBLE) { previews.removeAllViews(); previews.setVisibility(View.GONE); return; }
                previews.setVisibility(View.VISIBLE);
                for(String[] item:entry.getValue()) showPreview(previews,item);
            });
            heading.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            page.addView(previews,new LinearLayout.LayoutParams(-1,-2));
        }
        UI.text(this,page,"Weather uses Open-Meteo. Other widget data stays on this phone. Compass and breathing open a live session when tapped. Device batteries depend on Android reporting them.",13);
    }
    private void showPreview(LinearLayout parent,String[] item) {
        String name=item[0];
        UI.text(this,parent,item[1],18);
        FrameLayout preview=new FrameLayout(this);
        int columns=Integer.parseInt(item[3]),rows=Integer.parseInt(item[4]);
        int width=UI.dp(this,columns>=3?300:164),height=UI.dp(this,rows==1?90:164);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(width,height); lp.gravity=Gravity.CENTER_HORIZONTAL; lp.bottomMargin=UI.dp(this,8);
        parent.addView(preview,lp);
        try {
            Bundle size=new Bundle();
            int previewWidth=columns>=3?300:164,previewHeight=rows==1?90:164;
            size.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,previewWidth);size.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,previewWidth);
            size.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,previewHeight);size.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,previewHeight);
            preview.addView(Widgets.render(this,name,-1,size).apply(this,preview),new FrameLayout.LayoutParams(-1,-1));
            preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        } catch(Exception e) { TextView error=new TextView(this);error.setText("Preview unavailable");preview.addView(error); android.util.Log.e("NothingWidget","Preview "+name,e); }
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); parent.addView(actions);
        Button setup=new Button(this); setup.setText("Set up"); setup.setAllCaps(false);
        setup.setOnClickListener(v->startActivity(new Intent(this,ConfigureActivity.class).putExtra("kind",name)));
        actions.addView(setup,new LinearLayout.LayoutParams(0,UI.dp(this,52),1));
        Button add=new Button(this); add.setText("Add widget"); add.setAllCaps(false);
        add.setOnClickListener(v->{
            AppWidgetManager manager=AppWidgetManager.getInstance(this);
            if(manager.isRequestPinAppWidgetSupported()) manager.requestPinAppWidget(new ComponentName(this,Widgets.class.getName()+"$"+name),null,null);
            else Toast.makeText(this,"Add NothingWidget from your launcher’s widget picker",Toast.LENGTH_LONG).show();
        });
        actions.addView(add,new LinearLayout.LayoutParams(0,UI.dp(this,52),1));
    }
}
