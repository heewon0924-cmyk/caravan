package com.caravan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * docs/31-서버와-클라이언트-경계.md 의 규칙을 <b>깨지면 빌드가 실패하도록</b> 못 박는다.
 *
 * <p>문서에만 적힌 규칙은 안 지켜진다. 편의를 위해 한 번씩 넘나들다 보면 어느새
 * 규칙이 클라이언트에 반쯤 들어가 있고, 그때는 되돌리는 비용이 크다.
 * 그래서 경계를 테스트로 만든다.
 */
class ArchitectureTest {

    private static final Path SOURCE = Path.of("src/main/java/com/caravan");

    /** 규칙이 사는 곳. 여기가 오염되면 클라이언트를 갈아끼울 수 없게 된다. */
    private static final List<String> DOMAIN = List.of("world", "economy", "trade", "travel", "npc", "event");

    @Test
    @DisplayName("도메인은 프레임워크를 모른다 — Jackson도 Spring도 안 들어온다")
    void 도메인에_프레임워크가_없다() {
        for (String forbidden : List.of("com.fasterxml", "org.springframework",
                "jakarta.persistence", "jakarta.servlet")) {
            assertThat(importsIn(DOMAIN))
                    .describedAs("도메인이 %s 를 import 한다", forbidden)
                    .noneMatch(i -> i.startsWith(forbidden));
        }
    }

    @Test
    @DisplayName("도메인은 자기가 JSON이 되는 걸 모른다 — 직렬화 애노테이션이 없다")
    void 도메인에_직렬화_애노테이션이_없다() {
        // @JsonProperty 하나가 붙는 순간 도메인이 전송 형식에 묶인다.
        // 6단계에서 API 를 만들 때는 DTO 를 따로 둔다.
        List<String> offenders = new ArrayList<>();
        for (Path file : sourcesIn(concat(DOMAIN, List.of("data")))) {
            String body = read(file);
            for (String annotation : List.of("@Json", "@Entity", "@Table", "@Column")) {
                if (body.contains(annotation)) {
                    offenders.add(file.getFileName() + " 에 " + annotation);
                }
            }
        }
        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("도메인은 화면을 모른다 — cli 나 web 을 import 하지 않는다")
    void 도메인이_화면을_모른다() {
        assertThat(importsIn(DOMAIN))
                .noneMatch(i -> i.startsWith("com.caravan.cli"))
                .noneMatch(i -> i.startsWith("com.caravan.web"));
    }

    @Test
    @DisplayName("밸런스 데이터는 도메인을 모른다 — 화살표는 위에서 아래로만 간다")
    void data_는_도메인을_모른다() {
        assertThat(importsIn(List.of("data")))
                .noneMatch(i -> i.startsWith("com.caravan.world"))
                .noneMatch(i -> i.startsWith("com.caravan.economy"))
                .noneMatch(i -> i.startsWith("com.caravan.trade"))
                .noneMatch(i -> i.startsWith("com.caravan.cli"));
    }

    @Test
    @DisplayName("유스케이스 계층은 화면을 모른다")
    void app_이_화면을_모른다() {
        // app 은 도메인을 조립하는 곳이지 화면을 아는 곳이 아니다.
        // 여기가 cli 를 import 하기 시작하면 콘솔 전용 코드가 서버에 눌러앉는다.
        assertThat(importsIn(List.of("app")))
                .noneMatch(i -> i.startsWith("com.caravan.cli"))
                .noneMatch(i -> i.startsWith("com.fasterxml"))
                .noneMatch(i -> i.startsWith("org.springframework"));
    }

    @Test
    @DisplayName("난수는 도메인 밖에서 굴리지 않는다")
    void 난수는_도메인_안에서만_굴린다() {
        // docs/07 4장 — 난수는 서버에서 굴리고 시드를 기록한다.
        // 콘솔이나 화면이 난수를 굴리기 시작하면 결과를 재현할 수 없게 된다.
        for (Path file : sourcesIn(List.of("cli"))) {
            String body = read(file);
            assertThat(body)
                    .describedAs("%s 가 난수를 굴린다", file.getFileName())
                    .doesNotContain("Math.random")
                    .doesNotContain("new Random");
        }
    }

    @Test
    @DisplayName("밸런스 숫자가 코드에 박혀 있지 않다 — 전부 YAML 에서 온다")
    void 밸런스_숫자는_데이터에만_있다() {
        // docs/20 5장 — 숫자를 바꾸는 데 빌드가 필요하면 안 바꾸게 된다.
        // 기준가·탄력도·생산량 같은 값이 코드에 리터럴로 나타나면 안 된다.
        for (Path file : sourcesIn(DOMAIN)) {
            String body = read(file);
            for (String number : List.of("100.0", "0.6", "2000", "720", "900")) {
                assertThat(body)
                        .describedAs("%s 에 밸런스 숫자 %s 가 박혀 있다", file.getFileName(), number)
                        .doesNotContain("= " + number);
            }
        }
    }

    // ── 도구 ──────────────────────────────────────────────

    private List<String> importsIn(List<String> packages) {
        List<String> imports = new ArrayList<>();
        for (Path file : sourcesIn(packages)) {
            read(file).lines()
                    .filter(l -> l.startsWith("import "))
                    .map(l -> l.substring("import ".length()).replace("static ", "").replace(";", "").trim())
                    .forEach(imports::add);
        }
        assertThat(imports).describedAs("소스를 하나도 못 읽었다 — 경로 확인").isNotEmpty();
        return imports;
    }

    private List<Path> sourcesIn(List<String> packages) {
        List<Path> files = new ArrayList<>();
        for (String pkg : packages) {
            Path dir = SOURCE.resolve(pkg);
            assertThat(dir).describedAs("패키지가 사라졌다: %s", pkg).exists();
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return files;
    }

    private String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }
}
