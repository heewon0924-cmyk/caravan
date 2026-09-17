package com.caravan;

import com.caravan.economy.PriceCurve;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** docs/02-교역과-가격.md 2·3장이 실제로 그 숫자를 내는지 못 박는다. */
class PriceCurveTest {

    /** 밀: 기준가 100, 기준재고 2000, 탄력도 0.6 */
    private final PriceCurve wheat = new PriceCurve(100, 0.6, 2000, 0.35, 3.0);

    /** 향신료: 탄력도가 1을 넘는 쪽도 같은 식으로 도는지 본다 */
    private final PriceCurve spice = new PriceCurve(900, 1.2, 300, 0.35, 3.0);

    @Nested
    @DisplayName("시세")
    class Spot {

        @Test
        @DisplayName("문서 2장의 계산 예와 같은 값을 낸다")
        void 문서의_표대로_나온다() {
            assertThat(wheat.spot(3000)).isCloseTo(78, within(1));
            assertThat(wheat.spot(2000)).isCloseTo(100, within(0.01));
            assertThat(wheat.spot(1200)).isCloseTo(136, within(1));
            assertThat(wheat.spot(800)).isCloseTo(173, within(1));
            assertThat(wheat.spot(400)).isCloseTo(263, within(1));
        }

        @Test
        @DisplayName("재고가 기준재고면 정확히 기준가다")
        void 기준재고에서_기준가() {
            assertThat(wheat.spot(2000)).isEqualTo(100, within(1e-9));
            assertThat(spice.spot(300)).isEqualTo(900, within(1e-9));
        }

        @Test
        @DisplayName("재고가 바닥나도 값이 발산하지 않는다 — 상한에 붙는다")
        void 상한에_붙는다() {
            assertThat(wheat.spot(100)).isEqualTo(300, within(1e-9));
            assertThat(wheat.spot(1)).isEqualTo(300, within(1e-9));
            assertThat(wheat.spot(0)).isEqualTo(300, within(1e-9));
        }

        @Test
        @DisplayName("재고가 넘쳐도 값이 0으로 가지 않는다 — 하한에 붙는다")
        void 하한에_붙는다() {
            assertThat(wheat.spot(1_000_000)).isEqualTo(35, within(1e-9));
        }

        @Test
        @DisplayName("재고가 늘수록 값은 내려가기만 한다")
        void 단조_감소() {
            double previous = Double.MAX_VALUE;
            for (double stock = 1; stock < 20_000; stock += 7) {
                double price = wheat.spot(stock);
                assertThat(price).isFinite().isLessThanOrEqualTo(previous);
                previous = price;
            }
        }

        @Test
        @DisplayName("탄력도가 클수록 같은 재고 변화에 더 크게 튄다")
        void 탄력도가_성격을_만든다() {
            // 재고가 기준의 절반이 됐을 때
            double wheatJump = wheat.spot(1000) / wheat.basePrice();
            double spiceJump = spice.spot(150) / spice.basePrice();
            assertThat(spiceJump).isGreaterThan(wheatJump);
        }
    }

    @Nested
    @DisplayName("적분 가격")
    class Integral {

        @Test
        @DisplayName("문서 3장의 예 — 재고 2000에서 밀 300을 사면 평균 105G")
        void 문서의_예대로_나온다() {
            double cost = wheat.cost(2000, 300);

            assertThat(cost).isCloseTo(31_471, within(5.0));
            assertThat(wheat.averageBuyPrice(2000, 300)).isCloseTo(104.9, within(0.1));
            // 시작 시세 100G, 다 사고 난 뒤 110G
            assertThat(wheat.spot(1700)).isCloseTo(110, within(0.5));
        }

        @Test
        @DisplayName("살수록 단가가 오른다 — 무한 차익이 막힌다")
        void 살수록_단가가_오른다() {
            double small = wheat.averageBuyPrice(2000, 10);
            double medium = wheat.averageBuyPrice(2000, 300);
            double large = wheat.averageBuyPrice(2000, 1500);

            assertThat(small).isLessThan(medium);
            assertThat(medium).isLessThan(large);
            // 현재 시세보다는 언제나 비싸게 산다
            assertThat(small).isGreaterThan(wheat.spot(2000));
        }

