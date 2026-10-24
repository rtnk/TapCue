# Handover — STEP-03B: Network Policy for Flow Features

| Pole | Wartość |
|---|---|
| Krok | STEP-03B — **scalony** (#8, 2026-10-24) |
| PR | PR-03B: https://github.com/rtnk/TapCue/pull/8 |
| Gałąź | `step-03b-flow-network-policy` |
| Gałąź docelowa | `main` |
| Baza | `main` @ `3b71500` (merge PR #7 — uzupełnienie STEP-03) |
| Commit SHA | `3fe9b65` (head gałęzi `step-03b-flow-network-policy`); merge do `main`: `c0c143d` |

## 1. Co zrobiono

Cel: brak ścieżki, w której host CLI wykonuje żądanie do publicznego internetu z treści flow (NETWORK-AUDIT N-25,
N-26, N-28, N-29, N-30, T-01; LICENSE-AUDIT L-03). Decyzje właściciela D-3, D-4, D-6 (ROADMAP §2).

| Obszar | Zmiana |
|---|---|
| JS `http.*` (D-3) | nowa klasa `maestro.js.LocalNetworkPolicy` (`maestro-client`). Dozwolone: `127.0.0.0/8`, `::1`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `fc00::/7`, `169.254.0.0/16`, `fe80::/10` (IPv4-mapped `::ffff:a.b.c.d` jak IPv4). Trzy warstwy: (1) `GraalJsHttp.executeRequest` sprawdza URL **przed** przekazaniem do OkHttp — nazwa jest rozwiązywana i każdy adres musi być dozwolony; (2) klient skryptów (`httpClient.newBuilder()` — także gdy `Orchestra`/test poda własny) dostaje `Dns` z tą samą regułą, więc każdy skok przekierowania na nazwę jest sprawdzany przed połączeniem; (3) `SocketFactory`, której gniazda odrzucają niedozwolony adres przed `connect` (literały IP, których OkHttp nie rozwiązuje, np. przekierowanie na `http://93.184.215.14/`). Klient i gniazda łączą się z `Proxy.NO_PROXY` (zwykły `Socket()` pytał `ProxySelector` o SOCKS). Przekierowania zostają włączone (zachowanie upstream). Naruszenie → `NetworkPolicyViolation` (`IOException`) → błąd skryptu `JsEvaluationException` „Blocked request to <url>: <adres> is not a loopback, private or link-local address. Maestro Local lets JavaScript http requests reach only …”; nierozwiązywalna nazwa → „Cannot resolve '<host>' …”. Bez flagi opt-in. `GraalJsEngine`/`GraalJsHttp` mają nowy parametr `networkPolicy` (domyślnie systemowy resolver) |
| Platforma web (D-4) | usunięte: `maestro.drivers.CdpWebDriver`, `maestro.drivers.WebDriver`, `Maestro.web`, moduł `maestro-web` (`settings.gradle.kts`), `maestro.cli.web.WebInteractor` (nieużywany), zależności `selenium-java`/`selenium-devtools-v147` (z `selenium-manager`) w `maestro-client`, `jcodec` w `maestro-client` (używany tylko przez web), `MaestroException.UnsupportedJavaVersion` (rzucany tylko przez `Maestro.web`), `DeviceService.listWebDevices` i parametr `includeWeb`. Wpisy `selenium*`, `ktor-serial-json`, `ktor-client-content-negotiation` usunięte z `libs.versions.toml` (używał ich tylko `maestro-web`). Nowe `MaestroException.WebPlatformNotSupported` („The web platform is not supported in Maestro Local 0.1.0. …”), wypisywane bez stack trace. Rzucane przez: `TestCommand` (flow z `url:` w planie — przed listowaniem urządzeń), `RecordCommand`, `MaestroSessionManager` (`--platform web`, `--device chromium`), `StartDeviceCommand.buildDeviceSpec`, `DeviceCreateUtil`, `PickDeviceView`/`PickDeviceInteractor`, `DeviceService.startDevice`, `McpMaestroSessionManager`. `list-devices` i MCP `list_devices` nie pokazują przeglądarki; opisy `--platform` i instrukcje MCP bez web. `--headless`/`--screen-size` w `maestro test` — ukryte no-op (zgodność skryptów). Model danych (`Platform.WEB`, `DeviceSpec.Web`, `WebLocale`, `Device.DeviceType.BROWSER`, `AppMetadataAnalyzer`) zostaje: istniejące pliki i konfiguracje dalej się parsują, a usunięcie wymagałoby przebudowy schematów i serializacji |
| Zależności przechodnie | `maestro-client` brał `kotlinx-coroutines-core` przechodnio przez `maestro-web` (ktor `api`) — dodane jawne `implementation(libs.kotlinx.coroutines.core)` (rozwiązywane do tej samej 1.10.2). Inne moduły nie korzystały z `maestro-web`/Selenium (kompilacja wszystkich modułów i testów PASS) |
| GraalJS (D-6) | `maestro-client`: `org.graalvm.js:js-community` + `js-language` + `org.graalvm.polyglot:polyglot` 24.2.0 (UPL) zamiast `org.graalvm.js:js` (dokładał `truffle-enterprise` i `nativebridge`, Oracle GFTC) i nieużywanego `js-scriptengine`. Bez kompilatora JIT GraalJS działa w interpreterze; `GraalJsEngine` ustawia `engine.WarnInterpreterOnly=false`, żeby jednorazowe ostrzeżenie nie trafiało na stderr |
| `openLink`/`launchApp` na urządzeniach | udokumentowane jako GRANICA (`ARCHITECTURE-LOCAL.md` §4, NETWORK-AUDIT N-29 i sekcja „Granica”): CLI przekazuje tylko URL przez ADB `am start` / `simctl openurl`, ruch wykonuje urządzenie |
| T-01 | `multipart_request_file_script.js` wysyła na `UPLOAD_URL` z env flow zamiast `https://eu.httpbin.org/anything`; `IntegrationTest` Case 136 uruchamia `MockWebServer` i sprawdza żądanie (POST, `multipart/form-data`, `imagefile` = `abc.png`, pole `name` = `logo`). `maestro-test` dostał `testImplementation(libs.square.mock.server)` |
| Dokumenty | ROADMAP (status, D-3/D-4/D-6 — realizacja, STEP-03 #7, STEP-03B, R-3), NETWORK-AUDIT (wersja 5), LICENSE-AUDIT (wersja 4: L-03, selenium-manager, §4), ARCHITECTURE-LOCAL §1, §3–§5, CHANGELOG, AGENTS.md (nota forka, mapa modułów bez `maestro-web`) |

**Dodane testy:**
- `maestro-client` `LocalNetworkPolicyTest` (37): 15 adresów dozwolonych, 18 zablokowanych (publiczne, `0.0.0.0`, `::`,
  granice `172.15/172.32`, `100.64/10`, multicast, broadcast, `fec0::/10`, NAT64, `::ffff:8.8.8.8`…); nazwa dozwolona tylko gdy
  **każdy** adres jest dozwolony (także w `Dns`); literały bez DNS; nierozwiązywalna nazwa; gniazdo odrzuca adres publiczny
  przed `connect` (`isConnected == false`) i łączy się z loopback.
- `maestro-test` `JsHttpNetworkPolicyTest` (10, przez prawdziwy `GraalJsEngine`): `http.get` do `MockWebServer` na 127.0.0.1
  działa; `http.get('https://example.com')` (resolver testowy → `93.184.215.14`) → błąd bez `callStart`/`connectStart`
  i bez wywołania `ProxySelector` (który rzuca przy każdej próbie); każda metoda (`get/post/put/delete/request`) do adresu
  publicznego → błąd bez prób; nazwa → 127.0.0.1 działa (nagłówek `Host` zachowany); nazwa z adresem prywatnym i publicznym
  → błąd; nierozwiązywalna nazwa → błąd; przekierowanie 302 na publiczny literał → błąd, jedyne nawiązane połączenie
  (`connectEnd`) to 127.0.0.1, a każda nieudana próba to `NetworkPolicyViolation` z gniazda; przekierowanie 301 na
  publiczną nazwę → błąd, jedyne połączenie lokalne; przekierowanie w sieci lokalnej działa; domyślny `GraalJsEngine()`
  też stosuje politykę.
- `maestro-cli` `WebPlatformDisabledTest` (7): `test` z flow web i z workspace mieszanym, `record` z flow web → kod 1,
  komunikat, bez stack trace, zero wywołań `ProxySelector`; `start-device --platform web`/`DeviceCreateUtil` i
  `DeviceService.startDevice` (chromium) → `WebPlatformNotSupported`; klasy sterownika web, `maestro.web.*`,
  `WebInteractor` i Selenium nieobecne; na classpath testów (nadzbiór `runtimeClasspath` CLI) brak JAR-ów `selenium*`,
  `maestro-web`, `truffle-enterprise` i wpisów `org/openqa/`, `maestro/web/`, `com/oracle/truffle/enterprise/`.
- `maestro-test` `IntegrationTest` Case 136 — przepisany (wyżej).

**Usunięte testy:** `maestro-client` `CdpWebDriverTest` (7), `WebDriverTest` (8), cały `maestro-web` (18, w tym
`WebScreenRecorderTest`) — dotyczyły usuniętego kodu; `maestro-cli` `TestCommandTest`: 6 testów `allFlowsAreWebFlow`
(funkcja usunięta; ostatni z nich, mylnie nazwany, sprawdzał `executionPlanIncludesWebFlow` i dublował nazwę istniejącego).
Testy `executionPlanIncludesWebFlow` zostają (funkcja wykrywa flow web, żeby je odrzucić).

**Poza zakresem:** `cheat_sheet` (N-15) i link `docs.maestro.dev` w instrukcjach MCP (N-17), pakiet `mcp/viewer/` — STEP-04;
nazwa TapCue, polecenie `tapcue`, katalogi (D-1/D-2) — STEP-04B; README/CONTRIBUTING upstream (nadal opisują web i
`maestro-web`/`maestro-ai`) — STEP-08; fixture'y `e2e/workspaces/web`, `run_tests web`, `e2e/serve_fixtures` (uruchamiane tylko
przez zaparkowany workflow upstream) — STEP-06. `maestro-web.js` w zasobach `maestro-client` zostaje: używa go
`DadbChromeDevToolsClient` (hierarchia WebView na Androidzie przez ADB, N-33).

## 2. Walidacja

Środowisko: Linux x86_64, JDK 17.0.20 (doinstalowany przez `apt`), brak Android SDK/Xcode/urządzeń,
`dl.google.com` zablokowane. Lokalnie — **wariant analityczny** (handover STEP-00 §3: kopia w scratchpadzie bez
`maestro-android` i `google()`) z mirrorem Maven Central jak w STEP-01 §2. **Bez flag `-x`.**

| Test | Komenda | Wynik |
|---|---|---|
| Wszystkie testy jednostkowe | `LANG=C.UTF-8 ./gradlew test --continue` | **PASS — 1282 testy, 0 porażek** (STEP-03: 1265 z 1 porażką T-01). **T-01 rozwiązany**: `IntegrationTest` przechodzi bez internetu |
| ↳ `maestro-cli` | jw. | **PASS**, 124 (STEP-03: 123; −6 `TestCommandTest`, +7 `WebPlatformDisabledTest`) |
| ↳ `maestro-client` | jw. | **PASS**, 324 (2 skipped) (STEP-03: 302; −15 testów sterownika web, +37 `LocalNetworkPolicyTest`) |
| ↳ `maestro-test` | jw. | **PASS**, 233 (STEP-03: 223 z 1 FAIL; +10 `JsHttpNetworkPolicyTest`) |
| ↳ pozostałe | jw. | **PASS**: `maestro-orchestra` 456 (3 skipped), `maestro-orchestra-models` 51, `maestro-ios-driver` 47, `maestro-ios` 2, `maestro-utils` 45; `maestro-web` — moduł usunięty (było 18) |
| Detekt + dystrybucja | `./gradlew :maestro-orchestra:detektMain :maestro-client:detektMain :maestro-cli:installDist :maestro-cli:shadowJar` | **PASS** |
| Graf zależności | `./gradlew :maestro-cli:dependencies --configuration runtimeClasspath` na `main` i na gałęzi, diff | **PASS**: znikają `org.graalvm.js:js`, `js-scriptengine`, `org.graalvm.truffle:truffle-enterprise`, `org.graalvm.sdk:nativebridge`, 17 artefaktów `org.seleniumhq.selenium:*` (w tym `selenium-manager`), 11 `io.opentelemetry:*`, `net.bytebuddy:byte-buddy`, `com.google.auto.service:auto-service-annotations`, `org.jspecify:jspecify`, ktor `content-negotiation`/`serialization-kotlinx(-json)`. Guava rozwiązuje się teraz do `32.0.1-android` (wersja żądana przez gRPC/protobuf; `33.5.0-jre` wymuszało tylko Selenium; kod forka nie używa Guavy bezpośrednio), `failureaccess` 1.0.1, `j2objc-annotations` 2.8, `checker-qual` 3.33.0. `kotlinx-serialization` i `kotlinx-coroutines` bez zmian (1.10.0 / 1.10.2) |
| Zawartość archiwów | rozpakowany `maestro-cli-*-all.jar`, `install/maestro/lib` | **PASS**: 0 wpisów `org/openqa/`, `maestro/web/`, `com/oracle/truffle/enterprise/`, `io/opentelemetry/`, `maestro/drivers/CdpWebDriver*`, `maestro/drivers/WebDriver*`, `maestro/cli/web/`, `selenium-manager`; `lib/` ma `js-language`, `polyglot`, `truffle-api`, `truffle-runtime`, `truffle-compiler` 24.2.0, bez `truffle-enterprise`, `nativebridge`, `selenium*` |
| Strażnik | `scripts/fork/check-no-simulator-server.sh` (w kopii z wynikami builda) | **PASS** |
| Smoke CLI (`strace -f -e trace=connect`) | `test webflow.yaml`, `record webflow.yaml`, `start-device --platform web`, `--platform web hierarchy`, `list-devices` | **PASS**: pierwsze cztery → komunikat „The web platform is not supported in Maestro Local 0.1.0 …”, kod 1, bez stack trace; `test`/`record`/`start-device` — 0 wywołań `connect` `AF_INET*`; `hierarchy` i `list-devices` — tylko loopback (próby ADB na 127.0.0.1), 0 adresów innych; `list-devices` → „No devices found”, kod 0 |
| CI GitHub Actions (`ci-baseline.yml`, pełne repo) | PR-03B [#8](https://github.com/rtnk/TapCue/pull/8), [run 40029509069](https://github.com/rtnk/TapCue/actions/runs/40029509069) na `3fe9b65` | **PASS**; `main` po merge (`c0c143d`): [run 40067278323](https://github.com/rtnk/TapCue/actions/runs/40067278323) — **PASS** |
| Smoke z urządzeniem (flow z `runScript` + `http.*`), Swift | — | **NOT RUN**: brak SDK/Xcode/urządzeń; ścieżka JS jest ta sama co w `IntegrationTest`/`JsHttpNetworkPolicyTest` |

Jak powtórzyć:

```bash
./gradlew :maestro-orchestra:detektMain :maestro-client:detektMain
./gradlew test --continue
./gradlew :maestro-cli:dependencies --configuration runtimeClasspath | grep -cE 'selenium|truffle-enterprise'   # 0
./gradlew :maestro-client:test --tests 'maestro.js.LocalNetworkPolicyTest' \
          :maestro-test:test --tests 'maestro.test.JsHttpNetworkPolicyTest' --tests 'maestro.test.IntegrationTest' \
          :maestro-cli:test --tests 'maestro.cli.WebPlatformDisabledTest'
scripts/fork/check-no-simulator-server.sh
```

## 3. Ryzyka, blokery, znane problemy

- **Zmiana zachowania `http.*`:** skrypty flow, które wołały publiczne API (np. seed danych testowych w chmurze, webhooki,
  `httpbin`), kończą się teraz błędem. To zamierzone (D-3), ale jest to najbardziej widoczna zmiana kroku dla użytkowników
  upstream. Do skrótu w README/Release notes (STEP-08). Adresy CGNAT (`100.64.0.0/10`, np. Tailscale) są blokowane — poza
  listą D-3.
- **DNS:** nazwa hosta jest rozwiązywana przez systemowy resolver przed sprawdzeniem (zapytanie DNS może wyjść do
  resolvera skonfigurowanego w systemie, także publicznego). Samo żądanie HTTP nie wychodzi. Dla literałów IP nie ma
  zapytania DNS.
- **Proxy:** `http.*` w skryptach ignoruje systemowe proxy (HTTP i SOCKS). Usługa w sieci prywatnej osiągalna tylko przez
  proxy nie zadziała.
- **GraalJS bez JIT:** artefakty community na zwykłym JDK działają w interpreterze. Dla krótkich skryptów flow różnica jest
  pomijalna (testy `maestro-test` bez zmian czasu), ale ciężkie obliczenia w `runScript` mogą być wolniejsze niż w upstream.
  Ewentualny JIT (`org.graalvm.compiler:compiler` + `--upgrade-module-path`) — poza 0.1.0.
- **Web:** flow z nagłówkiem `url:` przestają działać. `Platform.WEB`/`DeviceSpec.Web` zostają w modelu — jeśli kiedyś web
  wróci, wejścia rzucające `WebPlatformNotSupported` są jedynym miejscem do zmiany. Fixture'y `e2e/workspaces/web` nadal są
  w repo (STEP-06).
- **API biblioteki:** usunięte `Maestro.web`, `CdpWebDriver`, `WebDriver`, `MaestroException.UnsupportedJavaVersion`,
  parametr `includeWeb` w `DeviceService`, moduł `maestro-web`; `MaestroSessionManager.newSession` bez `isHeadless`/`screenSize`.
- **Guava 32.0.1-android** zamiast 33.5.0-jre w runtime CLI (patrz §2) — testy zielone; ryzyko niskie.

## 4. Warunek rozpoczęcia następnego kroku

PR-03B **scalony** do `main` (#8, `c0c143d`), CI `ci-baseline.yml` zielone na `main` ([run 40067278323](https://github.com/rtnk/TapCue/actions/runs/40067278323)) — spełnione.

## 5. Następny krok

**STEP-04: Local-only MCP i usunięcie zamkniętego Viewera (PR-04).** Zakres w `docs/ROADMAP-0.1.md` §3 „STEP-04”.

## 6. PROMPT DLA NASTĘPNEGO AGENTA (STEP-04)

```text
Jesteś agentem kodującym w repozytorium rtnk/TapCue (fork mobile-dev-inc/Maestro → „Maestro Local”).
Nie masz pamięci poprzednich sesji. Zanim cokolwiek zmienisz:

1. git fetch origin && git checkout main && git pull origin main. Potwierdź przez GitHub, że PR-03B
   („STEP-03B: Network policy for flow features”) jest MERGED. Jeśli nie — zatrzymaj się i zgłoś to.
2. Przeczytaj: AGENTS.md (nota forka), docs/ROADMAP-0.1.md (§1, §2, STEP-04), docs/handovers/STEP-03B.md,
   docs/NETWORK-AUDIT.md (N-15, N-17, N-18, B-02, B-08), docs/LICENSE-AUDIT.md (L-01, L-10),
   docs/ARCHITECTURE-LOCAL.md §3–§5.
3. Wykonaj WYŁĄCZNIE STEP-04 — Local-only MCP i usunięcie zamkniętego Viewera — na gałęzi step-04-local-mcp:
   - Zachowaj narzędzia MCP: list_devices, inspect_screen, take_screenshot, run, cheat_sheet.
   - cheat_sheet: treść jako wersjonowany zasób w JAR (np. resources/mcp/cheat-sheet.md), napisana dla forka na
     podstawie dokumentacji w repozytorium — bez żądania do api.copilot.mobile.dev (N-15) i bez kopiowania treści
     o nieznanej licencji z docs.maestro.dev.
   - Instrukcje serwera (McpServer.kt): bez linku do docs.maestro.dev (N-17); platforma web jest już wyłączona (STEP-03B).
   - Usuń pakiet mcp/viewer/ (McpViewerDriver/McpViewerEvents/StreamDeviceType używane przez RunTool
     i McpMaestroSessionManager → zwykły Orchestra/Driver), katalog maestro-cli/mcp-viewer/ (+ .nvmrc),
     Unpacker.unpackTree (nieużywane), zdecyduj o ukrytych --no-viewer/--viewer-port (usunąć albo zostawić no-op).
   - Nie realizuj D-1/D-2 (TapCue, polecenie tapcue, katalogi) — to STEP-04B. Nie ruszaj dystrybucji (STEP-05).
4. Gradle uruchamiaj BEZ flag -x. Jeśli środowisko blokuje dl.google.com, użyj wariantu analitycznego z
   docs/handovers/STEP-00.md §3 (bez commitowania; mirror Maven Central jak w STEP-01 §2) i polegaj na CI.
   Po usunięciu kodu sprawdź, czy inne moduły nie korzystały z niego przechodnio (w STEP-03B maestro-client
   brał kotlinx-coroutines przez maestro-web).
5. Testy: test JSON-RPC na STDIO (proces CLI lub serwer in-process): initialize → tools/list (dokładnie zachowane
   narzędzia) → tools/call cheat_sheet (zasób lokalny, zero połączeń: ProxySelector/MockWebServer jak w
   NoTelemetryTest) → list_devices bez urządzeń (kontrolowany wynik) → zamknięcie STDIN kończy proces; stdout
   zawiera wyłącznie ramki JSON-RPC. CliJarContentsTest: wyjątek dla CheatSheetTool (api.copilot.mobile.dev)
   usunięty, brak mcp-viewer/**. Regresja: maestro-cli (NoTelemetryTest, LocalOnlyCliTest, NoRemoteAiTest,
   WebPlatformDisabledTest, McpServerTest), maestro-test, scripts/fork/check-no-simulator-server.sh.
6. Zaktualizuj docs/ROADMAP-0.1.md (status), docs/NETWORK-AUDIT.md (N-15, N-17, N-18, B-02), docs/LICENSE-AUDIT.md
   (L-10, jeśli dotyczy), docs/ARCHITECTURE-LOCAL.md §3–§5, CHANGELOG.md (sekcja forka), dodaj
   docs/handovers/STEP-04.md wg szablonu STEP-03B.md (z promptem dla STEP-04B).
7. Utwórz PR „STEP-04: Local-only MCP and Viewer removal” do main (cel, zakres, usunięte/dodane/zachowane, pliki,
   wyniki testów z komendami — tylko faktycznie uruchomione, audyt sieci, ryzyka, link do handover, checklista,
   prompt dla STEP-04B). Bez auto-merge. Doprowadź CI PR do zielonego.
8. Zakończ odpowiedzią: krok, URL PR, walidacja PASS/FAIL/NOT RUN, ryzyka, warunki następnego kroku,
   prompt na kolejną sesję. Nie zaczynaj STEP-04B.
```
