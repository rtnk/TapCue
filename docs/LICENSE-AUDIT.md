# Audyt licencji — Maestro Local (repozytorium `rtnk/TapCue`)

Wersja: **5 (STEP-04, Local-only MCP i usunięcie Viewera)**. Baseline: upstream `v2.11.0` (`c8c361be`); opisy źródeł odnoszą się do baseline.
Status audytu: **OTWARTY — Release zablokowany** (gate G-3 w `ROADMAP-0.1.md`).

> Ten dokument jest analizą techniczną wykonaną na potrzeby projektu, a nie poradą prawną.
> Pozycje oznaczone jako wymagające decyzji prawnej powinny zostać ocenione przez właściciela
> projektu (ew. z prawnikiem) przed publikacją.

## Legenda

- **Potwierdzone** — tekst licencji odczytany ze źródła (podano skąd i SHA-256 pobranego tekstu)
  lub z metadanych POM w cache Gradle.
- **Hipoteza** — wymaga sprawdzenia (np. brak pliku licencji w artefakcie, zawartość binarium).
- **Decyzja:** `OK` (redystrybucja zgodna z warunkami, wymaga tylko not), `ZAKAZ` (nie wolno
  pakować/uruchamiać), `DECYZJA` (wymaga decyzji właściciela, patrz D-x w roadmapie), `NIE DOTYCZY`
  (nie trafia do paczki).

## 1. Projekt główny

| Element | Licencja | Status | Obowiązki |
|---|---|---|---|
| Kod upstream `mobile-dev-inc/Maestro` | Apache-2.0 (`LICENSE` w repo; nagłówki „Copyright (c) 2022 mobile.dev inc.” w plikach) | Potwierdzone | zachować `LICENSE`, nagłówki copyright; oznaczyć zmienione pliki (§4(b)); dołączyć `NOTICE` forka; nie używać znaków towarowych (§6, D-1) |
| Plik `NOTICE` upstream | **nie istnieje** w `v2.11.0` | Potwierdzone | fork doda własny `NOTICE` (STEP-05) z atrybucją upstream |

## 2. Binaria i artefakty natywne w drzewie źródłowym / buildzie

