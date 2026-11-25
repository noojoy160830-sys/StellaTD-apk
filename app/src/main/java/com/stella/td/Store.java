package com.stella.td;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

/** Where a Profile keeps its values: the phone (SharedPreferences) or memory (a profile fetched from the cloud). */
interface Store {
    int getInt(String k, int def);

    boolean getBoolean(String k, boolean def);

    String getString(String k, String def);

    /** replace everything when clear is true, otherwise merge */
    void write(Map<String, Object> values, boolean clear);

    final class Prefs implements Store {
        final SharedPreferences sp;

        Prefs(SharedPreferences sp) { this.sp = sp; }

        public int getInt(String k, int def) { return sp.getInt(k, def); }

        public boolean getBoolean(String k, boolean def) { return sp.getBoolean(k, def); }

        public String getString(String k, String def) { return sp.getString(k, def); }

        public void write(Map<String, Object> values, boolean clear) {
            SharedPreferences.Editor ed = sp.edit();
            if (clear) ed.clear();
            for (Map.Entry<String, Object> e : values.entrySet()) {
                Object v = e.getValue();
                if (v instanceof Boolean) ed.putBoolean(e.getKey(), (Boolean) v);
                else if (v instanceof Number) ed.putInt(e.getKey(), ((Number) v).intValue());
                else if (v != null) ed.putString(e.getKey(), v.toString());
            }
            ed.apply();
        }
    }

    final class Mem implements Store {
        final Map<String, Object> m = new HashMap<>();

        public int getInt(String k, int def) {
            Object v = m.get(k);
            return v instanceof Number ? ((Number) v).intValue() : def;
        }

        public boolean getBoolean(String k, boolean def) {
            Object v = m.get(k);
            return v instanceof Boolean ? (Boolean) v : def;
        }

        public String getString(String k, String def) {
            Object v = m.get(k);
            return v == null ? def : v.toString();
        }

        public void write(Map<String, Object> values, boolean clear) {
            if (clear) m.clear();
            m.putAll(values);
        }
    }
}
