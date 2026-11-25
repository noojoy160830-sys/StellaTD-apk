package com.stella.td;

import android.content.SharedPreferences;

import java.util.Random;

/** Everything the player owns between matches. Saved in SharedPreferences. */
final class Profile {
    static final int PULL1 = 100, PULL10 = 900, ROLL_COST = 1, CRYSTAL_COST = 1, MAX_LV = 10;
    static final int DECK_SIZE = 4;

    private final Store sp;
    final Random rnd = new Random();
    /** called after every save (the logged-in profile uses it to schedule a cloud upload) */
    Runnable onSave;
    long saveTime;

    int stars, millite, crystals, unlocked, bossKills, games, wins, playSec, avatar;
    final boolean[] owned = new boolean[Defs.P_COUNT];
    final int[] copies = new int[Defs.P_COUNT];
    final int[] level = new int[Defs.P_COUNT];
    final int[] enchant = new int[Defs.P_COUNT];
    final int[] tier = new int[Defs.P_COUNT];
    final int[] material = new int[Defs.P_COUNT];
    final int[] deck = new int[DECK_SIZE];
    final int[] best = new int[Defs.ST_NAME.length];
    final boolean[] clear = new boolean[Defs.ST_NAME.length];
    boolean[] lastNew = new boolean[0];

    Profile(SharedPreferences sp) {
        this(new Store.Prefs(sp));
    }

    Profile(Store st) {
        this.sp = st;
        load();
    }

    // ------------------------------------------------------------------ persistence
    private static int[] ints(String s, int n, int def) {
        int[] a = new int[n];
        java.util.Arrays.fill(a, def);
        if (s == null || s.isEmpty()) return a;
        String[] parts = s.split(",");
        for (int i = 0; i < n && i < parts.length; i++) {
            try { a[i] = Integer.parseInt(parts[i].trim()); } catch (NumberFormatException ignored) { }
        }
        return a;
    }

