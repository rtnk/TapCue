package maestro.cli

import com.google.common.truth.Truth.assertThat
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import picocli.CommandLine
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.zip.ZipFile
import kotlin.io.path.exists

/**
 * STEP-03 (Remove telemetry, error upload and update checks): running the CLI, including a command
 * that fails, reports locally only. No analytics, error report, screen report, version check or
 * changelog download is attempted.
 */
@ExtendWith(SystemStubsExtension::class)
class NoTelemetryTest {

    @SystemStub
    private val environment = EnvironmentVariables()

    @TempDir
    lateinit var home: Path

    private val server = MockWebServer()
    private var originalHome: String? = null
    private var originalProxySelector: ProxySelector? = null
    private val requestedUris = mutableListOf<URI>()

    @BeforeEach
    fun setUp() {
        server.start()
        // The upstream clients took their base URL from MAESTRO_API_URL. Pointing it at a local
        // server makes any leftover client observable even if the proxy guard below is bypassed.
        environment.set("MAESTRO_API_URL", server.url("/").toString().removeSuffix("/"))
        environment.set("XDG_STATE_HOME", home.resolve("state").toString())

        originalHome = System.getProperty("user.home")
        System.setProperty("user.home", home.toString())

        // OkHttp, HttpURLConnection and the JDK HttpClient consult the default ProxySelector before
        // connecting to a non-loopback host, so any outbound attempt is recorded and then fails.
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
        System.setProperty("user.home", originalHome)
        ProxySelector.setDefault(originalProxySelector)
        server.shutdown()
    }

    @CommandLine.Command(name = "boom")
    class FailingCommand : Callable<Int> {
        override fun call(): Int = throw IllegalStateException("forced failure for NoTelemetryTest")
    }

    @Test
    fun `a failing command prints its stack trace locally and contacts nobody`() {
        val commandLine = createCommandLine().addSubcommand("boom", FailingCommand())
        val err = StringWriter()
        commandLine.err = PrintWriter(err, true)

        val exitCode = commandLine.execute("boom")

        assertThat(exitCode).isEqualTo(1)
        assertThat(err.toString()).contains("forced failure for NoTelemetryTest")
        assertThat(err.toString()).contains("The stack trace was:")
        assertThat(err.toString()).contains("java.lang.IllegalStateException")
        assertNothingLeftTheMachine()
    }

    @Test
    fun `version and help start no threads and contact nobody`() {
        val threadsBefore = liveThreadIds()

        listOf("--version", "--help").forEach { arg ->
            val commandLine = createCommandLine()
            commandLine.out = PrintWriter(StringWriter())
            commandLine.err = PrintWriter(StringWriter())
            assertThat(commandLine.execute(arg)).isEqualTo(0)
        }

        val newThreads = Thread.getAllStackTraces().keys
            .filter { it.isAlive && it.id !in threadsBefore }
            .map { it.name }
        assertThat(newThreads).isEmpty()
        assertNothingLeftTheMachine()
    }

    @Test
    fun `download-samples is an unknown command`() {
        val commandLine = createCommandLine()
        val err = StringWriter()
        commandLine.err = PrintWriter(err, true)

        assertThat(commandLine.execute("download-samples")).isNotEqualTo(0)
        assertThat(err.toString()).contains("Unmatched argument at index 0: 'download-samples'")
        assertThat(commandLine.usageMessage).doesNotContain("download-samples")
    }

    @Test
    fun `telemetry, error upload and update check classes are not on the classpath`() {
        listOf(
            "maestro.cli.analytics.Analytics",
            "maestro.cli.analytics.AnalyticsStateManager",
            "maestro.cli.analytics.CommandArgsSanitizer",
            "maestro.cli.analytics.PostHogEvent",
            "maestro.cli.util.ErrorReporter",
            "maestro.cli.util.ScreenReporter",
            "maestro.cli.util.ChangeLogUtils",
            "maestro.cli.util.FileDownloader",
            "maestro.cli.update.Updates",
            "maestro.cli.command.DownloadSamplesCommand",
            "maestro.cli.api.ApiClient",
            "maestro.cli.api.SystemInformationInterceptor",
            "com.posthog.server.PostHog",
        ).forEach { className ->
            assertThrows<ClassNotFoundException>(className) { Class.forName(className) }
        }
    }

    @Test
    fun `PostHog is not on the runtime classpath`() {
        // The test runtime classpath is a superset of the CLI's runtimeClasspath.
        val entries = System.getProperty("java.class.path").split(File.pathSeparator)
        assertThat(entries.filter { it.contains("maestro-orchestra") }).isNotEmpty()
        assertThat(entries.filter { it.contains("posthog") }).isEmpty()

        val jarsWithPostHog = entries
            .filter { it.endsWith(".jar") && File(it).isFile }
            .filter { jar ->
                ZipFile(jar).use { zip -> zip.entries().asSequence().any { it.name.startsWith("com/posthog/") } }
            }
        assertThat(jarsWithPostHog).isEmpty()
    }

    private fun assertNothingLeftTheMachine() {
        assertThat(requestedUris).isEmpty()
        assertThat(server.requestCount).isEqualTo(0)
        // Upstream wrote analytics consent and an installation UUID here on first run.
        assertThat(home.resolve("state").resolve("maestro").resolve("analytics.json").exists()).isFalse()
        assertThat(Files.walk(home).use { paths -> paths.anyMatch { it.fileName.toString() == "analytics.json" } })
            .isFalse()
    }

    private fun liveThreadIds(): Set<Long> =
        Thread.getAllStackTraces().keys.filter { it.isAlive }.map { it.id }.toSet()
}
