# ROADMAP 0.1.0 — Maestro Local (repozytorium `rtnk/TapCue`)

Status dokumentu: **STEP-00B — w review ([PR-00B #3](https://github.com/rtnk/TapCue/pull/3))**; STEP-00 ([#1](https://github.com/rtnk/TapCue/pull/1)) i STEP-01 ([#2](https://github.com/rtnk/TapCue/pull/2)) scalone 2026-10-23. Ostatnia aktualizacja: 2026-10-23.

## 0. Punkt wyjścia

| Element | Wartość |
|---|---|
| Upstream | `https://github.com/mobile-dev-inc/Maestro` |
| Bazowy tag upstream | `v2.11.0` → commit `c8c361be1cc89b2e742f185466a302a22778be6b` (2026-09-29) |
| `upstream/main` w chwili startu | `436309bdbd0aca29450a3f0c7fcf8bbe9c3ab027` (19 commitów po `v2.11.0`, świadomie **nie** wzięte) |
| Repozytorium docelowe | `rtnk/TapCue` (prywatne), gałąź `main` |
| `main` po bootstrapie | `6592abd3` = `v2.11.0` + jeden commit: `.github/workflows/` → `.github/upstream-workflows-disabled/` (czyste przeniesienie, `[skip ci]`) |
| Remotes w kopii roboczej | `origin` = `rtnk/TapCue`, `upstream` = `mobile-dev-inc/Maestro` (tylko fetch; tagi upstream **nie** są wypychane do forka) |
| Wersja docelowa | `0.1.0`, tag `v0.1.0` |

Dlaczego bootstrap zawiera jedną zmianę poza PR: repozytorium docelowe było puste, więc `main` musiał
powstać przez bezpośredni push. Workflowy upstream uruchomiłyby się przy tym pushu (`push: main`,
`schedule`) i na każdym PR: publikują do Maven Central/GCS upstream, uruchamiają joby macOS, a przez
`./gradlew test`/`build` wykonują task `downloadSimulatorServer` — czyli pobierałyby i pakowały binaria
objęte licencją L-01 w CI forka. Commit bootstrap jedynie przenosi pliki (weryfikacja:
`git diff -M --stat v2.11.0 6592abd3` → 14 plików, 0 insercji, 0 usunięć).

## 1. Zasady pracy (skrót kontraktu)

1. Jeden krok = jedna sesja = jedna gałąź `step-XX-<slug>` = jeden PR `STEP-XX: <cel>` do `main`.
2. Bez auto-merge. Kolejny krok startuje dopiero po merge poprzedniego PR (chyba że właściciel zdecyduje inaczej).
3. Każdy PR aktualizuje: ten plik (status), `NETWORK-AUDIT.md` / `LICENSE-AUDIT.md` (jeśli dotyczy),
   `ARCHITECTURE-LOCAL.md` §5, `CHANGELOG.md` (sekcja forka) i dodaje `docs/handovers/STEP-XX.md`.
4. Testy opisane w PR muszą być faktycznie uruchomione; inaczej status `NOT RUN` z powodem.
5. Bez force-push, bez nadpisywania tagów, bez obchodzenia branch protection i licencji.
6. Kod zdalny jest **usuwany**, nie ukrywany za flagą.

## 2. Decyzje właściciela (otwarte)

| ID | Pytanie | Rekomendacja | Blokuje |
|---|---|---|---|
| D-1 | Nazwa produktu. „Maestro” to nazwa/znak mobile.dev; Apache-2.0 §6 nie daje praw do znaków towarowych, a „Maestro Local” może sugerować produkt oficjalny. | Nazwa własna (np. **TapCue** — jak repozytorium) z opisem „based on Maestro (Apache-2.0), not affiliated with mobile.dev”. Jeśli zostaje „Maestro Local” — wyraźny disclaimer w README, `--help`, Release. | STEP-05 (nazwa archiwum, launcher), STEP-08 |
| D-2 | Nazwa pliku wykonywalnego i katalogi stanu. Upstream: `maestro`, `~/.maestro`, `~/.mobiledev`, `$XDG_STATE_HOME/maestro`. | Własna nazwa (`tapcue` lub `maestro-local`) i własny katalog (`~/.tapcue`), by nie kolidować z oficjalną instalacją. Format YAML flow i katalog `.maestro/` w projektach użytkownika — bez zmian (kompatybilność flow). | STEP-05 (nazwa), STEP-03 (katalog stanu — może być zrobione wcześniej) |
| D-3 | Polityka `http.*` w JavaScript flow (N-25). | Domyślnie tylko loopback i sieci prywatne (RFC 1918, `fc00::/7`, link-local); inne adresy → błąd z jasnym komunikatem. Ewentualny jawny opt-in (flaga CLI) jako osobna, udokumentowana decyzja — nie w 0.1.0. | STEP-03B |
| D-4 | Platforma `web` (Chrome + Selenium na hoście, N-26…N-30). | **Wyłączyć w 0.1.0** (kontrolowany błąd „platforma web nie jest wspierana w 0.1.0”); ewentualny powrót po 0.1.0 z lokalnym chromedriverem, `SE_OFFLINE=true` i polityką URL. | STEP-03B |
| D-5 | Prekompilowane ZIP-y sterownika iOS zawierają frameworki Apple z Xcode (`XCTest.framework`, `XCUIAutomation.framework`, `XCTestCore`, `XCUnit`, `libXCTestSwiftSupport.dylib`, `libswift_Concurrency.dylib` …) — redystrybucja podlega licencji Xcode/SDK (L-04). | Nie dystrybuować prekompilowanych ZIP; budować runner lokalnie z dołączonego źródła (`xcodebuild`, ścieżka już istnieje w `DriverBuilder`/`LocalXCTestInstaller`) przy pierwszym użyciu iOS. Xcode i tak jest wymagany dla symulatora. Wymaga weryfikacji, że ścieżka „build from source” działa dla symulatora. | STEP-05 |
| D-6 | GraalJS: `org.graalvm.js:js:24.2.0` dociąga `org.graalvm.truffle:truffle-enterprise` na licencji Oracle GFTC (L-03). | Przejść na artefakty community (UPL): `js-community`/`js-language` + `polyglot`, z wykluczeniem `truffle-enterprise`; regresja testów JS (`GraalJsEngineTest`, `IntegrationTest`). | STEP-05 (najpóźniej); może wejść do STEP-03B |
| D-7 | JRE w paczce. | 0.1.0: wymagany systemowy JDK/JRE 17+ (jasno w README, launcher sprawdza wersję — mechanizm już istnieje). Paczka **nie** jest nazywana samowystarczalną. Bundle JRE (jlink) — po 0.1.0. | STEP-05 |
| D-8 | Czy przyjąć proponowany **STEP-00B „Legal containment”** (usunięcie `downloadSimulatorServer`/Viewer z builda przed STEP-01)? | **Tak** — dziś każdy `./gradlew build/test/run` modułu CLI na `main` pobiera i pakuje binaria L-01. **Rozstrzygnięta:** STEP-01 wykonano bezpośrednio po PR-00; STEP-00B właściciel zlecił po merge PR-01 (2026-10-23), przed STEP-02. Po scaleniu PR-00B flagi `-x downloadSimulatorServer …` są zbędne (i błędne — taski nie istnieją). | kolejność kroków |
| D-9 | Widoczność repozytorium przy wydaniu i koszty runnerów macOS (w repo prywatnym minuty macOS liczone są z mnożnikiem). | Testy macOS arm64 tylko w `release.yml` i ręcznym `workflow_dispatch`; PR-y — Linux. Decyzja o upublicznieniu przed STEP-08. | STEP-06/07/08 |

## 3. Kroki

Legenda statusu: `TODO` · `IN PROGRESS` · `IN REVIEW` · `MERGED` · `BLOCKED`.

| Krok | PR | Cel | Zależy od | Status | Szacunek |
|---|---|---|---|---|---|
| STEP-00 | PR-00 | Baseline, audyty, plan, bazowy CI | — | MERGED ([#1](https://github.com/rtnk/TapCue/pull/1)) | 1 sesja |
| STEP-00B *(D-8)* | PR-00B | Legal containment: build bez simulator-server i Viewera | PR-00 (wykonany po PR-01) | IN REVIEW ([#3](https://github.com/rtnk/TapCue/pull/3)) | 0,5 sesji |
| STEP-01 | PR-01 | Remove Cloud CLI | PR-00 | MERGED ([#2](https://github.com/rtnk/TapCue/pull/2)) | 1–2 sesje |
| STEP-02 | PR-02 | Remove Remote AI | PR-01, PR-00B | TODO | 1–2 sesje |
| STEP-03 | PR-03 | Remove telemetry, error upload, update checks, auto-downloads | PR-02 | TODO | 1 sesja |
| STEP-03B | PR-03B | Polityka sieci dla funkcji flow (JS `http`, web, `openLink`) | PR-03, D-3, D-4 | TODO | 1 sesja |
| STEP-04 | PR-04 | Local-only MCP, usunięcie Viewera | PR-03B | TODO | 1–2 sesje |
| STEP-05 | PR-05 | Dystrybucja lokalna macOS arm64 + audyt licencji | PR-04, D-1, D-2, D-5, D-6, D-7 | TODO | 2 sesje |
| STEP-06 | PR-06 | GitHub Actions CI + testy offline | PR-05 | TODO | 2 sesje |
| STEP-07 | PR-07 | Release pipeline | PR-06 | TODO | 1 sesja |
| STEP-08 | PR-08 + tag | Release candidate i publikacja v0.1.0 | PR-07, wszystkie gate'y | TODO | 1 sesja + operacja release |

Numeracja kroków 00–08 jest zgodna z kontraktem; STEP-00B i STEP-03B to kroki dodane w wyniku audytu
(zostały wydzielone, żeby nie mieszać zakresów i nie powiększać PR-ów).

---

### STEP-00 — Baseline and implementation plan (PR-00)

- **Zakres:** klon upstream, bootstrap `main`, audyty (`NETWORK-AUDIT.md`, `LICENSE-AUDIT.md`),
  architektura, ten plan, `RELEASING.md` (szkic), sekcja forka w `CHANGELOG.md`, nota w `AGENTS.md`,
  bazowy workflow `ci-baseline.yml` ze strażnikiem `scripts/fork/check-no-simulator-server.sh`.
- **Bez zmian w kodzie produktu.**
- **Wyjście:** PR-00 zaakceptowany i scalony; decyzje D-1…D-9 co najmniej przeczytane (D-8 rozstrzygnięta).

### STEP-00B — Legal containment (PR-00B)

- **Cel:** żaden build/test/run na `main` nie pobiera, nie pakuje i nie uruchamia simulator-server.
- **Zakres:**
  - `maestro-cli/build.gradle.kts`: usunąć `downloadSimulatorServer`, `installMcpViewerDeps`,
    `buildMcpViewer` i ich wpięcie w `processResources`; usunąć `simulatorServer` z `gradle/libs.versions.toml`.
  - `McpCommand`: Viewer nie jest uruchamiany (opcje `--no-viewer`/`--viewer-port` zostają jako no-op
    z ostrzeżeniem na stderr albo zostają usunięte — decyzja w PR), `open_maestro_viewer` niezarejestrowane.
  - `Dependencies.installSimulatorServer()` i wywołanie w `McpViewerServer` — usunięte lub rzucające kontrolowany błąd.
  - Test (Gradle/JUnit): zbudowany JAR CLI nie zawiera `deps/simulator-server/**` ani `mcp-viewer/**`.
  - `ci-baseline.yml`: usunąć flagi `-x downloadSimulatorServer …` (stają się zbędne).
- **Poza zakresem:** pełne usunięcie pakietu `mcp/viewer/` i `maestro-cli/mcp-viewer/` (STEP-04).
- **Wyjście:** `./gradlew :maestro-cli:build` nie wykonuje żądań do `simulator-server-releases`; test zawartości JAR zielony.
- **Realizacja (PR-00B, szczegóły w `docs/handovers/STEP-00B.md`):** taski i wersja usunięte; `--no-viewer`
  i `--viewer-port` zostają jako ukryte no-op (`--viewer-port` → ostrzeżenie na stderr), bo konfiguracje klientów
  MCP mogą je przekazywać; `Dependencies.installSimulatorServer()` usunięte, `McpViewerServer` (nieosiągalny)
  zwraca kontrolowany błąd zamiast uruchamiać binarium; `OpenMaestroViewerTool` usunięty. Test:
  `maestro-cli` `CliJarContentsTest` sprawdza prawdziwy JAR (task `test` zależy od `jar`) i classpath.

### STEP-01 — Remove Cloud CLI (PR-01)

- **Usunąć:** `CloudCommand`, `LoginCommand`, `LogoutCommand`, `ListCloudDevicesCommand`, `StudioCommand`
  (+ `util/StudioDownload.kt`), `cloud/CloudInteractor.kt`, `auth/Auth.kt`, metody Cloud w `api/ApiClient.kt`
  (upload, runMaestroTest, uploadStatus, device list, login, start-trial, orgs/projects, describeRun, render),
  `graphics/RemoteVideoRenderer.kt` (`record` zawsze lokalnie — `LocalVideoRenderer`; `--local` jako no-op
  lub usunięte), `promotion/PromotionStateManager.kt` i komunikaty promocyjne w `TestCommand`,
  linki Cloud w `view/TestSuiteStatusView.kt`, `maestro-client/.../auth/ApiKey.kt`,
  `maestro-client/.../mockserver/MockInteractor.kt`, nawigację `isStudio` do `maestro.mobile.dev` w `CdpWebDriver`.
- **`TestCommand`:** bez pól `client`/`auth`/`authToken`; `--api-url` usunięte; token nie jest przekazywany do
  `TestRunner` (parametr `apiKey` zostaje tylko do czasu STEP-02, ustawiany na `null`, albo usunięty od razu,
  jeśli nie wymaga zmian w Orchestra — decyzja w PR).
- **Analytics:** usunąć `identifyAndUpdateState`/`identifyUserIfNeeded` (zależą od `ApiKey`/`ApiClient`);
  reszta analityki — STEP-03.
- **Zachować:** lokalne testy, raporty (JUnit/HTML), sharding (`--shards`, `--shard-split`/`--shard-all`),
  artefakty, `record --local`.
- **Testy:** testy CLI (picocli) — usunięte komendy dają „Unknown command”; `test --help` nie zawiera opcji Cloud;
  `TestCommand` da się skonstruować bez `HOME/.mobiledev` i bez sieci; istniejące testy `maestro-cli` zielone
  (testy dotyczące usuniętych klas usunięte razem z nimi — wymienione w PR).
- **Wyjście:** lokalny test nie konstruuje klienta Cloud i nie potrzebuje konta ani sieci.
- **Realizacja (PR-01, szczegóły w `docs/handovers/STEP-01.md`):** zakres jak wyżej, plus — bo ich zaplecze
  (`ApiKey`, metody Cloud `ApiClient`, `CloudInteractor`) zostało usunięte — narzędzia MCP `list_cloud_devices`,
  `run_on_cloud`, `get_cloud_run_status`, `describe_cloud_run` i sekcja Cloud w instrukcjach MCP (przeniesione
  z STEP-04), metadane Cloud w raportach JUnit/HTML (`cloud.*`), parametr `isStudio` w sterownikach web.
  `--local` w `record` zostaje jako ukryty no-op. `TestRunner` dostaje `apiKey = null`; `--analyze`/`--api-key`
  zostają do STEP-02 (token wyłącznie z jawnego `--api-key`, bez `~/.mobiledev`).

### STEP-02 — Remove Remote AI (PR-02)

- **Usunąć:** moduł `maestro-ai` (`settings.gradle.kts`, `api(project(":maestro-ai"))` w Orchestra);
  w `Orchestra.kt` — `apiKey`, `AIPredictionEngine`, obsługa komend AI; w `maestro-orchestra-models` —
  `AssertNoDefectsWithAICommand`, `AssertWithAICommand`, `ExtractTextWithAICommand` i pola w `MaestroCommand`;
  w `maestro-orchestra/yaml` — `YamlAssertWithAI`, `YamlAssertNoDefectsWithAI`, `YamlExtractTextWithAI`,
  wpisy w `YamlFluentCommand`, `MaestroFlowParser`, schemat (`yaml/schema/FlowCommandSchema.kt`);
  w CLI — `--analyze`, `--api-key`, `insights/TestAnalysisManager.kt`, `report/HtmlAITestSuiteReporter.kt`,
  `report/HtmlInsightsAnalysisReporter.kt`, `ai_report.css`, `tailwind.config.js`, `Defect` w `TestDebugReporter`,
  AI w `AnsiResultView`, parametry `apiKey`/`analyze` w `TestRunner`/`MaestroCommandRunner`.
- **Walidacja:** flow z `assertWithAI`/`assertNoDefectsWithAI`/`extractTextWithAI` → błąd parsera
  (`ValidationError`) z komunikatem „komenda AI nie jest dostępna w Maestro Local” — bez requestu.
- **Testy:** regresja parsera (dawne komendy AI → błąd), wykonawcy (`maestro-test` `IntegrationTest`),
  lokalnych asercji (`assertVisible`, `assertTrue`); test grafu: `runtimeClasspath` CLI bez `maestro-ai`.
- **Wyjście:** paczka i graf runtime nie zawierają zdalnych klientów/modeli AI.

### STEP-03 — Remove Telemetry, Error Upload and Update Checks (PR-03)

- **Usunąć:** `analytics/` (PostHog, `AnalyticsStateManager`, `CommandArgsSanitizer`, zdarzenia) i wszystkie
  wywołania `Analytics.*`; zależność `libs.posthog`; `util/ErrorReporter.kt`; `util/ScreenReporter.kt`
  (+ wywołanie w shutdown hooku); `update/Updates.kt`, `util/ChangeLogUtils.kt` i blok aktualizacji w `App.kt`;
  `TestAnalysisManager.maybeNotify` (jeśli pozostało); `DownloadSamplesCommand`; nagłówki `SystemInformationInterceptor`;
  pozostałości `api/ApiClient.kt` i `EnvUtils.BASE_API_URL`.
- **Raport HTML:** bez CDN (inline minimalny CSS), raport otwiera się offline.
- **`start-device`:** brak automatycznego pobierania obrazów systemu (tylko zainstalowane; jasny komunikat,
  jakie polecenie `sdkmanager` użytkownik może wykonać sam).
- **Katalog stanu:** jeśli D-2 rozstrzygnięta — przeniesienie stanu do katalogu forka.
- **Zachować:** lokalne logi (`maestro.log`, `DebugLogStore`), stack trace na stderr, raporty.
- **Testy:** wymuszony wyjątek komendy → stack trace lokalnie, zero połączeń (test z `MockWebServer`
  i nadpisanym resolverem/proxy, który failuje przy jakiejkolwiek próbie połączenia); `--version`/`--help`
  bez wątków sieciowych; skan JAR na zakazane hosty.
- **Wyjście:** zwykłe uruchomienie i wymuszone błędy nie nawiązują połączeń zewnętrznych.

### STEP-03B — Polityka sieci dla funkcji flow (PR-03B)

- **JS `http.*`** (`GraalJsHttp`): walidacja hosta przed żądaniem wg D-3 (rozwiązanie DNS i sprawdzenie
  adresu — także po przekierowaniach; wyłączyć `followRedirects` do hostów spoza polityki).
- **Platforma web** wg D-4 (rekomendacja: wyłączona z kontrolowanym błędem; `selenium`/`maestro-web`
  poza runtime classpath, jeśli możliwe).
- **`openLink`/`launchApp` na urządzeniach:** udokumentować jako GRANICA (ruch wykonuje urządzenie).
- **Testy:** `http.get("http://127.0.0.1:<mock>")` działa; `http.get("https://example.com")` → błąd bez
  próby połączenia; przekierowanie na host publiczny → błąd.
- **Wyjście:** brak ścieżki, w której host CLI wykonuje żądanie do publicznego internetu z treści flow.

### STEP-04 — Local-only MCP i usunięcie zamkniętego Viewera (PR-04)

- **Zachować (zweryfikowane nazwy w `v2.11.0`):** `list_devices`, `inspect_screen`, `take_screenshot`, `run`
  (`RunTool.TOOL_NAME = "run"`), `cheat_sheet`.
- **Usunąć:** pozostałości Viewera (narzędzia Cloud `run_on_cloud`, `get_cloud_run_status`,
  `describe_cloud_run`, `list_cloud_devices` usunięto już w STEP-01, a `open_maestro_viewer`,
  `Dependencies.installSimulatorServer` i start Viewera — w STEP-00B); pakiet `mcp/viewer/` (zależności w
  `RunTool` i `McpMaestroSessionManager` → zwykły `Orchestra`); katalog `maestro-cli/mcp-viewer/` (+ `.nvmrc`);
  `Unpacker.unpackTree` (nieużywane od STEP-00B); ukryte opcje `--no-viewer`/`--viewer-port` (decyzja: usunąć
  albo zostawić jako no-op dla zgodności konfiguracji klientów).
- **`cheat_sheet`:** treść jako wersjonowany zasób w JAR (`resources/mcp/cheat-sheet.md`), napisana dla forka
  na podstawie dokumentacji w repozytorium (bez kopiowania treści o nieznanej licencji z `docs.maestro.dev`).
- **Instrukcje serwera** (`McpServer.kt`): bez linku do `docs.maestro.dev` (sekcję Cloud usunięto w STEP-01).
- **Testy:** test JSON-RPC na STDIO (proces CLI lub serwer in-process): `initialize` → `tools/list` (dokładnie
  zachowane narzędzia) → `tools/call cheat_sheet` → `list_devices` bez urządzeń (kontrolowany wynik) →
  zamknięcie STDIN kończy proces; stdout zawiera wyłącznie ramki JSON-RPC. Testy z urządzeniem — jeśli dostępne.
- **Wyjście:** lokalny MCP działa bez Viewera i bez binarium simulator-server.

### STEP-05 — Dystrybucja lokalna i audyt licencji (PR-05)

- Usunąć `jreleaser` (brew tap upstream, release do `mobile-dev-inc/maestro`), `scripts/install.sh` upstream,
  komunikaty z linkami `maestro.dev`/`maestro.mobile.dev` w start-scriptach.
- Archiwum `maestro-local-0.1.0-macos-arm64.zip` (lub wg D-1): `bin/<launcher>`, `lib/*.jar`, `LICENSE`,
  `NOTICE`, `THIRD-PARTY-NOTICES.md` (licencje wszystkich zależności runtime), `README` instalacji.
- `SHA256SUMS`, skrypt skanu zawartości (`scripts/verify-dist.sh`): lista zakazanych ścieżek/wzorców
  (simulator-server, `mcp-viewer`, `truffle-enterprise` wg D-6, prekompilowane ZIP iOS wg D-5, klasy
  `com.posthog`, `maestro.ai`) i lista dozwolonych natywnych bibliotek z licencją.
- Weryfikacja instalacji i uruchomienia na macOS arm64 bez internetu (wynik w handover; jeśli brak Maca —
  runner `macos-*` arm64 z blokadą egress).
- **Wyjście:** paczka działa offline na przygotowanym Macu, kompletna licencyjnie, bez zakazanych binariów.

### STEP-06 — GitHub Actions CI i testy offline (PR-06)

- `.github/workflows/ci.yml` (PR + push `main`, `permissions: contents: read`, akcje przypięte do SHA):
  detekt, `./gradlew test`, testy schematów YAML, test MCP STDIO, kontrola zależności (zakazane GAV), build
  archiwum + `verify-dist.sh`, skan licencji, test egress (Linux: brak trasy poza loopback + rejestr prób
  DNS/`connect`), artefakty raportów.
- Integracja Android: emulator na `ubuntu-latest` (KVM) — jeśli stabilny; iOS: tylko `workflow_dispatch`/
  release na `macos-*` (D-9). Brak emulatora/Xcode → job kończy się jawnym `skipped`/komunikatem, nie zielonym fałszem.
- Ścieżki błędów: niepoprawny YAML, brak urządzenia, brak elementu (timeout), błąd sterownika, crash CLI.
- Usunięcie `.github/upstream-workflows-disabled/` i `ci-baseline.yml` (zastąpione).

### STEP-07 — Release pipeline (PR-07)

- `.github/workflows/release.yml`: `push: tags: ['v[0-9]+.[0-9]+.[0-9]+']` + `workflow_dispatch` (tryb
  walidacji bez publikacji). Job build na `macos-*` arm64 (asercja `uname -m == arm64`), checkout dokładnie
  otagowanego commita, testy + `verify-dist.sh` + skan licencji, `SHA256SUMS`, upload artefaktu; job publish
  (`needs: build`, jedyny z `contents: write`, `GITHUB_TOKEN`) tworzy Release tylko z tagu i tylko jeśli
  Release/tag nie istnieje (bez nadpisywania). Zero `pull_request_target`.
- `docs/RELEASING.md`: odtworzenie builda, procedury awaryjne (wycofanie Release, nowy patch zamiast nadpisania).

### STEP-08 — Release candidate i v0.1.0 (PR-08 + operacja release)

- PR-08: `CHANGELOG.md` 0.1.0, README (instalacja, uruchomienie CLI i MCP, ograniczenia), `RELEASING.md`,
  raport licencyjny, lista znanych ograniczeń i nieprzetestowanych platform.
- Po merge: tag `v0.1.0` na commicie `main` z PR-08 → pipeline → weryfikacja Release (pobranie, SHA256,
  instalacja z pobranego pliku, testy akceptacyjne AT-01…AT-11).

## 4. Gate'y blokujące publikację v0.1.0

| Gate | Warunek | Weryfikacja |
|---|---|---|
| G-1 | PR-00…PR-07 (oraz 00B/03B) scalone | historia `main` |
| G-2 | Zielony CI na `main` | GitHub Actions |
| G-3 | `LICENSE-AUDIT.md` zamknięty: L-01 usunięty, L-03/L-04 rozstrzygnięte, `THIRD-PARTY-NOTICES` kompletne | review + `verify-dist.sh` |
| G-4 | Brak simulator-server i innych zakazanych artefaktów w paczce | `verify-dist.sh` w `release.yml` |
| G-5 | Test offline (egress) zielony na Linux CI i na macOS arm64 | logi CI / handover STEP-08 |
| G-6 | Paczka zainstalowana i uruchomiona z pliku pobranego z Release | handover STEP-08 |
| G-7 | Decyzje D-1, D-2, D-5, D-6, D-7 rozstrzygnięte | ten plik |
| G-8 | Release notes wymieniają braki pokrycia i nieprzetestowane platformy | Release |

## 5. Testy akceptacyjne produktu

| ID | Test | Krok wprowadzający | Platforma |
|---|---|---|---|
| AT-01 | `--version`, `--help` bez kontaktu z internetem | 03 (+06 egress) | Linux CI, macOS |
| AT-02 | `list-devices` — tylko lokalne Android/iOS | 01/04 | Android emu, iOS sim |
| AT-03 | Flow: `launchApp`, `tapOn`, `assertVisible`, `scroll`, `inputText` | 06 | Android emu (CI), iOS sim (dispatch/release) |
| AT-04 | Oczekiwanie na element i timeout (`extendedWaitUntil`, `assertVisible` na brakującym elemencie) | 06 | jw. + `maestro-test` (FakeDriver) |
| AT-05 | Screenshot i hierarchia UI | 06 | Android/iOS |
| AT-06 | MCP handshake STDIO i wszystkie zachowane narzędzia | 04/06 | Linux (bez urządzenia), macOS + sim |
| AT-07 | Błąd w YAML i crash drivera — komunikaty lokalne, brak ErrorReporter | 03/06 | Linux |
| AT-08 | Komendy Cloud/AI niedostępne lub kontrolowany błąd walidacji bez requestu | 01/02 | Linux |
| AT-09 | Działanie paczki po odcięciu publicznego internetu | 05/06/08 | Linux CI, macOS |
| AT-10 | Zawartość paczki bez simulator-server i innych zakazanych binariów | 00B/05 | CI |
| AT-11 | Instalacja i uruchomienie z pliku pobranego z GitHub Release | 08 | macOS arm64 |

## 6. Ryzyka

| ID | Ryzyko | Wpływ | Mitigacja |
|---|---|---|---|
| R-1 | Licencja simulator-server (L-01) | bloker Release | STEP-00B/04, test zawartości |
| R-2 | Frameworki Apple w prekompilowanych ZIP iOS (L-04) | bloker Release (iOS) | D-5: build lokalny z źródła |
| R-3 | GraalJS `truffle-enterprise` GFTC (L-03) | bloker do decyzji | D-6: artefakty community |
| R-4 | Brak urządzeń w CI → fałszywie zielone testy | jakość | jawne `skipped`, testy z `FakeDriver`, testy ręczne opisane w handover |
| R-5 | Rozjazd z upstream (cherry-pick poprawek sterowników) | utrzymanie | baza = tag; przyszłe merge z upstream przez osobne PR „sync upstream vX.Y.Z” z ponownym audytem |
| R-6 | Kolizja z oficjalną instalacją Maestro (`maestro`, `~/.maestro`) | UX/bezpieczeństwo | D-2 |
| R-7 | Znak towarowy „Maestro” | prawny | D-1 |
| R-8 | `debug.keystore` w repo — APK sterownika podpisane publicznie znanym kluczem debug (stan upstream) | bezpieczeństwo (niskie; APK testowe) | opisać w ograniczeniach; ewentualnie własny klucz po 0.1.0 |
| R-9 | Środowisko sesji agenta bez dostępu do `dl.google.com` (Google Maven, Android SDK) | lokalna walidacja | testy pełne w GitHub Actions; lokalnie obejście analityczne (handover STEP-00) |
| R-10 | Koszt minut macOS w repo prywatnym | budżet | D-9 |
