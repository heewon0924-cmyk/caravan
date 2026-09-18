package com.caravan.people;

import com.caravan.data.JobSpec;
import com.caravan.people.Stats;

/**
 * 인물이 지금 무엇을 하는 사람인가.
 *
 * <p>이름과 외형은 인물에게 붙고, 직업은 전직으로 바뀐다 —
 * <b>전직해도 그 사람은 같은 사람이다</b> (docs/06 1장).
 */
public record Job(JobSpec spec) {

    public String id() { return spec.id(); }
    public String name() { return spec.name(); }
    public int tier() { return spec.tier(); }
    public String family() { return spec.family(); }

    public Stats bonus() {
        return new Stats(spec.stat("might"), spec.stat("insight"),
                spec.stat("commerce"), spec.stat("grit"));
    }

    public boolean has(String role) {
        return spec.rolesOrEmpty().contains(role);
    }
}
