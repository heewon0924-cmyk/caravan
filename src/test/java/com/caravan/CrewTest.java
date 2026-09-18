package com.caravan;

import com.caravan.app.Company;
import com.caravan.app.Simulation;
import com.caravan.data.WorldData;
import com.caravan.people.Person;
import com.caravan.people.Training;
import com.caravan.people.TransferResult;
import com.caravan.rumor.Source;
import com.caravan.trade.TradeRefused;
import com.caravan.travel.Departure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 캐러밴에 탄 사람들. docs/05-캐러밴.md 2장, docs/06-인물과-직업.md 3장. */
class CrewTest {

    private final WorldData data = WorldData.load();

    private Company company(String city) {
        return new Simulation(data, 42).newCompany("시험 상단", city);
    }

    @Test
    @DisplayName("여섯 칸이다 — 하나를 더 넣으려면 하나를 빼야 한다")
    void 여섯_칸이다() {
        Company company = company("harn");
        assertThat(company.crew().slots()).isEqualTo(6);

        for (int i = 0; i < 6; i++) {
            company.hire(0);
        }
        assertThat(company.crew().isFull()).isTrue();

        assertThatThrownBy(() -> company.hire(0))
                .isInstanceOf(TradeRefused.class)
                .hasMessageContaining("자리가 없다");
    }

    @Test
    @DisplayName("상재가 높은 사람을 태우면 거래세가 깎인다")
    void 상재가_거래세를_깎는다() {
        Company company = company("harn");
        double before = company.effectiveTaxRate();
        assertThat(before).isEqualTo(data.rules().tradeTaxRate());

        for (int i = 0; i < 6; i++) {
            company.hire(0);
        }
        assertThat(company.effectiveTaxRate()).isLessThan(before);
    }

    @Test
    @DisplayName("인내가 높은 사람을 태우면 더 싣는다")
    void 인내가_적재를_늘린다() {
        Company company = company("harn");
        double before = company.effectiveCapacity();

        for (int i = 0; i < 6; i++) {
            company.hire(0);
        }
        assertThat(company.effectiveCapacity()).isGreaterThan(before);
        assertThat(company.roomFor("wheat")).isGreaterThan(before);
    }

    @Test
    @DisplayName("정보 역할을 가진 사람을 태우면 정보 채널이 올라간다 — 5단계가 남긴 답")
    void 정보는_캐러밴_한_칸으로_산다() {
        // docs/25 3장 (라) — 믿을 만한 정보는 돈으로 살 수 없다. 왕복 한 번 이익이
        // 600 G 인데 정보 한 조각 값어치가 65 G 라 가격을 맞출 수가 없었다.
        // 답은 자리로 값을 치르는 것이다. 정보원을 태우면 호위나 상인을 한 명 포기해야 한다.
        Company company = company("selia");
        assertThat(company.informationChannel()).isEqualTo(Source.떠도는말);

        Person scout = grow(company, "scout");
        assertThat(scout.has("정보")).isTrue();
        assertThat(company.informationChannel()).isEqualTo(Source.정보원);
        assertThat(company.informationChannel().trust())
                .isGreaterThan(Source.떠도는말.trust());
    }

    @Test
    @DisplayName("수련처가 없는 도시에서는 전직할 수 없다 — 첫 전직을 하려면 떠나야 한다")
    void 하른에서는_전직할_수_없다() {
        Company company = company("harn");
        Person person = company.hire(0);

        assertThatThrownBy(() -> company.beginTransfer(person.id(), null, false))
                .isInstanceOf(TradeRefused.class)
                .hasMessageContaining("수련처가 없다");
    }

    @Test
    @DisplayName("전직은 도시에 머무는 동안에만 진행된다 — 이동 중에는 멈춘다")
    void 이동_중에는_전직이_멈춘다() {
        Simulation sim = new Simulation(data, 42);
        Company company = sim.newCompany("시험", "karden");
        Person person = company.hire(0);

        Training training = company.beginTransfer(person.id(), null, false);
        sim.advanceHours(2);
        double atCity = training.progress(sim.tick());
        assertThat(atCity).isPositive();

        Departure out = company.departVia(company.route("산기슭길"));
        sim.advanceTo(out.arrivesTick());

        // 하른에 도착해 있다. 카르덴에서 하던 전직은 그동안 멈춰 있었어야 한다.
        assertThat(company.here().id()).isEqualTo("harn");
        assertThat(company.training().progress(sim.tick()))
                .describedAs("이동 중에도 전직이 진행됐다")
                .isCloseTo(atCity, org.assertj.core.data.Offset.offset(0.05));
    }

