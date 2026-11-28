package com.stella.td;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

/** Pure game simulation for one stage. Coordinates are map units (1000 x 1200). */
final class Game {
    Random rnd = new Random();
    // co-op: the team shares one base; each player reports how much leaked through their board
    boolean coop;
    int leak, otherLeak, coopBonus;
    String partnerName;
    int partnerWave;
    boolean partnerAlive = true;

    void startCoop(long seed) {
        coop = true;
        rnd = new Random(seed);
        maxBaseHp = Social.TEAM_HP;
        baseHp = Social.TEAM_HP;
    }
    final int stage, maxWave;
    final float[] px, py, cum;
    final float total;
    float[] slotX, slotY;
    final Planet[] slots;

    final ArrayList<Enemy> enemies = new ArrayList<>();
    final ArrayList<Enemy> pending = new ArrayList<>();
    final ArrayList<Shot> shots = new ArrayList<>();
    final ArrayList<HeatWave> heat = new ArrayList<>();
    final ArrayList<Fx> fx = new ArrayList<>();
    final ArrayList<Integer> queue = new ArrayList<>();
    final ArrayList<Wall> walls = new ArrayList<>();
    /** the last slots sit on the path itself (Commander only) */
    final boolean[] pathSlot;
    boolean kingSkillUsed;
    /** team family tier per family (0-3) for this deck */
    final int[] famTier = new int[Defs.FAM_COUNT];
    /** Martin World skill timers */
    float worldCd, worldBuffT, worldStopT;
    // ---- co-op on one map: host owns even slots, guest owns odd ones
    int coopSide = -1;
    String lastBoard = "";
    // ---- PvP: the other player's base and monsters they sent me
    boolean pvp;
    int otherHp = -1;
    String otherName;

    boolean mySlot(int slot) { return coopSide < 0 || slot % 2 == coopSide; }
    int earnOil;

    final Profile pf;
    final int[] deck;
    int stars = 5, baseHp = 20, maxBaseHp = 20, wave = 0, sunCount = 0;
    int earnStars, earnMillite, earnCrystal, bossKills;
    boolean inWave, won, lost, infiniteSP;
    float prepT = 12f, spawnT, spawnGap = 0.7f, time, shake;
    String banner;
    float bannerT;
    int bannerColor = 0xFFFFFFFF;
    final float[] tmp = new float[2];

    /** 0 normal, 1 daily challenge, 2 weekly challenge, 10+e Admin Abuse effect stage, 20 Admin boss event */
    final int challenge;
    static final int CH_EFFECT = 10, CH_BOSS = 20;
    /** damage dealt to Admin bosses this game (boss event) */
    float bossDamage;

    boolean effectStage() { return challenge >= CH_EFFECT && challenge < CH_EFFECT + Defs.EFF_COUNT; }
    final boolean[] mod = new boolean[Defs.CH_MOD.length];

    Game(int stage, Profile pf) { this(stage, pf, 0); }

    Game(int stage, Profile pf, int challenge) {
        this.stage = stage;
        this.pf = pf;
        this.challenge = challenge;
        this.deck = pf.deck.clone();
        for (int f = 0; f < Defs.FAM_COUNT; f++) famTier[f] = Defs.famTier(deck, f);
        if (challenge == 1) mod[Defs.dailyMod(Profile.today())] = true;
        if (challenge == 2) for (int m : Defs.weeklyMods(Profile.week())) mod[m] = true;
        if (effectStage()) for (int m : Defs.effMods(challenge - CH_EFFECT)) mod[m] = true;
        this.maxWave = challenge == 1 ? Defs.DAILY_WAVES : challenge == 2 ? Defs.WEEKLY_WAVES
                : effectStage() ? Defs.EFF_WAVES : challenge == CH_BOSS ? Defs.BOSS_WAVES : Defs.ST_WAVES[stage];
        float[] p = Defs.ST_PATH[stage];
        int n = p.length / 2;
        px = new float[n];
        py = new float[n];
        cum = new float[n];
        float t = 0;
        for (int i = 0; i < n; i++) {
            px[i] = p[i * 2];
            py[i] = p[i * 2 + 1];
            if (i > 0) t += dist(px[i - 1], py[i - 1], px[i], py[i]);
            cum[i] = t;
        }
        total = t;
        float[] s = Defs.ST_SLOTS[stage];
        float[] slotX = new float[s.length / 2];
        float[] slotY = new float[s.length / 2];
        for (int i = 0; i < slotX.length; i++) {
            slotX[i] = s[i * 2];
            slotY[i] = s[i * 2 + 1];
        }
        this.slotX = slotX;
        this.slotY = slotY;
        // three extra slots right on the path for the Commander satellite
        int np = 3;
        float[] sx = new float[slotX.length + np], sy = new float[slotX.length + np];
        System.arraycopy(slotX, 0, sx, 0, slotX.length);
        System.arraycopy(slotY, 0, sy, 0, slotY.length);
        pathSlot = new boolean[sx.length];
        float[] fr = {0.32f, 0.55f, 0.78f};
        for (int k = 0; k < np; k++) {
            pointAt(total * fr[k], tmp);
            sx[slotX.length + k] = tmp[0];
            sy[slotX.length + k] = tmp[1];
            pathSlot[slotX.length + k] = true;
        }
        this.slotX = sx;
        this.slotY = sy;
        slots = new Planet[sx.length];
        showBanner(L.t("วางดวงดาวเพื่อป้องกันฐาน!", "Place planets to defend the base!"), 0xFFFFD23F);
    }

    /** distance from a point to the path */
    float pathDist(float x, float y) {
        float best = 1e9f;
        for (int i = 1; i < px.length; i++) best = Math.min(best, Defs.segDist(x, y, px[i - 1], py[i - 1], px[i], py[i]));
        return best;
    }

    /** path distance (from the black hole) of the path point nearest to (x, y) */
    float nearestD(float x, float y) {
        float bestD = 0, best = 1e9f;
        for (float d = 0; d <= total; d += 8) {
            pointAt(d, tmp);
            float q = dist(x, y, tmp[0], tmp[1]);
            if (q < best) { best = q; bestD = d; }
        }
        return bestD;
    }

