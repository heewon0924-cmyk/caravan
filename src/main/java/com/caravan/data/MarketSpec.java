package com.caravan.data;

/**
 * 한 도시가 한 품목을 어떻게 다루는지. {@code resources/data/cities.yml} 에서 읽는다.
 *
 * @param goods             품목 id
 * @param refStock          기준재고. 재고가 이 값이면 시세가 정확히 기준가다
 * @param productionPerDay  세계시간 하루당 생산량
 * @param consumptionPerDay 세계시간 하루당 소비량
 * @param initialStock      시작 재고. 생략하면 기준재고
 */
public record MarketSpec(String goods,
                         double refStock,
                         double productionPerDay,
                         double consumptionPerDay,
                         Double initialStock) {

    public double initialStockOrRef() {
        return initialStock != null ? initialStock : refStock;
    }

    /** 하루 순증감. 양수면 잉여가 쌓이고 음수면 굶는다. */
    public double netPerDay() {
        return productionPerDay - consumptionPerDay;
    }
}
