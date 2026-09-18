package com.caravan.people;

/**
 * 인물의 능력치. <b>네 개뿐이다.</b>
 *
 * <p>docs/06-인물과-직업.md 3장 — 각 능력치는 {@code docs/00} 의 네 가지 결정에
 * 하나씩 대응한다.
 *
 * <pre>
 *   무력 → 어느 길로 갈 것인가   (전투력, 습격 방어)
 *   통찰 → 무엇을 살 것인가     (소문 신뢰도, 소문 획득)
 *   상재 → 무엇을 살 것인가     (거래세, 적분 가격 완화)
 *   인내 → 누구를 데려갈 것인가 (적재 용량, 이동 속도)
 * </pre>
 *
 * <p>능력치를 늘리고 싶은 유혹이 계속 생길 텐데, <b>늘리기 전에 그 능력치가 어떤
 * 결정을 바꾸는지 먼저 답한다.</b> 답이 없으면 그건 화면을 채우는 숫자지 게임이 아니다.
 */
public record Stats(int might, int insight, int commerce, int grit) {

    public static final Stats ZERO = new Stats(0, 0, 0, 0);

    public Stats plus(Stats other) {
        return new Stats(might + other.might, insight + other.insight,
                commerce + other.commerce, grit + other.grit);
    }

    public Stats minus(Stats other) {
        return new Stats(might - other.might, insight - other.insight,
                commerce - other.commerce, grit - other.grit);
    }

    public int total() {
        return might + insight + commerce + grit;
    }

    /** 가장 높은 능력치. 전직 보정에서 "이 사람이 어느 쪽 사람인가" 를 볼 때 쓴다. */
    public String dominant() {
        int max = Math.max(Math.max(might, insight), Math.max(commerce, grit));
        if (max == might) return "무력";
        if (max == insight) return "통찰";
        if (max == commerce) return "상재";
        return "인내";
    }

    @Override
    public String toString() {
        return String.format("무력 %d · 통찰 %d · 상재 %d · 인내 %d",
                might, insight, commerce, grit);
    }
}
