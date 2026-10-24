# Audyt licencji — TapCue (repozytorium `rtnk/TapCue`)

Wersja: **8 (STEP-07, pipeline wydania: `verify-dist.sh` i skan licencji archiwum wydania)**. Baseline: upstream `v2.11.0` (`c8c361be`); opisy źródeł odnoszą się do baseline.
Status audytu: **OTWARTY — Release zablokowany** (gate G-3 w `ROADMAP-0.1.md`). Po STEP-06 otwarte zostają wyłącznie
decyzje właściciela (§5): przegląd `THIRD-PARTY-NOTICES.md`/suplementu, ocena `play-services-location` w APK sterownika
(L-05) i akceptacja polityki listy dozwolonych licencji (§3, `scripts/fork/allowed-licenses.tsv`). Pełna lista bibliotek APK
sterownika (L-05) i automatyczny skan licencji działają w CI od STEP-06.

Od STEP-05 paczka `tapcue-<wersja>-macos-arm64.zip` zawiera `LICENSE`, `NOTICE` (atrybucja upstream, dopisek
„based on Maestro (Apache-2.0), not affiliated with mobile.dev”), `THIRD-PARTY-NOTICES.md` (generowany przez
`:maestro-cli:generateThirdPartyNotices`: licencja z POM — z dziedziczeniem z POM nadrzędnego — dla każdego JAR-a w `lib/`,
wszystkie teksty licencji i pliki NOTICE zawarte w tych JAR-ach, Apache-2.0 §4(d), oraz ręczny suplement
[`audit/notices-supplement.md`](audit/notices-supplement.md)) i `README.md`. Task kończy build błędem, jeśli jakaś zależność
nie deklaruje licencji (i nie ma wpisu w `thirdPartyLicenseOverrides`). `scripts/verify-dist.sh` sprawdza archiwum (§4).
Od STEP-06 job CI `build` (`.github/workflows/ci.yml`) porównuje każdą licencję z tabeli `THIRD-PARTY-NOTICES.md` w paczce
z listą dozwolonych (`scripts/fork/check-licenses.py`, §3) — nowa licencja kończy CI błędem. Od STEP-07 te same kontrole
(`verify-dist.sh --smoke`, `check-licenses.py`, `check-forbidden-deps.sh`, strażnik simulator-server) przechodzi archiwum
wydania w jobie `build` workflowu `.github/workflows/release.yml` przed publikacją (gate G-4); Release zawiera wyłącznie to
archiwum i `SHA256SUMS`.

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
| Plik `NOTICE` upstream | **nie istnieje** w `v2.11.0` | Potwierdzone | **zrealizowane (STEP-05):** `NOTICE` forka w katalogu głównym i w paczce — atrybucja upstream (repozytorium, tag, copyright mobile.dev, Apache-2.0), dopisek o braku powiązania z mobile.dev, wskazanie `THIRD-PARTY-NOTICES.md` |

## 2. Binaria i artefakty natywne w drzewie źródłowym / buildzie

