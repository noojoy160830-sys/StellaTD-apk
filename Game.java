package com.stella.td;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

/** Pure game simulation for one stage. Coordinates are map units (1000 x 1200). */
final class Game {
    final Random rnd = new Random();
    final int stage, maxWave;
    final float[] px, py, cum;
    final float total;
    final float[] slotX, slotY;
    final Planet[] slots;

    final ArrayList<Enemy> enemies = new ArrayList<>();
    final ArrayList<Enemy> pending = new ArrayList<>();
    final ArrayList<Shot> shots = new ArrayList<>();
    final ArrayList<HeatWave> heat = new ArrayList<>();
    final ArrayList<Fx> fx = new ArrayList<>();
    final ArrayList<Integer> queue = new ArrayList<>();

    int stars = 5, baseHp = 20, maxBaseHp = 20, wave = 0, gachaCost = Defs.GACHA_BASE, sunCount = 0;
    boolean inWave, won, lost;
    float prepT = 12f, spawnT, spawnGap = 0.7f, time, shake;
    String banner;
    float bannerT;
    int bannerColor = 0xFFFFFFFF;
    final float[] tmp = new float[2];

    Game(int stage) {
        this.stage = stage;
        this.maxWave = Defs.ST_WAVES[stage];
        float[] p = Defs.ST_PATH[stage];
        int n = p.length / 2;
        px = new float[n];
        py = new float[n];
        cum = new float[n];
        float t = 0;
        for (int i = 0; i < n; i++) {
            px[i] = p[i * 2];
            py[i] = p[i * 2 + 1];
            if (i > 0) t += dist(px[i - 1], py[i - 1], px[i], py[i]);
            cum[i] = t;
        }
        total = t;
        float[] s = Defs.ST_SLOTS[stage];
        slotX = new float[s.length / 2];
        slotY = new float[s.length / 2];
        for (int i = 0; i < slotX.length; i++) {
            slotX[i] = s[i * 2];
            slotY[i] = s[i * 2 + 1];
        }
        slots = new Planet[slotX.length];
        showBanner("วางดวงดาวเพื่อป้องกันฐาน!", 0xFFFFD23F);
    }

