# Wydawanie TapCue (repozytorium `rtnk/TapCue`)

Status: **SZKIC (STEP-00), paczka i jej weryfikacja — STEP-05, CI — STEP-06**. Pipeline zostanie zaimplementowany w STEP-07
(`release.yml`) i zweryfikowany w STEP-08. Do tego czasu **nie tworzymy tagów `v*` ani GitHub Release**.

Plik `RELEASING.md` w katalogu głównym oraz skill `.claude/skills/release/` opisują proces **upstream**
(mobile.dev: Maestro Cloud, Maven Central, Homebrew) i **nie dotyczą** tego forka. Od STEP-05 build forka nie ma już
`jreleaser`, `scripts/install.sh` ani publikacji Maven Central — jedynym artefaktem wydania jest archiwum poniżej.

## Paczka (od STEP-05)

| Element | Wartość |
|---|---|
| Artefakt | `tapcue-<wersja>-macos-arm64.zip` + `SHA256SUMS` (`maestro-cli/build/distributions/`) |
| Komenda | `./gradlew :maestro-cli:macosArm64DistZip` (JDK 17; `SHA256SUMS` powstaje w tym samym przebiegu) |
| Zawartość | `tapcue-<wersja>-macos-arm64/`: `bin/tapcue`, `bin/jvm-version.jar`, `lib/*.jar`, `LICENSE`, `NOTICE`, `THIRD-PARTY-NOTICES.md`, `README.md` |
| Wymagania użytkownika | systemowa Java 17+ (D-7, JRE nie jest dołączane); Xcode dla iOS Simulator (sterownik budowany przy pierwszym użyciu, D-5); `adb` dla Androida |
| Wersja | `CLI_VERSION` w `maestro-cli/gradle.properties` (obecnie `2.11.0` z upstream; `0.1.0` ustawia PR-08) |
| Weryfikacja | `scripts/verify-dist.sh --smoke <archiwum>` (zawartość, zakazane artefakty, biblioteki natywne, noty, start-scripty, `SHA256SUMS`, `bin/tapcue --version`); testy negatywne `scripts/fork/test-verify-dist.sh <archiwum>` |
| CI (od STEP-06) | `.github/workflows/ci.yml` na każdym PR i pushu `main`: testy, archiwum + `verify-dist.sh`, zakazane zależności, skan licencji (`scripts/fork/check-licenses.py`), test offline (`scripts/fork/egress-test.sh`), emulator Android, macOS arm64 (paczka bez sieci, sterownik iOS ze źródła, symulator). Artefakt `tapcue-macos-arm64` (archiwum + `SHA256SUMS`) z joba `build` — **nie jest wydaniem** (wersja upstream, brak tagu) |
| Licencje | `THIRD-PARTY-NOTICES.md` generuje `:maestro-cli:generateThirdPartyNotices`; ręczna część: `docs/audit/notices-supplement.md`; nowa zależność bez licencji w POM kończy build błędem (wpis w `thirdPartyLicenseOverrides` po sprawdzeniu) |

Archiwum ma stałe znaczniki czasu i kolejność plików (`isPreserveFileTimestamps = false`, `isReproducibleFileOrder = true`),
tak samo jak JAR-y wszystkich modułów (`allprojects` w `build.gradle.kts`), a `version.properties` nie zawiera daty — ten sam
commit i te same zależności dają te same bajty (sprawdzone w STEP-05: identyczny SHA-256 po `--rerun-tasks`). Identyczność
między maszynami (inne JDK, inny system) nie była jeszcze sprawdzana — STEP-07.

Instalacja i uruchomienie z archiwum — `maestro-cli/src/dist/README.md` (w paczce jako `README.md`).

## Zasady (obowiązują od teraz)

1. Wydanie powstaje wyłącznie z tagu `vMAJOR.MINOR.PATCH` na commicie z `main`, po scaleniu PR
   przygotowującego wydanie (dla 0.1.0: PR-08).
2. Tagów i Release **nie nadpisujemy**. Błąd w wydaniu → nowa wersja patch (np. `v0.1.1`), a wadliwe
   wydanie oznaczamy w notach; usunięcie Release tylko świadomą decyzją właściciela.
3. Do forka **nie wypychamy tagów upstream** (`v2.*`): commit z tagiem upstream zawiera workflowy upstream
   w `.github/workflows/`, a tag `v*` uruchomiłby `publish-release.yaml`.
4. Publikację wykonuje tylko workflow `release.yml` z `GITHUB_TOKEN`; tylko job publikujący ma
   `contents: write`. Brak `pull_request_target`, brak PAT, jeśli nie jest niezbędny.
5. Przed publikacją muszą przejść gate'y G-1…G-8 z `docs/ROADMAP-0.1.md`.

## Planowany przebieg (do potwierdzenia w STEP-07)

1. `main` zielony, PR-08 scalony.
2. `git tag -a v0.1.0 <sha-main> -m "TapCue 0.1.0"` i `git push origin v0.1.0` (właściciel lub agent na polecenie).
3. `release.yml`: build na `macos-*` arm64 z dokładnie otagowanego commita → testy → `macosArm64DistZip`
   (z `SHA256SUMS`) → `verify-dist.sh --smoke` → skan licencji (`scripts/fork/check-licenses.py`) → test offline (job `macos`
   z rejestrem odmów sandboxa i/lub `egress-test.sh` na Linux) → upload artefaktu → job publish tworzy Release (jeśli nie
   istnieje). Wzorzec: joby `build`, `egress` i `macos` w `.github/workflows/ci.yml` (STEP-06).
4. Weryfikacja po publikacji: pobranie archiwum z Release, `shasum -a 256 -c SHA256SUMS`, instalacja,
   testy akceptacyjne AT-01…AT-11 (wyniki w `docs/handovers/STEP-08.md`).

## Odtworzenie builda

Zostanie uzupełnione w STEP-07 (dokładna wersja JDK, oczekiwane sumy kontrolne). Obecnie: JDK 17 (Zulu w CI),
`./gradlew :maestro-cli:macosArm64DistZip`, porównanie z `SHA256SUMS` z Release (`shasum -a 256 -c SHA256SUMS`).
