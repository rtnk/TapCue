package maestro.cli.mcp.tools

import com.google.common.truth.Truth.assertThat
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import maestro.orchestra.yaml.YamlCommandReader
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class CheatSheetToolTest {

    @TempDir
    lateinit var dir: Path

    private val cheatSheet = requireNotNull(CheatSheetTool.read()) { "cheat sheet resource missing" }

    @Test
    fun `cheat_sheet returns the bundled resource`() {
        val result = CheatSheetTool.handle()

        assertThat(result.isError).isNotEqualTo(true)
        assertThat((result.content.single() as TextContent).text).isEqualTo(cheatSheet)
    }

    @Test
    fun `every yaml example in the cheat sheet parses`() {
        val examples = Regex("```yaml\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
            .findAll(cheatSheet)
            .map { it.groupValues[1] }
            .toList()
        assertThat(examples.size).isAtLeast(8)

        examples.forEachIndexed { index, example ->
            val flow = dir.resolve("example-$index.yaml").also { it.writeText(example) }
            val commands = runCatching { YamlCommandReader.readCommands(flow) }
                .getOrElse { throw AssertionError("cheat sheet example #$index does not parse:\n$example", it) }
            assertThat(commands).isNotEmpty()
        }
    }

    @Test
    fun `cheat sheet links to no external site`() {
        assertThat(cheatSheet).doesNotContain("http://")
        assertThat(cheatSheet).doesNotContain("https://")
        assertThat(cheatSheet).doesNotContain("maestro.dev")
        assertThat(cheatSheet).doesNotContain("mobile.dev")
    }
}
