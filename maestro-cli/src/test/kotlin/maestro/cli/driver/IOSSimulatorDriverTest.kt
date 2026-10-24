package maestro.cli.driver

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import maestro.cli.util.EnvUtils
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

class IOSSimulatorDriverTest {

    @TempDir
    lateinit var home: Path

    private val driverBuilder = mockk<DriverBuilder>()
    private val configs = mutableListOf<DriverBuildConfig>()

    private val buildDirectory get() = home.resolve(".tapcue").resolve(DriverBuilder.SIMULATOR_BUILD_DIRECTORY)
    private val products get() = buildDirectory.resolve("driver-iPhoneSimulator/Build/Products")

    private fun simulatorDriver(fingerprint: String = "source-1"): IOSSimulatorDriver {
        every { driverBuilder.sourceFingerprint(any()) } returns fingerprint
        val config = slot<DriverBuildConfig>()
        every { driverBuilder.buildDriver(capture(config)) } answers {
            configs += config.captured
            writeBuild(config.captured.sourceFingerprint!!)
            products
        }
        return IOSSimulatorDriver(driverBuilder, userHome = home.toString(), hostArchitecture = "aarch64")
    }

    @Test
    fun `first use builds the runner from source for the simulator`() {
        val result = simulatorDriver().buildProducts()

        assertThat(result).isEqualTo(products)
        val config = configs.single()
        assertThat(config.teamId).isNull()
        assertThat(config.destination).isEqualTo("generic/platform=iOS Simulator")
        assertThat(config.architectures).isEqualTo("arm64")
        assertThat(config.buildDirectoryName).isEqualTo(DriverBuilder.SIMULATOR_BUILD_DIRECTORY)
        assertThat(config.derivedDataPath).isEqualTo("driver-iPhoneSimulator")
        assertThat(config.sourceCodeRoot).isEqualTo(home.toString())
        assertThat(config.sourceFingerprint).isEqualTo("source-1")
    }

    @Test
    fun `an up-to-date build is reused`() {
        simulatorDriver().buildProducts()
        simulatorDriver().buildProducts()

        verify(exactly = 1) { driverBuilder.buildDriver(any()) }
    }

    @Test
    fun `changed driver source triggers a rebuild`() {
        simulatorDriver("source-1").buildProducts()
        simulatorDriver("source-2").buildProducts()

        assertThat(configs.map { it.sourceFingerprint }).containsExactly("source-1", "source-2").inOrder()
    }

    @Test
    fun `build from another CLI version triggers a rebuild`() {
        simulatorDriver().buildProducts()
        val versionFile = buildDirectory.resolve("version.properties")
        Files.writeString(versionFile, "version=0.0.1\nsourceFingerprint=source-1\n")

        simulatorDriver().buildProducts()

        assertThat(configs).hasSize(2)
    }

    @Test
    fun `missing runner app triggers a rebuild`() {
        simulatorDriver().buildProducts()
        products.resolve("Debug-iphonesimulator").toFile().deleteRecursively()

        simulatorDriver().buildProducts()

        assertThat(configs).hasSize(2)
    }

    @Test
    fun `force rebuilds an up-to-date driver`() {
        simulatorDriver().buildProducts()
        simulatorDriver().buildProducts(force = true)

        assertThat(configs).hasSize(2)
    }

    @Test
    fun `simulator architecture follows the host`() {
        assertThat(IOSSimulatorDriver.simulatorArchitecture("aarch64")).isEqualTo("arm64")
        assertThat(IOSSimulatorDriver.simulatorArchitecture("x86_64")).isEqualTo("x86_64")
        assertThat(IOSSimulatorDriver.simulatorArchitecture("amd64")).isEqualTo("x86_64")
    }

    /** What DriverBuilder leaves behind after a successful `xcodebuild build-for-testing`. */
    private fun writeBuild(fingerprint: String) {
        buildDirectory.toFile().deleteRecursively()
        Files.createDirectories(products.resolve("Debug-iphonesimulator/maestro-driver-iosUITests-Runner.app"))
        Files.writeString(products.resolve("maestro-driver-ios_iphonesimulator18.0-arm64.xctestrun"), "xctestrun")
        Files.newBufferedWriter(buildDirectory.resolve("version.properties")).use {
            Properties().apply {
                this["version"] = EnvUtils.CLI_VERSION.toString()
                this[DriverBuilder.SOURCE_FINGERPRINT_PROPERTY] = fingerprint
            }.store(it, null)
        }
    }
}