    static float dist(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1, dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    void pointAt(float d, float[] out) {
        if (d <= 0) { out[0] = px[0]; out[1] = py[0]; return; }
        for (int i = 1; i < px.length; i++) {
            if (d <= cum[i]) {
                float seg = cum[i] - cum[i - 1];
                float f = seg <= 0 ? 0 : (d - cum[i - 1]) / seg;
                out[0] = px[i - 1] + (px[i] - px[i - 1]) * f;
                out[1] = py[i - 1] + (py[i] - py[i - 1]) * f;
                return;
            }
        }
        out[0] = px[px.length - 1];
        out[1] = py[py.length - 1];
    }

    void showBanner(String s, int color) {
        banner = s;
        bannerColor = color;
        bannerT = 2.2f;
    }

    // ------------------------------------------------------------------ stats
    // ------------------------------------------------------------------ stat bonuses from enchant + material
    static float enchDmg(Planet p) {
        switch (p.enchant) {
            case Defs.EN_STRONG: return Defs.STRONG_T[Math.max(0, p.tier - 1)];
            case Defs.EN_TECH: return 0.15f;
            case Defs.EN_CRYSTALRAIN: return 0.20f;
            case Defs.EN_GALACIA: case Defs.EN_LAVA: return 0.10f;
            case Defs.EN_MEGALONIA: return 0.17f;
            case Defs.EN_GIANTTIC: return 0.30f;
            case Defs.EN_GALAXIA: return 0.50f;
            case Defs.EN_FROSTBITE: return 0.10f;
            case Defs.EN_NOVA: return 0.12f;
            default: return 0f;
        }
    }

    static float enchRng(Planet p) {
        switch (p.enchant) {
            case Defs.EN_REACH: return Defs.REACH_T[Math.max(0, Math.min(1, p.tier - 1))];
            case Defs.EN_STORM: return 0.15f;
            case Defs.EN_ELECTRO: return 0.07f;
            case Defs.EN_MEGALONIA: return 0.17f;
            case Defs.EN_NOVA: return 0.12f;
            default: return 0f;
        }
    }

    static float enchSpa(Planet p) {
        switch (p.enchant) {
            case Defs.EN_MOVING: return Defs.MOVING_T[Math.max(0, p.tier - 1)];
            case Defs.EN_LIGHTNING: return 0.15f;
            case Defs.EN_ELECTRO: return 0.12f;
            case Defs.EN_MEGALONIA: return 0.17f;
            case Defs.EN_GIANTTIC: return -0.15f;
            case Defs.EN_GALAXIA: return 0.20f;
            default: return 0f;
        }
    }

    static float matDmg(Planet p) { return p.material >= 0 ? Defs.MAT_DMG[p.material] : 0f; }

    static float matRng(Planet p) { return p.material >= 0 ? Defs.MAT_RNG[p.material] : 0f; }

    static float matSpa(Planet p) { return p.material >= 0 ? Defs.MAT_SPA[p.material] : 0f; }

    static boolean isProducer(Planet p) {
        return Defs.P_KIND[p.type] == Defs.K_PRODUCE;
    }

    /** planets that shoot at enemies (spirits join these) */
    static boolean attacks(Planet p) {
        int k = Defs.P_KIND[p.type];
        return k != Defs.K_PRODUCE && k != Defs.K_WALL && k != Defs.K_NUKE && k != Defs.K_KING && k != Defs.K_HAUNT
                && k != Defs.K_STORM && k != Defs.K_BUFF && k != Defs.K_SUNWAVE;
    }

    static boolean isFire(Planet p) {
        int k = Defs.P_KIND[p.type];
        return k == Defs.K_SMOKE || k == Defs.K_FIREROCK || k == Defs.K_TORNADO || k == Defs.K_SUNWAVE || k == Defs.K_LANCE || p.hellMoon;
    }

    static boolean isIce(Planet p) {
        int k = Defs.P_KIND[p.type];
        return k == Defs.K_ICE || k == Defs.K_AURA || k == Defs.K_STORM;
    }

    static float dmgOf(Planet p) {
        return Defs.P_DMG[p.type] * (1f + 0.3f * (p.level - 1)) * (1f + 0.12f * (p.plv - 1)) * (1f + p.famDmg) * (p.worldBuff ? 1.3f : 1f)
                * (1f + enchDmg(p)) * (1f + matDmg(p)) * (1f + p.bonusDmg) * (1f + p.buff) * (p.evo ? 1.6f : 1f)
                * (p.eff >= 0 ? 1f + Defs.EFF_DMG[p.eff] : 1f);
    }

    static float rangeOf(Planet p) {
        return Defs.P_RANGE[p.type] * (1f + enchRng(p)) * (1f + matRng(p)) * (1f + p.famRng) * (p.worldBuff ? 1.3f : 1f) * (p.king ? 1.1f : 1f) * (p.evo ? 1.15f : 1f) * p.rangeMul
                * (p.eff >= 0 ? 1f + Defs.EFF_RNG[p.eff] : 1f);
    }

    /** the persistent upgrade level (Lv 1-10) speeds up attacks 4% and production 5% per level */
    static float cdOf(Planet p) {
        float c = Defs.P_CD[p.type];
        float k = (p.king ? 1.1f : 1f) * (p.eff >= 0 ? 1f + Defs.EFF_SPD[p.eff] : 1f) * (1f + p.famSpd) * (p.worldBuff ? 1.3f : 1f);
        if (isProducer(p)) return c / ((1f + 0.2f * (p.level - 1)) * (1f + 0.05f * (p.plv - 1)) * k * (p.evo ? 1.15f : 1f));
        return c / ((1f + enchSpa(p)) * (1f + matSpa(p)) * (1f + 0.04f * (p.plv - 1)) * k * (p.evo ? 1.15f : 1f) * (p.art == Defs.MOON_STORM ? 1.12f : 1f));
    }

    /** SP per production cycle (fractional, accumulated); persistent level adds 8% per level */
    static float produceRate(Planet p) {
        float a = (Defs.P_DMG[p.type] + (p.level >= 4 ? 1 : 0)) * (1f + 0.08f * (p.plv - 1)) * (p.king ? 1.1f : 1f);
        if (p.enchant == Defs.EN_MIDAS) a *= 1.5f;
        if (p.material == Defs.MAT_GOLD) a *= 1.6f;
        if (p.art == Defs.MOON_GOLD) a *= 1.2f;
        if (p.evo) a *= 1.6f;
        if (p.eff >= 0) a *= 1f + Defs.EFF_DMG[p.eff] * 0.5f;
        a *= 1f + p.famProd;
        if (p.worldBuff) a *= 1.3f;
        return a;
    }

    static int produceAmount(Planet p) {
        return Math.round(produceRate(p));
    }

    /** strength / duration multiplier for burn effects */
    static float fireMul(Planet p) {
        float m = p.hellMoon ? 1.1f : 1f;
        if (p.enchant == Defs.EN_LAVA) m *= 1.5f;
        if (p.enchant == Defs.EN_MEGALONIA && isFire(p)) m *= 1.15f;
        return m;
    }

    static float fireDurMul(Planet p) {
        float m = 1f;
        if (p.enchant == Defs.EN_LAVA) m *= 1.5f;
        if (p.enchant == Defs.EN_MEGALONIA && isFire(p)) m *= 1.3f;
        return m;
    }

    static float iceMul(Planet p) {
        float m = 1f;
        if (p.enchant == Defs.EN_GALACIA) m *= 1.6f;
        if (p.enchant == Defs.EN_MEGALONIA && isIce(p)) m *= 1.3f;
        return m;
    }

    // ------------------------------------------------------------------ building
    int upgradeCost(Planet p) {
        return p.level * 3;
    }

    String place(int type, int slot) {
        int cost = Defs.PLACE_COST[type];
        if (slots[slot] != null) return L.t("ช่องนี้มีดาวแล้ว", "Slot is taken");
        if (!mySlot(slot)) return L.t("ช่องนี้เป็นของเพื่อนร่วมทีม", "That slot belongs to your teammate");
        if (stars < cost) return L.t("SP ไม่พอ", "Not enough SP");
        if (pathSlot[slot] && !Defs.onPathOnly(type))
            return L.t("ช่องบนทางเดินใช้ได้เฉพาะ Commander", "Path slots are for the Commander only");
        if (!pathSlot[slot] && Defs.onPathOnly(type))
            return L.t("Commander ต้องวางบนช่องที่อยู่บนทางเดิน", "The Commander goes on a slot on the path");
        if (Defs.nearPathOnly(type) && pathDist(slotX[slot], slotY[slot]) > 175)
            return L.t("Cyber วางได้เฉพาะช่องที่ติดทางเดิน", "Cyber must go on a slot next to the path");
        if (countOf(type) >= Defs.PLACE_LIMIT[type])
            return L.t("วาง " + Defs.P_NAME[type] + " ได้สูงสุด " + Defs.PLACE_LIMIT[type] + " ดวง", "Max " + Defs.PLACE_LIMIT[type] + " " + Defs.P_NAME[type] + " on the map");
        stars -= cost;
        Sfx.play(Sfx.PLACE);
        Planet p = new Planet();
        p.type = type;
        p.slot = slot;
        p.x = slotX[slot];
        p.y = slotY[slot];
        p.spent = cost;
        p.cdT = 0.3f;
        p.plv = pf.level[type];
        p.enchant = pf.enchant[type];
        p.art = pf.moonOn[type];
        p.hellMoon = p.art == Defs.MOON_HELL;
        if (mod[2]) p.rangeMul = 0.8f; // challenge: short sight
        p.evo = pf.evolved[type];
        p.eff = pf.effect[type];
        p.skin = pf.skinOn[type];
        p.ax = p.x;
        p.ay = p.y;
        if (Defs.P_KIND[type] == Defs.K_WALL) p.wallD = nearestD(p.x, p.y);
        p.tier = pf.tier[type];
        p.material = pf.material[type];
        p.moons = p.enchant == Defs.EN_MOON ? 1 : 0;
        setFamily(p);
        slots[slot] = p;
        placedCount++;
        ring(p.x, p.y, Defs.RARITY_COLOR[Defs.P_RARITY[type]], 80);
        for (int i = 0; i < 14; i++) particle(p.x, p.y, Defs.RARITY_COLOR[Defs.P_RARITY[type]], 220);
        return null;
    }

    int countOf(int type) {
        int n = 0;
        for (Planet q : slots) if (q != null && q.type == type && !q.ghost) n++;
        return n;
    }

    void setFamily(Planet p) {
        int f = Defs.P_FAMILY[p.type], t = famTier[f];
        if (t <= 0) { p.famDmg = p.famSpd = p.famRng = p.famProd = 0; return; }
        float[] b = Defs.FAM_BONUS[f][t - 1];
        p.famDmg = b[0];
        p.famSpd = b[1];
        p.famRng = b[2];
        p.famProd = b[3];
    }

    // ------------------------------------------------------------------ co-op board sync
    /** my planets as "slot,type,level,plv,skin,eff,evo,enchant,material;..." */
    String board() {
        StringBuilder b = new StringBuilder();
        for (Planet p : slots) {
            if (p == null || p.ghost) continue;
            if (b.length() > 0) b.append(';');
            b.append(p.slot).append(',').append(p.type).append(',').append(p.level).append(',').append(p.plv).append(',').append(p.skin)
                    .append(',').append(p.eff).append(',').append(p.evo ? 1 : 0).append(',').append(p.enchant).append(',').append(p.material);
        }
        return b.toString();
    }

    /** mirror the teammate's planets onto my map */
    void syncGhosts(String board) {
        if (board == null || board.equals(lastBoard)) return;
        lastBoard = board;
        boolean[] seen = new boolean[slots.length];
        for (String part : board.split(";")) {
            String[] q = part.split(",");
            if (q.length < 9) continue;
            try {
                int slot = Integer.parseInt(q[0]), type = Integer.parseInt(q[1]);
                if (slot < 0 || slot >= slots.length || type < 0 || type >= Defs.P_COUNT || mySlot(slot)) continue;
                seen[slot] = true;
                Planet p = slots[slot];
                if (p == null || !p.ghost || p.type != type) {
                    p = new Planet();
                    p.ghost = true;
                    p.type = type;
                    p.slot = slot;
                    p.x = p.ax = slotX[slot];
                    p.y = p.ay = slotY[slot];
                    p.cdT = 0.3f;
                    if (Defs.P_KIND[type] == Defs.K_WALL) p.wallD = nearestD(p.x, p.y);
                    slots[slot] = p;
                    ring(p.x, p.y, 0xFF5EC8FF, 80);
                }
                p.level = Integer.parseInt(q[2]);
                p.plv = Integer.parseInt(q[3]);
                p.skin = Integer.parseInt(q[4]);
                p.eff = Integer.parseInt(q[5]);
                p.evo = "1".equals(q[6]);
                p.enchant = Integer.parseInt(q[7]);
                p.material = Integer.parseInt(q[8]);
                p.art = -1;
            } catch (RuntimeException ignored) { }
        }
        for (int i = 0; i < slots.length; i++) if (slots[i] != null && slots[i].ghost && !seen[i]) slots[i] = null;
    }

    // ------------------------------------------------------------------ PvP: monsters sent by the other player
    static final int[] SEND_COST = {12, 40, 220};
    static final String[] SEND_NAME = {"Meteor x4", "Mega", "Titan"};

    /** spawn monsters the other player paid for (kind 0 meteors, 1 mega, 2 titan) */
    void receiveSend(int kind) {
        if (kind == 0) for (int i = 0; i < 4; i++) queueSent(Defs.E_METEOR);
        else if (kind == 1) queueSent(Defs.E_MEGA);
        else queueSent(Defs.E_TITAN);
        showBanner(L.t("ศัตรูส่งมอนมา: ", "Incoming from rival: ") + SEND_NAME[Math.max(0, Math.min(2, kind))], 0xFFFF4B6B);
    }

    void queueSent(int type) { spawn(type); }

    // ------------------------------------------------------------------ Martin World: In This World
    boolean hasMartin() {
        for (Planet p : slots) if (p != null && !p.ghost && p.type == Defs.MARTIN) return true;
        return false;
    }

    String inThisWorld(int choice) {
        if (!hasMartin()) return L.t("ต้องวาง Martin World ก่อน", "Place Martin World first");
        if (worldCd > 0) return L.t("รออีก " + (int) Math.ceil(worldCd) + " วิ", "Ready in " + (int) Math.ceil(worldCd) + "s");
        worldCd = Defs.WORLD_CD;
        Sfx.play(Sfx.RARE);
        shake = 0.5f;
        if (choice == Defs.WORLD_STOP) {
            worldStopT = 6f;
            for (Enemy e : enemies) if (!e.dead) e.stunT = Math.max(e.stunT, 6f);
        } else if (choice == Defs.WORLD_STRONG) {
            worldBuffT = 25f;
        } else {
            for (Enemy e : enemies) if (!e.dead) { e.hp = Math.max(1, e.hp * 0.5f); if (e.admin) bossDamage += e.hp; }
        }
        showBanner("IN THIS WORLD • " + Defs.WORLD_NAME[choice].toUpperCase(java.util.Locale.US), 0xFFFF7AE0);
        ring(500, 600, 0xFFFF7AE0, 700);
        return null;
    }

    String upgrade(Planet p) {
        if (p.level >= Defs.MATCH_MAX[p.type]) return L.t("เลเวลสูงสุดในด่านแล้ว (Lv " + Defs.MATCH_MAX[p.type] + ")", "Max in-match level (Lv " + Defs.MATCH_MAX[p.type] + ")");
        int c = upgradeCost(p);
        if (stars < c) return L.t("SP ไม่พอ", "Not enough SP");
        stars -= c;
        p.spent += c;
        p.level++;
        Sfx.play(Sfx.UPGRADE);
        ring(p.x, p.y, 0xFFFFD23F, 90);
        text(p.x, p.y - 70, "Lv " + p.level, 0xFFFFD23F);
        return null;
    }

    void sell(Planet p) {
        int back = Math.max(1, p.spent / 2);
        stars += back;
        slots[p.slot] = null;
        starText(p.x, p.y - 40, back);
        for (int i = 0; i < 10; i++) particle(p.x, p.y, 0xFFB8C4D6, 160);
    }

    // ------------------------------------------------------------------ waves
    void startWave() {
        wave++;
        Sfx.play(Sfx.WAVE_START);
        inWave = true;
        queue.clear();
        int g = Defs.galaxyOf(stage);
        int w = wave + (stage % Defs.PER_GALAXY) / 2 + g * 4;
        int n = 4 + (int) (wave * 1.3f);
        for (int i = 0; i < n; i++) queue.add(pickEnemy(w));
        boolean boss = wave % 10 == 0 || wave == maxWave || (mod[3] && wave % 5 == 0);
        boolean mini = !boss && wave % 10 == 5;
        if (challenge == CH_BOSS) {
            // Admin boss event: an Admin Titan every wave with a small escort
            queue.clear();
            for (int i = 0; i < 3 + wave; i++) queue.add(pickEnemyBase(6 + wave));
            queue.add(Defs.E_TITAN);
            if (wave % 3 == 0) queue.add(MINI_FLAG + Defs.E_WRAITH);
            showBanner(L.t("ADMIN BOSS • เวฟ ", "ADMIN BOSS • wave ") + wave, 0xFFFF2B5B);
        } else if (stage == Defs.RAID && boss) {
            queue.add(Defs.E_TITAN);
            showBanner(L.t("บอส Raid: Void Titan!", "Raid boss: Void Titan!"), 0xFFFF2B5B);
        } else if (stage == Defs.RAID && mini) {
            queue.add(MINI_FLAG + Defs.E_WRAITH);
            showBanner(L.t("มินิบอส Raid!", "Raid mini boss!"), 0xFFFF8A2B);
        } else if (boss) {
            queue.add(Defs.E_BLACKHOLE);
            showBanner(L.t("บอส: หลุมดำมาแล้ว!", "Boss: Black Hole incoming!"), 0xFFB86BFF);
        } else if (mini) {
            queue.add(MINI_FLAG + (rnd.nextBoolean() ? Defs.E_HELL : Defs.E_EXOPLANET));
            showBanner(L.t("มินิบอสมาแล้ว!", "Mini boss incoming!"), 0xFFFF8A2B);
        } else {
            showBanner(L.t("เวฟ ", "Wave ") + wave, 0xFFFFFFFF);
        }
        spawnGap = Math.max(0.32f, 0.75f - wave * 0.015f);
        spawnT = 0.2f;
    }

    static final int MINI_FLAG = 100;

    int pickEnemy(int w) {
        if (stage == Defs.RAID) {
            int k = pickEnemyBase(Math.min(w, 16));
            float r = rnd.nextFloat();
            if (wave >= 5 && r < 0.18f) return Defs.E_VOIDLING;
            if (wave >= 15 && r < 0.3f) return Defs.E_WRAITH;
            return k;
        }
        return pickEnemyBase(w);
    }

    int pickEnemyBase(int w) {
        float[] weight = new float[8];
        weight[Defs.E_METEOR] = 5;
        if (w >= 2) weight[Defs.E_SHOOT] = 3;
        if (w >= 4) weight[Defs.E_MEGA] = 2;
        if (w >= 6) weight[Defs.E_EXOMOON] = 1.6f;
        if (w >= 7) weight[Defs.E_ALTMOON] = 1.8f;
        if (w >= 9) weight[Defs.E_EXOPLANET] = 1.5f;
        if (w >= 11) weight[Defs.E_ALTPLANET] = 1.5f;
        if (w >= 13) weight[Defs.E_HELL] = 1.4f;
        float sum = 0;
        for (float f : weight) sum += f;
        float r = rnd.nextFloat() * sum;
        for (int i = 0; i < weight.length; i++) {
            r -= weight[i];
            if (r <= 0) return i;
        }
        return Defs.E_METEOR;
    }

    float hpScale() {
        float w = wave - 1;
        return (1f + 0.2f * w + 0.008f * w * w) * Defs.ST_HP[stage];
    }

    Enemy makeEnemy(int type) {
        Enemy e = new Enemy();
        e.type = type;
        if (type == Defs.E_TITAN) {
            e.boss = true;
            e.maxHp = Defs.E_HP[type] * (1f + 0.35f * (wave / 10 - 1)) * (1f + 0.012f * wave * wave / 10f) * Defs.ST_HP[stage];
            e.maxShield = e.maxHp * 0.25f;
            e.shield = e.maxShield;
        } else if (type == Defs.E_BLACKHOLE) {
            e.boss = true;
            e.maxHp = Defs.E_HP[type] * (1f + 0.18f * Math.max(0, wave - 10)) * Defs.ST_HP[stage]
                    * (wave >= 10 ? 1f : 0.6f);
        } else {
            e.maxHp = Defs.E_HP[type] * hpScale();
        }
        e.hp = e.maxHp;
        if (type == Defs.E_TITAN) {
            // shield set above
        } else if (type == Defs.E_EXOPLANET) {
            e.maxShield = e.maxHp * 0.35f;
            e.shield = e.maxShield;
        }
        e.speed = Defs.E_SPEED[type] * (1f + Math.min(0.25f, wave * 0.006f)) * Defs.ST_SPEED[stage];
        if (mod[0]) e.speed *= 1.35f;
        if (mod[1]) { e.maxHp *= 1.6f; e.hp = e.maxHp; e.maxShield *= 1.6f; e.shield = e.maxShield; }
        if (effectStage()) { e.maxHp *= 1.35f + 0.1f * (challenge - CH_EFFECT); e.hp = e.maxHp; e.maxShield *= 1.4f; e.shield = e.maxShield; }
        e.radius = Defs.E_RADIUS[type];
        e.baseDmg = Defs.E_BASEDMG[type];
        if (challenge == CH_BOSS && type == Defs.E_TITAN) { e.maxHp *= 2.2f; e.hp = e.maxHp; e.admin = true; e.baseDmg = 4; }
        e.abilT = rnd.nextFloat() * 2f;
        e.angle = rnd.nextFloat() * 360f;
        return e;
    }

    void spawn(int code) {
        boolean mini = code >= MINI_FLAG;
        Enemy e = makeEnemy(code % MINI_FLAG);
        if (mini) {
            e.mini = true;
            e.maxHp *= 4f;
            e.hp = e.maxHp;
            e.maxShield *= 4f;
            e.shield = e.maxShield;
            e.radius *= 1.4f;
            e.speed *= 0.8f;
            e.baseDmg = 5;
            shake = 0.3f;
        }
        pointAt(0, tmp);
        e.x = tmp[0];
        e.y = tmp[1];
        enemies.add(e);
        if (e.boss) shake = 0.5f;
    }

    void waveCleared() {
        inWave = false;
        if (stage == Defs.RAID) earnOil += 1 + wave / 10;
        int g = 4 + wave / 3;
        stars += g;
        earnStars += 4 + wave / 2;
        if (wave >= maxWave) {
            won = true;
            Sfx.play(Sfx.WIN);
            earnStars += stage == Defs.RAID ? 3000 : 80 + 30 * stage;
            if (stage == Defs.RAID) earnOil += 100;
            return;
        }
        showBanner(L.t("เคลียร์เวฟ ", "Wave cleared ") + wave + "  +" + g + " SP", 0xFF9CFF7A);
        prepT = 5f;
    }

    // ------------------------------------------------------------------ skills
    /** Nightmare: spend a lot of SP, destroy itself and every enemy on the map (bosses lose 40% HP) */
    String nuke(Planet p) {
        int cost = Defs.nukeCost(wave);
        if (stars < cost) return L.t("SP ไม่พอ (ต้องใช้ ", "Not enough SP (needs ") + cost + ")";
        stars -= cost;
        for (Enemy e : enemies) {
            if (e.dead) continue;
            if (e.boss || e.type == Defs.E_TITAN) damage(e, e.maxHp * 0.4f, p);
            else kill(e, p);
        }
        for (Planet q : slots) if (q == p) slots[p.slot] = null;
        shake = 1f;
        ring(p.x, p.y, 0xFFFF1A3A, 900);
        ring(p.x, p.y, 0xFF7A0010, 600);
        for (int k = 0; k < 60; k++) particle(p.x, p.y, k % 2 == 0 ? 0xFFFF1A3A : 0xFF2A0612, 600);
        showBanner(L.t("NIGHTMARE! ศัตรูทั้งแมพถูกทำลาย", "NIGHTMARE! The map is wiped"), 0xFFFF1A3A);
        Sfx.play(Sfx.BOSS_KILL, 0.7f);
        return null;
    }

    /** King: Elite Command, once per match - 3 Knight Moons march down the path and the base gets +5 HP */
    String eliteCommand(Planet p) {
        if (kingSkillUsed) return L.t("ใช้ Elite Command ไปแล้วในด่านนี้", "Elite Command was already used this match");
        kingSkillUsed = true;
        baseHp += 5;
        coopBonus += 5;
        maxBaseHp = Math.max(maxBaseHp, baseHp);
        for (int k = 0; k < 3; k++) {
            HeatWave h = new HeatWave();
            h.d = total - k * 70;
            h.maxHp = dmgOf(p) * 40f * (1f + 0.15f * wave) * Defs.ST_HP[stage];
            h.hp = h.maxHp;
            h.summon = -2; // knight moon
            h.speed = 110f;
            h.src = p;
            heat.add(h);
        }
        ring(px[px.length - 1], py[py.length - 1], 0xFFFFD23F, 260);
        text(px[px.length - 1], py[py.length - 1] - 90, "+5 HP", 0xFF7CFF8A);
        showBanner(L.t("Elite Command! Knight Moon x3", "Elite Command! Knight Moons x3"), 0xFFFFD23F);
        Sfx.play(Sfx.WIN, 1.3f);
        return null;
    }

    void skipPrep() {
        if (!inWave) prepT = 0;
    }

    // ------------------------------------------------------------------ update
    void update(float dt) {
        time += dt;
        if (bannerT > 0) bannerT -= dt;
        if (shake > 0) shake -= dt;
        updateFx(dt);
        if (won || lost) return;
        if (!novaQueue.isEmpty()) {
            java.util.ArrayList<Object[]> q = new java.util.ArrayList<>(novaQueue);
            novaQueue.clear();
            for (Object[] o : q) damage((Enemy) o[0], (Float) o[1], (Planet) o[2]);
        }

        if (!inWave) {
            prepT -= dt;
            if (prepT <= 0) startWave();
        } else if (!queue.isEmpty()) {
            spawnT -= dt;
            if (spawnT <= 0) {
                spawn(queue.remove(0));
                spawnT = spawnGap;
            }
        } else if (enemies.isEmpty() && pending.isEmpty()) {
            waveCleared();
        }

        if (infiniteSP) stars = 999;
        for (int i = walls.size() - 1; i >= 0; i--) {
            Wall w = walls.get(i);
            if (w.hitT > 0) w.hitT -= dt;
            boolean owner = false;
            for (Planet q : slots) if (q == w.src) owner = true;
            if (w.dead || !owner) walls.remove(i);
        }
        computeBuffs();
        updatePlanets(dt);
        updateEnemies(dt);
        updateShots(dt);
        updateHeat(dt);
        if (!pending.isEmpty()) {
            enemies.addAll(pending);
            pending.clear();
        }
        if (coop) baseHp = Math.min(baseHp, maxBaseHp + coopBonus - leak - otherLeak);
        if (baseHp <= 0 && !lost) {
            baseHp = 0;
            lost = true;
            Sfx.play(Sfx.LOSE);
            shake = 0.8f;
        }
    }

    void computeBuffs() {
        boolean king = false;
        for (Planet q : slots) if (q != null && q.type == Defs.KING && q.disabledT <= 0) king = true;
        for (Planet p : slots) {
            if (p == null) continue;
            p.king = king;
            p.buff = king ? 0.10f : 0;
            for (Planet q : slots) {
                if (q == null || q == p || q.type != Defs.PLUTO || q.disabledT > 0) continue;
                if (dist(p.x, p.y, q.x, q.y) <= 240) p.buff += 0.10f;
            }
        }
    }

    Enemy findTarget(float x, float y, float range) {
        Enemy best = null;
        for (Enemy e : enemies) {
            if (e.dead || e.phased) continue;
            if (dist(x, y, e.x, e.y) <= range + e.radius && (best == null || e.d > best.d)) best = e;
        }
        return best;
    }

    void updatePlanets(float dt) {
        if (worldCd > 0) worldCd -= dt;
        if (worldBuffT > 0) worldBuffT -= dt;
        if (worldStopT > 0) {
            worldStopT -= dt;
            for (Enemy e : enemies) if (!e.dead) e.stunT = Math.max(e.stunT, 0.1f);
        }
        for (Planet p : slots) {
            if (p == null) continue;
            p.worldBuff = worldBuffT > 0;
            p.anim += dt;
            if (p.flash > 0) p.flash -= dt;
            p.orbitA += dt * 2.4f;
            if (p.disabledT > 0) {
                p.disabledT -= dt;
                continue;
            }
            int kind = Defs.P_KIND[p.type];
            float cd = cdOf(p);
            if (kind == Defs.K_COMMANDER) {
                double a = p.anim * 0.9 + p.slot;
                p.x = p.ax + (float) Math.cos(a) * 110f;
                p.y = p.ay + (float) Math.sin(a) * 110f;
            }
            if (kind == Defs.K_PRODUCE && p.ghost) continue; // the teammate's producers pay the teammate
            if (kind == Defs.K_PRODUCE) {
                p.prodT += dt;
                if (p.prodT >= cd) {
                    p.prodT = 0;
                    p.prodAcc += produceRate(p);
                    int a = (int) p.prodAcc;
                    p.prodAcc -= a;
                    if (a > 0) {
                        stars += a;
                        starText(p.x, p.y - 50, a);
                    }
                    p.flash = 0.3f;
                }
                continue;
            }
            p.cdT -= dt;
            if (p.cdT > 0) continue;
            if (kind == Defs.K_WORLD) {
                // Martin World: hits every enemy on the map
                if (!inWave || enemies.isEmpty()) { p.cdT = 0.2f; continue; }
                p.cdT = cd;
                float dmg = dmgOf(p);
                for (Enemy e : enemies) {
                    if (e.dead || e.phased) continue;
                    damage(e, dmg, p);
                    if (rnd.nextFloat() < 0.4f) particle(e.x, e.y, rnd.nextBoolean() ? 0xFF5EF2FF : 0xFFFF7AE0, 160);
                }
                ring(p.x, p.y, 0xFFFF7AE0, 260);
                p.flash = 0.35f;
                continue;
            }
            if (kind == Defs.K_AURA) {
                p.cdT = cd;
                float r = rangeOf(p), dmg = dmgOf(p);
                boolean hole = p.type == Defs.BLACK_HOLE;
                boolean any = false;
                for (Enemy e : enemies) {
                    if (e.dead || dist(p.x, p.y, e.x, e.y) > r + e.radius) continue;
                    any = true;
                    float im = iceMul(p);
                    applySlow(e, hole ? 0.6f : Math.min(0.7f, 0.4f * (im > 1f ? 1.3f : 1f)), 0.7f * im);
                    if (p.enchant == Defs.EN_GALACIA && rnd.nextFloat() < 0.04f && !e.boss) e.stunT = Math.max(e.stunT, 0.6f);
                    damage(e, dmg, p);
                    applyMaterialOnHit(e, dmg, p);
                }
                if (any) p.flash = 0.2f;
            } else if (kind == Defs.K_WALL) {
                // Cyber: keep up to 3 walls on the nearest stretch of path
                p.cdT = cd;
                float[] offs = {-75, 0, 75};
                for (float o : offs) {
                    float d = Math.max(60, Math.min(total - 60, p.wallD + o));
                    boolean have = false;
                    for (Wall w : walls) if (!w.dead && w.src == p && Math.abs(w.d - d) < 20) have = true;
                    if (have) continue;
                    Wall w = new Wall();
                    w.src = p;
                    w.d = d;
                    pointAt(d, tmp);
                    w.x = tmp[0];
                    w.y = tmp[1];
                    w.maxHp = dmgOf(p) * (1f + 0.12f * Math.max(0, wave - 1)) * Defs.ST_HP[stage] * 3.5f;
                    w.hp = w.maxHp;
                    walls.add(w);
                    ring(w.x, w.y, 0xFF5EF2FF, 70);
                    p.flash = 0.3f;
                    break; // one wall per cycle
                }
            } else if (kind == Defs.K_HAUNT) {
                // Savior: haunt and slow everything in range
                p.cdT = cd;
                float r = rangeOf(p);
                boolean any = false;
                for (Enemy e : enemies) {
                    if (e.dead || dist(p.x, p.y, e.x, e.y) > r + e.radius) continue;
                    any = true;
                    e.hauntT = 1.2f;
                    if (e.type != Defs.E_WRAITH) applySlow(e, 0.35f, 0.8f);
                }
                if (any) p.flash = 0.2f;
            } else if (kind == Defs.K_STORM) {
                // Galacia: a water storm that stuns, no damage
                Enemy t = findTarget(p.x, p.y, rangeOf(p));
                if (t == null) { p.cdT = 0; continue; }
                p.cdT = cd;
                float im = iceMul(p);
                for (Enemy e : enemies) {
                    if (e.dead || e.phased || dist(t.x, t.y, e.x, e.y) > 115 + e.radius) continue;
                    float st = (e.boss ? 0.45f : e.mini ? 0.8f : 1.4f) * im * (1f + 0.04f * (p.plv - 1));
                    e.stunT = Math.max(e.stunT, st);
                    if (Defs.P_DMG[p.type] > 0) damage(e, dmgOf(p), p); // Pulsar hits as it stuns
                }
                ring(t.x, t.y, 0xFF7FD8FF, 230);
                for (int k = 0; k < 14; k++) particle(t.x, t.y, k % 2 == 0 ? 0xFFDFF8FF : 0xFF4FC8FF, 260);
                Sfx.play(Sfx.S_ICE, 0.7f);
                p.flash = 0.25f;
            } else if (kind == Defs.K_NUKE || kind == Defs.K_KING) {
                p.cdT = 1f; // skill planets never attack
            } else if (kind == Defs.K_SUNWAVE) {
                if (!inWave || enemies.isEmpty()) { p.cdT = 0.2f; continue; }
                p.cdT = cd;
                Sfx.play(Sfx.S_WAVE);
                HeatWave h = new HeatWave();
                h.d = total;
                // each wave is a bit stronger than the last, capped so the Sun can't snowball forever
                // v3.8 nerf: weaker waves, smaller ramp
                h.maxHp = dmgOf(p) * 13f * (1f + 0.1f * Math.min(sunCount, 5)) * fireMul(p);
                h.hp = h.maxHp;
                h.src = p;
                sunCount++;
                heat.add(h);
                p.flash = 0.4f;
                ring(px[px.length - 1], py[py.length - 1], 0xFFFFB627, 120);
            } else {
                Enemy t = findTarget(p.x, p.y, rangeOf(p));
                if (t == null) { p.cdT = 0; continue; }
                p.cdT = cd;
                fire(p, t, p.x, p.y, 1f, false);
                if (p.spirits > 0) {
                    // each spirit adds a ghost bolt worth 5% of the host's damage
                    float sd = dmgOf(p) * 0.05f * p.spirits;
                    damage(t, sd, p);
                    if (rnd.nextFloat() < 0.5f) particle(t.x, t.y, 0xFFB8FFF0, 120);
                }
                for (int m = 0; m < p.moons; m++) {
                    double a = p.orbitA + m * Math.PI * 2 / p.moons;
                    float mx = p.x + (float) Math.cos(a) * 62f;
                    float my = p.y + (float) Math.sin(a) * 62f;
                    fire(p, t, mx, my, 0.10f, true);
                }
                p.flash = 0.12f;
            }
        }
    }

    void fire(Planet p, Enemy t, float x, float y, float mul, boolean mini) {
        int kind = Defs.P_KIND[p.type];
        if (!mini) Sfx.play(shotSound(kind), 0.92f + rnd.nextFloat() * 0.16f);
        Shot s = new Shot();
        s.src = p;
        s.target = t;
        s.x = x;
        s.y = y;
        s.dmg = dmgOf(p) * mul;
        s.mini = mini;
        s.life = 3f;
        s.size = 1f;
        if (mini) kind = Defs.K_ROCK;
        if (kind == Defs.K_COMMANDER) kind = Defs.K_MISSILE;
        switch (kind) {
            case Defs.K_MISSILE: s.speed = 560; s.splash = 75; s.splashMul = 0.45f; break;
            case Defs.K_ROCK: s.speed = 620; break;
            case Defs.K_BUFF: kind = Defs.K_ROCK; s.speed = 600; s.size = 0.8f; break;
            case Defs.K_SWARM:
                s.speed = 720; s.size = 0.6f;
                s.x += rnd.nextFloat() * 30 - 15; s.y += rnd.nextFloat() * 30 - 15;
                break;
            case Defs.K_SMOKE: s.speed = 380; s.burnDps = s.dmg * 5f * fireMul(p); s.burnDur = 3f * fireDurMul(p); s.splash = 90; s.splashMul = 0.5f; break;
            case Defs.K_LANCE: {
                s.speed = 950;
                s.burnDps = s.dmg * 1.4f * fireMul(p);
                s.burnDur = 3.5f * fireDurMul(p);
                s.hit = new HashSet<>();
                float dx = t.x - x, dy = t.y - y, l = Math.max(1f, dist(0, 0, dx, dy));
                s.vx = dx / l * s.speed;
                s.vy = dy / l * s.speed;
                s.life = rangeOf(p) * 1.6f / s.speed;
                s.target = null;
                s.splash = 120;
                s.splashMul = 0.6f;
                break;
            }
            case Defs.K_FIREROCK: s.speed = 720; s.size = 0.7f; s.burnDps = s.dmg * 1.2f * fireMul(p); s.burnDur = 2f * fireDurMul(p); break;
            case Defs.K_ICE: {
                float im = iceMul(p);
                s.speed = 560; s.slowAmt = 0.5f; s.slowDur = 2f * im;
                s.stunCh = p.enchant == Defs.EN_GALACIA ? 0.45f : 0.25f * (im > 1f ? 1.15f : 1f);
                s.stunDur = 0.8f * im;
                break;
            }
            case Defs.K_TORNADO: {
                s.speed = 360;
                s.burnDps = s.dmg * 3f * fireMul(p);
                s.burnDur = 3f * fireDurMul(p);
                s.hit = new HashSet<>();
                float dx = t.x - x, dy = t.y - y, l = Math.max(1f, dist(0, 0, dx, dy));
                s.vx = dx / l * s.speed;
                s.vy = dy / l * s.speed;
                s.life = rangeOf(p) * 1.5f / s.speed;
                s.target = null;
                break;
            }
            default: s.speed = 600;
        }
        s.kind = kind;
        shots.add(s);
    }

    void updateShots(float dt) {
        for (int i = shots.size() - 1; i >= 0; i--) {
            Shot s = shots.get(i);
            s.life -= dt;
            s.spin += dt * 8f;
            if (s.kind == Defs.K_TORNADO || s.kind == Defs.K_LANCE) {
                s.x += s.vx * dt;
                s.y += s.vy * dt;
                boolean lance = s.kind == Defs.K_LANCE;
                for (int k = 0; k < enemies.size(); k++) {
                    Enemy e = enemies.get(k);
                    if (e.dead || e.phased || s.hit.contains(e)) continue;
                    if (dist(s.x, s.y, e.x, e.y) < (lance ? 34 : 46) + e.radius) {
                        s.hit.add(e);
                        hitEnemy(s, e, false);
                    }
                }
                if (s.life <= 0) {
                    // the hero lance bursts in a ring of fire where it ends
                    if (lance) burst(s, s.x, s.y);
                    shots.remove(i);
                }
                continue;
            }
            if (s.target == null || s.target.dead || s.target.phased) {
                s.target = findTarget(s.x, s.y, 300);
                if (s.target == null) { shots.remove(i); continue; }
            }
            Enemy t = s.target;
            float dx = t.x - s.x, dy = t.y - s.y, l = dist(0, 0, dx, dy);
            float step = s.speed * dt;
            if (l <= step + t.radius * 0.6f) {
                hitEnemy(s, t);
                shots.remove(i);
                continue;
            }
            s.vx = dx / l * s.speed;
            s.vy = dy / l * s.speed;
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            if (s.life <= 0) shots.remove(i);
        }
    }

    void hitEnemy(Shot s, Enemy e) { hitEnemy(s, e, true); }

    /** AoE: damage (and burn) every other enemy around the point */
    void burst(Shot s, float x, float y) {
        if (s.splash <= 0) return;
        ring(x, y, s.kind == Defs.K_LANCE ? 0xFFFF6A3D : 0xFFFFB060, s.splash * 1.6f);
        for (int k = 0; k < enemies.size(); k++) {
            Enemy o = enemies.get(k);
            if (o.dead || o.phased || dist(x, y, o.x, o.y) > s.splash + o.radius) continue;
            if (s.hit != null && s.hit.contains(o) && s.kind != Defs.K_LANCE) continue;
            damage(o, s.dmg * s.splashMul, s.src);
            if (s.burnDps > 0) applyBurn(o, s.burnDps * 0.6f, s.burnDur);
        }
    }

    void hitEnemy(Shot s, Enemy e, boolean splashNow) {
        if (splashNow && s.splash > 0) {
            // splash first so the main target is not counted twice
            if (s.hit == null) s.hit = new HashSet<>();
            s.hit.add(e);
            burst(s, e.x, e.y);
        }
        float dmg = s.dmg;
        Planet src = s.src;
        if (src != null && src.material == Defs.MAT_DIAMOND && rnd.nextFloat() < 0.15f) {
            dmg *= 2f;
            text(e.x, e.y - 50, "CRIT!", 0xFF9EF6FF);
        }
        if (src != null && !s.mini) {
            if (src.art == Defs.MOON_FROST && e.slowT > 0) dmg *= 1.1f;
            if (src.art == Defs.MOON_VOID && (e.boss || e.mini)) dmg *= 1.25f;
            if (src.enchant == Defs.EN_OVERCHARGE && ++src.overShots % 5 == 0) {
                dmg *= 2.5f;
                text(e.x, e.y - 50, "OVERCHARGE", 0xFFFFE27A);
                ring(e.x, e.y, 0xFFFFE27A, 120);
            }
            if (src.evo && ++src.evoShots % 4 == 0) dmg = evoStrike(e, dmg, src);
            if (src.eff == Defs.EFF_SHINY && rnd.nextFloat() < 0.10f) {
                dmg *= 2f;
                text(e.x, e.y - 50, "SHINY!", 0xFF9EF6FF);
            }
        }
        if (src != null && src.material == Defs.MAT_OBSIDIAN && e.shield > 0) {
            // obsidian cuts through half of the shield
            float pierce = Math.min(e.shield, dmg * 0.5f);
            e.hp -= pierce;
            if (e.admin) bossDamage += pierce;
            dmg -= pierce;
        }
        damage(e, dmg, s.src);
        if (src != null && !s.mini) {
            if (src.art == Defs.MOON_FROST) applySlow(e, 0.2f, 1.2f);
            if (src.enchant == Defs.EN_FROSTBITE) applySlow(e, 0.15f, 1.2f);
            if (src.material == Defs.MAT_STELLARIUM && ++src.chainShots % 6 == 0) chain(e, dmg * 0.6f, src);
        }
        if (s.burnDps > 0) applyBurn(e, s.burnDps, s.burnDur);
        else if (s.src != null && s.src.hellMoon) applyBurn(e, dmg * 0.6f * fireMul(s.src), 2.5f); // Hell Moon: every hit burns
        else if (s.src != null && s.src.eff == Defs.EFF_BLAZE) applyBurn(e, dmg * 0.4f * fireMul(s.src), 2f); // Blaze effect
        if (s.slowAmt > 0) applySlow(e, s.slowAmt, s.slowDur);
        if (s.stunCh > 0 && rnd.nextFloat() < s.stunCh && !e.boss && !e.mini) {
            e.stunT = Math.max(e.stunT, s.stunDur);
            text(e.x, e.y - 40, "STUN", 0xFF7DF3FF);
        }
        if (s.src != null && !s.mini) applyMaterialOnHit(e, dmg, s.src);

        int c;
        switch (s.kind) {
            case Defs.K_SMOKE: c = 0xFFFF4D3D; puff(e.x, e.y, 0x99FF3B2F); break;
            case Defs.K_FIREROCK: case Defs.K_TORNADO: case Defs.K_LANCE: c = 0xFFFF8A2B; break;
            case Defs.K_ICE: c = 0xFF9BEFFF; break;
            case Defs.K_MISSILE: c = 0xFFFFE08A; break;
            default: c = 0xFFD9C7B0;
        }
        for (int k = 0; k < 3; k++) particle(e.x, e.y, c, 140);
    }

    /** each evolved planet's 4th-shot special; returns the (maybe boosted) damage */
    float evoStrike(Enemy e, float dmg, Planet p) {
        switch (p.type) {
            case Defs.MARS:
                for (Enemy o : enemies) {
                    if (o.dead || o.phased || dist(e.x, e.y, o.x, o.y) > 140 + o.radius) continue;
                    applyBurn(o, dmg * 0.5f * fireMul(p), 3f);
                }
                ring(e.x, e.y, 0xFFFF6A2B, 280);
                for (int k = 0; k < 10; k++) particle(e.x, e.y, k % 2 == 0 ? 0xFFFF6A2B : 0xFFFFD23F, 220);
                return dmg;
            case Defs.NEPTUNE:
                for (Enemy o : enemies) {
                    if (o.dead || o.phased || dist(e.x, e.y, o.x, o.y) > 160 + o.radius) continue;
                    applySlow(o, 0.5f, 2f);
                }
                ring(e.x, e.y, 0xFF5EC8FF, 320);
                for (int k = 0; k < 10; k++) particle(e.x, e.y, 0xFF9BEFFF, 240);
                return dmg;
            case Defs.MOON:
                text(e.x, e.y - 50, "ECLIPSE", 0xFFC58CFF);
                ring(e.x, e.y, 0xFF8A4BFF, 140);
                return dmg * 3f;
            case Defs.SATELLITE:
                ring(e.x, e.y, 0xFFFFD23F, 120);
                return dmg * 2f;
            default: // Gaia quake: stun everything around the target
                for (Enemy o : enemies) {
                    if (o.dead || o.phased || dist(e.x, e.y, o.x, o.y) > 140 + o.radius) continue;
                    o.stunT = Math.max(o.stunT, o.boss ? 0.3f : o.mini ? 0.6f : 1.0f);
                }
                ring(e.x, e.y, 0xFF7CFF8A, 280);
                for (int k = 0; k < 10; k++) particle(e.x, e.y, k % 2 == 0 ? 0xFF7CFF8A : 0xFFB88A4F, 220);
                return dmg;
        }
    }

    /** Stellarium: bounce to the two nearest other enemies */
    void chain(Enemy from, float dmg, Planet src) {
        int n = 0;
        for (int k = 0; k < 2; k++) {
            Enemy best = null;
            float bd = 260;
            for (Enemy o : enemies) {
                if (o == from || o.dead || o.phased || o.chainMark == chainId + 1) continue;
                float d = dist(from.x, from.y, o.x, o.y);
                if (d < bd) { bd = d; best = o; }
            }
            if (best == null) break;
            best.chainMark = chainId + 1;
            beam(from.x, from.y, best.x, best.y, 0xFFFF7AE0);
            damage(best, dmg, src);
            n++;
        }
        chainId++;
    }

    int chainId;

    void applyMaterialOnHit(Enemy e, float dmg, Planet p) {
        if (p.material == Defs.MAT_URANIUM && !e.dead) {
            e.radDps = Math.max(e.radDps, dmg * 0.4f);
            e.radT = 3f;
        }
    }

    void applySlow(Enemy e, float amt, float dur) {
        if (e.type == Defs.E_WRAITH) return; // raid wraiths can't be slowed
        if (e.boss) amt *= 0.5f;
        if (amt >= e.slow) { e.slow = amt; e.slowT = Math.max(e.slowT, dur); }
    }

    void applyBurn(Enemy e, float dps, float dur) {
        if (e.type == Defs.E_HELL) return;
        if (dps >= e.burnDps || e.burnT <= 0) e.burnDps = dps;
        e.burnT = Math.max(e.burnT, dur);
    }

    void damage(Enemy e, float amt, Planet src) {
        if (e.dead || amt <= 0) return;
        e.noDmgT = 0;
        if (e.admin) bossDamage += Math.min(amt, Math.max(0, e.hp + e.shield));
        if (e.shield > 0) {
            float a = Math.min(e.shield, amt);
            e.shield -= a;
            amt -= a;
        }
        e.hp -= amt;
        e.hitFlash = 0.08f;
        if (e.hp <= 0) kill(e, src);
    }

    static int shotSound(int kind) {
        switch (kind) {
            case Defs.K_MISSILE: return Sfx.S_MISSILE;
            case Defs.K_ROCK: case Defs.K_BUFF: return Sfx.S_ROCK;
            case Defs.K_SWARM: return Sfx.S_RAPID;
            case Defs.K_SMOKE: case Defs.K_FIREROCK: case Defs.K_TORNADO: return Sfx.S_FIRE;
            case Defs.K_ICE: case Defs.K_AURA: return Sfx.S_ICE;
            case Defs.K_LANCE: return Sfx.S_LANCE;
            default: return Sfx.S_RAPID;
        }
    }

    int kills, placedCount;

    void kill(Enemy e, Planet src) {
        e.dead = true;
        kills++;
        Sfx.play(e.boss || e.mini ? Sfx.BOSS_KILL : Sfx.KILL, 0.9f + rnd.nextFloat() * 0.25f);
        boolean big = e.boss || e.mini;
        int c = e.boss ? 0xFFB86BFF : 0xFFFFB35C;
        ring(e.x, e.y, c, e.radius * 2f);
        for (int i = 0; i < (big ? 40 : 8); i++) particle(e.x, e.y, c, big ? 400 : 200);
        if (src != null) {
            src.kills++;
            float spChance = (src.enchant == Defs.EN_MIDAS ? 0.2f : 0f) + (src.material == Defs.MAT_GOLD ? 0.25f : 0f) + (src.art == Defs.MOON_GOLD ? 0.1f : 0f);
            if (src.enchant == Defs.EN_NOVA) {
                // nova: the kill explodes on its neighbours
                float boom = e.maxHp * 0.3f;
                ring(e.x, e.y, 0xFFFFB0F0, 200);
                for (Enemy o : enemies) {
                    if (o == e || o.dead || o.phased || dist(e.x, e.y, o.x, o.y) > 130 + o.radius) continue;
                    novaQueue.add(new Object[]{o, Math.min(boom, dmgOf(src) * 3f), src});
                }
            }
            if (spChance > 0 && rnd.nextFloat() < spChance) {
                stars++;
                starText(e.x, e.y - 30, 1);
            }
            if (src.enchant == Defs.EN_MOON && (e.type == Defs.E_EXOMOON || e.type == Defs.E_ALTMOON)
                    && src.moons < 6 && rnd.nextFloat() < 0.10f) {
                src.moons++;
                text(src.x, src.y - 80, "+1 Moon", 0xFFE6E8F5);
            }
        }
        if (e.boss || e.mini) {
            int sp = e.boss ? 8 : 4, mil = e.boss ? 3 : 2, cry = e.boss ? 2 : 1;
            stars += sp;
            starText(e.x, e.y - 50, sp);
            earnMillite += mil;
            earnCrystal += cry;
            bossKills++;
            text(e.x, e.y - 100, "+" + mil + " Millite  +" + cry + " Crystal", 0xFFC58CFF);
            shake = 0.6f;
            if (src != null && src.enchant == Defs.EN_GIANTTIC) {
                src.bonusDmg += 0.05f;
                text(src.x, src.y - 80, "+5% DMG", 0xFFFF4F8B);
            }
            if (src != null && src.enchant == Defs.EN_GALAXIA) {
                HeatWave h = new HeatWave();
                h.d = total;
                h.maxHp = e.maxHp * 0.5f;
                h.hp = h.maxHp;
                h.summon = e.type;
                h.summonBoss = e.boss;
                h.speed = 90f;
                h.src = src;
                heat.add(h);
                showBanner(L.t("Galaxia เรียก ", "Galaxia summoned ") + Defs.E_NAME[e.type] + "!", 0xFF5EF2FF);
            }
        }
        if (e.hauntT > 0 && !e.boss) {
            // Savior: the haunted enemy returns as a spirit around the nearest attacking planet
            Planet host = null;
            float bd = 1e9f;
            for (Planet q : slots) {
                if (q == null || !attacks(q)) continue;
                float d = dist(q.x, q.y, e.x, e.y);
                if (d < bd) { bd = d; host = q; }
            }
            if (host != null && host.spirits < 8) {
                host.spirits++;
                text(host.x, host.y - 80, L.t("+1 วิญญาณ", "+1 Spirit"), 0xFFB8FFF0);
                for (int k = 0; k < 8; k++) particle(e.x, e.y, 0xFFB8FFF0, 160);
            }
        }
        if (stage == Defs.RAID) {
            earnOil += e.boss ? 8 : e.mini ? 3 : 0;
        }
        if (e.type == Defs.E_VOIDLING && !e.child) {
            for (int k = 0; k < 2; k++) {
                Enemy m = makeEnemy(Defs.E_VOIDLING);
                m.child = true;
                m.maxHp = e.maxHp * 0.35f;
                m.hp = m.maxHp;
                m.radius *= 0.7f;
                m.d = Math.max(0, e.d - 20 * k);
                pointAt(m.d, tmp);
                m.x = tmp[0];
                m.y = tmp[1];
                pending.add(m);
            }
        }
        if (e.type == Defs.E_MEGA) {
            for (int k = 0; k < 2; k++) {
                Enemy m = makeEnemy(Defs.E_METEOR);
                m.maxHp = e.maxHp * 0.3f;
                m.hp = m.maxHp;
                m.d = Math.max(0, e.d - 15 * k);
                pointAt(m.d, tmp);
                m.x = tmp[0];
                m.y = tmp[1];
                pending.add(m);
            }
        }
    }

    void updateEnemies(float dt) {
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            if (e.dead) { enemies.remove(i); continue; }
            e.angle += dt * (e.boss ? 60f : 90f);
            if (e.hitFlash > 0) e.hitFlash -= dt;
            if (e.burnT > 0) {
                e.burnT -= dt;
                damage(e, e.burnDps * dt, null);
                if (e.dead) { enemies.remove(i); continue; }
                if (rnd.nextFloat() < dt * 8) particle(e.x, e.y - e.radius * 0.5f, 0xFFFF7A2B, 60);
            }
            if (e.radT > 0) {
                e.radT -= dt;
                damage(e, e.radDps * dt, null);
                if (e.dead) { enemies.remove(i); continue; }
                if (rnd.nextFloat() < dt * 6) particle(e.x, e.y, 0xFF7CFF4A, 50);
            }
            if (e.slowT > 0) { e.slowT -= dt; if (e.slowT <= 0) e.slow = 0; }
            if (e.hauntT > 0) e.hauntT -= dt;
            if (e.stunT > 0) e.stunT -= dt;
            else {
                float nd = e.d + e.speed * (1f - e.slow) * dt;
                e.blocked = false;
                if (!e.phased) {
                    // Cyber walls stop enemies until they break through
                    for (Wall w : walls) {
                        if (w.dead || w.d < e.d - 2 || w.d > nd + e.radius * 0.6f) continue;
                        nd = Math.min(nd, Math.max(e.d, w.d - e.radius * 0.6f));
                        w.hp -= (e.boss ? 60f : e.mini ? 30f : 12f) * e.baseDmg * dt * Defs.ST_HP[stage];
                        w.hitT = 0.15f;
                        e.blocked = true;
                        if (w.hp <= 0) {
                            w.dead = true;
                            ring(w.x, w.y, 0xFF5EF2FF, 90);
                            for (int k = 0; k < 12; k++) particle(w.x, w.y, 0xFF5EF2FF, 220);
                        }
                        break;
                    }
                }
                e.d = nd;
            }

            e.abilT += dt;
            e.noDmgT += dt;
            switch (e.type) {
                case Defs.E_ALTMOON:
                    e.phased = (e.abilT % 3.4f) > 2.4f;
                    break;
                case Defs.E_ALTPLANET:
                    if (e.abilT > 4.5f) {
                        e.abilT = 0;
                        ring(e.x, e.y, 0xFFFF5BD8, 70);
                        e.d += 120;
                    }
                    break;
                case Defs.E_EXOMOON:
                    if (e.abilT > 2.5f) {
                        e.abilT = 0;
                        for (Enemy o : enemies) {
                            if (o.dead || o.boss || o.mini || dist(o.x, o.y, e.x, e.y) > 170) continue;
                            o.hp = Math.min(o.maxHp, o.hp + o.maxHp * 0.08f);
                        }
                        ring(e.x, e.y, 0xFF7CFF8A, 170);
                    }
                    break;
                case Defs.E_WRAITH:
                    e.hp = Math.min(e.maxHp, e.hp + e.maxHp * 0.03f * dt);
                    break;
                case Defs.E_TITAN:
                    if (e.noDmgT > 3f && e.shield < e.maxShield) e.shield = Math.min(e.maxShield, e.shield + e.maxShield * 0.15f * dt);
                    if (e.abilT > 6f) {
                        e.abilT = 0;
                        for (int k = 0; k < 2; k++) {
                            Enemy m = makeEnemy(Defs.E_VOIDLING);
                            m.d = Math.max(0, e.d - 40 - 30 * k);
                            pointAt(m.d, tmp);
                            m.x = tmp[0];
                            m.y = tmp[1];
                            pending.add(m);
                        }
                        ring(e.x, e.y, 0xFFFF2B5B, 200);
                    }
                    break;
                case Defs.E_EXOPLANET:
                    if (e.noDmgT > 2.5f && e.shield < e.maxShield)
                        e.shield = Math.min(e.maxShield, e.shield + e.maxShield * 0.25f * dt);
                    break;
                case Defs.E_BLACKHOLE:
                    if (e.abilT > 6f) {
                        e.abilT = 0;
                        ArrayList<Planet> near = new ArrayList<>();
                        for (Planet p : slots)
                            if (p != null && p.disabledT <= 0 && dist(p.x, p.y, e.x, e.y) < 420) near.add(p);
                        if (!near.isEmpty()) {
                            Planet p = near.get(rnd.nextInt(near.size()));
                            p.disabledT = 3f;
                            text(p.x, p.y - 70, L.t("ถูกดูด!", "Pulled!"), 0xFFB86BFF);
                        }
                        ring(e.x, e.y, 0xFF8A5BFF, 420);
                    }
                    break;
                default:
                    break;
            }

            if (e.d >= total) {
                baseHp -= e.baseDmg;
                leak += e.baseDmg;
                Sfx.play(Sfx.BASE_HIT);
                shake = 0.35f;
                pointAt(total, tmp);
                ring(tmp[0], tmp[1], 0xFFFF4D5E, 110);
                text(tmp[0], tmp[1] - 70, "-" + e.baseDmg, 0xFFFF4D5E);
                e.dead = true;
                enemies.remove(i);
                continue;
            }
            pointAt(e.d, tmp);
            e.x = tmp[0];
            e.y = tmp[1];
        }
    }

