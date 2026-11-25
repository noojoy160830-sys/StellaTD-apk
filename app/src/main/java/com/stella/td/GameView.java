package com.stella.td;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
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

/** Renders every screen and handles touch. Match rules live in {@link Game}, saved data in {@link Profile}. */
public final class GameView extends View {

    static final String VERSION = "v2.1";

    // hub screens
    static final int S_LOBBY = 0, S_SHOP = 1, S_PLANET = 2, S_PLAYMENU = 3, S_BANNER = 4, S_ENCHANT = 5, S_GAME = 6;
    // popups
    static final int P_NONE = 0, P_PLAYER = 1, P_SETTINGS = 2, P_GACHA = 3, P_INDEX = 4, P_AUTO = 5;

    static final class Btn {
        final RectF r;
        final Runnable a;
        Btn(RectF r, Runnable a) { this.r = r; this.a = a; }
    }

    final Profile pf;
    int screen = S_LOBBY, popup = P_NONE;
    boolean paused;
    Game game;
    int speed = 1, buildSel = -1;
    Planet selPlanet;
    boolean resultSaved;
    String toast;
    float toastT;

    int selType = Defs.EARTH, enchSel = Defs.EARTH;
    int[] gachaRes;
    boolean[] gachaNew;
    float gachaT;
    boolean autoOn;
    int autoTarget = -1, autoCount;
    float autoT;
    float enchFlash;

    float W, H, u, ms, mox, moy, mapTop, mapBot, panelTop, navTop;
    float anim;
    long lastNs;

    Bitmap bg;
    final Bitmap[] pBmp = new Bitmap[Defs.P_COUNT];
    final Bitmap[] eBmp = new Bitmap[Defs.E_COUNT];

