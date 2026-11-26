package com.stella.td;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Local account registry. Every account keeps its game progress in its own SharedPreferences file.
 * Passwords are stored only as salted SHA-256 hashes, never as plain text.
 * NOTE: accounts live on this phone only; there is no server yet.
 */
final class Accounts {
    static final String ADMIN_USER = "chain";
    static final String ADMIN_DISPLAY = "Chain";
    /** first-time admin password; the game asks the admin to change it after the first login */
    static final String ADMIN_DEFAULT_PASS = "stella1234";

    private final Context ctx;
    private final SharedPreferences sp;

    Accounts(Context ctx) {
        this.ctx = ctx;
        this.sp = ctx.getSharedPreferences("stella_accounts", Context.MODE_PRIVATE);
        migrateOldAdmin();
        if (!exists(ADMIN_USER)) {
            create(ADMIN_DISPLAY, ADMIN_DEFAULT_PASS, true);
            sp.edit().putBoolean("must_" + ADMIN_USER, true).apply();
        }
    }

    // ------------------------------------------------------------------ helpers
    static String norm(String u) {
        return u == null ? "" : u.trim().toLowerCase(Locale.US);
    }

    private static String join(ArrayList<String> l) {
        StringBuilder s = new StringBuilder();
        for (String x : l) {
            if (s.length() > 0) s.append(',');
            s.append(x);
        }
        return s.toString();
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format(Locale.US, "%02x", x & 0xFF));
        return s.toString();
    }

    private static String hash(String salt, String pass) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((salt + ":" + pass).getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < 2000; i++) d = md.digest(d); // slow down brute force a little
            return hex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String newSalt() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        return hex(b);
    }

    /** v2.2 used "admin"; rename it to "chain" keeping its password and progress */
    private void migrateOldAdmin() {
        ArrayList<String> list = users();
        if (!list.contains("admin") || list.contains(ADMIN_USER) || !sp.getBoolean("admin_admin", false)) return;
        list.set(list.indexOf("admin"), ADMIN_USER);
        SharedPreferences.Editor ed = sp.edit()
                .putString("users", join(list))
                .putString("salt_" + ADMIN_USER, sp.getString("salt_admin", ""))
                .putString("hash_" + ADMIN_USER, sp.getString("hash_admin", ""))
                .putBoolean("admin_" + ADMIN_USER, true)
                .putBoolean("must_" + ADMIN_USER, sp.getBoolean("must_admin", true))
                .putString("prefs_" + ADMIN_USER, sp.getString("prefs_admin", "stella_u_admin"))
                .putString("disp_" + ADMIN_USER, ADMIN_DISPLAY)
                .remove("salt_admin").remove("hash_admin").remove("admin_admin").remove("must_admin").remove("prefs_admin");
        if ("admin".equals(sp.getString("current", null))) ed.putString("current", ADMIN_USER);
        ed.apply();
    }

    // ------------------------------------------------------------------ queries
    /** name as the player typed it (keeps upper case) */
    String display(String u) {
        u = norm(u);
        return sp.getString("disp_" + u, u);
    }

    ArrayList<String> users() {
        ArrayList<String> out = new ArrayList<>();
        String s = sp.getString("users", "");
        if (s == null || s.isEmpty()) return out;
        for (String x : s.split(",")) if (!x.isEmpty()) out.add(x);
        return out;
    }

    boolean exists(String u) { return users().contains(norm(u)); }

    boolean isAdmin(String u) { return sp.getBoolean("admin_" + norm(u), false); }

    boolean isBanned(String u) { return sp.getBoolean("ban_" + norm(u), false); }

    boolean mustChange(String u) { return sp.getBoolean("must_" + norm(u), false); }

    String current() {
        String u = sp.getString("current", null);
        return (u != null && exists(u) && !isBanned(u)) ? u : null;
    }

    boolean lang() { return sp.getInt("lang", 0) == 1; }

    void setLang(boolean en) { sp.edit().putInt("lang", en ? 1 : 0).apply(); }

    boolean sfxOn() { return sp.getBoolean("sfx", true); }

    void setSfx(boolean on) { sp.edit().putBoolean("sfx", on).apply(); }

    int volume() { return sp.getInt("vol", 100); }

    void setVolume(int v) { sp.edit().putInt("vol", v).apply(); }

    boolean sfxBattle() { return sp.getBoolean("sfxBattle", true); }

    void setSfxBattle(boolean on) { sp.edit().putBoolean("sfxBattle", on).apply(); }

    boolean sfxUi() { return sp.getBoolean("sfxUi", true); }

    void setSfxUi(boolean on) { sp.edit().putBoolean("sfxUi", on).apply(); }

    boolean infiniteSP() { return sp.getBoolean("dbg_sp", false); }

    void setInfiniteSP(boolean b) { sp.edit().putBoolean("dbg_sp", b).apply(); }

    SharedPreferences prefsOf(String u) {
        return ctx.getSharedPreferences(sp.getString("prefs_" + norm(u), "stella_u_" + norm(u)), Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ actions (return error text or null)
    static String checkName(String u) {
        if (u.length() < 3 || u.length() > 16) return L.t("ชื่อไอดีต้องยาว 3–16 ตัว", "Username must be 3–16 characters");
        // (letters are checked in lower case; the typed upper/lower case is kept for display)
        for (int i = 0; i < u.length(); i++) {
            char ch = u.charAt(i);
            if (!((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_'))
                return L.t("ชื่อไอดีใช้ได้แค่ a-z, 0-9 และ _", "Username may only use a-z, 0-9 and _");
        }
        return null;
    }

    static String checkPass(String p) {
        if (p == null || p.length() < 4) return L.t("รหัสผ่านต้องยาวอย่างน้อย 4 ตัว", "Password must be at least 4 characters");
        if (p.length() > 64) return L.t("รหัสผ่านยาวเกินไป", "Password is too long");
        return null;
    }

    private void create(String typed, String pass, boolean admin) {
        String u = norm(typed);
        String salt = newSalt();
        ArrayList<String> list = users();
        list.add(u);
        // the first normal account keeps progress saved by older versions of the game
        String prefs = "stella_u_" + u;
        if (!admin && !sp.getBoolean("legacy_claimed", false)) {
            prefs = "stella";
            sp.edit().putBoolean("legacy_claimed", true).apply();
        }
        sp.edit()
                .putString("users", join(list))
                .putString("salt_" + u, salt)
                .putString("hash_" + u, hash(salt, pass))
                .putBoolean("admin_" + u, admin)
                .putString("prefs_" + u, prefs)
                .putString("disp_" + u, typed.trim())
                .apply();
    }

    String register(String typed, String pass, String confirm) {
        String u = norm(typed);
        String e = checkName(u);
        if (e != null) return e;
        if (exists(u)) return L.t("มีไอดีนี้แล้ว", "That username is taken");
        e = checkPass(pass);
        if (e != null) return e;
        if (!pass.equals(confirm)) return L.t("รหัสผ่านทั้งสองช่องไม่ตรงกัน", "Passwords do not match");
        create(typed, pass, false);
        return null;
    }

    String login(String u, String pass) {
        u = norm(u);
        if (!exists(u)) return L.t("ไม่พบไอดีนี้", "Account not found");
        String salt = sp.getString("salt_" + u, "");
        if (!hash(salt, pass == null ? "" : pass).equals(sp.getString("hash_" + u, "")))
            return L.t("รหัสผ่านไม่ถูกต้อง", "Wrong password");
        if (isBanned(u)) return L.t("บัญชีนี้ถูกระงับโดยแอดมิน", "This account was banned by an admin");
        sp.edit().putString("current", u).apply();
        return null;
    }

    void logout() { sp.edit().remove("current").apply(); }

    String changePassword(String u, String oldPass, String newPass, String confirm) {
        u = norm(u);
        String salt = sp.getString("salt_" + u, "");
        if (!hash(salt, oldPass == null ? "" : oldPass).equals(sp.getString("hash_" + u, "")))
            return L.t("รหัสผ่านเดิมไม่ถูกต้อง", "Current password is wrong");
        if (!newPass.equals(confirm)) return L.t("รหัสผ่านใหม่ทั้งสองช่องไม่ตรงกัน", "New passwords do not match");
        if (isAdmin(u) && newPass.equals(ADMIN_DEFAULT_PASS))
            return L.t("ห้ามใช้รหัสเริ่มต้น", "Do not reuse the default password");
        return setPassword(u, newPass);
    }

    /** admin action: set a new password without knowing the old one */
    String setPassword(String u, String newPass) {
        u = norm(u);
        String e = checkPass(newPass);
        if (e != null) return e;
        String salt = newSalt();
        sp.edit().putString("salt_" + u, salt).putString("hash_" + u, hash(salt, newPass)).putBoolean("must_" + u, false).apply();
        return null;
    }

    void setBanned(String u, boolean b) {
        if (isAdmin(u)) return;
        sp.edit().putBoolean("ban_" + norm(u), b).apply();
    }

    String delete(String u) {
        u = norm(u);
        if (isAdmin(u)) return L.t("ลบบัญชีแอดมินไม่ได้", "The admin account can't be deleted");
        if (!exists(u)) return L.t("ไม่พบไอดีนี้", "Account not found");
        prefsOf(u).edit().clear().apply();
        ArrayList<String> list = users();
        list.remove(u);
        sp.edit().putString("users", join(list))
                .remove("salt_" + u).remove("hash_" + u).remove("admin_" + u)
                .remove("ban_" + u).remove("must_" + u).remove("prefs_" + u).remove("disp_" + u).apply();
        return null;
    }
}
