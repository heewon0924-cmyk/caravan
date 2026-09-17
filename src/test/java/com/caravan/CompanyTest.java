package com.caravan;

import com.caravan.app.Company;
import com.caravan.data.WorldData;
import com.caravan.trade.TradeRefused;
import com.caravan.travel.Departure;
import com.caravan.travel.TravelRefused;
import com.caravan.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * 유스케이스 계층의 규칙.
 *
 * <p>여기 있는 규칙들이 <b>클라이언트에 들어가면 안 되는 것들</b>이다
 * (docs/31-서버와-클라이언트-경계.md). 콘솔이나 Phaser 가 "용량이 찼으니 버튼을
 * 막자" 를 스스로 판단하면, 클라이언트를 갈아끼울 때 다시 구현해야 하고
 * 멀티에서는 클라이언트를 고쳐서 우회할 수 있게 된다.
 */
class CompanyTest {

    private WorldData data;
    private World world;
    private Company company;

    @BeforeEach
    void setUp() {
        data = WorldData.load();
        world = new World(data);
        company = new Company(world, data, "시험 상단", "harn");
    }

    @Test
    @DisplayName("캐러밴 용량을 넘겨 실을 수 없다")
    void 용량을_넘길_수_없다() {
        // 용량 60, 밀 부피 1.0 → 60개까지
        company.buy("wheat", 60);
        assertThat(company.loadRatio()).isCloseTo(1.0, within(1e-9));

        assertThatThrownBy(() -> company.buy("wheat", 1))
                .isInstanceOf(TradeRefused.class)
                .hasMessageContaining("자리가 없다");
    }

    @Test
    @DisplayName("남은 부피로 몇 개나 더 실을 수 있는지 알려준다")
    void 남은_자리를_알려준다() {
        assertThat(company.roomFor("wheat")).isCloseTo(60, within(1e-9));
        assertThat(company.roomFor("spice")).isCloseTo(300, within(1e-9));

        company.buy("wheat", 30);
        assertThat(company.roomFor("wheat")).isCloseTo(30, within(1e-9));
        assertThat(company.roomFor("spice")).isCloseTo(150, within(1e-9));
    }

    @Test
    @DisplayName("이동 중에는 사고팔 수 없다")
    void 이동_중에는_거래하지_못한다() {
        company.buy("wheat", 20);
        company.departVia(company.route("산기슭길"));

        assertThat(company.here()).isNull();
        assertThatThrownBy(() -> company.buy("wheat", 1))
                .isInstanceOf(TradeRefused.class)
                .hasMessageContaining("이동 중");
        assertThatThrownBy(() -> company.sell("wheat", 1))
                .isInstanceOf(TradeRefused.class)
                .hasMessageContaining("이동 중");
    }

    @Test
    @DisplayName("도착해도 자동으로 팔지 않는다 — 파는 시점을 고르는 게 재미다")
    void 도착해도_자동으로_팔지_않는다() {
        company.buy("wheat", 60);
        Departure d = company.departVia(company.route("산기슭길"));

        // 출발비를 문 직후를 기준으로 잡는다 — 도착 자체로는 돈이 오가지 않아야 한다
        double goldOnTheRoad = company.trader().gold();
        world.advanceTo(d.arrivesTick());

        assertThat(company.here().id()).isEqualTo("karden");
        assertThat(company.cargo().quantityOf("wheat")).isEqualTo(60);
        assertThat(company.trader().gold()).isEqualTo(goldOnTheRoad);
    }

    @Test
    @DisplayName("길이 여럿인 목적지는 어느 길로 갈지 골라야 한다")
    void 길이_여럿이면_골라야_한다() {
        // 하른 → 셀리아에는 해안가도와 늑대고개가 있다.
        // 임의로 고르면 이 게임의 핵심 결정 하나가 사라진다.
        assertThatThrownBy(() -> company.departTo("selia"))
                .isInstanceOf(TravelRefused.class)
                .hasMessageContaining("해안가도")
                .hasMessageContaining("늑대고개");

        assertThat(company.here().id()).isEqualTo("harn");
    }

    @Test
    @DisplayName("길이 하나뿐이면 목적지만 줘도 된다")
    void 길이_하나면_바로_간다() {
        Departure d = company.departTo("karden");

        assertThat(d.route().name()).isEqualTo("산기슭길");
        assertThat(company.isTravelling()).isTrue();
    }

    @Test
    @DisplayName("총자산은 소지금 + 여기서 화물을 다 팔았을 때의 금액이다")
    void 총자산은_화물을_쳐준다() {
        double before = company.netWorth();
        assertThat(before).isEqualTo(data.rules().startingGold());

        company.buy("wheat", 60);

        // 사자마자 되팔면 거래세 두 번만큼 줄어 있다
        assertThat(company.netWorth()).isLessThan(before);
        assertThat(company.netWorth()).isGreaterThan(before * 0.9);
    }

    @Test
    @DisplayName("이동 중에는 화물 값을 매기지 않는다 — 어느 도시 시세인지 알 수 없다")
    void 이동_중_총자산은_소지금뿐이다() {
        company.buy("wheat", 60);
        company.departVia(company.route("산기슭길"));

        assertThat(company.netWorth()).isEqualTo(company.trader().gold());
    }

    @Test
    @DisplayName("한 바퀴를 돈다 — 사고, 가고, 팔고, 돌아온다")
    void 한_바퀴를_돈다() {
        world.advanceDays(2);
        double start = company.netWorth();

        company.buy("wheat", 60);
        Departure out = company.departVia(company.route("산기슭길"));
        world.advanceTo(out.arrivesTick());

        company.sell("wheat", 60);
        company.buy("iron", 50);
        Departure back = company.departVia(company.route("산기슭길"));
        world.advanceTo(back.arrivesTick());

        company.sell("iron", 50);

        assertThat(company.here().id()).isEqualTo("harn");
        assertThat(company.cargo().isEmpty()).isTrue();
        assertThat(company.netWorth()).isGreaterThan(start);
    }
}