    static float dist(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1, dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    void pointAt(float d, float[] out) {
        if (d <= 0) { out[0] = px[0]; out[1] = py[0]; return; }
        for (int i = 1; i < px.length; i++) {
            if (d <= cum[i]) {
                float seg = cum[i] - cum[i - 1];
                float f = seg <= 0 ? 0 : (d - cum[i - 1]) / seg;
                out[0] = px[i - 1] + (px[i] - px[i - 1]) * f;
                out[1] = py[i - 1] + (py[i] - py[i - 1]) * f;
                return;
            }
        }
        out[0] = px[px.length - 1];
        out[1] = py[py.length - 1];
    }

    void showBanner(String s, int color) {
        banner = s;
        bannerColor = color;
        bannerT = 2.2f;
    }

    // ------------------------------------------------------------------ stats
    float enchDmg(Planet p) {
        switch (p.enchant) {
            case Defs.EN_DMG: return 1f + 0.10f * p.tier;
            case Defs.EN_GIGA: return 1.4f;
            case Defs.EN_MEGE: return 1.8f;
            default: return 1f;
        }
    }

    float enchRng(Planet p) {
        switch (p.enchant) {
            case Defs.EN_RNG: return 1f + 0.08f * p.tier;
            case Defs.EN_GIGA: return 1.2f;
            case Defs.EN_MEGE: return 1.35f;
            default: return 1f;
        }
    }

    float enchCd(Planet p) {
        switch (p.enchant) {
            case Defs.EN_CD: return 1f - 0.08f * p.tier;
            case Defs.EN_BLITZ: return 0.65f;
            case Defs.EN_GIGA: return 0.8f;
            case Defs.EN_MEGE: return 0.65f;
            default: return 1f;
        }
    }

    boolean isProducer(Planet p) {
        return Defs.P_KIND[p.type] == Defs.K_PRODUCE;
    }

    float dmgOf(Planet p) {
        float d = Defs.P_DMG[p.type] * (1f + 0.3f * (p.level - 1)) * enchDmg(p) * (1f + p.buff);
        if (p.effect >= 0) d *= Defs.EF_DMG[p.effect];
        return d;
    }

    float rangeOf(Planet p) {
        float r = Defs.P_RANGE[p.type] * enchRng(p);
        if (p.effect >= 0) r *= Defs.EF_RNG[p.effect];
        return r;
    }

    float cdOf(Planet p) {
        float c = Defs.P_CD[p.type];
        if (isProducer(p)) {
            c /= (1f + 0.2f * (p.level - 1));
            if (p.enchant == Defs.EN_GOLD) c *= 0.7f;
            return c;
        }
        c *= enchCd(p);
        if (p.effect >= 0) c *= Defs.EF_CD[p.effect];
        return c;
    }

    int produceAmount(Planet p) {
        return (int) Defs.P_DMG[p.type] + (p.level >= 4 ? 1 : 0);
    }

    // ------------------------------------------------------------------ building
    int upgradeCost(Planet p) {
        return p.level * 3;
    }

    String place(int type, int slot, int cost) {
        if (slots[slot] != null) return "ช่องนี้มีดาวแล้ว";
        if (stars < cost) return "ดาวไม่พอ";
        stars -= cost;
        Planet p = new Planet();
        p.type = type;
        p.slot = slot;
        p.x = slotX[slot];
        p.y = slotY[slot];
        p.spent = cost;
        p.cdT = 0.3f;
        rollEnchant(p);
        rollEffect(p);
        slots[slot] = p;
        ring(p.x, p.y, Defs.RARITY_COLOR[Defs.P_RARITY[type]], 80);
        for (int i = 0; i < 14; i++) particle(p.x, p.y, Defs.RARITY_COLOR[Defs.P_RARITY[type]], 220);
        return null;
    }

    String gacha(int slot) {
        int cost = gachaCost;
        if (slots[slot] != null) return "ช่องนี้มีดาวแล้ว";
        if (stars < cost) return "ดาวไม่พอ";
        float r = rnd.nextFloat();
        int type;
        if (r < 0.03f) type = Defs.SUN;
        else if (r < 0.23f) type = Defs.MERCURY + rnd.nextInt(5);
        else if (r < 0.45f) type = Defs.MARS;
        else type = rnd.nextBoolean() ? Defs.SATURN : Defs.JUPITER;
        String err = place(type, slot, cost);
        if (err == null) {
            gachaCost = Math.min(gachaCost + 1, Defs.GACHA_MAX);
            Planet p = slots[slot];
            String s = "ได้ " + Defs.P_NAME[type] + " (" + Defs.RARITY_NAME[Defs.P_RARITY[type]] + ")";
            showBanner(s, Defs.RARITY_COLOR[Defs.P_RARITY[type]]);
            if (p.effect >= 0) text(p.x, p.y - 80, Defs.EF_NAME[p.effect] + "!", Defs.EF_COLOR[p.effect]);
        }
        return err;
    }

    void rollEnchant(Planet p) {
        float r = rnd.nextFloat();
        if (r < 0.01f) p.enchant = Defs.EN_MEGE;
        else if (r < 0.08f) p.enchant = rnd.nextBoolean() ? Defs.EN_MOON : Defs.EN_GIGA;
        else if (r < 0.30f) p.enchant = Defs.EN_GOLD + rnd.nextInt(4);
        else {
            p.enchant = rnd.nextInt(3);
            float t = rnd.nextFloat();
            p.tier = t < 0.6f ? 1 : (t < 0.9f ? 2 : 3);
        }
    }

    void rollEffect(Planet p) {
        float r = rnd.nextFloat();
        if (r < 0.015f) p.effect = Defs.EF_DIAMOND;
        else if (r < 0.05f) p.effect = Defs.EF_ELITE;
        else if (r < 0.10f) p.effect = Defs.EF_BIGGER;
        else if (r < 0.20f) p.effect = Defs.EF_GOLD;
        else p.effect = -1;
    }

    String upgrade(Planet p) {
        if (p.level >= Defs.MAX_LEVEL) return "เลเวลสูงสุดแล้ว";
        int c = upgradeCost(p);
        if (stars < c) return "ดาวไม่พอ";
        stars -= c;
        p.spent += c;
        p.level++;
        ring(p.x, p.y, 0xFFFFD23F, 90);
        text(p.x, p.y - 70, "Lv " + p.level, 0xFFFFD23F);
        return null;
    }

    void sell(Planet p) {
        int back = Math.max(1, p.spent / 2);
        stars += back;
        slots[p.slot] = null;
        starText(p.x, p.y - 40, back);
        for (int i = 0; i < 10; i++) particle(p.x, p.y, 0xFFB8C4D6, 160);
    }

    // ------------------------------------------------------------------ waves
    void startWave() {
        wave++;
        inWave = true;
        queue.clear();
        int w = wave + stage * 2;
        int n = 4 + (int) (wave * 1.3f);
        for (int i = 0; i < n; i++) queue.add(pickEnemy(w));
        boolean boss = wave % 10 == 0 || wave == maxWave;
        if (boss) {
            queue.add(Defs.E_BLACKHOLE);
            showBanner("บอส: หลุมดำมาแล้ว!", 0xFFB86BFF);
        } else {
            showBanner("เวฟ " + wave, 0xFFFFFFFF);
        }
        spawnGap = Math.max(0.32f, 0.75f - wave * 0.015f);
        spawnT = 0.2f;
    }

    int pickEnemy(int w) {
        float[] weight = new float[8];
        weight[Defs.E_METEOR] = 5;
        if (w >= 2) weight[Defs.E_SHOOT] = 3;
        if (w >= 4) weight[Defs.E_MEGA] = 2;
        if (w >= 6) weight[Defs.E_EXOMOON] = 1.6f;
        if (w >= 7) weight[Defs.E_ALTMOON] = 1.8f;
        if (w >= 9) weight[Defs.E_EXOPLANET] = 1.5f;
        if (w >= 11) weight[Defs.E_ALTPLANET] = 1.5f;
        if (w >= 13) weight[Defs.E_HELL] = 1.4f;
        float sum = 0;
        for (float f : weight) sum += f;
        float r = rnd.nextFloat() * sum;
        for (int i = 0; i < weight.length; i++) {
            r -= weight[i];
            if (r <= 0) return i;
        }
        return Defs.E_METEOR;
    }

    float hpScale() {
        float w = wave - 1;
        return (1f + 0.2f * w + 0.008f * w * w) * Defs.ST_HP[stage];
    }

    Enemy makeEnemy(int type) {
        Enemy e = new Enemy();
        e.type = type;
        if (type == Defs.E_BLACKHOLE) {
            e.boss = true;
            e.maxHp = Defs.E_HP[type] * (1f + 0.18f * Math.max(0, wave - 10)) * Defs.ST_HP[stage]
                    * (wave >= 10 ? 1f : 0.6f);
        } else {
            e.maxHp = Defs.E_HP[type] * hpScale();
        }
        e.hp = e.maxHp;
        if (type == Defs.E_EXOPLANET) {
            e.maxShield = e.maxHp * 0.35f;
            e.shield = e.maxShield;
        }
        e.speed = Defs.E_SPEED[type] * (1f + Math.min(0.25f, wave * 0.006f));
        e.radius = Defs.E_RADIUS[type];
        e.baseDmg = Defs.E_BASEDMG[type];
        e.abilT = rnd.nextFloat() * 2f;
        e.angle = rnd.nextFloat() * 360f;
        return e;
    }

    void spawn(int type) {
        Enemy e = makeEnemy(type);
        pointAt(0, tmp);
        e.x = tmp[0];
        e.y = tmp[1];
        enemies.add(e);
        if (e.boss) shake = 0.5f;
    }

    void waveCleared() {
        inWave = false;
        int g = 4 + wave / 3;
        stars += g;
        if (wave >= maxWave) {
            won = true;
            return;
        }
        showBanner("เคลียร์เวฟ " + wave + "  +" + g + " ดาว", 0xFF9CFF7A);
        prepT = 5f;
    }

    void skipPrep() {
        if (!inWave) prepT = 0;
    }

    // ------------------------------------------------------------------ update
    void update(float dt) {
        time += dt;
        if (bannerT > 0) bannerT -= dt;
        if (shake > 0) shake -= dt;
        updateFx(dt);
        if (won || lost) return;

        if (!inWave) {
            prepT -= dt;
            if (prepT <= 0) startWave();
        } else if (!queue.isEmpty()) {
            spawnT -= dt;
            if (spawnT <= 0) {
                spawn(queue.remove(0));
                spawnT = spawnGap;
            }
        } else if (enemies.isEmpty() && pending.isEmpty()) {
            waveCleared();
        }

        computeBuffs();
        updatePlanets(dt);
        updateEnemies(dt);
        updateShots(dt);
        updateHeat(dt);
        if (!pending.isEmpty()) {
            enemies.addAll(pending);
            pending.clear();
        }
        if (baseHp <= 0 && !lost) {
            baseHp = 0;
            lost = true;
            shake = 0.8f;
        }
    }

    void computeBuffs() {
        for (Planet p : slots) {
            if (p == null) continue;
            p.buff = 0;
            for (Planet q : slots) {
                if (q == null || q == p || q.type != Defs.PLUTO || q.disabledT > 0) continue;
                if (dist(p.x, p.y, q.x, q.y) <= 240) p.buff += 0.10f;
            }
        }
    }

    Enemy findTarget(float x, float y, float range) {
        Enemy best = null;
        for (Enemy e : enemies) {
            if (e.dead || e.phased) continue;
            if (dist(x, y, e.x, e.y) <= range + e.radius && (best == null || e.d > best.d)) best = e;
        }
        return best;
    }

    void updatePlanets(float dt) {
        for (Planet p : slots) {
            if (p == null) continue;
            p.anim += dt;
            if (p.flash > 0) p.flash -= dt;
            p.orbitA += dt * 2.4f;
            if (p.disabledT > 0) {
                p.disabledT -= dt;
                continue;
            }
            int kind = Defs.P_KIND[p.type];
            float cd = cdOf(p);
            if (kind == Defs.K_PRODUCE) {
                p.prodT += dt;
                if (p.prodT >= cd) {
                    p.prodT = 0;
                    int a = produceAmount(p);
                    stars += a;
                    starText(p.x, p.y - 50, a);
                    p.flash = 0.3f;
                }
                continue;
            }
            p.cdT -= dt;
            if (p.cdT > 0) continue;
            if (kind == Defs.K_AURA) {
                p.cdT = cd;
                float r = rangeOf(p), dmg = dmgOf(p);
                boolean any = false;
                for (Enemy e : enemies) {
                    if (e.dead || dist(p.x, p.y, e.x, e.y) > r + e.radius) continue;
                    any = true;
                    applySlow(e, 0.35f, 0.7f);
                    damage(e, dmg, p);
                    applyEnchantOnHit(e, dmg, p);
                }
                if (any) p.flash = 0.2f;
            } else if (kind == Defs.K_SUNWAVE) {
                if (!inWave || enemies.isEmpty()) { p.cdT = 0.2f; continue; }
                p.cdT = cd;
                HeatWave h = new HeatWave();
                h.d = total;
                h.maxHp = dmgOf(p) * 20f * (1f + 0.2f * sunCount);
                h.hp = h.maxHp;
                h.src = p;
                sunCount++;
                heat.add(h);
                p.flash = 0.4f;
                ring(px[px.length - 1], py[py.length - 1], 0xFFFFB627, 120);
            } else {
                Enemy t = findTarget(p.x, p.y, rangeOf(p));
                if (t == null) { p.cdT = 0; continue; }
                p.cdT = cd;
                fire(p, t, p.x, p.y, 1f, false);
                if (p.enchant == Defs.EN_MOON) {
                    float mx = p.x + (float) Math.cos(p.orbitA) * 62f;
                    float my = p.y + (float) Math.sin(p.orbitA) * 62f;
                    fire(p, t, mx, my, 0.15f, true);
                }
                p.flash = 0.12f;
            }
        }
    }

    void fire(Planet p, Enemy t, float x, float y, float mul, boolean mini) {
        int kind = Defs.P_KIND[p.type];
        Shot s = new Shot();
        s.src = p;
        s.target = t;
        s.x = x;
        s.y = y;
        s.dmg = dmgOf(p) * mul;
        s.mini = mini;
        s.life = 3f;
        s.size = 1f;
        if (mini) kind = Defs.K_ROCK;
        switch (kind) {
            case Defs.K_MISSILE: s.speed = 560; break;
            case Defs.K_ROCK: s.speed = 620; break;
            case Defs.K_BUFF: kind = Defs.K_ROCK; s.speed = 600; s.size = 0.8f; break;
            case Defs.K_SWARM:
                s.speed = 720; s.size = 0.6f;
                s.x += rnd.nextFloat() * 30 - 15; s.y += rnd.nextFloat() * 30 - 15;
                break;
            case Defs.K_SMOKE: s.speed = 380; s.burnDps = s.dmg * 5f; s.burnDur = 3f; break;
            case Defs.K_FIREROCK: s.speed = 720; s.size = 0.7f; s.burnDps = s.dmg * 1.2f; s.burnDur = 2f; break;
            case Defs.K_ICE: s.speed = 560; s.slowAmt = 0.5f; s.slowDur = 2f; s.stunCh = 0.25f; s.stunDur = 0.8f; break;
            case Defs.K_TORNADO: {
                s.speed = 360;
                s.burnDps = s.dmg * 3f;
                s.burnDur = 3f;
                s.hit = new HashSet<>();
                float dx = t.x - x, dy = t.y - y, l = Math.max(1f, dist(0, 0, dx, dy));
                s.vx = dx / l * s.speed;
                s.vy = dy / l * s.speed;
                s.life = rangeOf(p) * 1.5f / s.speed;
                s.target = null;
                break;
            }
            default: s.speed = 600;
        }
        s.kind = kind;
        shots.add(s);
    }

    void updateShots(float dt) {
        for (int i = shots.size() - 1; i >= 0; i--) {
            Shot s = shots.get(i);
            s.life -= dt;
            s.spin += dt * 8f;
            if (s.kind == Defs.K_TORNADO) {
                s.x += s.vx * dt;
                s.y += s.vy * dt;
                for (Enemy e : enemies) {
                    if (e.dead || e.phased || s.hit.contains(e)) continue;
                    if (dist(s.x, s.y, e.x, e.y) < 46 + e.radius) {
                        s.hit.add(e);
                        hitEnemy(s, e);
                    }
                }
                if (s.life <= 0) shots.remove(i);
                continue;
            }
            if (s.target == null || s.target.dead || s.target.phased) {
                s.target = findTarget(s.x, s.y, 300);
                if (s.target == null) { shots.remove(i); continue; }
            }
            Enemy t = s.target;
            float dx = t.x - s.x, dy = t.y - s.y, l = dist(0, 0, dx, dy);
            float step = s.speed * dt;
            if (l <= step + t.radius * 0.6f) {
                hitEnemy(s, t);
                shots.remove(i);
                continue;
            }
            s.vx = dx / l * s.speed;
            s.vy = dy / l * s.speed;
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            if (s.life <= 0) shots.remove(i);
        }
    }

    void hitEnemy(Shot s, Enemy e) {
        damage(e, s.dmg, s.src);
        if (s.burnDps > 0) applyBurn(e, s.burnDps, s.burnDur);
        if (s.slowAmt > 0) applySlow(e, s.slowAmt, s.slowDur);
        if (s.stunCh > 0 && rnd.nextFloat() < s.stunCh && !e.boss) {
            e.stunT = Math.max(e.stunT, s.stunDur);
            text(e.x, e.y - 40, "STUN", 0xFF7DF3FF);
        }
        if (s.src != null && !s.mini) applyEnchantOnHit(e, s.dmg, s.src);
        int c;
        switch (s.kind) {
            case Defs.K_SMOKE: c = 0xFFFF4D3D; puff(e.x, e.y, 0x99FF3B2F); break;
            case Defs.K_FIREROCK: case Defs.K_TORNADO: c = 0xFFFF8A2B; break;
            case Defs.K_ICE: c = 0xFF9BEFFF; break;
            case Defs.K_MISSILE: c = 0xFFFFE08A; break;
            default: c = 0xFFD9C7B0;
        }
        for (int k = 0; k < 3; k++) particle(e.x, e.y, c, 140);
    }

    void applyEnchantOnHit(Enemy e, float dmg, Planet p) {
        if (p.enchant == Defs.EN_FROZE) {
            applySlow(e, 0.3f, 1.5f);
            damage(e, dmg * 0.2f, p);
        } else if (p.enchant == Defs.EN_BURN) {
            applyBurn(e, dmg * 0.3f, 3f);
        }
    }

    void applySlow(Enemy e, float amt, float dur) {
        if (e.boss) amt *= 0.5f;
        if (amt >= e.slow) { e.slow = amt; e.slowT = Math.max(e.slowT, dur); }
    }

    void applyBurn(Enemy e, float dps, float dur) {
        if (e.type == Defs.E_HELL) return;
        if (dps >= e.burnDps || e.burnT <= 0) e.burnDps = dps;
        e.burnT = Math.max(e.burnT, dur);
    }

    void damage(Enemy e, float amt, Planet src) {
        if (e.dead || amt <= 0) return;
        e.noDmgT = 0;
        if (e.shield > 0) {
            float a = Math.min(e.shield, amt);
            e.shield -= a;
            amt -= a;
        }
        e.hp -= amt;
        e.hitFlash = 0.08f;
        if (e.hp <= 0) kill(e, src);
    }

    void kill(Enemy e, Planet src) {
        e.dead = true;
        int c = e.boss ? 0xFFB86BFF : 0xFFFFB35C;
        ring(e.x, e.y, c, e.radius * 2f);
        for (int i = 0; i < (e.boss ? 40 : 8); i++) particle(e.x, e.y, c, e.boss ? 400 : 200);
        if (src != null) src.kills++;
        if (src != null && src.enchant == Defs.EN_GOLD && rnd.nextFloat() < 0.35f) {
            stars++;
            starText(e.x, e.y - 30, 1);
        }
        if (e.boss) {
            stars += 8;
            starText(e.x, e.y - 50, 8);
            shake = 0.6f;
        }
        if (e.type == Defs.E_MEGA) {
            for (int k = 0; k < 2; k++) {
                Enemy m = makeEnemy(Defs.E_METEOR);
                m.maxHp = e.maxHp * 0.3f;
                m.hp = m.maxHp;
                m.d = Math.max(0, e.d - 15 * k);
                pointAt(m.d, tmp);
                m.x = tmp[0];
                m.y = tmp[1];
                pending.add(m);
            }
        }
    }

    void updateEnemies(float dt) {
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            if (e.dead) { enemies.remove(i); continue; }
            e.angle += dt * (e.boss ? 60f : 90f);
            if (e.hitFlash > 0) e.hitFlash -= dt;
            if (e.burnT > 0) {
                e.burnT -= dt;
                damage(e, e.burnDps * dt, null);
                if (e.dead) { enemies.remove(i); continue; }
                if (rnd.nextFloat() < dt * 8) particle(e.x, e.y - e.radius * 0.5f, 0xFFFF7A2B, 60);
            }
            if (e.slowT > 0) { e.slowT -= dt; if (e.slowT <= 0) e.slow = 0; }
            if (e.stunT > 0) e.stunT -= dt;
            else e.d += e.speed * (1f - e.slow) * dt;

            e.abilT += dt;
            e.noDmgT += dt;
            switch (e.type) {
                case Defs.E_ALTMOON:
                    e.phased = (e.abilT % 3.4f) > 2.4f;
                    break;
                case Defs.E_ALTPLANET:
                    if (e.abilT > 4.5f) {
                        e.abilT = 0;
                        ring(e.x, e.y, 0xFFFF5BD8, 70);
                        e.d += 120;
                    }
                    break;
                case Defs.E_EXOMOON:
                    if (e.abilT > 2.5f) {
                        e.abilT = 0;
                        for (Enemy o : enemies) {
                            if (o.dead || o.boss || dist(o.x, o.y, e.x, e.y) > 170) continue;
                            o.hp = Math.min(o.maxHp, o.hp + o.maxHp * 0.08f);
                        }
                        ring(e.x, e.y, 0xFF7CFF8A, 170);
                    }
                    break;
                case Defs.E_EXOPLANET:
                    if (e.noDmgT > 2.5f && e.shield < e.maxShield)
                        e.shield = Math.min(e.maxShield, e.shield + e.maxShield * 0.25f * dt);
                    break;
                case Defs.E_BLACKHOLE:
                    if (e.abilT > 6f) {
                        e.abilT = 0;
                        ArrayList<Planet> near = new ArrayList<>();
                        for (Planet p : slots)
                            if (p != null && p.disabledT <= 0 && dist(p.x, p.y, e.x, e.y) < 420) near.add(p);
                        if (!near.isEmpty()) {
                            Planet p = near.get(rnd.nextInt(near.size()));
                            p.disabledT = 3f;
                            text(p.x, p.y - 70, "ถูกดูด!", 0xFFB86BFF);
                        }
                        ring(e.x, e.y, 0xFF8A5BFF, 420);
                    }
                    break;
                default:
                    break;
            }

            if (e.d >= total) {
                baseHp -= e.baseDmg;
                shake = 0.35f;
                pointAt(total, tmp);
                ring(tmp[0], tmp[1], 0xFFFF4D5E, 110);
                text(tmp[0], tmp[1] - 70, "-" + e.baseDmg, 0xFFFF4D5E);
                e.dead = true;
                enemies.remove(i);
                continue;
            }
            pointAt(e.d, tmp);
            e.x = tmp[0];
            e.y = tmp[1];
        }
    }

