package com.stella.td;

import java.util.HashSet;

/** Something that travels backwards along the path from the base: the Sun's heat wave, or a Galaxia-summoned ally boss. */
final class HeatWave {
    float d, hp, maxHp, dmgMul, speed = 260f, angle;
    int summon = -1;       // enemy type of a summoned ally, -1 for a heat wave
    boolean summonBoss;
    Planet src;
    boolean dead;
    final HashSet<Enemy> hit = new HashSet<>();
}
