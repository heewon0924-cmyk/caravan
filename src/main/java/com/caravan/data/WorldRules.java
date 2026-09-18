package com.caravan.data;

/**
 * 세계 전체에 걸리는 규칙. {@code resources/data/world.yml} 에서 읽는다.
 *
 * @param speed                시간 배율. 현실 1분이 세계 몇 분인가 (docs/03-이동과-시간.md 3장)
 * @param granaryCapMultiple   재고가 기준재고의 이 배수를 넘으면 초과분이 상한다
 * @param spoilRatePerTick     넘친 초과분이 매 틱 상하는 비율
 * @param priceFloorMultiple   시세 하한 (기준가의 배수)
 * @param priceCeilingMultiple 시세 상한 (기준가의 배수)
 * @param tradeTaxRate         거래세. 사고팔 때 각각 물린다 (docs/02-교역과-가격.md 6장)
 * @param startingGold         상단의 시작 자본
 * @param caravanCapacity      캐러밴이 실을 수 있는 총 부피
 * @param loadSlowdown         가득 실었을 때 느려지는 비율
 * @param travelCostPerHour    노선 기본 소요시간 1시간당 출발비
 * @param npcCaravanCapacity   NPC 짐수레 용량. 플레이어보다 작다
 * @param npcStartingGold      NPC 한 명의 밑천
 * @param npcTemperaments      NPC 성격별 설정. count 합계가 인원이다
 * @param crewSlots            캐러밴에 탈 수 있는 인물 수
 * @param hireCost             인물 한 명을 고용하는 값
 * @param hireCandidates       도시마다 대기 중인 인물 수
 * @param informantFee         정보상에게 한 번 묻는 값
 * @param eventsPerDay         세계 하루당 평균 이벤트 발생 수
 * @param maxConcurrentEvents  동시에 진행될 수 있는 이벤트 수
 */
public record WorldRules(double speed,
                         double granaryCapMultiple,
                         double spoilRatePerTick,
                         double priceFloorMultiple,
                         double priceCeilingMultiple,
                         double tradeTaxRate,
                         double startingGold,
                         double caravanCapacity,
                         double loadSlowdown,
                         double travelCostPerHour,
                         double npcCaravanCapacity,
                         int crewSlots,
                         double hireCost,
                         int hireCandidates,
                         double crewTaxReliefPerCommerce,
                         double crewTaxReliefCap,
                         double crewCapacityPerGrit,
                         double npcStartingGold,
                         java.util.List<TemperamentSpec> npcTemperaments,
                         double informantFee,
                         double eventsPerDay,
                         int maxConcurrentEvents) {

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
        if (tradeTaxRate < 0 || tradeTaxRate >= 1) {
            throw new IllegalArgumentException("거래세는 0 이상 1 미만이어야 한다: " + tradeTaxRate);
        }
        if (startingGold < 0) {
            throw new IllegalArgumentException("시작 자본은 음수일 수 없다: " + startingGold);
        }
        if (caravanCapacity <= 0) {
            throw new IllegalArgumentException("캐러밴 용량은 0보다 커야 한다: " + caravanCapacity);
        }
        if (loadSlowdown < 0) {
            throw new IllegalArgumentException("적재 감속은 음수일 수 없다: " + loadSlowdown);
        }
        if (travelCostPerHour < 0) {
            throw new IllegalArgumentException("이동 비용은 음수일 수 없다: " + travelCostPerHour);
        }
        if (npcCaravanCapacity <= 0) {
            throw new IllegalArgumentException("NPC 용량은 0보다 커야 한다: " + npcCaravanCapacity);
        }
        if (eventsPerDay < 0) {
            throw new IllegalArgumentException("이벤트 빈도는 음수일 수 없다: " + eventsPerDay);
        }
        if (maxConcurrentEvents < 0) {
            throw new IllegalArgumentException("동시 이벤트 수는 음수일 수 없다: " + maxConcurrentEvents);
        }
        if (crewSlots <= 0) {
            throw new IllegalArgumentException("캐러밴 인원 칸은 0보다 커야 한다: " + crewSlots);
        }
        if (crewTaxReliefCap < 0 || crewTaxReliefCap > 1) {
            throw new IllegalArgumentException("거래세 감면 상한은 0~1 이어야 한다");
        }
        if (npcTemperaments == null || npcTemperaments.isEmpty()) {
            throw new IllegalArgumentException("NPC 성격이 하나도 정의되지 않았다");
        }
    }

    /** 성격별 인원의 합. 이게 NPC 상인의 수다. */
    public int npcCount() {
        return npcTemperaments.stream().mapToInt(TemperamentSpec::count).sum();
    }
}
