package maestro.cli

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import picocli.CommandLine
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * STEP-02 (Remove Remote AI): the CLI has no AI options, no AI clients and no AI report, and the
 * `maestro-ai` module is not on its runtime classpath.
 */
class NoRemoteAiTest {

    @Test
    fun `test help has no AI options`() {
        val help = CommandLine(App()).subcommands.getValue("test").usageMessage

        assertThat(help).contains("--format")
        assertThat(help).doesNotContain("--analyze")
        assertThat(help).doesNotContain("--api-key")
        assertThat(help).doesNotContainMatch("\\bAI\\b")
    }

    @Test
    fun `removed AI options are unknown`() {
        listOf("--analyze", "--api-key=secret").forEach { option ->
            val commandLine = CommandLine(App())
            val err = StringWriter()
            commandLine.err = PrintWriter(err)

            val exitCode = commandLine.execute("test", option, "flow.yaml")

            assertThat(exitCode).isEqualTo(CommandLine.ExitCode.USAGE)
            assertThat(err.toString()).contains("Unknown option: '$option'")
        }
    }

    @Test
    fun `AI classes are not on the classpath`() {
        listOf(
            "maestro.ai.AI",
            "maestro.ai.AIPredictionEngine",
            "maestro.ai.CloudAIPredictionEngine",
            "maestro.ai.cloud.Defect",
            "maestro.orchestra.AssertWithAICommand",
            "maestro.orchestra.AssertNoDefectsWithAICommand",
            "maestro.orchestra.ExtractTextWithAICommand",
            "maestro.orchestra.yaml.YamlAssertWithAI",
            "maestro.orchestra.yaml.YamlAssertNoDefectsWithAI",
            "maestro.orchestra.yaml.YamlExtractTextWithAI",
            "maestro.cli.insights.TestAnalysisManager",
            "maestro.cli.report.HtmlAITestSuiteReporter",
            "maestro.cli.report.HtmlInsightsAnalysisReporter",
            "maestro.cli.report.FlowAIOutput",
        ).forEach { className ->
            assertThrows<ClassNotFoundException>(className) { Class.forName(className) }
        }
    }

    @Test
    fun `AI report resources are gone`() {
        val classLoader = NoRemoteAiTest::class.java.classLoader

        assertThat(classLoader.getResource("ai_report.css")).isNull()
        assertThat(classLoader.getResource("tailwind.config.js")).isNull()
        assertThat(classLoader.getResource("askForDefects_schema.json")).isNull()
        assertThat(classLoader.getResource("extractText_schema.json")).isNull()
    }

    @Test
    fun `maestro-ai is not on the runtime classpath`() {
        // The test runtime classpath is a superset of the CLI's runtimeClasspath.
        val entries = System.getProperty("java.class.path").split(File.pathSeparator)

        assertThat(entries.filter { it.contains("maestro-orchestra") }).isNotEmpty()
        assertThat(entries.filter { it.contains("maestro-ai") }).isEmpty()
    }
}
