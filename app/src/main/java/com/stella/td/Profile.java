package com.stella.td;

import android.content.SharedPreferences;

import java.util.Random;

/** Everything the player owns between matches. Saved in SharedPreferences. */
final class Profile {
    static final int PULL1 = 100, PULL10 = 900, ROLL_COST = 1, CRYSTAL_COST = 1, MAX_LV = 10;
    /** 4 normal slots + 1 slot (index Defs.SPECIAL_SLOT) only for Specialist planets */
    static final int DECK_SIZE = 5;

    private final Store sp;
    final Random rnd = new Random();
    /** called after every save (the logged-in profile uses it to schedule a cloud upload) */
    Runnable onSave;
    long saveTime;

    int stars, millite, crystals, unlocked, bossKills, games, wins, playSec, avatar, giftDay;
    /** Crystal Oil (from Raid) pays for Exclusive capsules and the Raid shop */
    int oil, raidBest, raidCapsules;
    /** owned skins (Skins.key) and the skin style worn by each planet (-1 = none) */
    final java.util.TreeSet<Integer> skins = new java.util.TreeSet<>();
    final int[] skinOn = new int[Defs.P_COUNT];
    /** moons owned (by Defs.MOON_*) and the moon equipped on each planet (-1 = none) */
    final int[] moons = new int[Defs.MOON_COUNT];
    final int[] moonOn = new int[Defs.P_COUNT];
    final boolean[] evolved = new boolean[Defs.P_COUNT];
    /** Admin Abuse effect worn by each planet (-1 = none) */
    final int[] effect = new int[Defs.P_COUNT];
    /** Gold Coin: earned by trading / selling in the Plaza, spent in the Gold shop */
    int gold;
    int pvpWins;
    boolean goldStart;
    /** Admin Abuse claims: event id + bitmask of cleared effect stages, last claimed gift id, last boss reward id */
    String abuseId = "";
    int abuseMask;
    String giftClaimed = "", bossClaimed = "";
    /** my damage on the current Admin boss */
    String bossDmgId = "";
    long bossDmg;
    // daily missions + login calendar
    int missionDay, loginStreak;
    // bag items (Items), challenge completion stamps
    final int[] items = new int[Items.COUNT];
    int dailyDone, weeklyDone;
    /** last challenge reward text (shown on the result screen) */
    String lastChallengeReward;
    final int[] missionType = new int[Missions.PER_DAY], missionProg = new int[Missions.PER_DAY];
    final boolean[] missionDone = new boolean[Missions.PER_DAY];
    boolean missionBonus;
    /** Base64 JPEG profile photo (used when avatar == -1) */
    String photo;
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
        giftDay = sp.getInt("giftDay", 0);
        oil = sp.getInt("oil", 0);
        raidBest = sp.getInt("raidBest", 0);
        raidCapsules = sp.getInt("raidCapsules", 0);
        skins.clear();
        for (int k : ints(sp.getString("skins", null), 400, -1)) if (k >= 0) skins.add(k);
        int[] so = ints(sp.getString("skinOn", null), Defs.P_COUNT, -1);
        System.arraycopy(so, 0, skinOn, 0, Defs.P_COUNT);
        int[] mo = ints(sp.getString("moons", null), Defs.MOON_COUNT, 0);
        System.arraycopy(mo, 0, moons, 0, Defs.MOON_COUNT);
        int[] it = ints(sp.getString("items", null), Items.COUNT, 0);
        System.arraycopy(it, 0, items, 0, Items.COUNT);
        dailyDone = sp.getInt("dailyDone", 0);
        weeklyDone = sp.getInt("weeklyDone", 0);
        missionDay = sp.getInt("missionDay", 0);
        loginStreak = sp.getInt("loginStreak", 0);
        missionBonus = sp.getBoolean("missionBonus", false);
        int[] mt = ints(sp.getString("missionType", null), Missions.PER_DAY, 0);
        int[] mp = ints(sp.getString("missionProg", null), Missions.PER_DAY, 0);
        int[] md = ints(sp.getString("missionDone", null), Missions.PER_DAY, 0);
        for (int i = 0; i < Missions.PER_DAY; i++) {
            missionType[i] = Math.max(0, Math.min(Missions.COUNT - 1, mt[i]));
            missionProg[i] = mp[i];
            missionDone[i] = md[i] == 1;
        }
        int[] ev = ints(sp.getString("evolved", null), Defs.P_COUNT, 0);
        for (int i = 0; i < Defs.P_COUNT; i++) evolved[i] = ev[i] == 1 && Defs.canEvolve(i);
        int[] efx = ints(sp.getString("effect", null), Defs.P_COUNT, -1);
        for (int i = 0; i < Defs.P_COUNT; i++) effect[i] = efx[i] >= 0 && efx[i] < Defs.EFF_COUNT ? efx[i] : -1;
        gold = sp.getInt("gold", 0);
        pvpWins = sp.getInt("pvpWins", 0);
        // everyone starts with 100 Gold Coins (once)
        if (!sp.getBoolean("goldStart", false)) { gold += 100; goldStart = true; }
        else goldStart = true;
        abuseId = sp.getString("abuseId", "");
        abuseMask = sp.getInt("abuseMask", 0);
        giftClaimed = sp.getString("giftClaimed", "");
        bossClaimed = sp.getString("bossClaimed", "");
        bossDmgId = sp.getString("bossDmgId", "");
        try { bossDmg = Long.parseLong(sp.getString("bossDmg", "0")); } catch (NumberFormatException e) { bossDmg = 0; }
        int[] mq = ints(sp.getString("moonOn", null), Defs.P_COUNT, -1);
        System.arraycopy(mq, 0, moonOn, 0, Defs.P_COUNT);
        photo = sp.getString("photo", null);
        if (avatar < 0 && (photo == null || photo.isEmpty())) avatar = 0;
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
            // write the starter kit without stamping a save time: a fresh install must never look
            // newer than the account's cloud save (that used to wipe progress after reinstalling)
            sp.write(toMap(), false);
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
        m.put("giftDay", giftDay);
        m.put("oil", oil);
        m.put("raidBest", raidBest);
        m.put("raidCapsules", raidCapsules);
        int[] sk = new int[skins.size()];
        int si = 0;
        for (int k : skins) sk[si++] = k;
        m.put("skins", str(sk));
        m.put("skinOn", str(skinOn));
        m.put("moons", str(moons));
        m.put("moonOn", str(moonOn));
        m.put("items", str(items));
        m.put("dailyDone", dailyDone);
        m.put("weeklyDone", weeklyDone);
        m.put("missionDay", missionDay);
        m.put("loginStreak", loginStreak);
        m.put("missionBonus", missionBonus);
        m.put("missionType", str(missionType));
        m.put("missionProg", str(missionProg));
        int[] mdn = new int[Missions.PER_DAY];
        for (int i = 0; i < Missions.PER_DAY; i++) mdn[i] = missionDone[i] ? 1 : 0;
        m.put("missionDone", str(mdn));
        int[] evs = new int[Defs.P_COUNT];
        for (int i = 0; i < Defs.P_COUNT; i++) evs[i] = evolved[i] ? 1 : 0;
        m.put("evolved", str(evs));
        m.put("effect", str(effect));
        m.put("gold", gold);
        m.put("pvpWins", pvpWins);
        m.put("goldStart", goldStart);
        m.put("abuseId", abuseId == null ? "" : abuseId);
        m.put("abuseMask", abuseMask);
        m.put("giftClaimed", giftClaimed == null ? "" : giftClaimed);
        m.put("bossClaimed", bossClaimed == null ? "" : bossClaimed);
        m.put("bossDmgId", bossDmgId == null ? "" : bossDmgId);
        m.put("bossDmg", String.valueOf(bossDmg));
        m.put("photo", photo == null ? "" : photo);
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