| ID | Artefakt | Skąd | Licencja | Status | Decyzja |
|---|---|---|---|---|---|
| **L-01** | **simulator-server** (darwin-universal, linux-x86_64, windows-x86_64; wersja `10dae08e` w `gradle/libs.versions.toml`) | pobierany przez task `downloadSimulatorServer` (`maestro-cli/build.gradle.kts:232-306`) z `github.com/mobile-dev-inc/simulator-server-releases` i pakowany do JAR jako `deps/simulator-server/**`; uruchamiany przez Viewer (`McpViewerServer.kt:253`) | „Simulator Server Binary License”, © 2026 Software Mansion S.A. — uruchamianie i dystrybucja **wyłącznie** w oficjalnych wydaniach Maestro od mobile.dev; zakaz użycia w narzędziach budujących na Maestro, zakaz redystrybucji w utworach zależnych, zakaz RE. Tekst: `https://raw.githubusercontent.com/mobile-dev-inc/simulator-server-releases/main/LICENSE`, SHA-256 `37835d52ee41c919bb337ea39761a1559bf68f0f5cbd8c9883fb5d6fdcb754d8` (pobrany 2026-10-23) | Potwierdzone | **ZAKAZ** — **usunięte z builda (STEP-00B):** brak tasku `downloadSimulatorServer` i wersji `simulatorServer` w `libs.versions.toml`, `Dependencies.installSimulatorServer()` usunięte, `maestro mcp` nie startuje Viewera, `McpViewerServer` zamiast uruchamiać binarium zwraca kontrolowany błąd. Test `CliJarContentsTest` (zawartość zbudowanego JAR i classpath) + strażnik `scripts/fork/check-no-simulator-server.sh` w CI. Kod Viewera (`mcp/viewer/`, `maestro-cli/mcp-viewer/`) **usunięty w STEP-04** (`CliJarContentsTest`: brak klas `maestro.cli.mcp.viewer.*`); test zawartości paczki dystrybucyjnej (STEP-05/06). Binarium **nie zostało pobrane** w STEP-00 (testy uruchamiano z `-x downloadSimulatorServer`). Historia git upstream nie zawiera tych binariów (sprawdzone `git rev-list --all --objects`). |
| L-02 | `maestro-cli/src/main/resources/deps/applesimutils` (Mach-O universal x86_64+arm64, AppleSimulatorUtils 0.9.10, dodany w upstream `c1a9f5a8`) | zatwierdzony w repo, rozpakowywany do `~/.maestro/deps` (`Dependencies.kt`) | MIT, © 2017 Wix.com. Tekst: `https://raw.githubusercontent.com/wix/AppleSimulatorUtils/master/LICENSE`, SHA-256 `b9c6cf767b922ff04cc2ea2a8cdd4e3a42a61cb6d32251f7c8eb7de5a7b9108a` | Potwierdzone (licencja). **STEP-05:** wydanie 0.9.10 (`github.com/wix/AppleSimulatorUtils/releases/download/0.9.10/AppleSimulatorUtils-0.9.10.tar.gz`, SHA-256 `0c63ecae…98dd9`) to **wyłącznie źródła** (instalacja przez Homebrew = kompilacja) — oficjalnego binarium do porównania nie ma. Binarium w repo: SHA-256 `a60cccbd98c285320f848e90d9e4daecbd93670dd8a08aee30f8173e298bcfa3`, zawiera symbole FMDB (`FMDatabase`, 328 wystąpień w `strings`) — zgodne ze źródłem (ObjCCLIInfra, DTXLoggingInfra: MIT © 2017 Wix.com; FMDB: MIT © 2008-2014 Flying Meat Inc.). Pochodzenie binarium: Hipoteza (zbudowane przez upstream z tego źródła) | **OK** — noty MIT (AppleSimulatorUtils, FMDB) w `THIRD-PARTY-NOTICES.md` §3.1; binarium na liście dozwolonych `verify-dist.sh` |
| **L-03** | `org.graalvm.truffle:truffle-enterprise:24.2.0` | przechodnio z `org.graalvm.js:js:24.2.0` (`libs.graaljs`, `maestro-client/build.gradle.kts:56`) | GraalVM Free Terms and Conditions (GFTC) — licencja własnościowa Oracle z warunkami redystrybucji (m.in. bez opłat) — nie Apache/UPL | Potwierdzone (POM) | **USUNIĘTE (STEP-03B, D-6)** — `maestro-client` deklaruje `org.graalvm.js:js-community`, `js-language` i `org.graalvm.polyglot:polyglot` 24.2.0 (UPL-1.0) zamiast `org.graalvm.js:js` (który dokłada `truffle-enterprise` i `nativebridge`); nieużywany `js-scriptengine` usunięty. Z `runtimeClasspath` CLI zniknęły `org.graalvm.truffle:truffle-enterprise` i `org.graalvm.sdk:nativebridge` (porównanie `dependencies` w handover STEP-03B). Bez kompilatora JIT GraalJS działa w trybie interpretera (`engine.WarnInterpreterOnly=false`). Test: `WebPlatformDisabledTest` (brak JAR-a `truffle-enterprise` i pakietu `com/oracle/truffle/enterprise/` na classpath) |
| **L-04** | Prekompilowane ZIP-y sterownika iOS: `maestro-ios-driver/src/main/resources/driver-iPhoneSimulator/Debug-iphonesimulator/*.zip`, `driver-iphoneos/Debug-iphoneos/*.zip` | zbudowane przez CI upstream z `maestro-ios-xctest-runner` (źródło Apache-2.0) | zawierają kod forka (Apache-2.0), FlyingFox (MIT) **oraz frameworki Apple skopiowane z Xcode**: `XCTest.framework`, `XCTestCore.framework`, `XCTestSupport.framework`, `XCTAutomationSupport.framework`, `XCUIAutomation.framework`, `XCUnit.framework`, `Testing.framework`, `libXCTestSwiftSupport.dylib`, `libswift_Concurrency.dylib` — redystrybucja podlega licencji Xcode/Apple SDK | Potwierdzone (zawartość ZIP), Hipoteza (zakres zgody Apple na redystrybucję) | **USUNIĘTE (STEP-05, D-5)** — ZIP-y i katalogi `driver-iPhoneSimulator/`, `driver-iphoneos/` usunięte z `maestro-ios-driver/src/main/resources` (razem z taskiem `buildIosDriver` i skryptami `build-maestro-ios-runner*.sh`, które je generowały). Runner dla symulatora budowany przy pierwszym użyciu z kodu w JAR CLI (`driver/ios`, teraz z `MaestroDriverLib/` i dołączonym FlyingFox w `Vendor/`) lokalnym Xcode, bez sieci (`IOSSimulatorDriver` → `~/.tapcue/maestro-iphonesimulator-driver-build`); urządzenia fizyczne — jak dotąd (`driver-setup --apple-team-id`). Testy: `CliJarContentsTest`, `IOSBuildProductsExtractorTest`, `IOSSimulatorDriverTest`, `DriverBuilderTest`; `verify-dist.sh` (zakaz `driver-*/`, `*.xctestrun`, `.app/`, `.framework/`); job CI `macos-dist` buduje runner ze źródła z rozpakowanej paczki. Historia git nadal zawiera ZIP-y (ROADMAP §2, uwaga do D-9) |
| L-05 | `maestro-client/src/main/resources/maestro-app.apk`, `maestro-server.apk` (+ `maestro-android-source.sha256`) | zbudowane z `maestro-android` (Apache-2.0) | kod forka + biblioteki AndroidX/uiautomator, gRPC, protobuf (Apache-2.0 / BSD-3) wkompilowane w APK | Potwierdzone (pochodzenie, zależności bezpośrednie z `maestro-android/build.gradle.kts`), Hipoteza (pełna lista przechodnia — `./gradlew :maestro-android:dependencies` wymaga Google Maven, zablokowanego w sesjach agenta) | **OK** — STEP-05: nota w `THIRD-PARTY-NOTICES.md` §3.2 (gRPC, protobuf-lite BSD-3, Ktor, Gson, commons-lang3, HiddenApiBypass, AndroidX Test/UI Automator, kotlin-retry ISC). Uwaga: `play-services-location` jest na licencji „Android Software Development Kit License” (własnościowa licencja Google zezwalająca na użycie w aplikacjach) — **do przeglądu właściciela**. **STEP-06: pełna lista przechodnia z CI** (`debugRuntimeClasspath` → `maestro-app.apk`,
`debugAndroidTestRuntimeClasspath` → `maestro-server.apk`; `scripts/fork/android-driver-deps.py`, licencje z POM): 102 artefakty —
Apache-2.0 (AndroidX, AndroidX Test, gRPC, Ktor, Gson, Guava, Kotlin, kotlinx-coroutines i in.), BSD-3-Clause (protobuf-lite,
Hamcrest 1.3), MIT (checker-qual, animal-sniffer-annotations, slf4j-api), ISC (kotlin-retry), **EPL-1.0 (JUnit 4.13.2, tylko APK
instrumentacji)**, CDDL/GPLv2-CPE (`javax.annotation-api`), **Android SDK License (Google, własnościowa): `play-services-location`
21.3.0, `play-services-base` 18.5.0, `play-services-basement` 18.4.0, `play-services-tasks` 18.2.0**. Lista w
`THIRD-PARTY-NOTICES.md` §3.2 (suplement). Teksty licencji EPL-1.0/CDDL/BSD bibliotek APK nie są jeszcze powielone w suplemencie.
**Ocena Google Play services i EPL-1.0 w APK — właściciel (otwarte)** |
| L-06 | FlyingFox 0.22.0 (`swhitty/FlyingFox`; w baseline SwiftPM pin w `project.xcworkspace/xcshareddata/swiftpm/Package.resolved`, od STEP-05 `maestro-ios-xctest-runner/Vendor/FlyingFox/`) | wkompilowany w runner iOS | MIT, © 2022 Simon Whitty. SHA-256 tekstu `d108595341bd18bb218ee3bad7eb5bdeee07c7c55e89e4488392449f84f2eef0` | Potwierdzone | **OK z notą (STEP-05; dołączenie zaakceptowane przez właściciela 2026-10-24)** — źródła FlyingFox 0.22.0 (rewizja `3ad076e081749cef043e25ac01719a503b772113`, ta sama co w usuniętym `Package.resolved`; SHA-256 `LICENSE` jak wyżej) dołączone bez zmian w `maestro-ios-xctest-runner/Vendor/FlyingFox/` (`XCLocalSwiftPackageReference` zamiast pakietu zdalnego), więc trafiają do JAR CLI (`driver/ios/Vendor/`) jako źródło. Dzięki temu build sterownika przy pierwszym użyciu nie pobiera niczego (N-36). Tekst MIT w `THIRD-PARTY-NOTICES.md` §3.3; `CSystemLinux/` (tylko Linux) — Apache-2.0 z Runtime Library Exception |
| L-07 | `maestro-cli/jvm-version.jar` | zatwierdzony; kopiowany do `bin/` dystrybucji przez `startScripts` | źródło opisane w komentarzu `maestro-cli/build.gradle.kts` (klasa `JvmVersion`, część projektu → Apache-2.0) | Potwierdzone (opis); **STEP-05:** `jvm-version.jar` zawiera wyłącznie `META-INF/MANIFEST.MF` (`Main-Class: JvmVersion`) i `JvmVersion.class` (`javap -c`: `System.getProperty("java.version")`, `StringTokenizer`, `System.out.println`, `System.exit` — zgodne z opisem, bez sieci i plików) | OK — `THIRD-PARTY-NOTICES.md` §3.4 |
| L-08 | `gradle/wrapper/gradle-wrapper.jar` | build | Apache-2.0 | Potwierdzone | NIE DOTYCZY paczki; walidacja `wrapper-validation` w CI |
| L-09 | `debug.keystore` | podpis APK sterownika | klucz debug (bez licencji) | Potwierdzone | NIE DOTYCZY paczki; ryzyko R-8 |
| L-10 | `maestro-cli/mcp-viewer/` (frontend React/Vite, zależności npm) | build Viewera | MIT/ISC itd. (npm) | Hipoteza | NIE DOTYCZY — **usunięty w STEP-04** razem z `.nvmrc` (taski npm usunięte w STEP-00B) |
| L-11 | Fixture'y testowe (`maestro-cli/src/test/resources/apps/test-ios.zip`, `e2e/apps/*`, `flow.zip`, `short_media.mov`) | testy | do oceny | Hipoteza | NIE DOTYCZY paczki — `verify-dist.sh` odrzuca każde zagnieżdżone archiwum i binarium spoza listy dozwolonych (STEP-05: w paczce są tylko APK sterownika i `publicsuffixes.gz` OkHttp) |
| L-12 | `okhttp3/internal/publicsuffix/publicsuffixes.gz` w `okhttp-4.12.0.jar` | dane Public Suffix List | MPL-2.0 (dane), OkHttp Apache-2.0 | Potwierdzone (pochodzenie) | OK — plik niezmieniony w JAR-ze (MPL-2.0 dopuszcza redystrybucję w postaci niezmienionej); na liście dozwolonych `verify-dist.sh` |

