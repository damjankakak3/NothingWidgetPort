package dev.dipa.widgets;

import android.app.*;
import android.os.*;
import android.content.*;
import android.hardware.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.animation.*;

public final class SessionActivity extends Activity implements SensorEventListener {
    private String kind;
    private SensorManager sensors;
    private TextView bearing;
    private SessionView visual;
    private ValueAnimator animator;
    private float heading;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);kind=getIntent().getStringExtra("kind");if(kind==null)kind="Compass";
        boolean compass=kind.startsWith("Compass");
        LinearLayout page=UI.page(this,Registry.find(kind)[1]);
        bearing=UI.text(this,page,compass?"Finding north…":"Ready when you are",28);
        visual=new SessionView(this,compass);
        page.addView(visual,new LinearLayout.LayoutParams(-1,UI.dp(this,320)));
        if(compass) {
            UI.text(this,page,"Move the phone in a figure eight to calibrate. The reading is magnetic north.",15);
            sensors=getSystemService(SensorManager.class);
            if(sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)==null)bearing.setText("Rotation-vector sensor unavailable");
        } else {
            UI.text(this,page,kind.equals("BreathRelax")?"Inhale 4 seconds, hold 7, exhale 8.":kind.equals("BreathFocus")?"Inhale 4 seconds, hold 4, exhale 4, hold 4.":"Inhale 4 seconds, exhale 4.",16);
            UI.button(this,page,"Start / pause",()->{if(animator!=null&&animator.isRunning())stopAnimation();else startAnimation();});
        }
        UI.button(this,page,"Close",this::finish);
    }
    @Override protected void onResume() {
        super.onResume();
        if(sensors!=null) {Sensor s=sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);if(s!=null)sensors.registerListener(this,s,SensorManager.SENSOR_DELAY_UI);}
    }
    @Override protected void onPause() {
        if(sensors!=null) {sensors.unregisterListener(this);Data.prefs(this).edit().putFloat("compass",heading).apply();Widgets.updateGroup(this,"Compass");}
        stopAnimation();super.onPause();
    }
    private void startAnimation() {
        int cycle=kind.equals("BreathRelax")?19:kind.equals("BreathFocus")?16:8;
        animator=ValueAnimator.ofFloat(0,cycle);animator.setDuration(cycle*1000L);animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new android.view.animation.LinearInterpolator());
        animator.addUpdateListener(a->{
            float t=(Float)a.getAnimatedValue(),scale;
            String phase;
            if(t<4){phase="Inhale";scale=t/4;}
            else if(kind.equals("BreathRelax")&&t<11){phase="Hold";scale=1;}
            else if(kind.equals("BreathFocus")&&t<8){phase="Hold";scale=1;}
            else if(kind.equals("BreathFocus")&&t>=12){phase="Hold";scale=0;}
            else {phase="Exhale";float start=kind.equals("BreathRelax")?11:kind.equals("BreathFocus")?8:4;scale=1-(t-start)/(kind.equals("BreathRelax")?8:4);}
            if(!bearing.getText().toString().equals(phase))bearing.setText(phase);
            visual.scale=scale;visual.invalidate();
        });
        if(!ValueAnimator.areAnimatorsEnabled()){bearing.setText("Animations are disabled in Android settings");return;}
        animator.start();
    }
    private void stopAnimation(){if(animator!=null){animator.cancel();animator=null;}}
    @Override public void onSensorChanged(SensorEvent e) {
        float[] rotation=new float[9],orientation=new float[3];SensorManager.getRotationMatrixFromVector(rotation,e.values);SensorManager.getOrientation(rotation,orientation);
        heading=(float)((Math.toDegrees(orientation[0])+360)%360);visual.angle=heading;visual.invalidate();
        String direction=new String[]{"N","NE","E","SE","S","SW","W","NW"}[(int)((heading+22.5)/45)%8];
        bearing.setText(Math.round(heading)+"° "+direction);
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy) {}
    private static final class SessionView extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final boolean compass;
        float angle,scale;
        SessionView(Context c,boolean compass){super(c);this.compass=compass;setContentDescription(compass?"Compass dial":"Breathing guide");}
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(x,y)*.8f;
            p.setColor(Color.rgb(38,37,35));c.drawCircle(x,y,r,p);
            if(compass){c.save();c.rotate(-angle,x,y);p.setColor(Color.rgb(225,218,212));p.setStrokeWidth(UI.dp(getContext(),2));
                for(int i=0;i<60;i++){c.drawLine(x,y-r*.87f,x,y-r*(i%5==0?.7f:.8f),p);c.rotate(6,x,y);}
                p.setColor(Color.rgb(207,63,54));Path triangle=new Path();triangle.moveTo(x,y-r*.7f);triangle.lineTo(x-r*.13f,y);triangle.lineTo(x+r*.13f,y);triangle.close();c.drawPath(triangle,p);c.restore();
            }else{p.setColor(Color.rgb(207,63,54));float rad=r*(.25f+.55f*scale);c.drawCircle(x,y,rad,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(UI.dp(getContext(),2));p.setColor(Color.rgb(245,243,240));c.drawCircle(x,y,r*.85f,p);p.setStyle(Paint.Style.FILL);}
        }
    }
}