| ID | Artefakt | Skąd | Licencja | Status | Decyzja |
|---|---|---|---|---|---|
| **L-01** | **simulator-server** (darwin-universal, linux-x86_64, windows-x86_64; wersja `10dae08e` w `gradle/libs.versions.toml`) | pobierany przez task `downloadSimulatorServer` (`maestro-cli/build.gradle.kts:232-306`) z `github.com/mobile-dev-inc/simulator-server-releases` i pakowany do JAR jako `deps/simulator-server/**`; uruchamiany przez Viewer (`McpViewerServer.kt:253`) | „Simulator Server Binary License”, © 2026 Software Mansion S.A. — uruchamianie i dystrybucja **wyłącznie** w oficjalnych wydaniach Maestro od mobile.dev; zakaz użycia w narzędziach budujących na Maestro, zakaz redystrybucji w utworach zależnych, zakaz RE. Tekst: `https://raw.githubusercontent.com/mobile-dev-inc/simulator-server-releases/main/LICENSE`, SHA-256 `37835d52ee41c919bb337ea39761a1559bf68f0f5cbd8c9883fb5d6fdcb754d8` (pobrany 2026-10-23) | Potwierdzone | **ZAKAZ** — **usunięte z builda (STEP-00B):** brak tasku `downloadSimulatorServer` i wersji `simulatorServer` w `libs.versions.toml`, `Dependencies.installSimulatorServer()` usunięte, `maestro mcp` nie startuje Viewera, `McpViewerServer` zamiast uruchamiać binarium zwraca kontrolowany błąd. Test `CliJarContentsTest` (zawartość zbudowanego JAR i classpath) + strażnik `scripts/fork/check-no-simulator-server.sh` w CI. Kod Viewera (`mcp/viewer/`, `maestro-cli/mcp-viewer/`) **usunięty w STEP-04** (`CliJarContentsTest`: brak klas `maestro.cli.mcp.viewer.*`); test zawartości paczki dystrybucyjnej (STEP-05/06). Binarium **nie zostało pobrane** w STEP-00 (testy uruchamiano z `-x downloadSimulatorServer`). Historia git upstream nie zawiera tych binariów (sprawdzone `git rev-list --all --objects`). |
| L-02 | `maestro-cli/src/main/resources/deps/applesimutils` (Mach-O universal x86_64+arm64, AppleSimulatorUtils 0.9.10, dodany w upstream `c1a9f5a8`) | zatwierdzony w repo, rozpakowywany do `~/.maestro/deps` (`Dependencies.kt`) | MIT, © 2017 Wix.com. Tekst: `https://raw.githubusercontent.com/wix/AppleSimulatorUtils/master/LICENSE`, SHA-256 `b9c6cf767b922ff04cc2ea2a8cdd4e3a42a61cb6d32251f7c8eb7de5a7b9108a` | Potwierdzone (licencja); Hipoteza (że binarium odpowiada niezmodyfikowanemu wydaniu 0.9.10 — do porównania SHA z oficjalnym wydaniem w STEP-05) | **OK** z notą MIT w `THIRD-PARTY-NOTICES` |
| **L-03** | `org.graalvm.truffle:truffle-enterprise:24.2.0` | przechodnio z `org.graalvm.js:js:24.2.0` (`libs.graaljs`, `maestro-client/build.gradle.kts:56`) | GraalVM Free Terms and Conditions (GFTC) — licencja własnościowa Oracle z warunkami redystrybucji (m.in. bez opłat) — nie Apache/UPL | Potwierdzone (POM) | **USUNIĘTE (STEP-03B, D-6)** — `maestro-client` deklaruje `org.graalvm.js:js-community`, `js-language` i `org.graalvm.polyglot:polyglot` 24.2.0 (UPL-1.0) zamiast `org.graalvm.js:js` (który dokłada `truffle-enterprise` i `nativebridge`); nieużywany `js-scriptengine` usunięty. Z `runtimeClasspath` CLI zniknęły `org.graalvm.truffle:truffle-enterprise` i `org.graalvm.sdk:nativebridge` (porównanie `dependencies` w handover STEP-03B). Bez kompilatora JIT GraalJS działa w trybie interpretera (`engine.WarnInterpreterOnly=false`). Test: `WebPlatformDisabledTest` (brak JAR-a `truffle-enterprise` i pakietu `com/oracle/truffle/enterprise/` na classpath) |
| **L-04** | Prekompilowane ZIP-y sterownika iOS: `maestro-ios-driver/src/main/resources/driver-iPhoneSimulator/Debug-iphonesimulator/*.zip`, `driver-iphoneos/Debug-iphoneos/*.zip` | zbudowane przez CI upstream z `maestro-ios-xctest-runner` (źródło Apache-2.0) | zawierają kod forka (Apache-2.0), FlyingFox (MIT) **oraz frameworki Apple skopiowane z Xcode**: `XCTest.framework`, `XCTestCore.framework`, `XCTestSupport.framework`, `XCTAutomationSupport.framework`, `XCUIAutomation.framework`, `XCUnit.framework`, `Testing.framework`, `libXCTestSwiftSupport.dylib`, `libswift_Concurrency.dylib` — redystrybucja podlega licencji Xcode/Apple SDK | Potwierdzone (zawartość ZIP), Hipoteza (zakres zgody Apple na redystrybucję) | **DECYZJA D-5** — rekomendacja: nie dystrybuować ZIP-ów; budować runner lokalnie z dołączonego źródła |
| L-05 | `maestro-client/src/main/resources/maestro-app.apk`, `maestro-server.apk` (+ `maestro-android-source.sha256`) | zbudowane z `maestro-android` (Apache-2.0) | kod forka + biblioteki AndroidX/uiautomator, gRPC, protobuf (Apache-2.0 / BSD-3) wkompilowane w APK | Potwierdzone (pochodzenie), Hipoteza (pełna lista bibliotek w APK — wymaga drzewa zależności `maestro-android`, niedostępnego w sesji STEP-00 z powodu blokady `dl.google.com`) | **OK** po skompletowaniu not (STEP-05) |
| L-06 | FlyingFox 0.22.0 (`swhitty/FlyingFox`, SwiftPM pin w `project.xcworkspace/xcshareddata/swiftpm/Package.resolved`) | wkompilowany w runner iOS | MIT, © 2022 Simon Whitty. SHA-256 tekstu `d108595341bd18bb218ee3bad7eb5bdeee07c7c55e89e4488392449f84f2eef0` | Potwierdzone | **OK** z notą |
| L-07 | `maestro-cli/jvm-version.jar` | zatwierdzony; kopiowany do `bin/` dystrybucji przez `startScripts` | źródło opisane w komentarzu `maestro-cli/build.gradle.kts` (klasa `JvmVersion`, część projektu → Apache-2.0) | Potwierdzone (opis), Hipoteza (zgodność binarium z opisem — dekompilacja w STEP-05) | OK |
| L-08 | `gradle/wrapper/gradle-wrapper.jar` | build | Apache-2.0 | Potwierdzone | NIE DOTYCZY paczki; walidacja `wrapper-validation` w CI |
| L-09 | `debug.keystore` | podpis APK sterownika | klucz debug (bez licencji) | Potwierdzone | NIE DOTYCZY paczki; ryzyko R-8 |
| L-10 | `maestro-cli/mcp-viewer/` (frontend React/Vite, zależności npm) | build Viewera | MIT/ISC itd. (npm) | Hipoteza | NIE DOTYCZY — **usunięty w STEP-04** razem z `.nvmrc` (taski npm usunięte w STEP-00B) |
| L-11 | Fixture'y testowe (`maestro-cli/src/test/resources/apps/test-ios.zip`, `e2e/apps/*`, `flow.zip`, `short_media.mov`) | testy | do oceny | Hipoteza | NIE DOTYCZY paczki (sprawdzić w `verify-dist.sh`) |

