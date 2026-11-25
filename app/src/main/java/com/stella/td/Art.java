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
        p.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0xFF0A0620, 0xFF1C0F45, 0xFF0B1A3D}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        Random r = new Random(7);
        int[] neb = {0x55A43CFF, 0x4433C6E0, 0x44FF4F9B, 0x334F6BFF};
        for (int i = 0; i < 7; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, rad = (0.25f + r.nextFloat() * 0.35f) * w;
            p.setShader(new RadialGradient(x, y, rad, neb[i % neb.length], 0x00000000, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, rad, p);
        }
        p.setShader(null);
        for (int i = 0; i < 260; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, s = r.nextFloat();
            p.setColor(s > 0.9f ? 0xFFFFF2C4 : (s > 0.7f ? 0xCCBFD9FF : 0x88FFFFFF));
            c.drawCircle(x, y, 0.6f + s * s * 2.6f, p);
        }
        // a few distant cartoon planets
        drawDecoPlanet(c, w * 0.12f, h * 0.08f, w * 0.05f, 0xFFFF8A5C);
        drawDecoPlanet(c, w * 0.9f, h * 0.93f, w * 0.08f, 0xFF5BC8FF);
        return b;
    }

    private static void drawDecoPlanet(Canvas c, float x, float y, float r, int color) {
        Paint p = fill();
        p.setShader(new RadialGradient(x - r * 0.4f, y - r * 0.4f, r * 1.6f, color, 0xFF140A30, Shader.TileMode.CLAMP));
        p.setAlpha(150);
        c.drawCircle(x, y, r, p);
    }

    // ------------------------------------------------------------------ helpers
    static void face(Canvas c, float cx, float cy, float r, boolean angry) {
        Paint p = fill();
        float ex = r * 0.32f, ey = cy - r * 0.06f;
        for (int s = -1; s <= 1; s += 2) {
            float x = cx + s * ex;
            p.setColor(0xFFFFFFFF);
            c.drawOval(new RectF(x - r * 0.16f, ey - r * 0.2f, x + r * 0.16f, ey + r * 0.2f), p);
            p.setColor(0xFF140A2A);
            c.drawCircle(x + r * 0.03f, ey + r * 0.04f, r * 0.095f, p);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(x - r * 0.01f, ey - r * 0.03f, r * 0.035f, p);
            if (angry) {
                Paint br = stroke(r * 0.07f, 0xFF140A2A);
                c.drawLine(x - s * r * 0.2f, ey - r * 0.3f, x + s * r * 0.12f, ey - r * 0.2f, br);
            }
        }
        Paint m = stroke(r * 0.06f, 0xFF140A2A);
        RectF mouth = new RectF(cx - r * 0.16f, cy + r * 0.1f, cx + r * 0.16f, cy + r * 0.34f);
        if (angry) c.drawArc(new RectF(cx - r * 0.14f, cy + r * 0.24f, cx + r * 0.14f, cy + r * 0.44f), 200, 140, false, m);
        else c.drawArc(mouth, 20, 140, false, m);
        // blush
        p.setColor(0x55FF6B8B);
        c.drawCircle(cx - r * 0.5f, cy + r * 0.16f, r * 0.1f, p);
        c.drawCircle(cx + r * 0.5f, cy + r * 0.16f, r * 0.1f, p);
    }

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

    static Bitmap planet(int type, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float cx = size / 2f, cy = size / 2f;
        float r = size * 0.36f, ow = size * 0.022f;
        if (type == Defs.SATELLITE) {
            satellite(c, cx, cy, size);
            return b;
        }
        if (type == Defs.SATURN || type == Defs.URANUS) r = size * 0.27f;
        if (type == Defs.SUN) r = size * 0.31f;

        int light, dark, atmo;
        switch (type) {
            case Defs.EARTH: light = 0xFF5AB8FF; dark = 0xFF0E3E8C; atmo = 0xFF8FD3FF; break;
            case Defs.MOON: light = 0xFFE9EAF2; dark = 0xFF7E8098; atmo = 0xFFD8DAE8; break;
            case Defs.SATURN: light = 0xFFF8E2AA; dark = 0xFFB98A3A; atmo = 0xFFFFE9B0; break;
            case Defs.JUPITER: light = 0xFFF6D9B4; dark = 0xFFB0703A; atmo = 0xFFFFD9A8; break;
            case Defs.MARS: light = 0xFFF08A5A; dark = 0xFF8E2A16; atmo = 0xFFFFA77F; break;
            case Defs.MERCURY: light = 0xFFCDBBAA; dark = 0xFF6B5546; atmo = 0xFFBFAEA0; break;
            case Defs.VENUS: light = 0xFFFFE7A6; dark = 0xFFD08A2A; atmo = 0xFFFFF0C0; break;
            case Defs.URANUS: light = 0xFFC8FBFF; dark = 0xFF3AA6BC; atmo = 0xFFB8F6FF; break;
            case Defs.NEPTUNE: light = 0xFF6F94FF; dark = 0xFF16228A; atmo = 0xFF7FA6FF; break;
            case Defs.PLUTO: light = 0xFFF2DCC2; dark = 0xFF94694A; atmo = 0xFFE8D4BE; break;
            case Defs.SUN: light = 0xFFFFF7B0; dark = 0xFFFF9A14; atmo = 0xFFFFC23C; break;
            default: light = 0xFFFFFFFF; dark = 0xFF888888; atmo = 0xFFFFFFFF;
        }

        // ---- behind the planet
        if (type == Defs.SUN) {
            c.drawCircle(cx, cy, size * 0.5f, grad(new RadialGradient(cx, cy, size * 0.5f,
                    new int[]{0xFFFFD45A, 0xAAFF9A1C, 0x33FF6A00, 0x00FF5A00}, new float[]{0.5f, 0.66f, 0.85f, 1f}, Shader.TileMode.CLAMP)));
            Path rp = new Path();
            Random rd = new Random(12);
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8 + rd.nextFloat() * 0.1;
                float len = r * (1.32f + (i % 2) * 0.16f + rd.nextFloat() * 0.06f);
                double a1 = a - 0.11, a2 = a + 0.11;
                rp.moveTo(cx + (float) Math.cos(a1) * r * 0.95f, cy + (float) Math.sin(a1) * r * 0.95f);
                rp.lineTo(cx + (float) Math.cos(a) * len, cy + (float) Math.sin(a) * len);
                rp.lineTo(cx + (float) Math.cos(a2) * r * 0.95f, cy + (float) Math.sin(a2) * r * 0.95f);
                rp.close();
            }
            c.drawPath(rp, grad(new RadialGradient(cx, cy, r * 1.5f, 0xFFFFE07A, 0xFFFF7A0A, Shader.TileMode.CLAMP)));
            c.drawPath(rp, stroke(ow * 0.8f, 0xFFB34A00));
        } else {
            atmosphere(c, cx, cy, r, atmo);
        }
        if (type == Defs.SATURN)
            rings(c, cx, cy, r, -14, false, new int[]{0xFF8A6A35, 0xFFE9D29A, 0xFFC7A866, 0xFFF4E4BC},
                    new float[]{1.32f, 1.48f, 1.66f, 1.82f}, 0.13f);
        if (type == Defs.URANUS)
            rings(c, cx, cy, r, -72, false, new int[]{0xAADDFBFF, 0x88BDF6FF, 0xAAE8FFFF},
                    new float[]{1.45f, 1.6f, 1.75f}, 0.035f);

        // ---- planet body
        c.drawCircle(cx, cy, r, grad(new RadialGradient(cx - r * 0.3f, cy - r * 0.35f, r * 1.45f, light, dark, Shader.TileMode.CLAMP)));
        c.save();
        c.clipPath(circlePath(cx, cy, r));
        Paint p = fill();
        switch (type) {
            case Defs.EARTH: {
                c.drawCircle(cx, cy, r, grad(new RadialGradient(cx + r * 0.2f, cy + r * 0.2f, r, 0x33002050, 0x00002050, Shader.TileMode.CLAMP)));
                int land = 0xFF3FAE5A, land2 = 0xFF8C9A4A, land3 = 0xFFD8C27A;
                blob(c, cx - r * 0.45f, cy - r * 0.15f, r * 0.42f, 3, 0.35f, land);
                blob(c, cx - r * 0.3f, cy + r * 0.45f, r * 0.24f, 4, 0.4f, land);
                blob(c, cx + r * 0.5f, cy + r * 0.05f, r * 0.36f, 5, 0.4f, land);
                blob(c, cx + r * 0.42f, cy - r * 0.05f, r * 0.17f, 6, 0.4f, land3);
                blob(c, cx - r * 0.52f, cy - r * 0.25f, r * 0.18f, 7, 0.4f, land2);
                blob(c, cx + r * 0.6f, cy + r * 0.25f, r * 0.12f, 8, 0.4f, land2);
                p.setColor(0xFFF4FAFF);
                c.drawOval(new RectF(cx - r * 0.6f, cy - r * 1.08f, cx + r * 0.6f, cy - r * 0.8f), p);
                c.drawOval(new RectF(cx - r * 0.5f, cy + r * 0.84f, cx + r * 0.5f, cy + r * 1.08f), p);
                Paint cl = stroke(r * 0.05f, 0xAAFFFFFF);
                Random rd = new Random(9);
                for (int i = 0; i < 4; i++) {
                    float y = cy - r * 0.55f + i * r * 0.36f, x = cx - r + rd.nextFloat() * r;
                    c.drawArc(new RectF(x - r * 0.35f, y - r * 0.08f, x + r * 0.45f, y + r * 0.1f), 190, 150, false, cl);
                }
                break;
            }
            case Defs.MOON: {
                blob(c, cx - r * 0.25f, cy - r * 0.25f, r * 0.33f, 21, 0.3f, 0x558A8CA8);
                blob(c, cx + r * 0.2f, cy - r * 0.05f, r * 0.22f, 22, 0.3f, 0x558A8CA8);
                blob(c, cx - r * 0.05f, cy + r * 0.35f, r * 0.2f, 23, 0.3f, 0x448A8CA8);
                speckle(c, cx, cy, r, 31, 220, 0x22FFFFFF, 0x22303048);
                craterField(c, cx, cy, r, 3, 16, 0x557A7C96, 0x88FFFFFF);
                break;
            }
            case Defs.MERCURY: {
                speckle(c, cx, cy, r, 41, 260, 0x22FFFFFF, 0x2A2A1A10);
                blob(c, cx + r * 0.25f, cy - r * 0.3f, r * 0.3f, 42, 0.3f, 0x33503A2C);
                craterField(c, cx, cy, r, 11, 22, 0x66574536, 0x77F2E4D6);
                break;
            }
            case Defs.MARS: {
                blob(c, cx - r * 0.3f, cy + r * 0.1f, r * 0.45f, 51, 0.35f, 0x55701A0C);
                blob(c, cx + r * 0.45f, cy - r * 0.35f, r * 0.25f, 52, 0.35f, 0x44701A0C);
                blob(c, cx + r * 0.2f, cy + r * 0.45f, r * 0.3f, 53, 0.35f, 0x33FFC09A);
                Paint can = stroke(r * 0.05f, 0x77601408);
                Path v = new Path();
                v.moveTo(cx - r * 0.7f, cy - r * 0.05f);
                v.cubicTo(cx - r * 0.3f, cy - r * 0.15f, cx + r * 0.1f, cy + r * 0.05f, cx + r * 0.55f, cy - r * 0.02f);
                c.drawPath(v, can);
                speckle(c, cx, cy, r, 54, 200, 0x22FFE0C8, 0x22400A00);
                craterField(c, cx, cy, r, 5, 8, 0x44701A0C, 0x55FFC8A8);
                p.setColor(0xF0FFFFFF);
                c.drawPath(blobPath(cx, cy - r * 0.95f, r * 0.42f, 55, 0.2f), p);
                break;
            }
            case Defs.SATURN: {
                int[] bc = {0x33A0702A, 0x22FFFFFF, 0x44A0702A, 0x22FFF4D0, 0x33885820, 0x22FFFFFF, 0x44A0702A};
                for (int i = 0; i < bc.length; i++)
                    wavyBand(c, cx, cy, r, cy - r + (i + 0.5f) * 2 * r / bc.length, r * 0.22f, r * 0.02f, 0.6f, i, bc[i]);
                // ring shadow on the planet
                c.save();
                c.rotate(-14, cx, cy);
                Paint rs = stroke(r * 0.12f, 0x55301A00);
                c.drawArc(new RectF(cx - r * 1.6f, cy - r * 0.2f, cx + r * 1.6f, cy + r * 0.62f), 200, 140, false, rs);
                c.restore();
                break;
            }
            case Defs.JUPITER: {
                int[] bc = {0x66B86A34, 0x44FFF2E0, 0x88C0703A, 0x55FFF6E8, 0x77A0522A, 0x33FFE8D0, 0x88B46030, 0x55FFF0DC, 0x66A0582E};
                for (int i = 0; i < bc.length; i++)
                    wavyBand(c, cx, cy, r, cy - r + (i + 0.5f) * 2 * r / bc.length, r * 0.2f, r * 0.03f, 0.9f, i * 1.7f, bc[i]);
                float sx = cx + r * 0.3f, sy = cy + r * 0.38f;
                c.drawOval(new RectF(sx - r * 0.3f, sy - r * 0.16f, sx + r * 0.3f, sy + r * 0.16f), grad(new RadialGradient(sx, sy, r * 0.3f, 0xFFE0502E, 0xFFA02818, Shader.TileMode.CLAMP)));
                c.drawOval(new RectF(sx - r * 0.3f, sy - r * 0.16f, sx + r * 0.3f, sy + r * 0.16f), stroke(r * 0.04f, 0xAAFFD8B8));
                c.drawArc(new RectF(sx - r * 0.18f, sy - r * 0.08f, sx + r * 0.18f, sy + r * 0.08f), 0, 270, false, stroke(r * 0.03f, 0x88FFB090));
                p.setColor(0xCCFFF6EA);
                c.drawOval(new RectF(cx - r * 0.5f, cy - r * 0.42f, cx - r * 0.3f, cy - r * 0.33f), p);
                c.drawOval(new RectF(cx - r * 0.2f, cy - r * 0.44f, cx - r * 0.05f, cy - r * 0.36f), p);
                break;
            }
            case Defs.VENUS: {
                for (int i = 0; i < 7; i++)
                    wavyBand(c, cx, cy, r, cy - r + (i + 0.5f) * 2 * r / 7, r * 0.18f, r * 0.08f, 0.5f, i * 2.1f, (i % 2 == 0) ? 0x33FFFFFF : 0x33C06A10);
                Paint sw = stroke(r * 0.06f, 0x44FFFFFF);
                for (int i = 0; i < 4; i++) {
                    float rr = r * (0.25f + i * 0.17f);
                    c.drawArc(new RectF(cx - rr, cy - rr * 0.6f, cx + rr, cy + rr * 0.6f), 160 + i * 25, 200, false, sw);
                }
                c.drawCircle(cx, cy, r, grad(new RadialGradient(cx, cy, r, 0x00FFF4D0, 0x55FFF4D0, Shader.TileMode.CLAMP)));
                break;
            }
            case Defs.URANUS: {
                for (int i = 0; i < 5; i++)
                    wavyBand(c, cx, cy, r, cy - r + (i + 0.5f) * 2 * r / 5, r * 0.3f, r * 0.01f, 0.4f, i, (i % 2 == 0) ? 0x18FFFFFF : 0x1A1A7080);
                c.drawCircle(cx - r * 0.1f, cy - r * 0.6f, r * 0.45f, grad(new RadialGradient(cx - r * 0.1f, cy - r * 0.6f, r * 0.45f, 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)));
                break;
            }
            case Defs.NEPTUNE: {
                int[] bc = {0x2266C8FF, 0x33102080, 0x2280D8FF, 0x44102080, 0x2266C8FF, 0x33102080};
                for (int i = 0; i < bc.length; i++)
                    wavyBand(c, cx, cy, r, cy - r + (i + 0.5f) * 2 * r / bc.length, r * 0.3f, r * 0.03f, 0.7f, i * 1.3f, bc[i]);
                float sx = cx - r * 0.3f, sy = cy + r * 0.3f;
                c.drawOval(new RectF(sx - r * 0.26f, sy - r * 0.14f, sx + r * 0.26f, sy + r * 0.14f), grad(new RadialGradient(sx, sy, r * 0.26f, 0xFF0A0F50, 0xFF18268A, Shader.TileMode.CLAMP)));
                Paint wc = stroke(r * 0.05f, 0xDDFFFFFF);
                c.drawLine(sx - r * 0.15f, sy - r * 0.22f, sx + r * 0.25f, sy - r * 0.25f, wc);
                c.drawLine(cx + r * 0.1f, cy - r * 0.35f, cx + r * 0.55f, cy - r * 0.38f, wc);
                c.drawLine(cx + r * 0.25f, cy + r * 0.55f, cx + r * 0.5f, cy + r * 0.53f, wc);
                break;
            }
            case Defs.PLUTO: {
                blob(c, cx - r * 0.45f, cy + r * 0.1f, r * 0.4f, 61, 0.35f, 0x66704830);
                blob(c, cx + r * 0.1f, cy - r * 0.55f, r * 0.3f, 62, 0.35f, 0x44704830);
                p.setColor(0xFFFFF4E6);
                Path h = new Path();
                float hx = cx + r * 0.22f, hy = cy + r * 0.18f, hs = r * 0.5f;
                h.moveTo(hx, hy + hs * 0.9f);
                h.cubicTo(hx - hs * 1.4f, hy, hx - hs * 0.6f, hy - hs * 1.0f, hx, hy - hs * 0.35f);
                h.cubicTo(hx + hs * 0.6f, hy - hs * 1.0f, hx + hs * 1.4f, hy, hx, hy + hs * 0.9f);
                c.drawPath(h, p);
                speckle(c, cx, cy, r, 63, 200, 0x22FFFFFF, 0x22402010);
                craterField(c, cx, cy, r, 64, 6, 0x33603820, 0x44FFFFFF);
                break;
            }
            case Defs.SUN: {
                speckle(c, cx, cy, r, 71, 420, 0x33FFFFC0, 0x33FF6A00);
                Random rd = new Random(72);
                for (int i = 0; i < 18; i++) {
                    float a = rd.nextFloat() * 6.283f, d = (float) Math.sqrt(rd.nextFloat()) * r * 0.85f;
                    float x = cx + (float) Math.cos(a) * d, y = cy + (float) Math.sin(a) * d, s = r * (0.06f + rd.nextFloat() * 0.08f);
                    c.drawCircle(x, y, s, grad(new RadialGradient(x, y, s, 0x66FFFFE0, 0x00FFFFE0, Shader.TileMode.CLAMP)));
                }
                p.setColor(0xAA8A2A00);
                c.drawOval(new RectF(cx - r * 0.42f, cy + r * 0.3f, cx - r * 0.26f, cy + r * 0.42f), p);
                c.drawOval(new RectF(cx + r * 0.35f, cy - r * 0.15f, cx + r * 0.45f, cy - r * 0.07f), p);
                c.drawCircle(cx, cy, r, grad(new RadialGradient(cx, cy, r, new int[]{0x00FFFFFF, 0x00FFFFFF, 0x55FF7A00},
                        new float[]{0f, 0.75f, 1f}, Shader.TileMode.CLAMP)));
                break;
            }
            default: break;
        }
        if (type != Defs.SUN) lighting(c, cx, cy, r);
        else c.drawCircle(cx - r * 0.3f, cy - r * 0.35f, r * 0.5f, grad(new RadialGradient(cx - r * 0.3f, cy - r * 0.35f, r * 0.5f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)));
        c.restore();
        outline(c, cx, cy, r, ow);
        if (type == Defs.SATURN) {
            rings(c, cx, cy, r, -14, true, new int[]{0xFF8A6A35, 0xFFE9D29A, 0xFFC7A866, 0xFFF4E4BC},
                    new float[]{1.32f, 1.48f, 1.66f, 1.82f}, 0.13f);
            c.save();
            c.rotate(-14, cx, cy);
            c.drawArc(new RectF(cx - r * 1.92f, cy - r * 0.54f, cx + r * 1.92f, cy + r * 0.54f), 0, 180, false, stroke(ow, OUTLINE));
            c.restore();
        }
        if (type == Defs.URANUS)
            rings(c, cx, cy, r, -72, true, new int[]{0xAADDFBFF, 0x88BDF6FF, 0xAAE8FFFF},
                    new float[]{1.45f, 1.6f, 1.75f}, 0.035f);
        return b;
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

    // ------------------------------------------------------------------ enemies
    static Bitmap enemy(int type, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float cx = size / 2f, cy = size / 2f, r = size * 0.38f, ow = size * 0.035f;
        switch (type) {
            case Defs.E_METEOR: rock(c, cx, cy, r, 0xFFC99A6B, 0xFF5E3A22, 21); break;
            case Defs.E_MEGA: rock(c, cx, cy, r, 0xFFE0705A, 0xFF5A1A1A, 33); break;
            case Defs.E_SHOOT: {
                Path sp = starPath(cx, cy, r * 1.05f, r * 0.5f, 5);
                Paint p = fill();
                p.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.4f, 0xFFFFF7B0, 0xFFFFB000, Shader.TileMode.CLAMP));
                c.drawPath(sp, p);
                c.drawPath(sp, stroke(ow, OUTLINE));
                face(c, cx, cy + r * 0.05f, r * 0.75f, true);
                break;
            }
            case Defs.E_EXOMOON: {
                glow(c, cx, cy, size * 0.5f, 0x887CFF8A);
                ball(c, cx, cy, r * 0.9f, 0xFFE6D9FF, 0xFF7A63B8);
                c.save();
                c.clipPath(circlePath(cx, cy, r * 0.9f));
                craters(c, cx, cy, r * 0.9f, 0x5547308A, 8, 6);
                shade(c, cx, cy, r * 0.9f);
                c.restore();
                outline(c, cx, cy, r * 0.9f, ow);
                face(c, cx, cy, r * 0.9f, true);
                break;
            }
            case Defs.E_ALTMOON: {
                ball(c, cx, cy, r * 0.9f, 0xFF4FE0D0, 0xFF0F3A4A);
                c.save();
                c.clipPath(circlePath(cx, cy, r * 0.9f));
                craters(c, cx, cy, r * 0.9f, 0x66FF4FD8, 9, 6);
                shade(c, cx, cy, r * 0.9f);
                c.restore();
                c.drawCircle(cx, cy, r * 0.9f, stroke(ow * 1.2f, 0xFFFF4FD8));
                face(c, cx, cy, r * 0.9f, true);
                break;
            }
            case Defs.E_EXOPLANET: {
                RectF ring = new RectF(cx - r * 1.25f, cy - r * 0.32f, cx + r * 1.25f, cy + r * 0.32f);
                c.drawOval(ring, stroke(size * 0.06f, OUTLINE));
                c.drawOval(ring, stroke(size * 0.035f, 0xFF7CFF8A));
                ball(c, cx, cy, r * 0.85f, 0xFF7CF5D0, 0xFF117060);
                c.save();
                c.clipPath(circlePath(cx, cy, r * 0.85f));
                bands(c, cx, cy, r * 0.85f, new int[]{0x00000000, 0x55004030, 0x00000000, 0x44004030});
                shade(c, cx, cy, r * 0.85f);
                c.restore();
                outline(c, cx, cy, r * 0.85f, ow);
                c.drawArc(ring, 0, 180, false, stroke(size * 0.06f, OUTLINE));
                c.drawArc(ring, 0, 180, false, stroke(size * 0.035f, 0xFF7CFF8A));
                face(c, cx, cy, r * 0.85f, true);
                break;
            }
            case Defs.E_ALTPLANET: {
                c.save();
                c.clipPath(circlePath(cx, cy, r));
                Paint p = fill();
                p.setColor(0xFFFF5BD8);
                c.drawRect(cx - r, cy - r, cx, cy + r, p);
                p.setColor(0xFF3FD6FF);
                c.drawRect(cx, cy - r, cx + r, cy + r, p);
                p.setColor(0x55FFFFFF);
                for (int i = 0; i < 5; i++) c.drawRect(cx - r, cy - r + i * r * 0.45f, cx + r, cy - r + i * r * 0.45f + r * 0.06f, p);
                shade(c, cx, cy, r);
                c.restore();
                outline(c, cx, cy, r, ow);
                face(c, cx, cy, r, true);
                break;
            }
            case Defs.E_HELL: {
                glow(c, cx, cy, size * 0.5f, 0x88FF3B1F);
                ball(c, cx, cy, r, 0xFF6B1F2A, 0xFF1E0610);
                c.save();
                c.clipPath(circlePath(cx, cy, r));
                Paint lava = stroke(r * 0.08f, 0xFFFF7A1F);
                Random rd = new Random(4);
                for (int i = 0; i < 6; i++) {
                    Path l = new Path();
                    float x = cx - r + rd.nextFloat() * r * 2, y = cy - r + rd.nextFloat() * r * 2;
                    l.moveTo(x, y);
                    for (int k = 0; k < 3; k++) {
                        x += (rd.nextFloat() - 0.5f) * r * 0.8f;
                        y += (rd.nextFloat() - 0.3f) * r * 0.6f;
                        l.lineTo(x, y);
                    }
                    c.drawPath(l, lava);
                }
                c.restore();
                outline(c, cx, cy, r, ow);
                face(c, cx, cy, r, true);
                break;
            }
            default: break;
        }
        return b;
    }

    static void glow(Canvas c, float cx, float cy, float r, int color) {
        Paint g = fill();
        g.setShader(new RadialGradient(cx, cy, r, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, g);
    }

    static void rock(Canvas c, float cx, float cy, float r, int light, int dark, int seed) {
        Random rd = new Random(seed);
        Path path = new Path();
        int n = 11;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            float rr = r * (0.82f + rd.nextFloat() * 0.2f);
            float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        Paint p = fill();
        p.setShader(new RadialGradient(cx - r * 0.35f, cy - r * 0.4f, r * 1.5f, light, dark, Shader.TileMode.CLAMP));
        c.drawPath(path, p);
        c.save();
        c.clipPath(path);
        craters(c, cx, cy, r, 0x44000000, seed + 1, 5);
        c.restore();
        c.drawPath(path, stroke(r * 0.09f, OUTLINE));
        face(c, cx, cy + r * 0.05f, r * 0.85f, true);
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
