package com.stella.td;

import java.util.Random;

/** Daily missions: three a day picked from this pool, each with its own reward. */
final class Missions {
    private Missions() {}

    static final int KILL = 0, WIN = 1, PLAY = 2, BOSS = 3, PLACE = 4, UPGRADE = 5, PULL = 6, RAIDWAVE = 7, STAGE = 8;
    static final int COUNT = 9;
    static final int PER_DAY = 3;

    static final int[] TARGET = {150, 2, 3, 2, 25, 2, 5, 15, 1};
    static final String[] NAME = {"ล้มศัตรู %d ตัว", "ชนะ %d เกม", "เล่น %d เกม", "ล้มบอส %d ตัว", "วางดาว %d ครั้ง",
            "อัปเกรดดาว %d ครั้ง", "สุ่มแบนเนอร์ %d ครั้ง", "Raid ให้ถึงเวฟ %d", "ผ่านด่าน Story ใหม่ %d ด่าน"};
    static final String[] NAME_EN = {"Defeat %d enemies", "Win %d games", "Play %d games", "Defeat %d bosses", "Place planets %d times",
            "Upgrade planets %d times", "Pull the banner %d times", "Reach wave %d in Raid", "Clear %d new Story stage"};

    // reward: 0 Star, 1 Crystal, 2 Crystal Oil
    static final int[] REWARD_KIND = {0, 1, 0, 1, 0, 0, 1, 2, 1};
    static final int[] REWARD = {400, 5, 500, 5, 300, 400, 5, 20, 8};

    /** all-three-done bonus */
    static final int BONUS_CRYSTAL = 10, BONUS_CAPSULE = 1;

    static String name(int type) {
        return String.format(L.en ? NAME_EN[type] : NAME[type], TARGET[type]);
    }

    /** the day's three missions; raid missions only once Raid is open */
    static int[] pick(int day, boolean raid) {
        Random r = new Random(day * 7919L + 17);
        int[] out = new int[PER_DAY];
        boolean[] used = new boolean[COUNT];
        if (!raid) used[RAIDWAVE] = true;
        for (int i = 0; i < PER_DAY; i++) {
            int t;
            do t = r.nextInt(COUNT); while (used[t]);
            used[t] = true;
            out[i] = t;
        }
        return out;
    }

    // ---- 7-day login calendar: kind (0 Star, 1 Crystal, 2 Oil, 3 Raid Capsule), amount
    static final int[][] LOGIN = {{0, 300}, {1, 3}, {0, 600}, {2, 15}, {1, 5}, {0, 1000}, {3, 1}};
    static final int LOGIN_DAY7_CRYSTAL = 10;
}