    void updateHeat(float dt) {
        for (int i = heat.size() - 1; i >= 0; i--) {
            HeatWave h = heat.get(i);
            h.d -= 260 * dt;
            for (Enemy e : enemies) {
                if (e.dead || h.hit.contains(e) || Math.abs(e.d - h.d) > 40 + e.radius) continue;
                h.hit.add(e);
                float need = e.hp + e.shield;
                float dmg = Math.min(h.hp, need);
                damage(e, dmg, h.src);
                applyBurn(e, dmgOf(h.src) * 0.5f, 3f);
                h.hp -= dmg * 0.5f;
                if (h.hp <= 0) break;
            }
            if (h.hp <= 0 || h.d <= 0) {
                pointAt(Math.max(0, h.d), tmp);
                ring(tmp[0], tmp[1], 0xFFFFB627, 90);
                heat.remove(i);
            }
        }
    }

    // ------------------------------------------------------------------ fx
    void updateFx(float dt) {
        for (int i = fx.size() - 1; i >= 0; i--) {
            Fx f = fx.get(i);
            f.life -= dt;
            if (f.life <= 0) { fx.remove(i); continue; }
            f.x += f.vx * dt;
            f.y += f.vy * dt;
            if (f.kind == Fx.PART) { f.vx *= 0.92f; f.vy *= 0.92f; }
        }
        if (fx.size() > 400) fx.subList(0, fx.size() - 400).clear();
    }

