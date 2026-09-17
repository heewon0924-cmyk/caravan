package com.caravan.travel;

import com.caravan.data.RouteSpec;
import com.caravan.world.WorldClock;

/**
 * 진행 중인 이동 한 건.
 *
 * <p>출발 시각과 도착 예정 시각만 들고 있다. 그래서 <b>플레이어가 접속하지 않아도
 * 캐러밴은 간다</b> — 매 틱 뭔가를 갱신하는 게 아니라, 지금 몇 시인지만 보면
 * 도착했는지 알 수 있다 (docs/03-이동과-시간.md 2장).
 *
 * @param loadRatio 출발 당시 적재율. 이것 때문에 실제 소요가 기본 소요보다 길다
 */
public record Journey(RouteSpec route,
                      String fromId,
                      String toId,
                      double actualHours,
                      double loadRatio,
                      long departedTick,
                      long arrivesTick) {

    public boolean hasArrived(long tick) {
        return tick >= arrivesTick;
    }

    public long ticksLeft(long tick) {
        return Math.max(0, arrivesTick - tick);
    }

    /** 얼마나 왔는가. 0.0 ~ 1.0 */
    public double progress(long tick) {
        long total = arrivesTick - departedTick;
        if (total <= 0) {
            return 1.0;
        }
        return Math.min(1.0, Math.max(0.0, (double) (tick - departedTick) / total));
    }

    public String remainingText(long tick) {
        long minutes = ticksLeft(tick) * WorldClock.MINUTES_PER_TICK;
        return String.format("%d시간 %02d분", minutes / 60, minutes % 60);
    }
}
