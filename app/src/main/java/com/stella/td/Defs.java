package com.stella.td;

/** All static game data: planets, enchants, effects, enemies and stages. */
final class Defs {
    private Defs() {}

    // ---------- rarity ----------
    static final int COMMON = 0, RARE = 1, EPIC = 2, LEGEND = 3, MYTHIC = 4;
    static final String[] RARITY_NAME = {"Common", "Rare", "Epic", "Legend", "Mythical"};
    static final int[] RARITY_COLOR = {0xFFC9D3E3, 0xFF4FA3FF, 0xFFB86BFF, 0xFFFFB627, 0xFFFF4F8B};

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
            "บัฟดวงดาวรอบ ๆ +10% ดาเมจ",
            "ปล่อยคลื่นความร้อนจากฐาน แรงขึ้นทุกคลื่น"};
    static final int[] P_RARITY = {COMMON, COMMON, COMMON, RARE, RARE, EPIC,
            LEGEND, LEGEND, LEGEND, LEGEND, LEGEND, MYTHIC};

    static final int K_MISSILE = 0, K_ROCK = 1, K_PRODUCE = 2, K_SWARM = 3, K_SMOKE = 4,
            K_FIREROCK = 5, K_TORNADO = 6, K_ICE = 7, K_AURA = 8, K_BUFF = 9, K_SUNWAVE = 10;
    static final int[] P_KIND = {K_MISSILE, K_PRODUCE, K_ROCK, K_SWARM, K_SMOKE, K_PRODUCE,
            K_FIREROCK, K_TORNADO, K_ICE, K_AURA, K_BUFF, K_SUNWAVE};
    /** damage per hit (producers: stars per cycle) */
    static final float[] P_DMG = {14, 1, 22, 5, 4, 2, 6, 9, 11, 4, 8, 50};
    /** seconds between attacks (producers: seconds per cycle) */
    static final float[] P_CD = {0.9f, 10f, 1.0f, 0.2f, 0.9f, 11f, 0.16f, 1.5f, 1.0f, 0.5f, 1.2f, 6f};
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
            "Buffs nearby planets +10% damage",
            "Heat waves from the base, stronger each time"};
    /** in-match SP cost to place each planet */
    static final int[] PLACE_COST = {1, 2, 2, 3, 3, 4, 5, 5, 5, 5, 5, 8};
    static final int MAX_LEVEL = 5;

    // ---------- enchants ----------
    static final int EN_DMG = 0, EN_RNG = 1, EN_CD = 2, EN_GOLD = 3, EN_BLITZ = 4, EN_FROZE = 5,
            EN_BURN = 6, EN_MOON = 7, EN_GIGA = 8, EN_MEGE = 9;
    static final String[] EN_NAME = {"Damage", "Range", "Cooldown", "Gold", "Blitz", "Froze",
            "Burn", "Moon", "Gigantic", "Megelinia"};
    static final String[] EN_DESC = {"+10% ดาเมจ ต่อระดับ", "+8% ระยะ ต่อระดับ", "-8% คูลดาวน์ ต่อระดับ",
            "ฆ่าศัตรูมีโอกาสได้ดาว / ผลิตเร็วขึ้น", "+ความเร็วโจมตี 35%", "ทำให้ช้า + ดาเมจน้ำแข็ง",
            "ทำให้ติดไฟ", "มีดวงจันทร์จิ๋วช่วยยิง 15%", "+ดาเมจ +ระยะ +ความเร็ว", "+ดาเมจ +ระยะ +ความเร็ว (สุดยอด)"};
    static final String[] EN_DESC_EN = {"+10% damage per tier", "+8% range per tier", "-8% cooldown per tier",
            "Kills may drop SP / faster production", "+35% attack speed", "Slows + bonus ice damage",
            "Sets enemies on fire", "A little moon fires at 15% power", "+damage +range +speed", "+damage +range +speed (ultimate)"};
    static final int[] EN_RARITY = {COMMON, COMMON, COMMON, RARE, RARE, RARE, RARE, LEGEND, LEGEND, MYTHIC};

    // ---------- effects ----------
    static final int EF_GOLD = 0, EF_DIAMOND = 1, EF_ELITE = 2, EF_BIGGER = 3;
    static final String[] EF_NAME = {"Gold", "Diamond", "Elite", "Bigger"};
    static final String[] EF_DESC = {"+15% ดาเมจ", "+30% ดาเมจ ระยะ ความเร็ว", "+20% ดาเมจ ระยะ ความเร็ว", "+10% ดาเมจ ระยะ ความเร็ว"};
    static final String[] EF_DESC_EN = {"+15% damage", "+30% damage, range, speed", "+20% damage, range, speed", "+10% damage, range, speed"};
    static final int[] EF_COLOR = {0xFFFFD23F, 0xFF7DF3FF, 0xFFFF4D5E, 0xFF9CFF7A};
    static final float[] EF_DMG = {1.15f, 1.30f, 1.20f, 1.10f};
    static final float[] EF_RNG = {1.0f, 1.30f, 1.20f, 1.10f};
    static final float[] EF_CD = {1.0f, 0.77f, 0.83f, 0.91f};

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
