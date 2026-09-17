package com.caravan.rumor;

/**
 * 소문의 출처. 이게 <b>신뢰도</b>를 정한다.
 *
 * <p>플레이어에게 정확한 퍼센트를 보여주지 않고 출처만 보여준다
 * (docs/04-정보.md 2장). 숫자를 주면 기댓값 계산기가 되고, 출처를 주면
 * <b>"주점 얘기는 반은 거짓이더라"</b> 는 경험적 지식이 쌓인다. 후자가 낫다.
 */
public enum Source {

    /** 그 도시에 있었다. 눈으로 봤다. */
    직접본것(1.00, "확실하다"),

    /** 상인 길드 공시. */
    길드공시(0.95, "길드 공시"),

    /** 우리 정보원이 가져왔다. 6단계에서 인물이 붙는다. */
    정보원(0.85, "우리 정보원"),

    /** 돈 주고 샀다. */
    정보상(0.70, "정보상"),

    /** 주점에서 들었다. */
    주점(0.40, "술자리 얘기"),

    /** 그냥 떠도는 말. */
    떠도는말(0.25, "소문");

    private final double trust;
    private final String label;

    Source(double trust, String label) {
        this.trust = trust;
        this.label = label;
    }

    /** 이 출처의 말이 참일 확률. <b>플레이어에게 숫자로 보여주지 않는다.</b> */
    public double trust() {
        return trust;
    }

    /** 플레이어가 보는 문구. */
    public String label() {
        return label;
    }
}
