# Wydawanie TapCue (repozytorium `rtnk/TapCue`)

Status: **pipeline gotowy (STEP-07, `.github/workflows/release.yml`)**; pierwsze wydanie `v0.1.0` przygotowane w PR-08 (STEP-08); tag po scaleniu PR-08.
Do scalenia PR-08 **nie tworzymy tagów `v*` ani GitHub Release** (tryb walidacji przez `workflow_dispatch` — bez ograniczeń).

Plik `RELEASING.md` w katalogu głównym oraz skille `.claude/skills/release/` i `bump-android-version` opisują procesy
**upstream** (mobile.dev: Maestro Cloud, Maven Central, Homebrew, GCS) i **nie dotyczą** tego forka. Build forka nie ma
`jreleaser`, `scripts/install.sh` ani publikacji Maven Central — jedynym artefaktem wydania jest archiwum poniżej.

## 1. Paczka

| Element | Wartość |
|---|---|
| Artefakt | `tapcue-<wersja>-macos-arm64.zip` + `SHA256SUMS` (`maestro-cli/build/distributions/`) — jedyne zasoby Release |
| Komenda | `./gradlew :maestro-cli:macosArm64DistZip` (JDK 17; `SHA256SUMS` powstaje w tym samym przebiegu) |
| Zawartość | `tapcue-<wersja>-macos-arm64/`: `bin/tapcue`, `bin/jvm-version.jar`, `lib/*.jar`, `LICENSE`, `NOTICE`, `THIRD-PARTY-NOTICES.md`, `README.md` |
| Wymagania użytkownika | systemowa Java 17+ (D-7, JRE nie jest dołączane); Xcode dla iOS Simulator (sterownik budowany przy pierwszym użyciu, D-5); `adb` dla Androida |
| Wersja | `CLI_VERSION` w `maestro-cli/gradle.properties` (`0.1.0` od PR-08; wcześniej `2.11.0` z upstream). Tag wydania = `v<CLI_VERSION>` |
| Weryfikacja | `scripts/verify-dist.sh --smoke <archiwum>`; testy negatywne `scripts/fork/test-verify-dist.sh <archiwum>` |
| Licencje | `THIRD-PARTY-NOTICES.md` z `:maestro-cli:generateThirdPartyNotices` (ręczna część: `docs/audit/notices-supplement.md`); skan `scripts/fork/check-licenses.py` vs `scripts/fork/allowed-licenses.tsv` |
| CI | `.github/workflows/ci.yml` na każdym PR i pushu `main`. Artefakt `tapcue-macos-arm64` z joba `build` **nie jest wydaniem** |

Instalacja i uruchomienie z archiwum — `maestro-cli/src/dist/README.md` (w paczce jako `README.md`).

## 2. Zasady

1. Wydanie powstaje wyłącznie z **adnotowanego** tagu `vMAJOR.MINOR.PATCH` równego `v<CLI_VERSION>`, na commicie z `main`,
   po scaleniu PR przygotowującego wydanie (dla 0.1.0: PR-08). Pipeline odrzuca tag lekki, inny format, niezgodną wersję
   i commit spoza `main`.
2. Tagów i Release **nie nadpisujemy**: pipeline nie tworzy, nie przesuwa i nie usuwa tagów ani Release, a job publish
   kończy się błędem, jeśli Release dla tagu już istnieje (także szkic). Błąd w wydaniu → nowa wersja patch (`v0.1.1`).
3. Do forka **nie wypychamy tagów upstream** (`v2.*`): commit z tagiem upstream zawiera workflowy upstream
   w `.github/workflows/`, a tag `v*` uruchomiłby tam `publish-release.yaml`. (Tag `v2.11.0` na commicie forka nie
   opublikowałby niczego: fork nie ma sekcji `### 2.11.0` w `CHANGELOG.md` — patrz §3.)
4. Publikuje wyłącznie job `publish` w `release.yml`, z `GITHUB_TOKEN`; tylko on ma `contents: write`, workflow ma
   `permissions: contents: read`. Brak `pull_request_target`, PAT i innych sekretów; akcje przypięte do SHA.
5. Przed tagiem `v0.1.0` muszą przejść gate'y G-1…G-8 z `docs/ROADMAP-0.1.md`.

## 3. Pipeline `.github/workflows/release.yml`

| Wyzwalacz | Tryb |
|---|---|
| `push` tagu `v[0-9]+.[0-9]+.[0-9]+` | **publish** — build, kontrole i Release z dokładnie otagowanego commita |
| `workflow_dispatch` (dowolna gałąź) | **validate** — te same joby; `publish` jest pomijany (`skipped`), Release nie powstaje |

`workflow_dispatch` działa dopiero, gdy `release.yml` jest na gałęzi domyślnej (przed scaleniem PR-07 API zwraca 404); w PR-07
tryb walidacji uruchamiał tymczasowy wyzwalacz `pull_request`, usunięty przed scaleniem (handover STEP-07). Od scalenia PR-07
`workflow_dispatch` działa na dowolnej gałęzi (w STEP-08: na `main` i na gałęzi PR-08).

