package maestro.cli.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.types.*
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import kotlinx.serialization.json.*

object CheatSheetTool {

    // Shipped in the CLI JAR and versioned with it; upstream fetched it from Maestro's API (NETWORK-AUDIT N-15).
    internal const val RESOURCE_PATH = "mcp/cheat-sheet.md"

    fun create(): RegisteredTool {
        return RegisteredTool(
            Tool(
                name = "cheat_sheet",
                description = "Get the Maestro cheat sheet with common commands and syntax examples. " +
                    "Returns comprehensive documentation on Maestro flow syntax, commands, and best practices.",
                inputSchema = ToolSchema(
                    properties = buildJsonObject {},
                    required = emptyList()
                )
            )
        ) { _ -> handle() }
    }

    internal fun handle(): CallToolResult {
        val cheatSheet = read()
            ?: return CallToolResult(
                content = listOf(TextContent("Failed to get cheat sheet: resource $RESOURCE_PATH is missing from this build")),
                isError = true
            )
        return CallToolResult(content = listOf(TextContent(cheatSheet)))
    }

    internal fun read(): String? =
        CheatSheetTool::class.java.classLoader.getResource(RESOURCE_PATH)?.readText(Charsets.UTF_8)
}
