package com.caravan.trade;

import com.caravan.data.WorldRules;
import com.caravan.world.City;
import com.caravan.world.Market;

/**
 * 거래소. <b>모든 매매가 여기를 지난다.</b>
 *
 * <p>docs/20-프로토타입-1차.md 5장의 구조 제약이다.
 *
 * <blockquote>거래는 함수로 분리한다. {@code 구매(주체, 도시, 품목, 수량)} —
 * 주체가 플레이어든 NPC 든 같은 함수를 쓴다.</blockquote>
 *
 * <p>4단계에서 NPC 상인이 이 클래스를 그대로 부른다. NPC 가 다른 경로로 거래하면
 * 적분 가격도 거래세도 안 물게 되고, 그러면 세계가 거짓말이 된다.
 *
 * <p>가격이 어떻게 정해지는지는 여기에 없다 — {@code PriceCurve} 가 한다.
 * 거래소가 하는 일은 <b>돈과 물건과 재고를 동시에 움직이는 것</b>뿐이다.
 */
public final class Exchange {

    private final double taxRate;

    public Exchange(WorldRules rules) {
        this.taxRate = rules.tradeTaxRate();
    }

    /**
     * 산다. 지불액은 적분 가격 + 거래세다.
     *
     * <p>돈이 모자라면 <b>재고를 건드리기 전에</b> 거절한다.
     *
     * <p>돈은 {@code trader} 에서 나가고 물건은 {@code cargo} 로 들어간다.
     * 둘을 따로 받는 이유는 상단이 캐러밴을 여럿 굴릴 수 있기 때문이다.
     */
    public Receipt buy(Trader trader, Cargo cargo, City city, String goodsId, double quantity) {
        return buy(trader, cargo, city, goodsId, quantity, taxRate);
    }

    /**
     * 세율을 따로 준다. 상재가 높은 인물을 태우면 거래세가 깎이기 때문이다
     * (docs/06 3장).
     */
    public Receipt buy(Trader trader, Cargo cargo, City city, String goodsId,
                       double quantity, double taxRate) {
        Market market = city.market(goodsId);
        requirePositive(quantity);

        if (quantity > market.stock() + 1e-9) {
            throw new TradeRefused(String.format(
                    "%s 의 %s 재고가 모자란다: 재고 %,.0f, 요청 %,.0f",
                    city.name(), market.goods().name(), market.stock(), quantity));
        }

        double before = market.spotPrice();
        double gross = market.costToBuy(quantity);
        double tax = gross * taxRate;
        double total = gross + tax;

        trader.pay(total);
        market.takeStock(quantity);
        cargo.add(goodsId, quantity);

        return new Receipt(goodsId, quantity, gross, tax, total,
                before, market.spotPrice(), true);
    }

    /**
     * 판다. 수령액은 적분 가격 − 거래세다.
     *
     * <p>가진 것보다 많이 팔려 하면 <b>재고를 건드리기 전에</b> 거절한다.
     */
    public Receipt sell(Trader trader, Cargo cargo, City city, String goodsId, double quantity) {
        return sell(trader, cargo, city, goodsId, quantity, taxRate);
    }

    public Receipt sell(Trader trader, Cargo cargo, City city, String goodsId,
                        double quantity, double taxRate) {
        Market market = city.market(goodsId);
        requirePositive(quantity);

        cargo.remove(goodsId, quantity);

        double before = market.spotPrice();
        double gross = market.revenueToSell(quantity);
        double tax = gross * taxRate;
        double net = gross - tax;

        market.addStock(quantity);
        trader.earn(net);

        return new Receipt(goodsId, quantity, gross, tax, net,
                before, market.spotPrice(), false);
    }

    /**
     * 사면 얼마인지만 계산한다. <b>아무것도 바꾸지 않는다.</b>
     *
     * <p>화면에 "이만큼 사면 얼마" 를 미리 보여주는 데 쓰고, 4단계에서 NPC 가
     * 어디로 갈지 고를 때도 이걸로 따진다.
     */
    public Receipt quoteBuy(City city, String goodsId, double quantity) {
        return quoteBuy(city, goodsId, quantity, taxRate);
    }

    public Receipt quoteBuy(City city, String goodsId, double quantity, double taxRate) {
        Market market = city.market(goodsId);
        requirePositive(quantity);

        double before = market.spotPrice();
        double gross = market.costToBuy(quantity);
        double tax = gross * taxRate;

        return new Receipt(goodsId, quantity, gross, tax, gross + tax,
                before, market.curve().spot(market.stock() - quantity), true);
    }

    /** 팔면 얼마인지만 계산한다. 아무것도 바꾸지 않는다. */
    public Receipt quoteSell(City city, String goodsId, double quantity) {
        return quoteSell(city, goodsId, quantity, taxRate);
    }

    public Receipt quoteSell(City city, String goodsId, double quantity, double taxRate) {
        Market market = city.market(goodsId);
        requirePositive(quantity);

        double before = market.spotPrice();
        double gross = market.revenueToSell(quantity);
        double tax = gross * taxRate;

        return new Receipt(goodsId, quantity, gross, tax, gross - tax,
                before, market.curve().spot(market.stock() + quantity), false);
    }

    public double taxRate() {
        return taxRate;
    }

    private static void requirePositive(double quantity) {
        if (quantity <= 0) {
            throw new TradeRefused("수량은 0보다 커야 한다: " + quantity);
        }
    }
}
