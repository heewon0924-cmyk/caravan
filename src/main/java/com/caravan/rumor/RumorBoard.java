package com.caravan.rumor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 플레이어(또는 상인)가 모아둔 소문.
 *
 * <p>유효기간이 지난 것은 버린다. <b>정보에는 유통기한이 있다</b> —
 * 사실이었지만 이미 반영된 소문이 제일 고약하고, 그래서 제일 좋은 교보재다
 * (docs/04-정보.md 3장).
 */
public final class RumorBoard {

    private final List<Rumor> heard = new ArrayList<>();

    public void add(Rumor rumor) {
        // 같은 도시·품목·방향의 소문은 더 믿을 만한 쪽만 남긴다.
        // 같은 얘기를 여러 번 듣는 것 자체가 신호이긴 하지만,
        // 목록이 같은 말로 도배되면 읽지 않게 된다.
        heard.removeIf(r -> r.cityId().equals(rumor.cityId())
                && r.goodsId().equals(rumor.goodsId())
                && r.rising() == rumor.rising()
                && r.source().trust() <= rumor.source().trust());
        heard.add(rumor);
    }

    /** 아직 유효한 소문들. 믿을 만한 것부터. */
    public List<Rumor> current(long tick) {
        heard.removeIf(r -> r.isExpired(tick));
        List<Rumor> sorted = new ArrayList<>(heard);
        sorted.sort(Comparator.comparingDouble((Rumor r) -> -r.source().trust())
                .thenComparingLong(r -> -r.heardTick()));
        return sorted;
    }

    /**
     * 이 도시·품목에 대해 들은 말을 하나로 합친 기대 변화율.
     *
     * <p>여러 소문이 같은 말을 하면 더 믿고, 엇갈리면 상쇄된다.
     * 상한을 둬서 소문 몇 개로 값이 두 배가 될 거라 믿지는 않게 한다.
     *
     * @param memoryAgeTicks 그 도시를 마지막으로 본 지 얼마나 됐는가.
     *        <b>이게 중요하다.</b> 방금 보고 온 도시에 대해서는 이미 벌어진 일을
     *        말하는 소문이 쓸모없다 — 눈으로 본 것과 중복되어 이중 계산이 된다.
     *        반면 <b>앞일에 대한 소문(예고)은 언제나 값지다.</b> 아무리 최근에
     *        다녀왔어도 앞으로 일어날 일은 가봐서 알 수 없다.
     */
    public double expectedShift(String cityId, String goodsId, long tick, long memoryAgeTicks) {
        return expectedShiftAt(cityId, goodsId, tick, tick, memoryAgeTicks);
    }

    /**
     * {@code atTick} 에 그 도시에 도착했을 때 값이 얼마나 달라져 있을 것인가.
     *
     * <p><b>도착 시각을 넣는 것이 핵심이다.</b> 내일 일어날 일을 오늘 알아도,
     * 세 시간 뒤에 도착해서 팔면 아무 소용이 없다. 정보를 쓰려면 "언제 거기 있을
     * 것인가" 와 맞춰봐야 한다 — 그게 docs/03 6.2 의 "지금 출발하면 언제 도착하는가"
     * 가 실제로 판단이 되는 지점이다.
     */
    public double expectedShiftAt(String cityId, String goodsId,
                                  long tick, long atTick, long memoryAgeTicks) {
        double staleness = Math.min(1.0, memoryAgeTicks / (double) FRESH_MEMORY_TICKS);
        double sum = 0;
        for (Rumor r : current(tick)) {
            if (!r.cityId().equals(cityId) || !r.goodsId().equals(goodsId)) {
                continue;
            }
            if (!r.appliesAt(atTick)) {
                continue;
            }
            sum += r.forward() ? r.expectedShift() : r.expectedShift() * staleness;
        }
        return Math.max(-0.6, Math.min(0.8, sum));
    }

    /** 기억이 이보다 묵으면 현재에 대한 소문도 온전히 값지다. 세계 시간 12시간. */
    private static final long FRESH_MEMORY_TICKS = 12 * 60 / com.caravan.world.WorldClock.MINUTES_PER_TICK;

    public int size(long tick) {
        return current(tick).size();
    }

    public boolean isEmpty(long tick) {
        return current(tick).isEmpty();
    }
}
