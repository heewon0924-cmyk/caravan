package com.caravan.npc;

import com.caravan.world.City;
import com.caravan.world.Market;

import java.util.HashMap;
import java.util.Map;

/**
 * 이 상인이 <b>기억하고 있는</b> 시세.
 *
 * <p>NPC 는 전지하지 않다. 자기가 마지막으로 그 도시에 있었을 때의 값만 안다 —
 * 플레이어와 똑같은 정보 지연을 겪는다 (docs/09-이벤트와-NPC.md 2장).
 *
 * <p>이게 NPC 가 플레이어보다 느린 이유의 절반이다. 흉년이 나서 밀값이 뛰어도,
 * 그 도시에 가본 NPC 가 나올 때까지는 아무도 모른다.
 * <b>정보를 먼저 아는 플레이어가 NPC 보다 앞선다.</b>
 */
public final class PriceMemory {

    private final Map<String, Double> price = new HashMap<>();
    private final Map<String, Double> stock = new HashMap<>();
    private final Map<String, Long> seenAt = new HashMap<>();

    /** 지금 있는 도시의 시세를 눈으로 본다. 여기 값만은 정확하다. */
    public void observe(City city, long tick) {
        for (Market m : city.markets()) {
            String key = key(city.id(), m.goods().id());
            price.put(key, m.spotPrice());
            stock.put(key, m.stock());
            seenAt.put(key, tick);
        }
    }

    /** 기억하는 시세. 가본 적이 없으면 {@code fallback}(보통 기준가). */
    public double recall(String cityId, String goodsId, double fallback) {
        return price.getOrDefault(key(cityId, goodsId), fallback);
    }

    /**
     * 기억하는 재고. 가본 적이 없으면 {@code fallback}(보통 기준재고).
     *
     * <p>재고를 기억하는 것이 중요하다. 값만 기억하면 "거기 900G 니까 200개 갖다 팔면
     * 18만" 으로 계산하는데, 그 도시 창고가 작으면 다 팔기도 전에 값이 반토막 난다.
     * 플레이어도 재고를 눈대중으로 보므로(docs/02 5장) 공정하다.
     */
    public double recallStock(String cityId, String goodsId, double fallback) {
        return stock.getOrDefault(key(cityId, goodsId), fallback);
    }

    /** 그 기억이 몇 틱이나 묵었는가. 가본 적이 없으면 {@code Long.MAX_VALUE}. */
    public long ageOf(String cityId, String goodsId, long now) {
        Long seen = seenAt.get(key(cityId, goodsId));
        return seen == null ? Long.MAX_VALUE : now - seen;
    }

    public boolean hasSeen(String cityId, String goodsId) {
        return price.containsKey(key(cityId, goodsId));
    }

    private static String key(String cityId, String goodsId) {
        return cityId + "/" + goodsId;
    }
}
