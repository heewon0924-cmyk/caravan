package com.caravan.world;

import com.caravan.data.CitySpec;
import com.caravan.data.MarketSpec;
import com.caravan.data.WorldData;
import com.caravan.data.WorldRules;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 도시 하나.
 *
 * <p>도시는 시장 · 길 · 소문 · 수련처를 가진다 (docs/01-세계와-도시.md 3장).
 * 1단계에서는 시장만 있다.
 */
public final class City {

    private final CitySpec spec;
    private final Map<String, Market> markets = new LinkedHashMap<>();

    City(CitySpec spec, WorldData data, WorldRules rules) {
        this.spec = spec;
        for (MarketSpec m : spec.market()) {
            markets.put(m.goods(), new Market(data.goods(m.goods()), m, rules));
        }
    }

    void tick() {
        for (Market m : markets.values()) {
            m.tick();
        }
    }

    public Market market(String goodsId) {
        Market m = markets.get(goodsId);
        if (m == null) {
            throw new IllegalArgumentException(spec.id() + " 에 " + goodsId + " 시장이 없다");
        }
        return m;
    }

    public Collection<Market> markets() { return markets.values(); }
    public String id() { return spec.id(); }
    public String name() { return spec.name(); }
    public String trait() { return spec.trait(); }
}
