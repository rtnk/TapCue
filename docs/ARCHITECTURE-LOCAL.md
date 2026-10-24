# Architektura lokalna — Maestro Local (repozytorium `rtnk/TapCue`)

Wersja: **0 (STEP-00)** — opis stanu wyjściowego (upstream `v2.11.0`) i architektury docelowej
0.1.0. Sekcja „Stan docelowy” jest planem; każdy krok aktualizuje sekcję „Stan bieżący”.

> Ten projekt jest niezależnym forkiem [mobile-dev-inc/Maestro](https://github.com/mobile-dev-inc/Maestro)
> (Apache-2.0). Nie jest produktem mobile.dev inc. ani Software Mansion S.A. i nie korzysta z ich usług.

## 1. Moduły (stan wyjściowy)

| Moduł Gradle | Rola | Los w 0.1.0 |
|---|---|---|
| `maestro-utils` | narzędzia wspólne, `HttpClient` (OkHttp), `Metrics` (Micrometer, domyślnie NoOp), `TempFileHandler` | zostaje |
| `maestro-proto` | definicje protobuf (wire format sterownika Android) | zostaje |
| `maestro-client` | SDK hosta sterujące urządzeniami: `AndroidDriver`, `IOSDriver`, `CdpWebDriver`/`WebDriver`, silnik JS (GraalJS), `DeviceService`, `ApiKey` (token Cloud), `MockInteractor` (Cloud) | zostaje; usunąć `ApiKey`, `MockInteractor`; ograniczyć `GraalJsHttp` i sterownik web |
| `maestro-android` | sterownik on-device (APK `maestro-app.apk`, `maestro-server.apk`, budowane z tego źródła, zatwierdzone w `maestro-client/src/main/resources`) | zostaje |
| `maestro-ios-xctest-runner` | Swift XCTest runner (źródło); prekompilowane ZIP-y w `maestro-ios-driver/src/main/resources` | zostaje (decyzja D-5 o prekompilowanych ZIP) |
| `maestro-ios-driver` | host-side klient XCTest (HTTP na `127.0.0.1`), `LocalSimulatorUtils` (simctl, applesimutils) | zostaje |
| `maestro-ios` | glue iOS | zostaje |
| `maestro-orchestra-models` | modele komend (w tym `AssertWithAICommand`, `AssertNoDefectsWithAICommand`, `ExtractTextWithAICommand`) | zostaje bez komend AI |
| `maestro-orchestra` | interpretacja komend (`Orchestra.kt`), parser YAML, planowanie workspace; `api(project(":maestro-ai"))` | zostaje bez AI |
| `maestro-ai` | klienci OpenAI/Anthropic/Cloud AI | **usunięty** (STEP-02) |
| `maestro-web` | CDP/Selenium | **usunięty** (STEP-03B, D-4: platforma web wyłączona) |
| `maestro-cli` | CLI (picocli), serwer MCP (STDIO), raporty, nagrywanie, Viewer | zostaje bez Cloud/AI/telemetrii/Viewera |
| `maestro-test` | testy integracyjne z `FakeDriver` | zostaje; rozbudowany o testy offline |

Graf (uproszczony): `maestro-cli → maestro-orchestra → {maestro-client, maestro-orchestra-models, maestro-ai, maestro-utils}`,
`maestro-cli → {maestro-ios, maestro-ios-driver}`, `maestro-client → {maestro-proto, maestro-ios-driver, maestro-web, maestro-utils}`.

## 2. Punkty styku z usługami zdalnymi (stan wyjściowy)

Szczegóły i dowody: [`NETWORK-AUDIT.md`](NETWORK-AUDIT.md). W skrócie, zdalne usługi są splecione
z lokalną ścieżką wykonania w kilku miejscach, które trzeba rozciąć w odpowiedniej kolejności:

1. **`App.main()`** — przed wykonaniem jakiejkolwiek komendy: komunikat i zapis zgody analityki,
   asynchroniczne sprawdzenie wersji; po wykonaniu: zdarzenie PostHog, powiadomienie o „Analyze with AI”,
   blokujące (≤3 s) sprawdzenie aktualizacji i changelog; w obsłudze błędów — `ErrorReporter`.
2. **`TestCommand`** — pola instancji tworzą `ApiClient` i `Auth`, odczytują token Cloud
   i przekazują go jako `apiKey` przez `TestRunner` → `MaestroCommandRunner` → `Orchestra`,
   gdzie jego obecność włącza `CloudAIPredictionEngine`. Także `--analyze`, `--api-url`, `--api-key`,
   promocje Cloud (`PromotionStateManager`) i zdarzenia analityczne.
3. **`MaestroSessionManager`** — shutdown hook wysyła `ScreenReporter.reportMaxDepth()`.
4. **`McpServer`** — rejestruje narzędzia Cloud i `open_maestro_viewer`; instrukcje kierują do Cloud;
   `RunTool` i `McpMaestroSessionManager` używają `McpViewerOrchestra`/`McpViewerDriver`.
5. **`McpCommand`** — domyślnie startuje `McpViewerServer`, który uruchamia binarium simulator-server.
6. **Build `maestro-cli`** — `processResources` zależy od `downloadSimulatorServer` i `buildMcpViewer`.

## 3. Stan docelowy 0.1.0

```
                ┌────────────────────────── host (macOS arm64) ──────────────────────────┐
 użytkownik ──► │ tapcue CLI (JVM 17+)                                                    │
 agent MCP  ──► │   ├─ test / check-syntax / hierarchy / query / record --local / ...     │
   (STDIO)      │   ├─ mcp: list_devices, inspect_screen, take_screenshot, run,           │
                │   │       cheat_sheet (zasób lokalny w JAR)                             │
                │   ├─ raporty: JUnit, HTML (bez CDN), artefakty debug, logi lokalne      │
                │   └─ Orchestra → maestro-client                                          │
                │         ├─ Android: ADB (dadb) ──► emulator/urządzenie (APK sterownika)  │
                │         └─ iOS: simctl/xcodebuild + XCTest runner na 127.0.0.1 ──► Sim.  │
                └─────────────────────────────────────────────────────────────────────────┘
       Brak: Cloud API, PostHog, error upload, update check, AI, Viewer, simulator-server.
```

Zasady architektoniczne:

- **Brak klientów zdalnych w runtime.** Kod usuwany, nie wyłączany flagą. Test architektoniczny
  (STEP-06) skanuje JAR i graf zależności.
- **Kontrolowane błędy dla usuniętych funkcji.** Usunięte komendy CLI — standardowy błąd picocli
  „Unknown command” (zero żądań). Usunięte komendy YAML (`assertWithAI` itd.) — błąd walidacji
  parsera z komunikatem, że funkcja nie istnieje w Maestro Local (zero żądań).
- **Sieć tylko lokalna.** Dozwolone: loopback, ADB, gniazda urządzeń. JS `http.*` w flow — tylko loopback,
  sieci prywatne (RFC 1918, `fc00::/7`) i link-local, sprawdzane przed żądaniem, przy każdym przekierowaniu
  i przy łączeniu gniazda (`LocalNetworkPolicy`, D-3); inne adresy → błąd skryptu. Sterownika web nie ma (D-4).
- **Odrębny stan od oficjalnego Maestro.** Upstream używa `~/.maestro` (sesje, deps, logi),
  `~/.mobiledev/authtoken`, `$XDG_STATE_HOME/maestro` i `.maestro/` w projektach. TapCue (D-1, D-2) ma polecenie
  `tapcue`, stan w `~/.tapcue` (sesje, `deps/`, build sterownika iOS dla urządzeń fizycznych), artefakty testów
  w `$XDG_STATE_HOME/tapcue/tests` (bez zmiennej: `~/.tapcue/tests`), logi w katalogu logów systemu dla aplikacji
  `tapcue`, a w projektach — `.tapcue/`. Jedno źródło ścieżek: `maestro.utils.TapCueDirs` (moduł `maestro-utils`,
  bo korzystają z niego też `maestro-client` i `maestro-ios-driver`; `EnvUtils.xdgStateHome()`/`tapcueHome()` w CLI
  delegują do niego). Katalogów upstream CLI nie czyta i nie zapisuje (STEP-04B).
- **STDIO MCP jest czyste.** `claimMcpStdout()` przekierowuje `System.out` na `stderr` przed
  inicjalizacją; po usunięciu analityki i update-checka znika najczęstsze źródło zanieczyszczeń. MCP używa
  wyłącznie STDIO (bez portu HTTP); `McpStdioTest` sprawdza, że stdout zawiera tylko ramki JSON-RPC.
- **Dystrybucja bez zamkniętych binariów.** Paczka `tapcue-<wersja>-macos-arm64.zip` (STEP-05): `bin/tapcue` (sprawdza
  Javę 17+, D-7 — JRE nie jest dołączane), `lib/*.jar`, `LICENSE`, `NOTICE`, `THIRD-PARTY-NOTICES.md` (generowany
  z `runtimeClasspath`), `README.md`. Bez prekompilowanego sterownika iOS (D-5): runner XCTest dla symulatora powstaje
  przy pierwszym użyciu z kodu w JAR CLI (`driver/ios`, z dołączonym pakietem Swift FlyingFox) lokalnym Xcode, bez sieci.
  `scripts/verify-dist.sh` odrzuca archiwum z zakazanym artefaktem (simulator-server, Viewer, `truffle-enterprise`,
  sterownik iOS/frameworki Apple, PostHog, AI, Selenium), z binarium spoza listy dozwolonych, z niewymienioną biblioteką
  albo linkiem producenta w start-scriptach.

## 4. Granica odpowiedzialności sieciowej

| Ruch | Kto go wykonuje | Polityka forka |
|---|---|---|
| Sterowanie urządzeniem (ADB, XCTest na loopback, gRPC przez forward) | CLI | dozwolony |
| Backend testowanej aplikacji, WebView, przeglądarka w urządzeniu, `openLink` na urządzeniu | urządzenie/aplikacja | poza zakresem; dokumentujemy, nie mockujemy |
| `http.*` w skryptach JS flow | CLI (JVM hosta) | tylko loopback, RFC 1918, `fc00::/7`, link-local; nazwa hosta rozwiązywana przez systemowy resolver i **każdy** adres musi być dozwolony; każdy skok przekierowania sprawdzany; bez proxy; inne → błąd skryptu (STEP-03B, D-3) |
| Platforma web (Chrome na hoście) | — | usunięta w 0.1.0: flow z `url:`, `--platform web`, `chromium` → `WebPlatformNotSupported` (STEP-03B, D-4) |
| `openLink`, `launchApp` z linkiem, deep linki na Android/iOS | urządzenie/symulator (CLI przekazuje tylko URL przez ADB `am start` / `simctl openurl`) | **GRANICA**: ruch wykonuje urządzenie, fork go nie ogranicza ani nie obserwuje; z hosta CLI nie wychodzi żadne żądanie (N-29) |
| `sdkmanager`, `xcodebuild`, `simctl` | narzędzia systemowe | CLI nie wywołuje ich w celu pobierania bez jawnego polecenia |

## 5. Stan bieżący

- STEP-00: brak zmian w kodzie produktu. `main` = upstream `v2.11.0` + commit bootstrap
  `6592abd3` (zaparkowane workflowy upstream).
- STEP-01 (Remove Cloud CLI): CLI nie ma komend `cloud`, `login`, `logout`, `list-cloud-devices`,
  `studio` (picocli: „Unmatched argument”). Usunięte: `CloudInteractor`, `auth/Auth.kt`,
  `maestro.auth.ApiKey` (token `~/.mobiledev/authtoken` / `MAESTRO_CLOUD_API_KEY` nie jest już nigdzie
  czytany), `MockInteractor`, `RemoteVideoRenderer` (`record` renderuje lokalnie), `PromotionStateManager`,
  metody Cloud w `ApiClient`, narzędzia MCP Cloud, linki Cloud w widoku wyników i raportach,
  `isStudio` w sterownikach web. Punkt styku nr 2 z §2 jest rozcięty po stronie CLI: `TestCommand`
  nie tworzy klienta API i przekazuje `apiKey = null` do `TestRunner` → `Orchestra` (komendy AI
  kończą się lokalnym `CloudApiKeyNotAvailable` bez żądania). Pozostają do kolejnych kroków:
  `--analyze`/`--api-key` i parametr `apiKey` w `TestRunner`/`Orchestra` (STEP-02), analityka,
  `ErrorReporter`, `ScreenReporter`, sprawdzanie wersji i reszta `ApiClient` (STEP-03), Viewer
  i `cheat_sheet` (STEP-04).
- STEP-00B (legal containment): build nie ma tasków `downloadSimulatorServer`, `installMcpViewerDeps`,
  `buildMcpViewer` (punkt styku nr 6 z §2 usunięty), JAR CLI nie zawiera `deps/simulator-server/**`
  ani `mcp-viewer/**`. `maestro mcp` nie startuje Viewera (punkt nr 5 rozcięty; `--no-viewer`/`--viewer-port`
  ukryte no-op), `open_maestro_viewer` niezarejestrowane. Kod Viewera (`mcp/viewer/` — nadal używany przez
  `RunTool`/`McpMaestroSessionManager` jako wrapper `Orchestra`/`Driver`; `maestro-cli/mcp-viewer/`) — STEP-04.
- STEP-02 (Remove Remote AI): moduł `maestro-ai` usunięty z builda; graf: `maestro-cli → maestro-orchestra →
  {maestro-client, maestro-orchestra-models, maestro-utils}`. Punkt styku nr 2 z §2 usunięty w całości: brak
  `apiKey`/`AIPredictionEngine` w `Orchestra`, brak komend AI w modelach i parserze (dawne nazwy → `SyntaxError`
  „AI Command Not Available” w `MaestroFlowParser`, przed wykonaniem flow), brak `--analyze`/`--api-key`,
  `TestAnalysisManager`, raportu AI i `ApiClient.analyze` w CLI. `ChatCommand` (ukryte `maestro chat`, w upstream
  już wyłącznie komunikat bez sieci) zostaje bez zmian.
- STEP-03 (Remove telemetry, error upload and update checks): punkty styku nr 1 i 3 z §2 usunięte. `App.main()`
  nie ma już analityki (komunikat o zgodzie, `analytics.json`, zdarzenia PostHog), `ErrorReporter`, sprawdzania wersji
  ani changelogu; obsługa błędów komend (wydzielona do `createCommandLine()`) pisze komunikat i stack trace tylko na
  stderr. `MaestroSessionManager` nie wysyła `ScreenReporter.reportMaxDepth()` w shutdown hooku. Usunięte: pakiety
  `analytics/`, `update/`, `api/` (cały `ApiClient` z `SystemInformationInterceptor`; `CliVersion` →
  `maestro.cli.util`), `EnvUtils.BASE_API_URL`, `DownloadSamplesCommand` z `FileDownloader`, zależność
  `com.posthog:posthog-server`. Raport HTML ma CSS i skrypt inline (otwiera się offline). `start-device` nie pobiera
  obrazów systemu Android (tylko zainstalowane; brak obrazu → komunikat z poleceniem `sdkmanager`). Lokalne logi
  (`maestro.log`, `DebugLogStore`) i raporty bez zmian. Katalog stanu bez zmian (`~/.maestro` /
  `$XDG_STATE_HOME/maestro`), ale pozostawiony przez upstream `analytics.json` jest usuwany przy starcie
  (`LegacyAnalyticsState`, D-2); przejście na `tapcue`/`~/.tapcue`/`.tapcue/` — STEP-04B. Pozostałe ruchy zewnętrzne hosta: `cheat_sheet` (N-15, STEP-04),
  JS `http.*` i platforma web (STEP-03B).
- STEP-03B (Network policy for flow features): JS `http.*` (`GraalJsHttp`) przechodzi przez
  `maestro.js.LocalNetworkPolicy` — sprawdzenie URL przed OkHttp, `Dns` z tą samą regułą na każdym skoku przekierowania,
  gniazda odrzucające niedozwolony adres przed `connect` (literały IP), `Proxy.NO_PROXY`; polityka obejmuje także klienta
  przekazanego do `GraalJsEngine`/`Orchestra`. Platforma web usunięta: brak `CdpWebDriver`, `WebDriver`, `Maestro.web`,
  modułu `maestro-web` i Selenium; graf: `maestro-client → {maestro-proto, maestro-ios-driver, maestro-ios, maestro-utils}`.
  Modele `Platform.WEB`/`DeviceSpec.Web`/`WebLocale` zostają (parsowanie istniejących plików), a każde wejście w platformę
  web kończy się `MaestroException.WebPlatformNotSupported`. GraalJS z artefaktów community (UPL) bez `truffle-enterprise`
  (D-6). Pozostałe ruchy zewnętrzne hosta: `cheat_sheet` (N-15) i link `docs.maestro.dev` w instrukcjach MCP (N-17) — STEP-04.
- STEP-04 (Local-only MCP i usunięcie Viewera): punkt styku nr 4 i 5 z §2 usunięte w całości. `cheat_sheet` czyta
  `mcp/cheat-sheet.md` z JAR CLI (bez sieci), instrukcje serwera nie linkują `docs.maestro.dev`. Pakiet `mcp/viewer/`
  usunięty: `RunTool` tworzy zwykły `Orchestra(maestro)`, a `McpMaestroSessionManager` — zwykłe `AndroidDriver`/`IOSDriver`
  (zestaw obsługiwanych urządzeń bez zmian). Usunięte też `maestro-cli/mcp-viewer/`, `.nvmrc`, `Unpacker.unpackTree`
  i zależności CLI `ktor-server-*`; `--no-viewer`/`--viewer-port` zostają jako ukryte no-op. Serwer MCP:
  `list_devices`, `inspect_screen`, `take_screenshot`, `run`, `cheat_sheet` na STDIO; ruch tylko lokalny (ADB, XCTest
  na loopback). Po STEP-04 host CLI nie ma znanych ścieżek do usług producenta w runtime (pozostają linki w komunikatach,
  N-35 — STEP-05).
- STEP-04B (TapCue: nazwa, polecenie i katalogi): polecenie `tapcue` (picocli, start-script `bin/tapcue`, `--help`
  i `--version` z dopiskiem „based on Maestro (Apache-2.0), not affiliated with mobile.dev”), serwer MCP przedstawia się
  jako `tapcue`. Stan i artefakty — `~/.tapcue`, `$XDG_STATE_HOME/tapcue`, logi `tapcue` (`TapCueDirs`); katalog projektu
  `.tapcue/` (w tym fixture'y `e2e/demo_app/.tapcue/`), `--debug-output <dir>` → `<dir>/.tapcue/tests`. Stary `.maestro/`
  w projekcie: ostrzeżenie `tapcue test` z poleceniem `mv .maestro .tapcue` (`LegacyProjectDir`), bez automatycznego
  odczytu. `LegacyAnalyticsState` usunięty — CLI nie dotyka `~/.maestro` ani `$XDG_STATE_HOME/maestro`. Bez zmian:
  pakiety `maestro.*`, moduły Gradle, format YAML flow, zmienne JS `maestro.*`, identyfikatory sterowników na urządzeniu
  (`dev.mobile.maestro*`), nazwy archiwów dystrybucji `maestro.zip`/`maestro.tar` i `jreleaser` (STEP-05).
- STEP-05 (Dystrybucja lokalna i audyt licencji): archiwum `tapcue-<wersja>-macos-arm64.zip` (task
  `:maestro-cli:macosArm64DistZip`, katalog główny `tapcue-<wersja>-macos-arm64/`, znaczniki czasu i kolejność plików
  deterministyczne) z `SHA256SUMS`, bez Skia innych platform i `tapcue.bat`; ogólne `distZip`/`distTar` → `tapcue-<wersja>.zip/.tar`.
  `NOTICE` w katalogu głównym repozytorium i paczki; `THIRD-PARTY-NOTICES.md` z taska `generateThirdPartyNotices` (licencje
  z POM, teksty i NOTICE z JAR-ów, suplement `docs/audit/notices-supplement.md`; brak licencji → błąd builda); `README.md`
  instalacji z `maestro-cli/src/dist/`. Usunięte: `jreleaser` (B-04), `scripts/install.sh` (B-05), publikacja Maven Central
  (B-09), linki producenta w komunikatach i start-scriptach (N-35). iOS (D-5): prekompilowane ZIP-y sterownika i task
  `buildIosDriver` usunięte; `maestro.cli.driver.IOSSimulatorDriver` buduje runner dla symulatora przez `DriverBuilder`
  (`xcodebuild build-for-testing`, `generic/platform=iOS Simulator`, bez podpisu, architektura hosta) do
  `~/.tapcue/maestro-iphonesimulator-driver-build`, z blokadą (wątki i procesy) i przebudową, gdy zmieni się wersja CLI
  albo odcisk SHA-256 źródła w JAR (`version.properties`); `IOSBuildProductsExtractor` przyjmuje bezwzględną ścieżkę
  produktów także dla symulatora; `tapcue driver-setup --simulator` wymusza build. JAR CLI zawiera teraz całe źródło schematu
  `maestro-driver-ios` (z `MaestroDriverLib/` i `Vendor/FlyingFox/` — pakiet lokalny zamiast zdalnego, N-36). Bez zmian:
  pakiety `maestro.*`, moduły Gradle, format flow, katalogi `~/.tapcue`/`.tapcue/`, identyfikatory `dev.mobile.maestro*`
  na urządzeniu, sterownik Android (APK w `maestro-client`), wersja CLI (`2.11.0` do STEP-08).
- STEP-06 (CI i testy offline): jedynym workflowem jest `.github/workflows/ci.yml` (joby `changes`, `build`, `egress`,
  `android`, `macos`; akcje przypięte do SHA, `permissions: contents: read`); `ci-baseline.yml` i zaparkowane workflowy upstream
  usunięte. Narzędzia CI w `scripts/fork/`: `egress-test.sh` (+ `egress-reap.sh`) — przestrzeń sieciowa z samym loopbackiem
  i rejestr prób przez `strace`; `check-forbidden-deps.sh`, `check-licenses.py` z `allowed-licenses.tsv`, `test-ci-checks.sh`;
  `android-integration.sh` z flow w `android-flows/`; `android-driver-deps.py` (L-05); `macos-sandbox-log.sh`;
  `resolve-test-runtime.gradle`. Zmiana produktu: serwer MCP owija STDIN/STDOUT w `PendingRequests` — EOF na STDIN dociera
  do transportu SDK dopiero, gdy każde odczytane żądanie ma odpowiedź (wcześniej SDK zamykał sesję i porzucał żądania
  w toku). Usunięte: `maestro-cli/src/test/mcp/` (ewaluacje LLM upstream), `e2e/workspaces/web` z serwerem fixture'ów,
  `e2e/update_samples`. Bez zmian: pakiety `maestro.*`, moduły Gradle, format flow, katalogi `~/.tapcue`/`.tapcue/`,
  zawartość paczki, `Vendor/`, `verify-dist.sh`.
- STEP-07 (pipeline wydania): drugi workflow `.github/workflows/release.yml` — tag `vX.Y.Z` (adnotowany, `= v<CLI_VERSION>`,
  na `main`) → build i testy na `macos-26` arm64, kontrole archiwum (`verify-dist.sh`, licencje, zakazane GAV, paczka bez
  sieci z rejestrem odmów sandboxa), egress na Linux, powtórny build na Linux z porównaniem bajtów, a na końcu GitHub Release
  (`tapcue-<wersja>-macos-arm64.zip` + `SHA256SUMS`, notatki z `CHANGELOG.md`) — bez nadpisywania istniejącego Release;
  `workflow_dispatch` = walidacja bez publikacji. Nowe skrypty w `scripts/fork/`: `release-context.sh`, `release-notes.py`,
  `publish-release.sh`, `compare-archives.py`, `macos-offline-test.sh` (krok offline joba `macos` z `ci.yml` przeniesiony do
  skryptu), `test-release.sh`. Bez zmian: kod produktu, pakiety `maestro.*`, moduły Gradle, format flow, katalogi
  `~/.tapcue`/`.tapcue/`, zawartość paczki, `Vendor/`, wersja CLI `2.11.0`.
- STEP-08 (wydanie 0.1.0): `CLI_VERSION=0.1.0` (archiwum `tapcue-0.1.0-macos-arm64.zip`, `tapcue --version` → `tapcue 0.1.0`).
  `RealIOSDeviceDriver` przebudowuje sterownik dla urządzeń fizycznych przy każdej wersji różnej od zapisanej w
  `version.properties` (wcześniej tylko przy wyższej — wersja spadła z 2.11.0 do 0.1.0; dla symulatora `IOSSimulatorDriver`
  porównywał już równość). Suplement not (`docs/audit/notices-supplement.md` §3.6) z pełnymi tekstami licencji bibliotek APK
  sterownika Android. README repozytorium opisuje TapCue (instalacja z Release, CLI, MCP, ograniczenia) z logo
  `docs/assets/tapcue-logo.png` — logo nie trafia do paczki (zawartość archiwum i `verify-dist.sh` bez zmian). Bez zmian:
  pakiety `maestro.*`, moduły Gradle, format flow, katalogi `~/.tapcue`/`.tapcue/`, `Vendor/`, `release.yml`, `ci.yml`.
