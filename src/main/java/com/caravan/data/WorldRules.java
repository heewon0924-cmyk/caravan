package com.caravan.data;

/**
 * 세계 전체에 걸리는 규칙. {@code resources/data/world.yml} 에서 읽는다.
 *
 * @param speed                시간 배율. 현실 1분이 세계 몇 분인가 (docs/03-이동과-시간.md 3장)
 * @param granaryCapMultiple   재고가 기준재고의 이 배수를 넘으면 초과분이 상한다
 * @param spoilRatePerTick     넘친 초과분이 매 틱 상하는 비율
 * @param priceFloorMultiple   시세 하한 (기준가의 배수)
 * @param priceCeilingMultiple 시세 상한 (기준가의 배수)
 */
public record WorldRules(double speed,
                         double granaryCapMultiple,
                         double spoilRatePerTick,
                         double priceFloorMultiple,
                         double priceCeilingMultiple) {

    public WorldRules {
        if (speed <= 0) {
            throw new IllegalArgumentException("시간 배율은 0보다 커야 한다: " + speed);
        }
        if (granaryCapMultiple <= 1) {
            throw new IllegalArgumentException("곳간 상한 배수는 1보다 커야 한다: " + granaryCapMultiple);
        }
        if (spoilRatePerTick <= 0 || spoilRatePerTick > 1) {
            throw new IllegalArgumentException("상하는 비율은 0~1 이어야 한다: " + spoilRatePerTick);
        }
    }
}
