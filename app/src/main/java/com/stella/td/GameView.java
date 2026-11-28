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

    static final String VERSION = "v3.7";
    /** must match versionCode in app/build.gradle */
    static final int VERSION_CODE = 19;

    // hub screens
    static final int S_LOBBY = 0, S_SHOP = 1, S_PLANET = 2, S_PLAYMENU = 3, S_BANNER = 4, S_ENCHANT = 5, S_GAME = 6,
            S_LOGIN = 7, S_ADMIN = 8, S_LEADER = 9, S_TERM = 10, S_STAFF = 11, S_SERVER = 12, S_LOGS = 13, S_INV = 14, S_EVENTS = 15, S_SOCIAL = 16;
    // popups
    static final int P_NONE = 0, P_PLAYER = 1, P_SETTINGS = 2, P_GACHA = 3, P_INDEX = 4, P_AUTO = 5, P_GIVE = 6,
            P_AVATAR = 7, P_CONSOLE = 8, P_BULKRES = 9, P_ROLE = 10, P_EQUIP = 11, P_INV = 12, P_RSHOP = 13, P_ITEMS = 14, P_RATES = 15, P_CRATES = 16, P_REVEAL = 17, P_LOGIN = 18;

    static final class Btn {
        final RectF r;
        final Runnable a;
        boolean net;
        Btn(RectF r, Runnable a) { this.r = r; this.a = a; }
    }

    final Context ctx;
    final Accounts acc;
    Profile pf;
    String user;
    String adminSel;
    final Cloud cloud;
    final Social social;
    boolean busy, dirty, pushing;
    String busyMsg = "";
    float pushAcc;
    // ---- always-online state
    static final int NET_CONNECTING = 0, NET_ONLINE = 1, NET_LOST = 2, NET_UPDATE = 3, NET_MAINT = 4, NET_NOSTAFF = 5;
    /** true in the separate "Stella Console" app (package name ends with .console) */
    final boolean consoleApp;
    // ---- server announcements and staff screens
    String bcMsg;
    long bcTime;
    float bcShowT;
    Cloud.Server serverInfo;
    boolean serverLoading, logsLoading;
    java.util.List<Cloud.LogLine> logLines = new java.util.ArrayList<>();
    String adminFilter = "";
    String roleTarget;
    String lastCommand;
    int net = NET_CONNECTING;
    String sessionId;
    float beatAcc;
    boolean beating, claimed, connecting;
    int onlineCount, pingMs, beatFails;
    float lostT;
    java.util.List<Cloud.Player> players = new java.util.ArrayList<>();
    boolean playersLoading;
    Profile adminProf;
    String adminProfUid;
    boolean adminLoading;
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
    boolean autoOn; // kept false: auto roll was replaced by bulk roll
    int autoTarget = -1, autoCount;
    // ---- bulk roll
    final boolean[] keepEn = new boolean[Defs.EN_COUNT], keepMat = new boolean[Defs.MAT_COUNT];
    final int[] keepTier = new int[Defs.EN_COUNT];
    int bulkCount = 10;
    boolean bulkRevert = true, bulkInit;
    // result of the last bulk roll
    int resRolls, resSpent, resGot = -1, resTier;
    boolean resHit, resMat, resReverted;
    final int[] resByRarity = new int[7];
    float autoT;
    // ---- UI animation clocks
    float transT = 9, popT = 9;
    int lastScreen = -1, lastPopup = -1;
    float enchFlash, playAcc, saveAcc;
    int labTab, leaderTab;

    float W, H, u, ms, mox, moy, mapTop, mapBot, panelTop, navTop;
    float anim;
    long lastNs;

    Bitmap bg, gBmp, earthBig, nebulaBmp;
    volatile boolean ready;
    volatile float loadProg;
    boolean loadStarted;
    int planetCat;
    final java.util.ArrayList<String> consoleLog = new java.util.ArrayList<>();
    final java.util.ArrayList<float[]> streaks = new java.util.ArrayList<>();
    float streakT;
    final java.util.Random fxr = new java.util.Random();
    final Bitmap[] pBmp = new Bitmap[Defs.P_COUNT];
    final Bitmap[] eBmp = new Bitmap[Defs.E_COUNT];
    int playGal = -1, playSel = -1;
    // planet editor in the admin panel
    int givePick = -1;
    boolean giveDirty;
    // ---- profile photos
    Runnable photoPicker;
    final java.util.HashMap<String, Bitmap> photoCache = new java.util.HashMap<>();

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
        Sfx.enabled = acc.sfxOn();
        Sfx.master = acc.volume() / 100f;
        Sfx.battle = acc.sfxBattle();
        Sfx.ui = acc.sfxUi();
        Sfx.init(ctx);
        cloud = new Cloud(ctx, this::post);
        social = new Social(cloud);
        consoleApp = ctx.getPackageName() != null && ctx.getPackageName().endsWith(".console");
        if (consoleApp) {
            cloud.sessionKey = "consoleSession";
            cloud.countPresence = false;
            cloud.console = true;
        }
        if (cloud.hasSession()) startSession(false);
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
        if (!loadStarted) {
            loadStarted = true;
            // realistic sprites are rendered pixel by pixel, so build them off the UI thread
            new Thread(() -> {
                int total = Defs.P_COUNT + Defs.E_COUNT + 1, done = 0;
                for (int i = 0; i < Defs.P_COUNT; i++) { pBmp[i] = Art.planet(i, 256); loadProg = ++done / (float) total; }
                for (int i = 0; i < Defs.E_COUNT; i++) {
                    if (i != Defs.E_BLACKHOLE) eBmp[i] = Art.enemy(i, 160);
                    loadProg = ++done / (float) total;
                }
                gBmp = Real.galaxy(320);
                nebulaBmp = Real.nebula(270, 585, 11);
                earthBig = Art.planet(Defs.EARTH, 720);
                loadProg = 1f;
                ready = true;
                postInvalidate();
            }).start();
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
    /** open the logged-in player's save (local copy first, then sync with the cloud) */
    void startSession(boolean brandNew) {
        user = cloud.name;
        pf = new Profile(ctx.getSharedPreferences("stella_cloud_" + cloud.uid, Context.MODE_PRIVATE));
        pf.onSave = () -> dirty = true;
        selType = enchSel = pf.deck[0] >= 0 ? pf.deck[0] : Defs.EARTH;
        game = null;
        popup = P_NONE;
        screen = consoleApp ? S_ADMIN : S_LOBBY;
        if (consoleApp) { adminSel = cloud.uid; refreshPlayers(); }
        lastSynced = 0;
        connecting = false;
        claimed = false;
        beating = false;
        net = NET_CONNECTING;
        sessionId = Long.toHexString(System.nanoTime()) + Integer.toHexString((int) (Math.random() * 1e9));
        connect(brandNew);
    }

    /** (re)connect: claim the session, download the save, then go online. The game is locked until this succeeds. */
    void connect(boolean brandNew) {
        if (pf == null || connecting) return;
        connecting = true;
        claimed = false;
        if (net != NET_LOST) net = NET_CONNECTING;
        final Profile mine = pf;
        cloud.claimSession(sessionId, (ok, err) -> {
            if (pf != mine) { connecting = false; return; }
            if (Cloud.isBanError(err)) { connecting = false; kicked(err); return; }
            if (err != null) { connecting = false; goLost(); return; }
            claimed = true;
            if (brandNew) { connecting = false; goOnline(); pushNow(); return; }
            cloud.loadMine((remote, err2) -> {
                connecting = false;
                if (pf != mine) return;
                if (Cloud.isBanError(err2)) { kicked(err2); return; }
                if (err2 != null) { claimed = false; goLost(); return; }
                long rt = Cloud.saveTimeOf(remote);
                lastSynced = rt;
                // the cloud wins when it is newer, or when this device only has an untouched starter profile
                boolean useRemote = remote != null && (rt > pf.saveTime || pf.saveTime == 0 || pf.pristine());
                if (useRemote) applyRemote(remote);
                goOnline();
                if (remote == null || (!useRemote && rt < pf.saveTime)) pushNow();
            });
        });
    }

    void goOnline() {
        net = consoleApp && cloud.myRole() < Roles.STAFF ? NET_NOSTAFF : NET_ONLINE;
        beatFails = 0;
        beatAcc = 99; // heartbeat right away
    }

    void goMaintenance() {
        net = NET_MAINT;
        if (screen == S_GAME && game != null && !game.won && !game.lost) paused = true;
    }

    void goLost() {
        if (net != NET_LOST) lostT = 0;
        net = NET_LOST;
        if (screen == S_GAME && game != null && !game.won && !game.lost) paused = true;
    }

    void beat() {
        if (pf == null || cloud.uid == null) return;
        if (!claimed) { connect(false); return; } // never finished connecting: start over instead
        if (beating) return;
        beating = true;
        final Profile mine = pf;
        cloud.heartbeat(sessionId, (b, err) -> {
            beating = false;
            if (pf != mine) return;
            if (err != null) {
                // one slow request is forgiven, two in a row means the connection is gone
                if (++beatFails >= 2 || net == NET_LOST) goLost();
                return;
            }
            beatFails = 0;
            pingMs = b.ping;
            onlineCount = Math.max(1, b.online);
            if (b.ban != null) { kicked(Cloud.friendly(new Exception(b.ban))); return; }
            if (b.kickedByStaff) { kicked(L.t("คุณถูกทีมงานเตะออกจากเกม", "You were kicked by a staff member")); return; }
            if (b.kickedElsewhere) { kicked(L.t("ไอดีนี้ถูกล็อกอินจากเครื่องอื่น", "This account logged in on another device")); return; }
            if (b.broadcast != null && b.broadcastTime != bcTime) {
                bcTime = b.broadcastTime;
                bcMsg = b.broadcast;
                if (System.currentTimeMillis() - bcTime < 86400000L) bcShowT = 9f;
            } else if (b.broadcast == null) bcMsg = null;
            if (consoleApp) {
                if (b.role < Roles.STAFF) { net = NET_NOSTAFF; return; }
                if (net == NET_NOSTAFF) net = NET_ONLINE;
            } else {
                if (b.minVersion > VERSION_CODE) { net = NET_UPDATE; return; }
                if (b.maintenance && b.role < Roles.STAFF) { goMaintenance(); return; }
                if (net == NET_MAINT) { net = NET_ONLINE; showToast(L.t("เซิร์ฟเวอร์เปิดแล้ว!", "The server is open again!")); return; }
            }
            if (net == NET_LOST) {
                net = NET_ONLINE;
                showToast(L.t("เชื่อมต่อกลับมาแล้ว", "Reconnected"));
                if (dirty) pushNow();
            } else if (net == NET_UPDATE) net = NET_ONLINE;
        });
    }

    long lastSynced;

    void applyRemote(java.util.Map<String, Object> remote) {
        Runnable hook = pf.onSave;
        pf.onSave = null;
        pf.fromMap(remote);
        pf.onSave = hook;
        dirty = false;
        lastSynced = pf.saveTime;
        if (pf.deck[0] >= 0) selType = enchSel = pf.deck[0];
    }

    void kicked(String msg) {
        cloud.logout();
        user = null;
        pf = null;
        game = null;
        popup = P_NONE;
        screen = S_LOGIN;
        net = NET_CONNECTING;
        showToast(msg);
    }

    void pushNow() {
        // the console app never uploads its own (possibly empty) local save
        if (pf == null || cloud.uid == null || pushing || !Cloud.configured() || consoleApp) return;
        pushing = true;
        dirty = false;
        final long st = pf.saveTime;
        final Profile mine = pf;
        cloud.sync(user, pf, lastSynced, (remote, err) -> {
            pushing = false;
            if (pf != mine) return;
            if (Cloud.isBanError(err)) { kicked(err); return; }
            if (err != null) { dirty = true; return; }
            if (remote != null) {
                // the cloud copy is newer (admin edit or another phone) -> load it instead of overwriting it
                if (screen != S_GAME) {
                    applyRemote(remote);
                    showToast(L.t("โหลดข้อมูลล่าสุดจากเซิร์ฟเวอร์แล้ว", "Loaded the latest save from the server"));
                } else dirty = true;
            } else lastSynced = st;
        });
    }

    void setBusy(String msg) {
        busy = msg != null;
        busyMsg = msg == null ? "" : msg;
    }

    /** only the Stella Console app (signed in as the console account) can manage the game */
    boolean isAdmin() {
        return consoleApp && pf != null && cloud.isAdmin();
    }

    void askLogin(String prefillUser) {
        if (!Cloud.configured()) { showToast(L.t("ยังไม่ได้ตั้งค่า Firebase (ดู README)", "Firebase is not set up yet (see README)")); return; }
        Dialogs.ask(ctx, L.t("เข้าสู่ระบบ", "Log in"), L.t("ยินดีต้อนรับกลับสู่ Stella TD", "Welcome back to Stella TD"),
                new String[]{L.t("ชื่อไอดี", "Username"), L.t("รหัสผ่าน", "Password")},
                new int[]{Dialogs.TEXT, Dialogs.PASSWORD}, new String[]{prefillUser, null},
                L.t("เข้าสู่ระบบ", "Log in"), v -> {
                    if (v[0].trim().isEmpty() || v[1].isEmpty()) return L.t("กรอกให้ครบทุกช่อง", "Fill in every field");
                    final String name = v[0];
                    setBusy(L.t("กำลังเข้าสู่ระบบ...", "Logging in..."));
                    cloud.signIn(name, v[1], (id, err) -> {
                        setBusy(null);
                        if (err != null) { showToast(err); askLogin(name); return; }
                        startSession(false);
                        showToast(L.t("ยินดีต้อนรับ ", "Welcome ") + user + "!");
                    });
                    return null;
                });
    }

    void askRegister() {
        if (!Cloud.configured()) { showToast(L.t("ยังไม่ได้ตั้งค่า Firebase (ดู README)", "Firebase is not set up yet (see README)")); return; }
        Dialogs.ask(ctx, L.t("สร้างไอดีใหม่", "Create account"), L.t("ชื่อไอดีใช้ a-z, 0-9, _ (3–16 ตัว) • รหัสผ่าน 6 ตัวขึ้นไป", "Username: a-z, 0-9, _ (3–16) • password 6+ characters"),
                new String[]{L.t("ชื่อไอดี", "Username"), L.t("รหัสผ่าน", "Password"), L.t("ยืนยันรหัสผ่าน", "Confirm password")},
                new int[]{Dialogs.TEXT, Dialogs.PASSWORD, Dialogs.PASSWORD}, null,
                L.t("สร้างไอดี", "Create"), v -> {
                    String e = Accounts.checkName(Accounts.norm(v[0]));
                    if (e != null) return e;
                    if (Accounts.norm(v[0]).equals(Cloud.CONSOLE_USER)) return L.t("ชื่อนี้ถูกใช้ไปแล้ว", "This name is already taken");
                    if (v[1].length() < 6) return L.t("รหัสผ่านต้องยาวอย่างน้อย 6 ตัว", "Password must be at least 6 characters");
                    if (!v[1].equals(v[2])) return L.t("รหัสผ่านทั้งสองช่องไม่ตรงกัน", "Passwords do not match");
                    final String name = v[0].trim();
                    setBusy(L.t("กำลังสร้างไอดี...", "Creating account..."));
                    cloud.signUp(name, v[1], (id, err) -> {
                        setBusy(null);
                        if (err != null) { showToast(err); return; }
                        startSession(true);
                        // bring over progress from a local (offline) account with the same name, if this phone has one
                        if (acc.exists(name)) {
                            Profile old = new Profile(acc.prefsOf(name));
                            pf.fromMap(old.toMap());
                            pf.onSave = () -> dirty = true;
                            pf.save();
                            showToast(L.t("ย้ายข้อมูลจากไอดีในเครื่องมาแล้ว!", "Moved your offline progress online!"));
                        } else showToast(L.t("สร้างไอดีสำเร็จ!", "Account created!"));
                        pushNow();
                    });
                    return null;
                });
    }

    void askChangePassword() {
        Dialogs.ask(ctx, L.t("เปลี่ยนรหัสผ่าน", "Change password"), null,
                new String[]{L.t("รหัสผ่านเดิม", "Current password"), L.t("รหัสผ่านใหม่", "New password"), L.t("ยืนยันรหัสผ่านใหม่", "Confirm new password")},
                new int[]{Dialogs.PASSWORD, Dialogs.PASSWORD, Dialogs.PASSWORD}, null,
                L.t("บันทึก", "Save"), v -> {
                    if (v[1].length() < 6) return L.t("รหัสผ่านต้องยาวอย่างน้อย 6 ตัว", "Password must be at least 6 characters");
                    if (!v[1].equals(v[2])) return L.t("รหัสผ่านใหม่ทั้งสองช่องไม่ตรงกัน", "New passwords do not match");
                    setBusy(L.t("กำลังเปลี่ยนรหัสผ่าน...", "Changing password..."));
                    cloud.changePassword(v[0], v[1], (ok, err) -> {
                        setBusy(null);
                        showToast(err != null ? err : L.t("เปลี่ยนรหัสผ่านแล้ว", "Password changed"));
                    });
                    return null;
                });
    }

    void logout() {
        loginShown = false;
        // upload the latest save first, so my leaderboard entry stays up to date after I leave
        if (pf != null) { pf.save(); pushNow(); }
        cloud.offline();
        net = NET_CONNECTING;
        cloud.logout();
        user = null;
        pf = null;
        game = null;
        popup = P_NONE;
        players.clear();
        adminProf = null;
        screen = S_LOGIN;
    }

    void pauseGame() {
        if (pf != null) { pf.save(); pushNow(); }
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

    void startChallenge(int kind) {
        int s = kind == 1 ? Defs.dailyStage(Profile.today()) : Defs.weeklyStage(Profile.week());
        startStage(s, kind);
    }

    void startStage(int s) { startStage(s, 0); }

    void startStage(int s, int challenge) {
        if (pf.deckCount() == 0) {
            showToast(L.t("ใส่ดวงดาวใน Deck ก่อน (เมนู Planet)", "Add planets to your deck first (Planet menu)"));
            return;
        }
        pf.heroUnlocked = false;
        pf.firstClear = false;
        popup = P_NONE;
        game = new Game(s, pf, challenge);
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
        pushNow();
    }

    void showToast(String s) {
        toast = s;
        toastT = 1.8f;
    }

    // ================================================================== touch
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                lastTouchY = y;
                dragging = false;
                scrollVel = 0;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && scrollOn && scrollView.contains(downX, downY) && Math.abs(y - downY) > 22 * u) dragging = true;
                if (dragging) {
                    float dy = y - lastTouchY;
                    scrollY -= dy;
                    scrollVel = -dy * 60;
                }
                lastTouchY = y;
                break;
            case MotionEvent.ACTION_UP:
                if (!dragging) {
                    tap(x, y);
                    performClick();
                }
                dragging = false;
                break;
            default:
        }
        return true;
    }

    // one scrolling list per screen: its window on screen, offset and limit
    final RectF scrollView = new RectF();
    boolean scrollOn, dragging;
    float scrollY, scrollMax, scrollVel, downX, downY, lastTouchY;
    int scrollOwner = -1;

    /** start a scroll window for this frame; content draws translated by -scrollY */
    void beginScroll(Canvas c, int owner, RectF view, float contentH, float dt) {
        if (scrollOwner != owner) { scrollOwner = owner; scrollY = 0; scrollVel = 0; }
        scrollOn = true;
        scrollView.set(view);
        scrollMax = Math.max(0, contentH - view.height());
        if (!dragging && scrollVel != 0) {
            scrollY += scrollVel * dt;
            scrollVel *= (float) Math.pow(0.04, dt);
            if (Math.abs(scrollVel) < 5) scrollVel = 0;
        }
        if (scrollY < 0) { scrollY = 0; scrollVel = 0; }
        if (scrollY > scrollMax) { scrollY = scrollMax; scrollVel = 0; }
        c.save();
        c.clipRect(view.left, view.top, view.right, view.bottom);
        c.translate(0, -scrollY);
    }

    void endScroll(Canvas c) {
        c.restore();
        if (scrollMax > 0) {
            // scrollbar
            float vh = scrollView.height(), bh = Math.max(60 * u, vh * vh / (vh + scrollMax));
            float by = scrollView.top + (vh - bh) * (scrollY / scrollMax);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x66FFFFFF);
            rf2.set(scrollView.right - 10 * u, by, scrollView.right - 3 * u, by + bh);
            c.drawRoundRect(rf2, 4 * u, 4 * u, p);
        }
    }

    /** a tappable rect inside the scroll window (given in content coordinates) */
    void scrollBtn(RectF r, Runnable a) {
        float top = r.top - scrollY, bot = r.bottom - scrollY;
        if (bot < scrollView.top || top > scrollView.bottom) return;
        btns.add(new Btn(new RectF(r.left, Math.max(top, scrollView.top), r.right, Math.min(bot, scrollView.bottom)), a));
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    float tapX, tapY;

    void tap(float x, float y) {
        tapX = x;
        tapY = y;
        if (busy) return;
        if (pf != null && net != NET_ONLINE) {
            // only the buttons of the connection screen work while offline
            for (int i = btns.size() - 1; i >= 0; i--) if (btns.get(i).net && btns.get(i).r.contains(x, y)) { btns.get(i).a.run(); return; }
            return;
        }
        for (int i = btns.size() - 1; i >= 0; i--) {
            Btn b = btns.get(i);
            if (b.r.contains(x, y)) {
                Sfx.play(Sfx.CLICK);
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
        transT += dt;
        popT += dt;
        if (bcShowT > 0) bcShowT -= dt;
        if (screen != lastScreen) { lastScreen = screen; transT = 0; }
        if (popup != lastPopup) { lastPopup = popup; popT = 0; }
        if (toastT > 0) toastT -= dt;
        if (gachaT < 10) gachaT += dt;
        if (enchFlash > 0) enchFlash -= dt;
        if (pf != null) {
            // only count and save once the cloud save has been loaded, so nothing local can outrank it
            if (net == NET_ONLINE) {
                playAcc += dt;
                if (playAcc >= 1f) { int s = (int) playAcc; pf.playSec += s; playAcc -= s; }
                saveAcc += dt;
                if (saveAcc > 15f) { saveAcc = 0; pf.save(); }
            }
            pushAcc += dt;
            if (net == NET_ONLINE && dirty && pushAcc > 20f && screen != S_GAME) { pushAcc = 0; pushNow(); }
            beatAcc += dt;
            float every = net == NET_ONLINE ? 12f : 4f;
            if (net != NET_CONNECTING && beatAcc > every) { beatAcc = 0; beat(); }
            if (net == NET_LOST) lostT += dt;
        }
        updateAuto(dt);
        coopTick(dt);
        boolean locked = pf != null && net != NET_ONLINE;
        if (screen == S_GAME && game != null && !paused && !locked) {
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
        scrollOn = false;
        if (consoleApp) consoleBg(c);
        else {
            if (bg != null) c.drawBitmap(bg, 0, 0, null);
            twinkles(c);
        }
        if (!ready) {
            drawLoading(c);
            postInvalidateOnAnimation();
            return;
        }
        if (screen != S_GAME && !consoleApp) meteorShower(c, dt);
        // screen entrance: slide up + fade in
        float te = ease(Math.min(1f, transT / 0.35f));
        c.save();
        if (te < 1f && screen != S_GAME) c.translate(0, (1f - te) * 90 * u);
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
            case S_TERM: drawTerm(c); break;
            case S_STAFF: drawStaff(c); break;
            case S_SERVER: drawServer(c); break;
            case S_LOGS: drawLogs(c); break;
            case S_INV: drawInvScreen(c); break;
            case S_EVENTS: drawEvents(c); break;
            case S_SOCIAL: drawSocial(c); break;
            default:
                if (game != null) drawPlay(c);
                else screen = S_PLAYMENU;
                break;
        }
        c.restore();
        if (te < 1f && screen != S_GAME) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(((int) ((1f - te) * 200) << 24) | 0x0A0420);
            c.drawRect(0, 0, W, H, p);
        }
        if (screen != S_GAME && screen != S_LOGIN && screen != S_LEADER && !staffScreen(screen)) drawNav(c);
        // popup entrance: pop with a little overshoot
        float pe = Math.min(1f, popT / 0.28f);
        float ps = popup != P_NONE && pe < 1f ? 0.82f + 0.18f * backOut(pe) : 1f;
        c.save();
        if (ps != 1f) c.scale(ps, ps, W / 2, H / 2);
        switch (popup) {
            case P_PLAYER: drawPlayerInfo(c); break;
            case P_SETTINGS: drawSettings(c); break;
            case P_GACHA: drawGachaResult(c); break;
            case P_INDEX: drawIndex(c); break;
            case P_AUTO: drawAutoPick(c); break;
            case P_BULKRES: drawBulkResult(c); break;
            case P_GIVE: drawGivePlanet(c); break;
            case P_AVATAR: drawAvatarPick(c); break;
            case P_CONSOLE: popup = P_NONE; go(S_TERM); break;
            case P_ROLE: drawRolePick(c); break;
            case P_EQUIP: drawEquip(c); break;
            case P_INV: popup = P_NONE; go(S_INV); break;
            case P_CRATES: drawCapsuleRates(c); break;
            case P_RSHOP: drawRaidShop(c); break;
            case P_ITEMS: drawGiveItems(c); break;
            case P_RATES: drawRates(c); break;
            case P_REVEAL: drawReveal(c); break;
            case P_LOGIN: drawLoginPopup(c); break;
            default: break;
        }
        c.restore();
        if (pf != null && net != NET_ONLINE && ready) drawNetLock(c);
        if (bcShowT > 0 && bcMsg != null && pf != null) drawBroadcast(c);
        if (busy) {
            dim(c);
            glowCircle(c, W / 2, H / 2, 200 * u, 0x66B86BFF);
            sp.setPathEffect(null);
            sp.setStrokeWidth(10 * u);
            sp.setColor(0xFF4FE0FF);
            rf.set(W / 2 - 70 * u, H / 2 - 70 * u, W / 2 + 70 * u, H / 2 + 70 * u);
            c.drawArc(rf, anim * 360, 260, false, sp);
            text(c, busyMsg, W / 2, H / 2 + 150 * u, 40 * u, 0xFFFFFFFF);
        }
        if (toastT > 0 && toast != null) {
            float y = screen == S_GAME ? panelTop - 60 * u : navTop - 60 * u;
            pill(c, W / 2, y, toast, 40 * u, 0xFFFFFFFF, 0xEE2A1650);
        }
        postInvalidateOnAnimation();
    }

    /** small "● 12 online • 85 ms" status pill */
    void netPill(Canvas c, float cx, float cy) {
        if (net != NET_ONLINE) return;
        String s = L.t("ออนไลน์ ", "Online ") + onlineCount + L.t(" คน", "") + "  •  " + pingMs + " ms";
        tp.setTextSize(26 * u);
        tp.setTypeface(bodyFont);
        float w = tp.measureText(s) + 80 * u;
        rf.set(cx - w / 2, cy - 24 * u, cx + w / 2, cy + 24 * u);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xAA120A2A);
        c.drawRoundRect(rf, 24 * u, 24 * u, p);
        int dot = pingMs < 150 ? 0xFF4DFF8A : pingMs < 400 ? 0xFFFFD23F : 0xFFFF6B5B;
        float pulse = 0.5f + 0.5f * (float) Math.sin(anim * 4);
        p.setColor(alpha(dot, (int) (60 + 80 * pulse)));
        c.drawCircle(rf.left + 30 * u, cy, 13 * u, p);
        p.setColor(dot);
        c.drawCircle(rf.left + 30 * u, cy, 7 * u, p);
        plain(c, s, rf.left + 50 * u, cy + 9 * u, 26 * u, 0xFFE6E0FF, Paint.Align.LEFT, 0);
    }

    /** full-screen lock shown while the game has no connection to the server */
    void drawNetLock(Canvas c) {
        dim(c);
        if (screen != S_GAME) dim(c);
        RectF r = new RectF(80 * u, H / 2 - 420 * u, W - 80 * u, H / 2 + 420 * u);
        panel(c, r, 0xF01C1240, 44 * u);
        float cx = W / 2, cy = r.top + 230 * u;
        if (net == NET_CONNECTING || (net == NET_LOST && connecting && lostT < 1)) {
            glowCircle(c, cx, cy, 170 * u, 0x664FE0FF);
            sp.setPathEffect(null);
            sp.setStrokeWidth(12 * u);
            sp.setColor(0xFF4FE0FF);
            rf.set(cx - 80 * u, cy - 80 * u, cx + 80 * u, cy + 80 * u);
            c.drawArc(rf, anim * 360, 260, false, sp);
            title(c, L.t("กำลังเชื่อมต่อเซิร์ฟเวอร์", "Connecting to server"), cx, r.top + 450 * u, 50 * u, 0xFFFFFFFF, Paint.Align.CENTER, r.width() - 60 * u);
            plain(c, L.t("กำลังโหลดข้อมูลของคุณ...", "Loading your save..."), cx, r.top + 520 * u, 32 * u, 0xFFB8F6FF, Paint.Align.CENTER, r.width() - 60 * u);
        } else if (net == NET_LOST) {
            // broken-signal icon
            glowCircle(c, cx, cy, 170 * u, 0x66FF6B5B);
            sp.setPathEffect(null);
            sp.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 3; i++) {
                float rr = (40 + i * 38) * u;
                sp.setStrokeWidth(14 * u);
                sp.setColor(i == 2 ? 0x55FFFFFF : 0xFFFF8A7A);
                rf.set(cx - rr, cy + 40 * u - rr, cx + rr, cy + 40 * u + rr);
                c.drawArc(rf, 225, 90, false, sp);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFF8A7A);
            c.drawCircle(cx, cy + 40 * u, 14 * u, p);
            sp.setStrokeWidth(12 * u);
            sp.setColor(0xFFFF4B5B);
            c.drawLine(cx - 110 * u, cy - 90 * u, cx + 110 * u, cy + 110 * u, sp);
            title(c, L.t("การเชื่อมต่อขาดหาย", "Connection lost"), cx, r.top + 450 * u, 52 * u, 0xFFFFD0D8, Paint.Align.CENTER, r.width() - 60 * u);
            String sub = screen == S_GAME && game != null && !game.won && !game.lost
                    ? L.t("ด่านถูกหยุดไว้ จะเล่นต่อได้เมื่อเน็ตกลับมา", "Match paused - it resumes when you're back online")
                    : L.t("เกมนี้ต้องใช้อินเทอร์เน็ต กรุณาตรวจ Wi-Fi หรือเน็ตมือถือ", "This game needs internet. Check Wi-Fi or mobile data");
            plain(c, sub, cx, r.top + 520 * u, 30 * u, 0xFFE6E0FF, Paint.Align.CENTER, r.width() - 60 * u);
            int dots = (int) (anim * 2) % 4;
            plain(c, L.t("กำลังลองเชื่อมต่อใหม่", "Reconnecting") + "....".substring(0, dots) + "  (" + (int) lostT + "s)",
                    cx, r.top + 580 * u, 28 * u, 0xFF9C94C8, Paint.Align.CENTER, 0);
            rf.set(r.left + 60 * u, r.bottom - 200 * u, cx - 15 * u, r.bottom - 90 * u);
            netButton(c, rf, L.t("ลองใหม่", "Retry"), 0xFF33A8D0, () -> { beatAcc = 0; beat(); });
            rf.set(cx + 15 * u, r.bottom - 200 * u, r.right - 60 * u, r.bottom - 90 * u);
            netButton(c, rf, L.t("ออกจากระบบ", "Log out"), 0xFF6B5BD6, this::logout);
        } else if (net == NET_MAINT) {
            glowCircle(c, cx, cy, 170 * u, 0x66FFB627);
            wrench(c, cx, cy, 90 * u);
            title(c, L.t("ปิดปรับปรุงเซิร์ฟเวอร์", "Server maintenance"), cx, r.top + 450 * u, 52 * u, 0xFFFFE27A, Paint.Align.CENTER, r.width() - 60 * u);
            plain(c, L.t("ทีมงานกำลังอัปเดตเกม กลับมาใหม่อีกสักครู่นะ", "The team is updating the game, please come back soon"), cx, r.top + 520 * u, 30 * u, 0xFFE6E0FF, Paint.Align.CENTER, r.width() - 60 * u);
            if (bcMsg != null) plain(c, "\"" + bcMsg + "\"", cx, r.top + 580 * u, 28 * u, 0xFFB8F6FF, Paint.Align.CENTER, r.width() - 60 * u);
            rf.set(cx - 220 * u, r.bottom - 200 * u, cx + 220 * u, r.bottom - 90 * u);
            netButton(c, rf, L.t("ออกจากระบบ", "Log out"), 0xFF6B5BD6, this::logout);
        } else if (net == NET_NOSTAFF) {
            glowCircle(c, cx, cy, 170 * u, 0x66FF6B5B);
            shield(c, cx, cy, 90 * u);
            title(c, L.t("ไม่มีสิทธิ์เข้าใช้", "No access"), cx, r.top + 450 * u, 52 * u, 0xFFFFD0D8, Paint.Align.CENTER, r.width() - 60 * u);
            plain(c, L.t("แอป Console ใช้ได้เฉพาะทีมงาน (Staff ขึ้นไป)", "The Console app is for staff only (Staff and above)"), cx, r.top + 520 * u, 30 * u, 0xFFE6E0FF, Paint.Align.CENTER, r.width() - 60 * u);
            plain(c, L.t("ให้ Owner หรือ Admin มอบยศให้ก่อน", "Ask an Owner or Admin to give you a rank"), cx, r.top + 575 * u, 28 * u, 0xFF9C94C8, Paint.Align.CENTER, r.width() - 60 * u);
            rf.set(cx - 220 * u, r.bottom - 200 * u, cx + 220 * u, r.bottom - 90 * u);
            netButton(c, rf, L.t("ออกจากระบบ", "Log out"), 0xFF6B5BD6, this::logout);
        } else if (net == NET_UPDATE) {
            glowCircle(c, cx, cy, 170 * u, 0x66FFD23F);
            sprite(c, pBmp[Defs.SATURN], cx, cy, 110 * u, 255);
            title(c, L.t("มีเวอร์ชันใหม่!", "New version available!"), cx, r.top + 450 * u, 52 * u, 0xFFFFE27A, Paint.Align.CENTER, r.width() - 60 * u);
            plain(c, L.t("กรุณาอัปเดตเกมจากหน้า Releases บน GitHub ก่อนเล่นต่อ", "Please update the game from the GitHub Releases page to keep playing"),
                    cx, r.top + 520 * u, 30 * u, 0xFFE6E0FF, Paint.Align.CENTER, r.width() - 60 * u);
            plain(c, L.t("เวอร์ชันของคุณ: ", "Your version: ") + VERSION, cx, r.top + 580 * u, 28 * u, 0xFF9C94C8, Paint.Align.CENTER, 0);
            rf.set(cx - 220 * u, r.bottom - 200 * u, cx + 220 * u, r.bottom - 90 * u);
            netButton(c, rf, L.t("ออกจากระบบ", "Log out"), 0xFF6B5BD6, this::logout);
        }
    }

    void netButton(Canvas c, RectF r, String label, int color, Runnable a) {
        button(c, r, label, color, true, a);
        btns.get(btns.size() - 1).net = true;
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
        if (consoleApp) { flatPanel(c, r, color, rad); return; }
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
        if (r.width() > 160 * u && r.height() > 90 * u) ornaments(c, r, color, rad);
    }

    /** sci-fi corner brackets and rivets so panels never look flat */
    void ornaments(Canvas c, RectF r, int color, float rad) {
        float in = Math.max(10 * u, rad * 0.45f), len = Math.min(34 * u, Math.min(r.width(), r.height()) * 0.18f);
        sp.setPathEffect(null);
        sp.setStrokeWidth(4 * u);
        sp.setColor(alpha(lighten(color, 0.75f), 0xAA));
        float l = r.left + in, t = r.top + in, rr = r.right - in, b = r.bottom - in;
        c.drawLine(l, t, l + len, t, sp);
        c.drawLine(l, t, l, t + len, sp);
        c.drawLine(rr, t, rr - len, t, sp);
        c.drawLine(rr, t, rr, t + len, sp);
        c.drawLine(l, b, l + len, b, sp);
        c.drawLine(l, b, l, b - len, sp);
        c.drawLine(rr, b, rr - len, b, sp);
        c.drawLine(rr, b, rr, b - len, sp);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(alpha(lighten(color, 0.6f), 0x88));
        float d = 9 * u;
        c.drawCircle(l + len + d, t, 3 * u, p);
        c.drawCircle(rr - len - d, b, 3 * u, p);
    }

    // ================================================================== loading + shooting stars
    void drawLoading(Canvas c) {
        float cy = H * 0.42f;
        glowCircle(c, W / 2, cy, 300 * u, 0x66B86BFF);
        sp.setPathEffect(null);
        for (int i = 0; i < 3; i++) {
            sp.setStrokeWidth((10 - i * 3) * u);
            sp.setColor(i == 1 ? 0xCC4FE0FF : 0xCCE6B8FF);
            float rr = (90 + i * 40) * u;
            rf.set(W / 2 - rr, cy - rr, W / 2 + rr, cy + rr);
            c.drawArc(rf, anim * (200 - i * 60) * (i % 2 == 0 ? 1 : -1), 240, false, sp);
        }
        logo(c, W / 2, H * 0.22f, 160 * u);
        float bw = W * 0.6f, by = H * 0.66f;
        rf.set(W / 2 - bw / 2, by, W / 2 + bw / 2, by + 34 * u);
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(0xCC140A33);
        c.drawRoundRect(rf, 17 * u, 17 * u, p);
        rf2.set(rf.left + 5 * u, rf.top + 5 * u, rf.left + 5 * u + (bw - 10 * u) * Math.max(0.03f, loadProg), rf.bottom - 5 * u);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(rf2.left, 0, rf.right, 0, 0xFFB86BFF, 0xFF4FE0FF, Shader.TileMode.CLAMP));
        c.drawRoundRect(rf2, 12 * u, 12 * u, p);
        p.setShader(null);
        sp.setStrokeWidth(3 * u);
        sp.setColor(0x88FFFFFF);
        c.drawRoundRect(rf, 17 * u, 17 * u, sp);
        plain(c, L.t("กำลังสร้างจักรวาล... ", "Building the universe... ") + (int) (loadProg * 100) + "%", W / 2, by + 100 * u, 36 * u, 0xFFE6E0FF, Paint.Align.CENTER, 0);
    }

    /** endless, pretty shooting stars across every menu */
    void meteorShower(Canvas c, float dt) {
        streakT -= dt;
        if (streakT <= 0) {
            streakT = 0.35f + fxr.nextFloat() * 0.9f;
            float ang = (float) Math.toRadians(18 + fxr.nextFloat() * 22);
            float sp0 = (900 + fxr.nextFloat() * 900) * u;
            float sx = W * (0.3f + fxr.nextFloat() * 0.9f), sy = -40 * u + fxr.nextFloat() * H * 0.45f;
            int[] cols = {0xFFFFF2C8, 0xFFBFE6FF, 0xFFFFC8F0, 0xFFFFFFFF};
            streaks.add(new float[]{sx, sy, -(float) Math.cos(ang) * sp0, (float) Math.sin(ang) * sp0,
                    0.9f + fxr.nextFloat() * 0.6f, (120 + fxr.nextFloat() * 220) * u, cols[fxr.nextInt(cols.length)], 0});
        }
        for (int i = streaks.size() - 1; i >= 0; i--) {
            float[] s = streaks.get(i);
            s[7] += dt;
            s[0] += s[2] * dt;
            s[1] += s[3] * dt;
            float life = s[7] / s[4];
            if (life >= 1 || s[0] < -400 * u || s[1] > H + 200 * u) { streaks.remove(i); continue; }
            float fade = life < 0.15f ? life / 0.15f : (1 - life);
            float vl = (float) Math.sqrt(s[2] * s[2] + s[3] * s[3]);
            float tx = s[0] - s[2] / vl * s[5], ty = s[1] - s[3] / vl * s[5];
            int col = (int) s[6];
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            p.setShader(new LinearGradient(s[0], s[1], tx, ty, alpha(col, (int) (230 * fade)), alpha(col, 0), Shader.TileMode.CLAMP));
            float nx = -s[3] / vl * 3.2f * u, ny = s[2] / vl * 3.2f * u;
            Path tr = new Path();
            tr.moveTo(s[0] + nx, s[1] + ny);
            tr.lineTo(tx, ty);
            tr.lineTo(s[0] - nx, s[1] - ny);
            tr.close();
            c.drawPath(tr, p);
            p.setShader(null);
            glowCircle(c, s[0], s[1], 18 * u, alpha(col, (int) (200 * fade)));
            p.setColor(alpha(0xFFFFFFFF, (int) (255 * fade)));
            c.drawCircle(s[0], s[1], 3 * u, p);
        }
    }

    void button(Canvas c, RectF r, String label, int color, boolean enabled, Runnable a) {
        if (consoleApp) { flatButton(c, r, label, color, enabled, a); return; }
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
        if (enabled && r.width() > 120 * u) {
            // a glossy light sweep runs across each button every few seconds
            float ph = (anim * 0.45f + (r.left * 0.37f + r.top * 0.71f) / W) % 2.2f;
            if (ph < 1f) {
                float bw2 = r.height() * 0.6f;
                float x0 = r.left - bw2 * 2 + ph * (r.width() + bw2 * 3);
                c.save();
                Path cl = new Path();
                cl.addRoundRect(new RectF(r), rad, rad, Path.Direction.CW);
                c.clipPath(cl);
                Path band = new Path();
                band.moveTo(x0, r.bottom);
                band.lineTo(x0 + bw2, r.top);
                band.lineTo(x0 + bw2 * 1.6f, r.top);
                band.lineTo(x0 + bw2 * 0.6f, r.bottom);
                band.close();
                p.setColor(0x38FFFFFF);
                c.drawPath(band, p);
                c.restore();
            }
        }
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
        if (bright && (rarityColor == Defs.RARITY_COLOR[Defs.LEGEND] || rarityColor == Defs.RARITY_COLOR[Defs.MYTHIC]
                || rarityColor == Defs.RARITY_COLOR[Defs.EXCLUSIVE] || rarityColor == Defs.RARITY_COLOR[Defs.GALAXY]) && r.width() > 60 * u) {
            // twinkling sparkle in the corner of rare cards
            float tw = (float) Math.sin(anim * 3 + r.left * 0.01f);
            if (tw > 0) {
                float sx = r.left + r.width() * 0.2f, sy2 = r.top + r.height() * 0.2f;
                p.setColor(alpha(0xFFFFFFFF, (int) (220 * tw)));
                Path st = Art.starPath(sx, sy2, 9 * u * tw + 3 * u, 2 * u, 4);
                c.drawPath(st, p);
            }
        }
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
        if (consoleApp) { text(c, s, x, y, size * 0.86f, color, al, maxW); return; }
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

    /** Crystal Oil: a glossy amber drop with a crystal glint */
    void oilIcon(Canvas c, float x, float y, float r) {
        Path d = new Path();
        d.moveTo(x, y - r * 1.15f);
        d.quadTo(x + r * 0.95f, y + r * 0.05f, x + r * 0.72f, y + r * 0.5f);
        d.quadTo(x, y + r * 1.25f, x - r * 0.72f, y + r * 0.5f);
        d.quadTo(x - r * 0.95f, y + r * 0.05f, x, y - r * 1.15f);
        d.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(x - r, y - r, x + r, y + r, new int[]{0xFFFFE0A0, 0xFFFF8A2B, 0xFF7A1E5A},
                null, Shader.TileMode.CLAMP));
        c.drawPath(d, p);
        p.setShader(null);
        sp.setPathEffect(null);
        sp.setStrokeWidth(r * 0.1f);
        sp.setColor(0xFF3A0A20);
        c.drawPath(d, sp);
        p.setColor(0xCCFFFFFF);
        c.drawCircle(x - r * 0.3f, y + r * 0.1f, r * 0.18f, p);
        p.setColor(0xFF5EF2FF);
        c.drawCircle(x + r * 0.25f, y + r * 0.45f, r * 0.12f, p);
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
        sp.setColor(kind == 1 ? 0x99C58CFF : kind == 2 ? 0x995EF2FF : kind == 3 ? 0x99FF9A3B : 0x99FFD23F);
        c.drawRoundRect(rf, 36 * u, 36 * u, sp);
        float ix = rf.left + 38 * u;
        glowCircle(c, ix, cy, 40 * u, kind == 1 ? 0x77C58CFF : kind == 2 ? 0x775EF2FF : kind == 3 ? 0x77FF9A3B : 0x77FFD23F);
        if (kind == 0) star(c, ix, cy, 24 * u);
        else if (kind == 1) milliteIcon(c, ix, cy, 24 * u);
        else if (kind == 3) oilIcon(c, ix, cy, 26 * u);
        else crystalIcon(c, ix, cy, 26 * u);
        text(c, s, ix + 36 * u, cy + 15 * u, 40 * u, kind == 1 ? 0xFFE2C6FF : kind == 2 ? 0xFFB8F6FF : kind == 3 ? 0xFFFFC890 : 0xFFFFE27A, Paint.Align.LEFT, 0);
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
        tp.setTextSize(40 * u);
        float w2 = tp.measureText(String.valueOf(v2)) + 120 * u, w1 = tp.measureText(String.valueOf(v1)) + 120 * u;
        float c2 = W - 24 * u - w2 / 2, c1 = c2 - w2 / 2 - 14 * u - w1 / 2;
        title(c, title, 180 * u, 125 * u, 60 * u, 0xFFFFD23F, Paint.Align.LEFT, Math.max(200 * u, c1 - w1 / 2 - 200 * u));
        chip(c, c1, 100 * u, k1, v1);
        chip(c, c2, 100 * u, k2, v2);
    }

    static final int AVATARS = 12;

    /** profile icon: astronaut, alien, rocket, UFO, comet, black hole, or a planet */
    Bitmap photoBmp(String b64) {
        if (b64 == null || b64.isEmpty()) return null;
        Bitmap b = photoCache.get(b64);
        if (b == null && !photoCache.containsKey(b64)) {
            b = Photo.decode(b64);
            if (photoCache.size() > 60) photoCache.clear();
            photoCache.put(b64, b);
        }
        return b;
    }

    /** sprite of a planet wearing a skin style (cached) */
    final java.util.HashMap<Integer, Bitmap> skinCache = new java.util.HashMap<>();

    Bitmap skinBmp(int type, int style) {
        if (style < 0 || pBmp[type] == null) return pBmp[type];
        int k = Skins.key(style, type);
        Bitmap b = skinCache.get(k);
        if (b == null) {
            b = SkinArt.make(pBmp[type], style, type);
            skinCache.put(k, b);
        }
        return b;
    }

    Bitmap hellMoon;

    /** the Hell Moon equipment sprite: the moon recoloured to fire */
    Bitmap hellMoonBmp() {
        if (hellMoon == null && pBmp[Defs.MOON] != null) hellMoon = SkinArt.hellMoon(pBmp[Defs.MOON]);
        return hellMoon;
    }

    final Bitmap[] artBmp = new Bitmap[Defs.MOON_COUNT];

    /** artifact sprite: each moon tinted in its own colours */
    Bitmap moonBmp(int m) {
        if (m == Defs.MOON_HELL) return hellMoonBmp();
        if (m < 0 || m >= Defs.MOON_COUNT || pBmp[Defs.MOON] == null) return pBmp[Defs.MOON];
        if (artBmp[m] == null) artBmp[m] = SkinArt.tintMoon(pBmp[Defs.MOON], m);
        return artBmp[m];
    }

    final java.util.HashMap<Integer, Bitmap> evoCache = new java.util.HashMap<>();

    Bitmap evoBmp(int type) {
        Bitmap b = evoCache.get(type);
        if (b == null && pBmp[type] != null) { b = SkinArt.evolved(pBmp[type], type); evoCache.put(type, b); }
        return b;
    }

    /** sprite for a planet: a skin wins, else the evolved form, else the base sprite */
    Bitmap planetBmp(int type, int skin, boolean evo) {
        if (skin >= 0) return skinBmp(type, skin);
        return evo ? evoBmp(type) : pBmp[type];
    }

    /** my planet as it looks with its equipped skin / evolution */
    Bitmap pbmp(int type) { return pf == null ? pBmp[type] : planetBmp(type, pf.skinOn[type], pf.evolved[type]); }

    /** a round photo, or the drawn icon when there is no photo */
    void avatarOf(Canvas c, float x, float y, float r, int id, String photo) {
        Bitmap b = id < 0 ? photoBmp(photo) : null;
        if (b == null) { avatar(c, x, y, r, Math.max(0, id)); return; }
        c.save();
        Path cl = new Path();
        cl.addCircle(x, y, r * 0.95f, Path.Direction.CW);
        c.clipPath(cl);
        c.drawBitmap(b, null, new RectF(x - r * 0.95f, y - r * 0.95f, x + r * 0.95f, y + r * 0.95f), null);
        c.restore();
        sp.setPathEffect(null);
        sp.setStrokeWidth(r * 0.08f);
        sp.setColor(0x66FFFFFF);
        c.drawCircle(x, y, r * 0.95f, sp);
    }

    void setPhoto(String b64) {
        if (pf == null) return;
        if (b64 == null) { showToast(L.t("อ่านรูปนี้ไม่ได้ ลองรูปอื่น", "Couldn't read that picture, try another")); return; }
        pf.photo = b64;
        pf.avatar = -1;
        pf.save();
        pushNow();
        popup = P_PLAYER;
        showToast(L.t("เปลี่ยนรูปโปรไฟล์แล้ว", "Profile picture updated"));
    }

    void avatar(Canvas c, float x, float y, float r, int id) {
        if (id < 0) { avatarOf(c, x, y, r, id, pf != null ? pf.photo : null); return; }
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        switch (id) {
            case 0: { // astronaut helmet
                p.setColor(0xFFFFFFFF);
                p.setShader(new RadialGradient(x - r * 0.3f, y - r * 0.3f, r * 1.3f, 0xFFFFFFFF, 0xFFA8B0C4, Shader.TileMode.CLAMP));
                c.drawCircle(x, y, r * 0.8f, p);
                p.setShader(new LinearGradient(x - r * 0.5f, y - r * 0.35f, x + r * 0.5f, y + r * 0.35f, 0xFF4FE0FF, 0xFF1A2A6A, Shader.TileMode.CLAMP));
                c.drawOval(new RectF(x - r * 0.55f, y - r * 0.35f, x + r * 0.55f, y + r * 0.3f), p);
                p.setShader(null);
                p.setColor(0x88FFFFFF);
                c.drawOval(new RectF(x - r * 0.42f, y - r * 0.28f, x - r * 0.05f, y - r * 0.1f), p);
                break;
            }
            case 1: { // alien
                p.setColor(0xFFFFFFFF);
                p.setShader(new RadialGradient(x - r * 0.2f, y - r * 0.3f, r * 1.2f, 0xFFB8FF9A, 0xFF2E9A4A, Shader.TileMode.CLAMP));
                c.drawOval(new RectF(x - r * 0.6f, y - r * 0.8f, x + r * 0.6f, y + r * 0.75f), p);
                p.setShader(null);
                p.setColor(0xFF0B0520);
                c.save();
                c.rotate(20, x - r * 0.25f, y);
                c.drawOval(new RectF(x - r * 0.48f, y - r * 0.15f, x - r * 0.05f, y + r * 0.1f), p);
                c.restore();
                c.save();
                c.rotate(-20, x + r * 0.25f, y);
                c.drawOval(new RectF(x + r * 0.05f, y - r * 0.15f, x + r * 0.48f, y + r * 0.1f), p);
                c.restore();
                break;
            }
            case 2: { // rocket
                Path b = new Path();
                b.moveTo(x, y - r * 0.85f);
                b.cubicTo(x + r * 0.45f, y - r * 0.4f, x + r * 0.35f, y + r * 0.4f, x + r * 0.25f, y + r * 0.55f);
                b.lineTo(x - r * 0.25f, y + r * 0.55f);
                b.cubicTo(x - r * 0.35f, y + r * 0.4f, x - r * 0.45f, y - r * 0.4f, x, y - r * 0.85f);
                b.close();
                glowCircle(c, x, y + r * 0.75f, r * 0.4f, 0xFFFF9A3C);
                p.setColor(0xFFFFFFFF);
                p.setShader(new LinearGradient(x - r * 0.4f, 0, x + r * 0.4f, 0, 0xFFFFFFFF, 0xFFB8C0D0, Shader.TileMode.CLAMP));
                c.drawPath(b, p);
                p.setShader(null);
                p.setColor(0xFFFF4D5E);
                c.drawCircle(x, y - r * 0.15f, r * 0.17f, p);
                p.setColor(0xFF4FE0FF);
                c.drawCircle(x, y - r * 0.15f, r * 0.11f, p);
                break;
            }
            case 3: { // UFO
                glowCircle(c, x, y + r * 0.5f, r * 0.6f, 0x887CFF8A);
                p.setColor(0xFFFFFFFF);
                p.setShader(new LinearGradient(0, y - r * 0.6f, 0, y, 0xFFD8FFFF, 0xFF4FB8E0, Shader.TileMode.CLAMP));
                c.drawArc(new RectF(x - r * 0.38f, y - r * 0.6f, x + r * 0.38f, y + r * 0.1f), 180, 180, true, p);
                p.setShader(new LinearGradient(0, y - r * 0.15f, 0, y + r * 0.25f, 0xFFE6EAF2, 0xFF6A7288, Shader.TileMode.CLAMP));
                c.drawOval(new RectF(x - r * 0.8f, y - r * 0.15f, x + r * 0.8f, y + r * 0.25f), p);
                p.setShader(null);
                p.setColor(0xFFFFE27A);
                for (int i = -2; i <= 2; i++) c.drawCircle(x + i * r * 0.3f, y + r * 0.05f, r * 0.06f, p);
                break;
            }
            case 4: { // comet
                p.setColor(0xFFFFFFFF);
                p.setShader(new LinearGradient(x + r * 0.2f, y - r * 0.2f, x - r * 0.8f, y + r * 0.8f, 0xEEBFE6FF, 0x00BFE6FF, Shader.TileMode.CLAMP));
                Path t = new Path();
                t.moveTo(x + r * 0.35f, y - r * 0.05f);
                t.lineTo(x - r * 0.85f, y + r * 0.85f);
                t.lineTo(x + r * 0.05f, y - r * 0.35f);
                t.close();
                c.drawPath(t, p);
                p.setShader(null);
                glowCircle(c, x + r * 0.25f, y - r * 0.25f, r * 0.45f, 0xFFFFFFFF);
                break;
            }
            case 5: blackHole(c, x, y, r * 0.5f); break;
            default: {
                int[] pl = {Defs.EARTH, Defs.MOON, Defs.SATURN, Defs.MARS, Defs.NEPTUNE, Defs.SUN};
                sprite(c, pBmp[pl[(id - 6) % pl.length]], x, y, r * 1.05f, 255);
            }
        }
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
        // planet below the station
        float er = W * 0.95f;
        sprite(c, earthBig, W / 2, navTop + er * 0.55f, er / 0.72f, 255);
        // galaxy far away
        c.save();
        c.translate(W * 0.18f, H * 0.36f);
        c.rotate(-anim * 4);
        c.scale(1f, 0.55f);
        sprite(c, gBmp, 0, 0, 150 * u, 170);
        c.restore();
        float sy = 800 * u + (float) Math.sin(anim * 0.8f) * 8 * u;
        station(c, W / 2, sy, W * 0.33f);
        // top bar
        rf.set(30 * u, 50 * u, 150 * u, 170 * u);
        button(c, rf, null, 0xFF6B5BD6, true, () -> popup = P_PLAYER);
        avatar(c, rf.centerX(), rf.centerY() - 4 * u, 46 * u, pf.avatar);
        rf.set(170 * u, 50 * u, 290 * u, 170 * u);
        button(c, rf, null, 0xFFE0A020, true, () -> { leaderTab = 0; screen = S_LEADER; refreshPlayers(); });
        trophy(c, rf.centerX(), rf.centerY() - 6 * u, 40 * u);
        rf.set(W - 150 * u, 50 * u, W - 30 * u, 170 * u);
        button(c, rf, null, 0xFF6B5BD6, true, () -> popup = P_SETTINGS);
        gear(c, rf.centerX(), rf.centerY() - 4 * u, 40 * u);
        rf.set(W - 290 * u, 50 * u, W - 170 * u, 170 * u);
        button(c, rf, null, 0xFF2E9A6A, true, () -> popup = P_LOGIN);
        calendarIcon(c, rf.centerX(), rf.centerY() - 2 * u, 36 * u);
        if (pf.giftReady()) {
            p.setShader(null);
            p.setColor(0xFFFF3B5B);
            c.drawCircle(rf.right - 12 * u, rf.top + 12 * u, 13 * u * (1f + 0.15f * (float) Math.sin(anim * 6)), p);
        }
        if (!loginShown && net == NET_ONLINE && popup == P_NONE && busyMsg.isEmpty()) {
            loginShown = true;
            popup = P_LOGIN;
        }
        if (isAdmin()) {
            rf.set(W - 430 * u, 50 * u, W - 310 * u, 170 * u);
            button(c, rf, null, 0xFFD0407A, true, () -> { adminSel = cloud.uid; go(S_ADMIN); refreshPlayers(); selectAdmin(cloud.uid); });
            shield(c, rf.centerX(), rf.centerY() - 6 * u, 42 * u);
        }
        rf.set(310 * u, 50 * u, 430 * u, 170 * u);
        button(c, rf, null, 0xFFFF8A2B, true, () -> go(S_INV));
        bagIcon(c, rf.centerX(), rf.centerY() - 4 * u, 40 * u);
        rf.set(450 * u, 50 * u, 570 * u, 170 * u);
        button(c, rf, null, 0xFF2FB86A, true, () -> { go(S_SOCIAL); socialRefresh(); });
        friendsIcon(c, rf.centerX(), rf.centerY() - 4 * u, 38 * u);
        if (!social.requests.isEmpty() || !social.invites.isEmpty()) {
            p.setShader(null);
            p.setColor(0xFFFF3B5B);
            c.drawCircle(rf.right - 12 * u, rf.top + 12 * u, 13 * u, p);
        }
        if (pf.raidCapsules > 0) {
            p.setShader(null);
            p.setColor(0xFFFF3B6B);
            c.drawCircle(rf.right - 12 * u, rf.top + 14 * u, 22 * u, p);
            text(c, String.valueOf(Math.min(99, pf.raidCapsules)), rf.right - 12 * u, rf.top + 24 * u, 26 * u, 0xFFFFFFFF);
        }
        // currencies on their own row so nothing overlaps
        tp.setTextSize(40 * u);
        float wA = tp.measureText(String.valueOf(pf.stars)) + 120 * u, wB = tp.measureText(String.valueOf(pf.millite)) + 120 * u, wC = tp.measureText(String.valueOf(pf.oil)) + 120 * u;
        float cx0 = W / 2 - (wA + wB + wC + 28 * u) / 2;
        chip(c, cx0 + wA / 2, 225 * u, 0, pf.stars);
        chip(c, cx0 + wA + 14 * u + wB / 2, 225 * u, 1, pf.millite);
        chip(c, cx0 + wA + wB + 28 * u + wC / 2, 225 * u, 3, pf.oil);
        logo(c, W / 2, 420 * u, 150 * u);
        RectF tag = new RectF(W / 2 - 250 * u, 450 * u, W / 2 + 250 * u, 510 * u);
        panel(c, tag, 0xCC1A0F3D, 30 * u);
        plain(c, L.t("สถานีอวกาศ Stella • ผู้บัญชาการ ", "Stella Station • Commander ") + user, W / 2, 490 * u, 30 * u, 0xFFE6E0FF, Paint.Align.CENTER, 480 * u);
        netPill(c, W / 2, 545 * u);
        if (bcMsg != null && System.currentTimeMillis() - bcTime < 86400000L) ticker(c, 600 * u);
        lobbyCards(c, Math.max(900 * u, navTop - 790 * u));
        plain(c, VERSION, W - 30 * u, navTop - 30 * u, 26 * u, 0x99FFFFFF, Paint.Align.RIGHT, 0);
    }

    /** lobby: Story / Raid mode cards, my deck and quick links */
    void lobbyCards(Canvas c, float top) {
        float gap = 24 * u, cw = (W - 80 * u - gap) / 2f, ch = 360 * u;
        // ---- Story card
        RectF s = new RectF(40 * u, top, 40 * u + cw, top + ch);
        modeCard(c, s, 0xFFFF8A2B, 0xEE2A1550);
        int cleared = 0;
        for (int i = 0; i < Defs.ST_COUNT; i++) if (pf.clear[i]) cleared++;
        int next = Math.min(pf.unlocked, Defs.ST_COUNT) - 1;
        c.save();
        c.clipRect(s.left, s.top, s.right, s.bottom);
        blackHole(c, s.right - 80 * u, s.top + 95 * u, 50 * u);
        for (int k = 0; k < 5; k++) {
            double a = anim * 0.9 + k * 1.25;
            sprite(c, eBmp[k % 4], s.right - 80 * u + (float) Math.cos(a) * 95 * u, s.top + 95 * u + (float) Math.sin(a) * 40 * u, 14 * u, 220);
        }
        c.restore();
        title(c, L.t("สตอรี่", "Story"), s.left + 30 * u, s.top + 80 * u, 52 * u, 0xFFFFFFFF, Paint.Align.LEFT, cw - 170 * u);
        plain(c, L.t("ผ่านแล้ว ", "Cleared ") + cleared + "/" + Defs.ST_COUNT, s.left + 30 * u, s.top + 140 * u, 28 * u, 0xFFFFD8B0, Paint.Align.LEFT, 0);
        progressBar(c, s.left + 30 * u, s.top + 165 * u, cw - 60 * u, 16 * u, cleared / (float) Defs.ST_COUNT, 0xFFFF8A2B);
        rf.set(s.left + 24 * u, s.bottom - 112 * u, s.right - 24 * u, s.bottom - 26 * u);
        final int ns = next;
        button(c, rf, L.t("เล่นต่อ ", "Continue ") + (Defs.galaxyOf(ns) + 1) + "-" + (ns % Defs.PER_GALAXY + 1), 0xFFFF8A2B, true, () -> {
            playRaid = false;
            playSel = ns;
            playGal = Defs.galaxyOf(ns);
            go(S_PLAYMENU);
        });
        // ---- Raid card
        RectF r = new RectF(s.right + gap, top, W - 40 * u, top + ch);
        boolean open = raidOpen();
        modeCard(c, r, open ? 0xFFFF3B6B : 0xFF6A4A60, 0xEE300A22);
        c.save();
        c.clipRect(r.left, r.top, r.right, r.bottom);
        for (int ring = 0; ring < 3; ring++) {
            sp.setPathEffect(null);
            sp.setStrokeWidth((5 - ring) * u);
            sp.setColor(alpha(0xFFFF3B6B, open ? 200 - ring * 50 : 90));
            float rr = (40 + ring * 22) * u + (float) Math.sin(anim * 2 + ring) * 4 * u;
            rf2.set(r.right - 80 * u - rr, r.top + 95 * u - rr * 0.6f, r.right - 80 * u + rr, r.top + 95 * u + rr * 0.6f);
            c.drawOval(rf2, sp);
        }
        sprite(c, eBmp[Defs.E_TITAN], r.right - 80 * u, r.top + 95 * u, 38 * u, open ? 255 : 110);
        c.restore();
        title(c, "Raid", r.left + 30 * u, r.top + 80 * u, 52 * u, open ? 0xFFFFFFFF : 0xFFB8A8B8, Paint.Align.LEFT, cw - 170 * u);
        if (open) {
            plain(c, L.t("เวฟสูงสุด ", "Best wave ") + pf.raidBest + "/" + Defs.RAID_WAVES, r.left + 30 * u, r.top + 140 * u, 28 * u, 0xFFFFC0D0, Paint.Align.LEFT, 0);
            progressBar(c, r.left + 30 * u, r.top + 165 * u, cw - 60 * u, 16 * u, pf.raidBest / (float) Defs.RAID_WAVES, 0xFFFF3B6B);
            oilIcon(c, r.left + 48 * u, r.top + 222 * u, 14 * u);
            plain(c, String.valueOf(pf.oil), r.left + 72 * u, r.top + 232 * u, 28 * u, 0xFFFFC890, Paint.Align.LEFT, 0);
        } else {
            plain(c, L.t("ชนะ ", "Wins ") + pf.wins + "/" + RAID_WINS + L.t(" เพื่อปลดล็อก", " to unlock"), r.left + 30 * u, r.top + 140 * u, 28 * u, 0xFFD8C0C8, Paint.Align.LEFT, cw - 60 * u);
            progressBar(c, r.left + 30 * u, r.top + 165 * u, cw - 60 * u, 16 * u, pf.wins / (float) RAID_WINS, 0xFF8A6A80);
        }
        rf.set(r.left + 24 * u, r.bottom - 112 * u, r.right - 24 * u, r.bottom - 26 * u);
        button(c, rf, open ? L.t("เข้าสู่ Raid", "Enter Raid") : L.t("ล็อกอยู่", "Locked"), 0xFFFF3B6B, open, () -> { playRaid = true; go(S_PLAYMENU); });
        if (!open) btns.add(new Btn(new RectF(rf), () -> showToast(L.t("ชนะให้ครบ " + RAID_WINS + " ครั้งเพื่อปลดล็อก Raid", "Win " + RAID_WINS + " games to unlock Raid"))));
        // ---- events bar: challenges, missions, login calendar
        RectF ev = new RectF(40 * u, r.bottom + gap, W - 40 * u, r.bottom + gap + 110 * u);
        modeCard(c, ev, 0xFF7CFF8A, 0xEE0F2A1E);
        trophy(c, ev.left + 60 * u, ev.centerY(), 30 * u);
        text(c, L.t("ชาเลนจ์ & ภารกิจ", "Challenges & Missions"), ev.left + 110 * u, ev.centerY() + 4 * u, 34 * u, 0xFFFFFFFF, Paint.Align.LEFT, ev.width() - 380 * u);
        int ready = (pf.dailyReady() ? 1 : 0) + (pf.weeklyReady() ? 1 : 0);
        plain(c, ready > 0 ? L.t("ชาเลนจ์พร้อม " + ready + " • รางวัลวิวัฒน์", ready + " challenge(s) ready • evolution rewards")
                : L.t("ชาเลนจ์วันนี้ผ่านแล้ว", "Today's challenges done"), ev.left + 110 * u, ev.centerY() + 40 * u, 22 * u, 0xFFB8FFC8, Paint.Align.LEFT, ev.width() - 380 * u);
        if (pf.anyMissionClaim() || pf.giftReady() || ready > 0) {
            p.setShader(null);
            p.setColor(0xFFFF3B5B);
            c.drawCircle(ev.right - 40 * u, ev.top + 26 * u, 14 * u * (1f + 0.15f * (float) Math.sin(anim * 6)), p);
        }
        rf.set(ev.right - 230 * u, ev.top + 18 * u, ev.right - 24 * u, ev.bottom - 18 * u);
        button(c, rf, L.t("เปิด", "Open"), 0xFF2FB86A, true, () -> go(S_EVENTS));
        btns.add(new Btn(new RectF(ev), () -> go(S_EVENTS)));
        // ---- deck strip
        RectF d = new RectF(40 * u, ev.bottom + gap, W - 40 * u, ev.bottom + gap + 210 * u);
        panel(c, d, 0xDD1A0F3D, 30 * u);
        text(c, L.t("Deck ของฉัน", "My deck"), d.left + 30 * u, d.top + 48 * u, 32 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
        float ds = 112 * u, dx = d.left + 30 * u;
        for (int i = 0; i < Profile.DECK_SIZE; i++) {
            int t = pf.deck[i];
            boolean sp4 = i == Defs.SPECIAL_SLOT;
            RectF q = new RectF(dx + i * (ds + 16 * u) + (sp4 ? 14 * u : 0), d.top + 72 * u, dx + i * (ds + 16 * u) + (sp4 ? 14 * u : 0) + ds, d.top + 72 * u + ds);
            rarityCard(c, q, t >= 0 ? Defs.RARITY_COLOR[Defs.P_RARITY[t]] : sp4 ? Defs.RARITY_COLOR[Defs.SPECIALIST] : 0xFF4A4460, t >= 0, false, 18 * u);
            if (t >= 0) sprite(c, pbmp(t), q.centerX(), q.centerY(), ds * 0.36f, 255);
            else text(c, "+", q.centerX(), q.centerY() + 16 * u, 44 * u, 0xFF6A6488);
        }
        rf.set(d.right - 190 * u, d.top + 88 * u, d.right - 24 * u, d.top + 168 * u);
        button(c, rf, L.t("แก้ไข", "Edit"), 0xFF6B5BD6, true, () -> go(S_PLANET));
    }

    void modeCard(Canvas c, RectF r, int accent, int bg) {
        rf2.set(r);
        rf2.offset(0, 10 * u);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x66000000);
        c.drawRoundRect(rf2, 34 * u, 34 * u, p);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, bg, darken(accent, 0.3f) & 0xEEFFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, 34 * u, 34 * u, p);
        p.setShader(null);
        sp.setPathEffect(null);
        sp.setStrokeWidth(4 * u);
        sp.setColor(alpha(accent, 0xCC));
        c.drawRoundRect(r, 34 * u, 34 * u, sp);
        // sheen
        p.setShader(new LinearGradient(r.left, r.top, r.left, r.top + r.height() * 0.4f, 0x22FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, 34 * u, 34 * u, p);
        p.setShader(null);
    }

    void progressBar(Canvas c, float x, float y, float w, float h, float f, int col) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x66000000);
        rf2.set(x, y, x + w, y + h);
        c.drawRoundRect(rf2, h / 2, h / 2, p);
        f = Math.max(0, Math.min(1, f));
        if (f > 0) {
            p.setColor(0xFFFFFFFF);
            p.setShader(new LinearGradient(x, 0, x + w, 0, lighten(col, 0.3f), col, Shader.TileMode.CLAMP));
            rf2.set(x, y, x + Math.max(h, w * f), y + h);
            c.drawRoundRect(rf2, h / 2, h / 2, p);
            p.setShader(null);
        }
    }

    /** orbital station: solar wings, modules, a rotating habitat ring and blinking lights */
    void station(Canvas c, float x, float y, float s) {
        glowCircle(c, x, y, s * 1.2f, 0x335BC8FF);
        sp.setPathEffect(null);
        // truss
        sp.setStrokeWidth(s * 0.025f);
        sp.setColor(0xFF8A93A8);
        c.drawLine(x - s * 1.05f, y, x + s * 1.05f, y, sp);
        sp.setStrokeWidth(s * 0.008f);
        sp.setColor(0xFFC8CFE0);
        for (int i = -10; i <= 10; i++) {
            float tx = x + i * s * 0.1f;
            c.drawLine(tx, y - s * 0.02f, tx + s * 0.05f, y + s * 0.02f, sp);
        }
        // solar wings (4 per side)
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < 2; k++) {
                float px = x + side * s * (0.55f + k * 0.28f);
                for (int vv = -1; vv <= 1; vv += 2) {
                    RectF pan = new RectF(px - s * 0.11f, y + vv * s * 0.05f + (vv < 0 ? -s * 0.38f : 0), px + s * 0.11f, y + vv * s * 0.05f + (vv < 0 ? 0 : s * 0.38f));
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xFFFFFFFF);
                    p.setShader(new LinearGradient(pan.left, pan.top, pan.right, pan.bottom, 0xFF2E5FC8, 0xFF0C1E5A, Shader.TileMode.CLAMP));
                    c.drawRect(pan, p);
                    p.setShader(new LinearGradient(pan.left, pan.top, pan.left + pan.width() * 0.7f, pan.top + pan.height() * 0.5f, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
                    c.drawRect(pan, p);
                    p.setShader(null);
                    sp.setStrokeWidth(s * 0.004f);
                    sp.setColor(0x887FB0FF);
                    for (int gx = 1; gx < 4; gx++) c.drawLine(pan.left + pan.width() * gx / 4f, pan.top, pan.left + pan.width() * gx / 4f, pan.bottom, sp);
                    for (int gy = 1; gy < 8; gy++) c.drawLine(pan.left, pan.top + pan.height() * gy / 8f, pan.right, pan.top + pan.height() * gy / 8f, sp);
                    sp.setStrokeWidth(s * 0.008f);
                    sp.setColor(0xFFB8C0D0);
                    c.drawRect(pan, sp);
                }
            }
        }
        // rotating habitat ring (back half)
        float rw = s * 0.48f, rh = s * 0.17f;
        RectF ring = new RectF(x - rw, y - rh, x + rw, y + rh);
        sp.setStrokeWidth(s * 0.06f);
        sp.setColor(0xFF5A6278);
        c.drawArc(ring, 180, 180, false, sp);
        sp.setStrokeWidth(s * 0.035f);
        sp.setColor(0xFFAEB6C8);
        c.drawArc(ring, 180, 180, false, sp);
        // spokes
        sp.setStrokeWidth(s * 0.012f);
        sp.setColor(0xFF9AA3B8);
        for (int i = 0; i < 4; i++) {
            double a = anim * 0.6 + i * Math.PI / 2;
            c.drawLine(x, y, x + (float) Math.cos(a) * rw, y + (float) Math.sin(a) * rh, sp);
        }
        // modules (central hub)
        float mw = s * 0.42f, mh = s * 0.12f;
        RectF hub = new RectF(x - mw / 2, y - mh / 2, x + mw / 2, y + mh / 2);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(0, hub.top, 0, hub.bottom, new int[]{0xFFF2F4FA, 0xFFB8C0D0, 0xFF5A6278}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(hub, mh / 2, mh / 2, p);
        RectF mod2 = new RectF(x - mh * 0.45f, y - s * 0.3f, x + mh * 0.45f, y + s * 0.3f);
        p.setShader(new LinearGradient(mod2.left, 0, mod2.right, 0, new int[]{0xFF5A6278, 0xFFE6EAF2, 0xFF6A7288}, new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(mod2, mh * 0.4f, mh * 0.4f, p);
        p.setShader(null);
        sp.setStrokeWidth(s * 0.006f);
        sp.setColor(0xFF3A4258);
        c.drawRoundRect(hub, mh / 2, mh / 2, sp);
        c.drawRoundRect(mod2, mh * 0.4f, mh * 0.4f, sp);
        for (int i = 0; i < 4; i++) {
            float bx = hub.left + hub.width() * (0.15f + i * 0.23f);
            c.drawLine(bx, hub.top, bx, hub.bottom, sp);
        }
        // gold foil node and dish
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(x - mh * 0.6f, y - mh * 0.6f, x + mh * 0.6f, y + mh * 0.6f, 0xFFFFE08A, 0xFFB07018, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, mh * 0.62f, p);
        p.setShader(null);
        sp.setStrokeWidth(s * 0.01f);
        sp.setColor(0xFFDDE3EE);
        c.drawLine(x, y - s * 0.3f, x, y - s * 0.42f, sp);
        rf2.set(x - s * 0.07f, y - s * 0.5f, x + s * 0.07f, y - s * 0.4f);
        p.setColor(0xFFF2F4FA);
        c.drawArc(rf2, 0, 180, true, p);
        // windows
        for (int i = 0; i < 7; i++) {
            float wx = hub.left + hub.width() * (0.08f + i * 0.13f);
            boolean on = ((int) (anim * 0.7f + i * 1.7f)) % 5 != 0;
            p.setColor(on ? 0xFFFFE6A0 : 0xFF4A5068);
            c.drawRect(wx, y - mh * 0.12f, wx + s * 0.018f, y + mh * 0.12f, p);
            if (on) glowCircle(c, wx + s * 0.009f, y, s * 0.03f, 0x55FFE6A0);
        }
        // ring front half with lit windows moving round
        sp.setStrokeWidth(s * 0.06f);
        sp.setColor(0xFF5A6278);
        c.drawArc(ring, 0, 180, false, sp);
        sp.setStrokeWidth(s * 0.035f);
        sp.setColor(0xFFD8DEEA);
        c.drawArc(ring, 0, 180, false, sp);
        for (int i = 0; i < 16; i++) {
            double a = anim * 0.6 + i * Math.PI / 8;
            if (Math.sin(a) < 0) continue;
            float wx = x + (float) Math.cos(a) * rw, wy = y + (float) Math.sin(a) * rh;
            glowCircle(c, wx, wy, s * 0.025f, 0x99FFE6A0);
            p.setColor(0xFFFFF2C8);
            c.drawCircle(wx, wy, s * 0.008f, p);
        }
        // blinking navigation lights
        boolean blink = ((int) (anim * 2)) % 2 == 0;
        glowCircle(c, x - s * 1.05f, y, s * 0.05f, blink ? 0xFFFF4D5E : 0x44FF4D5E);
        glowCircle(c, x + s * 1.05f, y, s * 0.05f, !blink ? 0xFF4DFF7A : 0x444DFF7A);
        glowCircle(c, x + s * 0.08f, y - s * 0.47f, s * 0.035f, blink ? 0xFFFFFFFF : 0x33FFFFFF);
    }

    // ================================================================== shop
    void drawShop(Canvas c) {
        header(c, L.t("ร้านค้า", "Shop"));
        float cw = (W - 140 * u) / 2f, ch = 420 * u, y0 = 220 * u;
        // top-up is left out until the game is on Google Play
        String[] names = {L.t("ของขวัญรายวัน", "Daily gift"), "Star Pass", L.t("สกินดวงดาว", "Planet skins")};
        int[] cols = {0xFF33C6A6, 0xFFB86BFF, 0xFF4FA3FF};
        int[] icon = {0, 2, 3};
        for (int i = 0; i < 3; i++) {
            float appear = ease(Math.min(1f, Math.max(0f, (transT - i * 0.06f) / 0.35f)));
            // the daily gift spans the whole first row
            float x = i == 0 ? 50 * u : 50 * u + ((i - 1) % 2) * (cw + 40 * u);
            float y = y0 + (i == 0 ? 0 : ch + 40 * u) + (1f - appear) * 60 * u;
            RectF q = new RectF(x, y, i == 0 ? W - 50 * u : x + cw, y + ch);
            boolean live = i != 1;
            rarityCard(c, q, cols[i], live, false, 36 * u);
            float ix = q.centerX(), iy = q.top + ch * 0.36f + (float) Math.sin(anim * 2.2f + i) * 10 * u;
            glowCircle(c, ix, iy, 130 * u, alpha(cols[i], 0x55));
            shopIcon(c, icon[i], ix, iy, 80 * u);
            text(c, names[i], q.centerX(), q.bottom - 120 * u, 40 * u, 0xFFFFFFFF, Paint.Align.CENTER, cw - 30 * u);
            if (i == 0) {
                boolean ready = pf.giftReady();
                rf.set(q.left + 30 * u, q.bottom - 95 * u, q.right - 30 * u, q.bottom - 25 * u);
                button(c, rf, ready ? L.t("รับของล็อกอิน", "Claim login reward") : L.t("ปฏิทินล็อกอิน", "Login calendar"),
                        0xFF2E9A6A, true, () -> popup = P_LOGIN);
            } else if (i == 2) {
                plain(c, L.t("Raid Capsule • ร้านค้า Raid", "Raid Capsule • Raid shop"), q.centerX(), q.bottom - 50 * u, 26 * u, 0xFFB8F6FF, Paint.Align.CENTER, cw - 20 * u);
                btns.add(new Btn(q, () -> {
                    if (raidOpen()) popup = P_RSHOP;
                    else showToast(L.t("ชนะให้ครบ " + RAID_WINS + " ครั้งเพื่อปลดล็อกร้าน Raid", "Win " + RAID_WINS + " games to unlock the Raid shop"));
                }));
            } else {
                plain(c, L.t("เร็ว ๆ นี้", "Coming soon"), q.centerX(), q.bottom - 50 * u, 30 * u, 0xFFB8B2CC, Paint.Align.CENTER, 0);
                btns.add(new Btn(q, () -> showToast(L.t("เร็ว ๆ นี้", "Coming soon"))));
            }
        }
    }

    void shopIcon(Canvas c, int kind, float x, float y, float r) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        sp.setPathEffect(null);
        switch (kind) {
            case 0: { // gift box
                float lid = (float) Math.max(0, Math.sin(anim * 3)) * r * 0.12f;
                rf2.set(x - r * 0.8f, y - r * 0.2f, x + r * 0.8f, y + r * 0.85f);
                p.setColor(0xFFFF5B7A);
                c.drawRoundRect(rf2, r * 0.12f, r * 0.12f, p);
                rf2.set(x - r * 0.95f, y - r * 0.5f - lid, x + r * 0.95f, y - r * 0.15f - lid);
                p.setColor(0xFFFF7A95);
                c.drawRoundRect(rf2, r * 0.1f, r * 0.1f, p);
                p.setColor(0xFFFFD23F);
                c.drawRect(x - r * 0.14f, y - r * 0.5f - lid, x + r * 0.14f, y + r * 0.85f, p);
                rf2.set(x - r * 0.55f, y - r * 0.85f - lid, x - r * 0.02f, y - r * 0.48f - lid);
                c.drawOval(rf2, p);
                rf2.set(x + r * 0.02f, y - r * 0.85f - lid, x + r * 0.55f, y - r * 0.48f - lid);
                c.drawOval(rf2, p);
                sp.setStrokeWidth(4 * u);
                sp.setColor(0xFF140A2A);
                rf2.set(x - r * 0.8f, y - r * 0.2f, x + r * 0.8f, y + r * 0.85f);
                c.drawRoundRect(rf2, r * 0.12f, r * 0.12f, sp);
                break;
            }
            case 1: // stack of star coins
                for (int k = 0; k < 3; k++) star(c, x + (k - 1) * r * 0.55f, y + r * 0.25f - (k == 1 ? r * 0.35f : 0), r * 0.42f);
                break;
            case 2: { // pass ticket
                c.save();
                c.rotate(-12 + 4 * (float) Math.sin(anim * 2), x, y);
                rf2.set(x - r, y - r * 0.55f, x + r, y + r * 0.55f);
                p.setColor(0xFFB86BFF);
                c.drawRoundRect(rf2, r * 0.15f, r * 0.15f, p);
                p.setColor(0xFFE6D0FF);
                rf2.set(x - r * 0.85f, y - r * 0.4f, x + r * 0.85f, y + r * 0.4f);
                c.drawRoundRect(rf2, r * 0.1f, r * 0.1f, p);
                star(c, x, y, r * 0.3f);
                c.restore();
                break;
            }
            default: // a planet wearing a skin ring
                sprite(c, pBmp[Defs.EARTH], x, y, r * 0.9f, 255);
                sp.setStrokeWidth(8 * u);
                sp.setColor(hsv(anim * 80));
                rf2.set(x - r * 1.2f, y - r * 0.35f, x + r * 1.2f, y + r * 0.35f);
                c.drawArc(rf2, 0, 180, false, sp);
        }
    }

    // ================================================================== planet menu
    void drawPlanetMenu(Canvas c) {
        header(c, L.t("ดวงดาว", "Planet"), 0, pf.stars, 3, pf.oil);
        int t = selType;
        boolean own = pf.owned[t];
        // top row: inventory
        int ownAll = 0;
        for (int k = 0; k < Defs.P_COUNT; k++) if (pf.owned[k]) ownAll++;
        plain(c, L.t("ดวงดาวที่สะสม ", "Planets collected ") + ownAll + "/" + Defs.P_COUNT, 50 * u, 240 * u, 32 * u, 0xFFC9C2E6, Paint.Align.LEFT, 0);
        plain(c, L.t("สกิน / Artifact อยู่ใน กระเป๋า (หน้าหลัก)", "Skins / Artifacts: Inventory (home)"), W - 50 * u, 240 * u, 24 * u, 0xFF8A84A0, Paint.Align.RIGHT, 0);
        // scrolling collection, stacked by category
        float cell = (W - 100 * u) / 6f;
        RectF view = new RectF(30 * u, navTop - 30 * u - cell * 2 - 80 * u, W - 30 * u, navTop - 8 * u);
        float secH = 74 * u, contentH = 0;
        int nCat = Defs.CAT_NAME.length;
        int[] inCat = new int[nCat], ownCat = new int[nCat];
        for (int k = 0; k < Defs.P_COUNT; k++) { inCat[Defs.P_CATEGORY[k]]++; if (pf.owned[k]) ownCat[Defs.P_CATEGORY[k]]++; }
        for (int ci = 0; ci < nCat; ci++) contentH += secH + (float) Math.ceil(inCat[ci] / 6f) * cell + 16 * u;
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x55080320);
        c.drawRoundRect(view, 28 * u, 28 * u, p);
        beginScroll(c, S_PLANET, view, contentH, 0.016f);
        float y = view.top;
        for (int ci = 0; ci < nCat; ci++) {
            int col = ci == Defs.CAT_EXCLUSIVE ? Defs.RARITY_COLOR[Defs.EXCLUSIVE] : ci == Defs.CAT_SPECIALIST ? Defs.RARITY_COLOR[Defs.SPECIALIST] : 0xFFFF8A2B;
            p.setColor(col);
            rf2.set(50 * u, y + 26 * u, 58 * u, y + 62 * u);
            c.drawRoundRect(rf2, 4 * u, 4 * u, p);
            text(c, (L.en ? Defs.CAT_NAME[ci] : Defs.CAT_NAME_TH[ci]) + "  " + ownCat[ci] + "/" + inCat[ci], 72 * u, y + 58 * u, 36 * u, 0xFFFFFFFF, Paint.Align.LEFT, 0);
            String how = ci == Defs.CAT_EXCLUSIVE ? L.t("สุ่มจากตู้ Exclusive (Crystal Oil)", "From the Exclusive capsule (Crystal Oil)")
                    : ci == Defs.CAT_SPECIALIST ? L.t("หายากสุดในตู้ Exclusive • ช่อง Deck พิเศษ", "Rarest in the Exclusive capsule • own deck slot") : "";
            if (!how.isEmpty()) plain(c, how, W - 50 * u, y + 56 * u, 25 * u, alpha(col, 0xDD), Paint.Align.RIGHT, 0);
            y += secH;
            int slot = 0;
            for (int i = 0; i < Defs.P_COUNT; i++) {
                if (Defs.P_CATEGORY[i] != ci) continue;
                final int ti = i;
                float x = 50 * u + (slot % 6) * cell, yy = y + (slot / 6) * cell;
                slot++;
                RectF r = new RectF(x + 6 * u, yy + 6 * u, x + cell - 6 * u, yy + cell - 6 * u);
                int rc = Defs.RARITY_COLOR[Defs.P_RARITY[i]];
                rarityCard(c, r, rc, pf.owned[i], i == t, 22 * u);
                sprite(c, pf.owned[i] ? pbmp(i) : pBmp[i], r.centerX(), r.centerY() - 6 * u, cell * 0.42f, pf.owned[i] ? 255 : 45);
                if (pf.owned[i]) {
                    text(c, "Lv" + pf.level[i], r.centerX(), r.bottom - 14 * u, 26 * u, 0xFFFFFFFF);
                    if (pf.inDeck(i)) {
                        p.setColor(0xFF33C6A6);
                        c.drawCircle(r.right - 18 * u, r.top + 18 * u, 13 * u, p);
                    }
                    if (pf.moonOn[i] >= 0) sprite(c, moonBmp(pf.moonOn[i]), r.left + 20 * u, r.top + 20 * u, 13 * u, 255);
                } else text(c, "?", r.centerX(), r.centerY() + 18 * u, 56 * u, 0xFF6A6488);
                scrollBtn(r, () -> selType = ti);
            }
            y += (float) Math.ceil(inCat[ci] / 6f) * cell + 16 * u;
        }
        endScroll(c);
        // deck row (4 normal + 1 specialist)
        float ds = 128 * u, dTop = view.top - 30 * u - ds;
        text(c, "Deck", 50 * u, dTop + ds / 2 + 14 * u, 40 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
        for (int i = 0; i < Profile.DECK_SIZE; i++) {
            final int d = pf.deck[i];
            boolean sp4 = i == Defs.SPECIAL_SLOT;
            float x = 180 * u + i * (ds + 22 * u) + (sp4 ? 26 * u : 0);
            RectF r = new RectF(x, dTop, x + ds, dTop + ds);
            rarityCard(c, r, d >= 0 ? Defs.RARITY_COLOR[Defs.P_RARITY[d]] : sp4 ? Defs.RARITY_COLOR[Defs.SPECIALIST] : 0xFF6A6488, d >= 0, false, 24 * u);
            if (d >= 0) {
                sprite(c, pbmp(d), r.centerX(), r.centerY(), ds * 0.45f, 255);
                btns.add(new Btn(r, () -> selType = d));
            } else if (sp4) plain(c, "Specialist", r.centerX(), r.centerY() + 10 * u, 24 * u, 0xFFB8A878, Paint.Align.CENTER, 0);
            else text(c, "+", r.centerX(), r.centerY() + 20 * u, 60 * u, 0xFF6A6488);
        }
        // buttons (two rows)
        float bh = 92 * u, bw = (W - 140 * u) / 2f;
        float b2 = dTop - 30 * u - bh, b1 = b2 - 20 * u - bh;
        boolean maxed = pf.level[t] >= Profile.MAX_LV;
        boolean canUp = own && !maxed && pf.copies[t] >= pf.upgradeCopies(t) && pf.stars >= pf.upgradeStars(t);
        rf.set(50 * u, b1, 50 * u + bw, b1 + bh);
        boolean evoReady = own && maxed && Defs.canEvolve(t) && !pf.evolved[t];
        if (evoReady) {
            // only planets that have an evolution ever show this button
            final int needCat = Defs.evoCatalysts(t);
            final String costTxt = needCat + " Evo Catalyst + " + Defs.EVO_STARS + " Star";
            boolean afford = pf.stars >= Defs.EVO_STARS && pf.items[Items.CATALYST] >= needCat;
            glowCircle(c, rf.centerX(), rf.centerY(), rf.width() * 0.6f, alpha(0xFF7CFF8A, (int) (60 + 40 * Math.sin(anim * 4))));
            button(c, rf, L.t("วิวัฒน์!", "Evolve!"), 0xFF2FB86A, afford, () -> Dialogs.confirm(ctx, L.t("วิวัฒนาการ", "Evolve"),
                    Defs.P_NAME[selType] + " → " + Defs.evoName(selType) + "\n" + Defs.evoDesc(selType) + "\n\n"
                            + L.t("ใช้ ", "Costs ") + costTxt + L.t(" (มี ", " (you have ") + pf.items[Items.CATALYST] + ")",
                    L.t("วิวัฒน์!", "Evolve!"), () -> {
                        String err = pf.evolve(selType);
                        if (err != null) showToast(err);
                        else { Sfx.play(Sfx.RARE); showToast(Defs.evoName(selType) + "!"); evoFlash = 1.6f; pushNow(); }
                    }));
            if (!afford) btns.add(new Btn(new RectF(rf), () -> showToast(L.t("ต้องใช้ ", "Needs ") + costTxt + L.t(" • หา Evo Catalyst จาก Weekly Challenge หรือคราฟในกระเป๋า", " • get Evo Catalysts from the Weekly Challenge or craft them in the bag"))));
        } else button(c, rf, maxed ? "MAX" : L.t("อัปเกรด Lv", "Upgrade Lv"), 0xFF33C6A6, canUp, () -> {
            String err = pf.upgrade(selType);
            if (err != null) showToast(err);
            else showToast(Defs.P_NAME[selType] + " Lv " + pf.level[selType] + "!");
        });
        rf.set(90 * u + bw, b1, 90 * u + bw * 2, b1 + bh);
        final int sellT = t;
        boolean sellable = own && pf.canSell(t);
        button(c, rf, sellable ? L.t("ขาย +", "Sell +") + pf.sellValue(t) + " Star" : L.t("ขายไม่ได้", "Can't sell"), 0xFFD0407A, sellable,
                () -> Dialogs.confirm(ctx, L.t("ขายดวงดาว", "Sell planet"),
                        L.t("ขาย ", "Sell ") + Defs.P_NAME[sellT] + " (Lv " + pf.level[sellT] + ")" + L.t(" ได้ ", " for ") + pf.sellValue(sellT) + " Star?\n"
                                + L.t("ดาวดวงนี้จะหายไปพร้อม Level, Enchant และ Material ย้อนกลับไม่ได้", "It's gone for good, together with its level, enchant and material."),
                        L.t("ขายเลย", "Sell"), () -> {
                            int got = pf.sell(sellT);
                            if (got > 0) {
                                showToast(L.t("ขายแล้ว +", "Sold +") + got + " Star");
                                Sfx.play(Sfx.SELL);
                                pushNow();
                                for (int k = 0; k < Defs.P_COUNT; k++) if (pf.owned[k]) { selType = k; break; }
                            }
                        }));
        boolean inDeck = pf.inDeck(t);
        rf.set(50 * u, b2, 50 * u + bw, b2 + bh);
        button(c, rf, inDeck ? L.t("ถอดออกจาก Deck", "Remove from deck") : L.t("ใส่ Deck", "Add to deck"),
                inDeck ? 0xFFFF6B5B : 0xFF4F8BFF, own, () -> {
                    String err = pf.toggleDeck(selType);
                    if (err != null) showToast(err);
                });
        rf.set(90 * u + bw, b2, 90 * u + bw * 2, b2 + bh);
        button(c, rf, L.t("อุปกรณ์", "Equipment"), 0xFFB86BFF, own, () -> popup = P_EQUIP);
        // status block
        float sTop = b1 - 250 * u;
        int rar = Defs.P_RARITY[t];
        text(c, L.t("สถานะ:", "Status:"), 50 * u, sTop + 40 * u, 38 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
        text(c, L.t("ระดับ: ", "Rarity: ") + Defs.RARITY_NAME[rar], W - 50 * u, sTop + 40 * u, 38 * u, Defs.RARITY_COLOR[rar], Paint.Align.RIGHT, 0);
        plain(c, statLine(previewPlanet(t)), 50 * u, sTop + 95 * u, 32 * u, 0xFFE6E0FF, Paint.Align.LEFT, W - 100 * u);
        String cp = maxed ? "" : String.format(Locale.US, L.t("   การ์ดซ้ำ %d/%d  •  ใช้ %d Star", "   Copies %d/%d  •  Cost %d Star"),
                pf.copies[t], pf.upgradeCopies(t), pf.upgradeStars(t));
        plain(c, "Lv " + pf.level[t] + "/" + Profile.MAX_LV + cp + L.t("   •  ในด่าน วางได้ ", "   •  In match: max ") + Defs.PLACE_LIMIT[t]
                + L.t(" ดวง, Lv สูงสุด ", ", up to Lv ") + Defs.MATCH_MAX[t], 50 * u, sTop + 145 * u, 28 * u, 0xFFC9C2E6, Paint.Align.LEFT, W - 100 * u);
        if (pf.enchant[t] >= 0) {
            badge(c, 75 * u, sTop + 192 * u, 22 * u, pf.enchant[t], 0);
            plain(c, enchName(pf.enchant[t], pf.tier[t]), 110 * u, sTop + 204 * u, 32 * u, Defs.RARITY_COLOR[Defs.EN_RARITY[pf.enchant[t]]], Paint.Align.LEFT, 0);
        } else plain(c, L.t("Enchant: ยังไม่มี", "Enchant: none"), 50 * u, sTop + 204 * u, 32 * u, 0xFF8A84A0, Paint.Align.LEFT, 0);
        if (pf.material[t] >= 0) {
            materialBadge(c, W - 70 * u, sTop + 192 * u, 22 * u, pf.material[t]);
            plain(c, matName(pf.material[t]), W - 105 * u, sTop + 204 * u, 32 * u, Defs.MAT_COLOR[pf.material[t]], Paint.Align.RIGHT, 0);
        } else plain(c, L.t("Material: ยังไม่มี", "Material: none"), W - 50 * u, sTop + 204 * u, 32 * u, 0xFF8A84A0, Paint.Align.RIGHT, 0);
        // big planet
        float aTop = 280 * u, aBot = sTop - 10 * u;
        float nameY = aBot - 110 * u;
        float half = Math.min(220 * u, (nameY - 70 * u - aTop) * 0.45f);
        float cy = (aTop + nameY - 60 * u) / 2;
        bigPlanet(c, t, W / 2, cy, half, own);
        if (own && pf.moonOn[t] >= 0) {
            double ma = anim * 1.2;
            float mx = W / 2 + (float) Math.cos(ma) * half * 1.35f, my = cy + (float) Math.sin(ma) * half * 0.4f;
            glowCircle(c, mx, my, half * 0.4f, 0x88FF4B2B);
            sprite(c, moonBmp(pf.moonOn[t]), mx, my, half * 0.2f, 255);
        }
        if (evoFlash > 0) {
            evoFlash -= 0.016f;
            glowCircle(c, W / 2, cy, half * (2.4f - evoFlash), alpha(0xFF7CFF8A, (int) (200 * Math.min(1f, evoFlash))));
        }
        if (own && pf.evolved[t]) glowCircle(c, W / 2, cy, half * 1.5f, alpha(0xFF7CFF8A, (int) (50 + 30 * Math.sin(anim * 3))));
        if (Defs.canEvolve(t) && !pf.evolved[t])
            pill(c, W / 2 + half * 0.9f, cy - half * 0.95f, L.t("วิวัฒน์ได้ Lv " + Profile.MAX_LV, "Evolves at Lv " + Profile.MAX_LV), 24 * u, 0xFF0B2A14, 0xFF7CFF8A);
        String nm = (own && pf.evolved[t] ? Defs.evoName(t) : Defs.P_NAME[t]) + (own && pf.skinOn[t] >= 0 ? "  •  " + (L.en ? Skins.NAME[pf.skinOn[t]] : Skins.NAME_TH[pf.skinOn[t]]) : "");
        text(c, nm, W / 2, nameY, 56 * u, own ? 0xFFFFFFFF : 0xFF8A84A0, Paint.Align.CENTER, W - 80 * u);
        plain(c, own && pf.evolved[t] ? Defs.evoDesc(t) : L.en ? Defs.P_DESC_EN[t] : Defs.P_DESC[t], W / 2, nameY + 45 * u, 28 * u,
                own && pf.evolved[t] ? 0xFFB8FFC8 : 0xFFC9C2E6, Paint.Align.CENTER, W - 100 * u);
        atkChips(c, W / 2, nameY + 95 * u, Defs.P_ATTACK[t], 34 * u);
        if (!own && t == Defs.HERO)
            plain(c, L.t("ผ่านด่าน 3-15 เพื่อปลดล็อก", "Clear stage 3-15 to unlock"), W / 2, nameY - 70 * u, 30 * u, 0xFFFF9A7A, Paint.Align.CENTER, 0);
    }

    /** one-line stats for the planet page */
    String statLine(Planet tp0) {
        int kd = Defs.P_KIND[tp0.type];
        if (Game.isProducer(tp0)) return String.format(Locale.US, L.t("ผลิต %.1f SP ทุก %.1f วิ", "Makes %.1f SP every %.1fs"), Game.produceRate(tp0), Game.cdOf(tp0));
        if (tp0.type == Defs.SUN) return String.format(Locale.US, L.t("คลื่นความร้อน ทุก %.1f วิ", "Heat wave every %.1fs"), Game.cdOf(tp0));
        if (kd == Defs.K_WALL) return String.format(Locale.US, L.t("สร้างกำแพง 3 ชั้นบนทางเดิน ทุก %.1f วิ", "Builds 3 walls on the path every %.1fs"), Game.cdOf(tp0));
        if (kd == Defs.K_HAUNT) return String.format(Locale.US, L.t("Haunted + สโลว์ 35%%  ระยะ %d", "Haunted + 35%% slow  Range %d"), (int) Game.rangeOf(tp0));
        if (kd == Defs.K_STORM) return String.format(Locale.US, L.t("พายุน้ำ สตัน ทุก %.1f วิ  ระยะ %d", "Water storm stun every %.1fs  Range %d"), Game.cdOf(tp0), (int) Game.rangeOf(tp0));
        if (kd == Defs.K_NUKE) return L.t("ไม่โจมตี • สกิลระเบิดตัวเองและศัตรูทั้งแมพ", "No attacks • skill: blow up with every enemy");
        if (kd == Defs.K_KING) return L.t("บัฟทุกดวง +10% ทุกค่า • Elite Command", "+10% all stats to every planet • Elite Command");
        return String.format(Locale.US, L.t("ดาเมจ %.1f  ระยะ %d  ยิงทุก %.2f วิ", "DMG %.1f  Range %d  Every %.2fs"), Game.dmgOf(tp0), (int) Game.rangeOf(tp0), Game.cdOf(tp0));
    }

    // ------------------------------------------------------------------ equipment (skin + moon)
    void drawEquip(Canvas c) {
        final int t = selType;
        RectF r = new RectF(40 * u, 160 * u, W - 40 * u, H - 160 * u);
        popupFrame(c, r, L.t("อุปกรณ์: ", "Equipment: ") + Defs.P_NAME[t]);
        float y = r.top + 160 * u;
        sprite(c, pbmp(t), r.centerX(), y + 110 * u, 110 * u, 255);
        if (pf.moonOn[t] >= 0) {
            double ma = anim * 1.4;
            sprite(c, moonBmp(pf.moonOn[t]), r.centerX() + (float) Math.cos(ma) * 160 * u, y + 110 * u + (float) Math.sin(ma) * 40 * u, 26 * u, 255);
        }
        y += 260 * u;
        text(c, L.t("สกิน", "Skin"), r.left + 40 * u, y, 40 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
        y += 20 * u;
        java.util.ArrayList<Integer> opts = new java.util.ArrayList<>();
        opts.add(-1);
        for (int s = 0; s < Skins.STYLES; s++) if (pf.skins.contains(Skins.key(s, t))) opts.add(s);
        float cell = (r.width() - 60 * u) / 5f;
        for (int k = 0; k < opts.size(); k++) {
            final int s = opts.get(k);
            float x = r.left + 30 * u + (k % 5) * cell, yy = y + (k / 5) * (cell + 40 * u);
            RectF cr = new RectF(x + 6 * u, yy + 6 * u, x + cell - 6 * u, yy + cell - 6 * u);
            int rc = s < 0 ? 0xFF6A6488 : Defs.RARITY_COLOR[Skins.TIER[s]];
            rarityCard(c, cr, rc, true, pf.skinOn[t] == s, 20 * u);
            sprite(c, skinBmp(t, s), cr.centerX(), cr.centerY(), cell * 0.36f, 255);
            plain(c, s < 0 ? L.t("ปกติ", "Default") : (L.en ? Skins.NAME[s] : Skins.NAME_TH[s]), cr.centerX(), cr.bottom + 30 * u, 24 * u, 0xFFE6E0FF, Paint.Align.CENTER, cell);
            btns.add(new Btn(cr, () -> { pf.equipSkin(t, s); pushNow(); }));
        }
        if (opts.size() == 1)
            plain(c, L.t("ยังไม่มีสกินของดาวนี้ — เปิด Raid Capsule ในกระเป๋า", "No skins for this planet yet — open Raid Capsules in your Inventory"),
                    r.left + 30 * u + cell + 20 * u, y + cell / 2 + 10 * u, 26 * u, 0xFF8A84A0, Paint.Align.LEFT, r.width() - cell - 100 * u);
        y += ((opts.size() + 4) / 5) * (cell + 40 * u) + 50 * u;
        text(c, "Artifact", r.left + 40 * u, y, 40 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
        plain(c, L.t("ใส่ได้ดวงละ 1 ชิ้น", "One per planet"), r.right - 40 * u, y, 26 * u, 0xFFC9C2E6, Paint.Align.RIGHT, 0);
        y += 30 * u;
        for (int m = 0; m < Defs.MOON_COUNT; m++) {
            final int mm = m;
            RectF row = new RectF(r.left + 30 * u, y, r.right - 30 * u, y + 130 * u);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(alpha(Defs.MOON_COLOR[m], 0x30));
            c.drawRoundRect(row, 20 * u, 20 * u, p);
            glowCircle(c, row.left + 70 * u, row.centerY(), 60 * u, alpha(Defs.MOON_COLOR[m], 0x66));
            sprite(c, moonBmp(m), row.left + 70 * u, row.centerY(), 40 * u, 255);
            text(c, Defs.MOON_NAME[m] + "  x" + pf.moons[m], row.left + 130 * u, row.top + 52 * u, 34 * u, 0xFFFFFFFF, Paint.Align.LEFT, 0);
            plain(c, L.en ? Defs.MOON_DESC_EN[m] : Defs.MOON_DESC[m], row.left + 130 * u, row.top + 98 * u, 24 * u, 0xFFC9C2E6, Paint.Align.LEFT, row.width() - 400 * u);
            boolean on = pf.moonOn[t] == m;
            rf.set(row.right - 240 * u, row.top + 25 * u, row.right - 20 * u, row.bottom - 25 * u);
            button(c, rf, on ? L.t("ถอด", "Remove") : L.t("ใส่", "Equip"), on ? 0xFFFF6B5B : 0xFF33C6A6, on || pf.moonsFree(m) > 0, () -> {
                String err = pf.equipMoon(t, pf.moonOn[t] == mm ? -1 : mm);
                if (err != null) showToast(err);
                else pushNow();
            });
            y += 150 * u;
        }
        rf.set(r.centerX() - 200 * u, r.bottom - 120 * u, r.centerX() + 200 * u, r.bottom - 30 * u);
        button(c, rf, L.t("ปิด", "Close"), 0xFF6B5BD6, true, () -> popup = P_NONE);
    }

    // ------------------------------------------------------------------ inventory screen: capsules | skins | artifacts
    int invReveal = Integer.MIN_VALUE, invTab;
    float invRevealT;

    int evTab, socTab;
    int tutStep = -1;

    /** first-match tutorial: pick a card, place it, learn SP and upgrades, defend the base */
    void drawTutorial(Canvas c, Game g) {
        if (tutStep == -1) {
            boolean done = ctx.getSharedPreferences("stella_ui", Context.MODE_PRIVATE).getBoolean("tut", false);
            tutStep = done || g.stage != 0 || g.coop || g.challenge != 0 || pf.games > 0 ? 99 : 0;
        }
        if (tutStep >= 4 || g.won || g.lost) return;
        int placed = 0;
        for (Planet pl : g.slots) if (pl != null) placed++;
        if (tutStep == 0 && buildSel >= 0) tutStep = 1;
        if (tutStep == 1 && placed > 0) tutStep = 2;
        if (tutStep < 2 && !g.inWave) g.prepT = Math.max(g.prepT, 4f); // wait for the player
        String[] th = {"แตะการ์ดดาวด้านล่างเพื่อเลือก", "แตะช่องว่างบนแมพเพื่อวางดาว", "ดาวบางดวงผลิต SP • ใช้ SP วางดาวเพิ่ม และแตะดาวบนแมพเพื่ออัปเกรด", "อย่าให้ศัตรูเดินถึงฐาน! ผ่านครบทุกเวฟเพื่อชนะ"};
        String[] en = {"Tap a planet card below to pick it", "Tap an empty slot on the map to place it", "Some planets make SP • spend SP to place more and tap a planet to upgrade it", "Don't let enemies reach your base! Survive every wave to win"};
        float y = tutStep == 1 ? mapTop + 120 * u : panelTop - 360 * u;
        RectF b = new RectF(60 * u, y, W - 60 * u, y + 230 * u);
        panel(c, b, 0xF02A1A5E, 34 * u);
        pill(c, b.left + 120 * u, b.top + 40 * u, L.t("แนะนำ ", "Tip ") + (tutStep + 1) + "/4", 24 * u, 0xFF140A2A, 0xFFFFD23F);
        plain(c, L.en ? en[tutStep] : th[tutStep], b.left + 40 * u, b.top + 115 * u, 32 * u, 0xFFFFFFFF, Paint.Align.LEFT, b.width() - 80 * u);
        if (tutStep == 0) {
            // bouncing arrow toward the cards
            float ay = b.bottom + 30 * u + (float) Math.sin(anim * 6) * 12 * u;
            sp.setPathEffect(null);
            sp.setStrokeWidth(10 * u);
            sp.setColor(0xFFFFD23F);
            c.drawLine(W / 2, ay, W / 2, ay + 70 * u, sp);
            c.drawLine(W / 2, ay + 70 * u, W / 2 - 28 * u, ay + 42 * u, sp);
            c.drawLine(W / 2, ay + 70 * u, W / 2 + 28 * u, ay + 42 * u, sp);
        }
        if (tutStep >= 2) {
            rf.set(b.right - 260 * u, b.bottom - 90 * u, b.right - 30 * u, b.bottom - 20 * u);
            button(c, rf, tutStep == 3 ? L.t("เริ่มเลย!", "Let's go!") : L.t("ถัดไป", "Next"), 0xFF2FB86A, true, () -> {
                tutStep++;
                if (tutStep >= 4) ctx.getSharedPreferences("stella_ui", Context.MODE_PRIVATE).edit().putBoolean("tut", true).apply();
            });
        }
        rf.set(b.left + 30 * u, b.bottom - 90 * u, b.left + 200 * u, b.bottom - 20 * u);
        button(c, rf, L.t("ข้าม", "Skip"), 0xFF4A4562, true, () -> {
            tutStep = 99;
            ctx.getSharedPreferences("stella_ui", Context.MODE_PRIVATE).edit().putBoolean("tut", true).apply();
        });
    }
    float coopAcc, socAcc;
    boolean coopInGame, coopBusy;

    void socialRefresh() {
        if (cloud.uid == null) return;
        social.refresh(null);
        if (social.roomCode != null) social.poll(null);
    }

    /** room polling in the lobby, progress sync during a co-op match */
    void coopTick(float dt) {
        if (pf == null || cloud.uid == null || consoleApp) return;
        socAcc += dt;
        if (screen == S_SOCIAL && socAcc > 2.5f) { socAcc = 0; socialRefresh(); }
        else if (socAcc > 45f && screen != S_GAME) { socAcc = 0; social.refresh(null); }
        if (social.roomCode == null) return;
        coopAcc += dt;
        if (coopInGame && game != null && game.coop) {
            if (coopAcc < 1f || coopBusy) return;
            coopAcc = 0;
            coopBusy = true;
            final Game g = game;
            social.push(user, g.wave, g.leak, !g.lost, (r, err) -> {
                coopBusy = false;
                if (r == null || game != g) return;
                Social.Member o = r.other(cloud.uid);
                if (o != null) { g.otherLeak = o.leak; g.partnerName = o.name; g.partnerWave = o.wave; g.partnerAlive = o.alive; }
                int sum = 0;
                for (int v : r.giftsForMe.values()) sum += v;
                if (sum > 0) { g.stars += sum; g.text(g.px[g.px.length - 1], g.py[g.py.length - 1] - 60, "+" + sum + " SP", 0xFF7CFF8A); showToast(L.t("เพื่อนส่ง SP ให้ +", "Your teammate sent +") + sum + " SP"); }
            });
            return;
        }
        if (screen == S_GAME || coopAcc < 1.5f || coopBusy) return;
        coopAcc = 0;
        coopBusy = true;
        social.poll((r, err) -> {
            coopBusy = false;
            if (r == null) {
                if (err == null && social.roomCode != null) { social.roomCode = null; social.room = null; showToast(L.t("ห้องถูกปิดแล้ว", "The room was closed")); }
                return;
            }
            if ("playing".equals(r.state) && game == null && r.get(cloud.uid) != null && r.seed != lastCoopSeed) startCoopGame(r);
        });
    }

    long lastCoopSeed = -1;

    void startCoopGame(Social.Room r) {
        lastCoopSeed = r.seed;
        int stage = r.mode == Social.MODE_RAID ? Defs.RAID : Math.max(0, Math.min(r.stage, Defs.ST_COUNT - 1));
        startStage(stage);
        if (game == null) return;
        game.startCoop(r.seed);
        Social.Member o = r.other(cloud.uid);
        if (o != null) game.partnerName = o.name;
        coopInGame = true;
        coopAcc = 0;
        showToast(L.t("Co-op เริ่มแล้ว! ฐานใช้ร่วมกัน", "Co-op started! You share one base"));
    }

    void friendsIcon(Canvas c, float x, float y, float r) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(x - r * 0.35f, y - r * 0.3f, r * 0.28f, p);
        rf2.set(x - r * 0.85f, y + r * 0.05f, x + r * 0.15f, y + r * 0.9f);
        c.drawArc(rf2, 180, 180, true, p);
        p.setColor(0xFFDFFFE8);
        c.drawCircle(x + r * 0.38f, y - r * 0.2f, r * 0.24f, p);
        rf2.set(x - r * 0.05f, y + r * 0.12f, x + r * 0.85f, y + r * 0.9f);
        c.drawArc(rf2, 180, 180, true, p);
    }

    /** Friends • Co-op rooms • Gift codes */
    void drawSocial(Canvas c) {
        header(c, L.t("เพื่อน & Co-op", "Friends & Co-op"));
        String[] tabs = {L.t("เพื่อน", "Friends"), "Co-op"};
        boolean[] dot = {!social.requests.isEmpty(), !social.invites.isEmpty()};
        if (socTab > 1) socTab = 0;
        float tw = (W - 110 * u) / 2f;
        for (int i = 0; i < 2; i++) {
            final int ti = i;
            rf.set(50 * u + i * (tw + 10 * u), 190 * u, 50 * u + i * (tw + 10 * u) + tw, 280 * u);
            button(c, rf, tabs[i], socTab == i ? 0xFF2FB86A : 0xFF3A3360, true, () -> socTab = ti);
            if (dot[i]) { p.setShader(null); p.setColor(0xFFFF3B5B); c.drawCircle(rf.right - 16 * u, rf.top + 14 * u, 12 * u, p); }
        }
        if (cloud.uid == null) { plain(c, L.t("ต้องออนไลน์ก่อน", "You need to be online"), W / 2, 600 * u, 32 * u, 0xFFE6E0FF, Paint.Align.CENTER, 0); return; }
        if (socTab == 0) socFriends(c);
        else socCoop(c);
    }

    boolean friendOnline(Social.Friend f) { return f.seen > 0 && Math.abs(System.currentTimeMillis() - f.seen) < 60000; }

    void socFriends(Canvas c) {
        rf.set(40 * u, 310 * u, W - 40 * u, 410 * u);
        button(c, rf, L.t("+ เพิ่มเพื่อนด้วยชื่อไอดี", "+ Add a friend by name"), 0xFF4F8BFF, true, () ->
                Dialogs.ask(ctx, L.t("เพิ่มเพื่อน", "Add friend"), L.t("พิมพ์ชื่อไอดีของเพื่อน", "Your friend's account name"),
                        new String[]{L.t("ชื่อ", "Name")}, new int[]{Dialogs.TEXT}, new String[]{""}, L.t("ส่งคำขอ", "Send request"), v -> {
                            String nm = v[0].trim();
                            if (nm.isEmpty()) return L.t("ใส่ชื่อ", "Enter a name");
                            if (Accounts.norm(nm).equals(Accounts.norm(user))) return L.t("เพิ่มตัวเองไม่ได้", "That's you");
                            cloud.players((list, err) -> {
                                if (err != null) { showToast(err); return; }
                                for (Cloud.Player pl : list)
                                    if (Accounts.norm(pl.name).equals(Accounts.norm(nm))) {
                                        social.sendRequest(pl.uid, user, (ok, e2) -> showToast(e2 != null ? e2 : L.t("ส่งคำขอเป็นเพื่อนถึง ", "Friend request sent to ") + pl.name));
                                        return;
                                    }
                                showToast(L.t("ไม่พบผู้เล่นชื่อ ", "No player named ") + nm);
                            });
                            return null;
                        }));
        float y = 440 * u;
        java.util.List<Social.Friend> req, fr;
        synchronized (social) { req = new java.util.ArrayList<>(social.requests); fr = new java.util.ArrayList<>(social.friends); }
        if (!req.isEmpty()) {
            text(c, L.t("คำขอเป็นเพื่อน", "Friend requests"), 50 * u, y + 30 * u, 34 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
            y += 50 * u;
            for (final Social.Friend f : req) {
                RectF row = new RectF(40 * u, y, W - 40 * u, y + 110 * u);
                panel(c, row, 0xEE1A1440, 24 * u);
                text(c, f.name, row.left + 40 * u, row.centerY() + 12 * u, 34 * u, 0xFFFFFFFF, Paint.Align.LEFT, row.width() - 520 * u);
                rf.set(row.right - 460 * u, row.top + 15 * u, row.right - 250 * u, row.bottom - 15 * u);
                button(c, rf, L.t("รับ", "Accept"), 0xFF2FB86A, true, () -> social.accept(f, user, (ok, e) -> { showToast(e != null ? e : L.t("เป็นเพื่อนกับ ", "Now friends with ") + f.name); socialRefresh(); }));
                rf.set(row.right - 230 * u, row.top + 15 * u, row.right - 20 * u, row.bottom - 15 * u);
                button(c, rf, L.t("ปฏิเสธ", "Decline"), 0xFF6A4A60, true, () -> social.decline(f, (ok, e) -> socialRefresh()));
                y += 125 * u;
            }
            y += 20 * u;
        }
        text(c, L.t("เพื่อน ", "Friends ") + fr.size(), 50 * u, y + 30 * u, 34 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
        y += 50 * u;
        RectF view = new RectF(30 * u, y, W - 30 * u, navTop - 20 * u);
        beginScroll(c, 5000, view, fr.size() * 125 * u + 10 * u, 0.016f);
        float yy = view.top;
        for (final Social.Friend f : fr) {
            RectF row = new RectF(40 * u, yy, W - 40 * u, yy + 110 * u);
            panel(c, row, 0xEE1A1440, 24 * u);
            boolean on = friendOnline(f);
            p.setShader(null);
            p.setColor(on ? 0xFF7CFF8A : 0xFF6A6488);
            c.drawCircle(row.left + 40 * u, row.centerY(), 12 * u, p);
            text(c, f.name, row.left + 70 * u, row.centerY() - 2 * u, 34 * u, 0xFFFFFFFF, Paint.Align.LEFT, row.width() - 520 * u);
            plain(c, on ? L.t("ออนไลน์", "online") : L.t("ออฟไลน์", "offline"), row.left + 70 * u, row.centerY() + 34 * u, 22 * u, on ? 0xFF7CFF8A : 0xFF8A84A0, Paint.Align.LEFT, 0);
            RectF b1 = new RectF(row.right - 460 * u, row.top + 15 * u, row.right - 250 * u, row.bottom - 15 * u);
            boolean inRoom = social.roomCode != null;
            flatOrButton(c, b1, L.t("เชิญ", "Invite"), 0xFF2FB86A, inRoom);
            scrollBtn(b1, () -> {
                if (social.roomCode == null) { showToast(L.t("สร้างห้องในแท็บ Co-op ก่อน", "Create a room in the Co-op tab first")); return; }
                social.invite(f.uid, user, (ok, e) -> showToast(e != null ? e : L.t("ส่งคำเชิญถึง ", "Invited ") + f.name));
            });
            RectF b2 = new RectF(row.right - 230 * u, row.top + 15 * u, row.right - 20 * u, row.bottom - 15 * u);
            flatOrButton(c, b2, L.t("ลบ", "Remove"), 0xFF6A4A60, true);
            scrollBtn(b2, () -> Dialogs.confirm(ctx, L.t("ลบเพื่อน", "Remove friend"), f.name + "?", L.t("ลบ", "Remove"), () -> social.unfriend(f, (ok, e) -> socialRefresh())));
            yy += 125 * u;
        }
        if (fr.isEmpty()) plain(c, L.t("ยังไม่มีเพื่อน • เพิ่มด้วยชื่อไอดี", "No friends yet • add one by name"), W / 2, view.top + 80 * u, 28 * u, 0xFF8A84A0, Paint.Align.CENTER, 0);
        endScroll(c);
    }

    void socCoop(Canvas c) {
        Social.Room r = social.room;
        if (social.roomCode == null) {
            // no room: create or join, plus pending invites
            RectF box = new RectF(40 * u, 310 * u, W - 40 * u, 900 * u);
            modeCard(c, box, 0xFF2FB86A, 0xEE0F2A1E);
            friendsIcon(c, box.left + 110 * u, box.top + 110 * u, 70 * u);
            title(c, L.t("เล่นด้วยกัน 2 คน", "Play as a team of 2"), box.left + 210 * u, box.top + 100 * u, 46 * u, 0xFFFFFFFF, Paint.Align.LEFT, box.width() - 240 * u);
            plain(c, L.t("ฐานใช้ร่วมกัน " + Social.TEAM_HP + " HP • ส่ง SP ช่วยเพื่อนได้ • ชนะได้โบนัสทีม", "Shared " + Social.TEAM_HP + " HP base • send SP to help • team win bonus"),
                    box.left + 210 * u, box.top + 150 * u, 26 * u, 0xFFB8FFC8, Paint.Align.LEFT, box.width() - 240 * u);
            final int stage = Math.max(0, Math.min(pf.unlocked, Defs.ST_COUNT) - 1);
            rf.set(box.left + 30 * u, box.top + 230 * u, box.right - 30 * u, box.top + 340 * u);
            button(c, rf, L.t("สร้างห้อง Story ", "Create Story room ") + (Defs.galaxyOf(stage) + 1) + "-" + (stage % Defs.PER_GALAXY + 1), 0xFFFF8A2B, true,
                    () -> createRoom(Social.MODE_STORY, stage));
            rf.set(box.left + 30 * u, box.top + 360 * u, box.right - 30 * u, box.top + 470 * u);
            button(c, rf, L.t("สร้างห้อง Raid", "Create Raid room"), 0xFFFF3B6B, raidOpen(), () -> createRoom(Social.MODE_RAID, Defs.RAID));
            rf.set(box.left + 30 * u, box.top + 490 * u, box.right - 30 * u, box.top + 570 * u);
            button(c, rf, L.t("เข้าห้องด้วยโค้ด", "Join with a code"), 0xFF4F8BFF, true, () ->
                    Dialogs.ask(ctx, "Co-op", L.t("ใส่โค้ดห้อง 6 ตัว", "6-letter room code"), new String[]{L.t("โค้ด", "Code")}, new int[]{Dialogs.TEXT}, new String[]{""},
                            L.t("เข้าห้อง", "Join"), v -> { joinRoom(v[0]); return null; }));
            float y = box.bottom + 40 * u;
            java.util.List<Social.Invite> inv;
            synchronized (social) { inv = new java.util.ArrayList<>(social.invites); }
            text(c, L.t("คำเชิญ", "Invites"), 50 * u, y + 30 * u, 34 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
            y += 50 * u;
            if (inv.isEmpty()) plain(c, L.t("ยังไม่มีคำเชิญ", "No invites"), 50 * u, y + 40 * u, 26 * u, 0xFF8A84A0, Paint.Align.LEFT, 0);
            for (final Social.Invite iv : inv) {
                RectF row = new RectF(40 * u, y, W - 40 * u, y + 110 * u);
                panel(c, row, 0xEE1A1440, 24 * u);
                text(c, iv.name + L.t(" ชวนเล่น", " invited you"), row.left + 40 * u, row.centerY() + 12 * u, 32 * u, 0xFFFFFFFF, Paint.Align.LEFT, row.width() - 330 * u);
                rf.set(row.right - 250 * u, row.top + 15 * u, row.right - 20 * u, row.bottom - 15 * u);
                button(c, rf, L.t("เข้าร่วม", "Join"), 0xFF2FB86A, true, () -> joinRoom(iv.code));
                y += 125 * u;
            }
            return;
        }
        // in a room
        RectF box = new RectF(40 * u, 310 * u, W - 40 * u, 760 * u);
        modeCard(c, box, 0xFF2FB86A, 0xEE0F2A1E);
        plain(c, L.t("โค้ดห้อง", "Room code"), box.left + 40 * u, box.top + 60 * u, 26 * u, 0xFFB8FFC8, Paint.Align.LEFT, 0);
        title(c, social.roomCode, box.left + 40 * u, box.top + 150 * u, 90 * u, 0xFFFFFFFF, Paint.Align.LEFT, 0);
        String mode = r == null ? "..." : r.mode == Social.MODE_RAID ? "Raid" : "Story " + (Defs.galaxyOf(r.stage) + 1) + "-" + (r.stage % Defs.PER_GALAXY + 1);
        pill(c, box.right - 170 * u, box.top + 70 * u, mode, 30 * u, 0xFF0A0F1C, 0xFF7CFF8A);
        plain(c, L.t("บอกโค้ดให้เพื่อน หรือเชิญจากแท็บเพื่อน", "Share the code or invite from the Friends tab"), box.left + 40 * u, box.top + 200 * u, 24 * u, 0xFFE6E0FF, Paint.Align.LEFT, box.width() - 80 * u);
        float y = box.top + 240 * u;
        for (int k = 0; k < 2; k++) {
            Social.Member m = r != null && k < r.members.size() ? r.members.get(k) : null;
            RectF slot = new RectF(box.left + 30 * u + k * (box.width() / 2 - 15 * u), y, box.left + 30 * u + k * (box.width() / 2 - 15 * u) + box.width() / 2 - 45 * u, y + 170 * u);
            panel(c, slot, 0xEE141030, 24 * u);
            if (m == null) {
                plain(c, L.t("รอผู้เล่น...", "Waiting..."), slot.centerX(), slot.centerY() + 10 * u, 28 * u, 0xFF8A84A0, Paint.Align.CENTER, 0);
                continue;
            }
            boolean host = r != null && m.uid.equals(r.host);
            text(c, m.name, slot.centerX(), slot.top + 70 * u, 34 * u, 0xFFFFFFFF, Paint.Align.CENTER, slot.width() - 20 * u);
            pill(c, slot.centerX(), slot.top + 125 * u, host ? "HOST" : m.ready ? L.t("พร้อม", "READY") : L.t("ยังไม่พร้อม", "NOT READY"), 24 * u, 0xFF0A0F1C,
                    host ? 0xFFFFD23F : m.ready ? 0xFF7CFF8A : 0xFFFF8A8A);
        }
        boolean amHost = r != null && cloud.uid.equals(r.host);
        Social.Member me = r == null ? null : r.get(cloud.uid), other = r == null ? null : r.other(cloud.uid);
        float by = box.bottom + 40 * u;
        rf.set(40 * u, by, W - 40 * u, by + 130 * u);
        if (amHost) {
            boolean go = other != null && other.ready;
            button(c, rf, go ? L.t("เริ่มเกม!", "Start!") : L.t("รอเพื่อนกดพร้อม", "Waiting for your teammate"), 0xFFFF8A2B, go, () -> {
                if (pf.deckCount() == 0) { showToast(L.t("ใส่ดวงดาวใน Deck ก่อน", "Add planets to your deck first")); return; }
                social.start((ok, e) -> { if (e != null) showToast(e); });
            });
        } else {
            final boolean rd = me != null && me.ready;
            button(c, rf, rd ? L.t("ยกเลิกพร้อม", "Not ready") : L.t("พร้อม!", "Ready!"), rd ? 0xFF6A4A60 : 0xFF2FB86A, me != null, () -> {
                social.setReady(!rd, user);
                if (social.room != null && social.room.get(cloud.uid) != null) social.room.get(cloud.uid).ready = !rd;
            });
        }
        rf.set(40 * u, by + 160 * u, W - 40 * u, by + 260 * u);
        button(c, rf, amHost ? L.t("ปิดห้อง", "Close room") : L.t("ออกจากห้อง", "Leave room"), 0xFF5A1E2E, true, () -> { social.leave(); showToast(L.t("ออกจากห้องแล้ว", "Left the room")); });
        plain(c, L.t("ใช้ Deck ของตัวเอง • ศัตรูแยกบอร์ดแต่ฐานใช้ร่วมกัน", "Each uses their own deck • separate boards, one shared base"), W / 2, by + 320 * u, 24 * u, 0xFFC9C2E6, Paint.Align.CENTER, W - 80 * u);
    }

    void createRoom(int mode, int stage) {
        if (pf.deckCount() == 0) { showToast(L.t("ใส่ดวงดาวใน Deck ก่อน", "Add planets to your deck first")); return; }
        setBusy(L.t("กำลังสร้างห้อง...", "Creating room..."));
        social.create(mode, stage, user, (r, err) -> {
            setBusy(null);
            if (err != null) { showToast(err); return; }
            social.room = r;
        });
    }

    void joinRoom(String code) {
        setBusy(L.t("กำลังเข้าห้อง...", "Joining..."));
        social.join(code, user, (r, err) -> {
            setBusy(null);
            if (err != null) { showToast(err); return; }
            social.room = r;
            socTab = 1;
            showToast(L.t("เข้าห้องแล้ว • กดพร้อม", "Joined • tap Ready"));
        });
    }

    /** Events: daily/weekly challenges, daily missions and the 7-day login calendar */
    void drawEvents(Canvas c) {
        pf.refreshMissions();
        header(c, L.t("กิจกรรม", "Events"), 0, pf.stars, 2, pf.crystals);
        String[] tabs = {L.t("ชาเลนจ์", "Challenge"), L.t("ภารกิจ", "Missions")};
        boolean[] dot = {pf.dailyReady() || pf.weeklyReady(), pf.anyMissionClaim()};
        if (evTab > 1) evTab = 0;
        float tw = (W - 110 * u) / 2f;
        for (int i = 0; i < 2; i++) {
            final int ti = i;
            rf.set(50 * u + i * (tw + 10 * u), 190 * u, 50 * u + i * (tw + 10 * u) + tw, 280 * u);
            button(c, rf, tabs[i], evTab == i ? 0xFF2FB86A : 0xFF3A3360, true, () -> evTab = ti);
            if (dot[i]) { p.setShader(null); p.setColor(0xFFFF3B5B); c.drawCircle(rf.right - 16 * u, rf.top + 14 * u, 12 * u, p); }
        }
        if (evTab == 0) evChallenges(c);
        else evMissions(c);
    }

    void evChallenges(Canvas c) {
        float h = 600 * u, y = 320 * u;
        for (int k = 1; k <= 2; k++) {
            final int kind = k;
            boolean daily = k == 1;
            RectF box = new RectF(40 * u, y, W - 40 * u, y + h);
            int col = daily ? 0xFF5EC8FF : 0xFFB86BFF;
            modeCard(c, box, col, daily ? 0xEE0A1E36 : 0xEE1E0A36);
            int st = daily ? Defs.dailyStage(Profile.today()) : Defs.weeklyStage(Profile.week());
            int[] mods = daily ? new int[]{Defs.dailyMod(Profile.today())} : Defs.weeklyMods(Profile.week());
            boolean ready = daily ? pf.dailyReady() : pf.weeklyReady();
            pill(c, box.left + 170 * u, box.top + 60 * u, daily ? "DAILY" : "WEEKLY", 30 * u, 0xFF0A0F1C, col);
            plain(c, daily ? L.t("รีเซ็ตทุกวัน", "Resets daily") : L.t("รีเซ็ตทุกวันจันทร์", "Resets every Monday"), box.right - 40 * u, box.top + 70 * u, 24 * u, 0xFFC9C2E6, Paint.Align.RIGHT, 0);
            title(c, (daily ? L.t("ชาเลนจ์ประจำวัน", "Daily Challenge") : L.t("ชาเลนจ์ประจำสัปดาห์", "Weekly Challenge")), box.left + 40 * u, box.top + 150 * u, 52 * u, 0xFFFFFFFF, Paint.Align.LEFT, box.width() - 80 * u);
            plain(c, (L.en ? Defs.ST_NAME_EN[st] : Defs.ST_NAME[st]) + " • " + (daily ? Defs.DAILY_WAVES : Defs.WEEKLY_WAVES) + L.t(" เวฟ", " waves"),
                    box.left + 40 * u, box.top + 200 * u, 28 * u, 0xFFE6E0FF, Paint.Align.LEFT, box.width() - 80 * u);
            float my = box.top + 250 * u;
            for (int m : mods) {
                p.setShader(null);
                p.setColor(alpha(0xFFFF6B5B, 0x40));
                rf2.set(box.left + 40 * u, my, box.right - 40 * u, my + 56 * u);
                c.drawRoundRect(rf2, 16 * u, 16 * u, p);
                plain(c, Defs.CH_MOD[m] + " — " + (L.en ? Defs.CH_MOD_EN[m] : Defs.CH_MOD_TH[m]), box.left + 60 * u, my + 38 * u, 26 * u, 0xFFFFC0B0, Paint.Align.LEFT, box.width() - 120 * u);
                my += 66 * u;
            }
            // rewards
            plain(c, L.t("รางวัล (ครั้งแรกของรอบ):", "Reward (first win):"), box.left + 40 * u, box.bottom - 175 * u, 24 * u, 0xFFC9C2E6, Paint.Align.LEFT, 0);
            float rx = box.left + 70 * u, ry = box.bottom - 120 * u;
            int[][] rew = daily ? new int[][]{{Items.STARDUST, 8}, {Items.NEBULA, 1}, {-1, 500}} : new int[][]{{Items.CATALYST, 1}, {Items.NEBULA, 3}, {-3, 40}};
            for (int[] rw : rew) {
                if (rw[0] >= 0) itemIcon(c, rw[0], rx, ry, 30 * u);
                else if (rw[0] == -1) star(c, rx, ry, 24 * u);
                else oilIcon(c, rx, ry, 22 * u);
                text(c, "x" + rw[1], rx + 40 * u, ry + 12 * u, 30 * u, 0xFFFFE27A, Paint.Align.LEFT, 0);
                rx += 170 * u;
            }
            rf.set(box.right - 280 * u, box.bottom - 160 * u, box.right - 30 * u, box.bottom - 40 * u);
            button(c, rf, ready ? L.t("เล่น", "Play") : L.t("เล่นอีก", "Replay"), ready ? col : 0xFF4A4562, true, () -> startChallenge(kind));
            if (!ready) pill(c, box.right - 155 * u, box.bottom - 200 * u, L.t("ผ่านแล้ว", "Done"), 24 * u, 0xFF0B2A14, 0xFF7CFF8A);
            y += h + 30 * u;
        }
    }

    void evMissions(Canvas c) {
        plain(c, L.t("ภารกิจรีเซ็ตทุกวัน • ทำครบ 3 อันรับโบนัส", "Missions reset daily • finish all 3 for a bonus"), 50 * u, 335 * u, 26 * u, 0xFFE6E0FF, Paint.Align.LEFT, W - 100 * u);
        float y = 370 * u, h = 230 * u;
        String[] rk = {"Star", "Crystal", "Crystal Oil"};
        for (int i = 0; i < Missions.PER_DAY; i++) {
            final int mi = i;
            int t = pf.missionType[i];
            RectF box = new RectF(40 * u, y, W - 40 * u, y + h);
            boolean done = pf.missionDone[i], ready = pf.missionReady(i);
            modeCard(c, box, done ? 0xFF4A4562 : ready ? 0xFF7CFF8A : 0xFF4F8BFF, 0xEE141030);
            text(c, Missions.name(t), box.left + 40 * u, box.top + 70 * u, 36 * u, done ? 0xFF8A84A0 : 0xFFFFFFFF, Paint.Align.LEFT, box.width() - 340 * u);
            int prog = pf.missionProg[i], tgt = Missions.TARGET[t];
            progressBar(c, box.left + 40 * u, box.top + 110 * u, box.width() - 360 * u, 22 * u, prog / (float) tgt, ready || done ? 0xFF7CFF8A : 0xFF4F8BFF);
            plain(c, prog + " / " + tgt, box.left + 40 * u, box.top + 170 * u, 26 * u, 0xFFC9C2E6, Paint.Align.LEFT, 0);
            plain(c, "+" + Missions.REWARD[t] + " " + rk[Math.min(2, Missions.REWARD_KIND[t])], box.left + box.width() - 360 * u, box.top + 170 * u, 26 * u, 0xFFFFE27A, Paint.Align.RIGHT, 0);
            rf.set(box.right - 280 * u, box.centerY() - 55 * u, box.right - 30 * u, box.centerY() + 55 * u);
            button(c, rf, done ? L.t("รับแล้ว", "Claimed") : L.t("รับ", "Claim"), 0xFF2FB86A, ready, () -> {
                String s = pf.claimMission(mi);
                if (s != null) { Sfx.play(Sfx.COIN); showToast("+" + s); pushNow(); }
            });
            y += h + 20 * u;
        }
        RectF bb = new RectF(40 * u, y + 10 * u, W - 40 * u, y + 230 * u);
        boolean bonus = pf.bonusReady();
        modeCard(c, bb, bonus ? 0xFFFFD23F : 0xFF4A4562, 0xEE2A1E0A);
        trophy(c, bb.left + 90 * u, bb.centerY(), 50 * u);
        text(c, L.t("โบนัสทำครบ", "All-clear bonus"), bb.left + 170 * u, bb.top + 85 * u, 38 * u, 0xFFFFFFFF, Paint.Align.LEFT, bb.width() - 460 * u);
        plain(c, Missions.BONUS_CRYSTAL + " Crystal + " + Missions.BONUS_CAPSULE + " Raid Capsule + 1 Nebula Core", bb.left + 170 * u, bb.top + 140 * u, 24 * u, 0xFFFFE27A, Paint.Align.LEFT, bb.width() - 460 * u);
        rf.set(bb.right - 280 * u, bb.centerY() - 55 * u, bb.right - 30 * u, bb.centerY() + 55 * u);
        button(c, rf, pf.missionBonus ? L.t("รับแล้ว", "Claimed") : L.t("รับ", "Claim"), 0xFFE0A020, bonus, () -> {
            String s = pf.claimBonus();
            if (s != null) { Sfx.play(Sfx.RARE); showToast("+" + s); pushNow(); }
        });
    }

    void evLogin(Canvas c, float top) {
        plain(c, L.t("เข้าเกมทุกวันรับของ • วันที่ 7 ได้โบนัสใหญ่", "Log in every day • day 7 has a big bonus"), 50 * u, top + 335 * u, 26 * u, 0xFFE6E0FF, Paint.Align.LEFT, W - 100 * u);
        float cw = (W - 130 * u) / 4f, ch = 300 * u;
        int slot = pf.loginSlot();
        boolean ready = pf.giftReady();
        String[] kn = {"Star", "Crystal", "Crystal Oil", "Raid Capsule"};
        for (int i = 0; i < 7; i++) {
            int col = i % 4, row = i / 4;
            RectF q = new RectF(50 * u + col * (cw + 10 * u), top + 380 * u + row * (ch + 20 * u), 50 * u + col * (cw + 10 * u) + cw, top + 380 * u + row * (ch + 20 * u) + ch);
            if (i == 6) q.right = W - 50 * u;
            boolean past = i < slot, today = i == slot;
            rarityCard(c, q, i == 6 ? 0xFFFFD23F : today ? 0xFF7CFF8A : 0xFF6B5BD6, past || today, today && ready, 22 * u);
            text(c, L.t("วันที่ ", "Day ") + (i + 1), q.centerX(), q.top + 50 * u, 28 * u, 0xFFFFFFFF, Paint.Align.CENTER, 0);
            int[] rw = Missions.LOGIN[i];
            float ix = q.centerX(), iy = q.top + 145 * u;
            if (rw[0] == 0) star(c, ix, iy, 36 * u);
            else if (rw[0] == 1) crystalIcon(c, ix, iy, 36 * u);
            else if (rw[0] == 2) oilIcon(c, ix, iy, 36 * u);
            else capsuleIcon(c, ix, iy, 44 * u);
            plain(c, "x" + rw[1] + (i == 6 ? " + " + Missions.LOGIN_DAY7_CRYSTAL + " Crystal" : ""), q.centerX(), q.bottom - 60 * u, 24 * u, 0xFFFFE27A, Paint.Align.CENTER, q.width() - 10 * u);
            plain(c, kn[rw[0]], q.centerX(), q.bottom - 25 * u, 20 * u, 0xFFC9C2E6, Paint.Align.CENTER, q.width() - 10 * u);
            if (past) {
                sp.setPathEffect(null);
                sp.setStrokeWidth(8 * u);
                sp.setColor(0xFF7CFF8A);
                float kx = q.right - 36 * u, ky = q.top + 40 * u;
                c.drawLine(kx - 14 * u, ky, kx - 4 * u, ky + 12 * u, sp);
                c.drawLine(kx - 4 * u, ky + 12 * u, kx + 16 * u, ky - 12 * u, sp);
            }
        }
        rf.set(80 * u, top + 380 * u + 2 * (ch + 20 * u) + 40 * u, W - 80 * u, top + 380 * u + 2 * (ch + 20 * u) + 160 * u);
        button(c, rf, ready ? L.t("รับของวันนี้", "Claim today's reward") : L.t("รับแล้ว กลับมาพรุ่งนี้", "Claimed — come back tomorrow"), 0xFF2FB86A, ready, () -> {
            String s = pf.claimLogin();
            if (s != null) { Sfx.play(Sfx.COIN); showToast("+" + s); pushNow(); }
        });
    }

    boolean loginShown;

    /** daily login calendar popup: opens by itself on every launch, and from the lobby button */
    void drawLoginPopup(Canvas c) {
        RectF r = new RectF(30 * u, 230 * u, W - 30 * u, 1520 * u);
        popupFrame(c, r, L.t("ของขวัญล็อกอินรายวัน", "Daily login rewards"));
        evLogin(c, r.top - 120 * u);
        rf.set(r.centerX() - 200 * u, r.bottom - 110 * u, r.centerX() + 200 * u, r.bottom - 30 * u);
        button(c, rf, L.t("ปิด", "Close"), 0xFF6B5BD6, true, () -> popup = P_NONE);
    }

    void calendarIcon(Canvas c, float x, float y, float r) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        rf2.set(x - r * 0.8f, y - r * 0.6f, x + r * 0.8f, y + r * 0.85f);
        c.drawRoundRect(rf2, r * 0.18f, r * 0.18f, p);
        p.setColor(0xFFFF5B6B);
        c.drawRect(rf2.left, rf2.top, rf2.right, y - r * 0.2f, p);
        p.setColor(0xFF6B5BD6);
        for (int k = 0; k < 2; k++) {
            float kx = x + (k == 0 ? -r * 0.4f : r * 0.4f);
            c.drawRoundRect(new RectF(kx - r * 0.09f, y - r * 0.82f, kx + r * 0.09f, y - r * 0.45f), r * 0.08f, r * 0.08f, p);
        }
        p.setColor(0xFF2A1650);
        for (int k = 0; k < 6; k++) c.drawCircle(x - r * 0.45f + (k % 3) * r * 0.45f, y + r * 0.12f + (k / 3) * r * 0.38f, r * 0.09f, p);
    }

    void askRedeem() {
        Dialogs.ask(ctx, L.t("โค้ดของขวัญ", "Gift code"), L.t("พิมพ์โค้ดที่ได้รับจากทีมงาน", "Type a code from the team"), new String[]{L.t("โค้ด", "Code")},
                new int[]{Dialogs.TEXT}, new String[]{""}, L.t("รับของ", "Redeem"), v -> {
                    setBusy(L.t("กำลังตรวจโค้ด...", "Checking..."));
                    social.redeem(v[0], pf, (got, err) -> {
                        setBusy(null);
                        if (err != null) { Sfx.play(Sfx.ERROR); showToast(err); return; }
                        Sfx.play(Sfx.RARE);
                        showToast(L.t("ได้รับ: ", "Received: ") + got);
                        pushNow();
                    });
                    return null;
                });
    }

    void drawInvScreen(Canvas c) {
        header(c, L.t("กระเป๋า", "Inventory"), 0, pf.stars, 3, pf.oil);
        int caps = pf.raidCapsules + pf.items[Items.ART_CAPSULE] + pf.items[Items.SUPPLY];
        String[] tabs = {L.t("กระเป๋า", "Bag") + (caps > 0 ? " " + caps : ""), L.t("คราฟ", "Craft"), "Artifact", L.t("สกิน", "Skins")};
        int[] cols = {0xFFFF3B6B, 0xFF7CFF8A, 0xFFFF8A2B, 0xFFB86BFF};
        float tw = (W - 130 * u) / 4f;
        for (int i = 0; i < 4; i++) {
            final int ti = i;
            rf.set(50 * u + i * (tw + 10 * u), 190 * u, 50 * u + i * (tw + 10 * u) + tw, 280 * u);
            button(c, rf, tabs[i], invTab == i ? cols[i] : 0xFF3A3360, true, () -> invTab = ti);
        }
        if (invTab == 0) invBag(c);
        else if (invTab == 1) invItems(c);
        else if (invTab == 2) invArtifacts(c);
        else invSkins(c);
    }

    int capSel;
    /** what the last opened capsule gave: kind 0 skin, 1 artifact, 2 supply */
    int revealKind = -1, revealA, revealB;

    void invCapsules(Canvas c) {
        // capsule selector row
        int[] counts = {pf.raidCapsules, pf.items[Items.ART_CAPSULE], pf.items[Items.SUPPLY]};
        String[] names = {"Raid Capsule", "Artifact Capsule", "Supply Capsule"};
        int[] cols = {0xFFFF3B6B, 0xFFFF9A3B, 0xFF5EC8FF};
        float cw = (W - 120 * u) / 3f;
        for (int i = 0; i < 3; i++) {
            final int ci = i;
            RectF q = new RectF(50 * u + i * (cw + 10 * u), 310 * u, 50 * u + i * (cw + 10 * u) + cw, 560 * u);
            rarityCard(c, q, cols[i], counts[i] > 0, capSel == i, 24 * u);
            capsuleOf(c, i, q.centerX(), q.top + 100 * u, 58 * u);
            plain(c, names[i], q.centerX(), q.bottom - 62 * u, 24 * u, 0xFFFFFFFF, Paint.Align.CENTER, cw - 10 * u);
            text(c, "x" + counts[i], q.centerX(), q.bottom - 18 * u, 34 * u, counts[i] > 0 ? 0xFFFFE27A : 0xFF8A84A0, Paint.Align.CENTER, 0);
            btns.add(new Btn(q, () -> { capSel = ci; revealKind = -1; }));
        }
        RectF box = new RectF(40 * u, 590 * u, W - 40 * u, navTop - 180 * u);
        modeCard(c, box, cols[capSel], 0xEE1A0F30);
        float cx = box.centerX(), cy = box.top + box.height() * 0.4f;
        invRevealT += 0.016f;
        float e = Math.min(1f, invRevealT / 0.6f);
        if (revealKind >= 0) {
            int rc;
            String nm, sub;
            if (revealKind == 0) {
                int key = revealA < 0 ? -revealA - 1 : revealA;
                int st = Skins.styleOf(key), pt = Skins.planetOf(key);
                rc = Defs.RARITY_COLOR[Skins.TIER[st]];
                nm = Skins.name(key);
                sub = revealA < 0 ? L.t("ซ้ำ! คืน +", "Duplicate! +") + Profile.DUP_SKIN_OIL + " Crystal Oil" : Defs.RARITY_NAME[Skins.TIER[st]] + L.t(" • สกินใหม่!", " • new skin!");
                rays(c, cx, cy, rc, e);
                sprite(c, skinBmp(pt, st), cx, cy, 150 * u * backOut(e), 255);
            } else if (revealKind == 1) {
                rc = Defs.MOON_COLOR[revealA];
                nm = Defs.MOON_NAME[revealA];
                sub = L.en ? Defs.MOON_DESC_EN[revealA] : Defs.MOON_DESC[revealA];
                rays(c, cx, cy, rc, e);
                sprite(c, moonBmp(revealA), cx, cy, 120 * u * backOut(e), 255);
            } else {
                rc = 0xFF5EC8FF;
                String[] kn = {"Star", "Millite", "Crystal", "Stardust", "Nebula Core"};
                nm = "+" + revealB + " " + kn[revealA];
                sub = L.t("จาก Supply Capsule", "from a Supply Capsule");
                rays(c, cx, cy, rc, e);
                float sz = 110 * u * backOut(e);
                if (revealA == 0) star(c, cx, cy, sz * 0.6f);
                else if (revealA == 1) milliteIcon(c, cx, cy, sz * 0.6f);
                else if (revealA == 2) crystalIcon(c, cx, cy, sz * 0.6f);
                else itemIcon(c, revealA == 3 ? Items.STARDUST : Items.NEBULA, cx, cy, sz * 0.8f);
            }
            title(c, nm, cx, cy + 200 * u, 50 * u, rc, Paint.Align.CENTER, box.width() - 60 * u);
            plain(c, sub, cx, cy + 255 * u, 28 * u, 0xFFFFFFFF, Paint.Align.CENTER, box.width() - 60 * u);
        } else {
            capsuleOf(c, capSel, cx, cy, 150 * u);
            String[] desc = {L.t("สุ่มสกินดวงดาว Common > Exclusive", "Random planet skin, Common > Exclusive"),
                    L.t("สุ่ม Artifact 1 ชิ้น (Hell, Frost, Storm, Gold, Void)", "1 random Artifact (Hell, Frost, Storm, Gold, Void)"),
                    L.t("สุ่ม Star, Millite, Crystal หรือวัตถุดิบวิวัฒน์", "Star, Millite, Crystal or evolution materials")};
            title(c, names[capSel], cx, cy + 220 * u, 52 * u, 0xFFFFFFFF);
            plain(c, desc[capSel], cx, cy + 275 * u, 28 * u, 0xFFE6E0FF, Paint.Align.CENTER, box.width() - 60 * u);
        }
        float bw = (box.width() - 90 * u) / 2f, by = box.bottom - 125 * u;
        rf.set(box.left + 30 * u, by, box.left + 30 * u + bw, by + 100 * u);
        boolean has = counts[capSel] > 0;
        button(c, rf, has ? L.t("เปิด", "Open") + " (" + counts[capSel] + ")" : L.t("ไม่มี Capsule", "No capsules"), cols[capSel], has, this::openSelectedCapsule);
        rf.set(box.right - 30 * u - bw, by, box.right - 30 * u, by + 100 * u);
        button(c, rf, L.t("อัตรา", "Rates"), 0xFF4F8BFF, true, () -> popup = P_CRATES);
        rf.set(40 * u, box.bottom + 30 * u, W - 40 * u, box.bottom + 130 * u);
        button(c, rf, L.t("ร้านค้า Raid", "Raid shop"), 0xFFE0A020, true, () -> {
            if (raidOpen()) popup = P_RSHOP;
            else showToast(L.t("ชนะให้ครบ " + RAID_WINS + " ครั้งเพื่อปลดล็อกร้าน Raid", "Win " + RAID_WINS + " games to unlock the Raid shop"));
        });
    }

    /** bag entries: 0 Raid Capsule, then Items ids + 1 */
    int bagSel;

    int bagCount(int e) { return e == 0 ? pf.raidCapsules : pf.items[e - 1]; }

    String bagName(int e) { return e == 0 ? "Raid Capsule" : Items.NAME[e - 1]; }

    String bagDesc(int e) {
        return e == 0 ? L.t("สุ่มสกินดวงดาว Common > Exclusive", "Random planet skin, Common > Exclusive") : L.en ? Items.DESC_EN[e - 1] : Items.DESC[e - 1];
    }

    boolean bagUsable(int e) { return e == 0 || e - 1 == Items.ART_CAPSULE || e - 1 == Items.SUPPLY; }

    int bagRarity(int e) { return e == 0 ? Defs.LEGEND : Items.RARITY[e - 1]; }

    void bagIconOf(Canvas c, int e, float x, float y, float r) {
        if (e == 0) capsuleIcon(c, x, y, r);
        else itemIcon(c, e - 1, x, y, r);
    }

    /** one bag for everything: tap an item, then Use it */
    void invBag(Canvas c) {
        // order: capsules first, then materials
        int[] order = {0, 1 + Items.ART_CAPSULE, 1 + Items.SUPPLY, 1 + Items.STARDUST, 1 + Items.NEBULA, 1 + Items.CATALYST};
        float cell = (W - 100 * u) / 4f;
        for (int k = 0; k < order.length; k++) {
            final int e = order[k];
            int n = bagCount(e);
            float x = 50 * u + (k % 4) * cell, y = 310 * u + (k / 4) * (cell + 10 * u);
            RectF q = new RectF(x + 6 * u, y + 6 * u, x + cell - 6 * u, y + cell - 6 * u);
            rarityCard(c, q, Defs.RARITY_COLOR[bagRarity(e)], n > 0, bagSel == e, 22 * u);
            bagIconOf(c, e, q.centerX(), q.centerY() - 10 * u, cell * 0.28f);
            text(c, "x" + n, q.right - 14 * u, q.bottom - 14 * u, 28 * u, n > 0 ? 0xFFFFFFFF : 0xFF8A84A0, Paint.Align.RIGHT, 0);
            if (bagUsable(e) && n > 0) pill(c, q.left + 46 * u, q.top + 24 * u, L.t("ใช้ได้", "USE"), 16 * u, 0xFF0B2A14, 0xFF7CFF8A);
            btns.add(new Btn(q, () -> bagSel = e));
        }
        // detail panel
        int e = bagSel, n = bagCount(e);
        float top = 310 * u + 2 * (cell + 10 * u) + 20 * u;
        RectF box = new RectF(40 * u, top, W - 40 * u, navTop - 30 * u);
        int col = Defs.RARITY_COLOR[bagRarity(e)];
        modeCard(c, box, col, 0xEE1A0F30);
        float ix = box.left + 170 * u, iy = box.top + 180 * u;
        glowCircle(c, ix, iy, 150 * u, alpha(col, 0x55));
        bagIconOf(c, e, ix, iy, 105 * u);
        title(c, bagName(e), box.left + 320 * u, box.top + 110 * u, 50 * u, 0xFFFFFFFF, Paint.Align.LEFT, box.width() - 350 * u);
        pill(c, box.left + 400 * u, box.top + 170 * u, Defs.RARITY_NAME[bagRarity(e)], 24 * u, 0xFF140A2A, col);
        text(c, L.t("มี ", "Owned ") + n, box.left + 320 * u, box.top + 250 * u, 36 * u, 0xFFFFE27A, Paint.Align.LEFT, 0);
        plain(c, bagDesc(e), box.left + 40 * u, box.top + 380 * u, 28 * u, 0xFFE6E0FF, Paint.Align.LEFT, box.width() - 80 * u);
        float bw = (box.width() - 90 * u) / 2f, by = box.bottom - 130 * u;
        if (bagUsable(e)) {
            rf.set(box.left + 30 * u, by, box.left + 30 * u + bw, by + 100 * u);
            button(c, rf, n > 0 ? L.t("ใช้", "Use") : L.t("ไม่มี", "None"), 0xFF2FB86A, n > 0, () -> useBagItem(e));
            rf.set(box.right - 30 * u - bw, by, box.right - 30 * u, by + 100 * u);
            button(c, rf, L.t("อัตรา", "Rates"), 0xFF4F8BFF, true, () -> { capSel = e == 0 ? 0 : e - 1 == Items.ART_CAPSULE ? 1 : 2; popup = P_CRATES; });
        } else {
            rf.set(box.left + 30 * u, by, box.right - 30 * u, by + 100 * u);
            button(c, rf, L.t("ไปที่คราฟ", "Go to Craft"), 0xFF33C6A6, true, () -> invTab = 1);
        }
    }

    void useBagItem(int e) {
        capSel = e == 0 ? 0 : e - 1 == Items.ART_CAPSULE ? 1 : 2;
        revealKind = -1;
        openSelectedCapsule();
        if (revealKind >= 0) popup = P_REVEAL;
    }

    /** prize reveal after using a capsule */
    void drawReveal(Canvas c) {
        dim(c);
        float cx = W / 2, cy = H * 0.42f;
        invRevealT += 0.016f;
        float e = Math.min(1f, invRevealT / 0.6f);
        int rc;
        String nm, sub;
        if (revealKind == 0) {
            int key = revealA < 0 ? -revealA - 1 : revealA;
            int st = Skins.styleOf(key), pt = Skins.planetOf(key);
            rc = Defs.RARITY_COLOR[Skins.TIER[st]];
            nm = Skins.name(key);
            sub = revealA < 0 ? L.t("ซ้ำ! คืน +", "Duplicate! +") + Profile.DUP_SKIN_OIL + " Crystal Oil" : Defs.RARITY_NAME[Skins.TIER[st]] + L.t(" • สกินใหม่!", " • new skin!");
            rays(c, cx, cy, rc, e);
            sprite(c, skinBmp(pt, st), cx, cy, 190 * u * backOut(e), 255);
        } else if (revealKind == 1) {
            rc = Defs.MOON_COLOR[revealA];
            nm = Defs.MOON_NAME[revealA];
            sub = L.en ? Defs.MOON_DESC_EN[revealA] : Defs.MOON_DESC[revealA];
            rays(c, cx, cy, rc, e);
            sprite(c, moonBmp(revealA), cx, cy, 150 * u * backOut(e), 255);
        } else {
            rc = 0xFF5EC8FF;
            String[] kn = {"Star", "Millite", "Crystal", "Stardust", "Nebula Core"};
            nm = "+" + revealB + " " + kn[revealA];
            sub = L.t("จาก Supply Capsule", "from a Supply Capsule");
            rays(c, cx, cy, rc, e);
            float sz = 140 * u * backOut(e);
            if (revealA == 0) star(c, cx, cy, sz * 0.6f);
            else if (revealA == 1) milliteIcon(c, cx, cy, sz * 0.6f);
            else if (revealA == 2) crystalIcon(c, cx, cy, sz * 0.6f);
            else itemIcon(c, revealA == 3 ? Items.STARDUST : Items.NEBULA, cx, cy, sz * 0.8f);
        }
        title(c, nm, cx, cy + 300 * u, 64 * u, rc, Paint.Align.CENTER, W - 80 * u);
        plain(c, sub, cx, cy + 370 * u, 32 * u, 0xFFFFFFFF, Paint.Align.CENTER, W - 100 * u);
        float bw = (W - 180 * u) / 2f, by = H - 420 * u;
        int left = bagCount(capSel == 0 ? 0 : capSel == 1 ? 1 + Items.ART_CAPSULE : 1 + Items.SUPPLY);
        rf.set(60 * u, by, 60 * u + bw, by + 120 * u);
        button(c, rf, L.t("ใช้อีก", "Use again") + " (" + left + ")", 0xFF2FB86A, left > 0, () -> useBagItem(capSel == 0 ? 0 : capSel == 1 ? 1 + Items.ART_CAPSULE : 1 + Items.SUPPLY));
        rf.set(W - 60 * u - bw, by, W - 60 * u, by + 120 * u);
        button(c, rf, "OK", 0xFF6B5BD6, true, () -> popup = P_NONE);
    }

    void capsuleOf(Canvas c, int i, float x, float y, float r) {
        if (i == 0) capsuleIcon(c, x, y, r);
        else itemIcon(c, i == 1 ? Items.ART_CAPSULE : Items.SUPPLY, x, y, r);
    }

    void rays(Canvas c, float cx, float cy, int rc, float e) {
        for (int k = 0; k < 12; k++) {
            c.save();
            c.rotate(anim * 20 + k * 30, cx, cy);
            p.setShader(null);
            p.setColor(alpha(rc, (int) (60 * e)));
            Path ray = new Path();
            ray.moveTo(cx, cy);
            ray.lineTo(cx - 30 * u, cy - 300 * u * e);
            ray.lineTo(cx + 30 * u, cy - 300 * u * e);
            ray.close();
            c.drawPath(ray, p);
            c.restore();
        }
        glowCircle(c, cx, cy, 220 * u * e, alpha(rc, 0x99));
    }

    void openSelectedCapsule() {
        if (capSel == 0) {
            if (pf.raidCapsules <= 0) return;
            revealA = pf.openCapsule();
            revealKind = 0;
            int tier = Skins.TIER[Skins.styleOf(revealA < 0 ? -revealA - 1 : revealA)];
            Sfx.play(tier >= Defs.LEGEND ? Sfx.RARE : Sfx.GACHA);
        } else if (capSel == 1) {
            int a = pf.openArtifactCapsule();
            if (a < 0) return;
            revealA = a;
            revealKind = 1;
            Sfx.play(a == Defs.MOON_HELL || a == Defs.MOON_VOID ? Sfx.RARE : Sfx.GACHA);
        } else {
            int[] r = pf.openSupply();
            if (r == null) return;
            revealA = r[0];
            revealB = r[1];
            revealKind = 2;
            Sfx.play(Sfx.GACHA);
        }
        invRevealT = 0;
        pushNow();
    }

    /** materials + crafting */
    void invItems(Canvas c) {
        int[] mats = {Items.STARDUST, Items.NEBULA, Items.CATALYST};
        float cw = (W - 120 * u) / 3f;
        for (int i = 0; i < 3 && false; i++) {
            int id = mats[i];
            RectF q = new RectF(50 * u + i * (cw + 10 * u), 310 * u, 50 * u + i * (cw + 10 * u) + cw, 600 * u);
            rarityCard(c, q, Defs.RARITY_COLOR[Items.RARITY[id]], pf.items[id] > 0, false, 24 * u);
            itemIcon(c, id, q.centerX(), q.top + 105 * u, 62 * u);
            plain(c, Items.NAME[id], q.centerX(), q.bottom - 75 * u, 26 * u, 0xFFFFFFFF, Paint.Align.CENTER, cw - 10 * u);
            text(c, "x" + pf.items[id], q.centerX(), q.bottom - 25 * u, 38 * u, 0xFFFFE27A, Paint.Align.CENTER, 0);
            final int fid = id;
            btns.add(new Btn(q, () -> showToast(Items.NAME[fid] + ": " + (L.en ? Items.DESC_EN[fid] : Items.DESC[fid]))));
        }
        // material counts strip
        float sx = 60 * u;
        for (int id : mats) {
            itemIcon(c, id, sx + 30 * u, 345 * u, 28 * u);
            text(c, "x" + pf.items[id], sx + 70 * u, 358 * u, 32 * u, 0xFFFFE27A, Paint.Align.LEFT, 0);
            sx += 320 * u;
        }
        plain(c, L.t("หาวัตถุดิบจาก Daily / Weekly Challenge, ภารกิจ และ Supply Capsule", "Get materials from Daily / Weekly Challenges, missions and Supply Capsules"),
                50 * u, 425 * u, 24 * u, 0xFFC9C2E6, Paint.Align.LEFT, W - 100 * u);
        float rowH = 190 * u;
        RectF view = new RectF(30 * u, 450 * u, W - 30 * u, navTop - 20 * u);
        beginScroll(c, 4200, view, Items.RECIPE_OUT.length * (rowH + 14 * u), 0.016f);
        float y = view.top + 4 * u;
        for (int rI = 0; rI < Items.RECIPE_OUT.length; rI++) {
            final int ri = rI;
            int out = Items.RECIPE_OUT[rI];
            RectF row = new RectF(50 * u, y, W - 50 * u, y + rowH);
            boolean can = pf.canCraft(rI);
            modeCard(c, row, Items.COLOR[out], 0xEE1A0F30);
            float x = row.left + 70 * u;
            for (int[] in : Items.RECIPE_IN[rI]) {
                if (in[0] >= 0) itemIcon(c, in[0], x, row.top + 70 * u, 34 * u);
                else if (in[0] == -1) star(c, x, row.top + 70 * u, 26 * u);
                else if (in[0] == -2) milliteIcon(c, x, row.top + 70 * u, 26 * u);
                else oilIcon(c, x, row.top + 70 * u, 26 * u);
                boolean ok = pf.have(in[0]) >= in[1];
                plain(c, pf.have(in[0]) + "/" + in[1], x, row.top + 140 * u, 24 * u, ok ? 0xFF9CFF7A : 0xFFFF8A8A, Paint.Align.CENTER, 0);
                x += 150 * u;
            }
            // arrow
            sp.setPathEffect(null);
            sp.setStrokeWidth(6 * u);
            sp.setColor(0xFFFFFFFF);
            float ax = x - 40 * u;
            c.drawLine(ax, row.top + 70 * u, ax + 50 * u, row.top + 70 * u, sp);
            c.drawLine(ax + 50 * u, row.top + 70 * u, ax + 32 * u, row.top + 55 * u, sp);
            c.drawLine(ax + 50 * u, row.top + 70 * u, ax + 32 * u, row.top + 85 * u, sp);
            itemIcon(c, out, ax + 110 * u, row.top + 70 * u, 40 * u);
            plain(c, Items.NAME[out], ax + 110 * u, row.top + 140 * u, 22 * u, 0xFFFFFFFF, Paint.Align.CENTER, 200 * u);
            RectF b = new RectF(row.right - 210 * u, row.centerY() - 46 * u, row.right - 24 * u, row.centerY() + 46 * u);
            flatOrButton(c, b, L.t("คราฟ", "Craft"), 0xFF33C6A6, can);
            scrollBtn(b, () -> {
                String err = pf.craft(ri);
                if (err != null) { Sfx.play(Sfx.ERROR); showToast(err); return; }
                Sfx.play(Sfx.COIN);
                showToast("+1 " + Items.NAME[Items.RECIPE_OUT[ri]]);
                pushNow();
            });
            y += rowH + 14 * u;
        }
        endScroll(c);
    }

    void invSkins(Canvas c) {
        java.util.List<Integer> all = Skins.all();
        plain(c, L.t("สกินที่มี ", "Skins owned ") + pf.skins.size() + "/" + all.size() + L.t("  •  แตะเพื่อใส่ / ถอด", "  •  tap to equip / remove"), 50 * u, 335 * u, 28 * u, 0xFFE6E0FF, Paint.Align.LEFT, 0);
        float cell = (W - 100 * u) / 5f, rowH = cell + 40 * u, secH = 70 * u;
        RectF view = new RectF(30 * u, 360 * u, W - 30 * u, navTop - 20 * u);
        float contentH = 0;
        for (int tier : Skins.TIER_ORDER) {
            int n = 0;
            for (int k : all) if (Skins.TIER[Skins.styleOf(k)] == tier) n++;
            contentH += secH + (float) Math.ceil(n / 5f) * rowH;
        }
        beginScroll(c, 3000, view, contentH + 20 * u, 0.016f);
        float y = view.top;
        for (int ti = Skins.TIER_ORDER.length - 1; ti >= 0; ti--) {
            int tier = Skins.TIER_ORDER[ti];
            int rc = Defs.RARITY_COLOR[tier];
            int have = 0, n = 0;
            for (int k : all) if (Skins.TIER[Skins.styleOf(k)] == tier) { n++; if (pf.skins.contains(k)) have++; }
            p.setShader(null);
            p.setColor(rc);
            rf2.set(50 * u, y + 22 * u, 58 * u, y + 56 * u);
            c.drawRoundRect(rf2, 4 * u, 4 * u, p);
            text(c, Defs.RARITY_NAME[tier] + "  " + have + "/" + n, 72 * u, y + 52 * u, 34 * u, rc, Paint.Align.LEFT, 0);
            y += secH;
            int slot = 0;
            for (int k : all) {
                if (Skins.TIER[Skins.styleOf(k)] != tier) continue;
                final int key = k;
                final int st = Skins.styleOf(k), pt = Skins.planetOf(k);
                boolean own = pf.skins.contains(k);
                float x = 50 * u + (slot % 5) * cell, yy = y + (slot / 5) * rowH;
                slot++;
                RectF cr = new RectF(x + 6 * u, yy + 6 * u, x + cell - 6 * u, yy + cell - 6 * u);
                boolean on = own && pf.skinOn[pt] == st;
                rarityCard(c, cr, rc, own, on, 18 * u);
                sprite(c, skinBmp(pt, st), cr.centerX(), cr.centerY(), cell * 0.36f, own ? 255 : 40);
                if (!own) text(c, "?", cr.centerX(), cr.centerY() + 16 * u, 48 * u, 0x996A6488);
                if (on) pill(c, cr.centerX(), cr.top + 18 * u, L.t("ใส่อยู่", "ON"), 18 * u, 0xFF0B2A14, 0xFF7CFF8A);
                plain(c, Defs.P_NAME[pt], cr.centerX(), cr.bottom + 26 * u, 20 * u, own ? 0xFFE6E0FF : 0xFF6A6488, Paint.Align.CENTER, cell);
                scrollBtn(cr, () -> {
                    if (!pf.skins.contains(key)) { showToast(Skins.name(key) + L.t(" • ยังไม่มี (สุ่มจาก Raid Capsule)", " • not owned yet (Raid Capsule)")); return; }
                    if (!pf.owned[pt]) { showToast(L.t("ยังไม่มีดาว ", "You don't own ") + Defs.P_NAME[pt]); return; }
                    pf.equipSkin(pt, pf.skinOn[pt] == st ? -1 : st);
                    showToast(pf.skinOn[pt] == st ? L.t("ใส่สกิน ", "Equipped ") + Skins.name(key) : L.t("ถอดสกินแล้ว", "Skin removed"));
                    pushNow();
                });
            }
            y += (float) Math.ceil(n / 5f) * rowH;
        }
        endScroll(c);
    }

    void invArtifacts(Canvas c) {
        plain(c, L.t("Artifact ใส่ได้ดวงละ 1 ชิ้น และ 1 ชิ้นใส่ได้ดวงเดียว • ถอดก่อนย้าย", "One artifact per planet, each copy on one planet • remove it to move it"),
                50 * u, 335 * u, 26 * u, 0xFFE6E0FF, Paint.Align.LEFT, W - 100 * u);
        float rowH = 300 * u;
        RectF view = new RectF(30 * u, 360 * u, W - 30 * u, navTop - 20 * u);
        beginScroll(c, 4300, view, Defs.MOON_COUNT * (rowH + 16 * u), 0.016f);
        float y = view.top + 4 * u;
        for (int m = 0; m < Defs.MOON_COUNT; m++) {
            RectF box = new RectF(50 * u, y, W - 50 * u, y + rowH);
            modeCard(c, box, Defs.MOON_COLOR[m], 0xEE1A0F30);
            glowCircle(c, box.left + 120 * u, box.top + 120 * u, 110 * u, alpha(Defs.MOON_COLOR[m], pf.moons[m] > 0 ? 0x88 : 0x33));
            sprite(c, moonBmp(m), box.left + 120 * u, box.top + 120 * u, 64 * u, pf.moons[m] > 0 ? 255 : 90);
            text(c, Defs.MOON_NAME[m], box.left + 230 * u, box.top + 70 * u, 40 * u, 0xFFFFFFFF, Paint.Align.LEFT, box.width() - 260 * u);
            plain(c, L.en ? Defs.MOON_DESC_EN[m] : Defs.MOON_DESC[m], box.left + 230 * u, box.top + 115 * u, 24 * u, 0xFFFFE0D0, Paint.Align.LEFT, box.width() - 260 * u);
            plain(c, L.t("มี ", "Owned ") + pf.moons[m] + L.t("  •  ว่าง ", "  •  free ") + pf.moonsFree(m), box.left + 230 * u, box.top + 175 * u, 28 * u, 0xFF9CFF7A, Paint.Align.LEFT, 0);
            StringBuilder on = new StringBuilder();
            for (int t = 0; t < Defs.P_COUNT; t++) if (pf.moonOn[t] == m) on.append(on.length() > 0 ? ", " : "").append(Defs.P_NAME[t]);
            plain(c, L.t("ใส่อยู่ที่: ", "On: ") + (on.length() > 0 ? on.toString() : "-"), box.left + 40 * u, box.bottom - 40 * u, 24 * u, 0xFFC9C2E6, Paint.Align.LEFT, box.width() - 80 * u);
            y += rowH + 16 * u;
        }
        endScroll(c);
    }

    /** Artifact / Supply capsule contents with chances */
    void drawItemCapsuleRates(Canvas c) {
        boolean art = capSel == 1;
        RectF r = new RectF(40 * u, 300 * u, W - 40 * u, H - 300 * u);
        popupFrame(c, r, L.t("อัตรา: ", "Rates: ") + (art ? "Artifact Capsule" : "Supply Capsule"));
        int n = art ? Defs.MOON_COUNT : Items.SUPPLY_TABLE.length;
        float sum = 0;
        if (art) for (int w : Items.ART_WEIGHT) sum += w;
        else for (int[] row : Items.SUPPLY_TABLE) sum += row[1];
        float rowH = Math.min(150 * u, (r.height() - 330 * u) / n), y = r.top + 160 * u;
        String[] kn = {"Star", "Millite", "Crystal", "Stardust", "Nebula Core"};
        for (int i = 0; i < n; i++) {
            RectF row = new RectF(r.left + 30 * u, y, r.right - 30 * u, y + rowH - 12 * u);
            int col = art ? Defs.MOON_COLOR[i] : 0xFF5EC8FF;
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(alpha(col, 0x30));
            c.drawRoundRect(row, 18 * u, 18 * u, p);
            float ix = row.left + 70 * u, iy = row.centerY();
            String nm;
            if (art) { sprite(c, moonBmp(i), ix, iy, rowH * 0.3f, 255); nm = Defs.MOON_NAME[i]; }
            else {
                int[] t = Items.SUPPLY_TABLE[i];
                if (t[0] == 0) star(c, ix, iy, rowH * 0.22f);
                else if (t[0] == 1) milliteIcon(c, ix, iy, rowH * 0.22f);
                else if (t[0] == 2) crystalIcon(c, ix, iy, rowH * 0.22f);
                else itemIcon(c, t[0] == 3 ? Items.STARDUST : Items.NEBULA, ix, iy, rowH * 0.3f);
                nm = kn[t[0]] + "  " + t[2] + "-" + t[3];
            }
            text(c, nm, row.left + 140 * u, iy + 12 * u, 34 * u, 0xFFFFFFFF, Paint.Align.LEFT, row.width() - 340 * u);
            float pc = (art ? Items.ART_WEIGHT[i] : Items.SUPPLY_TABLE[i][1]) * 100f / sum;
            text(c, fmtPct(pc), row.right - 30 * u, iy + 12 * u, 36 * u, col, Paint.Align.RIGHT, 0);
            y += rowH;
        }
        rf.set(r.centerX() - 200 * u, r.bottom - 120 * u, r.centerX() + 200 * u, r.bottom - 30 * u);
        button(c, rf, L.t("ปิด", "Close"), 0xFF6B5BD6, true, () -> popup = P_NONE);
    }

    /** Raid capsule contents by tier, every skin with its chance */
    void drawCapsuleRates(Canvas c) {
        if (capSel != 0) { drawItemCapsuleRates(c); return; }
        RectF r = new RectF(30 * u, 110 * u, W - 30 * u, H - 110 * u);
        popupFrame(c, r, L.t("อัตรา: Raid Capsule", "Rates: Raid Capsule"));
        java.util.List<Integer> all = Skins.all();
        float cell = (r.width() - 80 * u) / 5f, rowH = cell + 44 * u, headH = 90 * u, contentH = 0;
        for (int tier : Skins.TIER_ORDER) {
            int n = 0;
            for (int k : all) if (Skins.TIER[Skins.styleOf(k)] == tier) n++;
            contentH += headH + (float) Math.ceil(n / 5f) * rowH + 20 * u;
        }
        RectF view = new RectF(r.left + 10 * u, r.top + 150 * u, r.right - 10 * u, r.bottom - 140 * u);
        beginScroll(c, 3100, view, contentH, 0.016f);
        float y = view.top;
        for (int ti = Skins.TIER_ORDER.length - 1; ti >= 0; ti--) {
            int tier = Skins.TIER_ORDER[ti];
            int col = Defs.RARITY_COLOR[tier];
            java.util.ArrayList<Integer> mm = new java.util.ArrayList<>();
            for (int k : all) if (Skins.TIER[Skins.styleOf(k)] == tier) mm.add(k);
            float each = Skins.TIER_RATE[ti] / Math.max(1, mm.size());
            RectF hb = new RectF(r.left + 30 * u, y + 10 * u, r.right - 30 * u, y + headH - 6 * u);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(alpha(col, 0x38));
            c.drawRoundRect(hb, 18 * u, 18 * u, p);
            p.setColor(col);
            rf2.set(hb.left, hb.top, hb.left + 12 * u, hb.bottom);
            c.drawRoundRect(rf2, 6 * u, 6 * u, p);
            text(c, Defs.RARITY_NAME[tier], hb.left + 34 * u, hb.centerY() + 14 * u, 40 * u, col, Paint.Align.LEFT, 0);
            text(c, fmtPct(Skins.TIER_RATE[ti]), hb.right - 24 * u, hb.centerY() + 14 * u, 40 * u, 0xFFFFFFFF, Paint.Align.RIGHT, 0);
            y += headH;
            for (int k = 0; k < mm.size(); k++) {
                int key = mm.get(k), st = Skins.styleOf(key), pt = Skins.planetOf(key);
                boolean own = pf.skins.contains(key);
                float x = r.left + 40 * u + (k % 5) * cell, yy = y + (k / 5) * rowH;
                RectF cr = new RectF(x + 6 * u, yy + 6 * u, x + cell - 6 * u, yy + cell - 6 * u);
                rarityCard(c, cr, col, own, false, 18 * u);
                sprite(c, skinBmp(pt, st), cr.centerX(), cr.centerY() - 8 * u, cell * 0.34f, 255);
                if (own) star(c, cr.right - 16 * u, cr.top + 16 * u, 10 * u);
                plain(c, fmtPct(each), cr.centerX(), cr.bottom - 12 * u, 20 * u, 0xFFFFFFFF, Paint.Align.CENTER, 0);
                plain(c, (st == Skins.ANNIV ? "1st Anniv" : L.en ? Skins.NAME[st] : Skins.NAME_TH[st]) + " " + Defs.P_NAME[pt], cr.centerX(), cr.bottom + 30 * u, 18 * u,
                        own ? 0xFFE6E0FF : 0xFF8A84A0, Paint.Align.CENTER, cell);
            }
            y += (float) Math.ceil(mm.size() / 5f) * rowH + 20 * u;
        }
        endScroll(c);
        plain(c, L.t("ดาวเหลือง = มีแล้ว  •  ได้ซ้ำคืน ", "yellow star = owned  •  duplicates refund ") + Profile.DUP_SKIN_OIL + " Crystal Oil", r.centerX(), r.bottom - 120 * u, 24 * u, 0xFF9C94C8, Paint.Align.CENTER, 0);
        rf.set(r.centerX() - 200 * u, r.bottom - 100 * u, r.centerX() + 200 * u, r.bottom - 25 * u);
        button(c, rf, L.t("ปิด", "Close"), 0xFF6B5BD6, true, () -> popup = P_NONE);
    }

    void bagIcon(Canvas c, float x, float y, float r) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        sp.setPathEffect(null);
        sp.setStrokeWidth(r * 0.16f);
        sp.setColor(0xFF5A2A08);
        rf2.set(x - r * 0.42f, y - r * 0.95f, x + r * 0.42f, y - r * 0.2f);
        c.drawArc(rf2, 180, 180, false, sp);
        rf2.set(x - r * 0.85f, y - r * 0.45f, x + r * 0.85f, y + r * 0.85f);
        p.setColor(0xFFFFE6B0);
        c.drawRoundRect(rf2, r * 0.3f, r * 0.3f, p);
        p.setColor(0xFFE0A050);
        rf2.set(x - r * 0.85f, y - r * 0.45f, x + r * 0.85f, y + r * 0.05f);
        c.drawRoundRect(rf2, r * 0.3f, r * 0.3f, p);
        p.setColor(0xFFFF3B6B);
        c.drawCircle(x, y + r * 0.05f, r * 0.16f, p);
        sp.setStrokeWidth(r * 0.08f);
        sp.setColor(0xFF5A2A08);
        rf2.set(x - r * 0.85f, y - r * 0.45f, x + r * 0.85f, y + r * 0.85f);
        c.drawRoundRect(rf2, r * 0.3f, r * 0.3f, sp);
    }

    String skinRates() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Skins.TIER_ORDER.length; i++) {
            if (i > 0) sb.append("  ");
            sb.append(Defs.RARITY_NAME[Skins.TIER_ORDER[i]]).append(' ').append(Skins.TIER_RATE[i]).append('%');
        }
        return sb.toString();
    }

    /** the Raid capsule: a red/black pod with a glowing seam */
    void capsuleIcon(Canvas c, float x, float y, float r) {
        capsulePod(c, x, y, r, 0xFF4A0818, 0xFFFF3B6B, 0xFFFFE27A, 0);
    }

    /**
     * glossy capsule: shadow, two-tone shell, metal waist band with lights, emblem,
     * specular shine and a few sparkles. emblem: 0 oil drop, 1 artifact moon, 2 star
     */
    void capsulePod(Canvas c, float x, float y, float r, int dark, int light, int glow, int emblem) {
        float bob = (float) Math.sin(anim * 2.4 + x * 0.01) * r * 0.06f;
        float cy = y + bob;
        glowCircle(c, x, cy, r * 1.55f, alpha(light, 0x50));
        // floor shadow
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x55000000);
        rf2.set(x - r * 0.62f, y + r * 0.98f, x + r * 0.62f, y + r * 1.16f);
        c.drawOval(rf2, p);
        RectF body = new RectF(x - r * 0.68f, cy - r, x + r * 0.68f, cy + r);
        // lower shell
        c.save();
        c.clipRect(body.left - 2, cy, body.right + 2, body.bottom + 2);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(body.left, 0, body.right, 0, new int[]{darken(dark, 0.2f), dark, lighten(dark, 0.25f), dark, darken(dark, 0.3f)}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(body, r * 0.68f, r * 0.68f, p);
        c.restore();
        // upper shell (lighter colour)
        c.save();
        c.clipRect(body.left - 2, body.top - 2, body.right + 2, cy);
        p.setShader(new LinearGradient(body.left, 0, body.right, 0, new int[]{darken(light, 0.35f), light, lighten(light, 0.45f), light, darken(light, 0.4f)}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(body, r * 0.68f, r * 0.68f, p);
        c.restore();
        p.setShader(null);
        // waist band with running lights
        rf2.set(body.left - r * 0.04f, cy - r * 0.11f, body.right + r * 0.04f, cy + r * 0.11f);
        p.setShader(new LinearGradient(0, rf2.top, 0, rf2.bottom, 0xFFE8ECF4, 0xFF6A7488, Shader.TileMode.CLAMP));
        c.drawRoundRect(rf2, r * 0.08f, r * 0.08f, p);
        p.setShader(null);
        for (int k = 0; k < 4; k++) {
            float lx = rf2.left + rf2.width() * (0.2f + k * 0.2f);
            float on = 0.5f + 0.5f * (float) Math.sin(anim * 6 - k * 1.2f);
            p.setColor(alpha(glow, (int) (120 + 135 * on)));
            c.drawCircle(lx, cy, r * 0.045f, p);
        }
        // emblem on the upper shell
        float ey = cy - r * 0.5f;
        p.setColor(0x33FFFFFF);
        c.drawCircle(x, ey, r * 0.27f, p);
        sp.setPathEffect(null);
        sp.setStrokeWidth(r * 0.04f);
        sp.setColor(alpha(glow, 0xCC));
        c.drawCircle(x, ey, r * 0.27f, sp);
        if (emblem == 0) oilIcon(c, x, ey, r * 0.17f);
        else if (emblem == 1) {
            p.setColor(glow);
            c.drawCircle(x, ey, r * 0.15f, p);
            p.setColor(darken(light, 0.3f));
            c.drawCircle(x + r * 0.06f, ey - r * 0.04f, r * 0.13f, p);
        } else star(c, x, ey, r * 0.16f);
        // specular shine
        p.setColor(0x66FFFFFF);
        rf2.set(x - r * 0.48f, cy - r * 0.88f, x - r * 0.24f, cy - r * 0.2f);
        c.drawRoundRect(rf2, r * 0.12f, r * 0.12f, p);
        p.setColor(0x33FFFFFF);
        c.drawCircle(x - r * 0.36f, cy + r * 0.55f, r * 0.06f, p);
        // outline
        sp.setStrokeWidth(r * 0.06f);
        sp.setColor(0xFF140A2A);
        c.drawRoundRect(body, r * 0.68f, r * 0.68f, sp);
        // sparkles
        for (int k = 0; k < 3; k++) {
            float ph = (anim * 0.7f + k * 0.33f) % 1f;
            double a = k * 2.1 + 0.6;
            float sx = x + (float) Math.cos(a) * r * 1.05f, sy = cy + (float) Math.sin(a) * r * 0.9f - ph * r * 0.3f;
            p.setColor(alpha(0xFFFFFFFF, (int) (255 * (1f - ph))));
            c.save();
            c.translate(sx, sy);
            c.scale(r * 0.09f, r * 0.09f);
            c.drawPath(Art.starPath(0, 0, 1f, 0.35f, 4), p);
            c.restore();
        }
    }

    /** row of attack-type tags, centred on cx */
    void atkChips(Canvas c, float cx, float cy, int mask, float size) {
        java.util.ArrayList<Integer> list = new java.util.ArrayList<>();
        for (int i = 0; i < Defs.AT_COUNT; i++) if ((mask & (1 << i)) != 0) list.add(i);
        tp.setTypeface(bodyFont);
        tp.setTextSize(size * 0.8f);
        float pad = size * 0.7f, gap = size * 0.4f, total = 0;
        float[] ws = new float[list.size()];
        for (int k = 0; k < ws.length; k++) {
            int i = list.get(k);
            ws[k] = tp.measureText(L.en ? Defs.AT_NAME_EN[i] : Defs.AT_NAME_TH[i]) + pad * 2 + size * 0.9f;
            total += ws[k] + (k > 0 ? gap : 0);
        }
        float x = cx - total / 2;
        for (int k = 0; k < ws.length; k++) {
            int i = list.get(k);
            int col = Defs.AT_COLOR[i];
            float bob = (float) Math.sin(anim * 2.5f + k) * size * 0.06f;
            RectF r = new RectF(x, cy - size * 0.75f + bob, x + ws[k], cy + size * 0.75f + bob);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(alpha(darken(col, 0.55f), 0xEE));
            c.drawRoundRect(r, size * 0.75f, size * 0.75f, p);
            sp.setPathEffect(null);
            sp.setStrokeWidth(size * 0.09f);
            sp.setColor(col);
            c.drawRoundRect(r, size * 0.75f, size * 0.75f, sp);
            atkIcon(c, r.left + pad * 0.7f + size * 0.35f, r.centerY(), size * 0.36f, i, col);
            plain(c, L.en ? Defs.AT_NAME_EN[i] : Defs.AT_NAME_TH[i], r.left + pad * 0.7f + size * 0.9f, r.centerY() + size * 0.28f, size * 0.8f, 0xFFFFFFFF, Paint.Align.LEFT, 0);
            x += ws[k] + gap;
        }
    }

    /** tiny symbol for each attack type */
    void atkIcon(Canvas c, float x, float y, float r, int type, int col) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(col);
        sp.setPathEffect(null);
        sp.setStrokeCap(Paint.Cap.ROUND);
        sp.setStrokeWidth(r * 0.35f);
        sp.setColor(col);
        switch (type) {
            case 0: // single: target dot
                c.drawCircle(x, y, r * 0.35f, p);
                c.drawCircle(x, y, r * 0.85f, sp);
                break;
            case 1: // aoe: burst
                c.drawPath(Art.starPath(x, y, r, r * 0.45f, 8), p);
                break;
            case 2: // pierce: arrow
                c.drawLine(x - r, y, x + r, y, sp);
                c.drawLine(x + r, y, x + r * 0.35f, y - r * 0.6f, sp);
                c.drawLine(x + r, y, x + r * 0.35f, y + r * 0.6f, sp);
                break;
            case 3: { // burn: flame
                Path f = new Path();
                f.moveTo(x, y - r);
                f.quadTo(x + r, y, x + r * 0.5f, y + r * 0.8f);
                f.quadTo(x, y + r * 1.1f, x - r * 0.5f, y + r * 0.8f);
                f.quadTo(x - r, y, x, y - r);
                f.close();
                c.drawPath(f, p);
                break;
            }
            case 4: // slow: snowflake
                for (int k = 0; k < 3; k++) {
                    double a = k * Math.PI / 3;
                    float dx = (float) Math.cos(a) * r, dy = (float) Math.sin(a) * r;
                    c.drawLine(x - dx, y - dy, x + dx, y + dy, sp);
                }
                break;
            case 5: { // stun: bolt
                Path b = new Path();
                b.moveTo(x + r * 0.3f, y - r);
                b.lineTo(x - r * 0.6f, y + r * 0.1f);
                b.lineTo(x, y + r * 0.1f);
                b.lineTo(x - r * 0.3f, y + r);
                b.lineTo(x + r * 0.6f, y - r * 0.15f);
                b.lineTo(x, y - r * 0.15f);
                b.close();
                c.drawPath(b, p);
                break;
            }
            case 6: // zone: rings
                c.drawCircle(x, y, r * 0.4f, sp);
                c.drawCircle(x, y, r * 0.95f, sp);
                break;
            case 7: // produce: star
                c.drawPath(Art.starPath(x, y, r, r * 0.45f, 5), p);
                break;
            case 8: // buff: up arrow
                c.drawLine(x, y + r, x, y - r, sp);
                c.drawLine(x, y - r, x - r * 0.6f, y - r * 0.35f, sp);
                c.drawLine(x, y - r, x + r * 0.6f, y - r * 0.35f, sp);
                break;
            case 9: // wave
                rf2.set(x - r, y - r * 0.6f, x, y + r * 0.6f);
                c.drawArc(rf2, 180, 180, false, sp);
                rf2.set(x, y - r * 0.6f, x + r, y + r * 0.6f);
                c.drawArc(rf2, 0, 180, false, sp);
                break;
            case 11: // wall: bricks
                rf2.set(x - r * 0.85f, y - r * 0.6f, x + r * 0.85f, y + r * 0.6f);
                c.drawRoundRect(rf2, r * 0.15f, r * 0.15f, p);
                sp.setStrokeWidth(r * 0.14f);
                sp.setColor(0xFF140A2A);
                c.drawLine(x - r * 0.85f, y, x + r * 0.85f, y, sp);
                c.drawLine(x, y - r * 0.6f, x, y, sp);
                c.drawLine(x - r * 0.4f, y, x - r * 0.4f, y + r * 0.6f, sp);
                c.drawLine(x + r * 0.4f, y, x + r * 0.4f, y + r * 0.6f, sp);
                break;
            case 12: { // haunt: ghost
                Path gh = new Path();
                gh.moveTo(x - r * 0.7f, y + r * 0.8f);
                gh.lineTo(x - r * 0.7f, y - r * 0.1f);
                gh.quadTo(x - r * 0.7f, y - r * 0.85f, x, y - r * 0.85f);
                gh.quadTo(x + r * 0.7f, y - r * 0.85f, x + r * 0.7f, y - r * 0.1f);
                gh.lineTo(x + r * 0.7f, y + r * 0.8f);
                gh.lineTo(x + r * 0.35f, y + r * 0.5f);
                gh.lineTo(x, y + r * 0.8f);
                gh.lineTo(x - r * 0.35f, y + r * 0.5f);
                gh.close();
                c.drawPath(gh, p);
                p.setColor(0xFF140A2A);
                c.drawCircle(x - r * 0.25f, y - r * 0.2f, r * 0.13f, p);
                c.drawCircle(x + r * 0.25f, y - r * 0.2f, r * 0.13f, p);
                break;
            }
            case 13: // nuke: skull-ish blast
                c.drawCircle(x, y, r * 0.5f, p);
                for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI / 4;
                    c.drawLine(x + (float) Math.cos(a) * r * 0.6f, y + (float) Math.sin(a) * r * 0.6f, x + (float) Math.cos(a) * r * 0.95f, y + (float) Math.sin(a) * r * 0.95f, sp);
                }
                break;
            case 14: // skill: crown
                Art.crown(c, x, y + r * 0.15f, r * 1.6f);
                break;
            case 15: // orbit: circle with a dot on it
                sp.setStrokeWidth(r * 0.2f);
                c.drawCircle(x, y, r * 0.75f, sp);
                c.drawCircle(x + (float) Math.cos(anim * 3) * r * 0.75f, y + (float) Math.sin(anim * 3) * r * 0.75f, r * 0.28f, p);
                c.drawCircle(x, y, r * 0.2f, p);
                break;
            default: // rapid: three dots
                for (int k = -1; k <= 1; k++) c.drawCircle(x + k * r * 0.7f, y, r * 0.28f, p);
        }
    }

    Planet previewPlanet(int t) {
        Planet q = new Planet();
        q.type = t;
        q.plv = pf.level[t];
        q.enchant = pf.enchant[t];
        q.tier = pf.tier[t];
        q.material = pf.material[t];
        q.moons = q.enchant == Defs.EN_MOON ? 1 : 0;
        q.evo = pf.evolved[t];
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
        sprite(c, own ? pbmp(t) : pBmp[t], x, y + bob, half, own ? 255 : 60);
        if (hasMat) materialBadge(c, x - half * 0.62f, y - half * 0.55f + bob, half * 0.2f, pf.material[t]);
        if (own && pf.enchant[t] >= 0) badge(c, x + half * 0.62f, y - half * 0.55f + bob, half * 0.2f, pf.enchant[t], pf.tier[t]);
        if (!own) text(c, "?", x, y + half * 0.25f, half * 0.8f, 0xFF8A84A0);
    }

    // ================================================================== play menu
    boolean galaxyOpen(int gal) { return pf.unlocked > gal * Defs.PER_GALAXY; }

    void drawPlayMenu(Canvas c) {
        if (playRaid && !raidOpen()) playRaid = false;
        if (playRaid) { header(c, "Raid", 0, pf.stars, 3, pf.oil); drawRaidMenu(c); return; }
        header(c, L.t("สตอรี่", "Story"));
        if (playSel < 0 || playSel >= pf.unlocked) {
            playSel = Math.min(pf.unlocked, Defs.ST_COUNT) - 1;
            playGal = Defs.galaxyOf(playSel);
        }
        if (playGal < 0) playGal = Defs.galaxyOf(playSel);
        final int gal = playGal;
        int gcol = Defs.GAL_COLOR[gal];
        // ---- bottom up: start button, detail card, stage grid, galaxy tabs, art
        float sbH = 125 * u, sbY = navTop - 40 * u - sbH;
        float dH = 300 * u, dY = sbY - 24 * u - dH;
        float cellW = (W - 100 * u - 4 * 16 * u) / 5f, cellH = 128 * u, gapG = 16 * u;
        float gridY = dY - 30 * u - 3 * cellH - 2 * gapG;
        float tabH = 92 * u, tabY = gridY - 30 * u - tabH;
        modeTabs(c);
        float aTop = 300 * u, aBot = tabY - 26 * u;
        // ---- art: the galaxy's black hole spitting out enemies
        RectF art = new RectF(40 * u, aTop, W - 40 * u, aBot);
        blackHoleScene(c, art, gal);
        // ---- galaxy tabs
        float tw = (W - 120 * u) / 3f;
        for (int i = 0; i < Defs.GALAXIES; i++) {
            final int gi = i;
            boolean open = galaxyOpen(i);
            rf.set(50 * u + i * (tw + 10 * u), tabY, 50 * u + i * (tw + 10 * u) + tw, tabY + tabH);
            String nm = (L.en ? Defs.GAL_NAME[i] : Defs.GAL_NAME_TH[i]);
            button(c, rf, nm, i == gal ? Defs.GAL_COLOR[i] : 0xFF3A3360, open, () -> {
                playRaid = false;
                playGal = gi;
                int first = gi * Defs.PER_GALAXY, last = Math.min(pf.unlocked - 1, first + Defs.PER_GALAXY - 1);
                playSel = Math.max(first, last);
            });
            if (!open) lockIcon(c, rf.left + 30 * u, rf.centerY(), 12 * u);
            if (!open) btns.add(new Btn(new RectF(rf), () -> showToast(L.t("ผ่านกาแล็กซีก่อนหน้าให้ครบ 15 ด่านก่อน", "Clear all 15 stages of the previous galaxy first"))));
        }
        // ---- stage grid (3 x 5)
        for (int k = 0; k < Defs.PER_GALAXY; k++) {
            final int st = gal * Defs.PER_GALAXY + k;
            boolean open = st < pf.unlocked;
            float x = 50 * u + (k % 5) * (cellW + 16 * u), y = gridY + (k / 5) * (cellH + gapG);
            // staggered pop-in when the screen opens
            float appear = ease(Math.min(1f, Math.max(0f, (transT - k * 0.025f) / 0.3f)));
            float sh = (1f - appear) * 30 * u;
            RectF r = new RectF(x, y + sh, x + cellW, y + cellH + sh);
            int dc = Defs.DIFF_COLOR[Defs.ST_DIFF[st]];
            boolean sel = st == playSel;
            rarityCard(c, r, open ? dc : 0xFF4A4460, open, sel, 22 * u);
            boolean next = open && !pf.clear[st] && st == pf.unlocked - 1;
            if (next) glowCircle(c, r.centerX(), r.centerY(), r.width() * (0.7f + 0.08f * (float) Math.sin(anim * 4)), alpha(dc, 0x44));
            title(c, (gal + 1) + "-" + (k + 1), r.centerX(), r.centerY() + 14 * u, 40 * u, open ? 0xFFFFFFFF : 0xFF6A6488);
            // difficulty pips
            int pips = Defs.ST_DIFF[st] + 1;
            for (int q = 0; q < pips; q++) {
                p.setShader(null);
                p.setColor(open ? dc : 0xFF4A4460);
                c.drawCircle(r.centerX() + (q - (pips - 1) / 2f) * 14 * u, r.bottom - 20 * u, 5 * u, p);
            }
            if (!open) lockIcon(c, r.right - 24 * u, r.top + 26 * u, 14 * u);
            else if (pf.clear[st]) star(c, r.right - 24 * u, r.top + 24 * u, 15 * u);
            if (st == Defs.ST_COUNT - 1) sprite(c, pBmp[Defs.HERO], r.left + 24 * u, r.top + 24 * u, 20 * u, open ? 255 : 120);
            if (open) btns.add(new Btn(r, () -> playSel = st));
            else btns.add(new Btn(r, () -> showToast(L.t("ผ่านด่านก่อนหน้าเพื่อปลดล็อก", "Clear the previous stage to unlock"))));
        }
        // ---- selected stage details
        final int st = playSel;
        RectF d = new RectF(40 * u, dY, W - 40 * u, dY + dH);
        panel(c, d, 0xEE1C1240, 34 * u);
        int diff = Defs.ST_DIFF[st], dc = Defs.DIFF_COLOR[diff];
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Defs.GAL_COLOR[Defs.galaxyOf(st)]);
        rf.set(d.left, d.top + 30 * u, d.left + 14 * u, d.bottom - 30 * u);
        c.drawRoundRect(rf, 7 * u, 7 * u, p);
        String sn = (Defs.galaxyOf(st) + 1) + "-" + (st % Defs.PER_GALAXY + 1) + "  " + (L.en ? Defs.ST_NAME_EN[st] : Defs.ST_NAME[st]);
        title(c, sn, d.left + 50 * u, d.top + 70 * u, 46 * u, 0xFFFFFFFF, Paint.Align.LEFT, d.width() - 360 * u);
        pill(c, d.right - 150 * u, d.top + 55 * u, L.en ? Defs.DIFF_EN[diff] : Defs.DIFF_TH[diff], 32 * u, 0xFF140A2A, dc);
        String l1 = Defs.ST_WAVES[st] + L.t(" เวฟ", " waves") + "   •   " + L.t("พลังศัตรู x", "Enemy power x") + String.format(Locale.US, "%.2f", Defs.ST_HP[st])
                + "   •   " + L.t("ความเร็ว x", "Speed x") + String.format(Locale.US, "%.2f", Defs.ST_SPEED[st]);
        plain(c, l1, d.left + 50 * u, d.top + 128 * u, 28 * u, 0xFFC9C2E6, Paint.Align.LEFT, d.width() - 80 * u);
        String l2 = L.t("รางวัลชนะ: ", "Win reward: ") + (80 + 30 * st) + " Star";
        if (!pf.clear[st]) l2 += L.t("  +  ครั้งแรก ", "  +  first clear ") + (2 + Defs.galaxyOf(st)) + " Millite, " + (1 + Defs.galaxyOf(st)) + " Crystal";
        plain(c, l2, d.left + 50 * u, d.top + 178 * u, 28 * u, 0xFFFFE27A, Paint.Align.LEFT, d.width() - 80 * u);
        String l3;
        if (st == Defs.ST_COUNT - 1) l3 = pf.owned[Defs.HERO] ? L.t("ได้รับดาว Hero แล้ว", "Hero planet already earned")
                : L.t("ด่านสุดท้าย! ชนะเพื่อรับดาว Hero (Exclusive)", "Final stage! Win to earn the Hero planet (Exclusive)");
        else if (pf.clear[st]) l3 = L.t("ผ่านแล้ว", "Cleared");
        else l3 = pf.best[st] > 0 ? L.t("ไปได้ไกลสุด: เวฟ ", "Best: wave ") + pf.best[st] : L.t("ยังไม่เคยผ่าน", "Not cleared yet");
        plain(c, l3, d.left + 50 * u, d.top + 230 * u, 30 * u, st == Defs.ST_COUNT - 1 ? 0xFFFF8A5B : 0xFF9CFF7A, Paint.Align.LEFT, d.width() - 80 * u);
        if (st == Defs.ST_COUNT - 1) sprite(c, pBmp[Defs.HERO], d.right - 90 * u, d.top + 200 * u, 60 * u + 4 * u * (float) Math.sin(anim * 3), 255);
        // ---- start
        rf.set(70 * u, sbY, W - 70 * u, sbY + sbH);
        float pulse = 1f + 0.025f * (float) Math.sin(anim * 4);
        rf.inset(-(pulse - 1) * rf.width() / 2, -(pulse - 1) * rf.height() / 2);
        button(c, rf, L.t("เริ่มด่าน ", "Start ") + (Defs.galaxyOf(st) + 1) + "-" + (st % Defs.PER_GALAXY + 1), 0xFFFF8A2B, true, () -> startStage(playSel));
    }

    boolean playRaid;

    static final int RAID_WINS = 15;

    boolean raidOpen() { return pf.wins >= RAID_WINS; }

    /** Story | Raid switch at the top of the play menu */
    void modeTabs(Canvas c) {
        float tw = (W - 120 * u) / 2f, y = 190 * u, h = 90 * u;
        rf.set(50 * u, y, 50 * u + tw, y + h);
        button(c, rf, L.t("สตอรี่", "Story"), !playRaid ? 0xFFFF8A2B : 0xFF3A3360, true, () -> playRaid = false);
        rf.set(70 * u + tw, y, 70 * u + tw * 2, y + h);
        boolean open = raidOpen();
        button(c, rf, open ? "Raid" : "Raid  " + pf.wins + "/" + RAID_WINS + " Win", playRaid ? 0xFFFF3B6B : 0xFF5A2440, open, () -> playRaid = true);
        if (!open) {
            lockIcon(c, rf.left + 40 * u, rf.centerY(), 13 * u);
            btns.add(new Btn(new RectF(rf), () -> showToast(L.t("ชนะให้ครบ " + RAID_WINS + " ครั้งเพื่อปลดล็อก Raid (ตอนนี้ ", "Win " + RAID_WINS + " games to unlock Raid (now ") + pf.wins + ")")));
        }
    }

    void drawRaidMenu(Canvas c) {
        float sbH = 125 * u, sbY = navTop - 40 * u - sbH;
        float dH = 560 * u, dY = sbY - 24 * u - dH;
        modeTabs(c);
        RectF art = new RectF(40 * u, 300 * u, W - 40 * u, dY - 26 * u);
        raidScene(c, art);
        RectF d = new RectF(40 * u, dY, W - 40 * u, dY + dH);
        panel(c, d, 0xEE2A0A1E, 34 * u);
        title(c, L.en ? Defs.ST_NAME_EN[Defs.RAID] : Defs.ST_NAME[Defs.RAID], d.left + 50 * u, d.top + 75 * u, 50 * u, 0xFFFFFFFF, Paint.Align.LEFT, d.width() - 360 * u);
        pill(c, d.right - 150 * u, d.top + 58 * u, "RAID", 34 * u, 0xFF140A2A, 0xFFFF3B6B);
        plain(c, Defs.RAID_WAVES + L.t(" เวฟ • ศัตรูพิเศษ Voidling (แตกตัว), Wraith (ทะลุสโลว์+ฟื้นเลือด) และบอส Titan",
                " waves • special enemies: Voidling (splits), Wraith (ignores slow, regenerates) and the Titan boss"),
                d.left + 50 * u, d.top + 135 * u, 28 * u, 0xFFFFD8E0, Paint.Align.LEFT, d.width() - 100 * u);
        plain(c, L.t("รางวัล: ทุกเวฟ +1 Oil (เพิ่มทุก 10 เวฟ) • บอส +8 • มินิบอส +3 • ผ่านครบ +100 Oil, 3000 Star",
                "Rewards: every wave +1 Oil (more every 10 waves) • boss +8 • mini boss +3 • full clear +100 Oil, 3000 Star"),
                d.left + 50 * u, d.top + 225 * u, 28 * u, 0xFFFFC890, Paint.Align.LEFT, d.width() - 100 * u);
        text(c, L.t("ไปได้ไกลสุด: เวฟ ", "Best: wave ") + pf.raidBest + " / " + Defs.RAID_WAVES, d.left + 50 * u, d.top + 330 * u, 36 * u, 0xFF9CFF7A, Paint.Align.LEFT, 0);
        // progress bar
        rf2.set(d.left + 50 * u, d.top + 360 * u, d.right - 50 * u, d.top + 384 * u);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x66000000);
        c.drawRoundRect(rf2, 12 * u, 12 * u, p);
        p.setColor(0xFFFF3B6B);
        rf2.right = rf2.left + (rf2.width()) * Math.min(1f, pf.raidBest / (float) Defs.RAID_WAVES);
        c.drawRoundRect(rf2, 12 * u, 12 * u, p);
        for (int k = 0; k < 3; k++) sprite(c, eBmp[Defs.E_VOIDLING + k], d.left + 90 * u + k * 130 * u, d.top + 470 * u, (k == 2 ? 52 : 38) * u, 255);
        rf.set(d.right - 420 * u, d.top + 420 * u, d.right - 40 * u, d.top + 520 * u);
        button(c, rf, L.t("ร้านค้า Raid", "Raid Shop"), 0xFFE0A020, true, () -> popup = P_RSHOP);
        rf.set(70 * u, sbY, W - 70 * u, sbY + sbH);
        float pulse = 1f + 0.025f * (float) Math.sin(anim * 4);
        rf.inset(-(pulse - 1) * rf.width() / 2, -(pulse - 1) * rf.height() / 2);
        button(c, rf, L.t("เริ่ม Raid", "Start Raid"), 0xFFFF3B6B, raidOpen(), () -> startStage(Defs.RAID));
    }

    /** raid art: a red void gate with the Titan looming and voidlings swarming */
    void raidScene(Canvas c, RectF art) {
        panel(c, art, 0xFF12040C, 40 * u);
        c.save();
        c.clipRect(art.left + 6 * u, art.top + 6 * u, art.right - 6 * u, art.bottom - 6 * u);
        float cx = art.centerX(), cy = art.centerY();
        float R = Math.min(art.width(), art.height()) * 0.5f;
        glowCircle(c, cx, cy, R * 1.3f, 0x55FF3B6B);
        for (int ring = 0; ring < 5; ring++) {
            sp.setPathEffect(null);
            sp.setStrokeWidth((8 - ring) * u);
            float rr = R * (0.35f + ring * 0.14f) + (float) Math.sin(anim * 2 + ring) * 6 * u;
            sp.setColor(alpha(ring % 2 == 0 ? 0xFFFF3B6B : 0xFFFFB060, 200 - ring * 35));
            rf2.set(cx - rr, cy - rr * 0.6f, cx + rr, cy + rr * 0.6f);
            c.drawOval(rf2, sp);
        }
        blackHole(c, cx, cy, R * 0.3f);
        sprite(c, eBmp[Defs.E_TITAN], cx, cy - R * 0.05f + (float) Math.sin(anim * 1.5) * 8 * u, R * 0.36f, 255);
        for (int i = 0; i < 10; i++) {
            double a = anim * 0.7 + i * Math.PI * 2 / 10;
            float x = cx + (float) Math.cos(a) * R * 0.85f, y = cy + (float) Math.sin(a) * R * 0.5f;
            sprite(c, eBmp[i % 3 == 0 ? Defs.E_WRAITH : Defs.E_VOIDLING], x, y, (24 + 8 * (i % 3)) * u, 230);
        }
        c.restore();
        pill(c, art.centerX(), art.top + 55 * u, "RAID  •  VOID GATE", 34 * u, 0xFFFFFFFF, 0xDD5A0A20);
    }

    void drawRaidShop(Canvas c) {
        dim(c);
        RectF r = new RectF(30 * u, 110 * u, W - 30 * u, H - 110 * u);
        // shop frame: dark red-gold panel with a glowing marquee header
        panel(c, r, 0xFF2A0A1E, 44 * u);
        RectF head = new RectF(r.left + 20 * u, r.top + 20 * u, r.right - 20 * u, r.top + 250 * u);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(head.left, head.top, head.right, head.bottom, new int[]{0xFF5A0A2A, 0xFFB8203A, 0xFFE0A020}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(head, 32 * u, 32 * u, p);
        p.setShader(null);
        c.save();
        c.clipRect(head.left, head.top, head.right, head.bottom);
        for (int k = 0; k < 6; k++) {
            float sx = head.left + ((anim * 120 * u + k * 260 * u) % (head.width() + 200 * u)) - 100 * u;
            p.setColor(0x22FFFFFF);
            Path band = new Path();
            band.moveTo(sx, head.bottom);
            band.lineTo(sx + 60 * u, head.top);
            band.lineTo(sx + 110 * u, head.top);
            band.lineTo(sx + 50 * u, head.bottom);
            band.close();
            c.drawPath(band, p);
        }
        c.restore();
        for (int k = 0; k < 14; k++) {
            // marquee bulbs
            float bx = head.left + 30 * u + k * (head.width() - 60 * u) / 13f;
            boolean on = ((int) (anim * 4) + k) % 2 == 0;
            p.setColor(on ? 0xFFFFF0A0 : 0x66FFF0A0);
            c.drawCircle(bx, head.top + 22 * u, 7 * u, p);
            c.drawCircle(bx, head.bottom - 22 * u, 7 * u, p);
        }
        capsuleIcon(c, head.left + 120 * u, head.centerY(), 70 * u);
        title(c, L.t("ร้านค้า Raid", "Raid Shop"), head.left + 230 * u, head.centerY() - 5 * u, 64 * u, 0xFFFFFFFF, Paint.Align.LEFT, head.width() - 520 * u);
        plain(c, L.t("แลกด้วย Crystal Oil จากโหมด Raid", "Trade Crystal Oil from Raid"), head.left + 230 * u, head.centerY() + 45 * u, 26 * u, 0xFFFFE6B0, Paint.Align.LEFT, head.width() - 520 * u);
        chip(c, head.right - 140 * u, head.centerY(), 3, pf.oil);
        // product grid
        float gap = 20 * u, cw = (r.width() - 60 * u - gap) / 2f, ch = 430 * u;
        int n = Defs.RAID_SHOP.length;
        RectF view = new RectF(r.left + 10 * u, head.bottom + 20 * u, r.right - 10 * u, r.bottom - 140 * u);
        beginScroll(c, 4100, view, (float) Math.ceil(n / 2f) * (ch + gap), 0.016f);
        for (int i = 0; i < n; i++) {
            final int ii = i;
            int id = Defs.RAID_SHOP[i][0], price = Defs.RAID_SHOP[i][1];
            float x = r.left + 30 * u + (i % 2) * (cw + gap), y = view.top + (i / 2) * (ch + gap);
            RectF q = new RectF(x, y, x + cw, y + ch);
            String name, desc, tag;
            int col, owned, rar;
            if (id == -100) { name = "Raid Capsule"; desc = L.t("สุ่มสกินดวงดาว", "Random planet skin"); col = 0xFFFF3B6B; owned = pf.raidCapsules; rar = Defs.LEGEND; tag = "Capsule"; }
            else if (id <= -10) { int m = -10 - id; name = Defs.MOON_NAME[m]; desc = L.en ? Defs.MOON_DESC_EN[m] : Defs.MOON_DESC[m]; col = Defs.MOON_COLOR[m]; owned = pf.moons[m]; rar = Defs.MYTHIC; tag = "Artifact"; }
            else { name = Items.NAME[id]; desc = L.en ? Items.DESC_EN[id] : Items.DESC[id]; col = Items.COLOR[id]; owned = pf.items[id]; rar = Items.RARITY[id]; tag = Items.isCapsule(id) ? "Capsule" : L.t("วัตถุดิบ", "Material"); }
            rarityCard(c, q, Defs.RARITY_COLOR[rar], true, false, 30 * u);
            pill(c, q.left + 100 * u, q.top + 34 * u, tag, 20 * u, 0xFF140A2A, col);
            plain(c, L.t("มี ", "Own ") + owned, q.right - 24 * u, q.top + 42 * u, 22 * u, 0xFF9CFF7A, Paint.Align.RIGHT, 0);
            float iy = q.top + 145 * u;
            glowCircle(c, q.centerX(), iy, 95 * u, alpha(col, 0x55));
            if (id == -100) capsuleIcon(c, q.centerX(), iy, 62 * u);
            else if (id <= -10) sprite(c, moonBmp(-10 - id), q.centerX(), iy, 58 * u, 255);
            else itemIcon(c, id, q.centerX(), iy, 62 * u);
            text(c, name, q.centerX(), q.top + 268 * u, 32 * u, 0xFFFFFFFF, Paint.Align.CENTER, cw - 30 * u);
            plain(c, desc, q.centerX(), q.top + 305 * u, 21 * u, 0xFFE6E0FF, Paint.Align.CENTER, cw - 30 * u);
            RectF b = new RectF(q.left + 24 * u, q.bottom - 92 * u, q.right - 24 * u, q.bottom - 20 * u);
            boolean can = pf.oil >= price;
            flatOrButton(c, b, null, can ? 0xFFE0A020 : 0xFF4A4562, true);
            tp.setTextSize(34 * u);
            float tw = tp.measureText(String.valueOf(price)) + 40 * u;
            oilIcon(c, b.centerX() - tw / 2 + 10 * u, b.centerY(), 16 * u);
            text(c, String.valueOf(price), b.centerX() - tw / 2 + 36 * u, b.centerY() + 12 * u, 34 * u, can ? 0xFFFFFFFF : 0xFFB8B2CC, Paint.Align.LEFT, 0);
            scrollBtn(b, () -> {
                String err = pf.buyRaidShop(ii);
                if (err != null) { Sfx.play(Sfx.ERROR); showToast(err); return; }
                Sfx.play(Sfx.COIN);
                showToast(L.t("ซื้อแล้ว! อยู่ในกระเป๋า", "Bought! It's in your bag"));
                pushNow();
            });
        }
        endScroll(c);
        rf.set(r.centerX() - 200 * u, r.bottom - 120 * u, r.centerX() + 200 * u, r.bottom - 30 * u);
        button(c, rf, L.t("ปิด", "Close"), 0xFF6B5BD6, true, () -> popup = P_NONE);
    }

    /** a button drawn without registering a tap (the caller registers it, e.g. inside a scroll) */
    void flatOrButton(Canvas c, RectF r, String label, int col, boolean en) {
        int n = btns.size();
        button(c, r, label, col, en, null);
        while (btns.size() > n) btns.remove(btns.size() - 1);
    }

    /** icons for bag items */
    void itemIcon(Canvas c, int id, float x, float y, float r) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        sp.setPathEffect(null);
        switch (id) {
            case Items.STARDUST: {
                glowCircle(c, x, y, r * 1.1f, 0x66B8C8FF);
                for (int k = 0; k < 9; k++) {
                    double a = k * 2.4 + anim * 0.3;
                    float rr = r * (0.15f + 0.08f * (k % 4));
                    float dx = x + (float) Math.cos(a) * r * (0.1f + 0.06f * k), dy = y + (float) Math.sin(a) * r * (0.1f + 0.05f * k);
                    c.save();
                    c.translate(dx, dy);
                    c.scale(rr, rr);
                    p.setColor(k % 3 == 0 ? 0xFFFFFFFF : k % 3 == 1 ? 0xFFB8C8FF : 0xFFFFE6A8);
                    c.drawPath(Art.starPath(0, 0, 1f, 0.45f, 4), p);
                    c.restore();
                }
                break;
            }
            case Items.NEBULA: {
                glowCircle(c, x, y, r * 1.2f, 0x77B86BFF);
                p.setColor(0xFFFFFFFF);
                p.setShader(new RadialGradient(x - r * 0.25f, y - r * 0.25f, r, new int[]{0xFFFFE0FF, 0xFFB86BFF, 0xFF2A0A5A}, null, Shader.TileMode.CLAMP));
                c.drawCircle(x, y, r * 0.75f, p);
                p.setShader(null);
                sp.setStrokeWidth(r * 0.08f);
                for (int arm = 0; arm < 3; arm++) {
                    sp.setColor(alpha(0xFFFFFFFF, 160));
                    rf2.set(x - r * 0.5f, y - r * 0.5f, x + r * 0.5f, y + r * 0.5f);
                    c.drawArc(rf2, anim * 60 + arm * 120, 70, false, sp);
                }
                sp.setStrokeWidth(r * 0.06f);
                sp.setColor(0xFF140A2A);
                c.drawCircle(x, y, r * 0.75f, sp);
                break;
            }
            case Items.CATALYST: {
                glowCircle(c, x, y, r * 1.2f, 0x777CFF8A);
                Path d = new Path();
                d.moveTo(x, y - r);
                d.lineTo(x + r * 0.55f, y - r * 0.15f);
                d.lineTo(x + r * 0.3f, y + r * 0.85f);
                d.lineTo(x - r * 0.3f, y + r * 0.85f);
                d.lineTo(x - r * 0.55f, y - r * 0.15f);
                d.close();
                p.setColor(0xFFFFFFFF);
                p.setShader(new LinearGradient(x - r, y - r, x + r, y + r, 0xFFE8FFE8, 0xFF2FB86A, Shader.TileMode.CLAMP));
                c.drawPath(d, p);
                p.setShader(null);
                sp.setStrokeWidth(r * 0.07f);
                sp.setColor(0xFF0B2A14);
                c.drawPath(d, sp);
                sp.setColor(0x99FFFFFF);
                c.drawLine(x, y - r * 0.85f, x, y + r * 0.75f, sp);
                break;
            }
            case Items.ART_CAPSULE:
                capsulePod(c, x, y, r, 0xFF3A1A06, 0xFFFF9A3B, 0xFFFFF0B0, 1);
                break;
            case Items.SUPPLY:
                capsulePod(c, x, y, r, 0xFF06203A, 0xFF5EC8FF, 0xFFB8FFFF, 2);
                break;
            default:
        }
    }

    /** a capsule pod in two colours (shared by the capsule items) */
    void podIcon(Canvas c, float x, float y, float r, int dark, int light) {
        float bob = (float) Math.sin(anim * 2.4) * r * 0.06f;
        glowCircle(c, x, y + bob, r * 1.4f, alpha(light, 0x55));
        rf2.set(x - r * 0.7f, y - r + bob, x + r * 0.7f, y + r + bob);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(rf2.left, 0, rf2.right, 0, new int[]{dark, light, dark}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(rf2, r * 0.7f, r * 0.7f, p);
        p.setShader(null);
        p.setColor(alpha(0xFFFFFFFF, (int) (150 + 70 * Math.sin(anim * 5))));
        c.drawRect(rf2.left, y - r * 0.05f + bob, rf2.right, y + r * 0.05f + bob, p);
        sp.setPathEffect(null);
        sp.setStrokeWidth(r * 0.06f);
        sp.setColor(0xFF140A2A);
        c.drawRoundRect(rf2, r * 0.7f, r * 0.7f, sp);
    }

    void lockIcon(Canvas c, float x, float y, float r) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFB8B2CC);
        rf2.set(x - r, y - r * 0.2f, x + r, y + r);
        c.drawRoundRect(rf2, r * 0.25f, r * 0.25f, p);
        sp.setPathEffect(null);
        sp.setStrokeWidth(r * 0.3f);
        sp.setColor(0xFFB8B2CC);
        rf2.set(x - r * 0.6f, y - r * 1.0f, x + r * 0.6f, y + r * 0.2f);
        c.drawArc(rf2, 180, 180, false, sp);
    }

    /** play-menu art: the galaxy's black hole with enemies spiralling out of it */
    void blackHoleScene(Canvas c, RectF art, int gal) {
        panel(c, art, 0xFF0A0620, 40 * u);
        c.save();
        Path cl = new Path();
        cl.addRoundRect(new RectF(art.left + 6 * u, art.top + 6 * u, art.right - 6 * u, art.bottom - 6 * u), 34 * u, 34 * u, Path.Direction.CW);
        c.clipPath(cl);
        int gc = Defs.GAL_COLOR[gal];
        float cx = art.centerX(), cy = art.centerY() + art.height() * 0.04f;
        float R = Math.min(art.width(), art.height()) * 0.5f;
        // nebula backdrop
        glowCircle(c, art.left + art.width() * 0.2f, art.top + art.height() * 0.3f, R * 0.9f, alpha(gc, 0x44));
        glowCircle(c, art.right - art.width() * 0.15f, art.bottom - art.height() * 0.2f, R * 0.8f, 0x44FF4FD8);
        glowCircle(c, cx, cy, R * 1.2f, alpha(gc, 0x33));
        // background stars drifting toward the hole
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 70; i++) {
            float f = (float) ((anim * 0.05 + i * 0.6180339) % 1.0);
            double a = i * 2.39996 + f * 1.4;
            float rr = R * 1.6f * (1f - f);
            float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr * 0.62f;
            p.setColor(alpha(i % 3 == 0 ? 0xFFFFE6A8 : 0xFFE6E0FF, (int) (60 + 180 * f)));
            c.drawCircle(x, y, (1.2f + 2.2f * (1 - f)) * u, p);
        }
        // spiral arms of the accretion swirl
        for (int arm = 0; arm < 4; arm++) {
            for (int k = 0; k < 60; k++) {
                float f = k / 60f;
                double a = -anim * 0.9 + arm * Math.PI / 2 + f * 5.2;
                float rr = R * (0.2f + f * 0.75f);
                float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr * 0.42f;
                int col = arm % 2 == 0 ? lighten(gc, 0.3f) : 0xFFFFB060;
                p.setColor(alpha(col, (int) (200 * (1 - f))));
                c.drawCircle(x, y, (6f - f * 4.5f) * u, p);
            }
        }
        blackHole(c, cx, cy, R * 0.32f + R * 0.015f * (float) Math.sin(anim * 2));
        // enemies flying out of the hole toward the viewer
        int[] kinds = {Defs.E_METEOR, Defs.E_SHOOT, Defs.E_MEGA, Defs.E_EXOMOON, Defs.E_ALTMOON, Defs.E_EXOPLANET, Defs.E_ALTPLANET, Defs.E_HELL};
        int count = 12 + gal * 3;
        for (int i = 0; i < count; i++) {
            float f = (float) ((anim * 0.16 + i / (float) count) % 1.0);
            double a = i * 2.39996 + f * 1.1 + gal;
            float rr = R * (0.18f + f * 1.25f);
            float x = cx + (float) Math.cos(a) * rr * 1.2f, y = cy + (float) Math.sin(a) * rr * 0.7f;
            float sz = (16 + 72 * f * f) * u;
            int al = (int) (255 * Math.min(1f, f * 4f) * Math.min(1f, (1f - f) * 6f));
            int kind = kinds[(i + gal * 2) % Math.min(kinds.length, 4 + gal * 2)];
            if (kind == Defs.E_SHOOT) {
                // streak behind shooting stars
                p.setColor(alpha(0xFFFFE6A8, al / 3));
                c.drawCircle(x - (float) Math.cos(a) * sz, y - (float) Math.sin(a) * sz * 0.6f, sz * 0.5f, p);
            }
            sprite(c, eBmp[kind], x, y, sz, al);
        }
        c.restore();
        // galaxy title ribbon
        String gn = (L.en ? Defs.GAL_NAME[gal] : Defs.GAL_NAME_TH[gal]);
        pill(c, art.centerX(), art.top + 55 * u, L.t("กาแล็กซี ", "Galaxy ") + (gal + 1) + "  •  " + gn, 34 * u, 0xFFFFFFFF, alpha(darken(gc, 0.45f), 0xDD));
        int done = 0;
        for (int k = 0; k < Defs.PER_GALAXY; k++) if (pf.clear[gal * Defs.PER_GALAXY + k]) done++;
        pill(c, art.centerX(), art.bottom - 45 * u, L.t("ผ่านแล้ว ", "Cleared ") + done + "/" + Defs.PER_GALAXY, 28 * u, 0xFFFFE27A, 0xCC140A2A);
    }

    // ================================================================== banner
    void drawBanner(Canvas c) {
        final boolean ex = bannerTab == 1;
        if (ex) header(c, L.t("แบนเนอร์", "Banner"), 0, pf.stars, 3, pf.oil);
        else header(c, L.t("แบนเนอร์", "Banner"));
        // banner switch + rates sit at the bottom, above the nav bar
        float tY = navTop - 125 * u;
        float tw3 = (W - 120 * u) / 3f;
        rf.set(50 * u, tY, 50 * u + tw3, tY + 88 * u);
        button(c, rf, "Regular", ex ? 0xFF3A3360 : 0xFFFF8A2B, true, () -> bannerTab = 0);
        rf.set(60 * u + tw3, tY, 60 * u + tw3 * 2, tY + 88 * u);
        button(c, rf, "Exclusive", ex ? Defs.RARITY_COLOR[Defs.EXCLUSIVE] : 0xFF3A3360, true, () -> bannerTab = 1);
        rf.set(70 * u + tw3 * 2, tY, W - 50 * u, tY + 88 * u);
        button(c, rf, L.t("อัตรา", "Rates"), 0xFF4F8BFF, true, () -> popup = P_RATES);
        float bTop = 190 * u, bBot = navTop - 560 * u;
        RectF card = new RectF(40 * u, bTop, W - 40 * u, bBot);
        // card body: deep space + rotating god rays + galaxy
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        rf2.set(card);
        rf2.offset(0, 12 * u);
        p.setColor(0x88000000);
        c.drawRoundRect(rf2, 44 * u, 44 * u, p);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(card.left, card.top, card.right, card.bottom, ex ? new int[]{0xFF2A0610, 0xFF6A0F2A, 0xFFE0A020} : new int[]{0xFF2A0F5E, 0xFF5B1F8A, 0xFFB8461E}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(card, 44 * u, 44 * u, p);
        p.setShader(null);
        c.save();
        Path clip = new Path();
        clip.addCircle(card.centerX(), card.centerY(), Math.max(card.width(), card.height()), Path.Direction.CW);
        float cx = card.centerX(), cy = card.top + card.height() * 0.47f, hh = Math.min(card.width(), card.height() * 0.9f);
        for (int i = 0; i < 14; i++) {
            float a0 = anim * 8 + i * 360f / 14;
            Path ray = new Path();
            double a1 = Math.toRadians(a0 - 5), a2 = Math.toRadians(a0 + 5);
            float L1 = hh * 0.75f;
            ray.moveTo(cx, cy);
            ray.lineTo(cx + (float) Math.cos(a1) * L1, cy + (float) Math.sin(a1) * L1);
            ray.lineTo(cx + (float) Math.cos(a2) * L1, cy + (float) Math.sin(a2) * L1);
            ray.close();
            p.setColor(0xFFFFFFFF);
            p.setShader(new RadialGradient(cx, cy, L1, 0x55FFE6A8, 0x00FFE6A8, Shader.TileMode.CLAMP));
            float top = Math.max(card.top + 10 * u, cy - L1), bot = Math.min(card.bottom - 10 * u, cy + L1);
            c.save();
            Path cl = new Path();
            cl.addCircle(cx, (top + bot) / 2, 1, Path.Direction.CW);
            c.drawPath(ray, p);
            c.restore();
        }
        p.setShader(null);
        c.restore();
        c.save();
        c.translate(cx, cy);
        c.rotate(anim * 6);
        c.scale(1f, 0.6f);
        sprite(c, gBmp, 0, 0, hh * 0.48f, 140);
        c.restore();
        for (int i = 0; i < 16; i++) {
            double a = anim * 0.35 + i * Math.PI / 8;
            float x = cx + (float) Math.cos(a) * hh * 0.4f, y = cy + (float) Math.sin(a) * hh * 0.28f;
            star(c, x, y, (6 + (i % 4) * 4) * u);
        }
        // featured line-up
        float bob = (float) Math.sin(anim * 1.8f) * 10 * u;
        int[] side = ex ? new int[]{Defs.GALACIA_P, Defs.SMART_SAT, Defs.COMMANDER, Defs.CYBER, Defs.SAVIOR_SAT}
                : new int[]{Defs.MERCURY, Defs.VENUS, Defs.URANUS, Defs.NEPTUNE, Defs.PLUTO};
        float[][] pos = {{-0.33f, -0.17f, 0.1f}, {-0.36f, 0.12f, 0.12f}, {0.34f, -0.15f, 0.12f}, {0.36f, 0.14f, 0.11f}, {0f, 0.3f, 0.085f}};
        for (int i = 0; i < side.length; i++)
            sprite(c, pBmp[side[i]], cx + pos[i][0] * hh, cy + pos[i][1] * hh + bob * (i % 2 == 0 ? 1 : -1), pos[i][2] * hh / 0.72f, 255);
        int hero = ex ? Defs.KING : Defs.SUN;
        glowCircle(c, cx, cy + bob, hh * 0.3f, ex ? 0x88FFD23F : 0x88FFB627);
        sprite(c, pBmp[hero], cx, cy + bob, hh * 0.25f, 255);
        if (ex) sprite(c, pBmp[Defs.NIGHTMARE], cx - hh * 0.2f, cy - hh * 0.32f - bob, hh * 0.09f, 255);
        // ribbon title + featured tag
        RectF rib = new RectF(card.left + 60 * u, card.top + 30 * u, card.right - 60 * u, card.top + 130 * u);
        panel(c, rib, 0xEE1A0F3D, 30 * u);
        title(c, ex ? "EXCLUSIVE CAPSULE" : "REGULAR BANNER", cx, rib.centerY() + 20 * u, 56 * u, 0xFFFFD23F, Paint.Align.CENTER, rib.width() - 60 * u);
        RectF tag = new RectF(cx + hh * 0.12f, cy - hh * 0.3f + bob, cx + hh * 0.12f + 290 * u, cy - hh * 0.3f + 70 * u + bob);
        panel(c, tag, 0xEEFF4F8B, 35 * u);
        title(c, ex ? "RAREST: KING" : "FEATURED: SUN", tag.centerX(), tag.centerY() + 12 * u, 32 * u, 0xFFFFFFFF, Paint.Align.CENTER, tag.width() - 20 * u);
        float rbL = card.left + 40 * u, rbR = card.right - 40 * u, rbY = card.bottom - 120 * u;
        if (ex) {
            // per-planet rates of the capsule
            float lx = rbL, ly = rbY - 40 * u;
            for (int k = 0; k < Defs.CAPSULE_POOL.length; k++) {
                int t = Defs.CAPSULE_POOL[k];
                String s1 = Defs.P_NAME[t] + " " + Defs.CAPSULE_RATE[k] + "%";
                tp.setTextSize(24 * u);
                float tw = tp.measureText(s1) + 60 * u;
                if (lx + tw > rbR) { lx = rbL; ly += 56 * u; }
                sprite(c, pBmp[t], lx + 18 * u, ly - 8 * u, 18 * u, 255);
                plain(c, s1, lx + 42 * u, ly, 24 * u, t == Defs.KING ? Defs.RARITY_COLOR[Defs.SPECIALIST] : 0xFFFFE6F0, Paint.Align.LEFT, 0);
                lx += tw;
            }
            sp.setStrokeWidth(5 * u);
            sp.setColor(0xFFFFD23F);
            c.drawRoundRect(card, 44 * u, 44 * u, sp);
            ornaments(c, card, 0xFFFF3B6B, 44 * u);
            float by = bBot + 40 * u, bw = (W - 140 * u) / 2f, bh = 150 * u;
            rf.set(50 * u, by, 50 * u + bw, by + bh);
            button(c, rf, null, 0xFFD0304A, pf.oil >= Defs.CAPSULE_OIL, () -> doCapsule(1));
            title(c, L.t("สุ่ม x1", "Roll x1"), rf.centerX(), rf.top + 70 * u, 52 * u, 0xFFFFFFFF);
            oilIcon(c, rf.centerX() - 50 * u, rf.top + 108 * u, 18 * u);
            text(c, String.valueOf(Defs.CAPSULE_OIL), rf.centerX() - 25 * u, rf.top + 122 * u, 36 * u, 0xFFFFC890, Paint.Align.LEFT, 0);
            rf.set(90 * u + bw, by, 90 * u + bw * 2, by + bh);
            button(c, rf, null, 0xFFE0A020, pf.oil >= Defs.CAPSULE_OIL_10, () -> doCapsule(10));
            title(c, L.t("สุ่ม x10", "Roll x10"), rf.centerX(), rf.top + 70 * u, 52 * u, 0xFFFFFFFF);
            oilIcon(c, rf.centerX() - 50 * u, rf.top + 108 * u, 18 * u);
            text(c, String.valueOf(Defs.CAPSULE_OIL_10), rf.centerX() - 25 * u, rf.top + 122 * u, 36 * u, 0xFFFFC890, Paint.Align.LEFT, 0);
            oilIcon(c, W / 2 - 150 * u, by + bh + 62 * u, 24 * u);
            text(c, "Crystal Oil: " + pf.oil, W / 2 - 115 * u, by + bh + 78 * u, 40 * u, 0xFFFFC890, Paint.Align.LEFT, 0);
            plain(c, L.t("หา Crystal Oil ได้จากโหมด Raid", "Earn Crystal Oil in Raid mode"), W / 2, by + bh + 130 * u, 26 * u, 0xFFC9C2E6, Paint.Align.CENTER, 0);
            return;
        }
        // rate bar
        float acc0 = rbL;
        for (int r = 0; r < Profile.PULL_RATE.length; r++) {
            float w = (rbR - rbL) * Profile.PULL_RATE[r] / 100f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Defs.RARITY_COLOR[r]);
            rf2.set(acc0, rbY, acc0 + Math.max(w, 6 * u), rbY + 26 * u);
            c.drawRect(rf2, p);
            acc0 += w;
        }
        sp.setStrokeWidth(3 * u);
        sp.setColor(0xFF0B0520);
        rf2.set(rbL, rbY, rbR, rbY + 26 * u);
        c.drawRect(rf2, sp);
        float lx = rbL;
        for (int r = 0; r < Profile.PULL_RATE.length; r++) {
            String s1 = Defs.RARITY_NAME[r] + " " + Profile.PULL_RATE[r] + "%";
            tp.setTextSize(24 * u);
            float tw = tp.measureText(s1);
            plain(c, s1, lx, rbY + 66 * u, 24 * u, Defs.RARITY_COLOR[r], Paint.Align.LEFT, 0);
            lx += tw + 22 * u;
        }
        sp.setStrokeWidth(5 * u);
        sp.setColor(0xFFFFD23F);
        c.drawRoundRect(card, 44 * u, 44 * u, sp);
        ornaments(c, card, 0xFFFFD23F, 44 * u);
        // pull buttons with prices
        float by = bBot + 40 * u, bw = (W - 140 * u) / 2f, bh = 150 * u;
        rf.set(50 * u, by, 50 * u + bw, by + bh);
        button(c, rf, null, 0xFF4F8BFF, pf.stars >= Profile.PULL1, () -> doPull(1));
        title(c, L.t("สุ่ม x1", "Roll x1"), rf.centerX(), rf.top + 70 * u, 52 * u, 0xFFFFFFFF);
        star(c, rf.centerX() - 50 * u, rf.top + 108 * u, 18 * u);
        text(c, String.valueOf(Profile.PULL1), rf.centerX() - 25 * u, rf.top + 122 * u, 36 * u, 0xFFFFE27A, Paint.Align.LEFT, 0);
        rf.set(90 * u + bw, by, 90 * u + bw * 2, by + bh);
        button(c, rf, null, 0xFFFF8A2B, pf.stars >= Profile.PULL10, () -> doPull(10));
        title(c, L.t("สุ่ม x10", "Roll x10"), rf.centerX(), rf.top + 70 * u, 52 * u, 0xFFFFFFFF);
        star(c, rf.centerX() - 50 * u, rf.top + 108 * u, 18 * u);
        text(c, String.valueOf(Profile.PULL10), rf.centerX() - 25 * u, rf.top + 122 * u, 36 * u, 0xFFFFE27A, Paint.Align.LEFT, 0);
        RectF save = new RectF(rf.right - 150 * u, rf.top - 30 * u, rf.right + 10 * u, rf.top + 25 * u);
        panel(c, save, 0xFFFF4F8B, 25 * u);
        text(c, "-10%", save.centerX(), save.centerY() + 12 * u, 32 * u, 0xFFFFFFFF);
        star(c, W / 2 - 110 * u, by + bh + 62 * u, 24 * u);
        text(c, "Star: " + pf.stars, W / 2 - 75 * u, by + bh + 78 * u, 42 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
    }

    int bannerTab;
    float evoFlash;

    /** drop rates by tier, with every planet of that tier (like other TD gacha screens) */
    void drawRates(Canvas c) {
        final boolean ex = bannerTab == 1;
        RectF r = new RectF(30 * u, 110 * u, W - 30 * u, H - 110 * u);
        popupFrame(c, r, ex ? L.t("อัตรา: ตู้ Exclusive", "Rates: Exclusive capsule") : L.t("อัตรา: แบนเนอร์ปกติ", "Rates: Regular banner"));
        // tiers, rarest first
        java.util.ArrayList<int[]> tiers = new java.util.ArrayList<>(); // {rarity}
        java.util.ArrayList<float[]> chance = new java.util.ArrayList<>(); // per planet chance, index by tier
        java.util.ArrayList<int[]> members = new java.util.ArrayList<>();
        if (ex) {
            for (int rar : new int[]{Defs.SPECIALIST, Defs.EXCLUSIVE}) {
                java.util.ArrayList<Integer> m = new java.util.ArrayList<>();
                java.util.ArrayList<Float> ch = new java.util.ArrayList<>();
                for (int k = 0; k < Defs.CAPSULE_POOL.length; k++) {
                    int t = Defs.CAPSULE_POOL[k];
                    if (Defs.P_RARITY[t] == rar) { m.add(t); ch.add((float) Defs.CAPSULE_RATE[k]); }
                }
                int[] mm = new int[m.size()];
                float[] cc = new float[m.size()];
                for (int k = 0; k < mm.length; k++) { mm[k] = m.get(k); cc[k] = ch.get(k); }
                tiers.add(new int[]{rar});
                members.add(mm);
                chance.add(cc);
            }
        } else {
            for (int rar = Profile.PULL_RATE.length - 1; rar >= 0; rar--) {
                int[] mm = Profile.typesOfRarity(rar);
                float[] cc = new float[mm.length];
                for (int k = 0; k < mm.length; k++) cc[k] = Profile.PULL_RATE[rar] / mm.length;
                tiers.add(new int[]{rar});
                members.add(mm);
                chance.add(cc);
            }
        }
        float cell = (r.width() - 80 * u) / 5f, headH = 90 * u;
        float contentH = 0;
        for (int[] mm : members) contentH += headH + (float) Math.ceil(mm.length / 5f) * (cell + 46 * u) + 24 * u;
        RectF view = new RectF(r.left + 10 * u, r.top + 150 * u, r.right - 10 * u, r.bottom - 140 * u);
        beginScroll(c, 2000 + (ex ? 1 : 0), view, contentH, 0.016f);
        float y = view.top;
        for (int i = 0; i < tiers.size(); i++) {
            int rar = tiers.get(i)[0];
            int col = Defs.RARITY_COLOR[rar];
            float total = 0;
            for (float f : chance.get(i)) total += f;
            RectF hb = new RectF(r.left + 30 * u, y + 10 * u, r.right - 30 * u, y + headH - 6 * u);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(alpha(col, 0x38));
            c.drawRoundRect(hb, 18 * u, 18 * u, p);
            p.setColor(col);
            rf2.set(hb.left, hb.top, hb.left + 12 * u, hb.bottom);
            c.drawRoundRect(rf2, 6 * u, 6 * u, p);
            text(c, Defs.RARITY_NAME[rar], hb.left + 34 * u, hb.centerY() + 14 * u, 40 * u, col, Paint.Align.LEFT, 0);
            text(c, fmtPct(total), hb.right - 24 * u, hb.centerY() + 14 * u, 40 * u, 0xFFFFFFFF, Paint.Align.RIGHT, 0);
            y += headH;
            int[] mm = members.get(i);
            float[] cc = chance.get(i);
            for (int k = 0; k < mm.length; k++) {
                float x = r.left + 40 * u + (k % 5) * cell, yy = y + (k / 5) * (cell + 46 * u);
                RectF cr = new RectF(x + 6 * u, yy + 6 * u, x + cell - 6 * u, yy + cell - 6 * u);
                rarityCard(c, cr, col, pf.owned[mm[k]], false, 18 * u);
                sprite(c, pBmp[mm[k]], cr.centerX(), cr.centerY() - 8 * u, cell * 0.36f, 255);
                plain(c, fmtPct(cc[k]), cr.centerX(), cr.bottom - 12 * u, 22 * u, 0xFFFFFFFF, Paint.Align.CENTER, 0);
                plain(c, Defs.P_NAME[mm[k]], cr.centerX(), cr.bottom + 32 * u, 22 * u, pf.owned[mm[k]] ? 0xFFE6E0FF : 0xFF8A84A0, Paint.Align.CENTER, cell);
            }
            y += (float) Math.ceil(mm.length / 5f) * (cell + 46 * u) + 24 * u;
        }
        endScroll(c);
        plain(c, L.t("การ์ดซ้ำจะกลายเป็นชิ้นส่วนสำหรับอัปเกรด", "Duplicates become upgrade copies"), r.centerX(), r.bottom - 150 * u + 30 * u, 24 * u, 0xFF9C94C8, Paint.Align.CENTER, 0);
        rf.set(r.centerX() - 200 * u, r.bottom - 110 * u, r.centerX() + 200 * u, r.bottom - 25 * u);
        button(c, rf, L.t("ปิด", "Close"), 0xFF6B5BD6, true, () -> popup = P_NONE);
    }

    static String fmtPct(float f) {
        return f >= 10 || f == (int) f ? String.format(Locale.US, "%.1f%%", f) : String.format(Locale.US, "%.2f%%", f);
    }

    void doCapsule(int n) {
        int[] r = pf.pullCapsule(n);
        if (r == null) { Sfx.play(Sfx.ERROR); showToast(L.t("Crystal Oil ไม่พอ — เล่นโหมด Raid", "Not enough Crystal Oil — play Raid")); return; }
        Sfx.play(Sfx.GACHA);
        for (int t : r) if (t == Defs.KING || t == Defs.NIGHTMARE) { postDelayed(() -> Sfx.play(Sfx.RARE), 600); break; }
        gachaRes = r;
        gachaNew = pf.lastNew;
        gachaT = 0;
        popup = P_GACHA;
        pushNow();
    }

    void doPull(int n) {
        int[] r = pf.pull(n);
        if (r == null) { Sfx.play(Sfx.ERROR); showToast(L.t("Star ไม่พอ", "Not enough Star")); return; }
        Sfx.play(Sfx.GACHA);
        int bestR = 0;
        for (int t : r) bestR = Math.max(bestR, Defs.P_RARITY[t]);
        if (bestR >= Defs.LEGEND) postDelayed(() -> Sfx.play(Sfx.RARE), 600);
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
    void updateAuto(float dt) { }

    void drawEnchant(Canvas c) {
        boolean mat = labTab == 1;
        header(c, mat ? "Lab" : "Factory", 1, pf.millite, 2, pf.crystals);
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
            if (ok) {
                enchFlash = 0.5f;
                int got = labTab == 1 ? Defs.MAT_RARITY[pf.material[enchSel]] : Defs.EN_RARITY[pf.enchant[enchSel]];
                Sfx.play(got >= Defs.LEGEND ? Sfx.RARE : Sfx.ROLL);
            } else {
                Sfx.play(Sfx.ERROR);
                showToast(labTab == 1 ? L.t("Crystal ไม่พอ (ได้จากบอส)", "Not enough Crystal (from bosses)")
                    : L.t("Millite ไม่พอ (ได้จากบอส)", "Not enough Millite (from bosses)"));
            }
        });
        rf.set(70 * u + bw, by, 70 * u + bw * 2, by + bh);
        button(c, rf, L.t("สุ่มหลายครั้ง", "Bulk roll"), 0xFF4F8BFF, true, this::openBulk);
        rf.set(90 * u + bw * 2, by, 90 * u + bw * 3, by + bh);
        button(c, rf, "Index", 0xFF33C6A6, true, () -> popup = P_INDEX);
        // owned planet picker
        float cell = (W - 100 * u) / 6f;
        float pTop = by - 40 * u - cell * 2;
        text(c, L.t("เลือกดวงดาว", "Choose a planet"), 50 * u, pTop - 20 * u, 38 * u, 0xFFFFFFFF, Paint.Align.LEFT, 0);
        int owned = pf.ownedCount();
        if (owned > 12) plain(c, L.t("ปัดขึ้นเพื่อดูเพิ่ม", "Swipe up for more"), W - 50 * u, pTop - 20 * u, 26 * u, 0xFFC9C2E6, Paint.Align.RIGHT, 0);
        RectF view = new RectF(40 * u, pTop, W - 40 * u, pTop + cell * 2);
        beginScroll(c, S_ENCHANT, view, (float) Math.ceil(owned / 6f) * cell, 0.016f);
        int k = 0;
        for (int t = 0; t < Defs.P_COUNT; t++) {
            if (!pf.owned[t]) continue;
            final int tt = t;
            float x = 50 * u + (k % 6) * cell, y = pTop + (k / 6) * cell;
            RectF r = new RectF(x + 6 * u, y + 6 * u, x + cell - 6 * u, y + cell - 6 * u);
            rarityCard(c, r, Defs.RARITY_COLOR[Defs.P_RARITY[t]], true, t == enchSel, 22 * u);
            sprite(c, pbmp(t), r.centerX(), r.centerY(), cell * 0.4f, 255);
            if (!mat && pf.enchant[t] >= 0) badge(c, r.right - 22 * u, r.top + 22 * u, 16 * u, pf.enchant[t], 0);
            if (mat && pf.material[t] >= 0) materialBadge(c, r.right - 22 * u, r.top + 22 * u, 16 * u, pf.material[t]);
            scrollBtn(r, () -> { if (!autoOn) enchSel = tt; });
            k++;
        }
        endScroll(c);
        // machine / lab picture with the selected planet
        float aTop = 310 * u, aBot = pTop - 70 * u;
        float half = Math.min(270 * u, (aBot - aTop - 300 * u) * 0.42f);
        float cy = aTop + (aBot - aTop - 230 * u) / 2f;
        if (mat) labScene(c, aTop, cy, half);
        else factoryScene(c, aTop, cy, half);
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

    /** Factory backdrop: turning gears, a conveyor belt, smokestacks and hazard stripes */
    void factoryScene(Canvas c, float top, float cy, float half) {
        float floorY = cy + half * 1.25f;
        // smokestacks with puffs
        for (int s = 0; s < 2; s++) {
            float sx = s == 0 ? 95 * u : W - 95 * u, sw = 70 * u;
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF2A2350);
            c.drawRect(sx - sw / 2, top + 120 * u, sx + sw / 2, floorY, p);
            p.setColor(0xFF3A3170);
            c.drawRect(sx - sw / 2 - 8 * u, top + 110 * u, sx + sw / 2 + 8 * u, top + 140 * u, p);
            for (int k = 0; k < 4; k++) {
                float f = (float) ((anim * 0.35 + k * 0.25 + s * 0.12) % 1.0);
                float py = top + 100 * u - f * 220 * u, px = sx + (float) Math.sin(f * 5 + s) * 20 * u;
                p.setColor(alpha(0xFFB8B2CC, (int) (120 * (1 - f))));
                c.drawCircle(px, py, (16 + 30 * f) * u, p);
            }
            // stripes
            for (int k = 0; k < 4; k++) {
                p.setColor(k % 2 == 0 ? 0xFFFFD23F : 0xFF140A2A);
                c.drawRect(sx - sw / 2, floorY - (k + 1) * 18 * u, sx + sw / 2, floorY - k * 18 * u, p);
            }
        }
        // gears turning on both sides of the machine
        gear(c, W / 2 - half * 1.55f, cy - half * 0.55f, half * 0.38f, anim * 40, 0xFF6B5BD6);
        gear(c, W / 2 - half * 1.25f, cy + half * 0.1f, half * 0.24f, -anim * 64, 0xFFB86BFF);
        gear(c, W / 2 + half * 1.5f, cy - half * 0.35f, half * 0.32f, -anim * 48, 0xFF6B5BD6);
        gear(c, W / 2 + half * 1.3f, cy + half * 0.3f, half * 0.2f, anim * 78, 0xFFE0A020);
        // conveyor belt under the machine
        RectF belt = new RectF(60 * u, floorY - 10 * u, W - 60 * u, floorY + 40 * u);
        p.setColor(0xFF1C1640);
        c.drawRoundRect(belt, 25 * u, 25 * u, p);
        sp.setPathEffect(null);
        sp.setStrokeWidth(5 * u);
        sp.setColor(0xFF4A3D8F);
        c.drawRoundRect(belt, 25 * u, 25 * u, sp);
        float step = 46 * u, off = (anim * 120 * u) % step;
        sp.setStrokeWidth(4 * u);
        sp.setColor(0xFF6B5BD6);
        for (float x = belt.left + 20 * u + off; x < belt.right - 20 * u; x += step) c.drawLine(x, belt.top + 8 * u, x - 12 * u, belt.bottom - 8 * u, sp);
        for (int k = 0; k < 2; k++) {
            float wx = k == 0 ? belt.left + 25 * u : belt.right - 25 * u;
            gear(c, wx, belt.centerY(), 22 * u, anim * 200, 0xFF8F80D0);
        }
        // crates riding the belt
        for (int k = 0; k < 3; k++) {
            float f = (float) ((anim * 0.12 + k / 3.0) % 1.0);
            float bx = belt.left + 60 * u + f * (belt.width() - 120 * u);
            if (Math.abs(bx - W / 2) < half * 0.9f) continue;
            rf2.set(bx - 22 * u, belt.top - 40 * u, bx + 22 * u, belt.top - 2 * u);
            p.setColor(0xFFB86BFF);
            c.drawRoundRect(rf2, 6 * u, 6 * u, p);
            sp.setStrokeWidth(3 * u);
            sp.setColor(0xFF2A1A5E);
            c.drawRoundRect(rf2, 6 * u, 6 * u, sp);
            milliteIcon(c, bx, rf2.centerY(), 10 * u);
        }
    }

    void gear(Canvas c, float x, float y, float r, float rot, int col) {
        c.save();
        c.rotate(rot, x, y);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(col);
        int teeth = Math.max(8, (int) (r / (6 * u)));
        for (int k = 0; k < teeth; k++) {
            c.save();
            c.rotate(k * 360f / teeth, x, y);
            c.drawRect(x - r * 0.14f, y - r * 1.18f, x + r * 0.14f, y - r * 0.8f, p);
            c.restore();
        }
        c.drawCircle(x, y, r * 0.92f, p);
        p.setColor(darken(col, 0.45f));
        c.drawCircle(x, y, r * 0.38f, p);
        sp.setPathEffect(null);
        sp.setStrokeWidth(Math.max(2f, r * 0.08f));
        sp.setColor(darken(col, 0.6f));
        c.drawCircle(x, y, r * 0.92f, sp);
        for (int k = 0; k < 4; k++) {
            c.save();
            c.rotate(k * 90 + 45, x, y);
            c.drawLine(x, y - r * 0.4f, x, y - r * 0.75f, sp);
            c.restore();
        }
        c.restore();
    }

    /** Lab backdrop: glass shelves with bubbling flasks, test tubes and orbiting atoms */
    void labScene(Canvas c, float top, float cy, float half) {
        float shelfY = cy + half * 1.2f;
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x6633A8D0);
        c.drawRect(50 * u, shelfY, W - 50 * u, shelfY + 12 * u, p);
        // flasks left and right
        flask(c, W / 2 - half * 1.5f, shelfY, half * 0.42f, 0xFF4DFF8A, 0);
        flask(c, W / 2 + half * 1.5f, shelfY, half * 0.36f, 0xFFFF4FD8, 1);
        // test tube rack
        for (int k = 0; k < 4; k++) {
            float tx = (k < 2 ? W / 2 - half * 1.05f : W / 2 + half * 0.9f) + (k % 2) * 34 * u;
            int lc = new int[]{0xFF7DD3FF, 0xFFFFD23F, 0xFFB86BFF, 0xFF4DFF8A}[k];
            float th = half * 0.55f, lvl = 0.55f + 0.1f * (float) Math.sin(anim * 1.5f + k);
            rf2.set(tx - 12 * u, shelfY - th, tx + 12 * u, shelfY);
            p.setColor(alpha(lc, 0xCC));
            c.drawRect(tx - 10 * u, shelfY - th * lvl, tx + 10 * u, shelfY - 6 * u, p);
            sp.setPathEffect(null);
            sp.setStrokeWidth(3 * u);
            sp.setColor(0xAAE6F6FF);
            c.drawRoundRect(rf2, 12 * u, 12 * u, sp);
        }
        // atoms orbiting above
        for (int s = 0; s < 2; s++) {
            float ax = s == 0 ? W / 2 - half * 1.45f : W / 2 + half * 1.45f, ay = top + 120 * u;
            for (int k = 0; k < 3; k++) {
                c.save();
                c.rotate(k * 60 + anim * 10 * (s == 0 ? 1 : -1), ax, ay);
                sp.setStrokeWidth(2.5f * u);
                sp.setColor(0x887DD3FF);
                rf2.set(ax - 60 * u, ay - 20 * u, ax + 60 * u, ay + 20 * u);
                c.drawOval(rf2, sp);
                double a = anim * 3 + k * 2;
                p.setColor(0xFFB8F6FF);
                c.drawCircle(ax + (float) Math.cos(a) * 60 * u, ay + (float) Math.sin(a) * 20 * u, 6 * u, p);
                c.restore();
            }
            glowCircle(c, ax, ay, 22 * u, 0xFFFFD23F);
        }
    }

    void flask(Canvas c, float x, float base, float r, int liquid, int seed) {
        Path f = new Path();
        float neck = r * 0.3f;
        f.moveTo(x - neck, base - r * 2.2f);
        f.lineTo(x - neck, base - r * 1.3f);
        f.lineTo(x - r, base - r * 0.15f);
        f.quadTo(x - r, base, x - r * 0.8f, base);
        f.lineTo(x + r * 0.8f, base);
        f.quadTo(x + r, base, x + r, base - r * 0.15f);
        f.lineTo(x + neck, base - r * 1.3f);
        f.lineTo(x + neck, base - r * 2.2f);
        f.close();
        c.save();
        c.clipPath(f);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(alpha(liquid, 0xCC));
        float lvl = base - r * (0.75f + 0.05f * (float) Math.sin(anim * 2 + seed));
        c.drawRect(x - r * 1.2f, lvl, x + r * 1.2f, base, p);
        // bubbles rising
        for (int k = 0; k < 6; k++) {
            float t = (float) ((anim * 0.6 + k / 6.0 + seed * 0.3) % 1.0);
            float bx = x + (float) Math.sin(k * 2.3 + t * 6) * r * 0.5f, by = base - t * r * 0.8f;
            p.setColor(alpha(0xFFFFFFFF, (int) (180 * (1 - t))));
            c.drawCircle(bx, by, (3 + 4 * t) * u, p);
        }
        c.restore();
        glowCircle(c, x, base - r * 0.4f, r * 1.2f, alpha(liquid, 0x33));
        sp.setPathEffect(null);
        sp.setStrokeWidth(4 * u);
        sp.setColor(0xCCE6F6FF);
        c.drawPath(f, sp);
        // vapour from the mouth
        for (int k = 0; k < 3; k++) {
            float t = (float) ((anim * 0.4 + k / 3.0 + seed * 0.2) % 1.0);
            p.setColor(alpha(liquid, (int) (110 * (1 - t))));
            c.drawCircle(x + (float) Math.sin(t * 7 + k) * 10 * u, base - r * 2.3f - t * r, (6 + 14 * t) * u, p);
        }
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
        if (consoleApp) {
            dim(c);
            flatPanel(c, r, C_CARD, 28 * u);
            p.setShader(null);
            p.setColor(C_ACCENT);
            rf2.set(r.left, r.top, r.right, r.top + 8 * u);
            c.save();
            c.clipRect(r.left, r.top, r.right, r.top + 8 * u);
            c.drawRoundRect(new RectF(r.left, r.top, r.right, r.top + 56 * u), 28 * u, 28 * u, p);
            c.restore();
            text(c, title, r.left + 40 * u, r.top + 90 * u, 44 * u, C_TEXT, Paint.Align.LEFT, r.width() - 80 * u);
            return;
        }
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
        int[] order = mat ? Defs.MAT_ORDER : Defs.EN_ORDER;
        for (int row = 0; row < n; row++) {
            int i = order[row];
            float y = top + row * rh;
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

    int bulkMax() {
        boolean mat = labTab == 1;
        return mat ? pf.crystals / Profile.CRYSTAL_COST : pf.millite / Profile.ROLL_COST;
    }

    void openBulk() {
        if (!bulkInit) {
            // sensible default filter: keep Legend and better
            for (int i = 0; i < Defs.EN_COUNT; i++) keepEn[i] = Defs.EN_RARITY[i] >= Defs.LEGEND;
            for (int i = 0; i < Defs.MAT_COUNT; i++) keepMat[i] = Defs.MAT_RARITY[i] >= Defs.LEGEND;
            bulkInit = true;
        }
        int max = bulkMax();
        if (max <= 0) {
            showToast(labTab == 1 ? L.t("Crystal ไม่พอ (ได้จากบอส)", "Not enough Crystal (from bosses)")
                    : L.t("Millite ไม่พอ (ได้จากบอส)", "Not enough Millite (from bosses)"));
            return;
        }
        bulkCount = max;
        popup = P_AUTO;
    }

    boolean bulkHit(boolean mat, int t) {
        if (mat) return pf.material[t] >= 0 && keepMat[pf.material[t]];
        int e = pf.enchant[t];
        return e >= 0 && keepEn[e] && (Defs.EN_TIERS[e] == 0 || pf.tier[t] >= keepTier[e] + 1);
    }

    int keptCount(boolean mat) {
        int n = 0;
        if (mat) { for (boolean b : keepMat) if (b) n++; }
        else for (boolean b : keepEn) if (b) n++;
        return n;
    }

    /** ask first, then roll up to bulkCount times in one go, stopping at the first result the filter keeps */
    void confirmBulk() {
        final boolean mat = labTab == 1;
        final int n = Math.min(bulkCount, bulkMax());
        if (n <= 0) { showToast(L.t("จำนวนไม่พอ", "Not enough to roll")); return; }
        String cur = mat ? "Crystal" : "Millite";
        int kept = keptCount(mat);
        String msg = L.t("ใช้ ", "Use up to ") + n + " " + cur + L.t(" สุ่ม ", " to roll ") + Defs.P_NAME[enchSel] + L.t(" สูงสุด ", " up to ") + n + L.t(" ครั้ง", " times") + "\n";
        if (kept == 0) msg += L.t("ไม่ได้เลือกตัวที่จะเก็บ จะสุ่มครบทุกครั้งและได้ผลครั้งสุดท้าย", "Nothing is selected to keep: every roll is used and the last result stays");
        else {
            msg += L.t("จะหยุดทันทีเมื่อได้ 1 ใน ", "Stops as soon as you get one of the ") + kept + L.t(" แบบที่เลือกไว้", " selected results");
            if (bulkRevert) msg += "\n" + L.t("ถ้าไม่ได้เลย จะคืนของเดิมให้ (แต่ใช้ " + cur + " ไปแล้ว)", "If none hit, your current one is kept (the " + cur + " is still spent)");
        }
        msg += "\n\n" + L.t("คงเหลือหลังสุ่ม (ถ้าใช้ครบ): ", "Left afterwards (if all used): ") + (bulkMax() - n) + " " + cur;
        Dialogs.confirm(ctx, L.t("ยืนยันการสุ่ม ", "Confirm ") + n + L.t(" ครั้ง?", " rolls?"), msg, L.t("สุ่มเลย", "Roll"), () -> runBulk(mat, n));
    }

    void runBulk(boolean mat, int n) {
        int t = enchSel;
        int oE = pf.enchant[t], oT = pf.tier[t], oM = pf.material[t];
        java.util.Arrays.fill(resByRarity, 0);
        resRolls = 0;
        resHit = false;
        resReverted = false;
        resMat = mat;
        boolean filter = keptCount(mat) > 0;
        for (int i = 0; i < n; i++) {
            boolean ok = mat ? pf.rollMaterial(t, false) : pf.rollEnchant(t, false);
            if (!ok) break;
            resRolls++;
            resByRarity[mat ? Defs.MAT_RARITY[pf.material[t]] : Defs.EN_RARITY[pf.enchant[t]]]++;
            if (filter && bulkHit(mat, t)) { resHit = true; break; }
        }
        resSpent = resRolls * (mat ? Profile.CRYSTAL_COST : Profile.ROLL_COST);
        if (filter && !resHit && bulkRevert) {
            pf.enchant[t] = oE;
            pf.tier[t] = oT;
            pf.material[t] = oM;
            resReverted = true;
        }
        resGot = mat ? pf.material[t] : pf.enchant[t];
        resTier = pf.tier[t];
        pf.save();
        enchFlash = resHit ? 0.9f : 0.4f;
        popup = P_BULKRES;
        Sfx.play(resHit ? Sfx.RARE : Sfx.OK);
        popT = 0;
    }

    void drawAutoPick(Canvas c) {
        final boolean mat = labTab == 1;
        RectF r = new RectF(30 * u, 110 * u, W - 30 * u, H - 110 * u);
        popupFrame(c, r, L.t("สุ่มหลายครั้ง", "Bulk roll"));
        plain(c, L.t("ติ๊กตัวที่อยากได้ — สุ่มจะหยุดทันทีเมื่อได้ตัวที่ติ๊ก", "Tick what you want — rolling stops as soon as you get one"),
                r.centerX(), r.top + 150 * u, 30 * u, 0xFFC9C2E6, Paint.Align.CENTER, r.width() - 60 * u);
        int n = mat ? Defs.MAT_COUNT : Defs.EN_COUNT;
        float listTop = r.top + 185 * u, listBot = r.bottom - 470 * u;
        float rh = Math.min(100 * u, (listBot - listTop) / n);
        int[] order = mat ? Defs.MAT_ORDER : Defs.EN_ORDER;
        for (int ri = 0; ri < n; ri++) {
            final int i = order[ri];
            final int e = i;
            int rar = mat ? Defs.MAT_RARITY[i] : Defs.EN_RARITY[i];
            int rc = rar == Defs.GALAXY ? hsv(anim * 90) : Defs.RARITY_COLOR[rar];
            boolean on = mat ? keepMat[i] : keepEn[i];
            float y = listTop + ri * rh;
            RectF row = new RectF(r.left + 30 * u, y + 4 * u, r.right - 30 * u, y + rh - 4 * u);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(on ? alpha(rc, 0x40) : 0x33000000);
            c.drawRoundRect(row, 18 * u, 18 * u, p);
            if (on) {
                sp.setPathEffect(null);
                sp.setStrokeWidth(3 * u);
                sp.setColor(alpha(rc, 0xCC));
                c.drawRoundRect(row, 18 * u, 18 * u, sp);
            }
            // checkbox
            float bx = row.left + 45 * u, by = row.centerY(), bs = Math.min(26 * u, rh * 0.3f);
            rf2.set(bx - bs, by - bs, bx + bs, by + bs);
            p.setColor(on ? 0xFF33C6A6 : 0xFF241A4E);
            c.drawRoundRect(rf2, 8 * u, 8 * u, p);
            sp.setStrokeWidth(3 * u);
            sp.setColor(on ? 0xFFB8FFE8 : 0xFF6A6488);
            c.drawRoundRect(rf2, 8 * u, 8 * u, sp);
            if (on) {
                sp.setStrokeWidth(5 * u);
                sp.setColor(0xFFFFFFFF);
                c.drawLine(bx - bs * 0.5f, by, bx - bs * 0.1f, by + bs * 0.45f, sp);
                c.drawLine(bx - bs * 0.1f, by + bs * 0.45f, bx + bs * 0.55f, by - bs * 0.45f, sp);
            }
            if (mat) materialBadge(c, row.left + 115 * u, by, rh * 0.3f, i);
            else badge(c, row.left + 115 * u, by, rh * 0.3f, i, 0);
            String nm = mat ? matName(i) : Defs.EN_NAME[i];
            text(c, nm, row.left + 165 * u, by + rh * 0.12f, Math.min(34 * u, rh * 0.36f), on ? 0xFFFFFFFF : 0xFFC9C2E6, Paint.Align.LEFT, row.width() - 470 * u);
            float ch = mat ? Defs.MAT_CHANCE[i] : Defs.EN_CHANCE[i];
            plain(c, String.format(Locale.US, "%s %.2f%%", Defs.RARITY_NAME[rar], ch), row.right - 30 * u, by + rh * 0.1f, Math.min(26 * u, rh * 0.28f), rc, Paint.Align.RIGHT, 0);
            btns.add(new Btn(new RectF(row), () -> {
                if (mat) keepMat[e] = !keepMat[e];
                else keepEn[e] = !keepEn[e];
            }));
            if (!mat && Defs.EN_TIERS[i] > 0) {
                // minimum tier chip, tap to cycle
                String[] tn = {"I+", "II+", "III"};
                RectF chip = new RectF(row.right - 340 * u, row.top + rh * 0.16f, row.right - 230 * u, row.bottom - rh * 0.16f);
                button(c, chip, tn[Math.min(keepTier[i], Defs.EN_TIERS[i] - 1)], 0xFF6B5BD6, true,
                        () -> keepTier[e] = (keepTier[e] + 1) % Defs.EN_TIERS[e]);
            }
        }
        // quick filters
        float qy = listBot + 20 * u, qw = (r.width() - 100 * u) / 3f;
        rf.set(r.left + 30 * u, qy, r.left + 30 * u + qw, qy + 80 * u);
        button(c, rf, "Legend+", 0xFFE0A020, true, () -> setKeep(mat, Defs.LEGEND));
        rf.set(r.left + 50 * u + qw, qy, r.left + 50 * u + qw * 2, qy + 80 * u);
        button(c, rf, "Epic+", 0xFFB86BFF, true, () -> setKeep(mat, Defs.EPIC));
        rf.set(r.left + 70 * u + qw * 2, qy, r.left + 70 * u + qw * 3, qy + 80 * u);
        button(c, rf, L.t("ล้าง", "Clear"), 0xFF4A4562, true, () -> setKeep(mat, 99));
        // count stepper
        int max = bulkMax();
        bulkCount = Math.max(Math.min(1, max), Math.min(bulkCount, max));
        float sy = qy + 110 * u, sw = 110 * u;
        int[] steps = {-10, -1, 1, 10};
        String[] sl = {"-10", "-1", "+1", "+10"};
        for (int i = 0; i < 4; i++) {
            final int d = steps[i];
            float x = i < 2 ? r.left + 30 * u + i * (sw + 12 * u) : r.right - 30 * u - (4 - i) * (sw + 12 * u) + 12 * u;
            rf.set(x, sy, x + sw, sy + 90 * u);
            button(c, rf, sl[i], 0xFF3A3360, true, () -> bulkCount = Math.max(1, Math.min(bulkMax(), bulkCount + d)));
        }
        RectF cnt = new RectF(r.left + 30 * u + 2 * (sw + 12 * u), sy, r.right - 30 * u - 2 * (sw + 12 * u), sy + 90 * u);
        panel(c, cnt, 0xFF140A33, 24 * u);
        title(c, bulkCount + "", cnt.centerX(), cnt.centerY() + 6 * u, 46 * u, 0xFFFFFFFF);
        plain(c, L.t("ครั้ง (มีพอ ", "rolls (max ") + max + ")", cnt.centerX(), cnt.bottom - 8 * u, 22 * u, 0xFFB8B2CC, Paint.Align.CENTER, 0);
        btns.add(new Btn(new RectF(cnt), () -> Dialogs.ask(ctx, L.t("จำนวนครั้ง", "How many rolls"), L.t("มีพอสุ่มได้ ", "You can roll up to ") + bulkMax() + L.t(" ครั้ง", " times"),
                new String[]{L.t("จำนวน", "Rolls")}, new int[]{Dialogs.NUMBER}, new String[]{String.valueOf(bulkCount)}, "OK", v -> {
                    try {
                        int k = Integer.parseInt(v[0].trim());
                        if (k < 1 || k > bulkMax()) return L.t("ใส่ได้ 1 ถึง ", "Use 1 to ") + bulkMax();
                        bulkCount = k;
                        return null;
                    } catch (NumberFormatException ex) {
                        return L.t("ใส่ตัวเลขเท่านั้น", "Numbers only");
                    }
                })));
        // revert toggle
        float ty = sy + 115 * u;
        rf.set(r.left + 30 * u, ty, r.right - 30 * u, ty + 80 * u);
        button(c, rf, L.t("ถ้าไม่ได้ตัวที่ติ๊ก ให้คงของเดิมไว้: ", "Keep my current one if nothing hits: ") + (bulkRevert ? L.t("เปิด", "ON") : L.t("ปิด", "OFF")),
                bulkRevert ? 0xFF2E9A6A : 0xFF4A4562, true, () -> bulkRevert = !bulkRevert);
        // actions
        float ay = r.bottom - 120 * u, aw = (r.width() - 100 * u) / 2f;
        rf.set(r.left + 30 * u, ay, r.left + 30 * u + aw, ay + 95 * u);
        button(c, rf, L.t("ยกเลิก", "Cancel"), 0xFF6B5BD6, true, () -> popup = P_NONE);
        rf.set(r.right - 30 * u - aw, ay, r.right - 30 * u, ay + 95 * u);
        button(c, rf, L.t("สุ่ม ", "Roll ") + bulkCount + L.t(" ครั้ง", "x"), mat ? 0xFF33A8D0 : 0xFFB86BFF, max > 0, this::confirmBulk);
    }

    void setKeep(boolean mat, int minRarity) {
        if (mat) for (int i = 0; i < Defs.MAT_COUNT; i++) keepMat[i] = Defs.MAT_RARITY[i] >= minRarity;
        else {
            for (int i = 0; i < Defs.EN_COUNT; i++) keepEn[i] = Defs.EN_RARITY[i] >= minRarity;
            java.util.Arrays.fill(keepTier, 0);
        }
    }

    void drawBulkResult(Canvas c) {
        boolean mat = resMat;
        RectF r = new RectF(70 * u, H / 2 - 560 * u, W - 70 * u, H / 2 + 560 * u);
        popupFrame(c, r, resHit ? L.t("ได้แล้ว!", "Got it!") : L.t("ผลการสุ่ม", "Roll results"));
        float cy = r.top + 330 * u;
        float pop = Math.min(1f, popT * 3f);
        float k = pop < 1 ? 0.6f + 0.55f * (float) Math.sin(pop * Math.PI * 0.8) : 1f;
        if (resHit) for (int i = 0; i < 10; i++) {
            double a = anim * 0.6 + i * Math.PI / 5;
            float rr = 170 * u + (float) Math.sin(anim * 3 + i) * 15 * u;
            star(c, r.centerX() + (float) Math.cos(a) * rr, cy + (float) Math.sin(a) * rr, 14 * u);
        }
        if (resGot >= 0) {
            int rar = mat ? Defs.MAT_RARITY[resGot] : Defs.EN_RARITY[resGot];
            int rc = rar == Defs.GALAXY ? hsv(anim * 90) : Defs.RARITY_COLOR[rar];
            glowCircle(c, r.centerX(), cy, 190 * u * k, alpha(rc, 0x77));
            if (mat) materialBadge(c, r.centerX(), cy, 95 * u * k, resGot);
            else badge(c, r.centerX(), cy, 95 * u * k, resGot, resTier);
            title(c, mat ? matName(resGot) : enchName(resGot, resTier), r.centerX(), cy + 170 * u, 52 * u, rc, Paint.Align.CENTER, r.width() - 60 * u);
            plain(c, Defs.RARITY_NAME[rar], r.centerX(), cy + 215 * u, 30 * u, 0xFFC9C2E6, Paint.Align.CENTER, 0);
        } else plain(c, "-", r.centerX(), cy, 40 * u, 0xFF8A84A0, Paint.Align.CENTER, 0);
        String cur = mat ? "Crystal" : "Millite";
        String line = L.t("สุ่มไป ", "Rolled ") + resRolls + L.t(" ครั้ง • ใช้ ", " times • spent ") + resSpent + " " + cur;
        plain(c, line, r.centerX(), cy + 285 * u, 32 * u, 0xFFFFFFFF, Paint.Align.CENTER, r.width() - 60 * u);
        if (resReverted) plain(c, L.t("ไม่ได้ตัวที่เลือก — คืนของเดิมให้แล้ว", "No match - your previous one was kept"), r.centerX(), cy + 330 * u, 28 * u, 0xFFFFB8C8, Paint.Align.CENTER, r.width() - 60 * u);
        // what came out, by rarity
        float by = cy + 380 * u, bw = (r.width() - 80 * u) / 6f;
        for (int i = 0; i < 6; i++) {
            float x = r.left + 40 * u + i * bw;
            RectF b = new RectF(x + 5 * u, by, x + bw - 5 * u, by + 120 * u);
            rarityCard(c, b, Defs.RARITY_COLOR[i], resByRarity[i] > 0, false, 18 * u);
            title(c, String.valueOf(resByRarity[i]), b.centerX(), b.top + 62 * u, 40 * u, resByRarity[i] > 0 ? 0xFFFFFFFF : 0xFF6A6488);
            plain(c, Defs.RARITY_NAME[i], b.centerX(), b.bottom - 18 * u, 20 * u, Defs.RARITY_COLOR[i], Paint.Align.CENTER, b.width() - 6 * u);
        }
        rf.set(r.centerX() - 220 * u, r.bottom - 130 * u, r.centerX() + 220 * u, r.bottom - 35 * u);
        button(c, rf, "OK", 0xFF33C6A6, true, () -> popup = P_NONE);
    }

    // ================================================================== lobby popups
    void drawPlayerInfo(Canvas c) {
        RectF r = new RectF(50 * u, H / 2 - 800 * u, W - 50 * u, H / 2 + 800 * u);
        popupFrame(c, r, L.t("ข้อมูลผู้เล่น", "Player info"));
        float acx = r.centerX(), acy = r.top + 240 * u;
        glowCircle(c, acx, acy, 150 * u, 0x55FFD23F);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF2A1A5E);
        c.drawCircle(acx, acy, 92 * u, p);
        sp.setPathEffect(null);
        sp.setStrokeWidth(6 * u);
        sp.setColor(0xFFFFD23F);
        c.drawCircle(acx, acy, 92 * u, sp);
        avatar(c, acx, acy, 78 * u, pf.avatar);
        rf.set(acx + 50 * u, acy + 40 * u, acx + 110 * u, acy + 100 * u);
        button(c, rf, null, 0xFF4F8BFF, true, () -> popup = P_AVATAR);
        sp.setStrokeWidth(5 * u);
        sp.setColor(0xFFFFFFFF);
        c.drawLine(rf.left + 18 * u, rf.bottom - 18 * u, rf.right - 18 * u, rf.top + 18 * u, sp);
        btns.add(new Btn(new RectF(acx - 92 * u, acy - 92 * u, acx + 92 * u, acy + 92 * u), () -> popup = P_AVATAR));
        title(c, user, acx, acy + 160 * u, 56 * u, 0xFFFFFFFF, Paint.Align.CENTER, r.width() - 80 * u);
        int role = cloud.myRole();
        pill(c, acx, acy + 215 * u, role > 0 ? Roles.NAME[role].toUpperCase(Locale.US) : L.t("ผู้เล่น", "PLAYER"), 26 * u, 0xFF140A2A, role > 0 ? Roles.COLOR[role] : 0xFF9C94C8);
        int cleared = 0;
        for (int i = 0; i < Defs.ST_COUNT; i++) if (pf.clear[i]) cleared++;
        int rate = pf.games > 0 ? Math.round(pf.wins * 100f / pf.games) : 0;
        String[] lab = {L.t("ชนะ", "Wins"), L.t("อัตราชนะ", "Win rate"), L.t("เกมที่เล่น", "Games"), L.t("เวลาเล่น", "Play time"),
                L.t("ด่านที่ผ่าน", "Stages"), L.t("บอสที่ล้ม", "Bosses"), "Raid", L.t("ดวงดาว", "Planets"),
                "Star", "Millite", "Crystal", "Crystal Oil", L.t("สกิน", "Skins"), L.t("วิวัฒน์", "Evolved")};
        int evo = 0;
        for (boolean b : pf.evolved) if (b) evo++;
        String[] val = {String.valueOf(pf.wins), rate + "%", String.valueOf(pf.games), fmtTime(pf.playSec),
                cleared + "/" + Defs.ST_COUNT, String.valueOf(pf.bossKills), L.t("เวฟ ", "Wave ") + pf.raidBest, pf.ownedCount() + "/" + Defs.P_COUNT,
                String.valueOf(pf.stars), String.valueOf(pf.millite), String.valueOf(pf.crystals), String.valueOf(pf.oil),
                String.valueOf(pf.skins.size()), String.valueOf(evo)};
        int[] col = {0xFFFFD23F, 0xFF9CFF7A, 0xFF4FA3FF, 0xFF5EF2FF, 0xFFFF8A2B, 0xFFFF5B7A, 0xFFFF3B6B, 0xFFB86BFF,
                0xFFFFD23F, 0xFFC58CFF, 0xFF5EF2FF, 0xFFFF9A3B, 0xFFFF4FD8, 0xFF7CFF8A};
        float top = acy + 270 * u, gap = 18 * u, tw = (r.width() - 80 * u - gap) / 2f, th = 104 * u;
        for (int i = 0; i < lab.length; i++) {
            float x = r.left + 40 * u + (i % 2) * (tw + gap), y = top + (i / 2) * (th + 14 * u);
            RectF t = new RectF(x, y, x + tw, y + th);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF1C1240);
            c.drawRoundRect(t, 22 * u, 22 * u, p);
            sp.setPathEffect(null);
            sp.setStrokeWidth(3 * u);
            sp.setColor(alpha(col[i], 0x66));
            c.drawRoundRect(t, 22 * u, 22 * u, sp);
            float ix = t.left + 54 * u, iy = t.centerY();
            glowCircle(c, ix, iy, 50 * u, alpha(col[i], 0x44));
            statIcon(c, i, ix, iy, 26 * u, col[i]);
            plain(c, lab[i], t.left + 100 * u, t.top + 40 * u, 24 * u, 0xFFB8B2CC, Paint.Align.LEFT, tw - 110 * u);
            text(c, val[i], t.left + 100 * u, t.top + 86 * u, 36 * u, 0xFFFFFFFF, Paint.Align.LEFT, tw - 110 * u);
        }
        float bw = (r.width() - 120 * u) / 2f;
        rf.set(r.left + 40 * u, r.bottom - 245 * u, r.left + 40 * u + bw, r.bottom - 155 * u);
        button(c, rf, L.t("เปลี่ยนไอคอน", "Change icon"), 0xFF4F8BFF, true, () -> popup = P_AVATAR);
        rf.set(r.right - 40 * u - bw, r.bottom - 245 * u, r.right - 40 * u, r.bottom - 155 * u);
        button(c, rf, L.t("ออกจากระบบ", "Log out"), 0xFFFF6B5B, true, () -> Dialogs.confirm(ctx, L.t("ออกจากระบบ", "Log out"),
                L.t("ต้องการออกจากระบบใช่ไหม?", "Log out of this account?"), L.t("ออกจากระบบ", "Log out"), this::logout));
        rf.set(r.centerX() - 200 * u, r.bottom - 125 * u, r.centerX() + 200 * u, r.bottom - 35 * u);
        button(c, rf, L.t("ปิด", "Close"), 0xFF6B5BD6, true, () -> popup = P_NONE);
    }

    /** icons for the profile stat tiles */
    void statIcon(Canvas c, int i, float x, float y, float r, int col) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        sp.setPathEffect(null);
        switch (i) {
            case 0: trophy(c, x, y, r * 1.1f); break;
            case 1: { // win rate: pie
                float f = pf.games > 0 ? pf.wins / (float) pf.games : 0;
                p.setColor(0xFF3A3360);
                c.drawCircle(x, y, r, p);
                p.setColor(col);
                rf2.set(x - r, y - r, x + r, y + r);
                c.drawArc(rf2, -90, 360 * f, true, p);
                sp.setStrokeWidth(r * 0.12f);
                sp.setColor(0xFF0B0520);
                c.drawCircle(x, y, r, sp);
                break;
            }
            case 2: { // games: controller
                rf2.set(x - r, y - r * 0.55f, x + r, y + r * 0.6f);
                p.setColor(col);
                c.drawRoundRect(rf2, r * 0.5f, r * 0.5f, p);
                p.setColor(0xFF0B0520);
                c.drawRect(x - r * 0.65f, y - r * 0.08f, x - r * 0.25f, y + r * 0.08f, p);
                c.drawRect(x - r * 0.53f, y - r * 0.2f, x - r * 0.37f, y + r * 0.2f, p);
                c.drawCircle(x + r * 0.4f, y - r * 0.1f, r * 0.12f, p);
                c.drawCircle(x + r * 0.62f, y + r * 0.12f, r * 0.12f, p);
                break;
            }
            case 3: { // play time: clock
                p.setColor(col);
                c.drawCircle(x, y, r, p);
                p.setColor(0xFF140A2A);
                c.drawCircle(x, y, r * 0.78f, p);
                sp.setStrokeWidth(r * 0.15f);
                sp.setColor(col);
                sp.setStrokeCap(Paint.Cap.ROUND);
                c.drawLine(x, y, x, y - r * 0.55f, sp);
                c.drawLine(x, y, x + r * 0.4f, y + r * 0.15f, sp);
                break;
            }
            case 4: { // stages: flag
                sp.setStrokeWidth(r * 0.14f);
                sp.setColor(0xFFE6E0FF);
                c.drawLine(x - r * 0.6f, y + r, x - r * 0.6f, y - r, sp);
                Path f = new Path();
                f.moveTo(x - r * 0.55f, y - r);
                f.lineTo(x + r * 0.9f, y - r * 0.6f);
                f.lineTo(x - r * 0.55f, y - r * 0.15f);
                f.close();
                p.setColor(col);
                c.drawPath(f, p);
                break;
            }
            case 5: sprite(c, eBmp[Defs.E_MEGA], x, y, r * 1.2f, 255); break;
            case 6: sprite(c, eBmp[Defs.E_TITAN], x, y, r * 1.2f, 255); break;
            case 7: sprite(c, pBmp[Defs.SATURN], x, y, r * 1.25f, 255); break;
            case 8: star(c, x, y, r * 0.9f); break;
            case 9: milliteIcon(c, x, y, r * 0.9f); break;
            case 10: crystalIcon(c, x, y, r * 0.95f); break;
            case 11: oilIcon(c, x, y, r * 0.85f); break;
            case 12: sprite(c, skinBmp(Defs.SUN, Skins.KING), x, y, r * 1.15f, 255); break;
            default: sprite(c, evoBmp(Defs.EARTH), x, y, r * 1.2f, 255);
        }
    }

    void drawAvatarPick(Canvas c) {
        RectF r = new RectF(60 * u, H / 2 - 620 * u, W - 60 * u, H / 2 + 620 * u);
        popupFrame(c, r, L.t("เลือกไอคอนโปรไฟล์", "Choose a profile icon"));
        // gallery photo
        float gy = r.top + 150 * u;
        rf.set(r.left + 40 * u, gy, r.right - 40 * u, gy + 120 * u);
        button(c, rf, null, 0xFF2E9A6A, photoPicker != null, () -> { if (photoPicker != null) photoPicker.run(); });
        boolean hasPhoto = photoBmp(pf.photo) != null;
        if (hasPhoto) avatarOf(c, rf.left + 75 * u, rf.centerY(), 44 * u, -1, pf.photo);
        else {
            // little picture icon
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFE6FFF4);
            rf2.set(rf.left + 35 * u, rf.centerY() - 32 * u, rf.left + 115 * u, rf.centerY() + 32 * u);
            c.drawRoundRect(rf2, 8 * u, 8 * u, p);
            p.setColor(0xFF2E9A6A);
            Path m = new Path();
            m.moveTo(rf2.left + 8 * u, rf2.bottom - 8 * u);
            m.lineTo(rf2.left + 30 * u, rf2.top + 22 * u);
            m.lineTo(rf2.left + 48 * u, rf2.bottom - 20 * u);
            m.lineTo(rf2.left + 60 * u, rf2.top + 34 * u);
            m.lineTo(rf2.right - 8 * u, rf2.bottom - 8 * u);
            m.close();
            c.drawPath(m, p);
            c.drawCircle(rf2.right - 18 * u, rf2.top + 16 * u, 7 * u, p);
        }
        title(c, L.t("เลือกรูปจากแกลเลอรี", "Pick from gallery"), rf.centerX() + 40 * u, rf.centerY() + 16 * u, 44 * u, 0xFFFFFFFF, Paint.Align.CENTER, rf.width() - 200 * u);
        if (hasPhoto) {
            final RectF pr = new RectF(rf);
            if (pf.avatar == -1) {
                sp.setStrokeWidth(8 * u);
                sp.setColor(alpha(0xFFFFD23F, (int) (150 + 80 * Math.sin(anim * 5))));
                c.drawRoundRect(pr, 34 * u, 34 * u, sp);
            }
            rf.set(r.left + 40 * u, gy + 135 * u, r.right - 40 * u, gy + 215 * u);
            button(c, rf, pf.avatar == -1 ? L.t("กำลังใช้รูปนี้", "Using this photo") : L.t("ใช้รูปเดิม", "Use my photo"), 0xFF4F8BFF, true,
                    () -> { pf.avatar = -1; pf.save(); pushNow(); popup = P_PLAYER; });
        }
        float cell = (r.width() - 80 * u) / 4f, top = gy + (hasPhoto ? 290 : 200) * u;
        plain(c, L.t("หรือเลือกไอคอน", "or choose an icon"), r.centerX(), top - 20 * u, 30 * u, 0xFFC9C2E6, Paint.Align.CENTER, 0);
        for (int i = 0; i < AVATARS; i++) {
            final int id = i;
            float x = r.left + 40 * u + (i % 4) * cell, y = top + (i / 4) * cell;
            RectF cr = new RectF(x + 10 * u, y + 10 * u, x + cell - 10 * u, y + cell - 10 * u);
            rarityCard(c, cr, 0xFF8A6BFF, true, pf.avatar == i, 30 * u);
            avatar(c, cr.centerX(), cr.centerY(), cell * 0.32f, i);
            btns.add(new Btn(cr, () -> { pf.avatar = id; pf.save(); pushNow(); popup = P_PLAYER; }));
        }
        rf.set(r.centerX() - 200 * u, r.bottom - 120 * u, r.centerX() + 200 * u, r.bottom - 35 * u);
        button(c, rf, L.t("ปิด", "Close"), 0xFF6B5BD6, true, () -> popup = P_PLAYER);
    }

    void drawSettings(Canvas c) {
        RectF r = new RectF(50 * u, H / 2 - 820 * u, W - 50 * u, H / 2 + 820 * u);
        popupFrame(c, r, L.t("ตั้งค่า", "Settings"));
        float x0 = r.left + 40 * u, x1 = r.right - 40 * u, y = r.top + 160 * u;
        // ---- sound
        y = settingsSection(c, x0, x1, y, L.t("เสียง", "Sound"), 0);
        settingsToggle(c, x0, x1, y, L.t("เสียงทั้งหมด", "All sounds"), Sfx.enabled, () -> { Sfx.enabled = !Sfx.enabled; acc.setSfx(Sfx.enabled); Sfx.play(Sfx.OK); });
        y += 100 * u;
        // volume slider: tap anywhere on the bar, or use - / +
        plain(c, L.t("ระดับเสียง", "Volume"), x0 + 20 * u, y + 52 * u, 32 * u, 0xFFE6E0FF, Paint.Align.LEFT, 0);
        int vol = Math.round(Sfx.master * 100);
        float bx0 = x0 + 290 * u, bx1 = x1 - 210 * u, by = y + 40 * u;
        rf.set(bx0 - 120 * u, y + 5 * u, bx0 - 40 * u, y + 80 * u);
        button(c, rf, "-", 0xFF4A3D8F, Sfx.enabled && vol > 0, () -> setVol(Math.round(Sfx.master * 100) - 10));
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF140A2A);
        rf2.set(bx0, by - 10 * u, bx1, by + 10 * u);
        c.drawRoundRect(rf2, 10 * u, 10 * u, p);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(bx0, 0, bx1, 0, 0xFF4FE0FF, 0xFFB86BFF, Shader.TileMode.CLAMP));
        rf2.set(bx0, by - 10 * u, bx0 + (bx1 - bx0) * vol / 100f, by + 10 * u);
        c.drawRoundRect(rf2, 10 * u, 10 * u, p);
        p.setShader(null);
        float kx = bx0 + (bx1 - bx0) * vol / 100f;
        p.setColor(Sfx.enabled ? 0xFFFFFFFF : 0xFF8A84A0);
        c.drawCircle(kx, by, 22 * u, p);
        sp.setPathEffect(null);
        sp.setStrokeWidth(4 * u);
        sp.setColor(0xFFB86BFF);
        c.drawCircle(kx, by, 22 * u, sp);
        final float fb0 = bx0, fb1 = bx1;
        btns.add(new Btn(new RectF(bx0 - 20 * u, y, bx1 + 20 * u, y + 85 * u), () -> setVol(Math.round((tapX - fb0) / (fb1 - fb0) * 100))));
        rf.set(bx1 + 40 * u, y + 5 * u, bx1 + 120 * u, y + 80 * u);
        button(c, rf, "+", 0xFF4A3D8F, Sfx.enabled && vol < 100, () -> setVol(Math.round(Sfx.master * 100) + 10));
        plain(c, vol + "%", x1 - 20 * u, y + 52 * u, 30 * u, 0xFFB8F6FF, Paint.Align.RIGHT, 0);
        y += 100 * u;
        settingsToggle(c, x0, x1, y, L.t("เสียงในการต่อสู้", "Battle sounds"), Sfx.battle, () -> { Sfx.battle = !Sfx.battle; acc.setSfxBattle(Sfx.battle); });
        y += 100 * u;
        settingsToggle(c, x0, x1, y, L.t("เสียงเมนู / ปุ่ม", "Menu & button sounds"), Sfx.ui, () -> { Sfx.ui = !Sfx.ui; acc.setSfxUi(Sfx.ui); Sfx.play(Sfx.OK); });
        y += 120 * u;
        // ---- language
        y = settingsSection(c, x0, x1, y, L.t("ภาษา", "Language"), 1);
        float bw = (x1 - x0 - 30 * u) / 2f;
        rf.set(x0, y, x0 + bw, y + 90 * u);
        button(c, rf, "ไทย", !L.en ? 0xFFFF8A2B : 0xFF3A3360, true, () -> { L.en = false; acc.setLang(false); });
        rf.set(x1 - bw, y, x1, y + 90 * u);
        button(c, rf, "English", L.en ? 0xFFFF8A2B : 0xFF3A3360, true, () -> { L.en = true; acc.setLang(true); });
        y += 130 * u;
        // ---- account
        y = settingsSection(c, x0, x1, y, L.t("บัญชี", "Account"), 2);
        RectF acct = new RectF(x0, y, x1, y + 120 * u);
        panel(c, acct, 0xFF1C1240, 22 * u);
        avatar(c, acct.left + 70 * u, acct.centerY(), 42 * u, pf.avatar);
        text(c, user, acct.left + 140 * u, acct.top + 58 * u, 38 * u, 0xFFFFFFFF, Paint.Align.LEFT, acct.width() - 360 * u);
        int role = cloud.myRole();
        plain(c, role > 0 ? Roles.NAME[role] : L.t("ผู้เล่น", "Player"), acct.left + 140 * u, acct.top + 100 * u, 26 * u, role > 0 ? Roles.COLOR[role] : 0xFF9C94C8, Paint.Align.LEFT, 0);
        netPill(c, acct.right - 175 * u, acct.centerY());
        y += 150 * u;
        rf.set(x0, y, x0 + bw, y + 95 * u);
        button(c, rf, L.t("เปลี่ยนรหัสผ่าน", "Change password"), 0xFF4F8BFF, true, () -> { popup = P_NONE; askChangePassword(); });
        rf.set(x1 - bw, y, x1, y + 95 * u);
        button(c, rf, L.t("ออกจากระบบ", "Log out"), 0xFFFF6B5B, true, () -> Dialogs.confirm(ctx, L.t("ออกจากระบบ", "Log out"),
                L.t("ต้องการออกจากระบบใช่ไหม? ข้อมูลเซฟอยู่บนเซิร์ฟเวอร์แล้ว", "Log out of this account? Your progress is saved online."), L.t("ออกจากระบบ", "Log out"), this::logout));
        y += 125 * u;
        rf.set(x0, y, x0 + bw, y + 95 * u);
        button(c, rf, L.t("โปรไฟล์ / ไอคอน", "Profile / icon"), 0xFF6B5BD6, true, () -> popup = P_PLAYER);
        rf.set(x1 - bw, y, x1, y + 95 * u);
        button(c, rf, L.t("โค้ดของขวัญ", "Gift code"), 0xFFE0A020, true, () -> { popup = P_NONE; askRedeem(); });
        plain(c, "Stella TD " + VERSION + "  •  " + L.t("เซฟออนไลน์อัตโนมัติ", "Auto cloud save"), r.centerX(), r.bottom - 150 * u, 26 * u, 0x99FFFFFF, Paint.Align.CENTER, r.width() - 60 * u);
        rf.set(r.centerX() - 200 * u, r.bottom - 120 * u, r.centerX() + 200 * u, r.bottom - 35 * u);
        button(c, rf, L.t("ปิด", "Close"), 0xFF6B5BD6, true, () -> popup = P_NONE);
    }

    void setVol(int v) {
        v = Math.max(0, Math.min(100, Math.round(v / 5f) * 5));
        Sfx.master = v / 100f;
        acc.setVolume(v);
        Sfx.play(Sfx.CLICK);
    }

    /** a section title with a small icon; returns the y below it */
    float settingsSection(Canvas c, float x0, float x1, float y, String title, int icon) {
        float cy = y + 30 * u;
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        if (icon == 0) speakerIcon(c, x0 + 26 * u, cy, 22 * u, 0xFFFFD23F);
        else if (icon == 1) globeIcon(c, x0 + 26 * u, cy, 22 * u, 0xFFFFD23F);
        else personIcon(c, x0 + 26 * u, cy, 22 * u, 0xFFFFD23F);
        text(c, title, x0 + 64 * u, cy + 14 * u, 38 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
        sp.setPathEffect(null);
        sp.setStrokeWidth(2 * u);
        sp.setColor(0x44FFD23F);
        tp.setTextSize(38 * u);
        c.drawLine(x0 + 84 * u + tp.measureText(title), cy, x1, cy, sp);
        return y + 75 * u;
    }

    /** a row with a label and an on/off switch */
    void settingsToggle(Canvas c, float x0, float x1, float y, String label, boolean on, Runnable a) {
        RectF row = new RectF(x0, y, x1, y + 85 * u);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x33000000);
        c.drawRoundRect(row, 20 * u, 20 * u, p);
        plain(c, label, x0 + 20 * u, row.centerY() + 11 * u, 32 * u, 0xFFE6E0FF, Paint.Align.LEFT, row.width() - 200 * u);
        float sw = 120 * u, sh = 58 * u, sx = x1 - 20 * u - sw, sy = row.centerY() - sh / 2;
        rf2.set(sx, sy, sx + sw, sy + sh);
        p.setColor(on ? 0xFF2E9A6A : 0xFF3A3360);
        c.drawRoundRect(rf2, sh / 2, sh / 2, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(on ? sx + sw - sh / 2 : sx + sh / 2, sy + sh / 2, sh / 2 - 6 * u, p);
        plain(c, on ? L.t("เปิด", "ON") : L.t("ปิด", "OFF"), sx - 16 * u, row.centerY() + 10 * u, 26 * u, on ? 0xFF9CFF7A : 0xFF8A84A0, Paint.Align.RIGHT, 0);
        btns.add(new Btn(row, a));
    }

    void speakerIcon(Canvas c, float x, float y, float r, int col) {
        Path s = new Path();
        s.moveTo(x - r * 0.9f, y - r * 0.35f);
        s.lineTo(x - r * 0.4f, y - r * 0.35f);
        s.lineTo(x + r * 0.15f, y - r * 0.85f);
        s.lineTo(x + r * 0.15f, y + r * 0.85f);
        s.lineTo(x - r * 0.4f, y + r * 0.35f);
        s.lineTo(x - r * 0.9f, y + r * 0.35f);
        s.close();
        p.setColor(col);
        c.drawPath(s, p);
        sp.setPathEffect(null);
        sp.setStrokeWidth(r * 0.16f);
        sp.setColor(col);
        rf2.set(x - r * 0.1f, y - r * 0.55f, x + r * 0.75f, y + r * 0.55f);
        c.drawArc(rf2, -50, 100, false, sp);
        rf2.set(x - r * 0.35f, y - r * 0.95f, x + r * 1.05f, y + r * 0.95f);
        c.drawArc(rf2, -50, 100, false, sp);
    }

    void globeIcon(Canvas c, float x, float y, float r, int col) {
        sp.setPathEffect(null);
        sp.setStrokeWidth(r * 0.14f);
        sp.setColor(col);
        c.drawCircle(x, y, r * 0.9f, sp);
        rf2.set(x - r * 0.4f, y - r * 0.9f, x + r * 0.4f, y + r * 0.9f);
        c.drawOval(rf2, sp);
        c.drawLine(x - r * 0.9f, y, x + r * 0.9f, y, sp);
    }

    void personIcon(Canvas c, float x, float y, float r, int col) {
        p.setColor(col);
        c.drawCircle(x, y - r * 0.4f, r * 0.42f, p);
        rf2.set(x - r * 0.8f, y + r * 0.1f, x + r * 0.8f, y + r * 1.5f);
        c.drawArc(rf2, 180, 180, true, p);
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
            case Defs.EN_FROSTBITE: // snowflake
                sp.setStrokeWidth(s * 0.16f);
                sp.setColor(0xFFFFFFFF);
                for (int k = 0; k < 3; k++) {
                    double a = k * Math.PI / 3;
                    float dx = (float) Math.cos(a) * s, dy = (float) Math.sin(a) * s;
                    c.drawLine(x - dx, y - dy, x + dx, y + dy, sp);
                }
                break;
            case Defs.EN_OVERCHARGE: { // battery with bolt
                p.setColor(0xFFFFFFFF);
                rf2.set(x - s * 0.55f, y - s * 0.8f, x + s * 0.55f, y + s * 0.9f);
                sp.setStrokeWidth(s * 0.14f);
                sp.setColor(0xFFFFFFFF);
                c.drawRoundRect(rf2, s * 0.15f, s * 0.15f, sp);
                c.drawRect(x - s * 0.22f, y - s, x + s * 0.22f, y - s * 0.8f, p);
                Path bo = new Path();
                bo.moveTo(x + s * 0.1f, y - s * 0.55f);
                bo.lineTo(x - s * 0.3f, y + s * 0.1f);
                bo.lineTo(x, y + s * 0.1f);
                bo.lineTo(x - s * 0.1f, y + s * 0.65f);
                bo.lineTo(x + s * 0.3f, y - s * 0.05f);
                bo.lineTo(x, y - s * 0.05f);
                bo.close();
                c.drawPath(bo, p);
                break;
            }
            case Defs.EN_NOVA: // starburst
                p.setColor(0xFFFFFFFF);
                c.save();
                c.translate(x, y);
                c.rotate(anim * 40);
                c.scale(s, s);
                c.drawPath(Art.starPath(0, 0, 1.05f, 0.4f, 8), p);
                c.restore();
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
            case Defs.MAT_MYTHRIL:
                glowCircle(c, x, y, r * 1.45f, 0x668FE8D8);
                sp.setStrokeWidth(r * 0.06f);
                sp.setColor(alpha(0xFFB8FFF0, (int) (150 + 80 * Math.sin(anim * 3))));
                c.drawCircle(x, y, r * 1.12f, sp);
                break;
            case Defs.MAT_OBSIDIAN:
                glowCircle(c, x, y, r * 1.5f, 0x88502070);
                p.setStyle(Paint.Style.FILL);
                p.setShader(null);
                p.setColor(0xFF2A1238);
                for (int i = 0; i < 5; i++) {
                    double a = -anim * 0.8 + i * Math.PI * 2 / 5;
                    float dx = x + (float) Math.cos(a) * r * 1.25f, dy = y + (float) Math.sin(a) * r * 1.25f;
                    Path sh = new Path();
                    sh.moveTo(dx, dy - r * 0.16f);
                    sh.lineTo(dx + r * 0.09f, dy);
                    sh.lineTo(dx, dy + r * 0.16f);
                    sh.lineTo(dx - r * 0.09f, dy);
                    sh.close();
                    c.drawPath(sh, p);
                }
                break;
            case Defs.MAT_STELLARIUM:
                glowCircle(c, x, y, r * 1.6f, alpha(hsv(anim * 60), 0x77));
                p.setStyle(Paint.Style.FILL);
                p.setShader(null);
                for (int i = 0; i < 6; i++) {
                    double a = anim * 1.6 + i * Math.PI / 3;
                    p.setColor(hsv(anim * 60 + i * 60));
                    c.drawCircle(x + (float) Math.cos(a) * r * 1.3f, y + (float) Math.sin(a) * r * 1.3f, r * 0.07f, p);
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
    /** animated cover: the whole solar system orbiting inside a spiral galaxy */
    void solarCover(Canvas c, float cx, float cy) {
        // painted nebula behind everything, drifting and breathing slowly
        if (nebulaBmp != null) {
            float k = 1.08f + 0.03f * (float) Math.sin(anim * 0.25f);
            float ox = (float) Math.sin(anim * 0.07f) * 30 * u, oy = (float) Math.cos(anim * 0.05f) * 40 * u;
            Paint np = new Paint(Paint.FILTER_BITMAP_FLAG);
            c.drawBitmap(nebulaBmp, null, new RectF(W / 2 - W * k / 2 + ox, H / 2 - H * k / 2 + oy, W / 2 + W * k / 2 + ox, H / 2 + H * k / 2 + oy), np);
            // twinkling stars on top of the painting
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 60; i++) {
                float sx = (i * 173.1f) % W, sy = (i * 287.9f) % H;
                float tw = (float) Math.max(0, Math.sin(anim * (1.2f + (i % 4) * 0.5f) + i));
                if (tw < 0.3f) continue;
                p.setColor(alpha(0xFFFFFFFF, (int) (220 * tw)));
                c.drawPath(Art.starPath(sx, sy, (4 + (i % 3) * 3) * u * tw, 1.2f * u, 4), p);
            }
        }
        // god rays from the sun
        c.save();
        c.rotate(anim * 3, cx, cy);
        for (int k = 0; k < 10; k++) {
            Path ray = new Path();
            double a0 = k * Math.PI / 5, a1 = a0 + 0.09;
            float rr = (700 + 120 * (float) Math.sin(anim * 0.8 + k)) * u;
            ray.moveTo(cx, cy);
            ray.lineTo(cx + (float) Math.cos(a0) * rr, cy + (float) Math.sin(a0) * rr);
            ray.lineTo(cx + (float) Math.cos(a1) * rr, cy + (float) Math.sin(a1) * rr);
            ray.close();
            p.setShader(new RadialGradient(cx, cy, rr, 0x33FFD890, 0x00FFD890, Shader.TileMode.CLAMP));
            c.drawPath(ray, p);
        }
        p.setShader(null);
        c.restore();
        // milky-way band and the turning galaxy behind everything
        c.save();
        c.translate(cx, cy);
        c.rotate(-22);
        c.scale(1f, 0.5f);
        c.rotate(anim * 4);
        sprite(c, gBmp, 0, 0, W * 0.95f, 170);
        c.restore();
        glowCircle(c, cx - 330 * u, cy - 260 * u, 330 * u, 0x335B8CFF);
        glowCircle(c, cx + 360 * u, cy + 200 * u, 360 * u, 0x33E05BD8);
        glowCircle(c, cx, cy, 470 * u, 0x44FF9A3C);
        int[] orb = {Defs.MERCURY, Defs.VENUS, Defs.EARTH, Defs.MARS, Defs.JUPITER, Defs.SATURN, Defs.URANUS, Defs.NEPTUNE, Defs.PLUTO};
        float tiltK = 0.36f;
        // orbit lines with a soft glow
        sp.setPathEffect(null);
        for (int i = 0; i < orb.length; i++) {
            float rr = (150 + i * 44) * u;
            RectF o = new RectF(cx - rr, cy - rr * tiltK, cx + rr, cy + rr * tiltK);
            sp.setStrokeWidth(7 * u);
            sp.setColor(0x14FFFFFF);
            c.drawOval(o, sp);
            sp.setStrokeWidth(2 * u);
            sp.setColor(0x40FFFFFF);
            c.drawOval(o, sp);
        }
        // planets behind the sun first, then the sun, then those in front
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1) {
                // sun with turning corona rays
                for (int k = 0; k < 12; k++) {
                    double a = anim * 0.3 + k * Math.PI / 6;
                    float l = (150 + 25 * (float) Math.sin(anim * 2 + k)) * u;
                    sp.setStrokeWidth(10 * u);
                    sp.setColor(0x33FFC860);
                    c.drawLine(cx + (float) Math.cos(a) * 95 * u, cy + (float) Math.sin(a) * 95 * u,
                            cx + (float) Math.cos(a) * l, cy + (float) Math.sin(a) * l, sp);
                }
                sprite(c, pBmp[Defs.SUN], cx, cy, (150 + 4 * (float) Math.sin(anim * 2)) * u, 255);
            }
            for (int i = 0; i < orb.length; i++) {
                float rr = (150 + i * 44) * u;
                double speed = 0.9 / Math.pow(1 + i, 0.75);
                double a = anim * speed + i * 2.3;
                boolean front = Math.sin(a) > 0;
                if ((pass == 1) != front) continue;
                float depth = 0.82f + 0.18f * (float) Math.sin(a);
                float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr * tiltK;
                // glowing trail along the orbit
                p.setShader(null);
                p.setStyle(Paint.Style.FILL);
                for (int k = 1; k <= 10; k++) {
                    double ta = a - k * 0.05;
                    p.setColor(alpha(0xFFBFD8FF, (int) (90 * (1 - k / 11f) * depth)));
                    c.drawCircle(cx + (float) Math.cos(ta) * rr, cy + (float) Math.sin(ta) * rr * tiltK, (5 - k * 0.35f) * u, p);
                }
                float sz = (i == 4 ? 60 : i == 5 ? 66 : i >= 6 ? 44 : 34 + i * 3) * u * depth;
                sprite(c, pBmp[orb[i]], x, y, sz, 255);
            }
        }
        // a black hole far away and shooting stars
        blackHole(c, W * 0.86f, H * 0.075f, 40 * u);
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
    }

    void drawLogin(Canvas c) {
        if (consoleApp) { drawConsoleLogin(c); return; }
        solarCover(c, W / 2, H * 0.265f);
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
        if (!Cloud.configured()) {
            RectF warn = new RectF(60 * u, card.bottom + 30 * u, W - 60 * u, card.bottom + 170 * u);
            panel(c, warn, 0xEE6A1F3A, 30 * u);
            plain(c, L.t("ยังไม่ได้ตั้งค่า Firebase", "Firebase is not set up yet"), W / 2, warn.top + 58 * u, 36 * u, 0xFFFFD0DD, Paint.Align.CENTER, warn.width() - 40 * u);
            plain(c, L.t("ใส่ API Key และ Database URL ใน FirebaseConfig.java", "Put the API key and database URL in FirebaseConfig.java"), W / 2, warn.top + 108 * u, 28 * u, 0xFFFFB8C8, Paint.Align.CENTER, warn.width() - 40 * u);
        }
        plain(c, L.t("ไอดีออนไลน์ • ข้อมูลเก็บบนเซิร์ฟเวอร์", "Online accounts • progress saved in the cloud"),
                W / 2, H - 60 * u, 28 * u, 0x99FFFFFF, Paint.Align.CENTER, W - 80 * u);
        plain(c, VERSION, W - 30 * u, H - 20 * u, 24 * u, 0x66FFFFFF, Paint.Align.RIGHT, 0);
    }

    // ================================================================== leaderboard
    void refreshPlayers() { refreshPlayers(null); }

    void refreshPlayers(Runnable then) {
        if (playersLoading || !Cloud.configured()) return;
        playersLoading = true;
        cloud.players((list, err) -> {
            playersLoading = false;
            if (err != null) { showToast(err); return; }
            players = list;
            if (then != null) then.run();
        });
    }

    /** online players, with my own entry replaced by my live local numbers */
    java.util.List<Cloud.Player> rankList() {
        java.util.ArrayList<Cloud.Player> out = new java.util.ArrayList<>();
        boolean me = false;
        for (Cloud.Player p0 : players) {
            if (p0.uid.equals(cloud.uid)) {
                me = true;
                Cloud.Player q = new Cloud.Player();
                q.uid = p0.uid; q.name = user; q.avatar = pf.avatar; q.photo = pf.photo; q.role = cloud.myRole(); q.lastSeen = p0.lastSeen; q.wins = pf.wins; q.stars = pf.stars; q.playSec = pf.playSec;
                q.planets = pf.ownedCount(); q.banned = p0.banned;
                out.add(q);
            } else out.add(p0);
        }
        if (!me && pf != null) {
            Cloud.Player q = new Cloud.Player();
            q.uid = cloud.uid; q.name = user; q.avatar = pf.avatar; q.photo = pf.photo; q.role = cloud.myRole(); q.wins = pf.wins; q.stars = pf.stars; q.playSec = pf.playSec; q.planets = pf.ownedCount();
            out.add(q);
        }
        return out;
    }

    int statOf(Cloud.Player p0, int tab) {
        return tab == 0 ? p0.wins : tab == 1 ? p0.stars : p0.playSec;
    }

    void drawLeader(Canvas c) {
        header(c, L.t("อันดับ", "Leaderboard"));
        String[] tabs = {L.t("ชนะมากสุด", "Most wins"), L.t("Star มากสุด", "Most Star"), L.t("เล่นนานสุด", "Play time")};
        float tw = (W - 120 * u) / 3f;
        for (int i = 0; i < 3; i++) {
            final int t = i;
            rf.set(50 * u + i * (tw + 10 * u), 190 * u, 50 * u + i * (tw + 10 * u) + tw, 285 * u);
            button(c, rf, tabs[i], leaderTab == i ? 0xFFFF8A2B : 0xFF3A3360, true, () -> leaderTab = t);
        }
        final java.util.List<Cloud.Player> list = new java.util.ArrayList<>();
        // only regular players are ranked: staff/ranked ids, banned ids and the console are left out
        for (Cloud.Player x : rankList()) if (x.role == 0 && !x.banned && !"console".equalsIgnoreCase(x.name)) list.add(x);
        final int tab = leaderTab;
        java.util.Collections.sort(list, (x, y) -> Integer.compare(statOf(y, tab), statOf(x, tab)));
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
                Cloud.Player a = list.get(rank);
                float cy = col.top - 110 * u;
                glowCircle(c, col.centerX(), cy, 100 * u, alpha(medal[rank], 0x66));
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF2A1A5E);
                c.drawCircle(col.centerX(), cy, 56 * u, p);
                sp.setStrokeWidth(6 * u);
                sp.setColor(medal[rank]);
                c.drawCircle(col.centerX(), cy, 56 * u, sp);
                avatarOf(c, col.centerX(), cy, 46 * u, a.avatar, a.photo);
                if (rank == 0) trophy(c, col.centerX(), cy - 95 * u, 30 * u);
                text(c, a.name, col.centerX(), col.top - 20 * u, 34 * u, java.util.Objects.equals(a.uid, cloud.uid) ? 0xFFFFD23F : 0xFFFFFFFF, Paint.Align.CENTER, pw);
            }
        }
        float y = py + ph + 40 * u, rh = 96 * u;
        float cardTop0 = H - 470 * u;
        int max = (int) Math.max(0, Math.min(list.size(), (cardTop0 - 20 * u - y) / (rh + 12 * u)));
        for (int i = 0; i < max; i++) {
            Cloud.Player a = list.get(i);
            boolean mine = java.util.Objects.equals(a.uid, cloud.uid);
            RectF r = new RectF(40 * u, y, W - 40 * u, y + rh);
            panel(c, r, mine ? 0xFF4A3D8F : 0xFF241A4E, 24 * u);
            int mc = i < 3 ? medal[i] : 0xFF6A6488;
            p.setStyle(Paint.Style.FILL);
            p.setColor(mc);
            c.drawCircle(r.left + 60 * u, r.centerY(), 30 * u, p);
            text(c, String.valueOf(i + 1), r.left + 60 * u, r.centerY() + 13 * u, 36 * u, 0xFF140A2A);
            avatarOf(c, r.left + 135 * u, r.centerY(), 30 * u, a.avatar, a.photo);
            text(c, a.name + (mine ? L.t("  (คุณ)", "  (you)") : ""), r.left + 185 * u, r.centerY() + 14 * u, 38 * u, 0xFFFFFFFF, Paint.Align.LEFT, 430 * u);
            int v = statOf(a, tab);
            String vs = tab == 2 ? fmtTime(v) : String.valueOf(v);
            text(c, vs, r.right - 40 * u, r.centerY() + 14 * u, 40 * u, 0xFFFFE27A, Paint.Align.RIGHT, 0);
            y += rh + 12 * u;
        }
        if (playersLoading) plain(c, L.t("กำลังโหลดอันดับออนไลน์...", "Loading online ranking..."), W / 2, y + 40 * u, 32 * u, 0xFFB8F6FF, Paint.Align.CENTER, 0);
        float cardTop = Math.max(y + 20 * u, cardTop0);
        RectF me = new RectF(40 * u, cardTop, W - 40 * u, cardTop + 360 * u);
        panel(c, me, 0xEE2A1A5E, 36 * u);
        avatar(c, me.left + 110 * u, me.top + 110 * u, 70 * u, pf.avatar);
        title(c, user, me.left + 210 * u, me.top + 95 * u, 50 * u, 0xFFFFD23F, Paint.Align.LEFT, me.width() - 450 * u);
        int myRank = -1;
        for (int i = 0; i < list.size(); i++) if (java.util.Objects.equals(list.get(i).uid, cloud.uid)) myRank = i + 1;
        plain(c, myRank < 0 ? L.t("ทีมงานไม่ถูกจัดอันดับ", "Staff ranks aren't ranked") : L.t("อันดับของคุณ: #", "Your rank: #") + myRank + " / " + list.size(), me.left + 210 * u, me.top + 145 * u, 32 * u, 0xFFE6E0FF, Paint.Align.LEFT, 0);
        rf.set(me.right - 230 * u, me.top + 40 * u, me.right - 40 * u, me.top + 120 * u);
        button(c, rf, L.t("รีเฟรช", "Refresh"), 0xFF33A8D0, !playersLoading, this::refreshPlayers);
        String[] lab = {L.t("ชนะ", "Wins"), "Star", L.t("เวลาเล่น", "Play time")};
        String[] vals = {String.valueOf(pf.wins), String.valueOf(pf.stars), fmtTime(pf.playSec)};
        float cw = (me.width() - 80 * u) / 3f;
        for (int i = 0; i < 3; i++) {
            RectF b = new RectF(me.left + 40 * u + i * cw + 8 * u, me.top + 190 * u, me.left + 40 * u + (i + 1) * cw - 8 * u, me.bottom - 30 * u);
            rarityCard(c, b, i == leaderTab ? 0xFFFFD23F : 0xFF8A6BFF, true, false, 24 * u);
            plain(c, lab[i], b.centerX(), b.top + 45 * u, 28 * u, 0xFFE6E0FF, Paint.Align.CENTER, 0);
            title(c, vals[i], b.centerX(), b.bottom - 25 * u, 40 * u, 0xFFFFFFFF, Paint.Align.CENTER, b.width() - 20 * u);
        }
        plain(c, L.t("อันดับออนไลน์ของผู้เล่นทุกคน", "Online ranking of every player"), W / 2, H - 50 * u, 28 * u, 0x99FFFFFF, Paint.Align.CENTER, 0);
    }

    // ================================================================== admin panel
    final java.util.HashMap<String, Profile> profCache = new java.util.HashMap<>();

    Cloud.Player playerById(String id) {
        for (Cloud.Player q : players) if (q.uid.equals(id)) return q;
        return null;
    }

    Cloud.Player playerByName(String n) {
        String k = Accounts.norm(n);
        for (Cloud.Player q : players) if (Accounts.norm(q.name).equals(k)) return q;
        return null;
    }

    String nameOf(String id) {
        if (id == null) return "?";
        if (id.equals(cloud.uid)) return user;
        Cloud.Player q = playerById(id);
        return q == null ? id : q.name;
    }

    /** load the selected player's save for editing (my own save is edited directly) */
    void selectAdmin(String id) {
        adminSel = id;
        if (id.equals(cloud.uid) && !consoleApp) { adminProf = pf; adminProfUid = id; return; }
        adminProf = null;
        adminLoading = true;
        cloud.fetchProfile(id, (q, err) -> {
            adminLoading = false;
            if (err != null) { showToast(err); return; }
            if (id.equals(adminSel)) { adminProf = q; adminProfUid = id; }
        });
    }

    /** save an edited player back to the cloud */
    void commitAdmin(String msg) {
        if (adminProf == null) return;
        if (!Roles.canEdit(cloud.myRole())) { showToast(L.t("ต้องเป็น Admin ขึ้นไป", "Admin rank or higher needed")); return; }
        if (adminProfUid.equals(cloud.uid) && !consoleApp) { pf.save(); pushNow(); cloud.logAsync("edit self: " + msg); showToast(msg); return; }
        adminProf.saveTime = System.currentTimeMillis();
        cloud.upload(adminProfUid, nameOf(adminProfUid), adminProf, "edit " + nameOf(adminProfUid) + ": " + msg, (ok, err) -> {
            showToast(err != null ? err : msg);
            refreshPlayers();
        });
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

    void setAmount(String what, int current, IntSet setter) {
        Dialogs.ask(ctx, L.t("ตั้ง ", "Set ") + what, nameOf(adminSel), new String[]{what}, new int[]{Dialogs.NUMBER},
                new String[]{String.valueOf(current)}, "OK", v -> {
                    try {
                        int n = Integer.parseInt(v[0].trim());
                        if (n < 0 || n > 99999999) return L.t("ใส่ได้ 0 ถึง 99,999,999", "Use 0 to 99,999,999");
                        setter.set(n);
                        commitAdmin(what + " = " + n + " -> " + nameOf(adminSel));
                        return null;
                    } catch (NumberFormatException e) {
                        return L.t("ใส่ตัวเลขเท่านั้น", "Numbers only");
                    }
                });
    }

    /** admin: give a planet and set its level, enchant and material */
    void drawGivePlanet(Canvas c) {
        RectF r = new RectF(30 * u, 120 * u, W - 30 * u, H - 120 * u);
        popupFrame(c, r, L.t("ดวงดาวของ ", "Planets of ") + nameOf(adminSel));
        final Profile q = adminProf;
        if (q == null) { popup = P_NONE; return; }
        int cols = 5;
        float cell = (r.width() - 80 * u) / cols, top = r.top + 140 * u, rowH = cell * 0.95f;
        for (int i = 0; i < Defs.P_COUNT; i++) {
            final int t = i;
            float x = r.left + 40 * u + (i % cols) * cell, y = top + (i / cols) * rowH;
            RectF cr = new RectF(x + 6 * u, y + 6 * u, x + cell - 6 * u, y + rowH - 6 * u);
            rarityCard(c, cr, Defs.RARITY_COLOR[Defs.P_RARITY[i]], q.owned[i], givePick == i, 22 * u);
            sprite(c, pBmp[i], cr.centerX(), cr.centerY() - 16 * u, cell * 0.25f, q.owned[i] ? 255 : 110);
            plain(c, Defs.P_NAME[i], cr.centerX(), cr.bottom - 16 * u, 24 * u, 0xFFFFFFFF, Paint.Align.CENTER, cr.width() - 8 * u);
            if (q.owned[i]) plain(c, "Lv" + q.level[i], cr.right - 10 * u, cr.top + 30 * u, 22 * u, 0xFF7CFF8A, Paint.Align.RIGHT, 0);
            btns.add(new Btn(cr, () -> givePick = t));
        }
        float ey = top + ((Defs.P_COUNT + cols - 1) / cols) * rowH + 20 * u;
        final int t = givePick;
        if (t < 0) {
            plain(c, L.t("แตะดวงดาวที่ต้องการให้หรือแก้ไข", "Tap a planet to give or edit it"), r.centerX(), ey + 80 * u, 32 * u, 0xFFC9C2E6, Paint.Align.CENTER, 0);
        } else {
            RectF ed = new RectF(r.left + 30 * u, ey, r.right - 30 * u, r.bottom - 150 * u);
            panel(c, ed, 0xEE140A33, 30 * u);
            sprite(c, pBmp[t], ed.left + 90 * u, ed.top + 90 * u, 60 * u, 255);
            title(c, Defs.P_NAME[t], ed.left + 175 * u, ed.top + 80 * u, 44 * u, Defs.RARITY_COLOR[Defs.P_RARITY[t]], Paint.Align.LEFT, 0);
            plain(c, q.owned[t] ? L.t("มีแล้ว • ซ้ำ ", "Owned • copies ") + q.copies[t] : L.t("ยังไม่มี", "Not owned"),
                    ed.left + 175 * u, ed.top + 125 * u, 26 * u, q.owned[t] ? 0xFF7CFF8A : 0xFFFF8A8A, Paint.Align.LEFT, 0);
            float bw = (ed.width() - 90 * u) / 2f, y = ed.top + 170 * u, bh = 80 * u;
            rf.set(ed.left + 30 * u, y, ed.left + 30 * u + bw, y + bh);
            button(c, rf, q.owned[t] ? L.t("+1 ชิ้นซ้ำ", "+1 copy") : L.t("ให้ดาวนี้", "Give it"), 0xFF33C6A6, true, () -> { q.givePlanet(t); giveDirty = true; });
            rf.set(ed.right - 30 * u - bw, y, ed.right - 30 * u, y + bh);
            button(c, rf, L.t("เอาออก", "Remove"), 0xFFFF6B5B, q.owned[t], () -> {
                q.owned[t] = false;
                q.copies[t] = 0;
                for (int k = 0; k < q.deck.length; k++) if (q.deck[k] == t) q.deck[k] = -1;
                giveDirty = true;
            });
            y += bh + 24 * u;
            // level
            editorRow(c, ed, y, "Level", "Lv " + q.level[t], q.owned[t], () -> { q.level[t] = Math.max(1, q.level[t] - 1); giveDirty = true; },
                    () -> { q.level[t] = Math.min(Profile.MAX_LV, q.level[t] + 1); giveDirty = true; });
            y += 100 * u;
            // enchant (none + all enchants)
            int e = q.enchant[t];
            editorRow(c, ed, y, "Enchant", e < 0 ? L.t("ไม่มี", "none") : enchName(e, q.tier[t]), q.owned[t],
                    () -> { setEnchant(q, t, q.enchant[t] < 0 ? Defs.EN_COUNT - 1 : q.enchant[t] - 1); giveDirty = true; },
                    () -> { setEnchant(q, t, q.enchant[t] + 1 >= Defs.EN_COUNT ? -1 : q.enchant[t] + 1); giveDirty = true; });
            if (e >= 0) badge(c, ed.left + 255 * u, y + 40 * u, 26 * u, e, q.tier[t]);
            y += 100 * u;
            if (e >= 0 && Defs.EN_TIERS[e] > 0) {
                editorRow(c, ed, y, "Tier", "I II III".split(" ")[Math.max(0, q.tier[t] - 1)], true,
                        () -> { q.tier[t] = Math.max(1, q.tier[t] - 1); giveDirty = true; },
                        () -> { q.tier[t] = Math.min(Defs.EN_TIERS[q.enchant[t]], q.tier[t] + 1); giveDirty = true; });
                y += 100 * u;
            }
            // material
            int m = q.material[t];
            editorRow(c, ed, y, "Material", m < 0 ? L.t("ไม่มี", "none") : matName(m), q.owned[t],
                    () -> { q.material[t] = q.material[t] < 0 ? Defs.MAT_COUNT - 1 : q.material[t] - 1; giveDirty = true; },
                    () -> { q.material[t] = q.material[t] + 1 >= Defs.MAT_COUNT ? -1 : q.material[t] + 1; giveDirty = true; });
            if (m >= 0) materialBadge(c, ed.left + 255 * u, y + 40 * u, 26 * u, m);
        }
        float bw = (r.width() - 100 * u) / 2f;
        rf.set(r.left + 30 * u, r.bottom - 125 * u, r.left + 30 * u + bw, r.bottom - 35 * u);
        button(c, rf, L.t("ปิด", "Close"), 0xFF6B5BD6, true, () -> {
            popup = P_NONE;
            if (giveDirty) { giveDirty = false; selectAdmin(adminSel); showToast(L.t("ไม่ได้บันทึก", "Not saved")); }
        });
        rf.set(r.right - 30 * u - bw, r.bottom - 125 * u, r.right - 30 * u, r.bottom - 35 * u);
        button(c, rf, L.t("บันทึก", "Save"), 0xFF2E9A6A, giveDirty, () -> {
            giveDirty = false;
            q.save();
            commitAdmin(givePick >= 0 ? Defs.P_NAME[givePick] + L.t(" อัปเดตแล้ว", " updated") : L.t("บันทึกแล้ว", "Saved"));
        });
    }

    static void setEnchant(Profile q, int t, int e) {
        q.enchant[t] = e;
        q.tier[t] = e >= 0 && Defs.EN_TIERS[e] > 0 ? Defs.EN_TIERS[e] : 0;
    }

    /** label  [<]  value  [>] */
    void editorRow(Canvas c, RectF ed, float y, String label, String value, boolean enabled, Runnable minus, Runnable plus) {
        plain(c, label, ed.left + 40 * u, y + 52 * u, 30 * u, 0xFFC9C2E6, Paint.Align.LEFT, 0);
        rf.set(ed.left + 300 * u, y, ed.left + 400 * u, y + 80 * u);
        button(c, rf, "<", 0xFF4A3D8F, enabled, minus);
        rf.set(ed.right - 130 * u, y, ed.right - 30 * u, y + 80 * u);
        button(c, rf, ">", 0xFF4A3D8F, enabled, plus);
        text(c, value, (ed.left + 400 * u + ed.right - 130 * u) / 2, y + 54 * u, 34 * u, enabled ? 0xFFFFFFFF : 0xFF6A6488, Paint.Align.CENTER, ed.right - ed.left - 560 * u);
    }

    void drawConsole(Canvas c) {
        RectF r = new RectF(40 * u, H / 2 - 640 * u, W - 40 * u, H / 2 + 640 * u);
        popupFrame(c, r, L.t("คอนโซลคำสั่งแอดมิน", "Admin command console"));
        RectF box = new RectF(r.left + 40 * u, r.top + 140 * u, r.right - 40 * u, r.bottom - 300 * u);
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(0xFF05020F);
        c.drawRoundRect(box, 20 * u, 20 * u, p);
        sp.setPathEffect(null);
        sp.setStrokeWidth(3 * u);
        sp.setColor(0xFF2E9A6A);
        c.drawRoundRect(box, 20 * u, 20 * u, sp);
        float lh = 46 * u;
        int maxLines = (int) ((box.height() - 40 * u) / lh);
        int start = Math.max(0, consoleLog.size() - maxLines);
        float y = box.top + 50 * u;
        if (consoleLog.isEmpty()) plain(c, L.t("พิมพ์ help เพื่อดูคำสั่งทั้งหมด", "Type help to list every command"), box.left + 24 * u, y, 30 * u, 0xFF7CFF8A, Paint.Align.LEFT, box.width() - 48 * u);
        for (int i = start; i < consoleLog.size(); i++) {
            String line = consoleLog.get(i);
            int col = line.startsWith(">") ? 0xFFFFE27A : line.startsWith("!") ? 0xFFFF8A8A : 0xFF9CFFB8;
            plain(c, line, box.left + 24 * u, y, 28 * u, col, Paint.Align.LEFT, box.width() - 48 * u);
            y += lh;
        }
        if (((int) (anim * 2)) % 2 == 0) plain(c, "_", box.left + 24 * u, y, 30 * u, 0xFF7CFF8A, Paint.Align.LEFT, 0);
        float bw = (r.width() - 120 * u) / 2f;
        rf.set(r.left + 40 * u, r.bottom - 270 * u, r.left + 40 * u + bw, r.bottom - 160 * u);
        button(c, rf, L.t("พิมพ์คำสั่ง", "Type command"), 0xFF2E9A6A, true, this::askCommand);
        rf.set(r.right - 40 * u - bw, r.bottom - 270 * u, r.right - 40 * u, r.bottom - 160 * u);
        button(c, rf, "help", 0xFF4F8BFF, true, () -> runCommand("help"));
        rf.set(r.centerX() - 200 * u, r.bottom - 130 * u, r.centerX() + 200 * u, r.bottom - 40 * u);
        button(c, rf, L.t("ปิด", "Close"), 0xFF6B5BD6, true, () -> popup = P_NONE);
    }

    void askCommand() {
        if (players.isEmpty()) refreshPlayers();
        Dialogs.ask(ctx, L.t("พิมพ์คำสั่ง", "Command"), "give <user> star 500  •  planet <user> sun  •  ban <user>",
                new String[]{L.t("คำสั่ง", "Command")}, new int[]{Dialogs.TEXT}, null, L.t("รัน", "Run"), v -> {
                    runCommand(v[0]);
                    return null;
                });
    }

    void log(String s) {
        consoleLog.add(s);
        while (consoleLog.size() > 60) consoleLog.remove(0);
    }

    /** general admin commands, also usable from the console popup */
    void runCommand(String raw) { runCommand(raw, false); }

    void runCommand(String raw, boolean retried) {
        String line = raw == null ? "" : raw.trim();
        if (line.startsWith("/")) line = line.substring(1);
        if (line.isEmpty()) return;
        if (!retried) { log("> " + line); lastCommand = line; }
        final String[] a = line.split("\\s+");
        final String cmd = a[0].toLowerCase(Locale.US);
        final int my = cloud.myRole();
        if (my < Roles.STAFF) { log("! " + L.t("ต้องมียศทีมงาน", "staff rank required")); return; }
        switch (cmd) {
            case "help":
                log("# view:    list [text] • online • whois <u> • info <u> • logs [n] • server • refresh • clear");
                log("# players: ban <u> [hours|perm] [reason] • tempban <u> <hours> [reason] • unban <u> • kick <u>");
                log("# items:   give|take|set <u> <star|millite|crystal|oil|wins|bosskills> <n>");
                log("#         skin <u> <style> <planet|all> • unskin <u> <style> <planet|all>   (styles: golden graveyard white aurora red elite shinypurple shiny madness king anniv)");
                log("#         item <u> <capsule|stardust|nebula|catalyst|artcapsule|supply|hellmoon|frostmoon|stormmoon|goldmoon|voidmoon> <n> • evolve <u> <planet> [off]");
                log("#         planet <u> <name|all> • unplanet <u> <name> • level <u> <name|all> <1-10>");
                log("#         enchant <u> <planet> <enchant|none> [tier] • material <u> <planet> <material|none>");
                log("#         unlock <u> [stage] • maxlevel <u> • reset <u> • wipe <u>");
                log("#         giveall <star|millite|crystal> <n>");
                log("# server:  maintenance on|off • broadcast <msg>|clear • minver <n>" + (consoleApp ? "" : " • infsp on|off"));
                log("# codes:   code create <CODE> <star:500,supply:1,...> [uses] [days] • code delete <CODE>");

                return;
            case "clear": consoleLog.clear(); return;
            case "refresh": refreshPlayers(); log("refreshing player list..."); return;
            case "list": {
                if (players.isEmpty()) { refreshPlayers(() -> runCommand(lineOf(a), true)); return; }
                String f = a.length > 1 ? Accounts.norm(a[1]) : "";
                StringBuilder b = new StringBuilder();
                int n = 0;
                for (Cloud.Player x : players) {
                    if (!f.isEmpty() && !Accounts.norm(x.name).contains(f)) continue;
                    n++;
                    b.append(x.name).append(x.role > 0 ? "[" + Roles.NAME[x.role] + "]" : "").append(x.banned ? "(ban)" : "").append(onlineNow(x) ? "*" : "").append("  ");
                }
                log(n + " players  (* = online):");
                log(b.toString().trim());
                return;
            }
            case "online": {
                if (players.isEmpty()) { refreshPlayers(() -> runCommand(lineOf(a), true)); return; }
                StringBuilder b = new StringBuilder();
                int n = 0;
                for (Cloud.Player x : players) if (onlineNow(x)) { n++; b.append(x.name).append("  "); }
                log("online now: " + n + (consoleApp ? "" : "  •  ping " + pingMs + " ms"));
                if (n > 0) log(b.toString().trim());
                return;
            }
            case "logs": {
                int n = 10;
                try { if (a.length > 1) n = Math.max(1, Math.min(50, Integer.parseInt(a[1]))); } catch (NumberFormatException ignored) { }
                java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("d/M HH:mm", Locale.US);
                cloud.logs(n, (list, err) -> {
                    if (err != null) { log("! " + err); return; }
                    if (list.isEmpty()) log("(no logs)");
                    for (int i = list.size() - 1; i >= 0; i--) log(f.format(new java.util.Date(list.get(i).t)) + "  " + list.get(i).by + ": " + list.get(i).msg);
                });
                return;
            }
            case "server":
                cloud.server((s, err) -> {
                    if (err != null) { log("! " + err); return; }
                    serverInfo = s;
                    log("server: " + (s.maintenance ? "MAINTENANCE" : "open") + " • online " + s.online + " • minver " + s.minVersion + " • broadcast: " + (s.broadcast == null ? "-" : s.broadcast));
                });
                return;
            case "maintenance":
                if (!need(Roles.DEV)) return;
                if (a.length < 2) { log("! maintenance on|off"); return; }
                cloud.setMaintenance(a[1].equalsIgnoreCase("on"), (ok, err) -> log(err != null ? "! " + err : "ok: maintenance " + a[1].toLowerCase(Locale.US)));
                return;
            case "code": {
                if (!need(Roles.DEV)) return;
                if (a.length >= 3 && a[1].equalsIgnoreCase("delete")) {
                    final String cd = a[2];
                    social.deleteCode(cd, (ok, err) -> log(err != null ? "! " + err : "ok: code " + cd.toUpperCase(Locale.US) + " deleted"));
                    return;
                }
                if (a.length < 4 || !a[1].equalsIgnoreCase("create")) {
                    log("! code create <CODE> <reward:n,...> [uses 0=unlimited] [days 0=forever] • code delete <CODE>");
                    log("  rewards: star crystal oil capsule stardust nebula catalyst artcapsule supply millite   e.g. star:500,supply:1");
                    return;
                }
                String[] names = {"star", "crystal", "oil", "capsule", "stardust", "nebula", "catalyst", "artcapsule", "supply", "millite"};
                StringBuilder rw = new StringBuilder();
                for (String part : a[3].split(",")) {
                    String[] kv = part.split(":");
                    if (kv.length != 2) { log("! bad reward " + part); return; }
                    int kind = -1;
                    for (int k = 0; k < names.length; k++) if (names[k].equalsIgnoreCase(kv[0].trim())) kind = k;
                    if (kind < 0) { log("! unknown reward " + kv[0]); return; }
                    int n;
                    try { n = Integer.parseInt(kv[1].trim()); } catch (NumberFormatException e) { log("! bad amount " + kv[1]); return; }
                    if (n <= 0 || n > 100000) { log("! amount 1..100000"); return; }
                    rw.append(rw.length() > 0 ? "," : "").append(kind).append(':').append(n);
                }
                int uses = 0, days = 0;
                try {
                    if (a.length > 4) uses = Integer.parseInt(a[4]);
                    if (a.length > 5) days = Integer.parseInt(a[5]);
                } catch (NumberFormatException e) { log("! uses / days must be numbers"); return; }
                final String cd = a[2].toUpperCase(Locale.US).replaceAll("[^A-Z0-9]", "");
                if (cd.length() < 3) { log("! code needs 3+ letters/digits"); return; }
                final int fu = uses, fd = days;
                social.makeCode(cd, rw.toString(), uses, days, (ok, err) -> log(err != null ? "! " + err : "ok: code " + cd + " = " + a[3] + (fu > 0 ? " • " + fu + " uses" : "") + (fd > 0 ? " • " + fd + " days" : "")));
                return;
            }
            case "broadcast": {
                if (!need(Roles.DEV)) return;
                if (a.length < 2) { log("! broadcast <message> | broadcast clear"); return; }
                final String msg = a[1].equalsIgnoreCase("clear") ? null : line.substring(line.indexOf(' ') + 1).trim();
                cloud.setBroadcast(msg, (ok, err) -> log(err != null ? "! " + err : "ok: broadcast " + (msg == null ? "cleared" : "sent")));
                return;
            }
            case "minver": {
                if (!need(Roles.DEV)) return;
                if (a.length < 2) { log("! minver <versionCode>   (this game = " + VERSION_CODE + ")"); return; }
                final int mv;
                try { mv = Integer.parseInt(a[1]); } catch (NumberFormatException e) { log("! number"); return; }
                cloud.setMinVersion(mv, (ok, err) -> log(err != null ? "! " + err : "ok: min version = " + mv));
                return;
            }
            case "infsp":
                if (!need(Roles.ADMIN)) return;
                acc.setInfiniteSP(a.length > 1 && a[1].equalsIgnoreCase("on"));
                log("infinite SP = " + acc.infiniteSP());
                return;
            case "giveall": {
                if (!need(Roles.ADMIN)) return;
                if (a.length < 3) { log("! giveall <star|millite|crystal> <n>"); return; }
                if (players.isEmpty()) { refreshPlayers(() -> runCommand(lineOf(a), true)); return; }
                final String field = a[1].toLowerCase(Locale.US);
                final int n;
                try { n = Integer.parseInt(a[2]); } catch (NumberFormatException e) { log("! number"); return; }
                if (!field.equals("star") && !field.equals("millite") && !field.equals("crystal")) { log("! star | millite | crystal"); return; }
                log("giving " + n + " " + field + " to " + players.size() + " players...");
                cloud.logAsync("giveall " + field + " " + n);
                for (Cloud.Player x : new java.util.ArrayList<>(players)) {
                    final Cloud.Player tx = x;
                    cloud.fetchProfile(tx.uid, (q, err) -> {
                        if (err != null || q == null) return;
                        if (field.equals("star")) q.stars += n;
                        else if (field.equals("millite")) q.millite += n;
                        else q.crystals += n;
                        q.saveTime = System.currentTimeMillis();
                        cloud.upload(tx.uid, tx.name, q, (ok, e2) -> { if (e2 != null) log("! " + tx.name + ": " + e2); });
                    });
                }
                return;
            }
            default: break;
        }
        if (a.length < 2) { log("! " + L.t("ต้องระบุชื่อไอดี (พิมพ์ help)", "username required (type help)")); return; }
        boolean meTarget = Accounts.norm(a[1]).equals(Accounts.norm(user));
        final Cloud.Player target = meTarget ? playerById(cloud.uid) : playerByName(a[1]);
        final String tid = meTarget ? cloud.uid : (target == null ? null : target.uid);
        if (tid == null && !retried) { final String again = line; refreshPlayers(() -> runCommand(again, true)); return; }
        if (tid == null) { log("! " + L.t("ไม่พบไอดี ", "no such player ") + a[1]); return; }
        final String tname = nameOf(tid);
        final int trole = meTarget ? my : target == null ? 0 : target.role;
        boolean protectedTarget = !meTarget && trole >= my && my < Roles.OWNER;
        switch (cmd) {
            case "whois": {
                Cloud.Player x = target;
                if (x == null) { log("whois " + tname + ": (you)"); return; }
                java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("d MMM HH:mm", Locale.US);
                log("whois " + x.name + "  uid " + x.uid);
                log("  rank " + Roles.NAME[x.role] + " • " + (onlineNow(x) ? "online" : x.lastSeen > 0 ? "seen " + f.format(new java.util.Date(x.lastSeen)) : "offline")
                        + " • " + (x.banned ? "BANNED" + (x.banReason == null || x.banReason.isEmpty() ? "" : " (" + x.banReason + ")") + (x.banUntil > 0 ? " until " + f.format(new java.util.Date(x.banUntil)) : " permanent") : "not banned"));
                log("  " + x.stars + " Star • " + x.wins + " wins • " + x.planets + " planets • played " + fmtTime(x.playSec));
                return;
            }
            case "ban":
            case "tempban": {
                if (!need(Roles.MOD)) return;
                if (meTarget || protectedTarget) { log("! " + L.t("แบนคนนี้ไม่ได้ (ยศเท่ากันหรือสูงกว่า)", "can't ban this player (same or higher rank)")); return; }
                int hours = 0, from = 2;
                if (a.length > 2 && !a[2].equalsIgnoreCase("perm")) {
                    try { hours = Integer.parseInt(a[2].replace("h", "")); from = 3; } catch (NumberFormatException ignored) { }
                } else if (a.length > 2) from = 3;
                if (cmd.equals("tempban") && hours <= 0) { log("! tempban <user> <hours> [reason]"); return; }
                StringBuilder why = new StringBuilder();
                for (int i = from; i < a.length; i++) why.append(i > from ? " " : "").append(a[i]);
                final int h = hours;
                cloud.setBan(tid, tname, true, why.toString(), h, (ok, err) -> { log(err != null ? "! " + err : "ok: banned " + tname + (h > 0 ? " for " + h + "h" : " permanently")); refreshPlayers(); });
                return;
            }
            case "unban":
                if (!need(Roles.MOD)) return;
                cloud.setBan(tid, tname, false, null, 0, (ok, err) -> { log(err != null ? "! " + err : "ok: unbanned " + tname); refreshPlayers(); });
                return;
            case "kick":
                if (!need(Roles.MOD)) return;
                if (meTarget || protectedTarget) { log("! " + L.t("เตะคนนี้ไม่ได้", "can't kick this player")); return; }
                cloud.kick(tid, tname, (ok, err) -> log(err != null ? "! " + err : "ok: kicked " + tname));
                return;
            case "role":
                log("! " + L.t("ระบบยศถูกเอาออกแล้ว • จัดการเกมได้จาก Stella Console เท่านั้น", "ranks were removed • only Stella Console manages the game"));
                return;
            case "wipe":
                if (!need(Roles.ADMIN)) return;
                if (meTarget || protectedTarget) { log("! " + L.t("ลบคนนี้ไม่ได้", "can't wipe this player")); return; }
                cloud.wipe(tid, (ok, err) -> { log(err != null ? "! " + err : "ok: wiped " + tname); refreshPlayers(); });
                return;
            default: break;
        }
        // everything else reads (info) or edits the player's save: fetch -> change -> upload
        final boolean editing = !cmd.equals("info");
        if (editing && !need(Roles.ADMIN)) return;
        if (editing && protectedTarget) { log("! " + L.t("แก้ข้อมูลคนที่ยศเท่ากันหรือสูงกว่าไม่ได้", "can't edit a player of the same or higher rank")); return; }
        final String fline = line;
        Cloud.Cb<Profile> apply = (q, err) -> {
            if (err != null) { log("! " + err); return; }
            String res;
            try {
                res = applyCommand(cmd, a, q);
            } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
                res = "! " + L.t("รูปแบบคำสั่งไม่ถูกต้อง พิมพ์ help", "bad syntax, type help");
            }
            if (res == null) return;
            log(res);
            if (res.startsWith("ok")) {
                if (tid.equals(cloud.uid) && !consoleApp) { pf.save(); pushNow(); cloud.logAsync(fline); }
                else {
                    q.saveTime = System.currentTimeMillis();
                    cloud.upload(tid, tname, q, fline, (ok2, err2) -> { if (err2 != null) log("! " + err2); });
                }
                if (tid.equals(adminProfUid) && !tid.equals(cloud.uid)) adminProf = q;
            }
        };
        if (tid.equals(cloud.uid) && !consoleApp) apply.done(pf, null);
        else cloud.fetchProfile(tid, apply);
    }

    static String lineOf(String[] a) { return String.join(" ", a); }

    /** rank gate for console commands */
    boolean need(int rank) {
        if (cloud.myRole() >= rank) return true;
        log("! " + L.t("ต้องเป็น ", "needs ") + Roles.NAME[rank] + L.t(" ขึ้นไป", " or higher"));
        return false;
    }

    static int planetByName(String nm) {
        for (int i = 0; i < Defs.P_COUNT; i++) if (Defs.P_NAME[i].equalsIgnoreCase(nm)) return i;
        return -1;
    }

    /** @return the log line ("ok..." when the save changed and must be uploaded) */
    String applyCommand(String cmd, String[] a, Profile q) {
        switch (cmd) {
            case "info":
                return "info: " + q.stars + " Star, " + q.millite + " Mil, " + q.crystals + " Cry, " + q.oil + " Oil, " + q.skins.size() + " skins, " + q.wins + " wins, " + q.bossKills + " boss kills, "
                        + q.ownedCount() + "/" + Defs.P_COUNT + " planets, stage " + q.unlocked + "/" + Defs.ST_COUNT;
            case "give":
            case "take":
            case "set": {
                String what = a[2].toLowerCase(Locale.US);
                int v = Integer.parseInt(a[3]);
                if (v < 0 || v > 99999999) return "! 0 .. 99,999,999";
                int sign = cmd.equals("take") ? -1 : 1;
                boolean set = cmd.equals("set");
                switch (what) {
                    case "star": case "stars": q.stars = Math.max(0, set ? v : q.stars + sign * v); return "ok: star = " + q.stars;
                    case "millite": q.millite = Math.max(0, set ? v : q.millite + sign * v); return "ok: millite = " + q.millite;
                    case "crystal": case "crystals": q.crystals = Math.max(0, set ? v : q.crystals + sign * v); return "ok: crystal = " + q.crystals;
                    case "wins": q.wins = Math.max(0, set ? v : q.wins + sign * v); return "ok: wins = " + q.wins;
                    case "oil": q.oil = Math.max(0, set ? v : q.oil + sign * v); return "ok: crystal oil = " + q.oil;
                    case "bosskills": q.bossKills = Math.max(0, set ? v : q.bossKills + sign * v); return "ok: boss kills = " + q.bossKills;
                    default: return "! star | millite | crystal | oil | wins | bosskills";
                }
            }
            case "planet": {
                int t = planetByName(a[2]);
                if (t < 0) return "! " + L.t("ไม่พบดวงดาว ", "unknown planet ") + a[2];
                q.givePlanet(t);
                return "ok: +" + Defs.P_NAME[t];
            }
            case "unplanet": {
                int t = planetByName(a[2]);
                if (t < 0) return "! " + L.t("ไม่พบดวงดาว ", "unknown planet ") + a[2];
                q.owned[t] = false;
                q.copies[t] = 0;
                for (int i = 0; i < q.deck.length; i++) if (q.deck[i] == t) q.deck[i] = -1;
                q.save();
                return "ok: -" + Defs.P_NAME[t];
            }
            case "level": {
                int lv = Integer.parseInt(a[3]);
                if (lv < 1 || lv > Profile.MAX_LV) return "! level 1.." + Profile.MAX_LV;
                if (a[2].equalsIgnoreCase("all")) {
                    for (int i = 0; i < Defs.P_COUNT; i++) if (q.owned[i]) q.level[i] = lv;
                    q.save();
                    return "ok: every owned planet Lv " + lv;
                }
                int t = planetByName(a[2]);
                if (t < 0) return "! " + L.t("ไม่พบดวงดาว ", "unknown planet ") + a[2];
                if (!q.owned[t]) return "! " + L.t("ยังไม่มีดาวนี้ (ใช้ planet ก่อน)", "doesn't own it (use planet first)");
                q.level[t] = lv;
                q.save();
                return "ok: " + Defs.P_NAME[t] + " Lv " + lv;
            }
            case "enchant": {
                int t = planetByName(a[2]);
                if (t < 0) return "! " + L.t("ไม่พบดวงดาว ", "unknown planet ") + a[2];
                if (a[3].equalsIgnoreCase("none")) { q.enchant[t] = -1; q.tier[t] = 0; q.save(); return "ok: " + Defs.P_NAME[t] + " enchant removed"; }
                int e = -1;
                for (int i = 0; i < Defs.EN_COUNT; i++) if (Defs.EN_NAME[i].replace(" ", "").equalsIgnoreCase(a[3])) e = i;
                if (e < 0) return "! enchant: Strong Reach Moving Lightning Storm Technology Midas Electro CrystalRain Moon Galacia Lava Megalonia Gianttic Galaxia";
                q.enchant[t] = e;
                q.tier[t] = Defs.EN_TIERS[e] > 0 ? Math.max(1, Math.min(Defs.EN_TIERS[e], a.length > 4 ? Integer.parseInt(a[4]) : Defs.EN_TIERS[e])) : 0;
                q.save();
                return "ok: " + Defs.P_NAME[t] + " enchant = " + enchName(e, q.tier[t]);
            }
            case "material": {
                int t = planetByName(a[2]);
                if (t < 0) return "! " + L.t("ไม่พบดวงดาว ", "unknown planet ") + a[2];
                if (a[3].equalsIgnoreCase("none")) { q.material[t] = -1; q.save(); return "ok: " + Defs.P_NAME[t] + " material removed"; }
                int m = -1;
                for (int i = 0; i < Defs.MAT_COUNT; i++) if (Defs.MAT_NAME[i].equalsIgnoreCase(a[3])) m = i;
                if (m < 0) {
                    StringBuilder b = new StringBuilder("! material:");
                    for (String nm : Defs.MAT_NAME) b.append(' ').append(nm);
                    return b.toString();
                }
                q.material[t] = m;
                q.save();
                return "ok: " + Defs.P_NAME[t] + " material = " + Defs.MAT_NAME[m];
            }
            case "unlock": {
                if (a.length > 2) {
                    int st = Integer.parseInt(a[2]);
                    if (st < 1 || st > Defs.ST_COUNT) return "! stage 1.." + Defs.ST_COUNT;
                    q.unlocked = Math.max(q.unlocked, st);
                    q.save();
                    return "ok: unlocked up to stage " + st;
                }
                q.unlockAllStages();
                return "ok: stages unlocked";
            }
            case "skin":
            case "unskin": {
                int st = -1;
                for (int i = 0; i < Skins.STYLES; i++) if (Skins.NAME[i].replace(" ", "").equalsIgnoreCase(a[2]) || (i == Skins.ANNIV && a[2].equalsIgnoreCase("anniv"))) st = i;
                if (st < 0) return "! style: golden graveyard white aurora red elite shinypurple shiny madness king anniv";
                boolean all = a[3].equalsIgnoreCase("all");
                int t = all ? -1 : planetByName(a[3]);
                if (!all && t < 0) return "! " + L.t("ไม่พบดวงดาว ", "unknown planet ") + a[3];
                if (!all && !Skins.fits(st, t)) return "! " + Skins.NAME[st] + L.t(" ใช้กับ ", " doesn't fit ") + Defs.P_NAME[t] + L.t(" ไม่ได้", "");
                int n = 0;
                for (int k = 0; k < Defs.P_COUNT; k++) {
                    if ((!all && k != t) || !Skins.fits(st, k)) continue;
                    int key = Skins.key(st, k);
                    if (cmd.equals("skin")) { if (q.skins.add(key)) n++; }
                    else if (q.skins.remove(key)) { n++; if (q.skinOn[k] == st) q.skinOn[k] = -1; }
                }
                q.save();
                return "ok: " + (cmd.equals("skin") ? "+" : "-") + n + " " + Skins.NAME[st] + " skin(s)";
            }
            case "item": {
                int v = Integer.parseInt(a[3]);
                if (Math.abs(v) > 9999) return "! -9999 .. 9999";
                String what = a[2].toLowerCase(Locale.US).replace("_", "");
                if (what.equals("capsule") || what.equals("raidcapsule")) { q.raidCapsules = Math.max(0, q.raidCapsules + v); q.save(); return "ok: raid capsules = " + q.raidCapsules; }
                for (int m = 0; m < Defs.MOON_COUNT; m++) {
                    if (!Defs.MOON_NAME[m].replace(" ", "").equalsIgnoreCase(what)) continue;
                    q.moons[m] = Math.max(0, q.moons[m] + v);
                    for (int k = Defs.P_COUNT - 1; k >= 0 && q.moonsFree(m) < 0; k--) if (q.moonOn[k] == m) q.moonOn[k] = -1;
                    q.save();
                    return "ok: " + Defs.MOON_NAME[m] + " = " + q.moons[m];
                }
                String[] keys = {"stardust", "nebula", "catalyst", "artcapsule", "supply"};
                for (int id = 0; id < keys.length; id++) {
                    if (!what.equals(keys[id]) && !Items.NAME[id].replace(" ", "").equalsIgnoreCase(what)) continue;
                    q.items[id] = Math.max(0, q.items[id] + v);
                    q.save();
                    return "ok: " + Items.NAME[id] + " = " + q.items[id];
                }
                return "! capsule | stardust | nebula | catalyst | artcapsule | supply | hellmoon | frostmoon | stormmoon | goldmoon | voidmoon";
            }
            case "evolve": {
                int t = planetByName(a[2]);
                if (t < 0) return "! " + L.t("ไม่พบดวงดาว ", "unknown planet ") + a[2];
                if (!Defs.canEvolve(t)) return "! " + Defs.P_NAME[t] + L.t(" วิวัฒน์ไม่ได้", " has no evolution");
                boolean off = a.length > 3 && a[3].equalsIgnoreCase("off");
                if (!off && !q.owned[t]) return "! " + L.t("ยังไม่มีดาวนี้ (ใช้ planet ก่อน)", "doesn't own it (use planet first)");
                q.evolved[t] = !off;
                q.save();
                return "ok: " + Defs.P_NAME[t] + (off ? " evolution removed" : " -> " + Defs.evoName(t));
            }
            case "maxlevel": q.maxAllPlanets(); return "ok: all planets Lv 10";
            case "reset": q.reset(); return "ok: progress reset";
            default: return "! " + L.t("ไม่รู้จักคำสั่ง พิมพ์ help", "unknown command, type help");
        }
    }

    void adminDo(String msg, Runnable r) {
        r.run();
        showToast(msg);
    }

    boolean staffScreen(int s) { return s == S_ADMIN || s == S_TERM || s == S_STAFF || s == S_SERVER || s == S_LOGS; }

    // ================================================================== Stella Console look
    static final int C_BG = 0xFF0A0F1C, C_BG2 = 0xFF0F1830, C_CARD = 0xFF131C30, C_LINE = 0xFF24314F, C_TEXT = 0xFFEAF2FF,
            C_DIM = 0xFF8394B8, C_ACCENT = 0xFF34E39A;

    /** clean dashboard background: dark gradient, faint grid, one soft glow */
    void consoleBg(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(0, 0, 0, H, C_BG2, C_BG, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);
        glowCircle(c, W * 0.85f, H * 0.08f, 520 * u, 0x1834E39A);
        glowCircle(c, W * 0.1f, H * 0.9f, 520 * u, 0x14548CFF);
        sp.setPathEffect(null);
        sp.setStrokeWidth(1.2f * u);
        sp.setColor(0x10FFFFFF);
        float step = 72 * u, off = (anim * 6 * u) % step;
        for (float x = 0; x < W; x += step) c.drawLine(x, 0, x, H, sp);
        for (float y = -step + off; y < H; y += step) c.drawLine(0, y, W, y, sp);
    }

    /** flat rounded button: tinted fill, thin border, white label */
    void flatButton(Canvas c, RectF r, String label, int color, boolean enabled, Runnable a) {
        float rad = Math.min(r.height() / 2f, 22 * u);
        boolean neutral = darken(color, 0f) == color && (((color >> 16) & 255) + ((color >> 8) & 255) + (color & 255)) < 200;
        // game colours are muted toward the card colour so the console stays calm and readable
        int base = !enabled ? 0xFF1A2238 : mix(color, C_CARD, 0.45f) | 0xFF000000;
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(0, r.top, 0, r.bottom, enabled ? lighten(base, neutral ? 0.08f : 0.06f) : base, enabled ? darken(base, 0.18f) : base, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, p);
        p.setShader(null);
        sp.setPathEffect(null);
        sp.setStrokeWidth(2 * u);
        sp.setColor(enabled ? alpha(lighten(base, 0.45f), 0x99) : C_LINE);
        c.drawRoundRect(r, rad, rad, sp);
        if (label != null) {
            float ts = Math.min(r.height() * 0.38f, 40 * u);
            text(c, label, r.centerX(), r.centerY() + ts * 0.36f, ts, enabled ? 0xFFFFFFFF : 0xFF5E6B8A, Paint.Align.CENTER, r.width() - 24 * u);
        }
        if (a != null) btns.add(new Btn(new RectF(r), a));
    }

    /** flat card with a hairline border */
    void flatPanel(Canvas c, RectF r, int color, float rad) {
        rad = Math.min(rad, 28 * u);
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        // map the game's purple panel colours onto the console palette
        int fill = (((color >> 16) & 255) > 150 || ((color >> 8) & 255) > 150 || (color & 255) > 170) ? alpha(darken(color, 0.55f), 0xF0) : (color >>> 24) < 0x60 ? color : C_CARD;
        p.setColor(fill);
        c.drawRoundRect(r, rad, rad, p);
        sp.setPathEffect(null);
        sp.setStrokeWidth(2 * u);
        sp.setColor(C_LINE);
        c.drawRoundRect(r, rad, rad, sp);
    }

    /** the console logo mark: a hexagon with a prompt chevron */
    void consoleMark(Canvas c, float cx, float cy, float r) {
        Path h = new Path();
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 6 + i * Math.PI / 3;
            float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
            if (i == 0) h.moveTo(x, y); else h.lineTo(x, y);
        }
        h.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r, 0xFF34E39A, 0xFF2A7BFF, Shader.TileMode.CLAMP));
        c.drawPath(h, p);
        p.setShader(null);
        sp.setPathEffect(null);
        sp.setStrokeWidth(r * 0.16f);
        sp.setColor(0xFF0A0F1C);
        c.drawLine(cx - r * 0.42f, cy - r * 0.32f, cx - r * 0.08f, cy, sp);
        c.drawLine(cx - r * 0.08f, cy, cx - r * 0.42f, cy + r * 0.32f, sp);
        c.drawLine(cx + r * 0.02f, cy + r * 0.34f, cx + r * 0.42f, cy + r * 0.34f, sp);
    }

    /** small line icons for the console tabs: 0 players, 1 terminal, 2 server, 3 logs */
    void tabIcon(Canvas c, int k, float x, float y, float r, int col) {
        sp.setPathEffect(null);
        sp.setStrokeWidth(r * 0.17f);
        sp.setColor(col);
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(col);
        switch (k) {
            case 0:
                c.drawCircle(x, y - r * 0.35f, r * 0.32f, sp);
                rf2.set(x - r * 0.7f, y + r * 0.1f, x + r * 0.7f, y + r * 1.2f);
                c.drawArc(rf2, 180, 180, false, sp);
                break;
            case 1:
                rf2.set(x - r, y - r * 0.75f, x + r, y + r * 0.75f);
                c.drawRoundRect(rf2, r * 0.2f, r * 0.2f, sp);
                c.drawLine(x - r * 0.55f, y - r * 0.25f, x - r * 0.2f, y, sp);
                c.drawLine(x - r * 0.2f, y, x - r * 0.55f, y + r * 0.25f, sp);
                c.drawLine(x, y + r * 0.3f, x + r * 0.5f, y + r * 0.3f, sp);
                break;
            case 2:
                for (int i = 0; i < 2; i++) {
                    rf2.set(x - r, y - r * 0.8f + i * r * 0.9f, x + r, y - r * 0.1f + i * r * 0.9f);
                    c.drawRoundRect(rf2, r * 0.15f, r * 0.15f, sp);
                    c.drawCircle(x + r * 0.6f, rf2.centerY(), r * 0.1f, p);
                }
                break;
            default:
                for (int i = 0; i < 3; i++) {
                    float yy = y - r * 0.6f + i * r * 0.6f;
                    c.drawCircle(x - r * 0.8f, yy, r * 0.1f, p);
                    c.drawLine(x - r * 0.45f, yy, x + r * 0.9f, yy, sp);
                }
        }
    }

    /** header + tab row shared by every staff screen */
    void staffTop(Canvas c, String title) {
        if (consoleApp) { consoleTop(c, title); return; }
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setShader(new LinearGradient(0, 0, 0, 300 * u, 0xF0090420, 0x00090420, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, 300 * u, p);
        p.setShader(null);
        rf.set(30 * u, 50 * u, 150 * u, 150 * u);
        if (consoleApp) {
            button(c, rf, null, 0xFF6B5BD6, true, () -> Dialogs.confirm(ctx, L.t("ออกจากระบบ", "Log out"),
                    L.t("ออกจาก Stella Console ใช่ไหม?", "Log out of Stella Console?"), L.t("ออก", "Log out"), this::logout));
            sp.setPathEffect(null);
            sp.setStrokeWidth(9 * u);
            sp.setColor(0xFFFFFFFF);
            float bx = rf.centerX(), by = rf.centerY();
            rf2.set(bx - 24 * u, by - 24 * u, bx + 24 * u, by + 24 * u);
            c.drawArc(rf2, -60, 300, false, sp);
            c.drawLine(bx, by - 30 * u, bx, by - 4 * u, sp);
        } else {
            button(c, rf, null, 0xFF6B5BD6, true, () -> go(S_LOBBY));
            sp.setPathEffect(null);
            sp.setStrokeWidth(11 * u);
            sp.setColor(0xFFFFFFFF);
            float bx = rf.centerX(), by = rf.centerY();
            c.drawLine(bx + 10 * u, by - 22 * u, bx - 14 * u, by, sp);
            c.drawLine(bx - 14 * u, by, bx + 10 * u, by + 22 * u, sp);
        }
        title(c, title, 180 * u, 125 * u, 56 * u, 0xFFFFD23F, Paint.Align.LEFT, W - 560 * u);
        int r = cloud.myRole();
        pill(c, W - 200 * u, 75 * u, cloud.isConsoleOwner() ? "CONSOLE" : Roles.NAME[r], 30 * u, 0xFF140A2A, cloud.isConsoleOwner() ? 0xFF7CFF8A : Roles.COLOR[r]);
        plain(c, user, W - 200 * u, 140 * u, 28 * u, 0xFFE6E0FF, Paint.Align.CENTER, 300 * u);
        String[] tabs = {L.t("ผู้เล่น", "Players"), L.t("เทอร์มินัล", "Terminal"), L.t("เซิร์ฟเวอร์", "Server"), "Log"};
        int[] scr = {S_ADMIN, S_TERM, S_SERVER, S_LOGS};
        float tw = (W - 60 * u - 4 * 10 * u) / 5f;
        for (int i = 0; i < 5; i++) {
            final int s = scr[i];
            rf.set(30 * u + i * (tw + 10 * u), 185 * u, 30 * u + i * (tw + 10 * u) + tw, 265 * u);
            button(c, rf, tabs[i], screen == s ? 0xFFFF8A2B : 0xFF2A2350, true, () -> {
                go(s);
                if (s == S_SERVER) refreshServer();
                if (s == S_LOGS) refreshLogs();
                if (s == S_STAFF || s == S_ADMIN) refreshPlayers();
            });
        }
    }

    /** console app bar: logo, title, live status and a segmented tab bar with icons */
    void consoleTop(Canvas c, String title) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(0xF20A0F1C);
        c.drawRect(0, 0, W, 285 * u, p);
        sp.setPathEffect(null);
        sp.setStrokeWidth(2 * u);
        sp.setColor(C_LINE);
        c.drawLine(0, 285 * u, W, 285 * u, sp);
        consoleMark(c, 85 * u, 100 * u, 42 * u);
        text(c, "Stella Console", 150 * u, 92 * u, 42 * u, C_TEXT, Paint.Align.LEFT, 0);
        plain(c, title, 150 * u, 136 * u, 28 * u, C_DIM, Paint.Align.LEFT, 0);
        // live status chip
        boolean on = net == NET_ONLINE;
        String st = on ? L.t("เชื่อมต่อแล้ว", "Connected") : L.t("กำลังเชื่อมต่อ", "Connecting");
        tp.setTextSize(26 * u);
        float cw = tp.measureText(st) + 80 * u;
        rf.set(W - 40 * u - cw, 72 * u, W - 40 * u, 128 * u);
        p.setColor(on ? 0x2234E39A : 0x22FFD23F);
        c.drawRoundRect(rf, 28 * u, 28 * u, p);
        p.setColor(on ? C_ACCENT : 0xFFFFD23F);
        c.drawCircle(rf.left + 30 * u, rf.centerY(), 9 * u * (on ? 1f + 0.2f * (float) Math.sin(anim * 4) : 1f), p);
        plain(c, st, rf.left + 50 * u, rf.centerY() + 9 * u, 26 * u, on ? C_ACCENT : 0xFFFFD23F, Paint.Align.LEFT, 0);
        // segmented tabs
        String[] tabs = {L.t("ผู้เล่น", "Players"), L.t("เทอร์มินัล", "Terminal"), L.t("เซิร์ฟเวอร์", "Server"), "Log"};
        int[] scr = {S_ADMIN, S_TERM, S_SERVER, S_LOGS};
        RectF bar = new RectF(30 * u, 172 * u, W - 30 * u, 262 * u);
        p.setColor(0xFF0F1628);
        c.drawRoundRect(bar, 24 * u, 24 * u, p);
        c.drawRoundRect(bar, 24 * u, 24 * u, sp);
        float tw = bar.width() / 4f;
        for (int i = 0; i < 4; i++) {
            final int s = scr[i];
            boolean act = screen == s;
            RectF t = new RectF(bar.left + i * tw + 6 * u, bar.top + 6 * u, bar.left + (i + 1) * tw - 6 * u, bar.bottom - 6 * u);
            if (act) {
                p.setColor(0x2634E39A);
                c.drawRoundRect(t, 19 * u, 19 * u, p);
                sp.setColor(0x9934E39A);
                c.drawRoundRect(t, 19 * u, 19 * u, sp);
                sp.setColor(C_LINE);
            }
            int col = act ? C_ACCENT : C_DIM;
            tp.setTextSize(28 * u);
            float lw = tp.measureText(tabs[i]);
            float total = lw + 50 * u, x0 = t.centerX() - total / 2;
            tabIcon(c, i, x0 + 16 * u, t.centerY() - 2 * u, 16 * u, col);
            plain(c, tabs[i], x0 + 50 * u, t.centerY() + 10 * u, 28 * u, act ? C_TEXT : C_DIM, Paint.Align.LEFT, 0);
            btns.add(new Btn(t, () -> {
                go(s);
                if (s == S_SERVER) refreshServer();
                if (s == S_LOGS) refreshLogs();
                if (s == S_ADMIN) refreshPlayers();
            }));
        }
    }

    java.util.List<Cloud.Player> adminList() {
        java.util.List<Cloud.Player> list = rankList();
        if (!adminFilter.isEmpty()) {
            String f = Accounts.norm(adminFilter);
            java.util.Iterator<Cloud.Player> it = list.iterator();
            while (it.hasNext()) if (!Accounts.norm(it.next().name).contains(f)) it.remove();
        }
        java.util.Collections.sort(list, (x, y) -> y.role != x.role ? y.role - x.role : x.name.compareToIgnoreCase(y.name));
        return list;
    }

    boolean onlineNow(Cloud.Player p0) {
        return p0.uid.equals(cloud.uid) ? !consoleApp : p0.lastSeen > 0 && Math.abs(System.currentTimeMillis() - p0.lastSeen) < 60000;
    }

    void drawAdmin(Canvas c) {
        if (!isAdmin()) { screen = consoleApp ? S_ADMIN : S_LOBBY; return; }
        staffTop(c, L.t("ผู้เล่น", "Players"));
        if (adminSel == null) selectAdmin(cloud.uid);
        final int my = cloud.myRole();
        java.util.List<Cloud.Player> list = adminList();
        float y = 290 * u;
        int per = 4, pages = Math.max(1, (list.size() + per - 1) / per);
        adminPage = Math.max(0, Math.min(adminPage, pages - 1));
        rf.set(40 * u, y, 360 * u, y + 76 * u);
        button(c, rf, adminFilter.isEmpty() ? L.t("ค้นหา...", "Search...") : "\u2315 " + adminFilter, 0xFF241A4E, true, () ->
                Dialogs.ask(ctx, L.t("ค้นหาผู้เล่น", "Find a player"), L.t("พิมพ์บางส่วนของชื่อ (เว้นว่าง = ทั้งหมด)", "Part of a name (empty = everyone)"),
                        new String[]{L.t("ชื่อ", "Name")}, new int[]{Dialogs.TEXT}, new String[]{adminFilter}, "OK", v -> { adminFilter = v[0].trim(); adminPage = 0; return null; }));
        plain(c, list.size() + L.t(" คน", " players"), 380 * u, y + 50 * u, 30 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
        rf.set(W - 470 * u, y, W - 300 * u, y + 76 * u);
        button(c, rf, L.t("รีเฟรช", "Refresh"), 0xFF33A8D0, !playersLoading, this::refreshPlayers);
        rf.set(W - 290 * u, y, W - 190 * u, y + 76 * u);
        button(c, rf, "<", 0xFF4A3D8F, adminPage > 0, () -> adminPage--);
        plain(c, (adminPage + 1) + "/" + pages, W - 140 * u, y + 50 * u, 28 * u, 0xFFE6E0FF, Paint.Align.CENTER, 0);
        rf.set(W - 100 * u, y, W - 40 * u, y + 76 * u);
        button(c, rf, ">", 0xFF4A3D8F, adminPage < pages - 1, () -> adminPage++);
        y += 96 * u;
        for (int i = adminPage * per; i < Math.min(list.size(), (adminPage + 1) * per); i++) {
            final Cloud.Player a = list.get(i);
            boolean mine = java.util.Objects.equals(a.uid, cloud.uid);
            RectF r = new RectF(40 * u, y, W - 40 * u, y + 100 * u);
            panel(c, r, a.uid.equals(adminSel) ? 0xFF4A3D8F : 0xFF241A4E, 24 * u);
            avatarOf(c, r.left + 55 * u, r.centerY(), 30 * u, a.avatar, a.photo);
            if (onlineNow(a)) {
                p.setShader(null);
                p.setColor(0xFF4DFF8A);
                c.drawCircle(r.left + 78 * u, r.centerY() + 22 * u, 9 * u, p);
            }
            text(c, a.name + (mine ? L.t("  (คุณ)", "  (you)") : ""), r.left + 100 * u, r.top + 62 * u, 36 * u, 0xFFFFFFFF, Paint.Align.LEFT, 330 * u);
            float tx = r.left + 450 * u;
            if (a.role > 0) { pill(c, tx + 60 * u, r.centerY(), "Console", 24 * u, 0xFF0A0F1C, C_ACCENT); tx += 135 * u; }
            if (a.banned) pill(c, tx + 60 * u, r.centerY(), "BANNED", 24 * u, 0xFFFFFFFF, 0xFFD0304A);
            plain(c, a.stars + " Star • " + a.wins + " W", r.right - 30 * u, r.top + 60 * u, 24 * u, 0xFFFFD23F, Paint.Align.RIGHT, 0);
            btns.add(new Btn(r, () -> selectAdmin(a.uid)));
            y += 112 * u;
        }
        y = 290 * u + 96 * u + per * 112 * u + 10 * u;
        final String t = adminSel;
        final Profile q = adminProf;
        final boolean self = t != null && t.equals(cloud.uid);
        final Cloud.Player tp0 = playerById(t);
        final boolean banned = tp0 != null && tp0.banned;
        final int trole = self ? my : tp0 == null ? 0 : tp0.role;
        final boolean above = !self && trole >= my && my < Roles.OWNER; // can't act on equal or higher ranks
        text(c, L.t("จัดการ: ", "Manage: ") + nameOf(t), 50 * u, y + 40 * u, 40 * u, 0xFFFFFFFF, Paint.Align.LEFT, W - 640 * u);
        if (q != null) plain(c, q.stars + " Star • " + q.millite + " Mil • " + q.crystals + " Cry • " + q.ownedCount() + "/" + Defs.P_COUNT,
                W - 50 * u, y + 40 * u, 26 * u, 0xFFB8F6FF, Paint.Align.RIGHT, 0);
        else if (adminLoading) plain(c, L.t("กำลังโหลด...", "Loading..."), W - 50 * u, y + 40 * u, 26 * u, 0xFFB8F6FF, Paint.Align.RIGHT, 0);
        if (tp0 != null && tp0.banned && tp0.banReason != null && !tp0.banReason.isEmpty())
            plain(c, L.t("เหตุผลแบน: ", "Ban reason: ") + tp0.banReason, 50 * u, y + 80 * u, 24 * u, 0xFFFF8A8A, Paint.Align.LEFT, W - 100 * u);
        y += 95 * u;
        float bw = (W - 110 * u) / 2f, bh = 90 * u, gap = 14 * u;
        boolean edit = q != null && Roles.canEdit(my) && !above;
        boolean mod = t != null && Roles.canBan(my) && !self && !above;
        String[] labels = {L.t("ตั้ง Star", "Set Star"), L.t("ตั้ง Millite", "Set Millite"), L.t("ตั้ง Crystal", "Set Crystal"),
                L.t("ให้ / แก้ดวงดาว", "Give / edit planet"), banned ? L.t("ปลดแบน", "Unban") : L.t("แบน...", "Ban..."), L.t("เตะออก", "Kick"),
                L.t("Lv 10 ทุกดวง", "All planets Lv 10"), L.t("ปลดล็อกทุกด่าน", "Unlock all stages"),
                L.t("ตั้ง Wins", "Set wins"), L.t("ตั้ง Crystal Oil", "Set Crystal Oil"), L.t("ให้สกิน / ไอเทม", "Give skin / item"),
                L.t("รีเซ็ตความคืบหน้า", "Reset progress"), L.t("ลบข้อมูลผู้เล่น", "Delete player data")};
        int[] colors = {0xFFE0A020, 0xFFB86BFF, 0xFF33A8D0, 0xFF33C6A6, 0xFFFF6B5B, 0xFFFF8A2B, 0xFF4F8BFF, 0xFF33C6A6, 0xFF33C6A6,
                0xFFFF9A3B, 0xFFFF3B6B, 0xFFFF6B5B, 0xFFD0304A};
        boolean[] en = {edit, edit, edit, edit, mod, mod && onlineNow(tp0 == null ? new Cloud.Player() : tp0) || mod, edit, edit, edit, edit, edit, edit, edit && !self};
        Runnable[] acts = {
                () -> setAmount("Star", q.stars, v -> q.stars = v),
                () -> setAmount("Millite", q.millite, v -> q.millite = v),
                () -> setAmount("Crystal", q.crystals, v -> q.crystals = v),
                () -> { givePick = -1; popup = P_GIVE; },
                () -> { if (banned) unbanPlayer(t); else askBan(t); },
                () -> cloud.kick(t, nameOf(t), (ok, err) -> showToast(err != null ? err : nameOf(t) + L.t(" ถูกเตะออกแล้ว", " was kicked"))),
                () -> { q.maxAllPlanets(); commitAdmin(L.t("Lv 10 ทุกดวงแล้ว", "All planets Lv 10")); },
                () -> { q.unlockAllStages(); commitAdmin(L.t("ปลดล็อกทุกด่านแล้ว", "All stages unlocked")); },
                () -> setAmount("Wins", q.wins, v -> q.wins = v),
                () -> setAmount("Crystal Oil", q.oil, v -> q.oil = v),
                () -> popup = P_ITEMS,
                () -> Dialogs.confirm(ctx, L.t("รีเซ็ตความคืบหน้า", "Reset progress"),
                        L.t("ลบความคืบหน้าทั้งหมดของ ", "Erase all progress of ") + nameOf(t) + L.t(" ใช่ไหม? ย้อนกลับไม่ได้", "? This can't be undone."),
                        L.t("รีเซ็ต", "Reset"), () -> { q.reset(); commitAdmin(L.t("รีเซ็ตแล้ว", "Progress reset")); }),
                () -> Dialogs.confirm(ctx, L.t("ลบข้อมูลผู้เล่น", "Delete player data"),
                        L.t("ลบข้อมูลและแบน ", "Delete the data of and ban ") + nameOf(t) + L.t(" ใช่ไหม? ย้อนกลับไม่ได้", "? This can't be undone."),
                        L.t("ลบ", "Delete"), () -> cloud.wipe(t, (ok, err) -> {
                            showToast(err != null ? err : L.t("ลบข้อมูลแล้ว", "Player data deleted"));
                            selectAdmin(cloud.uid);
                            refreshPlayers();
                        }))};
        for (int i = 0; i < labels.length; i++) {
            float x = 40 * u + (i % 2) * (bw + 30 * u), yy = y + (i / 2) * (bh + gap);
            rf.set(x, yy, x + bw, yy + bh);
            button(c, rf, labels[i], colors[i], en[i], acts[i]);
        }
        y += ((labels.length + 1) / 2) * (bh + gap) + 10 * u;
        if (!Roles.canEdit(my)) plain(c, L.t("ยศ ", "Rank ") + Roles.NAME[my] + L.t(" ดูข้อมูลได้", " can view") + (Roles.canBan(my) ? L.t(" + แบน/เตะ", " + ban/kick") : ""),
                W / 2, y + 30 * u, 28 * u, 0xFF9C94C8, Paint.Align.CENTER, 0);
        else if (!consoleApp) {
            boolean inf = acc.infiniteSP();
            rf.set(40 * u, y, W - 40 * u, y + 85 * u);
            button(c, rf, L.t("SP ไม่จำกัด (ตอนเล่นเอง): ", "Infinite SP (when I play): ") + (inf ? "ON" : "OFF"), inf ? 0xFFFF8A2B : 0xFF3A3360, true, () -> acc.setInfiniteSP(!acc.infiniteSP()));
        }
    }

    int giveStyle, giveSkinPlanet = -1;

    /** staff: give skins (one planet or every planet the style fits) and items to the selected player */
    void drawGiveItems(Canvas c) {
        final Profile q = adminProf;
        if (q == null || !isAdmin() || !Roles.canEdit(cloud.myRole())) { popup = P_NONE; return; }
        RectF r = new RectF(30 * u, 110 * u, W - 30 * u, H - 110 * u);
        popupFrame(c, r, L.t("ให้สกิน / ไอเทม: ", "Give skin / item: ") + nameOf(adminSel));
        float y = r.top + 150 * u;
        text(c, L.t("สกิน (สไตล์)", "Skin style"), r.left + 40 * u, y + 30 * u, 34 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
        y += 50 * u;
        float cw = (r.width() - 80 * u - 3 * 12 * u) / 4f, chh = 70 * u;
        for (int s = 0; s < Skins.STYLES; s++) {
            final int ss = s;
            float x = r.left + 40 * u + (s % 4) * (cw + 12 * u), yy = y + (s / 4) * (chh + 12 * u);
            rf.set(x, yy, x + cw, yy + chh);
            button(c, rf, s == Skins.ANNIV ? "1st Anniv" : L.en ? Skins.NAME[s] : Skins.NAME_TH[s], giveStyle == s ? Defs.RARITY_COLOR[Skins.TIER[s]] : 0xFF3A3360, true, () -> {
                giveStyle = ss;
                if (giveSkinPlanet >= 0 && !Skins.fits(ss, giveSkinPlanet)) giveSkinPlanet = -1;
            });
        }
        y += 3 * (chh + 12 * u) + 20 * u;
        text(c, L.t("ดวงดาว", "Planet"), r.left + 40 * u, y + 30 * u, 34 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
        y += 50 * u;
        float cell = (r.width() - 80 * u) / 7f;
        for (int k = -1; k < Defs.P_COUNT; k++) {
            final int pt = k;
            int slot = k + 1;
            float x = r.left + 40 * u + (slot % 7) * cell, yy = y + (slot / 7) * cell;
            RectF cr = new RectF(x + 4 * u, yy + 4 * u, x + cell - 4 * u, yy + cell - 4 * u);
            boolean fits = k < 0 || Skins.fits(giveStyle, k);
            rarityCard(c, cr, k < 0 ? 0xFFFF3B6B : Defs.RARITY_COLOR[Defs.P_RARITY[k]], fits, giveSkinPlanet == k, 16 * u);
            if (k < 0) text(c, L.t("ทุกดวง", "All"), cr.centerX(), cr.centerY() + 10 * u, 26 * u, 0xFFFFFFFF, Paint.Align.CENTER, cell);
            else {
                sprite(c, skinBmp(k, fits ? giveStyle : -1), cr.centerX(), cr.centerY(), cell * 0.34f, fits ? 255 : 50);
                if (q.skins.contains(Skins.key(giveStyle, k))) star(c, cr.right - 14 * u, cr.top + 14 * u, 10 * u);
            }
            if (fits) btns.add(new Btn(cr, () -> giveSkinPlanet = pt));
        }
        y += 3 * cell + 20 * u;
        float bw = (r.width() - 110 * u) / 2f, bh = 86 * u;
        rf.set(r.left + 40 * u, y, r.left + 40 * u + bw, y + bh);
        button(c, rf, L.t("ให้สกิน", "Give skin"), 0xFF33C6A6, true, () -> {
            int n = 0;
            for (int k = 0; k < Defs.P_COUNT; k++)
                if ((giveSkinPlanet < 0 || giveSkinPlanet == k) && Skins.fits(giveStyle, k) && q.skins.add(Skins.key(giveStyle, k))) n++;
            commitAdmin(L.t("ให้สกิน ", "Gave ") + n + L.t(" ชิ้น", " skin(s)"));
        });
        rf.set(r.left + 70 * u + bw, y, r.left + 70 * u + bw * 2, y + bh);
        button(c, rf, L.t("ลบสกิน", "Remove skin"), 0xFFFF6B5B, true, () -> {
            int n = 0;
            for (int k = 0; k < Defs.P_COUNT; k++)
                if ((giveSkinPlanet < 0 || giveSkinPlanet == k) && q.skins.remove(Skins.key(giveStyle, k))) {
                    n++;
                    if (q.skinOn[k] == giveStyle) q.skinOn[k] = -1;
                }
            commitAdmin(L.t("ลบสกิน ", "Removed ") + n + L.t(" ชิ้น", " skin(s)"));
        });
        y += bh + 40 * u;
        text(c, L.t("ไอเทม / Artifact", "Items / Artifacts"), r.left + 40 * u, y + 30 * u, 34 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
        y += 60 * u;
        // every item as a compact two-column grid: icon, name, count, tap to set
        int n = 1 + Defs.MOON_COUNT + Items.COUNT;
        float colW = (r.width() - 100 * u) / 2f, cellH = 82 * u;
        for (int k = 0; k < n; k++) {
            float x = r.left + 40 * u + (k % 2) * (colW + 20 * u), yy = y + (k / 2) * (cellH + 10 * u);
            RectF ic = new RectF(x, yy, x + colW, yy + cellH);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x33FFFFFF);
            c.drawRoundRect(ic, 16 * u, 16 * u, p);
            float ix = ic.left + 45 * u, iy = ic.centerY();
            String nm;
            int have;
            Runnable set;
            if (k == 0) {
                capsuleIcon(c, ix, iy, 26 * u);
                nm = "Raid Capsule";
                have = q.raidCapsules;
                set = () -> setAmount("Raid Capsule", q.raidCapsules, v -> q.raidCapsules = v);
            } else if (k <= Defs.MOON_COUNT) {
                final int mm = k - 1;
                sprite(c, moonBmp(mm), ix, iy, 22 * u, 255);
                nm = Defs.MOON_NAME[mm];
                have = q.moons[mm];
                set = () -> setAmount(Defs.MOON_NAME[mm], q.moons[mm], v -> {
                    q.moons[mm] = v;
                    for (int t2 = Defs.P_COUNT - 1; t2 >= 0 && q.moonsFree(mm) < 0; t2--) if (q.moonOn[t2] == mm) q.moonOn[t2] = -1;
                });
            } else {
                final int id = k - 1 - Defs.MOON_COUNT;
                itemIcon(c, id, ix, iy, 26 * u);
                nm = Items.NAME[id];
                have = q.items[id];
                set = () -> setAmount(Items.NAME[id], q.items[id], v -> q.items[id] = v);
            }
            plain(c, nm, ic.left + 85 * u, iy - 4 * u, 24 * u, 0xFFFFFFFF, Paint.Align.LEFT, colW - 190 * u);
            plain(c, "x" + have, ic.left + 85 * u, iy + 26 * u, 22 * u, 0xFFFFE27A, Paint.Align.LEFT, 0);
            rf.set(ic.right - 100 * u, ic.top + 12 * u, ic.right - 12 * u, ic.bottom - 12 * u);
            button(c, rf, L.t("ตั้ง", "Set"), 0xFF4F8BFF, true, set);
        }
        rf.set(r.centerX() - 200 * u, r.bottom - 120 * u, r.centerX() + 200 * u, r.bottom - 30 * u);
        button(c, rf, L.t("ปิด", "Close"), 0xFF6B5BD6, true, () -> popup = P_NONE);
    }

    void askBan(String t) {
        Dialogs.ask(ctx, L.t("แบน ", "Ban ") + nameOf(t), L.t("ชั่วโมง: 0 = ถาวร", "Hours: 0 = permanent"),
                new String[]{L.t("เหตุผล", "Reason"), L.t("จำนวนชั่วโมง", "Hours")}, new int[]{Dialogs.TEXT, Dialogs.NUMBER},
                new String[]{"", "0"}, L.t("แบน", "Ban"), v -> {
                    int h;
                    try { h = Integer.parseInt(v[1].trim().isEmpty() ? "0" : v[1].trim()); } catch (NumberFormatException e) { return L.t("ชั่วโมงต้องเป็นตัวเลข", "Hours must be a number"); }
                    if (h < 0 || h > 87600) return L.t("ใส่ 0 ถึง 87600", "Use 0 to 87600");
                    cloud.setBan(t, nameOf(t), true, v[0].trim(), h, (ok, err) -> {
                        showToast(err != null ? err : nameOf(t) + L.t(" ถูกแบนแล้ว", " banned"));
                        refreshPlayers();
                    });
                    return null;
                });
    }

    void unbanPlayer(String t) {
        cloud.setBan(t, nameOf(t), false, null, 0, (ok, err) -> {
            showToast(err != null ? err : nameOf(t) + L.t(" ถูกปลดแบนแล้ว", " unbanned"));
            refreshPlayers();
        });
    }

    /** pick a rank for roleTarget */
    void drawRolePick(Canvas c) {
        final String t = roleTarget;
        if (t == null) { popup = P_NONE; return; }
        Cloud.Player tp0 = playerById(t);
        final int from = tp0 == null ? 0 : tp0.role, my = cloud.myRole();
        RectF r = new RectF(80 * u, H / 2 - 600 * u, W - 80 * u, H / 2 + 600 * u);
        popupFrame(c, r, L.t("ยศของ ", "Rank of ") + nameOf(t));
        String[] what = {L.t("ผู้เล่นทั่วไป", "Regular player"), L.t("ดูผู้เล่น โปรไฟล์ และ Log", "View players, profiles and logs"),
                L.t("+ แบน / ปลดแบน / เตะ", "+ ban / unban / kick"), L.t("+ แก้ข้อมูลผู้เล่นและดวงดาว", "+ edit players and planets"),
                L.t("+ ตั้งค่าเซิร์ฟเวอร์", "+ server settings"), L.t("ทำได้ทุกอย่าง (ยกเว้นให้ยศ)", "Everything except giving ranks")};
        float y = r.top + 150 * u, rh = 140 * u;
        for (int k = 0; k <= Roles.OWNER; k++) {
            final int to = k;
            boolean ok = Roles.canAssign(cloud.isConsoleOwner()) && to != from;
            RectF b = new RectF(r.left + 40 * u, y, r.right - 40 * u, y + rh - 16 * u);
            rarityCard(c, b, Roles.COLOR[k], ok || k == from, k == from, 26 * u);
            title(c, Roles.NAME[k], b.left + 40 * u, b.top + 62 * u, 42 * u, ok || k == from ? Roles.COLOR[k] : 0xFF6A6488, Paint.Align.LEFT, 0);
            plain(c, what[k], b.left + 40 * u, b.top + 104 * u, 26 * u, 0xFFC9C2E6, Paint.Align.LEFT, b.width() - 80 * u);
            if (k == from) plain(c, L.t("ปัจจุบัน", "current"), b.right - 30 * u, b.top + 62 * u, 26 * u, 0xFFFFD23F, Paint.Align.RIGHT, 0);
            if (ok) btns.add(new Btn(b, () -> Dialogs.confirm(ctx, L.t("เปลี่ยนยศ", "Change rank"),
                    nameOf(t) + ": " + Roles.NAME[from] + " -> " + Roles.NAME[to], L.t("ยืนยัน", "Confirm"),
                    () -> cloud.setRole(t, nameOf(t), to, (ok2, err) -> {
                        showToast(err != null ? err : nameOf(t) + " = " + Roles.NAME[to]);
                        popup = P_NONE;
                        refreshPlayers();
                    }))));
            y += rh;
        }
        rf.set(r.centerX() - 200 * u, r.bottom - 120 * u, r.centerX() + 200 * u, r.bottom - 35 * u);
        button(c, rf, L.t("ปิด", "Close"), 0xFF6B5BD6, true, () -> popup = P_NONE);
    }

    // ------------------------------------------------------------------ console screen
    void drawTerm(Canvas c) {
        if (!isAdmin()) { screen = consoleApp ? S_ADMIN : S_LOBBY; return; }
        staffTop(c, L.t("คอนโซล", "Console"));
        float bottom = H - 60 * u;
        RectF box = new RectF(40 * u, 290 * u, W - 40 * u, bottom - 330 * u);
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(0xF005020F);
        c.drawRoundRect(box, 24 * u, 24 * u, p);
        sp.setPathEffect(null);
        sp.setStrokeWidth(3 * u);
        sp.setColor(0xFF2E9A6A);
        c.drawRoundRect(box, 24 * u, 24 * u, sp);
        // faint scanlines for a terminal feel
        p.setColor(0x0A7CFF8A);
        for (float yy = box.top + 10 * u; yy < box.bottom; yy += 8 * u) c.drawRect(box.left + 6 * u, yy, box.right - 6 * u, yy + 2 * u, p);
        float lh = 44 * u;
        int maxLines = (int) ((box.height() - 40 * u) / lh);
        int start = Math.max(0, consoleLog.size() - maxLines);
        float y = box.top + 50 * u;
        if (consoleLog.isEmpty()) plain(c, L.t("พิมพ์ help เพื่อดูคำสั่งทั้งหมด", "Type help to list every command"), box.left + 24 * u, y, 30 * u, 0xFF7CFF8A, Paint.Align.LEFT, box.width() - 48 * u);
        for (int i = start; i < consoleLog.size(); i++) {
            String line = consoleLog.get(i);
            int col = line.startsWith(">") ? 0xFFFFE27A : line.startsWith("!") ? 0xFFFF8A8A : line.startsWith("#") ? 0xFF7DD3FF : 0xFF9CFFB8;
            plain(c, line, box.left + 24 * u, y, 27 * u, col, Paint.Align.LEFT, box.width() - 48 * u);
            y += lh;
        }
        if (((int) (anim * 2)) % 2 == 0) plain(c, "_", box.left + 24 * u, y, 30 * u, 0xFF7CFF8A, Paint.Align.LEFT, 0);
        // quick commands
        String[] quick = {"help", "list", "online", "logs 15", "server"};
        float qw = (W - 80 * u - 5 * 10 * u) / 6f, qy = bottom - 310 * u;
        for (int i = 0; i < quick.length; i++) {
            final String qc = quick[i];
            rf.set(40 * u + i * (qw + 10 * u), qy, 40 * u + i * (qw + 10 * u) + qw, qy + 80 * u);
            button(c, rf, qc, 0xFF2A2350, true, () -> runCommand(qc));
        }
        float bw = (W - 110 * u) / 2f;
        rf.set(40 * u, bottom - 200 * u, 40 * u + bw, bottom - 80 * u);
        button(c, rf, L.t("พิมพ์คำสั่ง", "Type command"), 0xFF2E9A6A, true, this::askCommand);
        rf.set(70 * u + bw, bottom - 200 * u, W - 40 * u, bottom - 80 * u);
        button(c, rf, L.t("รันซ้ำ", "Run again"), 0xFF4F8BFF, lastCommand != null, () -> { if (lastCommand != null) runCommand(lastCommand); });
    }

    // ------------------------------------------------------------------ staff list
    void drawStaff(Canvas c) {
        if (!isAdmin()) { screen = consoleApp ? S_ADMIN : S_LOBBY; return; }
        staffTop(c, L.t("ทีมงาน", "Staff"));
        java.util.List<Cloud.Player> list = new java.util.ArrayList<>();
        for (Cloud.Player x : rankList()) if (x.role > 0) list.add(x);
        java.util.Collections.sort(list, (x, y2) -> y2.role != x.role ? y2.role - x.role : x.name.compareToIgnoreCase(y2.name));
        float y = 300 * u;
        final int my = cloud.myRole();
        rf.set(40 * u, y, W - 40 * u, y + 95 * u);
        button(c, rf, cloud.isConsoleOwner() ? L.t("+ มอบยศให้ผู้เล่น", "+ Give a player a rank") : L.t("มอบยศได้จาก Stella Console เท่านั้น", "Ranks are given only from Stella Console"),
                0xFF33C6A6, cloud.isConsoleOwner(), () ->
                Dialogs.ask(ctx, L.t("มอบยศ", "Give rank"), L.t("ชื่อไอดีของผู้เล่น", "The player's username"), new String[]{L.t("ชื่อ", "Name")},
                        new int[]{Dialogs.TEXT}, null, "OK", v -> {
                            Cloud.Player x = playerByName(v[0]);
                            if (x == null) return L.t("ไม่พบผู้เล่นนี้ (ลองรีเฟรช)", "No such player (try refresh)");
                            roleTarget = x.uid;
                            popup = P_ROLE;
                            return null;
                        }));
        y += 120 * u;
        for (Cloud.Player x : list) {
            final Cloud.Player a = x;
            RectF r = new RectF(40 * u, y, W - 40 * u, y + 110 * u);
            panel(c, r, 0xFF241A4E, 24 * u);
            p.setShader(null);
            p.setColor(Roles.COLOR[a.role]);
            rf2.set(r.left, r.top + 18 * u, r.left + 12 * u, r.bottom - 18 * u);
            c.drawRoundRect(rf2, 6 * u, 6 * u, p);
            avatarOf(c, r.left + 70 * u, r.centerY(), 34 * u, a.avatar, a.photo);
            text(c, a.name, r.left + 125 * u, r.top + 66 * u, 38 * u, 0xFFFFFFFF, Paint.Align.LEFT, 380 * u);
            pill(c, r.right - 330 * u, r.centerY(), Roles.NAME[a.role], 28 * u, 0xFF140A2A, Roles.COLOR[a.role]);
            boolean can = cloud.isConsoleOwner() && !java.util.Objects.equals(a.uid, cloud.uid);
            rf.set(r.right - 200 * u, r.top + 18 * u, r.right - 24 * u, r.bottom - 18 * u);
            button(c, rf, L.t("เปลี่ยน", "Change"), 0xFF6B5BD6, can, () -> { roleTarget = a.uid; popup = P_ROLE; });
            y += 124 * u;
            if (y > H - 520 * u) break;
        }
        // what each rank can do
        float ly = H - 470 * u;
        RectF lg = new RectF(40 * u, ly, W - 40 * u, H - 50 * u);
        panel(c, lg, 0xEE1C1240, 30 * u);
        String[] can = {"", L.t("ดูผู้เล่น / โปรไฟล์ / Log", "view players / profiles / logs"), L.t("+ แบน ปลดแบน เตะ", "+ ban, unban, kick"),
                L.t("+ แก้ข้อมูลผู้เล่น, แก้ดวงดาว, ลบ", "+ edit players and planets, wipe"), L.t("+ ตั้งค่าเซิร์ฟเวอร์", "+ server settings"),
                L.t("ทำได้ทุกอย่าง (ยกเว้นให้ยศ)", "everything (except giving ranks)")};
        for (int k = Roles.OWNER; k >= Roles.STAFF; k--) {
            float yy = ly + 60 * u + (Roles.OWNER - k) * 76 * u;
            pill(c, lg.left + 110 * u, yy - 10 * u, Roles.NAME[k], 26 * u, 0xFF140A2A, Roles.COLOR[k]);
            plain(c, can[k], lg.left + 210 * u, yy, 27 * u, 0xFFE6E0FF, Paint.Align.LEFT, lg.width() - 240 * u);
        }
    }

    // ------------------------------------------------------------------ server settings
    void refreshServer() {
        if (serverLoading) return;
        serverLoading = true;
        cloud.server((s, err) -> {
            serverLoading = false;
            if (err != null) { showToast(err); return; }
            serverInfo = s;
        });
    }

    void drawServer(Canvas c) {
        if (!isAdmin()) { screen = consoleApp ? S_ADMIN : S_LOBBY; return; }
        staffTop(c, L.t("เซิร์ฟเวอร์", "Server"));
        if (serverInfo == null && !serverLoading) refreshServer();
        final Cloud.Server s = serverInfo;
        final boolean dev = Roles.canServer(cloud.myRole());
        float y = 300 * u;
        RectF st = new RectF(40 * u, y, W - 40 * u, y + 260 * u);
        panel(c, st, 0xEE1C1240, 34 * u);
        boolean maint = s != null && s.maintenance;
        int sc = maint ? 0xFFFFB627 : 0xFF4DFF8A;
        float pulse = 0.5f + 0.5f * (float) Math.sin(anim * 4);
        p.setShader(null);
        p.setColor(alpha(sc, (int) (80 + 100 * pulse)));
        c.drawCircle(st.left + 80 * u, st.top + 90 * u, 30 * u, p);
        p.setColor(sc);
        c.drawCircle(st.left + 80 * u, st.top + 90 * u, 16 * u, p);
        title(c, s == null ? L.t("กำลังโหลด...", "Loading...") : maint ? L.t("ปิดปรับปรุง", "Maintenance") : L.t("ออนไลน์", "Online"),
                st.left + 140 * u, st.top + 108 * u, 52 * u, sc, Paint.Align.LEFT, 0);
        if (s != null) {
            plain(c, L.t("ผู้เล่นออนไลน์: ", "Players online: ") + s.online + "   •   " + L.t("เวอร์ชันต่ำสุด: ", "Min version: ") + (s.minVersion <= 0 ? L.t("ไม่บังคับ", "none") : String.valueOf(s.minVersion))
                    + "   •   " + L.t("เกมนี้: ", "This build: ") + VERSION_CODE, st.left + 50 * u, st.top + 175 * u, 28 * u, 0xFFE6E0FF, Paint.Align.LEFT, st.width() - 100 * u);
            plain(c, L.t("ประกาศ: ", "Broadcast: ") + (s.broadcast == null ? "-" : s.broadcast), st.left + 50 * u, st.top + 225 * u, 28 * u, 0xFFB8F6FF, Paint.Align.LEFT, st.width() - 100 * u);
        }
        y += 290 * u;
        float bh = 110 * u;
        rf.set(40 * u, y, W - 40 * u, y + bh);
        button(c, rf, maint ? L.t("เปิดเซิร์ฟเวอร์ (ปิดโหมดปรับปรุง)", "Open the server (end maintenance)") : L.t("ปิดปรับปรุงเซิร์ฟเวอร์", "Start maintenance"),
                maint ? 0xFF2E9A6A : 0xFFE0A020, dev && s != null, () -> Dialogs.confirm(ctx, L.t("โหมดปรับปรุง", "Maintenance"),
                        maint ? L.t("ให้ผู้เล่นกลับเข้าเกมได้ใช่ไหม?", "Let players back in?") : L.t("ผู้เล่นทั่วไปจะเข้าเกมไม่ได้ (ทีมงานยังเข้าได้) ใช่ไหม?", "Regular players will be locked out (staff can still play). Continue?"),
                        L.t("ยืนยัน", "Confirm"), () -> cloud.setMaintenance(!maint, (ok, err) -> { showToast(err != null ? err : "OK"); refreshServer(); })));
        y += bh + 20 * u;
        rf.set(40 * u, y, W - 40 * u, y + bh);
        button(c, rf, L.t("ส่งประกาศถึงผู้เล่นทุกคน", "Send a broadcast to everyone"), 0xFF4F8BFF, dev, () ->
                Dialogs.ask(ctx, L.t("ประกาศ", "Broadcast"), L.t("ขึ้นเป็นแถบด้านบนของเกมผู้เล่นทุกคน (24 ชม.)", "Shows as a banner in every player's game (24 h)"),
                        new String[]{L.t("ข้อความ", "Message")}, new int[]{Dialogs.TEXT}, null, L.t("ส่ง", "Send"), v -> {
                            if (v[0].trim().isEmpty()) return L.t("พิมพ์ข้อความก่อน", "Type a message first");
                            cloud.setBroadcast(v[0].trim(), (ok, err) -> { showToast(err != null ? err : L.t("ส่งประกาศแล้ว", "Broadcast sent")); refreshServer(); });
                            return null;
                        }));
        y += bh + 20 * u;
        float bw = (W - 110 * u) / 2f;
        rf.set(40 * u, y, 40 * u + bw, y + bh);
        button(c, rf, L.t("ลบประกาศ", "Clear broadcast"), 0xFF6B5BD6, dev && s != null && s.broadcast != null,
                () -> cloud.setBroadcast(null, (ok, err) -> { showToast(err != null ? err : "OK"); refreshServer(); }));
        rf.set(70 * u + bw, y, W - 40 * u, y + bh);
        button(c, rf, L.t("บังคับอัปเดต...", "Force update..."), 0xFFB86BFF, dev, () ->
                Dialogs.ask(ctx, L.t("เวอร์ชันต่ำสุด", "Minimum version"), L.t("versionCode ที่ต่ำกว่านี้เล่นไม่ได้ (0 = ไม่บังคับ) • เกมนี้ = ", "Older versionCodes can't play (0 = off) • this build = ") + VERSION_CODE,
                        new String[]{"versionCode"}, new int[]{Dialogs.NUMBER}, new String[]{s == null ? "0" : String.valueOf(s.minVersion)}, "OK", v -> {
                            try {
                                int mv = Integer.parseInt(v[0].trim());
                                cloud.setMinVersion(mv, (ok, err) -> { showToast(err != null ? err : "min version = " + mv); refreshServer(); });
                                return null;
                            } catch (NumberFormatException e) { return L.t("ใส่ตัวเลข", "Numbers only"); }
                        }));
        y += bh + 20 * u;
        rf.set(40 * u, y, W - 40 * u, y + bh);
        button(c, rf, L.t("รีเฟรช", "Refresh"), 0xFF33A8D0, !serverLoading, this::refreshServer);
        if (consoleApp) {
            // the console stays unlocked on this device; locking it asks for the password again
            rf.set(40 * u, navTopOr(H) - 150 * u, W - 40 * u, navTopOr(H) - 60 * u);
            button(c, rf, L.t("ล็อก Console บนเครื่องนี้", "Lock Console on this device"), 0xFF5A1E2E, true, () -> Dialogs.confirm(ctx,
                    L.t("ล็อก Console", "Lock Console"), L.t("ครั้งหน้าต้องใส่รหัสผ่านอีกครั้ง", "You'll need the password again next time"),
                    L.t("ล็อก", "Lock"), this::logout));
        }
    }

    float navTopOr(float h) { return h; }

    // ------------------------------------------------------------------ audit log
    void refreshLogs() {
        if (logsLoading) return;
        logsLoading = true;
        cloud.logs(60, (list, err) -> {
            logsLoading = false;
            if (err != null) { showToast(err); return; }
            logLines = list;
        });
    }

    void drawLogs(Canvas c) {
        if (!isAdmin()) { screen = consoleApp ? S_ADMIN : S_LOBBY; return; }
        staffTop(c, "Log");
        if (logLines.isEmpty() && !logsLoading && transT < 0.05f) refreshLogs();
        float y = 300 * u;
        rf.set(W - 260 * u, y, W - 40 * u, y + 76 * u);
        button(c, rf, L.t("รีเฟรช", "Refresh"), 0xFF33A8D0, !logsLoading, this::refreshLogs);
        plain(c, L.t("ทุกการกระทำของทีมงาน (ล่าสุดก่อน)", "Every staff action (newest first)"), 50 * u, y + 50 * u, 28 * u, 0xFFC9C2E6, Paint.Align.LEFT, 0);
        y += 100 * u;
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("d MMM HH:mm", Locale.US);
        if (logLines.isEmpty()) plain(c, logsLoading ? L.t("กำลังโหลด...", "Loading...") : L.t("ยังไม่มี Log", "No log entries yet"), W / 2, y + 80 * u, 32 * u, 0xFF9C94C8, Paint.Align.CENTER, 0);
        for (Cloud.LogLine l : logLines) {
            RectF r = new RectF(40 * u, y, W - 40 * u, y + 96 * u);
            panel(c, r, 0xCC1C1240, 20 * u);
            plain(c, f.format(new java.util.Date(l.t)), r.left + 24 * u, r.top + 40 * u, 24 * u, 0xFF9C94C8, Paint.Align.LEFT, 0);
            plain(c, l.by, r.left + 24 * u, r.top + 78 * u, 26 * u, 0xFFFFD23F, Paint.Align.LEFT, 200 * u);
            plain(c, l.msg, r.left + 240 * u, r.top + 60 * u, 28 * u, 0xFFFFFFFF, Paint.Align.LEFT, r.width() - 270 * u);
            y += 106 * u;
            if (y > H - 120 * u) break;
        }
    }

    // ------------------------------------------------------------------ broadcast banner / ticker
    void drawBroadcast(Canvas c) {
        float k = Math.min(1f, Math.min(bcShowT, 9f - bcShowT) * 3f);
        float top = -170 * u + k * 230 * u;
        RectF r = new RectF(40 * u, top, W - 40 * u, top + 150 * u);
        panel(c, r, 0xF0281048, 34 * u);
        sp.setPathEffect(null);
        sp.setStrokeWidth(4 * u);
        sp.setColor(0xFFFFD23F);
        c.drawRoundRect(r, 34 * u, 34 * u, sp);
        megaphone(c, r.left + 80 * u, r.centerY(), 36 * u);
        plain(c, L.t("ประกาศจากทีมงาน", "Announcement"), r.left + 150 * u, r.top + 55 * u, 26 * u, 0xFFFFD23F, Paint.Align.LEFT, 0);
        plain(c, bcMsg, r.left + 150 * u, r.top + 108 * u, 32 * u, 0xFFFFFFFF, Paint.Align.LEFT, r.width() - 190 * u);
        btns.add(new Btn(r, () -> bcShowT = 0.3f));
    }

    void ticker(Canvas c, float y) {
        RectF r = new RectF(60 * u, y - 30 * u, W - 60 * u, y + 30 * u);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xAA281048);
        c.drawRoundRect(r, 30 * u, 30 * u, p);
        megaphone(c, r.left + 40 * u, y, 16 * u);
        c.save();
        c.clipRect(r.left + 75 * u, r.top, r.right - 20 * u, r.bottom);
        tp.setTextSize(28 * u);
        tp.setTypeface(bodyFont);
        float w = tp.measureText(bcMsg) + 200 * u;
        float span = r.width() - 95 * u;
        float off = (anim * 110 * u) % (w + span);
        plain(c, bcMsg, r.right - 20 * u - off, y + 10 * u, 28 * u, 0xFFFFE27A, Paint.Align.LEFT, 0);
        c.restore();
        btns.add(new Btn(r, () -> bcShowT = 9f));
    }

    void megaphone(Canvas c, float x, float y, float r) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFD23F);
        Path m = new Path();
        m.moveTo(x - r * 0.9f, y - r * 0.35f);
        m.lineTo(x + r * 0.6f, y - r);
        m.lineTo(x + r * 0.6f, y + r);
        m.lineTo(x - r * 0.9f, y + r * 0.35f);
        m.close();
        c.drawPath(m, p);
        sp.setPathEffect(null);
        sp.setStrokeCap(Paint.Cap.ROUND);
        sp.setStrokeWidth(r * 0.18f);
        sp.setColor(0xFFFFD23F);
        rf2.set(x + r * 0.3f, y - r * 0.9f, x + r * 1.3f, y + r * 0.9f);
        c.drawArc(rf2, -50, 100, false, sp);
    }

    void wrench(Canvas c, float x, float y, float r) {
        c.save();
        c.rotate(-40 + 8 * (float) Math.sin(anim * 3), x, y);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFE27A);
        rf2.set(x - r * 0.16f, y - r * 0.2f, x + r * 0.16f, y + r);
        c.drawRoundRect(rf2, r * 0.1f, r * 0.1f, p);
        c.drawCircle(x, y - r * 0.45f, r * 0.42f, p);
        p.setColor(0xFF1C1240);
        rf2.set(x - r * 0.14f, y - r * 0.95f, x + r * 0.14f, y - r * 0.45f);
        c.drawRect(rf2, p);
        c.restore();
    }

    /** create the console account once (fails if it already exists, so nobody can take it over later) */
    void askConsoleSetup() {
        Dialogs.ask(ctx, L.t("ตั้งรหัส Console ครั้งแรก", "First-time console setup"),
                L.t("สร้างบัญชี \"console\" ได้ครั้งเดียว ใครสร้างก่อนคนนั้นได้เป็นเจ้าของ จึงควรทำทันทีหลังตั้งค่า Firebase", "The \"console\" account can be created only once - whoever creates it first owns it, so do it right after setting up Firebase"),
                new String[]{L.t("รหัสผ่านใหม่ (6 ตัวขึ้นไป)", "New password (6+)"), L.t("ยืนยันรหัสผ่าน", "Confirm password")},
                new int[]{Dialogs.PASSWORD, Dialogs.PASSWORD}, null, L.t("สร้าง", "Create"), v -> {
                    if (v[0].length() < 6) return L.t("รหัสผ่านต้องยาวอย่างน้อย 6 ตัว", "Password must be at least 6 characters");
                    if (!v[0].equals(v[1])) return L.t("รหัสผ่านทั้งสองช่องไม่ตรงกัน", "Passwords do not match");
                    setBusy(L.t("กำลังสร้างบัญชี Console...", "Creating the console account..."));
                    cloud.signUp(Cloud.CONSOLE_USER, v[0], (id, err) -> {
                        setBusy(null);
                        if (err != null) {
                            showToast(err.equals(L.t("ชื่อนี้ถูกใช้ไปแล้ว", "This name is already taken"))
                                    ? L.t("มีบัญชี Console อยู่แล้ว ใช้ปุ่มเข้าสู่ระบบ", "The console account already exists - use Log in") : err);
                            return;
                        }
                        cloud.logAsync("console account created");
                        startSession(false);
                        showToast(L.t("สร้างบัญชี Console แล้ว!", "Console account created!"));
                    });
                    return null;
                });
    }

    /** login screen of the Stella Console app */
    void drawConsoleLogin(Canvas c) {
        float cy = H * 0.3f;
        glowCircle(c, W / 2, cy, 330 * u, 0x2234E39A);
        float pulse = 1f + 0.04f * (float) Math.sin(anim * 2.2f);
        consoleMark(c, W / 2, cy, 120 * u * pulse);
        text(c, "Stella Console", W / 2, cy + 230 * u, 70 * u, C_TEXT, Paint.Align.CENTER, W - 80 * u);
        plain(c, L.t("ศูนย์ควบคุมเกม Stella TD", "Stella TD control center"), W / 2, cy + 290 * u, 32 * u, C_DIM, Paint.Align.CENTER, 0);
        RectF card = new RectF(70 * u, cy + 380 * u, W - 70 * u, cy + 380 * u + 470 * u);
        flatPanel(c, card, C_CARD, 28 * u);
        String[] lines = {"firebase  " + (Cloud.configured() ? "ready" : "NOT CONFIGURED"), "account   console", "session   " + L.t("จำไว้ในเครื่องนี้", "kept on this device")};
        for (int i = 0; i < lines.length; i++) {
            p.setShader(null);
            p.setColor(i == 0 && !Cloud.configured() ? 0xFFFF5B5B : C_ACCENT);
            c.drawCircle(card.left + 50 * u, card.top + (62 + i * 56) * u, 7 * u, p);
            plain(c, lines[i], card.left + 75 * u, card.top + (72 + i * 56) * u, 30 * u, C_TEXT, Paint.Align.LEFT, card.width() - 100 * u);
        }
        rf.set(card.left + 40 * u, card.bottom - 150 * u, card.right - 40 * u, card.bottom - 40 * u);
        button(c, rf, L.t("ปลดล็อก Console", "Unlock Console"), 0xFF1E9E6A, Cloud.configured(), this::askConsoleUnlock);
        float bw = (W - 140 * u - 20 * u) / 2;
        rf.set(70 * u, card.bottom + 40 * u, 70 * u + bw, card.bottom + 130 * u);
        button(c, rf, "ไทย", !L.en ? 0xFF2A4A7A : 0xFF162036, true, () -> { L.en = false; acc.setLang(false); });
        rf.set(W - 70 * u - bw, card.bottom + 40 * u, W - 70 * u, card.bottom + 130 * u);
        button(c, rf, "English", L.en ? 0xFF2A4A7A : 0xFF162036, true, () -> { L.en = true; acc.setLang(true); });
        plain(c, L.t("ใส่รหัสครั้งเดียว แล้วเครื่องนี้จะเข้าได้ทันทีทุกครั้ง", "Enter the password once — this device stays unlocked"), W / 2, card.bottom + 200 * u, 26 * u, C_DIM, Paint.Align.CENTER, W - 80 * u);
        RectF link = new RectF(W / 2 - 250 * u, H - 190 * u, W / 2 + 250 * u, H - 120 * u);
        plain(c, L.t("ตั้งค่าบัญชี Console ครั้งแรก", "First-time console setup"), W / 2, link.centerY() + 10 * u, 26 * u, 0xFF5E8BFF, Paint.Align.CENTER, 0);
        if (Cloud.configured()) btns.add(new Btn(link, this::askConsoleSetup));
        plain(c, VERSION + " console", W - 40 * u, H - 40 * u, 24 * u, 0x55FFFFFF, Paint.Align.RIGHT, 0);
    }

    /** the console has one account, so unlocking only asks for its password */
    void askConsoleUnlock() {
        Dialogs.ask(ctx, "Stella Console", L.t("ใส่รหัสผ่าน Console", "Console password"),
                new String[]{L.t("รหัสผ่าน", "Password")}, new int[]{Dialogs.PASSWORD}, new String[]{null},
                L.t("ปลดล็อก", "Unlock"), v -> {
                    if (v[0].isEmpty()) return L.t("ใส่รหัสผ่าน", "Enter the password");
                    setBusy(L.t("กำลังปลดล็อก...", "Unlocking..."));
                    cloud.signIn(Cloud.CONSOLE_USER, v[0], (id, err) -> {
                        setBusy(null);
                        if (err != null) { showToast(err); return; }
                        startSession(false);
                    });
                    return null;
                });
    }

    // ================================================================== match
    void drawPlay(Canvas c) {
        Game g = game;
        c.save();
        if (g.shake > 0) c.translate((float) Math.sin(anim * 90) * 8 * u * g.shake, (float) Math.cos(anim * 77) * 8 * u * g.shake);
        gameSky(c);
        c.translate(mox, moy);
        c.scale(ms, ms);
        drawPath(c, g);
        drawSlots(c, g);
        drawWalls(c, g);
        drawHeat(c, g);
        for (Planet pl : g.slots) if (pl != null) drawPlanetAura(c, g, pl);
        for (Planet pl : g.slots) if (pl != null) drawPlanet(c, g, pl);
        for (Enemy e : g.enemies) drawEnemy(c, g, e);
        for (Shot s : g.shots) drawShot(c, s);
        drawFx(c, g);
        c.restore();

        drawHud(c, g);
        if (g.coop) drawCoopBar(c, g);
        drawTutorial(c, g);
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

    /** moving sky behind the map: twinkling stars, drifting dust, slow nebula glow and the odd comet */
    void gameSky(Canvas c) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        glowCircle(c, W * (0.25f + 0.05f * (float) Math.sin(anim * 0.11f)), H * 0.3f, 420 * u, 0x225B8CFF);
        glowCircle(c, W * (0.8f + 0.05f * (float) Math.cos(anim * 0.09f)), H * 0.62f, 460 * u, 0x22E05BD8);
        for (int i = 0; i < 90; i++) {
            float sx = (i * 197.3f) % W, sy = ((i * 331.7f) % H + anim * (6 + (i % 3) * 5) * u) % H;
            float tw = 0.5f + 0.5f * (float) Math.sin(anim * (1.5f + (i % 5) * 0.4f) + i);
            p.setColor(alpha(i % 7 == 0 ? 0xFFFFE6A8 : 0xFFE6E0FF, (int) (40 + 150 * tw)));
            c.drawCircle(sx, sy, (1 + (i % 3)) * u * (0.7f + 0.5f * tw), p);
        }
        float cf = (float) ((anim * 0.07) % 1.0);
        if (cf < 0.25f) {
            float k = cf / 0.25f;
            float mx = W * (1.1f - k * 1.4f), my = H * (0.08f + k * 0.3f);
            p.setShader(new LinearGradient(mx, my, mx + 200 * u, my - 90 * u, 0xAAFFE6A8, 0x00FFE6A8, Shader.TileMode.CLAMP));
            Path tr = new Path();
            tr.moveTo(mx, my - 5 * u);
            tr.lineTo(mx + 200 * u, my - 90 * u);
            tr.lineTo(mx, my + 5 * u);
            tr.close();
            c.drawPath(tr, p);
            p.setShader(null);
            glowCircle(c, mx, my, 12 * u, 0xFFFFF4D0);
        }
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

        // enemy spawn: a black hole
        float sx = g.px[0], sy = g.py[0];
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 18; i++) {
            float f = (float) ((anim * 0.5 + i / 18.0) % 1.0);
            double a = i * 2.4 + f * 3;
            float rr = 150 * (1 - f);
            p.setColor(alpha(i % 2 == 0 ? 0xFFB86BFF : 0xFFFF8A5B, (int) (200 * f)));
            c.drawCircle(sx + (float) Math.cos(a) * rr, sy + (float) Math.sin(a) * rr, 3 + 4 * (1 - f), p);
        }
        blackHole(c, sx, sy, 46 + 4 * (float) Math.sin(anim * 3));

        // our base: a spiral galaxy
        float bx = g.px[g.px.length - 1], by = g.py[g.py.length - 1];
        glowCircle(c, bx, by, 170, 0x666F8FFF);
        c.save();
        c.translate(bx, by);
        c.rotate(-18);
        c.scale(1f, 0.62f);
        c.rotate(-anim * 14);
        sprite(c, gBmp, 0, 0, 125, 255);
        c.restore();
        glowCircle(c, bx, by, 38 + 6 * (float) Math.sin(anim * 2.5f), 0xFFFFF4D0);
        for (int i = 0; i < 5; i++) {
            double a = anim * (0.8 + i * 0.15) + i * 1.3;
            float rr = 70 + i * 9;
            star(c, bx + (float) Math.cos(a) * rr, by + (float) Math.sin(a) * rr * 0.62f, 6 + (i % 2) * 3);
        }
        // base hp ring
        float frac = g.baseHp / (float) g.maxBaseHp;
        sp.setStrokeWidth(10);
        sp.setColor(0x66000000);
        rf.set(bx - 96, by - 96, bx + 96, by + 96);
        c.drawArc(rf, 0, 360, false, sp);
        sp.setColor(frac > 0.5f ? 0xFF7CFF8A : (frac > 0.25f ? 0xFFFFD23F : 0xFFFF4D5E));
        c.drawArc(rf, -90, 360 * frac, false, sp);
    }

    void drawWalls(Canvas c, Game g) {
        for (Wall w : g.walls) {
            if (w.dead) continue;
            g.pointAt(Math.min(g.total, w.d + 4), tmp);
            float ang = (float) Math.toDegrees(Math.atan2(tmp[1] - w.y, tmp[0] - w.x));
            c.save();
            c.translate(w.x, w.y);
            c.rotate(ang);
            if (w.hitT > 0) c.translate((float) Math.sin(anim * 80) * 3, 0);
            float frac = w.hp / w.maxHp;
            glowCircle(c, 0, 0, 70, 0x445EF2FF);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            for (int k = 0; k < 3; k++) {
                rf.set(-14, -56 + k * 38, 14, -22 + k * 38);
                p.setColor(k % 2 == 0 ? 0xFF22304A : 0xFF34486A);
                c.drawRoundRect(rf, 6, 6, p);
            }
            sp.setPathEffect(null);
            sp.setStrokeWidth(4);
            sp.setColor(alpha(0xFF5EF2FF, (int) (140 + 100 * Math.sin(anim * 6))));
            rf.set(-16, -58, 16, 58);
            c.drawRoundRect(rf, 8, 8, sp);
            c.restore();
            // hp bar
            p.setColor(0xCC0B0520);
            rf.set(w.x - 34, w.y - 74, w.x + 34, w.y - 66);
            c.drawRoundRect(rf, 4, 4, p);
            p.setColor(0xFF5EF2FF);
            rf.set(w.x - 34, w.y - 74, w.x - 34 + 68 * frac, w.y - 66);
            c.drawRoundRect(rf, 4, 4, p);
        }
    }

    void drawSlots(Canvas c, Game g) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(anim * 5);
        int bt = buildSel >= 0 && g != null ? g.deck[buildSel] : -1;
        for (int i = 0; i < g.slotX.length; i++) {
            float x = g.slotX[i], y = g.slotY[i];
            if (g.pathSlot[i]) {
                // path slots only show while placing a Commander
                if (g.slots[i] != null || bt < 0 || !Defs.onPathOnly(bt)) continue;
                sp.setPathEffect(null);
                sp.setStrokeWidth(6);
                sp.setColor(alpha(0xFFFF5B5B, (int) (150 + 100 * pulse)));
                c.drawCircle(x, y, 50, sp);
                glowCircle(c, x, y, 70, alpha(0xFFFF5B5B, (int) (50 * pulse)));
                continue;
            }
            boolean hl = buildSel >= 0 && g.slots[i] == null && (bt < 0 || !Defs.onPathOnly(bt))
                    && (bt < 0 || !Defs.nearPathOnly(bt) || g.pathDist(x, y) <= 175);
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
                double a = k * Math.PI / 2 + Math.PI / 4 + anim * 0.6 + i;
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
        float bob = (float) Math.sin(pl.anim * 2.2f + pl.slot) * 5f;
        sc *= 1f + 0.025f * (float) Math.sin(anim * 2.6f + pl.slot * 1.7f);
        float x = pl.x, y = pl.y + bob;
        float half = 64 * sc;
        // soft shadow that shrinks as the planet bobs up
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x44000000);
        rf.set(pl.x - 44 + bob, pl.y + 40, pl.x + 44 - bob, pl.y + 56);
        c.drawOval(rf, p);
        int rc = Defs.RARITY_COLOR[Defs.P_RARITY[pl.type]];
        glowCircle(c, x, y, 80 + 6 * (float) Math.sin(anim * 3 + pl.slot), alpha(rc, 0x30));
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
        if (Defs.P_KIND[pl.type] == Defs.K_COMMANDER) {
            // its circling route
            sp.setPathEffect(new DashPathEffect(new float[]{14, 12}, anim * 30));
            sp.setStrokeWidth(4);
            sp.setColor(0x66FF5B5B);
            c.drawCircle(pl.ax, pl.ay, 110, sp);
            sp.setPathEffect(null);
        }
        if (pl.type == Defs.KING) {
            // royal aura reaching every planet
            sp.setStrokeWidth(5);
            sp.setColor(alpha(0xFFFFD23F, (int) (90 + 60 * Math.sin(anim * 3))));
            c.drawCircle(x, y, 90 + 10 * (float) Math.sin(anim * 2), sp);
        }
        // a tiny sparkle orbiting the planet (behind it on the far side)
        double oa = anim * (1.6 + (pl.type % 3) * 0.3) + pl.slot;
        boolean back = Math.sin(oa) < 0;
        float ox = x + (float) Math.cos(oa) * 72, oy = y + (float) Math.sin(oa) * 24 - 10;
        if (back) star(c, ox, oy, 6);
        if (pl.evo) glowCircle(c, x, y, half * 1.5f, alpha(0xFF7CFF8A, (int) (70 + 40 * Math.sin(anim * 3))));
        sprite(c, planetBmp(pl.type, pl.skin, pl.evo), x, y, half, off ? 110 : 255);
        if (!back) star(c, ox, oy, 8);
        if (pl.hellMoon) {
            // Hell Moon equipment circling the planet
            double ha = anim * 1.8 + pl.slot;
            float hx = x + (float) Math.cos(ha) * 70, hy = y + (float) Math.sin(ha) * 70;
            glowCircle(c, hx, hy, 26, 0x99FF4B2B);
            sprite(c, hellMoonBmp(), hx, hy, 17, 255);
        }
        for (int k = 0; k < pl.spirits; k++) {
            // Savior spirits: pale wisps circling
            double sa = anim * 1.3 + k * Math.PI * 2 / pl.spirits;
            float gx = x + (float) Math.cos(sa) * 84, gy = y + (float) Math.sin(sa) * 40 - 20;
            glowCircle(c, gx, gy, 20, 0x88B8FFF0);
            p.setShader(null);
            p.setColor(0xDDE8FFFA);
            c.drawCircle(gx, gy, 7, p);
            p.setColor(0x66E8FFFA);
            c.drawCircle(gx - (float) Math.cos(sa) * 10, gy - 6, 5, p);
        }
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
            float sq = 1f + 0.06f * (float) Math.sin(anim * 9 + e.d * 0.05f);
            c.scale(sq, 2f - sq, e.x, e.y);
            sprite(c, eBmp[t], e.x, e.y, half, alpha);
            c.restore();
            if (!fiery && !e.phased) {
                // glitter trail
                for (int k = 1; k <= 3; k++) {
                    g.pointAt(e.d - k * 14, tmp);
                    p.setShader(null);
                    p.setColor(alpha(0xFFE6E0FF, 120 - k * 35));
                    c.drawCircle(tmp[0] + (float) Math.sin(anim * 7 + k) * 5, tmp[1], 4 - k * 0.8f, p);
                }
            }
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
            case Defs.K_LANCE: gc = 0x99FF6A3D; break;
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
            case Defs.K_LANCE: {
                c.save();
                c.translate(s.x, s.y);
                c.rotate(ang);
                // flame trail
                for (int i = 0; i < 6; i++) {
                    p.setColor(i % 2 == 0 ? 0x88FF4D2B : 0x88FFB627);
                    c.drawCircle(-18 - i * 13, (float) Math.sin(s.spin * 2 + i) * 5, 13 - i * 1.6f, p);
                }
                Path sp2 = new Path();
                sp2.moveTo(34, 0);
                sp2.lineTo(-6, -11);
                sp2.lineTo(-22, 0);
                sp2.lineTo(-6, 11);
                sp2.close();
                p.setColor(0xFFFFE27A);
                c.drawPath(sp2, p);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(8, 0, 5, p);
                sp.setStrokeWidth(3);
                sp.setColor(0xFF8A1A14);
                c.drawPath(sp2, sp);
                c.restore();
                break;
            }
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
            if (h.summon == -2) {
                // King's Knight Moon
                glowCircle(c, x, y, 90, 0x99FFD23F);
                sprite(c, pBmp[Defs.MOON], x, y, 44, 255);
                Art.crown(c, x, y - 52, 36);
                sp.setPathEffect(null);
                sp.setStrokeWidth(5);
                sp.setColor(0xDDFFD23F);
                c.drawCircle(x, y, 54, sp);
                float bw = 110;
                p.setShader(null);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xDD0B0520);
                rf.set(x - bw / 2 - 3, y - 92, x + bw / 2 + 3, y - 80);
                c.drawRoundRect(rf, 6, 6, p);
                p.setColor(0xFFFFD23F);
                rf.set(x - bw / 2, y - 90, x - bw / 2 + bw * Math.max(0, h.hp / h.maxHp), y - 82);
                c.drawRoundRect(rf, 4, 4, p);
                continue;
            }
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
                case Fx.BEAM:
                    sp.setPathEffect(null);
                    sp.setStrokeWidth(14 * a + 2);
                    sp.setColor(alpha(f.color, (int) (100 * a)));
                    c.drawLine(f.x, f.y, f.x2, f.y2, sp);
                    sp.setStrokeWidth(4 * a + 1);
                    sp.setColor(alpha(0xFFFFFFFF, (int) (230 * a)));
                    c.drawLine(f.x, f.y, f.x2, f.y2, sp);
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



    /** co-op: team base, partner progress and the SP gift button */
    void drawCoopBar(Canvas c, Game g) {
        float y = panelTop - 104 * u;
        RectF bar = new RectF(30 * u, y, W - 30 * u, y + 84 * u);
        panel(c, bar, 0xCC0F2A1E, 30 * u);
        heart(c, bar.left + 45 * u, bar.centerY(), 20 * u);
        plain(c, L.t("ทีม ", "Team ") + Math.max(0, g.baseHp) + "/" + (g.maxBaseHp + g.coopBonus), bar.left + 75 * u, bar.centerY() + 10 * u, 28 * u, 0xFFFFFFFF, Paint.Align.LEFT, 0);
        String pn = g.partnerName == null ? L.t("รอเพื่อน...", "waiting...") : g.partnerName;
        plain(c, pn + L.t(" • เวฟ ", " • wave ") + g.partnerWave + (g.partnerAlive ? "" : L.t(" (แพ้)", " (out)")), bar.left + 290 * u, bar.centerY() + 10 * u, 26 * u, 0xFFB8FFC8, Paint.Align.LEFT, 380 * u);
        rf.set(bar.right - 240 * u, bar.top + 10 * u, bar.right - 10 * u, bar.bottom - 10 * u);
        boolean can = g.stars >= Social.GIFT_SP && g.partnerName != null && !g.won && !g.lost;
        button(c, rf, L.t("ส่ง ", "Send ") + Social.GIFT_SP + " SP", 0xFF2FB86A, can, () -> {
            Social.Member o = social.room == null ? null : social.room.other(cloud.uid);
            if (o == null || game == null || game.stars < Social.GIFT_SP) return;
            game.stars -= Social.GIFT_SP;
            social.gift(o.uid, Social.GIFT_SP);
            showToast(L.t("ส่ง SP ให้ ", "Sent SP to ") + o.name);
        });
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
        int n = Profile.DECK_SIZE;
        float gap = 14 * u, cw = (W - 60 * u - gap * (n - 1)) / n, chh = 270 * u;
        float y0 = panelTop + 40 * u;
        if (buildSel >= 0) {
            int bt = g.deck[buildSel];
            String hint = bt >= 0 && Defs.onPathOnly(bt) ? L.t("แตะช่องบนทางเดิน (วงสีแดง)", "Tap a slot on the path (red ring)")
                    : bt >= 0 && Defs.nearPathOnly(bt) ? L.t("แตะช่องที่ติดทางเดิน", "Tap a slot next to the path")
                    : L.t("แตะช่องว่างบนแมพเพื่อวาง", "Tap an empty slot to place");
            plain(c, hint, W / 2, panelTop - 16 * u, 34 * u, 0xFFFFD23F, Paint.Align.CENTER, 0);
        }
        for (int i = 0; i < n; i++) {
            final int idx = i;
            final int type = g.deck[i];
            boolean special = i == Defs.SPECIAL_SLOT;
            float x0 = 30 * u + i * (cw + gap);
            float lift = buildSel == i ? 14 * u : 0;
            RectF r = new RectF(x0, y0 - lift, x0 + cw, y0 + chh - lift);
            if (type < 0) {
                rarityCard(c, r, special ? Defs.RARITY_COLOR[Defs.SPECIALIST] : 0xFF6A6488, false, false, 26 * u);
                text(c, special ? "Specialist" : L.t("ว่าง", "Empty"), r.centerX(), r.centerY() + 12 * u, 28 * u, special ? 0xFFB8A878 : 0xFF6A6488, Paint.Align.CENTER, cw - 10 * u);
                continue;
            }
            int cost = Defs.PLACE_COST[type];
            int placed = g.countOf(type);
            boolean can = g.stars >= cost && placed < Defs.PLACE_LIMIT[type];
            int rc = Defs.RARITY_COLOR[Defs.P_RARITY[type]];
            rarityCard(c, r, rc, can, buildSel == i, 26 * u);
            float cx = r.centerX();
            sprite(c, pbmp(type), cx, r.top + 100 * u, Math.min(70 * u, cw * 0.36f), can ? 255 : 120);
            if (pf.enchant[type] >= 0) badge(c, r.right - 28 * u, r.top + 46 * u, 15 * u, pf.enchant[type], 0);
            text(c, Defs.P_NAME[type], cx, r.top + 196 * u, 30 * u, 0xFFFFFFFF, Paint.Align.CENTER, cw - 14 * u);
            plain(c, placed + "/" + Defs.PLACE_LIMIT[type], r.left + 14 * u, r.top + 42 * u, 24 * u, placed >= Defs.PLACE_LIMIT[type] ? 0xFFFF6B6B : 0xFFB8F6FF, Paint.Align.LEFT, 0);
            star(c, cx - 24 * u, r.top + 236 * u, 16 * u);
            text(c, String.valueOf(cost), cx + 4 * u, r.top + 250 * u, 36 * u, can ? 0xFFFFD23F : 0xFFFF6B6B, Paint.Align.LEFT, 0);
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
        sprite(c, planetBmp(pl.type, pl.skin, pl.evo), x0 + 90 * u, y0 + 100 * u, 95 * u, 255);
        float tx = x0 + 200 * u;
        text(c, (pl.evo ? Defs.evoName(pl.type) : Defs.P_NAME[pl.type]) + "  Lv " + pl.level + "/" + Defs.MATCH_MAX[pl.type], tx, y0 + 50 * u, 50 * u, 0xFFFFFFFF, Paint.Align.LEFT, W - tx - 260 * u);
        text(c, Defs.RARITY_NAME[rar], W - 40 * u, y0 + 50 * u, 36 * u, Defs.RARITY_COLOR[rar], Paint.Align.RIGHT, 0);
        String stat;
        int kd = Defs.P_KIND[pl.type];
        if (Game.isProducer(pl)) {
            stat = String.format(Locale.US, L.t("ผลิต %.1f SP ทุก %.1f วิ", "Makes %.1f SP every %.1fs"), Game.produceRate(pl), Game.cdOf(pl));
        } else if (kd == Defs.K_WALL) {
            stat = String.format(Locale.US, L.t("กำแพง 3 ชั้น • สร้างใหม่ทุก %.1f วิ", "3 walls • rebuilds every %.1fs"), Game.cdOf(pl));
        } else if (kd == Defs.K_HAUNT) {
            stat = String.format(Locale.US, L.t("Haunted + สโลว์ 35%%  ระยะ %d", "Haunted + 35%% slow  Range %d"), (int) Game.rangeOf(pl));
        } else if (kd == Defs.K_STORM) {
            stat = String.format(Locale.US, L.t("สตันวงกว้าง ทุก %.1f วิ  ระยะ %d", "Area stun every %.1fs  Range %d"), Game.cdOf(pl), (int) Game.rangeOf(pl));
        } else if (kd == Defs.K_NUKE) {
            stat = L.t("ไม่โจมตี • สกิลระเบิดทั้งแมพ ", "No attack • map-wide blast ") + Defs.nukeCost(g.wave) + " SP";
        } else if (kd == Defs.K_KING) {
            stat = L.t("บัฟทุกดวง +10% • Elite Command 1 ครั้ง", "+10% to every planet • Elite Command once");
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
            plain(c, L.t("บัฟ +", "Buff +") + (int) (pl.buff * 100) + "%" + (pl.king ? " (King)" : ""), W - 40 * u, y0 + 100 * u, 28 * u, 0xFFFFB2D8, Paint.Align.RIGHT, 0);
        if (pl.spirits > 0) plain(c, L.t("วิญญาณ x", "Spirits x") + pl.spirits, W - 40 * u, y0 + 152 * u, 28 * u, 0xFFB8FFF0, Paint.Align.RIGHT, 0);

        float by = H - 120 * u, bh = 95 * u, bw = (W - 80 * u - 40 * u) / 3f;
        boolean max = pl.level >= Defs.MATCH_MAX[pl.type];
        int uc = g.upgradeCost(pl);
        rf.set(40 * u, by - bh / 2, 40 * u + bw, by + bh / 2);
        button(c, rf, max ? "MAX" : L.t("อัปเกรด ", "Upgrade ") + uc, 0xFF33C6A6, !max && g.stars >= uc, () -> {
            String err = game.upgrade(pl);
            if (err != null) showToast(err);
        });
        rf.set(60 * u + bw, by - bh / 2, 60 * u + bw * 2, by + bh / 2);
        if (kd == Defs.K_NUKE) {
            int nc = Defs.nukeCost(g.wave);
            button(c, rf, L.t("ระเบิด ", "Blast ") + nc, 0xFFD0304A, g.stars >= nc, () -> Dialogs.confirm(ctx, "Nightmare",
                    L.t("ใช้ ", "Spend ") + nc + L.t(" SP ระเบิด Nightmare และศัตรูทั้งแมพ?", " SP to blow up Nightmare and every enemy?"), L.t("ระเบิด!", "Blast!"), () -> {
                        String err = game.nuke(pl);
                        if (err != null) showToast(err);
                        selPlanet = null;
                    }));
        } else if (kd == Defs.K_KING) {
            button(c, rf, g.kingSkillUsed ? L.t("ใช้แล้ว", "Used") : "Elite Command", 0xFFE0A020, !g.kingSkillUsed, () -> {
                String err = game.eliteCommand(pl);
                if (err != null) showToast(err);
            });
        } else {
            button(c, rf, L.t("ขาย +", "Sell +") + Math.max(1, pl.spent / 2), 0xFFFF6B5B, true, () -> {
                game.sell(pl);
                selPlanet = null;
            });
        }
        rf.set(80 * u + bw * 2, by - bh / 2, 80 * u + bw * 3, by + bh / 2);
        button(c, rf, L.t("ปิด", "Close"), 0xFF6B5BD6, true, () -> selPlanet = null);
    }

    static float ease(float t) { float k = 1 - t; return 1 - k * k * k; }

    static float backOut(float t) { float s = 1.7f; t -= 1; return t * t * ((s + 1) * t + s) + 1; }

    void dim(Canvas c) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xCC05010D);
        c.drawRect(-W, -H, W * 2, H * 2, p);
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
            if (pf.heroUnlocked) {
                // celebrate the exclusive reward
                RectF hb = new RectF(r.left - 20 * u, r.top - 330 * u, r.right + 20 * u, r.top - 30 * u);
                panel(c, hb, 0xF0401018, 40 * u);
                glowCircle(c, hb.left + 150 * u, hb.centerY(), 160 * u, 0x99FF6A3D);
                sprite(c, pBmp[Defs.HERO], hb.left + 150 * u, hb.centerY() + 6 * u * (float) Math.sin(anim * 3), 115 * u, 255);
                title(c, L.t("ได้รับดาว Hero!", "Hero planet unlocked!"), hb.left + 290 * u, hb.top + 120 * u, 52 * u, 0xFFFFC83A, Paint.Align.LEFT, hb.width() - 320 * u);
                plain(c, L.t("Exclusive • ยิงทะลุ + เผา  (ใส่ Deck ให้แล้ว)", "Exclusive • Pierce + Burn  (added to your deck)"), hb.left + 290 * u, hb.top + 180 * u, 30 * u, 0xFFFFD8C8, Paint.Align.LEFT, hb.width() - 320 * u);
                plain(c, L.t("ผ่านครบ 3 กาแล็กซีแล้ว!", "All 3 galaxies cleared!"), hb.left + 290 * u, hb.top + 230 * u, 28 * u, 0xFFFF9A7A, Paint.Align.LEFT, 0);
            } else if (pf.lastChallengeReward != null) pill(c, W / 2, r.top + 425 * u, L.t("รางวัลชาเลนจ์: ", "Challenge reward: ") + pf.lastChallengeReward, 26 * u, 0xFF140A2A, 0xFF7CFF8A);
            else if (pf.firstClear) pill(c, W / 2, r.top + 425 * u, L.t("ผ่านครั้งแรก! โบนัส Millite + Crystal", "First clear! Bonus Millite + Crystal"), 30 * u, 0xFF140A2A, 0xFF9CFF7A);
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
        if (g.stage == Defs.RAID) {
            oilIcon(c, W / 2 + 175 * u, r.top + 520 * u, 28 * u);
            text(c, "+" + pf.lastOil, W / 2 + 212 * u, r.top + 536 * u, 46 * u, 0xFFFFC890, Paint.Align.LEFT, 0);
            plain(c, L.t("Raid สูงสุด: เวฟ ", "Raid best: wave ") + pf.raidBest, W / 2, r.top + 585 * u, 28 * u, 0xFF9CFF7A, Paint.Align.CENTER, 0);
        } else {
            crystalIcon(c, W / 2 + 175 * u, r.top + 520 * u, 28 * u);
            text(c, "+" + g.earnCrystal, W / 2 + 212 * u, r.top + 536 * u, 46 * u, 0xFFB8F6FF, Paint.Align.LEFT, 0);
        }
        final int st = g.stage;
        if (g.coop) {
            rf.set(r.left + 70 * u, r.top + 680 * u, r.right - 70 * u, r.top + 800 * u);
            button(c, rf, L.t("กลับห้อง Co-op", "Back to the co-op room"), 0xFF2FB86A, true, () -> {
                game = null;
                coopInGame = false;
                if (social.room != null && cloud.uid.equals(social.room.host)) social.backToLobby();
                socTab = 1;
                go(S_SOCIAL);
            });
            return;
        }
        if (g.challenge != 0) {
            final int ck = g.challenge;
            rf.set(r.left + 70 * u, r.top + 610 * u, r.right - 70 * u, r.top + 720 * u);
            button(c, rf, L.t("เล่นอีกครั้ง", "Play again"), 0xFFFF8A2B, true, () -> startChallenge(ck));
            rf.set(r.left + 70 * u, r.top + 760 * u, r.right - 70 * u, r.top + 870 * u);
            button(c, rf, L.t("กลับไปกิจกรรม", "Back to events"), 0xFF6B5BD6, true, () -> { game = null; evTab = 0; go(S_EVENTS); });
            return;
        }
        boolean hasNext = g.won && st != Defs.RAID && st + 1 < Defs.ST_COUNT && st + 1 < pf.unlocked;
        rf.set(r.left + 70 * u, r.top + 610 * u, r.right - 70 * u, r.top + 720 * u);
        if (hasNext) button(c, rf, L.t("ด่านต่อไป ", "Next stage ") + (Defs.galaxyOf(st + 1) + 1) + "-" + ((st + 1) % Defs.PER_GALAXY + 1), 0xFFFF8A2B, true,
                () -> { playSel = st + 1; playGal = Defs.galaxyOf(st + 1); startStage(st + 1); });
        else button(c, rf, L.t("เล่นอีกครั้ง", "Play again"), 0xFFFF8A2B, true, () -> startStage(st));
        rf.set(r.left + 70 * u, r.top + 760 * u, r.right - 70 * u, r.top + 870 * u);
        button(c, rf, L.t("เลือกด่าน", "Stage select"), 0xFF6B5BD6, true, () -> { game = null; screen = S_PLAYMENU; });
    }
}
