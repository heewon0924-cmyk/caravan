package com.caravan.cli;

import com.caravan.app.Company;
import com.caravan.data.RouteSpec;
import com.caravan.data.WorldData;
import com.caravan.trade.Receipt;
import com.caravan.trade.TradeRefused;
import com.caravan.travel.Departure;
import com.caravan.travel.Journey;
import com.caravan.travel.TravelRefused;
import com.caravan.world.City;
import com.caravan.world.Market;
import com.caravan.world.World;
import com.caravan.world.WorldClock;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 2~3단계 검증용 대화형 콘솔.
 *
 * <p><b>이 클래스에는 규칙이 하나도 없다.</b> 입력을 {@link Company} 에 넘기고
 * 돌아온 것을 찍을 뿐이다 (docs/31-서버와-클라이언트-경계.md).
 * 용량 제한도, "이동 중엔 거래 못 함" 도 전부 서버 쪽에서 판정한다 —
 * 여기 있는 것은 거절당한 이유를 보여주는 코드뿐이다.
 *
 * <p>Phaser 클라이언트가 할 일도 정확히 이만큼이다.
 */
public final class TradeConsole {

    private final World world;
    private final WorldData data;
    private final Company company;
    private final Names names;
    private final SimReport report;
    private final PrintStream out;

    public TradeConsole(World world, WorldData data, PrintStream out) {
        this.world = world;
        this.data = data;
        this.names = new Names(data);
        this.report = new SimReport(out);
        this.out = out;
        this.company = new Company(world, data, "내 상단",
                data.cities().get(0).id());
    }

