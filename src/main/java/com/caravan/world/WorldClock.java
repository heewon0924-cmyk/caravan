package com.caravan.world;

import com.caravan.data.WorldRules;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 세계 시간. 플레이어의 접속과 무관하게 흐른다 (docs/03-이동과-시간.md 2장).
 *
 * <p>현실 시간에 배율을 곱해서 세계 시간을 만든다. 배율을 바꿔도 밸런스는
 * 그대로다 — 생산 · 소비 · 이동이 전부 세계 시간 기준이라 배율에 비례한다.
 *
 * <p><b>코드에 현실 시간으로 적힌 숫자가 하나라도 들어가면 배율을 바꿀 수 없게 된다.</b>
 * 모든 시간은 세계 분(minute) 단위로만 다룬다.
 */
public final class WorldClock {

    /** 한 틱은 세계 시간 10분이다. */
    public static final int MINUTES_PER_TICK = 10;

    /** 세계 하루는 144틱이다. */
    public static final int TICKS_PER_DAY = 24 * 60 / MINUTES_PER_TICK;

    private final Clock clock;
    private final Instant realEpoch;
    private final double speed;

    public WorldClock(Clock clock, Instant realEpoch, double speed) {
        this.clock = clock;
        this.realEpoch = realEpoch;
        this.speed = speed;
    }

    public static WorldClock startingNow(WorldRules rules) {
        Clock system = Clock.systemUTC();
        return new WorldClock(system, system.instant(), rules.speed());
    }

    /** 세계가 시작된 뒤 흐른 세계 시간(분). */
    public long worldMinutes() {
        long realMillis = Duration.between(realEpoch, clock.instant()).toMillis();
        return (long) (realMillis / 60_000.0 * speed);
    }

    /** 지금 몇 번째 틱인가. */
    public long tick() {
        return worldMinutes() / MINUTES_PER_TICK;
    }

    /** 세계 며칠째인가. 0부터 센다. */
    public long day() {
        return tick() / TICKS_PER_DAY;
    }

    public double speed() {
        return speed;
    }

    /** 세계 시간 기준 {@code minutes} 분이 현실 몇 밀리초인가. */
    public long realMillisFor(long worldMinutes) {
        return (long) (worldMinutes * 60_000.0 / speed);
    }

    /** "3일 07:20" 꼴로 읽기 좋게 만든다. */
    public static String format(long tick) {
        long minutes = tick * MINUTES_PER_TICK;
        long day = minutes / (24 * 60);
        long hour = minutes / 60 % 24;
        long minute = minutes % 60;
        return String.format("%d일 %02d:%02d", day, hour, minute);
    }
}
