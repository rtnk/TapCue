package maestro.cli.util

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Upstream Maestro stored analytics consent and an installation UUID in `analytics.json` in its
 * state directory. This build has no analytics, so the file is deleted rather than left behind
 * (decision D-2 in docs/ROADMAP-0.1.md).
 */
object LegacyAnalyticsState {

    private const val FILE_NAME = "analytics.json"

    fun candidates(
        xdgStateHome: String? = System.getenv("XDG_STATE_HOME"),
        userHome: String = System.getProperty("user.home"),
    ): List<Path> = listOfNotNull(
        xdgStateHome?.let { Paths.get(it, "maestro", FILE_NAME) },
        Paths.get(userHome, ".maestro", FILE_NAME),
    )

    /** Deletes every leftover analytics state file; never fails the command that is starting. */
    fun remove(paths: List<Path> = candidates()) {
        paths.forEach { path -> runCatching { Files.deleteIfExists(path) } }
    }
}
