package com.stella.td;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Online accounts with Firebase (REST API, no SDK).
 *  - Firebase Authentication (email/password): a username "Nova" becomes the e-mail nova@stellatd.game
 *  - Realtime Database:  users/{uid}/profile   private save data
 *                        public/{uid}          name, avatar, wins, stars, play time (leaderboard)
 *                        bans/{uid}            true when an admin banned the player
 * The admin is the account named "Chain" (the database rules give that e-mail full access).
 */
final class Cloud {
    static final String DOMAIN = "@stellatd.game";
    /** Stella Console logins live on their own e-mail domain, so no game account can ever log into the console */
    static final String CONSOLE_DOMAIN = "@console.stellatd.game";
    /** the console owner account: console@console.stellatd.game (the database rules trust this e-mail) */
    static final String CONSOLE_USER = "console";

    interface Poster {
        boolean post(Runnable r);
    }

    interface Cb<T> {
        void done(T result, String error);
    }

    private final SharedPreferences sp;
    private final Poster ui;
    private final ExecutorService bg = Executors.newSingleThreadExecutor();

    String uid, name, idToken, refreshToken;
    long tokenExp;
    /** my staff rank (Roles.*), refreshed at sign-in and on every heartbeat */
    volatile int role;
    /** the console app keeps its own session slot so it never kicks the game on the same phone */
    String sessionKey = "session";
    /** console app: don't count as an online player */
    boolean countPresence = true;

    Cloud(Context ctx, Poster ui) {
        this.sp = ctx.getSharedPreferences("stella_cloud", Context.MODE_PRIVATE);
        this.ui = ui;
        uid = sp.getString("uid", null);
        name = sp.getString("name", null);
        refreshToken = sp.getString("refresh", null);
    }

    static boolean configured() {
        return !FirebaseConfig.API_KEY.startsWith("PASTE") && FirebaseConfig.DB_URL.trim().startsWith("https://");
    }

    boolean hasSession() { return sp.getString("uid", null) != null && uid != null && refreshToken != null; }

    /** true for the Stella Console app: logins use CONSOLE_DOMAIN */
    boolean console;

    /** the console owner account (only this account can give ranks) */
    boolean isConsoleOwner() { return console && name != null && Accounts.norm(name).equals(CONSOLE_USER); }

    /** may open the admin panel / console at all */
    boolean isAdmin() { return name != null && isConsoleOwner(); }

    int myRole() { return isConsoleOwner() ? Roles.OWNER : role; }

    /** read my rank from the database (background thread) */
    void loadRole() throws Exception {
        // ranks were removed: only the Stella Console account has any power
        role = isConsoleOwner() ? Roles.OWNER : Roles.NONE;
    }

    String email(String user) { return Accounts.norm(user) + (console ? CONSOLE_DOMAIN : DOMAIN); }

    private static String authBase() {
        return Http.overrideBase != null ? Http.overrideBase + "/auth" : "https://identitytoolkit.googleapis.com/v1";
    }

    private static String tokenBase() {
        return Http.overrideBase != null ? Http.overrideBase + "/token" : "https://securetoken.googleapis.com/v1";
    }

