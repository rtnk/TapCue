# Audyt ruchu sieciowego — Maestro Local (repozytorium `rtnk/TapCue`)

Wersja: **0 (STEP-00, baseline)**. Stan kodu: upstream `mobile-dev-inc/Maestro` tag `v2.11.0`
(`c8c361be1cc89b2e742f185466a302a22778be6b`).

Dokument jest rejestrem *wszystkich* znanych ścieżek, którymi narzędzie (CLI, MCP, build) może
nawiązać połączenie sieciowe. Każdy kolejny krok aktualizuje kolumnę **Status** i dopisuje dowody.

## Legenda

- **Potwierdzone** — ustalone przez odczyt kodu w tym commicie (podano `plik:linia`). Nie oznacza
  to, że ruch został zaobserwowany na żywo — taki test (monitor DNS/połączeń) powstaje w STEP-06.
- **Hipoteza** — wymaga sprawdzenia (zachowanie biblioteki zewnętrznej, zależność przechodnia,
  ścieżka nieprzeczytana w całości).
- **Opt-out?** — czy istnieje w upstream zmienna/flaga wyłączająca ruch, i czy faktycznie działa.
- **Decyzja** — `USUŃ`, `LOKALNIE` (zastąpić lokalnym zasobem), `OGRANICZ` (tylko loopback/
  sieć prywatna lub jawny opt-in), `ZACHOWAJ` (ruch lokalny niezbędny do sterowania urządzeniem),
  `GRANICA` (ruch testowanej aplikacji/urządzenia — poza zakresem forka, opisać w dokumentacji).

## Granica „lokalne vs zewnętrzne”

Dozwolone w gotowym narzędziu:

- połączenia do `127.0.0.1`/`::1`/`localhost` (driver iOS XCTest na porcie lokalnym, port-forward
  ADB, gRPC do `maestro-server.apk` przez forward, CDP do lokalnego Chrome/WebView, serwer MCP
  na STDIO — bez sieci w ogóle);
- połączenia ADB do urządzeń w sieci prywatnej, jeśli użytkownik sam je skonfigurował
  (`adb connect`, `--host`) — ruch sterujący urządzeniem, nie usługa producenta;
- wywołania narzędzi systemowych użytkownika (`adb`, `xcrun simctl`, `xcodebuild`) — ich własny
  ruch sieciowy jest poza forkiem, ale fork **nie może ich wywoływać w celu pobierania** komponentów
  bez wyraźnego polecenia użytkownika (patrz N-24).

Ruch sieciowy **testowanej aplikacji** (backend aplikacji, WebView, przeglądarka urządzenia) jest
osobną sprawą: fork nie mockuje backendu aplikacji. Komendy, które jedynie przekazują URL do
urządzenia (`openLink` na Android/iOS), mają status `GRANICA` i zostaną opisane w dokumentacji.
Komendy, w których **host** (JVM CLI) sam wykonuje żądanie (JS `http.*`, sterownik web), mają
status `OGRANICZ`.

## Rejestr — runtime CLI / MCP