## 3. Zależności runtime CLI (Maven)

Pełna, wygenerowana tabela baseline (186 artefaktów zewnętrznych): [`docs/audit/runtime-dependencies-v2.11.0.md`](audit/runtime-dependencies-v2.11.0.md).
**Stan po STEP-05 (134 artefakty zewnętrzne + 7 modułów TapCue):** [`docs/audit/runtime-dependencies-step05.md`](audit/runtime-dependencies-step05.md)
— tabela z `THIRD-PARTY-NOTICES.md` (wygenerowana z `runtimeClasspath` `:maestro-cli`). Każdy artefakt ma licencję w POM
(żaden nie potrzebował wpisu w `thirdPartyLicenseOverrides`). Licencje: Apache-2.0 (większość), UPL-1.0 (GraalVM: 11, w tym
`truffle-api` UPL/MIT), MIT (MCP Kotlin SDK, colormath, slf4j, i in.), BSD-2/BSD-3/FreeBSD (`apk-parser`, `jline`/`jansi`,
`jcodec`, `HdrHistogram`), ISC (`kotlin-result`), Unicode/ICU (`icu4j`), CC0 (`LatencyUtils`), CDDL+GPLv2-CPE
(`javax.annotation-api`), LGPL-2.1/Apache-2.0 (`jna`, `jna-platform` — wybieramy Apache-2.0). Bez GFTC/własnościowych w `lib/`.

