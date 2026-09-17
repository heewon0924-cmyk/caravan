package com.caravan;

import com.caravan.data.WorldData;
import com.caravan.world.Market;
import com.caravan.world.World;
import com.caravan.world.WorldClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 틱이 재고를 어떻게 움직이는지 못 박는다. docs/02-교역과-가격.md 4장. */
class MarketTickTest {

    private World world() {
        return new World(WorldData.load());
    }

    @Test
    @DisplayName("하루 지나면 생산량만큼 늘고 소비량만큼 준다")
    void 하루치_생산과_소비가_적용된다() {
        World world = world();
        Market wheat = world.city("harn").market("wheat");

        double before = wheat.stock();
        world.advanceDays(1);

        // 하른 밀: 생산 900 - 소비 240 = 하루 +660
        assertThat(wheat.stock()).isCloseTo(before + 660, within(0.5));
    }

    @Test
    @DisplayName("재고가 없으면 소비하지 못하고, 못 채운 수요가 쌓인다")
    void 재고가_바닥나면_미충족_수요가_쌓인다() {
        World world = world();
        // 카르덴 밀: 생산 0, 소비 300/일, 기준재고 600 → 이틀이면 바닥난다
        Market wheat = world.city("karden").market("wheat");

        world.advanceDays(3);

        assertThat(wheat.stock()).isZero();
        assertThat(wheat.unmetDemand()).isPositive();
    }

    @Test
    @DisplayName("재고는 절대 음수가 되지 않는다 — 음수가 되면 가격 수식이 통째로 무너진다")
    void 재고는_음수가_되지_않는다() {
        World world = world();
        world.advanceDays(30);

        for (var city : world.cities()) {
            for (Market m : city.markets()) {
                assertThat(m.stock())
                        .describedAs("%s / %s", city.name(), m.goods().name())
                        .isGreaterThanOrEqualTo(0.0);
            }
        }
    }

    @Test
    @DisplayName("잉여는 무한정 쌓이지 않는다 — 곳간을 넘치면 상해서 버린다")
    void 잉여는_곳간_상한_근처에서_멈춘다() {
        World world = world();
        Market wheat = world.city("harn").market("wheat");

        world.advanceDays(60);

        // 매 틱 들어오는 순증분과 상해서 나가는 분량이 맞물리는 지점에서 멈춘다
        double netPerTick = 660.0 / WorldClock.TICKS_PER_DAY;
        double equilibrium = wheat.granaryCap() + netPerTick / world.rules().spoilRatePerTick();

        assertThat(wheat.stock()).isLessThanOrEqualTo(equilibrium + 1);
        assertThat(wheat.stock()).isGreaterThan(wheat.granaryCap());
    }

    @Test
    @DisplayName("재고 단계는 정확한 숫자 대신 4단계로만 보인다")
    void 재고는_네_단계로_보인다() {
        World world = world();
        Market wheat = world.city("harn").market("wheat");

        assertThat(wheat.level()).isEqualTo(Market.StockLevel.보통);

        world.advanceDays(10);
        assertThat(wheat.level()).isEqualTo(Market.StockLevel.많음);

        Market kardenWheat = world.city("karden").market("wheat");
        assertThat(kardenWheat.level()).isEqualTo(Market.StockLevel.희귀);
    }

    @Test
    @DisplayName("이미 지나간 틱으로는 되돌아가지 않는다")
    void 시간은_거꾸로_가지_않는다() {
        World world = world();
        world.advanceDays(3);
        long tick = world.tick();
        double stock = world.city("harn").market("wheat").stock();

        world.advanceTo(tick - 100);

        assertThat(world.tick()).isEqualTo(tick);
        assertThat(world.city("harn").market("wheat").stock()).isEqualTo(stock);
    }

    @Test
    @DisplayName("한 번에 따라잡든 나눠서 따라잡든 결과가 같다")
    void 몰아서_계산해도_결과가_같다() {
        World once = world();
        once.advanceDays(7);

        World stepwise = world();
        for (int i = 0; i < 7; i++) {
            stepwise.advanceDays(1);
        }

        for (var city : once.cities()) {
            for (Market m : city.markets()) {
                double other = stepwise.city(city.id()).market(m.goods().id()).stock();
                assertThat(m.stock())
                        .describedAs("%s / %s", city.name(), m.goods().name())
                        .isCloseTo(other, within(1e-9));
            }
        }
    }
}
