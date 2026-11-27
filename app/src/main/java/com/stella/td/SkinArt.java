package com.stella.td;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/** Paints a planet sprite in a skin style: colour ramp on brightness, then small overlays. */
final class SkinArt {
    private SkinArt() {}

    /** dark → mid → light colour ramp for each style */
    static final int[][] RAMP = {
            {0xFF4A2A00, 0xFFD89A20, 0xFFFFF2A8},   // golden
            {0xFF10140E, 0xFF4E5A44, 0xFFB8C2A0},   // graveyard
            {0xFF7A88A8, 0xFFE4ECFA, 0xFFFFFFFF},   // white
            {0xFF0A2A40, 0xFF20E0A0, 0xFFE0A0FF},   // aurora
            {0xFF300408, 0xFFD02030, 0xFFFFB0A0},   // red
            {0xFF100818, 0xFF3A2A60, 0xFFFFD23F},   // elite
            {0xFF1E0838, 0xFFA040FF, 0xFFFFD8FF},   // shiny purple
            null,                                   // shiny (boosted original)
            {0xFF000000, 0xFF8A0010, 0xFFFF5040},   // madness
            {0xFF200A40, 0xFF8A3AD8, 0xFFFFE070},   // king
            {0xFF5A3000, 0xFFFFC030, 0xFFFFFFFF},   // anniversary
    };

