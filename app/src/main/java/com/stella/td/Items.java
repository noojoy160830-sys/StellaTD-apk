package com.stella.td;

import java.util.Random;

/** Bag items: evolution materials and capsules. The Raid Capsule (skins) keeps its own counter in Profile. */
final class Items {
    private Items() {}

    static final int STARDUST = 0, NEBULA = 1, CATALYST = 2, ART_CAPSULE = 3, SUPPLY = 4, ADMIN_CAPSULE = 5, EFF_TOKEN = 6;
    static final int COUNT = 11;
    static final String[] NAME = {"Stardust", "Nebula Core", "Evo Catalyst", "Artifact Capsule", "Supply Capsule", "Admin Capsule",
            "Golden Effect", "Silver Effect", "Rainbow Effect", "Shiny Effect", "Blaze Effect"};
    static final String[] DESC = {
            "ผงดาว วัตถุดิบพื้นฐาน ใช้คราฟ Nebula Core",
            "แกนเนบิวลา ใช้คราฟ Evo Catalyst และ Artifact Capsule",
            "ตัวเร่งวิวัฒนาการ ใช้วิวัฒน์ดาว",
            "สุ่ม Artifact 1 ชิ้น",
            "สุ่ม Star, Millite, Crystal หรือวัตถุดิบวิวัฒน์",
            "Capsule จากกิจกรรม Admin Abuse: Gold Coin, Effect, Artifact, Evo Catalyst หรือสกิน Admin",
            "ใส่ Effect Golden ให้ดาว 1 ดวง", "ใส่ Effect Silver ให้ดาว 1 ดวง", "ใส่ Effect Rainbow ให้ดาว 1 ดวง",
            "ใส่ Effect Shiny ให้ดาว 1 ดวง", "ใส่ Effect Blaze ให้ดาว 1 ดวง"};
    static final String[] DESC_EN = {
            "Basic material, crafted into Nebula Cores",
            "Crafted into Evo Catalysts and Artifact Capsules",
            "Used to evolve planets",
            "Opens into 1 random Artifact",
            "Opens into Star, Millite, Crystal or evolution materials",
            "Admin Abuse capsule: Gold Coins, an Effect, an Artifact, Evo Catalyst or an Admin skin",
            "Gives one planet the Golden effect", "Gives one planet the Silver effect", "Gives one planet the Rainbow effect",
            "Gives one planet the Shiny effect", "Gives one planet the Blaze effect"};
    static final int[] RARITY = {Defs.COMMON, Defs.RARE, Defs.EPIC, Defs.EPIC, Defs.RARE, Defs.EXCLUSIVE,
            Defs.LEGEND, Defs.EPIC, Defs.MYTHIC, Defs.LEGEND, Defs.MYTHIC};
    static final int[] COLOR = {0xFFB8C8FF, 0xFFB86BFF, 0xFF7CFF8A, 0xFFFF9A3B, 0xFF5EC8FF, 0xFFFF3B5B,
            0xFFFFD23F, 0xFFE0E8F0, 0xFFFF7AE0, 0xFF9EF6FF, 0xFFFF6A2B};

    static boolean isCapsule(int i) { return i == ART_CAPSULE || i == SUPPLY || i == ADMIN_CAPSULE; }

    static boolean isEffect(int i) { return i >= EFF_TOKEN && i < EFF_TOKEN + Defs.EFF_COUNT; }

    // ---- Admin Capsule: kind (0 gold, 1 effect token, 2 artifact, 3 catalyst, 4 admin skin), weight, min, max
    static final int[][] ADMIN_TABLE = {{0, 38, 20, 80}, {1, 24, 1, 1}, {2, 14, 1, 1}, {3, 16, 1, 2}, {4, 8, 1, 1}};

    // ---- Artifact Capsule: weights per artifact (Hell, Frost, Storm, Gold, Void)
    static final int[] ART_WEIGHT = {6, 28, 28, 24, 14};

    static int rollArtifact(Random r) {
        int sum = 0;
        for (int w : ART_WEIGHT) sum += w;
        int x = r.nextInt(sum);
        for (int i = 0; i < ART_WEIGHT.length; i++) { x -= ART_WEIGHT[i]; if (x < 0) return i; }
        return 0;
    }

    // ---- Supply Capsule: kind (0 Star, 1 Millite, 2 Crystal, 3 Stardust, 4 Nebula), weight, min, max
    static final int[][] SUPPLY_TABLE = {{0, 40, 600, 1500}, {1, 22, 6, 15}, {2, 15, 3, 8}, {3, 18, 4, 8}, {4, 5, 1, 2}};

    // ---- crafting: inputs {item or -1 star, -2 millite, -3 oil, amount}..., output item, amount
    static final int[][][] RECIPE_IN = {
            {{STARDUST, 10}},
            {{NEBULA, 3}, {-2, 20}},
            {{NEBULA, 4}, {-3, 60}},
            {{STARDUST, 15}, {-1, 2000}}};
    static final int[] RECIPE_OUT = {NEBULA, CATALYST, ART_CAPSULE, SUPPLY};

    static String costName(int id) {
        switch (id) {
            case -1: return "Star";
            case -2: return "Millite";
            case -3: return "Crystal Oil";
            default: return NAME[id];
        }
    }
}
