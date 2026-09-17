package com.caravan.rumor;

import com.caravan.world.WorldClock;

/**
 * 소문 하나.
 *
 * <p>이 게임에서 정보는 자원이다. 그러려면 <b>모르는 게 있어야</b> 한다 —
 * 그래서 다른 도시의 시세를 실시간으로 보여주지 않았고(docs/02 5장),
 * 그 빈칸이 소문이 팔릴 자리다.
 *
 * <p><b>{@code truth} 는 서버만 안다.</b> 클라이언트로 내려가는 DTO 에 이 값이 들어가면
 * 정보 시스템 전체가 무의미해진다 (docs/31 3장). 이 필드를 쓰는 곳은 서버 내부의
 * 판정과 테스트뿐이다.
 *
 * @param rising  값이 오른다는 소문이면 true, 내린다면 false
 * @param forward <b>아직 일어나지 않은 일</b>에 대한 말인가 (예고된 사건).
 *                이게 소문의 진짜 값어치다 — 이미 벌어진 일은 그 도시에 가보면
 *                눈으로 알 수 있지만, 앞일은 들어야만 안다
 * @param effectFrom  이 일이 값에 나타나기 시작하는 시각
 * @param effectUntil 이 일의 효과가 끝나는 시각
 * @param truth   실제로 참인가. <b>절대 클라이언트로 내려보내지 않는다</b>
 */
public record Rumor(long id,
                    String cityId,
                    String goodsId,
                    boolean rising,
                    Strength strength,
                    Source source,
                    boolean forward,
                    long heardTick,
                    long effectFrom,
                    long effectUntil,
                    long expiresTick,
                    String text,
                    boolean truth) {

    public boolean isExpired(long tick) {
        return tick >= expiresTick;
    }

    /** 들은 지 얼마나 됐는가. 정보에는 유통기한이 있다. */
    public long ageTicks(long tick) {
        return tick - heardTick;
    }

    public String ageText(long tick) {
        long minutes = ageTicks(tick) * WorldClock.MINUTES_PER_TICK;
        if (minutes < 60) {
            return minutes + "분 전";
        }
        return String.format("%d시간 전", minutes / 60);
    }

    /**
     * 플레이어가 보는 한 줄. <b>참인지 거짓인지는 들어가지 않는다.</b>
     */
    public String display(long tick) {
        return String.format("%s%s  [%s · %s]",
                forward ? "(예고) " : "", text, source.label(), ageText(tick));
    }

    /**
     * 이 소문을 믿고 움직일 때 추정치를 얼마나 옮길 것인가.
     *
     * <p><b>신뢰도를 제곱해서 쓴다.</b> 기댓값만 보면 신뢰도를 한 번만 곱하는 게 맞지만,
     * 상인은 여러 후보 중 <b>가장 좋아 보이는 하나를 고른다.</b> 최댓값을 고르는 판단에
     * 잡음을 섞으면 잡음이 부풀린 쪽이 뽑히게 되어, 기댓값이 맞아도 실현 손익은 나빠진다.
     * 5단계에서 이것 때문에 소문을 듣는 상단이 오히려 7% 적게 벌었다 (docs/25 3장).
     *
     * <p>제곱하면 못 믿을 말은 판단을 거의 못 흔들고 믿을 만한 말만 남는다.
     *
     * <pre>
     *   떠도는말 0.25 → 0.06    주점 0.40 → 0.16
     *   정보상  0.70 → 0.49    길드공시 0.95 → 0.90
     * </pre>
     *
     * <p>플레이어는 이 계산을 스스로 한다 — 화면에는 출처와 표현만 나온다.
     * 여기 있는 건 시험용 자동 상인이 쓰는 것이다.
     */
    public double expectedShift() {
        double reliability = source.trust() * source.trust();
        return (rising ? 1 : -1) * strength.magnitude() * reliability;
    }

    /**
     * {@code atTick} 에 이 일이 값에 나타나 있는가.
     *
     * <p><b>정보에는 "언제" 가 들어 있어야 쓸모가 있다.</b> 예고는 하루 뒤의 일인데
     * 캐러밴 왕복은 세 시간이다. 도착했을 때 아직 시작도 안 한 사건을 믿고 사면
     * 그냥 비싸게 사는 것이다 — 5단계에서 이것 때문에 소문을 듣는 상단이 손해를 봤다
     * (docs/25 3장).
     */
    public boolean appliesAt(long atTick) {
        return atTick >= effectFrom && atTick < effectUntil;
    }
}
