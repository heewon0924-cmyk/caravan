package com.caravan.trade;

/**
 * 거래 한 건의 내역.
 *
 * <p>시세가 얼마나 밀렸는지를 보여주는 게 목적이다. 2단계의 합격 기준이
 * "콘솔로 사고팔면서 <b>시세가 밀리는 게 보인다</b>" 라서, 거래 결과에
 * 거래 전후 시세가 같이 들어 있어야 한다.
 *
 * @param goodsId    품목
 * @param quantity   수량
 * @param gross      거래세 전 금액 (적분 가격)
 * @param tax        거래세
 * @param net        실제로 오간 돈. 매수면 지출, 매도면 수입
 * @param priceBefore 거래 직전 시세
 * @param priceAfter  거래 직후 시세
 * @param buying     매수면 true
 */
public record Receipt(String goodsId,
                      double quantity,
                      double gross,
                      double tax,
                      double net,
                      double priceBefore,
                      double priceAfter,
                      boolean buying) {

    /** 세금까지 포함한 실제 평균 단가. */
    public double averageUnitPrice() {
        return net / quantity;
    }

    /** 시세대로 거래했다면 오갔을 돈. 적분 가격과 비교하려고 쓴다. */
    public double atSpot() {
        return priceBefore * quantity;
    }

    /**
     * 시세가 밀린 정도. 매수면 더 비싸게 산 만큼, 매도면 덜 받은 만큼.
     * 거래세는 뺀 순수한 밀림이다.
     */
    public double slippage() {
        return buying ? gross - atSpot() : atSpot() - gross;
    }

    /** 시세가 움직인 비율. */
    public double priceMovedRatio() {
        return priceAfter / priceBefore - 1.0;
    }
}
