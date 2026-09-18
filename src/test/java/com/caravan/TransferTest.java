package com.caravan;

import com.caravan.app.Company;
import com.caravan.app.Simulation;
import com.caravan.data.JobSpec;
import com.caravan.data.WorldData;
import com.caravan.people.Job;
import com.caravan.people.Person;
import com.caravan.people.Prospect;
import com.caravan.people.Stats;
import com.caravan.people.TransferOffice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 전직. docs/07-전직.md. <b>이 게임에서 가장 조심해서 다뤄야 할 것.</b> */
class TransferTest {

    private final WorldData data = WorldData.load();

    private Person swordsman(int might) {
        return new Person("t1", "시험 검사",
                new Stats(might, 5, 5, 5), new Job(data.job("swordsman")));
    }

    private TransferOffice office() {
        return new TransferOffice(data, 42);
    }

    @Test
    @DisplayName("기본 가중치는 해적 40 : 창잡이 40 : 용병 20 이다")
    void 기본_가중치() {
        Map<String, Integer> weights = data.job("swordsman").nextSteps().stream()
                .collect(java.util.stream.Collectors.toMap(
                        JobSpec.Transition::to, JobSpec.Transition::weight));

        assertThat(weights).containsEntry("pirate", 40)
                .containsEntry("spearman", 40)
                .containsEntry("mercenary", 20);
    }

    @Test
    @DisplayName("능력치 보정은 목표마다 따로 본다 — 무력 계열 둘은 같이 유리해진다")
    void 능력치_보정은_목표마다_따로_본다() {
        // 해적도 창잡이도 무력이 앞선 직업이라, 무력이 높은 인물은 둘 다에 유리하다.
        // 한쪽만 밀어주는 게 아니라는 뜻이다 — 그래서 "무력형" 이라는 것만으로는
        // 어느 쪽이 될지 정해지지 않고, 도시가 그걸 정한다.
        List<Prospect> prospects = office().prospects(
                new Person("t", "평범한 검사", new Stats(5, 5, 5, 5),
                        new Job(data.job("swordsman"))),
                data.city("harn"), null, false, Map.of());

        double pirate = TransferOffice.probabilityOf(prospects, "pirate");
        double spear = TransferOffice.probabilityOf(prospects, "spearman");
        double mercenary = TransferOffice.probabilityOf(prospects, "mercenary");

        assertThat(pirate).isCloseTo(spear, within(0.001));
        assertThat(mercenary).isLessThan(pirate);
        assertThat(pirate + spear + mercenary).isCloseTo(1.0, within(1e-9));
    }

    @Test
    @DisplayName("셀리아에서 무력 높은 검사에 교관을 붙이면 해적이 크게 유력해진다")
    void 문서의_계산_예() {
        List<Prospect> prospects = office().prospects(
                swordsman(30), data.city("selia"), "pirate", true, Map.of());

        double pirate = TransferOffice.probabilityOf(prospects, "pirate");

        // 셀리아 ×3.0 · 무력 ×1.5 · 교관 ×1.5 = 270
        // 창잡이도 무력 계열이라 능력치 보정을 같이 받는다(40 × 1.5 = 60) —
        // docs/07 2장의 예시는 그걸 빼고 계산해서 81.8% 로 적혀 있는데,
        // 목표마다 따로 보는 지금 구현에서는 77% 다.
        assertThat(pirate).isCloseTo(0.77, within(0.02));
        assertThat(pirate).isGreaterThan(
                TransferOffice.probabilityOf(prospects, "spearman") * 3);
    }

    @Test
    @DisplayName("도시가 계열을 정한다 — 같은 인물도 카르덴에서는 창잡이가 유력하다")
    void 도시가_계열을_정한다() {
        Person person = swordsman(30);

        double pirateInSelia = TransferOffice.probabilityOf(
                office().prospects(person, data.city("selia"), null, false, Map.of()), "pirate");
        double spearInKarden = TransferOffice.probabilityOf(
                office().prospects(person, data.city("karden"), null, false, Map.of()), "spearman");

        assertThat(pirateInSelia).isGreaterThan(0.6);
        assertThat(spearInKarden).isGreaterThan(0.6);
    }

    @Test
    @DisplayName("사건이 계열에 바람을 넣는다 — 해적 창궐 중에는 해적이 되기 쉽다")
    void 사건이_확률을_바꾼다() {
        Person person = swordsman(30);
        double plain = TransferOffice.probabilityOf(
                office().prospects(person, data.city("selia"), null, false, Map.of()), "pirate");
        double duringPirates = TransferOffice.probabilityOf(
                office().prospects(person, data.city("selia"), null, false,
                        Map.of("해양", 2.0)), "pirate");

        assertThat(duringPirates).isGreaterThan(plain);
    }

