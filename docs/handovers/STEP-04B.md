# Handover — STEP-04B: TapCue — nazwa, polecenie i katalogi

| Pole | Wartość |
|---|---|
| Krok | STEP-04B — w review |
| PR | PR-04B: https://github.com/rtnk/TapCue/pull/12 |
| Gałąź | `step-04b-tapcue` |
| Gałąź docelowa | `main` |
| Baza | `main` @ `358cfbf` (merge PR #11 — poprawka handovera STEP-03B; kod STEP-04 z #9) |
| Commit SHA | ostatni commit gałęzi `step-04b-tapcue` (patrz PR) |

## 1. Co zrobiono

Cel (D-1, D-2, R-6): fork nie koliduje z oficjalną instalacją Maestro ani nazwą polecenia, ani stanem.

| Obszar | Zmiana |
|---|---|
| Jedno źródło ścieżek | nowy `maestro.utils.TapCueDirs` w `maestro-utils`: `NAME = "tapcue"`, `PROJECT_DIR = ".tapcue"`, `LEGACY_PROJECT_DIR = ".maestro"`, `home()` = `~/.tapcue`, `state()` = `$XDG_STATE_HOME/tapcue` albo `home()`, `binaryDependency(name)` = `~/.tapcue/deps/<name>`. Leży w `maestro-utils`, a nie w `EnvUtils` (jak sugerowała ROADMAP), bo `DebugLogStore` (`maestro-client`) i `LocalSimulatorUtils` (`maestro-ios-driver`) nie zależą od CLI; `EnvUtils.xdgStateHome()` i nowe `EnvUtils.tapcueHome()` delegują do niego |
| Katalog stanu (D-2) | `~/.maestro` → `~/.tapcue` w: `SessionStore` (`sessions`), `Unpacker.binaryDependency` (`deps/applesimutils`), `LocalSimulatorUtils` (ścieżka `applesimutils` — musi się zgadzać z `Unpacker`), `MaestroSessionManager` i `RealIOSDeviceDriver` (build sterownika iOS dla urządzeń fizycznych), `DriverBuilder` (`TapCueDirs.home(sourceCodeRoot)`). `TestDebugReporter`: artefakty w `$XDG_STATE_HOME/tapcue/tests` lub `~/.tapcue/tests`, a z `--debug-output <dir>` — `<dir>/.tapcue/tests`. `DebugLogStore`: `AppDirs` z nazwą `tapcue` (np. `~/.cache/tapcue/logs`, `~/Library/Logs/tapcue`; wcześniej `maestro`/`mobile_dev`). Nazwy plików wewnątrz (`maestro.log`, `maestro-iphoneos-driver-build`) bez zmian — nie kolidują, bo leżą w katalogach TapCue. `~/.mobiledev` nie jest używany (od STEP-01). Stan z `~/.maestro` nie jest migrowany (sesje i `deps` odtwarzają się same, build sterownika — `tapcue driver-setup`) |
| `LegacyAnalyticsState` | **decyzja: usunięty** (z testem i wywołaniem w `App.main`). Po przejściu na `~/.tapcue` fork nie ma własnego `analytics.json` do sprzątania, a kasowanie pliku w `~/.maestro`/`$XDG_STATE_HOME/maestro` ingerowałoby w równoległą instalację oficjalnego Maestro (R-6: np. ponowne pytanie o zgodę). Wymaga potwierdzenia właściciela — D-2 mówiła „analytics.json jest usuwany” |
| Polecenie i nazwa (D-1) | picocli `name = "tapcue"` z nagłówkiem „TapCue - local mobile UI test automation, based on Maestro (Apache-2.0), not affiliated with mobile.dev”; `--version` → `tapcue <wersja>` + dopisek w drugiej linii (`versionText()`); `application.applicationName = "tapcue"` → `bin/tapcue`, `bin/tapcue.bat`, `build/install/tapcue/`. Wstawka sprawdzająca Javę 17 w `tapcue.bat` szukała `@rem Execute maestro` — teraz `@rem Execute $applicationName` (inaczej po zmianie nazwy znikałaby po cichu; sprawdzone w zbudowanym skrypcie) |
| Komunikaty | `maestro <komenda>` → `tapcue <komenda>` (`StartDeviceCommand`, `PickDeviceInteractor`, `RecordCommand`, nagłówek wideo `SkiaFrameRenderer`); „Maestro” jako nazwa produktu → „TapCue” (`TestCommand`/`RecordCommand` `--config`, `DriverCommand`, `McpCommand`, tytuł raportu HTML, log „TapCue Version”, `tapcue-recording.mp4`); „Maestro Local” → „TapCue” w błędach (`WebPlatformNotSupported`, `LocalNetworkPolicy.POLICY`, komenda AI w `MaestroFlowParser`) i ich testach/fixture'ach `e025`–`e027`. „Maestro” zostaje tam, gdzie nazywa format flow lub produkt upstream (Viewer, `check-syntax` „Maestro flow file”, opisy narzędzi MCP o selektorach) |
| MCP | `serverInfo.name` = `tapcue`; instrukcje: „TapCue MCP … (the Maestro flow format)”, „Save flows in the project's `.tapcue/` directory” (limit 2 KB zachowany); ściągawka: „TapCue flow cheat sheet”, akapit o `.tapcue/` i `mv .maestro .tapcue`; `src/test/mcp/` (B-08): oczekiwany nagłówek ściągawki, ścieżka `install/tapcue/bin/tapcue` |
| Katalog projektu `.tapcue/` (D-2) | **decyzja: ostrzeżenie, bez automatycznego odczytu** (rekomendacja ROADMAP). `LegacyProjectDir.warning` — w `tapcue test`, gdy któryś flow leży w katalogu `.maestro/` albo katalog roboczy ma `.maestro/` bez `.tapcue/`: „TapCue keeps project flows in `.tapcue/` and does not read `.maestro/` on its own. Rename the directory with: mv .maestro .tapcue”. Flow podany jawnie z `.maestro/` dalej się wykonuje. Kod CLI nigdy nie czytał `.maestro/` w projekcie niejawnie — była to konwencja upstream (`maestro test .maestro`) i ścieżka `--debug-output` |
| Fixture'y i skrypty | `e2e/demo_app/.maestro/` → `e2e/demo_app/.tapcue/` (108 plików, czyste `git mv`), `e2e/workspaces.txt`, `e2e/run_tests` (polecenie `tapcue`, `--debug-output=$HOME/.tapcue/tests/…`), `e2e/cli/test-cli.sh`/`.ps1` (`tapcue -v` zawiera wersję i dopisek), `e2e/alert-repro-swiftui/verify_preflight_suppressed.sh`, `e2e/demo_app/{CLAUDE,README}.md`, `e2e/README.md`, komentarz w `.github/actions/build-demo-app/action.yml`, `.claude/agents/diagnose-maestro-failure.md`. Skrypt deweloperski `./maestro` → `./tapcue`, `installLocally.sh` (→ `~/.tapcue/{bin,lib}`), `CONTRIBUTING.md`. E2E nie są uruchamiane w CI forka (STEP-06) |
| Dokumenty | ROADMAP (status, D-1/D-2, STEP-04B — realizacja i decyzje, R-6), ARCHITECTURE-LOCAL §3 (diagram, „Odrębny stan”) i §5, NETWORK-AUDIT (wersja 7 — bez zmian statusów), CHANGELOG (sekcja forka: nagłówek TapCue, trzy wpisy „Zmiana łamiąca”), AGENTS.md (nota forka), `maestro-cli/src/main/java/maestro/cli/mcp/README.md` |

**Dodane testy:**
- `TapCueNameTest` (`maestro-cli`, 5) — `--help` („Usage: tapcue …”, dopisek o pochodzeniu, `Usage: tapcue test`, opis
  `mcp`), `versionText()`; **proces CLI** (`java -cp <classpath testów> maestro.cli.AppKt`, `-Duser.home` i `HOME`
  w katalogu tymczasowym, bez `JAVA_TOOL_OPTIONS`/`XDG_*`): `--version`, `--help`, `check-syntax`, `test missing.yaml`
  (kończy się na brakującym flow po utworzeniu katalogu artefaktów i logu) → istnieją `~/.tapcue/tests`
  i `~/.tapcue/deps/applesimutils`, a w całym `HOME` **nie ma** katalogu `maestro`, `.maestro` ani `.mobiledev`;
  z `XDG_STATE_HOME` artefakty w `$XDG_STATE_HOME/tapcue/tests`; projekt z `.maestro/` → ostrzeżenie z `mv .maestro .tapcue`.
  Sprawdzone mutacją w kopii analitycznej: `DebugLogStore.APP_NAME = "maestro"` → 2 testy FAIL.
- `LegacyProjectDirTest` (`maestro-cli`, 5), `TapCueDirsTest` (`maestro-utils`, 4), `EnvUtilsTest` +1 (`tapcueHome`).
- `McpStdioTest`: `serverInfo.name` = `tapcue`, instrukcje zawierają „TapCue” i `.tapcue/`, po sesji MCP brak
  `~/.maestro` i `$XDG_STATE_HOME/maestro`.

**Zmienione testy:** `EnvUtilsTest`, `TestDebugReporterTest`, `DriverBuilderTest`, `HtmlTestSuiteReporterTest`,
`WebPlatformDisabledTest`, `RemovedAiCommandsTest` (+ `error.txt` w `e025`–`e027`) — nowe ścieżki i nazwy.
**Usunięte testy:** `LegacyAnalyticsStateTest` (2) — razem z klasą.

**Poza zakresem (bez zmian):** pakiety Kotlin `maestro.*`, moduły Gradle i `rootProject.name`, format YAML flow i nazwy
komend, zmienne JS `maestro.platform`/`maestro.copiedText`, identyfikatory na urządzeniu (`dev.mobile.maestro`, bundle
`dev.mobile.maestro-driver-ios*`) — zmiana wymagałaby przebudowy APK/runnera; nazwy archiwów `maestro.zip`/`maestro.tar`,
`jreleaser`, `scripts/install.sh`, README główny, NOTICE — STEP-05; linki `maestro.dev`/`mobile.dev` w komunikatach
(N-35: `PickDeviceInteractor`, stopka `SkiaFrameRenderer`, `ChatCommand`, `BugReportCommand` z linkiem do zgłoszeń upstream,
start-scripty) — STEP-05; zaparkowane workflowy upstream i skille `release`/`bump-android-version` — nie dotyczą forka.

## 2. Walidacja

Środowisko: Linux x86_64, JDK 17.0.x (doinstalowany przez `apt`) i 21, brak Android SDK/Xcode/urządzeń, `dl.google.com`
zablokowane. Lokalnie — **wariant analityczny** (handover STEP-00 §3: kopia w scratchpadzie bez `maestro-android`
i `google()`) z mirrorem Maven Central jak w STEP-01 §2. **Bez flag `-x`.**

| Test | Komenda | Wynik |
|---|---|---|
| Wszystkie testy jednostkowe + detekt + dystrybucja | `LANG=C.UTF-8 ./gradlew --continue :maestro-orchestra:detektMain :maestro-client:detektMain test :maestro-cli:installDist :maestro-cli:shadowJar` | **PASS — 1300 testów, 0 porażek** (STEP-04: 1287) |
| ↳ `maestro-cli` | jw. | **PASS**, 138 (STEP-04: 129; +5 `TapCueNameTest`, +5 `LegacyProjectDirTest`, +1 `EnvUtilsTest`, −2 `LegacyAnalyticsStateTest`) |
| ↳ `maestro-utils` | jw. | **PASS**, 49 (STEP-04: 45; +4 `TapCueDirsTest`) |
| ↳ pozostałe | jw. | **PASS** bez zmian liczności: `maestro-client` 324 (2 skipped), `maestro-orchestra` 456 (3 skipped), `maestro-orchestra-models` 51, `maestro-ios-driver` 47, `maestro-ios` 2, `maestro-test` 233 |
| Mutacja | `DebugLogStore.APP_NAME = "maestro"` w kopii, `:maestro-cli:test --tests maestro.cli.TapCueNameTest` | **FAIL zgodnie z oczekiwaniem** (2 testy wykrywają katalog `maestro`) |
| Smoke dystrybucji | `build/install/tapcue/bin/tapcue` z `HOME` tymczasowym, bez `JAVA_TOOL_OPTIONS`/`XDG_*`; projekt z `.maestro/flow.yaml`: `--version`, `--help`, `check-syntax .maestro/flow.yaml`, `test .maestro/flow.yaml` | **PASS**: `tapcue 2.11.0` + dopisek; nagłówek i „Usage: tapcue”; `OK`; ostrzeżenie z `mv .maestro .tapcue`, potem „Not enough devices connected (0)”, kod 1. Katalogi w `HOME`: `.tapcue/{deps,tests/<data>}`, `.cache/tapcue/logs`, `.android` (lookup ADB) — brak `maestro`/`.maestro`. `bin/tapcue` i `bin/tapcue.bat` zawierają sprawdzenie Javy 17 |
| Strażnik | `scripts/fork/check-no-simulator-server.sh` (w kopii z wynikami builda) | **PASS** |
| Skrypty | `bash -n` na `e2e/run_tests`, `e2e/cli/test-cli.sh`, `verify_preflight_suppressed.sh`, `./tapcue`, `installLocally.sh`; `e2e/validate_workspaces`, `e2e/list_workspaces` | **PASS** (manifest z `demo_app/.tapcue` poprawny) |
| CI GitHub Actions (`ci-baseline.yml`, pełne repo) | PR-04B [#12](https://github.com/rtnk/TapCue/pull/12) | wynik w PR |
| E2E (`e2e/run_tests`, `e2e/cli/test-cli.*`), smoke z urządzeniem, Swift, Windows (`tapcue.bat`) | — | **NOT RUN**: brak SDK/Xcode/urządzeń/Windows; E2E poza CI forka do STEP-06 |

Jak powtórzyć:

```bash
./gradlew :maestro-orchestra:detektMain :maestro-client:detektMain
./gradlew test --continue
./gradlew :maestro-cli:test --tests 'maestro.cli.TapCueNameTest' --tests 'maestro.cli.util.*' --tests 'maestro.cli.mcp.*'
./gradlew :maestro-cli:installDist && maestro-cli/build/install/tapcue/bin/tapcue --version
scripts/fork/check-no-simulator-server.sh
```

## 3. Ryzyka, blokery, znane problemy

- **Zmiana łamiąca dla użytkowników forka:** nowe polecenie, katalogi i konwencja `.tapcue/`. Fork nie miał jeszcze wydania,
  więc dotyczy to tylko lokalnych buildów; CHANGELOG ma trzy wpisy „Zmiana łamiąca”. Stan z `~/.maestro` nie jest
  przenoszony; zbudowany sterownik iOS dla urządzeń fizycznych trzeba zbudować ponownie (`tapcue driver-setup`).
- **`LegacyAnalyticsState` usunięty wbrew dosłownemu brzmieniu D-2** (patrz §1). Jeśli właściciel woli zachować sprzątanie
  `analytics.json` upstream, wystarczy przywrócić klasę z STEP-03 — ale wtedy TapCue modyfikuje katalog oficjalnego Maestro.
- **Ostrzeżenie `.maestro/` tylko w `tapcue test`.** `record`, `check-syntax` i `run` w MCP (`dir`/`files` z `.maestro/`)
  działają bez ostrzeżenia. Wystarczy to dla głównej ścieżki; rozszerzenie — w razie potrzeby.
- **Nazwy wewnątrz katalogów TapCue** (`maestro.log`, `maestro-iphoneos-driver-build`, `maestro-flow*.yaml` w `/tmp`)
  zostały, bo nie kolidują z upstream i są używane przez narzędzia (np. agent `diagnose-maestro-failure` czyta `maestro.log`).
- **Nazwy archiwów** `maestro.zip`/`maestro.tar` (distZip/distTar) i konfiguracja `jreleaser` zostały do STEP-05, ale
  start-script wewnątrz to już `bin/tapcue` (wynika z `applicationName`; `distZip` nie był budowany w tej sesji).
- **Windows:** `tapcue.bat` nie był uruchamiany; wstawka sprawdzająca Javę jest w wygenerowanym pliku.

## 4. Warunek rozpoczęcia następnego kroku

PR-04B **scalony** do `main`, CI `ci-baseline.yml` zielone na `main`. Właściciel potwierdził (albo odrzucił) decyzje
z §1: usunięcie `LegacyAnalyticsState` i ostrzeżenie dla `.maestro/` bez automatycznego odczytu.

## 5. Następny krok

**STEP-05: Dystrybucja lokalna macOS arm64 i audyt licencji (PR-05).** Zakres w `docs/ROADMAP-0.1.md` §3 „STEP-05”
i decyzje D-1, D-5, D-7 (§2).

## 6. PROMPT DLA NASTĘPNEGO AGENTA (STEP-05)

```text
Jesteś agentem kodującym w repozytorium rtnk/TapCue (fork mobile-dev-inc/Maestro → „TapCue”, polecenie `tapcue`).
Nie masz pamięci poprzednich sesji. Zanim cokolwiek zmienisz:

1. git fetch origin && git checkout main && git pull origin main. Potwierdź przez GitHub, że PR-04B
   („STEP-04B: TapCue name, command and directories”) jest MERGED. Jeśli nie — zatrzymaj się i zgłoś to.
2. Przeczytaj: AGENTS.md (nota forka), docs/ROADMAP-0.1.md (§1, §2 — D-1, D-5, D-7, STEP-05, gate'y), docs/handovers/STEP-04B.md,
   docs/LICENSE-AUDIT.md, docs/NETWORK-AUDIT.md (N-35, rejestr build i dystrybucja), docs/RELEASING.md,
   docs/ARCHITECTURE-LOCAL.md §3 i §5.
3. Wykonaj WYŁĄCZNIE STEP-05 — dystrybucja lokalna macOS arm64 i audyt licencji — na gałęzi step-05-local-dist:
   - Usuń `jreleaser` (plugin, konfiguracja, `maestro-cli/src/jreleaser/`), `scripts/install.sh` upstream i publikację
     Maven Central z `maestro-cli/build.gradle.kts`, jeśli nie są potrzebne forkowi.
   - Start-scripty: bez linków `maestro.dev`/`maestro.mobile.dev` (N-35); pozostałe linki N-35 w komunikatach
     (`PickDeviceInteractor`, stopka `SkiaFrameRenderer`, `ChatCommand`, `BugReportCommand` — link do zgłoszeń upstream,
     `WorkspaceExecutionPlanner`, `MaestroFlowParser`) → dokumentacja/zgłoszenia forka albo usunięte.
   - Archiwum wg D-1 (np. `tapcue-<wersja>-macos-arm64.zip`, zamiast `maestro.zip`/`maestro.tar`): `bin/tapcue`, `lib/*.jar`,
     `LICENSE`, `NOTICE` (z dopiskiem „based on Maestro (Apache-2.0), not affiliated with mobile.dev”),
     `THIRD-PARTY-NOTICES.md` (licencje wszystkich zależności runtime), README instalacji (D-7: wymagany JDK/JRE 17+).
   - D-5: bez prekompilowanych ZIP-ów sterownika iOS w paczce — zweryfikuj ścieżkę „build from source” dla symulatora.
   - `SHA256SUMS` i `scripts/verify-dist.sh` (zakazane ścieżki/wzorce: simulator-server, mcp-viewer, truffle-enterprise,
     ZIP-y iOS, com/posthog, maestro/ai; dozwolone biblioteki natywne z licencją) + test/krok CI, który go uruchamia.
   - Nie zmieniaj nazw pakietów `maestro.*`, modułów Gradle, formatu flow ani katalogów `~/.tapcue`/`.tapcue/` (STEP-04B).
4. Gradle uruchamiaj BEZ flag -x. Jeśli środowisko blokuje dl.google.com, użyj wariantu analitycznego z
   docs/handovers/STEP-00.md §3 (bez commitowania; mirror Maven Central jak w STEP-01 §2) i polegaj na CI.
   Weryfikacja na macOS arm64 offline — jeśli brak Maca, runner `macos-*` arm64 (D-9) albo NOT RUN z powodem.
5. Testy: zawartość archiwum (verify-dist.sh), brak zakazanych artefaktów, `bin/tapcue --version` z rozpakowanego archiwum,
   brak linków producenta w start-scriptach. Regresja: `./gradlew test`, `scripts/fork/check-no-simulator-server.sh`,
   maestro-cli (TapCueNameTest, NoTelemetryTest, LocalOnlyCliTest, McpStdioTest, CliJarContentsTest).
6. Zaktualizuj docs/ROADMAP-0.1.md (status, STEP-05 — realizacja), docs/LICENSE-AUDIT.md, docs/NETWORK-AUDIT.md (N-35,
   rejestr dystrybucji), docs/ARCHITECTURE-LOCAL.md §5, docs/RELEASING.md, CHANGELOG.md (sekcja forka), AGENTS.md (nota forka),
   dodaj docs/handovers/STEP-05.md wg szablonu STEP-04B.md (z promptem dla STEP-06).
7. Utwórz PR „STEP-05: Local distribution and license audit” do main (cel, zakres, usunięte/dodane/zachowane, pliki,
   wyniki testów z komendami — tylko faktycznie uruchomione, ryzyka, link do handover, checklista, prompt dla STEP-06).
   Bez auto-merge. Doprowadź CI PR do zielonego.
8. Zakończ odpowiedzią: krok, URL PR, walidacja PASS/FAIL/NOT RUN, ryzyka, warunki następnego kroku,
   prompt na kolejną sesję. Nie zaczynaj STEP-06.
```
