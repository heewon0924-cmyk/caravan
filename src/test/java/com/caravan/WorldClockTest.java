package com.caravan;

import com.caravan.world.WorldClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** docs/03-이동과-시간.md 2·3장. */
class WorldClockTest {

    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    private WorldClock at(Duration elapsed, double speed) {
        Clock fixed = Clock.fixed(EPOCH.plus(elapsed), ZoneOffset.UTC);
        return new WorldClock(fixed, EPOCH, speed);
    }

    @Test
    @DisplayName("배율 1이면 현실 시간과 세계 시간이 같다")
    void 배율_1() {
        WorldClock clock = at(Duration.ofHours(2), 1.0);
        assertThat(clock.worldMinutes()).isEqualTo(120);
        assertThat(clock.tick()).isEqualTo(12);
    }

    @Test
    @DisplayName("배율 6이면 현실 20분이 세계 2시간이다 — 프로토타입 기본값")
    void 배율_6이_프로토타입_기본값() {
        WorldClock clock = at(Duration.ofMinutes(20), 6.0);

        assertThat(clock.worldMinutes()).isEqualTo(120);
        // 하른 → 카르덴 2시간 노선이 현실 20분이 된다
        assertThat(clock.tick()).isEqualTo(12);
    }

    @Test
    @DisplayName("배율 60이면 현실 24분이 세계 하루다 — 개발·검증용")
    void 배율_60은_검증용() {
        WorldClock clock = at(Duration.ofMinutes(24), 60.0);

        assertThat(clock.day()).isEqualTo(1);
        assertThat(clock.tick()).isEqualTo(WorldClock.TICKS_PER_DAY);
    }

    @Test
    @DisplayName("한 틱은 세계 10분, 하루는 144틱이다")
    void 틱_단위() {
        assertThat(WorldClock.MINUTES_PER_TICK).isEqualTo(10);
        assertThat(WorldClock.TICKS_PER_DAY).isEqualTo(144);
    }

    @Test
    @DisplayName("세계 시간을 현실 시간으로 되돌릴 수 있다 — 도착 예정 시각 계산에 쓴다")
    void 세계_시간을_현실로_되돌린다() {
        WorldClock clock = at(Duration.ZERO, 6.0);

        // 세계 2시간짜리 노선은 현실 20분
        assertThat(clock.realMillisFor(120)).isEqualTo(Duration.ofMinutes(20).toMillis());
    }

    @Test
    @DisplayName("틱을 읽기 좋은 꼴로 찍는다")
    void 시각_표기() {
        assertThat(WorldClock.format(0)).isEqualTo("0일 00:00");
        assertThat(WorldClock.format(144)).isEqualTo("1일 00:00");
        assertThat(WorldClock.format(144 + 44)).isEqualTo("1일 07:20");
    }
}
