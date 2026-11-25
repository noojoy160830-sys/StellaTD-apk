package com.stella.td;

import java.util.HashSet;

/** A projectile fired by a planet. */
final class Shot {
    int kind;
    float x, y, vx, vy, speed, dmg, life, size, spin;
    float burnDps, burnDur, slowAmt, slowDur, stunCh, stunDur;
    Enemy target;
    Planet src;
    boolean dead, mini;
    HashSet<Enemy> hit;
}
