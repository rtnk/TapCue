package maestro.cli.driver

import maestro.MaestroException
import maestro.utils.TapCueDirs
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.pathString

class DriverBuilder(private val processBuilderFactory: XcodeBuildProcessBuilderFactory = XcodeBuildProcessBuilderFactory()) {
    private val waitTimeOverride: Long? by lazy {
        System.getenv("MAESTRO_XCODEBUILD_WAIT_TIME")?.toLongOrNull()
    }

    /**
     * Builds the iOS driver for real iOS devices or for the iOS Simulator (decision D-5: TapCue ships no prebuilt
     * driver) by extracting the driver source, copying it to a temporary build
     * directory, and executing the Xcode build process. The resulting build products are placed in the specified
     * derived data path.
     *
     * @param config A configuration object containing details like team ID, derived data path, destination platform,
     *               architectures, and other parameters required for building the driver.
     * @return The path to the directory containing build products.
     * @throws RuntimeException if the build process fails.
     *
     * Directory Structure:
     *   1. workingDirectory (Path): Root working directory for TapCue stored in the user's home directory.
     *      .tapcue
     *      |_ maestro-iphoneos-driver-build ([DriverBuildConfig.buildDirectoryName]; the simulator uses
     *         |                             maestro-iphonesimulator-driver-build)
     *         |_ driver-iphoneos: Consists the build products to setup iOS driver: maestro-driver-*.xctestrun,
     *            Debug-iphoneos/maestro-driver-iosUITests-Runner.app, and Debug-iphoneos/maestro-driver-ios.app
     *         |_ output.log: In case of errors output.log would be there to help debug
     *         |_ version.properties: CLI version (and driver source fingerprint) the products were built from
     *
     *   2. xcodebuildOutput (Path): A temporary directory created to store the output logs of the xcodebuild process and source code.
     *      It exists only for the duration of the build operation.
     *      e.g., $TMPDIR/maestro-xcodebuild-outputXXXXXX
     */
    fun buildDriver(config: DriverBuildConfig): Path {
        // Get driver source from resources
        val driverSourcePath = getDriverSourceFromResources(config)

        // Create temporary build directory
        val workingDirectory = TapCueDirs.home(config.sourceCodeRoot)
        val buildDir = Files.createDirectories(workingDirectory.resolve(config.buildDirectoryName)).apply {
            // Cleanup directory before we execute the build
            toFile().deleteRecursively()
        }
        val xcodebuildOutput = Files.createTempDirectory("maestro-xcodebuild-output")
        val outputFile = File(xcodebuildOutput.pathString + "/output.log")
        var built = false

        try {
            // Copy driver source to build directory
            Files.walk(driverSourcePath).use { paths ->
                paths.filter { Files.isRegularFile(it) }.forEach { path ->
                    val targetPath = xcodebuildOutput.resolve(driverSourcePath.relativize(path).toString())
                    Files.createDirectories(targetPath.parent)
                    Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING)
                }
            }

            // Create derived data path
            val derivedDataPath = buildDir.resolve(config.derivedDataPath)
            Files.createDirectories(derivedDataPath)

            val process = try {
                processBuilderFactory.createProcess(
                    commands = xcodebuildCommand(config, xcodebuildOutput, derivedDataPath),
                    workingDirectory = workingDirectory.toFile(),
                    outputFile = outputFile,
                )
            } catch (e: IOException) {
                throw MaestroException.IOSDeviceDriverSetupException(
                    "Failed to build iOS driver for ${config.targetDescription}: xcodebuild could not be started. " +
                        "Install Xcode and select it with `sudo xcode-select -s /Applications/Xcode.app`.",
                    e,
                )
            }

            val waitTime = waitTimeOverride ?: config.timeoutSeconds
            val finished = process.waitFor(waitTime, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
            }

            if (!finished || process.exitValue() != 0) {
                // copy the error log inside driver output
                val targetErrorFile = File(buildDir.toFile(), outputFile.name)
                outputFile.copyTo(targetErrorFile, overwrite = true)
                val reason = if (finished) "" else " (xcodebuild did not finish within $waitTime s; set MAESTRO_XCODEBUILD_WAIT_TIME to wait longer)"
                throw MaestroException.IOSDeviceDriverSetupException(
                    """
                        
                        Failed to build iOS driver for ${config.targetDescription}$reason.
                        
                        Error details:
                        - Build log: ${targetErrorFile.path}
                    """.trimIndent()
                )
            }

            built = true
            // Return path to build products
            return derivedDataPath.resolve("Build/Products")
        } finally {
            File(buildDir.toFile(), "version.properties").writer().use {
                val p = Properties()
                p["version"] = config.cliVersion.toString()
                // Only a finished build may claim to match the source, so a failed one is retried next time.
                config.sourceFingerprint?.takeIf { built }?.let { p[SOURCE_FINGERPRINT_PROPERTY] = it }
                p.store(it, null)
            }
            xcodebuildOutput.toFile().deleteRecursively()
        }
    }

    private fun xcodebuildCommand(config: DriverBuildConfig, project: Path, derivedDataPath: Path): List<String> {
        val signing = config.teamId?.let { teamId ->
            listOf("-allowProvisioningUpdates", "DEVELOPMENT_TEAM=$teamId", "CODE_SIGN_IDENTITY=Apple Development")
        } ?: emptyList()

        return listOf(
            "xcodebuild",
            "clean",
            "build-for-testing",
            "-project", "${project.pathString}/maestro-driver-ios.xcodeproj",
            "-scheme", "maestro-driver-ios",
            "-destination", config.destination,
            "-derivedDataPath", derivedDataPath.toString(),
            "ARCHS=${config.architectures}",
        ) + signing
    }

    /** SHA-256 over the paths and contents of the driver source bundled in the CLI JAR. */
    fun sourceFingerprint(config: DriverBuildConfig): String {
        val root = getDriverSourceFromResources(config)
        val digest = MessageDigest.getInstance("SHA-256")
        Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) }
                .map { root.relativize(it).toString() to it }
                .sorted(compareBy { it.first })
                .forEach { (relative, path) ->
                    digest.update(relative.toByteArray())
                    digest.update(0)
                    digest.update(Files.readAllBytes(path))
                }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun getDriverSourceFromResources(config: DriverBuildConfig): Path {
        val resourcePath = config.sourceCodePath
        val resourceUrl = DriverBuilder::class.java.classLoader.getResource(resourcePath)
            ?: throw IllegalArgumentException("Resource not found: $resourcePath")
        val uri = resourceUrl.toURI()

        val path = if (uri.scheme == "jar") {
            val fs = try {
                FileSystems.getFileSystem(uri)
            } catch (e: FileSystemNotFoundException) {
                FileSystems.newFileSystem(uri, emptyMap<String, Any>())
            }
            fs.getPath("/$resourcePath")
        } else {
            Paths.get(uri)
        }
        return path
    }

    companion object {
        const val REAL_DEVICE_BUILD_DIRECTORY = "maestro-iphoneos-driver-build"
        const val SIMULATOR_BUILD_DIRECTORY = "maestro-iphonesimulator-driver-build"
        const val SOURCE_FINGERPRINT_PROPERTY = "sourceFingerprint"
    }
}
