package com.caravan;

import com.caravan.data.WorldData;
import com.caravan.world.City;
import com.caravan.world.Market;
import com.caravan.world.World;
import com.caravan.world.WorldClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * <b>1단계의 합격 기준</b> — docs/20-프로토타입-1차.md 4장.
 *
 * <blockquote>아무도 안 건드려도 7일간 경제가 발산하지 않는다.</blockquote>
 *
 * <p>여기서 "발산하지 않는다"는 <b>유계</b>라는 뜻이지 <b>균형</b>이라는 뜻이 아니다.
 * NPC 상인이 4단계에 들어오기 전까지는 하른의 밀이 곳간을 넘치고 카르덴은 굶는다.
 * 그건 설계대로다 — 세 도시 모두 혼자서는 굶게 만들어 둔 것이고, 그게 플레이어가
 * 있어야 할 이유다. 이 테스트는 그 상태에서도 숫자가 깨지지 않는지만 본다.
 */
class EconomyStabilityTest {

    @Test
    @DisplayName("7일간 아무도 거래하지 않아도 재고와 가격이 유계로 남는다")
    void 칠일간_발산하지_않는다() {
        World world = new World(WorldData.load());
        world.advanceDays(7);
        assertBounded(world);
    }

    @Test
    @DisplayName("100일을 굴려도 마찬가지다 — 오래 방치해도 안 터진다")
    void 백일을_굴려도_유계다() {
        World world = new World(WorldData.load());
        world.advanceDays(100);
        assertBounded(world);
    }

    @Test
    @DisplayName("굴리는 내내 매 틱 유계다 — 중간에 잠깐 튀었다가 돌아오는 것도 잡는다")
    void 매_틱_유계다() {
        World world = new World(WorldData.load());
        for (int day = 0; day < 7; day++) {
            for (int tick = 0; tick < WorldClock.TICKS_PER_DAY; tick++) {
                world.advanceTo(world.tick() + 1);
                assertBounded(world);
            }
        }
    }

    @Test
    @DisplayName("NPC 가 없으면 잉여 도시는 넘치고 부족 도시는 굶는다 — 4단계가 풀 문제")
    void 아직은_아무도_나르지_않는다() {
        World world = new World(WorldData.load());
        world.advanceDays(7);

        Market harnWheat = world.city("harn").market("wheat");
        Market kardenWheat = world.city("karden").market("wheat");

        // 하른은 밀이 남아돈다 → 하루 +660 씩 불어나 6일차에 곳간 상한(6000)을 넘고,
        // 그 뒤로는 넘친 만큼 상해서 상한 언저리에 멈춘다.
        assertThat(harnWheat.stock()).isGreaterThan(harnWheat.granaryCap());
        assertThat(harnWheat.spotPrice()).isLessThan(harnWheat.goods().basePrice() * 0.6);

        // 카르덴은 밀이 없다 → 재고 0, 시세 상한
        assertThat(kardenWheat.stock()).isZero();
        assertThat(kardenWheat.spotPrice()).isEqualTo(kardenWheat.curve().ceilingPrice());
        // 기준재고 600 이 이틀 만에 바닥나고, 남은 닷새를 통째로 굶는다 → 5 × 300 = 1500
        assertThat(kardenWheat.unmetDemand()).isCloseTo(1500, within(1.0));

        // 그래서 하른에서 사서 카르덴에 파는 차익이 실제로 존재한다.
        // 이 차익이 0이면 플레이어가 할 일이 없다는 뜻이라 1단계가 실패한 것이다.
        assertThat(kardenWheat.spotPrice() / harnWheat.spotPrice()).isGreaterThan(2.0);
    }

    @Test
    @DisplayName("7일 뒤에도 어느 도시든 모든 품목을 사고팔 수 있다")
    void 시장이_사라지지_않는다() {
        World world = new World(WorldData.load());
        WorldData data = WorldData.load();
        world.advanceDays(7);

        for (City city : world.cities()) {
            for (var goods : data.goodsInOrder()) {
                Market m = city.market(goods.id());
                assertThat(m.spotPrice())
                        .describedAs("%s / %s 시세", city.name(), goods.name())
                        .isFinite()
                        .isPositive();
            }
        }
    }

    private void assertBounded(World world) {
        double spoilRate = world.rules().spoilRatePerTick();

        for (City city : world.cities()) {
            for (Market m : city.markets()) {
                String where = city.name() + " / " + m.goods().name();

                // 재고는 0 이상, 곳간 상한 + 평형 초과분 안쪽
                double netPerTick = Math.max(0, m.spec().netPerDay()) / WorldClock.TICKS_PER_DAY;
                double ceiling = m.granaryCap() + netPerTick / spoilRate + 1;

                assertThat(m.stock())
                        .describedAs("%s 재고 (틱 %d)", where, world.tick())
                        .isFinite()
                        .isBetween(0.0, ceiling);

                // 시세는 언제나 상하한 안쪽
                assertThat(m.spotPrice())
                        .describedAs("%s 시세 (틱 %d)", where, world.tick())
                        .isFinite()
                        .isBetween(m.curve().floorPrice(), m.curve().ceilingPrice());
            }
        }
    }
}
