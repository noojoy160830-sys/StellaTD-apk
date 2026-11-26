package com.stella.td;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Locale;

/** Renders every screen and handles touch. Game rules live in {@link Game}. */
public final class GameView extends View {

    static final int S_MENU = 0, S_SELECT = 1, S_HELP = 2, S_CODEX = 3, S_PLAY = 4;

    static final class Btn {
        final RectF r;
        final Runnable a;
        Btn(RectF r, Runnable a) { this.r = r; this.a = a; }
    }

    int screen = S_MENU;
    boolean paused;
    Game game;
    int speed = 1, buildSel = -1, codexTab = 0;
    Planet selPlanet;
    boolean resultSaved;
    String toast;
    float toastT;

    final SharedPreferences prefs;
    float W, H, u, ms, mox, moy, mapTop, mapBot, panelTop;
    float anim;
    long lastNs;

    Bitmap bg;
    final Bitmap[] pBmp = new Bitmap[Defs.P_COUNT];
    final Bitmap[] eBmp = new Bitmap[Defs.E_COUNT];

    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF rf = new RectF(), rf2 = new RectF();
    final ArrayList<Btn> btns = new ArrayList<>();
    final float[] tmp = new float[2];
    final Path starUnit = Art.starPath(0, 0, 1f, 0.45f, 5);
    final Path heartUnit = new Path();
    final Path pathCache = new Path();
    int pathStage = -1;
    final float[] twX = new float[50], twY = new float[50], twP = new float[50];

    public GameView(Context ctx) {
        super(ctx);
        prefs = ctx.getSharedPreferences("stella", Context.MODE_PRIVATE);
        tp.setTypeface(Typeface.DEFAULT_BOLD);
        tp.setStrokeJoin(Paint.Join.ROUND);
        sp.setStyle(Paint.Style.STROKE);
        sp.setStrokeCap(Paint.Cap.ROUND);
        sp.setStrokeJoin(Paint.Join.ROUND);
        heartUnit.moveTo(0, 0.9f);
        heartUnit.cubicTo(-1.4f, 0f, -0.7f, -1.0f, 0f, -0.35f);
        heartUnit.cubicTo(0.7f, -1.0f, 1.4f, 0f, 0f, 0.9f);
        heartUnit.close();
        java.util.Random r = new java.util.Random(3);
        for (int i = 0; i < twX.length; i++) {
            twX[i] = r.nextFloat();
            twY[i] = r.nextFloat();
            twP[i] = r.nextFloat() * 6.28f;
        }
        setFocusable(true);
    }

