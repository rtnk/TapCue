package maestro.cli.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import maestro.debuglog.LogConfig
import maestro.cli.mcp.tools.ListDevicesTool
import maestro.cli.mcp.tools.TakeScreenshotTool
import maestro.cli.mcp.tools.RunTool
import maestro.cli.mcp.tools.InspectScreenTool
import maestro.cli.mcp.tools.CheatSheetTool
import maestro.cli.util.WorkingDirectory
import maestro.utils.TapCueDirs
import java.io.PrintStream

internal val INSTRUCTIONS = """
    TapCue MCP authors, edits and runs UI tests via declarative YAML flows (the Maestro flow format) on Android emulators and iOS simulators. Use when the user wants to write, run, or debug a mobile UI test, reproduce a bug, or self-validate a user-facing change you just built.

    Every tool that drives a device (`take_screenshot`, `inspect_screen`, `run`) needs a `device_id` from `list_devices` first.

    Call `cheat_sheet` (flow syntax reference bundled with this CLI) before authoring unfamiliar commands, required args, nested properties, conditionals, or multi-screen flows.

    ## Local workflow

    `list_devices` -> `inspect_screen` -> `run`.

    1. `list_devices`: pick a `device_id` (simulator or emulator). If empty, ask the user to boot one. Use only IDs returned.
    2. `inspect_screen`: fetch the current screen's view hierarchy before targeting elements. Use `take_screenshot` when a visual helps. Re-inspect after any UI change.
    3. `run`: pass exactly one of `{ yaml }` (inline, preferred for exploration), `{ files }`, or `{ dir, include_tags, exclude_tags }`. Always include `device_id`. Pass `env` for flow variables. `run` validates syntax.

    Flows declare `appId` and start with `launchApp`; web flows (`url:`) are not supported in TapCue 0.1.0. Save flows in the project's `.tapcue/` directory. `include_tags`/`exclude_tags` are bare names without `@`. Prefer one full flow over many single-command calls.
""".trimIndent()

// Captures the real stdout so the MCP protocol channel stays pristine even after
// `claimMcpStdout()` routes `System.out` to stderr. Defaults to `System.out` for
// test/dev paths that invoke `runMaestroMcpServer()` without going through main().
private var mcpProtocolOut: PrintStream = System.out

/**
 * Must run before any MCP-adjacent class loads: static init (kotlin-logging banner,
 * dependency banners, third-party println-on-load) writes to whatever stdout
 * is at that moment and corrupts the JSON-RPC handshake for strict clients like
 * Claude Desktop.
 */
internal fun claimMcpStdout() {
    mcpProtocolOut = System.out
    System.setOut(System.err)
}

fun runMaestroMcpServer() {
    // LogConfig silences log4j; the stdout redirect in `claimMcpStdout` catches
    // everything else. Keep both; they cover different noise sources.
    LogConfig.configure(logFileName = null, printToConsole = false)

    val sessionManager = McpMaestroSessionManager()

    val server = Server(
        serverInfo = Implementation(
            name = TapCueDirs.NAME,
            version = "1.0.0"
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true)
            )
        ),
        instructions = INSTRUCTIONS
    )

    server.addTools(listOf(
        ListDevicesTool.create(),
        TakeScreenshotTool.create(sessionManager),
        RunTool.create(sessionManager),
        InspectScreenTool.create(sessionManager),
        CheatSheetTool.create(),
    ))

    // A client may close STDIN right after its last request; the responses still go out (see PendingRequests).
    val pendingRequests = PendingRequests()
    val transport = StdioServerTransport(
        pendingRequests.input(System.`in`).asSource().buffered(),
        pendingRequests.output(mcpProtocolOut).asSink().buffered()
    )

    System.err.println("MCP Server: Started. Waiting for messages. Working directory: ${WorkingDirectory.baseDir}")

    try {
        runBlocking {
            val session = server.createSession(transport)
            val done = Job()
            session.onClose { done.complete() }
            done.join()
        }
    } finally {
        sessionManager.close()
    }
}
