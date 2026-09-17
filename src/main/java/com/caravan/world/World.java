package com.caravan.world;

import com.caravan.data.CitySpec;
import com.caravan.data.WorldData;
import com.caravan.data.WorldRules;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 세계 전체. 도시들을 들고 있고, 틱을 진행시킨다.
 *
 * <p>플레이어가 접속하지 않아도 세계는 흐른다. 매 틱마다 스케줄러를 돌리는 대신,
 * <b>읽을 때 밀린 틱을 몰아서 계산한다</b> (docs/03-이동과-시간.md 2장).
 * 도시 3개짜리 프로토타입에는 과한 구조지만, 처음부터 이렇게 두면 도시가
 * 30개가 돼도 안 바꾼다.
 */
public final class World {

    /**
     * 한 번에 따라잡을 수 있는 최대 틱. 세계 100일치다.
     * 시작 시각을 잘못 넣어 수백만 틱을 돌리다 멈추는 사고를 막는다.
     */
    private static final long MAX_CATCH_UP_TICKS = 100L * WorldClock.TICKS_PER_DAY;

    private final Map<String, City> cities = new LinkedHashMap<>();
    private final WorldRules rules;
    private long tick;

    public World(WorldData data) {
        this.rules = data.rules();
        for (CitySpec spec : data.cities()) {
            cities.put(spec.id(), new City(spec, data, rules));
        }
    }

    /** {@code targetTick} 까지 밀린 틱을 전부 적용한다. 이미 지나쳤으면 아무것도 안 한다. */
    public void advanceTo(long targetTick) {
        if (targetTick <= tick) {
            return;
        }
        long behind = targetTick - tick;
        if (behind > MAX_CATCH_UP_TICKS) {
            throw new IllegalStateException(
                    "따라잡을 틱이 너무 많다 (" + behind + "틱). 세계 시작 시각을 확인한다.");
        }
        // 곳간 넘침이 비선형이라 N틱치를 한 번에 더할 수 없다. 한 틱씩 돈다 —
        // 세계 7일치가 1008틱이라 비용은 무시할 만하다.
        for (long i = 0; i < behind; i++) {
            for (City c : cities.values()) {
                c.tick();
            }
            tick++;
        }
    }

    public void advanceDays(double days) {
        advanceTo(tick + Math.round(days * WorldClock.TICKS_PER_DAY));
    }

    public City city(String id) {
        City c = cities.get(id);
        if (c == null) {
            throw new IllegalArgumentException("그런 도시가 없다: " + id);
        }
        return c;
    }

    public Collection<City> cities() { return cities.values(); }
    public long tick() { return tick; }
    public WorldRules rules() { return rules; }
}