## 3. Zależności runtime CLI (Maven)

Pełna, wygenerowana tabela (186 artefaktów zewnętrznych): [`docs/audit/runtime-dependencies-v2.11.0.md`](audit/runtime-dependencies-v2.11.0.md).

Podsumowanie licencji wg POM: Apache-2.0 (zdecydowana większość), MIT, UPL-1.0 (GraalVM community),
BSD-2/BSD-3/FreeBSD (`jcodec`, `jline`, `HdrHistogram`), ISC (`kotlin-result`), Unicode/ICU (`icu4j` shadowed),
CC0 (`LatencyUtils`), EPL-1.0/Apache (`alpn-api` — usunięte w STEP-04), CDDL+GPLv2-CPE (`javax.annotation-api`),
LGPL-2.1/Apache-2.0 dual (`jna`, `jna-platform` — wybieramy Apache-2.0). GFTC (`truffle-enterprise`, L-03) — **usunięte
w STEP-03B**. Tabela z `v2.11.0` (186 artefaktów) zawiera jeszcze `truffle-enterprise`, `nativebridge` i Selenium — aktualna
lista powstanie w STEP-05.

Artefakty z bibliotekami natywnymi (do listy dozwolonych w `verify-dist.sh`):

| Artefakt | Natywne | Licencja |
|---|---|---|
| `org.jetbrains.skiko:skiko-awt-runtime-{macos,linux,windows}-*:0.8.18` | `libskiko-*.dylib/.so/.dll` (Skia) | Apache-2.0 (Skia: BSD-3 — dodać notę) |
| `org.fusesource.jansi:jansi:2.4.1` | `libjansi.*` | Apache-2.0 |
| `org.jline:jline-native`, `org.jline:jansi:3.30.6` | `libjlinenative.*` | BSD-3 |
| `net.java.dev.jna:jna:5.14.0` | `jnidispatch` | Apache-2.0 / LGPL-2.1 |
| `org.graalvm.truffle:truffle-api:24.2.0` | `libtruffleattach.*` | UPL-1.0 |
| ~~`org.seleniumhq.selenium:selenium-manager:4.43.0`~~ | binaria `selenium-manager` (Linux/macOS/Windows) — potrafią pobierać przeglądarki i sterowniki (N-26) | Apache-2.0 — **usunięte w STEP-03B** (D-4: platforma web wyłączona, `selenium-java`/`selenium-devtools` i moduł `maestro-web` poza grafem) |

