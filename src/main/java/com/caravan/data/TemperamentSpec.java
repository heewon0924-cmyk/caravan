package com.caravan.data;

/**
 * NPC 성격 하나. {@code resources/data/world.yml} 에서 읽는다.
 *
 * <p>성격을 나누는 이유는 세계를 다양하게 보이려는 게 아니라 <b>차익을 전부
 * 먹어치우지 않게</b> 하려는 것이다. 모두가 과감하면 플레이어에게 남는 게 없고,
 * 모두가 소심하면 잉여가 쌓여 가격이 바닥에 붙는다.
 *
 * @param count     이 성격의 상인 수
 * @param minMargin 추정 마진이 이만큼은 돼야 움직인다. <b>밸런스에서 가장 민감한 값</b>
 * @param maxDanger 이보다 위험한 노선은 타지 않는다
 * @param bite      한 도시 재고의 이 비율까지만 사들인다 — 시장을 통째로 비우지 않는다
 */
public record TemperamentSpec(String name,
                              int count,
                              double minMargin,
                              double maxDanger,
                              double bite) {

    public TemperamentSpec {
        if (count < 0) {
            throw new IllegalArgumentException(name + " 의 인원이 음수다: " + count);
        }
        if (minMargin < 0) {
            throw new IllegalArgumentException(name + " 의 최소 마진이 음수다: " + minMargin);
        }
        if (maxDanger < 0 || maxDanger > 1) {
            throw new IllegalArgumentException(name + " 의 위험 허용치가 0~1 이 아니다: " + maxDanger);
        }
        if (bite <= 0 || bite > 1) {
            throw new IllegalArgumentException(name + " 의 매수 비율이 0~1 이 아니다: " + bite);
        }
    }
}