    /** the Hell Moon: the moon in molten red/orange with glowing cracks */
    static Bitmap hellMoon(Bitmap moon) {
        int w = moon.getWidth(), h = moon.getHeight();
        int[] px = new int[w * h];
        moon.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            int c = px[i], a = c >>> 24;
            if (a == 0) continue;
            float lum = (0.3f * ((c >> 16) & 255) + 0.59f * ((c >> 8) & 255) + 0.11f * (c & 255)) / 255f;
            // dark craters glow like lava, bright crust stays charred red
            float x = (i % w) / (float) w, y = (i / w) / (float) h;
            float vein = (float) Math.abs(Math.sin(x * 23 + y * 11) * Math.sin(y * 19 - x * 7));
            int rgb = lum < 0.45f || vein > 0.93f ? mix(0xFFFF6A10, 0xFFFFE070, Math.max(0, 0.45f - lum) * 2 + (vein > 0.93f ? 0.5f : 0))
                    : mix(0xFF3A0606, 0xFFB0201A, (lum - 0.45f) * 1.8f);
            px[i] = (a << 24) | (rgb & 0xFFFFFF);
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, w, 0, 0, w, h);
        return out;
    }

    /** artifact moons: brightness mapped onto each artifact's colour ramp, plus a small motif */
    static final int[][] ART_RAMP = {null,
            {0xFF0A2A48, 0xFF5EC8FF, 0xFFF0FFFF},   // frost
            {0xFF1A0838, 0xFF8A4BFF, 0xFFE6D0FF},   // storm
            {0xFF4A2A00, 0xFFE0A020, 0xFFFFF4B0},   // gold
            {0xFF05000E, 0xFF2A0A4A, 0xFFB86BFF}};  // void

    static Bitmap tintMoon(Bitmap moon, int m) {
        int w = moon.getWidth(), h = moon.getHeight();
        int[] px = new int[w * h];
        moon.getPixels(px, 0, w, 0, 0, w, h);
        int[] ramp = ART_RAMP[m];
        for (int i = 0; i < px.length; i++) {
            int c = px[i], a = c >>> 24;
            if (a == 0) continue;
            float lum = (0.3f * ((c >> 16) & 255) + 0.59f * ((c >> 8) & 255) + 0.11f * (c & 255)) / 255f;
            if (m == Defs.MOON_VOID) lum = 1f - lum * 0.85f;
            int rgb = lum < 0.5f ? mix(ramp[0], ramp[1], lum * 2) : mix(ramp[1], ramp[2], (lum - 0.5f) * 2);
            px[i] = (a << 24) | (rgb & 0xFFFFFF);
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, w, 0, 0, w, h);
        Canvas c = new Canvas(out);
        float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) * 0.38f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        switch (m) {
            case Defs.MOON_FROST:
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(r * 0.06f);
                p.setColor(0xEEFFFFFF);
                for (int k = 0; k < 3; k++) {
                    double a = k * Math.PI / 3;
                    float dx = (float) Math.cos(a) * r * 0.45f, dy = (float) Math.sin(a) * r * 0.45f;
                    c.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, p);
                }
                break;
            case Defs.MOON_STORM: {
                p.setColor(0xFFFFF27A);
                android.graphics.Path b = new android.graphics.Path();
                b.moveTo(cx + r * 0.15f, cy - r * 0.6f);
                b.lineTo(cx - r * 0.3f, cy + r * 0.08f);
                b.lineTo(cx, cy + r * 0.08f);
                b.lineTo(cx - r * 0.15f, cy + r * 0.6f);
                b.lineTo(cx + r * 0.32f, cy - r * 0.1f);
                b.lineTo(cx + r * 0.02f, cy - r * 0.1f);
                b.close();
                c.drawPath(b, p);
                break;
            }
            case Defs.MOON_GOLD:
                sparkles(c, cx, cy, r, 0xFFFFFFFF, 4);
                break;
            case Defs.MOON_VOID:
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(r * 0.08f);
                p.setColor(0xCCB86BFF);
                c.drawCircle(cx, cy, r * 1.02f, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF000000);
                c.drawCircle(cx, cy, r * 0.32f, p);
                break;
            default:
        }
        return out;
    }

    /** evolved form: richer colours, a glowing core and a ring of living crystals */
    static Bitmap evolved(Bitmap src, int planet) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        float[] hsv = new float[3];
        for (int i = 0; i < px.length; i++) {
            int c = px[i], a = c >>> 24;
            if (a == 0) continue;
            Art.rgbToHsv(c, hsv);
            if (planet == Defs.EARTH) {
                // greens get lusher, blues turn into deep glowing teal
                if (hsv[0] > 60 && hsv[0] < 170) hsv[0] = 125;
                else if (hsv[0] >= 170 && hsv[0] < 260) hsv[0] = 185;
            } else {
                // pull every hue toward the evolution's colour, keeping a little variation
                float tgt = Defs.evoHue(planet), d = ((hsv[0] - tgt + 540) % 360) - 180;
                hsv[0] = (tgt + d * 0.25f + 360) % 360;
                if (planet == Defs.MOON) hsv[2] *= 0.8f;
                hsv[1] = Math.max(hsv[1], 0.45f);
            }
            hsv[1] = Math.min(1f, hsv[1] * 1.35f + 0.08f);
            hsv[2] = Math.min(1f, hsv[2] * 1.12f + 0.04f);
            px[i] = (a << 24) | (Art.hsvToRgb(hsv) & 0xFFFFFF);
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, w, 0, 0, w, h);
        Canvas c = new Canvas(out);
        float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) * 0.38f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // energy ring
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r * 0.07f);
        int ringCol = Art.hsvToRgb(new float[]{Defs.evoHue(planet), 0.55f, 1f}) | 0xFF000000;
        p.setColor((ringCol & 0xFFFFFF) | 0xCC000000);
        c.drawOval(new android.graphics.RectF(cx - r * 1.25f, cy - r * 0.36f, cx + r * 1.25f, cy + r * 0.36f), p);
        p.setStrokeWidth(r * 0.03f);
        p.setColor(0xFFE8FFE8);
        c.drawOval(new android.graphics.RectF(cx - r * 1.25f, cy - r * 0.36f, cx + r * 1.25f, cy + r * 0.36f), p);
        // crystal shards on the ring
        p.setStyle(Paint.Style.FILL);
        for (int k = 0; k < 5; k++) {
            double ang = k * Math.PI * 2 / 5 + 0.4;
            float x = cx + (float) Math.cos(ang) * r * 1.25f, y = cy + (float) Math.sin(ang) * r * 0.36f;
            android.graphics.Path d = new android.graphics.Path();
            float s = r * 0.13f;
            d.moveTo(x, y - s * 1.6f);
            d.lineTo(x + s, y);
            d.lineTo(x, y + s * 1.2f);
            d.lineTo(x - s, y);
            d.close();
            p.setColor(k % 2 == 0 ? ringCol : 0xFFFFFFFF);
            c.drawPath(d, p);
        }
        sparkles(c, cx, cy, r, 0xEEFFFFFF, 4);
        return out;
    }

    static Bitmap make(Bitmap src, int style, int planet) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        float[] hsv = new float[3];
        int[] ramp = RAMP[style];
        for (int i = 0; i < px.length; i++) {
            int c = px[i], a = c >>> 24;
            if (a == 0) continue;
            int rgb;
            if (ramp == null) {
                Art.rgbToHsv(c, hsv);
                hsv[0] = (hsv[0] + 25) % 360;
                hsv[1] = Math.min(1f, hsv[1] * 1.5f + 0.1f);
                hsv[2] = Math.min(1f, hsv[2] * 1.25f + 0.05f);
                rgb = Art.hsvToRgb(hsv);
            } else {
                float lum = (0.3f * ((c >> 16) & 255) + 0.59f * ((c >> 8) & 255) + 0.11f * (c & 255)) / 255f;
                if (style == Skins.AURORA) {
                    // flowing bands: shift along with the row
                    float band = (float) Math.sin((i / w) * 0.09f + (i % w) * 0.03f) * 0.5f + 0.5f;
                    lum = Math.min(1f, lum * 0.7f + band * 0.4f);
                } else if (style == Skins.MADNESS) {
                    lum = Math.min(1f, (float) Math.pow(lum, 1.6f) * 1.3f);
                }
                rgb = lum < 0.5f ? mix(ramp[0], ramp[1], lum * 2) : mix(ramp[1], ramp[2], (lum - 0.5f) * 2);
            }
            px[i] = (a << 24) | (rgb & 0xFFFFFF);
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, w, 0, 0, w, h);
        overlay(new Canvas(out), w, h, style);
        return out;
    }

    static void overlay(Canvas c, float w, float h, int style) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cx = w / 2, cy = h / 2, r = Math.min(w, h) * 0.38f;
        switch (style) {
            case Skins.GRAVEYARD: {
                // cracks and a little tombstone
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(r * 0.03f);
                p.setColor(0xCC0A0A08);
                c.drawLine(cx - r * 0.5f, cy - r * 0.2f, cx - r * 0.1f, cy + r * 0.1f, p);
                c.drawLine(cx - r * 0.1f, cy + r * 0.1f, cx + r * 0.2f, cy - r * 0.05f, p);
                c.drawLine(cx + r * 0.1f, cy + r * 0.4f, cx + r * 0.45f, cy + r * 0.2f, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF8A9080);
                c.drawRoundRect(new android.graphics.RectF(cx + r * 0.05f, cy - r * 0.55f, cx + r * 0.3f, cy - r * 0.2f), r * 0.1f, r * 0.1f, p);
                break;
            }
            case Skins.ELITE:
            case Skins.KING:
                Art.crown(c, cx, cy - r * 1.02f, r * 0.75f);
                break;
            case Skins.ANNIV:
                Art.crown(c, cx, cy - r * 1.02f, r * 0.8f);
                sparkles(c, cx, cy, r, 0xFFFFFFFF, 6);
                p.setColor(0xFFFF4F8B);
                p.setTextSize(r * 0.34f);
                p.setTextAlign(Paint.Align.CENTER);
                c.drawText("1st", cx, cy + r * 0.95f, p);
                break;
            case Skins.SHINY:
            case Skins.SHINY_PURPLE:
            case Skins.GOLDEN:
                sparkles(c, cx, cy, r, 0xEEFFFFFF, style == Skins.GOLDEN ? 3 : 5);
                break;
            case Skins.MADNESS: {
                // a staring eye
                p.setColor(0xFFFFE0D0);
                c.drawOval(new android.graphics.RectF(cx - r * 0.32f, cy - r * 0.16f, cx + r * 0.32f, cy + r * 0.16f), p);
                p.setColor(0xFFD00010);
                c.drawCircle(cx, cy, r * 0.13f, p);
                p.setColor(0xFF000000);
                c.drawCircle(cx, cy, r * 0.06f, p);
                break;
            }
            default:
        }
    }

    static void sparkles(Canvas c, float cx, float cy, float r, int col, int n) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(col);
        p.setStrokeWidth(r * 0.035f);
        for (int i = 0; i < n; i++) {
            double a = i * 2.4 + 0.6;
            float x = cx + (float) Math.cos(a) * r * (0.35f + 0.1f * (i % 3)), y = cy + (float) Math.sin(a) * r * (0.35f + 0.1f * (i % 2));
            float s = r * (0.07f + 0.03f * (i % 2));
            c.drawLine(x - s, y, x + s, y, p);
            c.drawLine(x, y - s, x, y + s, p);
        }
    }

    static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