| Job | Runner | Co robi |
|---|---|---|
| `prepare` | ubuntu | `scripts/fork/release-context.sh`: tryb (`publish` tylko dla wypchniętego tagu), wersja z `CLI_VERSION`, tag; w trybie publish: tag adnotowany, `vX.Y.Z`, wskazuje budowany commit, równy `v<CLI_VERSION>`, commit jest przodkiem `origin/main`. `publish-release.sh check` — brak Release dla tagu. `release-notes.py --require-section` — sekcja `### <wersja>` w części TapCue `CHANGELOG.md` z nagłówkiem „Known limitations”/„Znane ograniczenia” (G-8). Testy skryptów (`test-release.sh`). W trybie validate te fakty są tylko raportowane (ostrzeżenia) |
| `build` | `macos-26` arm64 | asercja `uname -m` = `arm64`; JDK Zulu `RELEASE_JDK` (`17.0.21`), bez cache Gradle i z `--no-build-cache`; fakty builda (`build-info.txt`: commit, runner, JDK, Gradle); `macosArm64DistZip`; nazwa archiwum = `tapcue-<CLI_VERSION>-macos-arm64.zip`, `shasum -c SHA256SUMS`; `verify-dist.sh --smoke`; skan licencji; zakazane GAV; `tapcue --version` z rozpakowanej paczki zawiera wersję; **paczka bez sieci** (`scripts/fork/macos-offline-test.sh` pod `sandbox-exec`, z rejestrem odmów — w release wymaganym: brak odmowy sondy `curl` w logu = porażka); potem detekt + `./gradlew test` i strażnik simulator-server na wszystkich wynikach builda (testy po teście offline — zostawiają demony Gradle/Kotlin i `adb`, po których log systemowy gubił odmowy sandboxa); notatki (`release-notes.py`); artefakty `tapcue-release` (archiwum + `SHA256SUMS`), `release-notes`, `release-build-reports` |
| `egress` | ubuntu | to samo archiwum (z artefaktu, `sha256sum -c`) w przestrzeni sieciowej z samym loopbackiem pod `strace`: `egress-test.sh self-test` i `cli` — zero prób poza loopback |
| `reproduce` | ubuntu | build archiwum z tego samego commita tym samym JDK na Linux; `compare-archives.py` + `cmp SHA256SUMS` — **bajty muszą być identyczne** (§5) |
| `publish` | ubuntu | tylko tryb publish i `github.event_name == push` z `ref_type == tag`, po `build`, `egress`, `reproduce`; `permissions: contents: write`; `scripts/fork/publish-release.sh publish`: ponowne sprawdzenie zdarzenia, tag na GitHub nadal wskazuje zbudowany commit, katalog zawiera tylko archiwum i pasujący `SHA256SUMS`, brak Release (także szkicu); `gh release create <tag> --verify-tag` z notatkami; pobranie zasobów z Release i porównanie bajtów |

Notatki wydania: sekcja `### <wersja>` z części TapCue `CHANGELOG.md` (między `## TapCue` a `## Upstream Maestro` — sekcje
upstream o tym samym numerze nie są używane) + nota pochodzenia (D-1) + SHA-256 archiwum + fakty builda.

## 4. Procedura wydania

### 4.1 Przygotowanie (PR przygotowujący wydanie, np. PR-08)

1. `CLI_VERSION` w `maestro-cli/gradle.properties` = nowa wersja (`0.1.0`).
2. `CHANGELOG.md`, część TapCue: sekcja `### <wersja> — <data>` (zamiast/obok `### Unreleased`) z podsekcją
   `#### Znane ograniczenia` (lub `#### Known limitations`): braki pokrycia testami, nieprzetestowane platformy (G-8).
3. Pozostałe dokumenty wydania (README, raport licencyjny — zakres STEP-08), gate'y G-1…G-8 sprawdzone.
4. Na gałęzi PR (po scaleniu PR-07 dostępne): **Actions → Release → Run workflow** na gałęzi PR albo
   `gh workflow run release.yml --ref <gałąź>` — tryb walidacji musi być zielony (ostrzeżenia `prepare` o brakującej sekcji
   lub istniejącym Release nie mogą wystąpić). `ci.yml` zielone na PR.
5. Scalenie PR do `main` (właściciel). Poczekać na zielony `ci.yml` na commicie scalenia na `main`.

### 4.2 Tag

```bash
git fetch origin main
git checkout --detach origin/main            # dokładnie commit scalenia, zielony w ci.yml
grep CLI_VERSION maestro-cli/gradle.properties   # = wersja tagu
git tag -a v0.1.0 -m "TapCue 0.1.0"
git push origin v0.1.0                         # właściciel lub agent na wyraźne polecenie
```

Wypchnięcie tagu uruchamia `release.yml` w trybie publish (workflow z otagowanego commita).

### 4.3 Weryfikacja Release

1. Run `Release` dla tagu zielony we wszystkich jobach; `publish` zakończony „OK: Release vX.Y.Z published … the downloaded
   assets are the built ones”.
