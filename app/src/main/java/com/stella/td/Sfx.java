package com.stella.td;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

/**
 * Sound effects, synthesized at start-up (no audio files needed): each sound is generated as a short
 * 16-bit WAV in the cache folder and loaded into a SoundPool, which mixes overlapping sounds.
 * Safe to call from anywhere: before init (or in tests) play() does nothing.
 */
final class Sfx {
    private Sfx() {}

    static final int CLICK = 0, TAB = 1, OK = 2, ERROR = 3, COIN = 4, ROLL = 5, RARE = 6, PLACE = 7, UPGRADE = 8,
            S_MISSILE = 9, S_ROCK = 10, S_RAPID = 11, S_FIRE = 12, S_ICE = 13, S_LANCE = 14, S_WAVE = 15,
            HIT = 16, KILL = 17, BOSS_KILL = 18, BASE_HIT = 19, WAVE_START = 20, WIN = 21, LOSE = 22, GACHA = 23, SELL = 24;
    static final int COUNT = 25;

    /** shortest gap between two plays of the same sound (seconds) */
    private static final float[] GAP = {0.04f, 0.05f, 0.1f, 0.15f, 0.06f, 0.1f, 0.2f, 0.05f, 0.1f,
            0.07f, 0.08f, 0.06f, 0.1f, 0.08f, 0.1f, 0.3f, 0.05f, 0.05f, 0.3f, 0.2f, 0.5f, 1f, 1f, 0.3f, 0.1f};
    private static final float[] VOL = {0.55f, 0.5f, 0.7f, 0.6f, 0.7f, 0.6f, 0.8f, 0.6f, 0.7f,
            0.35f, 0.4f, 0.22f, 0.35f, 0.35f, 0.55f, 0.6f, 0.25f, 0.35f, 0.9f, 0.7f, 0.6f, 0.8f, 0.8f, 0.7f, 0.7f};

    static volatile boolean enabled = true;
    private static SoundPool pool;
    private static final int[] ids = new int[COUNT];
    private static final long[] last = new long[COUNT];
    private static volatile boolean ready;
    private static long windowStart;
    private static int windowCount;

    static void init(Context ctx) {
        if (pool != null) return;
        try {
            pool = new SoundPool.Builder().setMaxStreams(12).setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        } catch (RuntimeException e) {
            pool = null;
            return;
        }
        final File dir = ctx.getCacheDir();
        new Thread(() -> {
            try {
                for (int i = 0; i < COUNT; i++) {
                    File f = new File(dir, "sfx_v1_" + i + ".wav");
                    if (!f.exists() || f.length() < 100) writeWav(f, make(i));
                    ids[i] = pool.load(f.getPath(), 1);
                }
                ready = true;
            } catch (Exception ignored) { }
        }, "sfx-gen").start();
    }

    static void play(int id) { play(id, 1f); }

    static void play(int id, float pitch) {
        if (!enabled || !ready || pool == null) return;
        long now = System.nanoTime() / 1000000L;
        if (now - last[id] < GAP[id] * 1000) return;
        // never more than 16 sounds per 250 ms, so big fights stay pleasant
        if (now - windowStart > 250) { windowStart = now; windowCount = 0; }
        if (++windowCount > 16) return;
        last[id] = now;
        float v = VOL[id];
        pool.play(ids[id], v, v, id <= GACHA && id >= WIN ? 2 : 1, 0, Math.max(0.5f, Math.min(2f, pitch)));
    }

    // ------------------------------------------------------------------ synthesis
    static final int RATE = 22050;
    private static final Random R = new Random(7);

    static float[] make(int id) {
        switch (id) {
            case CLICK: return tone(1700, 1100, 0.045f, 0, 0.002f, 1f);
            case TAB: return mix(tone(900, 1350, 0.07f, 0, 0.003f, 0.8f), tone(1800, 2700, 0.05f, 0, 0.002f, 0.25f));
            case OK: return seq(new float[]{1046, 1318, 1568}, 0.07f, 0.16f, 0);
            case ERROR: return mix(tone(240, 150, 0.2f, 2, 0.005f, 0.6f), tone(245, 155, 0.2f, 2, 0.005f, 0.4f));
            case COIN: return cat(tone(1318, 1318, 0.06f, 1, 0.002f, 0.6f), tone(1975, 1975, 0.18f, 1, 0.002f, 0.6f));
            case SELL: return cat(tone(1568, 1568, 0.05f, 1, 0.002f, 0.6f), cat(tone(1318, 1318, 0.05f, 1, 0.002f, 0.6f), tone(2093, 2093, 0.2f, 1, 0.002f, 0.6f)));
            case ROLL: return mix(noise(0.35f, 0.2f, 2500, 600), tone(300, 1200, 0.35f, 3, 0.02f, 0.35f));
            case RARE: return mix(seq(new float[]{1568, 2093, 2637, 3136, 4186}, 0.06f, 0.4f, 0), noise(0.6f, 0.08f, 9000, 6000));
            case PLACE: return mix(tone(380, 900, 0.09f, 0, 0.002f, 0.8f), noise(0.05f, 0.3f, 3000, 1000));
            case UPGRADE: return seq(new float[]{523, 659, 784, 1046}, 0.05f, 0.18f, 1);
            case S_MISSILE: return mix(tone(820, 260, 0.14f, 3, 0.003f, 0.6f), noise(0.1f, 0.25f, 4000, 800));
            case S_ROCK: return mix(tone(190, 70, 0.14f, 0, 0.002f, 0.9f), noise(0.08f, 0.4f, 900, 200));
            case S_RAPID: return tone(2100, 1500, 0.035f, 1, 0.001f, 0.6f);
            case S_FIRE: return noise(0.22f, 0.7f, 1800, 300);
            case S_ICE: return mix(tone(2637, 2400, 0.16f, 0, 0.002f, 0.5f), tone(3951, 3600, 0.12f, 0, 0.002f, 0.3f));
            case S_LANCE: return mix(tone(260, 1300, 0.26f, 3, 0.004f, 0.6f), noise(0.26f, 0.35f, 5000, 1200));
            case S_WAVE: return mix(noise(0.6f, 0.6f, 700, 150), tone(110, 70, 0.6f, 0, 0.08f, 0.5f));
            case HIT: return noise(0.025f, 0.6f, 5000, 2000);
            case KILL: return mix(tone(640, 180, 0.09f, 0, 0.002f, 0.7f), noise(0.06f, 0.3f, 3000, 600));
            case BOSS_KILL: return mix(noise(0.9f, 0.9f, 1200, 80), tone(90, 40, 0.9f, 0, 0.005f, 0.7f));
            case BASE_HIT: return mix(tone(95, 50, 0.25f, 0, 0.003f, 1f), noise(0.15f, 0.5f, 700, 150));
            case WAVE_START: return cat(tone(392, 392, 0.12f, 3, 0.01f, 0.45f), tone(587, 587, 0.3f, 3, 0.01f, 0.45f));
            case WIN: return seq(new float[]{523, 659, 784, 1046, 1318}, 0.11f, 0.45f, 1);
            case LOSE: return seq(new float[]{392, 330, 262, 196}, 0.16f, 0.5f, 2);
            case GACHA: return mix(noise(0.5f, 0.25f, 3000, 1500), seq(new float[]{784, 988, 1175, 1568}, 0.09f, 0.3f, 0));
            default: return new float[1];
        }
    }

