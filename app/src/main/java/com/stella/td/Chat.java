package com.stella.td;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * World chat and private messages.
 *   chat/world/<key> = {uid, name, msg, t}
 *   chat/dm/<uidA_uidB>/<key> = {uid, name, msg, t}   (uids sorted)
 *   dmNew/<to>/<from> = time of the newest unread message
 */
final class Chat {
    static final int MAX_LEN = 160, KEEP = 40;

    static final class Msg { String key, uid, name, msg; long t; }

    final Cloud cloud;
    final List<Msg> world = new ArrayList<>();
    final Map<String, List<Msg>> dms = new LinkedHashMap<>();
    /** who sent me unread private messages */
    final Map<String, Long> newFrom = new LinkedHashMap<>();
    long lastSend;

    Chat(Cloud cloud) { this.cloud = cloud; }

    static String pair(String a, String b) { return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a; }

    static String key() { return String.format(Locale.US, "c%013d%03d", System.currentTimeMillis(), (int) (Math.random() * 1000)); }

    synchronized boolean unread() { return !newFrom.isEmpty(); }

    static List<Msg> parse(Object o) {
        List<Msg> l = new ArrayList<>();
        if (!(o instanceof Map)) return l;
        for (Map.Entry<String, Object> e : Json.obj(o).entrySet()) {
            Map<String, Object> m = Social.obj(e.getValue());
            Msg x = new Msg();
            x.key = e.getKey();
            x.uid = Json.str(m, "uid", "");
            x.name = Json.str(m, "name", "?");
            x.msg = Json.str(m, "msg", "");
            x.t = Social.num(m.get("t"));
            l.add(x);
        }
        l.sort((a, b) -> a.key.compareTo(b.key));
        return l;
    }

    void loadWorld(Cloud.Cb<Boolean> cb) {
        cloud.async(() -> {
            List<Msg> l = parse(cloud.getLast("chat/world", KEEP));
            synchronized (this) { world.clear(); world.addAll(l); }
            return true;
        }, cb);
    }

    void loadDm(String other, Cloud.Cb<Boolean> cb) {
        final String me = cloud.uid;
        cloud.async(() -> {
            List<Msg> l = parse(cloud.getLast("chat/dm/" + pair(me, other), KEEP));
            cloud.delete("dmNew/" + me + "/" + other);
            synchronized (this) { dms.put(other, l); newFrom.remove(other); }
            return true;
        }, cb);
    }

    void loadNew(Cloud.Cb<Boolean> cb) {
        cloud.async(() -> {
            Map<String, Object> m = Social.obj(cloud.get("dmNew/" + cloud.uid));
            synchronized (this) {
                newFrom.clear();
                for (Map.Entry<String, Object> e : m.entrySet()) newFrom.put(e.getKey(), Social.num(e.getValue()));
            }
            return true;
        }, cb);
    }

    static String clean(String s) {
        s = s.replaceAll("[\\p{Cntrl}]", " ").trim();
        return s.length() > MAX_LEN ? s.substring(0, MAX_LEN) : s;
    }

    /** to = null for world chat, else the friend's uid */
    void send(String to, String myName, String text, Cloud.Cb<Boolean> cb) {
        String msg = clean(text);
        if (msg.isEmpty()) { cb.done(null, L.t("พิมพ์ข้อความก่อน", "Type a message")); return; }
        long now = System.currentTimeMillis();
        if (now - lastSend < 2000) { cb.done(null, L.t("ส่งเร็วเกินไป รอสักครู่", "Slow down a little")); return; }
        lastSend = now;
        final String me = cloud.uid;
        cloud.async(() -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uid", me);
            m.put("name", myName);
            m.put("msg", msg);
            m.put("t", System.currentTimeMillis());
            if (to == null) cloud.put("chat/world/" + key(), m);
            else {
                cloud.put("chat/dm/" + pair(me, to) + "/" + key(), m);
                cloud.put("dmNew/" + to + "/" + me, System.currentTimeMillis());
            }
            return true;
        }, cb);
    }
}
