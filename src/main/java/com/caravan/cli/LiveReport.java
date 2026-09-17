package com.caravan.cli;

import com.caravan.app.Simulation;
import com.caravan.data.WorldData;
import com.caravan.event.WorldEvent;
import com.caravan.npc.NpcMerchant;
import com.caravan.trade.Cargo;
import com.caravan.trade.Exchange;
import com.caravan.trade.Receipt;
import com.caravan.trade.Trader;
import com.caravan.world.City;
import com.caravan.world.Market;
import com.caravan.world.World;

import java.io.PrintStream;

/**
 * 살아 있는 세계를 굴리며 지켜본다. 4단계 검증용.
 *
 * <p>docs/09-이벤트와-NPC.md 4장이 재보라고 한 것들과,
 * docs/23 6장이 추가한 <b>"왕복 수익률이 5~15% 로 내려오는가"</b> 를 본다.
 */
public final class LiveReport {

    private final PrintStream out;

    public LiveReport(PrintStream out) {
        this.out = out;
    }

    public void run(WorldData data, long seed, int days, int every) {
        Simulation sim = new Simulation(data, seed);
        SimReport report = new SimReport(out);

        out.printf("세계를 %d일 굴린다. 시드 %d%n", days, seed);
        out.printf("NPC 상인 %d명 · 이벤트 %d종 · 하루 평균 %.1f건%n",
                sim.npcs().merchants().size(), data.events().size(), data.rules().eventsPerDay());
        out.printf("시작 자본 합계 %s G%n", Text.money(sim.npcs().totalGold()));

        for (int day = 1; day <= days; day++) {
            sim.advanceDays(1);
            if (day % every == 0 || day == days) {
                report.snapshot(sim.world());
                events(sim);
                npcs(sim);
            }
        }

        out.println();
        out.printf("  NPC 자본 합계 %s G%n", Text.money(sim.npcs().totalGold()));
        deliveries(sim, days);
        margins(data, seed, days);
    }

    /** 무엇이 실제로 어디로 날라졌는가. 밸런스가 틀렸을 때 원인을 찾는 자리. */
    public void deliveries(Simulation sim, int days) {
        out.println();
        out.println("  NPC 가 나른 것 (하루 평균)");
        out.printf("    %s %s %s %s%n",
                Text.padRight("도시/품목", 18), Text.padLeft("나른 양", 10),
                Text.padLeft("그 도시 소비", 12), Text.padLeft("충족률", 9));

        var deliveries = sim.npcs().deliveries();
        for (var city : sim.data().cities()) {
            for (var market : city.market()) {
                if (market.consumptionPerDay() <= 0) {
                    continue;
                }
                double total = deliveries.getOrDefault(city.id() + "/" + market.goods(), 0.0);
                double perDay = total / days;
                double need = market.netPerDay() < 0 ? -market.netPerDay() : 0;
                String rate = need > 0
                        ? String.format("%.0f%%", perDay / need * 100) : "—";
                out.printf("    %s %s %s %s%n",
                        Text.padRight(city.name() + "/" + goodsName(sim, market.goods()), 18),
                        Text.padLeft(String.format("%,.0f", perDay), 10),
                        Text.padLeft(String.format("%,.0f", need), 12),
                        Text.padLeft(rate, 9));
            }
        }
        out.printf("    쉰 시간 합계 %,d틱 (상인당 하루 평균 %.1f시간)%n",
                sim.npcs().totalIdleTicks(),
                sim.npcs().totalIdleTicks() * 10.0 / 60 / sim.npcs().merchants().size() / days);
    }

    private String goodsName(Simulation sim, String goodsId) {
        return sim.data().goods(goodsId).name();
    }

    private void events(Simulation sim) {
        if (!sim.activeEvents().isEmpty()) {
            out.println();
            out.println("  진행 중인 사건");
            for (WorldEvent e : sim.activeEvents()) {
                out.printf("    %s — %s   (%s)%n",
                        Text.padRight(e.spec().name(), 10), e.headline(), e.period());
            }
        }
        if (!sim.forecasts().isEmpty()) {
            out.println();
            out.println("  예고된 사건");
            for (WorldEvent e : sim.forecasts()) {
                out.printf("    %s — %s   (%s 시작)%n",
                        Text.padRight(e.spec().name(), 10), e.headline(),
                        com.caravan.world.WorldClock.format(e.startTick()));
            }
        }
    }

    private void npcs(Simulation sim) {
        out.println();
        out.println("  NPC 상인");
        for (NpcMerchant m : sim.npcs().merchants()) {
            out.printf("    %s%n", m.describe(sim.tick()));
        }
    }

    /**
     * 날짜별 왕복 수익률. <b>4단계의 합격 기준</b>이다.
     *
     * <p>NPC 가 도시 간 가격차를 좁히면 이 숫자가 목표(5~15%)로 내려와야 한다.
     * 안 내려오면 NPC 처리량이 모자라거나 기준재고가 너무 얕은 것이다.
     */
    public void margins(WorldData data, long seed, int days) {
        out.println();
        out.println("══ 왕복 수익률 — 밀 60개, 하른 → 카르덴 ══");
        out.println("   (목표 5~15% · docs/02 8장)");
        out.println();
        out.printf("    %s %s %s%n",
                Text.padLeft("일차", 6), Text.padLeft("수익률", 10), "  판정");

        for (int day = 0; day <= days; day++) {
            double margin = margin(data, seed, day, 60);
            out.printf("    %s %s   %s%n",
                    Text.padLeft(day + "일", 6),
                    Text.padLeft(String.format("%+.1f%%", margin * 100), 10),
                    verdict(margin));
        }
    }

    private String verdict(double margin) {
        if (margin < -0.02) return "손해";
        if (margin < 0.05) return "얇다";
        if (margin <= 0.15) return "★ 목표 안";
        if (margin <= 0.40) return "높다";
        return "한참 높다";
    }

    /** 그 시점의 세계에서 밀을 사서 옮겨 팔면 얼마가 남는가. */
    public double margin(WorldData data, long seed, int agedDays, double quantity) {
        Simulation sim = new Simulation(data, seed);
        if (agedDays > 0) {
            sim.advanceDays(agedDays);
        }
        World world = sim.world();
        Exchange exchange = new Exchange(data.rules());
        City from = world.city("harn");
        City to = world.city("karden");

        double available = Math.min(quantity, Math.floor(from.market("wheat").stock()));
        if (available < 1) {
            return 0;
        }

        Trader trader = new Trader("probe", "관측",
                exchange.quoteBuy(from, "wheat", available).net());
        Cargo cargo = new Cargo();

        Receipt bought = exchange.buy(trader, cargo, from, "wheat", available);
        Receipt sold = exchange.sell(trader, cargo, to, "wheat", available);

        return (sold.net() - bought.net()) / bought.net();
    }

    /** 한 도시의 재고가 얼마나 극단에 붙어 있는지. 시장이 읽을 만한지 보는 지표. */
    public void extremes(Simulation sim) {
        int atCeiling = 0;
        int atFloor = 0;
        int total = 0;
        for (City city : sim.world().cities()) {
            for (Market m : city.markets()) {
                total++;
                if (m.spotPrice() >= m.curve().ceilingPrice() - 1e-6) atCeiling++;
                if (m.spotPrice() <= m.curve().floorPrice() + 1e-6) atFloor++;
            }
        }
        out.printf("  시세가 극단에 붙은 시장 %d / %d  (상한 %d · 하한 %d)%n",
                atCeiling + atFloor, total, atCeiling, atFloor);
    }
}