**Skan licencji (STEP-06).** `scripts/fork/check-licenses.py <archiwum>` czyta tabelę §1 `THIRD-PARTY-NOTICES.md` z paczki
i porównuje każdą zadeklarowaną licencję (dokładny napis z POM: „nazwa (URL)”) z `scripts/fork/allowed-licenses.tsv`
(36 wpisów: napis → identyfikator SPDX i polityka). Polityka: `allowed` — może być jedyną licencją biblioteki (Apache-2.0, MIT,
BSD-2/3-Clause, BSD-2-Clause-FreeBSD, ISC, UPL-1.0, CC0-1.0, Unicode-3.0, CDDL-1.1 OR GPL-2.0-only WITH Classpath-exception-2.0);
`alternative-only` — tylko jako jedna z licencji do wyboru obok dozwolonej (LGPL-2.1 przy JNA: TapCue korzysta z Apache-2.0).
Napis spoza listy (nowa licencja albo znana licencja zapisana inaczej w POM nowej wersji) kończy CI błędem do czasu przeglądu
i dopisania wpisu. Wynik dla paczki STEP-06 (134 biblioteki): Apache-2.0 101, UPL-1.0 10, MIT 9, BSD-3-Clause 4,
BSD-2-Clause 2, BSD-2-Clause-FreeBSD 2, CC0-1.0 1, BSD-2-Clause/CC0-1.0 1, MIT/UPL-1.0 1, ISC 1, Unicode-3.0 1,
CDDL-1.1/GPL-2.0-CPE 1 — wszystkie na liście. Testy negatywne (`scripts/fork/test-ci-checks.sh`): nowa licencja (GFTC),
znana licencja zapisana inaczej, biblioteka tylko z licencją `alternative-only`, pusta tabela, niezgodna liczba wierszy —
każde odrzucone. Polityka jest decyzją techniczną do akceptacji właściciela (§5).

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

