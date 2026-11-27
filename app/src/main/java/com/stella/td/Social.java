package com.stella.td;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Friends, co-op rooms and gift codes over the Realtime Database.
 *   friends/<uid>/<other> = name           friendReq/<uid>/<from> = name
 *   invites/<uid>/<from> = {name, code, t} codes/<CODE> = {rewards, uses, expires}, codes/<CODE>/used/<uid>
 *   rooms/<code>/meta = {host, hostName, mode, stage, seed, state, t}
 *   rooms/<code>/members/<uid> = {name, ready, wave, leak, alive, t}   rooms/<code>/gifts/<uid>/<id> = sp
 */
final class Social {
    static final int TEAM_HP = 30, GIFT_SP = 10;
    static final int MODE_STORY = 0, MODE_RAID = 1;

    static final class Friend { String uid, name; long seen; }
    static final class Member { String uid, name; boolean ready, alive = true; int wave, leak; long t; }
    static final class Invite { String from, name, code; long t; }

    static final class Room {
        String code, host, hostName, state = "lobby";
        int mode, stage;
        long seed, t;
        final List<Member> members = new ArrayList<>();
        final Map<String, Integer> giftsForMe = new LinkedHashMap<>();
        Member other(String me) { for (Member m : members) if (!m.uid.equals(me)) return m; return null; }
        Member get(String uid) { for (Member m : members) if (m.uid.equals(uid)) return m; return null; }
    }

    final Cloud cloud;
    final List<Friend> friends = new ArrayList<>();
    final List<Friend> requests = new ArrayList<>();
    final List<Invite> invites = new ArrayList<>();
    Room room;
    String roomCode;

    Social(Cloud cloud) { this.cloud = cloud; }

    static Map<String, Object> obj(Object o) { return o instanceof Map ? Json.obj(o) : new LinkedHashMap<>(); }

    static long num(Object o) { return o instanceof Number ? ((Number) o).longValue() : 0; }

    // ---------------------------------------------------------------- friends
    void refresh(Cloud.Cb<Boolean> cb) {
        cloud.async(() -> {
            Map<String, Object> f = obj(cloud.get("friends/" + cloud.uid));
            Map<String, Object> r = obj(cloud.get("friendReq/" + cloud.uid));
            Map<String, Object> iv = obj(cloud.get("invites/" + cloud.uid));
            Map<String, Object> pres = obj(cloud.get("presence"));
            List<Friend> fl = new ArrayList<>(), rl = new ArrayList<>();
            List<Invite> il = new ArrayList<>();
            for (Map.Entry<String, Object> e : f.entrySet()) {
                Friend x = new Friend();
                x.uid = e.getKey();
                x.name = String.valueOf(e.getValue());
                Object pp = pres.get(x.uid);
                x.seen = pp instanceof Map ? num(Json.obj(pp).get("t")) : num(pp);
                fl.add(x);
            }
            for (Map.Entry<String, Object> e : r.entrySet()) {
                Friend x = new Friend();
                x.uid = e.getKey();
                x.name = String.valueOf(e.getValue());
                rl.add(x);
            }
            long now = System.currentTimeMillis();
            for (Map.Entry<String, Object> e : iv.entrySet()) {
                Map<String, Object> m = obj(e.getValue());
                Invite x = new Invite();
                x.from = e.getKey();
                x.name = Json.str(m, "name", "?");
                x.code = Json.str(m, "code", "");
                x.t = num(m.get("t"));
                if (now - x.t < 30 * 60000L) il.add(x);
            }
            synchronized (this) {
                friends.clear(); friends.addAll(fl);
                requests.clear(); requests.addAll(rl);
                invites.clear(); invites.addAll(il);
            }
            return true;
        }, cb);
    }

    void sendRequest(String toUid, String myName, Cloud.Cb<Boolean> cb) {
        cloud.async(() -> { cloud.put("friendReq/" + toUid + "/" + cloud.uid, myName); return true; }, cb);
    }

    void accept(Friend req, String myName, Cloud.Cb<Boolean> cb) {
        cloud.async(() -> {
            cloud.put("friends/" + cloud.uid + "/" + req.uid, req.name);
            cloud.put("friends/" + req.uid + "/" + cloud.uid, myName);
            cloud.delete("friendReq/" + cloud.uid + "/" + req.uid);
            return true;
        }, cb);
    }

    void decline(Friend req, Cloud.Cb<Boolean> cb) {
        cloud.async(() -> { cloud.delete("friendReq/" + cloud.uid + "/" + req.uid); return true; }, cb);
    }

    void unfriend(Friend f, Cloud.Cb<Boolean> cb) {
        cloud.async(() -> {
            cloud.delete("friends/" + cloud.uid + "/" + f.uid);
            cloud.delete("friends/" + f.uid + "/" + cloud.uid);
            return true;
        }, cb);
    }

