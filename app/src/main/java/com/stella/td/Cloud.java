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
    static final String ADMIN = "chain";

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

    boolean hasSession() { return uid != null && refreshToken != null; }

    boolean isAdmin() { return name != null && Accounts.norm(name).equals(ADMIN); }

    static String email(String user) { return Accounts.norm(user) + DOMAIN; }

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

    static String friendly(Exception e) {
        String m = e.getMessage() == null ? e.toString() : e.getMessage();
        if (m.contains("EMAIL_EXISTS")) return L.t("มีไอดีนี้แล้ว", "That username is taken");
        if (m.contains("EMAIL_NOT_FOUND") || m.contains("INVALID_LOGIN_CREDENTIALS") || m.contains("INVALID_PASSWORD"))
            return L.t("ชื่อไอดีหรือรหัสผ่านไม่ถูกต้อง", "Wrong username or password");
        if (m.contains("WEAK_PASSWORD")) return L.t("รหัสผ่านต้องยาวอย่างน้อย 6 ตัว", "Password must be at least 6 characters");
        if (m.contains("TOO_MANY_ATTEMPTS")) return L.t("ลองผิดบ่อยเกินไป รอสักครู่แล้วลองใหม่", "Too many attempts, try again later");
        if (m.contains("USER_DISABLED")) return L.t("บัญชีนี้ถูกปิดใช้งาน", "This account is disabled");
        if (m.contains("WRONG_OLD")) return L.t("รหัสผ่านเดิมไม่ถูกต้อง", "Current password is wrong");
        if (m.contains("BANNED")) return L.t("บัญชีนี้ถูกแบนโดยแอดมิน", "This account was banned by an admin");
        if (m.contains("Permission denied") || m.contains("401")) return L.t("ไม่มีสิทธิ์ (ตรวจกฎของ Database)", "Permission denied (check database rules)");
        if (m.contains("API key not valid") || m.contains("API_KEY")) return L.t("API Key ไม่ถูกต้อง (แก้ใน FirebaseConfig.java)", "Invalid API key (edit FirebaseConfig.java)");
        if (e instanceof java.net.UnknownHostException || e instanceof java.net.SocketTimeoutException || e instanceof java.net.ConnectException)
            return L.t("เชื่อมต่ออินเทอร์เน็ตไม่ได้", "No internet connection");
        return L.t("เกิดข้อผิดพลาด: ", "Error: ") + m;
    }

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
            if (isBanned(uid)) {
                logout();
                throw new Exception("BANNED");
            }
            // keep the display name the player registered with
            Map<String, Object> pub = Json.obj(get("public/" + uid));
            if (pub != null && pub.get("name") != null) {
                name = pub.get("name").toString();
                sp.edit().putString("name", name).apply();
            }
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

    void logout() {
        uid = null;
        name = null;
        idToken = null;
        refreshToken = null;
        sp.edit().remove("uid").remove("name").remove("refresh").apply();
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

    boolean isBanned(String who) throws Exception {
        return Boolean.TRUE.equals(get("bans/" + who));
    }

    static Map<String, Object> publicOf(String name, Profile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("avatar", p.avatar);
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
            if (isBanned(me)) throw new Exception("BANNED");
            return Json.obj(get("users/" + me + "/profile"));
        }, cb);
    }

    /** upload a profile (mine, or another player's when I am the admin) */
    void upload(String who, String displayName, Profile p, Cb<Boolean> cb) {
        final Map<String, Object> data = p.toMap();
        final Map<String, Object> pub = publicOf(displayName, p);
        async(() -> {
            put("users/" + who + "/profile", data);
            put("public/" + who, pub);
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
            if (isBanned(me)) throw new Exception("BANNED");
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
        return err != null && err.equals(friendly(new Exception("BANNED")));
    }

    static final class Player {
        String uid, name;
        int avatar, wins, stars, playSec, planets;
        boolean banned;
    }

    void players(Cb<List<Player>> cb) {
        async(() -> {
            List<Player> out = new ArrayList<>();
            Map<String, Object> all = Json.obj(get("public"));
            Map<String, Object> bans = isAdmin() ? Json.obj(get("bans")) : null;
            if (all != null) for (Map.Entry<String, Object> e : all.entrySet()) {
                Map<String, Object> m = Json.obj(e.getValue());
                if (m == null) continue;
                Player p = new Player();
                p.uid = e.getKey();
                p.name = Json.str(m, "name", "?");
                p.avatar = Json.num(m, "avatar", 0);
                p.wins = Json.num(m, "wins", 0);
                p.stars = Json.num(m, "stars", 0);
                p.playSec = Json.num(m, "playSec", 0);
                p.planets = Json.num(m, "planets", 0);
                p.banned = bans != null && Json.bool(bans, p.uid);
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

    void setBan(String who, boolean ban, Cb<Boolean> cb) {
        async(() -> {
            if (ban) put("bans/" + who, true);
            else delete("bans/" + who);
            return true;
        }, cb);
    }

    /** admin: remove a player's data and block the account (the login itself is removed in the Firebase console) */
    void wipe(String who, Cb<Boolean> cb) {
        async(() -> {
            put("bans/" + who, true);
            delete("users/" + who);
            delete("public/" + who);
            return true;
        }, cb);
    }
}
