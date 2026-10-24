package maestro.cli

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import maestro.cli.util.LegacyProjectDir
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * STEP-04B (decisions D-1, D-2): the CLI is called `tapcue`, says where it comes from, and keeps its state in
 * `~/.tapcue` / `$XDG_STATE_HOME/tapcue`. Nothing is written to upstream Maestro's directories, so the two can be
 * installed side by side.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class TapCueNameTest {

    @TempDir
    lateinit var dir: Path

    private val home: Path by lazy { dir.resolve("home").createDirectories() }
    private val project: Path by lazy { dir.resolve("project").createDirectories() }

    @Test
    fun `help is for tapcue and states its origin`() {
        val commandLine = createCommandLine()

        assertThat(commandLine.usageMessage).contains("Usage: tapcue ")
        assertThat(commandLine.usageMessage).contains(ORIGIN_NOTICE)
        assertThat(commandLine.usageMessage).doesNotContain("Usage: maestro")
        assertThat(commandLine.subcommands.getValue("test").usageMessage).contains("Usage: tapcue test ")
        assertThat(commandLine.subcommands.getValue("mcp").usageMessage).contains("TapCue MCP server")
    }

    @Test
    fun `version names tapcue and states its origin`() {
        val version = javaClass.classLoader.getResourceAsStream("version.properties").use {
            Properties().apply { load(it) }
        }["version"]

        assertThat(versionText()).isEqualTo("tapcue $version\n$ORIGIN_NOTICE")
    }

    @Test
    fun `commands keep their state in tapcue directories only`() {
        val flow = project.resolve("flow.yaml").also { it.writeText("appId: com.example\n---\n- launchApp\n") }

        val version = runCli("--version")
        assertThat(version.exitCode).isEqualTo(0)
        assertThat(version.output.lines().first()).startsWith("tapcue ")
        assertThat(version.output).contains(ORIGIN_NOTICE)

        val help = runCli("--help")
        assertThat(help.exitCode).isEqualTo(0)
        assertThat(help.output).contains("Usage: tapcue ")

        assertThat(runCli("check-syntax", flow.toString()).exitCode).isEqualTo(0)

        // Fails on the missing flow after the debug output directory and log are set up, before any device is needed.
        val test = runCli("test", "missing.yaml")
        assertThat(test.exitCode).isNotEqualTo(0)

        assertThat(home.resolve(".tapcue").resolve("tests").isDirectory()).isTrue()
        assertThat(home.resolve(".tapcue").resolve("deps").resolve("applesimutils").exists()).isTrue()
        assertNoUpstreamDirectories(home)
    }

    @Test
    fun `test artifacts follow XDG_STATE_HOME`() {
        val state = dir.resolve("state")

        runCli("test", "missing.yaml", xdgStateHome = state)

        assertThat(state.resolve("tapcue").resolve("tests").isDirectory()).isTrue()
        assertThat(home.resolve(".tapcue").resolve("tests").exists()).isFalse()
        assertNoUpstreamDirectories(state)
        assertNoUpstreamDirectories(home)
    }

    @Test
    fun `a project with a leftover maestro directory gets the rename command`() {
        project.resolve(".maestro").createDirectories()

        val test = runCli("test", ".maestro/missing.yaml")

        assertThat(test.output).contains(LegacyProjectDir.MESSAGE)
        assertThat(test.output).contains("mv .maestro .tapcue")
    }

    private fun assertNoUpstreamDirectories(root: Path) {
        val upstreamNames = setOf("maestro", ".maestro", ".mobiledev")
        Files.walk(root).use { paths ->
            paths.filter { it.isDirectory() }.forEach { path ->
                assertWithMessage("directory %s", path).that(path.fileName.toString()).isNotIn(upstreamNames)
            }
        }
    }

    private data class CliResult(val exitCode: Int, val output: String)

    private fun runCli(vararg args: String, xdgStateHome: Path? = null): CliResult {
        val java = File(System.getProperty("java.home"), "bin/java").absolutePath
        val output = dir.resolve("cli-output.log")
        val command = listOf(
            java,
            "-Duser.home=$home",
            "-cp",
            System.getProperty("java.class.path"),
            "maestro.cli.AppKt",
        ) + args
        val process = ProcessBuilder(command)
            .directory(project.toFile())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .apply {
                listOf("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "XDG_STATE_HOME", "XDG_CACHE_HOME", "XDG_DATA_HOME")
                    .forEach { environment().remove(it) }
                environment()["HOME"] = home.toString()
                xdgStateHome?.let { environment()["XDG_STATE_HOME"] = it.toString() }
            }
            .start()
        assertThat(process.waitFor(90, TimeUnit.SECONDS)).isTrue()
        return CliResult(process.exitValue(), output.readText())
    }
}
