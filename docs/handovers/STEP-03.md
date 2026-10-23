# Handover — STEP-03: Remove Telemetry, Error Upload and Update Checks

| Pole | Wartość |
|---|---|
| Krok | STEP-03 — **zakończony, w review** |
| PR | PR-03: https://github.com/rtnk/TapCue/pull/6 |
| Gałąź | `step-03-remove-telemetry` |
| Gałąź docelowa | `main` |
| Baza | `main` @ `9182c58` (merge PR #5, po merge PR-02 #4) |
| Commit SHA | ostatni commit gałęzi `step-03-remove-telemetry` (patrz PR) |

## 1. Co zrobiono

Cel: zwykłe uruchomienie CLI i wymuszone błędy nie nawiązują połączeń zewnętrznych (NETWORK-AUDIT N-01, N-03, N-04,
N-05, N-06, N-19, N-21, N-24, N-34).

| Obszar | Zmiana |
|---|---|
| Telemetria | pakiet `analytics/` (`Analytics` z kluczem i hostem PostHog, `AnalyticsStateManager`, `CommandArgsSanitizer`, `PostHogEvents`) usunięty; wywołania `Analytics.*` usunięte z `App`, `TestCommand`, `RecordCommand`, `PrintHierarchyCommand`; zależność `com.posthog:posthog-server` (wpis w `libs.versions.toml` i `maestro-cli/build.gradle.kts`). Brak komunikatu „Anonymous analytics enabled” i pliku `analytics.json` (UUID instalacji) |
| Błędy | `util/ErrorReporter.kt` usunięty; handler wyjątków (wydzielony do `internal fun createCommandLine()` w `App.kt`) wypisuje komunikat i stack trace tylko na stderr — zachowanie lokalne bez zmian |
| Raport ekranu | `util/ScreenReporter.kt` i jego wywołanie w shutdown hooku `MaestroSessionManager` usunięte (`DepthTracker` w `maestro-utils` zostaje, nikt już nie wysyła jego wartości) |
| Aktualizacje | `update/Updates.kt`, `util/ChangeLogUtils.kt` (+ `ChangeLogUtilsTest`), blok aktualizacji w `App.kt` (baner z `get.maestro.mobile.dev`) usunięte |
| API | `api/ApiClient.kt` usunięty w całości (`sendErrorReport`, `sendScreenReport`, `getLatestCliVersion`, `SystemInformationInterceptor`); `CliVersion` przeniesione do `maestro.cli.util.CliVersion` (używane lokalnie przez `RealIOSDeviceDriver`/`DriverBuildConfig`); `EnvUtils.BASE_API_URL`, `PROD_API_URL` i nieużywane już `getVersion()` usunięte |
| Komendy | `DownloadSamplesCommand` i `util/FileDownloader.kt` (klient ktor używany tylko przez nią) usunięte → `maestro download-samples` daje „Unmatched argument” |
| `TestCommand` | bez zdarzeń analitycznych; usunięte `getDeviceCount`/`getDeviceIds`, które listowały urządzenia (ADB/simctl) wyłącznie po to, by podać ich liczbę w zdarzeniu |
| Raport HTML | `bootstrap.min.css`/`bootstrap.min.js` z `cdn.jsdelivr.net` zastąpione CSS inline z zasobu `html-report.css` (podzbiór klas Bootstrap, których raport używa) i kilkuliniowym skryptem inline do zwijania flow (te same atrybuty `data-bs-*`); dodane `<meta charset="utf-8">`. Struktura HTML bez zmian |
| `start-device` | `DeviceService.installAndroidSystemImage` i pytanie „Would you like to install it? y/n” usunięte; `resolveSystemImage` przegląda tylko `sdkmanager --list_installed` (wcześniej też `--list`, czyli zdalny katalog Google). Brak obrazu → `CliError` „The required system image … is not installed, and Maestro does not download system images. To install it yourself, run: `<sdkmanager> "<pakiet>"`”; inny zainstalowany obraz dla tego `os` → podpowiedź `--device-os` |
| Dokumenty | ROADMAP (status, realizacja), NETWORK-AUDIT (wersja 4), LICENSE-AUDIT (wersja 3: posthog), ARCHITECTURE-LOCAL §5, CHANGELOG, komentarz `claimMcpStdout` |

**Katalog stanu:** bez zmian (`~/.maestro`, `$XDG_STATE_HOME/maestro`) — decyzja D-2 nadal otwarta.

**Dodane testy:**
- `maestro-cli` `NoTelemetryTest` (5): wymuszony wyjątek komendy (`createCommandLine()` + testowa podkomenda
  rzucająca `IllegalStateException`) → kod 1, komunikat i stack trace na stderr, zero wywołań `ProxySelector`
  (który rzuca przy każdej próbie), zero żądań do `MockWebServer` wskazanego przez `MAESTRO_API_URL`, brak
  `analytics.json` w `HOME`/`XDG_STATE_HOME`; `--version` i `--help` nie startują żadnego nowego wątku i nie łączą się
  z siecią; `download-samples` nieznane; klasy telemetrii/aktualizacji/`ApiClient`/PostHog nieobecne; żaden JAR
  classpath testów (nadzbiór `runtimeClasspath` CLI) nie zawiera `com/posthog/`.
- `maestro-cli` `CliJarContentsTest` „CLI jar references no telemetry, update or CDN hosts” — skan wszystkich wpisów
  zbudowanego JAR CLI na `posthog.com`, `api.copilot.mobile.dev`, `raw.githubusercontent.com/mobile-dev-inc`,
  `storage.googleapis.com/mobile.dev`, `get.maestro.mobile.dev`, `cdn.jsdelivr.net` (wyjątek: `CheatSheetTool`, N-15, STEP-04).
- `maestro-cli` `HtmlTestSuiteReporterTest` „report loads nothing from the network” (+ snapshoty zaktualizowane:
  `<meta charset>`, `<style>` i `<script>` inline zamiast CDN).
- `maestro-cli` `DeviceCreateUtilTest`: brakujący obraz → komunikat z poleceniem `sdkmanager`, bez tworzenia AVD
  (zastępuje test ścieżki „install prompt”); `DeviceService` nie ma `installAndroidSystemImage`.

**Usunięte/zmienione testy:** `CommandArgsSanitizerTest` (17), `ChangeLogUtilsTest` (7), `NoRemoteAiTest`
„ApiClient has no analyze endpoint” (1) — dotyczyły usuniętego kodu; `LocalOnlyCliTest` „ApiClient has no Cloud
endpoints” → „the API client is gone”.

**Poza zakresem:** `cheat_sheet` (N-15) i link `docs.maestro.dev` w instrukcjach MCP (N-17) — STEP-04; JS `http.*`,
platforma web, T-01 — STEP-03B; linki `maestro.dev`/`mobile.dev` w komunikatach i start-scriptach, `jreleaser` (N-35,
B-04) — STEP-05; `e2e/cli/test-cli.sh`/`.ps1` eksportują nieużywane już `MAESTRO_CLI_NO_ANALYTICS` (uruchamiane tylko
przez zaparkowany workflow upstream) i `e2e/update_samples`/`e2e/README.md` opisują `download-samples` — STEP-06
(ocena `e2e/`). Zależności ktor w `maestro-cli` zostają (Viewer — STEP-04, `encodeBase64`, `maestro-web`).

## 2. Walidacja

Środowisko: Linux x86_64, JDK 17.0.20 (doinstalowany przez `apt`), brak Android SDK/Xcode/urządzeń,
`dl.google.com` zablokowane. Lokalnie — **wariant analityczny** (handover STEP-00 §3: kopia w scratchpadzie bez
`maestro-android` i `google()`) z mirrorem Maven Central jak w STEP-01 §2. **Bez flag `-x`.**

| Test | Komenda | Wynik |
|---|---|---|
| Wszystkie testy jednostkowe | `LANG=C.UTF-8 ./gradlew test --continue` | **1265 testów, 1 porażka**: tylko znany T-01 (`IntegrationTest` Case 136, publiczny `eu.httpbin.org`) |
| ↳ `maestro-cli` | jw. | **PASS**, 121 (STEP-02: 138; −25 usunięte, +5 `NoTelemetryTest`, +1 skan JAR, +1 raport HTML offline, +1 `DeviceCreateUtilTest`) |
| ↳ `maestro-test` | jw. | 223, **1 FAIL** (T-01) |
| ↳ pozostałe | jw. | **PASS**: `maestro-client` 302 (2 skipped), `maestro-orchestra` 456 (3 skipped), `maestro-orchestra-models` 51, `maestro-ios-driver` 47, `maestro-ios` 2, `maestro-utils` 45, `maestro-web` 18 |
| Detekt + dystrybucja | `./gradlew :maestro-orchestra:detektMain :maestro-client:detektMain :maestro-cli:installDist :maestro-cli:shadowJar` | **PASS** |
| Graf zależności | `./gradlew :maestro-cli:dependencies --configuration runtimeClasspath` na `main` i na gałęzi, diff | **PASS**: znika tylko `com.posthog:posthog-server:1.0.3` i `com.posthog:posthog:3.23.1` (pozostałe różnice to ich wiersze żądanych wersji `gson`/`okhttp`, rozwiązywanych i tak do tych samych wersji) |
| Zawartość archiwów | rozpakowany `maestro-cli-*-all.jar`, `install/maestro/lib` | **PASS**: 0 wpisów `com/posthog/`, `maestro/cli/analytics/`, `maestro/cli/update/`; brak `posthog.com`, `raw.githubusercontent.com/mobile-dev-inc`, `storage.googleapis.com/mobile.dev`, `get.maestro.mobile.dev`, `cdn.jsdelivr.net`; `api.copilot.mobile.dev` tylko w `CheatSheetTool` (N-15) |
| Strażnik | `scripts/fork/check-no-simulator-server.sh` | **PASS** |
| Smoke CLI (pusty `HOME`, `strace -f -e trace=connect,sendto,sendmsg`) | `--version`, `--help`, `check-syntax ok.yaml`, `test ok.yaml`, `hierarchy`, `start-device --platform android --device-os android-34` | **PASS**: 0 wywołań `connect`/`send*` do adresów innych niż loopback (jedyne `AF_INET`: próby ADB na `127.0.0.1:5037`/`5555…`); `start-device` bez SDK → `IllegalStateException` + stack trace lokalnie, kod 1. `download-samples` → „Unmatched argument”, kod 2; flow z `assertWithAI` → „AI Command Not Available”, kod 1; `HOME` pozostał pusty |
| CI GitHub Actions (`ci-baseline.yml`, pełne repo) | PR-03 | wynik w PR |
| Smoke z urządzeniem, `start-device` z prawdziwym SDK, Swift | — | **NOT RUN**: brak SDK/Xcode/macOS |

Jak powtórzyć:

```bash
./gradlew :maestro-orchestra:detektMain :maestro-client:detektMain
./gradlew test --continue
./gradlew :maestro-cli:dependencies --configuration runtimeClasspath | grep -c posthog   # 0
./gradlew :maestro-cli:test --tests 'maestro.cli.NoTelemetryTest' --tests 'maestro.cli.CliJarContentsTest'
scripts/fork/check-no-simulator-server.sh
```

## 3. Ryzyka, blokery, znane problemy

- **Zmiana zachowania `start-device`:** brakujący obraz systemu nie jest już instalowany po potwierdzeniu — użytkownik
  dostaje polecenie `sdkmanager` do uruchomienia samemu. Podpowiedź `--device-os` bierze pod uwagę tylko obrazy
  zainstalowane (wcześniej także dostępne zdalnie). Ścieżka nieprzetestowana z prawdziwym SDK (brak SDK w sesji).
  Hipoteza do STEP-06: `sdkmanager --list_installed` nie łączy się z siecią.
- **Raport HTML:** wygląd zbliżony do Bootstrapa, ale nie identyczny (własny minimalny CSS). Zwijanie flow działa
  skryptem inline — przy wyłączonym JavaScript szczegóły flow pozostają zwinięte (jak wcześniej bez Bootstrap JS).
- **API biblioteki CLI:** `maestro.cli.api.CliVersion` → `maestro.cli.util.CliVersion`; pakiety `maestro.cli.analytics`,
  `maestro.cli.update`, `maestro.cli.api` nie istnieją.
- Stary plik `~/.maestro/analytics.json` (lub `$XDG_STATE_HOME/maestro/analytics.json`) z instalacji upstream nie jest
  usuwany ani czytany.
- T-01 (`IntegrationTest` Case 136) pada bez internetu — do przepisania w STEP-03B.

## 4. Warunek rozpoczęcia następnego kroku

PR-03 **scalony** do `main`, CI `ci-baseline.yml` zielone na `main`. Decyzje D-3 (polityka `http.*`) i D-4 (platforma
web) — przeczytane przez właściciela; jeśli nie zostały rozstrzygnięte, STEP-03B realizuje rekomendacje z ROADMAP §2
i opisuje to w PR.

## 5. Następny krok

**STEP-03B: Polityka sieci dla funkcji flow (PR-03B).** Zakres w `docs/ROADMAP-0.1.md` §3 „STEP-03B”.

## 6. PROMPT DLA NASTĘPNEGO AGENTA (STEP-03B)

```text
Jesteś agentem kodującym w repozytorium rtnk/TapCue (fork mobile-dev-inc/Maestro → „Maestro Local”).
Nie masz pamięci poprzednich sesji. Zanim cokolwiek zmienisz:

1. git fetch origin && git checkout main && git pull origin main. Potwierdź przez GitHub, że PR-03
   („STEP-03: Remove telemetry, error upload and update checks”) jest MERGED. Jeśli nie — zatrzymaj się i zgłoś to.
2. Przeczytaj: AGENTS.md (nota forka), docs/ROADMAP-0.1.md (§1, §2 — zwłaszcza D-3, D-4, D-6; STEP-03B),
   docs/handovers/STEP-03.md, docs/NETWORK-AUDIT.md (N-25, N-26, N-28, N-29, N-30, T-01, T-02),
   docs/ARCHITECTURE-LOCAL.md §3–§5, docs/LICENSE-AUDIT.md (selenium-manager, L-03).
3. Wykonaj WYŁĄCZNIE STEP-03B — Polityka sieci dla funkcji flow — na gałęzi step-03b-flow-network-policy:
   - JS http.* (maestro-client GraalJsHttp): walidacja hosta PRZED żądaniem wg D-3 (rekomendacja: tylko loopback,
     RFC 1918, fc00::/7, link-local; rozwiązanie DNS i sprawdzenie każdego adresu; przekierowania — każdy skok
     sprawdzany albo followRedirects wyłączone dla hostów spoza polityki); inne adresy → jasny błąd flow.
   - Platforma web wg D-4 (rekomendacja: wyłączona w 0.1.0 z kontrolowanym błędem; selenium/selenium-manager
     i maestro-web poza runtime classpath CLI, jeśli to możliwe bez dużej przebudowy — inaczej opisz w PR).
   - openLink/launchApp na urządzeniach: udokumentuj jako GRANICA (ruch wykonuje urządzenie).
   - T-01: IntegrationTest „Case 136 - Relative path in http multipart script” przepisz na lokalny MockWebServer.
   Jeśli właściciel rozstrzygnął D-6 i zlecił to w tym kroku — GraalJS community (bez truffle-enterprise);
   w przeciwnym razie nie ruszaj.
   Nie ruszaj Viewera/cheat_sheet (STEP-04) ani dystrybucji (STEP-05) poza tym, co niezbędne do kompilacji.
4. Gradle uruchamiaj BEZ flag -x (taski simulator-server/Viewera nie istnieją od STEP-00B). Jeśli środowisko
   blokuje dl.google.com, użyj wariantu analitycznego z docs/handovers/STEP-00.md §3 (bez commitowania; mirror
   Maven Central jak w docs/handovers/STEP-01.md §2) i polegaj na CI GitHub Actions (ci-baseline.yml). Po usunięciu
   zależności sprawdź, czy inne moduły nie korzystały z nich przechodnio.
5. Testy: http.get("http://127.0.0.1:<mock>") działa; http.get("https://example.com") → błąd bez próby połączenia
   (ProxySelector/MockWebServer jak w NoTelemetryTest/LocalOnlyCliTest — rejestruj każdą próbę); przekierowanie
   z mocka na host publiczny → błąd; adres prywatny przez nazwę DNS (np. resolver testowy) — zgodnie z polityką;
   flow web → kontrolowany błąd (jeśli D-4 = wyłączenie); IntegrationTest i GraalJsEngineTest/JsEngineTest zielone
   bez internetu (T-01 rozwiązany); testy maestro-cli (NoTelemetryTest, LocalOnlyCliTest, CliJarContentsTest,
   NoRemoteAiTest), maestro-client, maestro-orchestra, maestro-test; scripts/fork/check-no-simulator-server.sh.
6. Zaktualizuj docs/ROADMAP-0.1.md (status, D-3/D-4 jeśli rozstrzygnięte), docs/NETWORK-AUDIT.md (N-25, N-26,
   N-28, N-29, N-30, T-01), docs/LICENSE-AUDIT.md (selenium-manager, jeśli usunięty), docs/ARCHITECTURE-LOCAL.md §4–§5,
   CHANGELOG.md (sekcja forka), dodaj docs/handovers/STEP-03B.md wg szablonu STEP-03.md (z promptem dla STEP-04).
7. Utwórz PR „STEP-03B: Network policy for flow features” do main (cel, zakres, usunięte/dodane/zachowane, pliki,
   wyniki testów z komendami — tylko faktycznie uruchomione, audyt sieci, ryzyka, link do handover, checklista,
   prompt dla STEP-04). Bez auto-merge. Doprowadź CI PR do zielonego.
8. Zakończ odpowiedzią: krok, URL PR, walidacja PASS/FAIL/NOT RUN, ryzyka, warunki następnego kroku,
   prompt na kolejną sesję. Nie zaczynaj STEP-04.
```
