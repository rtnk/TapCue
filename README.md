<p align="center">
  <img src="docs/assets/tapcue-logo.png" alt="TapCue logo" width="160" />
</p>

# TapCue

**TapCue** is a local command-line tool for mobile UI tests and a local MCP server for coding agents. You write a test
as a flat list of YAML commands (`launchApp`, `tapOn`, `assertVisible`, …) and TapCue runs it on an Android emulator or
device or on an iOS Simulator on your machine.

TapCue is based on [Maestro](https://github.com/mobile-dev-inc/Maestro) (Apache-2.0), not affiliated with mobile.dev.
It is an independent fork of Maestro 2.11.0 that runs **only locally**: it has no cloud service, no remote AI, no
telemetry, no error upload and no update checks, and it ships no closed-source components. Flows use the Maestro flow
format unchanged.

## Install

TapCue 0.1.0 is published as a GitHub Release with two files: `tapcue-0.1.0-macos-arm64.zip` and `SHA256SUMS`.

Requirements:

- macOS on Apple silicon (arm64).
- **Java 17 or newer** on `PATH` or in `JAVA_HOME` (`java -version`). The archive does not include a Java runtime.
- Android: the Android SDK platform tools (`adb`) and an emulator or device.
- iOS Simulator: Xcode with its command-line tools. TapCue has no prebuilt iOS driver: on the first iOS run it builds
  the XCTest runner from the source it ships with your Xcode (a few minutes, no network needed) and keeps it in
  `~/.tapcue`. `tapcue driver-setup --simulator` builds it ahead of time.

```sh
gh release download v0.1.0 --repo rtnk/TapCue --dir tapcue-dl   # or download both files from the Release page
cd tapcue-dl
shasum -a 256 -c SHA256SUMS
unzip tapcue-0.1.0-macos-arm64.zip -d ~/opt
export PATH="$HOME/opt/tapcue-0.1.0-macos-arm64/bin:$PATH"
tapcue --version
```

The archive is not signed or notarized by Apple. If macOS blocks it as downloaded from the internet, clear the
quarantine flag once: `xattr -dr com.apple.quarantine ~/opt/tapcue-0.1.0-macos-arm64`.

To uninstall, delete the unpacked directory and, if you no longer need its state, `~/.tapcue`.

## Run tests

Keep a project's flows in a `.tapcue/` directory:

```yaml
# .tapcue/contacts.yaml
appId: com.android.contacts
---
- launchApp
- tapOn: "Create new contact"
- tapOn: "First Name"
- inputText: "John"
- tapOn: "Save"
- assertVisible: "John"
```

```sh
tapcue list-devices                 # local emulators, devices and simulators
tapcue test .tapcue/                # run every flow in the directory
tapcue test --device <id> .tapcue/contacts.yaml
tapcue check-syntax .tapcue/contacts.yaml
tapcue hierarchy                    # the UI hierarchy of the current screen
tapcue --help
```

Reports: `tapcue test --format junit --output report.xml` or `--format html`. State, logs and test artifacts live in
`~/.tapcue` (artifacts in `$XDG_STATE_HOME/tapcue` when that variable is set). TapCue does not read or write
`~/.maestro`, so it can be installed next to Maestro. A project's old `.maestro/` directory is not read: rename it
with `mv .maestro .tapcue`.

## MCP server

`tapcue mcp` is an MCP server on STDIO. It runs on your machine, talks only to local devices and makes no requests to
remote services. Tools: `list_devices`, `inspect_screen`, `take_screenshot`, `run` (runs flow commands or files) and
`cheat_sheet` (the flow syntax reference, bundled with TapCue).

Add it to an MCP client, e.g. Claude Code:

```sh
claude mcp add tapcue -- tapcue mcp
```

or in a client's JSON configuration:

```json
{ "mcpServers": { "tapcue": { "command": "tapcue", "args": ["mcp"] } } }
```

## Known limitations

TapCue 0.1.0 is the first release. Tested: the macOS arm64 archive (CLI, MCP and the iOS Simulator driver built from
source, on GitHub Actions `macos-26` runners, without network), Android on an API 34 emulator (CI), and the CLI and MCP
server without network on Linux (CI).

- **Untested platforms:** Windows, Linux as a platform to run TapCue on (Linux is used only in CI checks; there is no
  Linux archive), macOS on Intel (x64; there is no archive for it), physical iOS devices, physical Android devices.
- **Web is not supported:** flows for web browsers end with "The web platform is not supported".
- iOS Simulator coverage is small: CI builds the driver from source and reads the UI hierarchy of a simulator; flows on
  iOS are not run in CI. The first iOS run needs Xcode and takes a few minutes.
- Android coverage in CI is one emulator image (API 34) and the Settings app.
- Upstream's end-to-end suite (`e2e/`) is not run.
- JavaScript `http.*` in flows reaches only loopback, private and link-local addresses.
- Removed from Maestro: Maestro Cloud, Maestro Studio, AI commands (`assertWithAI`, `assertNoDefectsWithAI`,
  `extractTextWithAI`), Maestro Viewer, `download-samples`.
- The Android driver APKs are signed with the debug key that is public in the repository (as upstream).
- Java is not bundled (Java 17+ must be installed).

The full list is in the `0.1.0` section of [`CHANGELOG.md`](CHANGELOG.md) and in the Release notes.

## Build from source

```sh
./gradlew :maestro-cli:macosArm64DistZip    # JDK 17; writes maestro-cli/build/distributions/
scripts/verify-dist.sh --smoke maestro-cli/build/distributions/tapcue-0.1.0-macos-arm64.zip
./gradlew test
```

The release archive is reproducible: the same commit gives the same bytes ([`docs/RELEASING.md`](docs/RELEASING.md)).
Kotlin packages (`maestro.*`) and Gradle modules keep their upstream names.

## Documentation

- [`docs/RELEASING.md`](docs/RELEASING.md) — how releases are built, checked and verified
- [`docs/ROADMAP-0.1.md`](docs/ROADMAP-0.1.md), [`docs/ARCHITECTURE-LOCAL.md`](docs/ARCHITECTURE-LOCAL.md) — plan and architecture of the fork (Polish)
- [`docs/NETWORK-AUDIT.md`](docs/NETWORK-AUDIT.md), [`docs/LICENSE-AUDIT.md`](docs/LICENSE-AUDIT.md) — network and license audits (Polish)
- Issues: https://github.com/rtnk/TapCue/issues

## License

Apache License 2.0 — see [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE). TapCue is a modified version of Maestro by
mobile.dev inc. "Maestro" is a trademark of its owner; TapCue is not affiliated with or endorsed by mobile.dev. Licenses
of bundled libraries: `THIRD-PARTY-NOTICES.md` in the archive.
