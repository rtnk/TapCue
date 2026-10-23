package maestro.cli.mcp

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class McpServerTest {

    @Test
    fun `instructions fit the 2KB MCP cap`() {
        assertThat(INSTRUCTIONS.toByteArray(Charsets.UTF_8).size).isLessThan(2048)
    }

    @Test
    fun `instructions reference every registered tool`() {
        val registeredTools = listOf(
            "list_devices",
            "take_screenshot",
            "run",
            "inspect_screen",
            "cheat_sheet",
        )
        registeredTools.forEach { tool ->
            assertThat(INSTRUCTIONS).contains(tool)
        }
    }

    @Test
    fun `instructions do not point to Maestro Cloud`() {
        assertThat(INSTRUCTIONS).doesNotContain("Cloud")
        assertThat(INSTRUCTIONS).doesNotContain("cloud")
        assertThat(INSTRUCTIONS).doesNotContain("maestro login")
        assertThat(INSTRUCTIONS).doesNotContain("MAESTRO_CLOUD_API_KEY")
    }
}
