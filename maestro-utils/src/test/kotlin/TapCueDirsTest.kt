import com.google.common.truth.Truth.assertThat
import maestro.utils.TapCueDirs
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class TapCueDirsTest {

    @Test
    fun `home is dot tapcue in the user home`() {
        assertThat(TapCueDirs.home("/home/u")).isEqualTo(Paths.get("/home/u", ".tapcue"))
    }

    @Test
    fun `state follows XDG_STATE_HOME and falls back to home`() {
        assertThat(TapCueDirs.state("/xdg", "/home/u")).isEqualTo(Paths.get("/xdg", "tapcue"))
        assertThat(TapCueDirs.state(null, "/home/u")).isEqualTo(Paths.get("/home/u", ".tapcue"))
    }

    @Test
    fun `binary dependencies live under home deps`() {
        assertThat(TapCueDirs.binaryDependency("applesimutils"))
            .isEqualTo(TapCueDirs.home().resolve("deps").resolve("applesimutils"))
    }

    @Test
    fun `no path uses upstream Maestro names`() {
        val paths = listOf(
            TapCueDirs.home("/home/u"),
            TapCueDirs.state("/xdg", "/home/u"),
            TapCueDirs.state(null, "/home/u"),
            TapCueDirs.binaryDependency("applesimutils"),
        )
        paths.flatMap { path -> path.map { it.toString() } }.forEach { name ->
            assertThat(name).isNotEqualTo("maestro")
            assertThat(name).isNotEqualTo(".maestro")
            assertThat(name).isNotEqualTo(".mobiledev")
        }
        assertThat(TapCueDirs.PROJECT_DIR).isEqualTo(".tapcue")
    }
}
