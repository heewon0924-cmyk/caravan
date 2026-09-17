package com.caravan.npc;

import com.caravan.data.TemperamentSpec;
import com.caravan.data.WorldData;
import com.caravan.trade.Exchange;
import com.caravan.travel.Travel;
import com.caravan.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 세계를 도는 NPC 상인들.
 *
 * <p>docs/09-이벤트와-NPC.md 2장 — 소심 2, 보통 3, 과감 1.
 * 성격을 섞는 이유는 다양해 보이려는 게 아니라 <b>차익을 전부 먹어치우지 않게</b>
 * 하려는 것이다.
 */
public final class NpcFleet {

    private final List<NpcMerchant> merchants = new ArrayList<>();
    private final WorldData data;
    private final Exchange exchange;
    private final Travel travel;

    public NpcFleet(WorldData data) {
        this.data = data;
        this.exchange = new Exchange(data.rules());
        this.travel = new Travel(data);

        List<String> cities = data.cities().stream().map(c -> c.id()).toList();
        double gold = data.rules().npcStartingGold();
        double capacity = data.rules().npcCaravanCapacity();
        long dwell = NpcMerchant.defaultDwellTicks();

        // 성격과 인원은 데이터가 정한다 (world.yml 의 npcTemperaments).
        // 모두가 과감하면 차익이 남지 않고, 모두가 소심하면 잉여가 쌓인다.
        int index = 0;
        for (TemperamentSpec spec : data.rules().npcTemperaments()) {
            for (int i = 0; i < spec.count(); i++, index++) {
                merchants.add(new NpcMerchant(
                        "npc-" + (index + 1), nameFor(index), spec,
                        gold, capacity, cities.get(index % cities.size()), dwell));
            }
        }
    }

    /** 한 틱분. 모두가 한 번씩 판단하고 움직인다. */
    public void act(long tick, World world) {
        for (NpcMerchant m : merchants) {
            m.act(tick, world, data, exchange, travel);
        }
    }

    private static final String[] NAMES = {
            "늙은 마르코", "조심스런 엘사", "떠돌이 단", "수레꾼 보로",
            "장사치 미나", "겁없는 카이", "외눈 요르크", "말주변 리타",
            "구두쇠 벤", "노새몰이 산초", "발빠른 니카", "허풍쟁이 토르",
            "짐꾼 하야", "셈 빠른 올라", "느긋한 페드로", "재바른 유나",
    };

    private static String nameFor(int index) {
        return index < NAMES.length
                ? NAMES[index]
                : NAMES[index % NAMES.length] + " " + (index / NAMES.length + 1) + "세";
    }

    public List<NpcMerchant> merchants() {
        return List.copyOf(merchants);
    }

    /** "도시/품목" → 전원이 지금까지 날라다 판 총량. */
    public java.util.Map<String, Double> deliveries() {
        java.util.Map<String, Double> all = new java.util.LinkedHashMap<>();
        for (NpcMerchant m : merchants) {
            m.delivered().forEach((k, v) -> all.merge(k, v, Double::sum));
        }
        return all;
    }

    /** 아무 데도 못 가고 쉰 틱의 합. 크면 판단 기준이 너무 빡빡한 것이다. */
    public long totalIdleTicks() {
        return merchants.stream().mapToLong(NpcMerchant::idleTicks).sum();
    }

    /** 전원이 굴리는 총 자본. 경제가 커지는지 쪼그라드는지 보는 지표. */
    public double totalGold() {
        return merchants.stream().mapToDouble(m -> m.trader().gold()).sum();
    }
}
