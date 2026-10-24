import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import util.IOSDeviceType
import xcuitest.installer.Context
import xcuitest.installer.IOSBuildProductsExtractor
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path

class IOSBuildProductsExtractorTest {

    @TempDir
    lateinit var products: Path

    @TempDir
    lateinit var target: Path

    @Test
    fun `simulator build products are taken from an absolute directory built on this machine`() {
        // given: what `xcodebuild build-for-testing` leaves in Build/Products (D-5: no prebuilt driver in the JAR)
        val xctestRun = write("maestro-driver-ios_iphonesimulator18.0-arm64.xctestrun", "xctestrun")
        write("Debug-iphonesimulator/maestro-driver-iosUITests-Runner.app/Info.plist", "runner")
        write("Debug-iphonesimulator/maestro-driver-ios.app/Info.plist", "host app")
        Files.setPosixFilePermissions(xctestRun, java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"))

        // when
        val buildProducts = IOSBuildProductsExtractor(target, Context.CLI, IOSDeviceType.SIMULATOR)
            .extract(products.toAbsolutePath().toString())

        // then
        assertThat(buildProducts.xctestRunPath.toPath()).isEqualTo(target.resolve(xctestRun.fileName.toString()))
        assertThat(buildProducts.uiRunnerPath.toPath())
            .isEqualTo(target.resolve("Debug-iphonesimulator/maestro-driver-iosUITests-Runner.app"))
        assertThat(Files.isRegularFile(target.resolve("Debug-iphonesimulator/maestro-driver-ios.app/Info.plist"))).isTrue()
        assertThat(Files.isExecutable(buildProducts.xctestRunPath.toPath())).isTrue()
    }

    @Test
    fun `the JAR carries no prebuilt simulator driver`() {
        // D-5: the prebuilt ZIPs contained Apple frameworks (LICENSE-AUDIT L-04) and were removed with their resources.
        val classLoader = IOSBuildProductsExtractor::class.java.classLoader
        listOf(
            "driver-iPhoneSimulator/maestro-driver-ios-config.xctestrun",
            "driver-iPhoneSimulator/Debug-iphonesimulator/maestro-driver-ios.zip",
            "driver-iPhoneSimulator/Debug-iphonesimulator/maestro-driver-iosUITests-Runner.zip",
            "driver-iphoneos/maestro-driver-ios-config.xctestrun",
            "driver-iphoneos/Debug-iphoneos/maestro-driver-ios.zip",
            "driver-iphoneos/Debug-iphoneos/maestro-driver-iosUITests-Runner.zip",
        ).forEach { assertThat(classLoader.getResource(it)).isNull() }
    }

    @Test
    fun `missing runner in the build products is reported`() {
        write("maestro-driver-ios_iphonesimulator18.0-arm64.xctestrun", "xctestrun")

        assertThrows<FileNotFoundException> {
            IOSBuildProductsExtractor(target, Context.CLI, IOSDeviceType.SIMULATOR)
                .extract(products.toAbsolutePath().toString())
        }
    }

    private fun write(relative: String, content: String): Path =
        products.resolve(relative).also {
            Files.createDirectories(it.parent)
            Files.writeString(it, content)
        }
}
