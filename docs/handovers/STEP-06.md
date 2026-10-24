# Handover — STEP-06: GitHub Actions CI i testy offline

| Pole | Wartość |
|---|---|
| Krok | STEP-06 — **scalony** (PR #14, 2026-10-24) |
| PR | PR-06: https://github.com/rtnk/TapCue/pull/14 |
| Gałąź | `step-06-ci` |
| Gałąź docelowa | `main` |
| Baza | `main` @ `40df271` (merge PR #13 — STEP-05) |
| Commit SHA | `77571a7` (ostatni commit gałęzi), merge `52c7c38` na `main` |

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

Środowisko lokalne: Linux x86_64, JDK 17.0.20 (doinstalowany przez `apt`), brak Android SDK/Xcode/urządzeń/Maca,
`dl.google.com` zablokowane; `unshare`/`strace` dostępne (sesja jako root). Lokalnie — **wariant analityczny** (handover
STEP-00 §3: kopia w scratchpadzie bez `maestro-android` i `google()`, z mirrorem Maven Central jak w STEP-01 §2, niezatwierdzona).
**Bez flag `-x`.** CI — GitHub Actions na PR #14 (pełne repozytorium, Google Maven dostępny).

| Test | Komenda | Wynik |
|---|---|---|
| Wszystkie testy + detekt + archiwum (od zera) | `LANG=C.UTF-8 ./gradlew --continue --rerun-tasks :maestro-orchestra:detektMain :maestro-client:detektMain test :maestro-cli:macosArm64DistZip` | **PASS — 1324 testy, 0 porażek** (STEP-05: 1318; `maestro-cli` 159 = 153 + 5 `PendingRequestsTest` + 1 `McpStdioTest`; pozostałe bez zmian liczności) |
| Weryfikacja archiwum | `scripts/verify-dist.sh --smoke …/tapcue-2.11.0-macos-arm64.zip` | **PASS** — 142 pliki, 136 JAR-ów, 67 dozwolonych binariów, `SHA256SUMS` OK |
| Testy negatywne `verify-dist.sh` | `scripts/fork/test-verify-dist.sh …zip` | **PASS** — 13 mutacji odrzuconych |
| Strażnik simulator-server | `scripts/fork/check-no-simulator-server.sh` | **PASS** |
| Skan licencji | `python3 -I scripts/fork/check-licenses.py …zip` | **PASS** — 134 biblioteki, wszystkie licencje na liście |
| Testy negatywne skanu i kontroli zależności | `scripts/fork/test-ci-checks.sh …zip` | **PASS** — 5 mutacji `THIRD-PARTY-NOTICES.md` i 6 drzew zależności odrzuconych |
| Harness egress — wykrywalność | `scripts/fork/egress-test.sh self-test` | **PASS** — sondy `getent` (DNS do 8.8.8.8/8.8.4.4), TCP 1.1.1.1:443, `curl` (nscd/DNS), JVM `Socket.connect` 93.184.215.14:80 wykryte; loopback nie liczony. Uszkodzony harness (błędne sprawdzenie przestrzeni PID w `egress-reap.sh`) self-test **odrzucił** — FAIL na wszystkich sondach |
| Harness egress — launcher z dopisanym `curl` (kopia lokalna) | `sed -i '2i curl -s … https://example.com …' bin/tapcue; egress-test.sh cli …` | **FAIL zgodnie z oczekiwaniem** — wszystkie 9 scenariuszy odrzucone (zapytania DNS do 8.8.8.8:53) |
| CLI i MCP bez sieci, paczka z `main` (przed poprawką EOF) | `egress-test.sh cli <paczka z main>` | zero prób poza loopback; **scenariusz `mcp` FAIL** — brak odpowiedzi na `id` 1–4 (błąd EOF potwierdzony); `e2e/cli/test-cli.sh` FAIL (oczekiwał adresu zgłoszeń upstream — poprawione) |
| CLI i MCP bez sieci, paczka z gałęzi | `egress-test.sh cli <paczka>` | **PASS** — 9 scenariuszy, zero prób poza loopback (`test` bez urządzenia: 130 połączeń z `127.0.0.1:5037`, MCP: 260) |
| Testy jednostkowe bez sieci (T-02) | `egress-test.sh run … -- ./gradlew --offline --no-daemon :<moduł>:test --rerun …` | przed poprawką: **144 zapytania DNS** z `maestro-client` (`AdbSocket*Test`, bisekcja po pakietach i klasach); po poprawce: `maestro-client` 0 prób; `maestro-test`, `maestro-cli` 0 prób. `IntegrationTest` Case 112 padał pod obciążeniem (`timeout: 10` ms w flow) — po zmianie na 10 s PASS |
| CI — `build` | PR #14 | **PASS** (każdy przebieg) — testy, archiwum, `verify-dist`, testy negatywne, zależności (511 linii drzewa, zero zakazanych), licencje, lista APK (102 biblioteki, wszystkie z licencją w POM) |
| CI — `macos` | PR #14 | **PASS** (każdy przebieg) — paczka offline, rejestr: log pokazał odmowę `curl`, zero odmów `java`; MCP z EOF odpowiada; runner symulatora ze źródła bez pobrań (`checkouts` puste); `hierarchy` 126 KB |
| CI — `android` | PR #14, [run 40087871546](https://github.com/rtnk/TapCue/actions/runs/40087871546) i [40088621281](https://github.com/rtnk/TapCue/actions/runs/40088621281) | **PASS** w obu przebiegach (w trzecim, [40099657357](https://github.com/rtnk/TapCue/actions/runs/40099657357), `settings` padł na `scrollUntilVisible: System` w domyślnym limicie 20 s — limit 60 s i widoczność 50 %, wynik w ostatnim przebiegu PR) — emulator API 34 (Android 14): `settings` (flow przeszedł, 1 min 15 s), `hierarchy`, MCP z urządzeniem (`list_devices`: `emulator-5554`, AVD `test`), brak elementu/timeout, `assertVisible` na brakującym elemencie, aplikacja niezainstalowana („Package … is not installed”), niepoprawny YAML, zabity sterownik (błąd po 17–19 s; `maestro.log`: gRPC `UNAVAILABLE`). Jeden wcześniejszy przebieg: brak KVM na runnerze → kroki `skipped` z ostrzeżeniem (zadziałał mechanizm jawnego pominięcia) |
| CI — `egress` | PR #14 | **PASS** w [run 40099657357](https://github.com/rtnk/TapCue/actions/runs/40099657357) — self-test (sondy wykryte), launcher z `curl` odrzucony, 9 scenariuszy CLI/MCP bez prób, testy jednostkowe 8 modułów `--offline --no-build-cache --rerun` w izolacji bez prób. Wcześniejsze czerwone przebiegi tego joba: zawieszenie `strace -f` na demonie (`adb`) — reaper w przestrzeni PID; `sudo` gubił `JAVA_HOME` (Gradle na Javie 25) — `--preserve-env`; testy z cache buildu — `--no-build-cache --rerun`; Case 112 (limit 10 ms) — 10 s |
| E2E upstream (`e2e/run_tests`), Windows, macOS z `pf` | — | **NOT RUN** — suita upstream poza CI (B-07); `pf` i paczka z Release — STEP-08 |

Jak powtórzyć:

```bash
./gradlew :maestro-orchestra:detektMain :maestro-client:detektMain
./gradlew test --continue
./gradlew :maestro-cli:macosArm64DistZip
Z=maestro-cli/build/distributions/tapcue-*-macos-arm64.zip
scripts/verify-dist.sh --smoke $Z && scripts/fork/test-verify-dist.sh $Z
scripts/fork/check-forbidden-deps.sh && python3 -I scripts/fork/check-licenses.py $Z
scripts/fork/test-ci-checks.sh $Z build/reports/fork/cli-runtime-dependencies.txt
scripts/fork/check-no-simulator-server.sh
# Linux, root albo sudo; strace i iproute2:
scripts/fork/egress-test.sh self-test /tmp/egress-self-test
unzip -q $Z -d /tmp/tapcue && scripts/fork/egress-test.sh cli /tmp/tapcue/tapcue-*/bin/tapcue /tmp/egress
# z uruchomionym emulatorem Android:
scripts/fork/android-integration.sh /tmp/tapcue/tapcue-*/bin/tapcue /tmp/android
```

## 3. Ryzyka, blokery, znane problemy

- **Rejestr prób na Linux = `strace`.** Wykrywa wywołania `connect`/`sendto`/`sendmsg`/`sendmmsg` procesów z drzewa polecenia;
  nie widzi ruchu wykonanego w imieniu procesu przez inny, już działający proces spoza przestrzeni (np. `nscd`, demon `adb`
  uruchomiony wcześniej na hoście). Harness liczy połączenie z działającym `nscd` i gniazdem systemd-resolved jako próbę,
  a scenariusze CLI działają bez `adb` i Android SDK; testy z urządzeniem (job `android`) nie są pod rejestrem (ruch ADB/
  urządzenia — GRANICA).
- **macOS:** rejestr prób to odmowy sandboxa z logu systemowego; działa, jeśli log pokazuje odmowę sondy `curl` — inaczej
  krok kończy się ostrzeżeniem (nie zielonym fałszem co do rejestru, ale bez porażki joba). Pełna blokada `pf` — STEP-08.
- **KVM na runnerach `ubuntu-latest` w repozytorium prywatnym** bywa niedostępny (jeden przebieg) — wtedy job `android` jest
  zielony z krokami `skipped` i ostrzeżeniem; status gate'u G-2 trzeba czytać razem z podsumowaniem joba.
- **Zabity sterownik Android:** CLI kończy się kodem 1 bez czytelnego komunikatu na konsoli (szczegóły tylko w `maestro.log`:
  gRPC `UNAVAILABLE`) — zachowanie upstream, nie zmieniane w STEP-06; kandydat na poprawkę UX.
- **Flow Android na aplikacji Ustawienia** zależy od tekstów obrazu API 34 `google_apis`; zmiana obrazu może wymagać
  dostosowania `scripts/fork/android-flows/settings.yaml`.
- **Lista dozwolonych licencji** to dokładne napisy z POM — podbicie zależności, która zmienia zapis licencji, zatrzyma CI do
  czasu dopisania wpisu (zamierzone). Polityka (CDDL+GPLv2-CPE `allowed`, LGPL `alternative-only`) — do akceptacji właściciela.
- **L-05:** APK sterownika zawierają biblioteki Google Play services (Android SDK License, własnościowa) i JUnit 4 (EPL-1.0,
  APK instrumentacji); teksty licencji EPL/CDDL/BSD bibliotek APK nie są powielone w suplemencie — decyzja i przegląd
  właściciela przed STEP-08 (G-3).
- **`PendingRequests`** czeka na odpowiedź na każde żądanie przed przekazaniem EOF; żądanie, na które serwer nigdy nie
  odpowie (wbrew JSON-RPC), zatrzymałoby wyjście procesu po EOF (do sygnału). Anulowanie (`notifications/cancelled`) jest
  obsłużone.
- **Upstream `e2e/`** zostaje w repozytorium bez uruchamiania (B-07); `download_apps` pobiera aplikacje z bucketu mobile.dev,
  jeśli deweloper sam go uruchomi.
- **Case 112** (`maestro-test`): limit `scrollUntilVisible` w fixture podniesiony z 10 ms do 10 s; Case 079 (ten sam limit
  10 ms) przechodzi także pod obciążeniem, bo jego wariant „bez widoku” oczekuje porażki, a „z widokiem” znajduje element
  przy pierwszym sprawdzeniu — zostawiony bez zmian.

## 4. Warunek rozpoczęcia następnego kroku

PR-06 **scalony** do `main` (2026-10-24, merge `52c7c38`); CI `ci.yml` zielone na `main` — [run 40103408112](https://github.com/rtnk/TapCue/actions/runs/40103408112)
(`workflow_dispatch` na `52c7c38`): `build`, `egress`, `android` (emulator uruchomiony, „Android integration passed”, kroki nie
pominięte), `macos` — wszystkie **success** (sprawdzone na początku STEP-07). **Warunek spełniony — STEP-07 rozpoczęty.**
Otwarte po stronie właściciela (nie blokują STEP-07, blokują STEP-08 / G-3): przegląd `THIRD-PARTY-NOTICES.md`/suplementu,
ocena `play-services-location` i innych bibliotek Google w APK sterownika (L-05), akceptacja polityki
`scripts/fork/allowed-licenses.tsv` (CDDL+GPLv2-CPE jako `allowed`, LGPL-2.1 jako `alternative-only`).

## 5. Następny krok

**STEP-07: Release pipeline (PR-07).** Zakres w `docs/ROADMAP-0.1.md` §3 „STEP-07”.

## 6. PROMPT DLA NASTĘPNEGO AGENTA (STEP-07)

```text
Jesteś agentem kodującym w repozytorium rtnk/TapCue (fork mobile-dev-inc/Maestro → „TapCue”, polecenie `tapcue`).
Nie masz pamięci poprzednich sesji. Zanim cokolwiek zmienisz:

1. git fetch origin && git checkout main && git pull origin main. Potwierdź przez GitHub, że PR-06
   („STEP-06: GitHub Actions CI and offline tests”, #14) jest MERGED, a workflow `ci.yml` na `main` zielony (joby `build`,
   `egress`, `android`, `macos`; sprawdź w podsumowaniu `android`, że kroki nie zostały pominięte z braku KVM). Jeśli nie —
   zatrzymaj się i zgłoś to.
2. Przeczytaj: AGENTS.md (nota forka), docs/ROADMAP-0.1.md (§1, §2 — D-9, STEP-07, STEP-08, gate'y G-1…G-8, AT-01…AT-11),
   docs/handovers/STEP-06.md, docs/RELEASING.md, docs/NETWORK-AUDIT.md („Wyniki weryfikacji”, B-03, B-11), docs/LICENSE-AUDIT.md
   (§3–§5, L-05), docs/ARCHITECTURE-LOCAL.md §5, .github/workflows/ci.yml, scripts/verify-dist.sh, scripts/fork/egress-test.sh,
   scripts/fork/check-licenses.py, scripts/fork/macos-sandbox-log.sh.
   Otwarte po stronie właściciela (nie rozstrzygaj sam): przegląd THIRD-PARTY-NOTICES.md/suplementu, ocena Google Play services
   i JUnit (EPL-1.0) w APK sterownika (L-05), akceptacja polityki scripts/fork/allowed-licenses.tsv, upublicznienie repozytorium (D-9).
3. Wykonaj WYŁĄCZNIE STEP-07 — Release pipeline — na gałęzi step-07-release:
   - `.github/workflows/release.yml`: `push: tags: ['v[0-9]+.[0-9]+.[0-9]+']` + `workflow_dispatch` (tryb walidacji bez
     publikacji), `permissions: contents: read` na poziomie workflow, akcje przypięte do SHA, zero `pull_request_target`.
     Job build na `macos-*` arm64 (asercja `uname -m` = arm64), checkout dokładnie otagowanego commita (weryfikacja, że tag
     wskazuje commit z `main`), testy, `macosArm64DistZip`, `verify-dist.sh --smoke`, skan licencji, test offline paczki
     (wzorzec: job `macos` w ci.yml z rejestrem odmów sandboxa; opcjonalnie `egress-test.sh` na Linux z tej samej paczki),
     `SHA256SUMS`, upload artefaktu; zgodność wersji w nazwie archiwum (`CLI_VERSION`) z tagiem.
   - Job publish (`needs: build`, jedyny z `contents: write`, `GITHUB_TOKEN`): Release tylko z tagu i tylko, jeśli Release/tag
     nie istnieje (bez nadpisywania); w trybie `workflow_dispatch` nic nie publikuje. Notatki wydania z sekcji CHANGELOG.
   - Powtarzalność: sprawdź, czy archiwum z `release.yml` ma ten sam SHA-256 co z `ci.yml` dla tego samego commita (lub opisz,
     dlaczego nie — inne JDK/system); udokumentuj odtworzenie builda (dokładna wersja JDK).
   - docs/RELEASING.md: pełna procedura (przygotowanie, tag, weryfikacja Release, procedury awaryjne: wycofanie Release,
     nowy patch zamiast nadpisania). Nie twórz tagów `v*` ani Release w tym kroku (tylko `workflow_dispatch` w trybie walidacji).
   - Nie zmieniaj nazw pakietów `maestro.*`, modułów Gradle, formatu flow, katalogów `~/.tapcue`/`.tapcue/`, zawartości paczki,
     `Vendor/`, wersji CLI (`2.11.0` → `0.1.0` dopiero w PR-08) ani ci.yml poza tym, czego wymaga release.
4. Gradle uruchamiaj BEZ flag -x. Jeśli środowisko blokuje dl.google.com, użyj wariantu analitycznego z
   docs/handovers/STEP-00.md §3 (bez commitowania; mirror Maven Central jak w STEP-01 §2) i polegaj na CI.
5. Testy: `release.yml` faktycznie uruchomiony przez `workflow_dispatch` na gałęzi (link do runu; uwaga: dispatch działa
   dopiero, gdy workflow jest na gałęzi domyślnej — jeśli nie, opisz to i uruchom tryb walidacji inaczej, np. tymczasowym
   wyzwalaczem `pull_request` w trybie walidacji, usuniętym przed merge, albo jako job w ci.yml); test negatywny: tryb walidacji
   nie tworzy Release; publish nie nadpisuje istniejącego Release. Regresja: `./gradlew test` (STEP-06: 1324 testy),
   `scripts/fork/check-no-simulator-server.sh`, `scripts/verify-dist.sh --smoke`, `scripts/fork/test-verify-dist.sh`,
   `scripts/fork/test-ci-checks.sh`, `scripts/fork/egress-test.sh self-test`; CI `ci.yml` zielone na PR.
6. Zaktualizuj docs/ROADMAP-0.1.md (status: STEP-06 → MERGED (#14); STEP-07 — realizacja), docs/handovers/STEP-06.md
   (status „scalony”, §4), docs/RELEASING.md, docs/NETWORK-AUDIT.md / docs/LICENSE-AUDIT.md (jeśli dotyczy),
   docs/ARCHITECTURE-LOCAL.md §5, CHANGELOG.md (sekcja forka), AGENTS.md (nota forka), dodaj docs/handovers/STEP-07.md
   wg szablonu STEP-06.md (z promptem dla STEP-08).
7. Utwórz PR „STEP-07: Release pipeline” do main (cel, zakres, usunięte/dodane/zachowane, pliki, wyniki testów z komendami —
   tylko faktycznie uruchomione, ryzyka, link do handover, checklista, prompt dla STEP-08). Bez auto-merge. Doprowadź CI PR
   do zielonego.
8. Zakończ odpowiedzią: krok, URL PR, walidacja PASS/FAIL/NOT RUN, ryzyka, warunki następnego kroku,
   prompt na kolejną sesję. Nie zaczynaj STEP-08.
```
