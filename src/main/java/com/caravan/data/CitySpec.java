package com.caravan.data;

import java.util.List;
import java.util.Map;

/**
 * 도시 정의. {@code resources/data/cities.yml} 에서 읽는다.
 *
 * <p>도시는 상점이 아니다 — 시장·길·소문·수련처를 가진다
 * (docs/01-세계와-도시.md 3장). 1단계에서는 시장만 만든다.
 */
public record CitySpec(String id, String name, String trait,
                       List<MarketSpec> market,
                       Map<String, Double> training) {

    /** 이 도시가 이 계열의 전직에 주는 보정. 수련처가 없으면 전직 자체가 안 된다. */
    public double trainingBonus(String family) {
        return training == null ? 1.0 : training.getOrDefault(family, 1.0);
    }

    /** 여기서 전직할 수 있는가. */
    public boolean hasTrainingGround() {
        return training != null && !training.isEmpty();
    }
}
