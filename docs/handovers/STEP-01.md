# Handover — STEP-01: Remove Cloud CLI

| Pole | Wartość |
|---|---|
| Krok | STEP-01 — **zakończony, w review** |
| PR | PR-01: https://github.com/rtnk/TapCue/pull/2 |
| Gałąź | `step-01-remove-cloud-cli` |
| Gałąź docelowa | `main` |
| Baza | `main` @ `4f0425d` (merge PR-00 #1) |
| Commit SHA | ostatni commit gałęzi `step-01-remove-cloud-cli` (patrz PR) |

## 1. Co zrobiono

Warunek startu sprawdzony: PR-00 (#1) **MERGED** 2026-10-23 przez właściciela; `main` zawiera
`docs/ROADMAP-0.1.md`. D-8: właściciel zlecił STEP-01 bezpośrednio, więc STEP-00B go nie poprzedził
(STEP-00B pozostaje `TODO`, ROADMAP §2). Gradle uruchamiany wyłącznie z
`-x downloadSimulatorServer -x installMcpViewerDeps -x buildMcpViewer`.

**Usunięte (kod produkcyjny):**

| Obszar | Pliki / elementy |
|---|---|
| Komendy CLI | `CloudCommand`, `LoginCommand`, `LogoutCommand`, `ListCloudDevicesCommand`, `StudioCommand` (+ wpisy w `App.kt`), `util/StudioDownload.kt` |
| Klient Cloud | `cloud/CloudInteractor.kt`, `auth/Auth.kt`, w `api/ApiClient.kt`: `getAuthUrl`, `exchangeToken`, `isAuthTokenValid`, `uploadStatus`, `render`, `getRenderState`, `upload` (+ retry/progress), `startTrial`, `listCloudDevices`, `getUser`, `getOrg`, `getOrgs`, `switchOrg`, `getProjects`, `describeRun`, `domain` i klasy DTO (`UploadResponse`, `UploadStatus`, `RunDetails`, `RenderState`, `UserResponse`, `OrgResponse`, …) |
| `record` | `graphics/RemoteVideoRenderer.kt` — zawsze `LocalVideoRenderer`; `--local` = ukryty no-op; plik wyjściowy dozwolony bez `--local` |
| Promocje / linki | `promotion/PromotionStateManager.kt` i komunikaty w `TestCommand`; linki `app.maestro.dev` w `view/TestSuiteStatusView.kt` (+ `uploadUrl`/`flowUrl`/`projectUrl`, `UploadDetails`, powody anulowania Cloud); `FlowStatus.from(UploadStatus)`; komunikat `list-devices` o `list-cloud-devices`; opis `start-device`; link do Studio w `query` |
| Raporty | pola `cloudUploadId`, `cloudUploadUrl`, `appBinaryId`, `cloudRunId`, `cloudRunUrl` w `TestExecutionSummary`; właściwości JUnit `cloud.*`; sekcje „Maestro Cloud” w HTML |
| `TestCommand` | pola `client`/`auth`/`authToken`, opcja `--api-url`; do `TestRunner` trafia `apiKey = null` (parametr zostaje do STEP-02) |
| Analytics | `identifyAndUpdateState`, `identifyUserIfNeeded`, `AnalyticsStateManager.updateState`/`addOrgStatusProperties`, zdarzenia Cloud/Auth/Trial w `PostHogEvents.kt` (reszta analityki — STEP-03) |
| MCP | narzędzia `list_cloud_devices`, `run_on_cloud`, `get_cloud_run_status`, `describe_cloud_run` (przeniesione z STEP-04, bo ich zaplecze — `ApiKey`, metody Cloud `ApiClient` — zostało usunięte); sekcja „Cloud workflow” i „Auth” w `INSTRUCTIONS`; definicje ewaluacji w `src/test/mcp/*.yaml` |
| `maestro-client` | `maestro/auth/ApiKey.kt`, `maestro/mockserver/MockInteractor.kt`; parametr `isStudio` (nawigacja do `https://maestro.mobile.dev`) w `CdpWebDriver`, `WebDriver`, `Maestro.web`, `MaestroSessionManager` |

**Usunięte testy (razem z testowanymi klasami):** `ApiClientAndroidSystemImageTest`, `ApiClientUploadRetryTest`,
`CloudInteractorTest`, `CloudCommandTest`, `DescribeCloudRunToolTest`, zasoby `test/resources/workspaces/cloud_test/**`,
przypadki `HTML - Cloud run is rendered as a hyperlink…`, `XML - Cloud upload and run metadata are included`
i fixture `testWithCloudMetadata`.

**Dodane testy:** `maestro-cli/src/test/kotlin/maestro/cli/LocalOnlyCliTest.kt`:
`cloud`/`login`/`logout`/`list-cloud-devices`/`studio` → „Unmatched argument” (niezerowy kod), pomoc główna
bez Cloud, `test --help` bez `--api-url`/„Cloud”/`mobile.dev`, `record --local` akceptowane i ukryte,
`TestCommand` nie przechowuje tokenu z `~/.mobiledev/authtoken` ani `MAESTRO_CLOUD_API_KEY` i nie ma pól typu
`ApiClient`/`Auth`, konstrukcja drzewa komend nie tworzy `~/.mobiledev` i nie wykonuje żądań (rejestrujący
`ProxySelector`), klasy Cloud nieobecne na classpath, `ApiClient` bez metod Cloud.
`McpServerTest`: lista narzędzi bez Cloud + test „instructions do not point to Maestro Cloud”.

**Zachowane:** lokalne `test` (pojedynczy flow, workspace, `--continuous`), raporty JUnit/HTML/HTML-detailed,
sharding (`--shards`, `--shard-split`, `--shard-all`), artefakty (`--debug-output`, `--test-output-dir`,
`--flatten-debug-output`), `record` (lokalnie), MCP lokalne (`list_devices`, `inspect_screen`, `take_screenshot`,
`run`, `cheat_sheet`, `open_maestro_viewer`).

**Celowo NIE ruszane (kolejne kroki):** `--analyze`/`--api-key`, `TestAnalysisManager` (teraz bez
`CloudInteractor`: token tylko z jawnego `--api-key`, URL `EnvUtils.BASE_API_URL`), `apiKey` w
`TestRunner`/`MaestroCommandRunner`/`Orchestra`, `MaestroException.CloudApiKeyNotAvailable` — STEP-02;
PostHog, `ErrorReporter`, `ScreenReporter`, `Updates`, `ChangeLogUtils`, reszta `ApiClient`
(`sendErrorReport`, `sendScreenReport`, `getLatestCliVersion`), `EnvUtils.BASE_API_URL`,
`SystemInformationInterceptor` — STEP-03; `cheat_sheet` (zdalny), Viewer, link `docs.maestro.dev` w MCP — STEP-04;
`README.md` upstream (sekcje Studio/Cloud), stopka `maestro.mobile.dev` w `SkiaFrameRenderer`, ukryta komenda
`chat` (tylko komunikat, bez sieci) — STEP-05/08.

## 2. Walidacja

Środowisko sesji: Linux x86_64, JDK 17.0.20 (doinstalowany przez `apt`), **brak Android SDK, Xcode,
emulatora i symulatora**; `dl.google.com` zablokowane (403 z proxy), Maven Central odpowiadał chwilowo
HTTP 429. Lokalne przebiegi — **wariant analityczny** z `docs/handovers/STEP-00.md` §3 (kopia w scratchpadzie
bez `maestro-android`, bez `google()`) z jedną dodatkową, niezatwierdzoną zmianą: repozytorium
`https://maven-central.storage-download.googleapis.com/maven2/` (mirror Maven Central) przed `mavenCentral()`.

| Test | Komenda | Wynik |
|---|---|---|
| Kompilacja (main + test) | `./gradlew :maestro-cli:compileTestKotlin :maestro-client:compileTestKotlin -x downloadSimulatorServer -x installMcpViewerDeps -x buildMcpViewer` | **PASS** (bez nowych ostrzeżeń) |
| Wszystkie testy jednostkowe (wariant analityczny) | `LANG=C.UTF-8 ./gradlew test --continue -x downloadSimulatorServer -x installMcpViewerDeps -x buildMcpViewer` | **1262 testy, 1 porażka** — tylko T-01 (niżej) |
| ↳ `maestro-cli` | jw. | **PASS** — 129 testów (STEP-00: 160; −40 usuniętych testów Cloud, +8 `LocalOnlyCliTest`, +1 `McpServerTest`) |
| ↳ `maestro-client` | jw. | **PASS** — 301 (2 skipped, jak w STEP-00) |
| ↳ `maestro-orchestra` | jw. | **PASS** — 446 (3 skipped) |
| ↳ `maestro-test` | jw. | 223, **1 FAIL**: `IntegrationTest` Case 136 — znany T-01 (publiczny `eu.httpbin.org`, proxy 403); niezwiązany ze zmianą, ten sam wynik w STEP-00 |
| ↳ `maestro-orchestra-models` / `maestro-ios-driver` / `maestro-ios` / `maestro-utils` / `maestro-web` | jw. | **PASS** — 51 / 47 / 2 / 45 / 18 |
| Detekt | `./gradlew :maestro-orchestra:detektMain :maestro-client:detektMain` | **PASS** |
| Strażnik simulator-server | `scripts/fork/check-no-simulator-server.sh` | **PASS** — „OK: no simulator-server or mcp-viewer artifacts”; `maestro-cli/build/generated/simulator-server` nie istnieje |
| Smoke zbudowanego CLI | `./gradlew :maestro-cli:installDist -x …`; `maestro cloud\|login\|logout\|list-cloud-devices\|studio` z pustym `HOME` | **PASS** — każda: „Unmatched argument at index 0: '…'”, kod 2; `maestro test --help` bez `--api-url`/„cloud”; `HOME` pozostał pusty |
| `maestro-android`, CI GitHub Actions | `ci-baseline.yml` na PR-01 | wynik w PR (lokalnie NOT RUN — `dl.google.com`) |
| Smoke Android/iOS (`test`, `record` z urządzeniem) | — | **NOT RUN** — brak SDK/Xcode/urządzeń |
| Swift (`MaestroDriverLib`, XCTest runner) | — | **NOT RUN** — wymaga macOS (D-9); kod Swift nie był zmieniany |

Jak powtórzyć (środowisko z Google Maven i Android SDK, np. GitHub Actions `ubuntu-latest`):

```bash
./gradlew :maestro-orchestra:detektMain :maestro-client:detektMain
./gradlew test --continue -x downloadSimulatorServer -x installMcpViewerDeps -x buildMcpViewer
scripts/fork/check-no-simulator-server.sh
```

## 3. Ryzyka, blokery, znane problemy

- **Zmiana zachowania `record`:** dotąd domyślnie wysyłał nagranie do zdalnego renderera; teraz zawsze
  renderuje lokalnie (Skia, ffmpeg nie jest wymagany — `jcodec`). Na Linuksie bez urządzenia nietestowane.
- **`--analyze` bez `--api-key`** kończy się teraz błędem `--analyze requires --api-key` (wcześniej
  odpalał logowanie w przeglądarce). Całość znika w STEP-02.
- Flow z komendami AI (`assertWithAI` itp.) przy dowolnym tokenie kończą się lokalnym
  `CloudApiKeyNotAvailable` (żaden token nie trafia już do `Orchestra`) — bez żądania; walidacja parsera w STEP-02.
- STEP-00B (D-8) nadal otwarty — **Gradle tylko z flagami `-x …`**.
- Środowisko agenta: `dl.google.com` zablokowane (moduł `maestro-android` tylko w CI), Maven Central
  odpowiadał 429 — w lokalnym wariancie analitycznym użyto mirrora GCS (zmiana tylko w kopii w scratchpadzie).

## 4. Warunek rozpoczęcia następnego kroku

PR-01 **scalony** do `main`, CI `ci-baseline.yml` zielone na `main`. Właściciel decyduje, czy STEP-00B
idzie przed STEP-02 (rekomendacja: tak — D-8).

## 5. Następny krok

**STEP-02 — Remove Remote AI (PR-02)** — zakres i kryteria w `docs/ROADMAP-0.1.md` §3 „STEP-02”
(lub STEP-00B, jeśli właściciel tak zdecyduje — prompt w `docs/handovers/STEP-00.md` §8, z bazą = `main` po PR-01).

## 6. PROMPT DLA NASTĘPNEGO AGENTA (STEP-02)

```text
Jesteś agentem kodującym w repozytorium rtnk/TapCue (fork mobile-dev-inc/Maestro → „Maestro Local”).
Nie masz pamięci poprzednich sesji. Zanim cokolwiek zmienisz:

1. git fetch origin && git checkout main && git pull origin main. Potwierdź przez GitHub, że PR-01
   („STEP-01: Remove Cloud CLI”) jest MERGED. Jeśli nie — zatrzymaj się i zgłoś to. Sprawdź w ROADMAP §2
   (D-8), czy właściciel nie zlecił najpierw STEP-00B; jeśli tak i nie jest scalony — zatrzymaj się.
2. Przeczytaj: AGENTS.md (nota forka), docs/ROADMAP-0.1.md (§1, §2, STEP-02), docs/handovers/STEP-01.md,
   docs/NETWORK-AUDIT.md (N-07, N-08, N-09, N-10, N-22), docs/ARCHITECTURE-LOCAL.md §5.
3. Wykonaj WYŁĄCZNIE STEP-02 — Remove Remote AI — na gałęzi step-02-remove-remote-ai:
   usuń moduł maestro-ai (settings.gradle.kts, api(project(":maestro-ai")) w maestro-orchestra);
   w Orchestra — apiKey, AIPredictionEngine, obsługę komend AI i MaestroException.CloudApiKeyNotAvailable;
   w maestro-orchestra-models — AssertNoDefectsWithAICommand, AssertWithAICommand, ExtractTextWithAICommand
   i pola w MaestroCommand; w YAML — YamlAssertWithAI, YamlAssertNoDefectsWithAI, YamlExtractTextWithAI,
   wpisy w YamlFluentCommand, MaestroFlowParser i schemacie (yaml/schema/FlowCommandSchema.kt);
   w CLI — --analyze, --api-key, insights/TestAnalysisManager.kt, report/HtmlAITestSuiteReporter.kt,
   report/HtmlInsightsAnalysisReporter.kt, ai_report.css, tailwind.config.js, Defect w TestDebugReporter,
   AI w AnsiResultView, parametry apiKey/analyze w TestRunner/MaestroCommandRunner, ApiClient.analyze.
   Dawne komendy AI w YAML → ValidationError „komenda AI nie jest dostępna w Maestro Local” bez requestu.
   Nie usuwaj telemetrii (STEP-03) ani Viewera (STEP-04) poza tym, co niezbędne do kompilacji — opisz to w PR.
4. Gradle uruchamiaj ZAWSZE z: -x downloadSimulatorServer -x installMcpViewerDeps -x buildMcpViewer
   (licencja simulator-server, docs/LICENSE-AUDIT.md L-01), dopóki STEP-00B/04 nie usunie tych tasków.
   Jeśli środowisko blokuje dl.google.com, użyj wariantu analitycznego z docs/handovers/STEP-00.md §3
   (bez commitowania; przy HTTP 429 z Maven Central — mirror jak w docs/handovers/STEP-01.md §2)
   i polegaj na CI GitHub Actions (ci-baseline.yml).
5. Testy: regresja parsera (dawne komendy AI → błąd walidacji), IntegrationTest (maestro-test), lokalne
   asercje (assertVisible, assertTrue), test grafu: runtimeClasspath maestro-cli bez maestro-ai; testy
   maestro-cli (w tym LocalOnlyCliTest), maestro-client, maestro-orchestra, maestro-orchestra-models,
   maestro-test i scripts/fork/check-no-simulator-server.sh.
6. Zaktualizuj docs/ROADMAP-0.1.md (status), docs/NETWORK-AUDIT.md (N-07, N-08, N-09, N-10, N-22),
   docs/LICENSE-AUDIT.md (jeśli dotyczy), docs/ARCHITECTURE-LOCAL.md §5, CHANGELOG.md (sekcja forka),
   dodaj docs/handovers/STEP-02.md wg szablonu STEP-01.md (z promptem dla STEP-03).
7. Utwórz PR „STEP-02: Remove Remote AI” do main (cel, zakres, usunięte/dodane/zachowane, pliki, wyniki
   testów z komendami — tylko faktycznie uruchomione, audyt sieci, ryzyka, link do handover, checklista,
   prompt dla STEP-03). Bez auto-merge. Doprowadź CI PR do zielonego.
8. Zakończ odpowiedzią: krok, URL PR, walidacja PASS/FAIL/NOT RUN, ryzyka, warunki następnego kroku,
   prompt na kolejną sesję. Nie zaczynaj STEP-03.
```