    // ---------------------------------------------------------------- rooms
    static String newCode() {
        String abc = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder b = new StringBuilder();
        java.util.Random r = new java.util.Random();
        for (int i = 0; i < 6; i++) b.append(abc.charAt(r.nextInt(abc.length())));
        return b.toString();
    }

    void create(int mode, int stage, String myName, Cloud.Cb<Room> cb) {
        cloud.async(() -> {
            String code = newCode();
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("host", cloud.uid);
            meta.put("hostName", myName);
            meta.put("mode", mode);
            meta.put("stage", stage);
            meta.put("seed", System.nanoTime() & 0xFFFFFFFL);
            meta.put("state", "lobby");
            meta.put("t", System.currentTimeMillis());
            cloud.put("rooms/" + code + "/meta", meta);
            cloud.put("rooms/" + code + "/members/" + cloud.uid, member(myName, true, 0, 0, true));
            roomCode = code;
            return load(code);
        }, cb);
    }

    static Map<String, Object> member(String name, boolean ready, int wave, int leak, boolean alive) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("ready", ready);
        m.put("wave", wave);
        m.put("leak", leak);
        m.put("alive", alive);
        m.put("t", System.currentTimeMillis());
        return m;
    }

    /** join by code; refuses full, missing, old or started rooms */
    void join(String code, String myName, Cloud.Cb<Room> cb) {
        cloud.async(() -> {
            String c = code.trim().toUpperCase(java.util.Locale.US);
            Room r = load(c);
            if (r == null) throw new Cloud.UserError(L.t("ไม่พบห้อง " + c, "Room " + c + " not found"));
            if (!"lobby".equals(r.state)) throw new Cloud.UserError(L.t("ห้องนี้เริ่มเล่นไปแล้ว", "That room already started"));
            if (r.members.size() >= 2 && r.get(cloud.uid) == null) throw new Cloud.UserError(L.t("ห้องเต็มแล้ว (2 คน)", "The room is full (2 players)"));
            cloud.put("rooms/" + c + "/members/" + cloud.uid, member(myName, false, 0, 0, true));
            cloud.delete("invites/" + cloud.uid);
            roomCode = c;
            return load(c);
        }, cb);
    }

    Room load(String code) throws Exception {
        Object o = cloud.get("rooms/" + code);
        if (!(o instanceof Map)) return null;
        Map<String, Object> all = Json.obj(o);
        Map<String, Object> meta = obj(all.get("meta"));
        if (meta.isEmpty()) return null;
        Room r = new Room();
        r.code = code;
        r.host = Json.str(meta, "host", "");
        r.hostName = Json.str(meta, "hostName", "?");
        r.mode = (int) num(meta.get("mode"));
        r.stage = (int) num(meta.get("stage"));
        r.seed = num(meta.get("seed"));
        r.state = Json.str(meta, "state", "lobby");
        r.t = num(meta.get("t"));
        if (System.currentTimeMillis() - r.t > 6 * 3600000L) return null; // stale room
        for (Map.Entry<String, Object> e : obj(all.get("members")).entrySet()) {
            Map<String, Object> m = obj(e.getValue());
            Member x = new Member();
            x.uid = e.getKey();
            x.name = Json.str(m, "name", "?");
            x.ready = Boolean.TRUE.equals(m.get("ready"));
            x.alive = !Boolean.FALSE.equals(m.get("alive"));
            x.wave = (int) num(m.get("wave"));
            x.leak = (int) num(m.get("leak"));
            x.t = num(m.get("t"));
            r.members.add(x);
        }
        for (Map.Entry<String, Object> e : obj(obj(all.get("gifts")).get(cloud.uid)).entrySet())
            r.giftsForMe.put(e.getKey(), (int) num(e.getValue()));
        return r;
    }

    void poll(Cloud.Cb<Room> cb) {
        if (roomCode == null) return;
        String c = roomCode;
        cloud.async(() -> load(c), (r, err) -> {
            if (err == null && c.equals(roomCode)) room = r;
            if (cb != null) cb.done(r, err);
        });
    }

    void setReady(boolean ready, String myName) {
        if (roomCode == null) return;
        String c = roomCode;
        cloud.async(() -> { cloud.put("rooms/" + c + "/members/" + cloud.uid + "/ready", ready); return true; }, null);
    }

    void start(Cloud.Cb<Boolean> cb) {
        String c = roomCode;
        cloud.async(() -> {
            cloud.put("rooms/" + c + "/meta/seed", System.nanoTime() & 0xFFFFFFFL);
            cloud.put("rooms/" + c + "/meta/state", "playing");
            return true;
        }, cb);
    }

    /** in-match sync: push my progress, read the room (team leaks, gifts) */
    void push(String myName, int wave, int leak, boolean alive, Cloud.Cb<Room> cb) {
        if (roomCode == null) return;
        String c = roomCode;
        cloud.async(() -> {
            cloud.put("rooms/" + c + "/members/" + cloud.uid, member(myName, true, wave, leak, alive));
            Room r = load(c);
            if (r != null) for (String id : r.giftsForMe.keySet()) cloud.delete("rooms/" + c + "/gifts/" + cloud.uid + "/" + id);
            return r;
        }, (r, err) -> {
            if (err == null && r != null && c.equals(roomCode)) room = r;
            if (cb != null) cb.done(r, err);
        });
    }

    void gift(String toUid, int sp) {
        String c = roomCode;
        if (c == null) return;
        cloud.async(() -> { cloud.put("rooms/" + c + "/gifts/" + toUid + "/" + Long.toHexString(System.nanoTime()), sp); return true; }, null);
    }

    void backToLobby() {
        String c = roomCode;
        if (c == null) return;
        cloud.async(() -> { cloud.put("rooms/" + c + "/meta/state", "lobby"); return true; }, null);
    }

    void leave() {
        String c = roomCode, me = cloud.uid;
        Room r = room;
        roomCode = null;
        room = null;
        if (c == null) return;
        cloud.async(() -> {
            if (r != null && me.equals(r.host)) cloud.delete("rooms/" + c);
            else cloud.delete("rooms/" + c + "/members/" + me);
            return true;
        }, null);
    }

    void invite(String toUid, String myName, Cloud.Cb<Boolean> cb) {
        String c = roomCode;
        cloud.async(() -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", myName);
            m.put("code", c);
            m.put("t", System.currentTimeMillis());
            cloud.put("invites/" + toUid + "/" + cloud.uid, m);
            return true;
        }, cb);
    }

    // ---------------------------------------------------------------- gift codes
    /** rewards "kind:n,kind:n" (kinds as Profile.grant, 9 = Millite) */
    void redeem(String code, Profile pf, Cloud.Cb<String> cb) {
        cloud.async(() -> {
            String c = code.trim().toUpperCase(java.util.Locale.US).replaceAll("[^A-Z0-9]", "");
            if (c.isEmpty()) throw new Cloud.UserError(L.t("ใส่โค้ด", "Enter a code"));
            Object o = cloud.get("codes/" + c);
            if (!(o instanceof Map)) throw new Cloud.UserError(L.t("โค้ดไม่ถูกต้อง", "Invalid code"));
            Map<String, Object> m = Json.obj(o);
            long exp = num(m.get("expires"));
            if (exp > 0 && System.currentTimeMillis() > exp) throw new Cloud.UserError(L.t("โค้ดหมดอายุแล้ว", "This code has expired"));
            Map<String, Object> used = obj(m.get("used"));
            if (used.containsKey(cloud.uid)) throw new Cloud.UserError(L.t("ใช้โค้ดนี้ไปแล้ว", "You already used this code"));
            long max = num(m.get("uses"));
            if (max > 0 && used.size() >= max) throw new Cloud.UserError(L.t("โค้ดนี้ถูกใช้ครบแล้ว", "This code has run out"));
            cloud.put("codes/" + c + "/used/" + cloud.uid, System.currentTimeMillis());
            return Json.str(m, "rewards", "");
        }, (rewards, err) -> {
            if (err != null) { cb.done(null, err); return; }
            StringBuilder got = new StringBuilder();
            for (String part : rewards.split(",")) {
                String[] kv = part.trim().split(":");
                if (kv.length != 2) continue;
                try {
                    int kind = Integer.parseInt(kv[0].trim()), n = Integer.parseInt(kv[1].trim());
                    String s = kind == 9 ? millite(pf, n) : pf.grant(kind, n);
                    got.append(got.length() > 0 ? ", " : "").append(s);
                } catch (NumberFormatException ignored) { }
            }
            pf.save();
            cb.done(got.length() > 0 ? got.toString() : L.t("ไม่มีของ", "nothing"), null);
        });
    }

    static String millite(Profile pf, int n) { pf.millite += n; return n + " Millite"; }

    /** console: create or replace a code */
    void makeCode(String code, String rewards, int uses, int days, Cloud.Cb<Boolean> cb) {
        cloud.async(() -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rewards", rewards);
            m.put("uses", uses);
            m.put("expires", days > 0 ? System.currentTimeMillis() + days * 86400000L : 0);
            m.put("t", System.currentTimeMillis());
            cloud.put("codes/" + code.toUpperCase(java.util.Locale.US), m);
            return true;
        }, cb);
    }

    void deleteCode(String code, Cloud.Cb<Boolean> cb) {
        cloud.async(() -> { cloud.delete("codes/" + code.toUpperCase(java.util.Locale.US)); return true; }, cb);
    }
}
