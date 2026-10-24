package maestro.cli.mcp

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import maestro.cli.mcp.tools.CheatSheetTool
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.Writer
import java.net.ServerSocket
import java.net.SocketException
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * STEP-04 (Local-only MCP): `maestro mcp` as a real process speaking JSON-RPC over STDIO. The handshake,
 * tool listing and `cheat_sheet` work without any network access, `list_devices` answers without devices,
 * closing STDIN ends the process, and stdout carries nothing but JSON-RPC messages.
 *
 * The child JVM sends every HTTP(S) and SOCKS connection to a non-loopback host through local recorders,
 * so an outbound attempt is counted even though it never leaves the machine.
 */
@Timeout(value = 6, unit = TimeUnit.MINUTES)
class McpStdioTest {

    @TempDir
    lateinit var home: Path

    private val httpProxy = MockWebServer()
    private lateinit var socksProxy: ServerSocket
    private val socksConnections = AtomicInteger()

    @BeforeEach
    fun setUp() {
        httpProxy.start()
        socksProxy = ServerSocket(0)
        thread(isDaemon = true) {
            try {
                while (true) {
                    socksProxy.accept().close()
                    socksConnections.incrementAndGet()
                }
            } catch (_: SocketException) {
                // closed in tearDown
            }
        }
    }

    @AfterEach
    fun tearDown() {
        socksProxy.close()
        httpProxy.shutdown()
    }

    @Test
    fun `mcp serves the local tools over STDIO without network access`() {
        val process = startMcpProcess()
        val stdout = LinkedBlockingQueue<String>()
        val allStdout = mutableListOf<String>()
        val reader = thread(isDaemon = true) {
            BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).forEachLine { line ->
                synchronized(allStdout) { allStdout += line }
                stdout.put(line)
            }
        }
        val stdin = process.outputStream.writer(Charsets.UTF_8)

        try {
            stdin.sendRequest(1, "initialize") {
                put("protocolVersion", "2025-06-18")
                putJsonObject("capabilities") {}
                putJsonObject("clientInfo") {
                    put("name", "McpStdioTest")
                    put("version", "1.0.0")
                }
            }
            val initialize = stdout.awaitResponse(1)
            val serverInfo = initialize.result()["serverInfo"]!!.jsonObject
            assertThat(serverInfo["name"]!!.jsonPrimitive.content).isEqualTo("tapcue")
            val instructions = initialize.result()["instructions"]!!.jsonPrimitive.content
            assertThat(instructions).contains("cheat_sheet")
            assertThat(instructions).contains("TapCue")
            assertThat(instructions).contains(".tapcue/")
            assertThat(instructions).doesNotContain("docs.maestro.dev")
            assertThat(instructions).doesNotContain("http")

            stdin.sendNotification("notifications/initialized")

            stdin.sendRequest(2, "tools/list") {}
            val tools = stdout.awaitResponse(2).result()["tools"]!!.jsonArray
                .map { it.jsonObject["name"]!!.jsonPrimitive.content }
            assertThat(tools).containsExactly("list_devices", "take_screenshot", "run", "inspect_screen", "cheat_sheet")

            stdin.sendRequest(3, "tools/call") {
                put("name", "cheat_sheet")
                putJsonObject("arguments") {}
            }
            val cheatSheet = stdout.awaitResponse(3).result()
            assertThat(cheatSheet.isError()).isFalse()
            assertThat(cheatSheet.text()).isEqualTo(CheatSheetTool.read())

            stdin.sendRequest(4, "tools/call") {
                put("name", "list_devices")
                putJsonObject("arguments") {}
            }
            // list_devices runs `xcrun simctl` on macOS; the first call on a fresh runner starts CoreSimulator, which
            // took more than 90 s on a loaded macos-26 runner (release.yml run 40116758695).
            val listDevices = stdout.awaitResponse(4, timeoutSeconds = 240).result()
            assertThat(listDevices.isError()).isFalse()
            // The machine running the test may or may not have emulators or simulators; the shape is what counts.
            Json.parseToJsonElement(listDevices.text()).jsonObject["devices"]!!.jsonArray.forEach { device ->
                assertThat(device.jsonObject.keys).containsAtLeast("device_id", "platform", "connected")
            }

            stdin.close()
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue()
            assertThat(process.exitValue()).isEqualTo(0)
            reader.join(TimeUnit.SECONDS.toMillis(5))
        } finally {
            process.destroyForcibly()
        }