    @Test
    @DisplayName("천장이 쌓인다 — 같은 목표를 놓칠수록 그 목표가 유력해진다")
    void 천장이_쌓인다() {
        Person person = swordsman(30);
        double before = TransferOffice.probabilityOf(
                office().prospects(person, data.city("selia"), "pirate", false, Map.of()), "pirate");

        // 두 번 빗나갔다
        person.transitionTo(new Job(data.job("spearman")), "pirate", 500);
        person.revert();
        person.transitionTo(new Job(data.job("mercenary")), "pirate", 500);
        person.revert();

        assertThat(person.missedCount("pirate")).isEqualTo(2);
        double after = TransferOffice.probabilityOf(
                office().prospects(person, data.city("selia"), "pirate", false, Map.of()), "pirate");

        assertThat(after).isGreaterThan(before);
        // 확정은 주지 않는다. 확정을 주면 "몇 번만 하면 된다" 가 되어 확률이 의미를 잃는다.
        assertThat(after).isLessThan(1.0);
    }

    @Test
    @DisplayName("보이는 가중치가 실제로 쓰이는 값이다 — 숨은 보정이 없다")
    void 확률이_정직하다() {
        // docs/07 4장 — 플레이어가 조작을 의심하기 시작하면 고칠 수 있는 건 없다.
        TransferOffice office = new TransferOffice(data, 20240101);
        Person person = swordsman(30);
        List<Prospect> prospects = office.prospects(
                person, data.city("selia"), "pirate", true, Map.of());

        double expected = TransferOffice.probabilityOf(prospects, "pirate");

        int hits = 0;
        int rounds = 20_000;
        for (int i = 0; i < rounds; i++) {
            if (office.draw(prospects).id().equals("pirate")) {
                hits++;
            }
        }
        assertThat((double) hits / rounds)
                .describedAs("보이는 확률 %.3f, 실제로 나온 비율 %.3f", expected, (double) hits / rounds)
                .isCloseTo(expected, within(0.015));
    }

    @Test
    @DisplayName("전직해도 같은 사람이다 — 이름과 타고난 것은 안 변한다")
    void 전직해도_같은_사람이다() {
        Person person = swordsman(30);
        Stats innate = person.innate();
        String name = person.name();

        person.transitionTo(new Job(data.job("pirate")), "pirate", 500);

        assertThat(person.name()).isEqualTo(name);
        assertThat(person.innate()).isEqualTo(innate);
        assertThat(person.job().name()).isEqualTo("해적");
        // 직업이 준 만큼 능력치가 올라갔다
        assertThat(person.stats().might()).isGreaterThan(innate.might());
    }

    @Test
    @DisplayName("되돌리면 능력치도 돌아가지만 천장은 남는다")
    void 되돌려도_천장은_남는다() {
        Person person = swordsman(30);
        Stats before = person.stats();

        person.transitionTo(new Job(data.job("spearman")), "pirate", 500);
        assertThat(person.stats().might()).isGreaterThan(before.might());

        person.revert();

        assertThat(person.stats()).isEqualTo(before);
        assertThat(person.job().id()).isEqualTo("swordsman");
        assertThat(person.missedCount("pirate")).isEqualTo(1);
        assertThat(person.reverts()).isEqualTo(1);
    }

    @Test
    @DisplayName("되돌릴수록 비싸진다 — 무제한 무료 반복이면 확률이 의미를 잃는다")
    void 되돌리기는_점점_비싸진다() {
        Simulation sim = new Simulation(data, 42);
        Company company = sim.newCompany("시험", "karden");

        Person person = company.hire(0);
        // 되돌리기 비용만 보기 위해 직접 단계를 쌓는다
        person.transitionTo(new Job(data.job("spearman")), "pirate", 1000);
        double first = company.revert(person.id());

        person.transitionTo(new Job(data.job("spearman")), "pirate", 1000);
        double second = company.revert(person.id());

        assertThat(second).isGreaterThan(first);
        assertThat(second / first).isCloseTo(data.transfer().revertCostGrowth(), within(0.01));
    }

    @Test
    @DisplayName("각성하면 이름을 얻는다 — 해적왕이 리안이 된다")
    void 각성하면_이름을_얻는다() {
        Person person = swordsman(30);
        person.transitionTo(new Job(data.job("pirate")), "pirate", 500);
        person.transitionTo(new Job(data.job("pirate-king")), "pirate-king", 3000);

        assertThat(person.isAwakened()).isFalse();
        person.awaken(data.namedCharacter("lian"));

        assertThat(person.isAwakened()).isTrue();
        assertThat(person.displayName()).isEqualTo("해적왕 리안");
        assertThat(person.has("특수")).isTrue();
    }
}
