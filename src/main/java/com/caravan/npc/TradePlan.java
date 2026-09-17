package com.caravan.npc;

import com.caravan.data.RouteSpec;

/**
 * NPC 가 따져본 거래 하나. "이걸 사서 저기 가져다 팔면 이만큼 남을 것 같다."
 *
 * <p>{@code expectedProfit} 은 <b>기억에 근거한 추정</b>이라 틀릴 수 있다.
 * 그 도시에 가보니 값이 이미 내려가 있을 수도 있다 — 그게 정상이다.
 */
public record TradePlan(String goodsId,
                        double quantity,
                        RouteSpec route,
                        String toCityId,
                        double buyCost,
                        double expectedRevenue,
                        double travelCost,
                        double expectedProfit,
                        double expectedMargin) {
}