| ID | Źródło (plik:linia) | Wyzwalacz | Cel | Wysyłane dane | Opt-out? | Status | Decyzja | Krok |
|---|---|---|---|---|---|---|---|---|
| N-01 | `maestro-cli/.../analytics/Analytics.kt:19`, `App.kt:142,178` | **każde** uruchomienie z argumentem (także `--version`, `--help`) | `https://us.i.posthog.com` (PostHog) | UUID instalacji, nazwa komendy, oczyszczony ciąg argumentów (`commandStringUsed`), OS/arch, wersja, właściwości użytkownika/organizacji po zalogowaniu | `MAESTRO_CLI_NO_ANALYTICS` — sprawdzany w `trackEvent`; pierwszy start zapisuje zgodę domyślnie **włączoną** | Potwierdzone | USUŃ | 03 |
| N-02 | `analytics/Analytics.kt:93` (`identifyAndUpdateState`) | zdarzenie analityczne przy zapisanym tokenie | `api.copilot.mobile.dev` `/v2/maestro-studio/user`, `/org` | token Bearer, następnie dane użytkownika do PostHog | jak N-01 | Potwierdzone | USUŃ | 03 |
| N-03 | `util/ErrorReporter.kt:26`, `App.kt:153` | **każdy** wyjątek z komendy | `api.copilot.mobile.dev/maestro/error` | zserializowany wyjątek (stack trace, komunikat — może zawierać ścieżki, nazwy aplikacji) + pełna linia poleceń | **brak** — `MAESTRO_CLI_NO_ANALYTICS` nie jest sprawdzany | Potwierdzone | USUŃ | 03 |
| N-04 | `update/Updates.kt:59`, `App.kt:145,189` | każde uruchomienie (pobranie asynchroniczne przed wykonaniem komendy; potem blokada do 3 s) | `api.copilot.mobile.dev/v2/maestro/version` | nagłówki `X-UUID`, `X-VERSION`, `X-OS`, `X-OSARCH`, `X-FRESH-INSTALL` (`api/ApiClient.kt:1062`) | `MAESTRO_DISABLE_UPDATE_CHECK=true` blokuje tylko **wyświetlenie**; `fetchUpdatesAsync()` wysyła żądanie mimo to | Potwierdzone | USUŃ | 03 |
| N-05 | `util/ChangeLogUtils.kt:22` | po wykryciu nowszej wersji | `raw.githubusercontent.com/mobile-dev-inc/maestro/main/CHANGELOG.md` | IP, User-Agent | jak N-04 | Potwierdzone | USUŃ | 03 |
| N-06 | `util/ScreenReporter.kt:13`, `session/MaestroSessionManager.kt:127` | zamknięcie każdej sesji urządzenia (shutdown hook) | `api.copilot.mobile.dev/maestro/screen` | `maxDepth` hierarchii UI + nagłówki systemowe | **brak** | Potwierdzone | USUŃ | 03 |
| N-07 | `command/TestCommand.kt:197-204` | konstrukcja `TestCommand` (każde `maestro test`) | lokalnie odczytuje `~/.mobiledev/authtoken` / `MAESTRO_CLOUD_API_KEY`; token przekazywany do Orchestra jako `apiKey` | — (sam odczyt), ale włącza N-08 | — | Potwierdzone | USUŃ | 01 (+02) |
| N-08 | `maestro-orchestra/.../Orchestra.kt:154`, `maestro-ai/.../cloud/ApiClient.kt:48` | komendy YAML `assertWithAI`, `assertNoDefectsWithAI`, `extractTextWithAI` przy obecnym tokenie | `api.copilot.mobile.dev` (lub `MAESTRO_CLOUD_API_URL`) | **zrzut ekranu urządzenia** + prompt | brak (zależy tylko od obecności tokenu) | Potwierdzone | USUŃ | 02 |
| N-09 | `maestro-ai/.../openai/Client.kt:20`, `anthropic/Client.kt:23`, `AI.kt:42` | `TestAnalysisManager`/demo AI przy `MAESTRO_CLI_AI_KEY` | `api.openai.com`, `api.anthropic.com` | zrzuty ekranu, prompty | — | Potwierdzone (kod), Hipoteza (czy osiągalne z CLI poza `DemoApp`) | USUŃ | 02 |
| N-10 | `command/TestCommand.kt:460`, `insights/TestAnalysisManager.kt` | `maestro test --analyze` | `--api-url` (domyślnie `api.copilot.mobile.dev`) `/v2/analyze` | zrzuty ekranu i logi debug wszystkich flow | flaga opt-in | Potwierdzone | USUŃ | 02 |
| N-11 | `command/CloudCommand.kt`, `cloud/CloudInteractor.kt`, `api/ApiClient.kt:246,397` | `maestro cloud` | `api.copilot.mobile.dev`, link `app.maestro.dev` | **plik aplikacji, flow, konfiguracja**, token | komenda opt-in | Potwierdzone | USUŃ | 01 |
| N-12 | `command/LoginCommand.kt`, `auth/Auth.kt:79-93` | `maestro login` (oraz `triggerSignInFlow` w Cloud) | `api.copilot.mobile.dev` `/v2/maestroLogin/*`, przeglądarka; lokalny serwer Netty na wolnym porcie | kod OAuth, token | opt-in | Potwierdzone | USUŃ | 01 |
| N-13 | `command/ListCloudDevicesCommand.kt`, `api/ApiClient.kt:646` | `maestro list-cloud-devices` | `api.copilot.mobile.dev/v2/device/list` | token | opt-in | Potwierdzone | USUŃ | 01 |
| N-14 | `graphics/RemoteVideoRenderer.kt:23`, `api/ApiClient.kt:207` | `maestro record` **bez** `--local` (domyślnie!) | `https://maestro-record.ngrok.io/render` | **nagranie ekranu urządzenia** i klatki tekstowe | `--local` | Potwierdzone | USUŃ (zostaje tylko `LocalVideoRenderer`) | 01 |
| N-15 | `mcp/tools/CheatSheetTool.kt:30` | narzędzie MCP `cheat_sheet` | `api.copilot.mobile.dev/v2/bot/maestro-cheat-sheet` | IP | — | Potwierdzone | LOKALNIE (wersjonowany zasób w JAR) | 04 |
| N-16 | `mcp/tools/RunOnCloudTool.kt`, `GetCloudRunStatusTool.kt`, `DescribeCloudRunTool.kt`, `ListCloudDevicesTool.kt` | narzędzia MCP `run_on_cloud`, `get_cloud_run_status`, `describe_cloud_run`, `list_cloud_devices` | `api.copilot.mobile.dev` | aplikacja, flow, token | — | Potwierdzone | USUŃ | 04 |
| N-17 | `mcp/McpServer.kt:29-49` (instrukcje serwera) | handshake MCP | — (tekst kieruje agenta do `docs.maestro.dev/llms.txt` i Maestro Cloud) | — | — | Potwierdzone | USUŃ wzmianki / LOKALNIE | 04 |
| N-18 | `command/McpCommand.kt:43`, `mcp/viewer/McpViewerServer.kt:253` | `maestro mcp` (Viewer startuje **domyślnie**, `--no-viewer` wyłącza) | serwer HTTP na `127.0.0.1:<port>`; uruchamia binarium **simulator-server** | — (lokalnie), ale patrz bloker licencyjny L-01 | `--no-viewer` | Potwierdzone | USUŃ (licencja) | 00B/04 |
| N-19 | `command/DownloadSamplesCommand.kt:90` | `maestro download-samples` | `storage.googleapis.com/mobile.dev/samples/samples.zip` | IP | opt-in | Potwierdzone | USUŃ | 03 |
| N-20 | `command/StudioCommand.kt`, `util/StudioDownload.kt:6` | `maestro studio` | wyświetla link `studio.maestro.dev` (bez żądania) | — | — | Potwierdzone | USUŃ (zależność od oficjalnego Studio) | 01 |
| N-21 | `report/HtmlTestSuiteReporter.kt:55` | otwarcie raportu HTML w przeglądarce | `cdn.jsdelivr.net` (Bootstrap CSS/JS) | IP, Referer przeglądarki | — | Potwierdzone | LOKALNIE (inline CSS) | 03 |
| N-22 | `report/HtmlAITestSuiteReporter.kt:71`, `auth/Auth.kt:25` | raport AI / strona callbacku logowania | `cdn.tailwindcss.com` | IP | — | Potwierdzone | USUŃ (razem z AI/Cloud) | 01/02 |
| N-23 | `maestro-client/.../mockserver/MockInteractor.kt:53,84,103` | brak wywołań w kodzie produkcyjnym (tylko `main()` w tym pliku) | `api.copilot.mobile.dev` `/auth`, `/mms-events` | token | — | Potwierdzone (martwy kod) | USUŃ | 01 |
| N-24 | `maestro-client/.../device/DeviceService.kt:632,682` | `maestro start-device`, gdy obraz systemu Android nie jest zainstalowany | `sdkmanager --list` / instalacja obrazu (serwery Google) | — | — | Potwierdzone (wywołanie), Hipoteza (zakres ruchu `sdkmanager`) | OGRANICZ (tylko obrazy zainstalowane; brak auto-pobierania) | 03 |
| N-25 | `maestro-client/.../js/GraalJsHttp.kt:74`, `GraalJsEngine.kt:40,155` | `runScript`/`evalScript` z `http.get/post/put/delete/request` | **dowolny URL** podany w skrypcie flow | dowolne dane flow | — | Potwierdzone | OGRANICZ (domyślnie tylko loopback/prywatne; inne — jawny błąd) | 03B |
| N-26 | `maestro-client/.../drivers/CdpWebDriver.kt:107` (Selenium `ChromeDriverService.Builder` bez ścieżki) | platforma `web` | Selenium Manager może **pobrać chromedriver/Chrome** (Chrome for Testing) i wysłać statystyki użycia | wersje, OS | `SE_OFFLINE`, `SE_AVOID_STATS` (Selenium) | Hipoteza (zachowanie Selenium 4.43) | OGRANICZ lub wyłączyć platformę web w 0.1.0 (decyzja D-4) | 03B |
| N-27 | `CdpWebDriver.kt:97` | sterownik web w trybie `isStudio` | `https://maestro.mobile.dev` | IP | — | Potwierdzone | USUŃ | 01 |
| N-28 | `CdpWebDriver.kt:581`, `WebDriver.kt` (`openLink`, `launchApp` URL) | flow web | URL z flow — wykonuje **przeglądarka hosta** | dane testowanej strony | — | Potwierdzone | OGRANICZ (D-4) | 03B |
| N-29 | `openLink` na Android (ADB `am start`) / iOS (`simctl openurl`) | flow mobilny | URL wykonuje urządzenie/symulator | — | — | Potwierdzone | GRANICA (dokumentacja) | 03B |
| N-30 | Chrome uruchamiany przez sterownik web | platforma `web` | usługi tła Chrome (aktualizacje komponentów, Safe Browsing itp.) | — | — | Hipoteza | OGRANICZ (flagi Chrome) / D-4 | 03B |
| N-31 | `maestro-ios-driver/.../xcuitest/XCTestDriverClient.kt`, `OkHttpClientInstance.kt` | sterowanie iOS | `127.0.0.1:<port>` (XCTest runner) | polecenia UI | — | Potwierdzone | ZACHOWAJ | — |
| N-32 | `maestro-client/.../android/AdbSocketFactory.kt`, `AndroidDeviceConnection.kt`, dadb | sterowanie Android | ADB (`localhost:5037`/emulator/urządzenie), gRPC przez port-forward | polecenia UI | — | Potwierdzone | ZACHOWAJ | — |
| N-33 | `maestro-client/.../android/chromedevtools/DadbChromeDevToolsClient.kt` | hierarchia WebView na Android | socket DevTools przez ADB (`http://$socketName/json`) | — | — | Potwierdzone | ZACHOWAJ | — |
| N-34 | `maestro-cli/src/main/java/maestro/cli/util/EnvUtils.kt:17,34` | wszystkie klienty API | `MAESTRO_API_URL` (domyślnie `api.copilot.mobile.dev`) | — | — | Potwierdzone | USUŃ (po usunięciu wszystkich klientów) | 01–03 |
| N-35 | Teksty/linki w komunikatach (`App.kt` — `get.maestro.mobile.dev`, `PickDeviceInteractor.kt`, `WorkspaceExecutionPlanner.kt`, `MaestroFlowParser.kt`, `build.gradle.kts` start scripts) | wyświetlenie | brak żądania; promują usługi/dokumentację producenta | — | — | Potwierdzone | USUŃ / zamienić na dokumentację forka | 01–05 |

