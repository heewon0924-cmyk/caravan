package com.caravan.travel;

/**
 * 이동이 거절됐다. 이동 중이거나, 그 도시에서 갈 수 없는 길이거나, 비용이 모자라거나.
 *
 * <p>{@code TradeRefused} 와 마찬가지로 버그가 아니라 정상적인 거절이고,
 * 메시지는 그대로 화면에 보여줄 문장이다.
 */
public class TravelRefused extends RuntimeException {

    public TravelRefused(String message) {
        super(message);
    }
}
