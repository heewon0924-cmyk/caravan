package com.caravan;

import com.caravan.data.WorldData;
import com.caravan.trade.Cargo;
import com.caravan.trade.Exchange;
import com.caravan.trade.Receipt;
import com.caravan.trade.TradeRefused;
import com.caravan.trade.Trader;
import com.caravan.world.City;
import com.caravan.world.Market;
import com.caravan.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** 2단계 — 사고팔기. docs/02-교역과-가격.md 3·6장. */
class ExchangeTest {

    private WorldData data;
    private World world;
    private Exchange exchange;
    private Trader trader;
    private City harn;

    /**
     * 화물은 상인이 아니라 캐러밴 것이라 {@code Exchange} 가 둘을 따로 받는다.
     * 캐러밴이 없는 시험에서는 상인마다 화물칸 하나를 붙여 둔다.
     */
    private final Map<Trader, Cargo> cargos = new IdentityHashMap<>();

    private Cargo cargoOf(Trader t) {
        return cargos.computeIfAbsent(t, x -> new Cargo());
    }

    @BeforeEach
    void setUp() {
        data = WorldData.load();
        world = new World(data);
        exchange = new Exchange(data.rules());
        trader = new Trader("test", "시험 상단", 100_000);
        harn = world.city("harn");
    }

    @Nested
    @DisplayName("매수")
    class Buying {

        @Test
        @DisplayName("돈이 나가고 물건이 들어오고 재고가 빠진다")
        void 돈과_물건과_재고가_한꺼번에_움직인다() {
            Market wheat = harn.market("wheat");
            double stockBefore = wheat.stock();

            Receipt r = exchange.buy(trader, cargoOf(trader), harn, "wheat", 300);

            assertThat(trader.gold()).isCloseTo(100_000 - r.net(), within(1e-6));
            assertThat(cargoOf(trader).quantityOf("wheat")).isEqualTo(300);
            assertThat(wheat.stock()).isCloseTo(stockBefore - 300, within(1e-9));
        }

        @Test
        @DisplayName("사면 시세가 오른다 — 2단계의 합격 기준")
        void 사면_시세가_밀린다() {
            Receipt r = exchange.buy(trader, cargoOf(trader), harn, "wheat", 300);

            assertThat(r.priceBefore()).isCloseTo(100, within(0.01));
            assertThat(r.priceAfter()).isCloseTo(110, within(0.5));
            assertThat(r.priceMovedRatio()).isPositive();
            // 시세대로 샀을 때보다 더 냈다
            assertThat(r.slippage()).isPositive();
            assertThat(r.gross()).isGreaterThan(r.atSpot());
        }

        @Test
        @DisplayName("거래세 3%가 적분 금액 위에 붙는다")
        void 거래세는_적분_금액_위에_붙는다() {
            Receipt r = exchange.buy(trader, cargoOf(trader), harn, "wheat", 300);

            assertThat(r.tax()).isCloseTo(r.gross() * 0.03, within(1e-9));
            assertThat(r.net()).isCloseTo(r.gross() + r.tax(), within(1e-9));
            assertThat(r.averageUnitPrice()).isGreaterThan(r.priceBefore());
        }

        @Test
        @DisplayName("소지금이 모자라면 거절하고 아무것도 바꾸지 않는다")
        void 돈이_모자라면_아무것도_안_바뀐다() {
            Trader broke = new Trader("broke", "빈털터리", 1000);
            Market wheat = harn.market("wheat");
            double stockBefore = wheat.stock();

            assertThatThrownBy(() -> exchange.buy(broke, cargoOf(broke), harn, "wheat", 300))
                    .isInstanceOf(TradeRefused.class)
                    .hasMessageContaining("소지금이 모자란다");

            assertThat(broke.gold()).isEqualTo(1000);
            assertThat(cargoOf(broke).isEmpty()).isTrue();
            assertThat(wheat.stock()).isEqualTo(stockBefore);
        }

        @Test
        @DisplayName("재고보다 많이 살 수 없다")
        void 재고보다_많이_살_수_없다() {
            assertThatThrownBy(() -> exchange.buy(trader, cargoOf(trader), harn, "spice", 999))
                    .isInstanceOf(TradeRefused.class)
                    .hasMessageContaining("재고가 모자란다");

            assertThat(trader.gold()).isEqualTo(100_000);
        }
    }

    @Nested
    @DisplayName("매도")
    class Selling {

        @BeforeEach
        void 밀을_싣는다() {
            exchange.buy(trader, cargoOf(trader), harn, "wheat", 300);
        }