2. Release ma dokładnie dwa zasoby (`tapcue-<wersja>-macos-arm64.zip`, `SHA256SUMS`), tytuł `TapCue <wersja>`, notatki
   z sekcji CHANGELOG, notą pochodzenia i SHA-256.
3. Na czystym Macu arm64 (STEP-08, AT-11/G-6):

   ```bash
   gh release download v0.1.0 --repo rtnk/TapCue --dir tapcue-dl && cd tapcue-dl
   shasum -a 256 -c SHA256SUMS
   unzip -q tapcue-0.1.0-macos-arm64.zip && ./tapcue-0.1.0-macos-arm64/bin/tapcue --version
   ```

   oraz testy akceptacyjne AT-01…AT-11 (wyniki w `docs/handovers/STEP-08.md`).

### 4.4 Procedury awaryjne

| Sytuacja | Postępowanie |
|---|---|
| Run `Release` czerwony **przed** `publish` (test, `verify-dist`, licencje, egress, `reproduce`) | Nic nie zostało opublikowane. Naprawić w nowym PR do `main`; tag zostaje (nie przesuwamy go) — wydać kolejną wersję patch (`v0.1.1`: `CLI_VERSION`, sekcja CHANGELOG, nowy tag). Wyjątek: błąd czysto infrastrukturalny runnera (checkout, instalacja, utrata runnera) → jednorazowe „Re-run failed jobs” tego samego runu |
| `prepare` odrzuca tag (lekki, zła wersja, spoza `main`) | Nic nie zostało zbudowane. Tag jest błędny: właściciel decyduje o jego usunięciu (`git push origin :refs/tags/vX.Y.Z` — tylko gdy nie powstał Release); poprawny tag na właściwym commicie |
| `publish` padł **po** utworzeniu Release (np. błąd uploadu lub weryfikacji pobranych zasobów) | Release może być niekompletny. Ponowne uruchomienie się **nie powiedzie** (Release istnieje — zamierzone). Właściciel: oznaczyć Release jako wadliwy (edycja notatek: „WITHDRAWN — use vX.Y.Z+1”) lub usunąć go świadomą decyzją, następnie wydać wersję patch |
| Wada w opublikowanym wydaniu (błąd funkcjonalny, licencja, bezpieczeństwo) | **Wycofanie:** właściciel edytuje Release — tytuł/notatki z „WITHDRAWN” i powodem, opcjonalnie „Set as a pre-release”, żeby nie był „Latest”; zasobów nie podmieniamy. Usunięcie Release (a tym bardziej tagu) tylko świadomą decyzją właściciela (np. problem licencyjny wymagający usunięcia pliku). Poprawka: PR do `main` + wersja patch |
| Archiwum z Release różni się od lokalnego builda | Sprawdzić JDK (§5) i commit; różnica przy tym samym JDK i commicie to błąd powtarzalności — zgłosić, wstrzymać kolejne wydania do wyjaśnienia (`compare-archives.py` wskazuje różniące się wpisy) |

## 5. Odtworzenie builda (powtarzalność)

Archiwum i JAR-y mają stałe znaczniki czasu i kolejność plików (`isPreserveFileTimestamps = false`,
`isReproducibleFileOrder = true` w `allprojects`), `version.properties` nie zawiera daty, a `THIRD-PARTY-NOTICES.md` powstaje
z tych samych POM-ów. Job `reproduce` w każdym runie `release.yml` buduje archiwum ponownie na Linux i wymaga identycznych bajtów
z buildem macOS. Wynik STEP-07 (`docs/handovers/STEP-07.md` §2): dla tego samego commita archiwum ma ten sam SHA-256 w `ci.yml`
(ubuntu x64, Zulu 17.0.20, z cache) i `release.yml` (macOS arm64 i ubuntu x64, Zulu 17.0.21, bez cache) oraz w buildzie lokalnym
(Ubuntu OpenJDK 17.0.20.1) — bajty nie zależą od systemu, architektury ani wersji poprawki JDK 17. `SHA256SUMS` jest
wypisywany w podsumowaniu joba `build` w `ci.yml`, więc wydanie można porównać z CI commita scalenia przed tagiem.

Odtworzenie wydania (dowolny system z JDK, sieć do Maven Central/Google Maven/Gradle Plugin Portal):

```bash
git clone https://github.com/rtnk/TapCue && cd TapCue
git checkout v0.1.0                                   # commit z notatek Release (pole commit)
# JDK: Zulu 17.0.21 (pole jdk w notatkach Release; RELEASE_JDK w release.yml)
export JAVA_HOME=/path/to/zulu-17.0.21
./gradlew --no-build-cache :maestro-cli:macosArm64DistZip
cd maestro-cli/build/distributions
shasum -a 256 -c /path/to/SHA256SUMS-from-the-Release   # oczekiwane: OK
# różnica: python3 -I scripts/fork/compare-archives.py <z Release> <lokalny>
```

Gradle pochodzi z wrappera (wersja i suma kontrolna w `gradle/wrapper/gradle-wrapper.properties`; `release.yml` sprawdza
wrapper akcją `wrapper-validation`), zależności — z wersji w `gradle/libs.versions.toml`.