    Typeface bodyFont = Typeface.DEFAULT_BOLD, titleFont = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD_ITALIC);
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
        pf = new Profile(ctx.getSharedPreferences("stella", Context.MODE_PRIVATE));
        // Game font: Kanit (SIL Open Font License). Falls back to the system font if the files are missing.
        try { bodyFont = Typeface.createFromAsset(ctx.getAssets(), "fonts/Kanit-SemiBold.ttf"); } catch (RuntimeException ignored) { }
        try { titleFont = Typeface.createFromAsset(ctx.getAssets(), "fonts/Kanit-BlackItalic.ttf"); } catch (RuntimeException ignored) { }
        tp.setTypeface(bodyFont);
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
        if (pf.deck[0] >= 0) selType = enchSel = pf.deck[0];
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
            for (int i = 0; i < Defs.P_COUNT; i++) pBmp[i] = Art.planet(i, 256);
            for (int i = 0; i < Defs.E_COUNT; i++)
                if (i != Defs.E_BLACKHOLE) eBmp[i] = Art.enemy(i, 160);
        }
        navTop = H - 210 * u;
        mapTop = 190 * u;
        panelTop = H - 360 * u;
        mapBot = panelTop - 10 * u;
        float availH = mapBot - mapTop;
        ms = Math.min((W - 20 * u) / 1000f, availH / 1200f);
        mox = (W - 1000 * ms) / 2f;
        moy = mapTop + (availH - 1200 * ms) / 2f;
    }

    void pauseGame() {
        if (screen == S_GAME && game != null && !game.won && !game.lost) paused = true;
        autoOn = false;
        lastNs = 0;
    }

    /** @return true if handled */
    boolean onBack() {
        if (popup != P_NONE) { popup = P_NONE; autoOn = false; return true; }
        switch (screen) {
            case S_GAME:
                if (selPlanet != null || buildSel >= 0) { selPlanet = null; buildSel = -1; }
                else if (game != null && (game.won || game.lost)) { game = null; screen = S_PLAYMENU; }
                else paused = !paused;
                return true;
            case S_LOBBY:
                return false;
            default:
                autoOn = false;
                screen = S_LOBBY;
                return true;
        }
    }

    void go(int s) {
        autoOn = false;
        screen = s;
    }

    void startStage(int s) {
        if (pf.deckCount() == 0) {
            showToast(L.t("ใส่ดวงดาวใน Deck ก่อน (เมนู Planet)", "Add planets to your deck first (Planet menu)"));
            return;
        }
        game = new Game(s, pf);
        screen = S_GAME;
        paused = false;
        speed = 1;
        buildSel = -1;
        selPlanet = null;
        resultSaved = false;
    }

    void saveResult() {
        if (resultSaved || game == null) return;
        resultSaved = true;
        pf.addRewards(game);
    }

    void showToast(String s) {
        toast = s;
        toastT = 1.8f;
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
        if (screen != S_GAME || game == null || paused || game.won || game.lost) return;
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
        if (buildSel < 0 || game.deck[buildSel] < 0) {
            showToast(L.t("เลือกดวงดาวด้านล่างก่อน", "Pick a planet below first"));
            return;
        }
        String err = game.place(game.deck[buildSel], best);
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
        if (gachaT < 10) gachaT += dt;
        if (enchFlash > 0) enchFlash -= dt;
        updateAuto(dt);
        if (screen == S_GAME && game != null && !paused) {
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
            case S_LOBBY: drawLobby(c); break;
            case S_SHOP: drawShop(c); break;
            case S_PLANET: drawPlanetMenu(c); break;
            case S_PLAYMENU: drawPlayMenu(c); break;
            case S_BANNER: drawBanner(c); break;
            case S_ENCHANT: drawEnchant(c); break;
            default:
                if (game != null) drawPlay(c);
                else screen = S_PLAYMENU;
                break;
        }
        if (screen != S_GAME) drawNav(c);
        switch (popup) {
            case P_PLAYER: drawPlayerInfo(c); break;
            case P_SETTINGS: drawSettings(c); break;
            case P_GACHA: drawGachaResult(c); break;
            case P_INDEX: drawIndex(c); break;
            case P_AUTO: drawAutoPick(c); break;
            default: break;
        }
        if (toastT > 0 && toast != null) {
            float y = screen == S_GAME ? panelTop - 60 * u : navTop - 60 * u;
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



    /** heading text in the game's display font */
    void title(Canvas c, String s, float x, float y, float size, int color, Paint.Align al, float maxW) {
        tp.setTypeface(titleFont);
        text(c, s, x, y, size, color, al, maxW);
        tp.setTypeface(bodyFont);
    }

    void title(Canvas c, String s, float x, float y, float size, int color) {
        title(c, s, x, y, size, color, Paint.Align.CENTER, 0);
    }

    /** the Stella TD logo: layered outline, gradient fill, highlight and a sparkle */
    void logo(Canvas c, float x, float y, float size) {
        String s = "Stella TD";
        tp.setTypeface(titleFont);
        tp.setTextSize(size);
        tp.setTextAlign(Paint.Align.CENTER);
        float w = tp.measureText(s);
        if (w > W - 80 * u) { size *= (W - 80 * u) / w; tp.setTextSize(size); w = tp.measureText(s); }
        tp.setStyle(Paint.Style.STROKE);
        tp.setShader(null);
        tp.setStrokeWidth(size * 0.22f);
        tp.setColor(0xFF2A1060);
        c.drawText(s, x, y + size * 0.07f, tp);
        tp.setStrokeWidth(size * 0.16f);
        tp.setColor(0xFF140A2A);
        c.drawText(s, x, y, tp);
        tp.setStyle(Paint.Style.FILL);
        tp.setColor(0xFFFFFFFF);
        tp.setShader(new LinearGradient(0, y - size * 0.75f, 0, y + size * 0.05f,
                new int[]{0xFFFFF6B0, 0xFFFFD23F, 0xFFFF8A2B}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawText(s, x, y, tp);
        tp.setShader(null);
        tp.setStyle(Paint.Style.STROKE);
        tp.setStrokeWidth(size * 0.02f);
        tp.setColor(0x88FFFFFF);
        c.drawText(s, x - size * 0.015f, y - size * 0.02f, tp);
        tp.setStyle(Paint.Style.FILL);
        tp.setTypeface(bodyFont);
        float sx = x - w / 2 + size * 0.05f, sy = y - size * 0.72f;
        float tw = 0.6f + 0.4f * (float) Math.sin(anim * 3);
        c.save();
        c.translate(sx, sy);
        c.rotate(anim * 40);
        c.scale(size * 0.16f * tw, size * 0.16f * tw);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        c.drawPath(Art.starPath(0, 0, 1f, 0.25f, 4), p);
        c.restore();
    }

    // ================================================================== icons
    void milliteIcon(Canvas c, float x, float y, float r) {
        Path d = new Path();
        d.moveTo(x, y - r);
        d.lineTo(x + r * 0.7f, y - r * 0.2f);
        d.lineTo(x, y + r);
        d.lineTo(x - r * 0.7f, y - r * 0.2f);
        d.close();
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFC58CFF);
        c.drawPath(d, p);
        p.setColor(0xFF8A4FE0);
        Path h = new Path();
        h.moveTo(x, y - r);
        h.lineTo(x + r * 0.7f, y - r * 0.2f);
        h.lineTo(x, y + r);
        h.close();
        c.drawPath(h, p);
        sp.setPathEffect(null);
        sp.setStrokeWidth(r * 0.16f);
        sp.setColor(0xFF140A2A);
        c.drawPath(d, sp);
    }

    void ticketIcon(Canvas c, float x, float y, float r) {
        rf2.set(x - r * 1.3f, y - r * 0.8f, x + r * 1.3f, y + r * 0.8f);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFF8A5C);
        c.drawRoundRect(rf2, r * 0.25f, r * 0.25f, p);
        sp.setPathEffect(null);
        sp.setStrokeWidth(r * 0.14f);
        sp.setColor(0xFF140A2A);
        c.drawRoundRect(rf2, r * 0.25f, r * 0.25f, sp);
        sp.setColor(0xAAFFFFFF);
        sp.setPathEffect(new DashPathEffect(new float[]{r * 0.2f, r * 0.15f}, 0));
        c.drawLine(x + r * 0.5f, y - r * 0.7f, x + r * 0.5f, y + r * 0.7f, sp);
        sp.setPathEffect(null);
        star(c, x - r * 0.35f, y, r * 0.45f);
    }

    void chip(Canvas c, float cx, float cy, int kind, int value) {
        String s = String.valueOf(value);
        tp.setTextSize(40 * u);
        float w = tp.measureText(s) + 110 * u;
        rf.set(cx - w / 2, cy - 38 * u, cx + w / 2, cy + 38 * u);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xCC140A2A);
        c.drawRoundRect(rf, 38 * u, 38 * u, p);
        sp.setPathEffect(null);
        sp.setStrokeWidth(4 * u);
        sp.setColor(0x88FFFFFF);
        c.drawRoundRect(rf, 38 * u, 38 * u, sp);
        float ix = rf.left + 40 * u;
        if (kind == 0) star(c, ix, cy, 24 * u);
        else if (kind == 1) milliteIcon(c, ix, cy, 24 * u);
        else ticketIcon(c, ix, cy, 18 * u);
        text(c, s, ix + 36 * u, cy + 15 * u, 40 * u, kind == 1 ? 0xFFE2C6FF : 0xFFFFD23F, Paint.Align.LEFT, 0);
    }

    void header(Canvas c, String title) {
        rf.set(30 * u, 50 * u, 150 * u, 150 * u);
        button(c, rf, "<", 0xFF6B5BD6, true, () -> go(S_LOBBY));
        title(c, title, 180 * u, 125 * u, 60 * u, 0xFFFFD23F, Paint.Align.LEFT, 360 * u);
        chip(c, W - 330 * u, 100 * u, 0, pf.stars);
        chip(c, W - 120 * u, 100 * u, 1, pf.millite);
    }

    void gear(Canvas c, float x, float y, float r) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFE6E0FF);
        for (int i = 0; i < 8; i++) {
            c.save();
            c.rotate(i * 45, x, y);
            rf2.set(x - r * 0.2f, y - r, x + r * 0.2f, y - r * 0.55f);
            c.drawRoundRect(rf2, r * 0.08f, r * 0.08f, p);
            c.restore();
        }
        c.drawCircle(x, y, r * 0.7f, p);
        p.setColor(0xFF6B5BD6);
        c.drawCircle(x, y, r * 0.3f, p);
    }

    void person(Canvas c, float x, float y, float r) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFE6E0FF);
        c.drawCircle(x, y - r * 0.35f, r * 0.4f, p);
        rf2.set(x - r * 0.75f, y + r * 0.1f, x + r * 0.75f, y + r * 1.4f);
        c.drawArc(rf2, 180, 180, true, p);
    }

    // ================================================================== bottom navigation
    void drawNav(Canvas c) {
        rf.set(-20 * u, navTop, W + 20 * u, H + 40 * u);
        panel(c, new RectF(rf), 0xF01A0F3D, 40 * u);
        String[] labels = {L.t("ร้านค้า", "Shop"), L.t("ดวงดาว", "Planet"), L.t("เล่น", "Play"),
                L.t("แบนเนอร์", "Banner"), L.t("เอนชานต์", "Enchant")};
        int[] scr = {S_SHOP, S_PLANET, S_PLAYMENU, S_BANNER, S_ENCHANT};
        float cw = W / 5f;
        for (int i = 0; i < 5; i++) {
            final int target = scr[i];
            float cx = cw * i + cw / 2;
            boolean act = screen == target;
            float iy = navTop + 85 * u;
            if (i == 2) {
                float r = 92 * u;
                float cy = navTop + 40 * u;
                p.setShader(null);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF140A2A);
                c.drawCircle(cx, cy + 8 * u, r, p);
                p.setColor(act ? 0xFFFFB627 : 0xFFFF8A2B);
                c.drawCircle(cx, cy, r, p);
                sp.setPathEffect(null);
                sp.setStrokeWidth(6 * u);
                sp.setColor(0xFF140A2A);
                c.drawCircle(cx, cy, r, sp);
                Path tri = new Path();
                tri.moveTo(cx - 26 * u, cy - 36 * u);
                tri.lineTo(cx + 38 * u, cy);
                tri.lineTo(cx - 26 * u, cy + 36 * u);
                tri.close();
                p.setColor(0xFFFFFFFF);
                c.drawPath(tri, p);
                text(c, labels[i], cx, navTop + 175 * u, 38 * u, 0xFFFFFFFF);
                btns.add(new Btn(new RectF(cx - cw / 2, navTop - 60 * u, cx + cw / 2, H), () -> go(target)));
                continue;
            }
            if (act) {
                p.setShader(null);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x44FFD23F);
                rf.set(cx - cw / 2 + 10 * u, navTop + 14 * u, cx + cw / 2 - 10 * u, H - 14 * u);
                c.drawRoundRect(rf, 30 * u, 30 * u, p);
            }
            switch (i) {
                case 0: { // shop bag
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xFF33C6A6);
                    rf.set(cx - 38 * u, iy - 26 * u, cx + 38 * u, iy + 40 * u);
                    c.drawRoundRect(rf, 12 * u, 12 * u, p);
                    sp.setStrokeWidth(6 * u);
                    sp.setColor(0xFF140A2A);
                    c.drawRoundRect(rf, 12 * u, 12 * u, sp);
                    sp.setColor(0xFF9CF2DE);
                    rf.set(cx - 22 * u, iy - 50 * u, cx + 22 * u, iy - 6 * u);
                    c.drawArc(rf, 180, 180, false, sp);
                    break;
                }
                case 1: sprite(c, pBmp[Defs.SATURN], cx, iy, 62 * u, 255); break;
                case 3: { // banner flag
                    rf.set(cx - 32 * u, iy - 46 * u, cx + 32 * u, iy + 44 * u);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xFFB86BFF);
                    Path f = new Path();
                    f.moveTo(rf.left, rf.top);
                    f.lineTo(rf.right, rf.top);
                    f.lineTo(rf.right, rf.bottom);
                    f.lineTo(cx, rf.bottom - 20 * u);
                    f.lineTo(rf.left, rf.bottom);
                    f.close();
                    c.drawPath(f, p);
                    sp.setStrokeWidth(6 * u);
                    sp.setColor(0xFF140A2A);
                    c.drawPath(f, sp);
                    star(c, cx, iy - 8 * u, 20 * u);
                    break;
                }
                case 4: badge(c, cx, iy, 40 * u, Defs.EN_MEGE, 0); break;
                default: break;
            }
            text(c, labels[i], cx, navTop + 175 * u, 34 * u, act ? 0xFFFFD23F : 0xFFE6E0FF, Paint.Align.CENTER, cw - 10 * u);
            btns.add(new Btn(new RectF(cx - cw / 2, navTop, cx + cw / 2, H), () -> go(target)));
        }
    }

    // ================================================================== lobby
    void drawLobby(Canvas c) {
        rf.set(30 * u, 50 * u, 150 * u, 170 * u);
        button(c, rf, null, 0xFF6B5BD6, true, () -> popup = P_PLAYER);
        person(c, rf.centerX(), rf.centerY() - 6 * u, 46 * u);
        rf.set(W - 150 * u, 50 * u, W - 30 * u, 170 * u);
        button(c, rf, null, 0xFF6B5BD6, true, () -> popup = P_SETTINGS);
        gear(c, rf.centerX(), rf.centerY() - 4 * u, 40 * u);
        chip(c, W / 2 - 120 * u, 110 * u, 0, pf.stars);
        chip(c, W / 2 + 140 * u, 110 * u, 1, pf.millite);

        float ty = 330 * u;
        logo(c, W / 2, ty, 170 * u);
        // solar system
        float top = ty + 60 * u, bot = navTop - 80 * u;
        float cx = W / 2, cy = (top + bot) / 2 + 20 * u, R = Math.min(W * 0.46f, (bot - top) * 0.7f);
        int[] orb = {Defs.MERCURY, Defs.VENUS, Defs.EARTH, Defs.MARS, Defs.JUPITER, Defs.SATURN, Defs.URANUS, Defs.NEPTUNE};
        sp.setPathEffect(null);
        sp.setStrokeWidth(3 * u);
        sp.setColor(0x33FFFFFF);
        for (int i = 0; i < orb.length; i++) {
            float rr = R * (0.36f + 0.64f * i / (orb.length - 1));
            rf.set(cx - rr, cy - rr * 0.62f, cx + rr, cy + rr * 0.62f);
            c.drawOval(rf, sp);
        }
        float sunHalf = R * 0.36f;
        // planets behind the sun first (upper half), then sun, then front ones
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1) sprite(c, pBmp[Defs.SUN], cx, cy, sunHalf, 255);
            for (int i = 0; i < orb.length; i++) {
                float rr = R * (0.36f + 0.64f * i / (orb.length - 1));
                double a = anim * (0.9 - i * 0.09) + i * 1.7;
                float px = cx + (float) Math.cos(a) * rr, py = cy + (float) Math.sin(a) * rr * 0.62f;
                boolean front = Math.sin(a) > 0;
                if ((pass == 1) != front) continue;
                float s = (0.85f + 0.15f * (float) Math.sin(a)) * R * (orb[i] == Defs.SATURN || orb[i] == Defs.JUPITER ? 0.22f : 0.16f);
                sprite(c, pBmp[orb[i]], px, py, s, 255);
            }
        }
        float mxp = ((anim * 0.15f) % 1.5f - 0.25f) * W;
        sprite(c, eBmp[Defs.E_SHOOT], mxp, ty + 110 * u + mxp * 0.05f, 30 * u, 255);
        plain(c, VERSION, W - 30 * u, navTop - 110 * u, 28 * u, 0x99FFFFFF, Paint.Align.RIGHT, 0);
    }

    // ================================================================== shop
    void drawShop(Canvas c) {
        header(c, L.t("ร้านค้า", "Shop"));
        float y = 220 * u;
        RectF r = new RectF(50 * u, y, W - 50 * u, y + 330 * u);
        panel(c, r, 0xFF2A1A5E, 40 * u);
        ticketIcon(c, r.left + 130 * u, r.centerY() - 20 * u, 60 * u);
        text(c, "Effect Ticket", r.left + 250 * u, r.top + 90 * u, 52 * u, 0xFFFFFFFF, Paint.Align.LEFT, 0);
        plain(c, L.t("ใช้กับดวงดาวเพื่อสุ่ม Effect", "Use on a planet to roll an Effect"), r.left + 250 * u, r.top + 145 * u, 32 * u, 0xFFC9C2E6, Paint.Align.LEFT, r.width() - 280 * u);
        plain(c, "Gold 50% • Bigger 25% • Elite 17.5% • Diamond 7.5%", r.left + 250 * u, r.top + 195 * u, 28 * u, 0xFF9C94C2, Paint.Align.LEFT, r.width() - 280 * u);
        plain(c, L.t("มีอยู่ ", "Owned ") + pf.tickets, r.left + 130 * u, r.bottom - 40 * u, 32 * u, 0xFFFFD23F, Paint.Align.CENTER, 0);
        rf.set(r.right - 330 * u, r.bottom - 120 * u, r.right - 40 * u, r.bottom - 30 * u);
        button(c, rf, L.t("ซื้อ ", "Buy ") + Profile.TICKET_PRICE, 0xFF33C6A6, pf.stars >= Profile.TICKET_PRICE, () -> {
            if (pf.buyTicket()) showToast(L.t("ได้ Effect Ticket แล้ว! ไปใช้ที่เมนูดวงดาว", "Got an Effect Ticket! Use it in the Planet menu"));
            else showToast(L.t("Star ไม่พอ", "Not enough Star"));
        });
        star(c, rf.left - 30 * u, rf.centerY(), 22 * u);
        float y2 = r.bottom + 40 * u;
        float cw = (W - 140 * u) / 2f;
        for (int i = 0; i < 2; i++) {
            RectF q = new RectF(50 * u + i * (cw + 40 * u), y2, 50 * u + i * (cw + 40 * u) + cw, y2 + 300 * u);
            panel(c, q, 0xFF221B38, 40 * u);
            text(c, "?", q.centerX(), q.centerY() + 10 * u, 110 * u, 0xFF4A4460);
            plain(c, L.t("เร็ว ๆ นี้", "Coming soon"), q.centerX(), q.bottom - 40 * u, 34 * u, 0xFF8A84A0, Paint.Align.CENTER, 0);
        }
    }

    // ================================================================== planet menu
    void drawPlanetMenu(Canvas c) {
        header(c, L.t("ดวงดาว", "Planet"));
        int t = selType;
        boolean own = pf.owned[t];
        // inventory (anchored to bottom)
        float cell = (W - 100 * u) / 6f;
        float invTop = navTop - 30 * u - cell * 2;
        text(c, L.t("ดาวทั้งหมดในกระเป๋า", "All planets") + "  " + pf.ownedCount() + "/" + Defs.P_COUNT,
                50 * u, invTop - 20 * u, 38 * u, 0xFFFFFFFF, Paint.Align.LEFT, 0);
        for (int i = 0; i < Defs.P_COUNT; i++) {
            final int ti = i;
            float x = 50 * u + (i % 6) * cell, y = invTop + (i / 6) * cell;
            RectF r = new RectF(x + 6 * u, y + 6 * u, x + cell - 6 * u, y + cell - 6 * u);
            int rc = Defs.RARITY_COLOR[Defs.P_RARITY[i]];
            panel(c, r, pf.owned[i] ? (0xFF000000 | (rc & 0x00FFFFFF)) & 0x66FFFFFF | 0xFF000000 : 0xFF1A1430, 22 * u);
            p.setStyle(Paint.Style.FILL);
            p.setColor(pf.owned[i] ? 0xCC1A0F3D : 0xFF120C24);
            rf.set(r.left + 6 * u, r.top + 6 * u, r.right - 6 * u, r.bottom - 6 * u);
            c.drawRoundRect(rf, 18 * u, 18 * u, p);
            sprite(c, pBmp[i], r.centerX(), r.centerY() - 6 * u, cell * 0.42f, pf.owned[i] ? 255 : 45);
            if (pf.owned[i]) {
                text(c, "Lv" + pf.level[i], r.centerX(), r.bottom - 14 * u, 26 * u, 0xFFFFFFFF);
                if (pf.inDeck(i)) {
                    p.setColor(0xFF33C6A6);
                    c.drawCircle(r.right - 18 * u, r.top + 18 * u, 13 * u, p);
                }
            } else text(c, "?", r.centerX(), r.centerY() + 18 * u, 56 * u, 0xFF6A6488);
            if (i == t) {
                sp.setPathEffect(null);
                sp.setStrokeWidth(7 * u);
                sp.setColor(0xFFFFD23F);
                c.drawRoundRect(r, 22 * u, 22 * u, sp);
            }
            btns.add(new Btn(r, () -> selType = ti));
        }
        // deck row
        float dTop = invTop - 210 * u, ds = 130 * u;
        text(c, "Deck", 50 * u, dTop + ds / 2 + 14 * u, 40 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
        for (int i = 0; i < Profile.DECK_SIZE; i++) {
            final int d = pf.deck[i];
            float x = 190 * u + i * (ds + 24 * u);
            RectF r = new RectF(x, dTop, x + ds, dTop + ds);
            panel(c, r, d >= 0 ? 0xFF33296B : 0xFF1A1430, 24 * u);
            if (d >= 0) {
                sprite(c, pBmp[d], r.centerX(), r.centerY(), ds * 0.45f, 255);
                btns.add(new Btn(r, () -> selType = d));
            } else text(c, "+", r.centerX(), r.centerY() + 20 * u, 60 * u, 0xFF6A6488);
        }
        // buttons (two rows)
        float bh = 96 * u, bw = (W - 140 * u) / 2f;
        float b2 = dTop - 40 * u - bh, b1 = b2 - 24 * u - bh;
        boolean maxed = pf.level[t] >= Profile.MAX_LV;
        boolean canUp = own && !maxed && pf.copies[t] >= pf.upgradeCopies(t) && pf.stars >= pf.upgradeStars(t);
        rf.set(50 * u, b1, 50 * u + bw, b1 + bh);
        button(c, rf, maxed ? "MAX" : L.t("อัปเกรด Lv", "Upgrade Lv"), 0xFF33C6A6, canUp, () -> {
            String err = pf.upgrade(selType);
            if (err != null) showToast(err);
            else showToast(Defs.P_NAME[selType] + " Lv " + pf.level[selType] + "!");
        });
        rf.set(90 * u + bw, b1, 90 * u + bw * 2, b1 + bh);
        button(c, rf, L.t("Evolve (เร็ว ๆ นี้)", "Evolve (soon)"), 0xFF6B5BD6, false,
                () -> showToast(L.t("ระบบ Evolve เร็ว ๆ นี้", "Evolve is coming soon")));
        boolean inDeck = pf.inDeck(t);
        rf.set(50 * u, b2, 50 * u + bw, b2 + bh);
        button(c, rf, inDeck ? L.t("ถอดออกจาก Deck", "Remove from deck") : L.t("ใส่ Deck", "Add to deck"),
                inDeck ? 0xFFFF6B5B : 0xFF4F8BFF, own, () -> {
                    String err = pf.toggleDeck(selType);
                    if (err != null) showToast(err);
                });
        rf.set(90 * u + bw, b2, 90 * u + bw * 2, b2 + bh);
        button(c, rf, L.t("ใช้ Ticket", "Use Ticket") + " (" + pf.tickets + ")", 0xFFFF8A5C, own && pf.tickets > 0, () -> {
            if (pf.useTicket(selType)) {
                showToast(L.t("ได้ Effect: ", "Got effect: ") + Defs.EF_NAME[pf.effect[selType]] + "!");
                enchFlash = 0.6f;
            } else showToast(pf.tickets <= 0 ? L.t("ไม่มี Effect Ticket (ซื้อที่ร้านค้า)", "No Effect Ticket (buy in Shop)")
                    : L.t("ยังไม่มีดวงดาวนี้", "You don't own this planet"));
        });
        // status block
        float sTop = b1 - 260 * u;
        int rar = Defs.P_RARITY[t];
        text(c, L.t("สถานะ:", "Status:"), 50 * u, sTop + 40 * u, 38 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
        text(c, L.t("ระดับ: ", "Rarity: ") + Defs.RARITY_NAME[rar], W - 50 * u, sTop + 40 * u, 38 * u, Defs.RARITY_COLOR[rar], Paint.Align.RIGHT, 0);
        Planet tmpP = previewPlanet(t);
        String s1;
        if (Game.isProducer(tmpP)) s1 = String.format(Locale.US, L.t("ผลิต %d SP ทุก %.1f วิ", "Makes %d SP every %.1fs"), Game.produceAmount(tmpP), Game.cdOf(tmpP));
        else if (t == Defs.SUN) s1 = String.format(Locale.US, L.t("คลื่นความร้อน ทุก %.1f วิ", "Heat wave every %.1fs"), Game.cdOf(tmpP));
        else s1 = String.format(Locale.US, L.t("ดาเมจ %.1f  ระยะ %d  ยิงทุก %.2f วิ", "DMG %.1f  Range %d  Every %.2fs"), Game.dmgOf(tmpP), (int) Game.rangeOf(tmpP), Game.cdOf(tmpP));
        plain(c, s1, 50 * u, sTop + 95 * u, 32 * u, 0xFFE6E0FF, Paint.Align.LEFT, W - 100 * u);
        String cp = maxed ? "" : String.format(Locale.US, L.t("   การ์ดซ้ำ %d/%d  •  ใช้ %d Star", "   Copies %d/%d  •  Cost %d Star"),
                pf.copies[t], pf.upgradeCopies(t), pf.upgradeStars(t));
        plain(c, "Lv " + pf.level[t] + "/" + Profile.MAX_LV + cp, 50 * u, sTop + 145 * u, 30 * u, 0xFFC9C2E6, Paint.Align.LEFT, W - 100 * u);
        if (pf.enchant[t] >= 0) {
            badge(c, 75 * u, sTop + 192 * u, 22 * u, pf.enchant[t], 0);
            plain(c, enchName(pf.enchant[t], pf.tier[t]), 110 * u, sTop + 204 * u, 32 * u, Defs.RARITY_COLOR[Defs.EN_RARITY[pf.enchant[t]]], Paint.Align.LEFT, 0);
        } else plain(c, L.t("Enchant: ยังไม่มี", "Enchant: none"), 50 * u, sTop + 204 * u, 32 * u, 0xFF8A84A0, Paint.Align.LEFT, 0);
        if (pf.effect[t] >= 0) plain(c, "Effect: " + Defs.EF_NAME[pf.effect[t]], W - 50 * u, sTop + 204 * u, 32 * u, Defs.EF_COLOR[pf.effect[t]], Paint.Align.RIGHT, 0);
        else plain(c, L.t("Effect: ยังไม่มี", "Effect: none"), W - 50 * u, sTop + 204 * u, 32 * u, 0xFF8A84A0, Paint.Align.RIGHT, 0);
        // big planet
        float aTop = 190 * u, aBot = sTop - 20 * u;
        float nameY = aBot - 60 * u;
        float half = Math.min(240 * u, (nameY - 70 * u - aTop) * 0.45f);
        float cy = (aTop + nameY - 60 * u) / 2;
        bigPlanet(c, t, W / 2, cy, half, own);
        text(c, Defs.P_NAME[t], W / 2, nameY, 60 * u, own ? 0xFFFFFFFF : 0xFF8A84A0);
        plain(c, L.en ? Defs.P_DESC_EN[t] : Defs.P_DESC[t], W / 2, nameY + 45 * u, 30 * u, 0xFFC9C2E6, Paint.Align.CENTER, W - 100 * u);
    }

    Planet previewPlanet(int t) {
        Planet q = new Planet();
        q.type = t;
        q.plv = pf.level[t];
        q.enchant = pf.enchant[t];
        q.tier = pf.tier[t];
        q.effect = pf.effect[t];
        return q;
    }

    String enchName(int e, int tier) {
        if (e < 0) return "-";
        return Defs.EN_NAME[e] + (tier > 0 ? " " + (tier == 1 ? "I" : tier == 2 ? "II" : "III") : "");
    }

    void bigPlanet(Canvas c, int t, float x, float y, float half, boolean own) {
        int rc = Defs.RARITY_COLOR[Defs.P_RARITY[t]];
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new RadialGradient(x, y, half * 1.4f, (rc & 0x00FFFFFF) | 0x66000000, rc & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, half * 1.4f, p);
        p.setShader(null);
        if (own && pf.effect[t] == Defs.EF_GOLD || own && enchFlash > 0) {
            sp.setPathEffect(null);
            sp.setStrokeWidth(8 * u);
            sp.setColor(enchFlash > 0 ? 0xFFFFFFFF : 0xFFFFD23F);
            c.drawCircle(x, y, half * 0.8f + enchFlash * 60 * u, sp);
        }
        if (own && pf.effect[t] == Defs.EF_DIAMOND) {
            for (int i = 0; i < 4; i++) {
                double a = anim * 1.2 + i * Math.PI / 2;
                float dx = x + (float) Math.cos(a) * half * 0.95f, dy = y + (float) Math.sin(a) * half * 0.95f;
                c.save();
                c.translate(dx, dy);
                c.scale(half * 0.08f, half * 0.08f);
                p.setColor(0xFF7DF3FF);
                c.drawPath(Art.starPath(0, 0, 1.6f, 0.6f, 4), p);
                c.restore();
            }
        }
        float s = own && pf.effect[t] == Defs.EF_BIGGER ? 1.12f : 1f;
        float bob = (float) Math.sin(anim * 2) * 8 * u;
        sprite(c, pBmp[t], x, y + bob, half * s, own ? 255 : 60);
        if (own && pf.effect[t] == Defs.EF_ELITE) {
            Path cw = new Path();
            float ty = y - half * 0.62f + bob, w = half * 0.3f;
            cw.moveTo(x - w, ty);
            cw.lineTo(x - w, ty - w * 0.8f);
            cw.lineTo(x - w * 0.5f, ty - w * 0.35f);
            cw.lineTo(x, ty - w);
            cw.lineTo(x + w * 0.5f, ty - w * 0.35f);
            cw.lineTo(x + w, ty - w * 0.8f);
            cw.lineTo(x + w, ty);
            cw.close();
            p.setColor(0xFFFF4D5E);
            c.drawPath(cw, p);
            sp.setStrokeWidth(5 * u);
            sp.setColor(0xFF140A2A);
            c.drawPath(cw, sp);
        }
        if (own && pf.enchant[t] >= 0) badge(c, x + half * 0.62f, y - half * 0.55f + bob, half * 0.2f, pf.enchant[t], pf.tier[t]);
        if (!own) text(c, "?", x, y + half * 0.25f, half * 0.8f, 0xFF8A84A0);
    }

    // ================================================================== play menu
    void drawPlayMenu(Canvas c) {
        header(c, L.t("เล่น", "Play"));
        // stage list anchored to bottom
        int n = Defs.ST_NAME.length;
        float ch = 150 * u, gap = 18 * u;
        float listTop = navTop - 30 * u - n * (ch + gap);
        text(c, "Stage:", 50 * u, listTop - 24 * u, 42 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
        for (int i = 0; i < n; i++) {
            final int s = i;
            boolean open = i < pf.unlocked;
            float y = listTop + i * (ch + gap);
            RectF r = new RectF(50 * u, y, W - 50 * u, y + ch);
            panel(c, r, open ? 0xFF2A1A5E : 0xFF221B38, 30 * u);
            p.setStyle(Paint.Style.FILL);
            p.setColor(open ? Defs.ST_COLOR[i] : 0xFF4A4460);
            rf.set(r.left, r.top, r.left + 20 * u, r.bottom);
            c.drawRoundRect(rf, 10 * u, 10 * u, p);
            text(c, (i + 1) + ". " + (L.en ? Defs.ST_NAME_EN[i] : Defs.ST_NAME[i]), r.left + 50 * u, r.top + 65 * u, 46 * u,
                    open ? 0xFFFFFFFF : 0xFF8A84A0, Paint.Align.LEFT, r.width() - 300 * u);
            String sub;
            if (!open) sub = L.t("ผ่านด่านก่อนหน้าเพื่อปลดล็อก", "Clear the previous stage to unlock");
            else {
                sub = Defs.ST_WAVES[i] + L.t(" เวฟ", " waves");
                if (pf.clear[i]) sub += L.t("  •  ผ่านแล้ว", "  •  Cleared");
                else if (pf.best[i] > 0) sub += L.t("  •  ดีที่สุด ", "  •  Best ") + pf.best[i];
            }
            plain(c, sub, r.left + 50 * u, r.top + 118 * u, 30 * u, 0xFFC9C2E6, Paint.Align.LEFT, r.width() - 300 * u);
            if (open) {
                rf.set(r.right - 220 * u, r.top + 28 * u, r.right - 30 * u, r.bottom - 28 * u);
                button(c, rf, L.t("เริ่ม", "Start"), 0xFFFF8A2B, true, () -> startStage(s));
                btns.add(new Btn(r, () -> startStage(s)));
                btns.add(btns.remove(btns.size() - 2)); // keep the start button on top
            } else text(c, L.t("ล็อก", "Locked"), r.right - 125 * u, r.centerY() + 14 * u, 38 * u, 0xFF8A84A0);
        }
        // mode tabs
        float tabY = listTop - 190 * u, tw = (W - 120 * u) / 3f;
        String[] tabs = {"Story", L.t("Challenge (เร็ว ๆ นี้)", "Challenge (soon)"), L.t("Raid (เร็ว ๆ นี้)", "Raid (soon)")};
        for (int i = 0; i < 3; i++) {
            final int ti = i;
            rf.set(50 * u + i * (tw + 10 * u), tabY, 50 * u + i * (tw + 10 * u) + tw, tabY + 90 * u);
            button(c, rf, tabs[i], i == 0 ? 0xFFFF8A2B : 0xFF3A3360, i == 0, () -> {
                if (ti != 0) showToast(L.t("โหมดนี้เร็ว ๆ นี้", "This mode is coming soon"));
            });
        }
        // illustration
        float aTop = 190 * u, aBot = tabY - 30 * u;
        rf.set(50 * u, aTop, W - 50 * u, aBot);
        RectF art = new RectF(rf);
        panel(c, art, 0xFF0E0828, 40 * u);
        c.save();
        Path clip = new Path();
        clip.addCircle(art.centerX(), art.centerY(), 1, Path.Direction.CW);
        float gx = art.centerX(), gy = art.centerY(), gr = Math.min(art.width(), art.height()) * 0.46f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new RadialGradient(gx, gy, gr, 0x99B86BFF, 0x00140A2A, Shader.TileMode.CLAMP));
        c.drawCircle(gx, gy, gr, p);
        p.setShader(null);
        for (int arm = 0; arm < 3; arm++) {
            for (int k = 0; k < 70; k++) {
                float f = k / 70f;
                double a = anim * 0.25 + arm * 2.094 + f * 4.2;
                float rr = gr * (0.08f + f * 0.92f);
                float x = gx + (float) Math.cos(a) * rr, y = gy + (float) Math.sin(a) * rr * 0.55f;
                p.setColor(k % 3 == 0 ? 0xFFFFE6A8 : (arm == 1 ? 0xFF8FD6FF : 0xFFE6B8FF));
                p.setAlpha((int) (230 * (1 - f * 0.7f)));
                c.drawCircle(x, y, (3.5f - f * 2f) * u, p);
            }
        }
        p.setAlpha(255);
        p.setColor(0xFFFFF4D0);
        c.drawCircle(gx, gy, 16 * u, p);
        c.restore();
        sprite(c, pBmp[Defs.EARTH], art.left + art.width() * 0.16f, art.bottom - art.height() * 0.24f, art.height() * 0.13f, 255);
        sprite(c, pBmp[Defs.SATURN], art.right - art.width() * 0.15f, art.top + art.height() * 0.25f, art.height() * 0.16f, 255);
        blackHole(c, art.right - art.width() * 0.2f, art.bottom - art.height() * 0.22f, art.height() * 0.07f);
        float mx = art.left + ((anim * 0.12f) % 1f) * art.width();
        sprite(c, eBmp[Defs.E_METEOR], mx, art.top + art.height() * 0.2f + (mx - art.left) * 0.15f, art.height() * 0.06f, 255);
    }

    // ================================================================== banner
    void drawBanner(Canvas c) {
        header(c, L.t("แบนเนอร์", "Banner"));
        rf.set(50 * u, 190 * u, 360 * u, 270 * u);
        button(c, rf, "Regular", 0xFFFF8A2B, true, null);
        float bTop = 300 * u, bBot = navTop - 330 * u;
        RectF card = new RectF(50 * u, bTop, W - 50 * u, bBot);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(card.left, card.top, card.right, card.bottom, 0xFF5B2FB8, 0xFFFF7A4A, Shader.TileMode.CLAMP));
        rf2.set(card);
        rf2.offset(0, 10 * u);
        c.drawRoundRect(card, 44 * u, 44 * u, p);
        p.setShader(null);
        sp.setPathEffect(null);
        sp.setStrokeWidth(6 * u);
        sp.setColor(0xFF140A2A);
        c.drawRoundRect(card, 44 * u, 44 * u, sp);
        float cx = card.centerX(), cy = card.top + card.height() * 0.48f, hh = Math.min(card.width(), card.height() * 0.85f);
        p.setColor(0xFFFFFFFF);
        p.setShader(new RadialGradient(cx, cy, hh * 0.45f, 0x88FFF0B0, 0x00FFF0B0, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, hh * 0.45f, p);
        p.setShader(null);
        for (int i = 0; i < 12; i++) {
            double a = anim * 0.4 + i * Math.PI / 6;
            float x = cx + (float) Math.cos(a) * hh * 0.36f, y = cy + (float) Math.sin(a) * hh * 0.25f;
            star(c, x, y, (8 + (i % 3) * 4) * u);
        }
        sprite(c, pBmp[Defs.SUN], cx, cy, hh * 0.22f, 255);
        sprite(c, pBmp[Defs.VENUS], cx - hh * 0.3f, cy + hh * 0.12f, hh * 0.11f, 255);
        sprite(c, pBmp[Defs.URANUS], cx + hh * 0.3f, cy - hh * 0.12f, hh * 0.12f, 255);
        sprite(c, pBmp[Defs.NEPTUNE], cx + hh * 0.32f, cy + hh * 0.16f, hh * 0.09f, 255);
        sprite(c, pBmp[Defs.MERCURY], cx - hh * 0.3f, cy - hh * 0.16f, hh * 0.08f, 255);
        title(c, "Regular Banner", cx, card.top + 80 * u, 64 * u, 0xFFFFFFFF);
        String rates = "Common 45%  Rare 30%  Epic 14%  Legend 9.5%  Mythical 1.5%";
        plain(c, rates, cx, card.bottom - 40 * u, 28 * u, 0xFFFFF0D8, Paint.Align.CENTER, card.width() - 60 * u);
        // pull buttons
        float by = bBot + 40 * u, bw = (W - 140 * u) / 2f, bh = 120 * u;
        rf.set(50 * u, by, 50 * u + bw, by + bh);
        button(c, rf, L.t("สุ่ม 1", "Roll 1") + "  (" + Profile.PULL1 + ")", 0xFF4F8BFF, pf.stars >= Profile.PULL1, () -> doPull(1));
        rf.set(90 * u + bw, by, 90 * u + bw * 2, by + bh);
        button(c, rf, L.t("สุ่ม 10", "Roll 10") + "  (" + Profile.PULL10 + ")", 0xFFFF8A2B, pf.stars >= Profile.PULL10, () -> doPull(10));
        star(c, W / 2 - 110 * u, by + bh + 70 * u, 26 * u);
        text(c, "Star: " + pf.stars, W / 2 - 70 * u, by + bh + 86 * u, 46 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
    }

    void doPull(int n) {
        int[] r = pf.pull(n);
        if (r == null) { showToast(L.t("Star ไม่พอ", "Not enough Star")); return; }
        gachaRes = r;
        gachaNew = pf.lastNew;
        gachaT = 0;
        popup = P_GACHA;
    }

    void drawGachaResult(Canvas c) {
        dim(c);
        int n = gachaRes.length;
        int cols = n == 1 ? 1 : 5;
        int rows = (n + cols - 1) / cols;
        float cell = n == 1 ? 420 * u : (W - 100 * u) / 5f;
        float gw = cols * cell, gh = rows * (cell + 70 * u);
        float x0 = (W - gw) / 2, y0 = H / 2 - gh / 2 - 60 * u;
        title(c, L.t("ผลการสุ่ม", "Results"), W / 2, y0 - 60 * u, 70 * u, 0xFFFFD23F);
        for (int i = 0; i < n; i++) {
            float appear = Math.max(0, Math.min(1, (gachaT - i * 0.12f) * 4f));
            if (appear <= 0) continue;
            int t = gachaRes[i];
            int rc = Defs.RARITY_COLOR[Defs.P_RARITY[t]];
            float x = x0 + (i % cols) * cell + cell / 2, y = y0 + (i / cols) * (cell + 70 * u) + cell / 2;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            p.setShader(new RadialGradient(x, y, cell * 0.55f, rc, rc & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, cell * 0.55f * appear, p);
            p.setShader(null);
            sprite(c, pBmp[t], x, y, cell * 0.42f * appear, 255);
            text(c, Defs.P_NAME[t], x, y + cell * 0.52f, (n == 1 ? 56 : 28) * u, rc, Paint.Align.CENTER, cell);
            if (gachaNew != null && i < gachaNew.length && gachaNew[i])
                text(c, "NEW!", x, y - cell * 0.38f, (n == 1 ? 50 : 26) * u, 0xFF7CFF8A);
            else text(c, "+1", x + cell * 0.3f, y - cell * 0.3f, (n == 1 ? 46 : 26) * u, 0xFFFFFFFF);
        }
        rf.set(W / 2 - 220 * u, y0 + gh + 60 * u, W / 2 + 220 * u, y0 + gh + 180 * u);
        button(c, rf, "OK", 0xFF33C6A6, true, () -> popup = P_NONE);
    }

    // ================================================================== enchant
    void updateAuto(float dt) {
        if (!autoOn) return;
        autoT -= dt;
        if (autoT > 0) return;
        autoT = 0.12f;
        if (pf.millite < Profile.ROLL_COST) {
            autoOn = false;
            showToast(L.t("Millite หมดแล้ว", "Out of Millite"));
            return;
        }
        pf.rollEnchant(enchSel);
        autoCount++;
        enchFlash = 0.15f;
        if (pf.enchant[enchSel] == autoTarget && (autoTarget > Defs.EN_CD || pf.tier[enchSel] == 3)) {
            autoOn = false;
            enchFlash = 0.8f;
            showToast(L.t("ได้แล้ว! ", "Got it! ") + enchName(pf.enchant[enchSel], pf.tier[enchSel])
                    + L.t(" (สุ่ม ", " (") + autoCount + L.t(" ครั้ง)", " rolls)"));
        }
    }

    void drawEnchant(Canvas c) {
        header(c, L.t("เอนชานต์", "Enchant"));
        if (!pf.owned[enchSel]) enchSel = Defs.EARTH;
        // millite + buttons anchored to bottom
        float mY = navTop - 50 * u;
        milliteIcon(c, W / 2 - 150 * u, mY - 14 * u, 26 * u);
        text(c, "Millite: " + pf.millite, W / 2 - 110 * u, mY, 44 * u, 0xFFE2C6FF, Paint.Align.LEFT, 0);
        float bh = 110 * u, bw = (W - 140 * u) / 3f, by = mY - 70 * u - bh;
        rf.set(50 * u, by, 50 * u + bw, by + bh);
        button(c, rf, L.t("สุ่ม", "Roll") + " (1)", 0xFFB86BFF, !autoOn && pf.millite >= Profile.ROLL_COST, () -> {
            if (pf.rollEnchant(enchSel)) enchFlash = 0.5f;
            else showToast(L.t("Millite ไม่พอ (ได้จากการฆ่าบอส)", "Not enough Millite (earned from bosses)"));
        });
        rf.set(70 * u + bw, by, 70 * u + bw * 2, by + bh);
        button(c, rf, autoOn ? L.t("หยุด", "Stop") : L.t("สุ่มอัตโนมัติ", "Auto roll"), autoOn ? 0xFFFF6B5B : 0xFF4F8BFF, true, () -> {
            if (autoOn) autoOn = false;
            else popup = P_AUTO;
        });
        rf.set(90 * u + bw * 2, by, 90 * u + bw * 3, by + bh);
        button(c, rf, "Index", 0xFF33C6A6, true, () -> popup = P_INDEX);
        // owned planet picker
        float cell = (W - 100 * u) / 6f;
        float pTop = by - 40 * u - cell * 2;
        text(c, L.t("เลือกดวงดาว", "Choose a planet"), 50 * u, pTop - 20 * u, 38 * u, 0xFFFFFFFF, Paint.Align.LEFT, 0);
        int k = 0;
        for (int t = 0; t < Defs.P_COUNT; t++) {
            if (!pf.owned[t]) continue;
            final int tt = t;
            float x = 50 * u + (k % 6) * cell, y = pTop + (k / 6) * cell;
            RectF r = new RectF(x + 6 * u, y + 6 * u, x + cell - 6 * u, y + cell - 6 * u);
            panel(c, r, t == enchSel ? 0xFF4A3D8F : 0xFF221B48, 22 * u);
            sprite(c, pBmp[t], r.centerX(), r.centerY(), cell * 0.4f, 255);
            if (pf.enchant[t] >= 0) badge(c, r.right - 22 * u, r.top + 22 * u, 16 * u, pf.enchant[t], 0);
            if (t == enchSel) {
                sp.setPathEffect(null);
                sp.setStrokeWidth(7 * u);
                sp.setColor(0xFFFFD23F);
                c.drawRoundRect(r, 22 * u, 22 * u, sp);
            }
            btns.add(new Btn(r, () -> { if (!autoOn) enchSel = tt; }));
            k++;
        }
        // selected planet
        float aTop = 190 * u, aBot = pTop - 70 * u;
        float half = Math.min(250 * u, (aBot - aTop - 260 * u) * 0.5f);
        float cy = aTop + half + 40 * u;
        bigPlanet(c, enchSel, W / 2, cy, half, true);
        int e = pf.enchant[enchSel];
        text(c, Defs.P_NAME[enchSel], W / 2, cy + half + 70 * u, 52 * u, 0xFFFFFFFF);
        if (e >= 0) {
            int rc = Defs.RARITY_COLOR[Defs.EN_RARITY[e]];
            float sc = 1f + enchFlash * 0.4f;
            text(c, enchName(e, pf.tier[enchSel]), W / 2, cy + half + 140 * u, 56 * u * sc, rc);
            plain(c, Defs.RARITY_NAME[Defs.EN_RARITY[e]] + "  •  " + (L.en ? Defs.EN_DESC_EN[e] : Defs.EN_DESC[e]),
                    W / 2, cy + half + 190 * u, 30 * u, 0xFFC9C2E6, Paint.Align.CENTER, W - 100 * u);
        } else {
            plain(c, L.t("ยังไม่มี Enchant — กดสุ่มเลย!", "No enchant yet — roll one!"), W / 2, cy + half + 140 * u, 36 * u, 0xFF8A84A0, Paint.Align.CENTER, 0);
        }
        if (autoOn) plain(c, L.t("กำลังสุ่มหา ", "Rolling for ") + Defs.EN_NAME[autoTarget] + (autoTarget <= Defs.EN_CD ? " III" : "")
                + "...  " + autoCount, W / 2, aBot + 30 * u, 30 * u, 0xFFFFD23F, Paint.Align.CENTER, 0);
    }

    void popupFrame(Canvas c, RectF r, String title) {
        dim(c);
        panel(c, r, 0xFF2A1A5E, 44 * u);
        title(c, title, r.centerX(), r.top + 95 * u, 60 * u, 0xFFFFD23F, Paint.Align.CENTER, r.width() - 60 * u);
    }

    void drawIndex(Canvas c) {
        RectF r = new RectF(40 * u, 150 * u, W - 40 * u, H - 150 * u);
        popupFrame(c, r, "Enchant Index");
        int n = Defs.EN_NAME.length;
        float top = r.top + 150 * u, rh = Math.min(130 * u, (r.height() - 330 * u) / n);
        for (int i = 0; i < n; i++) {
            float y = top + i * rh;
            badge(c, r.left + 70 * u, y + rh / 2, rh * 0.32f, i, 0);
            int rc = Defs.RARITY_COLOR[Defs.EN_RARITY[i]];
            text(c, Defs.EN_NAME[i] + (i <= Defs.EN_CD ? " I-III" : ""), r.left + 130 * u, y + rh * 0.45f, rh * 0.3f, 0xFFFFFFFF, Paint.Align.LEFT, 0);
            text(c, String.format(Locale.US, "%s  %.1f%%", Defs.RARITY_NAME[Defs.EN_RARITY[i]], Profile.EN_CHANCE[i]),
                    r.right - 40 * u, y + rh * 0.45f, rh * 0.24f, rc, Paint.Align.RIGHT, 0);
            plain(c, L.en ? Defs.EN_DESC_EN[i] : Defs.EN_DESC[i], r.left + 130 * u, y + rh * 0.85f, rh * 0.22f, 0xFFC9C2E6, Paint.Align.LEFT, r.width() - 180 * u);
        }
        plain(c, L.t("ระดับ I/II/III: 60% / 30% / 10%", "Tier I/II/III: 60% / 30% / 10%"), r.centerX(), r.bottom - 150 * u, 30 * u, 0xFFC9C2E6, Paint.Align.CENTER, 0);
        rf.set(r.centerX() - 200 * u, r.bottom - 120 * u, r.centerX() + 200 * u, r.bottom - 30 * u);
        button(c, rf, L.t("ปิด", "Close"), 0xFF6B5BD6, true, () -> popup = P_NONE);
    }

    void drawAutoPick(Canvas c) {
        RectF r = new RectF(40 * u, 150 * u, W - 40 * u, H - 150 * u);
        popupFrame(c, r, L.t("สุ่มจนกว่าจะได้...", "Roll until you get..."));
        int n = Defs.EN_NAME.length;
        float top = r.top + 150 * u, rh = Math.min(120 * u, (r.height() - 300 * u) / n);
        for (int i = 0; i < n; i++) {
            final int e = i;
            rf.set(r.left + 40 * u, top + i * rh + 6 * u, r.right - 40 * u, top + (i + 1) * rh - 6 * u);
            String label = Defs.EN_NAME[i] + (i <= Defs.EN_CD ? " III" : "") + "  (" + Defs.RARITY_NAME[Defs.EN_RARITY[i]] + ")";
            button(c, rf, label, i == Defs.EN_MEGE ? 0xFFD0407A : (Defs.EN_RARITY[i] >= Defs.LEGEND ? 0xFFC98A1A : (Defs.EN_RARITY[i] == Defs.RARE ? 0xFF3A6FD0 : 0xFF4A4470)), true, () -> {
                autoTarget = e;
                autoCount = 0;
                autoT = 0;
                autoOn = true;
                popup = P_NONE;
            });
        }
        rf.set(r.centerX() - 200 * u, r.bottom - 120 * u, r.centerX() + 200 * u, r.bottom - 30 * u);
        button(c, rf, L.t("ยกเลิก", "Cancel"), 0xFF6B5BD6, true, () -> popup = P_NONE);
    }

    // ================================================================== lobby popups
    void drawPlayerInfo(Canvas c) {
        RectF r = new RectF(80 * u, H / 2 - 520 * u, W - 80 * u, H / 2 + 520 * u);
        popupFrame(c, r, L.t("ข้อมูลผู้เล่น", "Player info"));
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF6B5BD6);
        c.drawCircle(r.centerX(), r.top + 230 * u, 90 * u, p);
        person(c, r.centerX(), r.top + 225 * u, 70 * u);
        text(c, "Commander", r.centerX(), r.top + 380 * u, 52 * u, 0xFFFFFFFF);
        int cleared = 0;
        for (boolean b : pf.clear) if (b) cleared++;
        String[] lines = {
                "Star: " + pf.stars,
                "Millite: " + pf.millite,
                "Effect Ticket: " + pf.tickets,
                L.t("ดวงดาวที่มี: ", "Planets owned: ") + pf.ownedCount() + "/" + Defs.P_COUNT,
                L.t("ด่านที่ผ่าน: ", "Stages cleared: ") + cleared + "/" + Defs.ST_NAME.length,
                L.t("บอสที่ล้มได้: ", "Bosses defeated: ") + pf.bossKills,
                L.t("จำนวนเกมที่เล่น: ", "Games played: ") + pf.games};
        float y = r.top + 470 * u;
        for (String s : lines) {
            plain(c, s, r.left + 90 * u, y, 38 * u, 0xFFE6E0FF, Paint.Align.LEFT, r.width() - 180 * u);
            y += 62 * u;
        }
        rf.set(r.centerX() - 200 * u, r.bottom - 130 * u, r.centerX() + 200 * u, r.bottom - 40 * u);
        button(c, rf, L.t("ปิด", "Close"), 0xFF6B5BD6, true, () -> popup = P_NONE);
    }

    void drawSettings(Canvas c) {
        RectF r = new RectF(80 * u, H / 2 - 340 * u, W - 80 * u, H / 2 + 340 * u);
        popupFrame(c, r, L.t("ตั้งค่า", "Settings"));
        text(c, "ภาษา / Language", r.centerX(), r.top + 210 * u, 42 * u, 0xFFFFFFFF);
        float bw = (r.width() - 180 * u) / 2f;
        rf.set(r.left + 60 * u, r.top + 260 * u, r.left + 60 * u + bw, r.top + 370 * u);
        button(c, rf, "ไทย", !L.en ? 0xFFFF8A2B : 0xFF3A3360, true, () -> { L.en = false; pf.save(); });
        rf.set(r.right - 60 * u - bw, r.top + 260 * u, r.right - 60 * u, r.top + 370 * u);
        button(c, rf, "English", L.en ? 0xFFFF8A2B : 0xFF3A3360, true, () -> { L.en = true; pf.save(); });
        plain(c, "Stella TD " + VERSION, r.centerX(), r.top + 450 * u, 30 * u, 0x99FFFFFF, Paint.Align.CENTER, 0);
        rf.set(r.centerX() - 200 * u, r.bottom - 130 * u, r.centerX() + 200 * u, r.bottom - 40 * u);
        button(c, rf, L.t("ปิด", "Close"), 0xFF6B5BD6, true, () -> popup = P_NONE);
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



    // ================================================================== match
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
            tp.setTypeface(titleFont);
            pill(c, W / 2, y, g.banner, 46 * u, g.bannerColor, 0xEE1A0F3D);
            tp.setTypeface(bodyFont);
        }
        if (!g.inWave && !g.won && !g.lost) {
            String s = (g.wave == 0 ? L.t("เวฟแรกเริ่มใน ", "First wave in ") : L.t("เวฟถัดไปใน ", "Next wave in ")) + (int) Math.ceil(g.prepT);
            plain(c, s, 40 * u, mapTop + 10 * u, 34 * u, 0xFFFFFFFF, Paint.Align.LEFT, 0);
            rf.set(W - 260 * u, mapTop - 30 * u, W - 30 * u, mapTop + 50 * u);
            button(c, rf, L.t("เริ่มเลย", "Start"), 0xFF33C6A6, true, g::skipPrep);
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
        p.setColor(0xFFFFFFFF);
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
        float range = Game.rangeOf(pl);
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
            p.setColor(0xFFFFFFFF);
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
        p.setColor(0xFFFFFFFF);
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
        if (e.mini) {
            sp.setPathEffect(null);
            sp.setStrokeWidth(6);
            sp.setColor(0xCCFF8A2B);
            c.drawCircle(e.x, e.y, e.radius + 14 + (float) Math.sin(anim * 6) * 4, sp);
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
        boolean big = e.boss || e.mini;
        float bw = big ? 160 : Math.max(50, e.radius * 2f), bh = big ? 14 : 9;
        float bx = e.x - bw / 2, by = e.y - e.radius - (big ? 40 : 20);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xCC140A2A);
        rf.set(bx - 3, by - 3, bx + bw + 3, by + bh + 3);
        c.drawRoundRect(rf, 6, 6, p);
        float frac = Math.max(0, e.hp / e.maxHp);
        p.setColor(e.boss ? 0xFFB86BFF : e.mini ? 0xFFFF8A2B : (frac > 0.5f ? 0xFF7CFF8A : 0xFFFF6B5B));
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
            p.setColor(0xFFFFFFFF);
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



    void drawHud(Canvas c, Game g) {
        float top = 50 * u, h = 100 * u;
        rf.set(30 * u, top, 140 * u, top + h);
        button(c, rf, "II", 0xFF6B5BD6, true, () -> paused = !paused);
        heart(c, 200 * u, top + h / 2, 30 * u);
        text(c, String.valueOf(Math.max(0, g.baseHp)), 245 * u, top + h / 2 + 18 * u, 52 * u, 0xFFFFFFFF, Paint.Align.LEFT, 0);
        text(c, L.t("เวฟ ", "WAVE ") + g.wave + "/" + g.maxWave, W / 2, top + h / 2 + 18 * u, 52 * u, 0xFFFFFFFF);
        star(c, W - 330 * u, top + h / 2, 34 * u);
        text(c, g.stars + " SP", W - 285 * u, top + h / 2 + 20 * u, 50 * u, 0xFFFFD23F, Paint.Align.LEFT, 140 * u);
        rf.set(W - 140 * u, top, W - 30 * u, top + h);
        button(c, rf, "x" + speed, speed > 1 ? 0xFFFF8A2B : 0xFF6B5BD6, true, () -> speed = speed % 3 + 1);
    }

    void drawBuild(Canvas c, Game g) {
        rf.set(-20 * u, panelTop, W + 20 * u, H + 40 * u);
        panel(c, new RectF(rf), 0xF0221449, 50 * u);
        float gap = 20 * u, cw = (W - 60 * u - gap * 3) / 4f, chh = 270 * u;
        float y0 = panelTop + 40 * u;
        if (buildSel >= 0)
            plain(c, L.t("แตะช่องว่างบนแมพเพื่อวาง", "Tap an empty slot to place"), W / 2, panelTop - 16 * u, 34 * u, 0xFFFFD23F, Paint.Align.CENTER, 0);
        for (int i = 0; i < Profile.DECK_SIZE; i++) {
            final int idx = i;
            final int type = g.deck[i];
            float x0 = 30 * u + i * (cw + gap);
            float lift = buildSel == i ? 14 * u : 0;
            RectF r = new RectF(x0, y0 - lift, x0 + cw, y0 + chh - lift);
            if (type < 0) {
                panel(c, r, 0xFF1A1430, 30 * u);
                text(c, L.t("ว่าง", "Empty"), r.centerX(), r.centerY() + 14 * u, 36 * u, 0xFF6A6488);
                continue;
            }
            int cost = Defs.PLACE_COST[type];
            boolean can = g.stars >= cost;
            int rc = Defs.RARITY_COLOR[Defs.P_RARITY[type]];
            panel(c, r, can ? 0xFF33296B : 0xFF2A2440, 30 * u);
            p.setStyle(Paint.Style.FILL);
            p.setColor(rc);
            rf.set(r.left + 16 * u, r.top + 10 * u, r.right - 16 * u, r.top + 20 * u);
            c.drawRoundRect(rf, 5 * u, 5 * u, p);
            if (buildSel == i) {
                sp.setPathEffect(null);
                sp.setStrokeWidth(8 * u);
                sp.setColor(0xFFFFD23F);
                c.drawRoundRect(r, 30 * u, 30 * u, sp);
            }
            float cx = r.centerX();
            sprite(c, pBmp[type], cx, r.top + 100 * u, 80 * u, can ? 255 : 120);
            if (pf.enchant[type] >= 0) badge(c, r.right - 34 * u, r.top + 50 * u, 18 * u, pf.enchant[type], 0);
            text(c, Defs.P_NAME[type], cx, r.top + 200 * u, 36 * u, 0xFFFFFFFF, Paint.Align.CENTER, cw - 20 * u);
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
        text(c, Defs.P_NAME[pl.type] + "  Lv " + pl.level, tx, y0 + 50 * u, 50 * u, 0xFFFFFFFF, Paint.Align.LEFT, W - tx - 260 * u);
        text(c, Defs.RARITY_NAME[rar], W - 40 * u, y0 + 50 * u, 36 * u, Defs.RARITY_COLOR[rar], Paint.Align.RIGHT, 0);
        String stat;
        if (Game.isProducer(pl)) {
            stat = String.format(Locale.US, L.t("ผลิต %d SP ทุก %.1f วิ", "Makes %d SP every %.1fs"), Game.produceAmount(pl), Game.cdOf(pl));
        } else if (pl.type == Defs.SUN) {
            stat = String.format(Locale.US, L.t("คลื่นความร้อน ทุก %.1f วิ", "Heat wave every %.1fs"), Game.cdOf(pl));
        } else {
            stat = String.format(Locale.US, L.t("ดาเมจ %.1f  ระยะ %d  ยิงทุก %.2f วิ", "DMG %.1f  Range %d  Every %.2fs"), Game.dmgOf(pl), (int) Game.rangeOf(pl), Game.cdOf(pl));
        }
        plain(c, stat, tx, y0 + 100 * u, 32 * u, 0xFFE6E0FF, Paint.Align.LEFT, W - tx - 40 * u);
        if (pl.enchant >= 0) {
            badge(c, tx + 20 * u, y0 + 140 * u, 20 * u, pl.enchant, 0);
            plain(c, enchName(pl.enchant, pl.tier), tx + 52 * u, y0 + 152 * u, 34 * u, Defs.RARITY_COLOR[Defs.EN_RARITY[pl.enchant]], Paint.Align.LEFT, 0);
        }
        if (pl.effect >= 0)
            plain(c, "Effect: " + Defs.EF_NAME[pl.effect], tx + 330 * u, y0 + 152 * u, 34 * u, Defs.EF_COLOR[pl.effect], Paint.Align.LEFT, 0);
        if (pl.buff > 0)
            plain(c, L.t("บัฟ Pluto +", "Pluto buff +") + (int) (pl.buff * 100) + "%", W - 40 * u, y0 + 100 * u, 28 * u, 0xFFFFB2D8, Paint.Align.RIGHT, 0);

        float by = H - 120 * u, bh = 95 * u, bw = (W - 80 * u - 40 * u) / 3f;
        boolean max = pl.level >= Defs.MAX_LEVEL;
        int uc = g.upgradeCost(pl);
        rf.set(40 * u, by - bh / 2, 40 * u + bw, by + bh / 2);
        button(c, rf, max ? "MAX" : L.t("อัปเกรด ", "Upgrade ") + uc, 0xFF33C6A6, !max && g.stars >= uc, () -> {
            String err = game.upgrade(pl);
            if (err != null) showToast(err);
        });
        rf.set(60 * u + bw, by - bh / 2, 60 * u + bw * 2, by + bh / 2);
        button(c, rf, L.t("ขาย +", "Sell +") + Math.max(1, pl.spent / 2), 0xFFFF6B5B, true, () -> {
            game.sell(pl);
            selPlanet = null;
        });
        rf.set(80 * u + bw * 2, by - bh / 2, 80 * u + bw * 3, by + bh / 2);
        button(c, rf, L.t("ปิด", "Close"), 0xFF6B5BD6, true, () -> selPlanet = null);
    }

    void dim(Canvas c) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xCC05010D);
        c.drawRect(0, 0, W, H, p);
        btns.clear(); // only overlay buttons are active
    }

    void drawPause(Canvas c) {
        dim(c);
        RectF r = new RectF(100 * u, H / 2 - 300 * u, W - 100 * u, H / 2 + 300 * u);
        panel(c, r, 0xFF2A1A5E, 50 * u);
        title(c, L.t("พักเกม", "Paused"), W / 2, r.top + 130 * u, 80 * u, 0xFFFFD23F);
        rf.set(r.left + 80 * u, r.top + 230 * u, r.right - 80 * u, r.top + 350 * u);
        button(c, rf, L.t("เล่นต่อ", "Resume"), 0xFF33C6A6, true, () -> paused = false);
        rf.set(r.left + 80 * u, r.top + 400 * u, r.right - 80 * u, r.top + 520 * u);
        button(c, rf, L.t("ออกจากด่าน", "Quit stage"), 0xFFFF6B5B, true, () -> {
            paused = false;
            game.lost = true; // count as a loss so earned rewards are kept
            saveResult();
            game = null;
            screen = S_PLAYMENU;
        });
    }

    void drawResult(Canvas c, Game g) {
        dim(c);
        RectF r = new RectF(80 * u, H / 2 - 450 * u, W - 80 * u, H / 2 + 450 * u);
        panel(c, r, 0xFF2A1A5E, 50 * u);
        if (g.won) {
            sprite(c, pBmp[Defs.SUN], W / 2, r.top + 130 * u, 110 * u, 255);
            title(c, L.t("ชนะแล้ว!", "Victory!"), W / 2, r.top + 300 * u, 96 * u, 0xFFFFD23F);
            plain(c, L.t("ปกป้องฐานได้ครบ ", "Defended all ") + g.maxWave + L.t(" เวฟ", " waves"), W / 2, r.top + 370 * u, 40 * u, 0xFFFFFFFF, Paint.Align.CENTER, 0);
        } else {
            blackHole(c, W / 2, r.top + 140 * u, 80 * u);
            title(c, L.t("ฐานถูกทำลาย", "Base destroyed"), W / 2, r.top + 300 * u, 86 * u, 0xFFFF6B6B, Paint.Align.CENTER, r.width() - 60 * u);
            plain(c, L.t("ไปได้ถึงเวฟ ", "Reached wave ") + g.wave + " / " + g.maxWave, W / 2, r.top + 370 * u, 40 * u, 0xFFFFFFFF, Paint.Align.CENTER, 0);
        }
        text(c, L.t("รางวัล", "Rewards"), W / 2, r.top + 460 * u, 44 * u, 0xFFFFD23F);
        star(c, W / 2 - 230 * u, r.top + 520 * u, 26 * u);
        text(c, "+" + g.earnStars, W / 2 - 190 * u, r.top + 536 * u, 46 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
        milliteIcon(c, W / 2 + 90 * u, r.top + 520 * u, 26 * u);
        text(c, "+" + g.earnMillite, W / 2 + 130 * u, r.top + 536 * u, 46 * u, 0xFFE2C6FF, Paint.Align.LEFT, 0);
        final int st = g.stage;
        rf.set(r.left + 70 * u, r.top + 610 * u, r.right - 70 * u, r.top + 720 * u);
        button(c, rf, L.t("เล่นอีกครั้ง", "Play again"), 0xFFFF8A2B, true, () -> startStage(st));
        rf.set(r.left + 70 * u, r.top + 760 * u, r.right - 70 * u, r.top + 870 * u);
        button(c, rf, L.t("กลับล็อบบี้", "Back to lobby"), 0xFF6B5BD6, true, () -> { game = null; screen = S_LOBBY; });
    }
}
