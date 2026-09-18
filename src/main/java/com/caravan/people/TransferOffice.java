package com.caravan.people;

import com.caravan.data.CitySpec;
import com.caravan.data.JobSpec;
import com.caravan.data.TransferRules;
import com.caravan.data.WorldData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 전직 판정. <b>이 게임에서 가장 조심해서 다뤄야 할 것</b>이다.
 *
 * <p>랜덤 전직은 잘 되면 이 게임의 얼굴이 되고 잘못되면 플레이어를 쫓아낸다.
 * 그 차이는 확률값이 아니라 <b>원하지 않는 결과가 나왔을 때 무슨 일이 일어나는가</b>
 * 에서 갈린다 (docs/07 1장).
 *
 * <p>퍼센트를 더하지 않고 <b>가중치를 곱한 뒤 정규화한다.</b>
 *
 * <pre>
 *   W(i) = 기본가중치 × 도시보정 × 능력치보정 × 준비보정 × 이벤트보정 × 천장보정
 *   P(i) = W(i) / ΣW(j)
 * </pre>
 *
 * <p>난수는 밖에서 준 시드로만 굴린다. 보이는 가중치가 실제로 쓰이는 값이고
 * 숨은 보정은 없다 — 플레이어가 조작을 의심하기 시작하면 이 게임에서 고칠 수 있는
 * 건 없다 (docs/07 4장).
 */
public final class TransferOffice {

    private final WorldData data;
    private final TransferRules rules;
    private final Random random;

    public TransferOffice(WorldData data, long seed) {
        this.data = data;
        this.rules = data.transfer();
        this.random = new Random(seed);
    }

    /**
     * 이 도시에서 이 인물이 갈 수 있는 곳들과, 그 판단의 근거.
     *
     * @param aim           플레이어가 노리는 직업 id. 교관 보정이 여기에만 붙는다
     * @param eventBonuses  진행 중인 사건이 주는 계열 보너스
     */
    public List<Prospect> prospects(Person person, CitySpec city, String aim,
                                    boolean tutor, Map<String, Double> eventBonuses) {

        List<Prospect> prospects = new ArrayList<>();
        for (JobSpec.Transition step : person.jobSpec().nextSteps()) {
            JobSpec target = data.job(step.to());
            List<String> reasons = new ArrayList<>();
            double weight = step.weight();

            double cityBonus = city.trainingBonus(target.family());
            if (cityBonus != 1.0) {
                weight *= cityBonus;
                reasons.add(String.format("%s 에는 %s 계열을 가르칠 사람이 있다 (×%.1f)",
                        city.name(), target.family(), cityBonus));
            }

            double statFactor = statFactor(person, target);
            if (statFactor != 1.0) {
                weight *= statFactor;
                reasons.add(statFactor > 1
                        ? String.format("이 인물은 %s 쪽 사람이다 (×%.1f)",
                                person.stats().dominant(), statFactor)
                        : String.format("이 인물은 %s 쪽이라 결이 다르다 (×%.1f)",
                                person.stats().dominant(), statFactor));
            }

            if (tutor && target.id().equals(aim)) {
                weight *= rules.tutorWeightBonus();
                reasons.add(String.format("교관을 붙였다 (×%.1f)", rules.tutorWeightBonus()));
            }

            double eventBonus = eventBonuses.getOrDefault(target.family(), 1.0);
            if (eventBonus != 1.0) {
                weight *= eventBonus;
                reasons.add(String.format("지금 %s 계열에 바람이 분다 (×%.1f)",
                        target.family(), eventBonus));
            }

            int missed = person.missedCount(target.id());
            if (missed > 0) {
                double pity = rules.pity(missed);
                weight *= pity;
                reasons.add(String.format("%d 번 빗나갔다 (×%.1f)", missed, pity));
            }

            prospects.add(new Prospect(target, weight, List.copyOf(reasons)));
        }
        return prospects;
    }

    /**
     * 인물의 앞선 능력치가 목표 직업이 요구하는 것과 맞는가.
     *
     * <p>목표 직업이 어느 능력치를 가장 크게 올려주는지로 "그 직업이 어떤 사람의
     * 것인가" 를 정한다. 데이터에서 저절로 나오므로 따로 적을 필요가 없다.
     */
    private double statFactor(Person person, JobSpec target) {
        String wanted = dominantOf(target);
        if (wanted == null) {
            return 1.0;
        }
        String has = person.stats().dominant();
        if (has.equals(wanted)) {
            return rules.statMatchBonus();
        }
        // 그 직업이 거의 안 쓰는 능력치가 앞서 있으면 어긋난 것이다
        return target.stat(keyOf(has)) == 0 ? rules.statMismatchPenalty() : 1.0;
    }

    private String dominantOf(JobSpec spec) {
        int might = spec.stat("might");
        int insight = spec.stat("insight");
        int commerce = spec.stat("commerce");
        int grit = spec.stat("grit");
        int max = Math.max(Math.max(might, insight), Math.max(commerce, grit));
        if (max == 0) return null;
        if (max == might) return "무력";
        if (max == insight) return "통찰";
        if (max == commerce) return "상재";
        return "인내";
    }

    private String keyOf(String korean) {
        return switch (korean) {
            case "무력" -> "might";
            case "통찰" -> "insight";
            case "상재" -> "commerce";
            default -> "grit";
        };
    }

    /** 가중치대로 하나를 뽑는다. */
    public JobSpec draw(List<Prospect> prospects) {
        double total = prospects.stream().mapToDouble(Prospect::weight).sum();
        if (total <= 0) {
            throw new IllegalStateException("갈 수 있는 곳이 없다");
        }
        double roll = random.nextDouble() * total;
        for (Prospect p : prospects) {
            roll -= p.weight();
            if (roll <= 0) {
                return p.target();
            }
        }
        return prospects.get(prospects.size() - 1).target();
    }

    /** 시험과 화면 계산용. <b>플레이어에게 이 값을 내려보내지 않는다.</b> */
    public static double probabilityOf(List<Prospect> prospects, String jobId) {
        double total = prospects.stream().mapToDouble(Prospect::weight).sum();
        return prospects.stream()
                .filter(p -> p.target().id().equals(jobId))
                .mapToDouble(p -> p.weight() / total)
                .findFirst().orElse(0);
    }
}
