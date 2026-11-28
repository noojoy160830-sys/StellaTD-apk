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
            cb.done(grantAll(pf, rewards), null);
        });
    }

    /** give "kind:n,kind:n" rewards; returns what was given */
    static String grantAll(Profile pf, String rewards) {
        StringBuilder got = new StringBuilder();
        for (String part : rewards.split(",")) {
            String[] kv = part.trim().split(":");
            if (kv.length != 2) continue;
            try {
                int kind = Integer.parseInt(kv[0].trim()), n = Integer.parseInt(kv[1].trim());
                if (n <= 0 || n > 1000000) continue;
                String s = kind == 9 ? millite(pf, n) : pf.grant(kind, n);
                got.append(got.length() > 0 ? ", " : "").append(s);
            } catch (NumberFormatException ignored) { }
        }
        pf.save();
        return got.length() > 0 ? got.toString() : L.t("ไม่มีของ", "nothing");
    }

    /** human text of "kind:n,..." without giving it */
    static String rewardsText(String rewards) {
        String[] nm = {"Star", "Crystal", "Crystal Oil", "Raid Capsule", Items.NAME[0], Items.NAME[1], Items.NAME[2], Items.NAME[3], Items.NAME[4],
                "Millite", "Gold", "Admin Capsule", Items.NAME[6], Items.NAME[7], Items.NAME[8], Items.NAME[9], Items.NAME[10]};
        StringBuilder b = new StringBuilder();
        for (String part : rewards.split(",")) {
            String[] kv = part.trim().split(":");
            if (kv.length != 2) continue;
            try {
                int k = Integer.parseInt(kv[0].trim());
                if (k >= 0 && k < nm.length) b.append(b.length() > 0 ? " • " : "").append(kv[1].trim()).append(' ').append(nm[k]);
            } catch (NumberFormatException ignored) { }
        }
        return b.toString();
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
    // ---------------------------------------------------------------- player id
    /** short public player id (8 digits) derived from the account uid */
    static String pid(String uid) {
        if (uid == null) return "--------";
        long h = 1125899906842597L;
        for (int i = 0; i < uid.length(); i++) h = 31 * h + uid.charAt(i);
        return String.valueOf(Math.floorMod(h, 90000000L) + 10000000L);
    }

    // ---------------------------------------------------------------- Plaza: market + mail
    //   market/<id> = {seller, name, thing, price, t, buyer?}
    //   mail/<uid>/<id> = {from, name, thing, msg, t}
    static final int MAX_LISTINGS = 5;
    static final float FEE = 0.05f;

    static final class Listing { String id, seller, name, thing, buyer; int price; long t; }
    static final class Mail { String id, from, name, thing, msg; long t; }
    static final class Trade {
        String id, from, fromName, to, toName, state;
        final List<String> give = new ArrayList<>(), want = new ArrayList<>();
        long t;
    }

    final List<Listing> market = new ArrayList<>();
    final List<Trade> tradesIn = new ArrayList<>(), tradesOut = new ArrayList<>();

    void loadMarket(Cloud.Cb<Boolean> cb) {
        cloud.async(() -> {
            Map<String, Object> m = obj(cloud.get("market"));
            List<Listing> l = new ArrayList<>();
            for (Map.Entry<String, Object> e : m.entrySet()) {
                Map<String, Object> x = obj(e.getValue());
                Listing it = new Listing();
                it.id = e.getKey();
                it.seller = Json.str(x, "seller", "");
                it.name = Json.str(x, "name", "?");
                it.thing = Json.str(x, "thing", "");
                it.buyer = Json.str(x, "buyer", null);
                it.price = (int) num(x.get("price"));
                it.t = num(x.get("t"));
                if (!it.thing.isEmpty()) l.add(it);
            }
            l.sort((a, b) -> Long.compare(b.t, a.t));
            synchronized (this) { market.clear(); market.addAll(l); }
            return true;
        }, cb);
    }

    int myListings() {
        int n = 0;
        synchronized (this) { for (Listing l : market) if (cloud.uid.equals(l.seller) && l.buyer == null) n++; }
        return n;
    }

    /** list a thing for Gold (the thing is taken from the profile right away and held by the market) */
    void list(Profile pf, String thing, int price, String myName, Cloud.Cb<Boolean> cb) {
        if (price <= 0 || price > 1000000) { cb.done(null, L.t("ราคาไม่ถูกต้อง", "Invalid price")); return; }
        if (myListings() >= MAX_LISTINGS) { cb.done(null, L.t("ลงขายได้สูงสุด 5 ชิ้น", "You can list up to 5 things")); return; }
        if (!pf.giveAway(thing)) { cb.done(null, L.t("ไม่มีของชิ้นนี้", "You don't have that")); return; }
        cloud.async(() -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("seller", cloud.uid);
            m.put("name", myName);
            m.put("thing", thing);
            m.put("price", price);
            m.put("t", System.currentTimeMillis());
            cloud.put("market/M" + Long.toString(System.currentTimeMillis(), 36) + newCode(), m);
            return true;
        }, (ok, err) -> {
            if (err != null) pf.receive(thing); // give it back
            cb.done(ok, err);
        });
    }

    /** buy a listing: the first buyer to claim it wins; the seller is paid through mail */
    void buy(Profile pf, Listing l, Cloud.Cb<String> cb) {
        if (cloud.uid.equals(l.seller)) { cb.done(null, L.t("ซื้อของตัวเองไม่ได้", "That's your own listing")); return; }
        if (pf.gold < l.price) { cb.done(null, L.t("Gold ไม่พอ", "Not enough Gold")); return; }
        pf.gold -= l.price;
        pf.save();
        cloud.async(() -> {
            Map<String, Object> cur = Json.obj(cloud.get("market/" + l.id));
            if (cur == null || cur.get("buyer") != null) throw new Cloud.UserError(L.t("มีคนซื้อไปแล้ว", "Someone already bought it"));
            cloud.put("market/" + l.id + "/buyer", cloud.uid);
            if (!cloud.uid.equals(Json.str(Json.obj(cloud.get("market/" + l.id)), "buyer", "")))
                throw new Cloud.UserError(L.t("มีคนซื้อไปแล้ว", "Someone already bought it"));
            int pay = Math.max(1, Math.round(l.price * (1f - FEE)));
            sendMail(l.seller, "g:" + pay, "market sale: " + l.thing);
            cloud.delete("market/" + l.id);
            return Json.str(cur, "thing", l.thing);
        }, (thing, err) -> {
            if (err != null) { pf.gold += l.price; pf.save(); cb.done(null, err); return; }
            cb.done(pf.receive(thing), null);
        });
    }

    /** take back an unsold listing */
    void unlist(Profile pf, Listing l, Cloud.Cb<String> cb) {
        cloud.async(() -> {
            Map<String, Object> cur = Json.obj(cloud.get("market/" + l.id));
            if (cur == null || cur.get("buyer") != null) throw new Cloud.UserError(L.t("ขายไปแล้ว", "Already sold"));
            cloud.put("market/" + l.id + "/buyer", cloud.uid); // lock it so nobody can buy it now
            cloud.delete("market/" + l.id);
            return Json.str(cur, "thing", l.thing);
        }, (thing, err) -> {
            if (err != null) { cb.done(null, err); return; }
            cb.done(pf.receive(thing), null);
        });
    }

    void sendMail(String to, String thing, String msg) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", cloud.uid);
        m.put("name", cloud.name == null ? "?" : cloud.name);
        m.put("thing", thing);
        m.put("msg", msg);
        m.put("t", System.currentTimeMillis());
        cloud.put("mail/" + to + "/" + "m" + Long.toString(System.currentTimeMillis(), 36) + newCode(), m);
    }

    /** collect everything in my mailbox (market payments, trade deliveries, returned offers) */
    void collectMail(Profile pf, Cloud.Cb<List<String>> cb) {
        cloud.async(() -> {
            Map<String, Object> m = obj(cloud.get("mail/" + cloud.uid));
            List<Mail> l = new ArrayList<>();
            for (Map.Entry<String, Object> e : m.entrySet()) {
                Map<String, Object> x = obj(e.getValue());
                Mail it = new Mail();
                it.id = e.getKey();
                it.thing = Json.str(x, "thing", "");
                it.name = Json.str(x, "name", "?");
                it.msg = Json.str(x, "msg", "");
                cloud.delete("mail/" + cloud.uid + "/" + it.id);
                l.add(it);
            }
            return l;
        }, (l, err) -> {
            if (err != null) { cb.done(null, err); return; }
            List<String> got = new ArrayList<>();
            for (Mail it : l) {
                String s = pf.receive(it.thing);
                if (!s.isEmpty()) got.add(s + " (" + it.name + ")");
            }
            cb.done(got, null);
        });
    }

    // ---------------------------------------------------------------- friend trades
    //   tradeIn/<to>/<id> = {from, fromName, to, toName, give:"a|b", want:"c|d", t, state?}
    //   tradeOut/<from>/<id> = to
    static String join(List<String> l) { return String.join("|", l); }

    static void split(String s, List<String> out) {
        out.clear();
        if (s != null) for (String x : s.split("\\|")) if (!x.isEmpty()) out.add(x);
    }

    void loadTrades(Cloud.Cb<Boolean> cb) {
        cloud.async(() -> {
            List<Trade> in = new ArrayList<>(), out = new ArrayList<>();
            for (Map.Entry<String, Object> e : obj(cloud.get("tradeIn/" + cloud.uid)).entrySet()) {
                Trade t = trade(e.getKey(), obj(e.getValue()));
                if (t.state == null) in.add(t);
            }
            for (Map.Entry<String, Object> e : obj(cloud.get("tradeOut/" + cloud.uid)).entrySet()) {
                Object o;
                // a closed offer is gone, and reading a missing node is refused: both mean "closed"
                try { o = cloud.get("tradeIn/" + e.getValue() + "/" + e.getKey()); } catch (Exception gone) { o = null; }
                if (!(o instanceof Map)) { cloud.delete("tradeOut/" + cloud.uid + "/" + e.getKey()); continue; }
                out.add(trade(e.getKey(), Json.obj(o)));
            }
            synchronized (this) { tradesIn.clear(); tradesIn.addAll(in); tradesOut.clear(); tradesOut.addAll(out); }
            return true;
        }, cb);
    }

    static Trade trade(String id, Map<String, Object> m) {
        Trade t = new Trade();
        t.id = id;
        t.from = Json.str(m, "from", "");
        t.fromName = Json.str(m, "fromName", "?");
        t.to = Json.str(m, "to", "");
        t.toName = Json.str(m, "toName", "?");
        t.state = Json.str(m, "state", null);
        t.t = num(m.get("t"));
        split(Json.str(m, "give", ""), t.give);
        split(Json.str(m, "want", ""), t.want);
        return t;
    }

    /** offer a trade to a friend: what I give is held until they answer */
    void offer(Profile pf, Friend to, List<String> give, List<String> want, String myName, Cloud.Cb<Boolean> cb) {
        if (give.isEmpty() && want.isEmpty()) { cb.done(null, L.t("เลือกของก่อน", "Pick something first")); return; }
        List<String> taken = new ArrayList<>();
        for (String th : give) {
            if (!pf.giveAway(th)) {
                for (String b : taken) pf.receive(b);
                cb.done(null, L.t("ของไม่พอ: ", "You don't have: ") + pf.describe(th));
                return;
            }
            taken.add(th);
        }
        cloud.async(() -> {
            String id = "T" + Long.toString(System.currentTimeMillis(), 36) + newCode();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("from", cloud.uid);
            m.put("fromName", myName);
            m.put("to", to.uid);
            m.put("toName", to.name);
            m.put("give", join(give));
            m.put("want", join(want));
            m.put("t", System.currentTimeMillis());
            cloud.put("tradeIn/" + to.uid + "/" + id, m);
            cloud.put("tradeOut/" + cloud.uid + "/" + id, to.uid);
            return true;
        }, (ok, err) -> {
            if (err != null) for (String b : taken) pf.receive(b);
            cb.done(ok, err);
        });
    }

    /** accept an incoming trade: I pay what they want, they get it by mail, I get what they offered */
    void acceptTrade(Profile pf, Trade t, Cloud.Cb<String> cb) {
        List<String> taken = new ArrayList<>();
        // a wanted planet is sent as my real copy (level, copies, effect)
        for (int i = 0; i < t.want.size(); i++) {
            String th = t.want.get(i);
            if (th.startsWith("p:")) {
                try { int pt = Integer.parseInt(th.split(":")[1]); if (pf.owned[pt]) t.want.set(i, pf.planetThing(pt)); } catch (RuntimeException ignored) { }
            }
        }
        for (String th : t.want) {
            if (!pf.giveAway(th)) {
                for (String b : taken) pf.receive(b);
                cb.done(null, L.t("ของไม่พอ: ", "You don't have: ") + pf.describe(th));
                return;
            }
            taken.add(th);
        }
        cloud.async(() -> {
            String path = "tradeIn/" + cloud.uid + "/" + t.id;
            Map<String, Object> cur = Json.obj(cloud.get(path));
            if (cur == null || cur.get("state") != null) throw new Cloud.UserError(L.t("ข้อเสนอนี้ถูกยกเลิกแล้ว", "This offer was cancelled"));
            cloud.put(path + "/state", "done:" + cloud.uid);
            if (!("done:" + cloud.uid).equals(Json.str(Json.obj(cloud.get(path)), "state", "")))
                throw new Cloud.UserError(L.t("ข้อเสนอนี้ถูกยกเลิกแล้ว", "This offer was cancelled"));
            for (String th : t.want) sendMail(t.from, th, "trade with " + cloud.name);
            cloud.delete(path);
            return true;
        }, (ok, err) -> {
            if (err != null) { for (String b : taken) pf.receive(b); cb.done(null, err); return; }
            StringBuilder s = new StringBuilder();
            for (String th : t.give) { String x = pf.receive(th); if (!x.isEmpty()) s.append(s.length() > 0 ? ", " : "").append(x); }
            cb.done(s.length() > 0 ? s.toString() : L.t("เทรดเสร็จ", "Trade done"), null);
        });
    }

    /** decline an incoming trade (their things go back by mail) or cancel my own offer */
    void closeTrade(Profile pf, Trade t, Cloud.Cb<Boolean> cb) {
        final boolean mine = cloud.uid.equals(t.from);
        cloud.async(() -> {
            String path = "tradeIn/" + t.to + "/" + t.id;
            Map<String, Object> cur = Json.obj(cloud.get(path));
            if (cur == null || cur.get("state") != null) throw new Cloud.UserError(L.t("ข้อเสนอนี้ปิดไปแล้ว", "This offer is already closed"));
            String st = (mine ? "cancel:" : "decline:") + cloud.uid;
            cloud.put(path + "/state", st);
            if (!st.equals(Json.str(Json.obj(cloud.get(path)), "state", "")))
                throw new Cloud.UserError(L.t("ข้อเสนอนี้ปิดไปแล้ว", "This offer is already closed"));
            if (!mine) for (String th : t.give) sendMail(t.from, th, "trade declined");
            cloud.delete(path);
            if (mine) cloud.delete("tradeOut/" + cloud.uid + "/" + t.id);
            return true;
        }, (ok, err) -> {
            if (err == null && mine) for (String th : t.give) pf.receive(th);
            cb.done(ok, err);
        });
    }
}
