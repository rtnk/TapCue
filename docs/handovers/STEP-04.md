# Handover — STEP-04: Local-only MCP i usunięcie Viewera

| Pole | Wartość |
|---|---|
| Krok | STEP-04 — **scalony** (#9, 2026-10-24) |
| PR | PR-04: https://github.com/rtnk/TapCue/pull/9 |
| Gałąź | `step-04-local-mcp` |
| Gałąź docelowa | `main` |
| Baza | `main` @ `c0c143d` (merge PR #8 — STEP-03B) |
| Commit SHA | `6acadad` (head gałęzi `step-04-local-mcp`); merge do `main`: `00808e7` |

## 1. Co zrobiono

Cel: lokalny serwer MCP bez Viewera, bez binarium simulator-server i bez żądań do usług producenta
(NETWORK-AUDIT N-15, N-17, N-18, B-02, B-08; LICENSE-AUDIT L-01, L-10).

| Obszar | Zmiana |
|---|---|
| `cheat_sheet` (N-15) | `CheatSheetTool` zwraca zasób `maestro-cli/src/main/resources/mcp/cheat-sheet.md` (w JAR CLI) zamiast `GET https://api.copilot.mobile.dev/v2/bot/maestro-cheat-sheet`. Treść napisana dla forka na podstawie parsera w repozytorium (`maestro-orchestra`, pakiet `maestro.orchestra.yaml`: `YamlFluentCommand`, `YamlElementSelector`, `YamlConfig` i klasy komend; wartości domyślne z `Orchestra`, np. `eraseText` = 50, rozszerzenie `.png` dla `takeScreenshot`), bez kopiowania `docs.maestro.dev`. Opisuje układ flow, selektory, komendy, asercje, sterowanie przepływem, JavaScript (z polityką `http.*` z STEP-03B), nagrywanie i pracę przez MCP. Nazwa, opis i schemat narzędzia bez zmian. Brak zasobu → kontrolowany błąd narzędzia (`isError`) |
| Instrukcje serwera (N-17) | `McpServer.INSTRUCTIONS` bez `Docs: https://docs.maestro.dev/llms.txt`; zdanie kieruje do `cheat_sheet` („flow syntax reference bundled with this CLI”). Limit 2 KB zachowany (`McpServerTest`) |
| Viewer (N-18, L-01) | usunięty pakiet `maestro.cli.mcp.viewer` (`McpViewerServer` — serwer HTTP ktor/Netty Viewera, `McpViewerDriver`, `McpViewerOrchestra`, `McpViewerEvents`/`ViewerEvent`, `ViewerHint`, `StreamDeviceType`). `RunTool` tworzy `Orchestra(session.maestro)`; `McpMaestroSessionManager` — zwykłe `AndroidDriver`/`IOSDriver`. Zestaw obsługiwanych urządzeń bez zmian (Android: emulator i urządzenie, iOS: symulator; inne → ten sam `UnsupportedOperationException`) — dawniej wyliczany przez `StreamDeviceType.forConnected`, teraz `isSupported` |
| Frontend Viewera (B-02, L-10) | usunięte `maestro-cli/mcp-viewer/` (`package.json`, `package-lock.json`, źródła React/Vite) i `.nvmrc` (dotyczył tylko Viewera) |
| `Unpacker.unpackTree` | usunięte wraz z helperami (`ResourceRoot`, `openResourceRoot`, `openJarFileSystem`, `applyPermissions`, `grantDataPermissions`, `sameContents(InputStream, …)`) — służyło do rozpakowania simulator-server, nieużywane od STEP-00B |
| `--no-viewer`, `--viewer-port` | **decyzja: zostają jako ukryte no-op** (jak `--headless`/`--screen-size` w STEP-03B) — konfiguracje klientów MCP z tymi flagami dalej działają; `--viewer-port` wypisuje ostrzeżenie na stderr (bez zmian). `CliJarContentsTest` sprawdza, że są akceptowane i nie ma ich w `--help` |
| Zależności | `maestro-cli` bez `ktor-server-core`, `ktor-server-netty`, `ktor-server-cors`, `ktor-server-status-pages` (tylko Viewer); wpisy `ktor-server-netty/-cors/-status-pages` usunięte z `libs.versions.toml` (`ktor-server-core` zostaje — używa go `maestro-android`). **Netty:** `ktor-server-netty` podnosił netty gRPC (`grpc-netty` 1.50.2 żąda 4.1.79) do 4.1.111.Final; żeby usunięcie Viewera nie obniżyło wersji, `maestro-cli` ma `implementation(platform(libs.netty.bom))` z `netty = "4.1.111.Final"`. **`ktor-client-core`/`-cio` zostają:** kod CLI ich nie używa, ale SDK MCP ma wykluczone `io.ktor`, a jego `ServerOptions` (przez `PathSegmentTemplateMatcher`) odwołuje się do `io.ktor.http` z `ktor-http`, który przychodzi z `ktor-client-core` (sprawdzone `javap -v` na `kotlin-sdk-core/server-jvm-0.11.1`). Klasy SSE/WebSocket SDK (`io.ktor.server.sse` — nieobecne już na `main`) nie są używane przy STDIO |
| Testy upstream MCP (B-08) | `src/test/mcp/tool-tests-without-device.yaml`: przypadek `cheat_sheet` oczekuje sukcesu i treści ściągawki (wcześniej błędu `MAESTRO_CLOUD_API_KEY`); `maestro-mcp.json` bez `--no-viewer` i `MAESTRO_API_KEY`. Skrypty `npx mcp-server-tester` i ewaluacje LLM zostają w repo, nie są uruchamiane przez build ani CI (decyzja o usunięciu — STEP-06) |
| Dokumenty | ROADMAP (status, STEP-03B → MERGED, STEP-04 — realizacja i decyzja o flagach), NETWORK-AUDIT (wersja 6: N-15, N-17, N-18, N-34, B-02, B-08), LICENSE-AUDIT (wersja 5: L-01, L-10, §3 — zmiany grafu), ARCHITECTURE-LOCAL §3, §5, CHANGELOG, AGENTS.md (nota forka), `maestro-cli/src/main/java/maestro/cli/mcp/README.md` |

**Dodane testy (`maestro-cli`):**
- `McpStdioTest` (1) — `maestro mcp` uruchamiany jako **osobny proces JVM** (`java -cp <classpath testów> maestro.cli.AppKt mcp`),
  JSON-RPC na STDIO: `initialize` (nazwa serwera, instrukcje z `cheat_sheet`, bez `docs.maestro.dev` i URL-i) →
  `notifications/initialized` → `tools/list` (**dokładnie** `list_devices`, `take_screenshot`, `run`, `inspect_screen`,
  `cheat_sheet`) → `tools/call cheat_sheet` (treść = zasób z JAR) → `tools/call list_devices` (bez błędu, tablica `devices`;
  na maszynie bez urządzeń pusta) → zamknięcie STDIN → proces kończy się w ≤ 30 s z kodem 0. Każda niepusta linia stdout
  musi być obiektem JSON z `"jsonrpc":"2.0"`. Proces ma `-Dhttp(s).proxyHost` → lokalny `MockWebServer`,
  `-Dhttp.nonProxyHosts=` (puste — także loopback HTTP przez proxy) i `-DsocksProxyHost` → lokalny `ServerSocket`, bez
  `JAVA_TOOL_OPTIONS`, z `HOME`/`user.home`/`XDG_STATE_HOME` w katalogu tymczasowym; test wymaga **zero** żądań do proxy HTTP
  i **zero** połączeń SOCKS. Sprawdzone mutacjami w kopii analitycznej: dodanie żądania do `api.copilot.mobile.dev`
  w `cheat_sheet` → FAIL (licznik proxy); `println` na kanale protokołu → FAIL („stdout carried a line that is not a JSON-RPC message”).
- `CheatSheetToolTest` (3) — narzędzie zwraca zasób; **każdy** blok ` ```yaml ` ściągawki (8) parsuje się przez
  `YamlCommandReader.readCommands`; brak `http://`, `https://`, `maestro.dev`, `mobile.dev`.
- `CliJarContentsTest` — wyjątek `knownUntilStep04` dla `CheatSheetTool` usunięty (cały JAR CLI bez `api.copilot.mobile.dev`
  i pozostałych hostów STEP-03); nowy test: klasy `maestro/cli/mcp/**` i zasoby `mcp/**` w JAR bez `maestro.dev`, `mobile.dev`
  i URL-i, JAR zawiera `mcp/cheat-sheet.md`; zakazany prefiks `maestro/cli/mcp/viewer/`; test „Viewer classes are gone”
  (zamiast „open_maestro_viewer tool is gone”) sprawdza także klasy pakietu `mcp/viewer/`.

**Usunięte testy:** brak (Viewer nie miał testów w forku).

**Poza zakresem:** nazwa TapCue, polecenie `tapcue`, katalogi `~/.tapcue`/`.tapcue/` (D-1/D-2) — STEP-04B (`serverInfo.name`
MCP to nadal `maestro`, nazwy w instrukcjach i ściągawce — „Maestro”/„Maestro Local”); dystrybucja, `jreleaser`, linki
`maestro.dev` w komunikatach CLI (N-35: `PickDeviceInteractor`, start-scripty) — STEP-05; usunięcie lub zastąpienie
`maestro-cli/src/test/mcp/` (B-08) — STEP-06; nieużywane w kodzie CLI `ktor-client-cio` — patrz §3.

## 2. Walidacja

Środowisko: Linux x86_64, JDK 17.0.20 (doinstalowany przez `apt`), brak Android SDK/Xcode/urządzeń,
`dl.google.com` zablokowane. Lokalnie — **wariant analityczny** (handover STEP-00 §3: kopia w scratchpadzie bez
`maestro-android` i `google()`) z mirrorem Maven Central jak w STEP-01 §2. **Bez flag `-x`.**

| Test | Komenda | Wynik |
|---|---|---|
| Wszystkie testy jednostkowe | `LANG=C.UTF-8 ./gradlew test --continue` | **PASS — 1287 testów, 0 porażek** (STEP-03B: 1282) |
| ↳ `maestro-cli` | jw. | **PASS**, 129 (STEP-03B: 124; +1 `McpStdioTest`, +3 `CheatSheetToolTest`, +1 `CliJarContentsTest`) |
| ↳ pozostałe | jw. | **PASS** bez zmian liczności: `maestro-client` 324 (2 skipped), `maestro-orchestra` 456 (3 skipped), `maestro-orchestra-models` 51, `maestro-ios-driver` 47, `maestro-ios` 2, `maestro-utils` 45, `maestro-test` 233 |
| `maestro-cli` po przypięciu netty | `./gradlew :maestro-cli:test :maestro-orchestra:detektMain :maestro-client:detektMain :maestro-cli:installDist :maestro-cli:shadowJar` | **PASS** (129/129) |
| Detekt + dystrybucja | jw. | **PASS** |
| Graf zależności | `./gradlew :maestro-cli:dependencies --configuration runtimeClasspath` na `main` i na gałęzi, diff | **PASS**: znikają `io.ktor:ktor-server-{core,cors,host-common,netty,status-pages}(-jvm)` 2.3.13, `com.typesafe:config` 1.4.3, `org.eclipse.jetty.alpn:alpn-api` 1.1.3, `org.fusesource.jansi:jansi` 2.4.1 (CLI używa `org.jline:jansi`), `io.netty:netty-transport-{classes,native}-{epoll,kqueue}` 4.1.111; dochodzi `io.netty:netty-bom` 4.1.111.Final; `netty-codec-socks`/`netty-handler-proxy` 4.1.79 → 4.1.111; pozostałe `io.netty:*` bez zmian (4.1.111). Bez przypięcia netty spadał do 4.1.79 (pierwszy diff) |
| Zawartość archiwów | `maestro-cli-*-all.jar`, `install/maestro/lib` | **PASS**: 0 wpisów `maestro/cli/mcp/viewer/`, `mcp-viewer/`, `io/ktor/server/`, `deps/simulator-server/`, `com/typesafe/config/`; 0 wystąpień `api.copilot.mobile.dev` w `*-all.jar`; `mcp/cheat-sheet.md` obecny; `lib/` bez `ktor-server-*`, netty wyłącznie 4.1.111.Final |
| Strażnik | `scripts/fork/check-no-simulator-server.sh` (w kopii z wynikami builda) | **PASS** |
| Smoke MCP (`strace -f -e trace=connect`) | `install/maestro/bin/maestro mcp` z `HOME` tymczasowym, bez `JAVA_TOOL_OPTIONS`; na STDIN: `initialize`, `notifications/initialized`, `tools/list`, `tools/call` `cheat_sheet`, `list_devices`, `run` (`device_id: emulator-5554`) | **PASS**: 5 odpowiedzi JSON-RPC na stdout (i nic więcej), `cheat_sheet` → treść ściągawki, `list_devices` → `{"devices":[]}`, `run` → kontrolowany błąd „Device with id emulator-5554 is not connected”; zamknięcie STDIN → kod 0. 390 wywołań `connect` `AF_INET` — wszystkie do `127.0.0.1` (dadb: serwer ADB i porty emulatorów), 0 innych adresów |
| CI GitHub Actions (`ci-baseline.yml`, pełne repo) | PR-04 #9, [run 40069156871](https://github.com/rtnk/TapCue/actions/runs/40069156871) na `6acadad` | **PASS**; `main` po merge (`00808e7`): [run 40070077945](https://github.com/rtnk/TapCue/actions/runs/40070077945) |
| Smoke z urządzeniem (`run`, `inspect_screen`, `take_screenshot`), Swift | — | **NOT RUN**: brak SDK/Xcode/urządzeń; ścieżka `Orchestra`/sterowników jest ta sama co w `maestro test` (zmiana usuwa tylko wrappery publikujące zdarzenia Viewera) |
| Ewaluacje upstream (`run_mcp_tool_tests.sh`, `run_mcp_evals.sh`) | — | **NOT RUN**: wymagają `npx` i zdalnych API modeli (B-08) |

Jak powtórzyć:

```bash
./gradlew :maestro-orchestra:detektMain :maestro-client:detektMain
./gradlew test --continue
./gradlew :maestro-cli:test --tests 'maestro.cli.mcp.*' --tests 'maestro.cli.CliJarContentsTest'
./gradlew :maestro-cli:dependencies --configuration runtimeClasspath | grep -cE 'ktor-server|netty:4\.1\.79'   # 0
scripts/fork/check-no-simulator-server.sh
```

## 3. Ryzyka, blokery, znane problemy

- **Treść `cheat_sheet`:** to nowy tekst forka, krótszy niż ściągawka upstream (która nie jest dostępna offline i ma nieznaną
  licencję). Składnia jest pilnowana testem (każdy przykład się parsuje), ale opisy semantyki (np. dopasowanie tekstu jako
  regex, wartości domyślne) pochodzą z odczytu kodu i nie są testowane na urządzeniu. Agenci MCP mogą dostać mniej
  wskazówek niż w upstream.
- **Netty bez natywnych transportów:** wraz z `ktor-server-netty` znikają `netty-transport-native-epoll/kqueue`. gRPC
  (`grpc-netty`) używa wtedy transportu NIO. Połączenie gRPC idzie do `maestro-server.apk` przez port-forward na loopback,
  więc różnica wydajności jest pomijalna, ale nie zostało to sprawdzone na urządzeniu.
- **Przypięcie netty:** `netty-bom` 4.1.111.Final w `maestro-cli` utrzymuje dotychczasową wersję; aktualizacja netty to teraz
  osobna decyzja (wpis `netty` w `libs.versions.toml`). Moduł `maestro-client` sam w sobie (testy) rozwiązuje netty 4.1.79
  jak dotąd.
- **`ktor-client-core`/`-cio` w CLI:** zostają, bo SDK MCP potrzebuje `ktor-http`. Zastąpienie ich wąskim `ktor-http`
  (i usunięcie `ktor-client-cio`) jest możliwe, ale wymaga testu wszystkich ścieżek SDK — kandydat na STEP-06.
- **API biblioteki:** usunięte `maestro.cli.mcp.viewer.*`, `Unpacker.unpackTree`, pole `McpMaestroSession.streamDeviceType`
  (wszystko `internal` lub nieużywane poza CLI).

## 4. Warunek rozpoczęcia następnego kroku

PR-04 **scalony** do `main` (#9, `00808e7`) — spełnione. CI `ci-baseline.yml` na `main` po merge: [run 40070077945](https://github.com/rtnk/TapCue/actions/runs/40070077945) — sprawdź, że zielone, przed rozpoczęciem STEP-04B.

## 5. Następny krok

**STEP-04B: TapCue — nazwa, polecenie `tapcue`, katalogi `~/.tapcue` i `.tapcue/` (PR-04B).** Zakres w `docs/ROADMAP-0.1.md` §3
„STEP-04B” i decyzje D-1, D-2 (§2).

## 6. PROMPT DLA NASTĘPNEGO AGENTA (STEP-04B)

```text
Jesteś agentem kodującym w repozytorium rtnk/TapCue (fork mobile-dev-inc/Maestro → „Maestro Local”, docelowo „TapCue”).
Nie masz pamięci poprzednich sesji. Zanim cokolwiek zmienisz:

1. git fetch origin && git checkout main && git pull origin main. Potwierdź przez GitHub, że PR-04
   („STEP-04: Local-only MCP and Viewer removal”) jest MERGED. Jeśli nie — zatrzymaj się i zgłoś to.
2. Przeczytaj: AGENTS.md (nota forka), docs/ROADMAP-0.1.md (§1, §2 — D-1, D-2, STEP-04B, R-6), docs/handovers/STEP-04.md,
   docs/ARCHITECTURE-LOCAL.md §3 i §5 (stan katalogów), docs/NETWORK-AUDIT.md (N-35), docs/handovers/STEP-03.md
   (LegacyAnalyticsState).
3. Wykonaj WYŁĄCZNIE STEP-04B — TapCue: nazwa, polecenie i katalogi — na gałęzi step-04b-tapcue:
   - Nazwa CLI w picocli (`App`), `--help`, `--version` i komunikatach → `tapcue`, z dopiskiem
     „based on Maestro (Apache-2.0), not affiliated with mobile.dev” (D-1).
   - Start-script `tapcue` zamiast `maestro` (`applicationName` w maestro-cli/build.gradle.kts, `installDist`).
   - Katalog stanu `~/.tapcue` / `$XDG_STATE_HOME/tapcue`: jedno źródło w `EnvUtils` zamiast zakodowanych `~/.maestro`
     (`SessionStore`, `Unpacker.binaryDependency`, `RealIOSDeviceDriver`, `MaestroSessionManager`, `TestDebugReporter`,
     `DriverBuilder` i inne — znajdź wszystkie przez grep `.maestro`, `"maestro"` w ścieżkach). `~/.mobiledev` nie jest używany.
   - Katalog projektu `.tapcue/` zamiast `.maestro/` (artefakty `.tapcue/tests`, konfiguracja workspace, instrukcje MCP,
     ściągawka `maestro-cli/src/main/resources/mcp/cheat-sheet.md`, fixture'y e2e jeśli to konieczne). Format YAML flow
     i nazwy komend YAML bez zmian. Pakiety Kotlin (`maestro.*`) bez zmian.
   - MCP: `serverInfo.name` i instrukcje serwera — nazwa TapCue (limit 2 KB, `McpServerTest`), test `McpStdioTest`
     dostosowany.
   - Rozstrzygnij w PR (z rekomendacją z ROADMAP): stary `.maestro/` w projekcie → ostrzeżenie z poleceniem
     `mv .maestro .tapcue`, bez automatycznego odczytu; los `LegacyAnalyticsState` po przejściu na `~/.tapcue`.
   - Nie ruszaj dystrybucji (jreleaser, install.sh, archiwum, NOTICE) — STEP-05. Nie realizuj D-5/D-7.
4. Gradle uruchamiaj BEZ flag -x. Jeśli środowisko blokuje dl.google.com, użyj wariantu analitycznego z
   docs/handovers/STEP-00.md §3 (bez commitowania; mirror Maven Central jak w STEP-01 §2) i polegaj na CI.
5. Testy: brak zapisów do `~/.maestro` (HOME/XDG w katalogu tymczasowym, po uruchomieniu `--version`, `--help`,
   `check-syntax`, `test` z FakeDriver lub bez urządzenia, `mcp` handshake — w `~/.maestro` nic nie powstaje);
   `--help`/`--version` z nazwą TapCue i dopiskiem; artefakty w `.tapcue/`; ostrzeżenie dla starego `.maestro/`.
   Regresja: maestro-cli (NoTelemetryTest, LocalOnlyCliTest, NoRemoteAiTest, WebPlatformDisabledTest, McpServerTest,
   McpStdioTest, CheatSheetToolTest, CliJarContentsTest), maestro-test, scripts/fork/check-no-simulator-server.sh.
6. Zaktualizuj docs/ROADMAP-0.1.md (status, D-1/D-2 — realizacja), docs/ARCHITECTURE-LOCAL.md §3–§5, docs/NETWORK-AUDIT.md
   (jeśli dotyczy), CHANGELOG.md (sekcja forka — to zmiana łamiąca dla użytkowników: nowe polecenie i katalogi),
   AGENTS.md (nota forka), dodaj docs/handovers/STEP-04B.md wg szablonu STEP-04.md (z promptem dla STEP-05).
7. Utwórz PR „STEP-04B: TapCue name, command and directories” do main (cel, zakres, usunięte/dodane/zachowane, pliki,
   wyniki testów z komendami — tylko faktycznie uruchomione, ryzyka, link do handover, checklista, prompt dla STEP-05).
   Bez auto-merge. Doprowadź CI PR do zielonego.
8. Zakończ odpowiedzią: krok, URL PR, walidacja PASS/FAIL/NOT RUN, ryzyka, warunki następnego kroku,
   prompt na kolejną sesję. Nie zaczynaj STEP-05.
```
