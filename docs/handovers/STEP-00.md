# Handover — STEP-00: Baseline and implementation plan

| Pole | Wartość |
|---|---|
| Krok | STEP-00 — **zakończony, w review** |
| PR | PR-00: _link uzupełniany po utworzeniu PR_ |
| Gałąź | `step-00-baseline-plan` |
| Gałąź docelowa | `main` |
| Baza | `main` @ `6592abd3142e374a48ec52c80829b6c70ff0a546` (upstream `v2.11.0` `c8c361be` + bootstrap) |
| Commit SHA | ostatni commit gałęzi `step-00-baseline-plan` (patrz PR) |

## 1. Co zrobiono

1. Sklonowano upstream do pustego katalogu roboczego `TapCue` (repo nie zawierało commitów ani plików
   użytkownika). Remote `upstream` = `mobile-dev-inc/Maestro`, `origin` = `rtnk/TapCue`.
2. Bazą jest **tag `v2.11.0`** (`c8c361be…`), nie `upstream/main` (`436309bd…`, +19 commitów) — stabilne wydanie.
3. Bootstrap `main` (bezpośredni push do pustego repo — jedyna możliwa droga utworzenia bazy dla PR):
   pełna historia upstream do `v2.11.0` + commit `6592abd3` przenoszący `.github/workflows/` do
   `.github/upstream-workflows-disabled/` z `[skip ci]`. Powód: workflowy upstream przy pushu/PR
   publikowałyby do usług upstream, zużywały minuty macOS i — przez `./gradlew test/build` — pobierały
   simulator-server (L-01). Tagi upstream **nie** zostały wypchnięte.
4. Analiza kodu (CLI, Orchestra, AI, MCP, Viewer, sterowniki, build) → `docs/NETWORK-AUDIT.md` (35 ścieżek
   runtime, 8 ścieżek build, 2 testy zależne od internetu), `docs/LICENSE-AUDIT.md` (11 pozycji + 186
   zależności Maven w `docs/audit/runtime-dependencies-v2.11.0.md`), `docs/ARCHITECTURE-LOCAL.md`.
5. Plan: `docs/ROADMAP-0.1.md` — kroki 00…08 + proponowane 00B i 03B, decyzje D-1…D-9, gate'y G-1…G-8,
   testy AT-01…AT-11, ryzyka R-1…R-10.
6. Bazowy CI: `.github/workflows/ci-baseline.yml` + `scripts/fork/check-no-simulator-server.sh`.
7. Dokumentacja pomocnicza: szkic `docs/RELEASING.md`, sekcja forka w `CHANGELOG.md`, nota na początku
   `AGENTS.md`, `README.md` w `.github/upstream-workflows-disabled/`.

Kod produktu **nie został zmieniony**.

## 2. Najważniejsze ustalenia

- **Opt-out upstream nie działa w pełni:** `ErrorReporter` (N-03) i `ScreenReporter` (N-06) nie sprawdzają
  żadnej zmiennej; `fetchUpdatesAsync()` (N-04) wysyła żądanie mimo `MAESTRO_DISABLE_UPDATE_CHECK=true`.
  Nawet `maestro --version` kontaktuje PostHog i API wersji.
- **`maestro record` domyślnie wysyła nagranie ekranu** na `maestro-record.ngrok.io` (N-14).
- **`maestro test` przekazuje token Cloud do Orchestra** — obecność tokenu włącza zdalne AI (N-07, N-08).
- **Licencje:** L-01 simulator-server (ZAKAZ, potwierdzony tekst licencji); L-03 `truffle-enterprise` (GFTC)
  przez `org.graalvm.js:js`; L-04 frameworki Apple w prekompilowanych ZIP-ach iOS.
- Raport HTML ładuje Bootstrap z CDN (N-21); JS `http.*` i sterownik web mogą łączyć się z dowolnym hostem.

## 3. Walidacja

Środowisko sesji: Linux x86_64, 4 CPU, JDK 17.0.20 (doinstalowany; toolchain projektu wymaga 17) i 21,
**brak Android SDK, Xcode, emulatora i symulatora**. Polityka sieci środowiska blokuje `dl.google.com`
(Google Maven / Android SDK) — 403 z proxy.

