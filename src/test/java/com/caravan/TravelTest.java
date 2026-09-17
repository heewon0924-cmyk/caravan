package com.caravan;

import com.caravan.data.RouteSpec;
import com.caravan.data.WorldData;
import com.caravan.trade.TradeRefused;
import com.caravan.trade.Trader;
import com.caravan.travel.Caravan;
import com.caravan.travel.Departure;
import com.caravan.travel.Travel;
import com.caravan.travel.TravelRefused;
import com.caravan.world.World;
import com.caravan.world.WorldClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** 3단계 — 캐러밴 이동. docs/03-이동과-시간.md. */
class TravelTest {

    private WorldData data;
    private World world;
    private Travel travel;
    private Trader trader;
    private Caravan caravan;

    @BeforeEach
    void setUp() {
        data = WorldData.load();
        world = new World(data);
        travel = new Travel(data);
        trader = new Trader("t", "시험 상단", 100_000);
        caravan = new Caravan("c", "시험 캐러밴", data.rules().caravanCapacity(), "harn");
    }

    private RouteSpec route(String name) {
        return travel.route(name);
    }

    @Nested
    @DisplayName("소요 시간")
    class Duration {

        @Test
        @DisplayName("빈 캐러밴은 노선 기본 시간대로 간다")
        void 빈_캐러밴은_기본_시간() {
            Departure d = travel.quote(caravan, 0, route("산기슭길"));

            assertThat(d.baseHours()).isEqualTo(2.0);
            assertThat(d.actualHours()).isCloseTo(2.0, within(1e-9));
            assertThat(d.loadRatio()).isZero();
        }

        @Test
        @DisplayName("가득 실으면 40% 더 걸린다 — 많이 싣는 게 항상 이득이 아니다")
        void 가득_실으면_느려진다() {
            caravan.cargo().add("wheat", 60);   // 부피 1.0 × 60 = 용량 60 을 꽉 채운다

            Departure d = travel.quote(caravan, 0, route("산기슭길"));

            assertThat(d.loadRatio()).isCloseTo(1.0, within(1e-9));
            assertThat(d.actualHours()).isCloseTo(2.8, within(1e-9));
            assertThat(d.slowdownHours()).isCloseTo(0.8, within(1e-9));
        }

        @Test
        @DisplayName("절반 실으면 절반만 느려진다")
        void 적재율에_비례해_느려진다() {
            caravan.cargo().add("wheat", 30);

            Departure d = travel.quote(caravan, 0, route("산기슭길"));

            assertThat(d.loadRatio()).isCloseTo(0.5, within(1e-9));
            assertThat(d.actualHours()).isCloseTo(2.4, within(1e-9));
        }

        @Test
        @DisplayName("늑대고개는 가득 실어도 해안가도보다 빠르다")
        void 늑대고개가_여전히_빠르다() {
            caravan.cargo().add("wheat", 60);

            double wolfPass = travel.quote(caravan, 0, route("늑대고개")).actualHours();
            double coastRoad = travel.quote(caravan, 0, route("해안가도")).actualHours();

            assertThat(wolfPass).isCloseTo(3.5, within(1e-9));
            assertThat(coastRoad).isCloseTo(5.6, within(1e-9));
            // 2시간 6분을 위험 28%p 와 바꾸는 것이 이 게임의 핵심 결정이다
            assertThat(coastRoad - wolfPass).isCloseTo(2.1, within(1e-9));
        }
    }

    @Nested
    @DisplayName("비용")
    class Cost {

        @Test
        @DisplayName("비용은 기본 소요 × 시간당 출발비 + 통행료다")
        void 비용_계산() {
            Departure mountain = travel.quote(caravan, 0, route("산기슭길"));
            assertThat(mountain.travelCost()).isEqualTo(2.0 * 30);
            assertThat(mountain.toll()).isZero();
            assertThat(mountain.totalCost()).isEqualTo(60);

            Departure coast = travel.quote(caravan, 0, route("해안가도"));
            assertThat(coast.totalCost()).isEqualTo(4.0 * 30 + 60);
        }

