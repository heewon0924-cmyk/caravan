package com.caravan.world;

import com.caravan.data.GoodsSpec;
import com.caravan.data.MarketSpec;
import com.caravan.data.WorldRules;
import com.caravan.economy.PriceCurve;

/**
 * 한 도시의 한 품목 시장. 재고를 들고 있고, 가격은 재고에서 나온다.
 *
 * <p>시장 상태는 플레이어가 아니라 세계가 소유한다
 * (docs/20-프로토타입-1차.md 5장). 나중에 여러 명이 들어와도 이 구조는 안 바뀐다.
 */
public final class Market {

    private final GoodsSpec goods;
    private final MarketSpec spec;
    private final WorldRules rules;
    private final PriceCurve curve;

    private double stock;

    /**
     * 재고가 없어서 소비하지 못한 누적량. 이 도시가 굶고 있는지를 보여준다.
     * 4단계에서 NPC 상인이 어디로 갈지 정하는 근거가 된다.
     */
    private double unmetDemand;

    /** 이벤트가 걸어놓은 생산·소비 보정. 사건이 없으면 1.0 이다. */
    private double productionModifier = 1.0;
    private double consumptionModifier = 1.0;

    Market(GoodsSpec goods, MarketSpec spec, WorldRules rules) {
        this.goods = goods;
        this.spec = spec;
        this.rules = rules;
        this.curve = new PriceCurve(
                goods.basePrice(),
                goods.elasticity(),
                spec.refStock(),
                rules.priceFloorMultiple(),
                rules.priceCeilingMultiple());
        this.stock = spec.initialStockOrRef();
    }

    /**
     * 한 틱 진행한다. 생산 → 소비 → 곳간 넘침 순으로 적용한다.
     *
     * <p>소비는 재고가 있는 만큼만 한다. 재고가 음수로 내려가면 가격 수식이
     * 통째로 무너지므로, 못 채운 수요는 재고를 깎는 대신 {@link #unmetDemand}
     * 에 쌓는다.
     */
    void tick() {
        stock += spec.productionPerDay() * productionModifier / WorldClock.TICKS_PER_DAY;

        double want = spec.consumptionPerDay() * consumptionModifier / WorldClock.TICKS_PER_DAY;
        double taken = Math.min(want, stock);
        stock -= taken;
        unmetDemand += want - taken;

        // 잉여는 무한정 쌓이지 않는다 — 넘치는 만큼 상해서 버린다.
        // 이게 없으면 하른의 밀 재고가 폭주해 가격이 바닥에 붙는다.
        double cap = spec.refStock() * rules.granaryCapMultiple();
        if (stock > cap) {
            stock -= (stock - cap) * rules.spoilRatePerTick();
        }
    }

    public double spotPrice() {
        return curve.spot(stock);
    }

    public double costToBuy(double quantity) {
        return curve.cost(stock, quantity);
    }

    public double revenueToSell(double quantity) {
        return curve.revenue(stock, quantity);
    }

    /**
     * 플레이어에게 보이는 재고 단계. 정확한 숫자를 주면 계산기가 되고,
     * 단계를 주면 감이 된다 (docs/02-교역과-가격.md 5장).
     */
    public StockLevel level() {
        double ratio = stock / spec.refStock();
        if (ratio < 0.25) return StockLevel.희귀;
        if (ratio < 0.75) return StockLevel.적음;
        if (ratio < 1.5) return StockLevel.보통;
        return StockLevel.많음;
    }

    /**
     * 거래로 재고가 빠진다. {@code Exchange} 만 부른다 — 직접 부르면 돈이 오가지 않은 채
     * 재고만 사라진다.
     */
    public void takeStock(double quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 0보다 커야 한다: " + quantity);
        }
        if (quantity > stock + 1e-9) {
            throw new IllegalArgumentException(
                    "재고보다 많이 뺄 수 없다: 재고 " + stock + ", 요청 " + quantity);
        }
        stock = Math.max(0, stock - quantity);
    }

    /** 거래로 재고가 들어온다. {@code Exchange} 만 부른다. */
    public void addStock(double quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 0보다 커야 한다: " + quantity);
        }
        stock += quantity;
    }

    /**
     * 이벤트 효과를 건다. {@code EventEngine} 만 부른다 — 매 틱 전부 다시 계산하므로
     * 사건이 끝나면 저절로 1.0 으로 돌아온다.
     */
    public void setModifiers(double production, double consumption) {
        this.productionModifier = production;
        this.consumptionModifier = consumption;
    }

    public double productionModifier() { return productionModifier; }
    public double consumptionModifier() { return consumptionModifier; }

    /** 이벤트가 걸려 있는가. 화면에 표시할 때 쓴다. */
    public boolean isAffectedByEvent() {
        return Math.abs(productionModifier - 1.0) > 1e-9
                || Math.abs(consumptionModifier - 1.0) > 1e-9;
    }

    public GoodsSpec goods() { return goods; }
    public MarketSpec spec() { return spec; }
    public PriceCurve curve() { return curve; }
    public double stock() { return stock; }
    public double refStock() { return spec.refStock(); }
    public double unmetDemand() { return unmetDemand; }

    /** 곳간 상한. 재고가 이 값을 넘으면 초과분이 상한다. */
    public double granaryCap() {
        return spec.refStock() * rules.granaryCapMultiple();
    }

    public enum StockLevel { 희귀, 적음, 보통, 많음 }
}