Wariant platformowy paczki macOS arm64: skiko ładuje bibliotekę dla bieżącej platformy. **Decyzja STEP-05:** paczka
`macos-arm64` pomija `skiko-awt-runtime-{linux,windows,macos-x64}-*` i `bin/tapcue.bat` (136 JAR-ów zamiast 141, ~106 MB
zamiast ~167 MB); ogólna `distZip` (`tapcue-<wersja>.zip`, wszystkie platformy) zostaje do lokalnych buildów.
`THIRD-PARTY-NOTICES.md` wymienia cały `runtimeClasspath` (nadzbiór zawartości paczki macOS).

Biblioteki natywne i inne binaria w paczce `macos-arm64` (STEP-05, lista dozwolonych w `scripts/verify-dist.sh` — każde inne
binarium/archiwum zagnieżdżone kończy weryfikację błędem):

| JAR | Binaria | Licencja |
|---|---|---|
| `jansi-3.30.6.jar`, `jline-native-3.30.6.jar` | `libjlinenative.{so,jnilib,dll}` (15 platform) | BSD-3-Clause |
| `jna-5.14.0.jar` | `jnidispatch` (26 platform) | Apache-2.0 / LGPL-2.1 |
| `skiko-awt-runtime-macos-arm64-0.8.18.jar` | `libskiko-macos-{arm64,x64}.dylib` | Apache-2.0 (Skia: BSD-3) |
| `truffle-api-24.2.0.jar` | `libtruffleattach` (5 platform) | UPL-1.0 |
| `maestro-cli-<wersja>.jar` | `deps/applesimutils` (Mach-O universal) | MIT (L-02) |
| `maestro-client.jar` | `maestro-app.apk`, `maestro-server.apk` | Apache-2.0 (L-05) |
| `okhttp-4.12.0.jar` | `publicsuffixes.gz` | MPL-2.0 dane (L-12) |