    /** wave: 0 sine, 1 triangle, 2 square, 3 saw. Pitch glides from f0 to f1 with a quick attack and exponential decay */
    static float[] tone(float f0, float f1, float dur, int wave, float attack, float vol) {
        int n = (int) (dur * RATE);
        float[] out = new float[n];
        double ph = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE, k = i / (float) n;
            double f = f0 * Math.pow(f1 / f0, k);
            ph += f / RATE;
            double x = ph - Math.floor(ph), s;
            switch (wave) {
                case 1: s = 4 * Math.abs(x - 0.5) - 1; break;
                case 2: s = x < 0.5 ? 0.7 : -0.7; break;
                case 3: s = (2 * x - 1) * 0.8; break;
                default: s = Math.sin(2 * Math.PI * x);
            }
            float env = Math.min(1f, t / attack) * (float) Math.exp(-4.5 * k);
            out[i] = (float) s * env * vol;
        }
        return out;
    }

    /** filtered noise whose cutoff slides from c0 to c1 Hz */
    static float[] noise(float dur, float vol, float c0, float c1) {
        int n = (int) (dur * RATE);
        float[] out = new float[n];
        float y = 0;
        for (int i = 0; i < n; i++) {
            float k = i / (float) n;
            float cut = c0 + (c1 - c0) * k;
            float a = (float) (1 - Math.exp(-2 * Math.PI * cut / RATE));
            y += a * ((R.nextFloat() * 2 - 1) - y);
            float env = Math.min(1f, i / (RATE * 0.004f)) * (float) Math.exp(-4 * k);
            out[i] = y * env * vol * 1.6f;
        }
        return out;
    }

    /** a little melody: each note `step` seconds apart, the last one rings for `tail` */
    static float[] seq(float[] notes, float step, float tail, int wave) {
        float[] out = new float[(int) ((step * (notes.length - 1) + tail) * RATE)];
        for (int k = 0; k < notes.length; k++) {
            float[] t = tone(notes[k], notes[k], k == notes.length - 1 ? tail : step * 2.2f, wave, 0.003f, 0.5f);
            int off = (int) (k * step * RATE);
            for (int i = 0; i < t.length && off + i < out.length; i++) out[off + i] += t[i];
        }
        return out;
    }

    static float[] mix(float[] a, float[] b) {
        float[] out = new float[Math.max(a.length, b.length)];
        for (int i = 0; i < out.length; i++) out[i] = (i < a.length ? a[i] : 0) + (i < b.length ? b[i] : 0);
        return out;
    }

    static float[] cat(float[] a, float[] b) {
        float[] out = new float[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    static byte[] pcm16(float[] s) {
        float peak = 0.0001f;
        for (float v : s) peak = Math.max(peak, Math.abs(v));
        float g = peak > 0.95f ? 0.95f / peak : 1f;
        byte[] b = new byte[s.length * 2];
        for (int i = 0; i < s.length; i++) {
            int v = (int) (Math.max(-1f, Math.min(1f, s[i] * g)) * 32767);
            b[i * 2] = (byte) v;
            b[i * 2 + 1] = (byte) (v >> 8);
        }
        return b;
    }

    static void writeWav(File f, float[] samples) throws Exception {
        byte[] data = pcm16(samples);
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(new byte[]{'R', 'I', 'F', 'F'});
            le32(o, 36 + data.length);
            o.write(new byte[]{'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
            le32(o, 16);
            le16(o, 1);          // PCM
            le16(o, 1);          // mono
            le32(o, RATE);
            le32(o, RATE * 2);   // byte rate
            le16(o, 2);          // block align
            le16(o, 16);         // bits
            o.write(new byte[]{'d', 'a', 't', 'a'});
            le32(o, data.length);
            o.write(data);
        }
    }

    private static void le32(FileOutputStream o, int v) throws Exception { o.write(new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)}); }

    private static void le16(FileOutputStream o, int v) throws Exception { o.write(new byte[]{(byte) v, (byte) (v >> 8)}); }
}
