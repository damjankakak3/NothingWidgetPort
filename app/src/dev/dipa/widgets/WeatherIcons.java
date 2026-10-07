package dev.dipa.widgets;
import android.content.Context;

/** Map public WMO weather codes to original Nothing artwork, not OEM proprietary codes. */
public final class WeatherIcons {
    private WeatherIcons() {}
    public static String name(int code,boolean day) {
        if(code==0)return day?"sunny":"clear_night";
        if(code==1||code==2)return day?"partly_cloudy":"cloudy_night";
        if(code==3)return "overcast";
        if(code==45||code==48)return "fog";
        if((code>=71&&code<=77)||code==85||code==86)return "snow";
        if(code>=95)return "thunderstorms";
        if(code>=51&&code<=82)return "rainy";
        return "cloudy";
    }
    public static int resource(Context c,int code,boolean day) {
        return c.getResources().getIdentifier("ic_widget_"+name(code,day)+"_neutral","drawable",c.getPackageName());
    }
}
