package maestro.cli

import com.google.common.truth.Truth.assertThat
import maestro.MaestroException
import maestro.cli.command.StartDeviceCommand
import maestro.cli.device.DeviceCreateUtil
import maestro.device.Device
import maestro.device.DeviceService
import maestro.device.DeviceSpec
import maestro.device.Platform
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import picocli.CommandLine
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.nio.file.Paths
import java.util.zip.ZipFile

/**
 * STEP-03B, owner decision D-4: the web platform (Chrome driven by Selenium on the host) is not part of
 * Maestro Local 0.1.0. Every way into it ends in [MaestroException.WebPlatformNotSupported] before a
 * browser, Selenium Manager or the network is touched, and Selenium is not on the CLI classpath.
 */
class WebPlatformDisabledTest {

    private var originalProxySelector: ProxySelector? = null
    private val requestedUris = mutableListOf<URI>()

    @BeforeEach
    fun setUp() {
        // `test` and `record` install TestDebugReporter, which keeps its log directory for the rest of the
        // JVM; user.home is therefore left alone here (a temporary home would break later tests).
        originalProxySelector = ProxySelector.getDefault()
        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI): List<Proxy> {
                synchronized(requestedUris) { requestedUris.add(uri) }
                throw IOException("Network access is not allowed in this test: $uri")
            }

            override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) = Unit
        })
    }

    @AfterEach
    fun tearDown() {
        ProxySelector.setDefault(originalProxySelector)
    }

    @Test
    fun `test with a web flow fails with a clear error`() {
        val (exitCode, err) = execute("test", resource("workspaces/test_command_test/01_web_only/webflow.yaml"))

        assertThat(exitCode).isEqualTo(1)
        assertThat(err).contains("The web platform is not supported in Maestro Local 0.1.0")
        assertThat(err).doesNotContain("The stack trace was:")
        assertThat(requestedUris).isEmpty()
    }

    @Test
    fun `test with a workspace that mixes web and mobile flows fails as a whole`() {
        val (exitCode, err) = execute("test", resource("workspaces/test_command_test/00_mixed_web_mobile_flow_tests"))

        assertThat(exitCode).isEqualTo(1)
        assertThat(err).contains("The web platform is not supported in Maestro Local 0.1.0")
        assertThat(requestedUris).isEmpty()
    }

    @Test
    fun `record with a web flow fails with a clear error`() {
        val (exitCode, err) = execute("record", resource("workspaces/test_command_test/01_web_only/webflow.yaml"))

        assertThat(exitCode).isEqualTo(1)
        assertThat(err).contains("The web platform is not supported in Maestro Local 0.1.0")
        assertThat(requestedUris).isEmpty()
    }

    @Test
    fun `start-device and device creation refuse the web platform`() {
        val command = StartDeviceCommand()
        CommandLine(command).parseArgs("--platform", "web")
        assertThrows<MaestroException.WebPlatformNotSupported> { command.buildDeviceSpec(Platform.WEB) }
        assertThrows<MaestroException.WebPlatformNotSupported> {
            DeviceCreateUtil.getOrCreateDevice(DeviceSpec.Web.DEFAULT)
        }
    }

    @Test
    fun `no chromium browser is launched`() {
        assertThrows<MaestroException.WebPlatformNotSupported> {
            DeviceService.startDevice(
                device = Device.AvailableForLaunch(
                    modelId = "chromium",
                    description = "Chromium Web Browser",
                    platform = Platform.WEB,
                    deviceType = Device.DeviceType.BROWSER,
                    deviceSpec = DeviceSpec.Web.DEFAULT,
                ),
                driverHostPort = null,
            )
        }
    }

    @Test
    fun `web driver and Selenium classes are not on the classpath`() {
        listOf(
            "maestro.drivers.CdpWebDriver",
            "maestro.drivers.WebDriver",
            "maestro.web.cdp.CdpClient",
            "maestro.web.selenium.ChromeSeleniumFactory",
            "maestro.web.record.WebScreenRecorder",
            "maestro.cli.web.WebInteractor",
            "org.openqa.selenium.WebDriver",
            "org.openqa.selenium.chrome.ChromeDriverService",
            "org.openqa.selenium.manager.SeleniumManager",
        ).forEach { className ->
            assertThrows<ClassNotFoundException>(className) { Class.forName(className) }
        }
    }

    @Test
    fun `Selenium, Selenium Manager and truffle-enterprise are not on the runtime classpath`() {
        // The test runtime classpath is a superset of the CLI's runtimeClasspath.
        val entries = System.getProperty("java.class.path").split(File.pathSeparator)
        assertThat(entries.filter { it.contains("maestro-client") }).isNotEmpty()
        assertThat(entries.filter { it.contains("org.graalvm.js") || it.contains("js-language") }).isNotEmpty()
        assertThat(entries.filter { it.contains("selenium") || it.contains("maestro-web") }).isEmpty()
        assertThat(entries.filter { it.contains("truffle-enterprise") }).isEmpty()

        val offendingJars = entries
            .filter { it.endsWith(".jar") && File(it).isFile }
            .filter { jar ->
                ZipFile(jar).use { zip ->
                    zip.entries().asSequence().any {
                        it.name.startsWith("org/openqa/") ||
                            it.name.startsWith("maestro/web/") ||
                            it.name.startsWith("com/oracle/truffle/enterprise/") ||
                            it.name.contains("selenium-manager")
                    }
                }
            }
        assertThat(offendingJars).isEmpty()
    }

    private fun execute(vararg args: String): Pair<Int, String> {
        val commandLine = createCommandLine()
        val err = StringWriter()
        commandLine.err = PrintWriter(err, true)
        commandLine.out = PrintWriter(StringWriter(), true)
        return commandLine.execute(*args) to err.toString()
    }

    private fun resource(path: String): String =
        Paths.get(requireNotNull(javaClass.classLoader.getResource(path)) { path }.toURI()).toString()
}
