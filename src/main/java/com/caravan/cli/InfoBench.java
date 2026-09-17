package com.caravan.cli;

import com.caravan.app.Company;
import com.caravan.app.ScriptedTrader;
import com.caravan.app.Simulation;
import com.caravan.data.WorldData;
import com.caravan.rumor.Source;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 정보가 값을 하는지 잰다. docs/04-정보.md 1장의 합격 기준.
 *
 * <blockquote>소문을 듣고 움직인 플레이어가, 안 듣고 움직인 플레이어보다
 * 통계적으로 더 번다.</blockquote>
 *
 * <p>소문 채널 말고는 모든 것이 똑같은 두 상단을 같은 시드의 세계에 각각 풀어놓고
 * 자산을 비교한다. <b>5단계에서는 이 기준을 채우지 못했다</b> — 이유는
 * docs/25-5단계-기록.md 에 적었다. 이 명령은 그걸 다시 재보기 위한 것이다.
 *
 * <pre>
 *   caravan info [--days 20] [--seeds 40] [--channel 정보원]
 * </pre>
 */
public final class InfoBench {

    private final PrintStream out;

    public InfoBench(PrintStream out) {
        this.out = out;
    }

    public void run(WorldData data, int days, int seedCount, Source channel) {
        out.printf("%n══ 정보의 값어치 ══%n");
        out.printf("  소문을 듣는 상단(%s) vs 안 듣는 상단(%s)%n",
                channel.label(), Source.떠도는말.label());
        out.printf("  세계 %d일 · 시드 %d개%n", days, seedCount);

        double informedTotal = 0;
        double deafTotal = 0;
        int wins = 0;
        List<Double> gaps = new ArrayList<>();

        for (int i = 0; i < seedCount; i++) {
            long seed = 1000L + i * 977L;
            double informed = run(data, seed, days, channel, true);
            double deaf = run(data, seed, days, Source.떠도는말, false);
            informedTotal += informed;
            deafTotal += deaf;
            gaps.add(informed / deaf - 1);
            if (informed > deaf) {
                wins++;
            }
        }
        gaps.sort(Double::compareTo);

        out.println();
        out.printf("  이긴 세계     %d / %d  (%.0f%%)%n", wins, seedCount, wins * 100.0 / seedCount);
        out.printf("  자산 합계     %s G  vs  %s G   (%+.1f%%)%n",
                Text.money(informedTotal), Text.money(deafTotal),
                (informedTotal / deafTotal - 1) * 100);
        out.printf("  차이 중앙값   %+.1f%%%n", gaps.get(seedCount / 2) * 100);
        out.printf("  하위 25%%      %+.1f%%       상위 25%%  %+.1f%%%n",
                gaps.get(seedCount / 4) * 100, gaps.get(seedCount * 3 / 4) * 100);
        out.println();
        out.println("  세계끼리의 편차가 정보의 효과보다 훨씬 커서 지금 규모로는 판정이 안 된다.");
        out.println("  docs/25-5단계-기록.md 3장 참조.");
    }

    private double run(WorldData data, long seed, int days, Source channel, boolean useRumors) {
        Simulation sim = new Simulation(data, seed);
        Company company = sim.newCompany(useRumors ? "소문을 듣는 상단" : "귀머거리 상단", "harn");
        company.setInformationChannel(channel);
        ScriptedTrader trader = new ScriptedTrader(company, data, useRumors);

        for (int i = 0; i < days * 144; i++) {
            sim.advanceTo(sim.tick() + 1);
            trader.step();
        }
        return company.netWorth();
    }
}
