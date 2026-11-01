# Handover — STEP-09: Sync upstream v2.12.0 i wydanie 0.2.0

| Pole | Wartość |
|---|---|
| Krok | STEP-09 — PR-09 w review; tag `v0.2.0` i publikacja — **nie wykonane** (po scaleniu, na polecenie właściciela) |
| PR | PR-09 „STEP-09: sync upstream v2.12.0 and release 0.2.0” (link w PR / ROADMAP) |
| Gałąź | `step-09-upstream-2.12.0` |
| Gałąź docelowa | `main` |
| Baza | `main` @ `dbc0f18` (= tag `v0.1.0`, Release „TapCue 0.1.0” opublikowany 2026-10-25) |
| Upstream | `mobile-dev-inc/Maestro` `v2.12.0` → `0df4cf3c8cad946e0494f462c6ce4987145cd36f` (2026-10-26), 22 commity od `v2.11.0` (historia liniowa) |

## 1. Co zrobiono

Polecenie właściciela (2026-11-01): przenieść zmiany z nowego wydania upstream i utworzyć wydanie 0.2.0, z zachowaniem
głównego wymagania — TapCue działa offline i nie wywołuje zewnętrznych endpointów.

### Commity upstream `v2.11.0..v2.12.0`

Każdy przeniesiony commit to `git cherry-pick -x` (w wiadomości „cherry picked from commit …”); adaptacje forka są w tym samym
commicie (rozwiązanie konfliktu) albo w osobnym commicie forka (ostatnie wiersze tabeli). Ocena: sieć (N-xx), licencje (L-xx),
usunięte funkcje (Cloud, AI, web, simulator-server).

| Upstream | Opis | Decyzja | Adaptacja w TapCue |
|---|---|---|---|
| `51538ec1` #3637 | różnica `assertScreenshot` w artefaktach przebiegu | **wzięty** | — (`AGENTS.md`: reguła „trzy kategorie plików” z upstream) |
| `0ca4d8a7` #3645 | crash report dla `appId` po interpolacji | **wzięty** | — |
| `9ea05fe9` #3648 | crash iOS z zakończenia procesu (`IOSAppTerminationFinder`, `AppCrash` → `AppNotRunning`) | **wzięty** | usunięty `.github/workflows/test-e2e.yaml` (nie istnieje w forku); zmiany aplikacji demo i `.github/actions/build-demo-app` zostają (fixture, nieużywane w CI). Nowy proces lokalny: `xcrun simctl spawn <udid> log show` |
| `97fac1f0` #3656 | `sdkmanager` z limitem czasu i wyjściem do pliku | **wzięty z adaptacją** | konflikt w `DeviceService`: wzięte `runSdkManager`/`parseSdkPackagePaths`/`destroyProcessTree`, ale tylko z `--list_installed`; **nie** przywrócone `sdkmanager --list` (zdalny katalog Google) ani `installAndroidSystemImage` (N-24, STEP-03) |
| `3c9f8eab` #3511 | błędne `index`/`point`/`speed` jako błąd testu (`NumericFields`) | **wzięty** | — (część „Breaking”: wartości spoza zakresu kończą komendę błędem; `WorkspaceValidator`, który też ich używa, nie jest wołany przez CLI forka — walidował upload Cloud) |
| `60f9ba47` #3666, `59d25205` #3674, `17d0ee78` #3714 | wersja simulator-server | **pominięty** | L-01 — fork nie ma wersji `simulatorServer` ani taska pobierania |
| `c73b1202` #3668 | symulatory na Xcode 27 (bez Simulator.app) | **wzięty** | — |
| `df40dea5` #3642 | wytyczne PR/issue upstream (`docs/issues.md`, `docs/pull-requests.md`, szablon PR, `CONTRIBUTING.md`) | **pominięty** | proces upstream (mobile.dev), nie dotyczy forka |
| `7b0d53fb` #3667 + `0e5fcfd7` | `inputText`/`tapOn` bez czekania na animacje okien (`InputInjector`) + APK z CI upstream | **wzięte** | konflikty: `e2e/demo_app/CLAUDE.md` (sekcja `.tapcue/`), flow `slow_splash_handoff.yaml` trafił do `e2e/demo_app/.tapcue/`. Z wiadomości commita APK usunięty znacznik `[skip ci]` (żeby nie wyłączał CI forka). APK: fork nie zmieniał `maestro-android` względem `v2.11.0`, więc `maestro-server.apk` z CI upstream odpowiada źródłu po cherry-picku |
| `0fa5249b` #3683 | Selenium 4.51.0, CDP v155 | **pominięty** | platforma web usunięta (D-4, STEP-03B) |
| `a4ccea7a` #3649 | `launchApp` dla web czeka na stronę | **pominięty** | jw. (`CdpWebDriver`, `e2e/workspaces/web` usunięte) |
| `26a69b41` #3679 | `pressKey: Tab` przenosi fokus | **wzięty** | — |
| `3b068b87` #3676 | ograniczony czas startu emulatora, log emulatora w `DebugLogStore` | **wzięty** | — |
| `ab27b9a9` #3687 | `jarchivelib` → `compress4j` 5.1.0 | **wzięty z adaptacją** | pominięte zmiany usuniętych plików (`CloudInteractor`, `DownloadSamplesCommand`, `RunOnCloudTool`); w `maestro-cli/build.gradle.kts` tylko zamiana zależności (bez `ktor-server-*`, usuniętych w STEP-04); `IOSBuildProductsExtractorTest` — wersja forka (test budowy ze źródła, D-5; wersja upstream testuje usunięte ZIP-y) |
| `028c18b9` #3690 | `assertScreenshot` w walidacji workspace | **wzięty** | — |
| `6468ea39` #3660 | blokada bazy sesji (`KeyValueStore`), zamykanie strumieni `Unpacker` | **wzięty z adaptacją** | fork nie ma `Unpacker.unpackTree` (simulator-server, STEP-00B): poprawka zamykania strumieni w `sameContents(URL, File)`, test `unpackTree` usunięty, treść fixture neutralna |
| `436309bd` #3693 | skracanie nazw katalogów artefaktów > 255 B | **wzięty** | — |
| `da0f0f2b` #3695 | `--priority` dla `maestro cloud`/`run_on_cloud` | **pominięty** | Maestro Cloud usunięty (STEP-01) |
| `0df4cf3c` #3713 | przygotowanie wydania upstream (`VERSION_NAME`, `CLI_VERSION=2.12.0`) | **pominięty** | wersjonowanie TapCue; sekcja `## 2.12.0` przeniesiona dosłownie do części upstream `CHANGELOG.md` |
| fork `1ba19a41` | `KeyValueStoreTest` (nowy test upstream) czytał stdout+stderr sondy | poprawka testu | JVM wypisuje „Picked up JAVA_TOOL_OPTIONS” na stderr (środowisko sesji agenta; może też u użytkowników/w CI) — sonda czytana tylko ze stdout |
| fork (po walidacji `release.yml` run 41309244100) | `IntegrationTest` Case 079-2 padł na macos-26: flow `079_scroll_until_visible.yaml` miał `timeout: 10` ms na całą pętlę `scrollUntilVisible` (plik niezmieniony od baseline; lokalnie i w `ci.yml` zielony) | uodpornienie testu | budżet 3000 ms; oczekiwane zdarzenia bez zmian (jeden swipe), „no view” pętli 3 s na `FakeDriver`. Kod produktu bez zmian |
| fork `33e20c0f` | `RunOutputStaysInBundleTest` (nowy test upstream) wymieniał komendy AI | adaptacja testu | komendy AI usunięte w STEP-02 |

