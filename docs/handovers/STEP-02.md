# Handover — STEP-02: Remove Remote AI

| Pole | Wartość |
|---|---|
| Krok | STEP-02 — **zakończony, w review** |
| PR | PR-02: https://github.com/rtnk/TapCue/pull/4 |
| Gałąź | `step-02-remove-remote-ai` |
| Gałąź docelowa | `main` |
| Baza | `main` @ `f08d93a` (merge PR-00B #3) |
| Commit SHA | ostatni commit gałęzi `step-02-remove-remote-ai` (patrz PR) |

## 1. Co zrobiono

Cel: paczka i graf runtime nie zawierają zdalnych klientów ani modeli AI, a flow z dawnymi komendami AI
kończy się błędem walidacji bez żadnego żądania (NETWORK-AUDIT N-07, N-08, N-09, N-10, N-22).

| Obszar | Zmiana |
|---|---|
| Build | moduł `maestro-ai` usunięty (katalog, `include` w `settings.gradle.kts`, `api(project(":maestro-ai"))` w `maestro-orchestra`); nieużywany już wpis `clikt` w `libs.versions.toml` usunięty; `maestro-test`: jawne `testImplementation(libs.kotlinx.coroutines.core)` (wcześniej przechodnio przez `maestro-ai` → ktor) |
| Orchestra | bez `apiKey`, `AIPredictionEngine`, `onCommandGeneratedOutput` i obsługi trzech komend AI; `CommandMetadata.aiReasoning` usunięte; `OrchestraListener.onAIArtifactGenerated`, jego implementacja w `ArtifactsGenerator` i `BundleLayout.AI_ANALYSIS_DIR` (`ai-analysis/`) usunięte |
| Modele | `AssertNoDefectsWithAICommand`, `AssertWithAICommand`, `ExtractTextWithAICommand` i ich pola w `MaestroCommand` usunięte; `MaestroException.CloudApiKeyNotAvailable` (`maestro-client`) usunięte. `ArtifactKind.AI_ANALYSIS` **zostaje** — jest w publikowanym schemacie manifestu v1 (`artifact-manifest/v1.schema.json`) jako wartość zarezerwowana; nic jej nie emituje |
| YAML | `YamlAssertWithAI`, `YamlAssertNoDefectsWithAI`, `YamlExtractTextWithAI` i pola w `YamlFluentCommand` usunięte (schemat `FlowCommandSchema` jest generowany refleksją z `YamlFluentCommand`, więc też nie zawiera tych komend). `MaestroFlowParser`: nazwy `assertWithAI`, `assertNoDefectsWithAI`, `extractTextWithAI` (forma tekstowa i obiektowa, także w zagnieżdżonych `runFlow`/`repeat` itd.) → `ParseException` „AI Command Not Available: <komenda>” → `SyntaxError` (podklasa `ValidationError`) |
| CLI | `test --analyze`, `--api-key` usunięte (picocli: „Unknown option”); usunięte `insights/TestAnalysisManager.kt` (z `maybeNotify` wołanym w `App.kt` po każdej komendzie), `report/HtmlAITestSuiteReporter.kt`, `report/HtmlInsightsAnalysisReporter.kt`, `ai_report.css`, `tailwind.config.js`, `util/ScreenshotUtils.kt`, `FlowAIOutput`/`SingleScreenFlowAIOutput`/`saveSuggestions` (pliki `ai-*.json`) w `TestDebugReporter`, `ApiClient.analyze` z `AnalyzeResponse`/`Insight`, parametry `analyze`/`apiKey`/`aiOutput` w `TestRunner`, `MaestroCommandRunner`, `TestSuiteInteractor` (z nim `captureFullArtifacts`, które ustawiało tylko `--analyze`), przykład `AssertWithAICommand` w demo `AnsiResultView.main` |
| Dokumenty | ROADMAP (status, realizacja), NETWORK-AUDIT (wersja 3: N-07, N-08, N-09, N-10, N-22 usunięte; N-34), LICENSE-AUDIT (wersja 2), ARCHITECTURE-LOCAL §5, CHANGELOG |

Komunikat błędu jest po angielsku, jak pozostałe błędy parsera (roadmapa podaje go po polsku jako opis):

```
AI Command Not Available: assertWithAI at <plik>:4:15
  `assertWithAI` is an AI command. AI commands are not available in Maestro Local: they relied on a
  remote AI service, which this build does not include.

  Remove the command, or replace it with a local assertion such as `assertVisible` or `assertTrue`.
```

**Dodane testy:**
- `maestro-orchestra` `RemovedAiCommandsTest` (8): każda dawna komenda AI w formie tekstowej i obiektowej oraz
  w `runFlow` → `SyntaxError`/`ValidationError` z „AI Command Not Available”; nazwa komendy w tytule;
  `assertVisible` i `assertTrue` nadal parsują się do `AssertConditionCommand`.
- `maestro-orchestra` `WorkspaceExecutionPlannerErrorsTest`: przypadki `e025_ai_command_assertWithAI`,
  `e026_ai_command_assertNoDefectsWithAI`, `e027_ai_command_extractTextWithAI_in_subflow` (snapshot pełnego
  komunikatu przez `WorkspaceExecutionPlanner.plan`, czyli ścieżkę `maestro test`).
- `maestro-cli` `NoRemoteAiTest` (6): `test --help` bez `--analyze`/`--api-key`/„AI”; obie opcje → „Unknown option”;
  klasy AI (`maestro.ai.*`, modele, YAML, CLI) nieobecne; zasoby AI nieobecne; `ApiClient` bez `analyze`;
  classpath testów (nadzbiór `runtimeClasspath` CLI) bez `maestro-ai`.

**Usunięte testy:** `HtmlAITestSuiteReporterTest` (1), `TestDebugReporterTest` „saveSuggestions …” (1),
`ArtifactsGeneratorTest` „AI screenshot is recorded as AI_ANALYSIS …” (1) — dotyczyły usuniętego kodu.

**Poza zakresem:** `CommandArgsSanitizer` (redakcja `--api-key`/`--apiKey` w zdarzeniach analityki — STEP-03 razem
z analityką), `ChatCommand` (ukryte `maestro chat`; w upstream już tylko komunikat, bez sieci; jego ukryte `--api-key`
jest ignorowane), `ChangeLogUtilsTest` (cytuje changelog upstream z nazwami komend AI — STEP-03 razem z
`ChangeLogUtils`), konfiguracja `notifications.slack.apiKey` w `WorkspaceConfig` (parsowanie konfiguracji Cloud,
bez sieci), Viewer (`mcp/viewer/` — STEP-04).

## 2. Walidacja

Środowisko: Linux x86_64, JDK 17.0.20 (doinstalowany przez `apt`), brak Android SDK/Xcode/urządzeń,
`dl.google.com` zablokowane. Lokalnie — **wariant analityczny** (handover STEP-00 §3: kopia w scratchpadzie bez
`maestro-android` i `google()`) z mirrorem Maven Central jak w STEP-01 §2. **Bez flag `-x`.**

| Test | Komenda | Wynik |
|---|---|---|
| Wszystkie testy jednostkowe | `LANG=C.UTF-8 ./gradlew test --continue` | **1281 testów, 1 porażka**: tylko znany T-01 (`IntegrationTest` Case 136, publiczny `eu.httpbin.org`) |
| ↳ `maestro-cli` | jw. | **PASS**, 138 (STEP-00B: 134; −2 usunięte, +6 `NoRemoteAiTest`), w tym `LocalOnlyCliTest`, `CliJarContentsTest` |
| ↳ `maestro-orchestra` | jw. | **PASS**, 456 (3 skipped; STEP-00B: 446; −1 usunięty, +8 `RemovedAiCommandsTest`, +3 `e025`–`e027`) |
| ↳ `maestro-test` | jw. | 223, **1 FAIL** (T-01); `IntegrationTest` poza tym zielony — w tym `assertVisible`/`assertTrue` z `FakeDriver` |
| ↳ pozostałe | jw. | **PASS**: `maestro-client` 301 (2 skipped), `maestro-orchestra-models` 51, `maestro-ios-driver` 47, `maestro-ios` 2, `maestro-utils` 45, `maestro-web` 18 |
| Detekt + dystrybucja | `./gradlew :maestro-orchestra:detektMain :maestro-client:detektMain :maestro-cli:installDist :maestro-cli:shadowJar` | **PASS** |
| Graf zależności | `./gradlew :maestro-cli:dependencies --configuration runtimeClasspath` na `main` i na gałęzi, diff | **PASS**: znika tylko `project :maestro-ai` i `com.github.ajalt.clikt:clikt(-jvm):4.2.2` |
| Zawartość archiwów | `unzip -Z1` dla `maestro-cli-*.jar`, `maestro-cli-*-all.jar`, `install/maestro/lib/*.jar`; `grep` po rozpakowanym shadow JAR | **PASS**: 0 wpisów `maestro/ai/`, `ai_report.css`, `tailwind.config.js`, schematów AI; brak `api.openai.com`, `api.anthropic.com`, `cdn.tailwindcss.com`, `/v2/analyze`. `api.copilot.mobile.dev` zostaje w `EnvUtils` (N-34, STEP-03) i `CheatSheetTool` (N-15, STEP-04) |
| Strażnik | `scripts/fork/check-no-simulator-server.sh` | **PASS** |
| Smoke CLI (pusty `HOME`) | `maestro check-syntax ai.yaml`, `maestro test ai.yaml` (flow z `assertWithAI`), `maestro test --analyze ai.yaml` | **PASS**: „AI Command Not Available: assertWithAI at …:4:15”, kod 1 (`test` kończy się przed szukaniem urządzenia); `--analyze` → „Unknown option”, kod 2; `HOME` pusty. Ruch sieciowy w smoke nie był rejestrowany (analityka/sprawdzanie wersji — STEP-03) |
| CI GitHub Actions (`ci-baseline.yml`, pełne repo) | PR-02 | wynik w PR |
| Smoke z urządzeniem, Swift | — | **NOT RUN**: brak SDK/Xcode/macOS |

Jak powtórzyć:

```bash
./gradlew :maestro-orchestra:detektMain :maestro-client:detektMain
./gradlew test --continue
./gradlew :maestro-cli:dependencies --configuration runtimeClasspath | grep -c maestro-ai   # 0
scripts/fork/check-no-simulator-server.sh
```

Nowy przypadek błędu parsera: `GENERATE_ERRORS=true ./gradlew :maestro-orchestra:test --tests
"maestro.orchestra.workspace.WorkspaceExecutionPlannerErrorsTest"` (instrukcja w nagłówku tego testu).

## 3. Ryzyka, blokery, znane problemy

- **Zmiana zachowania flow:** flow z `assertWithAI`/`assertNoDefectsWithAI`/`extractTextWithAI` nie przechodzi
  walidacji (w upstream bez tokenu Cloud kończył się błędem `CloudApiKeyNotAvailable` dopiero w chwili wykonania
  komendy, a domyślne `optional: true` zamieniało go w ostrzeżenie). Teraz cały flow jest odrzucany — świadomie,
  bo cichy skip zmieniałby znaczenie testu.
- **API biblioteki:** usunięte publiczne typy/parametry (`Orchestra(apiKey=…, onCommandGeneratedOutput=…)`,
  komendy AI w `MaestroCommand`, `CloudApiKeyNotAvailable`, `onAIArtifactGenerated`). Konsumenci `maestro-orchestra`
  jako biblioteki muszą się dostosować (w repo dostosowane wszystkie wywołania).
- `MaestroCommand` zserializowany przez upstream z polami `assertWithAICommand` itp. nie zdeserializuje się
  do komendy (pole nieznane) — w forku nie ma takiego wejścia (brak Cloud).
- `AdbSocketTimeoutTest` (upstream, `maestro-client`) bywa niestabilny na runnerach (PR-01).
- T-01 (`IntegrationTest` Case 136) pada bez internetu — do przepisania w STEP-03B.

## 4. Warunek rozpoczęcia następnego kroku

PR-02 **scalony** do `main`, CI `ci-baseline.yml` zielone na `main`.

## 5. Następny krok

**STEP-03: Remove Telemetry, Error Upload and Update Checks (PR-03).** Zakres w `docs/ROADMAP-0.1.md` §3 „STEP-03”.

## 6. PROMPT DLA NASTĘPNEGO AGENTA (STEP-03)

```text
Jesteś agentem kodującym w repozytorium rtnk/TapCue (fork mobile-dev-inc/Maestro → „Maestro Local”).
Nie masz pamięci poprzednich sesji. Zanim cokolwiek zmienisz:

1. git fetch origin && git checkout main && git pull origin main. Potwierdź przez GitHub, że PR-02
   („STEP-02: Remove Remote AI”) jest MERGED. Jeśli nie — zatrzymaj się i zgłoś to.
2. Przeczytaj: AGENTS.md (nota forka), docs/ROADMAP-0.1.md (§1, §2 — zwłaszcza D-2, STEP-03),
   docs/handovers/STEP-02.md, docs/NETWORK-AUDIT.md (N-01, N-03, N-04, N-05, N-06, N-17, N-34, N-35 i pozostałe
   z krokiem 03), docs/ARCHITECTURE-LOCAL.md §2 i §5.
3. Wykonaj WYŁĄCZNIE STEP-03 — Remove Telemetry, Error Upload and Update Checks — na gałęzi
   step-03-remove-telemetry: usuń analytics/ (PostHog, AnalyticsStateManager, CommandArgsSanitizer, zdarzenia)
   i wszystkie wywołania Analytics.*; zależność libs.posthog; util/ErrorReporter.kt; util/ScreenReporter.kt
   (+ wywołanie w shutdown hooku); update/Updates.kt, util/ChangeLogUtils.kt (+ ChangeLogUtilsTest) i blok
   aktualizacji w App.kt; DownloadSamplesCommand; SystemInformationInterceptor; resztę api/ApiClient.kt
   i EnvUtils.BASE_API_URL. Raport HTML bez CDN (inline CSS, otwiera się offline). start-device bez
   automatycznego pobierania obrazów systemu (jasny komunikat z poleceniem sdkmanager). Katalog stanu —
   tylko jeśli właściciel rozstrzygnął D-2. Zachowaj lokalne logi (maestro.log, DebugLogStore), stack trace na
   stderr, raporty. Nie ruszaj Viewera/cheat_sheet (STEP-04) ani GraalJsHttp/web (STEP-03B) poza tym, co
   niezbędne do kompilacji — opisz to w PR.
4. Gradle uruchamiaj BEZ flag -x downloadSimulatorServer/installMcpViewerDeps/buildMcpViewer (te taski nie
   istnieją od STEP-00B). Jeśli środowisko blokuje dl.google.com, użyj wariantu analitycznego z
   docs/handovers/STEP-00.md §3 (bez commitowania; mirror Maven Central jak w docs/handovers/STEP-01.md §2)
   i polegaj na CI GitHub Actions (ci-baseline.yml). Po usunięciu modułów/zależności sprawdź, czy inne moduły
   nie korzystały z nich przechodnio (w STEP-02 maestro-test potrzebował jawnego kotlinx-coroutines).
5. Testy: wymuszony wyjątek komendy → stack trace lokalnie, zero połączeń (ProxySelector/MockWebServer, który
   failuje przy każdej próbie połączenia — wzór w LocalOnlyCliTest); --version/--help bez wątków sieciowych;
   skan JAR na zakazane hosty (api.copilot.mobile.dev, posthog — poza znanym N-15 w CheatSheetTool do STEP-04);
   runtimeClasspath CLI bez com.posthog; testy maestro-cli (LocalOnlyCliTest, CliJarContentsTest, NoRemoteAiTest),
   maestro-client, maestro-orchestra, maestro-orchestra-models, maestro-test i scripts/fork/check-no-simulator-server.sh.
6. Zaktualizuj docs/ROADMAP-0.1.md (status), docs/NETWORK-AUDIT.md, docs/LICENSE-AUDIT.md (posthog),
   docs/ARCHITECTURE-LOCAL.md §5, CHANGELOG.md (sekcja forka), dodaj docs/handovers/STEP-03.md wg szablonu
   STEP-02.md (z promptem dla STEP-03B).
7. Utwórz PR „STEP-03: Remove telemetry, error upload and update checks” do main (cel, zakres,
   usunięte/dodane/zachowane, pliki, wyniki testów z komendami — tylko faktycznie uruchomione, audyt sieci,
   ryzyka, link do handover, checklista, prompt dla STEP-03B). Bez auto-merge. Doprowadź CI PR do zielonego.
8. Zakończ odpowiedzią: krok, URL PR, walidacja PASS/FAIL/NOT RUN, ryzyka, warunki następnego kroku,
   prompt na kolejną sesję. Nie zaczynaj STEP-03B.
```
