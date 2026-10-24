# Handover — STEP-06: GitHub Actions CI i testy offline

| Pole | Wartość |
|---|---|
| Krok | STEP-06 — w review |
| PR | PR-06: https://github.com/rtnk/TapCue/pull/14 |
| Gałąź | `step-06-ci` |
| Gałąź docelowa | `main` |
| Baza | `main` @ `40df271` (merge PR #13 — STEP-05) |
| Commit SHA | ostatni commit gałęzi `step-06-ci` (patrz PR) |

## 1. Co zrobiono

Cel (gate'y G-2, G-4, G-5 — część Linux; AT-01…AT-10 w CI): jeden workflow CI, który na każdym PR i pushu `main` sprawdza
testy, paczkę, zależności i licencje, udowadnia brak prób połączeń poza loopback i uruchamia CLI na prawdziwych urządzeniach
(emulator Android, symulator iOS).

### `.github/workflows/ci.yml`

`on: pull_request, push: main, workflow_dispatch`; `permissions: contents: read`; wszystkie akcje przypięte do SHA
(`actions/checkout` v7.0.1, `gradle/actions/wrapper-validation` v6.4.0, `actions/setup-java` v6.1.0, `actions/upload-artifact`
v7.0.2, `actions/download-artifact` v8.0.2, `ReactiveCircus/android-emulator-runner` v2.37.0); bez `pull_request_target`,
bez sekretów.

| Job | Runner | Co robi |
|---|---|---|
| `changes` | ubuntu | PR zmieniający wyłącznie `*.md`/`docs/` → `devices=false` (joby urządzeń pokazują kroki jako `skipped` z powodem w podsumowaniu) |
| `build` | ubuntu | wrapper validation; detekt; `./gradlew test` (w tym `McpStdioTest`, `PendingRequestsTest`, testy schematu YAML); `macosArm64DistZip`; `verify-dist.sh --smoke`; `test-verify-dist.sh`; **kontrola zależności** `check-forbidden-deps.sh` (drzewo `runtimeClasspath` CLI → `build/reports/fork/cli-runtime-dependencies.txt`); **skan licencji** `check-licenses.py` (raport w podsumowaniu joba); testy negatywne obu `test-ci-checks.sh`; **lista bibliotek APK sterownika** (`:maestro-android:dependencies` dla `debugRuntimeClasspath` i `debugAndroidTestRuntimeClasspath` + `android-driver-deps.py` — licencje z POM); strażnik simulator-server; artefakty `tapcue-macos-arm64` (archiwum + `SHA256SUMS`, 7 dni) i `build-reports` (raporty testów, raporty `build/reports/fork`, 14 dni) |
| `egress` | ubuntu, `needs: build` | `strace` z apt; archiwum z artefaktu (`sha256sum -c`); **dowód wykrywalności** (`egress-test.sh self-test`: sondy DNS/TCP/`curl`/JVM wykryte, loopback nie) i launcher z dopisanym `curl` → FAIL; **CLI i MCP bez sieci** (`egress-test.sh cli`); **testy jednostkowe bez sieci** (T-02: kompilacja i pobranie zależności testów online — `resolve-test-runtime.gradle`, `--stop` demona — potem testy 8 modułów JVM `--offline` w izolacji); artefakt `egress-reports` |
| `android` | ubuntu + KVM, `needs: [changes, build]` | brak KVM albo PR tylko z dokumentacją → kroki `skipped` + `::warning::`/podsumowanie; emulator API 34 `google_apis` x86_64; `android-integration.sh` z paczką z artefaktu; artefakt `android-reports` (wyjścia, JUnit, debug output, zrzuty) |
| `macos` | `macos-26` arm64 | przeniesiony `macos-dist`: archiwum + `verify-dist` + `shasum -c`; paczka pod `sandbox-exec` bez sieci (`--version`, `--help`, `check-syntax`, sesja MCP zakończona EOF) **z rejestrem prób** (odmowy `network-outbound` z logu systemowego, po sprawdzeniu, że log pokazuje odmowę dla `curl`); `driver-setup --simulator` (runner ze źródła, asercja braku pobrań); `hierarchy` na symulatorze; artefakt `macos-reports` |

Zakres macOS (D-9): repozytorium jest 2026-10-24 nadal **prywatne**, więc minuty macOS są płatne. Job `macos` działa na każdym
PR zmieniającym coś poza dokumentacją, na pushu `main` i na `workflow_dispatch`; PR tylko z dokumentacją pomija go jawnie.
Po upublicznieniu repozytorium (decyzja właściciela) reguła może zostać bez zmian.

### Narzędzia (`scripts/fork/`)

| Plik | Rola |
|---|---|
| `egress-test.sh` | Test offline: każde polecenie w `unshare --net --pid --fork --mount-proc` (tylko `lo`), pod `strace -f --seccomp-bpf -e trace=connect,sendto,sendmsg,sendmmsg`, jako wywołujący użytkownik (`strace -u`), bez zmiennych proxy i opcji JVM ze środowiska, z limitem czasu (`EGRESS_TIMEOUT`, domyślnie 300 s na polecenie CLI, 3600 s w trybie `run`). Próba = adres spoza `127.0.0.0/8`/`::1` (także v4-mapped), port 53 (także lokalny stub-resolver), zapytanie przez gniazdo systemd-resolved albo działającego nscd. Tryby: `cli <launcher> [raporty]` (scenariusze niżej), `run <raporty> <nazwa> -- <polecenie>`, `self-test` |
| `egress-reap.sh` | Uruchamia polecenie, potem zabija w jego przestrzeni PID wszystko, co zostawiło (np. serwer `adb`), żeby `strace -f` się zakończył; odmawia działania poza przestrzenią `egress-test.sh` |
| `check-forbidden-deps.sh` | Zakazane GAV w `runtimeClasspath` CLI: `com.posthog`, `org.seleniumhq.selenium`, `truffle-enterprise`, `nativebridge`, `project :maestro-ai`/`:maestro-web`; moduły w `settings.gradle.kts` |
| `check-licenses.py` + `allowed-licenses.tsv` | Skan licencji §1 `THIRD-PARTY-NOTICES.md` (z paczki) vs lista dozwolonych (36 napisów → SPDX, polityka `allowed`/`alternative-only`); napis spoza listy → FAIL |
| `test-ci-checks.sh` | Testy negatywne skanu licencji (nowa licencja GFTC, inny zapis znanej, tylko LGPL, pusta tabela, zła liczba wierszy) i kontroli zależności (PostHog, Selenium, truffle-enterprise, `maestro-ai`, `maestro-web`, plik, który nie jest drzewem Gradle) |
| `android-integration.sh` + `android-flows/` | Integracja Android (scenariusze niżej) |
| `android-driver-deps.py` | Lista bibliotek APK sterownika z licencjami z POM (cache Gradle albo Google Maven/Maven Central — czas builda, jak B-03) |
| `macos-sandbox-log.sh` | Odmowy `network-outbound` z logu systemowego macOS od podanej chwili dla danego procesu |
| `resolve-test-runtime.gradle` | Init script: task `resolveTestRuntime` pobiera wszystkie JAR-y testów (przed przebiegiem `--offline`) |

### Scenariusze

- **Linux bez sieci** (`egress-test.sh cli`, paczka z artefaktu, `HOME` i `user.home` w katalogu roboczym, bez `adb` i Android SDK):
  `--version`, `--help`, `check-syntax` poprawnego flow, niepoprawnego YAML (`Parsing Failed`), nieznanej komendy, `test` bez
  urządzenia (`Not enough devices connected (0)`, bez stack trace), **crash CLI** (`--debug-output` wskazuje plik → `The stack
  trace was:` + `Not a directory` lokalnie, nic nie jest wysyłane), sesja MCP zakończona EOF (`initialize`, `tools/list`,
  `cheat_sheet`, `list_devices` — 4 odpowiedzi, `serverInfo.name` = `tapcue`), `e2e/cli/test-cli.sh`; brak `~/.maestro`/`~/.mobiledev`.
- **Android** (`android-integration.sh`): `settings.yaml` na aplikacji Ustawienia (`launchApp` z `clearState`, `assertVisible`,
  `scrollUntilVisible`, `tapOn`, `back`, `scroll`, `inputText` w wyszukiwarce, `takeScreenshot`; JUnit + debug output), `hierarchy`,
  MCP z urządzeniem (`list_devices` zawiera emulator, `inspect_screen`, `take_screenshot` z obrazem), ścieżki błędów: brak
  elementu z timeoutem (`extendedWaitUntil` 5 s), `assertVisible` na brakującym elemencie, aplikacja niezainstalowana,
  niepoprawny YAML, **sterownik zabity w trakcie flow** (`am force-stop` aplikacji i instrumentacji sterownika — CLI musi
  zakończyć się błędem przed 150-sekundowym limitem flow).

### Poprawka produktu: `tapcue mcp` i EOF na STDIN (osobny punkt z handover STEP-05)

**Ocena:** SDK MCP (`StdioServerTransport` 0.11.1) po EOF na STDIN natychmiast zamyka sesję: anuluje odczyt, przetwarzanie
i wysyłkę, porzucając żądania odczytane, ale jeszcze bez odpowiedzi. Klient, który zapisuje żądania i od razu zamyka wejście
(`printf '…' | tapcue mcp`, skrypty, CI), nie dostawał żadnej odpowiedzi — potwierdzone na paczce z `main` (scenariusz `mcp`
w `egress-test.sh`: brak odpowiedzi na `id` 1–4). To błąd zachowania (JSON-RPC: każde żądanie dostaje odpowiedź), nie cecha.
**Poprawka:** `maestro.cli.mcp.PendingRequests` owija STDIN i STDOUT serwera: żądania (`method` + `id`) są zapisywane, gdy ich
linie przechodzą do SDK, odpowiedzi (`id` bez `method`) je czyszczą, a EOF dociera do transportu dopiero, gdy nic nie czeka;
`notifications/cancelled` zwalnia anulowane żądanie (serwer na nie nie odpowiada). Kod SDK bez zmian. Testy:
`PendingRequestsTest` (5) i `McpStdioTest` +1 (proces CLI: trzy żądania i natychmiastowy EOF → trzy odpowiedzi, kod 0).

### Usunięte / ocenione (B-06, B-07, B-08)

- `.github/upstream-workflows-disabled/` (14 workflowów upstream) i `.github/workflows/ci-baseline.yml` — B-06.
- `maestro-cli/src/test/mcp/` (ewaluacje LLM i testy `npx mcp-server-tester`) — B-08; zastępstwo: testy lokalne wyżej.
- `e2e/` (B-07): suita upstream **nie jest uruchamiana w CI forka** (wymaga binariów `demo_app` z GCS upstream i aplikacji
  z `manifest.txt`). Usunięte: `e2e/update_samples` (wysyłka do bucketu mobile.dev dla usuniętego `download-samples`),
  `e2e/workspaces/web` z `serve_fixtures`/`ensure_fixtures` (platforma web wyłączona, D-4; wiersz w `workspaces.txt`, gałąź
  `web` w `run_tests`, `web` w `validate_workspaces`). `download_apps` zostaje jako ręczna czynność dewelopera (opis w
  `e2e/README.md`). `e2e/cli/test-cli.sh`/`.ps1`: bez `MAESTRO_CLI_NO_ANALYTICS`, adres zgłoszeń TapCue; `.sh` działa w CI.

### Poprawki testów (T-02)

Testy jednostkowe pod `egress-test.sh` (lokalnie): jedyne próby wysyłał `maestro-client` — `AdbSocketFactoryTest`
i `AdbSocketTimeoutTest` tworzyły `InetSocketAddress("webview_devtools_remote_…", port)`, co rozwiązuje nazwę przez DNS
(144 zapytania do resolvera). Testy używają teraz `InetSocketAddress.createUnresolved` — tak jak kod produkcyjny, który
przekazuje nazwę gniazda przez `DummyDns` bez zapytania. Po poprawce: zero prób we wszystkich modułach.

### L-05 — biblioteki APK sterownika Android

`docs/audit/notices-supplement.md` §3.2 uzupełniony pełną listą przechodnią z CI (job `build`, krok „Android driver APK
libraries”); ocena licencji `play-services-location` (i pozostałych komponentów Google Play services) — **nie rozstrzygana**,
po stronie właściciela.

**Poza zakresem (bez zmian):** pakiety `maestro.*`, moduły Gradle, format flow, katalogi `~/.tapcue`/`.tapcue/`, zawartość
paczki, `Vendor/`, `verify-dist.sh`, wersja CLI `2.11.0` (0.1.0 — PR-08), release (STEP-07).

## 2. Walidacja

{{WALIDACJA}}

## 3. Ryzyka, blokery, znane problemy

{{RYZYKA}}

## 4. Warunek rozpoczęcia następnego kroku

PR-06 **scalony** do `main`; CI `ci.yml` zielone na `main` (wszystkie joby: `build`, `egress`, `android`, `macos`).
Otwarte po stronie właściciela (nie blokują STEP-07, blokują STEP-08 / G-3): przegląd `THIRD-PARTY-NOTICES.md`/suplementu,
ocena `play-services-location` i innych bibliotek Google w APK sterownika (L-05), akceptacja polityki
`scripts/fork/allowed-licenses.tsv` (CDDL+GPLv2-CPE jako `allowed`, LGPL-2.1 jako `alternative-only`).

## 5. Następny krok

**STEP-07: Release pipeline (PR-07).** Zakres w `docs/ROADMAP-0.1.md` §3 „STEP-07”.

## 6. PROMPT DLA NASTĘPNEGO AGENTA (STEP-07)

{{PROMPT}}
