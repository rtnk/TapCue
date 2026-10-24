package maestro.cli.command

import picocli.CommandLine
import java.util.concurrent.Callable
import maestro.cli.mcp.runMaestroMcpServer
import java.io.File
import maestro.cli.util.WorkingDirectory

@CommandLine.Command(
    name = "mcp",
    description = [
        "Starts the TapCue MCP server, exposing device and automation commands as Model Context Protocol (MCP) tools over STDIO for LLM agents and automation clients."
    ],
)
class McpCommand : Callable<Int> {
    @CommandLine.Option(
        names = ["--working-dir"],
        description = ["Base working directory for resolving files"]
    )
    private var workingDir: File? = null

    // The Viewer was removed: it needs simulator-server, which this fork does not ship (docs/LICENSE-AUDIT.md L-01).
    // Both flags are still accepted and ignored so existing MCP client configurations keep working.
    @CommandLine.Option(names = ["--no-viewer"], hidden = true)
    private var noViewer: Boolean = false

    @CommandLine.Option(names = ["--viewer-port"], hidden = true)
    private var viewerPort: Int? = null

    override fun call(): Int {
        if (workingDir != null) {
            WorkingDirectory.baseDir = workingDir!!.absoluteFile
        }

        // stderr only: stdout is the MCP JSON-RPC channel.
        if (viewerPort != null) {
            System.err.println("Warning: --viewer-port is ignored; the Maestro Viewer is not available in this build.")
        }

        runMaestroMcpServer()
        return 0
    }
}
