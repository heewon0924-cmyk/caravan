package com.caravan.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 밸런스 데이터를 읽고 검증한다.
 *
 * <p>도시 · 품목 · 노선 · 직업 · 이벤트는 전부 데이터로 둔다. 코드에 하드코딩하지
 * 않는다 (docs/20-프로토타입-1차.md 5장). 프로토타입에서 가장 자주 바꿀 것이
 * 숫자인데, 숫자를 바꾸는 데 빌드가 필요하면 안 바꾸게 되기 때문이다.
 *
 * <p>검증은 읽는 시점에 전부 끝낸다. 밸런스 데이터의 오타는 조용히 굴러가다가
 * 한참 뒤에 이상한 숫자로 나타나는 게 최악이라, 여기서 시끄럽게 터뜨린다.
 */
public final class WorldData {

    private static final ObjectMapper YAML =
            new ObjectMapper(new YAMLFactory()).findAndRegisterModules();

    private final Map<String, GoodsSpec> goods;
    private final List<CitySpec> cities;
    private final WorldRules rules;

    private WorldData(Map<String, GoodsSpec> goods, List<CitySpec> cities, WorldRules rules) {
        this.goods = goods;
        this.cities = cities;
        this.rules = rules;
    }

    /** 클래스패스의 {@code data/} 에서 읽는다. */
    public static WorldData load() {
        return load("data/goods.yml", "data/cities.yml", "data/world.yml");
    }

    public static WorldData load(String goodsPath, String citiesPath, String worldPath) {
        List<GoodsSpec> goodsList = read(goodsPath, GoodsFile.class).goods();
        List<CitySpec> cityList = read(citiesPath, CityFile.class).cities();
        WorldRules rules = read(worldPath, WorldFile.class).world();

        Map<String, GoodsSpec> byId = new LinkedHashMap<>();
        for (GoodsSpec g : goodsList) {
            if (byId.put(g.id(), g) != null) {
                throw new IllegalStateException("품목 id 가 중복된다: " + g.id());
            }
        }

        validate(byId, cityList);
        return new WorldData(byId, cityList, rules);
    }

    private static void validate(Map<String, GoodsSpec> goods, List<CitySpec> cities) {
        if (goods.isEmpty()) {
            throw new IllegalStateException("품목이 하나도 없다");
        }
        if (cities.isEmpty()) {
            throw new IllegalStateException("도시가 하나도 없다");
        }

        Map<String, CitySpec> seen = new LinkedHashMap<>();
        for (CitySpec city : cities) {
            if (seen.put(city.id(), city) != null) {
                throw new IllegalStateException("도시 id 가 중복된다: " + city.id());
            }

            Map<String, MarketSpec> markets = new LinkedHashMap<>();
            for (MarketSpec m : city.market()) {
                if (!goods.containsKey(m.goods())) {
                    throw new IllegalStateException(
                            city.id() + " 의 시장이 모르는 품목을 가리킨다: " + m.goods());
                }
                if (markets.put(m.goods(), m) != null) {
                    throw new IllegalStateException(
                            city.id() + " 에 " + m.goods() + " 시장이 두 번 적혀 있다");
                }
                if (m.refStock() <= 0) {
                    throw new IllegalStateException(
                            city.id() + "/" + m.goods() + " 의 기준재고가 0 이하다");
                }
                if (m.productionPerDay() < 0 || m.consumptionPerDay() < 0) {
                    throw new IllegalStateException(
                            city.id() + "/" + m.goods() + " 의 생산·소비가 음수다");
                }
            }

            // 어느 도시에서든 모든 품목을 사고팔 수 있어야 한다.
            // 빠진 게 있으면 그 도시에서만 그 품목이 조용히 사라지는데,
            // 데이터 오타로 이렇게 되는 걸 밸런스 문제로 착각하기 쉽다.
            for (String goodsId : goods.keySet()) {
                if (!markets.containsKey(goodsId)) {
                    throw new IllegalStateException(
                            city.id() + " 에 " + goodsId + " 시장이 없다");
                }
            }
        }
    }

    private static <T> T read(String path, Class<T> type) {
        try (InputStream in = WorldData.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("데이터 파일을 못 찾았다: " + path);
            }
            return YAML.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException("데이터 파일을 못 읽었다: " + path, e);
        }
    }

    public Map<String, GoodsSpec> goods() { return Map.copyOf(goods); }
    public List<GoodsSpec> goodsInOrder() { return List.copyOf(goods.values()); }
    public List<CitySpec> cities() { return List.copyOf(cities); }
    public WorldRules rules() { return rules; }

    public GoodsSpec goods(String id) {
        GoodsSpec g = goods.get(id);
        if (g == null) {
            throw new IllegalArgumentException("그런 품목이 없다: " + id);
        }
        return g;
    }

    record GoodsFile(List<GoodsSpec> goods) { }
    record CityFile(List<CitySpec> cities) { }
    record WorldFile(WorldRules world) { }
}
