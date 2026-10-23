# Architektura lokalna — Maestro Local (repozytorium `rtnk/TapCue`)

Wersja: **0 (STEP-00)** — opis stanu wyjściowego (upstream `v2.11.0`) i architektury docelowej
0.1.0. Sekcja „Stan docelowy” jest planem; każdy krok aktualizuje sekcję „Stan bieżący”.

> Ten projekt jest niezależnym forkiem [mobile-dev-inc/Maestro](https://github.com/mobile-dev-inc/Maestro)
> (Apache-2.0). Nie jest produktem mobile.dev inc. ani Software Mansion S.A. i nie korzysta z ich usług.

## 1. Moduły (stan wyjściowy)

| Moduł Gradle | Rola | Los w 0.1.0 |
|---|---|---|
| `maestro-utils` | narzędzia wspólne, `HttpClient` (OkHttp), `Metrics` (Micrometer, domyślnie NoOp), `TempFileHandler` | zostaje |
| `maestro-proto` | definicje protobuf (wire format sterownika Android) | zostaje |
| `maestro-client` | SDK hosta sterujące urządzeniami: `AndroidDriver`, `IOSDriver`, `CdpWebDriver`/`WebDriver`, silnik JS (GraalJS), `DeviceService`, `ApiKey` (token Cloud), `MockInteractor` (Cloud) | zostaje; usunąć `ApiKey`, `MockInteractor`; ograniczyć `GraalJsHttp` i sterownik web |
| `maestro-android` | sterownik on-device (APK `maestro-app.apk`, `maestro-server.apk`, budowane z tego źródła, zatwierdzone w `maestro-client/src/main/resources`) | zostaje |
| `maestro-ios-xctest-runner` | Swift XCTest runner (źródło); prekompilowane ZIP-y w `maestro-ios-driver/src/main/resources` | zostaje (decyzja D-5 o prekompilowanych ZIP) |
| `maestro-ios-driver` | host-side klient XCTest (HTTP na `127.0.0.1`), `LocalSimulatorUtils` (simctl, applesimutils) | zostaje |
| `maestro-ios` | glue iOS | zostaje |
| `maestro-orchestra-models` | modele komend (w tym `AssertWithAICommand`, `AssertNoDefectsWithAICommand`, `ExtractTextWithAICommand`) | zostaje bez komend AI |
| `maestro-orchestra` | interpretacja komend (`Orchestra.kt`), parser YAML, planowanie workspace; `api(project(":maestro-ai"))` | zostaje bez AI |
| `maestro-ai` | klienci OpenAI/Anthropic/Cloud AI | **usunięty** (STEP-02) |
| `maestro-web` | CDP/Selenium | zostaje warunkowo (decyzja D-4) |
| `maestro-cli` | CLI (picocli), serwer MCP (STDIO), raporty, nagrywanie, Viewer | zostaje bez Cloud/AI/telemetrii/Viewera |
| `maestro-test` | testy integracyjne z `FakeDriver` | zostaje; rozbudowany o testy offline |

Graf (uproszczony): `maestro-cli → maestro-orchestra → {maestro-client, maestro-orchestra-models, maestro-ai, maestro-utils}`,
`maestro-cli → {maestro-ios, maestro-ios-driver}`, `maestro-client → {maestro-proto, maestro-ios-driver, maestro-web, maestro-utils}`.

## 2. Punkty styku z usługami zdalnymi (stan wyjściowy)

Szczegóły i dowody: [`NETWORK-AUDIT.md`](NETWORK-AUDIT.md). W skrócie, zdalne usługi są splecione
z lokalną ścieżką wykonania w kilku miejscach, które trzeba rozciąć w odpowiedniej kolejności:

1. **`App.main()`** — przed wykonaniem jakiejkolwiek komendy: komunikat i zapis zgody analityki,
   asynchroniczne sprawdzenie wersji; po wykonaniu: zdarzenie PostHog, powiadomienie o „Analyze with AI”,
   blokujące (≤3 s) sprawdzenie aktualizacji i changelog; w obsłudze błędów — `ErrorReporter`.
2. **`TestCommand`** — pola instancji tworzą `ApiClient` i `Auth`, odczytują token Cloud
   i przekazują go jako `apiKey` przez `TestRunner` → `MaestroCommandRunner` → `Orchestra`,
   gdzie jego obecność włącza `CloudAIPredictionEngine`. Także `--analyze`, `--api-url`, `--api-key`,
   promocje Cloud (`PromotionStateManager`) i zdarzenia analityczne.
3. **`MaestroSessionManager`** — shutdown hook wysyła `ScreenReporter.reportMaxDepth()`.
4. **`McpServer`** — rejestruje narzędzia Cloud i `open_maestro_viewer`; instrukcje kierują do Cloud;
   `RunTool` i `McpMaestroSessionManager` używają `McpViewerOrchestra`/`McpViewerDriver`.
5. **`McpCommand`** — domyślnie startuje `McpViewerServer`, który uruchamia binarium simulator-server.
6. **Build `maestro-cli`** — `processResources` zależy od `downloadSimulatorServer` i `buildMcpViewer`.

## 3. Stan docelowy 0.1.0

```
                ┌────────────────────────── host (macOS arm64) ──────────────────────────┐
 użytkownik ──► │ maestro-local CLI (JVM 17+)                                             │
 agent MCP  ──► │   ├─ test / check-syntax / hierarchy / query / record --local / ...     │
   (STDIO)      │   ├─ mcp: list_devices, inspect_screen, take_screenshot, run,           │
                │   │       cheat_sheet (zasób lokalny w JAR)                             │
                │   ├─ raporty: JUnit, HTML (bez CDN), artefakty debug, logi lokalne      │
                │   └─ Orchestra → maestro-client                                          │
                │         ├─ Android: ADB (dadb) ──► emulator/urządzenie (APK sterownika)  │
                │         └─ iOS: simctl/xcodebuild + XCTest runner na 127.0.0.1 ──► Sim.  │
                └─────────────────────────────────────────────────────────────────────────┘
       Brak: Cloud API, PostHog, error upload, update check, AI, Viewer, simulator-server.
```

Zasady architektoniczne:

- **Brak klientów zdalnych w runtime.** Kod usuwany, nie wyłączany flagą. Test architektoniczny
  (STEP-06) skanuje JAR i graf zależności.
- **Kontrolowane błędy dla usuniętych funkcji.** Usunięte komendy CLI — standardowy błąd picocli
  „Unknown command” (zero żądań). Usunięte komendy YAML (`assertWithAI` itd.) — błąd walidacji
  parsera z komunikatem, że funkcja nie istnieje w Maestro Local (zero żądań).
- **Sieć tylko lokalna.** Dozwolone: loopback, ADB, gniazda urządzeń. JS `http.*` i sterownik web
  — patrz polityka w STEP-03B (domyślnie loopback/prywatne, inne adresy → jawny błąd).
- **Odrębny stan od oficjalnego Maestro.** Upstream używa `~/.maestro` (sesje, deps, logi),
  `~/.mobiledev/authtoken`, `$XDG_STATE_HOME/maestro`. Fork zainstalowany obok oficjalnego Maestro
  nie powinien współdzielić tych katalogów ani nazwy pliku wykonywalnego `maestro` (decyzja D-2).
- **STDIO MCP jest czyste.** `claimMcpStdout()` przekierowuje `System.out` na `stderr` przed
  inicjalizacją; po usunięciu analityki i update-checka znika najczęstsze źródło zanieczyszczeń.
- **Dystrybucja bez zamkniętych binariów.** Lista zakazanych artefaktów (simulator-server, Viewer,
  `truffle-enterprise` do czasu decyzji D-6) sprawdzana testem zawartości archiwum.

## 4. Granica odpowiedzialności sieciowej

| Ruch | Kto go wykonuje | Polityka forka |
|---|---|---|
| Sterowanie urządzeniem (ADB, XCTest na loopback, gRPC przez forward) | CLI | dozwolony |
| Backend testowanej aplikacji, WebView, przeglądarka w urządzeniu, `openLink` na urządzeniu | urządzenie/aplikacja | poza zakresem; dokumentujemy, nie mockujemy |
| `http.*` w skryptach JS flow | CLI (JVM hosta) | ograniczony (STEP-03B) |
| Platforma web (Chrome na hoście) | CLI + Chrome | decyzja D-4 |
| `sdkmanager`, `xcodebuild`, `simctl` | narzędzia systemowe | CLI nie wywołuje ich w celu pobierania bez jawnego polecenia |

## 5. Stan bieżący

- STEP-00: brak zmian w kodzie produktu. `main` = upstream `v2.11.0` + commit bootstrap
  `6592abd3` (zaparkowane workflowy upstream).
