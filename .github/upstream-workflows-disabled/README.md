# Upstream workflows (disabled)

These are the GitHub Actions workflows of upstream `mobile-dev-inc/Maestro` at `v2.11.0`, moved out of
`.github/workflows/` by the fork's bootstrap commit so that GitHub never runs them here. They publish to
upstream's Maven Central, Homebrew tap and GCS buckets, post release communications, use macOS runners,
and run `./gradlew` tasks that download simulator-server (licensed only for official Maestro releases,
see `docs/LICENSE-AUDIT.md` L-01).

They are kept only as reference while the fork's own CI is built (`docs/ROADMAP-0.1.md`, STEP-06) and
will be deleted then. Do not move them back.