## 4. Lista artefaktów zakazanych / kontrolowanych w paczce (`scripts/verify-dist.sh`, od STEP-05)

`verify-dist.sh` sprawdza ścieżki w archiwum i w każdym JAR-ze z `lib/` (wzorce poniżej), binaria natywne (lista dozwolonych
w §3), kompletność `THIRD-PARTY-NOTICES.md` (każdy JAR z `lib/`), `NOTICE`/`LICENSE`/`README.md`, start-scripty (bez linków
producenta, sprawdzenie Javy 17) i `SHA256SUMS`; z `--smoke` uruchamia `bin/tapcue --version` z rozpakowanego archiwum.
Uruchamiany w CI (`.github/workflows/ci.yml`: job `build` i job `macos`; do STEP-05 `ci-baseline.yml`) i w pipeline wydania
(`.github/workflows/release.yml`, job `build` na `macos-26` arm64 — przed publikacją, od STEP-07).

| Wzorzec | Powód | Od kroku |
|---|---|---|
| `deps/simulator-server/**`, `simulator-server*`, pliki o nazwach z `simulator-server-releases` | L-01 | 00B (JAR CLI: `CliJarContentsTest`) |
| `mcp-viewer/**` | Viewer zależny od L-01 | 00B (JAR CLI: `CliJarContentsTest`) |
| `com/posthog/**` | telemetria | 03 (CLI: `NoTelemetryTest` — klasy i classpath; JAR-y dystrybucji sprawdzone ręcznie w PR-03) |
| `maestro/ai/**`, `ai_report.css`, `tailwind.config.js` | zdalne AI | 02 (CLI: `NoRemoteAiTest` — klasy, zasoby, classpath; JAR-y dystrybucji sprawdzone ręcznie w PR-02) |
| `truffle-enterprise*`, `com/oracle/truffle/enterprise/**` | L-03 (D-6: artefakty community) | 03B (CLI: `WebPlatformDisabledTest` — classpath); paczka: 05 (`verify-dist.sh`) |
| `driver-iPhoneSimulator/**`, `driver-iphoneos/**`, `maestro-driver-ios*.zip`, `*.xctestrun`, `*.app/**`, `*.framework/**`, `*.xctest/**` | L-04 (D-5: bez prekompilowanego sterownika) | 05 (`verify-dist.sh`, `CliJarContentsTest`, `IOSBuildProductsExtractorTest`) |
| `selenium-manager*`, `org/openqa/**`, `maestro/web/**` | N-26 (D-4: platforma web wyłączona) | 03B (CLI: `WebPlatformDisabledTest` — klasy i classpath) |

## 5. Otwarte zadania audytu

1. ~~STEP-05: `THIRD-PARTY-NOTICES.md`~~ — **zrealizowane** (generowany przy buildzie; teksty z JAR-ów + suplement).
   Zostaje: artefakty, których JAR nie zawiera tekstu licencji (np. część MIT/BSD), mają w tabeli nazwę i URL licencji
   z POM, ale nie pełny tekst — przegląd właściciela przed STEP-08 (ewentualnie dopisanie tekstów do suplementu).
2. STEP-05: drzewo zależności `maestro-android` (APK) i noty dla L-05 — **pełna lista przechodnia w STEP-06** (job CI `build`,
   `scripts/fork/android-driver-deps.py`, §3.2 suplementu); **ocena `play-services-location` — właściciel** (otwarte).
3. ~~STEP-05: SHA-256 `applesimutils` vs wydanie 0.9.10~~ — oficjalne wydanie to tylko źródła; porównanie niemożliwe (L-02).
4. ~~D-5: implementacja z testami regresji~~ — **zrealizowane w STEP-05**. D-6 zrealizowana w STEP-03B.
5. ~~STEP-06: automatyczny skan licencji w CI~~ — **zrealizowane** (`scripts/fork/check-licenses.py`, §3). Do akceptacji
   właściciela: polityka listy dozwolonych (w szczególności CDDL+GPLv2-CPE `javax.annotation-api` jako `allowed` i LGPL-2.1
   jako `alternative-only`).