    void particle(float x, float y, int color, float spd) {
        Fx f = new Fx();
        f.kind = Fx.PART;
        f.x = x;
        f.y = y;
        double a = rnd.nextDouble() * Math.PI * 2;
        float v = spd * (0.3f + rnd.nextFloat());
        f.vx = (float) Math.cos(a) * v;
        f.vy = (float) Math.sin(a) * v;
        f.life = f.maxLife = 0.4f + rnd.nextFloat() * 0.4f;
        f.size = 4 + rnd.nextFloat() * 6;
        f.color = color;
        fx.add(f);
    }

    void ring(float x, float y, int color, float size) {
        Fx f = new Fx();
        f.kind = Fx.RING;
        f.x = x;
        f.y = y;
        f.life = f.maxLife = 0.45f;
        f.size = size;
        f.color = color;
        fx.add(f);
    }

    void puff(float x, float y, int color) {
        Fx f = new Fx();
        f.kind = Fx.PUFF;
        f.x = x;
        f.y = y;
        f.vy = -30;
        f.life = f.maxLife = 0.7f;
        f.size = 40;
        f.color = color;
        fx.add(f);
    }

    void text(float x, float y, String s, int color) {
        Fx f = new Fx();
        f.kind = Fx.TEXT;
        f.x = x;
        f.y = y;
        f.vy = -50;
        f.life = f.maxLife = 1.1f;
        f.text = s;
        f.color = color;
        fx.add(f);
    }

    void starText(float x, float y, int amount) {
        Fx f = new Fx();
        f.kind = Fx.STARTEXT;
        f.x = x;
        f.y = y;
        f.vy = -60;
        f.life = f.maxLife = 1.1f;
        f.text = "+" + amount;
        f.color = 0xFFFFD23F;
        fx.add(f);
    }
}