    private static String dbBase() {
        if (Http.overrideBase != null) return Http.overrideBase + "/db";
        String u = FirebaseConfig.DB_URL.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    // ------------------------------------------------------------------ async plumbing
    interface Job<T> {
        T run() throws Exception;
    }

    <T> void async(Job<T> job, Cb<T> cb) {
        bg.execute(() -> {
            T r = null;
            String err = null;
            try {
                r = job.run();
            } catch (Exception e) {
                err = friendly(e);
            }
            final T fr = r;
            final String fe = err;
            if (cb != null) ui.post(() -> cb.done(fr, fe));
        });
    }

    /** an error whose message is already written for the player */
    static final class UserError extends Exception {
        UserError(String m) { super(m); }
    }

    static String friendly(Exception e) {
        if (e instanceof UserError) return e.getMessage();
        String m = e.getMessage() == null ? e.toString() : e.getMessage();
        if (m.contains("EMAIL_EXISTS")) return L.t("ชื่อนี้ถูกใช้ไปแล้ว", "This name is already taken");
        if (m.contains("EMAIL_NOT_FOUND") || m.contains("INVALID_LOGIN_CREDENTIALS") || m.contains("INVALID_PASSWORD"))
            return L.t("ชื่อไอดีหรือรหัสผ่านไม่ถูกต้อง", "Wrong username or password");
        if (m.contains("WEAK_PASSWORD")) return L.t("รหัสผ่านต้องยาวอย่างน้อย 6 ตัว", "Password must be at least 6 characters");
        if (m.contains("TOO_MANY_ATTEMPTS")) return L.t("ลองผิดบ่อยเกินไป รอสักครู่แล้วลองใหม่", "Too many attempts, try again later");
        if (m.contains("USER_DISABLED")) return L.t("บัญชีนี้ถูกปิดใช้งาน", "This account is disabled");
        if (m.contains("WRONG_OLD")) return L.t("รหัสผ่านเดิมไม่ถูกต้อง", "Current password is wrong");
        if (m.startsWith("BANNED")) {
            String[] parts = m.split("\\|", -1);
            String why = parts.length > 1 && !parts[1].isEmpty() ? parts[1] : "";
            String until = parts.length > 2 && !parts[2].isEmpty() && !parts[2].equals("0")
                    ? new java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.US).format(new java.util.Date(Long.parseLong(parts[2]))) : "";
            return BAN_PREFIX() + (why.isEmpty() ? "" : " - " + why) + (until.isEmpty() ? L.t(" (ถาวร)", " (permanent)") : L.t(" (ถึง ", " (until ") + until + ")");
        }
        if (m.contains("Permission denied") || m.startsWith("401")) {
            int at = m.indexOf(" @");
            String where = at >= 0 ? m.substring(at + 2) : "";
            return L.t("ไม่มีสิทธิ์เข้าถึง ", "Permission denied: ") + where + L.t(" - วางกฎ database.rules.json ใหม่แล้วกด Publish", " - paste database.rules.json again and Publish");
        }
        if (m.contains("API key not valid") || m.contains("API_KEY")) return L.t("API Key ไม่ถูกต้อง (แก้ใน FirebaseConfig.java)", "Invalid API key (edit FirebaseConfig.java)");
        if (e instanceof java.net.UnknownHostException || e instanceof java.net.SocketTimeoutException || e instanceof java.net.ConnectException)
            return L.t("เชื่อมต่ออินเทอร์เน็ตไม่ได้", "No internet connection");
        return L.t("เกิดข้อผิดพลาด: ", "Error: ") + m;
    }

    static String BAN_PREFIX() { return L.t("ไอดีนี้ถูกแบน", "This account is banned"); }

    private static Map<String, Object> call(String method, String url, Object body) throws Exception {
        Http.Res r = Http.request(method, url, body == null ? null : Json.write(body), null);
        Object parsed = r.body.isEmpty() ? null : Json.parse(r.body);
        if (!r.ok()) {
            Map<String, Object> m = Json.obj(parsed);
            String msg = String.valueOf(r.code);
            if (m != null) {
                Object er = m.get("error");
                if (er instanceof Map) msg = Json.str(Json.obj(er), "message", msg);
                else if (er != null) msg = er.toString();
            }
            // say which database path was refused, so a missing rule is easy to spot
            if (url.contains(".json")) {
                String path = url.substring(0, url.indexOf(".json"));
                int k = path.indexOf("/", path.indexOf("://") + 3);
                path = k >= 0 ? path.substring(k + 1) : path;
                if (path.startsWith("db/")) path = path.substring(3);
                int slash = path.indexOf('/');
                msg += " @" + (slash > 0 ? path.substring(0, slash) : path);
            }
            throw new Exception(msg);
        }
        Map<String, Object> m = Json.obj(parsed);
        if (m == null) {
            m = new LinkedHashMap<>();
            m.put("_value", parsed);
        }
        return m;
    }

    // ------------------------------------------------------------------ auth (call from the background thread)
    private void session(Map<String, Object> r, String displayName) {
        idToken = Json.str(r, "idToken", Json.str(r, "id_token", null));
        refreshToken = Json.str(r, "refreshToken", Json.str(r, "refresh_token", refreshToken));
        uid = Json.str(r, "localId", Json.str(r, "user_id", uid));
        int exp = Json.num(r, "expiresIn", Json.num(r, "expires_in", 3600));
        tokenExp = System.currentTimeMillis() + (exp - 120) * 1000L;
        if (displayName != null) name = displayName;
        sp.edit().putString("uid", uid).putString("name", name).putString("refresh", refreshToken).apply();
    }

