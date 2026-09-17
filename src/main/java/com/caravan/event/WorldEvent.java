package com.caravan.event;

import com.caravan.data.EventSpec;
import com.caravan.world.WorldClock;

/**
 * 일어나기로 정해진 사건 하나. 아직 시작 전일 수도, 진행 중일 수도 있다.
 *
 * <p>이벤트는 <b>미리 정해지고 나중에 일어난다.</b> 그래야 예고가 가능하다 —
 * 예고 없이 터지면 플레이어는 대응만 하게 되고, 예고가 있으면 <b>선점</b>하게 된다.
 * 선점이 이 게임의 재미다 (docs/09-이벤트와-NPC.md 3장).
 *
 * @param cityId 이 사건이 일어나는 도시. 노선 사건이면 {@code null} 일 수 있다
 */
public record WorldEvent(EventSpec spec,
                         String cityId,
                         long startTick,
                         long endTick) {

    public boolean isActiveAt(long tick) {
        return tick >= startTick && tick < endTick;
    }

    public boolean hasEndedBy(long tick) {
        return tick >= endTick;
    }

    /** 예고가 도는 시각. 예고가 없는 사건이면 시작 시각 그대로다. */
    public long forecastTick() {
        if (!spec.hasForecast()) {
            return startTick;
        }
        return startTick - Math.round(spec.forecastHours() * 60 / WorldClock.MINUTES_PER_TICK);
    }

    public boolean isForecastVisibleAt(long tick) {
        return spec.hasForecast() && tick >= forecastTick() && tick < startTick;
    }

    public String headline() {
        return spec.text();
    }

    /** "3일 07:20 ~ 6일 07:20" */
    public String period() {
        return WorldClock.format(startTick) + " ~ " + WorldClock.format(endTick);
    }
}
