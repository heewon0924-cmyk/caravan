package com.caravan.data;

import java.util.List;

/**
 * 전직 규칙. {@code resources/data/world.yml} 의 {@code transfer:} 에서 읽는다.
 *
 * <p>docs/07-전직.md. <b>밸런스에서 가장 조심해야 할 숫자들</b>이라 전부 데이터로 둔다.
 * 잘 되면 이 게임의 얼굴이 되고 잘못되면 플레이어를 쫓아낸다.
 */
public record TransferRules(double hoursTier2,
                            double hoursTier3,
                            double hoursAwaken,
                            double costTier2,
                            double costTier3,
                            double costAwaken,
                            double tutorSurcharge,
                            double tutorWeightBonus,
                            double statMatchBonus,
                            double statMismatchPenalty,
                            List<Double> pityWeights,
                            double revertTimeRatio,
                            double revertCostRatio,
                            double revertCostGrowth) {

    public TransferRules {
        if (pityWeights == null || pityWeights.isEmpty()) {
            throw new IllegalArgumentException("천장 가중치가 비어 있다");
        }
        if (statMatchBonus < 1) {
            throw new IllegalArgumentException("능력치 보정은 1 이상이어야 한다: " + statMatchBonus);
        }
        if (statMismatchPenalty <= 0 || statMismatchPenalty > 1) {
            throw new IllegalArgumentException("어긋남 보정은 0~1 이어야 한다: " + statMismatchPenalty);
        }
    }

    /** {@code tier} 로 올라가는 데 드는 세계 시간. */
    public double hoursTo(int tier) {
        return tier >= 3 ? hoursTier3 : hoursTier2;
    }

    public double costTo(int tier) {
        return tier >= 3 ? costTier3 : costTier2;
    }

    /** 같은 목표를 {@code missed} 번 놓친 인물에게 걸리는 천장 배수. */
    public double pity(int missed) {
        return pityWeights.get(Math.min(missed, pityWeights.size() - 1));
    }
}
