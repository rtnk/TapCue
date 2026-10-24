package maestro.cli.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createDirectories

class LegacyProjectDirTest {

    @TempDir
    lateinit var project: Path

    @Test
    fun `no warning for a project using tapcue`() {
        project.resolve(".tapcue").createDirectories()

        assertThat(LegacyProjectDir.warning(project, listOf(Paths.get(".tapcue")))).isNull()
    }

    @Test
    fun `no warning without any project directory`() {
        assertThat(LegacyProjectDir.warning(project, listOf(Paths.get("flow.yaml")))).isNull()
    }

    @Test
    fun `warns when a leftover maestro directory was not renamed`() {
        project.resolve(".maestro").createDirectories()

        val warning = LegacyProjectDir.warning(project, listOf(Paths.get("flow.yaml")))

        assertThat(warning).contains("mv .maestro .tapcue")
    }

    @Test
    fun `no warning when both directories exist and flows come from tapcue`() {
        project.resolve(".maestro").createDirectories()
        project.resolve(".tapcue").createDirectories()

        assertThat(LegacyProjectDir.warning(project, listOf(Paths.get(".tapcue")))).isNull()
    }

    @Test
    fun `warns when a flow path is inside a maestro directory`() {
        project.resolve(".tapcue").createDirectories()

        val relative = LegacyProjectDir.warning(project, listOf(Paths.get(".maestro", "login.yaml")))
        val absolute = LegacyProjectDir.warning(project, listOf(project.resolve("app/.maestro")))

        assertThat(relative).isEqualTo(LegacyProjectDir.MESSAGE)
        assertThat(absolute).isEqualTo(LegacyProjectDir.MESSAGE)
    }
}
