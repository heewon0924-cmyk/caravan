package com.caravan.trade;

/**
 * 거래하는 주체. 플레이어의 상단이든 NPC 상인이든 같은 타입을 쓴다.
 *
 * <p>이게 중요하다 — 4단계에서 NPC 상인이 들어올 때 {@link Exchange} 를 그대로 부른다.
 * NPC 가 플레이어와 다른 규칙으로 거래하면 세계가 거짓말이 되고, 플레이어가
 * "저 NPC 는 왜 저기로 가지?" 를 보고 시세를 역추론할 수 없게 된다
 * (docs/09-이벤트와-NPC.md 2장).
 */
public final class Trader {

    private final String id;
    private final String name;
    private final Cargo cargo = new Cargo();
    private double gold;

    public Trader(String id, String name, double startingGold) {
        if (startingGold < 0) {
            throw new IllegalArgumentException("시작 자본은 음수일 수 없다: " + startingGold);
        }
        this.id = id;
        this.name = name;
        this.gold = startingGold;
    }

    void pay(double amount) {
        if (amount > gold + 1e-9) {
            throw new TradeRefused(String.format(
                    "소지금이 모자란다: 가진 돈 %,.0f G, 필요한 돈 %,.0f G", gold, amount));
        }
        gold -= amount;
    }

    void earn(double amount) {
        gold += amount;
    }

    public String id() { return id; }
    public String name() { return name; }
    public double gold() { return gold; }
    public Cargo cargo() { return cargo; }
}
