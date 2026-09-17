package com.caravan.app;

import com.caravan.data.GoodsSpec;
import com.caravan.data.RouteSpec;
import com.caravan.data.WorldData;
import com.caravan.npc.PriceMemory;
import com.caravan.trade.TradeRefused;
import com.caravan.travel.TravelRefused;
import com.caravan.world.City;
import com.caravan.world.Market;

import java.util.Map;

/**
 * 규칙대로 움직이는 상단. <b>정보의 값어치를 재는 자다.</b>
 *
 * <p>docs/04-정보.md 1장이 세운 합격 기준은 하나다.
 *
 * <blockquote>소문을 듣고 움직인 플레이어가, 안 듣고 움직인 플레이어보다
 * 통계적으로 더 번다.</blockquote>
 *
 * <p>이걸 재려면 <b>소문 말고는 모든 것이 똑같은 두 상단</b>이 필요하다.
 * 같은 판단 규칙, 같은 자본, 같은 캐러밴, 같은 세계. 그래서 이 클래스를 만들었다 —
 * {@code useRumors} 하나만 다르게 두고 둘을 돌린다.
 *
 * <p>나중에 "자동 교역" 기능으로 쓸 수도 있지만 지금 목적은 계측이다.
 */
public final class ScriptedTrader {

    /** 한 시장에서 이 비율까지만 사들인다. 시장을 통째로 비우지 않는다. */
    private static final double BITE = 0.30;

    private final Company company;
    private final WorldData data;
    private final boolean useRumors;
    private final PriceMemory memory = new PriceMemory();

    public ScriptedTrader(Company company, WorldData data, boolean useRumors) {
        this.company = company;
        this.data = data;
        this.useRumors = useRumors;
    }

    /** 지금 할 수 있는 일을 한다. 이동 중이면 아무것도 안 한다. */
    public void step() {
        City here = company.here();
        if (here == null) {
            return;
        }
        long tick = company.world().tick();
        memory.observe(here, tick);

        // 도착하면 소문이 귀에 들어온다 (귀머거리 상단은 아무것도 안 들린다).
        // 다만 거저 들리는 건 떠도는 말이라 믿을 게 못 된다 —
        // 쓸 만한 정보는 돈을 주고 산다. 그게 값어치를 하는지가 5단계의 질문이다.
        company.takeFreshRumors();

        sellEverything(here);

        Plan best = bestPlan(here, tick);
        if (best == null) {
            return;
        }
        try {
            company.buy(best.goodsId, best.quantity);
            company.departVia(best.route);
        } catch (TradeRefused | TravelRefused ignored) {
            // 계산과 실제가 어긋났다. 다음 기회를 본다.
        }
    }

    /**
     * 정보상에게 한 번 묻는다.
     *
     * <p>자본에 견줘 정보값이 작을 때만 산다. 밑천이 얇으면 정보보다 화물이 먼저다.
     */
    private void buyInformationIfWorthIt() {
        double fee = data.rules().informantFee();
        if (company.trader().gold() < fee * 20) {
            return;
        }
        try {
            company.buyFromInformant();
        } catch (TradeRefused ignored) {
            // 못 사면 그만이다
        }
    }

    private void sellEverything(City here) {
        for (Map.Entry<String, Double> e : Map.copyOf(company.cargo().all()).entrySet()) {
            try {
                company.sell(e.getKey(), e.getValue());
            } catch (TradeRefused ignored) {
                // 팔 수 없는 상황은 없어야 하지만 계속 굴러가게 둔다
            }
        }
    }

    private Plan bestPlan(City here, long tick) {
        Plan best = null;

        for (RouteSpec route : company.routesFromHere()) {
            String toId = route.otherEnd(here.id());
            double travelCost = route.hours() * data.rules().travelCostPerHour() + route.toll();

            for (GoodsSpec goods : data.goodsInOrder()) {
                Plan plan = evaluate(here, goods, route, toId, travelCost, tick);
                if (plan != null && (best == null || plan.profit > best.profit)) {
                    best = plan;
                }
            }
        }
        return best;
    }

    private Plan evaluate(City here, GoodsSpec goods, RouteSpec route,
                          String toId, double travelCost, long tick) {

        Market market = here.market(goods.id());
        double quantity = Math.floor(Math.min(
                company.roomFor(goods.id()), market.stock() * BITE));
        if (quantity < 1) {
            return null;
        }

        double buyCost;
        try {
            buyCost = company.quoteBuy(goods.id(), quantity).net();
        } catch (TradeRefused e) {
            return null;
        }
        if (buyCost > company.trader().gold() - travelCost) {
            return null;
        }

        // 목적지에서 받을 값은 기억에 근거한 추정이다.
        // 기억하는 재고에 가격 곡선을 적용하고, 소문을 듣는다면 그만큼 보정한다.
        Market destination = company.world().city(toId).market(goods.id());
        double rememberedStock = memory.recallStock(toId, goods.id(), destination.refStock());
        double revenue = destination.curve().revenue(rememberedStock, quantity)
                * (1 - data.rules().tradeTaxRate());

        if (useRumors) {
            // 도착했을 때 그 일이 이미 값에 나타나 있는가로 따진다.
            // 내일 일어날 일을 오늘 알아도 세 시간 뒤에 팔면 소용이 없다.
            long arrivesAt = tick + travelTicks(route, quantity, goods);
            long memoryAge = memory.ageOf(toId, goods.id(), tick);
            revenue *= 1 + company.rumorBoard()
                    .expectedShiftAt(toId, goods.id(), tick, arrivesAt, memoryAge);
        }

        double profit = revenue - buyCost - travelCost;
        if (profit <= 0) {
            return null;
        }
        return new Plan(goods.id(), quantity, route, profit);
    }

    /** 이만큼 실으면 이 길을 몇 틱에 가는가. */
    private long travelTicks(RouteSpec route, double quantity, GoodsSpec goods) {
        double load = (company.load() + quantity * goods.volume())
                / company.caravan().capacity();
        double hours = route.hours() * (1 + data.rules().loadSlowdown() * Math.min(1, load));
        return Math.max(1, Math.round(hours * 60 / com.caravan.world.WorldClock.MINUTES_PER_TICK));
    }

    public Company company() {
        return company;
    }

    private record Plan(String goodsId, double quantity, RouteSpec route, double profit) {
    }
}
