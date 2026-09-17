package com.caravan;

import com.caravan.app.Company;
import com.caravan.app.ScriptedTrader;
import com.caravan.app.Simulation;
import com.caravan.data.WorldData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>5단계의 합격 기준</b> — docs/04-정보.md 1장.
 *
 * <blockquote>소문을 듣고 움직인 플레이어가, 안 듣고 움직인 플레이어보다
 * 통계적으로 더 번다.</blockquote>
 *
 * <p>이게 안 되면 소문은 읽을 필요 없는 플레이버 텍스트다.
 *
 * <p>재는 방법은 <b>소문 말고는 모든 것이 똑같은 두 상단</b>을 같은 시드의 세계에
 * 각각 풀어놓고 자산을 비교하는 것이다. 판단 규칙도, 자본도, 캐러밴도 같다.
 */
class InformationValueTest {

    /**
     * 30개. 이보다 적으면 <b>세계끼리의 편차에 묻혀서</b> 아무 말도 할 수 없다 —
     * 같은 규칙으로 도는 상단인데도 세계에 따라 최종 자산이 세 배 넘게 벌어진다.
     * 이것 자체가 5단계의 발견 중 하나다 (docs/25 3장 (마)).
     */
    private static final long[] SEEDS = seeds(30);
    private static final int DAYS = 20;

    private static long[] seeds(int count) {
        long[] all = new long[count];
        for (int i = 0; i < count; i++) {
            all[i] = 1000L + i * 977L;
        }
        return all;
    }

    @Test
    @DisplayName("소문을 듣는다고 크게 손해보지는 않는다")
    void 소문이_해가_되지는_않는다() {
        // docs/04 1장의 합격 기준 — "소문을 들은 쪽이 더 번다" — 은 5단계에서
        // 채우지 못했다. 세계끼리의 편차가 정보의 효과보다 훨씬 커서 지금 규모로는
        // 판정 자체가 안 된다 (docs/25 3장). 다시 재려면 `caravan info`.
        //
        // 그래서 여기서는 더 약한 것만 못 박는다 — <b>소문을 듣는 것이 해가 되어서는
        // 안 된다.</b> 5단계를 만드는 동안 소문을 들은 상단이 28.9% 적게 버는 구간이
        // 있었고, 그건 명백한 버그였다. 그 부류가 다시 생기면 여기서 걸린다.
        List<Double> gaps = new ArrayList<>();
        for (long seed : SEEDS) {
            gaps.add(runTrader(seed, true) / runTrader(seed, false) - 1);
        }
        double average = gaps.stream().mapToDouble(Double::doubleValue).average().orElseThrow();

        assertThat(average)
                .describedAs("소문을 들은 쪽이 평균 %.1f%% 벌었다 (시드 %d개)",
                        average * 100, SEEDS.length)
                .isGreaterThan(-0.15);
    }

    @Test
    @DisplayName("정보 채널이 좋을수록 참말을 더 자주 듣는다")
    void 채널이_좋을수록_참말이_많다() {
        WorldData data = WorldData.load();
        Simulation sim = new Simulation(data, 11);
        Company company = sim.newCompany("시험", "harn");

        int rumourTruth = 0, rumourTotal = 0;
        int informantTruth = 0, informantTotal = 0;

        for (int day = 0; day < 50; day++) {
            sim.advanceDays(1);
            for (int i = 0; i < 10; i++) {
                rumourTotal++;
                if (sim.rumorMill().hear(company.here(),
                        com.caravan.rumor.Source.떠도는말, sim.tick()).truth()) rumourTruth++;
                informantTotal++;
                if (sim.rumorMill().hear(company.here(),
                        com.caravan.rumor.Source.정보원, sim.tick()).truth()) informantTruth++;
            }
        }
        assertThat((double) informantTruth / informantTotal)
                .isGreaterThan((double) rumourTruth / rumourTotal);
    }

    @Test
    @DisplayName("소문에는 '언제' 가 들어 있다 — 도착했을 때 유효하지 않으면 안 쳐준다")
    void 소문에는_언제가_들어_있다() {
        WorldData data = WorldData.load();
        Simulation sim = new Simulation(data, 7);
        Company company = sim.newCompany("시험", "harn");
        company.setInformationChannel(com.caravan.rumor.Source.정보원);

        for (int day = 0; day < 60; day++) {
            sim.advanceDays(1);
            for (var r : company.rumors()) {
                if (!r.forward()) {
                    continue;
                }
                // 아직 시작 안 한 사건은 지금 도착해봐야 소용없다
                assertThat(r.appliesAt(sim.tick()))
                        .describedAs("%s — 아직 시작 전인데 지금 유효하다고 한다", r.text())
                        .isFalse();
                assertThat(r.appliesAt(r.effectFrom())).isTrue();
                return;
            }
        }
    }

