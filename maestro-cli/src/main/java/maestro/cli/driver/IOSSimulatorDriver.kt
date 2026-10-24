package maestro.cli.driver

import maestro.cli.util.EnvUtils
import maestro.cli.util.PrintUtils.message
import maestro.utils.TapCueDirs
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Properties

/**
 * Provides the XCTest runner for the iOS Simulator. TapCue does not ship prebuilt driver ZIPs (decision D-5 in
 * docs/ROADMAP-0.1.md: they contain Apple frameworks), so the runner is built from the source bundled in the CLI JAR
 * with the local Xcode on first use, and again whenever the CLI version or that source changes.
 *
 * Build products live in `~/.tapcue/maestro-iphonesimulator-driver-build/driver-iPhoneSimulator/Build/Products`.
 */
class IOSSimulatorDriver(
    private val driverBuilder: DriverBuilder = DriverBuilder(),
    private val userHome: String = System.getProperty("user.home"),
    private val hostArchitecture: String = System.getProperty("os.arch"),
) {

    /** Returns the build products directory, building the runner first if it is missing or stale. */
    fun buildProducts(force: Boolean = false): Path = synchronized(LOCK) {
        val buildDirectory = TapCueDirs.home(userHome).resolve(DriverBuilder.SIMULATOR_BUILD_DIRECTORY)
        Files.createDirectories(buildDirectory.parent)

        // Shards and parallel CLI processes may ask for the driver at the same time; only one builds it.
        FileChannel.open(
            buildDirectory.resolveSibling("${DriverBuilder.SIMULATOR_BUILD_DIRECTORY}.lock"),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        ).use { channel ->
            channel.lock().use {
                val config = buildConfig()
                val fingerprint = driverBuilder.sourceFingerprint(config)
                val products = buildDirectory.resolve(DERIVED_DATA_PATH).resolve("Build").resolve("Products")

                if (!force && isUpToDate(buildDirectory, products, fingerprint)) {
                    return products
                }

                message("Building the TapCue iOS driver for the simulator from source with Xcode. This happens on first use and after an update and can take a few minutes.")
                return driverBuilder.buildDriver(config.copy(sourceFingerprint = fingerprint))
            }
        }
    }

    private fun buildConfig() = DriverBuildConfig(
        teamId = null,
        derivedDataPath = DERIVED_DATA_PATH,
        sourceCodeRoot = userHome,
        destination = "generic/platform=iOS Simulator",
        architectures = simulatorArchitecture(hostArchitecture),
        cliVersion = EnvUtils.CLI_VERSION,
        buildDirectoryName = DriverBuilder.SIMULATOR_BUILD_DIRECTORY,
        targetDescription = "the iOS Simulator",
        timeoutSeconds = SIMULATOR_BUILD_TIMEOUT_SECONDS,
    )

    private fun isUpToDate(buildDirectory: Path, products: Path, fingerprint: String): Boolean {
        val versionFile = buildDirectory.resolve("version.properties")
        if (!Files.isRegularFile(versionFile)) return false

        val properties = Properties().apply { Files.newBufferedReader(versionFile).use(this::load) }
        if (properties.getProperty("version") != EnvUtils.CLI_VERSION.toString()) return false
        if (properties.getProperty(DriverBuilder.SOURCE_FINGERPRINT_PROPERTY) != fingerprint) return false

        val hasXctestRun = Files.isDirectory(products) && products.toFile().walk().any { it.extension == "xctestrun" }
        val hasRunner = Files.isDirectory(products.resolve("Debug-iphonesimulator").resolve(RUNNER_APP))
        return hasXctestRun && hasRunner
    }

    companion object {
        const val DERIVED_DATA_PATH = "driver-iPhoneSimulator"
        private const val RUNNER_APP = "maestro-driver-iosUITests-Runner.app"
        private const val SIMULATOR_BUILD_TIMEOUT_SECONDS = 900L
        private val LOCK = Any()

        /** Simulators run the host's architecture: arm64 on Apple silicon, x86_64 on Intel Macs. */
        fun simulatorArchitecture(osArch: String): String = when (osArch) {
            "aarch64", "arm64" -> "arm64"
            else -> "x86_64"
        }
    }
}