    String token() throws Exception {
        if (idToken != null && System.currentTimeMillis() < tokenExp) return idToken;
        if (refreshToken == null) throw new Exception("not signed in");
        String body = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8");
        Http.Res r = Http.request("POST", tokenBase() + "/token?key=" + FirebaseConfig.API_KEY, body, "application/x-www-form-urlencoded");
        if (!r.ok()) throw new Exception(Json.str(Json.obj(Json.obj(Json.parse(r.body)).get("error")), "message", "token " + r.code));
        session(Json.obj(Json.parse(r.body)), null);
        return idToken;
    }

    void signUp(String user, String pass, Cb<String> cb) {
        async(() -> {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("email", email(user));
            b.put("password", pass);
            b.put("returnSecureToken", true);
            session(call("POST", authBase() + "/accounts:signUp?key=" + FirebaseConfig.API_KEY, b), user.trim());
            return uid;
        }, cb);
    }

    void signIn(String user, String pass, Cb<String> cb) {
        async(() -> {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("email", email(user));
            b.put("password", pass);
            b.put("returnSecureToken", true);
            Map<String, Object> r = call("POST", authBase() + "/accounts:signInWithPassword?key=" + FirebaseConfig.API_KEY, b);
            session(r, user.trim());
            String ban = banInfo(uid);
            if (ban != null) {
                logoutNow();
                throw new Exception(ban);
            }
            // keep the display name the player registered with
            Map<String, Object> pub = Json.obj(get("public/" + uid));
            if (pub != null && pub.get("name") != null) {
                name = pub.get("name").toString();
                sp.edit().putString("name", name).apply();
            }
            loadRole();
            return uid;
        }, cb);
    }

    void changePassword(String oldPass, String newPass, Cb<Boolean> cb) {
        async(() -> {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("email", email(name));
            b.put("password", oldPass);
            b.put("returnSecureToken", true);
            try {
                session(call("POST", authBase() + "/accounts:signInWithPassword?key=" + FirebaseConfig.API_KEY, b), null);
            } catch (Exception e) {
                throw new Exception("WRONG_OLD");
            }
            Map<String, Object> u = new LinkedHashMap<>();
            u.put("idToken", idToken);
            u.put("password", newPass);
            u.put("returnSecureToken", true);
            session(call("POST", authBase() + "/accounts:update?key=" + FirebaseConfig.API_KEY, u), null);
            return true;
        }, cb);
    }

    /**
     * Forget the session. The in-memory token is cleared on the background queue, so a save that was
     * queued just before logging out still reaches the server (the leaderboard keeps the latest numbers).
     */
    void logout() {
        sp.edit().remove("uid").remove("name").remove("refresh").apply();
        bg.execute(() -> {
            uid = null;
            name = null;
            idToken = null;
            refreshToken = null;
            role = 0;
        });
    }

    /** used inside a background job (e.g. a banned sign-in): clear right away */
    private void logoutNow() {
        sp.edit().remove("uid").remove("name").remove("refresh").apply();
        uid = null;
        name = null;
        idToken = null;
        refreshToken = null;
        role = 0;
    }

    // ------------------------------------------------------------------ database (background thread)
    Object get(String path) throws Exception {
        Map<String, Object> m = call("GET", dbBase() + "/" + path + ".json?auth=" + token(), null);
        return m.containsKey("_value") && m.size() == 1 ? m.get("_value") : m;
    }

    void put(String path, Object value) throws Exception {
        call("PUT", dbBase() + "/" + path + ".json?auth=" + token(), value);
    }

    void delete(String path) throws Exception {
        call("DELETE", dbBase() + "/" + path + ".json?auth=" + token(), null);
    }

    boolean isBanned(String who) throws Exception { return banInfo(who) != null; }

    /** null when not banned, else "BANNED|reason|untilMillis" (until 0 = permanent) */
    String banInfo(String who) throws Exception {
        Object v = get("bans/" + who);
        if (v == null) return null;
        if (Boolean.TRUE.equals(v)) return "BANNED||0";
        Map<String, Object> m = Json.obj(v);
        if (m == null) return null;
        long until = 0;
        try { until = Long.parseLong(Json.str(m, "until", "0")); } catch (NumberFormatException ignored) { }
        if (until > 0 && until < System.currentTimeMillis()) return null; // temporary ban is over
        return "BANNED|" + Json.str(m, "reason", "").replace("|", "/") + "|" + until;
    }

