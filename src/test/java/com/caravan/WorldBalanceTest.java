package com.caravan;

import com.caravan.data.CitySpec;
import com.caravan.data.GoodsSpec;
import com.caravan.data.MarketSpec;
import com.caravan.data.WorldData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세계가 자기를 먹여살릴 수 있는가.
 *
 * <p><b>세계 전체 생산이 세계 전체 소비보다 적으면 누가 아무리 잘 날라도 어느 도시는
 * 반드시 굶는다.</b> 그건 NPC 로도 플레이어로도 풀 수 없는 문제다 — 교역은 있는 것을
 * 옮길 뿐 없는 것을 만들지 않는다.
 *
 * <p>4단계를 시작할 때 밀이 하루 80, 소금이 하루 50 모자란 상태였다 (docs/24 2장).
 * 도시별로 보면 "하른은 밀이 남고 카르덴은 모자란다" 라 정상처럼 보였는데,
 * 세계 전체로 더해보니 애초에 모자랐다. 눈으로는 안 보이는 종류의 오류라
 * 매 빌드마다 검사한다.
 */
class WorldBalanceTest {

    private final WorldData data = WorldData.load();

    @Test
    @DisplayName("세계 전체 생산이 세계 전체 소비보다 많다 — 모든 품목이")
    void 세계는_자기를_먹여살린다() {
        for (GoodsSpec goods : data.goodsInOrder()) {
            double production = sum(goods.id(), MarketSpec::productionPerDay);
            double consumption = sum(goods.id(), MarketSpec::consumptionPerDay);

            assertThat(production)
                    .describedAs("%s — 세계 생산 %,.0f / 세계 소비 %,.0f. "
                                    + "부족하면 누가 날라도 어느 도시는 굶는다",
                            goods.name(), production, consumption)
                    .isGreaterThan(consumption);
        }
    }

    @Test
    @DisplayName("남는 양이 지나치지도 않다 — 곳간만 채우고 썩는 품목이 없게")
    void 남는_양이_지나치지_않다() {
        // 여유가 너무 크면 남는 게 산지에 고여 산지 시세가 계속 내려간다.
        // 그러면 교역 수익률이 시간이 갈수록 벌어져 밸런스가 잡히지 않는다 —
        // 4단계에서 밀 여유가 하루 +160(20%)일 때 열흘 만에 하른 밀이
        // 87G 에서 69G 로 내려갔고 왕복 수익률이 +15% 에서 +49% 로 벌어졌다.
        // (docs/24 4장)
        for (GoodsSpec goods : data.goodsInOrder()) {
            double production = sum(goods.id(), MarketSpec::productionPerDay);
            double consumption = sum(goods.id(), MarketSpec::consumptionPerDay);
            double surplusRatio = (production - consumption) / consumption;

            assertThat(surplusRatio)
                    .describedAs("%s — 세계 잉여가 소비의 %.0f%% 다", goods.name(), surplusRatio * 100)
                    .isLessThan(0.30);
        }
    }

    @Test
    @DisplayName("품목마다 남는 도시와 모자란 도시가 둘 다 있다 — 그래야 나를 이유가 있다")
    void 모든_품목에_교역할_이유가_있다() {
        for (GoodsSpec goods : data.goodsInOrder()) {
            Map<String, Double> net = new LinkedHashMap<>();
            for (CitySpec city : data.cities()) {
                net.put(city.name(), city.market().stream()
                        .filter(m -> m.goods().equals(goods.id()))
                        .mapToDouble(MarketSpec::netPerDay).sum());
            }

            assertThat(net.values()).describedAs("%s 가 남는 도시가 없다", goods.name())
                    .anyMatch(v -> v > 0);
            assertThat(net.values()).describedAs("%s 가 모자란 도시가 없다", goods.name())
                    .anyMatch(v -> v < 0);
        }
    }

    private double sum(String goodsId, java.util.function.ToDoubleFunction<MarketSpec> field) {
        return data.cities().stream()
                .flatMap(c -> c.market().stream())
                .filter(m -> m.goods().equals(goodsId))
                .mapToDouble(field)
                .sum();
    }
}
