package maestro.cli.driver

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.*
import io.mockk.spyk
import maestro.cli.util.CliVersion
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.readText

class DriverBuilderTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `test if driver is built successfully and written in directory`() {
        // given
        val mockProcess = mockk<Process>(relaxed = true)
        val mockProcessBuilderFactory = mockk<XcodeBuildProcessBuilderFactory>()
        val sourceCodeRoot = System.getenv("GITHUB_WORKSPACE") ?: System.getProperty("user.home")
        every { mockProcess.waitFor(120, TimeUnit.SECONDS) } returns true // Simulate successful execution
        every { mockProcess.exitValue() } returns 0
        every { mockProcessBuilderFactory.createProcess(any(), any(), any()) }  answers {
            val derivedDataPath = Files.createDirectories(
                Paths.get(sourceCodeRoot , ".tapcue", "maestro-iphoneos-driver-build", "driver-iphoneos", "Build", "Products")
            )
            val debugIphoneDir = Files.createDirectories(Paths.get(derivedDataPath.pathString, "Debug-iphoneos"))
            // Simulate creating build products
            File(derivedDataPath.toFile(), "maestro-driver-ios-config.xctestrun").writeText("Fake Runner xctestrun file")
            File(debugIphoneDir.toFile(), "maestro-driver-iosUITests-Runner.app").writeText("Fake Runner App Content")
            File(debugIphoneDir.toFile(), "maestro-driver-ios.app").writeText("Fake iOS Driver App Content")
            println("Simulated build process: Build products created in derived data path.")

            mockProcess // Return the mocked process
        }

        // when
        val builder = DriverBuilder(mockProcessBuilderFactory)
        val buildProducts = builder.buildDriver(
            DriverBuildConfig(
                teamId = "25CQD4CKK3",
                derivedDataPath = "driver-iphoneos",
                sourceCodePath = "driver/ios",
                sourceCodeRoot = System.getenv("GITHUB_WORKSPACE") ?: System.getProperty("user.home"),
                cliVersion = CliVersion(1, 40, 0)
            )
        )
        val xctestRunFile = buildProducts.toFile().walk().firstOrNull { it.extension == "xctestrun" }
        val appDir = buildProducts.resolve("Debug-iphoneos/maestro-driver-ios.app")
        val runnerDir = buildProducts.resolve("Debug-iphoneos/maestro-driver-iosUITests-Runner.app")


        // then
        assertThat(xctestRunFile?.exists()).isTrue()
        assertThat(appDir.exists()).isTrue()
        assertThat(runnerDir.exists()).isTrue()

