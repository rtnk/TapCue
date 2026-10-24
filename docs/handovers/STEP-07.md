# Handover — STEP-07: Release pipeline

| Pole | Wartość |
|---|---|
| Krok | STEP-07 — **scalony** 2026-10-24 (PR #15, merge `610568a`; poprawka CI PR #16, merge `aeaebc5`) |
| PR | PR-07: https://github.com/rtnk/TapCue/pull/15 |
| Gałąź | `step-07-release` |
| Gałąź docelowa | `main` |
| Baza | `main` @ `52c7c38` (merge PR #14 — STEP-06) |
| Commit SHA | `2c558a3` (ostatni commit gałęzi), merge `610568a` |

## 1. Co zrobiono

Cel (STEP-07, gate'y G-4 i G-8 w pipeline; przygotowanie G-5/G-6 dla STEP-08): wydanie powstaje wyłącznie z adnotowanego tagu
na commicie z `main`, z dokładnie tego commita, po tych samych kontrolach co w CI, z powtarzalnymi bajtami — i nigdy nie
nadpisuje istniejącego Release. Tryb walidacji robi wszystko poza publikacją.

### `.github/workflows/release.yml`

`on: push: tags: ['v[0-9]+.[0-9]+.[0-9]+']` (tryb **publish**) + `workflow_dispatch` (tryb **validate**); `permissions:
contents: read` na poziomie workflow; akcje przypięte do SHA (te same wersje co w `ci.yml`); bez `pull_request_target`, bez
sekretów poza `GITHUB_TOKEN`, bez cache (`setup-java` bez `cache: gradle`, Gradle z `--no-build-cache`); `concurrency` bez
anulowania. JDK przypięty: `RELEASE_JDK: '17.0.21'` (Zulu).

| Job | Runner | Co robi |
|---|---|---|
| `prepare` | ubuntu | `scripts/fork/release-context.sh` → `mode`/`version`/`tag`/`sha`/`on_main`. Tryb publish **tylko** dla `push` tagu; wtedy tag musi być adnotowany, `vX.Y.Z`, wskazywać zbudowany commit, równać się `v<CLI_VERSION>`, a commit musi być przodkiem `origin/main`. `publish-release.sh check` (brak Release). `release-notes.py --require-section` (sekcja wersji w części TapCue `CHANGELOG.md` z „Known limitations”/„Znane ograniczenia”, G-8). `test-release.sh`. W trybie validate fakty są raportowane, a braki dają ostrzeżenia |
| `build` | `macos-26` | checkout = `sha` z `prepare`; `uname -m` = `arm64`; wrapper validation; fakty builda (`build-info.txt`); detekt + `./gradlew test`; `macosArm64DistZip`; nazwa archiwum `tapcue-<CLI_VERSION>-macos-arm64.zip` + `shasum -c`; `verify-dist.sh --smoke`; `check-licenses.py` (raport w podsumowaniu); `check-forbidden-deps.sh`; `check-no-simulator-server.sh`; `tapcue --version` z rozpakowanej paczki zawiera wersję; `macos-offline-test.sh` z `REQUIRE_REGISTER=true` (bez odmowy sondy `curl` w logu sandboxa → porażka, nie ostrzeżenie); `release-notes.py`; artefakty `tapcue-release` (tylko archiwum + `SHA256SUMS`), `release-notes`, `release-build-reports` (30 dni) |
| `egress` | ubuntu | archiwum z artefaktu (`sha256sum -c`), `egress-test.sh self-test` i `cli` |
| `reproduce` | ubuntu | build archiwum z tego samego commita tym samym JDK; `compare-archives.py` (`IDENTICAL` albo lista różnic) + `cmp SHA256SUMS` — różnica = porażka |
| `publish` | ubuntu | `if: mode == publish && event == push && ref_type == tag`, `needs` wszystkich; **jedyny** z `permissions: contents: write`; `publish-release.sh publish` (niżej) |

**Tymczasowy wyzwalacz `pull_request`** (tylko w tym PR, usunięty ostatnim commitem przed scaleniem): `workflow_dispatch` na
gałęzi innej niż domyślna zwraca **404** (`POST …/actions/workflows/release.yml/dispatches` z `ref: step-07-release` —
sprawdzone), więc tryb walidacji był uruchamiany przez `pull_request` (`release-context.sh`: każde zdarzenie poza `push` tagu
= validate). Po scaleniu: `gh workflow run release.yml --ref <gałąź>` / „Run workflow”.

### Skrypty (`scripts/fork/`)

| Plik | Rola |
|---|---|
| `release-context.sh` | tryb, wersja, tag i kontrole tagu (wyżej); wejście: zmienne GitHub Actions, `MAIN_REF` (domyślnie `origin/main`) |
| `release-notes.py` | notatki: sekcja `### <wersja>` (także `### v<wersja>`, z datą po wersji) wyłącznie z części `## TapCue` `CHANGELOG.md` (upstream `### 2.11.0` nie jest brany), nota pochodzenia (D-1), SHA-256 z `SHA256SUMS`, fakty builda; `--require-section` = brak sekcji, pusta, zduplikowana albo bez „Known limitations”/„Znane ograniczenia” → FAIL; bez flagi — zastępczy tekst |
| `publish-release.sh` | `check <tag>` — FAIL, jeśli Release istnieje (lista wszystkich Release przez API — z `contents: write` także szkice; błąd API = FAIL, nie „brak”). `publish <tag> <sha> <dir> <notes>` — FAIL poza runem z wypchniętego tego tagu, przy pustych notatkach, gdy katalog zawiera coś poza `tapcue-<wersja>-macos-arm64.zip` i `SHA256SUMS`, gdy suma się nie zgadza, gdy tag na GitHub (API, z rozwinięciem tagu adnotowanego) wskazuje inny commit, gdy Release istnieje; potem `gh release create <tag> --verify-tag --title "TapCue <wersja>" --notes-file …` (nigdy nie tworzy ani nie przesuwa tagu), `gh release download` i `cmp` z zbudowanymi plikami. Nie usuwa ani nie edytuje Release |
| `compare-archives.py` | porównanie dwóch archiwów: `IDENTICAL` albo różnice wpisów (także wewnątrz JAR-ów: zawartość, tryby, czasy, kolejność) |
| `macos-offline-test.sh` | krok „Run without network access” joba `macos` z `ci.yml` przeniesiony do skryptu (bez zmiany zachowania w `ci.yml`), `REQUIRE_REGISTER=true` dla release |
| `test-release.sh` | 38 przypadków: `release-context.sh` na tymczasowym repozytorium git (validate dla `workflow_dispatch`/`pull_request`, publish dla poprawnego tagu; odrzucenia: tag lekki, zły format, niezgodny z `CLI_VERSION`, na innym commicie, spoza `main`, checkout ≠ commit, zła `CLI_VERSION`), `release-notes.py` (sekcja, granice sekcji, nota pochodzenia, SHA, fakty; odrzucenia: brak sekcji, sekcja upstream, brak ograniczeń, duplikat), `publish-release.sh` z atrapą `gh` rejestrującą wywołania (odrzucenia bez `release create`: istniejący Release, `workflow_dispatch` — bez żadnego wywołania `gh`, `pull_request`, inny tag, tag przesunięty, tag nieistniejący, błąd listy Release, brak notatek, zła suma, dodatkowy plik, inna wersja archiwum; poprawna publikacja z `--verify-tag`) |

### `ci.yml` (tylko to, czego wymaga release)

Krok offline joba `macos` woła `scripts/fork/macos-offline-test.sh` (ta sama treść); job `build` uruchamia `test-release.sh`;
`build` (podsumowanie joba) i `macos` wypisują `SHA256SUMS` — do porównania z `release.yml` dla tego samego commita.

### Dokumentacja

`docs/RELEASING.md` przepisany: paczka, zasady, pipeline, procedura (przygotowanie, tag, weryfikacja Release, procedury
awaryjne: czerwony run przed publikacją, odrzucony tag, częściowa publikacja, wycofanie wadliwego Release — „WITHDRAWN”,
nowy patch zamiast nadpisania), odtworzenie builda (dokładny JDK).

**Poza zakresem (bez zmian):** kod produktu, pakiety `maestro.*`, moduły Gradle, format flow, `~/.tapcue`/`.tapcue/`, zawartość
paczki, `Vendor/`, wersja CLI `2.11.0` (0.1.0 — PR-08), tagi `v*` i Release (żadne nie zostały utworzone).

## 2. Walidacja

Środowisko lokalne: Linux x86_64, OpenJDK 17.0.20.1 (Ubuntu, doinstalowany) i 21, `dl.google.com` zablokowane, brak Maca,
Android SDK i urządzeń; `unshare`/`strace` dostępne, `iproute2` doinstalowany (wymagany przez `egress-test.sh`). Lokalnie —
**wariant analityczny** (STEP-00 §3: kopia w scratchpadzie bez `maestro-android` i `google()`, mirror Maven Central jak w
STEP-01 §2, niezatwierdzona). **Bez flag `-x`.**

| Test | Komenda | Wynik |
|---|---|---|
| Wszystkie testy + detekt + archiwum (od zera, lokalnie) | `LANG=C.UTF-8 ./gradlew --continue --rerun-tasks :maestro-orchestra:detektMain :maestro-client:detektMain test :maestro-cli:macosArm64DistZip` | **PASS — 1324 testy, 0 porażek** (5 pominiętych, jak w STEP-06; kod produktu bez zmian) |
| Weryfikacja archiwum | `scripts/verify-dist.sh --smoke …/tapcue-2.11.0-macos-arm64.zip` | **PASS** |
| Testy negatywne `verify-dist.sh` | `scripts/fork/test-verify-dist.sh …zip` | **PASS** |
| Strażnik simulator-server | `scripts/fork/check-no-simulator-server.sh` | **PASS** |
| Skan licencji | `python3 -I scripts/fork/check-licenses.py …zip` | **PASS** — 134 biblioteki |
| Testy negatywne skanu i zależności | `scripts/fork/test-ci-checks.sh …zip` | **PASS** |
| Harness egress | `scripts/fork/egress-test.sh self-test …` | **PASS** — 4 sondy wykryte, loopback nie (pierwsza próba bez `ip` → FAIL harnessu, zgodnie z wymaganiami narzędzia) |
| CLI i MCP bez sieci (lokalnie) | `scripts/fork/egress-test.sh cli <paczka>/bin/tapcue …` | **PASS** — zero prób poza loopback |
| Skrypty wydania | `scripts/fork/test-release.sh` | **PASS** — 38 przypadków (lokalnie, w `ci.yml` job `build` i w `release.yml` job `prepare`) |
| `workflow_dispatch` na gałęzi | `actions/workflows/release.yml/dispatches` z `ref: step-07-release` | **404 Not Found** — zgodnie z dokumentacją GitHub (workflow nie jest na gałęzi domyślnej); zastępczo `pull_request` |
| `release.yml` — tryb walidacji | [run 40105781259](https://github.com/rtnk/TapCue/actions/runs/40105781259) (PR #15, `c59b210`; checkout = commit scalenia PR `f9d5152`) | **PASS** — `prepare` (`mode=validate`, `tag=v2.11.0`, brak Release, ostrzeżenie o braku sekcji `### 2.11.0` w części TapCue — oczekiwane), `build` (`macos26 20261013.0424.1`, macOS 26.6.2 arm64, Azul Zulu `17.0.21+9-LTS`, Gradle 8.13; detekt + testy `BUILD SUCCESSFUL in 4m 36s`; archiwum, `verify-dist`, 134 licencje, 511 linii drzewa bez zakazanych GAV, strażnik; `tapcue 2.11.0`; log sandboxa pokazał odmowę `curl`, **zero odmów `java`**), `egress` (sondy wykryte, CLI/MCP bez prób), `reproduce` (**IDENTICAL**), `publish` **skipped** |
| Test negatywny: walidacja nie tworzy Release | `list_releases`, `list_tags` po runie | **PASS** — brak Release i tagów; `publish` = `skipped`; w `test-release.sh` `publish` z `workflow_dispatch` kończy się błędem bez żadnego wywołania `gh` |
| Test negatywny: publish nie nadpisuje istniejącego Release | `test-release.sh` (atrapa `gh`) | **PASS** — `check` i `publish` z istniejącym Release → FAIL „already exists”, brak `release create`. Na prawdziwym API **NOT RUN** (wymagałoby utworzenia tagu i Release — zakazane w STEP-07); pierwszy prawdziwy przebieg: tag `v0.1.0` w STEP-08 |
| `release.yml` — tryb walidacji, drugi przebieg | [run 40106270938](https://github.com/rtnk/TapCue/actions/runs/40106270938) (`1b9eb1a`) | **PASS** — wszystkie joby jak wyżej, `reproduce` **IDENTICAL**, `publish` **skipped** |
| Powtarzalność `release.yml` ↔ `ci.yml` (ten sam commit — commit scalenia PR na `1b9eb1a`) | SHA-256 archiwum `2.11.0` z logów | **identyczne** `7f967e0e7628082d40b6ddac9a445cda6aa7fa82c11e52504870206e69e4fea2` we wszystkich buildach: `ci.yml` `build` (ubuntu x64, Zulu **17.0.20**, z cache Gradle), `ci.yml` `macos` (arm64, Zulu 17.0.21), `release.yml` `build` (macOS arm64, Zulu 17.0.21+9, bez cache), `release.yml` `reproduce` (ubuntu x64, Zulu 17.0.21); ten sam SHA także dla `c59b210` i dla buildu lokalnego (Ubuntu OpenJDK 17.0.20.1, wariant analityczny). Wniosek: bajty nie zależą od systemu, architektury, wersji poprawki JDK 17 ani cache; wydanie odtwarza się `./gradlew :maestro-cli:macosArm64DistZip` na dowolnym JDK 17 (dla pewności — Zulu 17.0.21, `RELEASING.md` §5) |
| CI `ci.yml` na PR | [run 40106270941](https://github.com/rtnk/TapCue/actions/runs/40106270941) (`1b9eb1a`) | **PASS** — `build` (w tym `test-release.sh`), `egress`, `android` (emulator uruchomiony, 8/8 scenariuszy, „Android integration passed”), `macos` (krok offline przez `macos-offline-test.sh`: zero odmów `java`). Run na ostatnim commicie (usunięcie tymczasowego wyzwalacza) — w PR |
| Tag/Release na prawdziwym repozytorium, instalacja z Release | — | **NOT RUN** — STEP-08 (AT-11, G-6) |

Jak powtórzyć:

```bash
scripts/fork/test-release.sh
./gradlew test --continue && ./gradlew :maestro-cli:macosArm64DistZip
Z=maestro-cli/build/distributions/tapcue-*-macos-arm64.zip
scripts/verify-dist.sh --smoke $Z && scripts/fork/test-verify-dist.sh $Z && scripts/fork/test-ci-checks.sh $Z
python3 -I scripts/fork/compare-archives.py $Z <archiwum z artefaktu tapcue-release>
# po scaleniu: tryb walidacji
gh workflow run release.yml --ref main
```

## 3. Ryzyka, blokery, znane problemy

- **Prawdziwa publikacja nie była wykonana** (zakaz tagów w STEP-07): ścieżka `publish` (`gh release create --verify-tag`,
  `gh release download`, API tagów) jest sprawdzona na atrapie `gh`, nie na GitHub. Pierwsze uruchomienie — `v0.1.0` w STEP-08;
  procedury awaryjne w `docs/RELEASING.md` §4.4.
- **Tag adnotowany po `actions/checkout`** (`fetch-depth: 0`): kontrola „tag adnotowany” w `release-context.sh` czyta lokalny
  `refs/tags/<tag>`; `checkout` z pełną historią pobiera tagi z obiektami tagów. Nie było jeszcze przebiegu z prawdziwym
  tagiem — jeśli kontrola odrzuci poprawny tag adnotowany, przyczyną jest checkout (poprawka: jawne `git fetch` tagu), nie tag.
- **Tymczasowy wyzwalacz `pull_request`** usunięty przed scaleniem — commit końcowy PR nie ma już runu `release.yml`
  (ostatni run walidacji jest na commicie poprzednim; różnica: tylko usunięte linie wyzwalacza). Pierwszy `workflow_dispatch` —
  po scaleniu (warunek STEP-08).
- **`RELEASE_JDK` przypięty do 17.0.21**: gdy Azul wycofa tę wersję z `setup-java`, release padnie na „Set up Java” — podbicie
  `RELEASE_JDK` to świadoma zmiana w PR (bajty archiwum nie zależą od wersji poprawki JDK 17 — §2, ale to obserwacja, nie
  gwarancja; `reproduce` wykryje różnicę).
- **Notatki wymagają sekcji `### 0.1.0` z „Znanymi ograniczeniami”** — PR-08 musi ją dodać, inaczej tag `v0.1.0` padnie
  w `prepare` (zamierzone, G-8).
- **Po scaleniu (2026-10-24):** pierwszy push `main` z PR #15 ([run 40110095189](https://github.com/rtnk/TapCue/actions/runs/40110095189))
  — `android` czerwony: flow `settings` padł na pierwszym `assertVisible: "Network & internet"` zaraz po `launchApp`
  (emulator po zimnym starcie, 2 CPU; ten sam flow trwał 108–133 s w zielonych runach). Pozostałe 7 scenariuszy i joby
  `build`/`egress`/`macos` zielone. Poprawka w osobnym PR: `extendedWaitUntil` 60 s zamiast domyślnego oczekiwania.
- Koszt: każdy run `release.yml` to ok. 6 min `macos-26` + ~6 min ubuntu (repozytorium prywatne, D-9).
- Otwarte po stronie właściciela (bez zmian, blokują STEP-08/G-3): przegląd `THIRD-PARTY-NOTICES.md`/suplementu, ocena Google
  Play services i JUnit (EPL-1.0) w APK sterownika (L-05), akceptacja polityki `scripts/fork/allowed-licenses.tsv`,
  upublicznienie repozytorium (D-9).

## 4. Warunek rozpoczęcia następnego kroku

PR-07 **scalony** do `main`; `ci.yml` zielone na `main`; `release.yml` uruchomiony na `main` przez `workflow_dispatch`
(tryb walidacji) — zielony, `publish` `skipped`, bez Release. Decyzje właściciela z §3 (L-05, polityka licencji, przegląd not)
— przed tagiem `v0.1.0` (G-3).

**Stan po scaleniu (sprawdzony na początku STEP-08, 2026-10-24):** PR #15 i #16 MERGED. `ci.yml` na `main` (`aeaebc5`,
[run 40112502799](https://github.com/rtnk/TapCue/actions/runs/40112502799)) zielony: `build`, `egress`, `android` (żaden krok
nie pominięty, emulator API 34, 8/8 scenariuszy, `settings` PASS w 116 s), `macos`. `release.yml` bez wyzwalacza
`pull_request`. Pierwszy `workflow_dispatch` na `main` ([run 40115219200](https://github.com/rtnk/TapCue/actions/runs/40115219200)):
patrz `docs/handovers/STEP-08.md` §2. Decyzje właściciela (G-3) podjęte 2026-10-24 — `docs/LICENSE-AUDIT.md`.

## 5. Następny krok

**STEP-08: Release candidate i v0.1.0 (PR-08 + operacja release).** Zakres w `docs/ROADMAP-0.1.md` §3 „STEP-08”.

## 6. PROMPT DLA NASTĘPNEGO AGENTA (STEP-08)

```text
Jesteś agentem kodującym w repozytorium rtnk/TapCue (fork mobile-dev-inc/Maestro → „TapCue”, polecenie `tapcue`).
Nie masz pamięci poprzednich sesji. Zanim cokolwiek zmienisz:

1. git fetch origin && git checkout main && git pull origin main. Potwierdź przez GitHub, że PR-07
   („STEP-07: Release pipeline”, #15) jest MERGED, `ci.yml` na `main` zielony (joby `build`, `egress`, `android`, `macos`;
   w `android` kroki nie pominięte z braku KVM) i że `release.yml` nie ma wyzwalacza `pull_request`. Uruchom
   `release.yml` przez `workflow_dispatch` na `main` (tryb walidacji) i potwierdź: wszystkie joby zielone, `publish` skipped,
   `reproduce` IDENTICAL, brak Release i tagów `v*`. Jeśli cokolwiek z tego nie zachodzi — zatrzymaj się i zgłoś to.
2. Przeczytaj: AGENTS.md (nota forka), docs/ROADMAP-0.1.md (§1, §2, STEP-08, gate'y G-1…G-8, AT-01…AT-11),
   docs/handovers/STEP-07.md, docs/RELEASING.md (całość — procedura i procedury awaryjne), docs/NETWORK-AUDIT.md,
   docs/LICENSE-AUDIT.md (§3–§5, L-05), docs/ARCHITECTURE-LOCAL.md §5, CHANGELOG.md (część TapCue), README.md,
   maestro-cli/src/dist/README.md, .github/workflows/release.yml, scripts/fork/release-notes.py.
   Zapytaj właściciela o stan decyzji otwartych (nie rozstrzygaj sam): przegląd THIRD-PARTY-NOTICES.md/suplementu, ocena
   Google Play services i JUnit (EPL-1.0) w APK sterownika (L-05), polityka scripts/fork/allowed-licenses.tsv,
   upublicznienie repozytorium (D-9). Bez akceptacji G-3 nie twórz tagu.
3. Wykonaj WYŁĄCZNIE STEP-08 na gałęzi step-08-release-0.1.0:
   - PR-08: `CLI_VERSION=0.1.0` (maestro-cli/gradle.properties) i wszystko, co od niej zależy (testy z wersją, README);
     CHANGELOG.md — w części TapCue sekcja `### 0.1.0 — <data>` (zamiast `### Unreleased`) z podsekcją
     `#### Znane ograniczenia` (braki pokrycia, nieprzetestowane platformy: Windows, Linux jako platforma docelowa, macOS x64,
     fizyczne urządzenia iOS, web — G-8); README repozytorium (TapCue, instalacja z Release, uruchomienie CLI i MCP,
     ograniczenia, nota „based on Maestro (Apache-2.0), not affiliated with mobile.dev” — D-1); raport licencyjny
     (LICENSE-AUDIT zamknięty, jeśli właściciel zaakceptował); docs/RELEASING.md (tylko korekty po faktach).
   - Na gałęzi PR: `gh workflow run release.yml --ref step-08-release-0.1.0` (tryb walidacji) — zielony, bez ostrzeżeń
     `prepare` o sekcji CHANGELOG; notatki w podsumowaniu joba `build` zawierają sekcję 0.1.0 i ograniczenia.
   - Nie twórz tagu przed scaleniem PR-08 i zielonym `ci.yml` na commicie scalenia. Tag `v0.1.0` (adnotowany, na commicie
     scalenia PR-08 na `main`) tworzy właściciel albo agent wyłącznie na jego wyraźne polecenie (docs/RELEASING.md §4.2).
   - Po publikacji: weryfikacja Release (§4.3): dwa zasoby, notatki, pobranie na macOS arm64 (job lub Mac właściciela),
     `shasum -a 256 -c SHA256SUMS`, instalacja z pobranego pliku, AT-01…AT-11, offline (G-5 macOS; `pf` — jeśli możliwe).
     Wyniki w docs/handovers/STEP-08.md.
   - Nie zmieniaj nazw pakietów `maestro.*`, modułów Gradle, formatu flow, katalogów `~/.tapcue`/`.tapcue/`, `Vendor/`,
     release.yml (poza poprawką błędu wykrytego przy pierwszej publikacji — osobny commit z uzasadnieniem).
4. Gradle uruchamiaj BEZ flag -x. Jeśli środowisko blokuje dl.google.com, użyj wariantu analitycznego z
   docs/handovers/STEP-00.md §3 (bez commitowania; mirror Maven Central jak w STEP-01 §2; `egress-test.sh` wymaga `ip`
   z iproute2) i polegaj na CI.
5. Testy: `./gradlew test` (STEP-07: 1324 testy), `scripts/fork/test-release.sh`, `scripts/verify-dist.sh --smoke`,
   `scripts/fork/test-verify-dist.sh`, `scripts/fork/test-ci-checks.sh`, `scripts/fork/check-no-simulator-server.sh`,
   `scripts/fork/egress-test.sh self-test` i `cli`; `release.yml` w trybie walidacji na gałęzi PR-08; CI `ci.yml` zielone
   na PR. Po tagu: run `release.yml` w trybie publish — link, wynik każdego joba, SHA-256 zasobu = SHA-256 z `reproduce`.
   Raportuj tylko faktycznie uruchomione testy; reszta NOT RUN z powodem.
6. Zaktualizuj docs/ROADMAP-0.1.md (STEP-07 → MERGED (#15); STEP-08 — realizacja, gate'y G-1…G-8 ze statusem),
   docs/handovers/STEP-07.md (status „scalony”, §4), docs/RELEASING.md, docs/NETWORK-AUDIT.md / docs/LICENSE-AUDIT.md,
   docs/ARCHITECTURE-LOCAL.md §5, CHANGELOG.md, AGENTS.md (nota forka), dodaj docs/handovers/STEP-08.md wg szablonu STEP-07.md.
7. Utwórz PR „STEP-08: Release candidate 0.1.0” do main (cel, zakres, pliki, wyniki testów z komendami — tylko faktycznie
   uruchomione, ryzyka, link do handover, checklista z gate'ami G-1…G-8). Bez auto-merge. Doprowadź CI PR do zielonego.
8. Zakończ odpowiedzią: krok, URL PR, link do runu `release.yml` (walidacja; po tagu — publish i URL Release), walidacja
   PASS/FAIL/NOT RUN, stan gate'ów, ryzyka, co zostało do zrobienia przez właściciela.
```