Komponenty Viewera/simulator-server usunięte (STEP-00B: binarium i build npm; STEP-04: kod i zależności). STEP-04 zmienia
`runtimeClasspath` CLI (porównanie `./gradlew :maestro-cli:dependencies --configuration runtimeClasspath` `main` vs gałąź): znikają
`io.ktor:ktor-server-{core,cors,host-common,netty,status-pages}(-jvm)` 2.3.13, `com.typesafe:config` 1.4.3,
`org.eclipse.jetty.alpn:alpn-api` 1.1.3, `org.fusesource.jansi:jansi` 2.4.1 (kod CLI używa `org.jline:jansi`) oraz
`io.netty:netty-transport-{classes,native}-{epoll,kqueue}` 4.1.111 (biblioteki natywne — mniej pozycji do listy natywnych).
Netty dla gRPC zostaje na 4.1.111.Final (`netty-bom` w `maestro-cli`; bez tego rozwiązałoby się do 4.1.79 żądanego przez
`grpc-netty` 1.50.2); `netty-codec-socks`/`netty-handler-proxy` przechodzą z 4.1.79 na 4.1.111. Licencje bez zmian (Apache-2.0).
`com.posthog:posthog-server` i `com.posthog:posthog` **usunięte w STEP-03** (wpis `posthog` usunięty z `libs.versions.toml`
i `maestro-cli/build.gradle.kts`; zmiana `runtimeClasspath` CLI — handover STEP-03 §2). Moduł `maestro-ai` **usunięty w STEP-02**; razem z nim
z `runtimeClasspath` CLI zniknęło tylko `com.github.ajalt.clikt:clikt(-jvm):4.2.2` (używane wyłącznie przez demo `maestro-ai`;
wpis usunięty też z `libs.versions.toml`) — porównanie `./gradlew :maestro-cli:dependencies --configuration runtimeClasspath`
`main` vs gałąź STEP-02.

Wariant platformowy paczki macOS arm64: skiko ładuje bibliotekę dla bieżącej platformy; usunięcie artefaktów
`skiko-*` innych platform z paczki jest opcjonalne (rozmiar) — decyzja w STEP-05.

## 4. Lista artefaktów zakazanych / kontrolowanych w paczce (wejście do `verify-dist.sh`)

| Wzorzec | Powód | Od kroku |
|---|---|---|
| `deps/simulator-server/**`, `simulator-server*`, pliki o nazwach z `simulator-server-releases` | L-01 | 00B (JAR CLI: `CliJarContentsTest`) |
| `mcp-viewer/**` | Viewer zależny od L-01 | 00B (JAR CLI: `CliJarContentsTest`) |
| `com/posthog/**` | telemetria | 03 (CLI: `NoTelemetryTest` — klasy i classpath; JAR-y dystrybucji sprawdzone ręcznie w PR-03) |
| `maestro/ai/**`, `ai_report.css`, `tailwind.config.js` | zdalne AI | 02 (CLI: `NoRemoteAiTest` — klasy, zasoby, classpath; JAR-y dystrybucji sprawdzone ręcznie w PR-02) |
| `truffle-enterprise*.jar`, `com/oracle/truffle/enterprise/**` (wzorzec do potwierdzenia) | L-03 (D-6: artefakty community) | 03B (CLI: `WebPlatformDisabledTest` — classpath); paczka: 05 |
| `driver-iPhoneSimulator/**/*.zip`, `driver-iphoneos/**/*.zip` | L-04 (do decyzji D-5) | 05 |
| `selenium-manager*`, `org/openqa/**`, `maestro/web/**` | N-26 (D-4: platforma web wyłączona) | 03B (CLI: `WebPlatformDisabledTest` — klasy i classpath) |

## 5. Otwarte zadania audytu

1. STEP-05: wygenerować `THIRD-PARTY-NOTICES.md` z pełną listą z §3 (z tekstami licencji MIT/BSD/ISC/UPL
   i notami Apache NOTICE, jeśli artefakt je zawiera w `META-INF/NOTICE*`).
2. STEP-05: drzewo zależności `maestro-android` (APK) i noty dla L-05.
3. STEP-05: porównać SHA-256 `applesimutils` z oficjalnym wydaniem AppleSimulatorUtils 0.9.10.
4. D-5: implementacja z testami regresji (STEP-05). D-6 zrealizowana w STEP-03B.
5. STEP-06: automatyczny skan licencji w CI (porównanie z listą dozwolonych licencji; nowa licencja → fail).
