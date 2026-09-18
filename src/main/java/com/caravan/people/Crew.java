package com.caravan.people;

import java.util.ArrayList;
import java.util.List;

/**
 * 캐러밴에 탄 사람들. <b>여섯 칸이다.</b>
 *
 * <p>역할이 6종이라 "전부 하나씩" 이 정확히 꽉 찬다. 그래서 하나를 더 넣으려면
 * 반드시 하나를 빼야 하고, <b>모든 칸이 기회비용이 된다</b> (docs/05 2장).
 *
 * <p>여섯 칸을 균형 있게 채우면 평범하고, 한쪽으로 몰면 뾰족해진다.
 * 정보 둘 + 상인 둘을 태우면 돈은 잘 벌리는데 늑대고개는 못 간다.
 * 그 선택이 플레이 스타일이 된다.
 */
public final class Crew {

    private final int slots;
    private final List<Person> members = new ArrayList<>();

    public Crew(int slots) {
        this.slots = slots;
    }

    public void add(Person person) {
        if (isFull()) {
            throw new IllegalStateException(
                    "자리가 없다: " + members.size() + " / " + slots);
        }
        members.add(person);
    }

    public boolean remove(Person person) {
        return members.remove(person);
    }

    public boolean isFull() {
        return members.size() >= slots;
    }

    public int freeSlots() {
        return slots - members.size();
    }

    public List<Person> members() {
        return List.copyOf(members);
    }

    public List<Person> withRole(String role) {
        return members.stream().filter(p -> p.has(role)).toList();
    }

    /** 모두의 능력치 합. 캐러밴 효과는 이걸로 계산한다. */
    public Stats total() {
        return members.stream().map(Person::stats).reduce(Stats.ZERO, Stats::plus);
    }

    public Person byId(String id) {
        return members.stream().filter(p -> p.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("그런 인물이 없다: " + id));
    }

    public boolean contains(String id) {
        return members.stream().anyMatch(p -> p.id().equals(id));
    }

    public int size() { return members.size(); }
    public int slots() { return slots; }
    public boolean isEmpty() { return members.isEmpty(); }
}