    static Map<String, Object> publicOf(String name, Profile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("avatar", p.avatar);
        m.put("photo", p.avatar < 0 && p.photo != null ? p.photo : "");
        m.put("wins", p.wins);
        m.put("stars", p.stars);
        m.put("playSec", p.playSec);
        m.put("planets", p.ownedCount());
        m.put("updated", String.valueOf(System.currentTimeMillis()));
        return m;
    }

    /** download my save; returns null if the cloud has none yet */
    void loadMine(Cb<Map<String, Object>> cb) {
        final String me = uid;
        async(() -> {
            String ban = banInfo(me);
            if (ban != null) throw new Exception(ban);
            return Json.obj(get("users/" + me + "/profile"));
        }, cb);
    }

    /** upload a profile (mine, or another player's when I am the admin) */
    void upload(String who, String displayName, Profile p, Cb<Boolean> cb) { upload(who, displayName, p, null, cb); }

    /** upload a profile; `logMsg` (staff edits) goes to the audit log */
    void upload(String who, String displayName, Profile p, String logMsg, Cb<Boolean> cb) {
        final Map<String, Object> data = p.toMap();
        final Map<String, Object> pub = publicOf(displayName, p);
        async(() -> {
            put("users/" + who + "/profile", data);
            put("public/" + who, pub);
            if (logMsg != null) log(logMsg);
            return true;
        }, cb);
    }

    /**
     * Push my save, unless the cloud copy changed since my last sync (an admin edit or another phone):
     * then nothing is uploaded and the newer cloud save is returned so the game can load it.
     * Also refuses (BANNED) when the account has been banned.
     */
    void sync(String displayName, Profile p, long lastSynced, Cb<Map<String, Object>> cb) {
        final String me = uid;
        final Map<String, Object> data = p.toMap();
        final Map<String, Object> pub = publicOf(displayName, p);
        async(() -> {
            if (me == null) throw new Exception("not signed in");
            String ban = banInfo(me);
            if (ban != null) throw new Exception(ban);
            Map<String, Object> remote = Json.obj(get("users/" + me + "/profile"));
            if (remote != null && saveTimeOf(remote) > lastSynced) return remote;
            put("users/" + me + "/profile", data);
            put("public/" + me, pub);
            return null;
        }, cb);
    }

