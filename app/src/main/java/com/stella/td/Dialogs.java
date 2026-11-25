package com.stella.td;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

/** Native Android input dialogs (the game itself has no text fields). */
final class Dialogs {
    private Dialogs() {}

    interface Done {
        void ok(String[] values);
    }

    static final int TEXT = 0, PASSWORD = 1, NUMBER = 2;

    /** shows a dialog with one input per hint; calls done with the typed values when OK is pressed */
    static void ask(Context ctx, String title, String message, String[] hints, int[] types, String[] prefill, String okLabel, Done done) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * ctx.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        final EditText[] fields = new EditText[hints.length];
        for (int i = 0; i < hints.length; i++) {
            EditText e = new EditText(ctx);
            e.setHint(hints[i]);
            e.setSingleLine(true);
            int t = types[i];
            if (t == PASSWORD) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            else if (t == NUMBER) e.setInputType(InputType.TYPE_CLASS_NUMBER);
            else e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            if (prefill != null && i < prefill.length && prefill[i] != null) e.setText(prefill[i]);
            fields[i] = e;
            box.addView(e);
        }
        AlertDialog.Builder b = new AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setView(box)
                .setPositiveButton(okLabel, (d, w) -> {
                    String[] v = new String[fields.length];
                    for (int i = 0; i < fields.length; i++) v[i] = fields[i].getText().toString();
                    done.ok(v);
                })
                .setNegativeButton(L.t("ยกเลิก", "Cancel"), null);
        if (message != null) b.setMessage(message);
        b.show();
    }

    /** yes / no confirmation */
    static void confirm(Context ctx, String title, String message, String yes, Runnable onYes) {
        new AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(yes, (d, w) -> onYes.run())
                .setNegativeButton(L.t("ยกเลิก", "Cancel"), null)
                .show();
    }
}
