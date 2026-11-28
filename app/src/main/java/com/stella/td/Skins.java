package com.stella.td;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Planet skins (cosmetic). A skin is a style applied to one planet; the key style*P_COUNT+planet
 * identifies it. Skins come from Raid capsules (bought in the Raid shop) or from staff.
 */
final class Skins {
    private Skins() {}

    static final int GOLDEN = 0, GRAVEYARD = 1, WHITE = 2, AURORA = 3, RED = 4, ELITE = 5, SHINY_PURPLE = 6, SHINY = 7,
            MADNESS = 8, KING = 9, ANNIV = 10, NEON = 11, FROZEN = 12, GALAXY = 13, INFERNO = 14, CELESTIAL = 15, ADMIN = 16;
    static final int STYLES = 17;
    static final String[] NAME = {"Golden", "Graveyard", "White", "Aurora", "Red", "Elite", "Shiny Purple", "Shiny",
            "Madness", "King", "1st Anniversary King Elite Gold Shine", "Neon", "Frozen", "Galaxy", "Inferno", "Celestial", "Admin"};
    static final String[] NAME_TH = {"ทองคำ", "สุสาน", "ขาว", "ออโรร่า", "แดง", "อีลีท", "ม่วงประกาย", "ประกาย",
            "คลั่ง", "ราชา", "ครบรอบ 1 ปี ราชาอีลีททองประกาย", "นีออน", "น้ำแข็ง", "กาแล็กซี", "อินเฟอร์โน", "เซเลสเชียล", "แอดมิน"};
    /** capsule tier of each style: Common, Rare, Epic, Legend, Mythic, Exclusive */
    static final int[] TIER = {Defs.COMMON, Defs.COMMON, Defs.RARE, Defs.RARE, Defs.RARE, Defs.EPIC, Defs.EPIC, Defs.EPIC,
            Defs.LEGEND, Defs.MYTHIC, Defs.EXCLUSIVE, Defs.EPIC, Defs.RARE, Defs.LEGEND, Defs.EPIC, Defs.EXCLUSIVE, Defs.EXCLUSIVE};
    /** event skins never come out of capsules: 1st Anniversary (anniversary event), Admin (Admin Abuse capsule) */
    static boolean event(int style) { return style == ANNIV || style == ADMIN; }
    /** capsule chance of each tier (%) */
    static final int[] TIER_ORDER = {Defs.COMMON, Defs.RARE, Defs.EPIC, Defs.LEGEND, Defs.MYTHIC, Defs.EXCLUSIVE};
    static final float[] TIER_RATE = {55f, 27f, 12.5f, 4f, 1.3f, 0.2f};
    /** Crystal Oil given back for a skin you already have */
    static final int[] DUP_OIL = {4, 0, 0, 0, 0, 0, 0};

    /** which planets a style exists for */
    static boolean fits(int style, int planet) {
        switch (style) {
            case GOLDEN: return planet == Defs.SATELLITE || planet == Defs.MOON;
            case GRAVEYARD: return planet == Defs.MARS || planet == Defs.PLUTO;
            case WHITE: case ELITE: case SHINY_PURPLE: return planet == Defs.SUN;
            case AURORA: return planet == Defs.NEPTUNE || planet == Defs.URANUS;
            case RED: return planet == Defs.EARTH;
            case SHINY: return planet == Defs.PLUTO || planet == Defs.MARS || planet == Defs.MERCURY;
            case MADNESS: case GALAXY: return Defs.P_CATEGORY[planet] == Defs.CAT_SOLAR;
            case NEON: return planet == Defs.SATELLITE || planet == Defs.SMART_SAT || planet == Defs.SAVIOR_SAT || planet == Defs.COMMANDER;
            case FROZEN: return planet == Defs.NEPTUNE || planet == Defs.URANUS || planet == Defs.PLUTO || planet == Defs.MOON;
            case INFERNO: return planet == Defs.SUN || planet == Defs.MARS || planet == Defs.VENUS || planet == Defs.MERCURY;
            case KING: case ANNIV: case CELESTIAL: case ADMIN: return true;
            default: return false;
        }
    }

    /** planets 0-19 keep the original key (style * 20 + planet) so old saves stay valid; newer planets use 5000 + style * 100 + planet */
    static final int OLD = 20, NEW_BASE = 5000;

    static int key(int style, int planet) { return planet < OLD ? style * OLD + planet : NEW_BASE + style * 100 + planet; }

    static int styleOf(int key) { return key >= NEW_BASE ? (key - NEW_BASE) / 100 : key / OLD; }

    static int planetOf(int key) { return key >= NEW_BASE ? (key - NEW_BASE) % 100 : key % OLD; }

    static String name(int key) {
        int st = styleOf(key), p = planetOf(key);
        String s = L.en ? NAME[st] : NAME_TH[st];
        return s + " " + Defs.P_NAME[p];
    }

    static List<Integer> all() {
        List<Integer> out = new ArrayList<>();
        for (int st = 0; st < STYLES; st++) for (int p = 0; p < Defs.P_COUNT; p++) if (fits(st, p)) out.add(key(st, p));
        return out;
    }

    /** one Raid capsule draw: a tier by chance, then any skin of that tier */
    static int roll(Random r) {
        float x = r.nextFloat() * 100f, acc = 0;
        int tier = Defs.COMMON;
        for (int i = 0; i < TIER_ORDER.length; i++) {
            acc += TIER_RATE[i];
            if (x < acc) { tier = TIER_ORDER[i]; break; }
        }
        List<Integer> pool = new ArrayList<>();
        for (int k : all()) if (TIER[styleOf(k)] == tier && !event(styleOf(k))) pool.add(k);
        return pool.get(r.nextInt(pool.size()));
    }

    /** an Admin skin for a random planet (Admin Capsule prize) */
    static int rollAdmin(Random r) {
        List<Integer> pool = new ArrayList<>();
        for (int k : all()) if (styleOf(k) == ADMIN) pool.add(k);
        return pool.get(r.nextInt(pool.size()));
    }
}