        @Test
        @DisplayName("많이 실어도 비용은 안 오른다 — 느려질 뿐이다")
        void 적재는_비용이_아니라_시간을_먹는다() {
            double empty = travel.quote(caravan, 0, route("산기슭길")).totalCost();
            caravan.cargo().add("wheat", 60);
            double full = travel.quote(caravan, 0, route("산기슭길")).totalCost();

            assertThat(full).isEqualTo(empty);
        }

        @Test
        @DisplayName("늑대고개가 해안가도보다 105 G 싸다")
        void 늑대고개가_더_싸다() {
            double wolfPass = travel.quote(caravan, 0, route("늑대고개")).totalCost();
            double coastRoad = travel.quote(caravan, 0, route("해안가도")).totalCost();

            assertThat(coastRoad - wolfPass).isEqualTo(105);
        }

        @Test
        @DisplayName("돈이 모자라면 출발하지 못하고 도시에 남는다")
        void 돈이_없으면_못_떠난다() {
            Trader broke = new Trader("b", "빈털터리", 10);

            assertThatThrownBy(() -> travel.depart(broke, caravan, 0, route("해안가도")))
                    .isInstanceOf(TradeRefused.class);

            assertThat(caravan.cityId(0)).isEqualTo("harn");
            assertThat(broke.gold()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("이동")
    class Moving {

        @Test
        @DisplayName("떠나면 어느 도시에도 없다")
        void 떠나면_도시에_없다() {
            travel.depart(trader, caravan, 0, route("산기슭길"));

            assertThat(caravan.cityId(0)).isNull();
            assertThat(caravan.isTravelling(0)).isTrue();
        }

        @Test
        @DisplayName("도착 시각이 지나면 목적지에 있다")
        void 시간이_지나면_도착한다() {
            Departure d = travel.depart(trader, caravan, 0, route("산기슭길"));

            assertThat(caravan.cityId(d.arrivesTick() - 1)).isNull();
            assertThat(caravan.cityId(d.arrivesTick())).isEqualTo("karden");
            assertThat(caravan.isTravelling(d.arrivesTick())).isFalse();
        }

        @Test
        @DisplayName("접속하지 않아도 도착한다 — 시간만 흐르면 된다")
        void 접속하지_않아도_도착한다() {
            // 출발시키고 아무것도 하지 않은 채 세계 시간만 흘린다.
            // 매 틱 캐러밴을 훑는 코드가 없어도 도착해 있어야 한다.
            travel.depart(trader, caravan, 0, route("해안가도"));

            long muchLater = 10L * WorldClock.TICKS_PER_DAY;
            assertThat(caravan.cityId(muchLater)).isEqualTo("selia");
        }

        @Test
        @DisplayName("이동 중에 또 떠날 수 없다 — 되돌릴 수 없다")
        void 이동_중에는_못_떠난다() {
            travel.depart(trader, caravan, 0, route("산기슭길"));

            assertThatThrownBy(() -> travel.depart(trader, caravan, 1, route("해안가도")))
                    .isInstanceOf(TravelRefused.class)
                    .hasMessageContaining("이동 중");
        }

        @Test
        @DisplayName("그 도시를 지나지 않는 길로는 떠날 수 없다")
        void 닿지_않는_길로는_못_간다() {
            // 하른에 있는데 카르덴↔셀리아 노선을 타려 한다
            assertThatThrownBy(() -> travel.depart(trader, caravan, 0, route("광산가도")))
                    .isInstanceOf(TravelRefused.class);
        }

        @Test
        @DisplayName("한 번 더 떠나면 세 도시를 다 돌 수 있다")
        void 삼각형을_돈다() {
            Departure first = travel.depart(trader, caravan, 0, route("산기슭길"));
            long t = first.arrivesTick();
            assertThat(caravan.cityId(t)).isEqualTo("karden");

            Departure second = travel.depart(trader, caravan, t, route("광산가도"));
            assertThat(caravan.cityId(second.arrivesTick())).isEqualTo("selia");
        }
    }

    @Test
    @DisplayName("하른과 셀리아 사이에는 길이 둘이다 — 고르는 것이 결정이다")
    void 같은_목적지에_길이_둘이다() {
        assertThat(travel.routesBetween("harn", "selia"))
                .extracting(RouteSpec::name)
                .containsExactlyInAnyOrder("해안가도", "늑대고개");

        assertThat(travel.routesFrom("harn")).hasSize(3);
    }
}
