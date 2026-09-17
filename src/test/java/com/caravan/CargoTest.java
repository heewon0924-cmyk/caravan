package com.caravan;

import com.caravan.data.WorldData;
import com.caravan.trade.Cargo;
import com.caravan.trade.TradeRefused;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** docs/05-캐러밴.md 4장 — 화물은 부피로 센다. */
class CargoTest {

    private final WorldData data = WorldData.load();

    @Test
    @DisplayName("같은 품목을 두 번 실으면 합쳐진다")
    void 같은_품목은_합쳐진다() {
        Cargo cargo = new Cargo();
        cargo.add("wheat", 100);
        cargo.add("wheat", 50);

        assertThat(cargo.quantityOf("wheat")).isEqualTo(150);
        assertThat(cargo.all()).hasSize(1);
    }

    @Test
    @DisplayName("전부 내리면 목록에서 사라진다")
    void 다_내리면_사라진다() {
        Cargo cargo = new Cargo();
        cargo.add("wheat", 100);
        cargo.remove("wheat", 100);

        assertThat(cargo.isEmpty()).isTrue();
        assertThat(cargo.quantityOf("wheat")).isZero();
    }

    @Test
    @DisplayName("가진 것보다 많이 내릴 수 없다")
    void 없는_것은_내릴_수_없다() {
        Cargo cargo = new Cargo();
        cargo.add("wheat", 100);

        assertThatThrownBy(() -> cargo.remove("wheat", 101))
                .isInstanceOf(TradeRefused.class);
        assertThat(cargo.quantityOf("wheat")).isEqualTo(100);
    }

    @Test
    @DisplayName("부피당 가치가 품목의 성격을 만든다 — 같은 부피면 향신료가 45배 비싸다")
    void 부피가_성격을_만든다() {
        Cargo wheatOnly = new Cargo();
        wheatOnly.add("wheat", 60);          // 부피 1.0 × 60 = 60

        Cargo spiceOnly = new Cargo();
        spiceOnly.add("spice", 300);         // 부피 0.2 × 300 = 60

        // 캐러밴 용량 60을 꽉 채우는 두 가지 방법 (docs/05 4장)
        assertThat(wheatOnly.volume(data.goods())).isCloseTo(60, within(1e-9));
        assertThat(spiceOnly.volume(data.goods())).isCloseTo(60, within(1e-9));

        double wheatValue = 60 * data.goods("wheat").basePrice();
        double spiceValue = 300 * data.goods("spice").basePrice();
        assertThat(spiceValue / wheatValue).isCloseTo(45, within(0.1));
    }

    @Test
    @DisplayName("여러 품목의 부피가 더해진다")
    void 부피는_더해진다() {
        Cargo cargo = new Cargo();
        cargo.add("wheat", 10);   // 10.0
        cargo.add("iron", 10);    // 12.0
        cargo.add("spice", 10);   //  2.0

        assertThat(cargo.volume(data.goods())).isCloseTo(24.0, within(1e-9));
    }
}
