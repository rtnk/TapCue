# Wydawanie Maestro Local (repozytorium `rtnk/TapCue`)

Status: **SZKIC (STEP-00)**. Proces zostanie zaimplementowany w STEP-07 (`release.yml`) i zweryfikowany
w STEP-08. Do tego czasu **nie tworzymy tagów `v*` ani GitHub Release**.

Plik `RELEASING.md` w katalogu głównym oraz skill `.claude/skills/release/` opisują proces **upstream**
(mobile.dev: Maestro Cloud, Maven Central, Homebrew) i **nie dotyczą** tego forka.

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
2. `git tag -a v0.1.0 <sha-main> -m "Maestro Local 0.1.0"` i `git push origin v0.1.0` (właściciel lub agent na polecenie).
3. `release.yml`: build na `macos-*` arm64 z dokładnie otagowanego commita → testy → `verify-dist.sh`
   → skan licencji → `SHA256SUMS` → upload artefaktu → job publish tworzy Release (jeśli nie istnieje).
4. Weryfikacja po publikacji: pobranie archiwum z Release, `shasum -a 256 -c SHA256SUMS`, instalacja,
   testy akceptacyjne AT-01…AT-11 (wyniki w `docs/handovers/STEP-08.md`).

## Odtworzenie builda

Zostanie opisane w STEP-07 (wersja JDK, komenda Gradle, oczekiwane sumy kontrolne).
