package com.caravan.data;

import java.util.List;
import java.util.Map;

/**
 * 고유 캐릭터 정의. 직업 트리의 끝에서 이름을 얻는다.
 *
 * <p>docs/06-인물과-직업.md 5장 — <b>최종 캐릭터는 숫자가 큰 캐릭터가 아니라
 * 플레이 방식을 바꾸는 캐릭터다.</b> 설계할 때마다 반드시 답한다:
 * "이 캐릭터가 쓸모없어지는 상황은 언제인가?"
 *
 * @param portTaxRelief    항구 도시 거래세를 이만큼 깎는다
 * @param landAmbushRelief 육로 습격 확률을 이 비율만큼 낮춘다 (7단계에서 쓴다)
 */
public record NamedSpec(String id,
                        String name,
                        String title,
                        Map<String, Integer> stats,
                        List<String> roles,
                        String trait,
                        String traitText,
                        String skill,
                        String skillText,
                        Double portTaxRelief,
                        Double landAmbushRelief) {

    public String fullName() {
        return title + " " + name;
    }

    public int stat(String key) {
        return stats == null ? 0 : stats.getOrDefault(key, 0);
    }

    public List<String> rolesOrEmpty() {
        return roles == null ? List.of() : roles;
    }
}
