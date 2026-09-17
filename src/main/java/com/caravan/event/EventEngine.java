package com.caravan.event;

import com.caravan.data.EventSpec;
import com.caravan.data.WorldData;
import com.caravan.world.City;
import com.caravan.world.Market;
import com.caravan.world.World;
import com.caravan.world.WorldClock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 세계에 사건을 일으키고, 그 효과를 시장과 노선에 반영한다.
 *
 * <p><b>가격을 직접 바꾸지 않는다.</b> 생산·소비·재고를 바꾸고 가격은 따라온다
 * (docs/02-교역과-가격.md 1장). 흉년이 났다는 걸 아는 플레이어는 밀값이 얼마까지
 * 오를지 계산할 수 있어야 한다.
 *
 * <p>난수는 <b>사건이 일어나는지 여부</b>에만 들어간다. 일어난 뒤의 가격은 완전히
 * 결정론적이다. 시드를 밖에서 주므로 같은 시드면 같은 세계가 나온다 (docs/07 4장).
 */
public final class EventEngine {

    private final WorldData data;
    private final Random random;
    private final double perTickChance;
    private final int maxConcurrent;

    private final List<WorldEvent> pending = new ArrayList<>();
    private final List<WorldEvent> active = new ArrayList<>();
    private final List<WorldEvent> history = new ArrayList<>();

    /** 이번 틱에 새로 시작한 사건들. 콘솔이 알림으로 쓴다. */
    private final List<WorldEvent> justStarted = new ArrayList<>();

    private final Map<String, Double> routeDanger = new HashMap<>();

    public EventEngine(WorldData data, long seed) {
        this.data = data;
        this.random = new Random(seed);
        this.perTickChance = data.rules().eventsPerDay() / WorldClock.TICKS_PER_DAY;
        this.maxConcurrent = data.rules().maxConcurrentEvents();
    }

    /**
     * 한 틱분. <b>도시가 틱을 돌기 전에</b> 부른다 — 이번 틱의 생산·소비에
     * 이번 틱의 보정이 걸려야 하기 때문이다.
     */
    public void apply(long tick, World world) {
        justStarted.clear();

        expireFinished(tick);
        startDue(tick, world);
        maybeSchedule(tick);
        recomputeModifiers(world);
    }

    private void expireFinished(long tick) {
        active.removeIf(e -> {
            if (e.hasEndedBy(tick)) {
                history.add(e);
                return true;
            }
            return false;
        });
    }

    private void startDue(long tick, World world) {
        pending.removeIf(e -> {
            if (tick >= e.startTick()) {
                active.add(e);
                justStarted.add(e);
                applyStockBonus(e, world);
                return true;
            }
            return false;
        });
    }

    /** 상선 입항처럼 한 번에 재고를 쏟아붓는 사건. */
    private void applyStockBonus(WorldEvent event, World world) {
        EventSpec spec = event.spec();
        if (spec.stockBonusRatio() == null || event.cityId() == null) {
            return;
        }
        City city = world.city(event.cityId());
        for (Market m : targets(city, spec)) {
            m.addStock(m.refStock() * spec.stockBonusRatio());
        }
    }

    private void maybeSchedule(long tick) {
        if (active.size() + pending.size() >= maxConcurrent) {
            return;
        }
        if (random.nextDouble() >= perTickChance) {
            return;
        }

        EventSpec spec = pickByWeight();
        if (spec == null) {
            return;
        }
        // 이미 같은 사건이 걸려 있으면 거른다 — 흉년이 두 겹으로 오면 계산이 안 된다
        boolean already = java.util.stream.Stream.concat(active.stream(), pending.stream())
                .anyMatch(e -> e.spec().id().equals(spec.id()));
        if (already) {
            return;
        }

        long lead = Math.round(spec.forecastHours() * 60 / WorldClock.MINUTES_PER_TICK);
        long start = tick + Math.max(lead, 1);
        long end = start + Math.max(1, Math.round(spec.durationHours() * 60 / WorldClock.MINUTES_PER_TICK));

        pending.add(new WorldEvent(spec, cityFor(spec), start, end));
    }

    private String cityFor(EventSpec spec) {
        if (spec.city() != null) {
            return spec.city();
        }
        if (!spec.affectsMarkets()) {
            return null;
        }
        return data.cities().get(random.nextInt(data.cities().size())).id();
    }

    private EventSpec pickByWeight() {
        double total = data.events().stream().mapToDouble(EventSpec::weight).sum();
        if (total <= 0) {
            return null;
        }
        double roll = random.nextDouble() * total;
        for (EventSpec spec : data.events()) {
            roll -= spec.weight();
            if (roll <= 0) {
                return spec;
            }
        }
        return data.events().get(data.events().size() - 1);
    }

    /**
     * 진행 중인 사건들을 모아 시장과 노선의 보정치를 다시 계산한다.
     *
     * <p>매 틱 전부 다시 계산한다. 도시 3개 × 품목 5개라 비용이 없고,
     * 사건이 겹치거나 끝날 때 보정이 어긋나 남는 일이 없다.
     */
    private void recomputeModifiers(World world) {
        for (City city : world.cities()) {
            for (Market m : city.markets()) {
                m.setModifiers(1.0, 1.0);
            }
        }
        routeDanger.clear();

        for (WorldEvent e : active) {
            EventSpec spec = e.spec();

            if (spec.affectsMarkets() && e.cityId() != null) {
                City city = world.city(e.cityId());
                for (Market m : targets(city, spec)) {
                    m.setModifiers(
                            m.productionModifier() * spec.production(),
                            m.consumptionModifier() * spec.consumption());
                }
            }
            if (spec.affectsRoutes()) {
                for (String routeId : spec.routes()) {
                    routeDanger.merge(routeId, spec.danger(), (a, b) -> a * b);
                }
            }
        }
    }

    private List<Market> targets(City city, EventSpec spec) {
        if (spec.goods() == null || spec.goods().isEmpty()) {
            return new ArrayList<>(city.markets());
        }
        return spec.goods().stream().map(city::market).toList();
    }

    // ── 밖에서 보는 것 ────────────────────────────────────

    /** 지금 진행 중인 사건들. */
    public List<WorldEvent> active() {
        return List.copyOf(active);
    }

    /** 예고가 이미 도는, 아직 시작 전인 사건들. 5단계에서 소문의 재료가 된다. */
    public List<WorldEvent> forecasts(long tick) {
        return pending.stream().filter(e -> e.isForecastVisibleAt(tick)).toList();
    }

    /** 이번 틱에 새로 시작한 사건들. */
    public List<WorldEvent> justStarted() {
        return List.copyOf(justStarted);
    }

    public List<WorldEvent> history() {
        return List.copyOf(history);
    }

    /** 이 노선의 위험도 배수. 사건이 없으면 1.0. */
    public double dangerMultiplier(String routeId) {
        return routeDanger.getOrDefault(routeId, 1.0);
    }

    /** 지금 걸려 있는 전직 계열 보너스. 7단계에서 쓴다. */
    public Map<String, Double> jobFamilyBonuses() {
        Map<String, Double> bonuses = new LinkedHashMap<>();
        for (WorldEvent e : active) {
            if (e.spec().jobFamilyBonus() != null) {
                bonuses.merge(e.spec().jobFamilyBonus(), 2.0, (a, b) -> a * b);
            }
        }
        return bonuses;
    }
}
