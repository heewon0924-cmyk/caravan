package com.caravan.travel;

import com.caravan.data.RouteSpec;
import com.caravan.data.WorldData;
import com.caravan.data.WorldRules;
import com.caravan.trade.Trader;
import com.caravan.world.WorldClock;

import java.util.List;

/**
 * 이동. 노선을 고르고, 얼마나 걸리고 얼마가 드는지 계산하고, 떠나보낸다.
 *
 * <p>docs/03-이동과-시간.md 5장.
 *
 * <pre>
 *   실제 소요 = 기본 소요 × (1 + 적재감속 × 적재율)
 *   비용      = 기본 소요(시간) × 시간당 출발비 + 통행료
 * </pre>
 *
 * <p>가득 싣는 게 항상 이득이 아니게 만드는 장치다. 적분 가격이 "많이 사면 단가가
 * 오른다" 로 한 번 누르고, 여기서 "많이 실으면 느려진다" 로 또 한 번 누른다.
 * <b>두 장치가 같은 방향으로 눌러야 적재량 결정이 진짜 결정이 된다.</b>
 */
public final class Travel {

    private final WorldData data;
    private final WorldRules rules;

    public Travel(WorldData data) {
        this.data = data;
        this.rules = data.rules();
    }

    /** 얼마나 걸리고 얼마가 드는지만 계산한다. 아무것도 바꾸지 않는다. */
    public Departure quote(Caravan caravan, long tick, RouteSpec route) {
        String from = caravan.cityId(tick);
        if (from == null) {
            throw new TravelRefused(caravan.name() + " 은 지금 이동 중이다");
        }
        if (!route.connects(from)) {
            throw new TravelRefused(route.name() + " 은 " + from + " 를 지나지 않는다");
        }

        double loadRatio = caravan.loadRatio(data.goods());
        double actualHours = route.hours() * (1 + rules.loadSlowdown() * loadRatio);
        long ticks = Math.max(1, Math.round(actualHours * 60 / WorldClock.MINUTES_PER_TICK));

        return new Departure(
                route, from, route.otherEnd(from),
                route.hours(), actualHours, loadRatio,
                route.hours() * rules.travelCostPerHour(), route.toll(), route.danger(),
                tick, tick + ticks);
    }

    /**
     * 떠난다. 비용을 물고 캐러밴을 노선 위에 올린다.
     *
     * <p><b>되돌릴 수 없다.</b> 회군을 허용하면 "위험한 길을 감수한다" 는 결정이
     * 무의미해진다 (docs/03 1장). 결정은 출발 버튼을 누를 때 끝나야 한다.
     */
    public Departure depart(Trader trader, Caravan caravan, long tick, RouteSpec route) {
        Departure departure = quote(caravan, tick, route);

        // 돈을 먼저 문다. 모자라면 여기서 거절되고 캐러밴은 그대로 도시에 남는다.
        trader.pay(departure.totalCost());

        caravan.depart(new Journey(
                route, departure.fromId(), departure.toId(),
                departure.actualHours(), departure.loadRatio(),
                departure.departedTick(), departure.arrivesTick()));

        return departure;
    }

    /** {@code cityId} 에서 떠날 수 있는 노선들. */
    public List<RouteSpec> routesFrom(String cityId) {
        return data.routesFrom(cityId);
    }

    /**
     * 두 도시를 잇는 노선들. 여럿이면 플레이어가 골라야 한다 —
     * 하른 ↔ 셀리아의 해안가도와 늑대고개가 그렇다.
     */
    public List<RouteSpec> routesBetween(String from, String to) {
        return data.routesBetween(from, to);
    }

    public RouteSpec route(String routeId) {
        return data.routes().stream()
                .filter(r -> r.id().equalsIgnoreCase(routeId) || r.name().equals(routeId))
                .findFirst()
                .orElseThrow(() -> new TravelRefused("그런 노선이 없다: " + routeId));
    }
}
