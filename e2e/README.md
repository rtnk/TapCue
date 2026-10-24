# e2e

This directory contains glue code for testing Maestro itself.

## Testing

Typical workflow is:

1. Start Android emulator and iOS simulator
2. `download_apps`
3. `install_apps`
4. `run_tests`

We try to keep shell code in separate files, so we don't get too tightly coupled
to GitHub Actions.

### Expected failures

Let's say a critical bug is introduced that causes Maestro to always mark all
tests as passed. If our e2e test suite only was only checking if all tests pass
(i.e. `maestro test` exit code is 0), then wouldn't catch such a bug.

To prevent this, all flows in this directory MUST have a `passing` or `failing`
label, so the correct outcome can be asserted.

## Status in TapCue (STEP-06)

This suite is upstream's and is **not run by TapCue's CI** (`.github/workflows/ci.yml` runs its own emulator flows from
`scripts/fork/android-flows/` against a system app). It needs app binaries that are not in the repository: the Flutter
`demo_app` (build it from `demo_app/`) and the prebuilt apps listed in `manifest.txt`, which `download_apps` fetches from
mobile.dev's public storage bucket — run it yourself only if you accept that download. The web workspace and its fixture
server were removed with the web platform (decision D-4), and `update_samples` (uploading `download-samples` content to
mobile.dev's bucket) with the `download-samples` command.
