package com.caravan.people;

import com.caravan.data.JobSpec;
import com.caravan.data.NamedSpec;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 인물 하나. <b>직업 카드가 아니라 이름과 얼굴을 가진 사람이다.</b>
 *
 * <pre>
 *   인물 = 이름 + 외형 + 타고난 능력치 + 고유 특성   (안 변한다)
 *   직업 = 현재 성장 단계 + 역할 + 스킬              (전직으로 변한다)
 * </pre>
 *
 * <p>이 분리가 중요하다. 전직해도 그 사람은 같은 사람이다 —
 * "내가 데리고 다니던 그 검사가 해적이 됐다" 가 되어야 애착이 생긴다.
 * 전직할 때마다 다른 캐릭터로 교체되면 그건 그냥 강화다 (docs/06 1장).
 */
public final class Person {

    private final String id;
    private final String name;
    private final Stats innate;

    private Job job;
    private NamedSpec named;

    /** 목표 직업 id → 그 목표를 놓친 횟수. 천장이 여기서 쌓인다 (docs/07 4장). */
    private final Map<String, Integer> missed = new LinkedHashMap<>();

    /** 되돌리기용. 지나온 단계를 쌓아 둔다. */
    private final Deque<Step> path = new ArrayDeque<>();

    /** 몇 번 되돌렸는가. 되돌릴수록 비싸진다. */
    private int reverts;

    public Person(String id, String name, Stats innate, Job startingJob) {
        this.id = id;
        this.name = name;
        this.innate = innate;
        this.job = startingJob;
    }

    // ── 지금 어떤 사람인가 ────────────────────────────────

    /** 타고난 것 + 직업이 준 것. 각성했으면 고유 캐릭터의 능력치를 쓴다. */
    public Stats stats() {
        if (named != null) {
            return innate.plus(new Stats(named.stat("might"), named.stat("insight"),
                    named.stat("commerce"), named.stat("grit")));
        }
        return innate.plus(job.bonus());
    }

    /** 화면에 보이는 이름. 각성했으면 "해적왕 리안". */
    public String displayName() {
        return named != null ? named.fullName() : name + "(" + job.name() + ")";
    }

    public List<String> roles() {
        return named != null ? named.rolesOrEmpty() : job.spec().rolesOrEmpty();
    }

    public boolean has(String role) {
        return roles().contains(role);
    }

    public boolean isAwakened() {
        return named != null;
    }

    // ── 전직 ────────────────────────────────────────────

    /**
     * 전직한다. 지나온 단계를 쌓아 두므로 되돌릴 수 있다.
     *
     * <p>목표가 아닌 결과가 나왔으면 그 목표의 천장이 올라간다. <b>"실패" 라고
     * 부르지 않는다</b> — 무조건 무언가가 되고, 원하는 게 아닐 뿐이다 (docs/07 1장).
     */
    public void transitionTo(Job next, String aimedAt, double cost) {
        path.push(new Step(job, cost));
        if (aimedAt != null && !aimedAt.equals(next.id())) {
            missed.merge(aimedAt, 1, Integer::sum);
        }
        this.job = next;
    }

    /** 각성한다. 이름을 얻는 순간이다 (docs/06 4장). */
    public void awaken(NamedSpec spec) {
        this.named = spec;
    }

    /**
     * 이전 직업으로 되돌아간다.
     *
     * <p><b>천장은 유지된다.</b> 되돌리면서 천장이 쌓이므로 되돌릴수록 원하는 결과가
     * 가까워진다 — "계속 실패해서 화나는" 상황이 "돈과 시간을 얼마나 더 쓸 것인가"
     * 라는 판단으로 바뀐다 (docs/07 6장).
     *
     * @return 되돌리기 직전 직업이 들었던 비용 (되돌리기 값 계산에 쓴다)
     */
    public double revert() {
        if (path.isEmpty()) {
            throw new IllegalStateException(name + " 은 더 되돌아갈 곳이 없다");
        }
        Step step = path.pop();
        this.job = step.from();
        this.named = null;
        this.reverts++;
        return step.cost();
    }

    public boolean canRevert() {
        return !path.isEmpty();
    }

    /** 이 목표를 몇 번 놓쳤는가. */
    public int missedCount(String jobId) {
        return missed.getOrDefault(jobId, 0);
    }

    public Map<String, Integer> missedAll() {
        return Map.copyOf(missed);
    }

    public int reverts() {
        return reverts;
    }

    public String id() { return id; }
    public String name() { return name; }
    public Stats innate() { return innate; }
    public Job job() { return job; }
    public JobSpec jobSpec() { return job.spec(); }
    public NamedSpec named() { return named; }

    /** 지나온 한 단계. */
    private record Step(Job from, double cost) {
    }
}
