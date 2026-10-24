package maestro.utils

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Where TapCue keeps its files (decision D-2 in docs/ROADMAP-0.1.md). Every path is kept apart from upstream
 * Maestro's (`~/.maestro`, `$XDG_STATE_HOME/maestro`, `.maestro/` in projects), so both can be installed side by
 * side without sharing state.
 */
object TapCueDirs {

    /** Name of the command and of the state and log directories. */
    const val NAME = "tapcue"

    /** Directory in a user's project: flows by convention, and test artifacts under `--debug-output`. */
    const val PROJECT_DIR = ".tapcue"

    /** Upstream Maestro's project directory; never read implicitly, only pointed out to the user. */
    const val LEGACY_PROJECT_DIR = ".maestro"

    /** `~/.tapcue`: sessions, unpacked tool binaries and the real-device iOS driver build. */
    fun home(userHome: String = System.getProperty("user.home")): Path = Paths.get(userHome, ".$NAME")

    /** `$XDG_STATE_HOME/tapcue` when that variable is set, otherwise [home]: test artifacts. */
    fun state(
        xdgStateHome: String? = System.getenv("XDG_STATE_HOME"),
        userHome: String = System.getProperty("user.home"),
    ): Path = xdgStateHome?.let { Paths.get(it, NAME) } ?: home(userHome)

    /** Location of a tool binary unpacked from the CLI JAR, e.g. `applesimutils`. */
    fun binaryDependency(name: String): Path = home().resolve("deps").resolve(name)
}
