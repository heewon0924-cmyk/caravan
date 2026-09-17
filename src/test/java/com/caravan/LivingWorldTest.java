package com.caravan;

import com.caravan.app.Simulation;
import com.caravan.data.WorldData;
import com.caravan.npc.NpcMerchant;
import com.caravan.trade.Cargo;
import com.caravan.trade.Exchange;
import com.caravan.trade.Receipt;
import com.caravan.trade.Trader;
import com.caravan.world.City;
import com.caravan.world.Market;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>4단계의 합격 기준</b> — docs/20-프로토타입-1차.md 4장, docs/23 6장.
 *
 * <blockquote>방치해도 시세가 의미 있게 움직인다.<br>
 * 그리고 <b>왕복 수익률이 5~15% 로 내려온다.</b></blockquote>
 *
 * <p>둘째가 이 단계의 진짜 목표다. 1~3단계 내내 수익률이 목표의 열일곱 배였고
 * (docs/22 3장), 그 때문에 노선 선택이 결정이 아니게 죽어 있었다 (docs/23 3장).
 * NPC 가 도시 간 가격차를 좁혀야 그 결정들이 제 무게를 갖는다.
 */
class LivingWorldTest {

    private final WorldData data = WorldData.load();

    private static final long[] SEEDS = {1, 7, 42, 99, 123, 777, 2024, 31337};

    @Test
    @DisplayName("14일 방치해도 재고와 가격이 유계로 남는다")
    void 세계가_발산하지_않는다() {
        for (long seed : SEEDS) {
            Simulation sim = new Simulation(data, seed);
            sim.advanceDays(14);

            for (City city : sim.world().cities()) {
                for (Market m : city.markets()) {
                    assertThat(m.stock())
                            .describedAs("시드 %d · %s / %s 재고", seed, city.name(), m.goods().name())
                            .isFinite()
                            .isBetween(0.0, m.granaryCap() * 1.3);
                    assertThat(m.spotPrice())
                            .describedAs("시드 %d · %s / %s 시세", seed, city.name(), m.goods().name())
                            .isFinite()
                            .isBetween(m.curve().floorPrice(), m.curve().ceilingPrice());
                }
            }
        }
    }

    @Test
    @DisplayName("NPC 가 없을 때와 달리, 어느 도시도 굶어 죽지 않는다")
    void 아무도_굶어_죽지_않는다() {
        Simulation sim = new Simulation(data, 42);
        sim.advanceDays(14);

        for (City city : sim.world().cities()) {
            for (Market m : city.markets()) {
                if (m.spec().consumptionPerDay() <= 0) {
                    continue;
                }
                // NPC 가 없으면 부족 품목이 사흘이면 전부 바닥났다 (docs/21 3장).
                // 지금은 못 채운 수요가 며칠치를 넘지 않아야 한다.
                double daysStarved = m.unmetDemand() / m.spec().consumptionPerDay();
                assertThat(daysStarved)
                        .describedAs("%s / %s 가 %.1f일치를 굶었다",
                                city.name(), m.goods().name(), daysStarved)
                        .isLessThan(4.0);
            }
        }
    }

    @Test
    @DisplayName("시세가 극단에 붙어 있지 않다 — 그래야 재고로 시세를 추정할 수 있다")
    void 시세가_극단에_붙지_않는다() {
        int extreme = 0;
        int total = 0;

        for (long seed : SEEDS) {
            Simulation sim = new Simulation(data, seed);
            sim.advanceDays(14);
            for (City city : sim.world().cities()) {
                for (Market m : city.markets()) {
                    total++;
                    boolean atCeiling = m.spotPrice() >= m.curve().ceilingPrice() - 1e-6;
                    boolean atFloor = m.spotPrice() <= m.curve().floorPrice() + 1e-6;
                    if (atCeiling || atFloor) {
                        extreme++;
                    }
                }
            }
        }
        // NPC 가 없을 때는 부족 품목 전부가 상한에 붙어 있었다.
        // 가운데가 있어야 "재고 4단계로 시세 ±20% 추정" 이 성립한다 (docs/02 8장).
        assertThat((double) extreme / total)
                .describedAs("시장 %d개 중 %d개가 상하한에 붙어 있다", total, extreme)
                .isLessThan(0.15);
    }

    @Test
    @DisplayName("방치해도 시세가 의미 있게 움직인다 — 죽은 시장이 아니다")
    void 시세가_계속_움직인다() {
        Simulation sim = new Simulation(data, 42);
        sim.advanceDays(6);

        Market wheat = sim.world().city("karden").market("wheat");
        List<Double> prices = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            sim.advanceHours(6);
            prices.add(wheat.spotPrice());
        }

