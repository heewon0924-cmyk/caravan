package com.caravan.data;

import java.util.List;

/**
 * 이벤트 정의. {@code resources/data/events.yml} 에서 읽는다.
 *
 * <p>비어 있을 수 있는 칸이 많다. {@code city} 가 없으면 임의의 도시,
 * {@code goods} 가 없으면 그 도시의 모든 품목이다.
 *
 * @param forecastHours      시작 몇 시간 전부터 예고가 도는가. 0 이면 예고 없음
 * @param stockBonusRatio    시작할 때 기준재고의 이 비율만큼 재고를 한 번 넣는다
 * @param jobFamilyBonus     전직 계열 보너스. 7단계에서 쓴다 — 지금은 기록만 한다
 */
public record EventSpec(String id,
                        String name,
                        double weight,
                        String city,
                        List<String> goods,
                        List<String> routes,
                        Double productionMultiple,
                        Double consumptionMultiple,
                        Double stockBonusRatio,
                        Double dangerMultiple,
                        String jobFamilyBonus,
                        double durationHours,
                        double forecastHours,
                        String text) {

    public boolean affectsMarkets() {
        return productionMultiple != null || consumptionMultiple != null || stockBonusRatio != null;
    }

    public boolean affectsRoutes() {
        return dangerMultiple != null && routes != null && !routes.isEmpty();
    }

    public boolean hasForecast() {
        return forecastHours > 0;
    }

    public double production() { return productionMultiple == null ? 1.0 : productionMultiple; }
    public double consumption() { return consumptionMultiple == null ? 1.0 : consumptionMultiple; }
    public double danger() { return dangerMultiple == null ? 1.0 : dangerMultiple; }
}
