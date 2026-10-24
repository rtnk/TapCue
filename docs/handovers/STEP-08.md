# Handover — STEP-08: Release candidate i v0.1.0

| Pole | Wartość |
|---|---|
| Krok | STEP-08 — PR-08 w review; tag `v0.1.0` i publikacja — **nie wykonane** (po scaleniu, na polecenie właściciela) |
| PR | PR-08 „STEP-08: Release candidate 0.1.0” (link w PR / ROADMAP) |
| Gałąź | `claude/step-08-release-0.1.0-2drblu` (gałąź przydzielona sesji agenta; prompt podawał `step-08-release-0.1.0`) |
| Gałąź docelowa | `main` |
| Baza | `main` @ `aeaebc5` (merge PR #16 — poprawka CI po STEP-07) |
| Commit SHA | ostatni commit gałęzi (patrz PR) |

## 1. Co zrobiono

### Weryfikacja wejścia (krok 1 promptu)

- PR #15 (STEP-07) i #16 (poprawka CI) — **MERGED** (2026-10-24, `610568a`, `aeaebc5`).
- `ci.yml` na `main` (`aeaebc5`, [run 40112502799](https://github.com/rtnk/TapCue/actions/runs/40112502799)) — **zielony**:
  `changes`, `build`, `egress`, `android` (żaden krok nie pominięty; emulator API 34, 8/8 scenariuszy, `settings` PASS w 116 s),
  `macos` (rejestr odmów sandboxa zadziałał: „OK: no denied network attempt by TapCue”).
- `release.yml` — wyzwalacze tylko `push: tags` i `workflow_dispatch` (bez `pull_request`).
- **`release.yml` `workflow_dispatch` na `main` — CZERWONY** ([run 40115219200](https://github.com/rtnk/TapCue/actions/runs/40115219200),
  próby 1 i 2): `prepare` zielony, `build` padł w kroku „offline” (`scripts/fork/macos-offline-test.sh`, `REQUIRE_REGISTER=true`):
  „The sandbox log did not show curl's denied attempt” — po stałych 2 s `log show` nie zawierał jeszcze odmowy sondy `curl`.
  Komendy TapCue pod sandboxem przeszły (`--version`, `--help`, `check-syntax`, MCP). `egress`, `reproduce`, `publish` — `skipped`.
  Brak Release i tagów `v*`. Zgłoszone właścicielowi zgodnie z promptem; **decyzja właściciela: poprawka w PR-08** (osobny commit).
- Widoczność repozytorium (D-9): właściciel podał, że repozytorium jest już publiczne; API GitHub z sesji agenta
  (`GET /repos/rtnk/TapCue`, wyszukiwarka; 2026-10-24 ok. 17:30 UTC) zwraca `"visibility": "private"` — **do potwierdzenia**.

### Decyzje właściciela (2026-10-24, przed zmianami)

| Temat | Decyzja |
|---|---|
| Przegląd `THIRD-PARTY-NOTICES.md`/suplementu | dopisać brakujące pełne teksty licencji → zrealizowane (suplement §3.6) |
| L-05: Google Play services, JUnit EPL-1.0 w APK | **akceptacja**, bez dodatkowej wzmianki w ograniczeniach |
| Polityka `scripts/fork/allowed-licenses.tsv` | **akceptacja** |
| D-9 | „już publiczne” (API: `private` — wyżej) |
| Czerwony `release.yml` na `main` | poprawka w PR-08 |

### Zmiany

| Plik | Zmiana |
|---|---|
| `maestro-cli/gradle.properties` | `CLI_VERSION=0.1.0` (archiwum `tapcue-0.1.0-macos-arm64.zip`, `tapcue --version` → `tapcue 0.1.0`) |
| `maestro-cli/src/main/java/maestro/cli/driver/RealIOSDeviceDriver.kt` | przebudowa sterownika urządzeń fizycznych przy wersji **różnej** od zapisanej (było: wyższej) — 0.1.0 < 2.11.0, więc build z 2.11.0 nie zostałby zastąpiony; symulator (`IOSSimulatorDriver`) porównywał już równość |
| `maestro-cli/src/test/.../RealDeviceDriverTest.kt` | test: sterownik z `2.11.0` jest przebudowywany przez `0.1.0` |
| `CHANGELOG.md` | `### Unreleased` → `### 0.1.0 — 2026-10-24` ze wstępem, wpisami STEP-08 i `#### Znane ograniczenia` (nieprzetestowane platformy: Windows, Linux jako platforma docelowa, macOS x64, fizyczne iOS i Android; web wyłączony; pokrycie iOS/Android w CI; brak suity E2E upstream; polityka `http.*`; usunięte funkcje; klucz debug APK; Java nie dołączona; brak podpisu/notaryzacji Apple; brak synchronizacji z upstream) |
| `README.md` | przepisany dla TapCue: logo, opis i nota „based on Maestro (Apache-2.0), not affiliated with mobile.dev” (D-1), instalacja z Release (`gh release download`, `shasum -c`, kwarantanna), testy, MCP (`claude mcp add`, JSON), znane ograniczenia, build ze źródła, dokumentacja, licencja. Bez linków do maestro.dev/mobile.dev (N-35); link do repozytorium upstream na GitHub jako atrybucja |
| `docs/assets/tapcue-logo.png` | logo TapCue od właściciela (PNG 1254×1254, 1,2 MB, SHA-256 `d76f6c3b…9c19df1`); tylko w repozytorium — **nie** w paczce (zawartość ZIP i `verify-dist.sh` bez zmian) |
| `docs/audit/notices-supplement.md` | §3.2: L-05 zaakceptowane; nowa §3.6 — teksty licencji bibliotek APK spoza Apache-2.0: JUnit 4.13.2 (EPL-1.0), Hamcrest 1.3 (BSD-3), checker-qual 3.33.0 (MIT), SLF4J 1.7.36 (MIT, z repozytorium `v_1.7.36`), kotlin-retry 2.0.1 (ISC), `javax.annotation-api` 1.3.2 (CDDL+GPLv2-CPE); teksty z JAR-ów Maven Central (mirror) |
| `scripts/fork/macos-offline-test.sh` (osobny commit) | sondy `curl` przed i po komendach TapCue, każda z oczekiwaniem na wpis w logu do `LOG_WAIT` (60 s, co 5 s) zamiast stałego `sleep 2`; odmowy `java` czytane dopiero, gdy log pokazał sondę końcową (znacznik nadrobienia logu). Wymaganie rejestru w `release.yml` bez zmian; `release.yml` i `ci.yml` bez zmian |
| Dokumenty | ROADMAP (STEP-07 MERGED, STEP-08 realizacja, gate'y ze statusem, D-9), handover STEP-07 (scalony, §4), RELEASING (korekty po faktach), LICENSE-AUDIT v9 (zamknięty), NETWORK-AUDIT v11, ARCHITECTURE-LOCAL §5, AGENTS.md, ten plik |

**Bez zmian:** pakiety `maestro.*`, moduły Gradle, format flow, `~/.tapcue`/`.tapcue/`, `Vendor/`, `release.yml`, `ci.yml`,
`verify-dist.sh`, zawartość paczki (poza numerem wersji i suplementem not). **Nie utworzono tagu `v*` ani Release.**

## 2. Walidacja

Środowisko lokalne: Linux x86_64, OpenJDK 17.0.20.1 (Ubuntu, doinstalowany), `dl.google.com` zablokowane, brak Maca, Android SDK
i urządzeń; doinstalowane `iproute2`, `strace`. **Wariant analityczny** (STEP-00 §3: kopia w scratchpadzie bez `maestro-android`
i `google()`, mirror Maven Central jak w STEP-01 §2; niezatwierdzony). **Bez flag `-x`.**

| Test | Komenda | Wynik |
|---|---|---|
| Wszystkie testy + detekt + archiwum (od zera) | `LANG=C.UTF-8 ./gradlew --continue --rerun-tasks :maestro-orchestra:detektMain :maestro-client:detektMain test :maestro-cli:macosArm64DistZip` | **PASS — 1325 testów, 0 porażek, 5 pominiętych** (STEP-07: 1324; +1 `RealDeviceDriverTest`) |
| Archiwum | SHA-256 `tapcue-0.1.0-macos-arm64.zip` (lokalnie) | `e8dd01b061f5fe9d381cf510c65161c7181108833e0c05c757796750ddab6ec1` |
| Weryfikacja archiwum | `scripts/verify-dist.sh --smoke …/tapcue-0.1.0-macos-arm64.zip` | **PASS** — `tapcue 0.1.0` |
| Testy negatywne `verify-dist.sh` | `scripts/fork/test-verify-dist.sh …zip` | **PASS** |
| Skan licencji | `python3 -I scripts/fork/check-licenses.py …zip` | **PASS** — 134 biblioteki na liście |
| Testy negatywne skanu i zależności | `scripts/fork/test-ci-checks.sh …zip` | **PASS** |
| Strażnik simulator-server | `scripts/fork/check-no-simulator-server.sh` | **PASS** |
| Kompletność tekstów licencji | skrypt ad hoc: każdy wiersz §1 wygenerowanego `THIRD-PARTY-NOTICES.md` z licencją inną niż Apache-2.0 ma tekst w §2 (z JAR-a) lub §3.5 | **PASS** — 134 wiersze, 0 luk; §3.6 obecna w wygenerowanym pliku |
| Harness egress | `scripts/fork/egress-test.sh self-test …` | **PASS** — sondy wykryte, loopback nie liczony |
| CLI i MCP bez sieci | `scripts/fork/egress-test.sh cli <paczka 0.1.0>/bin/tapcue …` | **PASS** — zero prób poza loopback |
| Skrypty wydania | `scripts/fork/test-release.sh` | **PASS** — 38 przypadków |
| Notatki 0.1.0 (G-8) | `python3 -I scripts/fork/release-notes.py 0.1.0 --require-section` | **PASS** — sekcja i „Znane ograniczenia” znalezione |
| `macos-offline-test.sh` (po zmianie) | `bash -n` | składnia OK; działanie — tylko na macOS (job `macos` w `ci.yml` PR-08 i `release.yml` walidacja) |
| `release.yml` walidacja na `main` (przed zmianami) | [run 40115219200](https://github.com/rtnk/TapCue/actions/runs/40115219200) | **FAIL** 2/2 — opis w §1 |
| `release.yml` walidacja na gałęzi PR-08 | `workflow_dispatch` `--ref` gałąź PR-08 | patrz PR (uzupełniane po runie) |
| CI `ci.yml` na PR-08 | checki PR | patrz PR |
| Tag, publish, instalacja z Release (AT-11, G-6), AT-01…AT-11 na pobranej paczce | — | **NOT RUN** — tag dopiero po scaleniu PR-08 i na polecenie właściciela |

## 3. Ryzyka, blokery, znane problemy

- **Rejestr odmów w logu macOS** — poprawka czeka na wpis maks. 60 s na sondę; jeśli runner w ogóle nie loguje odmów sandboxa
  (zmiana obrazu), `release.yml` nadal się zatrzyma (zamierzone: wydanie bez dowodu braku prób nie powstaje). Wtedy: zbadać
  `log show` na runnerze, nie wyłączać `REQUIRE_REGISTER`.
- **Widoczność repozytorium** — rozbieżność deklaracji właściciela i API (`private`). W repozytorium prywatnym Release i
  `gh release download` z README wymagają dostępu; minuty macOS płatne.
- **Pierwsza prawdziwa publikacja** — ścieżka `publish` (`gh release create --verify-tag`, pobranie zasobów) i kontrola tagu
  adnotowanego po `actions/checkout` nadal nieprzetestowane na GitHub (handover STEP-07 §3). Procedury awaryjne: `docs/RELEASING.md` §4.4.
- **Data w sekcji CHANGELOG** (`2026-10-24`) — planowana data wydania; jeśli tag powstanie później, notatki pokażą tę datę
  (korekta — w PR przed tagiem).
- **Logo** — właściciel dostarczy docelowo wersję SVG (ostrość, tryb ciemny: obecny PNG ma jasne tło). Przed szerszym
  upublicznieniem warto sprawdzić logo pod kątem kolizji ze znakami towarowymi — decyzja właściciela (LICENSE-AUDIT §5 pkt 6).
- Tekst CDDL `javax.annotation-api` zawiera znaki `?` w miejscu oryginalnych znaków spoza ASCII — tak jest w pliku w JAR-ze
  (przeniesione bez zmian).
- Wersja 0.1.0 jest niższa niż numer wcześniejszych buildów forka (2.11.0): sterownik symulatora i urządzeń fizycznych jest
  przebudowywany przy zmianie wersji w obie strony.

## 4. Warunek rozpoczęcia następnego kroku (operacja release)

PR-08 **scalony** do `main`; `ci.yml` zielone na commicie scalenia; `release.yml` (walidacja) zielony na gałęzi PR-08 — bez
ostrzeżeń `prepare` o sekcji CHANGELOG; G-3 zaakceptowany (tak). Potem — **wyłącznie na polecenie właściciela** — tag
adnotowany `v0.1.0` na commicie scalenia (`docs/RELEASING.md` §4.2) i weryfikacja Release (§4.3).

## 5. Następny krok

**Operacja release v0.1.0** (dokończenie STEP-08): tag → run `release.yml` w trybie publish → weryfikacja Release: dwa zasoby,
notatki z sekcją 0.1.0 i ograniczeniami, SHA-256 zasobu = SHA-256 z `reproduce`, pobranie na macOS arm64, `shasum -a 256 -c
SHA256SUMS`, instalacja z pobranego pliku, AT-01…AT-11, offline (G-5 macOS; `pf` — jeśli możliwe). Wyniki dopisać do tego pliku.

## 6. PROMPT DLA NASTĘPNEGO AGENTA (operacja release v0.1.0)

```text
Jesteś agentem kodującym w repozytorium rtnk/TapCue (fork mobile-dev-inc/Maestro → „TapCue”, polecenie `tapcue`).
Nie masz pamięci poprzednich sesji. Zanim cokolwiek zmienisz:

1. git fetch origin && git checkout main && git pull origin main. Potwierdź przez GitHub, że PR-08
   („STEP-08: Release candidate 0.1.0”) jest MERGED, `ci.yml` na commicie scalenia zielony (wszystkie joby, `android` bez
   pominięć, krok offline `macos` z „OK: no denied network attempt by TapCue”), `maestro-cli/gradle.properties` ma
   CLI_VERSION=0.1.0, a `release.yml` uruchomiony przez workflow_dispatch na main jest zielony (`publish` skipped,
   `reproduce` IDENTICAL). Sprawdź, że nie istnieje tag v0.1.0 ani żaden Release. Jeśli cokolwiek nie zachodzi — zatrzymaj się.
2. Przeczytaj: AGENTS.md (nota forka), docs/handovers/STEP-08.md, docs/RELEASING.md (całość), docs/ROADMAP-0.1.md §4–§5.
3. Tag v0.1.0 twórz WYŁĄCZNIE na wyraźne polecenie właściciela w tej sesji (zapytaj). Wtedy dokładnie wg RELEASING §4.2:
   adnotowany tag na commicie scalenia PR-08 (origin/main), push tagu. Nie przesuwaj i nie usuwaj tagów, nie edytuj Release.
4. Śledź run `release.yml` w trybie publish: wynik każdego joba; SHA-256 archiwum z joba `build` = z `reproduce` = zasobu Release.
   Przy porażce — RELEASING §4.4 (bez nadpisywania; wersja patch).
5. Weryfikacja Release (RELEASING §4.3): dokładnie dwa zasoby, tytuł „TapCue 0.1.0”, notatki z sekcji 0.1.0 (Znane ograniczenia,
   nota pochodzenia, SHA-256). Na macOS arm64 (Mac właściciela albo job macos-26 uruchomiony ręcznie): gh release download,
   shasum -a 256 -c SHA256SUMS, unzip, tapcue --version, AT-01…AT-11 (ROADMAP §5), offline (sandbox-exec; pf — jeśli możliwe).
6. Zapisz wyniki w docs/handovers/STEP-08.md (§2, nowa sekcja „Publikacja”), ROADMAP (STEP-08 → MERGED/RELEASED, gate'y G-1…G-8),
   CHANGELOG (bez zmian treści sekcji 0.1.0) — w osobnym PR „STEP-08: v0.1.0 release verification”. Bez auto-merge.
7. Raportuj tylko faktycznie uruchomione testy; reszta NOT RUN z powodem.
```