        @Test
        @DisplayName("물건이 나가고 돈이 들어오고 재고가 늘어난다")
        void 돈과_물건과_재고가_반대로_움직인다() {
            City karden = world.city("karden");
            Market wheat = karden.market("wheat");
            double stockBefore = wheat.stock();
            double goldBefore = trader.gold();

            Receipt r = exchange.sell(trader, cargoOf(trader), karden, "wheat", 300);

            assertThat(trader.gold()).isCloseTo(goldBefore + r.net(), within(1e-6));
            assertThat(cargoOf(trader).quantityOf("wheat")).isZero();
            assertThat(wheat.stock()).isCloseTo(stockBefore + 300, within(1e-9));
        }

        @Test
        @DisplayName("팔면 시세가 내린다 — 한 도시에 쏟으면 내가 내 가격을 무너뜨린다")
        void 팔면_시세가_내린다() {
            Receipt r = exchange.sell(trader, cargoOf(trader), world.city("karden"), "wheat", 300);

            assertThat(r.priceMovedRatio()).isNegative();
            assertThat(r.gross()).isLessThan(r.atSpot());
            assertThat(r.slippage()).isPositive();
        }

        @Test
        @DisplayName("거래세 3%가 수령액에서 빠진다")
        void 거래세는_수령액에서_빠진다() {
            Receipt r = exchange.sell(trader, cargoOf(trader), world.city("karden"), "wheat", 300);

            assertThat(r.tax()).isCloseTo(r.gross() * 0.03, within(1e-9));
            assertThat(r.net()).isCloseTo(r.gross() - r.tax(), within(1e-9));
        }

        @Test
        @DisplayName("가진 것보다 많이 팔면 거절하고 아무것도 바꾸지 않는다")
        void 없는_것은_팔_수_없다() {
            Market wheat = harn.market("wheat");
            double stockBefore = wheat.stock();
            double goldBefore = trader.gold();

            assertThatThrownBy(() -> exchange.sell(trader, cargoOf(trader), harn, "wheat", 500))
                    .isInstanceOf(TradeRefused.class)
                    .hasMessageContaining("가진 것보다 많이");

            assertThat(cargoOf(trader).quantityOf("wheat")).isEqualTo(300);
            assertThat(trader.gold()).isEqualTo(goldBefore);
            assertThat(wheat.stock()).isEqualTo(stockBefore);
        }
    }

    @Nested
    @DisplayName("견적")
    class Quoting {

        @Test
        @DisplayName("견적은 아무것도 바꾸지 않는다")
        void 견적은_세계를_건드리지_않는다() {
            Market wheat = harn.market("wheat");
            double stockBefore = wheat.stock();

            Receipt quote = exchange.quoteBuy(harn, "wheat", 300);

            assertThat(wheat.stock()).isEqualTo(stockBefore);
            assertThat(trader.gold()).isEqualTo(100_000);

            // 견적대로 실제로 산다
            Receipt actual = exchange.buy(trader, cargoOf(trader), harn, "wheat", 300);
            assertThat(actual.net()).isCloseTo(quote.net(), within(1e-9));
            assertThat(actual.priceAfter()).isCloseTo(quote.priceAfter(), within(1e-9));
        }
    }

    @Test
    @DisplayName("같은 도시에서 사서 곧바로 되팔면 거래세만큼 손해다")
    void 제자리_왕복은_거래세만큼_손해다() {
        double before = trader.gold();

        exchange.buy(trader, cargoOf(trader), harn, "wheat", 300);
        exchange.sell(trader, cargoOf(trader), harn, "wheat", 300);

        double lost = before - trader.gold();

        // 곡선만으로는 본전이고, 나가는 건 양쪽 거래세뿐이다.
        // 이게 "움직일 가치가 있는 차익"의 하한선을 만든다 (docs/02 6장).
        assertThat(lost).isPositive();
        assertThat(lost / before).isLessThan(0.07);
        assertThat(cargoOf(trader).isEmpty()).isTrue();
        // 재고도 제자리로 돌아온다
        assertThat(harn.market("wheat").stock()).isCloseTo(2000, within(1e-9));
    }

    @Test
    @DisplayName("NPC 든 플레이어든 같은 함수를 쓴다 — 4단계가 여기 기댄다")
    void 주체가_누구든_같은_규칙이다() {
        Trader npc = new Trader("npc-1", "떠돌이 상인", 100_000);

        Receipt playerReceipt = exchange.buy(trader, cargoOf(trader), harn, "iron", 20);
        Receipt npcReceipt = exchange.buy(npc, cargoOf(npc), harn, "iron", 20);

        // 둘 다 적분 가격과 거래세를 물었다. 다만 NPC 가 나중에 샀으니 더 비싸다 —
        // 플레이어가 산 만큼 재고가 줄었기 때문이다.
        assertThat(npcReceipt.tax()).isCloseTo(npcReceipt.gross() * 0.03, within(1e-9));
        assertThat(npcReceipt.net()).isGreaterThan(playerReceipt.net());
        assertThat(npcReceipt.priceBefore()).isCloseTo(playerReceipt.priceAfter(), within(1e-9));
    }
}
