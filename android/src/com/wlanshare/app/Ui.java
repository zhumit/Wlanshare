package com.wlanshare.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Android 端视觉与布局助手（全部代码构建，不依赖 XML 布局）。 */
public final class Ui {

    public static final int BG = 0xFFF4F6FB;
    public static final int CARD = 0xFFFFFFFF;
    public static final int TEXT = 0xFF151C2C;
    public static final int SUB = 0xFF6B7488;
    public static final int MUTED = 0xFF9AA3B5;
    public static final int ACCENT = 0xFF2B6BF3;
    public static final int ACCENT_SOFT = 0xFFEAF0FF;
    public static final int GREEN = 0xFF1FBF6B;
    public static final int RED = 0xFFEE4B5E;
    public static final int AMBER = 0xFFF2A33C;
    public static final int BORDER = 0xFFE5E9F2;
    public static final int SOFT = 0xFFF6F8FC;

    public static final int[] AVATARS = {
            0xFF4C7DF0, 0xFF7A5CF0, 0xFF14B8A6, 0xFFF2994A, 0xFFEB5757, 0xFF2D9CDB
    };

    private Ui() {
    }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable round(int fill, float radius, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(dp(c, radius));
        return d;
    }

    public static GradientDrawable roundStroke(int fill, int stroke, float radius, Context c) {
        GradientDrawable d = round(fill, radius, c);
        d.setStroke(dp(c, 1), stroke);
        return d;
    }

    public static GradientDrawable circle(int fill, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        return d;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = column(c);
        l.setBackground(roundStroke(CARD, BORDER, 14, c));
        int p = dp(c, 14);
        l.setPadding(p, p, p, p);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lpWeight(int w, int h, float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.weight = weight;
        return p;
    }

    public static void margins(View v, int l, int t, int r, int b) {
        ViewGroup.LayoutParams raw = v.getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) raw).setMargins(l, t, r, b);
            v.setLayoutParams(raw);
        }
    }

    public static void marginDp(Context c, View v, float l, float t, float r, float b) {
        margins(v, dp(c, l), dp(c, t), dp(c, r), dp(c, b));
    }

    public static void click(View v, final Runnable r) {
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                r.run();
            }
        });
    }

    /** 小胶囊按钮。 */
    public static TextView pill(Context c, String label, int fg, int bg, final Runnable action) {
        TextView t = text(c, label, 12, fg, true);
        t.setBackground(round(bg, 9, c));
        int px = dp(c, 12);
        int py = dp(c, 5);
        t.setPadding(px, py, px, py);
        t.setGravity(Gravity.CENTER);
        click(t, action);
        return t;
    }

    /** 主按钮。 */
    public static TextView button(Context c, String label, int bg, int fg, final Runnable action) {
        TextView t = text(c, label, 14, fg, true);
        t.setBackground(round(bg, 10, c));
        int px = dp(c, 16);
        int py = dp(c, 10);
        t.setPadding(px, py, px, py);
        t.setGravity(Gravity.CENTER);
        click(t, action);
        return t;
    }

    /** 圆形头像（首字母）。 */
    public static TextView avatar(Context c, String id, String nick, int sizeDp) {
        TextView t = text(c, initial(nick), sizeDp * 0.42f, Color.WHITE, true);
        t.setBackground(circle(avatarColor(id), c));
        t.setGravity(Gravity.CENTER);
        int s = dp(c, sizeDp);
        t.setLayoutParams(lp(s, s));
        return t;
    }

    public static int avatarColor(String id) {
        int h = 0;
        String s = id == null ? "" : id;
        for (int i = 0; i < s.length(); i++) {
            h = 31 * h + s.charAt(i);
        }
        return AVATARS[Math.abs(h) % AVATARS.length];
    }

    public static String initial(String nick) {
        if (nick == null || nick.trim().isEmpty()) {
            return "?";
        }
        char ch = nick.trim().charAt(0);
        return String.valueOf(Character.toUpperCase(ch));
    }

    public static ImageView icon(Context c, int res, int color, int sizeDp) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setColorFilter(color);
        int s = dp(c, sizeDp);
        iv.setLayoutParams(lp(s, s));
        return iv;
    }

    public static TextView divider(Context c) {
        TextView v = new TextView(c);
        v.setBackgroundColor(BORDER);
        v.setLayoutParams(lp(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.7f))));
        return v;
    }

    public static View spacer(Context c, float h) {
        View v = new View(c);
        v.setLayoutParams(lp(1, dp(c, h)));
        return v;
    }

    public static String timeText(long ts) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(ts);
        java.util.Calendar now = java.util.Calendar.getInstance();
        boolean today = c.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR)
                && c.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR);
        String hm = String.format("%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY),
                c.get(java.util.Calendar.MINUTE));
        if (today) {
            return hm;
        }
        return (c.get(java.util.Calendar.MONTH) + 1) + "月" + c.get(java.util.Calendar.DAY_OF_MONTH) + "日 " + hm;
    }
}
