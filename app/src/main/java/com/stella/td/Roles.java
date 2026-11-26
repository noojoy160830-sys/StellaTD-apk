package com.stella.td;

/**
 * Staff ranks. Stored in the database as roles/{uid} = "owner" | "dev" | "admin" | "mod" | "staff".
 * Ranks are given ONLY from the Stella Console by the console account (console@console.stellatd.game).
 *
 *   staff  - can open the console, see players, profiles and logs
 *   mod    - + ban / unban / kick
 *   admin  - + edit players (currencies, planets, levels, enchants, reset, wipe)
 *   dev    - + server settings (maintenance, broadcast, min version)
 *   owner  - everything a player rank can do (giving ranks stays with the console account)
 */
final class Roles {
    private Roles() {}

    static final int NONE = 0, STAFF = 1, MOD = 2, ADMIN = 3, DEV = 4, OWNER = 5;
    static final String[] KEY = {"", "staff", "mod", "admin", "dev", "owner"};
    static final String[] NAME = {"Player", "Staff", "Mod", "Admin", "Dev", "Owner"};
    static final int[] COLOR = {0xFF9C94C8, 0xFF7DD3FF, 0xFF4DFF8A, 0xFFFF8A2B, 0xFFB86BFF, 0xFFFFD23F};

    static int of(Object v) {
        if (v == null) return NONE;
        String s = v.toString().trim().toLowerCase(java.util.Locale.US);
        for (int i = 1; i < KEY.length; i++) if (KEY[i].equals(s)) return i;
        return NONE;
    }

    /** only the console account gives ranks */
    static boolean canAssign(boolean consoleOwner) { return consoleOwner; }

    static boolean canView(int r) { return r >= STAFF; }
    static boolean canBan(int r) { return r >= MOD; }
    static boolean canEdit(int r) { return r >= ADMIN; }
    static boolean canServer(int r) { return r >= DEV; }
}
