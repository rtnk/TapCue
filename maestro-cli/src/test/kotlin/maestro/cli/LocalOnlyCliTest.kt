package maestro.cli

import com.google.common.truth.Truth.assertThat
import maestro.cli.api.ApiClient
import maestro.cli.command.TestCommand
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
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * STEP-01 (Remove Cloud CLI): the CLI has no Cloud commands and the local `test` command
 * neither reads a Cloud token nor needs the network to be constructed.
 */
@ExtendWith(SystemStubsExtension::class)
class LocalOnlyCliTest {

    @SystemStub
    private val environment = EnvironmentVariables()

    @TempDir
    lateinit var home: Path

    private var originalHome: String? = null
    private var originalProxySelector: ProxySelector? = null
    private val requestedUris = mutableListOf<URI>()

    @BeforeEach
    fun setUp() {
        originalHome = System.getProperty("user.home")
        System.setProperty("user.home", home.toString())

        // OkHttp and HttpURLConnection both consult the default ProxySelector before connecting,
        // so any outbound request made while building the command tree is recorded here.
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
    }

    @Test
    fun `removed Cloud commands are unknown`() {
        listOf("cloud", "login", "logout", "list-cloud-devices", "studio").forEach { command ->
            val commandLine = CommandLine(App())
            val err = StringWriter()
            commandLine.err = PrintWriter(err)

            val exitCode = commandLine.execute(command)

            assertThat(exitCode).isNotEqualTo(0)
            assertThat(err.toString()).contains("Unmatched argument at index 0: '$command'")
            assertThat(commandLine.subcommands.keys).doesNotContain(command)
        }
    }

    @Test
    fun `top level help lists no Cloud commands`() {
        val help = CommandLine(App()).usageMessage

        assertThat(help).doesNotContain("cloud")
        assertThat(help).doesNotContain("Cloud")
        assertThat(help).doesNotContain("login")
        assertThat(help).doesNotContain("studio")
    }

    @Test
    fun `test help has no Cloud options`() {
        val help = CommandLine(App()).subcommands.getValue("test").usageMessage

        assertThat(help).contains("--shard-split")
        assertThat(help).contains("--format")
        assertThat(help).doesNotContain("--api-url")
        assertThat(help).doesNotContain("Cloud")
        assertThat(help).doesNotContain("mobile.dev")
    }

    @Test
    fun `record renders locally and still accepts --local`() {
        val commandLine = CommandLine(App())
        val help = commandLine.subcommands.getValue("record").usageMessage
        assertThat(help).doesNotContain("--local")

        val parsed = commandLine.parseArgs("record", "--local", "flow.yaml", "out.mp4")
        assertThat(parsed.subcommand().matchedOption("--local")).isNotNull()
    }

    @Test
    fun `test command ignores stored Cloud credentials and needs no network`() {
        val storedToken = "stored-cloud-token"
        val envToken = "env-cloud-token"
        home.resolve(".mobiledev").createDirectories().resolve("authtoken").writeText(storedToken)
        environment.set("MAESTRO_CLOUD_API_KEY", envToken)

        val commandLine = CommandLine(App())
        commandLine.parseArgs("test", "--shard-split", "2", "flow.yaml")
        val testCommand = commandLine.subcommands.getValue("test").getCommand<TestCommand>()

        val fieldValues = TestCommand::class.java.declaredFields.map { field ->
            field.isAccessible = true
            field.get(testCommand)
        }
        assertThat(fieldValues).containsNoneOf(storedToken, envToken)
        assertThat(TestCommand::class.java.declaredFields.map { it.type.name })
            .containsNoneOf(ApiClient::class.java.name, "maestro.cli.auth.Auth")
        assertThat(requestedUris).isEmpty()
    }

    @Test
    fun `test command can be constructed without a home Cloud directory`() {
        assertThat(home.resolve(".mobiledev").exists()).isFalse()

        CommandLine(App()).parseArgs("test", "flow.yaml")

        assertThat(home.resolve(".mobiledev").exists()).isFalse()
        assertThat(requestedUris).isEmpty()
    }

    @Test
    fun `Cloud classes are not on the classpath`() {
        listOf(
            "maestro.cli.command.CloudCommand",
            "maestro.cli.command.LoginCommand",
            "maestro.cli.command.LogoutCommand",
            "maestro.cli.command.ListCloudDevicesCommand",
            "maestro.cli.command.StudioCommand",
            "maestro.cli.cloud.CloudInteractor",
            "maestro.cli.auth.Auth",
            "maestro.cli.graphics.RemoteVideoRenderer",
            "maestro.cli.promotion.PromotionStateManager",
            "maestro.cli.mcp.tools.RunOnCloudTool",
            "maestro.cli.mcp.tools.GetCloudRunStatusTool",
            "maestro.cli.mcp.tools.DescribeCloudRunTool",
            "maestro.cli.mcp.tools.ListCloudDevicesTool",
            "maestro.auth.ApiKey",
            "maestro.mockserver.MockInteractor",
        ).forEach { className ->
            assertThrows<ClassNotFoundException>(className) { Class.forName(className) }
        }
    }

    @Test
    fun `ApiClient has no Cloud endpoints`() {
        val methods = ApiClient::class.java.declaredMethods.map { it.name }

        assertThat(methods).containsNoneOf(
            "upload",
            "uploadStatus",
            "render",
            "getRenderState",
            "listCloudDevices",
            "getAuthUrl",
            "exchangeToken",
            "isAuthTokenValid",
            "getUser",
            "getOrg",
            "getOrgs",
            "switchOrg",
            "getProjects",
            "describeRun",
        )
    }
}
