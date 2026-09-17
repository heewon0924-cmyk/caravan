package com.caravan.data;

/**
 * 노선 정의. {@code resources/data/routes.yml} 에서 읽는다.
 *
 * <p>노선은 <b>양방향</b>이다. {@code from}/{@code to} 는 적는 순서일 뿐 방향이 아니다.
 *
 * @param hours  빈 캐러밴 기준 소요 시간(세계 시간). 실제로는 적재율만큼 느려진다
 * @param danger 습격 확률의 기본값. 7단계에서 실제로 굴린다
 * @param toll   통행료
 */
public record RouteSpec(String id,
                        String name,
                        String from,
                        String to,
                        double hours,
                        double danger,
                        double toll,
                        String note) {

    public boolean connects(String cityId) {
        return from.equals(cityId) || to.equals(cityId);
    }

    /** {@code cityId} 반대쪽 끝. */
    public String otherEnd(String cityId) {
        if (from.equals(cityId)) return to;
        if (to.equals(cityId)) return from;
        throw new IllegalArgumentException(name + " 은 " + cityId + " 를 지나지 않는다");
    }

    public boolean links(String a, String b) {
        return (from.equals(a) && to.equals(b)) || (from.equals(b) && to.equals(a));
    }
}