    void updateHeat(float dt) {
        for (int i = heat.size() - 1; i >= 0; i--) {
            if (i >= heat.size()) continue;
            HeatWave h = heat.get(i);
            h.d -= h.speed * dt;
            h.angle += dt * 60f;
            boolean ally = h.summon != -1; // summoned boss (>=0) or a King's knight moon (-2)
            for (Enemy e : enemies) {
                if (e.dead || h.hit.contains(e) || Math.abs(e.d - h.d) > (ally ? 60 : 40) + e.radius) continue;
                h.hit.add(e);
                float need = e.hp + e.shield;
                float dmg = Math.min(h.hp, need);
                damage(e, dmg, ally ? null : h.src);
                if (!ally) {
                    applyBurn(e, dmgOf(h.src) * 0.35f * fireMul(h.src), 3f * fireDurMul(h.src));
                    h.hp -= dmg * 0.8f;
                } else {
                    h.hp -= dmg;
                    ring(e.x, e.y, h.summon == -2 ? 0xFFFFD23F : 0xFF5EF2FF, 80);
                }
                if (h.hp <= 0) break;
            }
            if (h.hp <= 0 || h.d <= 0) {
                pointAt(Math.max(0, h.d), tmp);
                ring(tmp[0], tmp[1], ally ? 0xFF5EF2FF : 0xFFFFB627, ally ? 140 : 90);
                heat.remove(i);
            }
        }
    }

