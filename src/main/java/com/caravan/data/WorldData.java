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
    private final List<RouteSpec> routes;
    private final List<EventSpec> events;
    private final Map<String, JobSpec> jobs;
    private final Map<String, NamedSpec> named;
    private final WorldRules rules;
    private final TransferRules transfer;

    private WorldData(Map<String, GoodsSpec> goods, List<CitySpec> cities,
                      List<RouteSpec> routes, List<EventSpec> events,
                      Map<String, JobSpec> jobs, Map<String, NamedSpec> named,
                      WorldRules rules, TransferRules transfer) {
        this.goods = goods;
        this.cities = cities;
        this.routes = routes;
        this.events = events;
        this.jobs = jobs;
        this.named = named;
        this.rules = rules;
        this.transfer = transfer;
    }

    /** 클래스패스의 {@code data/} 에서 읽는다. */
    public static WorldData load() {
        return load("data/goods.yml", "data/cities.yml", "data/routes.yml",
                "data/events.yml", "data/jobs.yml", "data/world.yml");
    }

    public static WorldData load(String goodsPath, String citiesPath, String routesPath,
                                 String eventsPath, String jobsPath, String worldPath) {
        List<GoodsSpec> goodsList = read(goodsPath, GoodsFile.class).goods();
        List<CitySpec> cityList = read(citiesPath, CityFile.class).cities();
        List<RouteSpec> routeList = read(routesPath, RouteFile.class).routes();
        List<EventSpec> eventList = read(eventsPath, EventFile.class).events();
        JobFile jobFile = read(jobsPath, JobFile.class);
        WorldFile worldFile = read(worldPath, WorldFile.class);
        WorldRules rules = worldFile.world();

        Map<String, GoodsSpec> byId = new LinkedHashMap<>();
        for (GoodsSpec g : goodsList) {
            if (byId.put(g.id(), g) != null) {
                throw new IllegalStateException("품목 id 가 중복된다: " + g.id());
            }
        }

        validate(byId, cityList);
        validateRoutes(routeList, cityList);
        validateEvents(eventList, byId, cityList, routeList);

        Map<String, JobSpec> jobById = new LinkedHashMap<>();
        for (JobSpec job : jobFile.jobs()) {
            if (jobById.put(job.id(), job) != null) {
                throw new IllegalStateException("직업 id 가 중복된다: " + job.id());
            }
        }
        Map<String, NamedSpec> namedById = new LinkedHashMap<>();
        for (NamedSpec n : jobFile.namedOrEmpty()) {
            if (namedById.put(n.id(), n) != null) {
                throw new IllegalStateException("고유 캐릭터 id 가 중복된다: " + n.id());
            }
        }
        validateJobs(jobById, namedById, cityList);

        return new WorldData(byId, cityList, routeList, eventList,
                jobById, namedById, rules, worldFile.transfer());
    }

    private static void validateJobs(Map<String, JobSpec> jobs, Map<String, NamedSpec> named,
                                     List<CitySpec> cities) {
        if (jobs.isEmpty()) {
            throw new IllegalStateException("직업이 하나도 없다");
        }
        java.util.Set<String> families = new java.util.LinkedHashSet<>();

        for (JobSpec job : jobs.values()) {
            families.add(job.family());
            for (JobSpec.Transition t : job.nextSteps()) {
                if (!jobs.containsKey(t.to())) {
                    throw new IllegalStateException(
                            job.name() + " 이 모르는 직업을 가리킨다: " + t.to());
                }
                if (t.weight() <= 0) {
                    throw new IllegalStateException(
                            job.name() + " → " + t.to() + " 의 가중치가 0 이하다");
                }
            }
            if (job.canAwaken()) {
                for (String id : job.awakensTo()) {
                    if (!named.containsKey(id)) {
                        throw new IllegalStateException(
                                job.name() + " 이 모르는 고유 캐릭터를 가리킨다: " + id);
                    }
                }
            }
            // 막다른 길이 있으면 그 갈래가 나온 순간이 "실패" 가 된다.
            // 모든 갈래에 쓸모가 있어야 랜덤 전직이 성립한다 (docs/07 1장).
            if (job.isFinal() && !job.canAwaken() && job.tier() < 3) {
                throw new IllegalStateException(
                        job.name() + " 은 다음 단계도 각성도 없는 막다른 길이다");
            }
        }

        // 도시의 수련처가 있지도 않은 계열을 가리키면 조용히 아무 일도 안 일어난다
        for (CitySpec city : cities) {
            if (city.training() == null) {
                continue;
            }
            for (String family : city.training().keySet()) {
                if (!families.contains(family)) {
                    throw new IllegalStateException(
                            city.name() + " 의 수련처가 모르는 계열을 가리킨다: " + family);
                }
            }
        }
    }

    private static void validateEvents(List<EventSpec> events, Map<String, GoodsSpec> goods,
                                       List<CitySpec> cities, List<RouteSpec> routes) {
        java.util.Set<String> cityIds = new java.util.LinkedHashSet<>();
        cities.forEach(c -> cityIds.add(c.id()));
        java.util.Set<String> routeIds = new java.util.LinkedHashSet<>();
        routes.forEach(r -> routeIds.add(r.id()));

        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (EventSpec e : events) {
            if (!seen.add(e.id())) {
                throw new IllegalStateException("이벤트 id 가 중복된다: " + e.id());
            }
            if (e.city() != null && !cityIds.contains(e.city())) {
                throw new IllegalStateException(e.name() + " 이 모르는 도시를 가리킨다: " + e.city());
            }
            if (e.goods() != null) {
                for (String g : e.goods()) {
                    if (!goods.containsKey(g)) {
                        throw new IllegalStateException(e.name() + " 이 모르는 품목을 가리킨다: " + g);
                    }
                }
            }
            if (e.routes() != null) {
                for (String r : e.routes()) {
                    if (!routeIds.contains(r)) {
                        throw new IllegalStateException(e.name() + " 이 모르는 노선을 가리킨다: " + r);
                    }
                }
            }
            if (e.durationHours() <= 0) {
                throw new IllegalStateException(e.name() + " 의 지속 시간이 0 이하다");
            }
            if (e.weight() <= 0) {
                throw new IllegalStateException(e.name() + " 의 무게가 0 이하다");
            }
            // 품목을 지정했는데 도시를 안 정하면 어느 도시의 그 품목인지 알 수 없다
            if (e.goods() != null && !e.goods().isEmpty() && e.city() == null) {
                throw new IllegalStateException(
                        e.name() + " 은 품목을 지정했으니 도시도 지정해야 한다");
            }
        }
    }

    private static void validateRoutes(List<RouteSpec> routes, List<CitySpec> cities) {
        java.util.Set<String> cityIds = new java.util.LinkedHashSet<>();
        cities.forEach(c -> cityIds.add(c.id()));

        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (RouteSpec r : routes) {
            if (!seen.add(r.id())) {
                throw new IllegalStateException("노선 id 가 중복된다: " + r.id());
            }
            if (!cityIds.contains(r.from()) || !cityIds.contains(r.to())) {
                throw new IllegalStateException(
                        r.name() + " 이 모르는 도시를 잇는다: " + r.from() + " ↔ " + r.to());
            }
            if (r.from().equals(r.to())) {
                throw new IllegalStateException(r.name() + " 의 양 끝이 같은 도시다");
            }
            if (r.hours() <= 0) {
                throw new IllegalStateException(r.name() + " 의 소요 시간이 0 이하다");
            }
            if (r.danger() < 0 || r.danger() > 1) {
                throw new IllegalStateException(r.name() + " 의 위험도가 0~1 이 아니다");
            }
            if (r.toll() < 0) {
                throw new IllegalStateException(r.name() + " 의 통행료가 음수다");
            }
        }

        // 어느 도시에서도 다른 모든 도시로 갈 수 있어야 한다.
        // 못 가는 도시가 생기면 그 도시는 세계에서 떨어져 나간 것인데,
        // 데이터 오타로 이렇게 되면 한참 뒤에 "왜 저기를 아무도 안 가지" 로 나타난다.
        for (String city : cityIds) {
            java.util.Set<String> reachable = reachableFrom(city, routes);
            if (!reachable.containsAll(cityIds)) {
                java.util.Set<String> missing = new java.util.LinkedHashSet<>(cityIds);
                missing.removeAll(reachable);
                throw new IllegalStateException(city + " 에서 갈 수 없는 도시가 있다: " + missing);
            }
        }
    }

    private static java.util.Set<String> reachableFrom(String start, List<RouteSpec> routes) {
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        java.util.Deque<String> queue = new java.util.ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            String here = queue.poll();
            for (RouteSpec r : routes) {
                if (r.connects(here) && seen.add(r.otherEnd(here))) {
                    queue.add(r.otherEnd(here));
                }
            }
        }
        return seen;
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
    public List<RouteSpec> routes() { return List.copyOf(routes); }
    public List<EventSpec> events() { return List.copyOf(events); }
    public List<JobSpec> jobs() { return List.copyOf(jobs.values()); }
    public List<NamedSpec> namedCharacters() { return List.copyOf(named.values()); }
    public TransferRules transfer() { return transfer; }

    public JobSpec job(String id) {
        JobSpec spec = jobs.get(id);
        if (spec == null) {
            throw new IllegalArgumentException("그런 직업이 없다: " + id);
        }
        return spec;
    }

    public NamedSpec namedCharacter(String id) {
        NamedSpec spec = named.get(id);
        if (spec == null) {
            throw new IllegalArgumentException("그런 고유 캐릭터가 없다: " + id);
        }
        return spec;
    }

    /** 1티어 직업들. 도시에서 고용할 수 있는 사람들이 이 중 하나를 달고 온다. */
    public List<JobSpec> startingJobs() {
        return jobs.values().stream().filter(j -> j.tier() == 1).toList();
    }

    public CitySpec city(String id) {
        return cities.stream().filter(c -> c.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("그런 도시가 없다: " + id));
    }

    /** {@code cityId} 에서 떠날 수 있는 노선들. */
    public List<RouteSpec> routesFrom(String cityId) {
        return routes.stream().filter(r -> r.connects(cityId)).toList();
    }

    /** 두 도시를 잇는 노선들. 여럿일 수 있다 — 그게 "이동이 곧 의사결정" 의 뼈대다. */
    public List<RouteSpec> routesBetween(String a, String b) {
        return routes.stream().filter(r -> r.links(a, b)).toList();
    }
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
    record RouteFile(List<RouteSpec> routes) { }
    record EventFile(List<EventSpec> events) { }

    record JobFile(List<JobSpec> jobs, List<NamedSpec> named) {
        List<NamedSpec> namedOrEmpty() {
            return named == null ? List.of() : named;
        }
    }
    record WorldFile(WorldRules world, TransferRules transfer) { }
}
