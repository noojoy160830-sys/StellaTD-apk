package com.stella.td;

/** A meteor / planet / black hole travelling along the path. */
final class Enemy {
    int type;
    float hp, maxHp, shield, maxShield, d, speed, radius;
    float x, y, angle;
    float slow, slowT, stunT, burnDps, burnT, radDps, radT, hitFlash;
    float abilT, noDmgT;
    int baseDmg;
    boolean dead, phased, boss, mini;
    /** Savior's Haunted mark; split children don't split again; stopped at a wall */
    float hauntT;
    boolean child, blocked;
}
