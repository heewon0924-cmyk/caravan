package com.caravan.rumor;

/**
 * 소문의 <b>정확도</b> — 맞다면 얼마나 크게 맞는가.
 *
 * <p>{@link Source}(신뢰도)와 혼동하면 안 된다. 이 구분이 정보 시스템의 전부다
 * (docs/04-정보.md 2장).
 *
 * <ul>
 *   <li>정확도 = <b>맞다면 얼마나 크게 맞는가</b> (밀값이 10% 오르나 80% 오르나)</li>
 *   <li>신뢰도 = <b>맞을 확률이 얼마인가</b></li>
 * </ul>
 *
 * <p>신뢰도 90% · 정확도 약함인 소문과 신뢰도 40% · 정확도 강함인 소문은
 * 완전히 다른 물건이다. 전자는 안전한 소액, 후자는 도박이다.
 * <b>플레이어가 이 둘을 저울질하는 것이 이 게임의 판단이다.</b>
 */
public enum Strength {

    // 5단계에서 실측해 낮춘 값이다. 원래 0.15/0.35/0.70 이었는데, 사건이 몇 시간 안에
    // 실제로 만드는 가격 변화보다 한참 컸다. 과장된 정확도를 믿을 만한 출처가
    // 증폭하면 크게 틀리게 되어, 신뢰도가 높을수록 손해를 보는 일이 벌어졌다
    // (docs/25 3장 (다)).
    약함("조금", 0.08),
    보통("꽤", 0.20),
    강함("크게", 0.40);

    private final String label;
    private final double magnitude;

    Strength(String label, double magnitude) {
        this.label = label;
        this.magnitude = magnitude;
    }

    /** 대략 이 정도 비율로 움직인다는 뜻. */
    public double magnitude() {
        return magnitude;
    }

    public String label() {
        return label;
    }
}