    static long saveTimeOf(Map<String, Object> m) {
        if (m == null) return 0;
        try {
            return Long.parseLong(Json.str(m, "saveTime", "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static boolean isBanError(String err) {
        return err != null && err.startsWith(BAN_PREFIX());
    }

    // ------------------------------------------------------------------ online presence
    static final class Beat {
        long serverTime;
        int online, minVersion, ping, role;
        boolean kickedElsewhere, kickedByStaff, maintenance;
        String ban, broadcast;
        long broadcastTime;
        Abuse abuse;
    }

    /** Admin Abuse event, set from the console under config/abuse */
    static final class Abuse {
        String id = "", giftId = "", giftRewards = "", giftMsg = "", bossId = "";
        long until, bossUntil;
        long bossHp;
        boolean on(long now) { return !id.isEmpty() && now < until; }
        boolean bossOn(long now) { return !bossId.isEmpty() && now < bossUntil; }
    }

    static long lng(Object o) {
        if (o instanceof Number) return ((Number) o).longValue();
        try { return o == null ? 0 : Long.parseLong(o.toString()); } catch (NumberFormatException e) { return 0; }
    }

    static Abuse parseAbuse(Object o) {
        Map<String, Object> m = Json.obj(o);
        if (m == null) return null;
        Abuse a = new Abuse();
        a.id = Json.str(m, "id", "");
        a.until = lng(m.get("until"));
        Map<String, Object> gf = Json.obj(m.get("gift"));
        if (gf != null) {
            a.giftId = Json.str(gf, "id", "");
            a.giftRewards = Json.str(gf, "rewards", "");
            a.giftMsg = Json.str(gf, "msg", "");
        }
        Map<String, Object> bs = Json.obj(m.get("boss"));
        if (bs != null) {
            a.bossId = Json.str(bs, "id", "");
            a.bossHp = lng(bs.get("hp"));
            a.bossUntil = lng(bs.get("until"));
        }
        return a;
    }

    // ---- console: Admin Abuse controls
    void abuseStart(int minutes, Cb<Boolean> cb) {
        async(() -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", "A" + Long.toString(System.currentTimeMillis(), 36));
            m.put("until", System.currentTimeMillis() + minutes * 60000L);
            put("config/abuse", m);
            log("abuse on " + minutes + "m");
            return true;
        }, cb);
    }

    void abuseStop(Cb<Boolean> cb) {
        async(() -> { delete("config/abuse"); log("abuse off"); return true; }, cb);
    }

    void abuseGift(String rewards, String msg, Cb<Boolean> cb) {
        async(() -> {
            if (Json.obj(get("config/abuse")) == null) throw new UserError("start the event first: abuse on <minutes>");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", "G" + Long.toString(System.currentTimeMillis(), 36));
            m.put("rewards", rewards);
            m.put("msg", msg == null ? "" : msg);
            put("config/abuse/gift", m);
            log("abuse gift " + rewards);
            return true;
        }, cb);
    }

    void abuseBoss(long hp, int minutes, Cb<Boolean> cb) {
        async(() -> {
            if (Json.obj(get("config/abuse")) == null) throw new UserError("start the event first: abuse on <minutes>");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", "B" + Long.toString(System.currentTimeMillis(), 36));
            m.put("hp", hp);
            m.put("until", System.currentTimeMillis() + minutes * 60000L);
            put("config/abuse/boss", m);
            log("abuse boss " + hp + " " + minutes + "m");
            return true;
        }, cb);
    }

    /** this player's total damage on the event boss (kept at its best) */
    void reportBoss(String bossId, long dmg, Cb<Boolean> cb) {
        final String me = uid;
        async(() -> {
            long old = lng(get("bossDmg/" + bossId + "/" + me));
            if (dmg > old) put("bossDmg/" + bossId + "/" + me, dmg);
            return true;
        }, cb);
    }

    /** {total damage, players} for the event boss */
    void bossTotal(String bossId, Cb<long[]> cb) {
        async(() -> {
            Map<String, Object> m = Json.obj(get("bossDmg/" + bossId));
            long sum = 0, n = 0;
            if (m != null) for (Object v : m.values()) { sum += lng(v); n++; }
            return new long[]{sum, n};
        }, cb);
    }

    /** claim this device as the account's only active session */
    void claimSession(String sessionId, Cb<Boolean> cb) {
        final String me = uid;
        final String key = sessionKey;
        async(() -> {
            String ban = banInfo(me);
            if (ban != null) throw new Exception(ban);
            loadRole();
            put("users/" + me + "/" + key, sessionId);
            return true;
        }, cb);
    }

    /** heartbeat: presence, ping, online count, ban / kick / rank / maintenance / broadcast / min version */
    void heartbeat(String sessionId, Cb<Beat> cb) {
        final String me = uid;
        final String key = sessionKey;
        final boolean presence = countPresence;
        async(() -> {
            Beat b = new Beat();
            long t0 = System.currentTimeMillis();
            String tok = token();
            if (presence) {
                Map<String, Object> sv = new LinkedHashMap<>();
                sv.put(".sv", "timestamp");
                Map<String, Object> r = call("PUT", dbBase() + "/presence/" + me + ".json?auth=" + tok, sv);
                Object st = r.get("_value");
                b.serverTime = st instanceof Number ? ((Number) st).longValue() : System.currentTimeMillis();
            } else {
                get("config/minVersion");
                b.serverTime = System.currentTimeMillis();
            }
            b.ping = (int) (System.currentTimeMillis() - t0);
            b.ban = banInfo(me);
            Object ses = get("users/" + me + "/" + key);
            b.kickedElsewhere = ses != null && !sessionId.equals(ses.toString());
            b.kickedByStaff = b.kickedElsewhere && ses.toString().startsWith("kicked");
            loadRole();
            b.role = myRole();
            Map<String, Object> cfg = Json.obj(get("config"));
            if (cfg != null) {
                b.minVersion = Json.num(cfg, "minVersion", 0);
                b.maintenance = Json.bool(cfg, "maintenance");
                Map<String, Object> bc = Json.obj(cfg.get("broadcast"));
                if (bc != null) {
                    b.broadcast = Json.str(bc, "msg", null);
                    try { b.broadcastTime = Long.parseLong(Json.str(bc, "t", "0")); } catch (NumberFormatException ignored) { }
                }
                b.abuse = parseAbuse(cfg.get("abuse"));
            }
            Map<String, Object> pres = Json.obj(get("presence"));
            if (pres != null) for (Object v : pres.values())
                if (v instanceof Number && b.serverTime - ((Number) v).longValue() < 45000) b.online++;
            return b;
        }, cb);
    }

    /** dev+: oldest game version still allowed to play */
    void setMinVersion(int v, Cb<Boolean> cb) {
        async(() -> {
            put("config/minVersion", v);
            log("minver " + v);
            return true;
        }, cb);
    }

    void setMaintenance(boolean on, Cb<Boolean> cb) {
        async(() -> {
            put("config/maintenance", on);
            log("maintenance " + (on ? "on" : "off"));
            return true;
        }, cb);
    }

    void setBroadcast(String msg, Cb<Boolean> cb) {
        async(() -> {
            if (msg == null || msg.isEmpty()) delete("config/broadcast");
            else {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("msg", msg);
                m.put("t", String.valueOf(System.currentTimeMillis()));
                m.put("by", name);
                put("config/broadcast", m);
            }
            log("broadcast " + (msg == null || msg.isEmpty() ? "(clear)" : msg));
            return true;
        }, cb);
    }

    static final class Server {
        boolean maintenance;
        int minVersion, online;
        String broadcast;
        Abuse abuse;
    }

    void server(Cb<Server> cb) {
        async(() -> {
            Server sv = new Server();
            Map<String, Object> cfg = Json.obj(get("config"));
            if (cfg != null) {
                sv.minVersion = Json.num(cfg, "minVersion", 0);
                sv.maintenance = Json.bool(cfg, "maintenance");
                Map<String, Object> bc = Json.obj(cfg.get("broadcast"));
                if (bc != null) sv.broadcast = Json.str(bc, "msg", null);
                sv.abuse = parseAbuse(cfg.get("abuse"));
            }
            Map<String, Object> pres = Json.obj(get("presence"));
            long now = System.currentTimeMillis();
            if (pres != null) for (Object v : pres.values())
                if (v instanceof Number && Math.abs(now - ((Number) v).longValue()) < 60000) sv.online++;
            return sv;
        }, cb);
    }

    /** leave: drop my presence so I stop counting as online */
    void offline() {
        final String me = uid;
        if (me == null || !countPresence) return;
        async(() -> {
            delete("presence/" + me);
            return true;
        }, null);
    }

    // ------------------------------------------------------------------ staff tools
    /** append a line to the audit log (background thread; never fails the caller) */
    void log(String msg) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("by", name == null ? "?" : name);
            m.put("t", String.valueOf(System.currentTimeMillis()));
            m.put("msg", msg.length() > 200 ? msg.substring(0, 200) : msg);
            call("POST", dbBase() + "/logs.json?auth=" + token(), m);
        } catch (Exception ignored) { }
    }

