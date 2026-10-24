package maestro.cli

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import picocli.CommandLine
import java.io.File
import java.util.zip.ZipFile

/**
 * STEP-00B (legal containment): simulator-server may only be run and distributed with official
 * Maestro releases (docs/LICENSE-AUDIT.md L-01), so neither it nor the Viewer frontend that drives
 * it may end up in the CLI JAR or on its runtime classpath.
 */
class CliJarContentsTest {

    private val forbiddenPrefixes = listOf("deps/simulator-server/", "mcp-viewer/", "maestro/cli/mcp/viewer/")

    @Test
    fun `CLI jar contains no simulator-server or Viewer entries`() {
        val jarPath = System.getProperty("maestro.cli.jar")
        assertThat(jarPath).isNotNull()
        val jar = File(jarPath)
        assertThat(jar.isFile).isTrue()

        val entries = ZipFile(jar).use { zip -> zip.entries().asSequence().map { it.name }.toList() }

        // Guards against inspecting an empty or unrelated archive.
        assertThat(entries).contains("maestro/cli/AppKt.class")
        assertThat(entries).contains("deps/applesimutils")
        assertThat(entries.filter { entry -> forbiddenPrefixes.any { entry.startsWith(it) } }).isEmpty()
        assertThat(entries.filter { it.substringAfterLast('/').startsWith("simulator-server") }).isEmpty()
    }

    @Test
    fun `CLI jar references no telemetry, update or CDN hosts`() {
        // STEP-03: hosts of removed telemetry, error/screen reports, update checks, changelog and
        // samples downloads, and the CDN the HTML report used to load Bootstrap from.
        val forbiddenHosts = listOf(
            "posthog.com",
            "api.copilot.mobile.dev",
            "raw.githubusercontent.com/mobile-dev-inc",
            "storage.googleapis.com/mobile.dev",
            "get.maestro.mobile.dev",
            "cdn.jsdelivr.net",
        )
        val jar = File(System.getProperty("maestro.cli.jar"))
        val hits = ZipFile(jar).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory }
                .flatMap { entry ->
                    val content = zip.getInputStream(entry).use { String(it.readBytes(), Charsets.ISO_8859_1) }
                    forbiddenHosts.filter { content.contains(it) }.map { "${entry.name}: $it" }
                }
                .toList()
        }

        assertThat(hits).isEmpty()
    }

    @Test
    fun `MCP server code and resources reference no Maestro web hosts`() {
        // STEP-04: the cheat sheet is bundled (N-15) and the server instructions no longer link docs.maestro.dev (N-17).
        val forbidden = listOf("maestro.dev", "mobile.dev", "https://", "http://")

        val jar = File(System.getProperty("maestro.cli.jar"))
        val (mcpEntries, hits) = ZipFile(jar).use { zip ->
            val entries = zip.entries().asSequence()
                .filter { !it.isDirectory }
                .filter { it.name.startsWith("maestro/cli/mcp/") || it.name.startsWith("mcp/") }
                .toList()
            entries.map { it.name } to entries.flatMap { entry ->
                val content = zip.getInputStream(entry).use { String(it.readBytes(), Charsets.ISO_8859_1) }
                forbidden.filter { content.contains(it) }.map { "${entry.name}: $it" }
            }
        }

        assertThat(mcpEntries).contains("mcp/cheat-sheet.md")
        assertThat(mcpEntries).contains("maestro/cli/mcp/tools/CheatSheetTool.class")
        assertThat(hits).isEmpty()
    }

    @Test
    fun `runtime classpath has no simulator-server or Viewer resources`() {
        val classLoader = CliJarContentsTest::class.java.classLoader

        assertThat(classLoader.getResource("deps/simulator-server")).isNull()
        assertThat(classLoader.getResource("simulator-server-version.txt")).isNull()
        assertThat(classLoader.getResource("mcp-viewer/index.html")).isNull()
    }

    @Test
    fun `mcp still accepts the former Viewer flags`() {
        val parsed = CommandLine(App()).parseArgs("mcp", "--no-viewer", "--viewer-port", "9999")

        assertThat(parsed.subcommand().matchedOption("--no-viewer")).isNotNull()
        assertThat(parsed.subcommand().matchedOption("--viewer-port")).isNotNull()
    }

    @Test
    fun `mcp help does not advertise the Viewer`() {
        val help = CommandLine(App()).subcommands.getValue("mcp").usageMessage

        assertThat(help).doesNotContain("--no-viewer")
        assertThat(help).doesNotContain("--viewer-port")
        assertThat(help).doesNotContain("Viewer")
    }

    @Test
    fun `Viewer classes are gone`() {
        // open_maestro_viewer went in STEP-00B, the rest of the mcp/viewer package in STEP-04.
        listOf(
            "maestro.cli.mcp.tools.OpenMaestroViewerTool",
            "maestro.cli.mcp.viewer.McpViewerServer",
            "maestro.cli.mcp.viewer.McpViewerDriver",
            "maestro.cli.mcp.viewer.McpViewerOrchestra",
            "maestro.cli.mcp.viewer.McpViewerEvents",
            "maestro.cli.mcp.viewer.StreamDeviceType",
        ).forEach { name ->
            val result = runCatching { Class.forName(name) }

            assertThat(result.exceptionOrNull()).isInstanceOf(ClassNotFoundException::class.java)
        }
    }
}
