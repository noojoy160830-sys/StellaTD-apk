package com.stella.td;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/** Procedurally draws all cartoon sprites into bitmaps (no image files needed). */
final class Art {
    private Art() {}

    static final int OUTLINE = 0xFF1B1035;

    static Paint fill() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        return p;
    }

    static Paint stroke(float w, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setColor(color);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        return p;
    }

    // ------------------------------------------------------------------ background
    static Bitmap background(int w, int h) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = fill();
        p.setShader(new LinearGradient(0, 0, w * 0.3f, h,
                new int[]{0xFF06021A, 0xFF150A3C, 0xFF0C1240, 0xFF050318}, new float[]{0f, 0.4f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        Random r = new Random(7);
        // milky-way band (diagonal)
        float bx0 = -w * 0.2f, by0 = h * 0.15f, bx1 = w * 1.2f, by1 = h * 0.75f;
        for (int i = 0; i < 14; i++) {
            float t = i / 13f, x = bx0 + (bx1 - bx0) * t, y = by0 + (by1 - by0) * t, rad = w * (0.22f + r.nextFloat() * 0.15f);
            int col = i % 3 == 0 ? 0x2A8A6BFF : (i % 3 == 1 ? 0x22FF7AC8 : 0x2240C8FF);
            p.setShader(new RadialGradient(x, y, rad, col, col & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, rad, p);
        }
        p.setShader(null);
        for (int i = 0; i < 1600; i++) {
            float t = r.nextFloat();
            float off = (float) r.nextGaussian() * w * 0.09f;
            float x = bx0 + (bx1 - bx0) * t - off * 0.5f, y = by0 + (by1 - by0) * t + off;
            p.setColor((r.nextInt(3) == 0 ? 0x55FFE6F4 : 0x44C8D8FF));
            c.drawCircle(x, y, 0.5f + r.nextFloat() * 1.2f, p);
        }
        // nebula clouds
        int[] neb = {0x44A43CFF, 0x3333C6E0, 0x38FF4F9B, 0x2A4F6BFF, 0x30FF8A5C, 0x2836E0B0};
        for (int i = 0; i < 12; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, rad = (0.18f + r.nextFloat() * 0.3f) * w;
            int col = neb[i % neb.length];
            p.setShader(new RadialGradient(x, y, rad, new int[]{col, (col & 0x00FFFFFF) | 0x14000000, col & 0x00FFFFFF},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, rad, p);
        }
        // dark dust lanes
        for (int i = 0; i < 5; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, rad = (0.1f + r.nextFloat() * 0.15f) * w;
            p.setShader(new RadialGradient(x, y, rad, 0x55020008, 0x00020008, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, rad, p);
        }
        p.setShader(null);
        // stars
        int[] sc = {0xFFFFFFFF, 0xFFFFF2C4, 0xFFC4DCFF, 0xFFFFD0E8};
        for (int i = 0; i < 420; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, s = r.nextFloat();
            p.setColor(sc[r.nextInt(sc.length)]);
            p.setAlpha((int) (90 + s * 165));
            c.drawCircle(x, y, 0.5f + s * s * 2.2f, p);
        }
        // bright stars with glow and cross flare
        for (int i = 0; i < 9; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, s = w * (0.003f + r.nextFloat() * 0.003f);
            int col = sc[r.nextInt(sc.length)];
            p.setShader(new RadialGradient(x, y, s * 6, (col & 0x00FFFFFF) | 0x66000000, col & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, s * 6, p);
            p.setShader(null);
            p.setColor(0xCCFFFFFF);
            c.drawPath(starPath(x, y, s * 3.5f, s * 0.3f, 4), p);
            c.drawCircle(x, y, s, p);
        }
        // a distant planet peeking in
        float px = w * 0.95f, py = h * 0.9f, pr = w * 0.16f;
        p.setShader(new RadialGradient(px, py, pr * 1.25f, new int[]{0x00000000, 0x00000000, 0x553FA9FF, 0x003FA9FF},
                new float[]{0f, 0.78f, 0.82f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(px, py, pr * 1.25f, p);
        p.setShader(new RadialGradient(px - pr * 0.6f, py - pr * 0.6f, pr * 2f, new int[]{0xFF4F7BD8, 0xFF1A2A6A, 0xFF05081A},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(px, py, pr, p);
        return b;
    }

    // ------------------------------------------------------------------ helpers

    static void ball(Canvas c, float cx, float cy, float r, int light, int dark) {
        Paint p = fill();
        p.setShader(new RadialGradient(cx - r * 0.35f, cy - r * 0.4f, r * 1.5f, light, dark, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, p);
    }

    static Path circlePath(float cx, float cy, float r) {
        Path path = new Path();
        path.addCircle(cx, cy, r, Path.Direction.CW);
        return path;
    }

    static void shade(Canvas c, float cx, float cy, float r) {
        Paint p = fill();
        p.setColor(0x33000020);
        c.drawCircle(cx + r * 0.38f, cy + r * 0.38f, r, p);
        p.setColor(0x40FFFFFF);
        c.drawOval(new RectF(cx - r * 0.65f, cy - r * 0.75f, cx - r * 0.15f, cy - r * 0.45f), p);
    }

    static void outline(Canvas c, float cx, float cy, float r, float w) {
        c.drawCircle(cx, cy, r, stroke(w, OUTLINE));
    }

    static void craters(Canvas c, float cx, float cy, float r, int color, int seed, int n) {
        Random rd = new Random(seed);
        Paint p = fill();
        p.setColor(color);
        for (int i = 0; i < n; i++) {
            float a = rd.nextFloat() * 6.28f, d = rd.nextFloat() * r * 0.75f, s = r * (0.08f + rd.nextFloat() * 0.14f);
            c.drawCircle(cx + (float) Math.cos(a) * d, cy + (float) Math.sin(a) * d, s, p);
        }
    }

    static void bands(Canvas c, float cx, float cy, float r, int[] colors) {
        Paint p = fill();
        float h = r * 2f / colors.length;
        for (int i = 0; i < colors.length; i++) {
            p.setColor(colors[i]);
            float y = cy - r + i * h;
            c.drawRect(cx - r, y + h * 0.15f, cx + r, y + h * 0.85f, p);
        }
    }

    // ------------------------------------------------------------------ planets (detailed)
    static Paint grad(Shader s) {
        Paint p = fill();
        p.setShader(s);
        return p;
    }

    /** soft glowing atmosphere ring just outside the planet */
    static void atmosphere(Canvas c, float cx, float cy, float r, int color) {
        int a = color & 0x00FFFFFF;
        c.drawCircle(cx, cy, r * 1.16f, grad(new RadialGradient(cx, cy, r * 1.16f,
                new int[]{a, a, a | 0x99000000, a}, new float[]{0f, 0.82f, 0.87f, 1f}, Shader.TileMode.CLAMP)));
    }

    /** day/night shading, rim light and specular highlight; call while clipped to the planet */
    static void lighting(Canvas c, float cx, float cy, float r) {
        c.drawCircle(cx, cy, r, grad(new RadialGradient(cx - r * 0.45f, cy - r * 0.5f, r * 2.05f,
                new int[]{0x00000000, 0x00000000, 0x77060018, 0xE603000C}, new float[]{0f, 0.36f, 0.66f, 1f}, Shader.TileMode.CLAMP)));
        c.drawCircle(cx - r * 0.35f, cy - r * 0.4f, r * 0.55f, grad(new RadialGradient(cx - r * 0.35f, cy - r * 0.4f, r * 0.55f,
                0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)));
        Paint rim = stroke(r * 0.07f, 0x55FFFFFF);
        c.drawArc(new RectF(cx - r * 0.95f, cy - r * 0.95f, cx + r * 0.95f, cy + r * 0.95f), 185, 95, false, rim);
    }

    static void speckle(Canvas c, float cx, float cy, float r, int seed, int n, int light, int dark) {
        Random rd = new Random(seed);
        Paint p = fill();
        for (int i = 0; i < n; i++) {
            float a = rd.nextFloat() * 6.283f, d = (float) Math.sqrt(rd.nextFloat()) * r;
            p.setColor(rd.nextBoolean() ? light : dark);
            c.drawCircle(cx + (float) Math.cos(a) * d, cy + (float) Math.sin(a) * d, r * (0.008f + rd.nextFloat() * 0.022f), p);
        }
    }

    /** irregular blob (continents, maria, regions) */
    static Path blobPath(float x, float y, float rad, int seed, float rough) {
        Random rd = new Random(seed);
        int n = 18;
        float[] rr = new float[n];
        for (int i = 0; i < n; i++) rr[i] = rad * (1f - rough + rd.nextFloat() * rough * 2f);
        Path p = new Path();
        for (int i = 0; i <= n; i++) {
            int k = i % n;
            float r0 = (rr[k] + rr[(k + 1) % n] + rr[(k + n - 1) % n]) / 3f;
            double a = k * Math.PI * 2 / n;
            float px = x + (float) Math.cos(a) * r0, py = y + (float) Math.sin(a) * r0 * 0.8f;
            if (i == 0) p.moveTo(px, py);
            else p.lineTo(px, py);
        }
        p.close();
        return p;
    }

    static void blob(Canvas c, float x, float y, float rad, int seed, float rough, int color) {
        Paint p = fill();
        p.setColor(color);
        c.drawPath(blobPath(x, y, rad, seed, rough), p);
    }

    /** wavy horizontal band across the planet (gas giants) */
    static void wavyBand(Canvas c, float cx, float cy, float r, float y, float th, float amp, float freq, float phase, int color) {
        Path p = new Path();
        int steps = 24;
        for (int i = 0; i <= steps; i++) {
            float x = cx - r + 2 * r * i / steps;
            float yy = y + (float) Math.sin(phase + i * freq) * amp;
            if (i == 0) p.moveTo(x, yy - th / 2);
            else p.lineTo(x, yy - th / 2);
        }
        for (int i = steps; i >= 0; i--) {
            float x = cx - r + 2 * r * i / steps;
            float yy = y + (float) Math.sin(phase + 1.3f + i * freq) * amp;
            p.lineTo(x, yy + th / 2);
        }
        p.close();
        Paint f = fill();
        f.setColor(color);
        c.drawPath(p, f);
    }

    /** crater with dark floor and lit rim */
    static void crater(Canvas c, float x, float y, float s, int dark, int light) {
        Paint p = fill();
        p.setColor(dark);
        c.drawOval(new RectF(x - s, y - s * 0.85f, x + s, y + s * 0.85f), p);
        Paint rim = stroke(s * 0.28f, light);
        c.drawArc(new RectF(x - s, y - s * 0.85f, x + s, y + s * 0.85f), 20, 140, false, rim);
        Paint sh = stroke(s * 0.22f, 0x33000000);
        c.drawArc(new RectF(x - s, y - s * 0.85f, x + s, y + s * 0.85f), 200, 140, false, sh);
    }

    static void craterField(Canvas c, float cx, float cy, float r, int seed, int n, int dark, int light) {
        Random rd = new Random(seed);
        for (int i = 0; i < n; i++) {
            float a = rd.nextFloat() * 6.283f, d = (float) Math.sqrt(rd.nextFloat()) * r * 0.9f;
            float s = r * (0.04f + rd.nextFloat() * rd.nextFloat() * 0.2f);
            crater(c, cx + (float) Math.cos(a) * d, cy + (float) Math.sin(a) * d, s, dark, light);
        }
    }

    static void rings(Canvas c, float cx, float cy, float r, float tilt, boolean front, int[] cols, float[] radii, float w) {
        c.save();
        c.rotate(tilt, cx, cy);
        for (int i = 0; i < cols.length; i++) {
            float rr = r * radii[i];
            RectF o = new RectF(cx - rr, cy - rr * 0.28f, cx + rr, cy + rr * 0.28f);
            Paint p = stroke(r * w, cols[i]);
            p.setStrokeCap(Paint.Cap.BUTT);
            if (front) c.drawArc(o, 0, 180, false, p);
            else c.drawArc(o, 180, 180, false, p);
        }
        c.restore();
    }

    // ---------------------------------------------------------------- unique satellites
    /** Smart Satellite: sleek AI core, long angled solar wings and a big dish */
    static void smartSat(Canvas c, float cx, float cy, float size) {
        float s = size;
        Paint o = stroke(s * 0.018f, OUTLINE);
        c.drawCircle(cx, cy, s * 0.46f, grad(new RadialGradient(cx, cy, s * 0.46f, 0x3348F0FF, 0x0048F0FF, Shader.TileMode.CLAMP)));
        // wings: three tilted panels per side
        for (int side = -1; side <= 1; side += 2) {
            c.save();
            c.rotate(side * -12, cx, cy);
            // strut
            Paint st = stroke(s * 0.022f, 0xFFB8C8D8);
            c.drawLine(cx + side * s * 0.1f, cy, cx + side * s * 0.16f, cy, st);
            for (int k = 0; k < 3; k++) {
                float x0 = cx + side * (s * 0.16f + k * s * 0.095f);
                RectF pnl = new RectF(Math.min(x0, x0 + side * s * 0.085f), cy - s * 0.085f, Math.max(x0, x0 + side * s * 0.085f), cy + s * 0.085f);
                c.drawRect(pnl, grad(new LinearGradient(pnl.left, pnl.top, pnl.right, pnl.bottom, 0xFF5B8CFF, 0xFF1E2A8A, Shader.TileMode.CLAMP)));
                Paint gl = stroke(s * 0.006f, 0x8848F0FF);
                c.drawLine(pnl.centerX(), pnl.top, pnl.centerX(), pnl.bottom, gl);
                c.drawLine(pnl.left, pnl.centerY(), pnl.right, pnl.centerY(), gl);
                c.drawRect(pnl, o);
            }
            c.restore();
        }
        // dish
        Paint f = fill();
        c.drawLine(cx + s * 0.04f, cy - s * 0.1f, cx + s * 0.12f, cy - s * 0.26f, stroke(s * 0.02f, 0xFFB8C8D8));
        RectF dish = new RectF(cx + s * 0.02f, cy - s * 0.38f, cx + s * 0.24f, cy - s * 0.22f);
        c.save();
        c.rotate(-30, dish.centerX(), dish.centerY());
        f.setColor(0xFFF2F6FF);
        c.drawArc(dish, 0, 180, true, f);
        c.drawArc(dish, 0, 180, true, o);
        f.setColor(0xFFFFD23F);
        c.drawCircle(dish.centerX(), dish.centerY() + s * 0.01f, s * 0.018f, f);
        c.restore();
        // octagonal core
        android.graphics.Path oct = new android.graphics.Path();
        float r = s * 0.13f;
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 8 + k * Math.PI / 4;
            float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
            if (k == 0) oct.moveTo(x, y); else oct.lineTo(x, y);
        }
        oct.close();
        c.drawPath(oct, grad(new LinearGradient(cx - r, cy - r, cx + r, cy + r, 0xFFFFFFFF, 0xFF6FD8E8, Shader.TileMode.CLAMP)));
        c.drawPath(oct, o);
        // AI eye
        c.drawCircle(cx, cy, s * 0.06f, grad(new RadialGradient(cx, cy, s * 0.06f, 0xFFFFFFFF, 0xFF00C8FF, Shader.TileMode.CLAMP)));
        c.drawCircle(cx, cy, s * 0.06f, stroke(s * 0.012f, 0xFF0A3A5A));
        f.setColor(0xFF0A3A5A);
        c.drawCircle(cx, cy, s * 0.022f, f);
        // gold trim
        c.drawLine(cx - r * 0.7f, cy + r * 0.75f, cx + r * 0.7f, cy + r * 0.75f, stroke(s * 0.014f, 0xFFFFD23F));
    }

    /** Savior Satellite: pearl body, feather wings and a golden halo */
    static void saviorSat(Canvas c, float cx, float cy, float size) {
        float s = size;
        Paint o = stroke(s * 0.016f, OUTLINE);
        c.drawCircle(cx, cy, s * 0.47f, grad(new RadialGradient(cx, cy, s * 0.47f, 0x667CFFE0, 0x007CFFE0, Shader.TileMode.CLAMP)));
        // feather wings: stacked curved blades
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < 3; k++) {
                android.graphics.Path w = new android.graphics.Path();
                float len = s * (0.36f - k * 0.06f), y0 = cy - s * 0.02f + k * s * 0.07f;
                w.moveTo(cx + side * s * 0.08f, y0);
                w.quadTo(cx + side * len * 0.7f, y0 - s * (0.17f - k * 0.04f), cx + side * len, y0 - s * (0.06f - k * 0.03f));
                w.quadTo(cx + side * len * 0.6f, y0 + s * 0.03f, cx + side * s * 0.08f, y0 + s * 0.05f);
                w.close();
                c.drawPath(w, grad(new LinearGradient(cx, y0, cx + side * len, y0, 0xFFFFFFFF, k == 0 ? 0xFFB8FFF0 : 0xFF8FE8FF, Shader.TileMode.CLAMP)));
                c.drawPath(w, o);
            }
        }
        // pearl body
        RectF body = new RectF(cx - s * 0.1f, cy - s * 0.15f, cx + s * 0.1f, cy + s * 0.17f);
        c.drawOval(body, grad(new RadialGradient(cx - s * 0.03f, cy - s * 0.06f, s * 0.2f, 0xFFFFFFFF, 0xFFC8E8F0, Shader.TileMode.CLAMP)));
        c.drawOval(body, o);
        // glowing heart gem
        android.graphics.Path gem = new android.graphics.Path();
        gem.moveTo(cx, cy - s * 0.05f);
        gem.lineTo(cx + s * 0.045f, cy + s * 0.01f);
        gem.lineTo(cx, cy + s * 0.08f);
        gem.lineTo(cx - s * 0.045f, cy + s * 0.01f);
        gem.close();
        c.drawPath(gem, grad(new LinearGradient(cx, cy - s * 0.05f, cx, cy + s * 0.08f, 0xFFB8FFF0, 0xFF20C8A0, Shader.TileMode.CLAMP)));
        c.drawPath(gem, stroke(s * 0.01f, 0xFF0A4A3A));
        // halo
        RectF halo = new RectF(cx - s * 0.14f, cy - s * 0.31f, cx + s * 0.14f, cy - s * 0.23f);
        c.drawOval(halo, stroke(s * 0.03f, 0xFFFFE27A));
        c.drawOval(halo, stroke(s * 0.012f, 0xFFFFFFFF));
    }

    /** Commander Satellite: armoured hex hull, missile racks and a radar mast */
    static void commanderSat(Canvas c, float cx, float cy, float size) {
        float s = size;
        Paint o = stroke(s * 0.018f, OUTLINE);
        Paint f = fill();
        c.drawCircle(cx, cy, s * 0.46f, grad(new RadialGradient(cx, cy, s * 0.46f, 0x44FF3B3B, 0x00FF3B3B, Shader.TileMode.CLAMP)));
        // short armoured fins
        for (int side = -1; side <= 1; side += 2) {
            android.graphics.Path fin = new android.graphics.Path();
            fin.moveTo(cx + side * s * 0.12f, cy - s * 0.08f);
            fin.lineTo(cx + side * s * 0.33f, cy - s * 0.04f);
            fin.lineTo(cx + side * s * 0.33f, cy + s * 0.08f);
            fin.lineTo(cx + side * s * 0.12f, cy + s * 0.1f);
            fin.close();
            c.drawPath(fin, grad(new LinearGradient(cx, cy - s * 0.08f, cx, cy + s * 0.1f, 0xFF6A7488, 0xFF2A303C, Shader.TileMode.CLAMP)));
            c.drawPath(fin, o);
            c.drawLine(cx + side * s * 0.15f, cy + s * 0.01f, cx + side * s * 0.3f, cy + s * 0.02f, stroke(s * 0.018f, 0xFFFF3B3B));
            // missile rack: two missiles under each fin
            for (int k = 0; k < 2; k++) {
                float mx = cx + side * (s * 0.19f + k * s * 0.08f), my = cy + s * 0.16f;
                RectF m = new RectF(mx - s * 0.022f, my - s * 0.06f, mx + s * 0.022f, my + s * 0.06f);
                f.setColor(0xFFE8ECF2);
                c.drawRoundRect(m, s * 0.02f, s * 0.02f, f);
                f.setColor(0xFFFF3B3B);
                c.drawRect(m.left, m.bottom - s * 0.03f, m.right, m.bottom, f);
                c.drawRoundRect(m, s * 0.02f, s * 0.02f, o);
            }
        }
        // radar mast
        c.drawLine(cx, cy - s * 0.12f, cx, cy - s * 0.33f, stroke(s * 0.02f, 0xFF9AA3B5));
        RectF radar = new RectF(cx - s * 0.1f, cy - s * 0.37f, cx + s * 0.1f, cy - s * 0.29f);
        f.setColor(0xFFB8C0CC);
        c.drawOval(radar, f);
        c.drawOval(radar, o);
        f.setColor(0xFFFF3B3B);
        c.drawCircle(cx, cy - s * 0.38f, s * 0.022f, f);
        // hex hull
        android.graphics.Path hex = new android.graphics.Path();
        float r = s * 0.15f;
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3;
            float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r * 0.9f;
            if (k == 0) hex.moveTo(x, y); else hex.lineTo(x, y);
        }
        hex.close();
        c.drawPath(hex, grad(new LinearGradient(cx - r, cy - r, cx + r, cy + r, 0xFF8A94A8, 0xFF2A303C, Shader.TileMode.CLAMP)));
        c.drawPath(hex, o);
        // red command star
        f.setColor(0xFFFF3B3B);
        c.drawPath(starPath(cx, cy, s * 0.07f, s * 0.03f, 5), f);
        c.drawPath(starPath(cx, cy, s * 0.07f, s * 0.03f, 5), stroke(s * 0.008f, 0xFF4A0A0A));
    }

    static Bitmap planet(int type, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float cx = size / 2f, cy = size / 2f;
        if (type == Defs.SATELLITE) {
            satellite(c, cx, cy, size);
            return b;
        }
        if (type == Defs.SMART_SAT) { smartSat(c, cx, cy, size); return b; }
        if (type == Defs.SAVIOR_SAT) { saviorSat(c, cx, cy, size); return b; }
        if (type == Defs.COMMANDER) { commanderSat(c, cx, cy, size); return b; }
        if (type == Defs.ION_DRONE) { ionDrone(c, cx, cy, size); return b; }
        if (type == Defs.MARTIN) { martinWorld(c, cx, cy, size); return b; }
        if (type == Defs.BLACK_HOLE) { blackHole(c, cx, cy, size); return b; }
        if (type == Defs.COMET) {
            // icy nucleus with a glowing tail streaming to the lower left
            Path tail = new Path();
            tail.moveTo(cx + size * 0.08f, cy - size * 0.14f);
            tail.quadTo(cx - size * 0.2f, cy + size * 0.05f, cx - size * 0.48f, cy + size * 0.42f);
            tail.quadTo(cx - size * 0.05f, cy + size * 0.2f, cx + size * 0.14f, cy + size * 0.08f);
            tail.close();
            c.drawPath(tail, grad(new LinearGradient(cx, cy, cx - size * 0.45f, cy + size * 0.4f, new int[]{0xEEB8F6FF, 0x885EC8FF, 0x003B6BFF}, null, Shader.TileMode.CLAMP)));
            Path tail2 = new Path();
            tail2.moveTo(cx + size * 0.02f, cy - size * 0.08f);
            tail2.quadTo(cx - size * 0.25f, cy + size * 0.15f, cx - size * 0.36f, cy + size * 0.47f);
            tail2.quadTo(cx - size * 0.12f, cy + size * 0.2f, cx + size * 0.1f, cy + size * 0.05f);
            tail2.close();
            c.drawPath(tail2, grad(new LinearGradient(cx, cy, cx - size * 0.35f, cy + size * 0.45f, 0xCCFFB86B, 0x00FF6A3D, Shader.TileMode.CLAMP)));
        }
        if (type == Defs.PULSAR) {
            // twin radiation beams through the neutron star
            c.save();
            c.rotate(-30, cx, cy);
            Path beam = new Path();
            beam.moveTo(cx - size * 0.04f, cy);
            beam.lineTo(cx - size * 0.12f, cy - size * 0.5f);
            beam.lineTo(cx + size * 0.12f, cy - size * 0.5f);
            beam.lineTo(cx + size * 0.04f, cy);
            beam.lineTo(cx + size * 0.12f, cy + size * 0.5f);
            beam.lineTo(cx - size * 0.12f, cy + size * 0.5f);
            beam.close();
            c.drawPath(beam, grad(new RadialGradient(cx, cy, size * 0.5f, 0xEEDFFFFF, 0x225EC8FF, Shader.TileMode.CLAMP)));
            c.restore();
        }
        boolean ringed = type == Defs.SATURN || type == Defs.URANUS || type == Defs.HERO || type == Defs.KING || type == Defs.GALACIA_P;
        float rf = ringed ? 0.27f : (type == Defs.SUN ? 0.3f : type == Defs.COMET ? 0.2f : type == Defs.PULSAR ? 0.22f : 0.36f);
        int atmo;
        float as, glow;
        switch (type) {
            case Defs.EARTH: atmo = 0xFF6FB8FF; as = 0.95f; glow = 0.2f; break;
            case Defs.MOON: atmo = 0xFFCCCCDD; as = 0.12f; glow = 0.05f; break;
            case Defs.MERCURY: atmo = 0xFFBFAEA0; as = 0.1f; glow = 0.04f; break;
            case Defs.MARS: atmo = 0xFFFFA77F; as = 0.5f; glow = 0.1f; break;
            case Defs.JUPITER: atmo = 0xFFFFD9A8; as = 0.45f; glow = 0.1f; break;
            case Defs.SATURN: atmo = 0xFFFFE9B0; as = 0.45f; glow = 0.12f; break;
            case Defs.VENUS: atmo = 0xFFFFF0C0; as = 0.85f; glow = 0.17f; break;
            case Defs.URANUS: atmo = 0xFFB8F6FF; as = 0.8f; glow = 0.16f; break;
            case Defs.NEPTUNE: atmo = 0xFF7FA6FF; as = 0.85f; glow = 0.16f; break;
            case Defs.PLUTO: atmo = 0xFFE8D4BE; as = 0.25f; glow = 0.06f; break;
            case Defs.HERO: atmo = 0xFFFF6A3D; as = 1f; glow = 0.2f; break;
            case Defs.CYBER: atmo = 0xFF5EF2FF; as = 1f; glow = 0.22f; break;
            case Defs.NIGHTMARE: atmo = 0xFFFF1A3A; as = 1f; glow = 0.28f; break;
            case Defs.GALACIA_P: atmo = 0xFF7FD8FF; as = 1f; glow = 0.2f; break;
            case Defs.KING: atmo = 0xFFFFD23F; as = 1f; glow = 0.22f; break;
            case Defs.COMET: atmo = 0xFFB8F6FF; as = 0.9f; glow = 0.2f; break;
            case Defs.PULSAR: atmo = 0xFF9EF6FF; as = 1f; glow = 0.45f; break;
            default: atmo = 0xFFFFB030; as = 1f; glow = 0.58f; break;
        }
        float r = size * rf;
        int[] sat = {0x55806A48, 0xCCD8C090, 0xEEEAD8B0, 0x11000000, 0xDDC8AE80, 0xAAB09870, 0x66A08860};
        float[] satR = {1.25f, 1.36f, 1.5f, 1.6f, 1.68f, 1.78f, 1.86f};
        int[] ura = {0x88C8F4FF, 0x55A8E8F8, 0xAAE0FCFF};
        float[] uraR = {1.45f, 1.58f, 1.72f};
        if (type == Defs.SATURN) rings(c, cx, cy, r, -14, false, sat, satR, 0.09f);
        if (type == Defs.URANUS) rings(c, cx, cy, r, -72, false, ura, uraR, 0.03f);
        int[] hero = {0x66FF4D2B, 0xEEFFC83A, 0xFFFFF0A0, 0x22000000, 0xCCFF8A2B};
        float[] heroR = {1.3f, 1.42f, 1.5f, 1.58f, 1.66f};
        if (type == Defs.HERO) rings(c, cx, cy, r, -24, false, hero, heroR, 0.07f);
        int[] king = {0x88FFD23F, 0xFFFFF0A0, 0xEEB86BFF, 0xCCFFD23F};
        float[] kingR = {1.32f, 1.42f, 1.52f, 1.6f};
        int[] water = {0x664FC8FF, 0xAADFF8FF, 0x884FA8FF};
        float[] waterR = {1.3f, 1.42f, 1.55f};
        if (type == Defs.KING) rings(c, cx, cy, r, -12, false, king, kingR, 0.06f);
        if (type == Defs.GALACIA_P) rings(c, cx, cy, r, 20, false, water, waterR, 0.12f);
        Bitmap s = Real.toon(size, rf, Real.surface(type), atmo, glow * 1.1f + 0.08f, type == Defs.SUN || type == Defs.PULSAR, type * 17 + 3);
        if (type == Defs.COMET) c.drawBitmap(s, size * 0.06f, -size * 0.06f, null);
        else c.drawBitmap(s, 0, 0, null);
        if (type == Defs.SATURN) rings(c, cx, cy, r, -14, true, sat, satR, 0.09f);
        if (type == Defs.URANUS) rings(c, cx, cy, r, -72, true, ura, uraR, 0.03f);
        if (type == Defs.KING) {
            rings(c, cx, cy, r, -12, true, king, kingR, 0.06f);
            crown(c, cx, cy - r * 1.02f, r * 0.62f);
        }
        if (type == Defs.GALACIA_P) {
            rings(c, cx, cy, r, 20, true, water, waterR, 0.12f);
            Paint d = fill();
            for (int i = 0; i < 5; i++) {
                double a = i * 1.26 + 0.3;
                d.setColor(0xDDDFF8FF);
                c.drawCircle(cx + (float) Math.cos(a) * r * 1.7f, cy + (float) Math.sin(a) * r * 0.6f, size * 0.018f, d);
            }
        }
        if (type == Defs.NIGHTMARE) {
            // black thorns around the dark planet
            Paint th = fill();
            th.setColor(0xFF1A0612);
            Paint to = stroke(size * 0.012f, 0xFFFF1A3A);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4 + 0.2;
                float x0 = cx + (float) Math.cos(a) * r * 0.95f, y0 = cy + (float) Math.sin(a) * r * 0.95f;
                float x1 = cx + (float) Math.cos(a) * r * 1.35f, y1 = cy + (float) Math.sin(a) * r * 1.35f;
                Path p = new Path();
                p.moveTo(x0 + (float) Math.cos(a + 1.57) * r * 0.1f, y0 + (float) Math.sin(a + 1.57) * r * 0.1f);
                p.lineTo(x1, y1);
                p.lineTo(x0 - (float) Math.cos(a + 1.57) * r * 0.1f, y0 - (float) Math.sin(a + 1.57) * r * 0.1f);
                p.close();
                c.drawPath(p, th);
                c.drawPath(p, to);
            }
        }
        if (type == Defs.HERO) {
            rings(c, cx, cy, r, -24, true, hero, heroR, 0.07f);
            // golden sparks around the hero
            Paint sp = fill();
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3 + 0.4;
                float d = r * (1.75f + (i % 2) * 0.12f);
                float x = cx + (float) Math.cos(a) * d, y = cy + (float) Math.sin(a) * d * 0.75f;
                sp.setColor(0xFFFFE27A);
                c.drawPath(starPath(x, y, size * 0.035f, size * 0.012f, 4), sp);
            }
        }
        return b;
    }

    /** Ion Drone: a sleek hex drone with two thrusters and a glowing ion core */
    static void ionDrone(Canvas c, float cx, float cy, float s) {
        Paint o = stroke(s * 0.016f, OUTLINE);
        c.drawCircle(cx, cy, s * 0.45f, grad(new RadialGradient(cx, cy, s * 0.45f, 0x335EF2FF, 0x005EF2FF, Shader.TileMode.CLAMP)));
        for (int side = -1; side <= 1; side += 2) {
            // arm + thruster pod
            c.drawLine(cx, cy, cx + side * s * 0.3f, cy + s * 0.04f, stroke(s * 0.03f, 0xFF8A94A8));
            RectF pod = new RectF(cx + side * s * 0.3f - s * 0.07f, cy - s * 0.05f, cx + side * s * 0.3f + s * 0.07f, cy + s * 0.13f);
            c.drawRoundRect(pod, s * 0.04f, s * 0.04f, grad(new LinearGradient(pod.left, pod.top, pod.right, pod.bottom, 0xFFDDE6F2, 0xFF5A6478, Shader.TileMode.CLAMP)));
            c.drawRoundRect(pod, s * 0.04f, s * 0.04f, o);
            Paint fl = fill();
            fl.setShader(new RadialGradient(pod.centerX(), pod.bottom + s * 0.04f, s * 0.08f, 0xFF9EF6FF, 0x005EC8FF, Shader.TileMode.CLAMP));
            c.drawCircle(pod.centerX(), pod.bottom + s * 0.04f, s * 0.08f, fl);
        }
        Path hex = new Path();
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 3 * i;
            float x = cx + (float) Math.cos(a) * s * 0.17f, y = cy + (float) Math.sin(a) * s * 0.17f;
            if (i == 0) hex.moveTo(x, y); else hex.lineTo(x, y);
        }
        hex.close();
        c.drawPath(hex, grad(new LinearGradient(cx - s * 0.17f, cy - s * 0.17f, cx + s * 0.17f, cy + s * 0.17f, 0xFFF2F6FF, 0xFF4A5468, Shader.TileMode.CLAMP)));
        c.drawPath(hex, o);
        c.drawCircle(cx, cy, s * 0.08f, grad(new RadialGradient(cx, cy, s * 0.08f, new int[]{0xFFFFFFFF, 0xFF5EF2FF, 0xFF1A6AD8}, null, Shader.TileMode.CLAMP)));
        // laser barrel
        c.drawLine(cx, cy + s * 0.1f, cx, cy + s * 0.26f, stroke(s * 0.025f, 0xFF2A3040));
        Paint tip = fill();
        tip.setColor(0xFF5EF2FF);
        c.drawCircle(cx, cy + s * 0.27f, s * 0.02f, tip);
    }

    /** Black Hole: a black disc with a bright tilted accretion disk and lensing ring */
    static void blackHole(Canvas c, float cx, float cy, float s) {
        c.drawCircle(cx, cy, s * 0.46f, grad(new RadialGradient(cx, cy, s * 0.46f, 0x66B86BFF, 0x00B86BFF, Shader.TileMode.CLAMP)));
        RectF disk = new RectF(cx - s * 0.44f, cy - s * 0.12f, cx + s * 0.44f, cy + s * 0.12f);
        Paint dp = stroke(s * 0.07f, 0xFFFF9A3B);
        dp.setShader(new LinearGradient(disk.left, cy, disk.right, cy, new int[]{0xFFFF6A1A, 0xFFFFF0B0, 0xFFFF6A1A, 0xFFB8461E}, null, Shader.TileMode.CLAMP));
        // back half of the disk
        c.drawArc(disk, 180, 180, false, dp);
        // lensed ring over the top
        Paint lens = stroke(s * 0.035f, 0xFFFFD08A);
        c.drawArc(new RectF(cx - s * 0.2f, cy - s * 0.25f, cx + s * 0.2f, cy + s * 0.15f), 180, 180, false, lens);
        Paint hole = fill();
        hole.setColor(0xFF000000);
        c.drawCircle(cx, cy, s * 0.17f, hole);
        Paint rim = stroke(s * 0.012f, 0xFFFFE6A8);
        c.drawCircle(cx, cy, s * 0.175f, rim);
        // front half of the disk
        c.drawArc(disk, 0, 180, false, dp);
    }

    /** Martin World: a cheeky cream smiley wrapped in a pinwheel of sparkling cyan, pink and white arms */
    static void martinWorld(Canvas c, float cx, float cy, float s) {
        Paint bg = fill();
        bg.setShader(new RadialGradient(cx, cy, s * 0.5f, 0xFF2A2440, 0x002A2440, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, s * 0.5f, bg);
        java.util.Random r = new java.util.Random(24);
        int[] cols = {0xFF5EF2FF, 0xFFFF4FD8, 0xFFFFFFFF, 0xFFB86BFF, 0xFF3BB8FF};
        Paint d = fill();
        for (int arm = 0; arm < 5; arm++) {
            double a0 = arm * Math.PI * 2 / 5;
            for (int k = 0; k < 110; k++) {
                float t = k / 110f;
                double a = a0 + t * 2.4;
                float rad = s * (0.2f + t * 0.27f);
                float jit = s * 0.035f * (0.4f + t);
                float x = cx + (float) Math.cos(a) * rad + (r.nextFloat() - 0.5f) * jit * 2;
                float y = cy + (float) Math.sin(a) * rad + (r.nextFloat() - 0.5f) * jit * 2;
                d.setColor(cols[r.nextInt(cols.length)]);
                c.drawCircle(x, y, s * (0.006f + r.nextFloat() * 0.008f), d);
            }
        }
        float fr = s * 0.2f;
        c.drawCircle(cx, cy, fr, grad(new RadialGradient(cx - fr * 0.3f, cy - fr * 0.35f, fr * 1.3f, 0xFFFFF0D0, 0xFFFFC98A, Shader.TileMode.CLAMP)));
        Paint w = fill();
        w.setColor(0xFFFFFFFF);
        c.drawCircle(cx - fr * 0.36f, cy - fr * 0.2f, fr * 0.24f, w);
        c.drawCircle(cx + fr * 0.36f, cy - fr * 0.2f, fr * 0.24f, w);
        Paint pu = fill();
        pu.setColor(0xFF1A1420);
        c.drawCircle(cx - fr * 0.33f, cy - fr * 0.2f, fr * 0.04f, pu);
        c.drawCircle(cx + fr * 0.31f, cy - fr * 0.22f, fr * 0.04f, pu);
        Path smile = new Path();
        smile.moveTo(cx - fr * 0.62f, cy + fr * 0.22f);
        smile.quadTo(cx, cy + fr * 0.55f, cx + fr * 0.66f, cy + fr * 0.12f);
        Paint sm = stroke(fr * 0.07f, 0xFF1A1420);
        sm.setStrokeCap(Paint.Cap.ROUND);
        c.drawPath(smile, sm);
        // buck teeth
        RectF tooth = new RectF(cx - fr * 0.2f, cy + fr * 0.36f, cx + fr * 0.16f, cy + fr * 0.6f);
        c.drawRect(tooth, w);
        Paint tl = stroke(fr * 0.025f, 0xFFB8A890);
        c.drawRect(tooth, tl);
        c.drawLine(tooth.centerX(), tooth.top, tooth.centerX(), tooth.bottom, tl);
    }

    /** a small golden crown */
    static void crown(Canvas c, float cx, float cy, float w) {
        Path p = new Path();
        p.moveTo(cx - w * 0.5f, cy + w * 0.2f);
        p.lineTo(cx - w * 0.5f, cy - w * 0.25f);
        p.lineTo(cx - w * 0.25f, cy);
        p.lineTo(cx, cy - w * 0.4f);
        p.lineTo(cx + w * 0.25f, cy);
        p.lineTo(cx + w * 0.5f, cy - w * 0.25f);
        p.lineTo(cx + w * 0.5f, cy + w * 0.2f);
        p.close();
        c.drawPath(p, grad(new LinearGradient(cx, cy - w * 0.4f, cx, cy + w * 0.2f, 0xFFFFF4B0, 0xFFE0A020, Shader.TileMode.CLAMP)));
        c.drawPath(p, stroke(w * 0.06f, 0xFF5A3A08));
        Paint g = fill();
        g.setColor(0xFFFF4F8B);
        c.drawCircle(cx, cy - w * 0.02f, w * 0.07f, g);
    }

    /** hue-rotate (degrees) and scale saturation / brightness of a bitmap */
    static Bitmap recolor(Bitmap src, float hueDeg, float satMul, float valMul) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        float[] hsv = new float[3];
        for (int i = 0; i < px.length; i++) {
            int c = px[i], a = c >>> 24;
            if (a == 0) continue;
            rgbToHsv(c, hsv);
            hsv[0] = (hsv[0] + hueDeg + 360) % 360;
            hsv[1] = Math.min(1f, hsv[1] * satMul);
            hsv[2] = Math.min(1f, hsv[2] * valMul);
            px[i] = (a << 24) | (hsvToRgb(hsv) & 0xFFFFFF);
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, w, 0, 0, w, h);
        return out;
    }

    static void rgbToHsv(int c, float[] o) {
        float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        float mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b)), d = mx - mn;
        float hh;
        if (d == 0) hh = 0;
        else if (mx == r) hh = 60 * (((g - b) / d) % 6);
        else if (mx == g) hh = 60 * ((b - r) / d + 2);
        else hh = 60 * ((r - g) / d + 4);
        o[0] = hh < 0 ? hh + 360 : hh;
        o[1] = mx == 0 ? 0 : d / mx;
        o[2] = mx;
    }

    static int hsvToRgb(float[] h) {
        float c = h[2] * h[1], x = c * (1 - Math.abs((h[0] / 60f) % 2 - 1)), m = h[2] - c;
        float r, g, b;
        int seg = (int) (h[0] / 60) % 6;
        switch (seg) {
            case 0: r = c; g = x; b = 0; break;
            case 1: r = x; g = c; b = 0; break;
            case 2: r = 0; g = c; b = x; break;
            case 3: r = 0; g = x; b = c; break;
            case 4: r = x; g = 0; b = c; break;
            default: r = c; g = 0; b = x;
        }
        return 0xFF000000 | ((int) ((r + m) * 255) << 16) | ((int) ((g + m) * 255) << 8) | (int) ((b + m) * 255);
    }

    static void satellite(Canvas c, float cx, float cy, int size) {
        float s = size;
        float ow = s * 0.02f;
        Paint o = stroke(ow, OUTLINE);
        // solar panels with cells
        for (int side = -1; side <= 1; side += 2) {
            float x0 = side < 0 ? cx - s * 0.47f : cx + s * 0.19f;
            RectF pan = new RectF(x0, cy - s * 0.12f, x0 + s * 0.28f, cy + s * 0.12f);
            c.drawRect(pan, grad(new LinearGradient(pan.left, pan.top, pan.right, pan.bottom, 0xFF4F9CFF, 0xFF16307E, Shader.TileMode.CLAMP)));
            Paint g = stroke(s * 0.006f, 0x99A8D4FF);
            for (int i = 1; i < 5; i++) {
                float x = pan.left + pan.width() * i / 5f;
                c.drawLine(x, pan.top, x, pan.bottom, g);
            }
            for (int i = 1; i < 4; i++) {
                float y = pan.top + pan.height() * i / 4f;
                c.drawLine(pan.left, y, pan.right, y, g);
            }
            Paint shine = fill();
            shine.setColor(0x33FFFFFF);
            Path sh = new Path();
            sh.moveTo(pan.left, pan.top);
            sh.lineTo(pan.left + pan.width() * 0.45f, pan.top);
            sh.lineTo(pan.left, pan.top + pan.height() * 0.7f);
            sh.close();
            c.drawPath(sh, shine);
            c.drawRect(pan, o);
        }
        c.drawLine(cx - s * 0.2f, cy, cx + s * 0.2f, cy, stroke(s * 0.035f, 0xFF8A93A8));
        c.drawLine(cx - s * 0.2f, cy, cx + s * 0.2f, cy, stroke(s * 0.012f, 0xFFC8CFE0));
        // dish + antenna
        c.drawLine(cx, cy - s * 0.15f, cx, cy - s * 0.3f, stroke(s * 0.02f, 0xFFDDE3EE));
        RectF dish = new RectF(cx - s * 0.14f, cy - s * 0.44f, cx + s * 0.14f, cy - s * 0.26f);
        c.drawArc(dish, 0, 180, true, grad(new LinearGradient(dish.left, dish.top, dish.right, dish.bottom, 0xFFFFFFFF, 0xFFA8B0C4, Shader.TileMode.CLAMP)));
        c.drawArc(dish, 0, 180, true, o);
        c.drawLine(cx, cy - s * 0.35f, cx + s * 0.08f, cy - s * 0.47f, stroke(s * 0.012f, 0xFFDDE3EE));
        Paint led = fill();
        led.setColor(0xFFFF5B5B);
        c.drawCircle(cx + s * 0.08f, cy - s * 0.47f, s * 0.02f, led);
        // body: crinkled gold foil
        RectF body = new RectF(cx - s * 0.16f, cy - s * 0.16f, cx + s * 0.16f, cy + s * 0.18f);
        c.drawRoundRect(body, s * 0.04f, s * 0.04f, grad(new LinearGradient(body.left, body.top, body.right, body.bottom,
                new int[]{0xFFFFE9A0, 0xFFE0A030, 0xFFFFD36A, 0xFFB07018}, new float[]{0f, 0.4f, 0.6f, 1f}, Shader.TileMode.CLAMP)));
        c.save();
        c.clipPath(circlePath(cx, cy, s * 0.24f));
        Paint fold = stroke(s * 0.008f, 0x66804A00);
        Random rd = new Random(81);
        for (int i = 0; i < 9; i++) {
            float x = body.left + rd.nextFloat() * body.width(), y = body.top + rd.nextFloat() * body.height();
            c.drawLine(x, y, x + (rd.nextFloat() - 0.5f) * s * 0.12f, y + (rd.nextFloat() - 0.5f) * s * 0.12f, fold);
        }
        c.restore();
        Paint band = fill();
        band.setColor(0xFF5A6278);
        c.drawRect(body.left, cy + s * 0.05f, body.right, cy + s * 0.09f, band);
        Paint lens = fill();
        lens.setColor(0xFF1A2240);
        c.drawCircle(cx, cy - s * 0.04f, s * 0.06f, lens);
        lens.setColor(0xFF5FB8FF);
        c.drawCircle(cx - s * 0.015f, cy - s * 0.055f, s * 0.022f, lens);
        c.drawRoundRect(body, s * 0.04f, s * 0.04f, o);
    }

    // ------------------------------------------------------------------ enemies (realistic, no faces)
    /** irregular rocky silhouette */
    static Path rockPath(float cx, float cy, float r, int seed, float rough) {
        Random rd = new Random(seed);
        int n = 22;
        float[] rr = new float[n];
        for (int i = 0; i < n; i++) rr[i] = r * (1f - rough + rd.nextFloat() * rough);
        Path path = new Path();
        for (int i = 0; i <= n; i++) {
            int k = i % n;
            float r0 = (rr[k] * 2 + rr[(k + 1) % n] + rr[(k + n - 1) % n]) / 4f;
            double a = k * Math.PI * 2 / n;
            float x = cx + (float) Math.cos(a) * r0, y = cy + (float) Math.sin(a) * r0;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        return path;
    }

    static void rockBody(Canvas c, float cx, float cy, float r, int seed, int light, int mid, int dark, int crackColor) {
        Path body = rockPath(cx, cy, r, seed, 0.28f);
        c.drawPath(body, grad(new RadialGradient(cx - r * 0.35f, cy - r * 0.4f, r * 1.5f,
                new int[]{light, mid, dark}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP)));
        c.save();
        c.clipPath(body);
        // strata and bumps
        Random rd = new Random(seed * 7 + 1);
        for (int i = 0; i < 6; i++) {
            float x = cx + (rd.nextFloat() - 0.5f) * r * 1.4f, y = cy + (rd.nextFloat() - 0.5f) * r * 1.4f;
            blob(c, x, y, r * (0.18f + rd.nextFloat() * 0.25f), seed + i, 0.4f, (rd.nextBoolean() ? 0x22FFFFFF : 0x33000000));
        }
        speckle(c, cx, cy, r, seed + 3, 240, 0x26FFFFFF, 0x30000000);
        craterField(c, cx, cy, r, seed + 5, 7, 0x55000000, 0x44FFFFFF);
        if (crackColor != 0) {
            Paint glow = stroke(r * 0.13f, (crackColor & 0x00FFFFFF) | 0x55000000);
            Paint core = stroke(r * 0.045f, crackColor);
            for (int i = 0; i < 5; i++) {
                Path l = new Path();
                float x = cx + (rd.nextFloat() - 0.5f) * r * 1.2f, y = cy + (rd.nextFloat() - 0.5f) * r * 1.2f;
                l.moveTo(x, y);
                for (int k = 0; k < 3; k++) {
                    x += (rd.nextFloat() - 0.5f) * r * 0.6f;
                    y += (rd.nextFloat() - 0.5f) * r * 0.6f;
                    l.lineTo(x, y);
                }
                c.drawPath(l, glow);
                c.drawPath(l, core);
            }
        }
        // light from the top-left, dark bottom-right
        c.drawCircle(cx, cy, r * 1.1f, grad(new RadialGradient(cx - r * 0.5f, cy - r * 0.55f, r * 2.1f,
                new int[]{0x00000000, 0x00000000, 0x88000008}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP)));
        c.restore();
        c.drawPath(body, stroke(r * 0.04f, 0xAA0E0818));
    }

    static void moonBody(Canvas c, float cx, float cy, float r, int light, int dark, int seed, int craterDark, int craterLight) {
        c.drawCircle(cx, cy, r, grad(new RadialGradient(cx - r * 0.3f, cy - r * 0.35f, r * 1.45f, light, dark, Shader.TileMode.CLAMP)));
        c.save();
        c.clipPath(circlePath(cx, cy, r));
        blob(c, cx - r * 0.2f, cy - r * 0.2f, r * 0.35f, seed, 0.3f, 0x33000000);
        blob(c, cx + r * 0.3f, cy + r * 0.25f, r * 0.25f, seed + 1, 0.3f, 0x28000000);
        speckle(c, cx, cy, r, seed + 2, 220, 0x22FFFFFF, 0x26000000);
        craterField(c, cx, cy, r, seed + 3, 14, craterDark, craterLight);
        lighting(c, cx, cy, r);
        c.restore();
    }

    static Bitmap enemy(int type, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float cx = size / 2f, cy = size / 2f, r = size * 0.38f;
        if (type == Defs.E_SHOOT) {
            c.drawCircle(cx, cy, size * 0.5f, grad(new RadialGradient(cx, cy, size * 0.5f,
                    new int[]{0xFFFFFFFF, 0xCCFFF2A8, 0x55FFC23C, 0x00FF9A1C}, new float[]{0f, 0.2f, 0.5f, 1f}, Shader.TileMode.CLAMP)));
            Paint ray = fill();
            ray.setColor(0xAAFFFFFF);
            c.drawPath(starPath(cx, cy, size * 0.48f, size * 0.05f, 4), ray);
            ray.setColor(0x66FFF2A8);
            c.save();
            c.rotate(45, cx, cy);
            c.drawPath(starPath(cx, cy, size * 0.3f, size * 0.04f, 4), ray);
            c.restore();
            Paint core = fill();
            core.setColor(0xFFFFFFFF);
            c.drawCircle(cx, cy, size * 0.09f, core);
            return b;
        }
        if (type == Defs.E_EXOPLANET)
            rings(c, cx, cy, size * 0.32f, -20, false, new int[]{0x887CFF8A, 0xAA3FD08A, 0x667CFFD0}, new float[]{1.3f, 1.45f, 1.6f}, 0.08f);
        if (type == Defs.E_ALTMOON) {
            Paint gp = fill();
            gp.setColor(0x44FF3FD0);
            c.drawCircle(cx - size * 0.035f, cy, size * 0.36f, gp);
            gp.setColor(0x4433E0FF);
            c.drawCircle(cx + size * 0.035f, cy, size * 0.36f, gp);
        }
        Bitmap s = Real.enemy(type, size);
        if (s != null) c.drawBitmap(s, 0, 0, null);
        if (type == Defs.E_EXOPLANET)
            rings(c, cx, cy, size * 0.32f, -20, true, new int[]{0x887CFF8A, 0xAA3FD08A, 0x667CFFD0}, new float[]{1.3f, 1.45f, 1.6f}, 0.08f);
        if (type == Defs.E_ALTPLANET) {
            c.save();
            c.clipPath(circlePath(cx, cy, r));
            Paint riftGlow = stroke(r * 0.2f, 0x66FFC8FF);
            Paint rift = stroke(r * 0.05f, 0xFFFFFFFF);
            Path rp = new Path();
            rp.moveTo(cx - r * 0.2f, cy - r);
            rp.lineTo(cx + r * 0.1f, cy - r * 0.35f);
            rp.lineTo(cx - r * 0.12f, cy + r * 0.05f);
            rp.lineTo(cx + r * 0.18f, cy + r * 0.5f);
            rp.lineTo(cx, cy + r);
            c.drawPath(rp, riftGlow);
            c.drawPath(rp, rift);
            c.restore();
        }
        return b;
    }

    static void glow(Canvas c, float cx, float cy, float r, int color) {
        Paint g = fill();
        g.setShader(new RadialGradient(cx, cy, r, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, g);
    }

    static Path starPath(float cx, float cy, float ro, float ri, int pts) {
        Path p = new Path();
        for (int i = 0; i < pts * 2; i++) {
            double a = -Math.PI / 2 + i * Math.PI / pts;
            float rr = (i % 2 == 0) ? ro : ri;
            float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
            if (i == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        p.close();
        return p;
    }
}
