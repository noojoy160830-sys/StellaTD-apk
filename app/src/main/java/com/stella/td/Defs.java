package com.stella.td;

/** All static game data: planets, enchants, effects, enemies and stages. */
final class Defs {
    private Defs() {}

    // ---------- rarity ----------
    static final int COMMON = 0, RARE = 1, EPIC = 2, LEGEND = 3, MYTHIC = 4, GALAXY = 5;
    static final String[] RARITY_NAME = {"Common", "Rare", "Epic", "Legend", "Mythical", "Galaxy"};
    static final int[] RARITY_COLOR = {0xFFC9D3E3, 0xFF4FA3FF, 0xFFB86BFF, 0xFFFFB627, 0xFFFF4F8B, 0xFF5EF2FF};

    // ---------- planets ----------
    static final int EARTH = 0, SATELLITE = 1, MOON = 2, SATURN = 3, JUPITER = 4, MARS = 5,
            MERCURY = 6, VENUS = 7, URANUS = 8, NEPTUNE = 9, PLUTO = 10, SUN = 11;
    static final int P_COUNT = 12;
    static final String[] P_NAME = {"Earth", "Satellite", "Moon", "Saturn", "Jupiter", "Mars",
            "Mercury", "Venus", "Uranus", "Neptune", "Pluto", "Sun"};
    static final String[] P_DESC = {
            "ยิงมิสไซล์อวกาศใส่ศัตรู",
            "ผลิตดาวให้ผู้เล่นเรื่อย ๆ",
            "ปาอุกกาบาตเล็ก แรงกว่า Earth",
            "ปาอุกกาบาตเล็กจำนวนมาก รัวและถี่",
            "พ่นควันแดง ติดไฟแรงมาก",
            "ผลิตดาวได้มากกว่า Satellite",
            "อุกกาบาตติดไฟ รัวและถี่",
            "พายุหมุนทะลุศัตรู ติดไฟแรง",
            "ก้อนน้ำแข็ง ทำให้ช้าและอาจ stun",
            "โซนความเย็น สโลว์ศัตรูทุกตัวในระยะ",
            "บัฟดวงดาวรอบ ๆ +15% ดาเมจ",
            "ปล่อยคลื่นความร้อนจากฐาน แรงขึ้นทุกคลื่น"};
    static final int[] P_RARITY = {COMMON, COMMON, COMMON, RARE, RARE, EPIC,
            LEGEND, LEGEND, LEGEND, LEGEND, LEGEND, MYTHIC};

    static final int K_MISSILE = 0, K_ROCK = 1, K_PRODUCE = 2, K_SWARM = 3, K_SMOKE = 4,
            K_FIREROCK = 5, K_TORNADO = 6, K_ICE = 7, K_AURA = 8, K_BUFF = 9, K_SUNWAVE = 10;
    static final int[] P_KIND = {K_MISSILE, K_PRODUCE, K_ROCK, K_SWARM, K_SMOKE, K_PRODUCE,
            K_FIREROCK, K_TORNADO, K_ICE, K_AURA, K_BUFF, K_SUNWAVE};
    /** damage per hit (producers: stars per cycle) */
    static final float[] P_DMG = {14, 1, 22, 5, 4, 2, 7, 12, 18, 6, 12, 50};
    /** seconds between attacks (producers: seconds per cycle) */
    static final float[] P_CD = {0.9f, 10f, 1.0f, 0.2f, 0.9f, 10f, 0.16f, 1.4f, 0.9f, 0.5f, 1.1f, 7f};
    static final float[] P_RANGE = {270, 0, 270, 260, 240, 0, 290, 320, 280, 250, 260, 0};

    static final String[] P_DESC_EN = {
            "Fires space missiles",
            "Produces SP over time",
            "Throws small meteors, stronger than Earth",
            "Rapid swarm of tiny meteors",
            "Red smoke with strong burn",
            "Produces more SP than Satellite",
            "Rapid burning meteors",
            "Piercing tornado with strong burn",
            "Ice chunks that slow and may stun",
            "Cold zone slows every enemy in range",
            "Buffs nearby planets +15% damage",
            "Heat waves from the base, stronger each time"};
    /** in-match SP cost to place each planet */
    static final int[] PLACE_COST = {1, 2, 2, 3, 3, 4, 5, 5, 5, 5, 5, 8};
    static final int MAX_LEVEL = 5;

