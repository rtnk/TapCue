# Handover — STEP-00B: Legal containment (no simulator-server in build)

| Pole | Wartość |
|---|---|
| Krok | STEP-00B — **zakończony, w review** |
| PR | PR-00B: https://github.com/rtnk/TapCue/pull/3 |
| Gałąź | `step-00b-legal-containment` |
| Gałąź docelowa | `main` |
| Baza | `main` @ `27e8b55` (merge PR-01 #2) |
| Commit SHA | ostatni commit gałęzi `step-00b-legal-containment` (patrz PR) |

## 1. Co zrobiono

Kolejność (D-8): STEP-01 wykonano bezpośrednio po PR-00. STEP-00B właściciel zlecił po merge PR-01 (2026-10-23),
przed STEP-02. Cel: żaden build, test ani run nie pobiera, nie pakuje i nie uruchamia simulator-server
(`docs/LICENSE-AUDIT.md` L-01, licencja pozwala na to tylko w oficjalnych wydaniach Maestro).

| Obszar | Zmiana |
|---|---|
| Build | `maestro-cli/build.gradle.kts`: usunięte taski `downloadSimulatorServer` (pobieranie z `github.com/mobile-dev-inc/simulator-server-releases`), `installMcpViewerDeps`, `buildMcpViewer` (npm), wpięcie w `processResources` i nieużywane importy; `gradle/libs.versions.toml`: usunięta wersja `simulatorServer` |
| Test JAR | task `test` w `maestro-cli` zależy od `jar` i przekazuje jego ścieżkę (`maestro.cli.jar`) do testów |
| `maestro mcp` | `McpCommand` nie startuje `McpViewerServer`; `--no-viewer` i `--viewer-port` zostają jako **ukryte no-op** (konfiguracje klientów MCP często przekazują `--no-viewer`; `--viewer-port` daje ostrzeżenie na stderr, nie na stdout JSON-RPC) |
| Narzędzia MCP | `open_maestro_viewer` niezarejestrowane, `OpenMaestroViewerTool.kt` usunięty; `runMaestroMcpServer()` bez `viewerUrl` (bez `withViewerHint`) |
| Runtime | `Dependencies.installSimulatorServer()`/`simulatorServerBinary()` usunięte; `McpViewerServer` (już nieosiągalny) w miejscu startu binarium zwraca kontrolowany błąd „Device streaming is not available…” |
| CI | `ci-baseline.yml`: `./gradlew test --continue` bez flag `-x …` (taski nie istnieją, więc flagi przerwałyby build); strażnik `scripts/fork/check-no-simulator-server.sh` bez zmian |
| Dokumenty | nota w `AGENTS.md` (bez flag `-x`), `maestro-cli/src/main/java/maestro/cli/mcp/README.md` (Viewer niedostępny), definicje ewaluacji `src/test/mcp/*.yaml` bez `open_maestro_viewer`, ROADMAP, LICENSE-AUDIT (L-01), NETWORK-AUDIT (B-01, B-02, N-18), ARCHITECTURE-LOCAL §5, CHANGELOG, handover STEP-01 (wynik CI PR-01, nota o nieaktualnym prompcie) |

**Dodane testy:** `maestro-cli/src/test/kotlin/maestro/cli/CliJarContentsTest.kt` (5):
- zbudowany JAR CLI nie zawiera `deps/simulator-server/**`, `mcp-viewer/**` ani plików `simulator-server*`;
  test sprawdza też, że zawiera `maestro/cli/AppKt.class` i `deps/applesimutils`, więc nie przejdzie na pustym archiwum;
- classpath testów nie ma `deps/simulator-server`, `simulator-server-version.txt`, `mcp-viewer/index.html`;
- `mcp --no-viewer --viewer-port 9999` jest akceptowane;
- pomoc `mcp` nie wspomina Viewera;
- klasa `OpenMaestroViewerTool` nie istnieje.

**Poza zakresem (STEP-04):** pakiet `mcp/viewer/` (`McpViewerServer`, `ViewerHint`, `McpViewerOrchestra`,
`McpViewerDriver`, `McpViewerEvents`, z czego ostatnie trzy nadal opakowują `Orchestra`/`Driver` w `RunTool`
i `McpMaestroSessionManager`), katalog `maestro-cli/mcp-viewer/` i `.nvmrc`, `Unpacker.unpackTree`
(nieużywane), ostateczny los ukrytych flag `--no-viewer`/`--viewer-port`.

## 2. Walidacja

Środowisko jak w STEP-01: Linux x86_64, JDK 17.0.20, brak Android SDK/Xcode/urządzeń, `dl.google.com`
zablokowane. Testy szły w wariancie analitycznym (handover STEP-00 §3 i STEP-01 §2, czyli bez `maestro-android`,
bez `google()`, z mirrorem Maven Central na GCS; te zmiany są tylko w kopii w scratchpadzie). **Bez flag `-x`.**

| Test | Komenda | Wynik |
|---|---|---|
| Wszystkie testy jednostkowe | `LANG=C.UTF-8 ./gradlew test --continue` (czysty `maestro-cli/build`) | **1267 testów, 1 porażka**: tylko znany T-01 (`IntegrationTest` Case 136, publiczny `eu.httpbin.org`) |
| ↳ `maestro-cli` | jw. | **PASS**, 134 (STEP-01: 129 + 5 `CliJarContentsTest`) |
| ↳ pozostałe moduły | jw. | **PASS**: `maestro-client` 301 (2 skipped), `maestro-orchestra` 446 (3 skipped), `maestro-orchestra-models` 51, `maestro-ios-driver` 47, `maestro-ios` 2, `maestro-utils` 45, `maestro-web` 18; `maestro-test` 223, z czego 1 FAIL (T-01) |
| Brak tasków L-01/npm | `./gradlew :maestro-cli:tasks --all \| grep -ciE "simulator\|viewer"`; log builda | **PASS**: 0 tasków; w logu builda brak `downloadSimulatorServer`/`npm`/`simulator-server-releases`; `maestro-cli/build/generated/simulator-server` nie powstaje |
| Detekt + dystrybucja | `./gradlew :maestro-orchestra:detektMain :maestro-client:detektMain :maestro-cli:installDist :maestro-cli:shadowJar` | **PASS** |
| Zawartość archiwów | `unzip -Z1` dla `maestro-cli-*.jar`, `maestro-cli-*-all.jar` (shadow), `install/maestro/lib/*.jar` | **PASS**: 0 wpisów `deps/simulator-server/` ani `mcp-viewer/` |
| Strażnik | `scripts/fork/check-no-simulator-server.sh` | **PASS** („OK: no simulator-server or mcp-viewer artifacts”) |
| Smoke MCP STDIO | `maestro mcp --viewer-port 9999` z pustym `HOME`; STDIN: `initialize`, `notifications/initialized`, `tools/list`, potem EOF | **PASS**: stdout to wyłącznie 2 ramki JSON-RPC; narzędzia: `cheat_sheet`, `inspect_screen`, `list_devices`, `run`, `take_screenshot`; na stderr ostrzeżenie o `--viewer-port`, brak `mcp_viewer_ready`; EOF na STDIN kończy proces (kod 0); `HOME` pozostał pusty (nic nie rozpakowano) |
| CI GitHub Actions (`ci-baseline.yml`, pełne repo) | PR-00B | wynik w PR |
| Smoke z urządzeniem, Swift | — | **NOT RUN**: brak SDK/Xcode/macOS |

Jak powtórzyć:

```bash
./gradlew :maestro-orchestra:detektMain :maestro-client:detektMain
./gradlew test --continue
scripts/fork/check-no-simulator-server.sh
```

## 3. Ryzyka, blokery, znane problemy

- **Zmiana zachowania `maestro mcp`:** Viewer (lokalny podgląd urządzenia w przeglądarce) nie jest dostępny.
  Narzędzia MCP działają bez zmian.
- **Flagi `-x downloadSimulatorServer …` przerywają teraz build** („Task … not found”). Stare prompty
  (handover STEP-00 §7/§8, STEP-01 §6) je zawierają; STEP-01 §6 ma notę, aktualny prompt jest niżej.
- Kod Viewera nadal się kompiluje (martwy kod) do STEP-04; do tego czasu `RunTool` używa `McpViewerOrchestra`.
- `AdbSocketTimeoutTest` (upstream, `maestro-client`) bywa niestabilny na runnerach (PR-01).
- T-01 (`IntegrationTest` Case 136) pada bez internetu, do przepisania w STEP-03B.

## 4. Warunek rozpoczęcia następnego kroku

PR-00B **scalony** do `main`, CI `ci-baseline.yml` zielone na `main`.

## 5. Następny krok

**STEP-02: Remove Remote AI (PR-02).** Zakres w `docs/ROADMAP-0.1.md` §3 „STEP-02”.

## 6. PROMPT DLA NASTĘPNEGO AGENTA (STEP-02)

```text
Jesteś agentem kodującym w repozytorium rtnk/TapCue (fork mobile-dev-inc/Maestro → „Maestro Local”).
Nie masz pamięci poprzednich sesji. Zanim cokolwiek zmienisz:

1. git fetch origin && git checkout main && git pull origin main. Potwierdź przez GitHub, że PR-01
   („STEP-01: Remove Cloud CLI”) i PR-00B („STEP-00B: Legal containment …”) są MERGED. Jeśli nie —
   zatrzymaj się i zgłoś to.
2. Przeczytaj: AGENTS.md (nota forka), docs/ROADMAP-0.1.md (§1, §2, STEP-02), docs/handovers/STEP-01.md,
   docs/handovers/STEP-00B.md, docs/NETWORK-AUDIT.md (N-07, N-08, N-09, N-10, N-22),
   docs/ARCHITECTURE-LOCAL.md §5.
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
   Nie usuwaj telemetrii (STEP-03) ani kodu Viewera (STEP-04) poza tym, co niezbędne do kompilacji — opisz to w PR.
4. Gradle uruchamiaj BEZ flag -x downloadSimulatorServer/installMcpViewerDeps/buildMcpViewer (od STEP-00B
   te taski nie istnieją). Nie przywracaj ich. Jeśli środowisko blokuje dl.google.com, użyj wariantu
   analitycznego z docs/handovers/STEP-00.md §3 (bez commitowania; przy HTTP 429 z Maven Central — mirror
   jak w docs/handovers/STEP-01.md §2) i polegaj na CI GitHub Actions (ci-baseline.yml).
5. Testy: regresja parsera (dawne komendy AI → błąd walidacji), IntegrationTest (maestro-test), lokalne
   asercje (assertVisible, assertTrue), test grafu: runtimeClasspath maestro-cli bez maestro-ai; testy
   maestro-cli (w tym LocalOnlyCliTest, CliJarContentsTest), maestro-client, maestro-orchestra,
   maestro-orchestra-models, maestro-test i scripts/fork/check-no-simulator-server.sh.
6. Zaktualizuj docs/ROADMAP-0.1.md (status), docs/NETWORK-AUDIT.md (N-07, N-08, N-09, N-10, N-22),
   docs/LICENSE-AUDIT.md (jeśli dotyczy), docs/ARCHITECTURE-LOCAL.md §5, CHANGELOG.md (sekcja forka),
   dodaj docs/handovers/STEP-02.md wg szablonu STEP-00B.md (z promptem dla STEP-03).
7. Utwórz PR „STEP-02: Remove Remote AI” do main (cel, zakres, usunięte/dodane/zachowane, pliki, wyniki
   testów z komendami — tylko faktycznie uruchomione, audyt sieci, ryzyka, link do handover, checklista,
   prompt dla STEP-03). Bez auto-merge. Doprowadź CI PR do zielonego.
8. Zakończ odpowiedzią: krok, URL PR, walidacja PASS/FAIL/NOT RUN, ryzyka, warunki następnego kroku,
   prompt na kolejną sesję. Nie zaczynaj STEP-03.
```
