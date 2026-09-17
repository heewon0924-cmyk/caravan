package com.caravan;

import com.caravan.data.GoodsSpec;
import com.caravan.data.WorldData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * 밸런스 데이터가 문서와 어긋나지 않는지 본다.
 *
 * <p>숫자를 바꾸면 이 테스트가 깨진다 — 그게 목적이다. 문서와 데이터가 조용히
 * 갈라지면 나중에 어느 쪽이 맞는지 알 수 없게 된다. 숫자를 바꿀 때는 여기와
 * docs/02-교역과-가격.md 를 같이 고친다.
 */
class WorldDataTest {

    private final WorldData data = WorldData.load();

    @Test
    @DisplayName("프로토타입은 도시 3개, 품목 5개다")
    void 프로토타입_규모() {
        assertThat(data.cities()).hasSize(3);
        assertThat(data.goodsInOrder()).hasSize(5);
        assertThat(data.cities()).extracting("id")
                .containsExactly("harn", "karden", "selia");
    }

    @Test
    @DisplayName("기준가와 탄력도가 문서 02 4장의 표와 같다")
    void 기준가와_탄력도() {
        assertGoods("wheat", "밀", 100, 0.6, 1.0);
        assertGoods("salt", "소금", 140, 0.6, 0.8);
        assertGoods("cloth", "옷감", 320, 0.7, 0.5);
        assertGoods("iron", "철", 400, 0.7, 1.2);
        assertGoods("spice", "향신료", 900, 1.2, 0.2);
    }

    @Test
    @DisplayName("향신료의 탄력도만 1을 넘는다 — 정보가 돈이 되는 품목")
    void 향신료만_크게_튄다() {
        for (GoodsSpec g : data.goodsInOrder()) {
            if (g.id().equals("spice")) {
                assertThat(g.elasticity()).isGreaterThan(1.0);
            } else {
                assertThat(g.elasticity()).isLessThan(1.0);
            }
        }
    }

    @Test
    @DisplayName("부피당 가치가 품목의 성격을 만든다 — 밀 100, 향신료 4500")
    void 부피당_가치가_성격이다() {
        assertThat(data.goods("wheat").valuePerVolume()).isCloseTo(100, within(1.0));
        assertThat(data.goods("spice").valuePerVolume()).isCloseTo(4500, within(1.0));
        assertThat(data.goods("spice").valuePerVolume())
                .isGreaterThan(data.goods("wheat").valuePerVolume() * 40);
    }

    @Test
    @DisplayName("세 도시 모두 혼자서는 굶는다 — 누군가 실어 날라야 한다")
    void 어느_도시도_자급자족하지_못한다() {
        for (var city : data.cities()) {
            boolean anyShortage = city.market().stream().anyMatch(m -> m.netPerDay() < 0);
            assertThat(anyShortage)
                    .describedAs("%s 는 모자란 품목이 하나도 없다", city.name())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("하른은 밀이 남고 카르덴은 철이 남는다")
    void 도시마다_남는_것이_다르다() {
        assertThat(net("harn", "wheat")).isPositive();
        assertThat(net("harn", "iron")).isNegative();
        assertThat(net("karden", "iron")).isPositive();
        assertThat(net("karden", "wheat")).isNegative();
        assertThat(net("selia", "spice")).isPositive();
        assertThat(net("selia", "wheat")).isNegative();
    }

    @Test
    @DisplayName("데이터가 어긋나면 읽는 시점에 터진다")
    void 잘못된_데이터는_바로_터진다() {
        assertThatThrownBy(() -> WorldData.load("data/goods.yml", "data/nope.yml", "data/world.yml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("못 찾았다");
    }

    @Test
    @DisplayName("시간 배율은 데이터로 바꾼다 — 코드에 현실 시간이 박히면 안 된다")
    void 시간_배율은_데이터다() {
        assertThat(data.rules().speed()).isEqualTo(6.0);
        assertThat(data.rules().granaryCapMultiple()).isEqualTo(3.0);
        assertThat(data.rules().priceFloorMultiple()).isEqualTo(0.35);
        assertThat(data.rules().priceCeilingMultiple()).isEqualTo(3.0);
    }

    private double net(String cityId, String goodsId) {
        return data.cities().stream()
                .filter(c -> c.id().equals(cityId)).findFirst().orElseThrow()
                .market().stream()
                .filter(m -> m.goods().equals(goodsId)).findFirst().orElseThrow()
                .netPerDay();
    }

    private void assertGoods(String id, String name, double base, double elasticity, double volume) {
        GoodsSpec g = data.goods(id);
        assertThat(g.name()).isEqualTo(name);
        assertThat(g.basePrice()).isEqualTo(base);
        assertThat(g.elasticity()).isEqualTo(elasticity);
        assertThat(g.volume()).isEqualTo(volume);
    }
}