        val lines = synchronized(allStdout) { allStdout.toList() }.filter { it.isNotBlank() }
        assertThat(lines).isNotEmpty()
        lines.forEach { line ->
            val message = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull()
            assertWithMessage("stdout line: %s", line).that(message).isNotNull()
            assertThat(message!!["jsonrpc"]?.jsonPrimitive?.content).isEqualTo("2.0")
        }

        assertThat(httpProxy.requestCount).isEqualTo(0)
        assertThat(socksConnections.get()).isEqualTo(0)
        // STEP-04B: state goes to ~/.tapcue only, never to upstream Maestro's directories.
        assertThat(home.resolve(".maestro").toFile().exists()).isFalse()
        assertThat(home.resolve("state").resolve("maestro").toFile().exists()).isFalse()
    }

    @Test
    fun `requests followed by an immediate end of input are all answered`() {
        // STEP-06: as with `printf '…' | tapcue mcp` — STDIN closes right after the last request.
        val process = startMcpProcess()
        try {
            process.outputStream.writer(Charsets.UTF_8).use { stdin ->
                stdin.sendRequest(1, "initialize") {
                    put("protocolVersion", "2025-06-18")
                    putJsonObject("capabilities") {}
                    putJsonObject("clientInfo") {
                        put("name", "McpStdioTest")
                        put("version", "1.0.0")
                    }
                }
                stdin.sendNotification("notifications/initialized")
                stdin.sendRequest(2, "tools/list") {}
                stdin.sendRequest(3, "tools/call") {
                    put("name", "cheat_sheet")
                    putJsonObject("arguments") {}
                }
            }
            val lines = process.inputStream.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue()
            assertThat(process.exitValue()).isEqualTo(0)

            val ids = lines.map { Json.parseToJsonElement(it).jsonObject }
                .filter { it.containsKey("result") }
                .map { it["id"]!!.jsonPrimitive.int }
            assertWithMessage("stdout: %s", lines).that(ids).containsExactly(1, 2, 3)
        } finally {
            process.destroyForcibly()
        }
        assertThat(httpProxy.requestCount).isEqualTo(0)
        assertThat(socksConnections.get()).isEqualTo(0)
    }

    private fun startMcpProcess(): Process {
        val java = File(System.getProperty("java.home"), "bin/java").absolutePath
        val proxyHost = httpProxy.hostName
        val command = listOf(
            java,
            "-Duser.home=$home",
            "-Dhttp.proxyHost=$proxyHost",
            "-Dhttp.proxyPort=${httpProxy.port}",
            "-Dhttps.proxyHost=$proxyHost",
            "-Dhttps.proxyPort=${httpProxy.port}",
            "-Dhttp.nonProxyHosts=",
            "-DsocksProxyHost=127.0.0.1",
            "-DsocksProxyPort=${socksProxy.localPort}",
            "-Djava.net.useSystemProxies=false",
            "-cp",
            System.getProperty("java.class.path"),
            "maestro.cli.AppKt",
            "mcp",
            "--working-dir",
            home.toString(),
        )
        return ProcessBuilder(command)
            .redirectError(home.resolve("mcp-stderr.log").toFile())
            .apply {
                environment().remove("JAVA_TOOL_OPTIONS")
                environment().remove("JDK_JAVA_OPTIONS")
                environment()["HOME"] = home.toString()
                environment()["XDG_STATE_HOME"] = home.resolve("state").toString()
            }
            .start()
    }

    private fun Writer.sendRequest(id: Int, method: String, params: JsonObjectBuilder.() -> Unit) {
        send(buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            putJsonObject("params", params)
        })
    }

    private fun Writer.sendNotification(method: String) {
        send(buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
        })
    }

    private fun Writer.send(message: JsonElement) {
        write(message.toString())
        write("\n")
        flush()
    }

    private fun LinkedBlockingQueue<String>.awaitResponse(id: Int, timeoutSeconds: Long = 90): JsonObject {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (true) {
            val remaining = deadline - System.nanoTime()
            val line = poll(remaining.coerceAtLeast(0), TimeUnit.NANOSECONDS)
                ?: error("No response to request $id; stderr:\n${home.resolve("mcp-stderr.log").toFile().readText()}")
            val message = runCatching { Json.parseToJsonElement(line).jsonObject }
                .getOrElse { throw AssertionError("stdout carried a line that is not a JSON-RPC message: $line", it) }
            if (message["id"]?.jsonPrimitive?.int == id) return message
        }
    }

    private fun JsonObject.result(): JsonObject =
        this["result"]?.jsonObject ?: error("Expected a result, got: $this")

    private fun JsonObject.isError(): Boolean = this["isError"]?.jsonPrimitive?.boolean ?: false

    private fun JsonObject.text(): String =
        this["content"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content
}