### Zmiany forka

| Plik | Zmiana |
|---|---|
| `maestro-cli/gradle.properties` | `CLI_VERSION=0.2.0` (archiwum `tapcue-0.2.0-macos-arm64.zip`, `tapcue --version` → `tapcue 0.2.0`) |
| `Errors.kt`, `TestCommand.kt`, `McpServer.kt`, `mcp/cheat-sheet.md`, `WebPlatformDisabledTest.kt` | komunikat „The web platform is not supported in TapCue” bez numeru wersji (był „…in TapCue 0.1.0”) |
| `CHANGELOG.md` | część TapCue: `### 0.2.0 — 2026-11-01` (zmiany z upstream, nieprzeniesione, `#### Znane ograniczenia`); wstęp — sync z `v2.12.0`; część upstream: sekcja `## 2.12.0` dosłownie z upstream |
| `README.md` | instalacja 0.2.0, ograniczenia (zmiana walidacji `speed`/`point`), pochodzenie „forked from 2.11.0, synced with 2.12.0” |
| Dokumenty | ROADMAP (STEP-08 MERGED/wydane, STEP-09, gate'y dla 0.2.0), NETWORK-AUDIT v12, LICENSE-AUDIT v10, ARCHITECTURE-LOCAL §5, RELEASING (status), AGENTS.md (nota o sync upstream), ten plik |

**Bez zmian:** `ci.yml`, `release.yml`, `verify-dist.sh` i jego allowlisty, `allowed-licenses.tsv`, suplement not, `Vendor/`, pakiety
`maestro.*`, moduły Gradle, format flow, katalogi `~/.tapcue`/`.tapcue/`. **Nie utworzono tagu `v*` ani Release.**

### Sieć (główne wymaganie)

Diff dodanego kodu (`*.kt`, `*.kts`, `*.java`) przeszukany pod kątem HTTP, URL/URI, socketów, klientów sieciowych i pobierania —
brak nowych ścieżek sieciowych. Nowe procesy są lokalne: `xcrun simctl spawn … log show`, `sdkmanager --list_installed` (z limitem
czasu), `emulator` (wyjście do pliku). Ścieżki upstream wymagające sieci nie zostały przeniesione (simulator-server, Selenium/web,
Cloud, `sdkmanager --list`). Dowód działania: egress-test (§2).

### Licencje

Nowe w `lib/`: `compress4j` 5.1.0, `jspecify` 1.0.1, `commons-lang3` 3.21.0 (Apache-2.0); usunięty `jarchivelib`; podbite
`commons-compress` 1.28.0, `commons-io` 2.22.0, `commons-codec` 1.19.0, `slf4j-api` 2.0.20. Bez nowych licencji. Szczegóły:
`LICENSE-AUDIT.md` (v10).

## 2. Walidacja

Środowisko lokalne: Linux x86_64, OpenJDK 17 (Ubuntu, doinstalowany; domyślny JDK sesji to 21), `dl.google.com` zablokowane
(proxy 403), Maven Central — mirror GCS, brak Maca, Android SDK i urządzeń. **Wariant analityczny** (STEP-00 §3: kopia w scratchpadzie
bez `maestro-android` i `google()`, niezatwierdzona). **Bez flag `-x`.**

| Test | Komenda | Wynik |
|---|---|---|
| Wszystkie testy + detekt + archiwum (od zera) | `LANG=C.UTF-8 ./gradlew --continue --rerun-tasks :maestro-orchestra:detektMain :maestro-client:detektMain test :maestro-cli:macosArm64DistZip` | **PASS — 1416 testów, 0 porażek, 9 pominiętych** (0.1.0: 1325/5; nowe testy upstream). Pierwszy przebieg (przed poprawkami testów forka `1ba19a41`, `33e20c0f`): 5 porażek `KeyValueStoreTest` (stderr sondy) i błąd kompilacji `RunOutputStaysInBundleTest` (komendy AI) |
| Archiwum | SHA-256 `tapcue-0.2.0-macos-arm64.zip` (lokalnie, wariant analityczny) | `10ba7b31299cf4dc292be14ce0d25c67c563ee93246efa624f65dba377f009bf` — miarodajny jest SHA-256 z `ci.yml`/`release.yml` (`reproduce`) |
| Weryfikacja archiwum | `scripts/verify-dist.sh --smoke …/tapcue-0.2.0-macos-arm64.zip` | **PASS** — `tapcue 0.2.0` |
| Testy negatywne `verify-dist.sh` | `scripts/fork/test-verify-dist.sh …zip` | **PASS** |
| Skan licencji | `python3 -I scripts/fork/check-licenses.py …zip` | **PASS** — 136 bibliotek na liście |
| Testy negatywne skanu i zależności | `scripts/fork/test-ci-checks.sh …zip` | **PASS** |
| Zakazane zależności | `scripts/fork/check-forbidden-deps.sh` | **PASS** — 517 linii drzewa, zero zakazanych |
| Strażnik simulator-server | `scripts/fork/check-no-simulator-server.sh` | **PASS** |
| Zawartość `lib/` vs 0.1.0 | lista plików archiwum z `main` (ten sam wariant) vs 0.2.0 | różnice wyłącznie opisane w §1 „Licencje” |
| Noty | §1 `THIRD-PARTY-NOTICES.md` 0.1.0 vs 0.2.0 | różnice tylko w wierszach i tekstach zmienionych bibliotek; NOTICE nowych commons dołączone |
| Harness egress | `scripts/fork/egress-test.sh self-test …` | **PASS** — sondy wykryte, loopback nie liczony |
| CLI i MCP bez sieci | `scripts/fork/egress-test.sh cli <paczka 0.2.0>/bin/tapcue …` | **PASS** — `version`, `help`, `check-syntax`, `invalid-yaml`, `unknown-command`, `test-no-device`, `cli-crash`, `mcp`, `e2e-cli-test`; **zero prób poza loopback** |
| Skrypty wydania | `scripts/fork/test-release.sh` | **PASS** |
| Notatki 0.2.0 (G-8) | `python3 -I scripts/fork/release-notes.py 0.2.0 --require-section` | **PASS** — sekcja i „Znane ograniczenia” znalezione |
| `maestro-android` (APK, `InputInjector`), Android emulator, iOS Simulator, krok offline macOS | — | **NOT RUN lokalnie** (brak Google Maven/SDK/Maca) — w CI PR-09 (`build`, `android`, `macos`) i walidacji `release.yml` |
| `IOSDriverCrashSimulatorTest` | — | **NOT RUN** — wymaga `MAESTRO_TEST_SIMULATOR_UDID` (pomijany w `./gradlew test`, także w CI forka) |
| CI `ci.yml` na PR-09, commit `208cd6c5` | [run 41309245542](https://github.com/rtnk/TapCue/actions/runs/41309245542) | **PASS** — wszystkie joby |
| `release.yml` walidacja na gałęzi PR-09, commit `208cd6c5` | [run 41309244100](https://github.com/rtnk/TapCue/actions/runs/41309244100) | **FAIL** — `build` (macos-26): `IntegrationTest > Case 079-2` (`ElementNotFound`, budżet 10 ms) — test uodporniony (§1) |
| `release.yml` walidacja po uodpornieniu | — | wynik — w PR (uzupełnić przed scaleniem) |
| Tag, publish, instalacja z Release (G-6), AT-01…AT-11 na pobranej paczce | — | **NOT RUN** — po scaleniu PR-09, na polecenie właściciela |

## 3. Ryzyka, blokery, znane problemy

- **Zmiana niekompatybilna z upstream** (`#3511`): flow z `speed` spoza 0–100 albo literalnym `point` spoza zakresu, które dotąd
  przechodziły (z wartością domyślną), teraz kończą się błędem. Opisane w CHANGELOG i README.
- **APK z CI upstream** — `maestro-server.apk` nie jest przebudowywany w CI forka (jak dotąd); zgodność ze źródłem wynika z braku
  zmian forka w `maestro-android`. Kolejne sync-i: jeśli fork zmieni `maestro-android`, APK trzeba budować w forku.
- **Pokrycie nowych poprawek iOS/emulatora** — tylko testy jednostkowe; CI nie uruchamia `start-device` ani flow na iOS.
- Hash archiwum z `main` zbudowany lokalnie z `git archive` (`73efc744…`) różni się od opublikowanego 0.1.0 (`e8dd01b0…`) — użyty
  wyłącznie do porównania list plików; powtarzalność wydań sprawdza job `reproduce` w `release.yml`, nie ten build.
- **Data w sekcji CHANGELOG** (`2026-11-01`) — planowana data wydania; przy późniejszym tagu — korekta w PR przed tagiem.

## 4. Warunek rozpoczęcia operacji release

PR-09 **scalony** do `main` (właściciel); `ci.yml` zielony na commicie scalenia (wszystkie joby, `android` bez pominięć, krok offline
`macos` z „OK: no denied network attempt by TapCue”); walidacja `release.yml` (`workflow_dispatch`) zielona, `reproduce` IDENTICAL.

## 5. Następny krok

**Operacja release v0.2.0**: `docs/RELEASING.md` §4.2 (adnotowany tag `v0.2.0` na commicie scalenia PR-09) → run `release.yml`
w trybie publish → §4.3 (dwa zasoby, notatki z sekcji 0.2.0, SHA-256 = `reproduce`, instalacja na macOS arm64, offline).

## 6. PROMPT DLA NASTĘPNEGO AGENTA (operacja release v0.2.0)

```text
Jesteś agentem kodującym w repozytorium rtnk/TapCue (fork mobile-dev-inc/Maestro → „TapCue”, polecenie `tapcue`).
1. git fetch origin && git checkout main && git pull origin main. Potwierdź, że PR-09 („STEP-09: sync upstream v2.12.0 and
   release 0.2.0”) jest MERGED, `ci.yml` na commicie scalenia zielony, CLI_VERSION=0.2.0, walidacja `release.yml`
   (workflow_dispatch na main) zielona z `reproduce` IDENTICAL, a tag v0.2.0 i Release 0.2.0 nie istnieją. Inaczej — zatrzymaj się.
2. Przeczytaj AGENTS.md (nota forka), docs/handovers/STEP-09.md, docs/RELEASING.md.
3. Tag v0.2.0 twórz WYŁĄCZNIE na wyraźne polecenie właściciela w tej sesji, dokładnie wg RELEASING §4.2. Nie przesuwaj ani nie
   usuwaj tagów, nie edytuj Release.
4. Śledź run `release.yml` (publish); przy porażce — RELEASING §4.4 (wersja patch, bez nadpisywania).
5. Weryfikacja wg RELEASING §4.3; wyniki dopisz do docs/handovers/STEP-09.md (sekcja „Publikacja”) i ROADMAP w osobnym PR.
6. Raportuj tylko faktycznie uruchomione testy; reszta NOT RUN z powodem.
```