    void logAsync(String msg) { async(() -> { log(msg); return true; }, null); }

    static final class LogLine {
        String by, msg;
        long t;
    }

    void logs(int n, Cb<List<LogLine>> cb) {
        async(() -> {
            List<LogLine> out = new ArrayList<>();
            Map<String, Object> all = Json.obj(call("GET", dbBase() + "/logs.json?auth=" + token()
                    + "&orderBy=%22%24key%22&limitToLast=" + n, null));
            if (all != null) for (Object v : all.values()) {
                Map<String, Object> m = Json.obj(v);
                if (m == null) continue;
                LogLine l = new LogLine();
                l.by = Json.str(m, "by", "?");
                l.msg = Json.str(m, "msg", "");
                try { l.t = Long.parseLong(Json.str(m, "t", "0")); } catch (NumberFormatException ignored) { }
                out.add(l);
            }
            out.sort((x, y) -> Long.compare(y.t, x.t));
            return out;
        }, cb);
    }

    void setRole(String who, String whoName, int r, Cb<Boolean> cb) {
        async(() -> {
            if (r <= Roles.NONE) delete("roles/" + who);
            else put("roles/" + who, Roles.KEY[r]);
            log("role " + whoName + " -> " + Roles.NAME[r]);
            return true;
        }, cb);
    }

