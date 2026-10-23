package maestro.cli.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

class LegacyAnalyticsStateTest {

    @TempDir
    lateinit var dir: Path

    @Test
    fun `removes analytics state from both upstream locations and keeps other state`() {
        val xdg = dir.resolve("xdg")
        val home = dir.resolve("home")
        val xdgFile = xdg.resolve("maestro").createDirectories().resolve("analytics.json")
        val homeFile = home.resolve(".maestro").createDirectories().resolve("analytics.json")
        val session = home.resolve(".maestro").resolve("sessions")
        listOf(xdgFile, homeFile, session).forEach { it.writeText("{}") }

        LegacyAnalyticsState.remove(LegacyAnalyticsState.candidates(xdg.toString(), home.toString()))

        assertThat(xdgFile.exists()).isFalse()
        assertThat(homeFile.exists()).isFalse()
        assertThat(session.exists()).isTrue()
    }

    @Test
    fun `missing files and directories are not an error`() {
        LegacyAnalyticsState.remove(LegacyAnalyticsState.candidates(null, dir.resolve("nowhere").toString()))

        assertThat(dir.resolve("nowhere").exists()).isFalse()
    }
}
