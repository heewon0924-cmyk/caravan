package com.caravan.people;

import com.caravan.data.JobSpec;

import java.util.List;

/**
 * 전직 후보 하나와 <b>그렇게 판단한 이유들</b>.
 *
 * <p>docs/07-전직.md 3장 — 보정 요인은 전부 공개하고 <b>합산된 최종 확률은
 * 보여주지 않는다.</b>
 *
 * <blockquote>플레이어는 이러한 정보를 수집하고 <b>스스로</b> 확률을 계산한다.</blockquote>
 *
 * <p>최종 숫자를 띄워주면 플레이어가 하는 일은 버튼을 누를지 말지 정하는 것뿐이 된다.
 * 계산을 플레이어에게 남겨두면 정보를 모으는 것이 곧 계산 정확도가 되고,
 * 정보 시스템이 전직과 직접 연결된다.
 *
 * @param weight  실제로 쓰이는 가중치. <b>화면에 내려보내지 않는다</b>
 * @param reasons "셀리아는 해양 계열에 크게 유리하다 (×3.0)" 같은 문장들
 */
public record Prospect(JobSpec target, double weight, List<String> reasons) {

    public String name() {
        return target.name();
    }
}
