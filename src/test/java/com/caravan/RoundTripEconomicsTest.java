package com.caravan;

import com.caravan.data.WorldData;
import com.caravan.trade.Cargo;
import com.caravan.trade.Exchange;
import com.caravan.trade.Receipt;
import com.caravan.trade.Trader;
import com.caravan.world.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 왕복 한 번이 얼마를 남기는가.
 *
 * <p>docs/02-교역과-가격.md 8장이 재보라고 한 것 중 3번 —
 * <b>왕복 한 바퀴의 수익률이 자본의 5~15% 안에 들어오는가.</b>
 *
 * <p>지금은 <b>안 들어온다.</b> 평형에서는 손해고, 이틀만 지나면 +170% 가 넘는다.
 * NPC 상인(4단계)이 도시 간 가격차를 좁히기 전이라 그렇다. 이 테스트는 그 상태를
 * 숫자로 못 박아 둔다 — <b>4단계 NPC 밸런싱의 측정 기준</b>이 된다.
 * NPC 를 넣고 나서 이 숫자가 얼마나 내려오는지를 본다.
 */
class RoundTripEconomicsTest {

    /** 하른에서 사서 카르덴에 판다. 이동 비용과 시간은 아직 없다(3단계). */
    private double margin(int agedDays, double quantity) {
        WorldData data = WorldData.load();
        World world = new World(data);
        if (agedDays > 0) {
            world.advanceDays(agedDays);
        }
        Exchange exchange = new Exchange(data.rules());

        // 자본은 견적만큼만 준다. 아주 큰 수를 주면 double 정밀도에 손익이 묻힌다.
        Trader trader = new Trader("t", "시험",
                exchange.quoteBuy(world.city("harn"), "wheat", quantity).net());

        Cargo cargo = new Cargo();
        Receipt bought = exchange.buy(trader, cargo, world.city("harn"), "wheat", quantity);
        Receipt sold = exchange.sell(trader, cargo, world.city("karden"), "wheat", quantity);

        return (sold.net() - bought.net()) / bought.net();
    }

    @Test
    @DisplayName("평형 상태에서는 왕복이 손해다 — 공짜 차익이 없다")
    void 평형에서는_왕복이_손해다() {
        // 두 도시 모두 기준가인 0일차. 거래세 6% + 시세 밀림만큼 잃는다.
        // 이게 성립해야 "움직일 가치가 있는 차익" 의 하한선이 생긴다.
        assertThat(margin(0, 20)).isNegative();
        assertThat(margin(0, 300)).isNegative();
    }

    @Test
    @DisplayName("많이 실을수록 수익률이 낮아진다 — 적재량이 진짜 제약이다")
    void 많이_실을수록_수익률이_낮아진다() {
        double small = margin(3, 20);
        double medium = margin(3, 100);
        double large = margin(3, 300);

        assertThat(small).isGreaterThan(medium);
        assertThat(medium).isGreaterThan(large);
    }

    @Test
    @DisplayName("NPC 가 없으면 수익률이 목표(5~15%)를 한참 넘는다 — 4단계가 풀 문제")
    void 아직은_수익률이_목표를_한참_넘는다() {
        // 카르덴 밀이 이틀이면 바닥나 시세 상한(기준가 ×3)에 붙고,
        // 하른 밀은 쌓여서 기준가의 절반 근처로 내려간다. 5배 넘는 가격차가 벌어진다.
        double target = 0.15;

        assertThat(margin(2, 300)).isGreaterThan(target * 5);
        assertThat(margin(7, 300)).isGreaterThan(target * 5);

        // 4단계에서 NPC 를 넣고 나면 이 값이 목표 근처로 내려와야 한다.
        // 안 내려오면 NPC 처리량이 모자라거나 기준재고가 너무 얕은 것이다.
    }

    @Test
    @DisplayName("거래 규모를 줄여도 목표에 못 들어온다 — 규모가 아니라 가격차가 원인이다")
    void 규모를_줄여도_해결되지_않는다() {
        // 20개만 실어도 +250% 가 넘는다. 캐러밴 크기 문제가 아니라
        // 도시 간 가격차 자체가 비정상이라는 뜻이다.
        assertThat(margin(2, 20)).isGreaterThan(1.0);
    }
}