    // ------------------------------------------------------------------ fx
    void updateFx(float dt) {
        for (int i = fx.size() - 1; i >= 0; i--) {
            Fx f = fx.get(i);
            f.life -= dt;
            if (f.life <= 0) { fx.remove(i); continue; }
            f.x += f.vx * dt;
            f.y += f.vy * dt;
            if (f.kind == Fx.PART) { f.vx *= 0.92f; f.vy *= 0.92f; }
        }
        if (fx.size() > 400) fx.subList(0, fx.size() - 400).clear();
    }

    void particle(float x, float y, int color, float spd) {
        Fx f = new Fx();
        f.kind = Fx.PART;
        f.x = x;
        f.y = y;
        double a = rnd.nextDouble() * Math.PI * 2;
        float v = spd * (0.3f + rnd.nextFloat());
        f.vx = (float) Math.cos(a) * v;
        f.vy = (float) Math.sin(a) * v;
        f.life = f.maxLife = 0.4f + rnd.nextFloat() * 0.4f;
        f.size = 4 + rnd.nextFloat() * 6;
        f.color = color;
        fx.add(f);
    }

    void beam(float x, float y, float x2, float y2, int color) {
        Fx f = new Fx();
        f.kind = Fx.BEAM;
        f.x = x;
        f.y = y;
        f.x2 = x2;
        f.y2 = y2;
        f.life = f.maxLife = 0.25f;
        f.color = color;
        fx.add(f);
    }

