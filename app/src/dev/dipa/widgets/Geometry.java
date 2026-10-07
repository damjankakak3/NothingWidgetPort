package dev.dipa.widgets;

import android.content.Context;
import android.os.Bundle;
import android.appwidget.AppWidgetManager;
import android.util.SizeF;
import android.util.TypedValue;
import android.widget.RemoteViews;
import java.util.*;

/** Uses actual host size pairs; portrait width must not be combined with landscape height. */
public final class Geometry {
    private Geometry() {}
    public static List<SizeF> sizes(String kind,Bundle options) {
        LinkedHashSet<SizeF> sizes=new LinkedHashSet<>();
        if(options!=null) {
            ArrayList<SizeF> supplied=options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,SizeF.class);
            if(supplied!=null)for(SizeF size:supplied)
                if(size.getWidth()>0&&size.getHeight()>0)sizes.add(size);
            if(sizes.isEmpty()) {
                int minW=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,0),maxW=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,minW);
                int minH=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,0),maxH=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,minH);
                // Legacy options describe paired portrait and landscape bounds.
                if(minW>0&&maxH>0)sizes.add(new SizeF(minW,maxH));
                if(maxW>0&&minH>0)sizes.add(new SizeF(maxW,minH));
            }
        }
        if(sizes.isEmpty()) {
            String[] def=Registry.find(kind);
            sizes.add(new SizeF(Integer.parseInt(def[3])*86,Integer.parseInt(def[4])*86));
        }
        return new ArrayList<>(sizes);
    }
    public static boolean circular(String kind) {
        return kind.equals("AnalogBold")||kind.equals("AnalogScale")||kind.equals("BatterySmall")
            ||kind.equals("WeatherCircle")||kind.equals("PhotoCircle");
    }
    public static float[] content(String kind,SizeF host) {
        float w=host.getWidth(),h=host.getHeight();
        if(circular(kind)){float side=Math.min(w,h);return new float[]{side,side};}
        return new float[]{w,h};
    }
    public static void apply(Context c,RemoteViews v,String kind,float widthDp,float heightDp) {
        v.setViewLayoutWidth(R.id.content,widthDp,TypedValue.COMPLEX_UNIT_DIP);
        v.setViewLayoutHeight(R.id.content,heightDp,TypedValue.COMPLEX_UNIT_DIP);
        float density=c.getResources().getDisplayMetrics().density;
        float width=widthDp*density,height=heightDp*density,side=Math.min(width,height);
        // Initialize native clocks immediately as well as on window attachment.
        // This makes previews and launcher inflation follow the same date/time path.
        if(kind.equals("DigitalSquare")||kind.equals("DigitalWide")||kind.equals("World")||kind.equals("Date")||kind.startsWith("Quick")) {
            String zone=java.util.TimeZone.getDefault().getID();
            v.setString(R.id.clock,"setTimeZone",zone);
            if(kind.equals("DigitalSquare")||kind.equals("DigitalWide")||kind.equals("QuickCard"))v.setString(R.id.minutes,"setTimeZone",zone);
            if(kind.equals("Date"))v.setString(R.id.date_weekday,"setTimeZone",zone);
        }
        if(kind.equals("DigitalSquare")) {
            float font=side*158f/452f;
            v.setTextViewTextSize(R.id.clock,TypedValue.COMPLEX_UNIT_PX,font);
            v.setTextViewTextSize(R.id.minutes,TypedValue.COMPLEX_UNIT_PX,font);
            v.setViewPadding(R.id.clock,0,Math.round(side*21f/452f),0,0);
            v.setViewLayoutMargin(R.id.minutes,RemoteViews.MARGIN_TOP,side*16f/452f,TypedValue.COMPLEX_UNIT_PX);
        } else if(kind.equals("DigitalWide")) {
            float scale=Math.min(width/452f,height/205f);
            v.setTextViewTextSize(R.id.clock,TypedValue.COMPLEX_UNIT_PX,105f*scale);
            v.setTextViewTextSize(R.id.minutes,TypedValue.COMPLEX_UNIT_PX,105f*scale);
            v.setTextViewTextSize(R.id.time_colon,TypedValue.COMPLEX_UNIT_PX,105f*scale);
            v.setViewLayoutMargin(R.id.time_colon,RemoteViews.MARGIN_LEFT,16f*scale,TypedValue.COMPLEX_UNIT_PX);
            v.setViewLayoutMargin(R.id.time_colon,RemoteViews.MARGIN_RIGHT,16f*scale,TypedValue.COMPLEX_UNIT_PX);
            v.setViewPadding(R.id.clock,0,Math.round(16f*scale),0,0);
            v.setViewPadding(R.id.minutes,0,Math.round(16f*scale),0,0);
        } else if(kind.equals("Date")) {
            // Real Headline family keeps the date ticking natively at midnight.
            float day=side*335f/451f,weekday=side*52f/451f;
            v.setTextViewTextSize(R.id.clock,TypedValue.COMPLEX_UNIT_PX,day);
            v.setTextViewTextSize(R.id.date_weekday,TypedValue.COMPLEX_UNIT_PX,weekday);
            v.setViewPadding(R.id.date_weekday,0,Math.round(side*12f/172f),Math.round(side*16f/172f),0);
        } else if(kind.equals("World")) {
            float ratio=side/452f;
            v.setViewPadding(R.id.content,Math.round(42f*ratio),Math.round(42f*ratio),Math.round(42f*ratio),Math.round(32f*ratio));
            v.setTextViewTextSize(R.id.clock,TypedValue.COMPLEX_UNIT_PX,105f*ratio);
            v.setTextViewTextSize(R.id.subtitle,TypedValue.COMPLEX_UNIT_PX,53f*ratio);
            v.setViewLayoutMargin(R.id.clock,RemoteViews.MARGIN_TOP,side*.58f,TypedValue.COMPLEX_UNIT_PX);
        } else if(kind.equals("QuickCard")) {
            float ratio=Math.min(width/452f,height/451f),font=53f*ratio;
            v.setViewPadding(R.id.content,Math.round(42f*ratio),Math.round(36f*ratio),Math.round(42f*ratio),0);
            for(int id:new int[]{R.id.clock,R.id.minutes,R.id.subtitle,R.id.value})v.setTextViewTextSize(id,TypedValue.COMPLEX_UNIT_PX,font);
            v.setViewLayoutWidth(R.id.weather_icon,58f*ratio,TypedValue.COMPLEX_UNIT_PX);v.setViewLayoutHeight(R.id.weather_icon,58f*ratio,TypedValue.COMPLEX_UNIT_PX);
            v.setViewLayoutMargin(R.id.weather_icon,RemoteViews.MARGIN_END,18f*ratio,TypedValue.COMPLEX_UNIT_PX);
        } else if(kind.equals("QuickTransparent")) {
            float font=Math.min(height*53f/207f,width*.065f);
            v.setViewPadding(R.id.content,Math.round(12*density),0,Math.round(12*density),0);
            v.setViewPadding(R.id.clock,Math.round(3*density),0,Math.round(3*density),0);
            v.setViewPadding(R.id.quick_weather_row,Math.round(4*density),0,Math.round(4*density),0);
            v.setTextViewTextSize(R.id.clock,TypedValue.COMPLEX_UNIT_PX,font);v.setTextViewTextSize(R.id.subtitle,TypedValue.COMPLEX_UNIT_PX,font);
        } else if(kind.equals("ScreenFace")) {
            v.setViewLayoutHeight(R.id.face,side*.58f,TypedValue.COMPLEX_UNIT_PX);
        }
        // Values use each layout's original size, fitted in BOTH dimensions; no global42sp override.
    }
    public static void fitNumber(Context c,RemoteViews v,String kind,CharSequence text,float widthDp,float heightDp) {
        if(text==null||kind.startsWith("Quick"))return;
        float density=c.getResources().getDisplayMetrics().density;
        float availableWidth=Math.max(20,widthDp-24)*density;
        float availableHeight=Math.max(20,heightDp-52)*density;
        float proportion=kind.equals("Countdown")?.42f:kind.startsWith("Compass")?.30f:kind.startsWith("Weather")?.32f:.25f;
        float font=Math.min(widthDp,heightDp)*density*proportion;
        if(kind.startsWith("Weather")&&widthDp/heightDp>2)font=heightDp*density*.34f;
        android.graphics.Paint p=new android.graphics.Paint();p.setTypeface(android.graphics.Typeface.create("nothingdot57aligned",0));p.setTextSize(font);
        android.graphics.Paint.FontMetrics metrics=p.getFontMetrics();
        float fit=Math.min(1,Math.min(availableWidth/Math.max(1,p.measureText(text.toString())),availableHeight/Math.max(1,metrics.descent-metrics.ascent)));
        v.setTextViewTextSize(R.id.value,TypedValue.COMPLEX_UNIT_PX,Math.max(8*density,font*fit));
    }
}
