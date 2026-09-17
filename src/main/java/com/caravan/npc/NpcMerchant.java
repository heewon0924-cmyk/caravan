package com.caravan.npc;

import com.caravan.data.GoodsSpec;
import com.caravan.data.RouteSpec;
import com.caravan.data.TemperamentSpec;
import com.caravan.data.WorldData;
import com.caravan.trade.Exchange;
import com.caravan.trade.TradeRefused;
import com.caravan.trade.Trader;
import com.caravan.travel.Caravan;
import com.caravan.travel.Travel;
import com.caravan.travel.TravelRefused;
import com.caravan.world.City;
import com.caravan.world.Market;
import com.caravan.world.World;
import com.caravan.world.WorldClock;

import java.util.Map;

/**
 * NPC 상인 한 명.
 *
 * <p>플레이어가 없는 세계에서도 경제가 움직이게 하는 부품이다
 * (docs/09-이벤트와-NPC.md 2장). 장식이 아니라 <b>필수 부품</b>이다 —
 * 이들이 없으면 잉여가 무한정 쌓이고 부족한 도시는 영원히 부족하다.
 *
 * <p><b>플레이어와 똑같은 규칙으로 거래한다.</b> 같은 {@link Exchange} 를 부르고,
 * 적분 가격을 지불하고, 거래세를 내고, 이동 시간을 쓴다. 다른 경로로 거래하면
 * 세계가 거짓말이 되고, 플레이어가 "저 NPC 는 왜 저기로 가지?" 를 보고 시세를
 * 역추론할 수 없게 된다.
 *
 * <p>그러면서도 <b>플레이어보다 느려야 한다.</b> 셋으로 느리게 만든다.
 *
 * <ol>
 *   <li>기억에 의존한다 — 다른 도시 시세는 마지막으로 가봤을 때의 값이다</li>
 *   <li>작게 움직인다 — 캐러밴이 플레이어보다 작고, 한 시장의 일부만 산다</li>
 *   <li>도시마다 머문다 — 도착하자마자 되돌아 나가지 않는다</li>
 * </ol>
 */
public final class NpcMerchant {

    private final String id;
    private final String name;
    private final TemperamentSpec temperament;
    private final Trader trader;
    private final Caravan caravan;
    private final PriceMemory memory = new PriceMemory();
    private final long dwellTicks;

    /** 이 틱 전에는 다시 떠나지 않는다. */
    private long readyAt;

    /** 어디에 무엇을 얼마나 날랐는가. 밸런스를 볼 때 원인을 찾는 계측이다. */
    private final Map<String, Double> delivered = new java.util.LinkedHashMap<>();

    /** 왜 안 움직였는가. 후보가 하나도 기준을 못 넘긴 횟수. */
    private long idleTicks;

    public NpcMerchant(String id, String name, TemperamentSpec temperament,
                       double gold, double capacity, String startCityId, long dwellTicks) {
        this.id = id;
        this.name = name;
        this.temperament = temperament;
        this.trader = new Trader(id, name, gold);
        this.caravan = new Caravan(id + "-caravan", name + "의 짐수레", capacity, startCityId);
        this.dwellTicks = dwellTicks;
    }

    /**
     * 한 틱분의 판단과 행동.
     *
     * <p>도시에 있을 때만 움직인다. 이동 중에는 아무것도 하지 않는다 —
     * 도착 처리는 {@link Caravan} 이 알아서 한다.
     */
    public void act(long tick, World world, WorldData data, Exchange exchange, Travel travel) {
        this.world = world;
        String cityId = caravan.cityId(tick);
        if (cityId == null) {
            return;
        }

        City here = world.city(cityId);
        memory.observe(here, tick);

        // 싣고 온 게 있으면 판다. 여기까지 온 이유가 그것이다.
        if (!caravan.cargo().isEmpty()) {
            sellEverything(here, exchange);
            readyAt = Math.max(readyAt, tick + dwellTicks);
            return;
        }

        if (tick < readyAt) {
            return;
        }

        TradePlan plan = bestPlan(tick, world, data, exchange, travel, here);
        if (plan == null) {
            // 여기서 살 만한 게 없다. 그렇다고 눌러앉지는 않는다 —
            // 상인은 물건이 싼 곳으로 빈 수레를 끌고 간다.
            // 이게 없으면 잉여 도시에 NPC 가 고이고, 정작 물건이 있는 도시는 비어 있게 된다.
            if (!relocateEmpty(tick, data, travel, here)) {
                idleTicks += dwellTicks;
                readyAt = tick + dwellTicks;
            }
            return;
        }

        try {
            exchange.buy(trader, caravan.cargo(), here, plan.goodsId(), plan.quantity());
            travel.depart(trader, caravan, tick, plan.route());
        } catch (TradeRefused | TravelRefused e) {
            // 계산과 실제가 어긋났다(그 사이 누가 사갔다거나 돈이 모자란다거나).
            // 다음 기회를 본다 — 세계가 멈추지는 않는다.
            readyAt = tick + dwellTicks;
        }
    }