        Paths.get(System.getenv("GITHUB_WORKSPACE") ?: System.getProperty("user.home"), "maestro-iphoneos-driver-build").toFile().deleteRecursively()
    }

    @Test
    fun `should write error output to file inside _tapcue on build failure`() {
        // given
        val sourceCodeRoot = System.getenv("GITHUB_WORKSPACE") ?: System.getProperty("user.home")
        val driverBuildConfig = mockk<DriverBuildConfig>()
        val processBuilderFactory = mockk<XcodeBuildProcessBuilderFactory>()
        val driverBuilder = spyk(DriverBuilder(processBuilderFactory))
        val mockProcess = mockk<Process>(relaxed = true)
        val capturedFileSlot = slot<File>()

        every { driverBuildConfig.sourceCodePath } returns  "mock/source"
        every { driverBuildConfig.sourceCodeRoot } returns sourceCodeRoot
        every { driverBuildConfig.derivedDataPath } returns  "mock/source"
        every { driverBuildConfig.teamId } returns "mock-team-id"
        every { driverBuildConfig.architectures } returns "arm64"
        every { driverBuildConfig.destination } returns "generic/platform=ios"
        every { driverBuildConfig.cliVersion } returns CliVersion.parse("1.40.0")
        every { driverBuildConfig.buildDirectoryName } returns DriverBuilder.REAL_DEVICE_BUILD_DIRECTORY
        every { driverBuildConfig.targetDescription } returns "connected iOS device"
        every { driverBuildConfig.timeoutSeconds } returns 120
        every { driverBuildConfig.sourceFingerprint } returns null
        every { driverBuilder.getDriverSourceFromResources(any()) } returns tempDir
        every { mockProcess.exitValue() } returns 1
        every { mockProcess.waitFor(120, TimeUnit.SECONDS) } returns true
        every {
            processBuilderFactory.createProcess(commands = any(), workingDirectory = any(), outputFile = capture(capturedFileSlot))
        } answers {
            capturedFileSlot.captured.writeText("xcodebuild failed!")
            mockProcess
        }

        // when
        val error = assertThrows(RuntimeException::class.java) {
            driverBuilder.buildDriver(driverBuildConfig)
        }

        // then
        assertThat(error.message).contains("Failed to build iOS driver for connected iOS device")
        // Verify that the error log has been written inside the `.tapcue` directory
        val maestroDir = Paths.get(sourceCodeRoot, ".tapcue")
        val errorLog = maestroDir.resolve("maestro-iphoneos-driver-build").resolve("output.log")

        // Verify file exists and contains error output
        assertTrue(Files.exists(errorLog), "Expected an error log file to be written.")
        assertTrue(errorLog.readText().contains("xcodebuild failed!"), "Log should contain build failure message.")

        Paths.get(System.getenv("GITHUB_WORKSPACE") ?: System.getProperty("user.home"), "maestro-iphoneos-driver-build").toFile().deleteRecursively()
    }

    @Test
    fun `simulator build is unsigned and targets the generic simulator destination`() {
        // given
        val sourceCodeRoot = tempDir.toString()
        val processBuilderFactory = mockk<XcodeBuildProcessBuilderFactory>()
        val driverBuilder = spyk(DriverBuilder(processBuilderFactory))
        val mockProcess = mockk<Process>(relaxed = true)
        val commands = slot<List<String>>()
        every { driverBuilder.getDriverSourceFromResources(any()) } returns Files.createDirectories(tempDir.resolve("source"))
        every { mockProcess.waitFor(900, TimeUnit.SECONDS) } returns true
        every { mockProcess.exitValue() } returns 0
        every { processBuilderFactory.createProcess(commands = capture(commands), workingDirectory = any(), outputFile = any()) } returns mockProcess

        // when
        val products = driverBuilder.buildDriver(
            DriverBuildConfig(
                teamId = null,
                derivedDataPath = "driver-iPhoneSimulator",
                sourceCodeRoot = sourceCodeRoot,
                destination = "generic/platform=iOS Simulator",
                cliVersion = CliVersion(2, 11, 0),
                buildDirectoryName = DriverBuilder.SIMULATOR_BUILD_DIRECTORY,
                targetDescription = "the iOS Simulator",
                timeoutSeconds = 900,
                sourceFingerprint = "abc123",
            )
        )

        // then
        assertThat(commands.captured).containsAtLeast("build-for-testing", "-destination", "generic/platform=iOS Simulator").inOrder()
        assertThat(commands.captured.filter { it.startsWith("DEVELOPMENT_TEAM") || it.startsWith("CODE_SIGN_IDENTITY") }).isEmpty()
        assertThat(commands.captured).doesNotContain("-allowProvisioningUpdates")
        val buildDir = Paths.get(sourceCodeRoot, ".tapcue", DriverBuilder.SIMULATOR_BUILD_DIRECTORY)
        assertThat(products).isEqualTo(buildDir.resolve("driver-iPhoneSimulator/Build/Products"))
        val versionProperties = buildDir.resolve("version.properties").readText()
        assertThat(versionProperties).contains("version=2.11.0")
        assertThat(versionProperties).contains("sourceFingerprint=abc123")
    }

    @Test
    fun `failed simulator build does not record the source fingerprint`() {
        val processBuilderFactory = mockk<XcodeBuildProcessBuilderFactory>()
        val driverBuilder = spyk(DriverBuilder(processBuilderFactory))
        val mockProcess = mockk<Process>(relaxed = true)
        every { driverBuilder.getDriverSourceFromResources(any()) } returns Files.createDirectories(tempDir.resolve("source"))
        every { mockProcess.waitFor(900, TimeUnit.SECONDS) } returns true
        every { mockProcess.exitValue() } returns 65
        val outputFile = slot<File>()
        every { processBuilderFactory.createProcess(any(), any(), capture(outputFile)) } answers {
            outputFile.captured.writeText("** BUILD FAILED **")
            mockProcess
        }

        assertThrows(maestro.MaestroException.IOSDeviceDriverSetupException::class.java) {
            driverBuilder.buildDriver(
                DriverBuildConfig(
                    teamId = null,
                    derivedDataPath = "driver-iPhoneSimulator",
                    sourceCodeRoot = tempDir.toString(),
                    cliVersion = CliVersion(2, 11, 0),
                    buildDirectoryName = DriverBuilder.SIMULATOR_BUILD_DIRECTORY,
                    targetDescription = "the iOS Simulator",
                    timeoutSeconds = 900,
                    sourceFingerprint = "abc123",
                )
            )
        }

        val versionProperties = Paths.get(tempDir.toString(), ".tapcue", DriverBuilder.SIMULATOR_BUILD_DIRECTORY, "version.properties").readText()
        assertThat(versionProperties).doesNotContain("sourceFingerprint")
    }

    @Test
    fun `missing xcodebuild is reported as a driver setup error`() {
        val processBuilderFactory = mockk<XcodeBuildProcessBuilderFactory>()
        val driverBuilder = spyk(DriverBuilder(processBuilderFactory))
        every { driverBuilder.getDriverSourceFromResources(any()) } returns Files.createDirectories(tempDir.resolve("source"))
        every { processBuilderFactory.createProcess(any(), any(), any()) } throws java.io.IOException("Cannot run program \"xcodebuild\"")

        val error = assertThrows(maestro.MaestroException.IOSDeviceDriverSetupException::class.java) {
            driverBuilder.buildDriver(
                DriverBuildConfig(
                    teamId = null,
                    derivedDataPath = "driver-iPhoneSimulator",
                    sourceCodeRoot = tempDir.toString(),
                    cliVersion = CliVersion(2, 11, 0),
                    buildDirectoryName = DriverBuilder.SIMULATOR_BUILD_DIRECTORY,
                    targetDescription = "the iOS Simulator",
                )
            )
        }

        assertThat(error.message).contains("Failed to build iOS driver for the iOS Simulator")
        assertThat(error.message).contains("Install Xcode")
    }

    @Test
    fun `source fingerprint covers the bundled driver source`() {
        val first = Files.createDirectories(tempDir.resolve("a"))
        Files.writeString(first.resolve("main.swift"), "print(1)")
        val driverBuilder = spyk(DriverBuilder(mockk()))
        val config = DriverBuildConfig(teamId = null, derivedDataPath = "d", cliVersion = null)
        every { driverBuilder.getDriverSourceFromResources(any()) } returns first

        val before = driverBuilder.sourceFingerprint(config)
        Files.writeString(first.resolve("main.swift"), "print(2)")
        val after = driverBuilder.sourceFingerprint(config)

        assertThat(before).hasLength(64)
        assertThat(after).isNotEqualTo(before)
    }

    @Test
    fun `bundled iOS driver source is complete`() {
        // D-5: the simulator runner is built from this source, so every target of the maestro-driver-ios scheme
        // (including the MaestroDriverLib framework it links) has to be bundled.
        val source = DriverBuilder().getDriverSourceFromResources(
            DriverBuildConfig(teamId = null, derivedDataPath = "d", cliVersion = null)
        )

        listOf(
            "maestro-driver-ios.xcodeproj/project.pbxproj",
            "maestro-driver-ios.xcodeproj/xcshareddata/xcschemes/maestro-driver-ios.xcscheme",
            "Vendor/FlyingFox/Package.swift",
            "maestro-driver-iosUITests",
            "maestro-driver-ios",
            "MaestroDriverLib/Sources/MaestroDriverLib/MaestroDriverLib.swift",
        ).forEach { assertTrue(Files.exists(source.resolve(it)), "missing driver/ios/$it") }
    }
}
