package com.caravan.data;

import java.util.List;

/**
 * 도시 정의. {@code resources/data/cities.yml} 에서 읽는다.
 *
 * <p>도시는 상점이 아니다 — 시장·길·소문·수련처를 가진다
 * (docs/01-세계와-도시.md 3장). 1단계에서는 시장만 만든다.
 */
public record CitySpec(String id, String name, String trait, List<MarketSpec> market) {
}
