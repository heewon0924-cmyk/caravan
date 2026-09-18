package com.caravan.travel;

import com.caravan.data.GoodsSpec;
import com.caravan.trade.Cargo;

import java.util.Map;

/**
 * 플레이어의 캐러밴. 도시에 머물러 있거나, 노선 위를 가고 있거나 둘 중 하나다.
 *
 * <p>캐러밴은 <b>플레이어의 결정이 물리적 형태로 나타난 것</b>이다 —
 * 무엇을 실을지, 어느 길로 갈지가 전부 여기 들어간다 (docs/05-캐러밴.md 1장).
 *
 * <p>인물 편성은 아직 없다. 6단계에서 붙는다.
 */
public final class Caravan {

    private final String id;
    private final String name;
    private final double capacity;
    private final Cargo cargo = new Cargo();

    private String cityId;
    private Journey journey;

    /** 탄 사람들이 늘려주는 적재 용량. 인내가 높을수록 더 싣는다 (docs/06 3장). */
    private double capacityBonus;

    public Caravan(String id, String name, double capacity, String startCityId) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.cityId = startCityId;
    }

    /**
     * 지금 어느 도시에 있는가. 이동 중이면 {@code null}.
     *
     * <p>도착 시각이 지났으면 <b>읽는 김에 도착 처리를 한다.</b> 세계가 매 틱
     * 캐러밴을 훑는 대신 필요할 때 정산하는 것이라, 도시 재고를 밀린 틱만큼
     * 몰아서 계산하는 것과 같은 방식이다 (docs/03-이동과-시간.md 2장).
     */
    public String cityId(long tick) {
        settle(tick);
        return cityId;
    }

    public boolean isTravelling(long tick) {
        settle(tick);
        return journey != null;
    }

    /** 이동 중이면 그 이동, 아니면 {@code null}. */
    public Journey journey(long tick) {
        settle(tick);
        return journey;
    }

    /**
     * 떠난다. <b>되돌릴 수 없다</b> — 회군을 허용하면 "위험한 길을 감수한다" 는
     * 결정이 무의미해진다. 위험이 닥치면 돌아오면 되니까 (docs/03 1장).
     */
    void depart(Journey journey) {
        if (this.journey != null) {
            throw new IllegalStateException(name + " 은 이미 " + this.journey.toId() + " 로 가는 중이다");
        }
        this.journey = journey;
        this.cityId = null;
    }

    private void settle(long tick) {
        if (journey != null && journey.hasArrived(tick)) {
            cityId = journey.toId();
            journey = null;
        }
    }

    /** 실린 것의 총 부피. */
    public double load(Map<String, GoodsSpec> goods) {
        return cargo.volume(goods);
    }

    /**
     * 0.0(빈 캐러밴) ~ 1.0(가득).
     *
     * <p>{@code capacity} 필드가 아니라 {@link #capacity()} 를 쓴다 —
     * 탄 사람들이 늘려준 만큼이 빠지면 적재 칸만 늘고 실을 수는 없게 된다.
     */
    public double loadRatio(Map<String, GoodsSpec> goods) {
        return load(goods) / capacity();
    }

    public double freeSpace(Map<String, GoodsSpec> goods) {
        return capacity() - load(goods);
    }

    /** {@code Company} 가 인원이 바뀔 때마다 다시 계산해서 넣는다. */
    public void setCapacityBonus(double bonus) {
        this.capacityBonus = Math.max(0, bonus);
    }

    public String id() { return id; }
    public String name() { return name; }
    public double capacity() { return capacity + capacityBonus; }
    public double baseCapacity() { return capacity; }
    public Cargo cargo() { return cargo; }
}