    /** mod+: force a player out (their next heartbeat sees the changed session) */
    void kick(String who, String whoName, Cb<Boolean> cb) {
        async(() -> {
            put("users/" + who + "/session", "kicked-" + System.currentTimeMillis());
            delete("presence/" + who);
            log("kick " + whoName);
            return true;
        }, cb);
    }

    static final class Player {
        String uid, name;
        int avatar, wins, stars, playSec, planets, role;
        String photo, banReason;
        boolean banned;
        long banUntil, lastSeen;
    }

    void players(Cb<List<Player>> cb) {
        async(() -> {
            List<Player> out = new ArrayList<>();
            Map<String, Object> all = Json.obj(get("public"));
            Map<String, Object> roles = Json.obj(get("roles"));
            Map<String, Object> bans = isAdmin() ? Json.obj(get("bans")) : null;
            Map<String, Object> pres = Json.obj(get("presence"));
            long now = System.currentTimeMillis();
            if (all != null) for (Map.Entry<String, Object> e : all.entrySet()) {
                Map<String, Object> m = Json.obj(e.getValue());
                if (m == null) continue;
                Player p = new Player();
                p.uid = e.getKey();
                p.name = Json.str(m, "name", "?");
                p.avatar = Json.num(m, "avatar", 0);
                p.photo = Json.str(m, "photo", null);
                p.wins = Json.num(m, "wins", 0);
                p.stars = Json.num(m, "stars", 0);
                p.playSec = Json.num(m, "playSec", 0);
                p.planets = Json.num(m, "planets", 0);
                p.role = 0; // ranks were removed
                if (bans != null && bans.get(p.uid) != null) {
                    Object bv = bans.get(p.uid);
                    Map<String, Object> bm = Json.obj(bv);
                    p.banned = true;
                    if (bm != null) {
                        p.banReason = Json.str(bm, "reason", "");
                        try { p.banUntil = Long.parseLong(Json.str(bm, "until", "0")); } catch (NumberFormatException ignored) { }
                        if (p.banUntil > 0 && p.banUntil < now) p.banned = false;
                    }
                }
                Object pv = pres == null ? null : pres.get(p.uid);
                if (pv instanceof Number) p.lastSeen = ((Number) pv).longValue();
                out.add(p);
            }
            return out;
        }, cb);
    }

    /** admin: fetch another player's save as an editable in-memory profile */
    void fetchProfile(String who, Cb<Profile> cb) {
        async(() -> {
            Profile p = new Profile(new Store.Mem());
            Map<String, Object> m = Json.obj(get("users/" + who + "/profile"));
            if (m != null) p.fromMap(m);
            return p;
        }, cb);
    }

    void setBan(String who, boolean ban, Cb<Boolean> cb) { setBan(who, who, ban, "", 0, cb); }

    /** mod+: ban (hours 0 = permanent) or unban */
    void setBan(String who, String whoName, boolean ban, String reason, int hours, Cb<Boolean> cb) {
        async(() -> {
            if (ban) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("by", name);
                m.put("reason", reason == null ? "" : reason);
                m.put("until", String.valueOf(hours > 0 ? System.currentTimeMillis() + hours * 3600000L : 0));
                put("bans/" + who, m);
                put("users/" + who + "/session", "kicked-" + System.currentTimeMillis());
                log("ban " + whoName + (hours > 0 ? " " + hours + "h" : " permanent") + (reason == null || reason.isEmpty() ? "" : " : " + reason));
            } else {
                delete("bans/" + who);
                log("unban " + whoName);
            }
            return true;
        }, cb);
    }

    /** admin+: remove a player's data and block the account (the login itself is removed in the Firebase console) */
    void wipe(String who, Cb<Boolean> cb) {
        async(() -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("by", name);
            m.put("reason", "wiped");
            m.put("until", "0");
            put("bans/" + who, m);
            delete("users/" + who + "/profile");
            delete("public/" + who);
            delete("roles/" + who);
            log("wipe " + who);
            return true;
        }, cb);
    }
}