    /** true for a profile nobody has played yet (starter kit only) */
    boolean pristine() {
        return games == 0 && wins == 0 && playSec < 30 && ownedCount() <= 3 && unlocked <= 1;
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

    /** Star paid when a planet is sold, by rarity (grows with its level) */
    static final int[] SELL_BASE = {40, 120, 350, 900, 2200, 5000, 0, 0};

    int sellValue(int t) {
        int base = SELL_BASE[Defs.P_RARITY[t]];
        int v = base + base * (level[t] - 1) / 3 + base * copies[t] / 2;
        return effect[t] >= 0 ? Math.round(v * Defs.EFF_VALUE[effect[t]]) : v;
    }

    /** Gold Coin a planet is worth in the Plaza (rarity, level, copies, effect multiplier) */
    static final int[] GOLD_BASE = {1, 3, 8, 18, 40, 90, 150, 150};

    int goldValue(int t) {
        int base = GOLD_BASE[Defs.P_RARITY[t]];
        float v = base * (1f + (level[t] - 1) * 0.25f + copies[t] * 0.3f) * (evolved[t] ? 1.5f : 1f);
        if (effect[t] >= 0) v *= Defs.EFF_VALUE[effect[t]];
        return Math.max(1, Math.round(v));
    }

    /** sell a planet to the system for Gold Coin (Plaza quick-sell) */
    int sellForGold(int t) {
        if (!canSell(t)) return 0;
        int v = goldValue(t);
        strip(t);
        gold += v;
        save();
        return v;
    }

    /** remove a planet with everything on it (sold / traded away) */
    void strip(int t) {
        owned[t] = false;
        copies[t] = 0;
        level[t] = 1;
        enchant[t] = -1;
        tier[t] = 0;
        material[t] = -1;
        moonOn[t] = -1;
        evolved[t] = false;
        effect[t] = -1;
        skinOn[t] = -1;
        for (int i = 0; i < deck.length; i++) if (deck[i] == t) deck[i] = -1;
        if (deckCount() == 0) for (int k = 0; k < Defs.P_COUNT; k++) if (owned[k]) { deck[0] = k; break; }
    }

    /** buy from the Gold shop; returns the text of what was bought, or null */
    String buyGold(int i) {
        int[] it = Defs.GOLD_SHOP[i];
        if (gold < it[2]) return null;
        gold -= it[2];
        String s;
        if (it[0] == 9) { millite += it[1]; s = it[1] + " Millite"; }
        else s = grant(it[0], it[1]);
        save();
        return s;
    }

    /** Gold paid by the system for one of a bag item (Plaza quick-sell); 0 = can't be sold */
    static int itemGold(int id) {
        switch (id) {
            case Items.STARDUST: return 1;
            case Items.NEBULA: return 4;
            case Items.CATALYST: return 18;
            case Items.ART_CAPSULE: return 10;
            case Items.SUPPLY: return 3;
            case Items.ADMIN_CAPSULE: return 40;
            default: return Items.isEffect(id) ? Math.round(30 * Defs.EFF_VALUE[id - Items.EFF_TOKEN]) : 0;
        }
    }

    boolean sellItemForGold(int id) {
        int v = itemGold(id);
        if (v <= 0 || items[id] <= 0) return false;
        items[id]--;
        gold += v;
        save();
        return true;
    }

    /** put an effect token on a planet (replaces the old effect) */
    boolean applyEffect(int t, int e) {
        int id = Items.EFF_TOKEN + e;
        if (!owned[t] || items[id] <= 0) return false;
        items[id]--;
        effect[t] = e;
        save();
        return true;
    }

    // ---------------------------------------------------------------- tradeable things
    // "g:n" gold, "i:id:n" item, "a:m:n" artifact, "s:key" skin, "p:t:level:copies:effect:evolved" planet
    String describe(String th) {
        String[] p = th.split(":");
        try {
            switch (p[0]) {
                case "g": return p[1] + " Gold";
                case "i": return p[2] + "x " + Items.NAME[Integer.parseInt(p[1])];
                case "a": return p[2] + "x " + Defs.MOON_NAME[Integer.parseInt(p[1])];
                case "s": return Skins.name(Integer.parseInt(p[1])) + " skin";
                case "p": {
                    int e = Integer.parseInt(p[4]);
                    return (e >= 0 ? Defs.EFF_NAME[e] + " " : "") + Defs.P_NAME[Integer.parseInt(p[1])] + " Lv" + p[2];
                }
            }
        } catch (RuntimeException ignored) { }
        return th;
    }

    /** can this profile give the thing away now? */
    boolean canGive(String th) {
        String[] p = th.split(":");
        try {
            switch (p[0]) {
                case "g": int n = Integer.parseInt(p[1]); return n > 0 && gold >= n;
                case "i": int c = Integer.parseInt(p[2]); return c > 0 && items[Integer.parseInt(p[1])] >= c;
                case "a": {
                    int m = Integer.parseInt(p[1]), k = Integer.parseInt(p[2]);
                    int used = 0;
                    for (int x : moonOn) if (x == m) used++;
                    return k > 0 && moons[m] - used >= k;
                }
                case "s": {
                    int key = Integer.parseInt(p[1]);
                    return skins.contains(key) && Skins.styleOf(key) != Skins.ANNIV;
                }
                case "p": return canSell(Integer.parseInt(p[1]));
            }
        } catch (RuntimeException ignored) { }
        return false;
    }

    /** encode one of this profile's planets as a tradeable thing */
    String planetThing(int t) {
        return "p:" + t + ":" + level[t] + ":" + copies[t] + ":" + effect[t] + ":" + (evolved[t] ? 1 : 0);
    }

    /** remove a thing from this profile (escrow); returns false if it can't be given */
    boolean giveAway(String th) {
        if (!canGive(th)) return false;
        String[] p = th.split(":");
        switch (p[0]) {
            case "g": gold -= Integer.parseInt(p[1]); break;
            case "i": items[Integer.parseInt(p[1])] -= Integer.parseInt(p[2]); break;
            case "a": moons[Integer.parseInt(p[1])] -= Integer.parseInt(p[2]); break;
            case "s": {
                int key = Integer.parseInt(p[1]);
                skins.remove(key);
                int pl = Skins.planetOf(key);
                if (skinOn[pl] == Skins.styleOf(key)) skinOn[pl] = -1;
                break;
            }
            case "p": strip(Integer.parseInt(p[1])); break;
        }
        save();
        return true;
    }

    /** add a thing to this profile (trade / market delivery); returns the text shown */
    String receive(String th) {
        String[] p = th.split(":");
        try {
            switch (p[0]) {
                case "g": gold += Integer.parseInt(p[1]); break;
                case "i": items[Integer.parseInt(p[1])] += Integer.parseInt(p[2]); break;
                case "a": moons[Integer.parseInt(p[1])] += Integer.parseInt(p[2]); break;
                case "s": skins.add(Integer.parseInt(p[1])); break;
                case "p": {
                    int t = Integer.parseInt(p[1]);
                    int lv = Math.max(1, Math.min(MAX_LV, Integer.parseInt(p[2])));
                    int cp = Math.max(0, Integer.parseInt(p[3]));
                    int e = Integer.parseInt(p[4]);
                    if (owned[t]) {
                        // already owned: keep the better copy, fold the rest into copies
                        level[t] = Math.max(level[t], lv);
                        copies[t] += cp + 1;
                        if (e >= 0 && (effect[t] < 0 || Defs.EFF_VALUE[e] > Defs.EFF_VALUE[effect[t]])) effect[t] = e;
                    } else {
                        owned[t] = true;
                        level[t] = lv;
                        copies[t] = cp;
                        effect[t] = e >= 0 && e < Defs.EFF_COUNT ? e : -1;
                    }
                    if ("1".equals(p[5]) && Defs.canEvolve(t)) evolved[t] = true;
                    break;
                }
            }
        } catch (RuntimeException ignored) { return ""; }
        save();
        return describe(th);
    }

    /** exclusives can't be sold, and you must keep at least one planet */
    boolean canSell(int t) {
        if (!owned[t] || Defs.P_RARITY[t] >= Defs.EXCLUSIVE) return false;
        return ownedCount() > 1;
    }

    /** sell a planet: it is removed with its level, enchant and material; returns the Star earned */
    int sell(int t) {
        if (!canSell(t)) return 0;
        int v = sellValue(t);
        stars += v;
        strip(t);
        save();
        return v;
    }

    static final int GIFT_STARS = 200;

    static int today() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        return cal.get(java.util.Calendar.YEAR) * 400 + cal.get(java.util.Calendar.DAY_OF_YEAR);
    }

