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
            MADNESS = 8, KING = 9, ANNIV = 10;
    static final int STYLES = 11;
    static final String[] NAME = {"Golden", "Graveyard", "White", "Aurora", "Red", "Elite", "Shiny Purple", "Shiny",
            "Madness", "King", "1st Anniversary King Elite Gold Shine"};
    static final String[] NAME_TH = {"ทองคำ", "สุสาน", "ขาว", "ออโรร่า", "แดง", "อีลีท", "ม่วงประกาย", "ประกาย",
            "คลั่ง", "ราชา", "ครบรอบ 1 ปี ราชาอีลีททองประกาย"};
    /** capsule tier of each style: Common, Rare, Epic, Legend, Mythic, Exclusive */
    static final int[] TIER = {Defs.COMMON, Defs.COMMON, Defs.RARE, Defs.RARE, Defs.RARE, Defs.EPIC, Defs.EPIC, Defs.EPIC,
            Defs.LEGEND, Defs.MYTHIC, Defs.EXCLUSIVE};
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
            case MADNESS: return Defs.P_CATEGORY[planet] == Defs.CAT_SOLAR;
            case KING: case ANNIV: return true;
            default: return false;
        }
    }

    static int key(int style, int planet) { return style * Defs.P_COUNT + planet; }

    static int styleOf(int key) { return key / Defs.P_COUNT; }

    static int planetOf(int key) { return key % Defs.P_COUNT; }

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
        for (int k : all()) if (TIER[styleOf(k)] == tier) pool.add(k);
        return pool.get(r.nextInt(pool.size()));
    }
}
