package com.caravan.economy;

/**
 * 한 도시의 한 품목에 대한 가격 곡선.
 *
 * <p>docs/02-교역과-가격.md 2·3장의 구현이다. 대원칙은 하나다 —
 * <b>가격은 난수로 흔들리지 않는다. 재고로 움직인다.</b>
 * 그래야 흉년을 아는 플레이어가 밀값이 얼마까지 오를지 계산할 수 있다.
 *
 * <p>시세는 재고에 대해 세 구간으로 나뉜다.
 * <pre>
 *   재고 적음  ┤ 상한에 붙음 (기준가 × 3.0)
 *   재고 보통  ┤ P = 기준가 × (기준재고 / 재고) ^ 탄력도
 *   재고 많음  ┤ 하한에 붙음 (기준가 × 0.35)
 * </pre>
 *
 * <p>거래 가격은 이 곡선을 <b>재고에 대해 적분한 값</b>이다. 300개를 사면
 * 300개를 전부 마지막 가격에 사는 게 아니라, 사는 동안 값이 오른다.
 * 적분을 구간별로 나눠서 하는 게 중요하다 — 상하한을 무시하고 거듭제곱
 * 식만 적분하면 재고가 바닥난 도시에서 값이 발산한다.
 */
public final class PriceCurve {

    private final double basePrice;
    private final double elasticity;
    private final double refStock;

    private final double ceilingPrice;
    private final double floorPrice;

    /** 이 재고 아래로는 시세가 상한에 붙는다. */
    private final double stockAtCeiling;
    /** 이 재고 위로는 시세가 하한에 붙는다. */
    private final double stockAtFloor;

    /** 중간 구간 적분 계수: 기준가 × 기준재고^탄력도 / (1 − 탄력도) */
    private final double midCoefficient;
    private final double midConstant;
    private final double highConstant;

    public PriceCurve(double basePrice,
                      double elasticity,
                      double refStock,
                      double floorMultiple,
                      double ceilingMultiple) {

        if (basePrice <= 0) {
            throw new IllegalArgumentException("기준가는 0보다 커야 한다: " + basePrice);
        }
        if (refStock <= 0) {
            throw new IllegalArgumentException("기준재고는 0보다 커야 한다: " + refStock);
        }
        // 탄력도가 1이면 적분이 로그가 되어 아래 식이 전부 깨진다.
        // 조용히 NaN 을 뱉느니 데이터를 읽는 시점에 터지는 게 낫다.
        if (Math.abs(elasticity - 1.0) < 1e-6) {
            throw new IllegalArgumentException(
                    "탄력도 1.0 은 쓸 수 없다 (적분식이 로그가 된다). 0.9 나 1.1 로 둔다.");
        }
        if (elasticity <= 0) {
            throw new IllegalArgumentException("탄력도는 0보다 커야 한다: " + elasticity);
        }
        if (!(floorMultiple > 0 && floorMultiple < 1 && ceilingMultiple > 1)) {
            throw new IllegalArgumentException(
                    "가격 상하한은 0 < 하한 < 1 < 상한 이어야 한다: "
                            + floorMultiple + " ~ " + ceilingMultiple);
        }

        this.basePrice = basePrice;
        this.elasticity = elasticity;
        this.refStock = refStock;
        this.floorPrice = basePrice * floorMultiple;
        this.ceilingPrice = basePrice * ceilingMultiple;

        // P(S) = base × (ref/S)^e 가 상한·하한과 만나는 재고
        this.stockAtCeiling = refStock * Math.pow(ceilingMultiple, -1.0 / elasticity);
        this.stockAtFloor = refStock * Math.pow(floorMultiple, -1.0 / elasticity);

        this.midCoefficient =
                basePrice * Math.pow(refStock, elasticity) / (1.0 - elasticity);

        // 적분한 함수가 구간 경계에서 이어지도록 상수를 맞춘다.
        this.midConstant =
                ceilingPrice * stockAtCeiling - midCoefficient * Math.pow(stockAtCeiling, 1.0 - elasticity);
        this.highConstant =
                midCoefficient * Math.pow(stockAtFloor, 1.0 - elasticity) + midConstant
                        - floorPrice * stockAtFloor;
    }

    /** 재고가 {@code stock} 일 때의 현재 시세(1개당). */
    public double spot(double stock) {
        if (stock <= stockAtCeiling) {
            return ceilingPrice;
        }
        if (stock >= stockAtFloor) {
            return floorPrice;
        }
        return basePrice * Math.pow(refStock / stock, elasticity);
    }

    /**
     * 재고 {@code stock} 인 시장에서 {@code quantity} 개를 살 때의 <b>총 지불액</b>.
     * 사는 동안 값이 오르므로 마지막 시세 × 수량보다 크다.
     */
    public double cost(double stock, double quantity) {
        requirePositive(quantity);
        if (quantity > stock + 1e-9) {
            throw new IllegalArgumentException(
                    "재고보다 많이 살 수 없다: 재고 " + stock + ", 요청 " + quantity);
        }
        return integral(stock) - integral(stock - quantity);
    }

    /**
     * 재고 {@code stock} 인 시장에 {@code quantity} 개를 팔 때의 <b>총 수령액</b>.
     * 파는 동안 값이 내리므로 현재 시세 × 수량보다 작다.
     */
    public double revenue(double stock, double quantity) {
        requirePositive(quantity);
        return integral(stock + quantity) - integral(stock);
    }

    /** 살 때의 평균 단가. 시세가 얼마나 밀렸는지 보려고 쓴다. */
    public double averageBuyPrice(double stock, double quantity) {
        return cost(stock, quantity) / quantity;
    }

    /** 팔 때의 평균 단가. */
    public double averageSellPrice(double stock, double quantity) {
        return revenue(stock, quantity) / quantity;
    }

    /** 시세 곡선의 부정적분. 구간마다 식이 다르고 경계에서 이어진다. */
    private double integral(double stock) {
        double s = Math.max(stock, 0.0);
        if (s <= stockAtCeiling) {
            return ceilingPrice * s;
        }
        if (s <= stockAtFloor) {
            return midCoefficient * Math.pow(s, 1.0 - elasticity) + midConstant;
        }
        return floorPrice * s + highConstant;
    }

    private static void requirePositive(double quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 0보다 커야 한다: " + quantity);
        }
    }

    public double basePrice() { return basePrice; }
    public double elasticity() { return elasticity; }
    public double refStock() { return refStock; }
    public double floorPrice() { return floorPrice; }
    public double ceilingPrice() { return ceilingPrice; }

    /** 이 재고 아래로는 시세가 상한에 붙는다. */
    public double stockAtCeiling() { return stockAtCeiling; }

    /** 이 재고 위로는 시세가 하한에 붙는다. */
    public double stockAtFloor() { return stockAtFloor; }
}
