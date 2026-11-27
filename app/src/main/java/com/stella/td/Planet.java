package com.stella.td;

/** A planet (tower) placed on a slot. */
final class Planet {
    int type, slot, level = 1, plv = 1;
    int enchant = -1, tier = 0, material = -1;
    float x, y;
    float cdT, prodT, prodAcc, orbitA, disabledT, buff, bonusDmg, anim, flash;
    int spent, kills, moons;
    /** Commander: the path point it circles around */
    float ax, ay;
    /** Cyber: where its walls go on the path */
    float wallD = -1;
    /** Savior: friendly spirits circling this planet */
    int spirits;
    /** King aura is on this planet / Hell Moon equipped */
    boolean king, hellMoon, evo;
    int art = -1, overShots, chainShots;
    float rangeMul = 1f;
    int evoShots;
    /** skin style shown on the map (-1 = none) */
    int skin = -1;
}
