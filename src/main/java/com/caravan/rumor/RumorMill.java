package com.caravan.rumor;

import com.caravan.data.GoodsSpec;
import com.caravan.data.WorldData;
import com.caravan.event.EventEngine;
import com.caravan.event.WorldEvent;
import com.caravan.world.City;
import com.caravan.world.Market;
import com.caravan.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 소문을 만든다.
 *
 * <p>세계에서 실제로 일어나는 일이 소문의 재료다 — <b>사건은 정보 시스템의 먹이다</b>
 * (docs/09-이벤트와-NPC.md 3장). 예고된 사건, 진행 중인 사건, 재고가 극단으로 간 시장.
 *
 * <p>거짓 소문도 나온다. 다만 <b>게임이 플레이어를 속이려고 만드는 게 아니다</b>
 * (docs/04-정보.md 3장). 출처의 신뢰도만큼 참이 나오고 나머지가 거짓이며,
 * 신뢰도를 알고 기댓값대로 행동하면 장기적으로 이득이도록 둔다.
 * 그렇지 않으면 플레이어는 곧 <b>모든 정보를 무시하는 게 최적</b>이라는 걸 알아채고,
 * 그 순간 정보 시스템이 통째로 죽는다.
 */
public final class RumorMill {

    /** 이 비율만큼은 다른 도시 얘기로 만든다. 여기 얘기는 어차피 눈으로 보인다. */
    private static final double ELSEWHERE_RATIO = 0.6;

    /** 재고가 기준의 이 비율 아래면 "오른다" 는 참말이 된다. */
    private static final double SHORTAGE_RATIO = 0.55;

    /** 재고가 기준의 이 비율 위면 "내린다" 는 참말이 된다. */
    private static final double GLUT_RATIO = 1.6;

    private final WorldData data;
    private final World world;
    private final EventEngine events;
    private final Random random;
    private long nextId = 1;

    public RumorMill(WorldData data, World world, EventEngine events, long seed) {
        this.data = data;
        this.world = world;
        this.events = events;
        this.random = new Random(seed);
    }

    /**
     * 그 도시에서 이 출처로 소문 하나를 듣는다. 들을 게 없으면 {@code null}.
     */
    public Rumor hear(City here, Source source, long tick) {
        return hear(here, source, tick, false);
    }

    /**
     * @param preferForward 앞일(예고된 사건)을 우선해서 고른다.
     *        <b>정보상에게 돈을 내는 이유가 이것이다</b> — 이미 벌어진 일은 가보면
     *        알 수 있지만 앞일은 들어야만 안다 (docs/04 5장).
     */
    public Rumor hear(City here, Source source, long tick, boolean preferForward) {
        boolean truthful = random.nextDouble() < source.trust();
        Signal signal = truthful ? pickTrueSignal(here, tick, preferForward) : null;

        if (signal == null) {
            signal = fabricate(here, tick);
            truthful = false;
        }
        return build(signal, source, truthful, tick);
    }

