package com.stella.td;

/** All static game data: planets, enchants, effects, enemies and stages. */
final class Defs {
    private Defs() {}

    // ---------- rarity ----------
    static final int COMMON = 0, RARE = 1, EPIC = 2, LEGEND = 3, MYTHIC = 4, GALAXY = 5, EXCLUSIVE = 6, SPECIALIST = 7;
    static final String[] RARITY_NAME = {"Common", "Rare", "Epic", "Legend", "Mythical", "Galaxy", "Exclusive", "Specialist"};
    static final int[] RARITY_COLOR = {0xFFC9D3E3, 0xFF4FA3FF, 0xFFB86BFF, 0xFFFFB627, 0xFFFF4F8B, 0xFF5EF2FF, 0xFFFF6A3D, 0xFFFFF0A0};

    // ---------- planets ----------
    static final int EARTH = 0, SATELLITE = 1, MOON = 2, SATURN = 3, JUPITER = 4, MARS = 5,
            MERCURY = 6, VENUS = 7, URANUS = 8, NEPTUNE = 9, PLUTO = 10, SUN = 11, HERO = 12,
            CYBER = 13, SMART_SAT = 14, SAVIOR_SAT = 15, NIGHTMARE = 16, GALACIA_P = 17, COMMANDER = 18, KING = 19;
    static final int P_COUNT = 20;
    static final String[] P_NAME = {"Earth", "Satellite", "Moon", "Saturn", "Jupiter", "Mars",
            "Mercury", "Venus", "Uranus", "Neptune", "Pluto", "Sun", "Hero",
            "Cyber", "Smart Satellite", "Savior Satellite", "Nightmare", "Galacia", "Commander", "King"};
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
            "ปล่อยคลื่นความร้อนจากฐาน แรงขึ้นทุกคลื่น",
            "ดาวฮีโร่! ยิงหอกเพลิงทะลุศัตรูทั้งแถว ติดไฟรุนแรง",
            "สร้างกำแพง 3 ชั้นบนทางที่ใกล้ที่สุด (วางได้เฉพาะช่องติดทาง)",
            "ผลิต SP มากที่สุดและเร็วที่สุดในเกม",
            "สโลว์ศัตรูในระยะด้วย Haunted ตัวที่ตายกลายเป็นวิญญาณช่วยดาวที่ใกล้ที่สุดโจมตี",
            "ไม่โจมตี กดสกิลใช้ SP จำนวนมาก ระเบิดตัวเองและศัตรูทั้งแมพ",
            "พายุน้ำสตันศัตรูเป็นวงกว้าง ไม่มีดาเมจ",
            "วางบนทางเดิน โคจรเป็นวงกลมและยิงมิสไซล์ใส่ศัตรูในระยะ",
            "Specialist: บัฟดาวทุกดวงในแมพ +10% ทุกอย่าง สกิล Elite Command ใช้ได้ 1 ครั้ง"};
    static final int[] P_RARITY = {COMMON, COMMON, COMMON, RARE, RARE, EPIC,
            LEGEND, LEGEND, LEGEND, LEGEND, LEGEND, MYTHIC, EXCLUSIVE,
            EXCLUSIVE, EXCLUSIVE, EXCLUSIVE, EXCLUSIVE, EXCLUSIVE, EXCLUSIVE, SPECIALIST};

    static final int K_MISSILE = 0, K_ROCK = 1, K_PRODUCE = 2, K_SWARM = 3, K_SMOKE = 4,
            K_FIREROCK = 5, K_TORNADO = 6, K_ICE = 7, K_AURA = 8, K_BUFF = 9, K_SUNWAVE = 10, K_LANCE = 11,
            K_WALL = 12, K_HAUNT = 13, K_NUKE = 14, K_STORM = 15, K_COMMANDER = 16, K_KING = 17;
    static final int[] P_KIND = {K_MISSILE, K_PRODUCE, K_ROCK, K_SWARM, K_SMOKE, K_PRODUCE,
            K_FIREROCK, K_TORNADO, K_ICE, K_AURA, K_BUFF, K_SUNWAVE, K_LANCE,
            K_WALL, K_PRODUCE, K_HAUNT, K_NUKE, K_STORM, K_COMMANDER, K_KING};
    /** damage per hit (producers: SP per cycle; Cyber: wall HP; King: knight strength) */
    static final float[] P_DMG = {14, 1, 22, 5, 4, 2, 7, 11, 16, 6, 12, 28, 70,
            140, 3, 0, 0, 0, 26, 30};
    /** seconds between attacks (producers: seconds per cycle; Cyber: wall rebuild time) */
    static final float[] P_CD = {0.9f, 10f, 1.0f, 0.2f, 0.9f, 10f, 0.18f, 1.4f, 0.9f, 0.5f, 1.1f, 7.5f, 1.15f,
            9f, 6f, 0.5f, 1f, 2.6f, 0.75f, 1f};
    static final float[] P_RANGE = {270, 0, 270, 260, 240, 0, 290, 320, 280, 250, 260, 0, 380,
            170, 0, 240, 0, 300, 210, 0};
    /** planets that may only stand next to the path (Cyber) or on the path itself (Commander) */
    static boolean nearPathOnly(int t) { return t == CYBER; }

    static boolean onPathOnly(int t) { return t == COMMANDER; }

    /** SP cost of the Nightmare self-destruct: grows with the wave */
    static int nukeCost(int wave) { return 30 + wave * 2; }

    // ---------- attack types (shown as tags; each one is a real mechanic) ----------
    static final int AT_SINGLE = 1, AT_AOE = 2, AT_PIERCE = 4, AT_BURN = 8, AT_SLOW = 16, AT_STUN = 32,
            AT_ZONE = 64, AT_PRODUCE = 128, AT_BUFF = 256, AT_WAVE = 512, AT_RAPID = 1024,
            AT_WALL = 2048, AT_HAUNT = 4096, AT_NUKE = 8192, AT_SKILL = 16384, AT_ORBIT = 32768;
    static final int AT_COUNT = 16;
    static final String[] AT_NAME_TH = {"เดี่ยว", "วงกว้าง", "ทะลุ", "เผา", "สโลว์", "สตัน", "โซน", "ผลิต SP", "บัฟ", "คลื่น", "ยิงรัว",
            "กำแพง", "สิงวิญญาณ", "ทำลายล้าง", "สกิล", "โคจร"};
    static final String[] AT_NAME_EN = {"Single", "AoE", "Pierce", "Burn", "Slow", "Stun", "Zone", "Produce", "Buff", "Wave", "Rapid",
            "Wall", "Haunted", "Nuke", "Skill", "Orbit"};
    static final String[] AT_DESC_TH = {"โจมตีศัตรูทีละตัว", "ระเบิดโดนศัตรูรอบ ๆ เป้าหมาย", "ทะลุโดนศัตรูทุกตัวที่ผ่าน",
            "ติดไฟ เสียเลือดต่อเนื่อง", "ทำให้ศัตรูเดินช้าลง", "มีโอกาสทำให้ศัตรูหยุดนิ่ง", "สร้างพื้นที่โจมตีศัตรูทุกตัวในระยะ",
            "ผลิต SP ให้ใช้ในด่าน", "เพิ่มพลังดวงดาวรอบ ๆ", "ปล่อยคลื่นวิ่งกวาดไปตามเส้นทาง", "ยิงถี่มาก",
            "สร้างกำแพงขวางทางศัตรู", "ศัตรูที่ตายกลายเป็นวิญญาณฝ่ายเรา", "ทำลายศัตรูทั้งแมพ", "มีสกิลกดใช้ในด่าน", "เคลื่อนที่เป็นวงกลม"};
    static final String[] AT_DESC_EN = {"Hits one enemy at a time", "Explodes on enemies around the target", "Passes through every enemy in its way",
            "Sets enemies on fire for damage over time", "Makes enemies move slower", "May freeze enemies in place", "Damages every enemy inside its area",
            "Produces SP during the match", "Powers up nearby planets", "Sends a wave sweeping along the path", "Attacks very fast",
            "Builds walls that block the path", "Haunted enemies come back as friendly spirits", "Wipes every enemy on the map", "Has a skill button in the match", "Moves around in a circle"};
    static final int[] AT_COLOR = {0xFFB8C4D6, 0xFFFF8A2B, 0xFFFFE27A, 0xFFFF4D3D, 0xFF7DD3FF, 0xFF9EF6FF, 0xFF6B8BFF,
            0xFFFFD23F, 0xFF7CFF8A, 0xFFFFB627, 0xFFE07AFF, 0xFF5EF2FF, 0xFFB8FFE0, 0xFFFF3B6B, 0xFFFFF0A0, 0xFF8FB4FF};
    static final int[] P_ATTACK = {
            AT_SINGLE | AT_AOE,      // Earth: missile with splash
            AT_PRODUCE,              // Satellite
            AT_SINGLE,               // Moon
            AT_SINGLE | AT_RAPID,    // Saturn
            AT_AOE | AT_BURN,        // Jupiter: smoke cloud
            AT_PRODUCE,              // Mars
            AT_RAPID | AT_BURN,      // Mercury
            AT_PIERCE | AT_BURN,     // Venus: tornado
            AT_SLOW | AT_STUN,       // Uranus
            AT_ZONE | AT_SLOW,       // Neptune
            AT_BUFF | AT_SINGLE,     // Pluto
            AT_WAVE | AT_BURN,       // Sun
            AT_PIERCE | AT_BURN | AT_AOE, // Hero: piercing flame lance that bursts at the end
            AT_WALL,                 // Cyber
            AT_PRODUCE,              // Smart Satellite
            AT_HAUNT | AT_SLOW | AT_ZONE, // Savior Satellite
            AT_NUKE | AT_SKILL,      // Nightmare
            AT_STUN | AT_AOE,        // Galacia
            AT_ORBIT | AT_SINGLE,    // Commander
            AT_BUFF | AT_SKILL       // King
    };

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
            "Heat waves from the base, stronger each time",
            "Hero planet! Fires a flaming lance that pierces the whole line and burns hard",
            "Builds 3 walls on the nearest path (only on slots next to the path)",
            "The biggest and fastest SP producer in the game",
            "Slows enemies in range with Haunted; haunted enemies that die become spirits that help the nearest planet",
            "Never attacks. Its skill spends a lot of SP to blow itself up with every enemy on the map",
            "A water storm that stuns enemies in an area, no damage",
            "Stands on the path, circles around and fires missiles at enemies in range",
            "Specialist: +10% to everything for every planet on the map. Elite Command skill once per match"};
    /** in-match SP cost to place each planet */
    static final int[] PLACE_COST = {1, 2, 2, 3, 3, 4, 5, 5, 5, 5, 5, 8, 7,
            6, 5, 6, 6, 5, 6, 8};
    /** how many of each planet may be on the map at once */
    static final int[] PLACE_LIMIT = {6, 3, 6, 4, 3, 2, 3, 2, 3, 2, 2, 1, 1,
            1, 2, 1, 1, 2, 2, 1};
    /** highest level a planet can be upgraded to during a match (outside the match the cap is Profile.MAX_LV = 10) */
    static final int[] MATCH_MAX = {6, 4, 6, 5, 5, 4, 4, 4, 4, 4, 4, 3, 5,
            4, 4, 4, 3, 4, 4, 3};
    /** planet categories (the planet page lists them one under another) */
    static final int CAT_SOLAR = 0, CAT_EXCLUSIVE = 1, CAT_SPECIALIST = 2;
    static final String[] CAT_NAME = {"Solar System", "Exclusive", "Specialist"};
    static final String[] CAT_NAME_TH = {"ระบบสุริยะ", "เอ็กซ์คลูซีฟ", "สเปเชียลลิสต์"};
    static final int[] P_CATEGORY = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, CAT_EXCLUSIVE,
            CAT_EXCLUSIVE, CAT_EXCLUSIVE, CAT_EXCLUSIVE, CAT_EXCLUSIVE, CAT_EXCLUSIVE, CAT_EXCLUSIVE, CAT_SPECIALIST};
    /** the 5th deck slot takes only Specialist planets */
    static final int SPECIAL_SLOT = 4;

    // ---------- Exclusive capsule (paid with Crystal Oil) ----------
    static final int[] CAPSULE_POOL = {GALACIA_P, SMART_SAT, COMMANDER, CYBER, SAVIOR_SAT, NIGHTMARE, KING};
    static final float[] CAPSULE_RATE = {25f, 22f, 18f, 15f, 10f, 8f, 2f};
    static final int CAPSULE_OIL = 30, CAPSULE_OIL_10 = 270;

    // ---------- equipment: moons ----------
    static final int MOON_HELL = 0, MOON_FROST = 1, MOON_STORM = 2, MOON_GOLD = 3, MOON_VOID = 4, MOON_COUNT = 5;
    static final String[] MOON_NAME = {"Hell Moon", "Frost Moon", "Storm Moon", "Gold Moon", "Void Moon"};
    static final String[] MOON_DESC = {"+10% ดาเมจไฟ และทุกการโจมตีติดไฟ 100%", "ทุกการโจมตีทำให้ศัตรูช้าลง 20% และ +10% ดาเมจใส่ศัตรูที่ช้าอยู่",
            "+12% ความเร็วโจมตี", "+20% การผลิต SP, ฆ่าศัตรูมีโอกาส 10% ได้ 1 SP", "+25% ดาเมจใส่บอสและมินิบอส"};
    static final String[] MOON_DESC_EN = {"+10% fire damage and every hit sets enemies on fire", "Every hit slows 20%; +10% damage to slowed enemies",
            "+12% attack speed", "+20% SP production; kills have 10% to give 1 SP", "+25% damage to bosses and mini bosses"};
    static final int[] MOON_COLOR = {0xFFFF4B2B, 0xFF7FE0FF, 0xFFB86BFF, 0xFFFFC83A, 0xFF8A4BFF};
    /** recolour hue for each artifact's moon sprite (Hell uses its own lava art) */
    static final float[] MOON_HUE = {0, 195, 275, 45, 265};
    /** Raid shop prices (Crystal Oil) */
    static final int SHOP_CAPSULE = 40, SHOP_HELLMOON = 350;
    /** Raid shop: item id (Items) or -10 - artifact, price in Crystal Oil */
    static final int[][] RAID_SHOP = {{-100, 40}, {Items.ART_CAPSULE, 150}, {Items.SUPPLY, 60}, {Items.NEBULA, 80}, {Items.CATALYST, 260},
            {-10 - MOON_HELL, 350}, {-10 - MOON_VOID, 300}};

    // ---------- enchants (Factory, rolled with Millite) ----------
    static final int EN_STRONG = 0, EN_REACH = 1, EN_MOVING = 2, EN_LIGHTNING = 3, EN_STORM = 4, EN_TECH = 5,
            EN_MIDAS = 6, EN_ELECTRO = 7, EN_CRYSTALRAIN = 8, EN_MOON = 9, EN_GALACIA = 10, EN_LAVA = 11,
            EN_MEGALONIA = 12, EN_GIANTTIC = 13, EN_GALAXIA = 14, EN_FROSTBITE = 15, EN_OVERCHARGE = 16, EN_NOVA = 17;
    static final int EN_COUNT = 18;
    static final String[] EN_NAME = {"Strong", "Reach", "Moving", "Lightning", "Storm", "Technology", "Midas", "Electro",
            "Crystal Rain", "Moon", "Galacia", "Lava", "Megalonia", "Gianttic", "Galaxia", "Frostbite", "Overcharge", "Nova"};
    static final int[] EN_RARITY = {COMMON, COMMON, COMMON, RARE, RARE, RARE, EPIC, EPIC, EPIC, LEGEND, LEGEND, LEGEND,
            MYTHIC, MYTHIC, GALAXY, RARE, EPIC, LEGEND};
    /** number of tiers (0 = no tiers) */
    static final int[] EN_TIERS = {3, 2, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    static final float[] EN_CHANCE = {16.74f, 16.73f, 16.73f, 8f, 8f, 8f, 3f, 3f, 3f, 1.2f, 1.2f, 1.2f, 0.45f, 0.45f, 0.1f, 8f, 3f, 1.2f};
    static final float[] STRONG_T = {0.05f, 0.07f, 0.10f}, REACH_T = {0.06f, 0.12f}, MOVING_T = {0.04f, 0.08f, 0.12f};
    static final String[] EN_DESC = {
            "+5 / 7 / 10% ดาเมจ", "+6 / 12% ระยะ", "+4 / 8 / 12% ความเร็วโจมตี",
            "+15% ความเร็วโจมตี", "+15% ระยะ", "+15% ดาเมจ",
            "ผลิต SP มากขึ้น 50% / ฆ่าศัตรูมีโอกาสได้ SP", "+12% ความเร็วโจมตี +7% ระยะ", "+20% ดาเมจ",
            "ดวงจันทร์จิ๋วช่วยยิง 10% ฆ่าศัตรูสายดวงจันทร์ 10% ได้ดวงจันทร์เพิ่ม",
            "+10% ดาเมจ น้ำแข็ง stun นานและบ่อยขึ้น", "+10% ดาเมจ ไฟแรงขึ้นและติดนานขึ้น",
            "+17% ดาเมจ ระยะ ความเร็ว ถ้าเป็นไฟ/น้ำแข็ง สถานะแรงขึ้น 15% และนานขึ้น",
            "+30% ดาเมจ -15% ความเร็ว ฆ่าบอสแต่ละตัว +5% ดาเมจ",
            "+50% ดาเมจ +20% ความเร็ว ฆ่าบอสจะเรียกบอสตัวนั้นมาสู้ให้ (เลือด 50%)",
            "+10% ดาเมจ ทุกการโจมตีทำให้ศัตรูช้าลง 15%", "ทุกการโจมตีครั้งที่ 5 แรงขึ้น x2.5", "+12% ดาเมจ +12% ระยะ ศัตรูที่ตายระเบิดใส่รอบ ๆ 30%"};
    static final String[] EN_DESC_EN = {
            "+5 / 7 / 10% damage", "+6 / 12% range", "+4 / 8 / 12% attack speed",
            "+15% attack speed", "+15% range", "+15% damage",
            "+50% SP production / kills may give SP", "+12% attack speed +7% range", "+20% damage",
            "Little moons fire at 10% power; moon-type kills have 10% to add a moon",
            "+10% damage; ice stuns longer and more often", "+10% damage; fire burns harder and longer",
            "+17% damage, range, speed; fire/ice status +15% and longer",
            "+30% damage -15% speed; each boss kill +5% damage",
            "+50% damage +20% speed; boss kills summon that boss as an ally (50% HP)",
            "+10% damage; every hit slows 15%", "Every 5th attack hits x2.5", "+12% damage +12% range; kills explode for 30% around them"};

    // ---------- materials (Lab, rolled with Crystal) ----------
    static final int MAT_IRON = 0, MAT_COPPER = 1, MAT_QUARTZ = 2, MAT_TITANIUM = 3, MAT_COBALT = 4, MAT_GOLD = 5,
            MAT_PLATINUM = 6, MAT_URANIUM = 7, MAT_GRAPHENE = 8, MAT_DIAMOND = 9, MAT_MYTHRIL = 10, MAT_OBSIDIAN = 11, MAT_STELLARIUM = 12;
    static final int MAT_COUNT = 13;
    static final String[] MAT_NAME = {"Iron", "Copper", "Quartz", "Titanium", "Cobalt", "Gold", "Platinum", "Uranium", "Graphene", "Diamond",
            "Mythril", "Obsidian", "Stellarium"};
    static final String[] MAT_NAME_TH = {"เหล็ก", "ทองแดง", "ควอตซ์", "ไทเทเนียม", "โคบอลต์", "ทองคำ", "แพลทินัม", "ยูเรเนียม", "กราฟีน", "เพชร",
            "มิธริล", "ออบซิเดียน", "สเตลลาเรียม"};
    static final String[] MAT_SYM = {"Fe", "Cu", "Si", "Ti", "Co", "Au", "Pt", "U", "C", "", "Mi", "Ob", "St"};
    static final int[] MAT_RARITY = {COMMON, COMMON, COMMON, RARE, RARE, EPIC, EPIC, LEGEND, LEGEND, MYTHIC, RARE, LEGEND, MYTHIC};
    static final int[] MAT_COLOR = {0xFF9AA3B5, 0xFFE07A3C, 0xFFF6C8EA, 0xFFB8C8E0, 0xFF3F66E0, 0xFFFFC83A, 0xFFE8EEF8,
            0xFF7CFF4A, 0xFF4A505E, 0xFF9EF6FF, 0xFF8FE8D8, 0xFF3A1E4A, 0xFFFF7AE0};
    static final float[] MAT_DMG = {0.15f, 0f, 0f, 0.25f, 0f, 0.10f, 0.20f, 0.40f, 0f, 0.35f, 0.15f, 0.30f, 0.30f};
    static final float[] MAT_RNG = {0f, 0f, 0.15f, 0.10f, 0f, 0f, 0.20f, 0f, 0.20f, 0.35f, 0f, 0.10f, 0.30f};
    static final float[] MAT_SPA = {0f, 0.15f, 0f, 0f, 0.30f, 0f, 0.20f, 0f, 0.40f, 0.35f, 0.15f, 0f, 0.30f};
    static final float[] MAT_CHANCE = {16.17f, 16.17f, 16.16f, 12f, 12f, 6f, 6f, 2.5f, 2.5f, 1f, 6f, 2.5f, 1f};
    static final String[] MAT_DESC = {"+15% ดาเมจ", "+15% ความเร็วโจมตี", "+15% ระยะ", "+25% ดาเมจ +10% ระยะ", "+30% ความเร็วโจมตี",
            "+10% ดาเมจ ผลิต SP +60% ฆ่าศัตรู 25% ได้ SP", "+20% ดาเมจ ระยะ ความเร็ว", "+40% ดาเมจ ยิงโดนติดกัมมันตรังสี (ติดได้แม้ Hell Planet)",
            "+40% ความเร็วโจมตี +20% ระยะ", "+35% ทุกค่า และ 15% คริติคอล x2",
            "+15% ดาเมจ +15% ความเร็วโจมตี", "+30% ดาเมจ +10% ระยะ ทะลุโล่ศัตรู 50%", "+30% ทุกค่า ทุกการโจมตีครั้งที่ 6 ยิงกระดอนไปอีก 2 ตัว"};
    static final String[] MAT_DESC_EN = {"+15% damage", "+15% attack speed", "+15% range", "+25% damage +10% range", "+30% attack speed",
            "+10% damage, +60% SP production, kills 25% give SP", "+20% damage, range, speed", "+40% damage, hits irradiate (even Hell Planet)",
            "+40% attack speed +20% range", "+35% everything and 15% critical x2",
            "+15% damage +15% attack speed", "+30% damage +10% range; ignores 50% of shields", "+30% everything; every 6th attack chains to 2 more enemies"};

    // ---------- enemies ----------
    static final int E_METEOR = 0, E_SHOOT = 1, E_MEGA = 2, E_EXOMOON = 3, E_ALTMOON = 4,
            E_EXOPLANET = 5, E_ALTPLANET = 6, E_HELL = 7, E_BLACKHOLE = 8,
            E_VOIDLING = 9, E_WRAITH = 10, E_TITAN = 11;
    static final int E_COUNT = 12;
    static final String[] E_NAME = {"Meteor", "Shooting Star", "Mega Meteor", "EXO Moon", "Alternate Moon",
            "EXO Planet", "Alternate Planet", "Hell Planet", "Black Hole", "Voidling", "Star Wraith", "Void Titan"};
    static final String[] E_DESC = {"อุกกาบาตธรรมดา", "เร็วมาก แต่เลือดน้อย", "อึด ตายแล้วแตกเป็น Meteor 2 ก้อน",
            "ฮีลศัตรูรอบ ๆ", "หายตัวเป็นช่วง ๆ โจมตีไม่ได้", "มีโล่ที่ฟื้นฟูได้",
            "วาร์ปไปข้างหน้าเป็นระยะ", "ไม่ติดไฟ อึดมาก", "บอส! ปิดการทำงานดวงดาวใกล้ ๆ",
            "Raid: ตายแล้วแตกเป็น Voidling เล็ก 2 ตัว", "Raid: เร็ว ฟื้นเลือดเอง ไม่ติดสโลว์", "บอส Raid! โล่หนา เรียกลูกน้องเป็นระยะ"};
    static final float[] E_HP = {30, 18, 150, 90, 70, 240, 210, 320, 2600, 120, 160, 9000};
    static final float[] E_SPEED = {80, 170, 58, 78, 92, 52, 62, 50, 28, 70, 120, 24};
    static final float[] E_RADIUS = {26, 20, 40, 30, 30, 42, 40, 44, 72, 32, 28, 86};
    static final int[] E_BASEDMG = {1, 1, 2, 1, 1, 2, 2, 3, 10, 1, 2, 15};

    // ---------- story: 3 galaxies x 15 stages ----------
    static final int GALAXIES = 3, PER_GALAXY = 15, ST_COUNT = GALAXIES * PER_GALAXY;
    static final String[] GAL_NAME = {"Milky Way", "Andromeda", "Void Galaxy"};
    static final String[] GAL_NAME_TH = {"ทางช้างเผือก", "แอนดรอเมดา", "กาแล็กซีมืด"};
    static final int[] GAL_COLOR = {0xFF5B8CFF, 0xFFE05BD8, 0xFFFF5B5B};
    private static final String[] SEC_TH = {"วงโคจรแรก", "แถบดาวเคราะห์น้อย", "เนบิวลาวน", "ทุ่งดาวหาง", "ประตูดวงดาว",
            "ทะเลฝุ่นจักรวาล", "ป้อมดาวแคระ", "วงแหวนน้ำแข็ง", "พายุสุริยะ", "สุสานดวงดาว",
            "เขาวงกตแสง", "ดาวนิวตรอน", "ขอบฟ้าเหตุการณ์", "ใจกลางหลุมดำ", "บัลลังก์ราชันย์"};
    private static final String[] SEC_EN = {"First Orbit", "Asteroid Belt", "Swirl Nebula", "Comet Field", "Star Gate",
            "Cosmic Dust Sea", "Dwarf Fortress", "Ice Rings", "Solar Storm", "Star Graveyard",
            "Light Maze", "Neutron Star", "Event Horizon", "Black Hole Core", "Throne of the Void"};
    static final String[] DIFF_TH = {"ง่าย", "ปกติ", "ยาก", "ยากมาก", "โหด", "นรก"};
    static final String[] DIFF_EN = {"Easy", "Normal", "Hard", "Very Hard", "Extreme", "Nightmare"};
    static final int[] DIFF_COLOR = {0xFF4DFF8A, 0xFF7DD3FF, 0xFFFFD23F, 0xFFFF8A2B, 0xFFFF4B5B, 0xFFB43BFF};

    /** Raid is one more "stage" after the story: 100 waves, raid enemies, pays Crystal Oil */
    static final int RAID = ST_COUNT, RAID_WAVES = 100;
    static final String[] ST_NAME = new String[ST_COUNT + 1], ST_NAME_EN = new String[ST_COUNT + 1];
    static final int[] ST_WAVES = new int[ST_COUNT + 1], ST_COLOR = new int[ST_COUNT + 1], ST_DIFF = new int[ST_COUNT + 1];
    static final float[] ST_HP = new float[ST_COUNT + 1], ST_SPEED = new float[ST_COUNT + 1];
    static final float[][] ST_PATH = new float[ST_COUNT + 1][], ST_SLOTS = new float[ST_COUNT + 1][];

    // ---- evolution: a few planets can evolve once they reach max level
    static boolean canEvolve(int t) { return t == EARTH || t == MARS || t == NEPTUNE || t == MOON || t == SATELLITE; }
    static final int EVO_STARS = 3000, EVO_MILLITE = 0;
    /** Evo Catalysts needed: more for rarer planets */
    static int evoCatalysts(int t) { return 1 + Math.min(3, P_RARITY[t]); }
    static String evoName(int t) {
        switch (t) {
            case EARTH: return "Gaia Earth";
            case MARS: return "Inferno Mars";
            case NEPTUNE: return "Abyss Neptune";
            case MOON: return "Eclipse Moon";
            case SATELLITE: return "Satellite Mk-II";
            default: return P_NAME[t];
        }
    }
    static String evoDesc(int t) {
        if (P_KIND[t] == K_PRODUCE)
            return L.t("ร่างวิวัฒน์: ผลิต SP +60% และเร็วขึ้น 15%", "Evolved: +60% SP production and 15% faster");
        String base = L.t("ร่างวิวัฒน์: ดาเมจ +60% ระยะ +15% ยิงเร็วขึ้น 15% • ทุกนัดที่ 4: ", "Evolved: +60% damage, +15% range, 15% faster • every 4th shot: ");
        switch (t) {
            case EARTH: return base + L.t("แผ่นดินไหว สตันศัตรูรอบเป้า", "a quake that stuns nearby enemies");
            case MARS: return base + L.t("ระเบิดเพลิง เผาศัตรูรอบเป้า", "a fire burst that burns nearby enemies");
            case NEPTUNE: return base + L.t("คลื่นยักษ์ ทำให้ศัตรูรอบเป้าช้าลง 50%", "a tidal wave that slows nearby enemies 50%");
            case MOON: return base + L.t("สุริยุปราคา คริติคอล x3", "an eclipse critical hit x3");
            case SATELLITE: return base + L.t("ยิงเลเซอร์คู่ ดาเมจ x2", "a twin laser for x2 damage");
            default: return base;
        }
    }
    /** hue (degrees) the evolved sprite is painted toward */
    static float evoHue(int t) {
        switch (t) {
            case MARS: return 12;
            case NEPTUNE: return 192;
            case MOON: return 275;
            case SATELLITE: return 45;
            default: return 125;
        }
    }
    static final String EVO_DESC_TH = "ร่างวิวัฒน์", EVO_DESC_EN = "Evolved form";

    // ---- planet effects (Admin Abuse): stronger planet, multiplied trade value
    static final int EFF_GOLDEN = 0, EFF_SILVER = 1, EFF_RAINBOW = 2, EFF_SHINY = 3, EFF_BLAZE = 4, EFF_COUNT = 5;
    static final String[] EFF_NAME = {"Golden", "Silver", "Rainbow", "Shiny", "Blaze"};
    static final int[] EFF_COLOR = {0xFFFFD23F, 0xFFE0E8F0, 0xFFFF7AE0, 0xFF9EF6FF, 0xFFFF6A2B};
    static final float[] EFF_DMG = {0.15f, 0.08f, 0.25f, 0.20f, 0.20f};
    static final float[] EFF_SPD = {0.10f, 0.08f, 0.20f, 0.05f, 0.10f};
    static final float[] EFF_RNG = {0f, 0.08f, 0.10f, 0.10f, 0f};
    /** value multiplier for trading / selling */
    static final float[] EFF_VALUE = {2f, 1.5f, 4f, 2.5f, 3f};
    static final String[] EFF_DESC = {"+15% ดาเมจ +10% ความเร็ว • ราคา x2", "+8% ทุกค่า • ราคา x1.5", "+25% ดาเมจ +20% ความเร็ว +10% ระยะ • ราคา x4",
            "+20% ดาเมจ +10% ระยะ • 10% คริติคอล x2 • ราคา x2.5", "+20% ดาเมจ +10% ความเร็ว • ทุกการโจมตีติดไฟ • ราคา x3"};
    static final String[] EFF_DESC_EN = {"+15% damage +10% speed • value x2", "+8% everything • value x1.5", "+25% damage +20% speed +10% range • value x4",
            "+20% damage +10% range • 10% crit x2 • value x2.5", "+20% damage +10% speed • every hit burns • value x3"};
    /** Admin Abuse effect stages: map and difficulty mods per effect */
    static int effStage(int e) { return 22 + e * 4; }
    static int[] effMods(int e) {
        switch (e) {
            case EFF_GOLDEN: return new int[]{1};
            case EFF_SILVER: return new int[]{0};
            case EFF_RAINBOW: return new int[]{0, 1};
            case EFF_SHINY: return new int[]{3, 1};
            default: return new int[]{0, 3};
        }
    }
    static final int EFF_WAVES = 25, BOSS_WAVES = 10;

    /** Gold shop: {grant kind (Profile.grant, 9 Millite), amount, price in Gold} */
    static final int[][] GOLD_SHOP = {{8, 1, 10}, {3, 1, 12}, {7, 1, 30}, {6, 1, 45}, {5, 1, 12}, {0, 2000, 10}, {1, 10, 20}, {9, 5, 15}};

    // ---- daily / weekly challenges
    static final String[] CH_MOD = {"Swift", "Armored", "Short Sight", "Swarm"};
    static final String[] CH_MOD_TH = {"ศัตรูเร็ว +35%", "ศัตรูเลือด +60%", "ระยะดาว -20%", "บอสทุก 5 เวฟ"};
    static final String[] CH_MOD_EN = {"Enemies +35% speed", "Enemies +60% HP", "Planet range -20%", "Bosses every 5 waves"};
    static final int DAILY_WAVES = 20, WEEKLY_WAVES = 30;
    static int dailyMod(int day) { return Math.floorMod(day, CH_MOD.length); }
    static int dailyStage(int day) { return 2 + Math.floorMod(day * 7, 16); }
    static int[] weeklyMods(int week) { int a = Math.floorMod(week, CH_MOD.length); return new int[]{a, (a + 1 + Math.floorMod(week / 4, 3)) % CH_MOD.length}; }
    static int weeklyStage(int week) { return 15 + Math.floorMod(week * 5, 20); }

    /** indices ordered by rarity (common first), keeping table order inside a rarity */
    static int[] byRarity(int[] rarity) {
        Integer[] idx = new Integer[rarity.length];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (x, y) -> rarity[x] != rarity[y] ? rarity[x] - rarity[y] : x - y);
        int[] out = new int[idx.length];
        for (int i = 0; i < idx.length; i++) out[i] = idx[i];
        return out;
    }

    static final int[] EN_ORDER = byRarity(EN_RARITY), MAT_ORDER = byRarity(MAT_RARITY);

    static int galaxyOf(int st) { return Math.min(GALAXIES - 1, st / PER_GALAXY); }

    // Map frame is 1000 x 1200 units. Paths are x,y pairs; the first point is the black hole, the last the base.
    static final float[][] PATHS = {
            {100, 60, 100, 1100, 900, 1100, 900, 120},
            {100, 60, 900, 80, 900, 400, 100, 400, 100, 720, 900, 720, 900, 1040, 120, 1040},
            {100, 60, 100, 1100, 900, 1100, 900, 250, 300, 250, 300, 900, 700, 900, 700, 470},
            {900, 60, 100, 80, 100, 600, 900, 600, 900, 1100, 120, 1100},
            {150, 60, 150, 1080, 450, 1080, 450, 180, 750, 180, 750, 1080, 900, 1080},
            {80, 70, 920, 70, 920, 1120, 80, 1120, 80, 330, 700, 330, 700, 880, 320, 880, 320, 590, 500, 590},
            {500, 60, 500, 300, 120, 300, 120, 1100, 880, 1100, 880, 300, 620, 300, 620, 700},
            {100, 60, 900, 60, 900, 290, 100, 290, 100, 520, 900, 520, 900, 750, 100, 750, 100, 980, 900, 980},
    };
    static final float[][] PATH_SLOTS = {
            {300, 220, 500, 220, 700, 220, 300, 400, 500, 400, 700, 400, 300, 580, 500, 580, 700, 580,
                    300, 760, 500, 760, 700, 760, 300, 940, 500, 940, 700, 940},
            {200, 240, 350, 240, 500, 240, 650, 240, 800, 240, 200, 560, 350, 560, 500, 560, 650, 560, 800, 560,
                    200, 880, 350, 880, 500, 880, 650, 880, 800, 880},
            {300, 120, 500, 120, 700, 120, 200, 400, 200, 550, 200, 700, 200, 850, 200, 1000,
                    800, 400, 800, 550, 800, 700, 800, 850, 800, 1000, 500, 450, 500, 600, 500, 750},
            {250, 220, 400, 220, 550, 220, 700, 220, 850, 220, 250, 460, 400, 460, 550, 460, 700, 460, 850, 460,
                    150, 750, 300, 750, 450, 750, 600, 750, 750, 750, 150, 960, 300, 960, 450, 960, 600, 960, 750, 960},
            null, null, null, null
    };

    static {
        for (int s = 0; s < ST_COUNT; s++) {
            int g = s / PER_GALAXY, i = s % PER_GALAXY;
            ST_NAME[s] = SEC_TH[i];
            ST_NAME_EN[s] = SEC_EN[i];
            ST_WAVES[s] = 12 + (i * 2) / 3 + g * 2;
            // v3.8: tougher — enemy HP grows ~7% per stage and gets a bigger push in each new galaxy
            ST_HP[s] = 0.55f * (float) Math.pow(1.068, s) * (1f + 0.3f * g);
            ST_SPEED[s] = 1f + g * 0.09f + i * 0.005f;
            ST_DIFF[s] = Math.min(5, (int) ((g * 15 + i) / 7.5f));
            ST_COLOR[s] = GAL_COLOR[g];
            int pth = (i + g * 3) % PATHS.length;
            ST_PATH[s] = PATHS[pth];
            ST_SLOTS[s] = PATH_SLOTS[pth] != null ? PATH_SLOTS[pth] : autoSlots(PATHS[pth]);
        }
        ST_NAME[RAID] = "เรด: ประตูมิติ";
        ST_NAME_EN[RAID] = "Raid: Void Gate";
        ST_WAVES[RAID] = RAID_WAVES;
        ST_HP[RAID] = 0.65f;
        ST_SPEED[RAID] = 1.05f;
        ST_DIFF[RAID] = 5;
        ST_COLOR[RAID] = 0xFFFF3B6B;
        ST_PATH[RAID] = PATHS[5];
        ST_SLOTS[RAID] = autoSlots(PATHS[5]);
    }

    /** slots: a fine grid, greedily keeping points clear of the path and of each other */
    static float[] autoSlots(float[] path) {
        java.util.ArrayList<float[]> out = new java.util.ArrayList<>();
        for (int y = 150; y <= 1080; y += 24) {
            for (int x = 150; x <= 850; x += 24) {
                float best = 1e9f;
                for (int k = 0; k + 3 < path.length; k += 2) best = Math.min(best, segDist(x, y, path[k], path[k + 1], path[k + 2], path[k + 3]));
                if (best < 95) continue;
                boolean ok = true;
                for (float[] q : out) if (Math.abs(q[0] - x) < 128 && Math.abs(q[1] - y) < 128) { ok = false; break; }
                if (ok && out.size() < 20) out.add(new float[]{x, y});
            }
        }
        float[] a = new float[out.size() * 2];
        for (int k = 0; k < out.size(); k++) { a[k * 2] = out.get(k)[0]; a[k * 2 + 1] = out.get(k)[1]; }
        return a;
    }

    static float segDist(float px, float py, float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay, l = dx * dx + dy * dy;
        float t = l == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / l));
        float qx = ax + dx * t - px, qy = ay + dy * t - py;
        return (float) Math.sqrt(qx * qx + qy * qy);
    }
}