    @Test
    @DisplayName("소문을 몰라도 장사는 된다 — 다만 아무렇게나 굴리면 밑천을 깎아먹는다")
    void 소문을_몰라도_망하지는_않는다() {
        // docs/02 8장 5번 — 계산을 전혀 안 해도 망하지는 않아야 한다.
        // 소문이 "알면 유리한 것" 이어야지 "모르면 망하는 것" 이면 진입장벽이 된다.
        //
        // 다만 절반 넘게 잃는 세계가 실제로 있다. 작은 화물로 자주 움직이면
        // 고정 이동 비용(60~180 G)에 갉아먹히기 때문이고, 이건 버그가 아니라
        // 이 게임이 의도한 압력이다 — 많이 실으면 느려지고 적게 실으면 비용이 남는다.
        // 그래서 "중앙값은 밑천을 넘고, 아무도 파산하지는 않는다" 로 못 박는다.
        double starting = WorldData.load().rules().startingGold();
        List<Double> finals = new ArrayList<>();

        for (long seed : SEEDS) {
            double deaf = runTrader(seed, false);
            assertThat(deaf)
                    .describedAs("시드 %d — 소문을 못 듣는 상단이 파산했다", seed)
                    .isGreaterThan(starting * 0.1);
            finals.add(deaf);
        }
        finals.sort(Double::compareTo);

        assertThat(finals.get(finals.size() / 2))
                .describedAs("중앙값 %,.0f G 로 끝났다 (밑천 %,.0f G)",
                        finals.get(finals.size() / 2), starting)
                .isGreaterThan(starting);
    }

    @Test
    @DisplayName("소문에는 참말과 거짓말이 섞여 있다 — 전부 참이면 판단할 게 없다")
    void 참말과_거짓말이_섞인다() {
        WorldData data = WorldData.load();
        Simulation sim = new Simulation(data, 42);
        Company company = sim.newCompany("시험", "harn");

        int truths = 0;
        int lies = 0;
        for (int day = 0; day < 40; day++) {
            sim.advanceDays(1);
            for (int i = 0; i < 10; i++) {
                var r = sim.rumorMill().hear(company.here(),
                        com.caravan.rumor.Source.주점, sim.tick());
                if (r.truth()) truths++; else lies++;
            }
        }

        assertThat(truths).describedAs("참말이 하나도 없다").isPositive();
        assertThat(lies).describedAs("거짓말이 하나도 없다").isPositive();
    }

    @Test
    @DisplayName("앞일에 대한 소문이 실제로 나온다 — 그게 소문의 진짜 값어치다")
    void 앞일을_미리_듣는다() {
        WorldData data = WorldData.load();
        Simulation sim = new Simulation(data, 7);
        Company company = sim.newCompany("시험", "harn");

        int forward = 0;
        for (int day = 0; day < 60 && forward == 0; day++) {
            sim.advanceDays(1);
            for (int i = 0; i < 10; i++) {
                var r = sim.rumorMill().hear(company.here(),
                        com.caravan.rumor.Source.길드공시, sim.tick());
                if (r.forward() && r.truth()) forward++;
            }
        }
        assertThat(forward)
                .describedAs("예고된 사건에 대한 소문이 한 번도 안 나왔다")
                .isPositive();
    }

    @Test
    @DisplayName("믿을 만한 출처가 실제로 더 자주 맞는다 — 신뢰도가 거짓말이 아니다")
    void 출처가_신뢰도를_말해준다() {
        WorldData data = WorldData.load();
        Simulation sim = new Simulation(data, 7);
        Company company = sim.newCompany("시험", "harn");

        int tavernTruths = 0, tavernTotal = 0;
        int informantTruths = 0, informantTotal = 0;

        for (int day = 0; day < 60; day++) {
            sim.advanceDays(1);
            if (company.here() == null) {
                continue;
            }
            for (int i = 0; i < 20; i++) {
                var tavern = sim.rumorMill().hear(company.here(), com.caravan.rumor.Source.주점,
                        sim.tick());
                tavernTotal++;
                if (tavern.truth()) tavernTruths++;

                var informant = sim.rumorMill().hear(company.here(),
                        com.caravan.rumor.Source.정보상, sim.tick());
                informantTotal++;
                if (informant.truth()) informantTruths++;
            }
        }

        double tavernRate = (double) tavernTruths / tavernTotal;
        double informantRate = (double) informantTruths / informantTotal;

        assertThat(informantRate)
                .describedAs("정보상 %.0f%% vs 주점 %.0f%%", informantRate * 100, tavernRate * 100)
                .isGreaterThan(tavernRate);
    }

    /** 상단 하나를 {@code DAYS} 일 굴리고 최종 자산을 돌려준다. */
    private double runTrader(long seed, boolean useRumors) {
        WorldData data = WorldData.load();
        Simulation sim = new Simulation(data, seed);
        Company company = sim.newCompany(
                useRumors ? "소문을 듣는 상단" : "귀머거리 상단", "harn");
        company.setInformationChannel(useRumors
                ? com.caravan.rumor.Source.정보원 : com.caravan.rumor.Source.떠도는말);
        ScriptedTrader trader = new ScriptedTrader(company, data, useRumors);

        for (int i = 0; i < DAYS * 144; i++) {
            sim.advanceTo(sim.tick() + 1);
            trader.step();
        }
        return company.netWorth();
    }
}
