package dev.dipa.widgets;

import android.content.Context;
import android.graphics.*;
import java.time.LocalDate;

/** One bitmap per data update, never a home-screen animation loop. */
public final class Charts {
    private Charts() {}
    public static Bitmap points(Context c,double progress) {
        int w=420,h=96;Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        int lit=(int)Math.round(Math.max(0,Math.min(1,progress))*42);
        for(int row=0;row<3;row++)for(int col=0;col<14;col++) {
            p.setColor(row*14+col<lit?Color.rgb(207,63,54):Color.rgb(134,130,125));
            canvas.drawCircle(15+col*30,16+row*32,8,p);
        }
        return b;
    }
    public static Bitmap week(Context c) {
        Bitmap b=Bitmap.createBitmap(420,96,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        int goal=Data.prefs(c).getInt("step_goal",6000);
        for(int day=0;day<7;day++) {
            int count=Data.steps(c,LocalDate.now().minusDays(6-day).toString());
            p.setColor(Color.rgb(134,130,125));canvas.drawCircle(30+day*60,48,20,p);
            p.setColor(Color.rgb(207,63,54));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(8);
            canvas.drawArc(new RectF(10+day*60,28,50+day*60,68),-90,360*Math.min(1,count/(float)Math.max(1,goal)),false,p);p.setStyle(Paint.Style.FILL);
        }
        return b;
    }
}
