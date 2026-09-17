package com.caravan.app;

import com.caravan.data.WorldData;
import com.caravan.event.EventEngine;
import com.caravan.event.WorldEvent;
import com.caravan.npc.NpcFleet;
import com.caravan.world.World;
import com.caravan.world.WorldClock;

import java.util.List;

/**
 * 살아 있는 세계. 도시 · 사건 · NPC 상인을 함께 굴린다.
 *
 * <p>{@link World} 는 도시와 시계만 안다. 사건과 NPC 를 거기 넣지 않은 이유는
 * NPC 가 캐러밴을 굴리고 캐러밴은 세계를 알아야 해서, 서로가 서로를 참조하게 되기
 * 때문이다. 조립은 여기서 한다 — docs/31 의 app 계층이 하는 일이 이것이다.
 *
 * <p><b>틱 순서가 중요하다.</b>
 *
 * <ol>
 *   <li><b>사건</b> — 이번 틱의 생산·소비에 이번 틱의 보정이 걸려야 한다</li>
 *   <li><b>도시</b> — 생산하고 소비한다</li>
 *   <li><b>NPC</b> — 갱신된 시세를 보고 판단한다</li>
 * </ol>
 *
 * <p>NPC 를 도시보다 먼저 돌리면 한 틱 묵은 시세를 보고 움직이게 되고,
 * 그 어긋남이 밸런스를 볼 때 원인을 찾기 어려운 잡음이 된다.
 */
public final class Simulation {

    private final World world;
    private final WorldData data;
    private final EventEngine events;
    private final NpcFleet npcs;
    private final long seed;

    public Simulation(WorldData data) {
        this(data, System.nanoTime());
    }

    /**
     * 시드를 주면 같은 세계가 다시 나온다.
     *
     * <p>난수는 서버에서만 굴리고 시드를 기록한다 (docs/07 4장).
     * 밸런스를 볼 때 같은 세계를 다시 돌릴 수 있어야 비교가 된다.
     */
    public Simulation(WorldData data, long seed) {
        this.data = data;
        this.seed = seed;
        this.world = new World(data);
        this.events = new EventEngine(data, seed);
        this.npcs = new NpcFleet(data);
    }

    public void advanceTo(long targetTick) {
        while (world.tick() < targetTick) {
            long next = world.tick() + 1;
            events.apply(next, world);
            world.advanceTo(next);
            npcs.act(next, world);
        }
    }

    public void advanceDays(double days) {
        advanceTo(world.tick() + Math.round(days * WorldClock.TICKS_PER_DAY));
    }

    public void advanceHours(double hours) {
        advanceTo(world.tick() + Math.round(hours * 60 / WorldClock.MINUTES_PER_TICK));
    }

    /** 새 상단 하나를 이 세계에 들인다. */
    public Company newCompany(String name, String startCityId) {
        return new Company(world, data, name, startCityId);
    }

    public List<WorldEvent> activeEvents() {
        return events.active();
    }

    public List<WorldEvent> forecasts() {
        return events.forecasts(world.tick());
    }

    public List<WorldEvent> justStarted() {
        return events.justStarted();
    }

    public World world() { return world; }
    public WorldData data() { return data; }
    public EventEngine events() { return events; }
    public NpcFleet npcs() { return npcs; }
    public long seed() { return seed; }
    public long tick() { return world.tick(); }
}
