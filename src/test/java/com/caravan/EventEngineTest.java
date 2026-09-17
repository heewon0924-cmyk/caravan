package com.caravan;

import com.caravan.app.Simulation;
import com.caravan.data.WorldData;
import com.caravan.event.WorldEvent;
import com.caravan.world.Market;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 4단계 — 세계 사건. docs/09-이벤트와-NPC.md 3장. */
class EventEngineTest {

    private final WorldData data = WorldData.load();

    @Test
    @DisplayName("같은 시드면 같은 세계가 나온다 — 밸런스를 비교하려면 재현돼야 한다")
    void 같은_시드면_같은_세계다() {
        Simulation a = new Simulation(data, 12345);
        Simulation b = new Simulation(data, 12345);
        a.advanceDays(10);
        b.advanceDays(10);

        for (var city : a.world().cities()) {
            for (Market m : city.markets()) {
                assertThat(m.stock())
                        .describedAs("%s / %s", city.name(), m.goods().name())
                        .isCloseTo(b.world().city(city.id()).market(m.goods().id()).stock(),
                                within(1e-9));
            }
        }
        assertThat(a.events().history()).hasSameSizeAs(b.events().history());
    }

    @Test
    @DisplayName("시드가 다르면 다른 세계가 나온다")
    void 시드가_다르면_다른_세계다() {
        Simulation a = new Simulation(data, 1);
        Simulation b = new Simulation(data, 2);
        a.advanceDays(20);
        b.advanceDays(20);

        boolean anyDifference = a.world().cities().stream().anyMatch(city ->
                city.markets().stream().anyMatch(m ->
                        Math.abs(m.stock()
                                - b.world().city(city.id()).market(m.goods().id()).stock()) > 1));
        assertThat(anyDifference).isTrue();
    }

    @Test
    @DisplayName("사건이 실제로 일어난다")
    void 사건이_일어난다() {
        Simulation sim = new Simulation(data, 42);
        sim.advanceDays(30);

        assertThat(sim.events().history()).isNotEmpty();
    }

    @Test
    @DisplayName("동시에 진행되는 사건이 설정값을 넘지 않는다")
    void 동시_진행이_제한된다() {
        Simulation sim = new Simulation(data, 42);
        for (int i = 0; i < 60; i++) {
            sim.advanceDays(1);
            assertThat(sim.activeEvents().size() + sim.forecasts().size())
                    .isLessThanOrEqualTo(data.rules().maxConcurrentEvents());
        }
    }

    @Test
    @DisplayName("예고가 시작보다 먼저 돈다 — 예고 없이 터지면 정보가 무의미해진다")
    void 예고가_먼저_돈다() {
        Simulation sim = new Simulation(data, 7);
        boolean sawForecast = false;

        for (int i = 0; i < 60 * 6 && !sawForecast; i++) {
            sim.advanceHours(4);
            for (WorldEvent e : sim.forecasts()) {
                assertThat(e.startTick())
                        .describedAs("%s 의 예고가 시작 이후에 돈다", e.spec().name())
                        .isGreaterThan(sim.tick());
                assertThat(e.spec().hasForecast()).isTrue();
                sawForecast = true;
            }
        }
        assertThat(sawForecast).describedAs("예고가 한 번도 안 돌았다").isTrue();
    }

    @Test
    @DisplayName("사건은 끝이 있다 — 언제까지 오를지 계산할 수 있어야 한다")
    void 사건에는_끝이_있다() {
        Simulation sim = new Simulation(data, 42);
        sim.advanceDays(40);

        for (WorldEvent e : sim.events().history()) {
            assertThat(e.endTick()).isGreaterThan(e.startTick());
            assertThat(e.hasEndedBy(sim.tick())).isTrue();
        }
    }

    @Test
    @DisplayName("사건이 끝나면 보정이 남지 않는다")
    void 끝난_사건의_보정은_사라진다() {
        Simulation sim = new Simulation(data, 42);
        sim.advanceDays(60);

        // 진행 중인 사건이 없는 순간을 찾아 보정이 모두 1.0 인지 본다
        for (int i = 0; i < 200; i++) {
            sim.advanceHours(2);
            if (!sim.activeEvents().isEmpty()) {
                continue;
            }
            for (var city : sim.world().cities()) {
                for (Market m : city.markets()) {
                    assertThat(m.productionModifier()).isCloseTo(1.0, within(1e-9));
                    assertThat(m.consumptionModifier()).isCloseTo(1.0, within(1e-9));
                }
            }
            return;
        }
    }

    @Test
    @DisplayName("사건은 가격을 직접 바꾸지 않는다 — 생산·소비를 바꾸고 가격이 따라온다")
    void 사건은_생산과_소비를_바꾼다() {
        Simulation sim = new Simulation(data, 42);

        for (int i = 0; i < 2000; i++) {
            sim.advanceHours(2);
            var production = sim.activeEvents().stream()
                    .filter(e -> e.spec().productionMultiple() != null)
                    .findFirst();
            if (production.isEmpty()) {
                continue;
            }

            WorldEvent event = production.get();
            Market target = sim.world().city(event.cityId())
                    .market(event.spec().goods().get(0));

            assertThat(target.productionModifier())
                    .describedAs("%s 이 %s 의 생산을 바꾸지 않았다",
                            event.spec().name(), target.goods().name())
                    .isCloseTo(event.spec().production(), within(1e-9));
            assertThat(target.isAffectedByEvent()).isTrue();
            return;
        }
    }

    @Test
    @DisplayName("노선 위험도가 사건으로 오른다 — 7단계에서 실제로 굴린다")
    void 도적이_들면_길이_위험해진다() {
        Simulation sim = new Simulation(data, 3);

        for (int i = 0; i < 3000; i++) {
            sim.advanceHours(2);
            var bandits = sim.activeEvents().stream()
                    .filter(e -> e.spec().affectsRoutes())
                    .findFirst();
            if (bandits.isEmpty()) {
                continue;
            }
            String routeId = bandits.get().spec().routes().get(0);
            assertThat(sim.events().dangerMultiplier(routeId)).isGreaterThan(1.0);
            return;
        }
    }
}
