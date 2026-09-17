package com.caravan;

import com.caravan.cli.SimReport;
import com.caravan.data.WorldData;
import com.caravan.world.City;
import com.caravan.world.World;
import com.caravan.world.WorldClock;

/**
 * 1단계 검증용 콘솔.
 *
 * <pre>
 *   caravan sim   [--days 7] [--every 1]   세계를 굴리고 하루마다 찍는다
 *   caravan quote 밀@하른 --qty 300 [--sell] 대량 거래가 시세를 얼마나 미는지 본다
 *   caravan curve 밀@하른                    재고에 따른 시세표
 *   caravan data                             읽어들인 밸런스 데이터를 확인한다
 * </pre>
 *
 * 도시와 품목은 id(harn, wheat)로도 이름(하른, 밀)으로도 쓸 수 있다.
 */
public final class Main {

    public static void main(String[] args) {
        WorldData data = WorldData.load();
        SimReport report = new SimReport(System.out);
        String command = args.length == 0 ? "sim" : args[0];

        switch (command) {
            case "sim" -> sim(data, report, args);
            case "quote" -> quote(data, report, args);
            case "curve" -> curve(data, report, args);
            case "data" -> data(data);
            default -> {
                System.err.println("모르는 명령이다: " + command);
                System.err.println("쓸 수 있는 것: sim, quote, curve, data");
                System.exit(2);
            }
        }
    }

    private static void sim(WorldData data, SimReport report, String[] args) {
        int days = intArg(args, "--days", 7);
        int every = intArg(args, "--every", 1);

        World world = new World(data);
        System.out.printf("세계를 %d일 굴린다. 시간 배율 %.0f× (현실 %s 분량)%n",
                days, data.rules().speed(),
                formatReal(days * 24.0 * 60 / data.rules().speed()));
        System.out.println("아무도 거래하지 않는다 — NPC 상인은 4단계에 들어온다.");

        report.snapshot(world);
        for (int day = 1; day <= days; day++) {
            world.advanceDays(1);
            if (day % every == 0 || day == days) {
                report.snapshot(world);
            }
        }
    }

    private static void quote(WorldData data, SimReport report, String[] args) {
        Target target = Target.parse(args, data);
        double qty = doubleArg(args, "--qty", 300);
        boolean sell = has(args, "--sell");

        World world = new World(data);
        report.quote(world.city(target.cityId()), target.goodsId(), qty, sell);
    }

    private static void curve(WorldData data, SimReport report, String[] args) {
        Target target = Target.parse(args, data);
        World world = new World(data);
        report.curveTable(world.city(target.cityId()), target.goodsId());
    }

    private static void data(WorldData data) {
        System.out.printf("%n품목 %d개%n", data.goodsInOrder().size());
        data.goodsInOrder().forEach(g ->
                System.out.printf("  %-6s %-5s 기준가 %,6.0fG  탄력도 %.2f  부피 %.1f  부피당 %,6.0fG%n",
                        g.id(), g.name(), g.basePrice(), g.elasticity(), g.volume(), g.valuePerVolume()));

        System.out.printf("%n도시 %d개%n", data.cities().size());
        data.cities().forEach(c -> {
            System.out.printf("  %-7s %s — %s%n", c.id(), c.name(), c.trait());
            c.market().forEach(m ->
                    System.out.printf("      %-6s 기준재고 %,7.0f  생산 %,5.0f/일  소비 %,5.0f/일  순 %+,6.0f/일%n",
                            m.goods(), m.refStock(), m.productionPerDay(),
                            m.consumptionPerDay(), m.netPerDay()));
        });

        System.out.printf("%n규칙%n  시간 배율 %.0f×  ·  한 틱 %d분  ·  하루 %d틱%n",
                data.rules().speed(), WorldClock.MINUTES_PER_TICK, WorldClock.TICKS_PER_DAY);
        System.out.printf("  곳간 상한 기준재고 ×%.1f  ·  넘친 분량 매 틱 %.0f%% 상함%n",
                data.rules().granaryCapMultiple(), data.rules().spoilRatePerTick() * 100);
        System.out.printf("  시세 하한 기준가 ×%.2f  ·  상한 ×%.1f%n",
                data.rules().priceFloorMultiple(), data.rules().priceCeilingMultiple());
    }

    /** "밀@하른" 이나 "wheat@harn" 을 도시·품목 id 로 바꾼다. */
    private record Target(String cityId, String goodsId) {

        static Target parse(String[] args, WorldData data) {
            String raw = args.length > 1 && !args[1].startsWith("--") ? args[1] : "wheat@harn";
            String[] parts = raw.split("@");
            if (parts.length != 2) {
                throw new IllegalArgumentException("품목@도시 꼴로 적는다. 예: 밀@하른");
            }
            return new Target(resolveCity(parts[1], data), resolveGoods(parts[0], data));
        }

        private static String resolveGoods(String token, WorldData data) {
            return data.goodsInOrder().stream()
                    .filter(g -> g.id().equalsIgnoreCase(token) || g.name().equals(token))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("그런 품목이 없다: " + token))
                    .id();
        }

        private static String resolveCity(String token, WorldData data) {
            return data.cities().stream()
                    .filter(c -> c.id().equalsIgnoreCase(token) || c.name().equals(token))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("그런 도시가 없다: " + token))
                    .id();
        }
    }

    private static String formatReal(double minutes) {
        if (minutes < 60) {
            return String.format("%.0f분", minutes);
        }
        return String.format("%.1f시간", minutes / 60);
    }

    private static boolean has(String[] args, String flag) {
        for (String a : args) {
            if (a.equals(flag)) return true;
        }
        return false;
    }

    private static int intArg(String[] args, String flag, int fallback) {
        return (int) doubleArg(args, flag, fallback);
    }

    private static double doubleArg(String[] args, String flag, double fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(flag)) {
                return Double.parseDouble(args[i + 1]);
            }
        }
        return fallback;
    }
}