        double min = prices.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
        double max = prices.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        assertThat(max / min)
                .describedAs("엿새 동안 카르덴 밀값이 %.0f~%.0f G 로만 움직였다", min, max)
                .isGreaterThan(1.10);
    }

    @Test
    @DisplayName("왕복 수익률의 중앙값이 목표 대역(5~15%) 안에 들어온다")
    void 수익률이_목표로_내려온다() {
        List<Double> margins = steadyStateMargins();
        double median = median(margins);

        assertThat(median)
                .describedAs("왕복 수익률 중앙값 %.1f%% (표본 %d개)", median * 100, margins.size())
                .isBetween(0.05, 0.15);
    }

    @Test
    @DisplayName("한 번 벌면 끝나는 판이 아니다 — 손해 보는 날도 있고 크게 버는 날도 있다")
    void 수익률에_폭이_있다() {
        List<Double> margins = steadyStateMargins();

        long losing = margins.stream().filter(m -> m < 0).count();
        long big = margins.stream().filter(m -> m > 0.30).count();

        // 늘 벌기만 하면 판단할 이유가 없고, 늘 잃으면 아무도 안 한다.
        assertThat((double) losing / margins.size())
                .describedAs("손해 보는 날이 %d / %d", losing, margins.size())
                .isBetween(0.01, 0.30);
        // 사건과 타이밍이 만드는 기회가 있어야 정보가 자원이 된다
        assertThat(big).describedAs("크게 버는 날이 하나도 없다").isPositive();
    }

    @Test
    @DisplayName("NPC 도 플레이어와 똑같은 규칙으로 거래한다 — 공짜로 물건이 생기지 않는다")
    void npc_도_같은_규칙을_쓴다() {
        Simulation sim = new Simulation(data, 42);
        double before = sim.npcs().totalGold();
        sim.advanceDays(14);

        // 거래세와 이동 비용을 물기 때문에 자본이 무한정 불어나지 않는다.
        // 그렇다고 전부 파산해서도 안 된다 — 그러면 세계가 멈춘다.
        double after = sim.npcs().totalGold();
        assertThat(after).isPositive();
        assertThat(after / before).isBetween(0.5, 20.0);

        for (NpcMerchant m : sim.npcs().merchants()) {
            assertThat(m.trader().gold())
                    .describedAs("%s 가 파산했다", m.name())
                    .isPositive();
        }
    }

    @Test
    @DisplayName("NPC 가 놀지 않는다 — 살 게 없으면 빈 수레로 옮겨간다")
    void npc_는_놀지_않는다() {
        Simulation sim = new Simulation(data, 42);
        sim.advanceDays(14);

        // 눌러앉기 시작하면 잉여 도시에 NPC 가 고이고 물건이 있는 도시는 비어버린다.
        // 4단계에서 이것 때문에 밀 충족률이 40% 에 머물렀다 (docs/24 3장).
        long idleHours = sim.npcs().totalIdleTicks() * 10 / 60;
        long merchantDays = (long) sim.npcs().merchants().size() * 14;
        assertThat(idleHours / (double) merchantDays)
                .describedAs("상인당 하루 평균 %.1f시간을 놀았다", idleHours / (double) merchantDays)
                .isLessThan(2.0);
    }

    // ── 도구 ──────────────────────────────────────────────

    /** 세계가 자리잡은 뒤(6일차~)의 왕복 수익률 표본. */
    private List<Double> steadyStateMargins() {
        List<Double> margins = new ArrayList<>();
        for (long seed : SEEDS) {
            for (int day = 6; day <= 14; day++) {
                margins.add(margin(seed, day));
            }
        }
        return margins;
    }

    /** 그 시점의 세계에서 하른에서 밀을 사 카르덴에 팔면 얼마가 남는가. */
    private double margin(long seed, int agedDays) {
        Simulation sim = new Simulation(data, seed);
        sim.advanceDays(agedDays);

        Exchange exchange = new Exchange(data.rules());
        City from = sim.world().city("harn");
        City to = sim.world().city("karden");

        double quantity = Math.min(60, Math.floor(from.market("wheat").stock()));
        if (quantity < 1) {
            return 0;
        }
        Trader trader = new Trader("probe", "관측",
                exchange.quoteBuy(from, "wheat", quantity).net());
        Cargo cargo = new Cargo();

        Receipt bought = exchange.buy(trader, cargo, from, "wheat", quantity);
        Receipt sold = exchange.sell(trader, cargo, to, "wheat", quantity);
        return (sold.net() - bought.net()) / bought.net();
    }

    private double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        return sorted.get(sorted.size() / 2);
    }
}
