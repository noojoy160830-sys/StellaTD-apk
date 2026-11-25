package com.stella.td;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Game-themed input dialogs built from native widgets (so the phone keyboard works). */
final class Dialogs {
    private Dialogs() {}

    /** return an error message to keep the dialog open, or null to close it */
    interface Done {
        String ok(String[] values);
    }

    static final int TEXT = 0, PASSWORD = 1, NUMBER = 2;
    static Typeface body = Typeface.DEFAULT_BOLD, title = Typeface.DEFAULT_BOLD;

    private static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static GradientDrawable round(int[] colors, float radius, int strokeW, int strokeColor) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors);
        g.setCornerRadius(radius);
        if (strokeW > 0) g.setStroke(strokeW, strokeColor);
        return g;
    }

    private static TextView label(Context ctx, String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        return t;
    }

    private static TextView button(Context ctx, String s, int top, int bottom) {
        TextView b = label(ctx, s, 18, 0xFFFFFFFF, body);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12));
        b.setBackground(round(new int[]{top, bottom}, dp(ctx, 24), dp(ctx, 2), 0xFF0B0520));
        return b;
    }

    private static Dialog frame(Context ctx, LinearLayout box) {
        Dialog d = new Dialog(ctx);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout((int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.9f), LinearLayout.LayoutParams.WRAP_CONTENT);
        }
        return d;
    }

    private static LinearLayout card(Context ctx, String heading, String message) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(ctx, 22);
        box.setPadding(pad, pad, pad, pad);
        box.setBackground(round(new int[]{0xFF34207A, 0xFF150A38}, dp(ctx, 26), dp(ctx, 3), 0xFF9B7BFF));
        TextView t = label(ctx, heading, 26, 0xFFFFD23F, title);
        t.setGravity(Gravity.CENTER);
        box.addView(t);
        if (message != null) {
            TextView m = label(ctx, message, 14, 0xFFD8D0F0, body);
            m.setGravity(Gravity.CENTER);
            m.setPadding(0, dp(ctx, 6), 0, 0);
            box.addView(m);
        }
        return box;
    }

    private static LinearLayout.LayoutParams wide(Context ctx, int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(ctx, topMargin), 0, 0);
        return lp;
    }

    /** dialog with one labelled input per hint */
    static void ask(Context ctx, String heading, String message, String[] hints, int[] types, String[] prefill, String okLabel, Done done) {
        LinearLayout box = card(ctx, heading, message);
        final EditText[] fields = new EditText[hints.length];
        for (int i = 0; i < hints.length; i++) {
            TextView lab = label(ctx, hints[i], 14, 0xFFB8AEE0, body);
            lab.setLayoutParams(wide(ctx, i == 0 ? 16 : 10));
            box.addView(lab);
            EditText e = new EditText(ctx);
            e.setHint(hints[i]);
            e.setSingleLine(true);
            e.setTextColor(0xFFFFFFFF);
            e.setHintTextColor(0x66FFFFFF);
            e.setTypeface(body);
            e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            e.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12));
            e.setBackground(round(new int[]{0xFF0E0828, 0xFF1A1040}, dp(ctx, 16), dp(ctx, 2), 0xFF5B4BB0));
            int t = types[i];
            if (t == PASSWORD) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            else if (t == NUMBER) e.setInputType(InputType.TYPE_CLASS_NUMBER);
            else e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            if (prefill != null && i < prefill.length && prefill[i] != null) e.setText(prefill[i]);
            e.setLayoutParams(wide(ctx, 4));
            fields[i] = e;
            box.addView(e);
        }
        final TextView err = label(ctx, "", 14, 0xFFFF8A8A, body);
        err.setGravity(Gravity.CENTER);
        err.setLayoutParams(wide(ctx, 10));
        err.setVisibility(View.GONE);
        box.addView(err);
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(wide(ctx, 16));
        TextView cancel = button(ctx, L.t("ยกเลิก", "Cancel"), 0xFF6A6290, 0xFF3A3360);
        TextView ok = button(ctx, okLabel, 0xFFFFB45C, 0xFFE0560F);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        half.setMargins(0, 0, dp(ctx, 6), 0);
        cancel.setLayoutParams(half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        half2.setMargins(dp(ctx, 6), 0, 0, 0);
        ok.setLayoutParams(half2);
        row.addView(cancel);
        row.addView(ok);
        box.addView(row);
        final Dialog d = frame(ctx, box);
        cancel.setOnClickListener(v -> d.dismiss());
        ok.setOnClickListener(v -> {
            String[] vals = new String[fields.length];
            for (int i = 0; i < fields.length; i++) vals[i] = fields[i].getText().toString();
            String e = done.ok(vals);
            if (e == null) d.dismiss();
            else {
                err.setText(e);
                err.setVisibility(View.VISIBLE);
            }
        });
        d.show();
    }

    /** yes / no confirmation */
    static void confirm(Context ctx, String heading, String message, String yes, Runnable onYes) {
        LinearLayout box = card(ctx, heading, message);
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(wide(ctx, 18));
        TextView cancel = button(ctx, L.t("ยกเลิก", "Cancel"), 0xFF6A6290, 0xFF3A3360);
        TextView ok = button(ctx, yes, 0xFFFF8A8A, 0xFFC0303A);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        half.setMargins(0, 0, dp(ctx, 6), 0);
        cancel.setLayoutParams(half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        half2.setMargins(dp(ctx, 6), 0, 0, 0);
        ok.setLayoutParams(half2);
        row.addView(cancel);
        row.addView(ok);
        box.addView(row);
        final Dialog d = frame(ctx, box);
        cancel.setOnClickListener(v -> d.dismiss());
        ok.setOnClickListener(v -> {
            d.dismiss();
            onYes.run();
        });
        d.show();
    }
}
