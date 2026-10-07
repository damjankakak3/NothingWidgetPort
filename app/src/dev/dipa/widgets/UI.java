package dev.dipa.widgets;
import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
public final class UI {
    private UI() {}
    public static int dp(Context c,float n) { return Math.round(n*c.getResources().getDisplayMetrics().density); }
    public static LinearLayout page(Activity a,String title) {
        a.setTitle(title);
        ScrollView scroll=new ScrollView(a); scroll.setFillViewport(true);
        LinearLayout column=new LinearLayout(a); column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(a,20),dp(a,12),dp(a,20),dp(a,24));
        scroll.addView(column); a.setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
            column.setPadding(dp(a,20)+bars.left,dp(a,12)+bars.top,dp(a,20)+bars.right,dp(a,24)+bars.bottom);
            return insets;
        });
        return column;
    }
    public static TextView text(Activity a,LinearLayout parent,String content,int size) {
        TextView t=new TextView(a); t.setText(content); t.setTextSize(size); t.setPadding(0,dp(a,8),0,dp(a,12));
        parent.addView(t,new LinearLayout.LayoutParams(-1,-2)); return t;
    }
    public static Button button(Activity a,LinearLayout parent,String label,Runnable action) {
        Button b=new Button(a); b.setText(label); b.setAllCaps(false); b.setMinHeight(dp(a,48));
        b.setOnClickListener(v->action.run()); parent.addView(b,new LinearLayout.LayoutParams(-1,-2)); return b;
    }
    public static EditText field(Activity a,LinearLayout parent,String hint,String value) {
        EditText f=new EditText(a); f.setHint(hint); f.setText(value); f.setSingleLine(true); f.setMinHeight(dp(a,48)); parent.addView(f,new LinearLayout.LayoutParams(-1,-2)); return f;
    }
}