    @Test
    @DisplayName("한 번에 한 명만 전직한다 — 그래야 '지금 누구를' 이 결정이 된다")
    void 한_번에_한_명만() {
        Company company = company("karden");
        Person first = company.hire(0);
        Person second = company.hire(0);

        company.beginTransfer(first.id(), null, false);
        assertThatThrownBy(() -> company.beginTransfer(second.id(), null, false))
                .isInstanceOf(TradeRefused.class)
                .hasMessageContaining("이미 전직 중");
    }

    @Test
    @DisplayName("전직 중인 사람은 내보낼 수 없다")
    void 전직_중인_사람은_못_내보낸다() {
        Company company = company("karden");
        Person person = company.hire(0);
        company.beginTransfer(person.id(), null, false);

        assertThatThrownBy(() -> company.dismiss(person.id()))
                .isInstanceOf(TradeRefused.class);
    }

    @Test
    @DisplayName("해적왕까지 밀어붙인다 — 6단계의 합격 기준")
    void 해적왕까지_밀어붙인다() {
        Simulation sim = new Simulation(data, 42);
        Company company = sim.newCompany("시험", "selia");
        company.trader().earn(500_000);

        Person person = hireSwordsman(company);
        assertThat(person).describedAs("검사가 안 나왔다").isNotNull();

        // 셀리아에서 교관을 붙이고 해적을 노린다. 빗나가면 되돌리고 다시 한다 —
        // 천장이 쌓이므로 되돌릴수록 가까워진다.
        pushTo(sim, company, person, "pirate");
        assertThat(person.job().id()).isEqualTo("pirate");

        pushTo(sim, company, person, "pirate-king");
        assertThat(person.job().id()).isEqualTo("pirate-king");

        // 각성
        company.beginTransfer(person.id(), null, false);
        runUntilDone(sim, company);
        TransferResult result = company.takeTransferResult();

        assertThat(result.isAwakening()).isTrue();
        assertThat(person.displayName()).isEqualTo("해적왕 리안");
        assertThat(result.headline()).contains("리안");
    }

    // ── 도구 ──────────────────────────────────────────────

    private Person hireSwordsman(Company company) {
        for (int round = 0; round < 40; round++) {
            for (int i = 0; i < company.candidates().size(); i++) {
                if (company.candidates().get(i).jobSpec().id().equals("swordsman")) {
                    return company.hire(i);
                }
            }
            // 원하는 사람이 없으면 아무나 뽑았다가 내보낸다
            Person throwaway = company.hire(0);
            company.dismiss(throwaway.id());
        }
        return null;
    }

    /** 목표에 닿을 때까지 전직하고, 빗나가면 되돌린다. */
    private void pushTo(Simulation sim, Company company, Person person, String target) {
        for (int attempt = 0; attempt < 60; attempt++) {
            company.beginTransfer(person.id(), target, true);
            runUntilDone(sim, company);
            company.takeTransferResult();

            if (person.job().id().equals(target)) {
                return;
            }
            if (person.canRevert()) {
                company.revert(person.id());
            }
        }
        throw new AssertionError(target + " 에 닿지 못했다: 지금 " + person.job().id());
    }

    private void runUntilDone(Simulation sim, Company company) {
        for (int i = 0; i < 20_000 && company.training() != null; i++) {
            sim.advanceTo(sim.tick() + 1);
        }
    }

    /** 이 직업이 될 때까지 키운다. 시험에서 특정 역할이 필요할 때 쓴다. */
    private Person grow(Company company, String targetJob) {
        Simulation sim = new Simulation(data, 42);
        Company fresh = sim.newCompany("시험", "selia");
        fresh.trader().earn(500_000);
        Person person = null;
        for (int i = 0; i < fresh.candidates().size(); i++) {
            if (fresh.candidates().get(i).jobSpec().id().equals("apprentice")) {
                person = fresh.hire(i);
                break;
            }
        }
        if (person == null) {
            person = fresh.hire(0);
        }
        // 직접 승격시킨다 — 여기서 재는 것은 역할이 채널을 올리는가이지 확률이 아니다
        person.transitionTo(new com.caravan.people.Job(data.job(targetJob)), targetJob, 500);
        company.crew().members();
        company.hire(0);
        company.dismiss(company.crew().members().get(0).id());
        company.crew();
        // 이 인물을 시험 대상 상단에 태운다
        addTo(company, person);
        return person;
    }

    private void addTo(Company company, Person person) {
        company.crew().members();
        try {
            java.lang.reflect.Field field = Company.class.getDeclaredField("crew");
            field.setAccessible(true);
            ((com.caravan.people.Crew) field.get(company)).add(person);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