    /**
     * 도시에 도착하면 이만큼 저절로 귀에 들어온다.
     *
     * <p>{@code source} 가 곧 이 상단이 가진 <b>정보 채널의 품질</b>이다.
     * 아무것도 없으면 떠도는 말뿐이고, 캐러밴에 정보 계열 인물을 태우면
     * 그 사람이 쓸 만한 것을 물어온다 (docs/04 4장, docs/05 3장).
     *
     * <p>이게 5단계의 결론이다 — <b>믿을 만한 정보는 공짜로 떠돌지 않는다.
     * 캐러밴 여섯 칸 중 하나를 내줘야 얻는다.</b> 정보의 값을 돈으로 매기려 하면
     * 왕복 한 번 이익이 600 G 인데 정보 한 조각 값이 65 G 라 가격을 맞출 수가 없다
     * (docs/25 3장 (라)).
     */
    public List<Rumor> onArrival(City here, Source source, long tick) {
        List<Rumor> got = new ArrayList<>();
        int count = 1 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            Rumor r = hear(here, source, tick, source.trust() >= Source.정보원.trust());
            if (r != null) {
                got.add(r);
            }
        }
        // 여기 시세는 눈으로 본다. 이건 언제나 참이다.
        got.add(observed(here, tick));
        return got;
    }

    /** 지금 이 도시에서 눈으로 본 것. 신뢰도 100%. */
    private Rumor observed(City here, long tick) {
        Market tightest = here.markets().stream()
                .min((a, b) -> Double.compare(a.stock() / a.refStock(), b.stock() / b.refStock()))
                .orElseThrow();
        boolean rising = tightest.stock() / tightest.refStock() < 1.0;
        return build(new Signal(here.id(), tightest.goods().id(), rising,
                        strengthFor(tightest), false, tick, tick + hours(8), tick + hours(8),
                        String.format("%s의 %s 창고가 %s", here.name(), tightest.goods().name(),
                                rising ? "비어 간다" : "가득하다")),
                Source.직접본것, true, tick);
    }

    // ── 참말의 재료 ───────────────────────────────────────

    private Signal pickTrueSignal(City here, long tick, boolean preferForward) {
        List<Signal> forecasts = fromForecasts(tick);
        if (preferForward && !forecasts.isEmpty()) {
            return forecasts.get(random.nextInt(forecasts.size()));
        }

        List<Signal> pool = new ArrayList<>();
        pool.addAll(forecasts);
        pool.addAll(fromActiveEvents(tick));
        pool.addAll(fromMarkets(tick));

        if (pool.isEmpty()) {
            return null;
        }
        // 60% 는 다른 도시 얘기로. 정보의 가치는 "먼 곳의 일을 여기서 아는 것" 에 있다.
        List<Signal> elsewhere = pool.stream().filter(s -> !s.cityId.equals(here.id())).toList();
        List<Signal> local = pool.stream().filter(s -> s.cityId.equals(here.id())).toList();

        boolean preferElsewhere = random.nextDouble() < ELSEWHERE_RATIO;
        List<Signal> chosen = preferElsewhere && !elsewhere.isEmpty() ? elsewhere
                : (!local.isEmpty() ? local : pool);
        return chosen.get(random.nextInt(chosen.size()));
    }

    /** 아직 시작 안 한 사건. <b>선점의 재료</b>이고 가장 값진 정보다. */
    private List<Signal> fromForecasts(long tick) {
        List<Signal> signals = new ArrayList<>();
        for (WorldEvent e : events.forecasts(tick)) {
            signals.addAll(signalsOf(e, tick, e.endTick(), true));
        }
        return signals;
    }

    /** 이미 시작한 사건. 참이지만 값은 이미 움직이는 중이다. */
    private List<Signal> fromActiveEvents(long tick) {
        List<Signal> signals = new ArrayList<>();
        for (WorldEvent e : events.active()) {
            signals.addAll(signalsOf(e, tick, e.endTick(), false));
        }
        return signals;
    }

    private List<Signal> signalsOf(WorldEvent event, long tick, long until, boolean forward) {
        long effectFrom = event.startTick();
        long effectUntil = event.endTick();
        List<Signal> signals = new ArrayList<>();
        if (event.cityId() == null || !event.spec().affectsMarkets()) {
            return signals;
        }
        City city = world.city(event.cityId());
        List<String> goods = event.spec().goods() == null || event.spec().goods().isEmpty()
                ? data.goodsInOrder().stream().map(GoodsSpec::id).toList()
                : event.spec().goods();

        // 생산이 줄면 값이 오르고, 늘거나 재고가 쏟아지면 내린다
        boolean rising = event.spec().production() < 1.0
                || event.spec().consumption() > 1.0;
        Strength strength = strengthOfEvent(event);

        for (String goodsId : goods) {
            signals.add(new Signal(city.id(), goodsId, rising, strength, forward,
                    effectFrom, effectUntil,
                    Math.min(until, effectUntil), event.headline()));
        }
        return signals;
    }

    private Strength strengthOfEvent(WorldEvent event) {
        double production = event.spec().production();
        double shock = Math.abs(1 - production);
        if (event.spec().stockBonusRatio() != null) {
            shock = Math.max(shock, event.spec().stockBonusRatio());
        }
        if (shock >= 0.6) return Strength.강함;
        if (shock >= 0.3) return Strength.보통;
        return Strength.약함;
    }

    /** 사건이 없어도 재고가 한쪽으로 몰려 있으면 그것도 참말이다. */
    private List<Signal> fromMarkets(long tick) {
        List<Signal> signals = new ArrayList<>();
        for (City city : world.cities()) {
            for (Market m : city.markets()) {
                double ratio = m.stock() / m.refStock();
                if (ratio < SHORTAGE_RATIO) {
                    signals.add(new Signal(city.id(), m.goods().id(), true, strengthFor(m), false,
                            tick, tick + hours(10), tick + hours(10),
                            String.format("%s에 %s이 모자란다", city.name(), m.goods().name())));
                } else if (ratio > GLUT_RATIO) {
                    signals.add(new Signal(city.id(), m.goods().id(), false, strengthFor(m), false,
                            tick, tick + hours(10), tick + hours(10),
                            String.format("%s에 %s이 넘친다", city.name(), m.goods().name())));
                }
            }
        }
        return signals;
    }

    private Strength strengthFor(Market m) {
        double ratio = m.stock() / m.refStock();
        double distance = Math.abs(1 - ratio);
        if (distance >= 0.7) return Strength.강함;
        if (distance >= 0.35) return Strength.보통;
        return Strength.약함;
    }

    // ── 거짓말 ───────────────────────────────────────────

    /**
     * 지어낸다. 누군가 착각했거나 넘겨짚은 것이다.
     *
     * <p>실제 신호와 <b>반대</b>로 만들지 않는다. 그렇게 하면 거짓 소문이
     * 역으로 정확한 정보가 되어버린다 — "주점 얘기는 뒤집으면 맞다" 가 되면
     * 신뢰도가 의미를 잃는다. 그냥 아무 일도 안 일어날 대상을 고른다.
     */
    private Signal fabricate(City here, long tick) {
        List<City> cities = new ArrayList<>(world.cities());
        City city = cities.get(random.nextInt(cities.size()));
        GoodsSpec goods = data.goodsInOrder().get(random.nextInt(data.goodsInOrder().size()));
        boolean rising = random.nextBoolean();
        Strength strength = Strength.values()[random.nextInt(Strength.values().length)];

        return new Signal(city.id(), goods.id(), rising, strength, false,
                tick, tick + hours(8), tick + hours(8),
                String.format("%s의 %s 값이 %s %s 모양이다",
                        city.name(), goods.name(), strength.label(),
                        rising ? "오를" : "내릴"));
    }

    // ── 도구 ─────────────────────────────────────────────

    private Rumor build(Signal signal, Source source, boolean truth, long tick) {
        return new Rumor(nextId++, signal.cityId, signal.goodsId, signal.rising,
                signal.strength, source, signal.forward, tick,
                signal.effectFrom, signal.effectUntil, signal.expiresTick, signal.text, truth);
    }

    private static long hours(double h) {
        return Math.round(h * 60 / com.caravan.world.WorldClock.MINUTES_PER_TICK);
    }

    /** 소문이 되기 전의 재료. */
    private record Signal(String cityId, String goodsId, boolean rising, Strength strength,
                          boolean forward, long effectFrom, long effectUntil,
                          long expiresTick, String text) {
    }
}
