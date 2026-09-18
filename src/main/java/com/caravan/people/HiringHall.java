package com.caravan.people;

import com.caravan.data.JobSpec;
import com.caravan.data.WorldData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 도시마다 일자리를 찾는 사람들이 몇 명씩 서 있다.
 *
 * <p>docs/06-인물과-직업.md 6장 — 프로토타입의 인물 수급은 <b>도시 고용뿐</b>이다.
 * 뽑기는 넣지 않는다. 넣는 순간 "뽑기로 좋은 인물을 얻고 그걸 키운다" 가 되어
 * 플레이어의 성취가 전직이 아니라 뽑기 결과로 옮겨간다.
 *
 * <p>그리고 <b>기초 직업 인물은 흔하고 싸야 한다.</b> 재료가 귀하면 원치 않는 전직 결과가
 * 견딜 수 없어지고, 그러면 랜덤 전직을 못 넣는다. 인물 수급과 전직 확률은 같은 문제다.
 */
public final class HiringHall {

    private static final String[] GIVEN = {
            "마르", "엘", "단", "보로", "미나", "카이", "요르크", "리타", "벤", "산초",
            "니카", "토르", "하야", "올라", "페드", "유나", "라피", "코엔", "세라", "딘",
    };
    private static final String[] EPITHET = {
            "말수 적은", "손 큰", "발 빠른", "겁 없는", "셈 밝은", "고집 센",
            "웃음 많은", "눈썰미 좋은", "말 잘하는", "무뚝뚝한",
    };

    private final WorldData data;
    private final Random random;
    private final Map<String, List<Person>> waiting = new LinkedHashMap<>();
    private int serial = 1;

    public HiringHall(WorldData data, long seed) {
        this.data = data;
        this.random = new Random(seed);
        for (var city : data.cities()) {
            List<Person> pool = new ArrayList<>();
            for (int i = 0; i < data.rules().hireCandidates(); i++) {
                pool.add(newcomer());
            }
            waiting.put(city.id(), pool);
        }
    }

    public List<Person> candidates(String cityId) {
        return List.copyOf(waiting.getOrDefault(cityId, List.of()));
    }

    /** 한 명을 데려간다. 빈자리는 다른 사람이 채운다. */
    public Person hire(String cityId, int index) {
        List<Person> pool = waiting.get(cityId);
        if (pool == null || index < 0 || index >= pool.size()) {
            throw new IllegalArgumentException("그런 사람이 없다: " + (index + 1) + "번");
        }
        Person hired = pool.remove(index);
        pool.add(newcomer());
        return hired;
    }

    private Person newcomer() {
        List<JobSpec> starts = data.startingJobs();
        JobSpec job = starts.get(random.nextInt(starts.size()));

        // 타고난 것은 크지 않다. 사람을 만드는 건 직업이고, 직업은 플레이어가 고른다.
        Stats innate = new Stats(
                4 + random.nextInt(9),
                4 + random.nextInt(9),
                4 + random.nextInt(9),
                4 + random.nextInt(9));

        String name = EPITHET[random.nextInt(EPITHET.length)] + " "
                + GIVEN[random.nextInt(GIVEN.length)];
        return new Person("p-" + serial++, name, innate, new Job(job));
    }
}
