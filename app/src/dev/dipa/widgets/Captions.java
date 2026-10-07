package dev.dipa.widgets;

import android.content.Context;
import android.graphics.*;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

/** Original Lettera labels rasterized only when widget data changes.
 * Android intentionally blocks APK fonts in foreign RemoteViews hosts.
 * Live clock digits continue using the ROM's native Nothing dot family.
 */
public final class Captions {
    private static Typeface lettera;
    private static final java.util.Map<RemoteViews,Batch> pending=java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final class Batch {
        final int width;final java.util.Map<String,CharSequence> labels=new java.util.LinkedHashMap<>();
        Batch(int width){this.width=width;}
    }
    private Captions() {}
    public static void defaults(Context c, RemoteViews views, int widthDp) {
        String layout=c.getResources().getResourceEntryName(views.getLayoutId());
        Batch batch=new Batch(widthDp);
        for(String[] caption:CaptionData.ITEMS) if(caption[0].equals(layout))
            batch.labels.put(caption[1],caption[3]);
        pending.put(views,batch);
    }
    public static boolean draw(Context c,RemoteViews views,String name,CharSequence value,int widthDp) {
        Batch batch=pending.get(views);
        if(batch!=null&&batch.labels.containsKey(name)){batch.labels.put(name,value);return true;}
        return false;
    }
    public static void flush(Context c,RemoteViews views) {
        Batch batch=pending.remove(views);if(batch==null)return;
        for(java.util.Map.Entry<String,CharSequence> label:batch.labels.entrySet())
            paint(c,views,label.getKey(),label.getValue(),batch.width);
    }
    private static void paint(Context c,RemoteViews views,String name,CharSequence value,int widthDp) {
        String layout=c.getResources().getResourceEntryName(views.getLayoutId());
        int fontSize=12;
        for(String[] caption:CaptionData.ITEMS) if(caption[0].equals(layout)&&caption[1].equals(name)) {fontSize=Integer.parseInt(caption[2]);break;}
        int imageId=Widgets.resource(c,"id",name+"_image"),textId=Widgets.resource(c,"id",name+"_text");
        if(imageId==0||textId==0) return;
        if(lettera==null) lettera=c.getResources().getFont(R.font.lettera_mono_ll_regular);
        float density=c.getResources().getDisplayMetrics().density;
        float sp=TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,fontSize,c.getResources().getDisplayMetrics());
        int width=Math.max(16,Math.min(800,Math.round(Math.max(40,widthDp-24)*density)));
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);paint.setTypeface(lettera);paint.setTextSize(sp);
        // Tint is resolved by the host XML, so theme changes keep the labels legible.
        paint.setColor(Color.WHITE);
        Paint.FontMetrics metrics=paint.getFontMetrics();int height=Math.max(1,(int)Math.ceil(metrics.descent-metrics.ascent));
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);
        String text=value==null?"":value.toString();
        android.text.TextPaint textPaint=new android.text.TextPaint(paint);
        String fitted=TextUtils.ellipsize(text,textPaint,width,TextUtils.TruncateAt.END).toString();
        canvas.drawText(fitted,Math.max(0,(width-paint.measureText(fitted))/2),-metrics.ascent,paint);
        views.setImageViewBitmap(imageId,bitmap);views.setContentDescription(imageId,text);
        views.setViewVisibility(textId,View.GONE);views.setViewVisibility(imageId,View.VISIBLE);
    }
}
