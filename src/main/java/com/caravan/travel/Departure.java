package com.caravan.travel;

import com.caravan.data.RouteSpec;
import com.caravan.world.WorldClock;

/**
 * 출발 한 건의 내역. 견적으로도 쓰고 실제 출발 결과로도 쓴다.
 *
 * <p>플레이어가 출발 버튼을 누르기 전에 봐야 하는 것이 전부 들어 있다 —
 * <b>얼마나 걸리고, 얼마가 들고, 얼마나 위험한가.</b>
 * "지금 출발하면 언제 도착하는가" 가 중요한 판단이라(docs/03-이동과-시간.md 6.2)
 * 도착 예정 틱을 함께 준다.
 *
 * @param baseHours   빈 캐러밴 기준 소요
 * @param actualHours 적재율을 반영한 실제 소요
 * @param danger      습격 확률. 7단계에서 실제로 굴린다 — 지금은 표시만 한다
 */
public record Departure(RouteSpec route,
                        String fromId,
                        String toId,
                        double baseHours,
                        double actualHours,
                        double loadRatio,
                        double travelCost,
                        double toll,
                        double danger,
                        long departedTick,
                        long arrivesTick) {

    public double totalCost() {
        return travelCost + toll;
    }

    /** 적재 때문에 더 걸리는 시간. */
    public double slowdownHours() {
        return actualHours - baseHours;
    }

    public String arrivesAtText() {
        return WorldClock.format(arrivesTick);
    }

    public String durationText() {
        long minutes = Math.round(actualHours * 60);
        return String.format("%d시간 %02d분", minutes / 60, minutes % 60);
    }
}