    // ---------- enchants (Factory, rolled with Millite) ----------
    static final int EN_STRONG = 0, EN_REACH = 1, EN_MOVING = 2, EN_LIGHTNING = 3, EN_STORM = 4, EN_TECH = 5,
            EN_MIDAS = 6, EN_ELECTRO = 7, EN_CRYSTALRAIN = 8, EN_MOON = 9, EN_GALACIA = 10, EN_LAVA = 11,
            EN_MEGALONIA = 12, EN_GIANTTIC = 13, EN_GALAXIA = 14;
    static final int EN_COUNT = 15;
    static final String[] EN_NAME = {"Strong", "Reach", "Moving", "Lightning", "Storm", "Technology", "Midas", "Electro",
            "Crystal Rain", "Moon", "Galacia", "Lava", "Megalonia", "Gianttic", "Galaxia"};
    static final int[] EN_RARITY = {COMMON, COMMON, COMMON, RARE, RARE, RARE, EPIC, EPIC, EPIC, LEGEND, LEGEND, LEGEND,
            MYTHIC, MYTHIC, GALAXY};
    /** number of tiers (0 = no tiers) */
    static final int[] EN_TIERS = {3, 2, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    static final float[] EN_CHANCE = {20f, 20f, 20f, 8.33f, 8.33f, 8.34f, 3.33f, 3.33f, 3.34f, 1.33f, 1.33f, 1.34f, 0.45f, 0.45f, 0.1f};
    static final float[] STRONG_T = {0.05f, 0.07f, 0.10f}, REACH_T = {0.06f, 0.12f}, MOVING_T = {0.04f, 0.08f, 0.12f};
    static final String[] EN_DESC = {
            "+5 / 7 / 10% ดาเมจ", "+6 / 12% ระยะ", "+4 / 8 / 12% ความเร็วโจมตี",
            "+15% ความเร็วโจมตี", "+15% ระยะ", "+15% ดาเมจ",
            "ผลิต SP มากขึ้น 50% / ฆ่าศัตรูมีโอกาสได้ SP", "+12% ความเร็วโจมตี +7% ระยะ", "+20% ดาเมจ",
            "ดวงจันทร์จิ๋วช่วยยิง 10% ฆ่าศัตรูสายดวงจันทร์ 10% ได้ดวงจันทร์เพิ่ม",
            "+10% ดาเมจ น้ำแข็ง stun นานและบ่อยขึ้น", "+10% ดาเมจ ไฟแรงขึ้นและติดนานขึ้น",
            "+17% ดาเมจ ระยะ ความเร็ว ถ้าเป็นไฟ/น้ำแข็ง สถานะแรงขึ้น 15% และนานขึ้น",
            "+30% ดาเมจ -15% ความเร็ว ฆ่าบอสแต่ละตัว +5% ดาเมจ",
            "+50% ดาเมจ +20% ความเร็ว ฆ่าบอสจะเรียกบอสตัวนั้นมาสู้ให้ (เลือด 50%)"};
    static final String[] EN_DESC_EN = {
            "+5 / 7 / 10% damage", "+6 / 12% range", "+4 / 8 / 12% attack speed",
            "+15% attack speed", "+15% range", "+15% damage",
            "+50% SP production / kills may give SP", "+12% attack speed +7% range", "+20% damage",
            "Little moons fire at 10% power; moon-type kills have 10% to add a moon",
            "+10% damage; ice stuns longer and more often", "+10% damage; fire burns harder and longer",
            "+17% damage, range, speed; fire/ice status +15% and longer",
            "+30% damage -15% speed; each boss kill +5% damage",
            "+50% damage +20% speed; boss kills summon that boss as an ally (50% HP)"};

    // ---------- materials (Lab, rolled with Crystal) ----------
    static final int MAT_IRON = 0, MAT_COPPER = 1, MAT_QUARTZ = 2, MAT_TITANIUM = 3, MAT_COBALT = 4, MAT_GOLD = 5,
            MAT_PLATINUM = 6, MAT_URANIUM = 7, MAT_GRAPHENE = 8, MAT_DIAMOND = 9;
    static final int MAT_COUNT = 10;
    static final String[] MAT_NAME = {"Iron", "Copper", "Quartz", "Titanium", "Cobalt", "Gold", "Platinum", "Uranium", "Graphene", "Diamond"};
    static final String[] MAT_NAME_TH = {"เหล็ก", "ทองแดง", "ควอตซ์", "ไทเทเนียม", "โคบอลต์", "ทองคำ", "แพลทินัม", "ยูเรเนียม", "กราฟีน", "เพชร"};
    static final String[] MAT_SYM = {"Fe", "Cu", "Si", "Ti", "Co", "Au", "Pt", "U", "C", ""};
    static final int[] MAT_RARITY = {COMMON, COMMON, COMMON, RARE, RARE, EPIC, EPIC, LEGEND, LEGEND, MYTHIC};
    static final int[] MAT_COLOR = {0xFF9AA3B5, 0xFFE07A3C, 0xFFF6C8EA, 0xFFB8C8E0, 0xFF3F66E0, 0xFFFFC83A, 0xFFE8EEF8,
            0xFF7CFF4A, 0xFF4A505E, 0xFF9EF6FF};
    static final float[] MAT_DMG = {0.15f, 0f, 0f, 0.25f, 0f, 0.10f, 0.20f, 0.40f, 0f, 0.35f};
    static final float[] MAT_RNG = {0f, 0f, 0.15f, 0.10f, 0f, 0f, 0.20f, 0f, 0.20f, 0.35f};
    static final float[] MAT_SPA = {0f, 0.15f, 0f, 0f, 0.30f, 0f, 0.20f, 0f, 0.40f, 0.35f};
    static final float[] MAT_CHANCE = {17.33f, 17.33f, 17.34f, 14f, 14f, 6.5f, 6.5f, 3f, 3f, 1f};
    static final String[] MAT_DESC = {"+15% ดาเมจ", "+15% ความเร็วโจมตี", "+15% ระยะ", "+25% ดาเมจ +10% ระยะ", "+30% ความเร็วโจมตี",
            "+10% ดาเมจ ผลิต SP +60% ฆ่าศัตรู 25% ได้ SP", "+20% ดาเมจ ระยะ ความเร็ว", "+40% ดาเมจ ยิงโดนติดกัมมันตรังสี (ติดได้แม้ Hell Planet)",
            "+40% ความเร็วโจมตี +20% ระยะ", "+35% ทุกค่า และ 15% คริติคอล x2"};
    static final String[] MAT_DESC_EN = {"+15% damage", "+15% attack speed", "+15% range", "+25% damage +10% range", "+30% attack speed",
            "+10% damage, +60% SP production, kills 25% give SP", "+20% damage, range, speed", "+40% damage, hits irradiate (even Hell Planet)",
            "+40% attack speed +20% range", "+35% everything and 15% critical x2"};

    // ---------- enemies ----------
    static final int E_METEOR = 0, E_SHOOT = 1, E_MEGA = 2, E_EXOMOON = 3, E_ALTMOON = 4,
            E_EXOPLANET = 5, E_ALTPLANET = 6, E_HELL = 7, E_BLACKHOLE = 8;
    static final int E_COUNT = 9;
    static final String[] E_NAME = {"Meteor", "Shooting Star", "Mega Meteor", "EXO Moon", "Alternate Moon",
            "EXO Planet", "Alternate Planet", "Hell Planet", "Black Hole"};
    static final String[] E_DESC = {"อุกกาบาตธรรมดา", "เร็วมาก แต่เลือดน้อย", "อึด ตายแล้วแตกเป็น Meteor 2 ก้อน",
            "ฮีลศัตรูรอบ ๆ", "หายตัวเป็นช่วง ๆ โจมตีไม่ได้", "มีโล่ที่ฟื้นฟูได้",
            "วาร์ปไปข้างหน้าเป็นระยะ", "ไม่ติดไฟ อึดมาก", "บอส! ปิดการทำงานดวงดาวใกล้ ๆ"};
    static final float[] E_HP = {30, 18, 150, 90, 70, 240, 210, 320, 2600};
    static final float[] E_SPEED = {80, 170, 58, 78, 92, 52, 62, 50, 28};
    static final float[] E_RADIUS = {26, 20, 40, 30, 30, 42, 40, 44, 72};
    static final int[] E_BASEDMG = {1, 1, 2, 1, 1, 2, 2, 3, 10};

    // ---------- stages ----------
    static final String[] ST_NAME = {"วงโคจรแรก", "แถบดาวเคราะห์น้อย", "เนบิวลาวน", "ใจกลางหลุมดำ"};
    static final String[] ST_NAME_EN = {"First Orbit", "Asteroid Belt", "Swirl Nebula", "Black Hole Core"};
    static final int[] ST_WAVES = {15, 20, 25, 30};
    static final float[] ST_HP = {0.55f, 1.2f, 1.45f, 1.75f};
    static final int[] ST_COLOR = {0xFF5B8CFF, 0xFF33C6A6, 0xFFE05BD8, 0xFF8A5BFF};

    // Map frame is 1000 x 1200 units. Paths are x,y pairs.
    static final float[][] ST_PATH = {
            {100, 60, 100, 1100, 900, 1100, 900, 120},
            {100, 60, 900, 80, 900, 400, 100, 400, 100, 720, 900, 720, 900, 1040, 120, 1040},
            {100, 60, 100, 1100, 900, 1100, 900, 250, 300, 250, 300, 900, 700, 900, 700, 470},
            {900, 60, 100, 80, 100, 600, 900, 600, 900, 1100, 120, 1100},
    };
    static final float[][] ST_SLOTS = {
            {300, 220, 500, 220, 700, 220, 300, 400, 500, 400, 700, 400, 300, 580, 500, 580, 700, 580,
                    300, 760, 500, 760, 700, 760, 300, 940, 500, 940, 700, 940},
            {200, 240, 350, 240, 500, 240, 650, 240, 800, 240, 200, 560, 350, 560, 500, 560, 650, 560, 800, 560,
                    200, 880, 350, 880, 500, 880, 650, 880, 800, 880},
            {300, 120, 500, 120, 700, 120, 200, 400, 200, 550, 200, 700, 200, 850, 200, 1000,
                    800, 400, 800, 550, 800, 700, 800, 850, 800, 1000, 500, 450, 500, 600, 500, 750},
            {250, 220, 400, 220, 550, 220, 700, 220, 850, 220, 250, 460, 400, 460, 550, 460, 700, 460, 850, 460,
                    150, 750, 300, 750, 450, 750, 600, 750, 750, 750, 150, 960, 300, 960, 450, 960, 600, 960, 750, 960},
    };
}
