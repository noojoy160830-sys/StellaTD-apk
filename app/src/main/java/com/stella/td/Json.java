package com.stella.td;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON reader/writer (objects, arrays, strings, numbers, booleans, null). */
final class Json {
    private Json() {}

    // ------------------------------------------------------------------ write
    static String write(Object o) {
        StringBuilder b = new StringBuilder();
        write(o, b);
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(Object o, StringBuilder b) {
        if (o == null) b.append("null");
        else if (o instanceof String) quote((String) o, b);
        else if (o instanceof Boolean) b.append(o.toString());
        else if (o instanceof Integer || o instanceof Long) b.append(o.toString());
        else if (o instanceof Number) {
            double d = ((Number) o).doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < 1e15) b.append((long) d);
            else b.append(d);
        } else if (o instanceof Map) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
                if (!first) b.append(',');
                first = false;
                quote(e.getKey(), b);
                b.append(':');
                write(e.getValue(), b);
            }
            b.append('}');
        } else if (o instanceof List) {
            b.append('[');
            boolean first = true;
            for (Object v : (List<Object>) o) {
                if (!first) b.append(',');
                first = false;
                write(v, b);
            }
            b.append(']');
        } else quote(o.toString(), b);
    }

    private static void quote(String s, StringBuilder b) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
    }

    // ------------------------------------------------------------------ read
    static Object parse(String s) {
        if (s == null) return null;
        int[] pos = {0};
        Object v = value(s, pos);
        return v;
    }

    private static void ws(String s, int[] p) {
        while (p[0] < s.length() && Character.isWhitespace(s.charAt(p[0]))) p[0]++;
    }

    private static Object value(String s, int[] p) {
        ws(s, p);
        if (p[0] >= s.length()) return null;
        char c = s.charAt(p[0]);
        if (c == '{') {
            Map<String, Object> m = new LinkedHashMap<>();
            p[0]++;
            ws(s, p);
            if (s.charAt(p[0]) == '}') { p[0]++; return m; }
            while (true) {
                ws(s, p);
                String k = string(s, p);
                ws(s, p);
                p[0]++; // ':'
                m.put(k, value(s, p));
                ws(s, p);
                char d = s.charAt(p[0]++);
                if (d == '}') return m;
            }
        }
        if (c == '[') {
            List<Object> l = new ArrayList<>();
            p[0]++;
            ws(s, p);
            if (s.charAt(p[0]) == ']') { p[0]++; return l; }
            while (true) {
                l.add(value(s, p));
                ws(s, p);
                char d = s.charAt(p[0]++);
                if (d == ']') return l;
            }
        }
        if (c == '"') return string(s, p);
        if (s.startsWith("true", p[0])) { p[0] += 4; return Boolean.TRUE; }
        if (s.startsWith("false", p[0])) { p[0] += 5; return Boolean.FALSE; }
        if (s.startsWith("null", p[0])) { p[0] += 4; return null; }
        int st = p[0];
        while (p[0] < s.length() && "+-0123456789.eE".indexOf(s.charAt(p[0])) >= 0) p[0]++;
        return Double.parseDouble(s.substring(st, p[0]));
    }

    private static String string(String s, int[] p) {
        StringBuilder b = new StringBuilder();
        p[0]++; // opening quote
        while (p[0] < s.length()) {
            char c = s.charAt(p[0]++);
            if (c == '"') break;
            if (c == '\\') {
                char e = s.charAt(p[0]++);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 'r': b.append('\r'); break;
                    case 't': b.append('\t'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u': b.append((char) Integer.parseInt(s.substring(p[0], p[0] + 4), 16)); p[0] += 4; break;
                    default: b.append(e);
                }
            } else b.append(c);
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ helpers
    @SuppressWarnings("unchecked")
    static Map<String, Object> obj(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    static String str(Map<String, Object> m, String k, String def) {
        Object v = m == null ? null : m.get(k);
        return v == null ? def : v.toString();
    }

    static int num(Map<String, Object> m, String k, int def) {
        Object v = m == null ? null : m.get(k);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) try { return Integer.parseInt((String) v); } catch (NumberFormatException ignored) { }
        return def;
    }

    static boolean bool(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        return v instanceof Boolean && (Boolean) v;
    }
}
