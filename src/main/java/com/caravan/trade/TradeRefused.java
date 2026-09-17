package com.caravan.trade;

/**
 * 거래가 거절됐다. 소지금이 모자라거나, 재고가 없거나, 가진 것보다 많이 팔려 했거나.
 *
 * <p>버그가 아니라 <b>정상적인 거절</b>이다. 화면에서는 그대로 사용자에게 보여줄 문장이
 * 된다. 그래서 메시지에 숫자를 담아 왜 안 되는지가 보이게 한다.
 */
public class TradeRefused extends RuntimeException {

    public TradeRefused(String message) {
        super(message);
    }
}
