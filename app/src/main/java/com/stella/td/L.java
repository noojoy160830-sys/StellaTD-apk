package com.stella.td;

/** Tiny localisation helper: every UI string is written as a Thai / English pair. */
final class L {
    private L() {}

    static boolean en;

    static String t(String th, String en) {
        return L.en ? en : th;
    }
}
