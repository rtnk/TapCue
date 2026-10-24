package maestro.cli.driver

import maestro.cli.util.CliVersion

data class DriverBuildConfig(
    /** Apple team that signs a real-device build; `null` for a simulator build, which needs no signing. */
    val teamId: String?,
    val derivedDataPath: String,
    val sourceCodePath: String = "driver/ios",
    val sourceCodeRoot: String = System.getProperty("user.home"),
    val destination: String = "generic/platform=iphoneos",
    val architectures: String = "arm64",
    val configuration: String = "Debug",
    val cliVersion: CliVersion?,
    /** Directory under `~/.tapcue` that receives the build products, `output.log` and `version.properties`. */
    val buildDirectoryName: String = DriverBuilder.REAL_DEVICE_BUILD_DIRECTORY,
    /** What the driver is built for, as shown in error messages. */
    val targetDescription: String = "connected iOS device",
    /** Default `xcodebuild` timeout; `MAESTRO_XCODEBUILD_WAIT_TIME` overrides it. */
    val timeoutSeconds: Long = 120,
    /** Written to `version.properties` so a later run can tell whether the bundled driver source changed. */
    val sourceFingerprint: String? = null,
)
