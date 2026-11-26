package com.stella.td;

/** Visual effect: particle, ring or floating text. */
final class Fx {
    static final int PART = 0, RING = 1, TEXT = 2, STARTEXT = 3, PUFF = 4;
    int kind, color;
    float x, y, vx, vy, life, maxLife, size;
    String text;
}
