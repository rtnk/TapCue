# Handover — STEP-05: Dystrybucja lokalna macOS arm64 i audyt licencji

| Pole | Wartość |
|---|---|
| Krok | STEP-05 — w review |
| PR | PR-05: https://github.com/rtnk/TapCue/pull/13 |
| Gałąź | `step-05-local-dist` |
| Gałąź docelowa | `main` |
| Baza | `main` @ `ad15449` (merge PR #12 — STEP-04B) |
| Commit SHA | ostatni commit gałęzi `step-05-local-dist` (patrz PR) |

## 1. Co zrobiono

Cel (D-1, D-5, D-7, gate'y G-3/G-4): paczka `tapcue-<wersja>-macos-arm64.zip`, kompletna licencyjnie, bez zakazanych
binariów, działająca offline; proces dystrybucji i publikacji upstream usunięty.

| Obszar | Zmiana |
|---|---|
| Publikacja upstream (B-04, B-05, B-09) | Usunięte: `jreleaser` (wtyczka, blok konfiguracji z brew tap i release do `mobile-dev-inc/maestro`, szablon `maestro-cli/src/jreleaser/`, wpis w `libs.versions.toml`), `scripts/install.sh`, publikacja Maven Central we **wszystkich** modułach i katalogu głównym (`com.vanniktech.maven.publish`, `maven-publish`, bloki `mavenPublishing`, `*/gradle.properties` z `POM_*`, metadane `POM_*`/`SONATYPE_*`/`GROUP`/`VERSION_NAME` w `gradle.properties`). `maestro-cli` ma teraz jawne `version = CLI_VERSION` (wcześniej ustawiała je wtyczka; nazwa `maestro-cli-2.11.0.jar` bez zmian) |
| Powtarzalność | Wszystkie JAR-y i archiwa wszystkich modułów bez znaczników czasu, z ustaloną kolejnością (`allprojects` w głównym `build.gradle.kts`); `createProperties` pisze `version.properties` bez komentarza z datą |
| Archiwum (D-1) | `distributions`: `main` (`tapcue-<wersja>.zip/.tar`, wszystkie platformy — do lokalnych buildów) i `macosArm64` (`tapcue-<wersja>-macos-arm64.zip`, katalog `tapcue-<wersja>-macos-arm64/`; bez `skiko-awt-runtime-{linux,windows,macos-x64}` i `tapcue.bat`: 136 JAR-ów, ~106 MB zamiast 141/~167 MB). Zawartość: `bin/tapcue`, `bin/jvm-version.jar`, `lib/*.jar`, `LICENSE`, `NOTICE`, `THIRD-PARTY-NOTICES.md`, `README.md`. Archiwa bez znaczników czasu, z ustaloną kolejnością plików. `macosArm64DistSha256` (finalizer `macosArm64DistZip`) zapisuje `build/distributions/SHA256SUMS` (format `shasum -a 256 -c`) |
| `NOTICE` | nowy plik w katalogu głównym (i w paczce): TapCue, „based on Maestro (Apache-2.0), not affiliated with mobile.dev”, atrybucja upstream (repozytorium, tag `v2.11.0`, copyright mobile.dev, brak `NOTICE` upstream), zakres zmian forka, znaki towarowe, wskazanie `THIRD-PARTY-NOTICES.md` |
| `THIRD-PARTY-NOTICES.md` | task `:maestro-cli:generateThirdPartyNotices` (klasa `ThirdPartyNoticesTask` w `maestro-cli/build.gradle.kts`): (1) tabela — każdy zewnętrzny JAR z `runtimeClasspath` (134), plik, licencja z POM (z dziedziczeniem z POM nadrzędnych); moduły TapCue (7 + `maestro-cli`); (2) wszystkie teksty licencji/NOTICE zawarte w tych JAR-ach (31 unikalnych, Apache-2.0 §4(d)); (3) suplement `docs/audit/notices-supplement.md`: `applesimutils` (MIT Wix + FMDB), APK sterownika Android (L-05), źródło runnera iOS z FlyingFox (MIT), `jvm-version.jar`, oraz teksty licencji 12 pozycji MIT/BSD/UPL/Unicode, których JAR-y ich nie zawierają (pobrane z repozytoriów źródłowych w wydanej wersji — tabela z commitami w §3.5). Zależność bez licencji w POM → build kończy się błędem (mapa `thirdPartyLicenseOverrides`, obecnie pusta). Migawka tabeli: `docs/audit/runtime-dependencies-step05.md` |
| README instalacji (D-7) | `maestro-cli/src/dist/README.md` → `README.md` w paczce: wymagana Java 17+ (brak JRE w paczce; launcher to sprawdza), Xcode dla symulatora (sterownik budowany przy pierwszym użyciu), instalacja z `SHA256SUMS`, kwarantanna macOS, `~/.tapcue`, odinstalowanie |
| Start-scripty i linki (N-35) | `bin/tapcue`/`tapcue.bat`: komunikat o Javie 17 bez linku `maestro.dev/blog/...`. `PickDeviceInteractor` (WSL): bez linku do instalacji na Windows. `SkiaFrameRenderer`: stopka nagrań „TapCue” zamiast `maestro.mobile.dev`. `ChatCommand`: zamiast `docs.maestro.dev` — `tapcue mcp`. `BugReportCommand`: `https://github.com/rtnk/TapCue/issues`. `WorkspaceExecutionPlanner`: zamiast `maestro.mobile.dev`/`blog.mobile.dev` — przykład `flows:` w `config.yaml`. `MaestroFlowParser`: stałe `DOCS_FIRST_FLOW`/`DOCS_COMMANDS` usunięte (komunikaty mają przykłady inline; mechanizm `docs` w `ParserErrorRenderer` zostaje). Fixture'y `e002`, `e006`, `e008`–`e011`, `e015`, `e016`, `e018`, `e023` zaktualizowane |
| iOS — D-5 | Usunięte: `maestro-ios-driver/src/main/resources/driver-iPhoneSimulator/` i `driver-iphoneos/` (ZIP-y z frameworkami Apple i `.xctestrun`), task `buildIosDriver` (na Macu z Xcode przebudowywał je w każdym `processResources`), skrypty `build-maestro-ios-runner.sh`/`-all.sh`. Nowy `maestro.cli.driver.IOSSimulatorDriver`: przy pierwszym użyciu symulatora (`MaestroSessionManager`, MCP `McpMaestroSessionManager`) buduje runner przez `DriverBuilder` — `xcodebuild clean build-for-testing`, `generic/platform=iOS Simulator`, architektura hosta, **bez** `DEVELOPMENT_TEAM`/`CODE_SIGN_IDENTITY`/`-allowProvisioningUpdates` — do `~/.tapcue/maestro-iphonesimulator-driver-build/driver-iPhoneSimulator/Build/Products`; `version.properties` zapisuje wersję CLI i odcisk SHA-256 źródła `driver/ios` (tylko po udanym buildzie); przebudowa, gdy różni się wersja, odcisk albo brakuje `.xctestrun`/runnera; blokada `synchronized` + `FileLock` (shardy, równoległe procesy); limit 900 s (`MAESTRO_XCODEBUILD_WAIT_TIME` nadpisuje). `tapcue driver-setup --simulator` (ukryte) wymusza build. `DriverBuilder`: log `xcodebuild` zostaje w katalogu buildu także po sukcesie (`output.log`); `teamId` opcjonalny, katalog/opis/limit z konfiguracji, brak `xcodebuild` → `IOSDeviceDriverSetupException` z instrukcją zamiast `IOException`, przekroczony limit → kontrolowany błąd zamiast `IllegalThreadStateException` z `exitValue()`. `IOSBuildProductsExtractor`: bezwzględna ścieżka produktów działa też dla symulatora |
| Źródło sterownika w JAR | `driver/ios` zawiera teraz także `MaestroDriverLib/` (framework, od którego zależy schemat — brakowało go, więc build ze źródła nie mógł się udać także dla urządzeń fizycznych), `maestro-driver-iosTests/` i `Vendor/` |
| FlyingFox (N-36, L-06) | Projekt Xcode pobierał pakiet Swift FlyingFox z GitHuba przy każdym buildzie runnera — przy D-5 oznaczałoby to niejawne pobieranie komponentu przez narzędzie wywołane przez CLI. Źródła FlyingFox 0.22.0 (rewizja `3ad076e0…` z dotychczasowego `Package.resolved`, bez zmian, MIT) w `maestro-ios-xctest-runner/Vendor/FlyingFox/` (+ `Vendor/README.md`); `project.pbxproj`: `XCLocalSwiftPackageReference` (`relativePath = Vendor/FlyingFox`) zamiast `XCRemoteSwiftPackageReference`, `objectVersion` 55 → 60; `Package.resolved` usunięty. Kod Swift runnera bez zmian |
| `scripts/verify-dist.sh` | Sprawdza archiwum: układ (jeden katalog główny, wymagane pliki, nic poza `bin/`, `lib/` i 4 plikami), `NOTICE` z dopiskiem, `LICENSE` Apache, `README.md` z Javą 17; ścieżki zakazane w archiwum i w każdym JAR-ze (simulator-server, Viewer, `truffle-enterprise`, sterownik iOS i bundle Apple `.app/`/`.framework/`/`.xctest/`/`.xctestrun`, PostHog, AI, Selenium/web); każde binarium (ELF/Mach-O/PE/biblioteka/zagnieżdżone archiwum) musi być na liście dozwolonych z licencją (jline/jansi, JNA, Skiko, Truffle, `applesimutils`, APK sterownika, `publicsuffixes.gz`); każdy JAR z `lib/` wymieniony w `THIRD-PARTY-NOTICES.md`; start-scripty bez `maestro.dev`/`mobile.dev`/`mobile-dev-inc` i ze sprawdzeniem Javy 17; `bin/tapcue` wykonywalny; `SHA256SUMS`. `--smoke`: rozpakowanie (`unzip`) i `bin/tapcue --version` z pustym `HOME`, bez `JAVA_TOOL_OPTIONS` |
| Testy negatywne | `scripts/fork/test-verify-dist.sh <zip>`: 13 zmutowanych kopii (simulator-server, Viewer, ZIP iOS, framework Apple, `truffle-enterprise`, PostHog, AI, nieznana biblioteka natywna, link w start-scripcie, brak sprawdzenia Javy, brak `NOTICE`, niewymieniony JAR, zła suma) — każda musi zostać odrzucona z właściwym komunikatem |
| CI (`ci-baseline.yml`) | Job Linux: po testach `macosArm64DistZip`, `verify-dist.sh --smoke`, `test-verify-dist.sh`, artefakt `tapcue-macos-arm64` (archiwum + `SHA256SUMS`, 7 dni). Nowy job `macos-dist` (`macos-26`, asercja `uname -m` = arm64): build i weryfikacja archiwum, `shasum -c`, instalacja z rozpakowanego archiwum; **bez sieci** (`sandbox-exec` odrzuca ruch wychodzący poza loopback i gniazdo resolvera DNS; kontrola, że `curl` do internetu pada): `--version`, `--help`, `check-syntax`, `initialize` MCP; `driver-setup --simulator` (build runnera ze źródła z paczki) — poza sandboxem, bo SwiftPM i serwer makr Swift same używają `sandbox-exec`, którego nie da się zagnieździć — z asercją, że Xcode nie pobrał żadnego pakietu (brak `SourcePackages/checkouts`, log bez „Fetching/Cloning”); `hierarchy` na uruchomionym symulatorze z tym sterownikiem. JVM na macOS bierze `user.home` z konta, nie z `$HOME`, więc job używa katalogu domowego runnera |
| Dokumenty | ROADMAP (status, D-1/D-2/D-5/D-7, STEP-04B — potwierdzenie decyzji, STEP-05 — realizacja, R-2, R-6), LICENSE-AUDIT (wersja 6), NETWORK-AUDIT (wersja 8), ARCHITECTURE-LOCAL §3/§5, RELEASING, CHANGELOG, AGENTS.md (nota forka), CONTRIBUTING (artefakty iOS), handover STEP-04B (scalony, decyzje potwierdzone) |

**Dodane testy:**
- `IOSSimulatorDriverTest` (`maestro-cli`, 7) — pierwszy build (konfiguracja: bez zespołu, cel symulatora, `arm64`, katalog,
  odcisk), ponowne użycie aktualnego buildu, przebudowa po zmianie źródła, wersji CLI, braku runnera, `force`; architektura hosta.
- `DriverBuilderTest` +5 — build symulatora bez podpisu i z odciskiem w `version.properties`; nieudany build bez odcisku;
  brak `xcodebuild` → `IOSDeviceDriverSetupException`; odcisk zależy od treści źródła; kompletność źródła sterownika
  (`project.pbxproj`, schemat, `MaestroDriverLib`, `Vendor/FlyingFox/Package.swift`). Istniejące 2 testy: mocki nowych pól.
- `StartScriptsTest` (`maestro-cli`, 2) — wygenerowane `bin/tapcue` i `bin/tapcue.bat` sprawdzają Javę 17, są obok
  `jvm-version.jar` i nie zawierają linków producenta (task `test` zależy od `startScripts`).
- `CliJarContentsTest` +1 — prawdziwy JAR CLI zawiera źródło sterownika z `MaestroDriverLib` i `Vendor/FlyingFox`, nie zawiera
  `.zip`/`.xctestrun`/`.app/`/`.framework/`; classpath bez ZIP-ów sterownika.
- `IOSBuildProductsExtractorTest` (`maestro-ios-driver`, 3) — **przepisany**: dotychczasowy test był w JUnit 4
  (`org.junit.Test`) i nie uruchamiał się pod JUnit Platform (oczekiwał też nieistniejącego zasobu). Teraz: produkty
  z bezwzględnej ścieżki dla symulatora (z zachowaniem uprawnień), brak zasobów prekompilowanego sterownika, brak runnera → błąd.

**Poza zakresem (bez zmian):** pakiety `maestro.*`, moduły Gradle, format flow, katalogi `~/.tapcue`/`.tapcue/`, identyfikatory
`dev.mobile.maestro*` na urządzeniu, APK sterownika Android, wersja CLI `2.11.0` (0.1.0 — PR-08), README główny repozytorium
(STEP-08), `installLocally.ps1` (dalej odwołuje się do `build/install/maestro` i polecenia `maestro` — pozostałość sprzed
STEP-04B, skrypt deweloperski Windows), zaparkowane workflowy upstream (STEP-06).

## 2. Walidacja

Środowisko: Linux x86_64, JDK 17.0.20 (doinstalowany przez `apt`) i 21, brak Android SDK/Xcode/urządzeń/Maca,
`dl.google.com` zablokowane. Lokalnie — **wariant analityczny** (handover STEP-00 §3: kopia w scratchpadzie bez
`maestro-android` i `google()`) z mirrorem Maven Central jak w STEP-01 §2. **Bez flag `-x`.**

| Test | Komenda | Wynik |
|---|---|---|
| Wszystkie testy + detekt + dystrybucja (od zera) | `LANG=C.UTF-8 ./gradlew --continue --rerun-tasks :maestro-orchestra:detektMain :maestro-client:detektMain test :maestro-cli:installDist :maestro-cli:macosArm64DistZip` | **PASS — 1318 testów, 0 porażek** (STEP-04B: 1300), detekt OK |
| ↳ `maestro-cli` | jw. | **PASS**, 153 (STEP-04B: 138; +7 `IOSSimulatorDriverTest`, +5 `DriverBuilderTest`, +2 `StartScriptsTest`, +1 `CliJarContentsTest`) |
| ↳ `maestro-ios-driver` | jw. | **PASS**, 50 (STEP-04B: 47; +3 przepisany `IOSBuildProductsExtractorTest`, wcześniej nieuruchamiany) |
| ↳ pozostałe | jw. | **PASS** bez zmian liczności: `maestro-client` 324 (2 skipped), `maestro-orchestra` 456 (3 skipped; zaktualizowane fixture'y błędów), `maestro-orchestra-models` 51, `maestro-ios` 2, `maestro-test` 233, `maestro-utils` 49 |
| Weryfikacja archiwum | `scripts/verify-dist.sh --smoke maestro-cli/build/distributions/tapcue-2.11.0-macos-arm64.zip` | **PASS** — 142 pliki, 136 JAR-ów, 67 dozwolonych binariów; `SHA256SUMS` zgodne; `tapcue 2.11.0` + dopisek z rozpakowanego archiwum, pusty `HOME` |
| Testy negatywne skryptu | `scripts/fork/test-verify-dist.sh maestro-cli/build/distributions/tapcue-2.11.0-macos-arm64.zip` | **PASS** — dobre archiwum przyjęte, 13 zmutowanych odrzuconych z właściwym komunikatem |
| Powtarzalność archiwum | `macosArm64DistZip`, potem `macosArm64DistZip --rerun`, porównanie SHA-256 | **PASS** — ten sam SHA-256 (`8504e3a5…f6f8a4`) po zwykłym buildzie, po `--rerun-tasks` całego projektu i po ponownym `--rerun-tasks`. Wymagało: stałych znaczników czasu we wszystkich JAR-ach (`allprojects` w `build.gradle.kts`) i `version.properties` bez daty (`Properties.store`) |
| Strażnik | `scripts/fork/check-no-simulator-server.sh` (w kopii z wynikami builda) | **PASS** |
| Licencje | `generateThirdPartyNotices` | **PASS** — 134 biblioteki zewnętrzne, każda z licencją w POM (bez wpisów w `thirdPartyLicenseOverrides`), 31 unikalnych tekstów z JAR-ów; brak licencji GFTC/własnościowych w `lib/` |
| `jvm-version.jar` (L-07) | `unzip -l`, `javap -c -p JvmVersion.class` | **PASS** — tylko manifest i `JvmVersion.class`, wywołania zgodne z opisanym źródłem |
| `applesimutils` (L-02) | porównanie z wydaniem 0.9.10 | **NOT RUN (niemożliwe)** — wydanie zawiera tylko źródła; binarium zawiera FMDB zgodnie ze źródłem |
| CI GitHub Actions (`ci-baseline.yml`: job Linux z pełnym repo i job `macos-dist` na `macos-26` arm64, Xcode 26.6) | PR-05 [#13](https://github.com/rtnk/TapCue/pull/13), [run 40080598183](https://github.com/rtnk/TapCue/actions/runs/40080598183) na `43836ef` | **PASS** oba joby: Linux — testy, archiwum, `verify-dist`, testy negatywne; macOS — archiwum i `shasum -c`, polecenia CLI i MCP bez sieci, `** TEST BUILD SUCCEEDED **` runnera ze źródła bez pobierania pakietów, `tapcue hierarchy` na symulatorze (126 284 B). Wcześniejsze przebiegi tego joba padały na metodzie testu (zagnieżdżony `sandbox-exec` w SwiftPM i serwerze makr, ścieżka logu w `$HOME`), nie na kodzie — poprawione w workflow |
| E2E, Android/iOS na urządzeniu lokalnie, Windows (`tapcue.bat`) | — | **NOT RUN** lokalnie: brak SDK/Xcode/urządzeń/Maca/Windows; macOS arm64 offline i build sterownika symulatora — job `macos-dist` w CI |

Jak powtórzyć:

```bash
./gradlew :maestro-orchestra:detektMain :maestro-client:detektMain
./gradlew test --continue
./gradlew :maestro-cli:macosArm64DistZip
scripts/verify-dist.sh --smoke maestro-cli/build/distributions/tapcue-*-macos-arm64.zip
scripts/fork/test-verify-dist.sh maestro-cli/build/distributions/tapcue-*-macos-arm64.zip
scripts/fork/check-no-simulator-server.sh
# na Macu z Xcode, z rozpakowanego archiwum:
tapcue-*/bin/tapcue driver-setup --simulator
```

## 3. Ryzyka, blokery, znane problemy

- **Zmiana łamiąca (iOS):** pierwsze uruchomienie na symulatorze trwa kilka minut (build runnera) i wymaga Xcode. Na
  innych wersjach Xcode niż w CI build może się nie udać — błąd z logiem `~/.tapcue/maestro-iphonesimulator-driver-build/output.log`.
  `IOSBuildProductsExtractor` kopiuje produkty do katalogu tymczasowego tylko jako zwykłe pliki (dowiązania symboliczne
  pomija — jak dotąd dla urządzeń fizycznych); poprawność na prawdziwym symulatorze potwierdza tylko job `macos-dist`.
- **Edycja `project.pbxproj` ręczna** (pakiet lokalny, `objectVersion` 60) — weryfikowana wyłącznie przez build w CI na Xcode
  z `macos-26`. Aktualizacja FlyingFox wymaga teraz podmiany katalogu `Vendor/` (opis w `Vendor/README.md`).
- **Teksty licencji spoza JAR-ów** (suplement §3.5) pochodzą z repozytoriów źródłowych w wydanej wersji; dla `jcodec`
  (brak tagu 0.2.5) i `LatencyUtils` — z gałęzi domyślnej. `THIRD-PARTY_LICENSE.txt` całego GraalVM (413 KB, większość
  komponentów nie jest w paczce) nie jest powielany; teksty stron trzecich GraalJS (w tym ICU4J) są. Przegląd właściciela
  (ewentualnie z prawnikiem) przed STEP-08 — G-3.
- **L-05 częściowo:** lista bibliotek w APK sterownika Android — tylko zależności bezpośrednie; `play-services-location` na
  licencji „Android Software Development Kit License” (własnościowa, Google) — do przeglądu właściciela.
- **Build sterownika iOS offline nie jest sprawdzany pod blokadą sieci** (sandboxy nie zagnieżdżają się) — dowodem jest brak
  pobrań w logu i brak `SourcePackages/checkouts`; pełna blokada (`pf`) — STEP-06/08.
- **Test offline na macOS** w CI to `sandbox-exec` (polecenie przestarzałe, ale działające), a nie blokada `pf`; nie rejestruje
  prób połączeń — pełny test egress z rejestrem prób to STEP-06 (Linux) i STEP-08 (Mac).
- **Natychmiastowy EOF na STDIN `tapcue mcp`** (np. `printf … | tapcue mcp`) kończy proces przed odpowiedzią na
  `initialize` — zachowanie sprzed STEP-05 (SDK MCP); krok CI trzyma STDIN otwarte 5 s. Do oceny w STEP-06.
- **Wersja:** archiwum nazywa się `tapcue-2.11.0-macos-arm64.zip` (wersja upstream) do czasu PR-08.
- **`THIRD-PARTY-NOTICES.md` opisuje cały `runtimeClasspath`** — także biblioteki Skia innych platform, których paczka
  `macos-arm64` nie zawiera (nadzbiór, celowo jeden plik dla obu archiwów).
- Build lokalny z wcześniejszego checkoutu może mieć w `maestro-ios-driver/build/resources/main` puste katalogi
  `driver-iPhoneSimulator/`; testy sprawdzają konkretne pliki, nie katalogi, więc nie dają fałszywego alarmu.

## 4. Warunek rozpoczęcia następnego kroku

PR-05 **scalony** do `main`, CI `ci-baseline.yml` zielone na `main` (oba joby, także `macos-dist`). Właściciel przejrzał
`THIRD-PARTY-NOTICES.md`/suplement i zaakceptował (albo odrzucił) dołączenie FlyingFox do repozytorium (`Vendor/`) oraz
ocenę `play-services-location` w APK sterownika (L-05).

## 5. Następny krok

**STEP-06: GitHub Actions CI i testy offline (PR-06).** Zakres w `docs/ROADMAP-0.1.md` §3 „STEP-06”.

## 6. PROMPT DLA NASTĘPNEGO AGENTA (STEP-06)

```text
Jesteś agentem kodującym w repozytorium rtnk/TapCue (fork mobile-dev-inc/Maestro → „TapCue”, polecenie `tapcue`).
Nie masz pamięci poprzednich sesji. Zanim cokolwiek zmienisz:

1. git fetch origin && git checkout main && git pull origin main. Potwierdź przez GitHub, że PR-05
   („STEP-05: Local distribution and license audit”) jest MERGED, a CI `ci-baseline.yml` na `main` zielone (oba joby,
   także `macos-dist`). Jeśli nie — zatrzymaj się i zgłoś to.
2. Przeczytaj: AGENTS.md (nota forka), docs/ROADMAP-0.1.md (§1, §2 — D-9, STEP-06, gate'y G-2/G-4/G-5, AT-01…AT-11),
   docs/handovers/STEP-05.md, docs/NETWORK-AUDIT.md („Plan weryfikacji”, B-03, B-06, B-07, B-08, T-02), docs/LICENSE-AUDIT.md
   (§3, §4, §5 pkt 5), docs/RELEASING.md, docs/ARCHITECTURE-LOCAL.md §5, .github/workflows/ci-baseline.yml,
   scripts/verify-dist.sh, scripts/fork/test-verify-dist.sh.
3. Wykonaj WYŁĄCZNIE STEP-06 — GitHub Actions CI i testy offline — na gałęzi step-06-ci:
   - `.github/workflows/ci.yml` (pull_request + push main, `permissions: contents: read`, akcje przypięte do SHA, bez
     `pull_request_target`): detekt, `./gradlew test`, test MCP STDIO, kontrola zależności (zakazane GAV w `runtimeClasspath`
     CLI: posthog, selenium, truffle-enterprise, maestro-ai, maestro-web), build `macosArm64DistZip` + `verify-dist.sh --smoke`
     + `test-verify-dist.sh`, skan licencji (licencje z `THIRD-PARTY-NOTICES.md` porównane z listą dozwolonych; nowa → fail),
     test egress na Linux (CLI `--version`, `--help`, `check-syntax`, `test` bez urządzenia, handshake `mcp` w przestrzeni
     sieciowej bez trasy poza loopback, np. `unshare -n`, z rejestrem prób DNS/`connect`, np. `strace -f -e trace=network`;
     kryterium: zero prób poza loopback), artefakty raportów.
   - Przenieś job `macos-dist` z `ci-baseline.yml` (macOS arm64: paczka offline, build sterownika symulatora ze źródła,
     `hierarchy` na symulatorze); zakres jobów macOS wg D-9.
   - Integracja Android: emulator na `ubuntu-latest` (KVM), jeśli stabilny; brak emulatora/Xcode → jawne `skipped`, nie
     zielony fałsz. Ścieżki błędów: niepoprawny YAML, brak urządzenia, brak elementu (timeout), błąd sterownika, crash CLI.
   - Lista bibliotek w APK sterownika Android (L-05): `./gradlew :maestro-android:dependencies` w CI (Google Maven dostępny)
     i uzupełnienie `docs/audit/notices-supplement.md` §3.2.
   - Usuń `.github/upstream-workflows-disabled/` i `ci-baseline.yml` (zastąpione) — razem z oceną B-06/B-07/B-08
     (`e2e/`, `maestro-cli/src/test/mcp/`).
   - Nie zmieniaj nazw pakietów `maestro.*`, modułów Gradle, formatu flow, katalogów `~/.tapcue`/`.tapcue/`, zawartości paczki
     ani `verify-dist.sh` poza tym, czego wymaga CI.
4. Gradle uruchamiaj BEZ flag -x. Jeśli środowisko blokuje dl.google.com, użyj wariantu analitycznego z
   docs/handovers/STEP-00.md §3 (bez commitowania; mirror Maven Central jak w STEP-01 §2) i polegaj na CI.
5. Testy: każdy nowy job faktycznie uruchomiony w CI PR (link do runu); test egress z dowodem, że wykrywa próbę połączenia
   (np. celowo dodane `curl` w kopii lokalnej → FAIL). Regresja: `./gradlew test`, `scripts/fork/check-no-simulator-server.sh`,
   `scripts/verify-dist.sh --smoke`, `scripts/fork/test-verify-dist.sh`.
6. Zaktualizuj docs/ROADMAP-0.1.md (status: STEP-05 → MERGED (#<nr PR-05>); STEP-06 — realizacja), docs/handovers/STEP-05.md
   (status „scalony”, §4), docs/NETWORK-AUDIT.md (plan weryfikacji → wyniki, B-06…B-08, T-02), docs/LICENSE-AUDIT.md (skan
   licencji, L-05), docs/ARCHITECTURE-LOCAL.md §5, docs/RELEASING.md, CHANGELOG.md (sekcja forka), AGENTS.md (nota forka),
   dodaj docs/handovers/STEP-06.md wg szablonu STEP-05.md (z promptem dla STEP-07).
7. Utwórz PR „STEP-06: GitHub Actions CI and offline tests” do main (cel, zakres, usunięte/dodane/zachowane, pliki,
   wyniki testów z komendami — tylko faktycznie uruchomione, ryzyka, link do handover, checklista, prompt dla STEP-07).
   Bez auto-merge. Doprowadź CI PR do zielonego.
8. Zakończ odpowiedzią: krok, URL PR, walidacja PASS/FAIL/NOT RUN, ryzyka, warunki następnego kroku,
   prompt na kolejną sesję. Nie zaczynaj STEP-07.
```
