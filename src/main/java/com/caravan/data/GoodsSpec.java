package com.caravan.data;

/**
 * 교역품 정의. {@code resources/data/goods.yml} 에서 읽는다.
 *
 * @param id         코드에서 쓰는 식별자
 * @param name       화면에 보이는 이름
 * @param basePrice  세계 기준가
 * @param elasticity 탄력도. 1.0 은 쓸 수 없다
 * @param volume     1개당 부피 (docs/05-캐러밴.md 4장)
 */
public record GoodsSpec(String id, String name, double basePrice, double elasticity, double volume) {

    /** 부피당 가치. 이 값이 교역품의 성격을 만든다 — 밀 100, 향신료 4500. */
    public double valuePerVolume() {
        return basePrice / volume;
    }
}
