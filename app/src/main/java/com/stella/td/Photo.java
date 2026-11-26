package com.stella.td;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.io.ByteArrayOutputStream;

/** Profile photos: square-cropped, shrunk to a small JPEG and stored as Base64 text so it syncs with the save. */
final class Photo {
    private Photo() {}

    static final int SIZE = 160;

    static String encode(Bitmap src) {
        if (src == null) return null;
        int w = src.getWidth(), h = src.getHeight(), s = Math.min(w, h);
        if (s <= 0) return null;
        Bitmap sq = Bitmap.createBitmap(src, (w - s) / 2, (h - s) / 2, s, s);
        Bitmap sm = Bitmap.createScaledBitmap(sq, SIZE, SIZE, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        sm.compress(Bitmap.CompressFormat.JPEG, 78, out);
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    static Bitmap decode(String b64) {
        if (b64 == null || b64.isEmpty()) return null;
        try {
            byte[] d = Base64.decode(b64, Base64.NO_WRAP);
            return BitmapFactory.decodeByteArray(d, 0, d.length);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
