package maestro.orchestra.yaml

import com.google.common.truth.Truth.assertThat
import maestro.orchestra.AssertConditionCommand
import maestro.orchestra.error.SyntaxError
import maestro.orchestra.error.ValidationError
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * STEP-02 (Remove Remote AI): the former AI commands sent screenshots to Maestro Cloud. Maestro Local
 * rejects them while parsing, so a flow using one fails validation before any command runs.
 */
class RemovedAiCommandsTest {

    @TempDir
    lateinit var dir: Path

    @ParameterizedTest
    @ValueSource(
        strings = [
            "- assertNoDefectsWithAI",
            "- assertWithAI: The login button is visible",
            "- assertWithAI:\n    assertion: The login button is visible\n    optional: false",
            "- extractTextWithAI: The order number",
            "- extractTextWithAI:\n    query: The order number\n    outputVariable: orderNumber",
            "- runFlow:\n    commands:\n      - assertWithAI: The login button is visible",
        ]
    )
    fun `former AI commands are a validation error`(command: String) {
        val error = assertThrows<SyntaxError> { checkSyntax(command) }

        assertThat(error).isInstanceOf(ValidationError::class.java)
        assertThat(error.message).contains("AI Command Not Available")
        assertThat(error.detail).contains("AI commands are not available in Maestro Local")
    }

    @Test
    fun `each former AI command is named in its error`() {
        listOf("assertWithAI: x", "assertNoDefectsWithAI", "extractTextWithAI: x").forEach { command ->
            val name = command.substringBefore(':')

            val error = assertThrows<SyntaxError> { checkSyntax("- $command") }

            assertThat(error.message).contains("AI Command Not Available: $name")
        }
    }

    @Test
    fun `local assertions still parse`() {
        val flow = dir.resolve("flow.yaml")
        flow.writeText("appId: com.example\n---\n- assertVisible: Login\n- assertTrue: \${1 + 1 == 2}\n")

        val commands = YamlCommandReader.readCommands(flow)

        assertThat(commands.mapNotNull { it.asCommand() }.filterIsInstance<AssertConditionCommand>()).hasSize(2)
    }

    private fun checkSyntax(commands: String) =
        YamlCommandReader.checkSyntax("appId: com.example\n---\n$commands\n", null)
}