    // ================================================================== lifecycle
    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        W = w;
        H = h;
        u = w / 1080f;
        bg = Art.background(w, h);
        if (pBmp[0] == null) {
            for (int i = 0; i < Defs.P_COUNT; i++) pBmp[i] = Art.planet(i, 192);
            for (int i = 0; i < Defs.E_COUNT; i++)
                if (i != Defs.E_BLACKHOLE) eBmp[i] = Art.enemy(i, 160);
        }
        mapTop = 190 * u;
        panelTop = H - 360 * u;
        mapBot = panelTop - 10 * u;
        float availH = mapBot - mapTop;
        ms = Math.min((W - 20 * u) / 1000f, availH / 1200f);
        mox = (W - 1000 * ms) / 2f;
        moy = mapTop + (availH - 1200 * ms) / 2f;
    }

    void pauseGame() {
        if (screen == S_PLAY && game != null && !game.won && !game.lost) paused = true;
        lastNs = 0;
    }

    /** @return true if handled */
    boolean onBack() {
        switch (screen) {
            case S_PLAY:
                if (selPlanet != null || buildSel >= 0) { selPlanet = null; buildSel = -1; }
                else if (game != null && (game.won || game.lost)) screen = S_SELECT;
                else paused = !paused;
                return true;
            case S_SELECT: case S_HELP: case S_CODEX:
                screen = S_MENU;
                return true;
            default:
                return false;
        }
    }

    void startStage(int s) {
        game = new Game(s);
        screen = S_PLAY;
        paused = false;
        speed = 1;
        buildSel = -1;
        selPlanet = null;
        resultSaved = false;
    }

    int unlocked() { return Math.max(1, prefs.getInt("unlocked", 1)); }

    void saveResult() {
        if (resultSaved || game == null) return;
        resultSaved = true;
        SharedPreferences.Editor ed = prefs.edit();
        String key = "best" + game.stage;
        int reached = game.won ? game.wave : Math.max(0, game.wave - 1);
        if (reached > prefs.getInt(key, 0)) ed.putInt(key, reached);
        if (game.won) {
            if (game.stage + 2 > unlocked()) ed.putInt("unlocked", Math.min(Defs.ST_NAME.length, game.stage + 2));
            ed.putBoolean("clear" + game.stage, true);
        }
        ed.apply();
    }

    void showToast(String s) {
        toast = s;
        toastT = 1.6f;
    }

    // ================================================================== touch
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            tap(e.getX(), e.getY());
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    void tap(float x, float y) {
        for (int i = btns.size() - 1; i >= 0; i--) {
            Btn b = btns.get(i);
            if (b.r.contains(x, y)) {
                b.a.run();
                return;
            }
        }
        if (screen != S_PLAY || game == null || paused || game.won || game.lost) return;
        if (y < mapTop || y > panelTop) return;
        float mx = (x - mox) / ms, my = (y - moy) / ms;
        int best = -1;
        float bd = 80;
        for (int i = 0; i < game.slotX.length; i++) {
            float d = Game.dist(mx, my, game.slotX[i], game.slotY[i]);
            if (d < bd) { bd = d; best = i; }
        }
        if (best < 0) { selPlanet = null; return; }
        Planet pl = game.slots[best];
        if (pl != null) {
            selPlanet = (selPlanet == pl) ? null : pl;
            buildSel = -1;
            return;
        }
        selPlanet = null;
        if (buildSel < 0) { showToast("เลือกดวงดาวด้านล่างก่อน"); return; }
        String err = buildSel == 3 ? game.gacha(best)
                : game.place(Defs.STARTER[buildSel], best, Defs.STARTER_COST[buildSel]);
        if (err != null) showToast(err);
        else buildSel = -1;
    }

    // ================================================================== draw loop
    @Override
    protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.05f, (now - lastNs) / 1e9f);
        lastNs = now;
        anim += dt;
        if (toastT > 0) toastT -= dt;
        if (screen == S_PLAY && game != null && !paused) {
            float t = dt * speed;
            while (t > 0) {
                float s = Math.min(t, 0.02f);
                game.update(s);
                t -= s;
            }
            if (selPlanet != null && game.slots[selPlanet.slot] != selPlanet) selPlanet = null;
            if (game.won || game.lost) saveResult();
        }
        btns.clear();
        if (bg != null) c.drawBitmap(bg, 0, 0, null);
        twinkles(c);
        switch (screen) {
            case S_MENU: drawMenu(c); break;
            case S_SELECT: drawSelect(c); break;
            case S_HELP: drawHelp(c); break;
            case S_CODEX: drawCodex(c); break;
            default: drawPlay(c); break;
        }
        if (toastT > 0 && toast != null) {
            float y = screen == S_PLAY ? panelTop - 60 * u : H * 0.85f;
            pill(c, W / 2, y, toast, 40 * u, 0xFFFFFFFF, 0xEE2A1650);
        }
        postInvalidateOnAnimation();
    }

    void twinkles(Canvas c) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < twX.length; i++) {
            float a = 0.5f + 0.5f * (float) Math.sin(anim * 2.2f + twP[i]);
            p.setColor(0xFFFFFFFF);
            p.setAlpha((int) (a * 220));
            c.drawCircle(twX[i] * W, twY[i] * H, (1.2f + a * 2f) * u, p);
        }
        p.setAlpha(255);
    }

    // ================================================================== ui helpers
    void text(Canvas c, String s, float x, float y, float size, int color, Paint.Align al, float maxW) {
        tp.setTextSize(size);
        if (maxW > 0) {
            float w = tp.measureText(s);
            if (w > maxW) { size *= maxW / w; tp.setTextSize(size); }
        }
        tp.setTextAlign(al);
        tp.setStyle(Paint.Style.STROKE);
        tp.setStrokeWidth(size * 0.18f);
        tp.setColor(0xFF140A2A);
        c.drawText(s, x, y, tp);
        tp.setStyle(Paint.Style.FILL);
        tp.setColor(color);
        c.drawText(s, x, y, tp);
    }

    void text(Canvas c, String s, float x, float y, float size, int color) {
        text(c, s, x, y, size, color, Paint.Align.CENTER, 0);
    }

    void plain(Canvas c, String s, float x, float y, float size, int color, Paint.Align al, float maxW) {
        tp.setTextSize(size);
        if (maxW > 0) {
            float w = tp.measureText(s);
            if (w > maxW) tp.setTextSize(size * maxW / w);
        }
        tp.setTextAlign(al);
        tp.setStyle(Paint.Style.FILL);
        tp.setColor(color);
        c.drawText(s, x, y, tp);
    }

    void panel(Canvas c, RectF r, int color, float rad) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        rf2.set(r);
        rf2.offset(0, 8 * u);
        p.setColor(0x88000000);
        c.drawRoundRect(rf2, rad, rad, p);
        p.setColor(color);
        c.drawRoundRect(r, rad, rad, p);
        sp.setPathEffect(null);
        sp.setStrokeWidth(5 * u);
        sp.setColor(0xFF140A2A);
        c.drawRoundRect(r, rad, rad, sp);
    }

    void button(Canvas c, RectF r, String label, int color, boolean enabled, Runnable a) {
        float rad = Math.min(r.height() / 2f, 40 * u);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        rf2.set(r);
        rf2.offset(0, 9 * u);
        p.setColor(0xFF140A2A);
        c.drawRoundRect(rf2, rad, rad, p);
        p.setColor(enabled ? color : 0xFF5A5470);
        c.drawRoundRect(r, rad, rad, p);
        p.setColor(0x40FFFFFF);
        rf2.set(r.left + 14 * u, r.top + 8 * u, r.right - 14 * u, r.top + r.height() * 0.45f);
        c.drawRoundRect(rf2, rad * 0.7f, rad * 0.7f, p);
        sp.setPathEffect(null);
        sp.setStrokeWidth(5 * u);
        sp.setColor(0xFF140A2A);
        c.drawRoundRect(r, rad, rad, sp);
        if (label != null) {
            float ts = Math.min(r.height() * 0.42f, 52 * u);
            text(c, label, r.centerX(), r.centerY() + ts * 0.36f, ts, 0xFFFFFFFF, Paint.Align.CENTER, r.width() - 24 * u);
        }
        if (a != null) btns.add(new Btn(new RectF(r), a));
    }

    void pill(Canvas c, float cx, float cy, String s, float size, int color, int bgColor) {
        tp.setTextSize(size);
        float w = Math.min(tp.measureText(s) + size * 1.6f, W - 40 * u);
        rf.set(cx - w / 2, cy - size * 0.95f, cx + w / 2, cy + size * 0.75f);
        panel(c, rf, bgColor, size);
        text(c, s, cx, cy + size * 0.18f, size, color, Paint.Align.CENTER, w - size);
    }

    void star(Canvas c, float x, float y, float r) {
        c.save();
        c.translate(x, y);
        c.scale(r, r);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFD23F);
        c.drawPath(starUnit, p);
        sp.setPathEffect(null);
        sp.setStrokeWidth(0.18f);
        sp.setColor(0xFF140A2A);
        c.drawPath(starUnit, sp);
        c.restore();
    }

    void heart(Canvas c, float x, float y, float r) {
        c.save();
        c.translate(x, y);
        c.scale(r, r);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFF4D6D);
        c.drawPath(heartUnit, p);
        sp.setPathEffect(null);
        sp.setStrokeWidth(0.16f);
        sp.setColor(0xFF140A2A);
        c.drawPath(heartUnit, sp);
        c.restore();
    }

    void sprite(Canvas c, Bitmap b, float x, float y, float half, int alpha) {
        rf.set(x - half, y - half, x + half, y + half);
        bp.setAlpha(alpha);
        c.drawBitmap(b, null, rf, bp);
        bp.setAlpha(255);
    }

    void backButton(Canvas c) {
        rf.set(30 * u, 50 * u, 190 * u, 150 * u);
        button(c, rf, "กลับ", 0xFF6B5BD6, true, () -> screen = S_MENU);
    }

    static int hsv(float h) {
        h = ((h % 360) + 360) % 360;
        float x = 1 - Math.abs((h / 60f) % 2 - 1);
        float r, g, b;
        if (h < 60) { r = 1; g = x; b = 0; }
        else if (h < 120) { r = x; g = 1; b = 0; }
        else if (h < 180) { r = 0; g = 1; b = x; }
        else if (h < 240) { r = 0; g = x; b = 1; }
        else if (h < 300) { r = x; g = 0; b = 1; }
        else { r = 1; g = 0; b = x; }
        return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    // ================================================================== menu
    void drawMenu(Canvas c) {
        float cx = W / 2, cy = H * 0.38f, R = W * 0.33f;
        sp.setPathEffect(null);
        sp.setStrokeWidth(3 * u);
        sp.setColor(0x44FFFFFF);
        c.drawCircle(cx, cy, R, sp);
        c.drawCircle(cx, cy, R * 0.62f, sp);
        sprite(c, pBmp[Defs.SUN], cx, cy, 150 * u, 255);
        int[] orb = {Defs.EARTH, Defs.SATURN, Defs.NEPTUNE, Defs.MARS, Defs.URANUS, Defs.JUPITER};
        for (int i = 0; i < orb.length; i++) {
            boolean inner = i % 2 == 0;
            float rr = inner ? R * 0.62f : R;
            double a = anim * (inner ? 0.5 : 0.3) + i * Math.PI * 2 / orb.length;
            sprite(c, pBmp[orb[i]], cx + (float) Math.cos(a) * rr, cy + (float) Math.sin(a) * rr * 0.9f, 70 * u, 255);
        }
        // meteor flying across
        float mxp = ((anim * 0.18f) % 1.4f - 0.2f) * W;
        sprite(c, eBmp[Defs.E_METEOR], mxp, H * 0.12f + mxp * 0.08f, 40 * u, 255);

        text(c, "Stella TD", W / 2, H * 0.14f, 150 * u, 0xFFFFD23F);
        text(c, "ปกป้องฐานจากอุกกาบาตและหลุมดำ", W / 2, H * 0.14f + 80 * u, 44 * u, 0xFFFFFFFF);

        float bw = 600 * u, bh = 130 * u, by = H * 0.66f;
        rf.set(W / 2 - bw / 2, by, W / 2 + bw / 2, by + bh);
        button(c, rf, "เล่นเลย!", 0xFFFF8A2B, true, () -> screen = S_SELECT);
        rf.set(W / 2 - bw / 2, by + bh + 40 * u, W / 2 + bw / 2, by + bh * 2 + 40 * u);
        button(c, rf, "สมุดดวงดาว", 0xFF4F8BFF, true, () -> { codexTab = 0; screen = S_CODEX; });
        rf.set(W / 2 - bw / 2, by + bh * 2 + 80 * u, W / 2 + bw / 2, by + bh * 3 + 80 * u);
        button(c, rf, "วิธีเล่น", 0xFF33C6A6, true, () -> screen = S_HELP);
        plain(c, "v1.0", W - 30 * u, H - 30 * u, 30 * u, 0x88FFFFFF, Paint.Align.RIGHT, 0);
    }

    // ================================================================== stage select
    void drawSelect(Canvas c) {
        backButton(c);
        text(c, "เลือกด่าน", W / 2, 130 * u, 76 * u, 0xFFFFD23F);
        int unl = unlocked();
        float top = 220 * u, gap = 30 * u;
        float ch = Math.min(300 * u, (H - top - 60 * u) / 4f - gap);
        for (int i = 0; i < Defs.ST_NAME.length; i++) {
            final int s = i;
            boolean open = i < unl;
            float y = top + i * (ch + gap);
            RectF r = new RectF(50 * u, y, W - 50 * u, y + ch);
            panel(c, r, open ? 0xFF2A1A5E : 0xFF221B38, 36 * u);
            p.setStyle(Paint.Style.FILL);
            p.setColor(open ? Defs.ST_COLOR[i] : 0xFF4A4460);
            rf.set(r.left, r.top, r.left + 24 * u, r.bottom);
            c.drawRoundRect(rf, 12 * u, 12 * u, p);
            text(c, "ด่าน " + (i + 1), r.left + 60 * u, r.top + 70 * u, 40 * u, open ? Defs.ST_COLOR[i] : 0xFF8A84A0, Paint.Align.LEFT, 0);
            text(c, Defs.ST_NAME[i], r.left + 60 * u, r.top + 140 * u, 58 * u, open ? 0xFFFFFFFF : 0xFF8A84A0, Paint.Align.LEFT, 520 * u);
            String sub = Defs.ST_WAVES[i] + " เวฟ";
            int best = prefs.getInt("best" + i, 0);
            if (prefs.getBoolean("clear" + i, false)) sub += "  •  ผ่านแล้ว!";
            else if (best > 0) sub += "  •  ดีที่สุด " + best;
            plain(c, open ? sub : "ผ่านด่านก่อนหน้าเพื่อปลดล็อก", r.left + 60 * u, r.top + 200 * u, 34 * u, 0xFFC9C2E6, Paint.Align.LEFT, 560 * u);
            // mini map
            float mw = Math.min(ch - 40 * u, 260 * u) * 1000f / 1200f, mh = mw * 1.2f;
            float mx0 = r.right - mw - 40 * u, my0 = r.centerY() - mh / 2;
            float sc = mw / 1000f;
            float[] pth = Defs.ST_PATH[i];
            Path mp = new Path();
            for (int k = 0; k < pth.length / 2; k++) {
                float x = mx0 + pth[k * 2] * sc, yy = my0 + pth[k * 2 + 1] * sc;
                if (k == 0) mp.moveTo(x, yy); else mp.lineTo(x, yy);
            }
            sp.setPathEffect(null);
            sp.setStrokeWidth(14 * u);
            sp.setColor(open ? Defs.ST_COLOR[i] : 0xFF4A4460);
            c.drawPath(mp, sp);
            if (!open) {
                text(c, "ล็อก", mx0 + mw / 2, r.centerY() + 18 * u, 48 * u, 0xFFB8B0D0);
            } else {
                btns.add(new Btn(r, () -> startStage(s)));
            }
        }
    }

    // ================================================================== help
    void drawHelp(Canvas c) {
        backButton(c);
        text(c, "วิธีเล่น", W / 2, 130 * u, 76 * u, 0xFFFFD23F);
        String[] lines = {
                "• ศัตรูจะเดินตามเส้นทางไปที่ฐานคริสตัล",
                "• ใช้ ดาว (Star) เพื่อวางดวงดาวในช่องว่าง",
                "• เริ่มเกมมี 5 ดาว ได้เพิ่มทุกครั้งที่ผ่านเวฟ",
                "• Satellite และ Mars ช่วยผลิตดาวเรื่อย ๆ",
                "• ปุ่ม สุ่มดาว ให้ดาว Rare ถึง Mythical",
                "• ทุกดวงได้ Enchant แบบสุ่ม (สัญลักษณ์มุมขวา)",
                "• บางดวงได้ Effect: Gold Diamond Elite Bigger",
                "• แตะดวงดาวเพื่อดูค่า อัปเกรด หรือขาย",
                "• Pluto บัฟดาวรอบ ๆ +10% ดาเมจ",
                "• ทุก 10 เวฟจะมีบอส หลุมดำ",
                "• หลุมดำจะดูดดวงดาวใกล้ ๆ ให้หยุดทำงาน",
                "• ปุ่ม x1/x2/x3 มุมขวาบนเร่งความเร็วเกม",
                "• ฐานมี 20 HP ถ้าหมดถือว่าแพ้",
        };
        float y = 260 * u, lh = Math.min(100 * u, (H - 320 * u) / lines.length);
        for (String s : lines) {
            plain(c, s, 70 * u, y, 42 * u, 0xFFFFFFFF, Paint.Align.LEFT, W - 140 * u);
            y += lh;
        }
    }

    // ================================================================== codex
    void drawCodex(Canvas c) {
        backButton(c);
        text(c, "สมุดดวงดาว", W / 2 + 60 * u, 130 * u, 70 * u, 0xFFFFD23F);
        String[] tabs = {"ดวงดาว", "Enchant", "ศัตรู"};
        float tw = (W - 100 * u) / 3f;
        for (int i = 0; i < 3; i++) {
            final int t = i;
            rf.set(50 * u + i * tw + 8 * u, 190 * u, 50 * u + (i + 1) * tw - 8 * u, 280 * u);
            button(c, rf, tabs[i], codexTab == i ? 0xFFFF8A2B : 0xFF4A3D8F, true, () -> codexTab = t);
        }
        float top = 320 * u;
        if (codexTab == 0) {
            float rh = Math.min(140 * u, (H - top - 30 * u) / Defs.P_COUNT);
            for (int i = 0; i < Defs.P_COUNT; i++) {
                float y = top + i * rh;
                sprite(c, pBmp[i], 110 * u, y + rh / 2, rh * 0.55f, 255);
                int rc = Defs.RARITY_COLOR[Defs.P_RARITY[i]];
                text(c, Defs.P_NAME[i], 200 * u, y + rh * 0.45f, rh * 0.32f, 0xFFFFFFFF, Paint.Align.LEFT, 0);
                text(c, Defs.RARITY_NAME[Defs.P_RARITY[i]], W - 60 * u, y + rh * 0.45f, rh * 0.26f, rc, Paint.Align.RIGHT, 0);
                plain(c, Defs.P_DESC[i], 200 * u, y + rh * 0.82f, rh * 0.24f, 0xFFC9C2E6, Paint.Align.LEFT, W - 260 * u);
            }
        } else if (codexTab == 1) {
            int n = Defs.EN_NAME.length + Defs.EF_NAME.length + 1;
            float rh = Math.min(120 * u, (H - top - 30 * u) / n);
            float y = top;
            for (int i = 0; i < Defs.EN_NAME.length; i++, y += rh) {
                badge(c, 110 * u, y + rh / 2, rh * 0.36f, i, i < 3 ? 3 : 0);
                text(c, Defs.EN_NAME[i], 190 * u, y + rh * 0.45f, rh * 0.32f, 0xFFFFFFFF, Paint.Align.LEFT, 0);
                text(c, Defs.RARITY_NAME[Defs.EN_RARITY[i]], W - 60 * u, y + rh * 0.45f, rh * 0.26f, Defs.RARITY_COLOR[Defs.EN_RARITY[i]], Paint.Align.RIGHT, 0);
                plain(c, Defs.EN_DESC[i], 190 * u, y + rh * 0.85f, rh * 0.25f, 0xFFC9C2E6, Paint.Align.LEFT, W - 250 * u);
            }
            text(c, "Effect (สถานะพิเศษ)", W / 2, y + rh * 0.65f, rh * 0.34f, 0xFFFFD23F);
            y += rh;
            for (int i = 0; i < Defs.EF_NAME.length; i++, y += rh) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Defs.EF_COLOR[i]);
                c.drawCircle(110 * u, y + rh / 2, rh * 0.3f, p);
                sp.setPathEffect(null);
                sp.setStrokeWidth(4 * u);
                sp.setColor(0xFF140A2A);
                c.drawCircle(110 * u, y + rh / 2, rh * 0.3f, sp);
                text(c, Defs.EF_NAME[i], 190 * u, y + rh * 0.45f, rh * 0.32f, Defs.EF_COLOR[i], Paint.Align.LEFT, 0);
                plain(c, Defs.EF_DESC[i], 190 * u, y + rh * 0.85f, rh * 0.25f, 0xFFC9C2E6, Paint.Align.LEFT, W - 250 * u);
            }
        } else {
            float rh = Math.min(150 * u, (H - top - 30 * u) / Defs.E_COUNT);
            for (int i = 0; i < Defs.E_COUNT; i++) {
                float y = top + i * rh;
                if (i == Defs.E_BLACKHOLE) blackHole(c, 110 * u, y + rh / 2, rh * 0.36f);
                else sprite(c, eBmp[i], 110 * u, y + rh / 2, rh * 0.5f, 255);
                text(c, Defs.E_NAME[i], 200 * u, y + rh * 0.45f, rh * 0.3f, i == Defs.E_BLACKHOLE ? 0xFFB86BFF : 0xFFFFFFFF, Paint.Align.LEFT, 0);
                plain(c, Defs.E_DESC[i], 200 * u, y + rh * 0.82f, rh * 0.23f, 0xFFC9C2E6, Paint.Align.LEFT, W - 260 * u);
            }
        }
    }

    // ================================================================== enchant badge
    void badge(Canvas c, float x, float y, float r, int en, int tier) {
        if (en < 0) return;
        int col = Defs.RARITY_COLOR[Defs.EN_RARITY[en]];
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(en == Defs.EN_MEGE ? hsv(anim * 120) : col);
        c.drawCircle(x, y, r, p);
        sp.setPathEffect(null);
        sp.setStrokeWidth(r * 0.18f);
        sp.setColor(0xFF140A2A);
        c.drawCircle(x, y, r, sp);
        sp.setColor(0xFFFFFFFF);
        sp.setStrokeWidth(r * 0.17f);
        p.setColor(0xFFFFFFFF);
        Path g = new Path();
        float s = r * 0.6f;
        switch (en) {
            case Defs.EN_DMG: // sword
                c.drawLine(x, y + s, x, y - s, sp);
                c.drawLine(x - s * 0.5f, y + s * 0.35f, x + s * 0.5f, y + s * 0.35f, sp);
                break;
            case Defs.EN_RNG: // target
                c.drawCircle(x, y, s * 0.85f, sp);
                c.drawCircle(x, y, s * 0.25f, p);
                break;
            case Defs.EN_CD: // clock
                c.drawCircle(x, y, s * 0.85f, sp);
                c.drawLine(x, y, x, y - s * 0.55f, sp);
                c.drawLine(x, y, x + s * 0.4f, y, sp);
                break;
            case Defs.EN_GOLD:
                p.setColor(0xFFFFE27A);
                c.drawCircle(x, y, s * 0.85f, p);
                plain(c, "$", x, y + s * 0.42f, s * 1.3f, 0xFF8A5A00, Paint.Align.CENTER, 0);
                break;
            case Defs.EN_BLITZ:
                g.moveTo(x + s * 0.2f, y - s);
                g.lineTo(x - s * 0.5f, y + s * 0.15f);
                g.lineTo(x, y + s * 0.15f);
                g.lineTo(x - s * 0.2f, y + s);
                g.lineTo(x + s * 0.5f, y - s * 0.15f);
                g.lineTo(x, y - s * 0.15f);
                g.close();
                p.setColor(0xFFFFF27A);
                c.drawPath(g, p);
                break;
            case Defs.EN_FROZE:
                for (int i = 0; i < 3; i++) {
                    double a = i * Math.PI / 3;
                    float dx = (float) Math.cos(a) * s, dy = (float) Math.sin(a) * s;
                    c.drawLine(x - dx, y - dy, x + dx, y + dy, sp);
                }
                break;
            case Defs.EN_BURN:
                g.moveTo(x, y - s);
                g.cubicTo(x + s * 0.9f, y - s * 0.1f, x + s * 0.7f, y + s, x, y + s);
                g.cubicTo(x - s * 0.7f, y + s, x - s * 0.9f, y - s * 0.1f, x, y - s);
                g.close();
                p.setColor(0xFFFF7A2B);
                c.drawPath(g, p);
                p.setColor(0xFFFFE27A);
                c.drawCircle(x, y + s * 0.4f, s * 0.35f, p);
                break;
            case Defs.EN_MOON:
                p.setColor(0xFFFFFFFF);
                c.drawCircle(x, y, s * 0.8f, p);
                p.setColor(col);
                c.drawCircle(x + s * 0.4f, y - s * 0.25f, s * 0.7f, p);
                break;
            case Defs.EN_GIGA:
                g.moveTo(x, y - s);
                g.lineTo(x + s, y + s * 0.1f);
                g.lineTo(x + s * 0.4f, y + s * 0.1f);
                g.lineTo(x + s * 0.4f, y + s);
                g.lineTo(x - s * 0.4f, y + s);
                g.lineTo(x - s * 0.4f, y + s * 0.1f);
                g.lineTo(x - s, y + s * 0.1f);
                g.close();
                c.drawPath(g, p);
                break;
            case Defs.EN_MEGE:
                c.save();
                c.translate(x, y);
                c.rotate(anim * 60);
                c.scale(s * 1.1f, s * 1.1f);
                c.drawPath(Art.starPath(0, 0, 1f, 0.35f, 4), p);
                c.restore();
                break;
            default: break;
        }
        if (tier > 0) {
            String roman = tier == 1 ? "I" : (tier == 2 ? "II" : "III");
            text(c, roman, x + r * 0.9f, y + r * 1.1f, r * 0.8f, 0xFFFFFFFF, Paint.Align.CENTER, 0);
        }
    }

    // ================================================================== play
    void drawPlay(Canvas c) {
        Game g = game;
        c.save();
        if (g.shake > 0) c.translate((float) Math.sin(anim * 90) * 8 * u * g.shake, (float) Math.cos(anim * 77) * 8 * u * g.shake);
        c.translate(mox, moy);
        c.scale(ms, ms);
        drawPath(c, g);
        drawSlots(c, g);
        drawHeat(c, g);
        for (Planet pl : g.slots) if (pl != null) drawPlanetAura(c, g, pl);
        for (Planet pl : g.slots) if (pl != null) drawPlanet(c, g, pl);
        for (Enemy e : g.enemies) drawEnemy(c, g, e);
        for (Shot s : g.shots) drawShot(c, s);
        drawFx(c, g);
        c.restore();

        drawHud(c, g);
        if (selPlanet != null) drawInfo(c, g, selPlanet);
        else drawBuild(c, g);

        if (g.bannerT > 0 && g.banner != null) {
            float a = Math.min(1f, g.bannerT * 3f);
            float y = mapTop + 140 * u - (1f - a) * 30 * u;
            pill(c, W / 2, y, g.banner, 46 * u, g.bannerColor, 0xEE1A0F3D);
        }
        if (!g.inWave && !g.won && !g.lost) {
            String s = (g.wave == 0 ? "เวฟแรกเริ่มใน " : "เวฟถัดไปใน ") + (int) Math.ceil(g.prepT);
            plain(c, s, 40 * u, mapTop + 10 * u, 34 * u, 0xFFFFFFFF, Paint.Align.LEFT, 0);
            rf.set(W - 260 * u, mapTop - 30 * u, W - 30 * u, mapTop + 50 * u);
            button(c, rf, "เริ่มเลย", 0xFF33C6A6, true, g::skipPrep);
        }
        if (paused && !g.won && !g.lost) drawPause(c);
        if (g.won || g.lost) drawResult(c, g);
    }

    void drawPath(Canvas c, Game g) {
        if (pathStage != g.stage) {
            pathCache.reset();
            for (int i = 0; i < g.px.length; i++) {
                if (i == 0) pathCache.moveTo(g.px[i], g.py[i]);
                else pathCache.lineTo(g.px[i], g.py[i]);
            }
            pathStage = g.stage;
        }
        sp.setPathEffect(null);
        sp.setStrokeWidth(100);
        sp.setColor(0xFF120A2A);
        c.drawPath(pathCache, sp);
        sp.setStrokeWidth(82);
        sp.setColor(0xFF3A2A78);
        c.drawPath(pathCache, sp);
        sp.setStrokeWidth(50);
        sp.setColor(0x334F6BFF);
        c.drawPath(pathCache, sp);
        sp.setStrokeWidth(8);
        sp.setColor(0x88BFD0FF);
        sp.setPathEffect(new DashPathEffect(new float[]{24, 30}, -anim * 80));
        c.drawPath(pathCache, sp);
        sp.setPathEffect(null);

        // portal
        float sx = g.px[0], sy = g.py[0];
        for (int i = 0; i < 3; i++) {
            sp.setStrokeWidth(10);
            sp.setColor(i == 1 ? 0xFF4FE0FF : 0xFFB86BFF);
            float rr = 34 + i * 14;
            rf.set(sx - rr, sy - rr, sx + rr, sy + rr);
            c.drawArc(rf, anim * (120 + i * 60) * (i % 2 == 0 ? 1 : -1), 250, false, sp);
        }
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF140A2A);
        c.drawCircle(sx, sy, 26, p);

        // base crystal
        float bx = g.px[g.px.length - 1], by = g.py[g.py.length - 1];
        float pulse = 1f + 0.05f * (float) Math.sin(anim * 3);
        p.setShader(new RadialGradient(bx, by, 110, 0x885BC8FF, 0x005BC8FF, Shader.TileMode.CLAMP));
        c.drawCircle(bx, by, 110, p);
        p.setShader(null);
        Path cr = new Path();
        float h = 62 * pulse, w = 40 * pulse;
        cr.moveTo(bx, by - h);
        cr.lineTo(bx + w, by - h * 0.2f);
        cr.lineTo(bx, by + h * 0.8f);
        cr.lineTo(bx - w, by - h * 0.2f);
        cr.close();
        p.setColor(0xFF7DE6FF);
        c.drawPath(cr, p);
        p.setColor(0xFF3F8FE0);
        Path half = new Path();
        half.moveTo(bx, by - h);
        half.lineTo(bx + w, by - h * 0.2f);
        half.lineTo(bx, by + h * 0.8f);
        half.close();
        c.drawPath(half, p);
        sp.setStrokeWidth(7);
        sp.setColor(0xFF140A2A);
        c.drawPath(cr, sp);
        // base hp ring
        float frac = g.baseHp / (float) g.maxBaseHp;
        sp.setStrokeWidth(10);
        sp.setColor(0x55000000);
        rf.set(bx - 84, by - 84, bx + 84, by + 84);
        c.drawArc(rf, 0, 360, false, sp);
        sp.setColor(frac > 0.5f ? 0xFF7CFF8A : (frac > 0.25f ? 0xFFFFD23F : 0xFFFF4D5E));
        c.drawArc(rf, -90, 360 * frac, false, sp);
    }

    void drawSlots(Canvas c, Game g) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(anim * 5);
        for (int i = 0; i < g.slotX.length; i++) {
            float x = g.slotX[i], y = g.slotY[i];
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x55140A2A);
            c.drawCircle(x, y + 6, 60, p);
            p.setColor(0x662A1F5C);
            c.drawCircle(x, y, 58, p);
            boolean hl = buildSel >= 0 && g.slots[i] == null;
            sp.setStrokeWidth(hl ? 6 : 4);
            sp.setColor(hl ? (0xFFFFD23F & 0x00FFFFFF) | ((int) (120 + 135 * pulse) << 24) : 0x668F80D0);
            sp.setPathEffect(new DashPathEffect(new float[]{14, 10}, anim * 10));
            c.drawCircle(x, y, 58, sp);
            sp.setPathEffect(null);
            if (hl) {
                sp.setStrokeWidth(8);
                c.drawLine(x - 16, y, x + 16, y, sp);
                c.drawLine(x, y - 16, x, y + 16, sp);
            }
        }
    }

    void drawPlanetAura(Canvas c, Game g, Planet pl) {
        boolean sel = pl == selPlanet;
        float range = g.rangeOf(pl);
        if (pl.type == Defs.NEPTUNE && pl.disabledT <= 0) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x223FA9FF);
            c.drawCircle(pl.x, pl.y, range, p);
            sp.setStrokeWidth(3);
            sp.setColor(0x557DCBFF);
            c.drawCircle(pl.x, pl.y, range * (0.6f + 0.4f * ((anim * 0.6f) % 1f)), sp);
        }
        if (pl.type == Defs.PLUTO && pl.disabledT <= 0) {
            sp.setStrokeWidth(5);
            sp.setColor(0x66FFB2D8);
            sp.setPathEffect(new DashPathEffect(new float[]{10, 12}, anim * 30));
            for (Planet q : g.slots)
                if (q != null && q != pl && Game.dist(q.x, q.y, pl.x, pl.y) <= 240) c.drawLine(pl.x, pl.y, q.x, q.y, sp);
            sp.setPathEffect(null);
        }
        if (sel && range > 0) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x22FFFFFF);
            c.drawCircle(pl.x, pl.y, range, p);
            sp.setStrokeWidth(4);
            sp.setColor(0x99FFFFFF);
            c.drawCircle(pl.x, pl.y, range, sp);
        }
        if (sel) {
            sp.setStrokeWidth(6);
            sp.setColor(0xFFFFD23F);
            c.drawCircle(pl.x, pl.y, 64, sp);
        }
    }

    void drawPlanet(Canvas c, Game g, Planet pl) {
        float sc = 1f;
        if (pl.effect == Defs.EF_BIGGER) sc *= 1.2f;
        if (pl.enchant == Defs.EN_GIGA) sc *= 1.1f;
        if (pl.enchant == Defs.EN_MEGE) sc *= 1.12f;
        sc *= 1f + Math.max(0, pl.flash) * 0.4f;
        float bob = (float) Math.sin(pl.anim * 2.2f + pl.slot) * 3f;
        float x = pl.x, y = pl.y + bob;
        float half = 64 * sc;
        boolean off = pl.disabledT > 0;

        if (pl.enchant == Defs.EN_MEGE) {
            sp.setStrokeWidth(8);
            for (int i = 0; i < 3; i++) {
                sp.setColor(hsv(anim * 150 + i * 120) & 0xAAFFFFFF);
                rf.set(x - 58 - i * 6, y - 58 - i * 6, x + 58 + i * 6, y + 58 + i * 6);
                c.drawArc(rf, anim * 200 + i * 120, 90, false, sp);
            }
        }
        if (pl.effect == Defs.EF_GOLD) {
            p.setShader(new RadialGradient(x, y, 80, 0x66FFD23F, 0x00FFD23F, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, 80, p);
            p.setShader(null);
        }
        sprite(c, pBmp[pl.type], x, y, half, off ? 110 : 255);
        if (off) {
            sp.setStrokeWidth(7);
            sp.setColor(0xCCB86BFF);
            for (int i = 0; i < 2; i++) {
                rf.set(x - 30 - i * 14, y - 30 - i * 14, x + 30 + i * 14, y + 30 + i * 14);
                c.drawArc(rf, -anim * 300 + i * 180, 200, false, sp);
            }
        }
        // effects
        if (pl.effect == Defs.EF_GOLD) {
            sp.setStrokeWidth(5);
            sp.setColor(0xFFFFD23F);
            c.drawCircle(x, y, 50 * sc, sp);
        } else if (pl.effect == Defs.EF_DIAMOND) {
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 3; i++) {
                double a = anim * 1.6 + i * Math.PI * 2 / 3;
                float dx = x + (float) Math.cos(a) * 58, dy = y + (float) Math.sin(a) * 58;
                Path d = new Path();
                d.moveTo(dx, dy - 12);
                d.lineTo(dx + 8, dy);
                d.lineTo(dx, dy + 12);
                d.lineTo(dx - 8, dy);
                d.close();
                p.setColor(0xFF7DF3FF);
                c.drawPath(d, p);
                sp.setStrokeWidth(3);
                sp.setColor(0xFF140A2A);
                c.drawPath(d, sp);
            }
        } else if (pl.effect == Defs.EF_ELITE) {
            Path cw = new Path();
            float ty = y - half * 0.7f, w = 26;
            cw.moveTo(x - w, ty);
            cw.lineTo(x - w, ty - 22);
            cw.lineTo(x - w * 0.5f, ty - 10);
            cw.lineTo(x, ty - 28);
            cw.lineTo(x + w * 0.5f, ty - 10);
            cw.lineTo(x + w, ty - 22);
            cw.lineTo(x + w, ty);
            cw.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFF4D5E);
            c.drawPath(cw, p);
            sp.setStrokeWidth(4);
            sp.setColor(0xFF140A2A);
            c.drawPath(cw, sp);
        } else if (pl.effect == Defs.EF_BIGGER) {
            sp.setStrokeWidth(5);
            sp.setColor(0xFF9CFF7A);
            float ay = y - half * 0.62f + (float) Math.sin(anim * 4) * 4;
            c.drawLine(x - half * 0.7f, ay + 10, x - half * 0.7f + 10, ay, sp);
            c.drawLine(x - half * 0.7f + 10, ay, x - half * 0.7f + 20, ay + 10, sp);
        }
        if (pl.enchant == Defs.EN_MOON) {
            float mx = pl.x + (float) Math.cos(pl.orbitA) * 62, my = pl.y + (float) Math.sin(pl.orbitA) * 62;
            sprite(c, pBmp[Defs.MOON], mx, my, 22, 255);
        }
        // level pips
        for (int i = 0; i < pl.level; i++) {
            float lx = x - (pl.level - 1) * 11 + i * 22;
            star(c, lx, pl.y + 56, 11);
        }
        badge(c, x + 44 * sc, y - 42 * sc, 18, pl.enchant, pl.tier);
    }

    void blackHole(Canvas c, float x, float y, float r) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(x, y, r * 1.8f, 0x99A43CFF, 0x00A43CFF, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r * 1.8f, p);
        p.setShader(null);
        sp.setPathEffect(null);
        for (int i = 0; i < 3; i++) {
            sp.setStrokeWidth(r * 0.16f);
            sp.setColor(i == 0 ? 0xFFFF9A3C : (i == 1 ? 0xFFFF4FD8 : 0xFF8A5BFF));
            float rr = r * (1.05f + i * 0.2f);
            rf.set(x - rr, y - rr * 0.45f, x + rr, y + rr * 0.45f);
            c.save();
            c.rotate(-15, x, y);
            c.drawArc(rf, anim * (200 + i * 70), 220, false, sp);
            c.restore();
        }
        p.setColor(0xFF05010D);
        c.drawCircle(x, y, r * 0.8f, p);
        sp.setStrokeWidth(r * 0.08f);
        sp.setColor(0xFFFFE0B0);
        c.drawCircle(x, y, r * 0.82f, sp);
        p.setColor(0xFFFF4FD8);
        c.drawCircle(x - r * 0.28f, y - r * 0.08f, r * 0.12f, p);
        c.drawCircle(x + r * 0.28f, y - r * 0.08f, r * 0.12f, p);
        sp.setStrokeWidth(r * 0.08f);
        sp.setColor(0xFFFF4FD8);
        c.drawLine(x - r * 0.45f, y - r * 0.35f, x - r * 0.12f, y - r * 0.22f, sp);
        c.drawLine(x + r * 0.45f, y - r * 0.35f, x + r * 0.12f, y - r * 0.22f, sp);
    }

    void drawEnemy(Canvas c, Game g, Enemy e) {
        if (e.dead) return;
        int t = e.type;
        // trail
        if (t == Defs.E_METEOR || t == Defs.E_MEGA || t == Defs.E_SHOOT || t == Defs.E_HELL) {
            g.pointAt(e.d - e.radius * 2.2f, tmp);
            float bx = tmp[0], by = tmp[1];
            int col = t == Defs.E_SHOOT ? 0xFFFFF27A : 0xFFFF8A2B;
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            for (int i = 4; i >= 1; i--) {
                float f = i / 5f;
                p.setColor(col);
                p.setAlpha((int) (140 * (1 - f)));
                c.drawCircle(e.x + (bx - e.x) * f, e.y + (by - e.y) * f, e.radius * (1.0f - f * 0.6f), p);
            }
            p.setAlpha(255);
        }
        if (t == Defs.E_BLACKHOLE) {
            blackHole(c, e.x, e.y, e.radius * 0.75f);
        } else {
            int alpha = e.phased ? 70 : 255;
            c.save();
            c.rotate((float) Math.sin(e.angle * 0.05f) * 10f, e.x, e.y);
            float half = e.radius / 0.76f;
            sprite(c, eBmp[t], e.x, e.y, half, alpha);
            c.restore();
        }
        if (e.hitFlash > 0) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x66FFFFFF);
            c.drawCircle(e.x, e.y, e.radius, p);
        }
        if (e.slow > 0) {
            sp.setStrokeWidth(4);
            sp.setColor(0xAA7DE6FF);
            c.drawCircle(e.x, e.y, e.radius + 6, sp);
        }
        if (e.stunT > 0) {
            for (int i = 0; i < 3; i++) {
                double a = anim * 6 + i * 2.1;
                star(c, e.x + (float) Math.cos(a) * e.radius * 0.8f, e.y - e.radius - 6 + (float) Math.sin(a) * 6, 7);
            }
        }
        if (e.shield > 0) {
            sp.setStrokeWidth(5);
            sp.setColor(0x887CFFD8);
            c.drawCircle(e.x, e.y, e.radius + 10, sp);
        }
        // hp bar
        float bw = e.boss ? 160 : Math.max(50, e.radius * 2f), bh = e.boss ? 14 : 9;
        float bx = e.x - bw / 2, by = e.y - e.radius - (e.boss ? 40 : 20);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xCC140A2A);
        rf.set(bx - 3, by - 3, bx + bw + 3, by + bh + 3);
        c.drawRoundRect(rf, 6, 6, p);
        float frac = Math.max(0, e.hp / e.maxHp);
        p.setColor(e.boss ? 0xFFB86BFF : (frac > 0.5f ? 0xFF7CFF8A : 0xFFFF6B5B));
        rf.set(bx, by, bx + bw * frac, by + bh);
        c.drawRoundRect(rf, 4, 4, p);
        if (e.maxShield > 0 && e.shield > 0) {
            p.setColor(0xFF7DE6FF);
            rf.set(bx, by - bh * 0.7f, bx + bw * (e.shield / e.maxShield), by - 2);
            c.drawRect(rf, p);
        }
    }

    void drawShot(Canvas c, Shot s) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        float ang = (float) Math.toDegrees(Math.atan2(s.vy, s.vx));
        switch (s.kind) {
            case Defs.K_MISSILE: {
                c.save();
                c.translate(s.x, s.y);
                c.rotate(ang);
                p.setColor(0xAAFF8A2B);
                c.drawCircle(-22, 0, 9, p);
                p.setColor(0xFFF2F4FF);
                rf.set(-16, -7, 12, 7);
                c.drawRoundRect(rf, 6, 6, p);
                p.setColor(0xFFFF4D5E);
                c.drawCircle(12, 0, 7, p);
                sp.setStrokeWidth(3);
                sp.setColor(0xFF140A2A);
                rf.set(-16, -7, 19, 7);
                c.drawRoundRect(rf, 7, 7, sp);
                c.restore();
                break;
            }
            case Defs.K_SMOKE:
                for (int i = 0; i < 3; i++) {
                    p.setColor(0x88FF3B2F);
                    c.drawCircle(s.x - s.vx * 0.02f * i + (float) Math.sin(s.spin + i) * 5,
                            s.y - s.vy * 0.02f * i, 14 - i * 3, p);
                }
                break;
            case Defs.K_FIREROCK:
                p.setColor(0x88FF7A2B);
                c.drawCircle(s.x - s.vx * 0.025f, s.y - s.vy * 0.025f, 9, p);
                p.setColor(0xFFFF7A2B);
                c.drawCircle(s.x, s.y, 9, p);
                p.setColor(0xFFFFE27A);
                c.drawCircle(s.x, s.y, 4.5f, p);
                break;
            case Defs.K_TORNADO:
                sp.setStrokeWidth(6);
                for (int i = 0; i < 3; i++) {
                    sp.setColor(i == 1 ? 0xCCFF9A3C : 0xCCF2E2C8);
                    float rr = 18 + i * 12;
                    rf.set(s.x - rr, s.y - rr * 0.6f - i * 10, s.x + rr, s.y + rr * 0.6f - i * 10);
                    c.drawArc(rf, s.spin * 60 + i * 90, 260, false, sp);
                }
                break;
            case Defs.K_ICE: {
                c.save();
                c.translate(s.x, s.y);
                c.rotate(s.spin * 30);
                Path d = new Path();
                d.moveTo(0, -13);
                d.lineTo(9, 0);
                d.lineTo(0, 13);
                d.lineTo(-9, 0);
                d.close();
                p.setColor(0xFFBDF6FF);
                c.drawPath(d, p);
                sp.setStrokeWidth(3);
                sp.setColor(0xFF1F5FB8);
                c.drawPath(d, sp);
                c.restore();
                break;
            }
            default: {
                float r = (s.mini ? 6 : 10) * s.size;
                p.setColor(s.mini ? 0xFFDDE0F0 : 0xFFC99A6B);
                c.drawCircle(s.x, s.y, r, p);
                sp.setStrokeWidth(2.5f);
                sp.setColor(0xFF140A2A);
                c.drawCircle(s.x, s.y, r, sp);
            }
        }
    }

    void drawHeat(Canvas c, Game g) {
        for (HeatWave h : g.heat) {
            g.pointAt(h.d, tmp);
            float x = tmp[0], y = tmp[1];
            float a = Math.max(0.3f, h.hp / h.maxHp);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(x, y, 80, 0xEEFFB627, 0x00FF5A1F, Shader.TileMode.CLAMP));
            p.setAlpha((int) (255 * a));
            c.drawCircle(x, y, 80, p);
            p.setShader(null);
            p.setAlpha(255);
            sp.setStrokeWidth(8);
            sp.setColor(0xFFFFE27A);
            float rr = 40 + (float) Math.sin(anim * 12) * 6;
            c.drawCircle(x, y, rr, sp);
            text(c, String.valueOf((int) h.hp), x, y - 60, 26, 0xFFFFE27A);
        }
    }

    void drawFx(Canvas c, Game g) {
        p.setShader(null);
        for (Fx f : g.fx) {
            float a = f.life / f.maxLife;
            switch (f.kind) {
                case Fx.PART:
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(f.color);
                    p.setAlpha((int) (255 * a));
                    c.drawCircle(f.x, f.y, f.size * a, p);
                    break;
                case Fx.RING:
                    sp.setStrokeWidth(8 * a + 2);
                    sp.setColor(f.color);
                    sp.setAlpha((int) (220 * a));
                    c.drawCircle(f.x, f.y, f.size * (1.1f - a * 0.8f), sp);
                    sp.setAlpha(255);
                    break;
                case Fx.PUFF:
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(f.color);
                    p.setAlpha((int) (150 * a));
                    c.drawCircle(f.x, f.y, f.size * (1.5f - a * 0.5f), p);
                    break;
                case Fx.TEXT:
                    text(c, f.text, f.x, f.y, 34, f.color);
                    break;
                case Fx.STARTEXT:
                    star(c, f.x - 26, f.y - 12, 16);
                    text(c, f.text, f.x + 14, f.y, 36, f.color);
                    break;
                default: break;
            }
        }
        p.setAlpha(255);
    }

    // ================================================================== hud & panels
    void drawHud(Canvas c, Game g) {
        float top = 50 * u, h = 100 * u;
        rf.set(30 * u, top, 140 * u, top + h);
        button(c, rf, "II", 0xFF6B5BD6, true, () -> paused = !paused);
        heart(c, 200 * u, top + h / 2, 30 * u);
        text(c, String.valueOf(Math.max(0, g.baseHp)), 245 * u, top + h / 2 + 18 * u, 52 * u, 0xFFFFFFFF, Paint.Align.LEFT, 0);
        text(c, "WAVE " + g.wave + "/" + g.maxWave, W / 2, top + h / 2 + 18 * u, 52 * u, 0xFFFFFFFF);
        star(c, W - 330 * u, top + h / 2, 34 * u);
        text(c, String.valueOf(g.stars), W - 285 * u, top + h / 2 + 20 * u, 58 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
        rf.set(W - 140 * u, top, W - 30 * u, top + h);
        button(c, rf, "x" + speed, speed > 1 ? 0xFFFF8A2B : 0xFF6B5BD6, true, () -> speed = speed % 3 + 1);
    }

    void drawBuild(Canvas c, Game g) {
        rf.set(-20 * u, panelTop, W + 20 * u, H + 40 * u);
        panel(c, new RectF(rf), 0xF0221449, 50 * u);
        float gap = 20 * u, cw = (W - 60 * u - gap * 3) / 4f, chh = 270 * u;
        float y0 = panelTop + 40 * u;
        if (buildSel >= 0)
            plain(c, "แตะช่องว่างบนแมพเพื่อวาง", W / 2, panelTop - 16 * u, 34 * u, 0xFFFFD23F, Paint.Align.CENTER, 0);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            float x0 = 30 * u + i * (cw + gap);
            RectF r = new RectF(x0, y0 - (buildSel == i ? 14 * u : 0), x0 + cw, y0 + chh - (buildSel == i ? 14 * u : 0));
            int cost = i == 3 ? g.gachaCost : Defs.STARTER_COST[i];
            boolean can = g.stars >= cost;
            int col = i == 3 ? 0xFF7A3FD0 : 0xFF33296B;
            panel(c, r, can ? col : 0xFF2A2440, 30 * u);
            if (buildSel == i) {
                sp.setStrokeWidth(8 * u);
                sp.setColor(0xFFFFD23F);
                c.drawRoundRect(r, 30 * u, 30 * u, sp);
            }
            float cx = r.centerX();
            if (i == 3) {
                float rr = 58 * u;
                p.setStyle(Paint.Style.FILL);
                p.setShader(new RadialGradient(cx, r.top + 95 * u, rr * 1.5f, hsv(anim * 90), 0x00000000, Shader.TileMode.CLAMP));
                c.drawCircle(cx, r.top + 95 * u, rr * 1.5f, p);
                p.setShader(null);
                p.setColor(0xFF2A1650);
                c.drawCircle(cx, r.top + 95 * u, rr, p);
                sp.setStrokeWidth(6 * u);
                sp.setColor(0xFF140A2A);
                c.drawCircle(cx, r.top + 95 * u, rr, sp);
                text(c, "?", cx, r.top + 120 * u, 76 * u, 0xFFFFD23F);
                text(c, "สุ่มดาว", cx, r.top + 200 * u, 40 * u, 0xFFFFFFFF, Paint.Align.CENTER, cw - 20 * u);
            } else {
                sprite(c, pBmp[Defs.STARTER[i]], cx, r.top + 95 * u, 82 * u, can ? 255 : 120);
                text(c, Defs.P_NAME[Defs.STARTER[i]], cx, r.top + 200 * u, 38 * u, 0xFFFFFFFF, Paint.Align.CENTER, cw - 20 * u);
            }
            star(c, cx - 28 * u, r.top + 238 * u, 18 * u);
            text(c, String.valueOf(cost), cx + 6 * u, r.top + 252 * u, 40 * u, can ? 0xFFFFD23F : 0xFFFF6B6B, Paint.Align.LEFT, 0);
            btns.add(new Btn(r, () -> {
                if (paused || game.won || game.lost) return;
                selPlanet = null;
                buildSel = buildSel == idx ? -1 : idx;
            }));
        }
    }

    void drawInfo(Canvas c, Game g, Planet pl) {
        rf.set(-20 * u, panelTop, W + 20 * u, H + 40 * u);
        panel(c, new RectF(rf), 0xF0221449, 50 * u);
        int rar = Defs.P_RARITY[pl.type];
        float x0 = 40 * u, y0 = panelTop + 30 * u;
        sprite(c, pBmp[pl.type], x0 + 90 * u, y0 + 100 * u, 95 * u, 255);
        float tx = x0 + 200 * u;
        text(c, Defs.P_NAME[pl.type] + "  Lv " + pl.level, tx, y0 + 50 * u, 50 * u, 0xFFFFFFFF, Paint.Align.LEFT, W - tx - 40 * u);
        text(c, Defs.RARITY_NAME[rar], W - 40 * u, y0 + 50 * u, 36 * u, Defs.RARITY_COLOR[rar], Paint.Align.RIGHT, 0);
        String stat;
        if (g.isProducer(pl)) {
            stat = String.format(Locale.US, "ผลิต %d ดาว ทุก %.1f วิ", g.produceAmount(pl), g.cdOf(pl));
        } else if (pl.type == Defs.SUN) {
            stat = String.format(Locale.US, "คลื่นความร้อน ทุก %.1f วิ  พลัง %d+", g.cdOf(pl), (int) (g.dmgOf(pl) * 20));
        } else {
            stat = String.format(Locale.US, "ดาเมจ %.1f  ระยะ %d  ยิงทุก %.2f วิ", g.dmgOf(pl), (int) g.rangeOf(pl), g.cdOf(pl));
        }
        plain(c, stat, tx, y0 + 100 * u, 32 * u, 0xFFE6E0FF, Paint.Align.LEFT, W - tx - 40 * u);
        if (pl.enchant >= 0) {
            badge(c, tx + 20 * u, y0 + 140 * u, 20 * u, pl.enchant, 0);
            String en = Defs.EN_NAME[pl.enchant] + (pl.tier > 0 ? " " + (pl.tier == 1 ? "I" : pl.tier == 2 ? "II" : "III") : "");
            plain(c, en, tx + 52 * u, y0 + 152 * u, 34 * u, Defs.RARITY_COLOR[Defs.EN_RARITY[pl.enchant]], Paint.Align.LEFT, 0);
        }
        if (pl.effect >= 0) {
            plain(c, "Effect: " + Defs.EF_NAME[pl.effect], tx + 330 * u, y0 + 152 * u, 34 * u, Defs.EF_COLOR[pl.effect], Paint.Align.LEFT, 0);
        }
        if (pl.buff > 0)
            plain(c, "บัฟ Pluto +" + (int) (pl.buff * 100) + "%", W - 40 * u, y0 + 100 * u + 50 * u, 28 * u, 0xFFFFB2D8, Paint.Align.RIGHT, 0);

        float by = H - 120 * u, bh = 95 * u, bw = (W - 80 * u - 40 * u) / 3f;
        boolean max = pl.level >= Defs.MAX_LEVEL;
        int uc = g.upgradeCost(pl);
        rf.set(40 * u, by - bh / 2, 40 * u + bw, by + bh / 2);
        button(c, rf, max ? "เลเวลสูงสุด" : "อัปเกรด " + uc, 0xFF33C6A6, !max && g.stars >= uc, () -> {
            String err = game.upgrade(pl);
            if (err != null) showToast(err);
        });
        rf.set(60 * u + bw, by - bh / 2, 60 * u + bw * 2, by + bh / 2);
        button(c, rf, "ขาย +" + Math.max(1, pl.spent / 2), 0xFFFF6B5B, true, () -> {
            game.sell(pl);
            selPlanet = null;
        });
        rf.set(80 * u + bw * 2, by - bh / 2, 80 * u + bw * 3, by + bh / 2);
        button(c, rf, "ปิด", 0xFF6B5BD6, true, () -> selPlanet = null);
    }

    void dim(Canvas c) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xAA05010D);
        c.drawRect(0, 0, W, H, p);
        btns.clear(); // only overlay buttons are active
    }

    void drawPause(Canvas c) {
        dim(c);
        RectF r = new RectF(100 * u, H / 2 - 300 * u, W - 100 * u, H / 2 + 300 * u);
        panel(c, r, 0xFF2A1A5E, 50 * u);
        text(c, "พักเกม", W / 2, r.top + 130 * u, 80 * u, 0xFFFFD23F);
        rf.set(r.left + 80 * u, r.top + 230 * u, r.right - 80 * u, r.top + 350 * u);
        button(c, rf, "เล่นต่อ", 0xFF33C6A6, true, () -> paused = false);
        rf.set(r.left + 80 * u, r.top + 400 * u, r.right - 80 * u, r.top + 520 * u);
        button(c, rf, "ออกจากด่าน", 0xFFFF6B5B, true, () -> { paused = false; game = null; screen = S_SELECT; });
    }

    void drawResult(Canvas c, Game g) {
        dim(c);
        RectF r = new RectF(80 * u, H / 2 - 380 * u, W - 80 * u, H / 2 + 380 * u);
        panel(c, r, 0xFF2A1A5E, 50 * u);
        if (g.won) {
            sprite(c, pBmp[Defs.SUN], W / 2, r.top + 130 * u, 110 * u, 255);
            text(c, "ชนะแล้ว!", W / 2, r.top + 300 * u, 96 * u, 0xFFFFD23F);
            plain(c, "ปกป้องฐานได้ครบ " + g.maxWave + " เวฟ", W / 2, r.top + 370 * u, 40 * u, 0xFFFFFFFF, Paint.Align.CENTER, 0);
        } else {
            blackHole(c, W / 2, r.top + 140 * u, 80 * u);
            text(c, "ฐานถูกทำลาย", W / 2, r.top + 300 * u, 86 * u, 0xFFFF6B6B);
            plain(c, "ไปได้ถึงเวฟ " + g.wave + " จาก " + g.maxWave, W / 2, r.top + 370 * u, 40 * u, 0xFFFFFFFF, Paint.Align.CENTER, 0);
        }
        final int st = g.stage;
        rf.set(r.left + 70 * u, r.top + 440 * u, r.right - 70 * u, r.top + 560 * u);
        button(c, rf, "เล่นอีกครั้ง", 0xFFFF8A2B, true, () -> startStage(st));
        rf.set(r.left + 70 * u, r.top + 600 * u, r.right - 70 * u, r.top + 720 * u);
        button(c, rf, "เลือกด่าน", 0xFF6B5BD6, true, () -> { game = null; screen = S_SELECT; });
    }
}
