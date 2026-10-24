package maestro.cli

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * STEP-05: the launchers in the distribution check for Java 17 (D-7) and link nowhere at the vendor (N-35).
 * scripts/verify-dist.sh checks the same in the built archive.
 */
class StartScriptsTest {

    private val dir = File(System.getProperty("tapcue.start.scripts"))

    @Test
    fun `launchers are named tapcue and check for Java 17`() {
        val unix = dir.resolve("tapcue").readText()
        val windows = dir.resolve("tapcue.bat").readText()

        assertThat(unix).contains("ERROR: Java 17 or higher is required.")
        assertThat(windows).contains("ERROR: Java 17 or higher is required.")
        assertThat(dir.resolve("jvm-version.jar").isFile).isTrue()
    }

    @Test
    fun `launchers do not link to the vendor`() {
        val vendor = Regex("maestro\\.dev|mobile\\.dev|mobile-dev-inc")

        listOf("tapcue", "tapcue.bat").forEach { name ->
            assertThat(vendor.find(dir.resolve(name).readText())?.value).isNull()
        }
    }
}
