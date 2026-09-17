package com.caravan.cli;

import com.caravan.data.CitySpec;
import com.caravan.data.GoodsSpec;
import com.caravan.data.WorldData;

/** 콘솔에서 "밀" 이나 "wheat" 로 쓴 것을 id 로 바꾼다. */
public final class Names {

    private final WorldData data;

    public Names(WorldData data) {
        this.data = data;
    }

    public String goods(String token) {
        return data.goodsInOrder().stream()
                .filter(g -> g.id().equalsIgnoreCase(token) || g.name().equals(token))
                .findFirst()
                .map(GoodsSpec::id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "그런 품목이 없다: " + token + "  (" + goodsList() + ")"));
    }

    public String city(String token) {
        return data.cities().stream()
                .filter(c -> c.id().equalsIgnoreCase(token) || c.name().equals(token))
                .findFirst()
                .map(CitySpec::id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "그런 도시가 없다: " + token + "  (" + cityList() + ")"));
    }

    /** "밀@하른" 을 쪼갠다. */
    public String[] pair(String token) {
        String[] parts = token.split("@");
        if (parts.length != 2) {
            throw new IllegalArgumentException("품목@도시 꼴로 적는다. 예: 밀@하른");
        }
        return new String[]{ goods(parts[0]), city(parts[1]) };
    }

    public String routeList() {
        return String.join(", ", data.routes().stream()
                .map(com.caravan.data.RouteSpec::name).toList());
    }

    public String goodsList() {
        return String.join(", ", data.goodsInOrder().stream().map(GoodsSpec::name).toList());
    }

    public String cityList() {
        return String.join(", ", data.cities().stream().map(CitySpec::name).toList());
    }
}