## Rejestr — build i dystrybucja (CI może pobierać dozwolone zależności; gotowe narzędzie — nie)

| ID | Źródło | Cel | Uwagi | Status | Decyzja | Krok |
|---|---|---|---|---|---|---|
| B-01 | `maestro-cli/build.gradle.kts:232-306,325` (`downloadSimulatorServer`, wpięty w `processResources`) | `github.com/mobile-dev-inc/simulator-server-releases` | pobiera i **pakuje do JAR** binaria objęte restrykcyjną licencją (L-01). Uruchamia się przy każdym `./gradlew build`/`test`/`run` modułu CLI | Potwierdzone | USUŃ (bloker prawny) | 00B |
| B-02 | `maestro-cli/build.gradle.kts` (`installMcpViewerDeps`, `buildMcpViewer`) | `registry.npmjs.org` (npm) | buduje frontend Viewera (zależny od simulator-server) | Potwierdzone | USUŃ | 00B/04 |
| B-03 | `settings.gradle.kts` (`google()`, `mavenCentral()`, `gradlePluginPortal()`), wrapper `services.gradle.org` | repozytoria Maven | dozwolone w CI. W sesji STEP-00 `dl.google.com` było zablokowane polityką sieci środowiska — patrz wyniki testów w handover | Potwierdzone | ZACHOWAJ (CI) | 06 |
| B-04 | `jreleaser` w `maestro-cli/build.gradle.kts` | GitHub `mobile-dev-inc/maestro`, `mobile-dev-inc/homebrew-tap` | publikacja upstream | Potwierdzone | USUŃ / zastąpić `release.yml` | 05/07 |
| B-05 | `scripts/install.sh` | `github.com/mobile-dev-inc/maestro/releases` | instalator upstream | Potwierdzone | USUŃ / zastąpić | 05 |
| B-06 | Workflowy upstream (`.github/upstream-workflows-disabled/`) | Maven Central, GCS, Slack/release comms, runnery macOS | w forku **zaparkowane** commitem bootstrap `6592abd3` — nie uruchamiają się | Potwierdzone | USUŃ po STEP-06 | 06 |
| B-07 | `e2e/` (`demo_app` pobierany z bucketu GCS w CI upstream) | GCS upstream | fixture'y E2E | Potwierdzone | do oceny w STEP-06 | 06 |
| B-08 | `maestro-cli/src/test/mcp/` (`npx mcp-server-tester`, ewaluacje LLM) | npm, API modeli | testy upstream wymagające zdalnych LLM | Potwierdzone | nie uruchamiać w CI forka; testy MCP zastąpić lokalnym testem JSON-RPC | 04/06 |

