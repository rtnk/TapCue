# TapCue — installation

TapCue is a local command-line tool for mobile UI tests and a local MCP server. It is based on Maestro
(Apache-2.0), not affiliated with mobile.dev. TapCue does not contact Maestro Cloud, AI services, telemetry
or update servers.

## Requirements

- **Java 17 or newer (JDK or JRE)** on `PATH` or in `JAVA_HOME`. The package does **not** include a Java runtime;
  `bin/tapcue` stops with "Java 17 or higher is required" on older versions. Check with `java -version`.
- macOS on Apple silicon (arm64) for the `macos-arm64` package.
- Android: the Android SDK platform tools (`adb`) and an emulator or device.
- iOS Simulator: Xcode with its command-line tools (`xcode-select -p`). TapCue ships **no prebuilt iOS
  driver**: on the first iOS run it builds the XCTest runner from the source included in `lib/` with
  your Xcode (a few minutes) and keeps it in `~/.tapcue/maestro-iphonesimulator-driver-build`. Run
  `tapcue driver-setup --simulator` to build it ahead of time. The build needs no network: the runner's Swift
  dependency (FlyingFox, MIT) is included in the source.

## Install

```sh
shasum -a 256 -c SHA256SUMS            # in the directory holding the archive and SHA256SUMS
unzip tapcue-<version>-macos-arm64.zip -d ~/opt
export PATH="$HOME/opt/tapcue-<version>-macos-arm64/bin:$PATH"
tapcue --version
```

If macOS reports the archive as downloaded from the internet, clear the quarantine flag once:
`xattr -dr com.apple.quarantine ~/opt/tapcue-<version>-macos-arm64`.

## Use

```sh
tapcue test .tapcue/          # run the flows in a project's .tapcue/ directory
tapcue mcp                    # MCP server on STDIO for coding agents
tapcue --help
```

State and logs live in `~/.tapcue` (test artifacts in `$XDG_STATE_HOME/tapcue` when that variable is set);
TapCue does not use `~/.maestro`, so it can be installed next to Maestro.

## Contents

| Path | What |
|---|---|
| `bin/tapcue` | launcher (checks for Java 17+) |
| `bin/jvm-version.jar` | helper the launcher uses to read the Java version |
| `lib/*.jar` | TapCue and its libraries |
| `LICENSE` | Apache License 2.0 |
| `NOTICE` | attribution |
| `THIRD-PARTY-NOTICES.md` | licenses of all bundled libraries |

Uninstall: delete the unpacked directory and, if you no longer need its state, `~/.tapcue`.