    public void run() {
        out.println();
        out.println("══ 상단을 열었다 ══");
        out.printf("  자본 %s G · 캐러밴 용량 %.0f · 지금 %s 에 있다%n",
                Text.money(company.trader().gold()),
                company.caravan().capacity(),
                company.here().name());
        help();
        status();

        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            prompt();
            while ((line = in.readLine()) != null) {
                if (!handle(line.trim())) {
                    break;
                }
                prompt();
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
                case "시세", "market" -> here("시세를 보려면 도시에 있어야 한다");
                case "사기", "buy" -> trade(token, true);
                case "팔기", "sell" -> trade(token, false);
                case "견적", "quote" -> quote(token);
                case "길", "routes" -> routes();
                case "출발", "이동", "go" -> depart(token);
                case "대기", "wait" -> waitHours(token);
                case "세계", "world" -> report.snapshot(world);
                case "끝", "quit", "exit" -> { return false; }
                default -> out.println("  모르는 말이다: " + token[0] + "   ('도움' 참조)");
            }
        } catch (TradeRefused | TravelRefused e) {
            out.println("  거절됐다 — " + e.getMessage());
        } catch (IllegalArgumentException e) {
            out.println("  " + e.getMessage());
        }
        return true;
    }

    private void help() {
        out.println("""

              상태              소지금 · 화물 · 어디에 있는지
              시세              지금 도시의 시세표
              사기 밀 60        산다        팔기 밀 60      판다
              견적 밀 60        사면 얼마인지만 본다 (아무것도 안 바뀐다)
              길                여기서 갈 수 있는 길들
              출발 카르덴        떠난다. 길이 여럿이면 노선 이름을 준다
              출발 늑대고개      노선을 직접 고른다
              대기 3            세계 시간 3시간을 흘린다
              세계              세 도시 전부 본다
              끝""");
    }

    private void prompt() {
        City city = company.here();
        out.printf("%n[%s · %s] > ",
                city != null ? city.name() : "이동 중",
                WorldClock.format(world.tick()));
        out.flush();
    }

    private void status() {
        out.println();
        City city = company.here();

        if (city == null) {
            Journey j = company.journey();
            out.printf("  이동 중 — %s 로 %s%n",
                    world.city(j.toId()).name(), j.route().name());
            out.printf("    남은 시간  %s  (%.0f%% 왔다)%n",
                    j.remainingText(world.tick()), j.progress(world.tick()) * 100);
            out.printf("    도착 예정  세계 %s%n", WorldClock.format(j.arrivesTick()));
        } else {
            out.printf("  %s · 세계 %s%n", city.name(), WorldClock.format(world.tick()));
        }

        out.printf("    소지금  %s G%n", Text.money(company.trader().gold()));
        out.printf("    적재    %.1f / %.0f  (%.0f%%)%n",
                company.load(), company.caravan().capacity(), company.loadRatio() * 100);

        if (company.cargo().isEmpty()) {
            out.println("    화물    없음");
        } else {
            out.println("    화물");
            for (Map.Entry<String, Double> e : company.cargo().all().entrySet()) {
                String label = Text.padRight(data.goods(e.getKey()).name(), 8)
                        + Text.padLeft(Text.number(e.getValue()) + "개", 7);
                if (city == null) {
                    out.printf("      %s%n", label);
                } else {
                    Receipt q = company.quoteSell(e.getKey(), e.getValue());
                    out.printf("      %s   여기서 팔면 %s G (개당 %s G)%n",
                            label, Text.money(q.net()), Text.money(q.averageUnitPrice()));
                }
            }
            if (city != null) {
                out.printf("    ─ 총자산 %s G%n", Text.money(company.netWorth()));
            }
        }
    }

    private City here(String refusal) {
        City city = company.here();
        if (city == null) {
            out.println("  " + refusal);
            return null;
        }
        report.city(city);
        return city;
    }

    private void trade(String[] token, boolean buying) {
        if (token.length < 3) {
            out.println("  " + (buying ? "사기" : "팔기") + " <품목> <수량>");
            return;
        }
        String goodsId = names.goods(token[1]);
        double qty = Double.parseDouble(token[2]);

        Receipt r = buying ? company.buy(goodsId, qty) : company.sell(goodsId, qty);
        report.receipt(company.here(), r);
        out.printf("    소지금  %s G · 적재 %.1f / %.0f%n",
                Text.money(company.trader().gold()),
                company.load(), company.caravan().capacity());
    }

    private void quote(String[] token) {
        if (token.length < 3) {
            out.println("  견적 <품목> <수량>");
            return;
        }
        String goodsId = names.goods(token[1]);
        double qty = Double.parseDouble(token[2]);

        out.println();
        out.printf("  캐러밴에 %s 를 최대 %s개까지 더 실을 수 있다%n",
                data.goods(goodsId).name(), Text.number(company.roomFor(goodsId)));
        out.println("  사면");
        report.receiptBody(company.here(), company.quoteBuy(goodsId, qty), "    ");
        out.println("  팔면");
        report.receiptBody(company.here(), company.quoteSell(goodsId, qty), "    ");
    }

    private void routes() {
        City city = company.here();
        if (city == null) {
            out.println("  이동 중이다. 길은 도착해서 고른다.");
            return;
        }
        report.routes(world, city, company.routesFromHere(), company::quoteDeparture);
    }

    private void depart(String[] token) {
        if (token.length < 2) {
            out.println("  출발 <도시>  또는  출발 <노선>");
            routes();
            return;
        }
        String target = token[1];

        // 노선 이름이면 그 길로, 도시 이름이면 그 도시로 (길이 하나일 때만)
        Departure d = isRouteName(target)
                ? company.departVia(company.route(target))
                : company.departTo(names.city(target));

        report.departure(d, world.city(d.toId()).name());
    }

    private boolean isRouteName(String token) {
        return data.routes().stream()
                .anyMatch(r -> r.name().equals(token) || r.id().equalsIgnoreCase(token));
    }

    private void waitHours(String[] token) {
        double hours = token.length > 1 ? Double.parseDouble(token[1]) : 1;
        boolean wasTravelling = company.isTravelling();
        String destination = wasTravelling
                ? world.city(company.journey().toId()).name() : null;

        world.advanceTo(world.tick() + Math.round(hours * 60 / WorldClock.MINUTES_PER_TICK));
        out.printf("%n  세계 시간 %.0f시간이 흘렀다 → %s%n", hours, WorldClock.format(world.tick()));

        if (wasTravelling && !company.isTravelling()) {
            out.printf("  ★ %s 에 도착했다. 아무것도 팔지 않았다 — 파는 시점은 직접 고른다.%n",
                    destination);
            report.city(company.here());
        } else if (company.isTravelling()) {
            Journey j = company.journey();
            out.printf("  아직 가는 중 — %s 까지 %s 남았다%n",
                    world.city(j.toId()).name(), j.remainingText(world.tick()));
        } else {
            report.city(company.here());
        }
    }

    public Company company() { return company; }
}
