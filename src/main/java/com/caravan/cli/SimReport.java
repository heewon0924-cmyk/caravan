package com.caravan.cli;

import com.caravan.data.GoodsSpec;
import com.caravan.trade.Receipt;
import com.caravan.world.City;
import com.caravan.world.Market;
import com.caravan.world.World;
import com.caravan.world.WorldClock;

import java.io.PrintStream;

/**
 * 세계 상태를 사람이 읽을 수 있게 찍는다.
 *
 * <p>1단계에는 화면이 없다. 여기가 화면이다 — 숫자가 맞는지 눈으로 보려고 만든 것이고,
 * 그림에 시간을 쓰기 시작하면 밸런스를 바꾸기 싫어진다 (docs/20-프로토타입-1차.md 4장).
 */
public final class SimReport {

    private final PrintStream out;

    public SimReport(PrintStream out) {
        this.out = out;
    }

    /** 세계 전체 스냅샷. */
    public void snapshot(World world) {
        out.println();
        out.printf("══ 세계 %s (틱 %d) ══%n", WorldClock.format(world.tick()), world.tick());
        for (City city : world.cities()) {
            city(city);
        }
    }

    public void city(City city) {
        out.println();
        out.printf("  %s — %s%n", city.name(), city.trait());
        out.printf("    %s %s %s %s %s %s%n",
                Text.padRight("품목", 10),
                Text.padLeft("재고", 9),
                Text.padLeft("기준", 8),
                Text.padLeft("시세", 9),
                Text.padLeft("기준가", 8),
                "  상태");

        for (Market m : city.markets()) {
            GoodsSpec g = m.goods();
            double price = m.spotPrice();
            out.printf("    %s %s %s %s %s   %s%n",
                    Text.padRight(g.name(), 10),
                    Text.padLeft(Text.number(m.stock()), 9),
                    Text.padLeft(Text.number(m.refStock()), 8),
                    Text.padLeft(Text.money(price) + "G", 9),
                    Text.padLeft(Text.money(g.basePrice()) + "G", 8),
                    status(m, price));
        }
    }

    private String status(Market m, double price) {
        StringBuilder sb = new StringBuilder();
        sb.append(Text.padRight(m.level().name(), 5));

        double ratio = price / m.goods().basePrice();
        sb.append(String.format("%+5.0f%%", (ratio - 1) * 100));

        if (price >= m.curve().ceilingPrice() - 1e-6) {
            sb.append("  ▲ 상한");
        } else if (price <= m.curve().floorPrice() + 1e-6) {
            sb.append("  ▼ 하한");
        }
        if (m.stock() >= m.granaryCap() - 1e-6) {
            sb.append("  곳간 넘침");
        }
        if (m.unmetDemand() > 1) {
            sb.append("  굶음(미충족 ").append(Text.number(m.unmetDemand())).append(")");
        }
        return sb.toString();
    }

    /**
     * 거래 한 건의 내역. 2단계의 합격 기준이 "시세가 밀리는 게 보인다" 라서
     * 거래 전후 시세를 반드시 같이 찍는다.
     */
    public void receipt(City city, Receipt r) {
        out.println();
        out.printf("  %s 에서 %s %s개 %s%n",
                city.name(), city.market(r.goodsId()).goods().name(),
                Text.number(r.quantity()), r.buying() ? "매수" : "매도");
        receiptBody(city, r, "    ");
    }

    public void receiptBody(City city, Receipt r, String indent) {
        out.printf("%s시세      %s G  →  %s G   (%+.1f%%)%n",
                indent, Text.money(r.priceBefore()), Text.money(r.priceAfter()),
                r.priceMovedRatio() * 100);
        out.printf("%s적분 금액  %s G%s%n",
                indent, Text.money(r.gross()),
                r.slippage() > 1
                        ? String.format("   (시세대로면 %s G — %s %s G)",
                        Text.money(r.atSpot()),
                        r.buying() ? "더 냈다" : "덜 받았다",
                        Text.money(r.slippage()))
                        : "");
        out.printf("%s거래세     %s G%n", indent, Text.money(r.tax()));
        out.printf("%s%s     %s G   (개당 %s G)%n",
                indent, r.buying() ? "총 지출" : "총 수입",
                Text.money(r.net()), Text.money(r.averageUnitPrice()));
    }

    /** 사고팔 때 시세가 얼마나 밀리는지 보여준다. */
    public void quote(City city, String goodsId, double quantity, boolean selling) {
        Market m = city.market(goodsId);
        double before = m.spotPrice();

        out.println();
        out.printf("  %s / %s — 재고 %s (기준 %s)%n",
                city.name(), m.goods().name(),
                Text.number(m.stock()), Text.number(m.refStock()));
        out.printf("    현재 시세      %s G%n", Text.money(before));

        if (selling) {
            double revenue = m.revenueToSell(quantity);
            out.printf("    %s개 매도     총 %s G  (평균 %s G/개)%n",
                    Text.number(quantity), Text.money(revenue), Text.money(revenue / quantity));
            out.printf("    판 뒤 시세     %s G%n",
                    Text.money(m.curve().spot(m.stock() + quantity)));
            out.printf("    시세만큼 팔았다면 %s G — 실제와 차이 %s G%n",
                    Text.money(before * quantity), Text.money(before * quantity - revenue));
        } else {
            double cost = m.costToBuy(quantity);
            out.printf("    %s개 매수     총 %s G  (평균 %s G/개)%n",
                    Text.number(quantity), Text.money(cost), Text.money(cost / quantity));
            out.printf("    산 뒤 시세     %s G%n",
                    Text.money(m.curve().spot(m.stock() - quantity)));
            out.printf("    시세대로 샀다면 %s G — 실제와 차이 %s G%n",
                    Text.money(before * quantity), Text.money(cost - before * quantity));
        }
    }

    /** 재고에 따라 시세가 어떻게 움직이는지 표로 본다. */
    public void curveTable(City city, String goodsId) {
        Market m = city.market(goodsId);
        double ref = m.refStock();

        out.println();
        out.printf("  %s / %s — 기준재고 %s, 기준가 %s G, 탄력도 %.2f%n",
                city.name(), m.goods().name(), Text.number(ref),
                Text.money(m.goods().basePrice()), m.goods().elasticity());
        out.printf("    시세 상한 %s G (재고 %s 이하)  ·  하한 %s G (재고 %s 이상)%n",
                Text.money(m.curve().ceilingPrice()), Text.number(m.curve().stockAtCeiling()),
                Text.money(m.curve().floorPrice()), Text.number(m.curve().stockAtFloor()));
        out.println();
        out.printf("    %s %s %s%n",
                Text.padLeft("재고", 9), Text.padLeft("시세", 9), Text.padLeft("기준가 대비", 12));

        double[] ratios = {0.05, 0.1, 0.2, 0.4, 0.6, 0.8, 1.0, 1.25, 1.5, 2.0, 2.5, 3.0};
        for (double r : ratios) {
            double stock = ref * r;
            double price = m.curve().spot(stock);
            out.printf("    %s %s %s%n",
                    Text.padLeft(Text.number(stock), 9),
                    Text.padLeft(Text.money(price) + "G", 9),
                    Text.padLeft(String.format("%+.0f%%", (price / m.goods().basePrice() - 1) * 100), 12));
        }
    }
}
