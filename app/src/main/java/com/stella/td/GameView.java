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

    static final String VERSION = "v2.4";

    // hub screens
    static final int S_LOBBY = 0, S_SHOP = 1, S_PLANET = 2, S_PLAYMENU = 3, S_BANNER = 4, S_ENCHANT = 5, S_GAME = 6,
            S_LOGIN = 7, S_ADMIN = 8, S_LEADER = 9;
    // popups
    static final int P_NONE = 0, P_PLAYER = 1, P_SETTINGS = 2, P_GACHA = 3, P_INDEX = 4, P_AUTO = 5, P_GIVE = 6;

    static final class Btn {
        final RectF r;
        final Runnable a;
        Btn(RectF r, Runnable a) { this.r = r; this.a = a; }
    }

    final Context ctx;
    final Accounts acc;
    Profile pf;
    String user;
    String adminSel;
    int adminPage;
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
    float enchFlash, playAcc, saveAcc;
    int labTab, leaderTab;

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
        this.ctx = ctx;
        acc = new Accounts(ctx);
        L.en = acc.lang();
        // Game font: Kanit (SIL Open Font License). Falls back to the system font if the files are missing.
        try { bodyFont = Typeface.createFromAsset(ctx.getAssets(), "fonts/Kanit-SemiBold.ttf"); } catch (RuntimeException ignored) { }
        try { titleFont = Typeface.createFromAsset(ctx.getAssets(), "fonts/Kanit-BlackItalic.ttf"); } catch (RuntimeException ignored) { }
        tp.setTypeface(bodyFont);
        tp.setStrokeJoin(Paint.Join.ROUND);
        Dialogs.body = bodyFont;
        Dialogs.title = titleFont;
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
        String cur = acc.current();
        if (cur != null) loginAs(cur);
        else screen = S_LOGIN;
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

    // ================================================================== accounts
    void loginAs(String u) {
        user = u;
        pf = new Profile(acc.prefsOf(u));
        selType = enchSel = pf.deck[0] >= 0 ? pf.deck[0] : Defs.EARTH;
        game = null;
        popup = P_NONE;
        screen = S_LOBBY;
        if (acc.mustChange(u)) post(this::askChangePassword);
    }

    boolean isAdmin() {
        return user != null && acc.isAdmin(user);
    }

    void askLogin(String prefillUser) {
        Dialogs.ask(ctx, L.t("เข้าสู่ระบบ", "Log in"), L.t("ยินดีต้อนรับกลับสู่ Stella TD", "Welcome back to Stella TD"),
                new String[]{L.t("ชื่อไอดี", "Username"), L.t("รหัสผ่าน", "Password")},
                new int[]{Dialogs.TEXT, Dialogs.PASSWORD}, new String[]{prefillUser, null},
                L.t("เข้าสู่ระบบ", "Log in"), v -> {
                    String err = acc.login(v[0], v[1]);
                    if (err != null) return err;
                    loginAs(Accounts.norm(v[0]));
                    showToast(L.t("ยินดีต้อนรับ ", "Welcome ") + acc.display(user) + "!");
                    return null;
                });
    }

    void askRegister() {
        Dialogs.ask(ctx, L.t("สร้างไอดีใหม่", "Create account"), L.t("ชื่อไอดีใช้ a-z, 0-9, _ (3–16 ตัว)", "Username: a-z, 0-9, _ (3–16 chars)"),
                new String[]{L.t("ชื่อไอดี", "Username"), L.t("รหัสผ่าน", "Password"), L.t("ยืนยันรหัสผ่าน", "Confirm password")},
                new int[]{Dialogs.TEXT, Dialogs.PASSWORD, Dialogs.PASSWORD}, null,
                L.t("สร้างไอดี", "Create"), v -> {
                    String err = acc.register(v[0], v[1], v[2]);
                    if (err != null) return err;
                    acc.login(v[0], v[1]);
                    loginAs(Accounts.norm(v[0]));
                    showToast(L.t("สร้างไอดีสำเร็จ!", "Account created!"));
                    return null;
                });
    }

    void askChangePassword() {
        String msg = acc.mustChange(user)
                ? L.t("บัญชีแอดมินยังใช้รหัสเริ่มต้นอยู่ กรุณาตั้งรหัสใหม่", "The admin account still uses the default password. Please set a new one.")
                : null;
        Dialogs.ask(ctx, L.t("เปลี่ยนรหัสผ่าน", "Change password"), msg,
                new String[]{L.t("รหัสผ่านเดิม", "Current password"), L.t("รหัสผ่านใหม่", "New password"), L.t("ยืนยันรหัสผ่านใหม่", "Confirm new password")},
                new int[]{Dialogs.PASSWORD, Dialogs.PASSWORD, Dialogs.PASSWORD}, null,
                L.t("บันทึก", "Save"), v -> {
                    String err = acc.changePassword(user, v[0], v[1], v[2]);
                    if (err != null) return err;
                    showToast(L.t("เปลี่ยนรหัสผ่านแล้ว", "Password changed"));
                    return null;
                });
    }

    void logout() {
        acc.logout();
        user = null;
        pf = null;
        game = null;
        popup = P_NONE;
        screen = S_LOGIN;
    }

    void pauseGame() {
        if (pf != null) pf.save();
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
            case S_LOGIN:
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
        game.infiniteSP = isAdmin() && acc.infiniteSP();
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
        if (pf != null) {
            playAcc += dt;
            if (playAcc >= 1f) { int s = (int) playAcc; pf.playSec += s; playAcc -= s; }
            saveAcc += dt;
            if (saveAcc > 15f) { saveAcc = 0; pf.save(); }
        }
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
            case S_LOGIN: drawLogin(c); break;
            case S_ADMIN: drawAdmin(c); break;
            case S_LEADER: drawLeader(c); break;
            default:
                if (game != null) drawPlay(c);
                else screen = S_PLAYMENU;
                break;
        }
        if (screen != S_GAME && screen != S_LOGIN && screen != S_ADMIN && screen != S_LEADER) drawNav(c);
        switch (popup) {
            case P_PLAYER: drawPlayerInfo(c); break;
            case P_SETTINGS: drawSettings(c); break;
            case P_GACHA: drawGachaResult(c); break;
            case P_INDEX: drawIndex(c); break;
            case P_AUTO: drawAutoPick(c); break;
            case P_GIVE: drawGivePlanet(c); break;
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
    static int mix(int a, int b, float t) {
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg2 = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg2 - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    static int lighten(int c, float t) { return (c & 0xFF000000) | (mix(c, 0xFFFFFFFF, t) & 0x00FFFFFF); }

    static int darken(int c, float t) { return (c & 0xFF000000) | (mix(c, 0xFF000000, t) & 0x00FFFFFF); }

    static int alpha(int c, int a) { return (c & 0x00FFFFFF) | (a << 24); }

    void glowCircle(Canvas c, float x, float y, float r, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new RadialGradient(x, y, r, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
        p.setShader(null);
    }

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
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        rf2.set(r);
        rf2.offset(0, 10 * u);
        p.setColor(0x70000000);
        c.drawRoundRect(rf2, rad, rad, p);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(0, r.top, 0, r.bottom, lighten(color, 0.10f), darken(color, 0.22f), Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, p);
        p.setShader(null);
        sp.setPathEffect(null);
        sp.setStrokeWidth(4 * u);
        sp.setColor(0xFF0B0520);
        c.drawRoundRect(r, rad, rad, sp);
        sp.setStrokeWidth(2 * u);
        sp.setColor(alpha(lighten(color, 0.55f), 0x70));
        rf2.set(r.left + 4 * u, r.top + 4 * u, r.right - 4 * u, r.bottom - 4 * u);
        c.drawRoundRect(rf2, Math.max(0, rad - 4 * u), Math.max(0, rad - 4 * u), sp);
    }

    void button(Canvas c, RectF r, String label, int color, boolean enabled, Runnable a) {
        float rad = Math.min(r.height() / 2f, 34 * u);
        int base = enabled ? color : 0xFF4A4562;
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        rf2.set(r);
        rf2.offset(0, 11 * u);
        p.setColor(0x66000000);
        c.drawRoundRect(rf2, rad, rad, p);
        rf2.set(r);
        rf2.offset(0, 7 * u);
        p.setColor(darken(base, 0.5f));
        c.drawRoundRect(rf2, rad, rad, p);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(0, r.top, 0, r.bottom, lighten(base, 0.25f), darken(base, 0.12f), Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, p);
        rf2.set(r.left + 7 * u, r.top + 5 * u, r.right - 7 * u, r.top + r.height() * 0.5f);
        p.setShader(new LinearGradient(0, rf2.top, 0, rf2.bottom, 0x60FFFFFF, 0x05FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(rf2, rad * 0.8f, rad * 0.8f, p);
        p.setShader(null);
        sp.setPathEffect(null);
        sp.setStrokeWidth(2.5f * u);
        sp.setColor(0x50FFFFFF);
        rf2.set(r.left + 3.5f * u, r.top + 3.5f * u, r.right - 3.5f * u, r.bottom - 3.5f * u);
        c.drawRoundRect(rf2, Math.max(0, rad - 3.5f * u), Math.max(0, rad - 3.5f * u), sp);
        sp.setStrokeWidth(4 * u);
        sp.setColor(0xFF0B0520);
        c.drawRoundRect(r, rad, rad, sp);
        if (label != null) {
            float ts = Math.min(r.height() * 0.42f, 50 * u);
            text(c, label, r.centerX(), r.centerY() + ts * 0.36f, ts, enabled ? 0xFFFFFFFF : 0xFFB8B2CC, Paint.Align.CENTER, r.width() - 28 * u);
        }
        if (a != null) btns.add(new Btn(new RectF(r), a));
    }

    /** card framed and tinted by rarity colour */
    void rarityCard(Canvas c, RectF r, int rarityColor, boolean bright, boolean selected, float rad) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        rf2.set(r);
        rf2.offset(0, 8 * u);
        p.setColor(0x66000000);
        c.drawRoundRect(rf2, rad, rad, p);
        int inner = bright ? darken(rarityColor, 0.55f) : 0xFF16102C;
        p.setColor(0xFFFFFFFF);
        p.setShader(new RadialGradient(r.centerX(), r.top + r.height() * 0.35f, Math.max(r.width(), r.height()) * 0.8f,
                bright ? darken(rarityColor, 0.25f) : 0xFF231A44, darken(inner, 0.4f), Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, p);
        p.setShader(new LinearGradient(0, r.top, 0, r.top + r.height() * 0.45f, 0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        rf2.set(r.left + 4 * u, r.top + 4 * u, r.right - 4 * u, r.top + r.height() * 0.45f);
        c.drawRoundRect(rf2, rad * 0.8f, rad * 0.8f, p);
        p.setShader(null);
        sp.setPathEffect(null);
        sp.setStrokeWidth(4 * u);
        sp.setColor(0xFF0B0520);
        c.drawRoundRect(r, rad, rad, sp);
        sp.setStrokeWidth(3 * u);
        sp.setColor(bright ? rarityColor : 0x55FFFFFF);
        rf2.set(r.left + 4 * u, r.top + 4 * u, r.right - 4 * u, r.bottom - 4 * u);
        c.drawRoundRect(rf2, Math.max(0, rad - 4 * u), Math.max(0, rad - 4 * u), sp);
        if (selected) {
            sp.setStrokeWidth(10 * u);
            sp.setColor(alpha(0xFFFFD23F, (int) (120 + 80 * Math.sin(anim * 5))));
            rf2.set(r.left - 5 * u, r.top - 5 * u, r.right + 5 * u, r.bottom + 5 * u);
            c.drawRoundRect(rf2, rad + 5 * u, rad + 5 * u, sp);
            sp.setStrokeWidth(4 * u);
            sp.setColor(0xFFFFD23F);
            c.drawRoundRect(r, rad, rad, sp);
        }
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

    void crystalIcon(Canvas c, float x, float y, float r) {
        float[][] shards = {{0, 0, 1f}, {-0.45f, 0.2f, 0.65f}, {0.45f, 0.25f, 0.6f}};
        for (float[] s : shards) {
            float sx = x + s[0] * r, sy = y + s[1] * r, h = r * s[2], w = h * 0.38f;
            Path d = new Path();
            d.moveTo(sx, sy - h);
            d.lineTo(sx + w, sy - h * 0.45f);
            d.lineTo(sx + w, sy + h * 0.55f);
            d.lineTo(sx, sy + h * 0.8f);
            d.lineTo(sx - w, sy + h * 0.55f);
            d.lineTo(sx - w, sy - h * 0.45f);
            d.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            p.setShader(new LinearGradient(sx - w, sy - h, sx + w, sy + h, 0xFFD8FFFF, 0xFF2F8FD8, Shader.TileMode.CLAMP));
            c.drawPath(d, p);
            p.setShader(null);
            sp.setPathEffect(null);
            sp.setStrokeWidth(r * 0.1f);
            sp.setColor(0xFF0B0520);
            c.drawPath(d, sp);
        }
    }

    void chip(Canvas c, float cx, float cy, int kind, int value) {
        String s = String.valueOf(value);
        tp.setTextSize(40 * u);
        float w = tp.measureText(s) + 120 * u;
        rf.set(cx - w / 2, cy - 36 * u, cx + w / 2, cy + 36 * u);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, 0xEE2A1A5E, 0xEE0E0828, Shader.TileMode.CLAMP));
        c.drawRoundRect(rf, 36 * u, 36 * u, p);
        p.setShader(null);
        sp.setPathEffect(null);
        sp.setStrokeWidth(3 * u);
        sp.setColor(kind == 1 ? 0x99C58CFF : kind == 2 ? 0x995EF2FF : 0x99FFD23F);
        c.drawRoundRect(rf, 36 * u, 36 * u, sp);
        float ix = rf.left + 38 * u;
        glowCircle(c, ix, cy, 40 * u, kind == 1 ? 0x77C58CFF : kind == 2 ? 0x775EF2FF : 0x77FFD23F);
        if (kind == 0) star(c, ix, cy, 24 * u);
        else if (kind == 1) milliteIcon(c, ix, cy, 24 * u);
        else crystalIcon(c, ix, cy, 26 * u);
        text(c, s, ix + 36 * u, cy + 15 * u, 40 * u, kind == 1 ? 0xFFE2C6FF : kind == 2 ? 0xFFB8F6FF : 0xFFFFE27A, Paint.Align.LEFT, 0);
    }

    void header(Canvas c, String title) {
        header(c, title, 0, pf.stars, 1, pf.millite);
    }

    void header(Canvas c, String title, int k1, int v1, int k2, int v2) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(0, 0, 0, 200 * u, 0xE6090420, 0x00090420, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, 200 * u, p);
        p.setShader(null);
        rf.set(30 * u, 50 * u, 150 * u, 150 * u);
        button(c, rf, null, 0xFF6B5BD6, true, () -> go(S_LOBBY));
        sp.setPathEffect(null);
        sp.setStrokeWidth(11 * u);
        sp.setColor(0xFFFFFFFF);
        float bx = rf.centerX(), by = rf.centerY();
        c.drawLine(bx + 10 * u, by - 22 * u, bx - 14 * u, by, sp);
        c.drawLine(bx - 14 * u, by, bx + 10 * u, by + 22 * u, sp);
        title(c, title, 180 * u, 125 * u, 60 * u, 0xFFFFD23F, Paint.Align.LEFT, 330 * u);
        chip(c, W - 330 * u, 100 * u, k1, v1);
        chip(c, W - 120 * u, 100 * u, k2, v2);
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
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(0, navTop, 0, H, 0xF21C0F45, 0xF2080320, Shader.TileMode.CLAMP));
        c.drawRect(0, navTop, W, H, p);
        p.setShader(new LinearGradient(0, navTop, W, navTop, new int[]{0x00B86BFF, 0xFFB86BFF, 0xFF4FE0FF, 0x004FE0FF},
                new float[]{0f, 0.3f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, navTop, W, navTop + 4 * u, p);
        p.setShader(null);
        String[] labels = {L.t("ร้านค้า", "Shop"), L.t("ดวงดาว", "Planet"), L.t("เล่น", "Play"),
                L.t("แบนเนอร์", "Banner"), L.t("แล็บ", "Lab")};
        int[] scr = {S_SHOP, S_PLANET, S_PLAYMENU, S_BANNER, S_ENCHANT};
        float cw = W / 5f;
        for (int i = 0; i < 5; i++) {
            final int target = scr[i];
            float cx = cw * i + cw / 2;
            boolean act = screen == target;
            float iy = navTop + 80 * u;
            if (i == 2) {
                float r = 92 * u, cy = navTop + 36 * u;
                glowCircle(c, cx, cy, r * 1.6f, act ? 0x88FFB627 : 0x55FF8A2B);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF0B0520);
                c.drawCircle(cx, cy + 9 * u, r + 4 * u, p);
                p.setColor(0xFFFFFFFF);
                p.setShader(new LinearGradient(0, cy - r, 0, cy + r, 0xFFFFC46A, 0xFFE0560F, Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, r, p);
                p.setShader(new LinearGradient(0, cy - r, 0, cy, 0x70FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
                c.drawOval(new RectF(cx - r * 0.75f, cy - r * 0.92f, cx + r * 0.75f, cy - r * 0.05f), p);
                p.setShader(null);
                sp.setPathEffect(null);
                sp.setStrokeWidth(5 * u);
                sp.setColor(0xFF0B0520);
                c.drawCircle(cx, cy, r, sp);
                sp.setStrokeWidth(3 * u);
                sp.setColor(0x88FFF0C8);
                c.drawCircle(cx, cy, r - 6 * u, sp);
                Path tri = new Path();
                tri.moveTo(cx - 24 * u, cy - 36 * u);
                tri.lineTo(cx + 40 * u, cy);
                tri.lineTo(cx - 24 * u, cy + 36 * u);
                tri.close();
                p.setColor(0xFFFFFFFF);
                c.drawPath(tri, p);
                title(c, labels[i], cx, navTop + 178 * u, 40 * u, act ? 0xFFFFD23F : 0xFFFFFFFF);
                btns.add(new Btn(new RectF(cx - cw / 2, navTop - 60 * u, cx + cw / 2, H), () -> go(target)));
                continue;
            }
            if (act) {
                rf.set(cx - cw / 2 + 12 * u, navTop + 16 * u, cx + cw / 2 - 12 * u, H - 16 * u);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFFFFF);
                p.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, 0x66FFD23F, 0x10FFD23F, Shader.TileMode.CLAMP));
                c.drawRoundRect(rf, 30 * u, 30 * u, p);
                p.setShader(null);
                sp.setPathEffect(null);
                sp.setStrokeWidth(3 * u);
                sp.setColor(0x99FFD23F);
                c.drawRoundRect(rf, 30 * u, 30 * u, sp);
            }
            glowCircle(c, cx, iy, 70 * u, act ? 0x55FFFFFF : 0x22FFFFFF);
            switch (i) {
                case 0: { // shop bag
                    rf.set(cx - 38 * u, iy - 24 * u, cx + 38 * u, iy + 42 * u);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xFFFFFFFF);
                    p.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, 0xFF5BE8C8, 0xFF1A8C78, Shader.TileMode.CLAMP));
                    c.drawRoundRect(rf, 12 * u, 12 * u, p);
                    p.setShader(null);
                    sp.setPathEffect(null);
                    sp.setStrokeWidth(5 * u);
                    sp.setColor(0xFF0B0520);
                    c.drawRoundRect(rf, 12 * u, 12 * u, sp);
                    sp.setColor(0xFFB8FFF0);
                    rf.set(cx - 20 * u, iy - 48 * u, cx + 20 * u, iy - 6 * u);
                    c.drawArc(rf, 180, 180, false, sp);
                    star(c, cx, iy + 10 * u, 14 * u);
                    break;
                }
                case 1: sprite(c, pBmp[Defs.SATURN], cx, iy, 64 * u, 255); break;
                case 3: {
                    rf.set(cx - 32 * u, iy - 46 * u, cx + 32 * u, iy + 44 * u);
                    Path f = new Path();
                    f.moveTo(rf.left, rf.top);
                    f.lineTo(rf.right, rf.top);
                    f.lineTo(rf.right, rf.bottom);
                    f.lineTo(cx, rf.bottom - 20 * u);
                    f.lineTo(rf.left, rf.bottom);
                    f.close();
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xFFFFFFFF);
                    p.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, 0xFFD89BFF, 0xFF7A2FD0, Shader.TileMode.CLAMP));
                    c.drawPath(f, p);
                    p.setShader(null);
                    sp.setStrokeWidth(5 * u);
                    sp.setColor(0xFF0B0520);
                    c.drawPath(f, sp);
                    star(c, cx, iy - 8 * u, 20 * u);
                    break;
                }
                case 4: flask(c, cx, iy, 44 * u); break;
                default: break;
            }
            text(c, labels[i], cx, navTop + 178 * u, 34 * u, act ? 0xFFFFD23F : 0xFFD8D0F0, Paint.Align.CENTER, cw - 10 * u);
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
        rf.set(170 * u, 50 * u, 290 * u, 170 * u);
        button(c, rf, null, 0xFFE0A020, true, () -> { leaderTab = 0; screen = S_LEADER; });
        trophy(c, rf.centerX(), rf.centerY() - 6 * u, 40 * u);
        if (isAdmin()) {
            rf.set(W - 290 * u, 50 * u, W - 170 * u, 170 * u);
            button(c, rf, null, 0xFFD0407A, true, () -> { adminSel = user; screen = S_ADMIN; });
            shield(c, rf.centerX(), rf.centerY() - 6 * u, 42 * u);
        }
        chip(c, W / 2 - 110 * u, 110 * u, 0, pf.stars);
        chip(c, W / 2 + 125 * u, 110 * u, 1, pf.millite);

        float ty = 370 * u;
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
        float cw = (W - 140 * u) / 2f, ch = 360 * u, y0 = 230 * u;
        String[] names = {L.t("แพ็กเริ่มต้น", "Starter pack"), L.t("สกินดวงดาว", "Planet skins"), L.t("Star Pass", "Star Pass"), L.t("ข้อเสนอรายวัน", "Daily deals")};
        for (int i = 0; i < 4; i++) {
            float x = 50 * u + (i % 2) * (cw + 40 * u), y = y0 + (i / 2) * (ch + 40 * u);
            RectF q = new RectF(x, y, x + cw, y + ch);
            rarityCard(c, q, Defs.RARITY_COLOR[i + 1], false, false, 36 * u);
            glowCircle(c, q.centerX(), q.top + ch * 0.4f, 110 * u, alpha(Defs.RARITY_COLOR[i + 1], 0x44));
            title(c, "?", q.centerX(), q.top + ch * 0.5f, 120 * u, 0xFF6A6488);
            text(c, names[i], q.centerX(), q.bottom - 90 * u, 38 * u, 0xFFE6E0FF, Paint.Align.CENTER, cw - 30 * u);
            plain(c, L.t("เร็ว ๆ นี้", "Coming soon"), q.centerX(), q.bottom - 40 * u, 30 * u, 0xFF8A84A0, Paint.Align.CENTER, 0);
        }
        plain(c, L.t("Crystal ได้จากการล้มบอสเท่านั้น (มินิบอส 1 • บอส 2)", "Crystals drop only from bosses (mini boss 1 • boss 2)"),
                W / 2, y0 + 2 * (ch + 40 * u) + 40 * u, 32 * u, 0xFFB8F6FF, Paint.Align.CENTER, W - 80 * u);
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
            rarityCard(c, r, rc, pf.owned[i], i == t, 22 * u);
            sprite(c, pBmp[i], r.centerX(), r.centerY() - 6 * u, cell * 0.42f, pf.owned[i] ? 255 : 45);
            if (pf.owned[i]) {
                text(c, "Lv" + pf.level[i], r.centerX(), r.bottom - 14 * u, 26 * u, 0xFFFFFFFF);
                if (pf.inDeck(i)) {
                    p.setColor(0xFF33C6A6);
                    c.drawCircle(r.right - 18 * u, r.top + 18 * u, 13 * u, p);
                }
            } else text(c, "?", r.centerX(), r.centerY() + 18 * u, 56 * u, 0xFF6A6488);
            btns.add(new Btn(r, () -> selType = ti));
        }
        // deck row
        float dTop = invTop - 210 * u, ds = 130 * u;
        text(c, "Deck", 50 * u, dTop + ds / 2 + 14 * u, 40 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
        for (int i = 0; i < Profile.DECK_SIZE; i++) {
            final int d = pf.deck[i];
            float x = 190 * u + i * (ds + 24 * u);
            RectF r = new RectF(x, dTop, x + ds, dTop + ds);
            rarityCard(c, r, d >= 0 ? Defs.RARITY_COLOR[Defs.P_RARITY[d]] : 0xFF6A6488, d >= 0, false, 24 * u);
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
        button(c, rf, L.t("ไปที่ Lab", "Open Lab"), 0xFFB86BFF, own, () -> { enchSel = selType; go(S_ENCHANT); });
        // status block
        float sTop = b1 - 260 * u;
        int rar = Defs.P_RARITY[t];
        text(c, L.t("สถานะ:", "Status:"), 50 * u, sTop + 40 * u, 38 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
        text(c, L.t("ระดับ: ", "Rarity: ") + Defs.RARITY_NAME[rar], W - 50 * u, sTop + 40 * u, 38 * u, Defs.RARITY_COLOR[rar], Paint.Align.RIGHT, 0);
        Planet tmpP = previewPlanet(t);
        String s1;
        if (Game.isProducer(tmpP)) s1 = String.format(Locale.US, L.t("ผลิต %.1f SP ทุก %.1f วิ", "Makes %.1f SP every %.1fs"), Game.produceRate(tmpP), Game.cdOf(tmpP));
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
        if (pf.material[t] >= 0) {
            materialBadge(c, W - 70 * u, sTop + 192 * u, 22 * u, pf.material[t]);
            plain(c, matName(pf.material[t]), W - 105 * u, sTop + 204 * u, 32 * u, Defs.MAT_COLOR[pf.material[t]], Paint.Align.RIGHT, 0);
        } else plain(c, L.t("Material: ยังไม่มี", "Material: none"), W - 50 * u, sTop + 204 * u, 32 * u, 0xFF8A84A0, Paint.Align.RIGHT, 0);
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
        q.material = pf.material[t];
        q.moons = q.enchant == Defs.EN_MOON ? 1 : 0;
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
        boolean hasMat = own && pf.material[t] >= 0;
        if (hasMat) materialAura(c, x, y, half * 0.72f, pf.material[t]);
        if (own && enchFlash > 0) {
            sp.setPathEffect(null);
            sp.setStrokeWidth(8 * u);
            sp.setColor(0xFFFFFFFF);
            c.drawCircle(x, y, half * 0.8f + enchFlash * 60 * u, sp);
        }
        float bob = (float) Math.sin(anim * 2) * 8 * u;
        sprite(c, pBmp[t], x, y + bob, half, own ? 255 : 60);
        if (hasMat) materialBadge(c, x - half * 0.62f, y - half * 0.55f + bob, half * 0.2f, pf.material[t]);
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
        float gx = art.centerX(), gy = art.centerY(), gr = Math.min(art.width(), art.height()) * 0.5f;
        glowCircle(c, gx, gy, gr * 1.1f, 0x88A43CFF);
        glowCircle(c, gx, gy, gr * 0.55f, 0x99FFB8E0);
        for (int arm = 0; arm < 3; arm++) {
            for (int k = 0; k < 140; k++) {
                float f = k / 140f;
                double a = anim * 0.25 + arm * 2.094 + f * 4.4 + Math.sin(k * 12.9898) * 0.08;
                float rr = gr * (0.06f + f * 0.94f) * (1f + (float) Math.sin(k * 78.233) * 0.06f);
                float x = gx + (float) Math.cos(a) * rr, y = gy + (float) Math.sin(a) * rr * 0.5f;
                int col = k % 4 == 0 ? 0xFFFFE6A8 : (arm == 1 ? 0xFF8FD6FF : 0xFFE6B8FF);
                if (k % 9 == 0) glowCircle(c, x, y, (14 - f * 8) * u, alpha(col, 0x66));
                p.setStyle(Paint.Style.FILL);
                p.setShader(null);
                p.setColor(alpha(col, (int) (240 * (1 - f * 0.6f))));
                c.drawCircle(x, y, (4.2f - f * 2.4f) * u, p);
            }
        }
        glowCircle(c, gx, gy, 60 * u, 0xEEFFF4D0);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(gx, gy, 12 * u, p);
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
        boolean mat = labTab == 1;
        if (mat ? pf.crystals < Profile.CRYSTAL_COST : pf.millite < Profile.ROLL_COST) {
            autoOn = false;
            showToast(mat ? L.t("Crystal หมดแล้ว", "Out of Crystal") : L.t("Millite หมดแล้ว", "Out of Millite"));
            return;
        }
        if (mat) pf.rollMaterial(enchSel);
        else pf.rollEnchant(enchSel);
        autoCount++;
        enchFlash = 0.15f;
        boolean hit = mat ? pf.material[enchSel] == autoTarget
                : pf.enchant[enchSel] == autoTarget && pf.tier[enchSel] == Defs.EN_TIERS[autoTarget];
        if (hit) {
            autoOn = false;
            enchFlash = 0.8f;
            String got = mat ? matName(autoTarget) : enchName(pf.enchant[enchSel], pf.tier[enchSel]);
            showToast(L.t("ได้แล้ว! ", "Got it! ") + got + L.t(" (สุ่ม ", " (") + autoCount + L.t(" ครั้ง)", " rolls)"));
        }
    }

    void drawEnchant(Canvas c) {
        boolean mat = labTab == 1;
        header(c, "Lab", 1, pf.millite, 2, pf.crystals);
        if (!pf.owned[enchSel]) enchSel = Defs.EARTH;
        // tabs
        float tw = (W - 120 * u) / 2f;
        rf.set(50 * u, 190 * u, 50 * u + tw, 285 * u);
        button(c, rf, L.t("โรงงาน • Enchant", "Factory • Enchant"), !mat ? 0xFFB86BFF : 0xFF3A3360, true, () -> { if (!autoOn) labTab = 0; });
        rf.set(70 * u + tw, 190 * u, 70 * u + tw * 2, 285 * u);
        button(c, rf, L.t("แล็บ • Material", "Lab • Material"), mat ? 0xFF33A8D0 : 0xFF3A3360, true, () -> { if (!autoOn) labTab = 1; });
        // currency + buttons anchored to bottom
        float mY = navTop - 50 * u;
        if (mat) {
            crystalIcon(c, W / 2 - 150 * u, mY - 14 * u, 28 * u);
            text(c, "Crystal: " + pf.crystals, W / 2 - 110 * u, mY, 44 * u, 0xFFB8F6FF, Paint.Align.LEFT, 0);
        } else {
            milliteIcon(c, W / 2 - 150 * u, mY - 14 * u, 26 * u);
            text(c, "Millite: " + pf.millite, W / 2 - 110 * u, mY, 44 * u, 0xFFE2C6FF, Paint.Align.LEFT, 0);
        }
        float bh = 110 * u, bw = (W - 140 * u) / 3f, by = mY - 70 * u - bh;
        boolean can = mat ? pf.crystals >= Profile.CRYSTAL_COST : pf.millite >= Profile.ROLL_COST;
        rf.set(50 * u, by, 50 * u + bw, by + bh);
        button(c, rf, L.t("สุ่ม", "Roll") + " (1)", mat ? 0xFF33A8D0 : 0xFFB86BFF, !autoOn && can, () -> {
            boolean ok = labTab == 1 ? pf.rollMaterial(enchSel) : pf.rollEnchant(enchSel);
            if (ok) enchFlash = 0.5f;
            else showToast(labTab == 1 ? L.t("Crystal ไม่พอ (ได้จากบอส)", "Not enough Crystal (from bosses)")
                    : L.t("Millite ไม่พอ (ได้จากบอส)", "Not enough Millite (from bosses)"));
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
            rarityCard(c, r, Defs.RARITY_COLOR[Defs.P_RARITY[t]], true, t == enchSel, 22 * u);
            sprite(c, pBmp[t], r.centerX(), r.centerY(), cell * 0.4f, 255);
            if (!mat && pf.enchant[t] >= 0) badge(c, r.right - 22 * u, r.top + 22 * u, 16 * u, pf.enchant[t], 0);
            if (mat && pf.material[t] >= 0) materialBadge(c, r.right - 22 * u, r.top + 22 * u, 16 * u, pf.material[t]);
            btns.add(new Btn(r, () -> { if (!autoOn) enchSel = tt; }));
            k++;
        }
        // machine / lab picture with the selected planet
        float aTop = 310 * u, aBot = pTop - 70 * u;
        float half = Math.min(270 * u, (aBot - aTop - 300 * u) * 0.42f);
        float cy = aTop + (aBot - aTop - 230 * u) / 2f;
        labMachine(c, W / 2, cy, half, mat);
        bigPlanet(c, enchSel, W / 2, cy, half, true);
        float ny = cy + half * 1.4f;
        text(c, Defs.P_NAME[enchSel], W / 2, ny + 60 * u, 50 * u, 0xFFFFFFFF);
        float sc = 1f + enchFlash * 0.4f;
        if (!mat) {
            int e = pf.enchant[enchSel];
            if (e >= 0) {
                int rc = Defs.RARITY_COLOR[Defs.EN_RARITY[e]];
                title(c, enchName(e, pf.tier[enchSel]), W / 2, ny + 125 * u, 54 * u * sc, e == Defs.EN_GALAXIA ? hsv(anim * 90) : rc);
                plain(c, Defs.RARITY_NAME[Defs.EN_RARITY[e]] + "  •  " + (L.en ? Defs.EN_DESC_EN[e] : Defs.EN_DESC[e]),
                        W / 2, ny + 175 * u, 28 * u, 0xFFC9C2E6, Paint.Align.CENTER, W - 80 * u);
            } else plain(c, L.t("ยังไม่มี Enchant — กดสุ่มเลย!", "No enchant yet — roll one!"), W / 2, ny + 125 * u, 36 * u, 0xFF8A84A0, Paint.Align.CENTER, 0);
        } else {
            int m = pf.material[enchSel];
            if (m >= 0) {
                title(c, matName(m), W / 2, ny + 125 * u, 54 * u * sc, Defs.MAT_COLOR[m]);
                plain(c, Defs.RARITY_NAME[Defs.MAT_RARITY[m]] + "  •  " + (L.en ? Defs.MAT_DESC_EN[m] : Defs.MAT_DESC[m]),
                        W / 2, ny + 175 * u, 28 * u, 0xFFC9C2E6, Paint.Align.CENTER, W - 80 * u);
            } else plain(c, L.t("ยังไม่มี Material — ใช้ Crystal สุ่มเลย!", "No material yet — use a Crystal!"), W / 2, ny + 125 * u, 36 * u, 0xFF8A84A0, Paint.Align.CENTER, 0);
        }
        if (autoOn) {
            String target = mat ? matName(autoTarget) : Defs.EN_NAME[autoTarget] + tierSuffixMax(autoTarget);
            plain(c, L.t("กำลังสุ่มหา ", "Rolling for ") + target + "...  " + autoCount, W / 2, aBot + 30 * u, 30 * u, 0xFFFFD23F, Paint.Align.CENTER, 0);
        }
    }

    String tierSuffixMax(int e) {
        int n = Defs.EN_TIERS[e];
        return n == 3 ? " III" : n == 2 ? " II" : "";
    }

    String matName(int m) {
        return m < 0 ? "-" : (L.en ? Defs.MAT_NAME[m] : Defs.MAT_NAME_TH[m] + " (" + Defs.MAT_NAME[m] + ")");
    }

    /** factory machine (enchant) or lab flask ring (material) drawn behind the selected planet */
    void labMachine(Canvas c, float x, float y, float r, boolean mat) {
        int col = mat ? 0xFF33A8D0 : 0xFFB86BFF;
        glowCircle(c, x, y, r * 1.9f, alpha(col, 0x55));
        sp.setPathEffect(null);
        // rotating outer rings
        for (int i = 0; i < 3; i++) {
            sp.setStrokeWidth((10 - i * 3) * u);
            sp.setColor(alpha(lighten(col, 0.2f * i), 0xAA - i * 0x22));
            float rr = r * (1.25f + i * 0.14f);
            rf.set(x - rr, y - rr, x + rr, y + rr);
            float a0 = anim * (i % 2 == 0 ? 40 : -55) + i * 50;
            for (int k = 0; k < 3; k++) c.drawArc(rf, a0 + k * 120, 70, false, sp);
        }
        // base pedestal
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(0, y + r * 1.05f, 0, y + r * 1.4f, 0xFF4A3D8F, 0xFF140A33, Shader.TileMode.CLAMP));
        c.drawOval(new RectF(x - r * 1.1f, y + r * 1.0f, x + r * 1.1f, y + r * 1.4f), p);
        p.setShader(null);
        sp.setStrokeWidth(4 * u);
        sp.setColor(lighten(col, 0.4f));
        c.drawOval(new RectF(x - r * 1.1f, y + r * 1.0f, x + r * 1.1f, y + r * 1.4f), sp);
        // beam
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(0, y + r * 1.2f, 0, y - r, alpha(col, 0x55), alpha(col, 0x00), Shader.TileMode.CLAMP));
        Path beam = new Path();
        beam.moveTo(x - r * 0.9f, y + r * 1.2f);
        beam.lineTo(x + r * 0.9f, y + r * 1.2f);
        beam.lineTo(x + r * 0.5f, y - r);
        beam.lineTo(x - r * 0.5f, y - r);
        beam.close();
        c.drawPath(beam, p);
        p.setShader(null);
        // floating bits: gears (factory) or bubbles (lab)
        for (int i = 0; i < 6; i++) {
            double a = anim * 0.8 + i * Math.PI / 3;
            float bx = x + (float) Math.cos(a) * r * 1.55f, by2 = y + (float) Math.sin(a) * r * 0.6f;
            if (mat) {
                glowCircle(c, bx, by2, 22 * u, 0x6633E0FF);
                sp.setStrokeWidth(3 * u);
                sp.setColor(0xCCB8F6FF);
                c.drawCircle(bx, by2, 9 * u, sp);
            } else {
                gearSmall(c, bx, by2, 14 * u, anim * 90 * (i % 2 == 0 ? 1 : -1));
            }
        }
    }

    void gearSmall(Canvas c, float x, float y, float r, float rot) {
        c.save();
        c.rotate(rot, x, y);
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(0xFFD8C8FF);
        for (int i = 0; i < 6; i++) {
            c.save();
            c.rotate(i * 60, x, y);
            rf2.set(x - r * 0.22f, y - r * 1.15f, x + r * 0.22f, y - r * 0.6f);
            c.drawRect(rf2, p);
            c.restore();
        }
        c.drawCircle(x, y, r * 0.8f, p);
        p.setColor(0xFF2A1A5E);
        c.drawCircle(x, y, r * 0.3f, p);
        c.restore();
    }

    void popupFrame(Canvas c, RectF r, String title) {
        dim(c);
        panel(c, r, 0xFF2A1A5E, 44 * u);
        title(c, title, r.centerX(), r.top + 95 * u, 60 * u, 0xFFFFD23F, Paint.Align.CENTER, r.width() - 60 * u);
    }

    void drawIndex(Canvas c) {
        boolean mat = labTab == 1;
        RectF r = new RectF(40 * u, 130 * u, W - 40 * u, H - 130 * u);
        popupFrame(c, r, mat ? "Material Index" : "Enchant Index");
        int n = mat ? Defs.MAT_COUNT : Defs.EN_COUNT;
        float top = r.top + 140 * u, rh = Math.min(120 * u, (r.height() - 310 * u) / n);
        for (int i = 0; i < n; i++) {
            float y = top + i * rh;
            int rar = mat ? Defs.MAT_RARITY[i] : Defs.EN_RARITY[i];
            int rc = rar == Defs.GALAXY ? hsv(anim * 90) : Defs.RARITY_COLOR[rar];
            if (mat) materialBadge(c, r.left + 70 * u, y + rh / 2, rh * 0.32f, i);
            else badge(c, r.left + 70 * u, y + rh / 2, rh * 0.32f, i, 0);
            String nm = mat ? matName(i) : Defs.EN_NAME[i] + (Defs.EN_TIERS[i] == 3 ? " I-III" : Defs.EN_TIERS[i] == 2 ? " I-II" : "");
            text(c, nm, r.left + 130 * u, y + rh * 0.45f, rh * 0.3f, 0xFFFFFFFF, Paint.Align.LEFT, r.width() - 420 * u);
            float ch = mat ? Defs.MAT_CHANCE[i] : Defs.EN_CHANCE[i];
            text(c, String.format(Locale.US, "%s  %.2f%%", Defs.RARITY_NAME[rar], ch), r.right - 40 * u, y + rh * 0.45f, rh * 0.24f, rc, Paint.Align.RIGHT, 0);
            plain(c, mat ? (L.en ? Defs.MAT_DESC_EN[i] : Defs.MAT_DESC[i]) : (L.en ? Defs.EN_DESC_EN[i] : Defs.EN_DESC[i]),
                    r.left + 130 * u, y + rh * 0.86f, rh * 0.21f, 0xFFC9C2E6, Paint.Align.LEFT, r.width() - 170 * u);
        }
        rf.set(r.centerX() - 200 * u, r.bottom - 120 * u, r.centerX() + 200 * u, r.bottom - 30 * u);
        button(c, rf, L.t("ปิด", "Close"), 0xFF6B5BD6, true, () -> popup = P_NONE);
    }

    void drawAutoPick(Canvas c) {
        boolean mat = labTab == 1;
        RectF r = new RectF(40 * u, 130 * u, W - 40 * u, H - 130 * u);
        popupFrame(c, r, L.t("สุ่มจนกว่าจะได้...", "Roll until you get..."));
        int n = mat ? Defs.MAT_COUNT : Defs.EN_COUNT;
        float top = r.top + 140 * u, rh = Math.min(115 * u, (r.height() - 290 * u) / n);
        for (int i = 0; i < n; i++) {
            final int e = i;
            int rar = mat ? Defs.MAT_RARITY[i] : Defs.EN_RARITY[i];
            rf.set(r.left + 40 * u, top + i * rh + 5 * u, r.right - 40 * u, top + (i + 1) * rh - 5 * u);
            String label = (mat ? matName(i) : Defs.EN_NAME[i] + tierSuffixMax(i)) + "  (" + Defs.RARITY_NAME[rar] + ")";
            int col = darken(rar == Defs.GALAXY ? 0xFF33C0E0 : Defs.RARITY_COLOR[rar], rar == Defs.COMMON ? 0.55f : 0.25f);
            button(c, rf, label, col, true, () -> {
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
        RectF r = new RectF(80 * u, H / 2 - 660 * u, W - 80 * u, H / 2 + 660 * u);
        popupFrame(c, r, L.t("ข้อมูลผู้เล่น", "Player info"));
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF6B5BD6);
        c.drawCircle(r.centerX(), r.top + 230 * u, 90 * u, p);
        person(c, r.centerX(), r.top + 225 * u, 70 * u);
        title(c, acc.display(user), r.centerX(), r.top + 380 * u, 56 * u, 0xFFFFFFFF, Paint.Align.CENTER, r.width() - 80 * u);
        if (isAdmin()) plain(c, "ADMIN", r.centerX(), r.top + 425 * u, 30 * u, 0xFFFF8AB8, Paint.Align.CENTER, 0);
        int cleared = 0;
        for (boolean b : pf.clear) if (b) cleared++;
        String[] lines = {
                "Star: " + pf.stars,
                "Millite: " + pf.millite,
                "Crystal: " + pf.crystals,
                L.t("ชนะ: ", "Wins: ") + pf.wins,
                L.t("เวลาเล่น: ", "Play time: ") + fmtTime(pf.playSec),
                L.t("ดวงดาวที่มี: ", "Planets owned: ") + pf.ownedCount() + "/" + Defs.P_COUNT,
                L.t("ด่านที่ผ่าน: ", "Stages cleared: ") + cleared + "/" + Defs.ST_NAME.length,
                L.t("บอสที่ล้มได้: ", "Bosses defeated: ") + pf.bossKills,
                L.t("จำนวนเกมที่เล่น: ", "Games played: ") + pf.games};
        float y = r.top + 490 * u;
        for (String s : lines) {
            plain(c, s, r.left + 90 * u, y, 36 * u, 0xFFE6E0FF, Paint.Align.LEFT, r.width() - 180 * u);
            y += 56 * u;
        }
        float bw = (r.width() - 120 * u) / 2f;
        rf.set(r.left + 40 * u, r.bottom - 250 * u, r.left + 40 * u + bw, r.bottom - 160 * u);
        button(c, rf, L.t("เปลี่ยนรหัสผ่าน", "Change password"), 0xFF4F8BFF, true, () -> { popup = P_NONE; askChangePassword(); });
        rf.set(r.right - 40 * u - bw, r.bottom - 250 * u, r.right - 40 * u, r.bottom - 160 * u);
        button(c, rf, L.t("ออกจากระบบ", "Log out"), 0xFFFF6B5B, true, () -> Dialogs.confirm(ctx, L.t("ออกจากระบบ", "Log out"),
                L.t("ต้องการออกจากระบบใช่ไหม?", "Log out of this account?"), L.t("ออกจากระบบ", "Log out"), this::logout));
        rf.set(r.centerX() - 200 * u, r.bottom - 130 * u, r.centerX() + 200 * u, r.bottom - 40 * u);
        button(c, rf, L.t("ปิด", "Close"), 0xFF6B5BD6, true, () -> popup = P_NONE);
    }

    void drawSettings(Canvas c) {
        RectF r = new RectF(80 * u, H / 2 - 340 * u, W - 80 * u, H / 2 + 340 * u);
        popupFrame(c, r, L.t("ตั้งค่า", "Settings"));
        text(c, "ภาษา / Language", r.centerX(), r.top + 210 * u, 42 * u, 0xFFFFFFFF);
        float bw = (r.width() - 180 * u) / 2f;
        rf.set(r.left + 60 * u, r.top + 260 * u, r.left + 60 * u + bw, r.top + 370 * u);
        button(c, rf, "ไทย", !L.en ? 0xFFFF8A2B : 0xFF3A3360, true, () -> { L.en = false; acc.setLang(false); });
        rf.set(r.right - 60 * u - bw, r.top + 260 * u, r.right - 60 * u, r.top + 370 * u);
        button(c, rf, "English", L.en ? 0xFFFF8A2B : 0xFF3A3360, true, () -> { L.en = true; acc.setLang(true); });
        plain(c, "Stella TD " + VERSION, r.centerX(), r.top + 450 * u, 30 * u, 0x99FFFFFF, Paint.Align.CENTER, 0);
        rf.set(r.centerX() - 200 * u, r.bottom - 130 * u, r.centerX() + 200 * u, r.bottom - 40 * u);
        button(c, rf, L.t("ปิด", "Close"), 0xFF6B5BD6, true, () -> popup = P_NONE);
    }

    // ================================================================== enchant badge
    void badge(Canvas c, float x, float y, float r, int en, int tier) {
        if (en < 0) return;
        int rar = Defs.EN_RARITY[en];
        int col = rar == Defs.GALAXY ? hsv(anim * 120) : Defs.RARITY_COLOR[rar];
        if (rar >= Defs.LEGEND) glowCircle(c, x, y, r * 2f, alpha(col, 0x77));
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new RadialGradient(x - r * 0.3f, y - r * 0.35f, r * 1.5f, lighten(col, 0.35f), darken(col, 0.35f), Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
        p.setShader(null);
        sp.setPathEffect(null);
        sp.setStrokeWidth(r * 0.16f);
        sp.setColor(0xFF0B0520);
        c.drawCircle(x, y, r, sp);
        sp.setColor(0xFFFFFFFF);
        sp.setStrokeWidth(r * 0.15f);
        p.setColor(0xFFFFFFFF);
        Path gp = new Path();
        float s = r * 0.6f;
        switch (en) {
            case Defs.EN_STRONG:
                c.drawLine(x, y + s, x, y - s, sp);
                c.drawLine(x - s * 0.5f, y + s * 0.35f, x + s * 0.5f, y + s * 0.35f, sp);
                break;
            case Defs.EN_REACH:
                c.drawCircle(x, y, s * 0.85f, sp);
                c.drawCircle(x, y, s * 0.25f, p);
                break;
            case Defs.EN_MOVING:
                c.drawLine(x - s * 0.7f, y - s * 0.55f, x - s * 0.1f, y, sp);
                c.drawLine(x - s * 0.1f, y, x - s * 0.7f, y + s * 0.55f, sp);
                c.drawLine(x + s * 0.05f, y - s * 0.55f, x + s * 0.65f, y, sp);
                c.drawLine(x + s * 0.65f, y, x + s * 0.05f, y + s * 0.55f, sp);
                break;
            case Defs.EN_LIGHTNING:
            case Defs.EN_ELECTRO:
                gp.moveTo(x + s * 0.2f, y - s);
                gp.lineTo(x - s * 0.5f, y + s * 0.15f);
                gp.lineTo(x, y + s * 0.15f);
                gp.lineTo(x - s * 0.2f, y + s);
                gp.lineTo(x + s * 0.5f, y - s * 0.15f);
                gp.lineTo(x, y - s * 0.15f);
                gp.close();
                p.setColor(0xFFFFF27A);
                c.drawPath(gp, p);
                if (en == Defs.EN_ELECTRO) c.drawCircle(x, y, s * 0.95f, sp);
                break;
            case Defs.EN_STORM:
                for (int i = 0; i < 3; i++) {
                    float rr = s * (0.35f + i * 0.3f);
                    rf2.set(x - rr, y - rr * 0.8f, x + rr, y + rr * 0.8f);
                    c.drawArc(rf2, i * 60 + anim * 120, 220, false, sp);
                }
                break;
            case Defs.EN_TECH:
                rf2.set(x - s * 0.55f, y - s * 0.55f, x + s * 0.55f, y + s * 0.55f);
                c.drawRect(rf2, sp);
                for (int i = -1; i <= 1; i++) {
                    c.drawLine(x + i * s * 0.3f, y - s * 0.55f, x + i * s * 0.3f, y - s * 0.9f, sp);
                    c.drawLine(x + i * s * 0.3f, y + s * 0.55f, x + i * s * 0.3f, y + s * 0.9f, sp);
                }
                p.setColor(0xFFFFFFFF);
                c.drawCircle(x, y, s * 0.2f, p);
                break;
            case Defs.EN_MIDAS:
                p.setColor(0xFFFFE27A);
                c.drawCircle(x, y, s * 0.85f, p);
                plain(c, "$", x, y + s * 0.42f, s * 1.3f, 0xFF8A5A00, Paint.Align.CENTER, 0);
                break;
            case Defs.EN_CRYSTALRAIN:
                for (int i = -1; i <= 1; i++) {
                    float cx = x + i * s * 0.55f, cy = y + (i == 0 ? -s * 0.25f : s * 0.2f), h = s * 0.55f;
                    Path d = new Path();
                    d.moveTo(cx, cy - h);
                    d.lineTo(cx + h * 0.4f, cy);
                    d.lineTo(cx, cy + h);
                    d.lineTo(cx - h * 0.4f, cy);
                    d.close();
                    p.setColor(0xFFD8FFFF);
                    c.drawPath(d, p);
                }
                break;
            case Defs.EN_MOON:
                p.setColor(0xFFFFFFFF);
                c.drawCircle(x, y, s * 0.8f, p);
                p.setColor(darken(col, 0.2f));
                c.drawCircle(x + s * 0.4f, y - s * 0.25f, s * 0.7f, p);
                break;
            case Defs.EN_GALACIA:
                for (int i = 0; i < 3; i++) {
                    double a = i * Math.PI / 3;
                    float dx = (float) Math.cos(a) * s, dy = (float) Math.sin(a) * s;
                    c.drawLine(x - dx, y - dy, x + dx, y + dy, sp);
                }
                break;
            case Defs.EN_LAVA:
                gp.moveTo(x, y - s);
                gp.cubicTo(x + s * 0.9f, y - s * 0.1f, x + s * 0.7f, y + s, x, y + s);
                gp.cubicTo(x - s * 0.7f, y + s, x - s * 0.9f, y - s * 0.1f, x, y - s);
                gp.close();
                p.setColor(0xFFFF5A1F);
                c.drawPath(gp, p);
                p.setColor(0xFFFFE27A);
                c.drawCircle(x, y + s * 0.4f, s * 0.35f, p);
                break;
            case Defs.EN_MEGALONIA:
                c.save();
                c.translate(x, y);
                c.rotate(anim * 60);
                c.scale(s * 1.1f, s * 1.1f);
                c.drawPath(Art.starPath(0, 0, 1f, 0.35f, 4), p);
                c.restore();
                break;
            case Defs.EN_GIANTTIC:
                gp.moveTo(x, y - s);
                gp.lineTo(x + s, y + s * 0.1f);
                gp.lineTo(x + s * 0.4f, y + s * 0.1f);
                gp.lineTo(x + s * 0.4f, y + s);
                gp.lineTo(x - s * 0.4f, y + s);
                gp.lineTo(x - s * 0.4f, y + s * 0.1f);
                gp.lineTo(x - s, y + s * 0.1f);
                gp.close();
                c.drawPath(gp, p);
                break;
            case Defs.EN_GALAXIA:
                for (int arm = 0; arm < 2; arm++) {
                    for (int k = 0; k < 8; k++) {
                        double a = anim * 1.5 + arm * Math.PI + k * 0.5;
                        float rr = s * (0.12f + k * 0.11f);
                        p.setColor(0xFFFFFFFF);
                        c.drawCircle(x + (float) Math.cos(a) * rr, y + (float) Math.sin(a) * rr * 0.75f, s * (0.13f - k * 0.01f), p);
                    }
                }
                break;
            default: break;
        }
        if (tier > 0) {
            String roman = tier == 1 ? "I" : (tier == 2 ? "II" : "III");
            text(c, roman, x + r * 0.9f, y + r * 1.1f, r * 0.8f, 0xFFFFFFFF, Paint.Align.CENTER, 0);
        }
    }

    /** hexagon chip with the material's chemical symbol */
    void materialBadge(Canvas c, float x, float y, float r, int m) {
        if (m < 0) return;
        int col = Defs.MAT_COLOR[m];
        if (Defs.MAT_RARITY[m] >= Defs.LEGEND) glowCircle(c, x, y, r * 2f, alpha(m == Defs.MAT_GRAPHENE ? 0xFF9AA0B0 : col, 0x77));
        Path h = new Path();
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 6 + i * Math.PI / 3;
            float px = x + (float) Math.cos(a) * r, py = y + (float) Math.sin(a) * r;
            if (i == 0) h.moveTo(px, py);
            else h.lineTo(px, py);
        }
        h.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(x - r, y - r, x + r, y + r, lighten(col, 0.45f), darken(col, 0.3f), Shader.TileMode.CLAMP));
        c.drawPath(h, p);
        p.setShader(null);
        sp.setPathEffect(null);
        sp.setStrokeWidth(r * 0.14f);
        sp.setColor(0xFF0B0520);
        c.drawPath(h, sp);
        if (m == Defs.MAT_DIAMOND) {
            Path d = new Path();
            d.moveTo(x - r * 0.55f, y - r * 0.15f);
            d.lineTo(x - r * 0.3f, y - r * 0.45f);
            d.lineTo(x + r * 0.3f, y - r * 0.45f);
            d.lineTo(x + r * 0.55f, y - r * 0.15f);
            d.lineTo(x, y + r * 0.55f);
            d.close();
            p.setColor(0xFFFFFFFF);
            c.drawPath(d, p);
            sp.setStrokeWidth(r * 0.08f);
            sp.setColor(0xFF2F8FD8);
            c.drawPath(d, sp);
        } else {
            boolean darkText = m == Defs.MAT_QUARTZ || m == Defs.MAT_PLATINUM || m == Defs.MAT_GOLD || m == Defs.MAT_TITANIUM || m == Defs.MAT_IRON;
            plain(c, Defs.MAT_SYM[m], x, y + r * 0.32f, r * 0.9f, darkText ? 0xFF1A1030 : 0xFFFFFFFF, Paint.Align.CENTER, r * 1.5f);
        }
    }

    /** distinct aura around a planet for each material */
    void materialAura(Canvas c, float x, float y, float r, int m) {
        int col = Defs.MAT_COLOR[m];
        sp.setPathEffect(null);
        switch (m) {
            case Defs.MAT_IRON:
            case Defs.MAT_TITANIUM:
                sp.setStrokeWidth(r * 0.08f);
                sp.setColor(alpha(col, 0xCC));
                c.drawCircle(x, y, r * 1.12f, sp);
                sp.setStrokeWidth(r * 0.03f);
                sp.setColor(0xAAFFFFFF);
                c.drawCircle(x, y, r * 1.18f, sp);
                if (m == Defs.MAT_TITANIUM) for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4 + anim * 0.5;
                    c.drawLine(x + (float) Math.cos(a) * r * 1.2f, y + (float) Math.sin(a) * r * 1.2f,
                            x + (float) Math.cos(a) * r * 1.3f, y + (float) Math.sin(a) * r * 1.3f, sp);
                }
                break;
            case Defs.MAT_COPPER:
            case Defs.MAT_GOLD:
                glowCircle(c, x, y, r * 1.5f, alpha(col, m == Defs.MAT_GOLD ? 0x99 : 0x66));
                sp.setStrokeWidth(r * 0.06f);
                sp.setColor(col);
                c.drawCircle(x, y, r * 1.12f, sp);
                if (m == Defs.MAT_GOLD) for (int i = 0; i < 3; i++) {
                    double a = anim * 1.4 + i * 2.09;
                    star(c, x + (float) Math.cos(a) * r * 1.25f, y + (float) Math.sin(a) * r * 1.25f, r * 0.1f);
                }
                break;
            case Defs.MAT_QUARTZ:
                for (int i = 0; i < 3; i++) {
                    double a = anim * 0.9 + i * 2.09;
                    float px = x + (float) Math.cos(a) * r * 1.25f, py = y + (float) Math.sin(a) * r * 1.25f, h = r * 0.18f;
                    Path d = new Path();
                    d.moveTo(px, py - h);
                    d.lineTo(px + h * 0.45f, py - h * 0.3f);
                    d.lineTo(px + h * 0.45f, py + h * 0.5f);
                    d.lineTo(px, py + h);
                    d.lineTo(px - h * 0.45f, py + h * 0.5f);
                    d.lineTo(px - h * 0.45f, py - h * 0.3f);
                    d.close();
                    p.setStyle(Paint.Style.FILL);
                    p.setShader(null);
                    p.setColor(0xEEFFE0F4);
                    c.drawPath(d, p);
                }
                glowCircle(c, x, y, r * 1.35f, 0x44F6C8EA);
                break;
            case Defs.MAT_COBALT:
                glowCircle(c, x, y, r * 1.45f, 0x773F66E0);
                sp.setStrokeWidth(r * 0.07f);
                sp.setColor(0xFF6F8FFF);
                rf2.set(x - r * 1.18f, y - r * 1.18f, x + r * 1.18f, y + r * 1.18f);
                c.drawArc(rf2, anim * 200, 120, false, sp);
                c.drawArc(rf2, anim * 200 + 180, 120, false, sp);
                break;
            case Defs.MAT_PLATINUM:
                glowCircle(c, x, y, r * 1.4f, 0x55FFFFFF);
                sp.setStrokeWidth(r * 0.05f);
                for (int i = 0; i < 3; i++) {
                    sp.setColor(alpha(0xFFFFFFFF, 0xCC - i * 0x33));
                    float rr = r * (1.12f + i * 0.08f);
                    rf2.set(x - rr, y - rr, x + rr, y + rr);
                    c.drawArc(rf2, -anim * (100 + i * 40) + i * 120, 90, false, sp);
                }
                break;
            case Defs.MAT_URANIUM: {
                float pulse = 0.5f + 0.5f * (float) Math.sin(anim * 4);
                glowCircle(c, x, y, r * (1.4f + pulse * 0.2f), alpha(0xFF7CFF4A, (int) (90 + 80 * pulse)));
                p.setStyle(Paint.Style.FILL);
                p.setShader(null);
                p.setColor(0xFFB8FF7A);
                for (int i = 0; i < 5; i++) {
                    double a = anim * 2 + i * 1.256;
                    c.drawCircle(x + (float) Math.cos(a) * r * 1.2f, y + (float) Math.sin(a) * r * 1.2f, r * 0.05f, p);
                }
                break;
            }
            case Defs.MAT_GRAPHENE: {
                Path h = new Path();
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3 + anim * 0.3;
                    float px = x + (float) Math.cos(a) * r * 1.22f, py = y + (float) Math.sin(a) * r * 1.22f;
                    if (i == 0) h.moveTo(px, py);
                    else h.lineTo(px, py);
                }
                h.close();
                sp.setStrokeWidth(r * 0.09f);
                sp.setColor(0xFF20242C);
                c.drawPath(h, sp);
                sp.setStrokeWidth(r * 0.03f);
                sp.setColor(0xCCB8C0D0);
                c.drawPath(h, sp);
                break;
            }
            case Defs.MAT_DIAMOND:
                glowCircle(c, x, y, r * 1.5f, 0x889EF6FF);
                sp.setStrokeWidth(r * 0.05f);
                sp.setColor(0xFFDFFBFF);
                c.drawCircle(x, y, r * 1.14f, sp);
                p.setStyle(Paint.Style.FILL);
                p.setShader(null);
                p.setColor(0xFFFFFFFF);
                for (int i = 0; i < 4; i++) {
                    double a = anim * 1.2 + i * Math.PI / 2;
                    float dx = x + (float) Math.cos(a) * r * 1.28f, dy = y + (float) Math.sin(a) * r * 1.28f;
                    c.save();
                    c.translate(dx, dy);
                    c.rotate(anim * 90);
                    c.scale(r * 0.12f, r * 0.12f);
                    c.drawPath(Art.starPath(0, 0, 1.4f, 0.35f, 4), p);
                    c.restore();
                }
                break;
            default: break;
        }
    }

    void flask(Canvas c, float x, float y, float r) {
        Path f = new Path();
        f.moveTo(x - r * 0.22f, y - r);
        f.lineTo(x + r * 0.22f, y - r);
        f.lineTo(x + r * 0.22f, y - r * 0.35f);
        f.lineTo(x + r * 0.8f, y + r * 0.75f);
        f.cubicTo(x + r * 0.85f, y + r, x + r * 0.7f, y + r, x + r * 0.55f, y + r);
        f.lineTo(x - r * 0.55f, y + r);
        f.cubicTo(x - r * 0.7f, y + r, x - r * 0.85f, y + r, x - r * 0.8f, y + r * 0.75f);
        f.lineTo(x - r * 0.22f, y - r * 0.35f);
        f.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(0, y - r, 0, y + r, 0xFFE6F8FF, 0xFFB8E8FF, Shader.TileMode.CLAMP));
        c.drawPath(f, p);
        p.setShader(new LinearGradient(0, y + r * 0.1f, 0, y + r, 0xFF5EF2FF, 0xFFB86BFF, Shader.TileMode.CLAMP));
        Path liq = new Path();
        liq.moveTo(x - r * 0.48f, y + r * 0.2f);
        liq.lineTo(x + r * 0.48f, y + r * 0.2f);
        liq.lineTo(x + r * 0.8f, y + r * 0.75f);
        liq.cubicTo(x + r * 0.85f, y + r, x + r * 0.7f, y + r, x + r * 0.55f, y + r);
        liq.lineTo(x - r * 0.55f, y + r);
        liq.cubicTo(x - r * 0.7f, y + r, x - r * 0.85f, y + r, x - r * 0.8f, y + r * 0.75f);
        liq.close();
        c.drawPath(liq, p);
        p.setShader(null);
        p.setColor(0xCCFFFFFF);
        c.drawCircle(x - r * 0.2f, y + r * 0.55f, r * 0.1f, p);
        c.drawCircle(x + r * 0.15f, y + r * 0.35f, r * 0.07f, p);
        sp.setPathEffect(null);
        sp.setStrokeWidth(r * 0.12f);
        sp.setColor(0xFF0B0520);
        c.drawPath(f, sp);
    }

    void trophy(Canvas c, float x, float y, float r) {
        Path cup = new Path();
        cup.moveTo(x - r * 0.7f, y - r * 0.8f);
        cup.lineTo(x + r * 0.7f, y - r * 0.8f);
        cup.cubicTo(x + r * 0.7f, y + r * 0.1f, x + r * 0.3f, y + r * 0.35f, x, y + r * 0.35f);
        cup.cubicTo(x - r * 0.3f, y + r * 0.35f, x - r * 0.7f, y + r * 0.1f, x - r * 0.7f, y - r * 0.8f);
        cup.close();
        sp.setPathEffect(null);
        sp.setStrokeWidth(r * 0.14f);
        sp.setColor(0xFFFFE27A);
        rf2.set(x - r * 1.05f, y - r * 0.7f, x - r * 0.45f, y);
        c.drawArc(rf2, 90, 180, false, sp);
        rf2.set(x + r * 0.45f, y - r * 0.7f, x + r * 1.05f, y);
        c.drawArc(rf2, 270, 180, false, sp);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(0, y - r, 0, y + r, 0xFFFFF2B0, 0xFFE09A1A, Shader.TileMode.CLAMP));
        c.drawPath(cup, p);
        rf2.set(x - r * 0.15f, y + r * 0.3f, x + r * 0.15f, y + r * 0.7f);
        c.drawRect(rf2, p);
        rf2.set(x - r * 0.5f, y + r * 0.65f, x + r * 0.5f, y + r * 0.9f);
        c.drawRoundRect(rf2, r * 0.08f, r * 0.08f, p);
        p.setShader(null);
        sp.setStrokeWidth(r * 0.1f);
        sp.setColor(0xFF0B0520);
        c.drawPath(cup, sp);
        star(c, x, y - r * 0.3f, r * 0.25f);
    }

    static String fmtTime(int sec) {
        int h = sec / 3600, m = (sec % 3600) / 60;
        return h > 0 ? h + "h " + m + "m" : m + "m " + (sec % 60) + "s";
    }



    // ================================================================== login
    void drawLogin(Canvas c) {
        // hero: sun, orbiting planets, black hole and a meteor shower
        float cy = H * 0.3f;
        glowCircle(c, W / 2, cy, 420 * u, 0x55FF9A3C);
        sp.setPathEffect(null);
        sp.setStrokeWidth(3 * u);
        for (int i = 0; i < 3; i++) {
            float rr = (240 + i * 95) * u;
            sp.setColor(0x33FFFFFF);
            c.drawOval(new RectF(W / 2 - rr, cy - rr * 0.42f, W / 2 + rr, cy + rr * 0.42f), sp);
        }
        int[] orb = {Defs.EARTH, Defs.SATURN, Defs.NEPTUNE, Defs.MARS, Defs.URANUS, Defs.JUPITER};
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1) sprite(c, pBmp[Defs.SUN], W / 2, cy, 175 * u, 255);
            for (int i = 0; i < orb.length; i++) {
                int ring = i % 3;
                double a = anim * (0.5 - ring * 0.1) + i * 1.9;
                float rr = (240 + ring * 95) * u;
                boolean front = Math.sin(a) > 0;
                if ((pass == 1) != front) continue;
                float s = (0.8f + 0.2f * (float) Math.sin(a)) * (48 + ring * 10) * u;
                sprite(c, pBmp[orb[i]], W / 2 + (float) Math.cos(a) * rr, cy + (float) Math.sin(a) * rr * 0.42f, s, 255);
            }
        }
        blackHole(c, W * 0.86f, H * 0.08f, 46 * u);
        for (int i = 0; i < 4; i++) {
            float t = (float) ((anim * 0.22 + i * 0.27) % 1.0);
            float mx = W * (1.1f - t * 1.3f) + i * 60 * u, my = H * (0.02f + t * 0.3f) + i * 40 * u;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            p.setShader(new LinearGradient(mx, my, mx + 160 * u, my - 70 * u, 0xCCFFE6A8, 0x00FFE6A8, Shader.TileMode.CLAMP));
            Path tr = new Path();
            tr.moveTo(mx, my - 7 * u);
            tr.lineTo(mx + 160 * u, my - 70 * u);
            tr.lineTo(mx, my + 7 * u);
            tr.close();
            c.drawPath(tr, p);
            p.setShader(null);
            glowCircle(c, mx, my, 14 * u, 0xFFFFF4D0);
        }
        // glass card
        RectF card = new RectF(60 * u, H * 0.5f, W - 60 * u, H * 0.5f + 760 * u);
        panel(c, card, 0xE0241452, 48 * u);
        logo(c, W / 2, card.top + 150 * u, 150 * u);
        plain(c, L.t("ปกป้องฐานจากอุกกาบาตและหลุมดำ", "Defend your base from meteors and black holes"), W / 2, card.top + 220 * u, 34 * u, 0xFFE6E0FF, Paint.Align.CENTER, card.width() - 60 * u);
        float bw = card.width() - 120 * u, bh = 130 * u, by = card.top + 280 * u;
        rf.set(W / 2 - bw / 2, by, W / 2 + bw / 2, by + bh);
        button(c, rf, null, 0xFFFF8A2B, true, () -> askLogin(null));
        person(c, rf.left + 80 * u, rf.centerY() - 4 * u, 34 * u);
        title(c, L.t("เข้าสู่ระบบ", "Log in"), rf.centerX() + 30 * u, rf.centerY() + 18 * u, 52 * u, 0xFFFFFFFF);
        rf.set(W / 2 - bw / 2, by + bh + 36 * u, W / 2 + bw / 2, by + bh * 2 + 36 * u);
        button(c, rf, null, 0xFF4F8BFF, true, this::askRegister);
        star(c, rf.left + 80 * u, rf.centerY(), 30 * u);
        title(c, L.t("สร้างไอดีใหม่", "Create account"), rf.centerX() + 30 * u, rf.centerY() + 18 * u, 52 * u, 0xFFFFFFFF);
        float ly = by + bh * 2 + 90 * u, lw = 230 * u;
        rf.set(W / 2 - lw - 10 * u, ly, W / 2 - 10 * u, ly + 86 * u);
        button(c, rf, "ไทย", !L.en ? 0xFF33C6A6 : 0xFF3A3360, true, () -> { L.en = false; acc.setLang(false); });
        rf.set(W / 2 + 10 * u, ly, W / 2 + lw + 10 * u, ly + 86 * u);
        button(c, rf, "English", L.en ? 0xFF33C6A6 : 0xFF3A3360, true, () -> { L.en = true; acc.setLang(true); });
        plain(c, L.t("ไอดีและความคืบหน้าถูกเก็บไว้ในเครื่องนี้", "Accounts and progress are saved on this device"),
                W / 2, H - 60 * u, 28 * u, 0x99FFFFFF, Paint.Align.CENTER, W - 80 * u);
        plain(c, VERSION, W - 30 * u, H - 20 * u, 24 * u, 0x66FFFFFF, Paint.Align.RIGHT, 0);
    }

    // ================================================================== leaderboard
    void drawLeader(Canvas c) {
        header(c, L.t("อันดับ", "Leaderboard"));
        String[] tabs = {L.t("ชนะมากสุด", "Most wins"), L.t("Star มากสุด", "Most Star"), L.t("เล่นนานสุด", "Play time")};
        float tw = (W - 120 * u) / 3f;
        for (int i = 0; i < 3; i++) {
            final int t = i;
            rf.set(50 * u + i * (tw + 10 * u), 190 * u, 50 * u + i * (tw + 10 * u) + tw, 285 * u);
            button(c, rf, tabs[i], leaderTab == i ? 0xFFFF8A2B : 0xFF3A3360, true, () -> leaderTab = t);
        }
        final java.util.ArrayList<String> list = acc.users();
        final java.util.HashMap<String, Integer> val = new java.util.HashMap<>();
        for (String a : list) {
            Profile q = profileOf(a);
            val.put(a, leaderTab == 0 ? q.wins : leaderTab == 1 ? q.stars : q.playSec);
        }
        java.util.Collections.sort(list, (x, y) -> Integer.compare(val.get(y), val.get(x)));
        // podium for the top 3
        float py = 330 * u, ph = 420 * u;
        int[] order = {1, 0, 2};
        float[] heights = {0.75f, 1f, 0.6f};
        int[] medal = {0xFFFFD23F, 0xFFD8DEE8, 0xFFE0904A};
        float pw = (W - 160 * u) / 3f;
        for (int k = 0; k < 3; k++) {
            int rank = order[k];
            float x = 50 * u + k * (pw + 30 * u);
            float hh = ph * 0.5f * heights[k];
            RectF col = new RectF(x, py + ph - hh, x + pw, py + ph);
            rarityCard(c, col, medal[rank], true, false, 24 * u);
            title(c, String.valueOf(rank + 1), col.centerX(), col.top + 90 * u, 80 * u, medal[rank]);
            if (rank < list.size()) {
                String a = list.get(rank);
                float cy = col.top - 110 * u;
                glowCircle(c, col.centerX(), cy, 100 * u, alpha(medal[rank], 0x66));
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF2A1A5E);
                c.drawCircle(col.centerX(), cy, 56 * u, p);
                sp.setStrokeWidth(6 * u);
                sp.setColor(medal[rank]);
                c.drawCircle(col.centerX(), cy, 56 * u, sp);
                person(c, col.centerX(), cy - 4 * u, 40 * u);
                if (rank == 0) trophy(c, col.centerX(), cy - 95 * u, 30 * u);
                text(c, acc.display(a), col.centerX(), col.top - 20 * u, 34 * u, a.equals(user) ? 0xFFFFD23F : 0xFFFFFFFF, Paint.Align.CENTER, pw);
            }
        }
        // full list
        float y = py + ph + 40 * u, rh = 96 * u;
        int max = (int) Math.min(list.size(), Math.max(3, (navTop + 180 * u - y) / (rh + 12 * u)));
        for (int i = 0; i < max; i++) {
            String a = list.get(i);
            RectF r = new RectF(40 * u, y, W - 40 * u, y + rh);
            panel(c, r, a.equals(user) ? 0xFF4A3D8F : 0xFF241A4E, 24 * u);
            int mc = i < 3 ? medal[i] : 0xFF6A6488;
            p.setStyle(Paint.Style.FILL);
            p.setColor(mc);
            c.drawCircle(r.left + 60 * u, r.centerY(), 30 * u, p);
            text(c, String.valueOf(i + 1), r.left + 60 * u, r.centerY() + 13 * u, 36 * u, 0xFF140A2A);
            text(c, acc.display(a) + (a.equals(user) ? L.t("  (คุณ)", "  (you)") : ""), r.left + 115 * u, r.centerY() + 14 * u, 40 * u, 0xFFFFFFFF, Paint.Align.LEFT, 480 * u);
            int v = val.get(a);
            String vs = leaderTab == 2 ? fmtTime(v) : String.valueOf(v);
            if (leaderTab == 1) star(c, r.right - 40 * u - tp.measureText(vs) - 40 * u, r.centerY() - 2 * u, 20 * u);
            text(c, vs, r.right - 40 * u, r.centerY() + 14 * u, 40 * u, 0xFFFFE27A, Paint.Align.RIGHT, 0);
            y += rh + 12 * u;
        }
        plain(c, L.t("อันดับของผู้เล่นทุกไอดีในเครื่องนี้", "Ranking of every account on this device"), W / 2, H - 50 * u, 28 * u, 0x99FFFFFF, Paint.Align.CENTER, 0);
    }

    // ================================================================== admin panel
    final java.util.HashMap<String, Profile> profCache = new java.util.HashMap<>();

    Profile profileOf(String u) {
        if (u.equals(user)) return pf;
        Profile q = profCache.get(u);
        if (q == null) {
            q = new Profile(acc.prefsOf(u));
            profCache.put(u, q);
        }
        return q;
    }

    void shield(Canvas c, float x, float y, float r) {
        Path s = new Path();
        s.moveTo(x, y - r);
        s.lineTo(x + r * 0.8f, y - r * 0.65f);
        s.cubicTo(x + r * 0.8f, y + r * 0.2f, x + r * 0.4f, y + r * 0.75f, x, y + r);
        s.cubicTo(x - r * 0.4f, y + r * 0.75f, x - r * 0.8f, y + r * 0.2f, x - r * 0.8f, y - r * 0.65f);
        s.close();
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFE6F0);
        c.drawPath(s, p);
        sp.setPathEffect(null);
        sp.setStrokeWidth(r * 0.12f);
        sp.setColor(0xFF140A2A);
        c.drawPath(s, sp);
        star(c, x, y - r * 0.05f, r * 0.42f);
    }

    interface IntSet {
        void set(int v);
    }

    void setAmount(String what, String target, int current, IntSet setter, Profile q) {
        Dialogs.ask(ctx, L.t("ตั้ง ", "Set ") + what, acc.display(target), new String[]{what}, new int[]{Dialogs.NUMBER},
                new String[]{String.valueOf(current)}, "OK", v -> {
                    try {
                        int n = Integer.parseInt(v[0].trim());
                        if (n < 0 || n > 99999999) return L.t("ใส่ได้ 0 ถึง 99,999,999", "Use 0 to 99,999,999");
                        setter.set(n);
                        q.save();
                        showToast(what + " = " + n + " → " + acc.display(target));
                        return null;
                    } catch (NumberFormatException e) {
                        return L.t("ใส่ตัวเลขเท่านั้น", "Numbers only");
                    }
                });
    }

    void drawGivePlanet(Canvas c) {
        RectF r = new RectF(40 * u, H / 2 - 560 * u, W - 40 * u, H / 2 + 560 * u);
        popupFrame(c, r, L.t("ให้ดวงดาวกับ ", "Give a planet to ") + acc.display(adminSel));
        final Profile q = profileOf(adminSel);
        float cell = (r.width() - 80 * u) / 3f, top = r.top + 150 * u;
        float rowH = Math.min(cell * 0.8f, (r.height() - 300 * u) / 4f);
        for (int i = 0; i < Defs.P_COUNT; i++) {
            final int t = i;
            float x = r.left + 40 * u + (i % 3) * cell, y = top + (i / 3) * rowH;
            RectF cr = new RectF(x + 8 * u, y + 8 * u, x + cell - 8 * u, y + rowH - 8 * u);
            rarityCard(c, cr, Defs.RARITY_COLOR[Defs.P_RARITY[i]], true, false, 24 * u);
            sprite(c, pBmp[i], cr.centerX(), cr.centerY() - 22 * u, Math.min(cell * 0.26f, rowH * 0.34f), 255);
            text(c, Defs.P_NAME[i], cr.centerX(), cr.bottom - 22 * u, 30 * u, 0xFFFFFFFF, Paint.Align.CENTER, cr.width() - 10 * u);
            if (q.owned[i]) plain(c, "x" + (q.copies[i] + 1), cr.right - 14 * u, cr.top + 38 * u, 26 * u, 0xFF7CFF8A, Paint.Align.RIGHT, 0);
            btns.add(new Btn(cr, () -> {
                q.givePlanet(t);
                showToast(Defs.P_NAME[t] + " → " + acc.display(adminSel));
            }));
        }
        rf.set(r.centerX() - 200 * u, r.bottom - 120 * u, r.centerX() + 200 * u, r.bottom - 30 * u);
        button(c, rf, L.t("ปิด", "Close"), 0xFF6B5BD6, true, () -> popup = P_NONE);
    }

    void adminDo(String msg, Runnable r) {
        r.run();
        showToast(msg);
    }

    void drawAdmin(Canvas c) {
        if (!isAdmin()) { screen = S_LOBBY; return; }
        header(c, "Admin Panel");
        java.util.ArrayList<String> list = acc.users();
        if (adminSel == null || !list.contains(adminSel)) adminSel = user;
        float y = 190 * u;
        if (acc.mustChange(user)) {
            RectF w = new RectF(40 * u, y, W - 40 * u, y + 130 * u);
            panel(c, w, 0xFF6A1F3A, 30 * u);
            plain(c, L.t("แอดมินยังใช้รหัสเริ่มต้น! เปลี่ยนเลย", "Admin still uses the default password!"), w.left + 30 * u, w.centerY() + 12 * u, 32 * u, 0xFFFFD0DD, Paint.Align.LEFT, w.width() - 330 * u);
            rf.set(w.right - 280 * u, w.top + 25 * u, w.right - 25 * u, w.bottom - 25 * u);
            button(c, rf, L.t("เปลี่ยนรหัส", "Change"), 0xFFFF6B5B, true, this::askChangePassword);
            y += 160 * u;
        }
        // ---- account list
        int per = 4, pages = Math.max(1, (list.size() + per - 1) / per);
        adminPage = Math.max(0, Math.min(adminPage, pages - 1));
        text(c, L.t("บัญชีทั้งหมด ", "All accounts ") + "(" + list.size() + ")", 50 * u, y + 45 * u, 40 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
        rf.set(W - 290 * u, y, W - 180 * u, y + 70 * u);
        button(c, rf, "<", 0xFF4A3D8F, adminPage > 0, () -> adminPage--);
        plain(c, (adminPage + 1) + "/" + pages, W - 140 * u, y + 47 * u, 30 * u, 0xFFE6E0FF, Paint.Align.CENTER, 0);
        rf.set(W - 100 * u, y, W - 40 * u, y + 70 * u);
        button(c, rf, ">", 0xFF4A3D8F, adminPage < pages - 1, () -> adminPage++);
        y += 90 * u;
        for (int i = adminPage * per; i < Math.min(list.size(), (adminPage + 1) * per); i++) {
            final String a = list.get(i);
            RectF r = new RectF(40 * u, y, W - 40 * u, y + 100 * u);
            panel(c, r, a.equals(adminSel) ? 0xFF4A3D8F : 0xFF241A4E, 24 * u);
            text(c, acc.display(a) + (a.equals(user) ? L.t("  (คุณ)", "  (you)") : ""), r.left + 30 * u, r.top + 62 * u, 40 * u, 0xFFFFFFFF, Paint.Align.LEFT, 420 * u);
            float tx = r.left + 470 * u;
            if (acc.isAdmin(a)) { plain(c, "ADMIN", tx, r.top + 60 * u, 28 * u, 0xFFFF8AB8, Paint.Align.LEFT, 0); tx += 110 * u; }
            if (acc.isBanned(a)) { plain(c, "BANNED", tx, r.top + 60 * u, 28 * u, 0xFFFF6B5B, Paint.Align.LEFT, 0); }
            Profile q = profileOf(a);
            plain(c, q.stars + " Star • " + q.millite + " Mil • " + q.crystals + " Cry", r.right - 30 * u, r.top + 60 * u, 26 * u, 0xFFFFD23F, Paint.Align.RIGHT, 0);
            btns.add(new Btn(r, () -> adminSel = a));
            y += 112 * u;
        }
        y = Math.max(y, 190 * u + (acc.mustChange(user) ? 160 * u : 0) + 90 * u + per * 112 * u) + 10 * u;
        // ---- actions for the selected account
        final String t = adminSel;
        final Profile q = profileOf(t);
        final boolean tAdmin = acc.isAdmin(t);
        text(c, L.t("จัดการ: ", "Manage: ") + acc.display(t), 50 * u, y + 40 * u, 42 * u, 0xFFFFFFFF, Paint.Align.LEFT, W - 100 * u);
        y += 70 * u;
        float bw = (W - 110 * u) / 2f, bh = 96 * u, gap = 18 * u;
        String[] labels = {L.t("ตั้ง Star", "Set Star"), L.t("ตั้ง Millite", "Set Millite"), L.t("ตั้ง Crystal", "Set Crystal"),
                L.t("ให้ดวงดาว", "Give planet"), acc.isBanned(t) ? L.t("ปลดแบน", "Unban") : L.t("แบนบัญชี", "Ban account"),
                L.t("ตั้งรหัสผ่านใหม่", "Set new password"), L.t("ปลดล็อกทุกด่าน", "Unlock all stages"), L.t("ปลดล็อกดาวทั้งหมด", "Unlock all planets"),
                L.t("รีเซ็ตความคืบหน้า", "Reset progress"), L.t("ลบบัญชี", "Delete account")};
        int[] colors = {0xFFE0A020, 0xFFB86BFF, 0xFF33A8D0, 0xFF33C6A6, 0xFFFF6B5B, 0xFF4F8BFF, 0xFF33C6A6, 0xFF33C6A6, 0xFFFF6B5B, 0xFFD0304A};
        Runnable[] acts = {
                () -> setAmount("Star", t, q.stars, v -> q.stars = v, q),
                () -> setAmount("Millite", t, q.millite, v -> q.millite = v, q),
                () -> setAmount("Crystal", t, q.crystals, v -> q.crystals = v, q),
                () -> popup = P_GIVE,
                () -> {
                    if (tAdmin) { showToast(L.t("แบนแอดมินไม่ได้", "Admins can't be banned")); return; }
                    acc.setBanned(t, !acc.isBanned(t));
                    showToast(acc.display(t) + (acc.isBanned(t) ? L.t(" ถูกแบนแล้ว", " banned") : L.t(" ถูกปลดแบนแล้ว", " unbanned")));
                },
                () -> Dialogs.ask(ctx, L.t("ตั้งรหัสผ่านใหม่", "Set new password"), acc.display(t), new String[]{L.t("รหัสผ่านใหม่", "New password")},
                        new int[]{Dialogs.PASSWORD}, null, "OK", v -> {
                            String err = acc.setPassword(t, v[0]);
                            if (err != null) return err;
                            showToast(L.t("ตั้งรหัสผ่านใหม่ให้ ", "New password set for ") + acc.display(t));
                            return null;
                        }),
                () -> adminDo(L.t("ปลดล็อกทุกด่านแล้ว", "All stages unlocked"), q::unlockAllStages),
                () -> adminDo(L.t("ปลดล็อกดาวทั้งหมดแล้ว", "All planets unlocked"), q::unlockAllPlanets),
                () -> Dialogs.confirm(ctx, L.t("รีเซ็ตความคืบหน้า", "Reset progress"),
                        L.t("ลบความคืบหน้าทั้งหมดของ ", "Erase all progress of ") + acc.display(t) + L.t(" ใช่ไหม? ย้อนกลับไม่ได้", "? This can't be undone."),
                        L.t("รีเซ็ต", "Reset"), () -> adminDo(L.t("รีเซ็ตแล้ว", "Progress reset"), q::reset)),
                () -> {
                    if (tAdmin || t.equals(user)) { showToast(L.t("ลบบัญชีนี้ไม่ได้", "This account can't be deleted")); return; }
                    Dialogs.confirm(ctx, L.t("ลบบัญชี", "Delete account"),
                            L.t("ลบบัญชี ", "Delete account ") + acc.display(t) + L.t(" ถาวรใช่ไหม? ย้อนกลับไม่ได้", " permanently? This can't be undone."),
                            L.t("ลบ", "Delete"), () -> {
                                String err = acc.delete(t);
                                profCache.remove(t);
                                adminSel = user;
                                showToast(err != null ? err : L.t("ลบบัญชีแล้ว", "Account deleted"));
                            });
                }};
        for (int i = 0; i < labels.length; i++) {
            float x = 40 * u + (i % 2) * (bw + 30 * u), yy = y + (i / 2) * (bh + gap);
            rf.set(x, yy, x + bw, yy + bh);
            boolean en = !((i == 4 || i == 9) && (tAdmin || (i == 9 && t.equals(user))));
            button(c, rf, labels[i], colors[i], en, acts[i]);
        }
        y += 5 * (bh + gap) + 20 * u;
        // ---- admin tools
        text(c, L.t("เครื่องมือแอดมิน", "Admin tools"), 50 * u, y + 40 * u, 42 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
        y += 70 * u;
        boolean inf = acc.infiniteSP();
        rf.set(40 * u, y, 40 * u + bw, y + bh);
        button(c, rf, L.t("SP ไม่จำกัด: ", "Infinite SP: ") + (inf ? "ON" : "OFF"), inf ? 0xFFFF8A2B : 0xFF3A3360, true, () -> acc.setInfiniteSP(!acc.infiniteSP()));
        rf.set(70 * u + bw, y, 70 * u + bw * 2, y + bh);
        button(c, rf, L.t("เปลี่ยนรหัสแอดมิน", "Change admin password"), 0xFF4F8BFF, true, this::askChangePassword);
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
        int lane = Defs.ST_COLOR[g.stage];
        sp.setPathEffect(null);
        sp.setStrokeWidth(150);
        sp.setColor(alpha(lane, 0x22));
        c.drawPath(pathCache, sp);
        sp.setStrokeWidth(112);
        sp.setColor(alpha(lane, 0x44));
        c.drawPath(pathCache, sp);
        sp.setStrokeWidth(100);
        sp.setColor(0xFF07031A);
        c.drawPath(pathCache, sp);
        sp.setStrokeWidth(92);
        sp.setColor(lighten(lane, 0.2f));
        c.drawPath(pathCache, sp);
        sp.setStrokeWidth(84);
        sp.setColor(0xFF140A36);
        c.drawPath(pathCache, sp);
        sp.setStrokeWidth(60);
        sp.setColor(alpha(darken(lane, 0.45f), 0xCC));
        c.drawPath(pathCache, sp);
        sp.setStrokeWidth(6);
        sp.setColor(alpha(lighten(lane, 0.6f), 0xAA));
        sp.setPathEffect(new DashPathEffect(new float[]{30, 26}, -anim * 90));
        c.drawPath(pathCache, sp);
        sp.setPathEffect(null);
        // flowing chevrons toward the base
        float step = 140, off = (anim * 90) % step;
        sp.setStrokeWidth(7);
        for (float d = off; d < g.total - 60; d += step) {
            g.pointAt(d, tmp);
            float x = tmp[0], y = tmp[1];
            g.pointAt(d + 4, tmp);
            float ang = (float) Math.toDegrees(Math.atan2(tmp[1] - y, tmp[0] - x));
            float fade = Math.min(1f, Math.min(d, g.total - d) / 200f);
            sp.setColor(alpha(lighten(lane, 0.7f), (int) (150 * fade)));
            c.save();
            c.translate(x, y);
            c.rotate(ang);
            c.drawLine(-8, -16, 8, 0, sp);
            c.drawLine(8, 0, -8, 16, sp);
            c.restore();
        }

        // portal (start)
        float sx = g.px[0], sy = g.py[0];
        glowCircle(c, sx, sy, 120, 0x88B86BFF);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new RadialGradient(sx, sy, 62, new int[]{0xFF000000, 0xFF2A0A4A, 0xFF8A3FD0, 0x00B86BFF},
                new float[]{0f, 0.45f, 0.8f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(sx, sy, 62, p);
        p.setShader(null);
        for (int i = 0; i < 4; i++) {
            sp.setStrokeWidth(7 - i);
            sp.setColor(i % 2 == 0 ? 0xDDE6B8FF : 0xDD4FE0FF);
            float rr = 22 + i * 13;
            rf.set(sx - rr, sy - rr, sx + rr, sy + rr);
            c.drawArc(rf, anim * (160 + i * 50) * (i % 2 == 0 ? 1 : -1) + i * 40, 200, false, sp);
        }

        // base crystal (end)
        float bx = g.px[g.px.length - 1], by = g.py[g.py.length - 1];
        float pulse = 1f + 0.05f * (float) Math.sin(anim * 3);
        glowCircle(c, bx, by, 140, 0x885BC8FF);
        // platform
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(0, by + 20, 0, by + 60, 0xFF3A4A8A, 0xFF141A3A, Shader.TileMode.CLAMP));
        c.drawOval(new RectF(bx - 70, by + 18, bx + 70, by + 62), p);
        p.setShader(null);
        sp.setStrokeWidth(4);
        sp.setColor(0xFF7DE6FF);
        c.drawOval(new RectF(bx - 70, by + 18, bx + 70, by + 62), sp);
        // orbit rings
        for (int i = 0; i < 2; i++) {
            c.save();
            c.rotate(i == 0 ? anim * 40 : -anim * 30 + 60, bx, by);
            sp.setStrokeWidth(3);
            sp.setColor(i == 0 ? 0xAA9BEFFF : 0x88E6B8FF);
            c.drawOval(new RectF(bx - 78, by - 26, bx + 78, by + 26), sp);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(bx + 78, by, 5, p);
            c.restore();
        }
        float h = 64 * pulse, w = 40 * pulse;
        Path cr = new Path();
        cr.moveTo(bx, by - h);
        cr.lineTo(bx + w, by - h * 0.2f);
        cr.lineTo(bx, by + h * 0.75f);
        cr.lineTo(bx - w, by - h * 0.2f);
        cr.close();
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(bx - w, by - h, bx + w, by + h, 0xFFC8F6FF, 0xFF2F7FD8, Shader.TileMode.CLAMP));
        c.drawPath(cr, p);
        p.setShader(null);
        Path f1 = new Path();
        f1.moveTo(bx, by - h);
        f1.lineTo(bx + w, by - h * 0.2f);
        f1.lineTo(bx, by - h * 0.05f);
        f1.close();
        p.setColor(0x88FFFFFF);
        c.drawPath(f1, p);
        Path f2 = new Path();
        f2.moveTo(bx, by - h * 0.05f);
        f2.lineTo(bx + w, by - h * 0.2f);
        f2.lineTo(bx, by + h * 0.75f);
        f2.close();
        p.setColor(0x55102060);
        c.drawPath(f2, p);
        sp.setStrokeWidth(5);
        sp.setColor(0xFF0B0520);
        c.drawPath(cr, sp);
        p.setColor(0xFFFFFFFF);
        c.save();
        c.translate(bx - w * 0.35f, by - h * 0.45f);
        c.rotate(anim * 50);
        c.scale(10 * pulse, 10 * pulse);
        c.drawPath(Art.starPath(0, 0, 1f, 0.25f, 4), p);
        c.restore();
        // base hp ring
        float frac = g.baseHp / (float) g.maxBaseHp;
        sp.setStrokeWidth(10);
        sp.setColor(0x66000000);
        rf.set(bx - 96, by - 96, bx + 96, by + 96);
        c.drawArc(rf, 0, 360, false, sp);
        sp.setColor(frac > 0.5f ? 0xFF7CFF8A : (frac > 0.25f ? 0xFFFFD23F : 0xFFFF4D5E));
        c.drawArc(rf, -90, 360 * frac, false, sp);
    }

    void drawSlots(Canvas c, Game g) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(anim * 5);
        for (int i = 0; i < g.slotX.length; i++) {
            float x = g.slotX[i], y = g.slotY[i];
            boolean hl = buildSel >= 0 && g.slots[i] == null;
            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            p.setColor(0x66000000);
            c.drawCircle(x, y + 8, 60, p);
            p.setColor(0xFFFFFFFF);
            p.setShader(new RadialGradient(x, y - 10, 70, 0xFF2E2466, 0xFF0E0828, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, 58, p);
            p.setShader(null);
            sp.setPathEffect(null);
            sp.setStrokeWidth(6);
            sp.setColor(0xFF0B0520);
            c.drawCircle(x, y, 58, sp);
            sp.setStrokeWidth(3);
            sp.setColor(hl ? alpha(0xFFFFD23F, (int) (130 + 125 * pulse)) : 0x998F80D0);
            c.drawCircle(x, y, 53, sp);
            sp.setColor(0x33FFFFFF);
            sp.setStrokeWidth(2);
            c.drawCircle(x, y, 40, sp);
            sp.setStrokeWidth(4);
            sp.setColor(hl ? 0xFFFFD23F : 0x668F80D0);
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2 + Math.PI / 4;
                float cx = (float) Math.cos(a), sy = (float) Math.sin(a);
                c.drawLine(x + cx * 44, y + sy * 44, x + cx * 52, y + sy * 52, sp);
            }
            if (hl) {
                glowCircle(c, x, y, 70, alpha(0xFFFFD23F, (int) (60 * pulse)));
                sp.setStrokeWidth(8);
                sp.setColor(0xFFFFD23F);
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
        if (pl.enchant == Defs.EN_GIANTTIC) sc *= 1.15f;
        if (pl.enchant == Defs.EN_GALAXIA || pl.enchant == Defs.EN_MEGALONIA) sc *= 1.1f;
        sc *= 1f + Math.max(0, pl.flash) * 0.4f;
        float bob = (float) Math.sin(pl.anim * 2.2f + pl.slot) * 3f;
        float x = pl.x, y = pl.y + bob;
        float half = 64 * sc;
        boolean off = pl.disabledT > 0;
        if (pl.enchant == Defs.EN_GALAXIA || pl.enchant == Defs.EN_MEGALONIA) {
            sp.setPathEffect(null);
            sp.setStrokeWidth(8);
            for (int i = 0; i < 3; i++) {
                sp.setColor(alpha(pl.enchant == Defs.EN_GALAXIA ? hsv(anim * 150 + i * 120) : 0xFFFF4F8B, 0xAA));
                rf.set(x - 58 - i * 6, y - 58 - i * 6, x + 58 + i * 6, y + 58 + i * 6);
                c.drawArc(rf, anim * 200 + i * 120, 90, false, sp);
            }
        }
        if (pl.material >= 0) materialAura(c, x, y, 46 * sc, pl.material);
        sprite(c, pBmp[pl.type], x, y, half, off ? 110 : 255);
        if (off) {
            sp.setStrokeWidth(7);
            sp.setColor(0xCCB86BFF);
            for (int i = 0; i < 2; i++) {
                rf.set(x - 30 - i * 14, y - 30 - i * 14, x + 30 + i * 14, y + 30 + i * 14);
                c.drawArc(rf, -anim * 300 + i * 180, 200, false, sp);
            }
        }
        for (int m = 0; m < pl.moons; m++) {
            double a = pl.orbitA + m * Math.PI * 2 / pl.moons;
            sprite(c, pBmp[Defs.MOON], pl.x + (float) Math.cos(a) * 62, pl.y + (float) Math.sin(a) * 62, 20, 255);
        }
        for (int i = 0; i < pl.level; i++) {
            float lx = x - (pl.level - 1) * 11 + i * 22;
            star(c, lx, pl.y + 56, 11);
        }
        badge(c, x + 44 * sc, y - 42 * sc, 18, pl.enchant, pl.tier);
        if (pl.material >= 0) materialBadge(c, x - 44 * sc, y - 42 * sc, 17, pl.material);
    }

    void blackHole(Canvas c, float x, float y, float r) {
        glowCircle(c, x, y, r * 2.4f, 0x99A43CFF);
        glowCircle(c, x, y, r * 1.5f, 0x66FF7A3C);
        int[] cols = {0x66FF4FD8, 0xCCFF8A3C, 0xFFFFE0A0};
        float[] ws = {0.32f, 0.16f, 0.06f};
        float tilt = -18;
        // back half of the accretion disk
        for (int i = 0; i < 3; i++) {
            sp.setPathEffect(null);
            sp.setStrokeWidth(r * ws[i]);
            sp.setColor(cols[i]);
            rf.set(x - r * 1.45f, y - r * 0.42f, x + r * 1.45f, y + r * 0.42f);
            c.save();
            c.rotate(tilt, x, y);
            c.drawArc(rf, 180, 180, false, sp);
            c.restore();
        }
        // lensed light above the hole
        sp.setStrokeWidth(r * 0.1f);
        sp.setColor(0xAAFFC88A);
        rf.set(x - r * 0.8f, y - r * 0.8f, x + r * 0.8f, y + r * 0.8f);
        c.drawArc(rf, 200, 140, false, sp);
        // event horizon + photon ring
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(0xFF000000);
        c.drawCircle(x, y, r * 0.62f, p);
        sp.setStrokeWidth(r * 0.14f);
        sp.setColor(0x66FFB060);
        c.drawCircle(x, y, r * 0.66f, sp);
        sp.setStrokeWidth(r * 0.04f);
        sp.setColor(0xFFFFF4D8);
        c.drawCircle(x, y, r * 0.64f, sp);
        // front half of the disk (brighter on the approaching side)
        for (int i = 0; i < 3; i++) {
            sp.setStrokeWidth(r * ws[i]);
            sp.setColor(cols[i]);
            rf.set(x - r * 1.45f, y - r * 0.42f, x + r * 1.45f, y + r * 0.42f);
            c.save();
            c.rotate(tilt, x, y);
            c.drawArc(rf, 0, 180, false, sp);
            c.restore();
        }
        sp.setStrokeWidth(r * 0.08f);
        sp.setColor(0xCCFFFFFF);
        c.save();
        c.rotate(tilt, x, y);
        c.drawArc(rf, 120, 50, false, sp);
        c.restore();
        // infalling sparks
        p.setColor(0xFFFFE0A0);
        for (int i = 0; i < 6; i++) {
            double a = anim * (1.5 + i * 0.2) + i * 1.05;
            float rr = r * (1.0f + (float) ((anim * 0.5 + i * 0.17) % 1.0) * 0.6f);
            float px = x + (float) Math.cos(a) * rr, py = y + (float) Math.sin(a) * rr * 0.32f;
            c.drawCircle(px, py, r * 0.04f, p);
        }
    }

    void drawEnemy(Canvas c, Game g, Enemy e) {
        if (e.dead) return;
        int t = e.type;
        boolean fiery = t == Defs.E_METEOR || t == Defs.E_MEGA || t == Defs.E_SHOOT || t == Defs.E_HELL;
        if (fiery) {
            float len = e.radius * (t == Defs.E_SHOOT ? 5.5f : 3.2f);
            g.pointAt(e.d - len, tmp);
            float tx = tmp[0], ty = tmp[1];
            float dx = e.x - tx, dy = e.y - ty, l = Math.max(1, (float) Math.sqrt(dx * dx + dy * dy));
            float nx = -dy / l, ny = dx / l, w = e.radius * (t == Defs.E_SHOOT ? 0.7f : 0.95f);
            Path tr = new Path();
            tr.moveTo(e.x + nx * w, e.y + ny * w);
            tr.lineTo(tx, ty);
            tr.lineTo(e.x - nx * w, e.y - ny * w);
            tr.close();
            int c1 = t == Defs.E_SHOOT ? 0xEEFFF6C8 : (t == Defs.E_HELL ? 0xDDFF3B1F : 0xDDFF8A2B);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            p.setShader(new LinearGradient(e.x, e.y, tx, ty, c1, c1 & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawPath(tr, p);
            Path core = new Path();
            core.moveTo(e.x + nx * w * 0.45f, e.y + ny * w * 0.45f);
            core.lineTo(e.x + (tx - e.x) * 0.55f, e.y + (ty - e.y) * 0.55f);
            core.lineTo(e.x - nx * w * 0.45f, e.y - ny * w * 0.45f);
            core.close();
            p.setShader(new LinearGradient(e.x, e.y, tx, ty, 0xEEFFF2B0, 0x00FFF2B0, Shader.TileMode.CLAMP));
            c.drawPath(core, p);
            p.setShader(null);
            glowCircle(c, e.x, e.y, e.radius * 1.7f, alpha(c1, 0x66));
        }
        if (t == Defs.E_BLACKHOLE) {
            blackHole(c, e.x, e.y, e.radius * 0.75f);
        } else {
            int alpha = e.phased ? 70 : 255;
            c.save();
            boolean rock = t == Defs.E_METEOR || t == Defs.E_MEGA;
            c.rotate(rock ? e.angle * 1.5f : (t == Defs.E_SHOOT ? e.angle * 3 : (float) Math.sin(e.angle * 0.03f) * 12f), e.x, e.y);
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
        if (e.hitFlash > 0) glowCircle(c, e.x, e.y, e.radius * 1.2f, 0x99FFFFFF);
        if (e.slow > 0) {
            glowCircle(c, e.x, e.y, e.radius * 1.4f, 0x447DE6FF);
            sp.setStrokeWidth(3);
            sp.setColor(0xAA9BEFFF);
            c.drawCircle(e.x, e.y, e.radius + 6, sp);
        }
        if (e.stunT > 0) {
            for (int i = 0; i < 3; i++) {
                double a = anim * 6 + i * 2.1;
                star(c, e.x + (float) Math.cos(a) * e.radius * 0.8f, e.y - e.radius - 6 + (float) Math.sin(a) * 6, 7);
            }
        }
        if (e.shield > 0) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            p.setShader(new RadialGradient(e.x, e.y, e.radius + 14, new int[]{0x007CFFD8, 0x007CFFD8, 0x887CFFD8},
                    new float[]{0f, 0.7f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(e.x, e.y, e.radius + 14, p);
            p.setShader(null);
            sp.setStrokeWidth(3);
            sp.setColor(0xCC9BFFE6);
            c.drawCircle(e.x, e.y, e.radius + 14, sp);
        }
        // hp bar
        boolean big = e.boss || e.mini;
        float bw = big ? 170 : Math.max(54, e.radius * 2f), bh = big ? 14 : 9;
        float bx = e.x - bw / 2, by = e.y - e.radius - (big ? 44 : 22);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xDD0B0520);
        rf.set(bx - 3, by - 3, bx + bw + 3, by + bh + 3);
        c.drawRoundRect(rf, bh, bh, p);
        float frac = Math.max(0, e.hp / e.maxHp);
        int hc = e.boss ? 0xFFB86BFF : e.mini ? 0xFFFF8A2B : (frac > 0.5f ? 0xFF7CFF8A : 0xFFFF6B5B);
        if (frac > 0) {
            rf.set(bx, by, bx + bw * frac, by + bh);
            p.setColor(0xFFFFFFFF);
            p.setShader(new LinearGradient(0, by, 0, by + bh, lighten(hc, 0.35f), darken(hc, 0.2f), Shader.TileMode.CLAMP));
            c.drawRoundRect(rf, bh / 2, bh / 2, p);
            p.setShader(null);
        }
        if (e.maxShield > 0 && e.shield > 0) {
            p.setColor(0xFF7DE6FF);
            rf.set(bx, by - bh * 0.7f, bx + bw * (e.shield / e.maxShield), by - 2);
            c.drawRoundRect(rf, 3, 3, p);
        }
    }

    void drawShot(Canvas c, Shot s) {
        int gc;
        switch (s.kind) {
            case Defs.K_MISSILE: gc = 0x88FFB060; break;
            case Defs.K_SMOKE: gc = 0x66FF3B2F; break;
            case Defs.K_FIREROCK: gc = 0x99FF7A2B; break;
            case Defs.K_TORNADO: gc = 0x55FFB060; break;
            case Defs.K_ICE: gc = 0x887DE6FF; break;
            default: gc = 0x55FFE0B0;
        }
        glowCircle(c, s.x, s.y, s.kind == Defs.K_TORNADO ? 60 : 26 * s.size, gc);
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
            if (h.summon >= 0) {
                glowCircle(c, x, y, 120, 0x885EF2FF);
                float rad = Defs.E_RADIUS[h.summon] * (h.summonBoss ? 0.75f : 1.2f);
                if (h.summon == Defs.E_BLACKHOLE) blackHole(c, x, y, rad);
                else {
                    c.save();
                    c.rotate(h.angle, x, y);
                    sprite(c, eBmp[h.summon], x, y, rad / 0.76f, 255);
                    c.restore();
                }
                sp.setPathEffect(null);
                sp.setStrokeWidth(5);
                sp.setColor(alpha(hsv(anim * 120), 0xDD));
                c.drawCircle(x, y, rad + 16, sp);
                float bw = 150;
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xDD0B0520);
                rf.set(x - bw / 2 - 3, y - rad - 40, x + bw / 2 + 3, y - rad - 26);
                c.drawRoundRect(rf, 6, 6, p);
                p.setColor(0xFF5EF2FF);
                rf.set(x - bw / 2, y - rad - 37, x - bw / 2 + bw * Math.max(0, h.hp / h.maxHp), y - rad - 29);
                c.drawRoundRect(rf, 4, 4, p);
                continue;
            }
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
                    glowCircle(c, f.x, f.y, f.size * a * 2.4f, alpha(f.color, (int) (110 * a)));
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(lighten(f.color, 0.4f));
                    p.setAlpha((int) (255 * a));
                    c.drawCircle(f.x, f.y, f.size * a * 0.8f, p);
                    break;
                case Fx.RING: {
                    float rr = f.size * (1.1f - a * 0.8f);
                    sp.setPathEffect(null);
                    sp.setStrokeWidth(16 * a + 4);
                    sp.setColor(alpha(f.color, (int) (90 * a)));
                    c.drawCircle(f.x, f.y, rr, sp);
                    sp.setStrokeWidth(5 * a + 1.5f);
                    sp.setColor(alpha(lighten(f.color, 0.5f), (int) (230 * a)));
                    c.drawCircle(f.x, f.y, rr, sp);
                    break;
                }
                case Fx.PUFF:
                    glowCircle(c, f.x, f.y, f.size * (1.6f - a * 0.5f), alpha(f.color, (int) (170 * a)));
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
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(0, 0, 0, top + h + 40 * u, 0xEE0A0424, 0x000A0424, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, top + h + 40 * u, p);
        p.setShader(null);
        rf.set(170 * u, top + 12 * u, 360 * u, top + h - 12 * u);
        panel(c, new RectF(rf), 0xCC2A1650, 38 * u);
        rf.set(W / 2 - 190 * u, top + 12 * u, W / 2 + 190 * u, top + h - 12 * u);
        panel(c, new RectF(rf), 0xCC2A1650, 38 * u);
        rf.set(W - 375 * u, top + 12 * u, W - 160 * u, top + h - 12 * u);
        panel(c, new RectF(rf), 0xCC3A2A10, 38 * u);
        rf.set(30 * u, top, 140 * u, top + h);
        button(c, rf, "II", 0xFF6B5BD6, true, () -> paused = !paused);
        heart(c, 215 * u, top + h / 2, 28 * u);
        text(c, String.valueOf(Math.max(0, g.baseHp)), 255 * u, top + h / 2 + 17 * u, 48 * u, 0xFFFFFFFF, Paint.Align.LEFT, 95 * u);
        title(c, L.t("เวฟ ", "WAVE ") + g.wave + "/" + g.maxWave, W / 2, top + h / 2 + 17 * u, 48 * u, 0xFFFFFFFF, Paint.Align.CENTER, 340 * u);
        star(c, W - 335 * u, top + h / 2, 28 * u);
        text(c, g.stars + " SP", W - 298 * u, top + h / 2 + 17 * u, 46 * u, 0xFFFFE27A, Paint.Align.LEFT, 125 * u);
        rf.set(W - 140 * u, top, W - 30 * u, top + h);
        button(c, rf, "x" + speed, speed > 1 ? 0xFFFF8A2B : 0xFF6B5BD6, true, () -> speed = speed % 3 + 1);
    }

    void drawBuild(Canvas c, Game g) {
        rf.set(-20 * u, panelTop, W + 20 * u, H + 40 * u);
        panel(c, new RectF(rf), 0xF2201248, 50 * u);
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
                rarityCard(c, r, 0xFF6A6488, false, false, 30 * u);
                text(c, L.t("ว่าง", "Empty"), r.centerX(), r.centerY() + 14 * u, 36 * u, 0xFF6A6488);
                continue;
            }
            int cost = Defs.PLACE_COST[type];
            boolean can = g.stars >= cost;
            int rc = Defs.RARITY_COLOR[Defs.P_RARITY[type]];
            rarityCard(c, r, rc, can, buildSel == i, 30 * u);
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
        panel(c, new RectF(rf), 0xF2201248, 50 * u);
        int rar = Defs.P_RARITY[pl.type];
        float x0 = 40 * u, y0 = panelTop + 30 * u;
        sprite(c, pBmp[pl.type], x0 + 90 * u, y0 + 100 * u, 95 * u, 255);
        float tx = x0 + 200 * u;
        text(c, Defs.P_NAME[pl.type] + "  Lv " + pl.level, tx, y0 + 50 * u, 50 * u, 0xFFFFFFFF, Paint.Align.LEFT, W - tx - 260 * u);
        text(c, Defs.RARITY_NAME[rar], W - 40 * u, y0 + 50 * u, 36 * u, Defs.RARITY_COLOR[rar], Paint.Align.RIGHT, 0);
        String stat;
        if (Game.isProducer(pl)) {
            stat = String.format(Locale.US, L.t("ผลิต %.1f SP ทุก %.1f วิ", "Makes %.1f SP every %.1fs"), Game.produceRate(pl), Game.cdOf(pl));
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
        if (pl.material >= 0) {
            materialBadge(c, tx + 360 * u, y0 + 140 * u, 20 * u, pl.material);
            plain(c, Defs.MAT_NAME[pl.material], tx + 392 * u, y0 + 152 * u, 34 * u, Defs.MAT_COLOR[pl.material], Paint.Align.LEFT, 0);
        }
        if (pl.moons > 1) plain(c, "Moons x" + pl.moons, W - 40 * u, y0 + 152 * u, 28 * u, 0xFFE6E8F5, Paint.Align.RIGHT, 0);
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
        star(c, W / 2 - 270 * u, r.top + 520 * u, 26 * u);
        text(c, "+" + g.earnStars, W / 2 - 232 * u, r.top + 536 * u, 46 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
        milliteIcon(c, W / 2 - 20 * u, r.top + 520 * u, 26 * u);
        text(c, "+" + g.earnMillite, W / 2 + 18 * u, r.top + 536 * u, 46 * u, 0xFFE2C6FF, Paint.Align.LEFT, 0);
        crystalIcon(c, W / 2 + 175 * u, r.top + 520 * u, 28 * u);
        text(c, "+" + g.earnCrystal, W / 2 + 212 * u, r.top + 536 * u, 46 * u, 0xFFB8F6FF, Paint.Align.LEFT, 0);
        final int st = g.stage;
        rf.set(r.left + 70 * u, r.top + 610 * u, r.right - 70 * u, r.top + 720 * u);
        button(c, rf, L.t("เล่นอีกครั้ง", "Play again"), 0xFFFF8A2B, true, () -> startStage(st));
        rf.set(r.left + 70 * u, r.top + 760 * u, r.right - 70 * u, r.top + 870 * u);
        button(c, rf, L.t("กลับล็อบบี้", "Back to lobby"), 0xFF6B5BD6, true, () -> { game = null; screen = S_LOBBY; });
    }
}