    /** damage queued by kills (Nova), applied after the current hit finishes */
    final java.util.ArrayList<Object[]> novaQueue = new java.util.ArrayList<>();

    void ring(float x, float y, int color, float size) {
        Fx f = new Fx();
        f.kind = Fx.RING;
        f.x = x;
        f.y = y;
        f.life = f.maxLife = 0.45f;
        f.size = size;
        f.color = color;
        fx.add(f);
    }

    void puff(float x, float y, int color) {
        Fx f = new Fx();
        f.kind = Fx.PUFF;
        f.x = x;
        f.y = y;
        f.vy = -30;
        f.life = f.maxLife = 0.7f;
        f.size = 40;
        f.color = color;
        fx.add(f);
    }

    void text(float x, float y, String s, int color) {
        Fx f = new Fx();
        f.kind = Fx.TEXT;
        f.x = x;
        f.y = y;
        f.vy = -50;
        f.life = f.maxLife = 1.1f;
        f.text = s;
        f.color = color;
        fx.add(f);
    }

    void starText(float x, float y, int amount) {
        Fx f = new Fx();
        f.kind = Fx.STARTEXT;
        f.x = x;
        f.y = y;
        f.vy = -60;
        f.life = f.maxLife = 1.1f;
        f.text = "+" + amount;
        f.color = 0xFFFFD23F;
        fx.add(f);
    }
}