    boolean giftReady() { return giftDay != today(); }

    /** id of the current week (Monday start) */
    static int week() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setFirstDayOfWeek(java.util.Calendar.MONDAY);
        cal.setMinimalDaysInFirstWeek(4);
        return cal.get(java.util.Calendar.YEAR) * 60 + cal.get(java.util.Calendar.WEEK_OF_YEAR);
    }

    boolean dailyReady() { return dailyDone != today(); }

    boolean weeklyReady() { return weeklyDone != week(); }

    // ---------------------------------------------------------------- bag items
    /** open an Artifact Capsule; returns the artifact index or -1 */
    int openArtifactCapsule() {
        if (items[Items.ART_CAPSULE] <= 0) return -1;
        items[Items.ART_CAPSULE]--;
        int a = Items.rollArtifact(rnd);
        moons[a]++;
        track(Missions.PULL, 1);
        save();
        return a;
    }

    /** open a Supply Capsule; returns {kind, amount} (kind as in Items.SUPPLY_TABLE) or null */
    int[] openSupply() {
        if (items[Items.SUPPLY] <= 0) return null;
        items[Items.SUPPLY]--;
        int sum = 0;
        for (int[] row : Items.SUPPLY_TABLE) sum += row[1];
        int x = rnd.nextInt(sum);
        int[] pick = Items.SUPPLY_TABLE[0];
        for (int[] row : Items.SUPPLY_TABLE) { x -= row[1]; if (x < 0) { pick = row; break; } }
        int n = pick[2] + rnd.nextInt(pick[3] - pick[2] + 1);
        switch (pick[0]) {
            case 0: stars += n; break;
            case 1: millite += n; break;
            case 2: crystals += n; break;
            case 3: items[Items.STARDUST] += n; break;
            default: items[Items.NEBULA] += n;
        }
        track(Missions.PULL, 1);
        save();
        return new int[]{pick[0], n};
    }

    /** open an Admin Capsule; returns the text of what came out, or null */
    String openAdminCapsule() {
        if (items[Items.ADMIN_CAPSULE] <= 0) return null;
        items[Items.ADMIN_CAPSULE]--;
        int sum = 0;
        for (int[] row : Items.ADMIN_TABLE) sum += row[1];
        int x = rnd.nextInt(sum);
        int[] pick = Items.ADMIN_TABLE[0];
        for (int[] row : Items.ADMIN_TABLE) { x -= row[1]; if (x < 0) { pick = row; break; } }
        int n = pick[2] + rnd.nextInt(pick[3] - pick[2] + 1);
        String s;
        switch (pick[0]) {
            case 0: gold += n; s = n + " Gold"; break;
            case 1: { int e = rnd.nextInt(Defs.EFF_COUNT); items[Items.EFF_TOKEN + e]++; s = Items.NAME[Items.EFF_TOKEN + e]; break; }
            case 2: { int a = Items.rollArtifact(rnd); moons[a]++; s = Defs.MOON_NAME[a]; break; }
            case 3: items[Items.CATALYST] += n; s = n + " Evo Catalyst"; break;
            default: {
                int k = Skins.rollAdmin(rnd);
                if (skins.contains(k)) { gold += 60; s = Skins.name(k) + " (ซ้ำ → 60 Gold)"; }
                else { skins.add(k); s = Skins.name(k); }
            }
        }
        track(Missions.PULL, 1);
        save();
        return s;
    }

    int have(int id) {
        switch (id) {
            case -1: return stars;
            case -2: return millite;
            case -3: return oil;
            default: return items[id];
        }
    }

    void take(int id, int n) {
        switch (id) {
            case -1: stars -= n; break;
            case -2: millite -= n; break;
            case -3: oil -= n; break;
            default: items[id] -= n;
        }
    }

    boolean canCraft(int r) {
        for (int[] in : Items.RECIPE_IN[r]) if (have(in[0]) < in[1]) return false;
        return true;
    }

    String craft(int r) {
        if (!canCraft(r)) return L.t("วัตถุดิบไม่พอ", "Not enough materials");
        for (int[] in : Items.RECIPE_IN[r]) take(in[0], in[1]);
        items[Items.RECIPE_OUT[r]]++;
        save();
        return null;
    }

    /** buy Raid shop row i; returns null on success */
    String buyRaidShop(int i) {
        int[] row = Defs.RAID_SHOP[i];
        if (oil < row[1]) return L.t("Crystal Oil ไม่พอ", "Not enough Crystal Oil");
        oil -= row[1];
        if (row[0] == -100) raidCapsules++;
        else if (row[0] <= -10) moons[-10 - row[0]]++;
        else items[row[0]]++;
        save();
        return null;
    }

    /** today's login-calendar slot (0..6) */
    int loginSlot() { return loginStreak % 7; }

    /** claim today's login reward; returns a description or null when already claimed */
    String claimLogin() {
        if (!giftReady()) return null;
        int[] r = Missions.LOGIN[loginSlot()];
        String s = grant(r[0], r[1]);
        if (loginSlot() == 6) { crystals += Missions.LOGIN_DAY7_CRYSTAL; s += " + " + Missions.LOGIN_DAY7_CRYSTAL + " Crystal"; }
        loginStreak++;
        giftDay = today();
        save();
        return s;
    }

    /** kind: 0 Star, 1 Crystal, 2 Crystal Oil, 3 Raid Capsule */
    String grant(int kind, int n) {
        switch (kind) {
            case 1: crystals += n; return n + " Crystal";
            case 2: oil += n; return n + " Crystal Oil";
            case 3: raidCapsules += n; return n + " Raid Capsule";
            case 4: case 5: case 6: case 7: case 8: items[kind - 4] += n; return n + " " + Items.NAME[kind - 4];
            case 10: gold += n; return n + " Gold";
            case 11: items[Items.ADMIN_CAPSULE] += n; return n + " Admin Capsule";
            case 12: case 13: case 14: case 15: case 16:
                items[Items.EFF_TOKEN + kind - 12] += n; return n + " " + Items.NAME[Items.EFF_TOKEN + kind - 12];
            default: stars += n; return n + " Star";
        }
    }

    /** roll a fresh set of missions on a new day */
    void refreshMissions() {
        int d = today();
        if (missionDay == d) return;
        missionDay = d;
        int[] t = Missions.pick(d, wins >= 15);
        for (int i = 0; i < Missions.PER_DAY; i++) { missionType[i] = t[i]; missionProg[i] = 0; missionDone[i] = false; }
        missionBonus = false;
        save();
    }

    /** add progress to every open mission of this type (RAIDWAVE keeps the best value) */
    void track(int type, int amount) {
        if (amount <= 0) return;
        refreshMissions();
        for (int i = 0; i < Missions.PER_DAY; i++) {
            if (missionType[i] != type || missionDone[i]) continue;
            missionProg[i] = type == Missions.RAIDWAVE ? Math.max(missionProg[i], amount) : missionProg[i] + amount;
            missionProg[i] = Math.min(missionProg[i], Missions.TARGET[type]);
        }
    }

    boolean missionReady(int i) { return !missionDone[i] && missionProg[i] >= Missions.TARGET[missionType[i]]; }

    boolean anyMissionClaim() {
        refreshMissions();
        for (int i = 0; i < Missions.PER_DAY; i++) if (missionReady(i)) return true;
        return bonusReady();
    }

    boolean bonusReady() {
        if (missionBonus) return false;
        for (boolean b : missionDone) if (!b) return false;
        return true;
    }

    String claimMission(int i) {
        if (!missionReady(i)) return null;
        int t = missionType[i];
        missionDone[i] = true;
        String s = grant(Missions.REWARD_KIND[t] == 2 ? 2 : Missions.REWARD_KIND[t], Missions.REWARD[t]);
        save();
        return s;
    }

    String claimBonus() {
        if (!bonusReady()) return null;
        missionBonus = true;
        crystals += Missions.BONUS_CRYSTAL;
        raidCapsules += Missions.BONUS_CAPSULE;
        items[Items.NEBULA]++;
        save();
        return Missions.BONUS_CRYSTAL + " Crystal + " + Missions.BONUS_CAPSULE + " Raid Capsule + 1 Nebula Core";
    }

    /** true when any owned planet can be upgraded right now */
    boolean anyUpgrade() {
        for (int t = 0; t < Defs.P_COUNT; t++)
            if (owned[t] && level[t] < MAX_LV && copies[t] >= upgradeCopies(t) && stars >= upgradeStars(t)) return true;
        return false;
    }

    boolean claimGift() {
        if (!giftReady()) return false;
        giftDay = today();
        stars += GIFT_STARS;
        save();
        return true;
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
        unlocked = Defs.ST_COUNT;
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
        track(Missions.PULL, count);
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
            int[] pool = Defs.regularPool(rar, Defs.bannerHour(System.currentTimeMillis()));
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
        if (isSpecialist(t)) {
            if (deck[Defs.SPECIAL_SLOT] < 0) deck[Defs.SPECIAL_SLOT] = t;
            return;
        }
        for (int i = 0; i < Defs.SPECIAL_SLOT; i++) {
            if (deck[i] < 0) { deck[i] = t; return; }
        }
    }

    // ------------------------------------------------------------------ Crystal Oil: Exclusive capsule, Raid shop, skins, moons
    int[] pullCapsule(int count) {
        int cost = count >= 10 ? Defs.CAPSULE_OIL_10 : Defs.CAPSULE_OIL * count;
        if (oil < cost) return null;
        oil -= cost;
        track(Missions.PULL, count);
        int[] res = new int[count];
        lastNew = new boolean[count];
        for (int i = 0; i < count; i++) {
            int t = Defs.CAPSULE_POOL[pick(Defs.CAPSULE_RATE, rnd)];
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

    static final int DUP_SKIN_OIL = 10;

    boolean buyCapsules(int n) {
        if (n <= 0 || oil < Defs.SHOP_CAPSULE * n) return false;
        oil -= Defs.SHOP_CAPSULE * n;
        raidCapsules += n;
        save();
        return true;
    }

    boolean buyMoon(int m) {
        if (oil < Defs.SHOP_HELLMOON) return false;
        oil -= Defs.SHOP_HELLMOON;
        moons[m]++;
        save();
        return true;
    }

    /** open one Raid capsule: returns the skin key, negative (-key-1) when it was a duplicate (refunded in oil) */
    int openCapsule() {
        if (raidCapsules <= 0) return Integer.MIN_VALUE;
        raidCapsules--;
        int k = Skins.roll(rnd);
        int res = k;
        if (skins.contains(k)) {
            oil += DUP_SKIN_OIL;
            res = -k - 1;
        } else skins.add(k);
        save();
        return res;
    }

    /** evolve a max-level planet that has an evolution; returns an error message or null */
    String evolve(int t) {
        if (!Defs.canEvolve(t)) return L.t("ดาวนี้วิวัฒน์ไม่ได้", "This planet can't evolve");
        if (!owned[t]) return L.t("ยังไม่มีดาวนี้", "You don't own it");
        if (evolved[t]) return L.t("วิวัฒน์แล้ว", "Already evolved");
        if (level[t] < MAX_LV) return L.t("ต้องอัปเกรดถึง Lv " + MAX_LV + " ก่อน", "Reach Lv " + MAX_LV + " first");
        int need = Defs.evoCatalysts(t);
        if (stars < Defs.EVO_STARS || items[Items.CATALYST] < need)
            return L.t("ต้องใช้ " + need + " Evo Catalyst + " + Defs.EVO_STARS + " Star", "Needs " + need + " Evo Catalyst + " + Defs.EVO_STARS + " Star");
        stars -= Defs.EVO_STARS;
        items[Items.CATALYST] -= need;
        evolved[t] = true;
        save();
        return null;
    }

    void equipSkin(int planet, int style) {
        skinOn[planet] = style >= 0 && skins.contains(Skins.key(style, planet)) ? style : -1;
        save();
    }

    int moonsFree(int m) {
        int used = 0;
        for (int v : moonOn) if (v == m) used++;
        return moons[m] - used;
    }

    String equipMoon(int planet, int m) {
        if (m < 0) { moonOn[planet] = -1; save(); return null; }
        if (moonOn[planet] == m) return null;
        if (moonsFree(m) <= 0)
            return L.t(Defs.MOON_NAME[m] + " ทุกชิ้นใส่อยู่แล้ว • ถอดจากดาวอื่นก่อน หรือหาเพิ่ม", "Every " + Defs.MOON_NAME[m] + " is in use • remove it from another planet or get another one");
        moonOn[planet] = m;
        save();
        return null;
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

    boolean rollEnchant(int t) { return rollEnchant(t, true); }

    boolean rollEnchant(int t, boolean doSave) {
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
        if (doSave) save();
        return true;
    }

    boolean rollMaterial(int t) { return rollMaterial(t, true); }

    boolean rollMaterial(int t, boolean doSave) {
        if (!owned[t] || crystals < CRYSTAL_COST) return false;
        crystals -= CRYSTAL_COST;
        material[t] = pick(Defs.MAT_CHANCE, rnd);
        if (doSave) save();
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
        track(Missions.UPGRADE, 1);
        save();
        return null;
    }

    boolean inDeck(int t) {
        for (int d : deck) if (d == t) return true;
        return false;
    }

    /** toggles the planet in the deck; returns error or null */
    static boolean isSpecialist(int t) { return Defs.P_CATEGORY[t] == Defs.CAT_SPECIALIST; }

    String toggleDeck(int t) {
        if (!owned[t]) return L.t("ยังไม่มีดวงดาวนี้", "You don't own this planet");
        for (int i = 0; i < DECK_SIZE; i++) {
            if (deck[i] == t) { deck[i] = -1; save(); return null; }
        }
        if (isSpecialist(t)) {
            // the specialist slot holds one planet; a new one replaces the old
            deck[Defs.SPECIAL_SLOT] = t;
            save();
            return null;
        }
        for (int i = 0; i < Defs.SPECIAL_SLOT; i++) {
            if (deck[i] < 0) { deck[i] = t; save(); return null; }
        }
        return L.t("Deck เต็มแล้ว (ใส่ได้ 4 ดวง + ช่อง Specialist)", "Deck is full (4 planets + the Specialist slot)");
    }

    int deckCount() {
        int n = 0;
        for (int d : deck) if (d >= 0) n++;
        return n;
    }

    /** set by addRewards for the result screen */
    boolean firstClear, heroUnlocked;

    /** Crystal Oil the last Raid paid (for the result screen) */
    int lastOil;

    void addRewards(Game g) {
        if (g.stage == Defs.RAID) {
            lastOil = g.earnOil;
            oil += g.earnOil;
            raidBest = Math.max(raidBest, g.won ? Defs.RAID_WAVES : g.wave);
        }
        stars += g.earnStars;
        millite += g.earnMillite;
        crystals += g.earnCrystal;
        if (g.won) wins++;
        bossKills += g.bossKills;
        games++;
        int reached = g.won ? g.wave : Math.max(0, g.wave - 1);
        if (g.challenge == 0 && reached > best[g.stage]) best[g.stage] = reached;
        firstClear = false;
        heroUnlocked = false;
        lastChallengeReward = null;
        if (g.pvp) {
            if (g.won) { stars += 400; gold += 15; pvpWins++; lastChallengeReward = L.t("ชนะ PvP: 400 Star • 15 Gold", "PvP win: 400 Star • 15 Gold"); }
            else lastChallengeReward = L.t("แพ้ PvP: ได้ Star จากการเล่นตามปกติ", "PvP loss: you keep the Star you earned");
        } else if (g.coop && g.won) {
            // team win bonus
            int bonus = g.earnStars / 4;
            stars += bonus;
            items[Items.SUPPLY]++;
            lastChallengeReward = L.t("โบนัสทีม: ", "Team bonus: ") + bonus + " Star • 1 Supply Capsule";
        }
        if (g.challenge != 0 && g.won) {
            if (g.coop) {
                // challenges are solo
            } else if (g.challenge == 1 && dailyReady()) {
                dailyDone = today();
                items[Items.STARDUST] += 8;
                items[Items.NEBULA] += 1;
                stars += 500;
                lastChallengeReward = "8 Stardust • 1 Nebula Core • 500 Star";
            } else if (g.effectStage()) {
                int e = g.challenge - Game.CH_EFFECT;
                if ((abuseMask & (1 << e)) == 0 && abuseId != null && !abuseId.isEmpty()) {
                    abuseMask |= 1 << e;
                    items[Items.EFF_TOKEN + e]++;
                    gold += 25;
                    lastChallengeReward = Items.NAME[Items.EFF_TOKEN + e] + " • 25 Gold";
                } else lastChallengeReward = L.t("รับรางวัลด่านนี้ไปแล้วในอีเวนต์นี้", "Already claimed in this event");
            } else if (g.challenge == 2 && weeklyReady()) {
                weeklyDone = week();
                items[Items.CATALYST] += 1;
                items[Items.NEBULA] += 3;
                oil += 40;
                lastChallengeReward = "1 Evo Catalyst • 3 Nebula Core • 40 Crystal Oil";
            }
        }
        if (g.won && g.stage != Defs.RAID && g.challenge == 0) {
            if (!clear[g.stage]) {
                // first clear bonus
                firstClear = true;
                millite += 2 + Defs.galaxyOf(g.stage);
                crystals += 1 + Defs.galaxyOf(g.stage);
            }
            clear[g.stage] = true;
            unlocked = Math.max(unlocked, Math.min(Defs.ST_COUNT, g.stage + 2));
            if (g.stage == Defs.ST_COUNT - 1 && !owned[Defs.HERO]) {
                // finishing all three galaxies grants the exclusive Hero planet
                owned[Defs.HERO] = true;
                heroUnlocked = true;
                autoEquip(Defs.HERO);
            }
        }
        // daily missions
        track(Missions.KILL, g.kills);
        track(Missions.PLAY, 1);
        if (g.won) track(Missions.WIN, 1);
        track(Missions.BOSS, g.bossKills);
        track(Missions.PLACE, g.placedCount);
        if (g.stage == Defs.RAID) track(Missions.RAIDWAVE, g.won ? Defs.RAID_WAVES : g.wave);
        if (firstClear) track(Missions.STAGE, 1);
        save();
    }
}