    private static String str(int[] a) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) b.append(',');
            b.append(a[i]);
        }
        return b.toString();
    }

    void load() {
        boolean fresh = !sp.getBoolean("v2", false);
        saveTime = Long.parseLong(sp.getString("saveTime", "0"));
        stars = sp.getInt("stars", 300);
        millite = sp.getInt("millite", 5);
        crystals = sp.getInt("crystals", sp.getInt("tickets", 0));
        wins = sp.getInt("wins", 0);
        avatar = sp.getInt("avatar", 0);
        playSec = sp.getInt("playSec", 0);
        unlocked = Math.max(1, sp.getInt("unlocked", 1));
        bossKills = sp.getInt("bossKills", 0);
        games = sp.getInt("games", 0);
        int[] own = ints(sp.getString("owned", null), Defs.P_COUNT, 0);
        int[] cp = ints(sp.getString("copies", null), Defs.P_COUNT, 0);
        int[] lv = ints(sp.getString("level", null), Defs.P_COUNT, 1);
        int[] en = ints(sp.getString("enchant", null), Defs.P_COUNT, -1);
        int[] ti = ints(sp.getString("tier", null), Defs.P_COUNT, 0);
        int[] ef = ints(sp.getString("material", null), Defs.P_COUNT, -1);
        int[] dk = ints(sp.getString("deck", null), DECK_SIZE, -1);
        int[] bs = ints(sp.getString("best", null), best.length, 0);
        int[] cl = ints(sp.getString("clear", null), clear.length, 0);
        for (int i = 0; i < Defs.P_COUNT; i++) {
            owned[i] = own[i] == 1;
            copies[i] = cp[i];
            level[i] = Math.max(1, Math.min(MAX_LV, lv[i]));
            enchant[i] = en[i];
            tier[i] = ti[i];
            material[i] = ef[i];
        }
        System.arraycopy(dk, 0, deck, 0, DECK_SIZE);
        System.arraycopy(bs, 0, best, 0, best.length);
        for (int i = 0; i < clear.length; i++) clear[i] = cl[i] == 1;
        if (!sp.getBoolean("v3", false)) {
            // v3 replaced the old enchant list and effects with new enchants and materials
            java.util.Arrays.fill(enchant, -1);
            java.util.Arrays.fill(tier, 0);
            java.util.Arrays.fill(material, -1);
        }
        if (fresh) {
            owned[Defs.EARTH] = owned[Defs.MOON] = owned[Defs.SATELLITE] = true;
            deck[0] = Defs.EARTH;
            deck[1] = Defs.MOON;
            deck[2] = Defs.SATELLITE;
            deck[3] = -1;
            save();
        }
    }

    java.util.LinkedHashMap<String, Object> toMap() {
        int[] own = new int[Defs.P_COUNT];
        for (int i = 0; i < own.length; i++) own[i] = owned[i] ? 1 : 0;
        int[] cl = new int[clear.length];
        for (int i = 0; i < cl.length; i++) cl[i] = clear[i] ? 1 : 0;
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("v2", true);
        m.put("v3", true);
        m.put("saveTime", String.valueOf(saveTime));
        m.put("stars", stars);
        m.put("millite", millite);
        m.put("crystals", crystals);
        m.put("wins", wins);
        m.put("avatar", avatar);
        m.put("playSec", playSec);
        m.put("unlocked", unlocked);
        m.put("bossKills", bossKills);
        m.put("games", games);
        m.put("owned", str(own));
        m.put("copies", str(copies));
        m.put("level", str(level));
        m.put("enchant", str(enchant));
        m.put("tier", str(tier));
        m.put("material", str(material));
        m.put("deck", str(deck));
        m.put("best", str(best));
        m.put("clear", str(cl));
        return m;
    }

    /** replace this profile with values from the cloud (numbers arrive as doubles) */
    void fromMap(java.util.Map<String, Object> remote) {
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, Object> e : remote.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Double) v = ((Double) v).intValue();
            m.put(e.getKey(), v);
        }
        sp.write(m, true);
        load();
    }

    void save() {
        saveTime = System.currentTimeMillis();
        sp.write(toMap(), false);
        if (onSave != null) onSave.run();
    }

    /** wipe all progress back to a brand-new account */
    void reset() {
        sp.write(new java.util.HashMap<String, Object>(), true);
        java.util.Arrays.fill(owned, false);
        java.util.Arrays.fill(copies, 0);
        load();
    }

    void unlockAllPlanets() {
        for (int t = 0; t < Defs.P_COUNT; t++) owned[t] = true;
        save();
    }

    void maxAllPlanets() {
        for (int t = 0; t < Defs.P_COUNT; t++) if (owned[t]) level[t] = MAX_LV;
        save();
    }

    void unlockAllStages() {
        unlocked = Defs.ST_NAME.length;
        save();
    }

    int ownedCount() {
        int n = 0;
        for (boolean b : owned) if (b) n++;
        return n;
    }

    // ------------------------------------------------------------------ banner
    static final float[] PULL_RATE = {45f, 30f, 14f, 9.5f, 1.5f};

    static int[] typesOfRarity(int r) {
        int n = 0;
        for (int t = 0; t < Defs.P_COUNT; t++) if (Defs.P_RARITY[t] == r) n++;
        int[] out = new int[n];
        n = 0;
        for (int t = 0; t < Defs.P_COUNT; t++) if (Defs.P_RARITY[t] == r) out[n++] = t;
        return out;
    }

    /** @return pulled planet types, or null if not enough stars */
    int[] pull(int count) {
        int cost = count >= 10 ? PULL10 : PULL1 * count;
        if (stars < cost) return null;
        stars -= cost;
        int[] res = new int[count];
        lastNew = new boolean[count];
        for (int i = 0; i < count; i++) {
            float r = rnd.nextFloat() * 100f;
            int rar = 0;
            for (int k = 0; k < PULL_RATE.length; k++) {
                r -= PULL_RATE[k];
                if (r <= 0) { rar = k; break; }
                rar = k;
            }
            int[] pool = typesOfRarity(rar);
            int t = pool[rnd.nextInt(pool.length)];
            res[i] = t;
            if (owned[t]) copies[t]++;
            else {
                owned[t] = true;
                lastNew[i] = true;
                autoEquip(t);
            }
        }
        save();
        return res;
    }

    private void autoEquip(int t) {
        for (int i = 0; i < DECK_SIZE; i++) {
            if (deck[i] == t) return;
        }
        for (int i = 0; i < DECK_SIZE; i++) {
            if (deck[i] < 0) { deck[i] = t; return; }
        }
    }

    // ------------------------------------------------------------------ factory (enchant) & lab (material)
    static int pick(float[] chances, Random rnd) {
        float r = rnd.nextFloat() * 100f;
        for (int i = 0; i < chances.length; i++) {
            r -= chances[i];
            if (r <= 0) return i;
        }
        return 0;
    }

    boolean rollEnchant(int t) {
        if (!owned[t] || millite < ROLL_COST) return false;
        millite -= ROLL_COST;
        int e = pick(Defs.EN_CHANCE, rnd);
        enchant[t] = e;
        int n = Defs.EN_TIERS[e];
        if (n == 3) {
            float x = rnd.nextFloat();
            tier[t] = x < 0.6f ? 1 : (x < 0.9f ? 2 : 3);
        } else if (n == 2) tier[t] = rnd.nextFloat() < 0.7f ? 1 : 2;
        else tier[t] = 0;
        save();
        return true;
    }

    boolean rollMaterial(int t) {
        if (!owned[t] || crystals < CRYSTAL_COST) return false;
        crystals -= CRYSTAL_COST;
        material[t] = pick(Defs.MAT_CHANCE, rnd);
        save();
        return true;
    }

    /** admin: give one planet (a new planet or one extra copy) */
    void givePlanet(int t) {
        if (owned[t]) copies[t]++;
        else {
            owned[t] = true;
            autoEquip(t);
        }
        save();
    }

    // ------------------------------------------------------------------ upgrade & deck
    int upgradeCopies(int t) { return level[t]; }

    int upgradeStars(int t) { return 40 * level[t] * (1 + Defs.P_RARITY[t]); }

    /** @return null on success, otherwise an error message */
    String upgrade(int t) {
        if (!owned[t]) return L.t("ยังไม่มีดวงดาวนี้", "You don't own this planet");
        if (level[t] >= MAX_LV) return L.t("เลเวลสูงสุดแล้ว", "Already max level");
        if (copies[t] < upgradeCopies(t)) return L.t("การ์ดซ้ำไม่พอ", "Not enough copies");
        if (stars < upgradeStars(t)) return L.t("Star ไม่พอ", "Not enough Star");
        copies[t] -= upgradeCopies(t);
        stars -= upgradeStars(t);
        level[t]++;
        save();
        return null;
    }

    boolean inDeck(int t) {
        for (int d : deck) if (d == t) return true;
        return false;
    }

    /** toggles the planet in the deck; returns error or null */
    String toggleDeck(int t) {
        if (!owned[t]) return L.t("ยังไม่มีดวงดาวนี้", "You don't own this planet");
        for (int i = 0; i < DECK_SIZE; i++) {
            if (deck[i] == t) { deck[i] = -1; save(); return null; }
        }
        for (int i = 0; i < DECK_SIZE; i++) {
            if (deck[i] < 0) { deck[i] = t; save(); return null; }
        }
        return L.t("Deck เต็มแล้ว (ใส่ได้ 4 ดวง)", "Deck is full (4 planets)");
    }

    int deckCount() {
        int n = 0;
        for (int d : deck) if (d >= 0) n++;
        return n;
    }

    void addRewards(Game g) {
        stars += g.earnStars;
        millite += g.earnMillite;
        crystals += g.earnCrystal;
        if (g.won) wins++;
        bossKills += g.bossKills;
        games++;
        int reached = g.won ? g.wave : Math.max(0, g.wave - 1);
        if (reached > best[g.stage]) best[g.stage] = reached;
        if (g.won) {
            clear[g.stage] = true;
            unlocked = Math.max(unlocked, Math.min(Defs.ST_NAME.length, g.stage + 2));
        }
        save();
    }
}
