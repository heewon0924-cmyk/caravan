package com.caravan.app;

import com.caravan.data.JobSpec;
import com.caravan.data.NamedSpec;
import com.caravan.data.RouteSpec;
import com.caravan.data.WorldData;
import com.caravan.event.EventEngine;
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
import com.caravan.people.Crew;
import com.caravan.people.HiringHall;
import com.caravan.people.Person;
import com.caravan.people.Prospect;
import com.caravan.people.Stats;
import com.caravan.people.Training;
import com.caravan.people.TransferOffice;
import com.caravan.people.TransferResult;
import com.caravan.rumor.Rumor;
import com.caravan.rumor.RumorBoard;
import com.caravan.rumor.RumorMill;
import com.caravan.rumor.Source;
import com.caravan.world.World;
import com.caravan.world.WorldClock;

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
    private final HiringHall hall;
    private final TransferOffice office;
    private EventEngine events;
    private final Crew crew;

    /** 진행 중인 전직. 한 번에 하나뿐이다 — 그래야 "지금 누구를" 이 결정이 된다. */
    private Training training;

    /** 막 끝난 전직 결과. 화면이 한 번 읽어가면 비운다. */
    private TransferResult lastResult;

    /** 마지막으로 머물던 도시. 도착을 알아채는 데 쓴다. */
    private String lastCityId;

    /** 이 도시에서 주점에 들렀는가. 방문당 한 번만 공짜다. */
    private boolean tavernUsedHere;

    /** 막 도착해서 귀에 들어온 소문들. 화면이 한 번 읽어가면 비운다. */
    private final List<Rumor> freshlyHeard = new ArrayList<>();

    public Company(World world, WorldData data, String name, String startCityId) {
        this(world, data, name, startCityId, null, null, null);
    }

    public Company(World world, WorldData data, String name, String startCityId,
                   RumorMill mill) {
        this(world, data, name, startCityId, mill, null, null);
    }

    /** 시험에서만 쓰는 강제 지정. 평소에는 인물이 채널을 정한다. */
    private Source override;

    public Company(World world, WorldData data, String name, String startCityId,
                   RumorMill mill, HiringHall hall, TransferOffice office) {
        this.world = world;
        this.data = data;
        this.trader = new Trader("player", name, data.rules().startingGold());
        this.caravan = new Caravan("caravan-1", name + "의 캐러밴",
                data.rules().caravanCapacity(), startCityId);
        this.exchange = new Exchange(data.rules());
        this.travel = new Travel(data);
        this.mill = mill;
        this.hall = hall;
        this.office = office;
        this.crew = new Crew(data.rules().crewSlots());
        this.lastCityId = startCityId;
    }

    // ── 사람 ────────────────────────────────────────────

    /** 이 도시에서 고용할 수 있는 사람들. */
    public List<Person> candidates() {
        City city = requireAtCity();
        return hall == null ? List.of() : hall.candidates(city.id());
    }

    /** 한 명을 데려간다. */
    public Person hire(int index) {
        City city = requireAtCity();
        if (hall == null) {
            throw new TradeRefused("이 세계에는 고용할 사람이 없다");
        }
        if (crew.isFull()) {
            throw new TradeRefused(String.format(
                    "캐러밴에 자리가 없다: %d / %d. 누군가를 내려야 한다",
                    crew.size(), crew.slots()));
        }
        trader.pay(data.rules().hireCost());
        Person hired = hall.hire(city.id(), index);
        crew.add(hired);
        return hired;
    }

    /** 내보낸다. 전직 중인 사람은 못 내보낸다. */
    public void dismiss(String personId) {
        Person person = crew.byId(personId);
        if (training != null && training.person() == person) {
            throw new TradeRefused(person.name() + " 은 지금 전직 중이다");
        }
        crew.remove(person);
    }

    public Crew crew() {
        syncArrival();
        return crew;
    }

    // ── 전직 ────────────────────────────────────────────

    /**
     * 이 도시에서 이 인물이 갈 수 있는 곳들과 그 근거.
     *
     * <p><b>합산된 최종 확률은 들어 있지 않다.</b> 플레이어가 스스로 계산한다
     * (docs/07 3장).
     */
    public List<Prospect> prospects(String personId, String aim, boolean tutor) {
        City city = requireAtCity();
        Person person = crew.byId(personId);
        requireTrainingGround(city);
        return office.prospects(person, data.city(city.id()), aim, tutor,
                eventJobBonuses());
    }

    /**
     * 전직을 시작한다. <b>도시에 머무는 동안에만 진행된다.</b>
     *
     * @param aim   노리는 직업 id. 교관 보정이 여기에만 붙는다. {@code null} 이면 아무거나
     * @param tutor 교관을 붙이는가. 비용이 더 들지만 원하는 쪽 가중치가 오른다
     */
    public Training beginTransfer(String personId, String aim, boolean tutor) {
        City city = requireAtCity();
        Person person = crew.byId(personId);
        requireTrainingGround(city);

        if (training != null) {
            throw new TradeRefused(
                    training.person().name() + " 이 이미 전직 중이다. 한 번에 한 명이다");
        }
        if (person.isAwakened()) {
            throw new TradeRefused(person.displayName() + " 은 더 갈 곳이 없다");
        }

        boolean awakening = person.jobSpec().isFinal() && person.jobSpec().canAwaken();
        if (person.jobSpec().isFinal() && !awakening) {
            throw new TradeRefused(person.displayName() + " 은 더 갈 곳이 없다");
        }

        int targetTier = awakening ? 4 : person.jobSpec().tier() + 1;
        double cost = awakening
                ? data.transfer().costAwaken()
                : data.transfer().costTo(targetTier);
        double hours = awakening
                ? data.transfer().hoursAwaken()
                : data.transfer().hoursTo(targetTier);
        if (tutor && !awakening) {
            cost *= 1 + data.transfer().tutorSurcharge();
        }

        trader.pay(cost);
        long ticks = Math.max(1, Math.round(hours * 60 / WorldClock.MINUTES_PER_TICK));
        training = new Training(person, city.id(), aim, tutor && !awakening,
                ticks, cost, world.tick());
        return training;
    }

    /** 진행 중인 전직. 없으면 {@code null}. */
    public Training training() {
        syncArrival();
        return training;
    }

    /** 막 끝난 전직 결과. 한 번 읽으면 비워진다. */
    public TransferResult takeTransferResult() {
        syncArrival();
        TransferResult result = lastResult;
        lastResult = null;
        return result;
    }

    /**
     * 이전 직업으로 되돌린다.
     *
     * <p>천장은 유지된다 — 되돌릴수록 원하는 결과가 가까워진다 (docs/07 6장).
     * 되돌릴 때마다 값이 오르므로 "새 사람을 구할 것인가" 와 저울질하게 된다.
     */
    public double revert(String personId) {
        requireAtCity();
        Person person = crew.byId(personId);
        if (training != null && training.person() == person) {
            throw new TradeRefused(person.name() + " 은 지금 전직 중이다");
        }
        if (!person.canRevert()) {
            throw new TradeRefused(person.displayName() + " 은 더 되돌아갈 곳이 없다");
        }

        double previousCost = person.revert();
        double fee = previousCost * data.transfer().revertCostRatio()
                * Math.pow(data.transfer().revertCostGrowth(), person.reverts() - 1);
        trader.pay(fee);
        return fee;
    }

    private void requireTrainingGround(City city) {
        if (office == null || !data.city(city.id()).hasTrainingGround()) {
            throw new TradeRefused(city.name() + " 에는 수련처가 없다");
        }
    }

    /** 진행 중인 사건이 계열에 주는 보너스. '해적 창궐' 이면 해양 계열이 유리해진다. */
    private java.util.Map<String, Double> eventJobBonuses() {
        return events == null ? java.util.Map.of() : events.jobFamilyBonuses();
    }

    /** {@code Simulation} 이 상단을 만들 때 걸어 준다. */
    public void observeEvents(EventEngine engine) {
        this.events = engine;
    }

    /** 도시에 머무는 동안만 전직이 진행된다. 이동 중에는 멈춘다. */
    private void accrueTraining() {
        caravan.setCapacityBonus(crew.total().grit() * data.rules().crewCapacityPerGrit());
        if (training == null) {
            return;
        }
        String cityId = caravan.cityId(world.tick());
        if (cityId != null && cityId.equals(training.cityId())) {
            training.resume(world.tick());
            training.accrue(world.tick());
        } else {
            training.pause(world.tick());
        }
        if (training.isDone(world.tick())) {
            lastResult = finish(training);
            training = null;
        }
    }

    private TransferResult finish(Training done) {
        Person person = done.person();
        String fromName = person.job().name();

        if (person.jobSpec().canAwaken()) {
            NamedSpec spec = data.namedCharacter(person.jobSpec().awakensTo().get(0));
            person.awaken(spec);
            return new TransferResult(person, fromName, spec.fullName(),
                    done.aim(), true, spec);
        }

        List<Prospect> prospects = office.prospects(person, data.city(done.cityId()),
                done.aim(), done.tutor(), eventJobBonuses());
        JobSpec drawn = office.draw(prospects);
        person.transitionTo(new com.caravan.people.Job(drawn), done.aim(), done.cost());

        boolean asAimed = done.aim() == null || done.aim().equals(drawn.id());
        return new TransferResult(person, fromName, drawn.name(), done.aim(), asAimed, null);
    }

    // ── 사람이 캐러밴에 주는 것 ───────────────────────────

    /** 상재가 높을수록 거래세가 깎인다. */
    public double effectiveTaxRate() {
        syncArrival();
        double relief = Math.min(data.rules().crewTaxReliefCap(),
                crew.total().commerce() * data.rules().crewTaxReliefPerCommerce());
        return data.rules().tradeTaxRate() * (1 - relief);
    }

    /** 인내가 높을수록 더 싣는다. */
    public double effectiveCapacity() {
        syncArrival();
        return caravan.capacity();
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
            accrueTraining();
            return;
        }
        accrueTraining();
        String cityId = caravan.cityId(world.tick());
        if (cityId == null || cityId.equals(lastCityId)) {
            return;
        }
        lastCityId = cityId;
        tavernUsedHere = false;
        for (Rumor r : mill.onArrival(world.city(cityId), informationChannel(), world.tick())) {
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
        this.override = source;
    }

    /**
     * 지금 이 상단의 정보 채널.
     *
     * <p><b>5단계가 남긴 답이 여기서 닫힌다</b> — 믿을 만한 정보는 돈으로 살 수 없고
     * 캐러밴 여섯 칸 중 하나로 산다 (docs/25 3장 (라)). 정보 역할을 가진 인물을
     * 태우면 채널이 올라가고, 그러면 호위나 상인을 한 명 포기해야 한다.
     * 정보의 값이 금액이 아니라 <b>자리</b>가 되므로 자본이 커져도 희석되지 않는다.
     */
    public Source informationChannel() {
        if (override != null) {
            return override;
        }
        return crew.withRole("정보").isEmpty() ? Source.떠도는말 : Source.정보원;
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

        return exchange.buy(trader, caravan.cargo(), city, goodsId, quantity, effectiveTaxRate());
    }

    public Receipt sell(String goodsId, double quantity) {
        return exchange.sell(trader, caravan.cargo(), requireAtCity(), goodsId, quantity,
                effectiveTaxRate());
    }

    public Receipt quoteBuy(String goodsId, double quantity) {
        return exchange.quoteBuy(requireAtCity(), goodsId, quantity, effectiveTaxRate());
    }

    public Receipt quoteSell(String goodsId, double quantity) {
        return exchange.quoteSell(requireAtCity(), goodsId, quantity, effectiveTaxRate());
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
        return leave(options.get(0));
    }

    public Departure departVia(RouteSpec route) {
        requireAtCityForTravel();
        return leave(route);
    }

    /**
     * 떠난다. 전직은 여기서 멈춘다.
     *
     * <p>떠날 때 명시적으로 멈추지 않으면, 돌아왔을 때 <b>이동한 시간까지 전직에
     * 쌓여 버린다</b> — 캐러밴은 도착 처리를 읽을 때 하므로 중간에 아무도 정산해
     * 주지 않기 때문이다.
     */
    private Departure leave(RouteSpec route) {
        Departure departure = travel.depart(trader, caravan, world.tick(), route);
        if (training != null) {
            training.pause(world.tick());
        }
        return departure;
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
                .mapToDouble(e -> exchange.quoteSell(city, e.getKey(), e.getValue(),
                        effectiveTaxRate()).net())
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
