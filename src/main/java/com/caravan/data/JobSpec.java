package com.caravan.data;

import java.util.List;
import java.util.Map;

/**
 * 직업 정의. {@code resources/data/jobs.yml} 에서 읽는다.
 *
 * @param tier        1 기초 · 2 계열 · 3 정점
 * @param family      계열. 도시 보정과 이벤트 보정이 계열 단위로 걸린다
 * @param roles       캐러밴에서 맡는 역할
 * @param stats       이 직업이 되면 더해지는 능력치
 * @param transitions 다음 단계 후보와 <b>기본 가중치</b>. 퍼센트가 아니다
 * @param awakensTo   각성 후보. 최종 직업에서 고유 캐릭터가 된다
 */
public record JobSpec(String id,
                      String name,
                      int tier,
                      String family,
                      List<String> roles,
                      Map<String, Integer> stats,
                      List<Transition> transitions,
                      List<String> awakensTo) {

    public boolean isFinal() {
        return transitions == null || transitions.isEmpty();
    }

    public boolean canAwaken() {
        return awakensTo != null && !awakensTo.isEmpty();
    }

    public List<Transition> nextSteps() {
        return transitions == null ? List.of() : transitions;
    }

    public List<String> rolesOrEmpty() {
        return roles == null ? List.of() : roles;
    }

    public int stat(String key) {
        return stats == null ? 0 : stats.getOrDefault(key, 0);
    }

    /** 전직 후보 하나. {@code weight} 는 가중치이지 확률이 아니다. */
    public record Transition(String to, int weight) {
    }
}