        @Test
        @DisplayName("팔수록 단가가 내린다 — 한 도시에 쏟으면 내가 내 가격을 무너뜨린다")
        void 팔수록_단가가_내린다() {
            double small = wheat.averageSellPrice(2000, 10);
            double large = wheat.averageSellPrice(2000, 1500);

            assertThat(large).isLessThan(small);
            assertThat(small).isLessThan(wheat.spot(2000));
        }

        @Test
        @DisplayName("재고를 전부 사들여도 지불액이 유한하다")
        void 재고를_다_사도_유한하다() {
            // 상하한을 무시하고 거듭제곱식만 적분하면 여기서 발산한다.
            // 구간을 나눠 적분하는 이유가 이것이다.
            double cost = wheat.cost(2000, 2000);
            assertThat(cost).isFinite().isPositive();
            // 전부 사면 마지막엔 상한가에 사게 되므로 평균 단가가 기준가를 크게 웃돈다
            assertThat(cost / 2000).isGreaterThan(wheat.basePrice());
        }

        @Test
        @DisplayName("재고보다 많이 살 수는 없다")
        void 재고_초과_매수는_막힌다() {
            assertThatThrownBy(() -> wheat.cost(500, 501))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("재고보다 많이");
        }

        @Test
        @DisplayName("사서 곧바로 되팔면 정확히 본전이다 — 손실을 만드는 건 거래세다")
        void 왕복하면_본전이고_거래세가_하한선을_만든다() {
            double stock = 2000;
            double quantity = 300;

            double paid = wheat.cost(stock, quantity);
            double got = wheat.revenue(stock - quantity, quantity);

            // 곡선만으로는 왕복 손익이 0이다. docs/02 6장의 거래세 3%가
            // "움직일 가치가 있는 차익"의 하한선을 만드는 장치다.
            assertThat(got).isCloseTo(paid, within(1e-6));
        }

        @Test
        @DisplayName("탄력도가 1보다 큰 품목도 같은 식으로 돈다")
        void 탄력도가_1보다_커도_된다() {
            double cost = spice.cost(300, 100);
            assertThat(cost).isFinite().isPositive();
            assertThat(spice.averageBuyPrice(300, 100)).isGreaterThan(spice.spot(300));
            assertThat(spice.cost(300, 300)).isFinite();
        }

        @Test
        @DisplayName("상한·하한 구간을 가로질러도 적분이 이어진다")
        void 구간을_가로질러도_이어진다() {
            // 재고를 상한 구간(320 이하)까지 통째로 긁어내린다
            double crossing = wheat.cost(2000, 1900);
            // 나눠 사든 한 번에 사든 지불액이 같아야 한다
            double inTwoSteps = wheat.cost(2000, 900) + wheat.cost(1100, 1000);
            assertThat(crossing).isCloseTo(inTwoSteps, within(1e-6));
        }
    }

    @Nested
    @DisplayName("잘못된 설정은 읽는 시점에 터진다")
    class Validation {

        @Test
        @DisplayName("탄력도 1.0 은 거부한다 — 적분식이 로그가 된다")
        void 탄력도_1은_거부한다() {
            assertThatThrownBy(() -> new PriceCurve(100, 1.0, 2000, 0.35, 3.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("탄력도 1.0");
        }

        @Test
        @DisplayName("상하한이 뒤집혀 있으면 거부한다")
        void 상하한이_뒤집히면_거부한다() {
            assertThatThrownBy(() -> new PriceCurve(100, 0.6, 2000, 1.5, 3.0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new PriceCurve(100, 0.6, 2000, 0.35, 0.9))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("기준가·기준재고가 0 이하면 거부한다")
        void 영_이하는_거부한다() {
            assertThatThrownBy(() -> new PriceCurve(0, 0.6, 2000, 0.35, 3.0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new PriceCurve(100, 0.6, 0, 0.35, 3.0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static org.assertj.core.data.Offset<Double> within(double tolerance) {
        return org.assertj.core.data.Offset.offset(tolerance);
    }
}
