package com.caravan;

import com.caravan.data.JobSpec;
import com.caravan.data.NamedSpec;
import com.caravan.data.WorldData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 직업 트리가 성립하는가. docs/06-인물과-직업.md 4장, docs/07-전직.md 1장. */
class JobTreeTest {

    private final WorldData data = WorldData.load();

    @Test
    @DisplayName("막다른 길이 없다 — 하나라도 있으면 그 갈래가 나온 순간이 '실패' 가 된다")
    void 막다른_길이_없다() {
        // docs/07 1장 — 모든 갈래에 쓸모가 있어야 한다. 용병이 쓰레기면
        // 용병이 나온 건 실패고, 문구를 아무리 예쁘게 써도 플레이어는 안다.
        for (JobSpec job : data.jobs()) {
            if (job.tier() >= 3) {
                continue;
            }
            assertThat(job.nextSteps())
                    .describedAs("%s 에서 갈 곳이 없다", job.name())
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("기초 직업이 전투 계열만 있지 않다 — 6칸이 의미를 가지려면")
    void 기초_직업이_한_갈래가_아니다() {
        // 검사만 있으면 모든 인물이 같은 출발점이 되어 전투 외의 성장 경로가 없어진다.
        // 그러면 docs/05 의 6칸이 의미를 잃는다.
        Set<String> roles = new LinkedHashSet<>();
        data.startingJobs().forEach(j -> roles.addAll(j.rolesOrEmpty()));

        assertThat(data.startingJobs()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(roles).contains("전투", "상인", "운송");
    }

    @Test
    @DisplayName("모든 계열에 정점이 있다")
    void 모든_계열에_정점이_있다() {
        Set<String> families = new LinkedHashSet<>();
        Set<String> peaked = new LinkedHashSet<>();
        for (JobSpec job : data.jobs()) {
            if (job.tier() == 2) families.add(job.family());
            if (job.tier() == 3) peaked.add(job.family());
        }
        assertThat(peaked).containsAll(families);
    }

    @Test
    @DisplayName("고유 캐릭터는 최종 직업에서만 나온다")
    void 각성은_정점에서만() {
        for (JobSpec job : data.jobs()) {
            if (job.canAwaken()) {
                assertThat(job.tier())
                        .describedAs("%s 은 3티어가 아닌데 각성한다", job.name())
                        .isEqualTo(3);
                assertThat(job.isFinal()).isTrue();
            }
        }
        assertThat(data.namedCharacters()).hasSize(2);
    }

    @Test
    @DisplayName("리안과 블래스티는 비교할 수 없다 — 잘하는 것이 다르다")
    void 고유_캐릭터는_서로_비교되지_않는다() {
        // docs/06 5장 — 최종 캐릭터는 숫자가 큰 캐릭터가 아니라 플레이 방식을
        // 바꾸는 캐릭터다. 절대적으로 강한 캐릭터가 있으면 편성이 죽는다.
        NamedSpec lian = data.namedCharacter("lian");
        NamedSpec blasty = data.namedCharacter("blasty");

        // 블래스티가 더 세게 때리고, 리안이 더 넓게 쓰인다
        assertThat(blasty.stat("might")).isGreaterThan(lian.stat("might"));
        assertThat(lian.rolesOrEmpty()).hasSizeGreaterThan(blasty.rolesOrEmpty().size());

        // 둘 다 쓸모없어지는 상황이 분명히 있다
        assertThat(lian.portTaxRelief()).isNotNull();          // 항구가 없으면 무의미
        assertThat(blasty.landAmbushRelief()).isNotNull();     // 육로가 없으면 무의미
    }

    @Test
    @DisplayName("고유 캐릭터는 같은 티어 직업보다 역할을 더 많이 맡는다 — 칸을 아껴준다")
    void 고유_캐릭터는_칸을_아껴준다() {
        // docs/05 2장 — 고유 캐릭터의 강함은 숫자가 아니라 '자리' 다.
        NamedSpec lian = data.namedCharacter("lian");
        assertThat(lian.rolesOrEmpty()).contains("전투", "운송", "특수");
    }
}
