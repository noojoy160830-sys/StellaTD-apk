package com.stella.td;

/** A planet (tower) placed on a slot. */
final class Planet {
    int type, slot, level = 1;
    int enchant = -1, tier = 0, effect = -1;
    float x, y;
    float cdT, prodT, orbitA, disabledT, buff, anim, flash;
    int spent, kills;
}
