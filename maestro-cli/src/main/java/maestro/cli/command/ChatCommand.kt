package maestro.cli.command

import org.jline.jansi.Ansi
import picocli.CommandLine
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "chat",
    description = [
        "Discontinued. Use the local MCP server instead: tapcue mcp"
    ],
    hidden = true
)
class ChatCommand : Callable<Int> {

    // Accepted and ignored, so existing invocations reach the message instead of a usage dump.
    @CommandLine.Option(order = 0, names = ["--api-key", "--apiKey"], hidden = true)
    private var apiKey: String? = null

    @CommandLine.Option(order = 1, names = ["--api-url", "--apiUrl"], hidden = true)
    private var apiUrl: String? = null

    @CommandLine.Option(order = 2, names = ["--ask"], hidden = true)
    private var ask: String? = null

    override fun call(): Int {
        val message = Ansi.ansi().render(MESSAGE).toString()

        // --ask may be scripted in CI, so it fails loudly rather than returning prose as success.
        return if (ask != null) {
            System.err.println(message)
            1
        } else {
            println(message)
            0
        }
    }

    companion object {
        private val MESSAGE = """
            @|yellow tapcue chat has been discontinued.|@

            It relied on a remote AI service, which TapCue does not include. The local MCP server connects your coding agent (Claude Code, Cursor, Codex, and others) to TapCue over STDIO so it can write, run, and debug your flows.

            Get started: @|bold tapcue mcp|@
        """.trimIndent()
    }
}