    /**
     * 빈 수레로 옮겨간다. 여기서 살 게 없을 때만.
     *
     * <p>어디로 갈지는 <b>기억하는 시세가 기준가 대비 가장 싼 도시</b>로 정한다.
     * 싼 도시가 곧 물건을 실을 수 있는 도시다. 가본 적 없는 도시는 중립으로 쳐서
     * 한 번은 가보게 한다.
     *
     * @return 떠났으면 true
     */
    private boolean relocateEmpty(long tick, WorldData data, Travel travel, City here) {
        RouteSpec best = null;
        double bestCheapness = Double.MAX_VALUE;

        for (RouteSpec route : travel.routesFrom(here.id())) {
            if (route.danger() > temperament.maxDanger()) {
                continue;
            }
            double cost = route.hours() * data.rules().travelCostPerHour() + route.toll();
            if (cost > trader.gold() * 0.2) {
                continue;
            }
            String to = route.otherEnd(here.id());

            double cheapness = Double.MAX_VALUE;
            for (GoodsSpec g : data.goodsInOrder()) {
                double remembered = memory.recall(to, g.id(), g.basePrice());
                cheapness = Math.min(cheapness, remembered / g.basePrice());
            }
            // 짧은 길을 살짝 선호한다 — 빈 수레로 멀리 갈 이유가 없다
            cheapness *= 1 + route.hours() * 0.05;

            if (cheapness < bestCheapness) {
                bestCheapness = cheapness;
                best = route;
            }
        }

        if (best == null) {
            return false;
        }
        try {
            travel.depart(trader, caravan, tick, best);
            return true;
        } catch (TradeRefused | TravelRefused e) {
            return false;
        }
    }

    private void sellEverything(City here, Exchange exchange) {
        for (Map.Entry<String, Double> e : Map.copyOf(caravan.cargo().all()).entrySet()) {
            try {
                exchange.sell(trader, caravan.cargo(), here, e.getKey(), e.getValue());
                delivered.merge(here.id() + "/" + e.getKey(), e.getValue(), Double::sum);
            } catch (TradeRefused ignored) {
                // 팔 수 없는 상황은 없어야 하지만, 있더라도 계속 굴러가게 둔다
            }
        }
    }

    /**
     * 여기서 떠날 수 있는 모든 길 × 모든 품목을 따져 가장 많이 남을 것 같은 하나를 고른다.
     *
     * <p>목적지 시세는 <b>기억</b>이다. 가본 적이 없으면 기준가로 친다.
     */
    private TradePlan bestPlan(long tick, World world, WorldData data,
                               Exchange exchange, Travel travel, City here) {
        TradePlan best = null;

        for (RouteSpec route : travel.routesFrom(here.id())) {
            if (route.danger() > temperament.maxDanger()) {
                continue;
            }
            String toId = route.otherEnd(here.id());
            double travelCost = route.hours() * data.rules().travelCostPerHour() + route.toll();

            for (GoodsSpec goods : data.goodsInOrder()) {
                TradePlan plan = evaluate(tick, data, exchange, here, goods,
                        route, toId, travelCost);
                if (plan != null && (best == null || plan.expectedProfit() > best.expectedProfit())) {
                    best = plan;
                }
            }
        }
        return best;
    }

