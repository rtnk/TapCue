# Vendored Swift packages

The iOS driver is built on the user's Mac from source (decision D-5 in `docs/ROADMAP-0.1.md`), so its Swift
dependencies are kept here instead of being fetched by Xcode: the build then works offline and TapCue never makes
Xcode download anything on its own (`docs/NETWORK-AUDIT.md` N-36).

| Package | Version | Source | License |
|---|---|---|---|
| `FlyingFox/` | 0.22.0 (`3ad076e081749cef043e25ac01719a503b772113`, the revision upstream pinned in `Package.resolved`) | https://github.com/swhitty/FlyingFox | MIT (`FlyingFox/LICENSE`); `CSystemLinux/` (Linux only, not built for iOS): Apache-2.0 with Runtime Library Exception, Apple Inc. and the Swift System project authors |

The files are unmodified (`git archive` of that revision without `.github/`, `.gitignore`, `codecov.yml` and
`docker-run-tests.sh`). To update: replace the directory with another tagged revision and update this table,
`docs/LICENSE-AUDIT.md` (L-06) and `docs/audit/notices-supplement.md`.
