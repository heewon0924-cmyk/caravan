package com.caravan.people;

/**
 * 진행 중인 전직 하나.
 *
 * <p><b>도시에 머무는 동안에만 진행된다.</b> 이동 중에는 멈춘다 (docs/03 6장).
 * 이게 "지금 전직할 것인가, 한 바퀴 더 돌 것인가" 를 진짜 기회비용으로 만든다.
 *
 * <p>여기서 진짜 비용은 돈이 아니라 시간이다. 6시간을 셀리아에 앉아 있으면
 * 그동안 하른↔카르덴을 세 번 왕복할 수 있었다 — <b>그 왕복이 전직의 진짜 가격이다</b>
 * (docs/07 5장).
 */
public final class Training {

    private final Person person;
    private final String cityId;
    private final String aim;
    private final boolean tutor;
    private final long requiredTicks;
    private final double cost;

    private long accumulated;

    /** 진행 중이면 마지막으로 정산한 틱, 멈춰 있으면 {@code null}. */
    private Long runningSince;

    public Training(Person person, String cityId, String aim, boolean tutor,
                    long requiredTicks, double cost, long startTick) {
        this.person = person;
        this.cityId = cityId;
        this.aim = aim;
        this.tutor = tutor;
        this.requiredTicks = requiredTicks;
        this.cost = cost;
        this.runningSince = startTick;
    }

    /** 그 도시에 머무는 동안 흐른 만큼 쌓는다. */
    public void accrue(long now) {
        if (runningSince != null) {
            accumulated += Math.max(0, now - runningSince);
            runningSince = now;
        }
    }

    /** 떠났다. 여기까지 쌓고 멈춘다. */
    public void pause(long now) {
        accrue(now);
        runningSince = null;
    }

    /** 돌아왔다. 다시 쌓기 시작한다. */
    public void resume(long now) {
        if (runningSince == null) {
            runningSince = now;
        }
    }

    public boolean isRunning() {
        return runningSince != null;
    }

    public boolean isDone(long now) {
        long done = accumulated + (runningSince != null ? Math.max(0, now - runningSince) : 0);
        return done >= requiredTicks;
    }

    public long remainingTicks(long now) {
        long done = accumulated + (runningSince != null ? Math.max(0, now - runningSince) : 0);
        return Math.max(0, requiredTicks - done);
    }

    public double progress(long now) {
        long done = accumulated + (runningSince != null ? Math.max(0, now - runningSince) : 0);
        return Math.min(1.0, (double) done / requiredTicks);
    }

    public Person person() { return person; }
    public String cityId() { return cityId; }
    public String aim() { return aim; }
    public boolean tutor() { return tutor; }
    public double cost() { return cost; }
    public long requiredTicks() { return requiredTicks; }
}
