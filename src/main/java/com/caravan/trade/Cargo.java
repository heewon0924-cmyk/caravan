package com.caravan.trade;

import com.caravan.data.GoodsSpec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 상단이 들고 있는 물건.
 *
 * <p>화물은 <b>부피</b>로 센다. 무게가 아니라 부피인 이유는 규칙이 하나면 충분하기
 * 때문이다 (docs/05-캐러밴.md 4장). 부피당 가치가 품목의 성격을 만든다 —
 * 밀 100, 향신료 4,500.
 *
 * <p>용량 제한은 3단계(캐러밴 이동)에서 붙는다. 여기서는 부피를 세기만 한다.
 */
public final class Cargo {

    private final Map<String, Double> quantities = new LinkedHashMap<>();

    public void add(String goodsId, double quantity) {
        requirePositive(quantity);
        quantities.merge(goodsId, quantity, Double::sum);
    }

    public void remove(String goodsId, double quantity) {
        requirePositive(quantity);
        double held = quantityOf(goodsId);
        if (quantity > held + 1e-9) {
            throw new TradeRefused(
                    "가진 것보다 많이 팔 수 없다: 보유 " + fmt(held) + ", 요청 " + fmt(quantity));
        }
        double left = held - quantity;
        if (left <= 1e-9) {
            quantities.remove(goodsId);
        } else {
            quantities.put(goodsId, left);
        }
    }

    public double quantityOf(String goodsId) {
        return quantities.getOrDefault(goodsId, 0.0);
    }

    public boolean isEmpty() {
        return quantities.isEmpty();
    }

    public Map<String, Double> all() {
        return Collections.unmodifiableMap(quantities);
    }

    /** 실린 것의 총 부피. 3단계에서 캐러밴 용량·속도 계산에 쓴다. */
    public double volume(Map<String, GoodsSpec> goods) {
        return quantities.entrySet().stream()
                .mapToDouble(e -> e.getValue() * goods.get(e.getKey()).volume())
                .sum();
    }

    private static void requirePositive(double quantity) {
        if (quantity <= 0) {
            throw new TradeRefused("수량은 0보다 커야 한다: " + fmt(quantity));
        }
    }

    private static String fmt(double v) {
        return String.format("%,.0f", v);
    }
}
