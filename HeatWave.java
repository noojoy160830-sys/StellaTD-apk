package com.stella.td;

import java.util.HashSet;

/** Sun's heat wave that travels backwards along the path from the base. */
final class HeatWave {
    float d, hp, maxHp, dmgMul;
    Planet src;
    boolean dead;
    final HashSet<Enemy> hit = new HashSet<>();
}