| Test | Komenda | Wynik |
|---|---|---|
| Konfiguracja Gradle (pełne repo) | `./gradlew projects` | **FAIL (środowisko)** — wtyczka `com.android.application` niedostępna (blokada `dl.google.com`) |
| Testy jednostkowe — wariant analityczny\* | `./gradlew test --continue -x downloadSimulatorServer -x buildMcpViewer -x installMcpViewerDeps` | **1293 testy, 4 porażki** (szczegóły niżej); `maestro-android` NOT RUN |
| Porażka: `IntegrationTest` Case 136 (`maestro-test`) | jw. | FAIL — test woła publiczny `eu.httpbin.org` (T-01), proxy zwraca 403 |
| Porażka: `TestOutputWriterTest.saveScreenshots…` (`maestro-orchestra`) | jw. | FAIL przy domyślnym locale; **PASS** z `LANG=C.UTF-8` (nazwa pliku z „✅”) |
| Porażka: `AdbSocketTimeoutTest.close fallback threads…` (`maestro-client`) | jw. | FAIL w pierwszym przebiegu, **PASS** przy ponowieniu — test czasowy, niestabilny |
| Porażka: `ApiClientUploadRetryTest.failing before the request lands…` (`maestro-cli`) | jw. (również ponowiony z UTF-8) | FAIL stabilnie w tym środowisku (`Broken pipe` na lokalnym serwerze testowym); test dotyczy uploadu Cloud — zostanie usunięty w STEP-01 |
| Strażnik simulator-server | `scripts/fork/check-no-simulator-server.sh` na wynikach builda | PASS; test negatywny (sztuczny JAR z `deps/simulator-server/`) → FAIL zgodnie z oczekiwaniem |
| `ci-baseline.yml` na GitHub Actions | PR-00 | wynik w PR (uruchamiany pierwszy raz przez ten PR) |
| Smoke Android/iOS | — | **NOT RUN** — brak SDK/Xcode/urządzeń w środowisku |
| Swift (`MaestroDriverLib`, XCTest runner) | — | **NOT RUN** — wymaga macOS (D-9) |

\* Wariant analityczny = kopia robocza w scratchpadzie z trzema lokalnymi zmianami **niezatwierdzonymi
w repo**: usunięty `include("maestro-android")`, usunięte aliasy wtyczek Android w `build.gradle.kts`,
usunięte `google()` z repozytoriów (bo 403 z `dl.google.com` przerywa rozwiązywanie zamiast przejść do
Maven Central). Liczności: maestro-cli 160, maestro-client 301 (2 skipped), maestro-ios-driver 47,
maestro-ios 2, maestro-orchestra-models 51, maestro-orchestra 446 (3 skipped), maestro-test 223,
maestro-utils 45, maestro-web 18.

Jak powtórzyć (środowisko z dostępem do Google Maven i Android SDK, np. GitHub Actions `ubuntu-latest`):

```bash
./gradlew :maestro-orchestra:detektMain :maestro-client:detektMain
./gradlew test --continue -x downloadSimulatorServer -x installMcpViewerDeps -x buildMcpViewer
scripts/fork/check-no-simulator-server.sh
```

## 4. Ryzyka, blokery, znane problemy

- **Brak blokerów dla STEP-01.** Blokery Release: L-01, L-03, L-04 i decyzje D-1, D-2, D-5, D-6, D-7.
- Do czasu STEP-00B/04 każdy `./gradlew build`/`test`/`run` **bez** flag `-x downloadSimulatorServer …`
  pobiera simulator-server. Nie uruchamiaj Gradle bez tych flag.
- Sesje agenta w obecnym środowisku chmurowym nie mają dostępu do `dl.google.com` → pełna walidacja
  modułu `maestro-android` tylko w GitHub Actions (lub po dodaniu `dl.google.com` do dozwolonych domen
  środowiska).
- `upstream/main` jest 19 commitów przed bazą; synchronizacja z upstream — osobne PR-y po 0.1.0 (R-5).

## 5. Warunek rozpoczęcia następnego kroku

PR-00 **scalony** do `main` i `main` aktualny. Właściciel rozstrzyga D-8 (czy STEP-00B przed STEP-01).
Pozostałe decyzje D-x nie blokują STEP-01.

## 6. Następny krok

**STEP-01 — Remove Cloud CLI (PR-01)** — zakres i kryteria w `docs/ROADMAP-0.1.md` §3 „STEP-01”.
Kryterium ukończenia: lokalny `maestro test` nie konstruuje klienta Cloud, nie odczytuje tokenu Cloud i nie
wymaga konta ani sieci; komendy `cloud`, `login`, `logout`, `list-cloud-devices`, `studio` nie istnieją;
`record` renderuje lokalnie; testy CLI to potwierdzają.

Jeśli właściciel zaakceptuje D-8, najpierw wykonaj **STEP-00B** (prompt alternatywny niżej).

## 7. PROMPT DLA NASTĘPNEGO AGENTA (STEP-01)

