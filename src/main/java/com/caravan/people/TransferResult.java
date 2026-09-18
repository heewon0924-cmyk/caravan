package com.caravan.people;

import com.caravan.data.NamedSpec;

/**
 * 전직 한 번의 결과.
 *
 * <p><b>"실패" 라는 말을 쓰지 않는다.</b> 강화는 성공 아니면 실패지만 전직은 무조건
 * 무언가가 된다 — 원하는 게 아닐 수는 있어도 아무것도 아닌 상태는 없다.
 * 화면에 뜨는 문구도 "실패했습니다" 가 아니라 <b>"그는 용병이 되었다"</b> 다
 * (docs/07 1장).
 *
 * @param aimed    노렸던 직업. 안 노렸으면 {@code null}
 * @param asAimed  노린 대로 됐는가
 * @param awakened 각성이었으면 그 고유 캐릭터
 */
public record TransferResult(Person person,
                             String fromJobName,
                             String toJobName,
                             String aimed,
                             boolean asAimed,
                             NamedSpec awakened) {

    public boolean isAwakening() {
        return awakened != null;
    }

    /** 화면에 그대로 쓸 한 줄. */
    public String headline() {
        if (awakened != null) {
            return String.format("%s 은(는) %s 이 되었다.", person.name(), awakened.fullName());
        }
        return String.format("%s 은(는) %s 이(가) 되었다.", person.name(), toJobName);
    }
}
