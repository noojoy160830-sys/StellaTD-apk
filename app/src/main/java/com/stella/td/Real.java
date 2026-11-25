package com.stella.td;

import android.graphics.Bitmap;

/**
 * Per-pixel "realistic" renderer: planets are shaded spheres whose surfaces come from 3D noise,
 * with sunlight, terminator, atmosphere scattering and emissive parts (lava, city glow, the Sun).
 */
final class Real {
    private Real() {}

    // ------------------------------------------------------------------ noise
    private static int hash(int x, int y, int z, int s) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177 + s * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    private static float rnd(int x, int y, int z, int s) {
        return (hash(x, y, z, s) & 0xFFFFFF) / 16777215f;
    }

    private static float sm(float t) { return t * t * (3 - 2 * t); }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    static float noise(float x, float y, float z, int s) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        float xf = sm(x - xi), yf = sm(y - yi), zf = sm(z - zi);
        float a = lerp(rnd(xi, yi, zi, s), rnd(xi + 1, yi, zi, s), xf);
        float b = lerp(rnd(xi, yi + 1, zi, s), rnd(xi + 1, yi + 1, zi, s), xf);
        float c = lerp(rnd(xi, yi, zi + 1, s), rnd(xi + 1, yi, zi + 1, s), xf);
        float d = lerp(rnd(xi, yi + 1, zi + 1, s), rnd(xi + 1, yi + 1, zi + 1, s), xf);
        return lerp(lerp(a, b, yf), lerp(c, d, yf), zf);
    }

    static float fbm(float x, float y, float z, int oct, int s) {
        float sum = 0, amp = 0.5f, norm = 0;
        for (int i = 0; i < oct; i++) {
            sum += noise(x, y, z, s + i * 31) * amp;
            norm += amp;
            amp *= 0.5f;
            x *= 2.03f;
            y *= 2.03f;
            z *= 2.03f;
        }
        return sum / norm;
    }

    /** Worley noise: returns {F1, F2} distances to the nearest feature points */
    static void worley(float x, float y, float z, int s, float[] out) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        float f1 = 9, f2 = 9;
        for (int dz = -1; dz <= 1; dz++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int cx = xi + dx, cy = yi + dy, cz = zi + dz;
                    float px = cx + rnd(cx, cy, cz, s), py = cy + rnd(cx, cy, cz, s + 7), pz = cz + rnd(cx, cy, cz, s + 13);
                    float ddx = px - x, ddy = py - y, ddz = pz - z;
                    float d = ddx * ddx + ddy * ddy + ddz * ddz;
                    if (d < f1) { f2 = f1; f1 = d; }
                    else if (d < f2) f2 = d;
                }
        out[0] = (float) Math.sqrt(f1);
        out[1] = (float) Math.sqrt(f2);
    }

    // ------------------------------------------------------------------ colour helpers
    static int mix(int a, int b, float t) {
        if (t <= 0) return a;
        if (t >= 1) return b;
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    static int ramp(float t, int... cols) {
        t = Math.max(0, Math.min(0.9999f, t)) * (cols.length - 1);
        int i = (int) t;
        return mix(cols[i], cols[i + 1], t - i);
    }

    static float clamp(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

    static float sstep(float a, float b, float v) { return sm(clamp((v - a) / (b - a))); }

    static int scale(int c, float k) {
        int r = Math.min(255, (int) (((c >> 16) & 255) * k)), g = Math.min(255, (int) (((c >> 8) & 255) * k)), b = Math.min(255, (int) ((c & 255) * k));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** surface colour; the alpha byte is the emissive amount (0 = lit by the sun, 255 = glows on its own) */
    interface Surface {
        int color(float x, float y, float z, float[] tmp);
    }

    /**
     * @param rFrac planet radius as a fraction of the bitmap size
     * @param rough 0 for a sphere, >0 for an irregular rock silhouette
     */
    static Bitmap sphere(int size, float rFrac, Surface s, int atmo, float atmoStrength, float glow, boolean selfLit, float rough, int seed) {
        int[] px = new int[size * size];
        float c = size / 2f, R = size * rFrac;
        float lx = -0.55f, ly = -0.5f, lz = 0.67f;
        float ll = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        lx /= ll; ly /= ll; lz /= ll;
        float[] tmp = new float[2];
        float tilt = 0.25f, ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
        int ar = (atmo >> 16) & 255, ag = (atmo >> 8) & 255, ab = atmo & 255;
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                float dx = (i + 0.5f - c) / R, dy = (j + 0.5f - c) / R;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float edge = 1f;
                if (rough > 0) {
                    float ang = (float) Math.atan2(dy, dx);
                    edge = 1f - rough * fbm((float) Math.cos(ang) * 1.8f + 5, (float) Math.sin(ang) * 1.8f + 5, seed * 0.37f, 3, seed);
                    d = d / edge;
                }
                int out = 0;
                if (d < 1f) {
                    float nx = dx / edge, ny = dy / edge;
                    float nz = (float) Math.sqrt(Math.max(0, 1 - nx * nx - ny * ny));
                    // body frame (tilted axis)
                    float bx = nx * ct - ny * st, by = nx * st + ny * ct, bz = nz;
                    int col = s.color(bx, by, bz, tmp);
                    float em = ((col >>> 24) & 255) / 255f;
                    col |= 0xFF000000;
                    int lit;
                    if (selfLit) {
                        lit = scale(col, 0.62f + 0.38f * (float) Math.sqrt(nz));
                    } else {
                        float diff = nx * lx + ny * ly + nz * lz;
                        float shade = sstep(-0.18f, 0.55f, diff) * 1.08f + 0.05f;
                        lit = scale(col, shade);
                        float fres = (float) Math.pow(1 - nz, 2.4) * atmoStrength * (0.25f + 0.75f * clamp(diff + 0.3f));
                        lit = mix(lit, atmo | 0xFF000000, clamp(fres));
                        if (em > 0) lit = mix(lit, col, em);
                    }
                    float a = clamp((1f - d) * R * edge);
                    if (a >= 1f) out = lit;
                    else {
                        // anti-aliased edge blended with the halo colour
                        float ga = glow > 0 ? atmoStrength * 0.8f : 0f;
                        int br = (int) (((lit >> 16) & 255) * a + ar * (1 - a)), bg = (int) (((lit >> 8) & 255) * a + ag * (1 - a)), bb = (int) ((lit & 255) * a + ab * (1 - a));
                        int alpha = (int) (255 * Math.max(a, ga));
                        out = (alpha << 24) | (Math.min(255, br) << 16) | (Math.min(255, bg) << 8) | Math.min(255, bb);
                    }
                } else if (glow > 0 && d < 1f + glow) {
                    float t = 1f - (d - 1f) / glow;
                    float side = selfLit ? 1f : clamp(0.3f + 0.7f * ((dx * lx + dy * ly) / Math.max(0.001f, (float) Math.sqrt(dx * dx + dy * dy))));
                    float a = t * t * atmoStrength * side;
                    if (selfLit) {
                        float ang = (float) Math.atan2(dy, dx);
                        a *= 0.65f + 0.7f * fbm((float) Math.cos(ang) * 3 + d * 2, (float) Math.sin(ang) * 3, 1.7f, 3, seed + 5);
                    }
                    int alpha = (int) (255 * clamp(a));
                    out = (alpha << 24) | (ar << 16) | (ag << 8) | ab;
                }
                px[j * size + i] = out;
            }
        }
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        b.setPixels(px, 0, size, 0, 0, size, size);
        return b;
    }

    // ------------------------------------------------------------------ planet surfaces
    static Surface surface(int type) {
        switch (type) {
            case Defs.EARTH:
                return (x, y, z, t) -> {
                    float h = fbm(x * 1.7f + 3, y * 1.7f, z * 1.7f, 6, 11);
                    int col;
                    if (h < 0.52f) col = ramp(sstep(0.3f, 0.52f, h), 0xFF06204F, 0xFF0F3E86, 0xFF1F6FBE, 0xFF3A9AD8);
                    else {
                        float m = fbm(x * 4, y * 4, z * 4, 4, 12);
                        int land = ramp(m, 0xFF2F6A2C, 0xFF4E8A3A, 0xFF9C9A55, 0xFFC8A86A);
                        if (h > 0.63f) land = mix(land, 0xFF7A6652, sstep(0.63f, 0.7f, h));
                        if (h > 0.7f) land = mix(land, 0xFFF0F2F4, sstep(0.7f, 0.74f, h));
                        col = land;
                    }
                    float lat = Math.abs(y) + (fbm(x * 6, y * 6, z * 6, 3, 13) - 0.5f) * 0.12f;
                    if (lat > 0.8f) col = mix(col, 0xFFF4F8FC, sstep(0.8f, 0.86f, lat));
                    float cl = fbm(x * 2.2f + fbm(x * 3, y * 3, z * 3, 3, 15) * 1.5f, y * 4.5f, z * 2.2f, 5, 14);
                    col = mix(col, 0xFFFFFFFF, sstep(0.52f, 0.72f, cl) * 0.92f);
                    return col & 0x00FFFFFF | 0;
                };
            case Defs.MOON:
                return (x, y, z, t) -> {
                    float b = fbm(x * 3, y * 3, z * 3, 5, 21);
                    int col = ramp(b, 0xFF6E6E76, 0xFFA2A2AA, 0xFFCFCFD4);
                    float m = fbm(x * 1.3f + 7, y * 1.3f, z * 1.3f, 4, 22);
                    col = mix(col, 0xFF55555E, sstep(0.45f, 0.36f, m) * 0.8f);
                    col = craters(col, x, y, z, t, 4.5f, 23, 0xFF4A4A52, 0xFFE6E6EA);
                    col = craters(col, x, y, z, t, 11f, 24, 0xFF5A5A62, 0xFFDADADE);
                    return col & 0x00FFFFFF;
                };
            case Defs.MERCURY:
                return (x, y, z, t) -> {
                    float b = fbm(x * 3, y * 3, z * 3, 5, 31);
                    int col = ramp(b, 0xFF5E5048, 0xFF8E7E72, 0xFFBCAE9E);
                    col = craters(col, x, y, z, t, 5f, 33, 0xFF433830, 0xFFD8CABA);
                    col = craters(col, x, y, z, t, 12f, 34, 0xFF4E4238, 0xFFC8BAAA);
                    return col & 0x00FFFFFF;
                };
            case Defs.MARS:
                return (x, y, z, t) -> {
                    float b = fbm(x * 2.5f, y * 2.5f, z * 2.5f, 6, 41);
                    int col = ramp(b, 0xFF7A2E16, 0xFFB24A24, 0xFFD87A44, 0xFFE8A070);
                    float dk = fbm(x * 1.4f + 3, y * 1.4f, z * 1.4f, 4, 42);
                    col = mix(col, 0xFF4E2214, sstep(0.48f, 0.38f, dk) * 0.75f);
                    float can = Math.abs(y - 0.05f + (fbm(x * 3, 0, z * 3, 3, 43) - 0.5f) * 0.15f);
                    if (z > 0.2f) col = mix(col, 0xFF4A1A0E, sstep(0.035f, 0.0f, can) * 0.8f);
                    col = craters(col, x, y, z, t, 6f, 44, 0xFF6A2A16, 0xFFE8A070);
                    float lat = -y + (fbm(x * 8, y * 8, z * 8, 2, 45) - 0.5f) * 0.1f;
                    col = mix(col, 0xFFF6F2EE, sstep(0.82f, 0.88f, lat));
                    return col & 0x00FFFFFF;
                };
            case Defs.JUPITER:
                return (x, y, z, t) -> {
                    float w = fbm(x * 2, y * 6, z * 2, 5, 51);
                    float v = y * 7f + (w - 0.5f) * 2.4f + 0.3f * (float) Math.sin(x * 9 + z * 4);
                    float band = 0.5f + 0.5f * (float) Math.sin(v * 2.2f);
                    int col = ramp(band, 0xFF9A5A34, 0xFFC88A56, 0xFFE8CFAE, 0xFFF4E8D6);
                    col = mix(col, 0xFFB06A3A, sstep(0.6f, 0.75f, fbm(x * 8, y * 20, z * 8, 3, 52)) * 0.35f);
                    float sx = x - 0.55f, sy = (y - 0.3f) * 2.1f, sz = z - 0.6f;
                    float sd = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
                    if (sd < 0.32f) {
                        float rr = sd / 0.32f;
                        int spot = ramp(0.5f + 0.5f * (float) Math.sin(rr * 14 + w * 4), 0xFFA83A22, 0xFFD06A44, 0xFFE8A07A);
                        col = mix(col, spot, sstep(1f, 0.75f, rr));
                    }
                    return col & 0x00FFFFFF;
                };
            case Defs.SATURN:
                return (x, y, z, t) -> {
                    float w = fbm(x * 2, y * 5, z * 2, 4, 61);
                    float v = y * 6f + (w - 0.5f) * 1.2f;
                    float band = 0.5f + 0.5f * (float) Math.sin(v * 2.4f);
                    int col = ramp(band, 0xFFC8A060, 0xFFDCC08A, 0xFFF0DCB0, 0xFFF6EAD0);
                    if (y < -0.75f) col = mix(col, 0xFF8AA0B0, sstep(-0.75f, -0.95f, y) * 0.5f);
                    return col & 0x00FFFFFF;
                };
            case Defs.VENUS:
                return (x, y, z, t) -> {
                    float w = fbm(x * 2, y * 3, z * 2, 5, 71);
                    float v = y * 3f + (w - 0.5f) * 3f + (float) Math.sin(x * 4 + z * 2) * 0.4f;
                    int col = ramp(0.5f + 0.5f * (float) Math.sin(v * 2f), 0xFFC88A3A, 0xFFE0B068, 0xFFF2D8A0, 0xFFFAEACA);
                    return col & 0x00FFFFFF;
                };
            case Defs.URANUS:
                return (x, y, z, t) -> {
                    float w = fbm(x * 2, y * 4, z * 2, 3, 81);
                    int col = ramp(0.5f + 0.5f * (float) Math.sin((y * 5 + w) * 2f), 0xFF7FD0DA, 0xFF9AE2EA, 0xFFB6EEF2);
                    col = mix(col, 0xFFDDF8FA, sstep(-0.6f, -0.95f, y) * 0.6f);
                    return col & 0x00FFFFFF;
                };
            case Defs.NEPTUNE:
                return (x, y, z, t) -> {
                    float w = fbm(x * 2, y * 5, z * 2, 5, 91);
                    float v = y * 6 + (w - 0.5f) * 1.6f;
                    int col = ramp(0.5f + 0.5f * (float) Math.sin(v * 2.1f), 0xFF14247E, 0xFF2440B0, 0xFF3A66D8, 0xFF5A8AF0);
                    float sx = x + 0.45f, sy = (y - 0.25f) * 1.9f, sz = z - 0.7f;
                    float sd = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
                    col = mix(col, 0xFF0A1250, sstep(0.24f, 0.12f, sd));
                    float cl = fbm(x * 5, y * 30, z * 5, 3, 92);
                    col = mix(col, 0xFFF0F6FF, sstep(0.68f, 0.8f, cl) * 0.9f);
                    return col & 0x00FFFFFF;
                };
            case Defs.PLUTO:
                return (x, y, z, t) -> {
                    float b = fbm(x * 3, y * 3, z * 3, 5, 101);
                    int col = ramp(b, 0xFF7A5A40, 0xFFB08A66, 0xFFD8BC98);
                    float h1 = dist(x, y, z, 0.25f, 0.15f, 0.95f), h2 = dist(x, y, z, 0.55f, 0.05f, 0.82f);
                    float heart = Math.min(h1, h2) - (fbm(x * 6, y * 6, z * 6, 3, 102) - 0.5f) * 0.15f;
                    col = mix(col, 0xFFF4E8DA, sstep(0.36f, 0.3f, heart));
                    float dk = dist(x, y, z, -0.7f, 0.1f, 0.65f);
                    col = mix(col, 0xFF4A2E20, sstep(0.5f, 0.35f, dk) * 0.85f);
                    col = craters(col, x, y, z, t, 7f, 103, 0xFF6A4A36, 0xFFE0C8A8);
                    return col & 0x00FFFFFF;
                };
            case Defs.SUN:
                return (x, y, z, t) -> {
                    worley(x * 34, y * 34, z * 34, 111, t);
                    float g = clamp(t[1] - t[0]);
                    float f = fbm(x * 3, y * 3, z * 3, 5, 112);
                    int col = ramp(clamp(0.45f + g * 0.45f + (f - 0.5f) * 0.6f), 0xFFE0520A, 0xFFFF9A1C, 0xFFFFD040, 0xFFFFF6C8);
                    float spot = fbm(x * 2.5f + 9, y * 2.5f, z * 2.5f, 4, 113);
                    col = mix(col, 0xFF6A1A04, sstep(0.68f, 0.76f, spot));
                    return col & 0x00FFFFFF | 0xFF000000;
                };
            default:
                return (x, y, z, t) -> 0x888888;
        }
    }

    static float dist(float x, float y, float z, float a, float b, float c) {
        float l = (float) Math.sqrt(a * a + b * b + c * c);
        a /= l; b /= l; c /= l;
        float dx = x - a, dy = y - b, dz = z - c;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** bowl craters with bright rims from Worley noise */
    static int craters(int col, float x, float y, float z, float[] t, float freq, int seed, int floorC, int rimC) {
        worley(x * freq, y * freq, z * freq, seed, t);
        float f = t[0];
        float size = 0.18f + 0.18f * rnd((int) (x * freq), (int) (y * freq), (int) (z * freq), seed + 3);
        if (f < size) {
            float k = f / size;
            col = mix(col, floorC, (1 - k) * 0.55f);
            if (k > 0.75f) col = mix(col, rimC, (k - 0.75f) * 2.4f);
        } else if (f < size * 1.25f) {
            col = mix(col, rimC, (1 - (f - size) / (size * 0.25f)) * 0.25f);
        }
        return col;
    }

    // ------------------------------------------------------------------ enemy surfaces
    static Surface enemySurface(int type) {
        switch (type) {
            case Defs.E_METEOR:
                return (x, y, z, t) -> {
                    float b = fbm(x * 3, y * 3, z * 3, 5, 201);
                    int col = ramp(b, 0xFF3A2E28, 0xFF6E5A4C, 0xFFA48C78);
                    col = craters(col, x, y, z, t, 4f, 202, 0xFF2A201C, 0xFFB8A08A);
                    return col & 0x00FFFFFF;
                };
            case Defs.E_MEGA:
                return (x, y, z, t) -> {
                    float b = fbm(x * 3, y * 3, z * 3, 5, 211);
                    int col = ramp(b, 0xFF1E1412, 0xFF3E2A24, 0xFF6A4A3C);
                    worley(x * 3.2f, y * 3.2f, z * 3.2f, 212, t);
                    float crack = t[1] - t[0];
                    if (crack < 0.09f) {
                        float k = 1 - crack / 0.09f;
                        int lava = ramp(k, 0xFFB0280A, 0xFFFF7A1A, 0xFFFFE070);
                        return (lava & 0x00FFFFFF) | ((int) (k * 255) << 24);
                    }
                    return col & 0x00FFFFFF;
                };
            case Defs.E_EXOMOON:
                return (x, y, z, t) -> {
                    float b = fbm(x * 3, y * 3, z * 3, 5, 221);
                    int col = ramp(b, 0xFF3E3850, 0xFF6A6282, 0xFF9A92B4);
                    worley(x * 5, y * 5, z * 5, 222, t);
                    if (t[0] < 0.22f) {
                        float k = 1 - t[0] / 0.22f;
                        return (mix(col, 0xFF7CFF8A, k) & 0x00FFFFFF) | ((int) (k * 200) << 24);
                    }
                    return col & 0x00FFFFFF;
                };
            case Defs.E_ALTMOON:
                return (x, y, z, t) -> {
                    float b = fbm(x * 3, y * 3, z * 3, 5, 231);
                    int col = ramp(b, 0xFF0A2630, 0xFF1E5662, 0xFF3E8C96);
                    col = craters(col, x, y, z, t, 5f, 232, 0xFF08161C, 0xFF7AD8E0);
                    worley(x * 2.5f, y * 2.5f, z * 2.5f, 233, t);
                    if (t[1] - t[0] < 0.05f) return (0xFFFF4FD8 & 0x00FFFFFF) | 0xE0000000;
                    return col & 0x00FFFFFF;
                };
            case Defs.E_EXOPLANET:
                return (x, y, z, t) -> {
                    float w = fbm(x * 2, y * 5, z * 2, 5, 241);
                    int col = ramp(0.5f + 0.5f * (float) Math.sin((y * 6 + (w - 0.5f) * 2) * 2.2f), 0xFF0A3A30, 0xFF1E7A5A, 0xFF5ADCA0, 0xFFB8FFC8);
                    return col & 0x00FFFFFF;
                };
            case Defs.E_ALTPLANET:
                return (x, y, z, t) -> {
                    float w = fbm(x * 2, y * 4, z * 2, 5, 251);
                    int col = ramp(0.5f + 0.5f * (float) Math.sin((y * 5 + x * 2 + (w - 0.5f) * 2.5f) * 2f), 0xFF3A1270, 0xFF8A2FD0, 0xFFFF5BD8, 0xFF3FB8FF);
                    return col & 0x00FFFFFF;
                };
            case Defs.E_HELL:
                return (x, y, z, t) -> {
                    float b = fbm(x * 3, y * 3, z * 3, 5, 261);
                    int col = ramp(b, 0xFF120606, 0xFF2E1210, 0xFF4E2420);
                    worley(x * 4, y * 4, z * 4, 262, t);
                    float crack = t[1] - t[0];
                    if (crack < 0.12f) {
                        float k = 1 - crack / 0.12f;
                        return (ramp(k, 0xFF8A1A08, 0xFFFF5A1A, 0xFFFFD060) & 0x00FFFFFF) | ((int) (k * 255) << 24);
                    }
                    return col & 0x00FFFFFF;
                };
            default:
                return (x, y, z, t) -> 0x888888;
        }
    }

    static Bitmap enemy(int type, int size) {
        switch (type) {
            case Defs.E_METEOR: return sphere(size, 0.4f, enemySurface(type), 0xFF6E5A4C, 0f, 0f, false, 0.3f, 7);
            case Defs.E_MEGA: return sphere(size, 0.4f, enemySurface(type), 0xFFFF6A1A, 0.6f, 0.2f, false, 0.28f, 9);
            case Defs.E_EXOMOON: return sphere(size, 0.36f, enemySurface(type), 0xFF7CFF8A, 0.8f, 0.3f, false, 0f, 3);
            case Defs.E_ALTMOON: return sphere(size, 0.36f, enemySurface(type), 0xFFFF4FD8, 0.8f, 0.3f, false, 0f, 4);
            case Defs.E_EXOPLANET: return sphere(size, 0.32f, enemySurface(type), 0xFF7CFFC0, 0.9f, 0.3f, false, 0f, 5);
            case Defs.E_ALTPLANET: return sphere(size, 0.38f, enemySurface(type), 0xFFFF6AE0, 0.9f, 0.25f, false, 0f, 6);
            case Defs.E_HELL: return sphere(size, 0.38f, enemySurface(type), 0xFFFF3B1F, 1f, 0.3f, false, 0f, 8);
            default: return null;
        }
    }

    // ------------------------------------------------------------------ spiral galaxy (our base)
    static Bitmap galaxy(int size) {
        int[] px = new int[size * size];
        float c = size / 2f;
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                float dx = (i + 0.5f - c) / c, dy = (j + 0.5f - c) / c;
                float r = (float) Math.sqrt(dx * dx + dy * dy);
                if (r > 1f) { px[j * size + i] = 0; continue; }
                float th = (float) Math.atan2(dy, dx);
                float arm = (float) Math.cos(2 * (th - (float) Math.log(r + 0.05f) * 2.4f));
                float armI = (float) Math.pow(Math.max(0, arm), 3) * (float) Math.exp(-r * 2.2f);
                float n = fbm(dx * 6 + 9, dy * 6, 1.3f, 4, 301);
                float dust = sstep(0.55f, 0.75f, fbm(dx * 9, dy * 9, 4.1f, 3, 302)) * (float) Math.max(0, arm) * 0.6f;
                float core = (float) Math.exp(-r * r * 40);
                float bulge = (float) Math.exp(-r * 7);
                float bright = clamp(armI * (0.6f + 0.8f * n) * 1.4f + bulge * 0.9f + core - dust * 0.4f);
                int col = mix(0xFF6F8FFF, 0xFFE8F0FF, clamp(armI * 1.5f));
                col = mix(col, 0xFFFFE6B0, clamp(bulge * 1.4f));
                col = mix(col, 0xFFFFFFFF, core);
                if (armI > 0.15f && fbm(dx * 22, dy * 22, 7.7f, 2, 303) > 0.72f) col = mix(col, 0xFFFF6AC8, 0.7f);
                float a = clamp(bright * (1 - sstep(0.85f, 1f, r)));
                if (rnd(i, j, 3, 304) > 0.996f && r < 0.9f) { col = 0xFFFFFFFF; a = Math.max(a, 0.9f); }
                px[j * size + i] = ((int) (a * 255) << 24) | (col & 0x00FFFFFF);
            }
        }
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        b.setPixels(px, 0, size, 0, 0, size, size);
        return b;
    }
}
