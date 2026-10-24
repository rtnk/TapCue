package maestro.cli.util

import maestro.utils.TapCueDirs
import java.nio.file.Files
import java.nio.file.Path

/**
 * TapCue keeps a project's flows in `.tapcue/` (decision D-2 in docs/ROADMAP-0.1.md) and never reads upstream
 * Maestro's `.maestro/` on its own. A project that still has one gets a warning with the rename command instead
 * of being silently ignored.
 */
object LegacyProjectDir {

    val MESSAGE = "TapCue keeps project flows in `${TapCueDirs.PROJECT_DIR}/` and does not read " +
        "`${TapCueDirs.LEGACY_PROJECT_DIR}/` on its own. Rename the directory with: " +
        "mv ${TapCueDirs.LEGACY_PROJECT_DIR} ${TapCueDirs.PROJECT_DIR}"

    /**
     * @return [MESSAGE] when a flow path lies inside a `.maestro/` directory, or when [workingDir] has a
     * `.maestro/` directory and no `.tapcue/`; otherwise null.
     */
    fun warning(workingDir: Path, flowPaths: Collection<Path>): String? {
        val flowInLegacyDir = flowPaths.any { path ->
            workingDir.resolve(path).normalize().any { it.toString() == TapCueDirs.LEGACY_PROJECT_DIR }
        }
        val notRenamed = Files.isDirectory(workingDir.resolve(TapCueDirs.LEGACY_PROJECT_DIR)) &&
            !Files.exists(workingDir.resolve(TapCueDirs.PROJECT_DIR))
        return if (flowInLegacyDir || notRenamed) MESSAGE else null
    }
}
