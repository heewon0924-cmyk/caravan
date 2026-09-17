package com.caravan.app;

import com.caravan.data.RouteSpec;
import com.caravan.data.WorldData;
import com.caravan.trade.Exchange;
import com.caravan.trade.Receipt;
import com.caravan.trade.TradeRefused;
import com.caravan.trade.Trader;
import com.caravan.travel.Caravan;
import com.caravan.travel.Departure;
import com.caravan.travel.Journey;
import com.caravan.travel.Travel;
import com.caravan.travel.TravelRefused;
import com.caravan.world.City;
import com.caravan.rumor.Rumor;
import com.caravan.rumor.RumorBoard;
import com.caravan.rumor.RumorMill;
import com.caravan.rumor.Source;
import com.caravan.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 상단 하나. 플레이어가 조작하는 단위다.
 *
 * <p>docs/31-서버와-클라이언트-경계.md 의 <b>app 계층</b> — 유스케이스를 조립한다.
 * 도메인 조각들(거래소 · 이동 · 캐러밴 · 세계)을 모아 "플레이어가 할 수 있는 일"
 * 하나하나로 만든다.
 *
 * <p>여기 있는 규칙들이 특히 중요하다.
 *
 * <ul>
 *   <li>이동 중에는 사고팔 수 없다</li>
 *   <li>캐러밴 용량을 넘겨 실을 수 없다</li>
 *   <li>도착해도 자동으로 팔지 않는다</li>
 * </ul>
 *
 * <p>이런 규칙이 콘솔이나 화면에 들어가면 경계가 샌다 — 클라이언트를 갈아끼울 때
 * 다시 구현해야 하고, 멀티에서는 클라이언트를 고쳐서 우회할 수 있게 된다.
 * <b>클라이언트는 거절당한 이유를 받아서 보여줄 뿐이어야 한다.</b>
 */
public final class Company {

    private final World world;
    private final WorldData data;
    private final Trader trader;
    private final Caravan caravan;
    private final Exchange exchange;
    private final Travel travel;
    private final RumorMill mill;
    private final RumorBoard rumors = new RumorBoard();

    /** 마지막으로 머물던 도시. 도착을 알아채는 데 쓴다. */
    private String lastCityId;

    /** 이 도시에서 주점에 들렀는가. 방문당 한 번만 공짜다. */
    private boolean tavernUsedHere;

    /** 막 도착해서 귀에 들어온 소문들. 화면이 한 번 읽어가면 비운다. */
    private final List<Rumor> freshlyHeard = new ArrayList<>();

    public Company(World world, WorldData data, String name, String startCityId) {
        this(world, data, name, startCityId, null);
    }

    /** 이 상단이 가진 정보 채널의 품질. 6단계에서 정보 계열 인물이 이걸 올린다. */
    private Source channel = Source.떠도는말;

    public Company(World world, WorldData data, String name, String startCityId, RumorMill mill) {
        this.world = world;
        this.data = data;
        this.trader = new Trader("player", name, data.rules().startingGold());
        this.caravan = new Caravan("caravan-1", name + "의 캐러밴",
                data.rules().caravanCapacity(), startCityId);
        this.exchange = new Exchange(data.rules());
        this.travel = new Travel(data);
        this.mill = mill;
        this.lastCityId = startCityId;
    }

    // ── 소문 ────────────────────────────────────────────

    /**
     * 도시가 바뀌었으면 도착한 것이다. 그때 소문이 귀에 들어온다.
     *
     * <p>모든 공개 메서드가 먼저 이걸 부른다. 캐러밴은 도착 처리를 <b>읽을 때</b>
     * 하므로(docs/03 2장) 어딘가에서 알아채 줘야 하는데, 화면이 그 책임을 지면
     * 화면을 갈아끼울 때 소문이 조용히 사라진다.
     */
    private void syncArrival() {
        if (mill == null) {
            return;
        }
        String cityId = caravan.cityId(world.tick());
        if (cityId == null || cityId.equals(lastCityId)) {
            return;
        }
        lastCityId = cityId;
        tavernUsedHere = false;
        for (Rumor r : mill.onArrival(world.city(cityId), channel, world.tick())) {
            rumors.add(r);
            freshlyHeard.add(r);
        }
    }