## Rejestr — testy upstream zależne od internetu

| ID | Źródło | Cel | Skutek | Status | Decyzja | Krok |
|---|---|---|---|---|---|---|
| T-01 | `maestro-test/src/test/resources/script/multipart_request_file_script.js` (IntegrationTest „Case 136 - Relative path in http multipart script”) | `https://eu.httpbin.org/anything` | test pada bez internetu (w sesji STEP-00: `CONNECT 403` z proxy) | Potwierdzone | przepisać na lokalny `MockWebServer` (razem z polityką D-3) | 03B |
| T-02 | `maestro-test`/`maestro-orchestra` — URL-e `example.com` w testach | brak żądań (tylko parsowanie) | — | Hipoteza (do potwierdzenia testem egress) | weryfikacja | 06 |

## Zmienne środowiskowe związane z ruchem (upstream)

`MAESTRO_CLI_NO_ANALYTICS`, `MAESTRO_DISABLE_UPDATE_CHECK`, `MAESTRO_API_URL`, `MAESTRO_CLOUD_API_URL`,
`MAESTRO_CLOUD_API_KEY`, `MAESTRO_CLI_AI_KEY`, `MAESTRO_CLI_AI_MODEL`,
`MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED`. **Wniosek:** w upstream żadna kombinacja tych zmiennych
nie zapewnia braku ruchu zewnętrznego (N-03, N-04, N-06 nie mają działającego opt-out). Fork
nie będzie polegał na zmiennych — kod zostanie usunięty.

## Plan weryfikacji (do wykonania w STEP-06)

1. Test jednostkowy/architektoniczny: w klasach runtime brak odwołań do `api.copilot.mobile.dev`,
   `posthog`, `ngrok.io`, `openai.com`, `anthropic.com`, `storage.googleapis.com`, `jsdelivr`,
   `tailwindcss` (grep po zawartości JAR + test w Gradle).
2. Test grafu zależności: `runtimeClasspath` CLI bez `com.posthog`, bez modułu `maestro-ai`.
3. Test egress na runnerze Linux: uruchomienie CLI (`--version`, `--help`, `check-syntax`, `test`
   z `FakeDriver`/bez urządzenia, `mcp` handshake) w przestrzeni sieciowej bez trasy do internetu
   (np. `unshare -n` lub reguły `iptables` z wyjątkiem loopback) z rejestrowaniem prób DNS
   i `connect()` (`strace -f -e trace=network` lub logowanie odrzuceń). Kryterium: zero prób
   połączeń poza loopback/siecią prywatną — nie tylko „zero udanych żądań”.
4. Test na macOS arm64 (ręczny lub runner `macos-*`): to samo dla paczki z Release, z blokadą
   ruchu wychodzącego (np. `pf`) — wynik zapisywany w STEP-08.
