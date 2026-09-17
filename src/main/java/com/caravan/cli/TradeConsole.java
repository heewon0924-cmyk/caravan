package com.caravan.cli;

import com.caravan.data.WorldData;
import com.caravan.trade.Exchange;
import com.caravan.trade.Receipt;
import com.caravan.trade.TradeRefused;
import com.caravan.trade.Trader;
import com.caravan.world.City;
import com.caravan.world.Market;
import com.caravan.world.World;
import com.caravan.world.WorldClock;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 2단계 검증용 대화형 콘솔.
 *
 * <p>합격 기준이 "콘솔로 사고팔면서 <b>시세가 밀리는 게 보인다</b>" 라서,
 * 거래할 때마다 거래 전후 시세를 같이 찍는다.
 *
 * <p>이동은 아직 시간이 안 든다 — 3단계에서 붙는다.
 */
public final class TradeConsole {

    private final World world;
    private final WorldData data;
    private final Exchange exchange;
    private final Names names;
    private final SimReport report;
    private final PrintStream out;

    private Trader trader;
    private City here;

    public TradeConsole(World world, WorldData data, PrintStream out) {
        this.world = world;
        this.data = data;
        this.exchange = new Exchange(data.rules());
        this.names = new Names(data);
        this.report = new SimReport(out);
        this.out = out;
        this.trader = new Trader("player", "내 상단", data.rules().startingGold());
        this.here = world.cities().iterator().next();
    }

    public void run() {
        out.println();
        out.println("══ 상단을 열었다 ══");
        out.printf("  자본 %s G · 지금 %s 에 있다%n",
                Text.money(trader.gold()), here.name());
        out.println("  '도움' 을 치면 쓸 수 있는 말이 나온다. '끝' 이면 닫는다.");
        help();
        status();

        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            out.print("\n> ");
            out.flush();
            while ((line = in.readLine()) != null) {
                if (!handle(line.trim())) {
                    break;
                }
                out.print("\n> ");
                out.flush();
            }
        } catch (Exception e) {
            out.println("콘솔이 끊겼다: " + e.getMessage());
        }
        out.println("\n상단을 닫았다.");
    }

    /** @return 계속할 것인지 */
    public boolean handle(String line) {
        if (line.isEmpty()) {
            return true;
        }
        String[] token = line.split("\\s+");
        try {
            switch (token[0]) {
                case "도움", "help", "?" -> help();
                case "상태", "status" -> status();
                case "시세", "market" -> report.city(here);
                case "사기", "buy" -> trade(token, true);
                case "팔기", "sell" -> trade(token, false);
                case "견적", "quote" -> quote(token);
                case "이동", "go" -> move(token);
                case "대기", "wait" -> wait(token);
                case "세계", "world" -> report.snapshot(world);
                case "끝", "quit", "exit" -> { return false; }
                default -> out.println("  모르는 말이다: " + token[0] + "   ('도움' 참조)");
            }
        } catch (TradeRefused e) {
            out.println("  거절됐다 — " + e.getMessage());
        } catch (IllegalArgumentException e) {
            out.println("  " + e.getMessage());
        }
        return true;
    }

    private void help() {
        out.println("""

              상태              소지금과 화물
              시세              지금 도시의 시세표
              사기 밀 300       산다
              팔기 밀 300       판다
              견적 밀 300       사면 얼마인지만 본다 (아무것도 안 바뀐다)
              이동 카르덴        다른 도시로 (아직 시간이 안 든다 — 3단계)
              대기 6            세계 시간 6시간을 흘린다
              세계              세 도시 전부 본다
              끝""");
    }

    private void status() {
        out.println();
        out.printf("  %s — %s · 세계 %s%n",
                trader.name(), here.name(), WorldClock.format(world.tick()));
        out.printf("    소지금  %s G%n", Text.money(trader.gold()));

        if (trader.cargo().isEmpty()) {
            out.println("    화물    없음");
            return;
        }

        out.println("    화물");
        double bookValue = 0;
        for (Map.Entry<String, Double> e : trader.cargo().all().entrySet()) {
            Market m = here.market(e.getKey());
            double worth = exchange.quoteSell(here, e.getKey(), e.getValue()).net();
            bookValue += worth;
            out.printf("      %s %s개   여기서 팔면 %s G (세후, 개당 %s G)%n",
                    Text.padRight(m.goods().name(), 8),
                    Text.padLeft(Text.number(e.getValue()), 6),
                    Text.money(worth),
                    Text.money(worth / e.getValue()));
        }
        out.printf("    ─ 총자산 %s G  (소지금 + 여기서 다 팔았을 때)%n",
                Text.money(trader.gold() + bookValue));
        out.printf("    부피    %.1f%n", trader.cargo().volume(data.goods()));
    }

    private void trade(String[] token, boolean buying) {
        if (token.length < 3) {
            out.println("  " + (buying ? "사기" : "팔기") + " <품목> <수량>   예: "
                    + (buying ? "사기 밀 300" : "팔기 밀 300"));
            return;
        }
        String goodsId = names.goods(token[1]);
        double qty = Double.parseDouble(token[2]);

        Receipt r = buying
                ? exchange.buy(trader, here, goodsId, qty)
                : exchange.sell(trader, here, goodsId, qty);

        report.receipt(here, r);
        out.printf("    소지금  %s G%n", Text.money(trader.gold()));
    }

    private void quote(String[] token) {
        if (token.length < 3) {
            out.println("  견적 <품목> <수량>   예: 견적 밀 300");
            return;
        }
        String goodsId = names.goods(token[1]);
        double qty = Double.parseDouble(token[2]);

        out.println();
        out.println("  사면");
        report.receiptBody(here, exchange.quoteBuy(here, goodsId, qty), "    ");
        out.println("  팔면");
        report.receiptBody(here, exchange.quoteSell(here, goodsId, qty), "    ");
    }

    private void move(String[] token) {
        if (token.length < 2) {
            out.println("  이동 <도시>   (" + names.cityList() + ")");
            return;
        }
        here = world.city(names.city(token[1]));
        out.printf("  %s 로 왔다. (아직 시간이 안 든다 — 이동 시간은 3단계에서 붙는다)%n",
                here.name());
        report.city(here);
    }

    private void wait(String[] token) {
        double hours = token.length > 1 ? Double.parseDouble(token[1]) : 1;
        long ticks = Math.round(hours * 60 / WorldClock.MINUTES_PER_TICK);
        world.advanceTo(world.tick() + ticks);
        out.printf("  세계 시간 %.0f시간이 흘렀다 → %s%n", hours, WorldClock.format(world.tick()));
        report.city(here);
    }

    public Trader trader() { return trader; }
    public City here() { return here; }
}
