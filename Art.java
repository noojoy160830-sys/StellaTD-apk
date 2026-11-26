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

    // ------------------------------------------------------------------ planets
    static Bitmap planet(int type, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float cx = size / 2f, cy = size / 2f;
        float r = size * 0.36f, ow = size * 0.035f;
        if (type == Defs.SATELLITE) {
            satellite(c, cx, cy, size);
            return b;
        }
        if (type == Defs.SATURN || type == Defs.URANUS) r = size * 0.28f;
        if (type == Defs.SUN) {
            r = size * 0.3f;
            Paint g = fill();
            g.setShader(new RadialGradient(cx, cy, size * 0.5f, 0xAAFFC93C, 0x00FF7A00, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, size * 0.5f, g);
            Paint ray = fill();
            ray.setColor(0xFFFF9F1C);
            Path rp = new Path();
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6;
                double a1 = a - 0.16, a2 = a + 0.16;
                rp.moveTo(cx + (float) Math.cos(a1) * r * 0.95f, cy + (float) Math.sin(a1) * r * 0.95f);
                rp.lineTo(cx + (float) Math.cos(a) * r * 1.55f, cy + (float) Math.sin(a) * r * 1.55f);
                rp.lineTo(cx + (float) Math.cos(a2) * r * 0.95f, cy + (float) Math.sin(a2) * r * 0.95f);
                rp.close();
            }
            c.drawPath(rp, ray);
            c.drawPath(rp, stroke(ow * 0.7f, 0xFFB34A00));
        }
        Paint ringP = stroke(size * 0.05f, 0xFFE9D29A);
        RectF ringOval = new RectF(cx - r * 1.7f, cy - r * 0.45f, cx + r * 1.7f, cy + r * 0.45f);
        float tilt = type == Defs.URANUS ? -65f : -14f;
        if (type == Defs.URANUS) ringP.setColor(0xFFBDF6FF);
        if (type == Defs.SATURN || type == Defs.URANUS) {
            c.save();
            c.rotate(tilt, cx, cy);
            c.drawOval(ringOval, stroke(size * 0.075f, OUTLINE));
            c.drawOval(ringOval, ringP);
            c.restore();
        }

        int light, dark;
        switch (type) {
            case Defs.EARTH: light = 0xFF6FC3FF; dark = 0xFF1F5FB8; break;
            case Defs.MOON: light = 0xFFEEEFF7; dark = 0xFF8E90A8; break;
            case Defs.SATURN: light = 0xFFFBE3A6; dark = 0xFFC9963F; break;
            case Defs.JUPITER: light = 0xFFFFD3A1; dark = 0xFFC46A2E; break;
            case Defs.MARS: light = 0xFFFF9A6B; dark = 0xFFB3341F; break;
            case Defs.MERCURY: light = 0xFFD9C3B0; dark = 0xFF7A6152; break;
            case Defs.VENUS: light = 0xFFFFE9A0; dark = 0xFFE0952E; break;
            case Defs.URANUS: light = 0xFFC2FBFF; dark = 0xFF3FB6C9; break;
            case Defs.NEPTUNE: light = 0xFF7FA0FF; dark = 0xFF2232A8; break;
            case Defs.PLUTO: light = 0xFFF4DEC4; dark = 0xFFA77F5C; break;
            case Defs.SUN: light = 0xFFFFF59A; dark = 0xFFFFA41C; break;
            default: light = 0xFFFFFFFF; dark = 0xFF888888;
        }
        ball(c, cx, cy, r, light, dark);
        c.save();
        c.clipPath(circlePath(cx, cy, r));
        Paint p = fill();
        switch (type) {
            case Defs.EARTH: {
                p.setColor(0xFF4CC26A);
                c.drawOval(new RectF(cx - r * 0.9f, cy - r * 0.6f, cx - r * 0.1f, cy + r * 0.1f), p);
                c.drawOval(new RectF(cx + r * 0.15f, cy - r * 0.1f, cx + r * 0.95f, cy + r * 0.75f), p);
                c.drawOval(new RectF(cx - r * 0.3f, cy + r * 0.55f, cx + r * 0.2f, cy + r * 0.95f), p);
                p.setColor(0xCCFFFFFF);
                c.drawOval(new RectF(cx - r * 0.5f, cy - r * 1.05f, cx + r * 0.5f, cy - r * 0.78f), p);
                break;
            }
            case Defs.MOON: craters(c, cx, cy, r, 0x55707090, 3, 7); break;
            case Defs.MERCURY: craters(c, cx, cy, r, 0x665A4030, 11, 9); break;
            case Defs.MARS: {
                craters(c, cx, cy, r, 0x55801A10, 5, 6);
                p.setColor(0xDDFFFFFF);
                c.drawOval(new RectF(cx - r * 0.35f, cy - r * 1.05f, cx + r * 0.35f, cy - r * 0.8f), p);
                break;
            }
            case Defs.SATURN: bands(c, cx, cy, r, new int[]{0x00000000, 0x44A86F25, 0x00000000, 0x33A86F25, 0x00000000}); break;
            case Defs.JUPITER: {
                bands(c, cx, cy, r, new int[]{0x55A8461E, 0x00000000, 0x66FFF0D8, 0x55A8461E, 0x00000000, 0x55A8461E});
                p.setColor(0xFFD0402A);
                c.drawOval(new RectF(cx + r * 0.1f, cy + r * 0.3f, cx + r * 0.6f, cy + r * 0.6f), p);
                break;
            }
            case Defs.VENUS: {
                Paint sw = stroke(r * 0.12f, 0x55FFFFFF);
                for (int i = 0; i < 3; i++)
                    c.drawArc(new RectF(cx - r * (0.4f + i * 0.25f), cy - r * (0.3f + i * 0.2f),
                            cx + r * (0.4f + i * 0.25f), cy + r * (0.3f + i * 0.2f)), 200 + i * 40, 160, false, sw);
                break;
            }
            case Defs.NEPTUNE: {
                bands(c, cx, cy, r, new int[]{0x00000000, 0x335BE0FF, 0x00000000, 0x445BE0FF, 0x00000000});
                p.setColor(0xFF1A2380);
                c.drawOval(new RectF(cx - r * 0.6f, cy + r * 0.35f, cx - r * 0.15f, cy + r * 0.6f), p);
                break;
            }
            case Defs.PLUTO: {
                p.setColor(0xFFFFF3E3);
                Path h = new Path();
                float hx = cx + r * 0.25f, hy = cy + r * 0.25f, hs = r * 0.45f;
                h.moveTo(hx, hy + hs * 0.9f);
                h.cubicTo(hx - hs * 1.4f, hy, hx - hs * 0.6f, hy - hs * 1.0f, hx, hy - hs * 0.35f);
                h.cubicTo(hx + hs * 0.6f, hy - hs * 1.0f, hx + hs * 1.4f, hy, hx, hy + hs * 0.9f);
                c.drawPath(h, p);
                break;
            }
            case Defs.SUN: {
                p.setColor(0x44FF6A00);
                c.drawCircle(cx - r * 0.4f, cy + r * 0.5f, r * 0.2f, p);
                c.drawCircle(cx + r * 0.5f, cy - r * 0.3f, r * 0.15f, p);
                break;
            }
            default: break;
        }
        shade(c, cx, cy, r);
        c.restore();
        outline(c, cx, cy, r, ow);
        if (type == Defs.SATURN || type == Defs.URANUS) {
            c.save();
            c.rotate(tilt, cx, cy);
            c.drawArc(ringOval, 0, 180, false, stroke(size * 0.075f, OUTLINE));
            c.drawArc(ringOval, 0, 180, false, ringP);
            c.restore();
        }
        face(c, cx, cy + r * 0.05f, r, false);
        return b;
    }

    static void satellite(Canvas c, float cx, float cy, int size) {
        float s = size;
        float ow = s * 0.03f;
        Paint p = fill();
        Paint o = stroke(ow, OUTLINE);
        // panels
        for (int side = -1; side <= 1; side += 2) {
            RectF pan = new RectF(cx + side * s * 0.18f - (side > 0 ? 0 : s * 0.28f), cy - s * 0.11f,
                    cx + side * s * 0.18f + (side > 0 ? s * 0.28f : 0), cy + s * 0.11f);
            p.setShader(new LinearGradient(pan.left, pan.top, pan.right, pan.bottom, 0xFF5BA8FF, 0xFF1D3C9E, Shader.TileMode.CLAMP));
            c.drawRect(pan, p);
            p.setShader(null);
            Paint g = stroke(s * 0.012f, 0x88BFE3FF);
            for (int i = 1; i < 3; i++) {
                float x = pan.left + pan.width() * i / 3f;
                c.drawLine(x, pan.top, x, pan.bottom, g);
            }
            c.drawLine(pan.left, cy, pan.right, cy, g);
            c.drawRect(pan, o);
        }
        // arm
        c.drawLine(cx - s * 0.2f, cy, cx + s * 0.2f, cy, stroke(s * 0.04f, 0xFF9AA3B8));
        // dish
        Paint d = stroke(s * 0.025f, 0xFFDDE3EE);
        c.drawLine(cx, cy - s * 0.16f, cx, cy - s * 0.3f, d);
        p.setColor(0xFFEDEFF5);
        c.drawArc(new RectF(cx - s * 0.12f, cy - s * 0.42f, cx + s * 0.12f, cy - s * 0.26f), 0, 180, true, p);
        c.drawArc(new RectF(cx - s * 0.12f, cy - s * 0.42f, cx + s * 0.12f, cy - s * 0.26f), 0, 180, true, o);
        p.setColor(0xFFFF5B5B);
        c.drawCircle(cx, cy - s * 0.34f, s * 0.025f, p);
        // body
        RectF body = new RectF(cx - s * 0.17f, cy - s * 0.17f, cx + s * 0.17f, cy + s * 0.17f);
        p.setShader(new LinearGradient(body.left, body.top, body.right, body.bottom, 0xFFFFE08A, 0xFFD18A1E, Shader.TileMode.CLAMP));
        c.drawRoundRect(body, s * 0.06f, s * 0.06f, p);
        p.setShader(null);
        c.drawRoundRect(body, s * 0.06f, s * 0.06f, o);
        face(c, cx, cy + s * 0.01f, s * 0.2f, false);
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
