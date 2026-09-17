package com.caravan.cli;

import com.caravan.data.WorldData;
import com.caravan.trade.Cargo;
import com.caravan.trade.Exchange;
import com.caravan.trade.Receipt;
import com.caravan.trade.Trader;
import com.caravan.world.City;
import com.caravan.world.World;

import java.io.PrintStream;

/**
 * 왕복 한 번의 손익을 낸다.
 *
 * <p>docs/02-교역과-가격.md 8장이 프로토타입에서 재보라고 한 것 중 3번 —
 * <b>세 도시 왕복 한 바퀴의 수익률이 자본의 5~15% 안에 들어오는가.</b>
 * 너무 낮으면 지루하고 너무 높으면 며칠 만에 끝난다.
 *
 * <p>이동 비용과 시간은 아직 안 들어 있다 (3단계). 그래서 여기 나오는 수익률은
 * <b>상한</b>이다 — 실제로는 이보다 낮다.
 */
public final class RoundTrip {

    private final PrintStream out;

    public RoundTrip(PrintStream out) {
        this.out = out;
    }

    public void analyse(WorldData data, String goodsId, String fromId, String toId,
                        double quantity, int agedDays) {

        World world = new World(data);
        if (agedDays > 0) {
            world.advanceDays(agedDays);
        }

        Exchange exchange = new Exchange(data.rules());
        City from = world.city(fromId);
        City to = world.city(toId);

        // 분석용 자본은 먼저 견적을 내서 딱 그만큼만 준다.
        // 아주 큰 수를 주면 double 정밀도에 손익이 통째로 묻힌다 —
        // 1e307 에 3만을 더해도 그대로 1e307 이다.
        Trader trader = new Trader("analysis", "분석",
                exchange.quoteBuy(from, goodsId, quantity).net());
        Cargo cargo = new Cargo();

        Receipt bought = exchange.buy(trader, cargo, from, goodsId, quantity);
        Receipt sold = exchange.sell(trader, cargo, to, goodsId, quantity);

        double profit = sold.net() - bought.net();
        double margin = profit / bought.net();

        out.println();
        out.printf("══ 왕복 분석 — %s, %s → %s, %s개 (세계 %d일차) ══%n",
                from.market(goodsId).goods().name(), from.name(), to.name(),
                Text.number(quantity), agedDays);

        out.printf("%n  %s 에서 매수%n", from.name());
        line("시세", Text.money(bought.priceBefore()) + " G  →  "
                + Text.money(bought.priceAfter()) + " G");
        line("적분 지불액", Text.money(bought.gross()) + " G");
        line("거래세", Text.money(bought.tax()) + " G");
        line("총 지출", Text.money(bought.net()) + " G   (개당 "
                + Text.money(bought.averageUnitPrice()) + " G)");

        out.printf("%n  %s 에서 매도%n", to.name());
        line("시세", Text.money(sold.priceBefore()) + " G  →  "
                + Text.money(sold.priceAfter()) + " G");
        line("적분 수령액", Text.money(sold.gross()) + " G");
        line("거래세", Text.money(sold.tax()) + " G");
        line("총 수입", Text.money(sold.net()) + " G   (개당 "
                + Text.money(sold.averageUnitPrice()) + " G)");

        out.println();
        out.printf("  ── 순이익 %s G   투입 대비 %+.1f%%%n",
                Text.money(profit), margin * 100);
        out.printf("     거래세로 나간 돈 %s G (이익의 %.0f%%)%n",
                Text.money(bought.tax() + sold.tax()),
                profit > 0 ? (bought.tax() + sold.tax()) / profit * 100 : 0);
        out.println("     이동 비용과 시간은 아직 안 들어 있다 — 3단계에서 붙는다.");
    }

    private void line(String label, String value) {
        out.printf("    %s %s%n", Text.padRight(label, 14), value);
    }
}