    /**
     * 정보 채널을 올린다. 6단계에서 정보 계열 인물을 태우면 이걸 부른다.
     *
     * <p>지금은 시험용으로 직접 켠다 — 정보가 값을 하는지 재려면 채널 말고 모든 것이
     * 똑같은 두 상단이 필요하기 때문이다.
     */
    public void setInformationChannel(Source source) {
        this.channel = source;
    }

    public Source informationChannel() {
        return channel;
    }

    /** 막 도착해서 들은 것들. 한 번 읽으면 비워진다. */
    public List<Rumor> takeFreshRumors() {
        syncArrival();
        List<Rumor> got = List.copyOf(freshlyHeard);
        freshlyHeard.clear();
        return got;
    }

    /** 지금 들고 있는 소문들. 유효기간이 지난 것은 빠진다. */
    public List<Rumor> rumors() {
        syncArrival();
        return rumors.current(world.tick());
    }

    public RumorBoard rumorBoard() {
        return rumors;
    }

    /** 주점에서 듣는다. 공짜지만 방문당 한 번이고 믿을 게 못 된다. */
    public Rumor listenAtTavern() {
        City city = requireAtCity();
        if (mill == null) {
            throw new TradeRefused("이 세계에는 소문이 돌지 않는다");
        }
        if (tavernUsedHere) {
            throw new TradeRefused("이 도시 주점에서는 이미 들을 만큼 들었다");
        }
        tavernUsedHere = true;
        Rumor r = mill.hear(city, Source.주점, world.tick());
        rumors.add(r);
        return r;
    }

    /** 정보상에게 산다. 돈이 들지만 주점보다 훨씬 믿을 만하다. */
    public Rumor buyFromInformant() {
        City city = requireAtCity();
        if (mill == null) {
            throw new TradeRefused("이 세계에는 소문이 돌지 않는다");
        }
        trader.pay(data.rules().informantFee());
        Rumor r = mill.hear(city, Source.정보상, world.tick(), true);
        rumors.add(r);
        return r;
    }

    // ── 지금 어디에 있는가 ────────────────────────────────

    /** 머물고 있는 도시. 이동 중이면 {@code null}. */
    public City here() {
        syncArrival();
        String cityId = caravan.cityId(world.tick());
        return cityId == null ? null : world.city(cityId);
    }

    public boolean isTravelling() {
        return caravan.isTravelling(world.tick());
    }

    public Journey journey() {
        return caravan.journey(world.tick());
    }

    /** 도시에 있어야만 할 수 있는 일. 이동 중이면 거절한다. */
    private City requireAtCity() {
        City city = here();
        if (city == null) {
            Journey j = journey();
            throw new TradeRefused(String.format(
                    "이동 중이다 — %s 까지 %s 남았다",
                    world.city(j.toId()).name(), j.remainingText(world.tick())));
        }
        return city;
    }

    // ── 사고팔기 ─────────────────────────────────────────

    public Receipt buy(String goodsId, double quantity) {
        City city = requireAtCity();

        double needed = quantity * data.goods(goodsId).volume();
        double free = caravan.freeSpace(data.goods());
        if (needed > free + 1e-9) {
            throw new TradeRefused(String.format(
                    "캐러밴에 자리가 없다: 남은 부피 %.1f, 실으려는 부피 %.1f (%s %s개)",
                    free, needed, data.goods(goodsId).name(), fmt(quantity)));
        }

        return exchange.buy(trader, caravan.cargo(), city, goodsId, quantity);
    }