    private World world;

    /**
     * 목적지 시장. <b>재고는 읽지 않는다</b> — 기준재고와 기준가와 탄력도는 누구나 아는
     * 그 도시의 규모지만, 지금 창고에 얼마가 있는지는 가봐야 안다.
     */
    private Market destinationMarket(String cityId, String goodsId) {
        return world.city(cityId).market(goodsId);
    }

    private TradePlan evaluate(long tick, WorldData data, Exchange exchange, City here,
                               GoodsSpec goods, RouteSpec route, String toId, double travelCost) {

        Market market = here.market(goods.id());
        double quantity = affordableQuantity(data, exchange, here, goods, market);
        if (quantity < 1) {
            return null;
        }

        double buyCost = exchange.quoteBuy(here, goods.id(), quantity).net();
        if (buyCost > trader.gold() - travelCost) {
            return null;
        }

        // 목적지에서 받을 값은 기억에 근거한 추정이다.
        // 기억하는 재고에 가격 곡선을 그대로 적용한다 — 값만 기억하고 재고를 모르면
        // "거기 900G 니까 200개면 18만" 으로 계산하는데, 그 도시 창고가 작으면
        // 다 팔기도 전에 값이 반토막 난다. 향신료를 아무 데나 쏟아붓던 원인이 이것이었다.
        Market destination = destinationMarket(toId, goods.id());
        double rememberedStock = memory.recallStock(toId, goods.id(), destination.refStock());
        double expectedRevenue = destination.curve().revenue(rememberedStock, quantity)
                * (1 - data.rules().tradeTaxRate()) * SELL_HAIRCUT;

        double profit = expectedRevenue - buyCost - travelCost;
        double margin = profit / buyCost;
        if (margin < temperament.minMargin()) {
            return null;
        }

        return new TradePlan(goods.id(), quantity, route, toId,
                buyCost, expectedRevenue, travelCost, profit, margin);
    }

    /** 캐러밴 자리 · 소지금 · 시장 재고 셋 중 가장 빡빡한 것에 맞춘다. */
    private double affordableQuantity(WorldData data, Exchange exchange, City here,
                                      GoodsSpec goods, Market market) {
        double bySpace = caravan.freeSpace(data.goods()) / goods.volume();
        double byStock = market.stock() * temperament.bite();
        double quantity = Math.floor(Math.min(bySpace, byStock));

        // 소지금에 맞을 때까지 줄인다. 적분 가격이라 단순 나눗셈으로는 안 된다.
        while (quantity >= 1 && exchange.quoteBuy(here, goods.id(), quantity).net() > trader.gold()) {
            quantity = Math.floor(quantity * 0.6);
        }
        return quantity;
    }

    /**
     * 기억이 묵은 만큼 깎아서 잡는 보수 계수.
     * 값이 내려가는 것 자체는 이제 가격 곡선으로 제대로 계산하므로 크게 깎지 않는다.
     */
    private static final double SELL_HAIRCUT = 0.95;

    /** "도시/품목" → 지금까지 그 도시에 날라다 판 총량. */
    public Map<String, Double> delivered() { return Map.copyOf(delivered); }

    public long idleTicks() { return idleTicks; }

    /** 진단용 — 이 상인이 기억하는 시세. */
    public double recall(String cityId, String goodsId, double fallback) {
        return memory.recall(cityId, goodsId, fallback);
    }

    public String id() { return id; }
    public String name() { return name; }
    public TemperamentSpec temperament() { return temperament; }
    public Trader trader() { return trader; }
    public Caravan caravan() { return caravan; }

    public String whereIs(long tick) {
        String cityId = caravan.cityId(tick);
        if (cityId != null) {
            return cityId;
        }
        return "→ " + caravan.journey(tick).toId()
                + " (" + caravan.journey(tick).remainingText(tick) + ")";
    }

    public String describe(long tick) {
        return String.format("%s(%s) %s · %,.0f G",
                name, temperament.name(), whereIs(tick), trader().gold());
    }

    static long defaultDwellTicks() {
        return 60 / WorldClock.MINUTES_PER_TICK;   // 세계 시간 1시간
    }
}