```text
Jesteś agentem kodującym w repozytorium rtnk/TapCue (fork mobile-dev-inc/Maestro → „Maestro Local”).
Nie masz pamięci poprzednich sesji. Zanim cokolwiek zmienisz:

1. git fetch origin && git checkout main && git pull origin main. Potwierdź przez GitHub, że PR-00
   („STEP-00: Baseline and implementation plan”) jest MERGED, a main zawiera docs/ROADMAP-0.1.md.
   Jeśli nie jest scalony — zatrzymaj się i zgłoś to.
2. Przeczytaj: AGENTS.md (nota forka na początku), docs/ROADMAP-0.1.md (zwłaszcza §1, §2 i STEP-01),
   docs/handovers/STEP-00.md, docs/NETWORK-AUDIT.md (N-07, N-11…N-14, N-20, N-22, N-23, N-27, N-34),
   docs/ARCHITECTURE-LOCAL.md. Sprawdź w PR-00/roadmapie decyzję D-8: jeśli STEP-00B ma być pierwszy
   i nie jest jeszcze scalony — zatrzymaj się i zgłoś.
3. Wykonaj WYŁĄCZNIE STEP-01 — Remove Cloud CLI — na gałęzi step-01-remove-cloud-cli:
   usuń komendy cloud, login, logout, list-cloud-devices, studio (+StudioDownload), CloudInteractor,
   Auth, metody Cloud w ApiClient, RemoteVideoRenderer (record zawsze lokalnie), PromotionStateManager
   i komunikaty promujące Cloud, linki Cloud w TestSuiteStatusView, maestro.auth.ApiKey, MockInteractor,
   nawigację isStudio do maestro.mobile.dev w CdpWebDriver, identyfikację użytkownika w Analytics.
   Odetnij TestCommand od ApiClient/Auth/tokenu (bez --api-url). Zachowaj lokalne testy, raporty,
   sharding, artefakty i record --local. Nie usuwaj jeszcze AI (STEP-02) ani telemetrii (STEP-03)
   poza tym, co niezbędne do kompilacji — opisz to w PR.
4. Gradle uruchamiaj ZAWSZE z: -x downloadSimulatorServer -x installMcpViewerDeps -x buildMcpViewer
   (licencja simulator-server, docs/LICENSE-AUDIT.md L-01). Jeśli środowisko blokuje dl.google.com,
   użyj wariantu analitycznego z docs/handovers/STEP-00.md §3 (bez commitowania tych zmian) i polegaj na
   CI GitHub Actions (ci-baseline.yml) dla pełnej walidacji.
5. Dodaj testy: usunięte komendy → „Unknown command”; `maestro test --help` bez opcji Cloud;
   TestCommand działa bez ~/.mobiledev i bez sieci. Uruchom testy modułów maestro-cli, maestro-client,
   maestro-orchestra, maestro-test i scripts/fork/check-no-simulator-server.sh.
6. Zaktualizuj docs/ROADMAP-0.1.md (status), docs/NETWORK-AUDIT.md (status pozycji), docs/ARCHITECTURE-LOCAL.md §5,
   CHANGELOG.md (sekcja forka), dodaj docs/handovers/STEP-01.md wg szablonu z STEP-00.md (z promptem dla STEP-02).
7. Utwórz PR „STEP-01: Remove Cloud CLI” do main wg szablonu PR z roadmapy/kontraktu (cel, zakres,
   usunięte/dodane/zachowane, pliki, wyniki testów z komendami — tylko faktycznie uruchomione, audyt sieci,
   ryzyka, link do handover, checklista, prompt dla STEP-02). Bez auto-merge. Doprowadź CI PR do zielonego.
8. Zakończ odpowiedzią: krok, URL PR, walidacja PASS/FAIL/NOT RUN, ryzyka, warunki następnego kroku,
   prompt na kolejną sesję. Nie zaczynaj STEP-02.
```

## 8. PROMPT ALTERNATYWNY (jeśli D-8 zaakceptowana: STEP-00B przed STEP-01)

```text
Jesteś agentem kodującym w repozytorium rtnk/TapCue (fork mobile-dev-inc/Maestro → „Maestro Local”).
Nie masz pamięci poprzednich sesji.

1. git fetch origin && git checkout main && git pull origin main; potwierdź, że PR-00 jest MERGED.
2. Przeczytaj AGENTS.md, docs/ROADMAP-0.1.md (§2 D-8 i STEP-00B), docs/handovers/STEP-00.md,
   docs/LICENSE-AUDIT.md (L-01).
3. Wykonaj WYŁĄCZNIE STEP-00B — Legal containment — na gałęzi step-00b-legal-containment:
   usuń z maestro-cli/build.gradle.kts taski downloadSimulatorServer, installMcpViewerDeps, buildMcpViewer
   i ich wpięcie w processResources; usuń simulatorServer z gradle/libs.versions.toml; McpCommand nie
   uruchamia Viewera; open_maestro_viewer niezarejestrowane; installSimulatorServer usunięte lub rzucające
   kontrolowany błąd. Dodaj test, że JAR CLI nie zawiera deps/simulator-server/** ani mcp-viewer/**.
   Usuń flagi -x … z .github/workflows/ci-baseline.yml i notę o nich z AGENTS.md. Nie usuwaj jeszcze
   pakietu mcp/viewer/ ani maestro-cli/mcp-viewer/ (STEP-04).
4. Testy: maestro-cli, check-no-simulator-server.sh, MCP startuje (maestro mcp) bez Viewera.
5. Zaktualizuj ROADMAP, LICENSE-AUDIT (L-01: usunięte z builda), NETWORK-AUDIT (B-01, B-02, N-18),
   CHANGELOG, dodaj docs/handovers/STEP-00B.md z promptem dla STEP-01.
6. PR „STEP-00B: Legal containment (no simulator-server in build)” do main, bez auto-merge, CI zielone.
7. Zakończ raportem wg kontraktu. Nie zaczynaj STEP-01.
```