    public Receipt sell(String goodsId, double quantity) {
        return exchange.sell(trader, caravan.cargo(), requireAtCity(), goodsId, quantity);
    }

    public Receipt quoteBuy(String goodsId, double quantity) {
        return exchange.quoteBuy(requireAtCity(), goodsId, quantity);
    }

    public Receipt quoteSell(String goodsId, double quantity) {
        return exchange.quoteSell(requireAtCity(), goodsId, quantity);
    }

    /** 남은 부피로 이 품목을 몇 개나 더 실을 수 있는가. */
    public double roomFor(String goodsId) {
        return caravan.freeSpace(data.goods()) / data.goods(goodsId).volume();
    }

    // ── 이동 ────────────────────────────────────────────

    /** 여기서 떠날 수 있는 노선들. */
    public List<RouteSpec> routesFromHere() {
        City city = here();
        if (city == null) {
            return List.of();
        }
        return travel.routesFrom(city.id());
    }

    /** 이 노선으로 가면 얼마나 걸리고 얼마가 드는지. 아무것도 바꾸지 않는다. */
    public Departure quoteDeparture(RouteSpec route) {
        return travel.quote(caravan, world.tick(), route);
    }

    /**
     * 떠난다. <b>되돌릴 수 없다.</b>
     *
     * <p>목적지만 주고 그 사이에 노선이 여럿이면 거절한다 — 어느 길로 갈지가
     * 이 게임의 핵심 결정이라 임의로 고르면 안 된다 (docs/03 4장).
     */
    public Departure departTo(String destinationCityId) {
        City city = requireAtCityForTravel();
        List<RouteSpec> options = travel.routesBetween(city.id(), destinationCityId);

        if (options.isEmpty()) {
            throw new TravelRefused(city.name() + " 에서 그리로 가는 길이 없다");
        }
        if (options.size() > 1) {
            throw new TravelRefused(String.format(
                    "%s 로 가는 길이 %d 개다 — 어느 길로 갈지 골라야 한다: %s",
                    world.city(destinationCityId).name(), options.size(),
                    String.join(", ", options.stream().map(RouteSpec::name).toList())));
        }
        return travel.depart(trader, caravan, world.tick(), options.get(0));
    }

    public Departure departVia(RouteSpec route) {
        requireAtCityForTravel();
        return travel.depart(trader, caravan, world.tick(), route);
    }

    public RouteSpec route(String routeIdOrName) {
        return travel.route(routeIdOrName);
    }

    private City requireAtCityForTravel() {
        City city = here();
        if (city == null) {
            Journey j = journey();
            throw new TravelRefused(String.format(
                    "이미 이동 중이다 — %s 까지 %s 남았다",
                    world.city(j.toId()).name(), j.remainingText(world.tick())));
        }
        return city;
    }

    // ── 자산 ────────────────────────────────────────────

    /**
     * 소지금 + 지금 도시에서 화물을 전부 팔았을 때의 금액.
     * 이동 중이면 화물 값은 못 매기므로 소지금만 센다.
     */
    public double netWorth() {
        City city = here();
        if (city == null) {
            return trader.gold();
        }
        double cargoValue = caravan.cargo().all().entrySet().stream()
                .mapToDouble(e -> exchange.quoteSell(city, e.getKey(), e.getValue()).net())
                .sum();
        return trader.gold() + cargoValue;
    }

    public double load() { return caravan.load(data.goods()); }
    public double loadRatio() { return caravan.loadRatio(data.goods()); }

    /** 실려 있는 것. 화물은 상인이 아니라 캐러밴 것이다. */
    public com.caravan.trade.Cargo cargo() { return caravan.cargo(); }

    public Trader trader() { return trader; }
    public Caravan caravan() { return caravan; }
    public World world() { return world; }
    public Exchange exchange() { return exchange; }

    private static String fmt(double v) {
        return String.format("%,.0f", v);
    }
}
