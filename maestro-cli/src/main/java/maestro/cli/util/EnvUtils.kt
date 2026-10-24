package maestro.cli.util

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import maestro.device.CPU_ARCHITECTURE
import maestro.utils.TapCueDirs
import java.io.IOException
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object EnvUtils {
    val OS_NAME: String = System.getProperty("os.name")
    val OS_ARCH: String = System.getProperty("os.arch")
    val OS_VERSION: String = System.getProperty("os.version")

    val CLI_VERSION: CliVersion? = getCLIVersion()

    /** `$XDG_STATE_HOME/tapcue`, else `~/.tapcue` (see [TapCueDirs]). */
    fun xdgStateHome(): Path = TapCueDirs.state()

    /** `~/.tapcue` (see [TapCueDirs]). */
    fun tapcueHome(): Path = TapCueDirs.home()

    /**
     * @return true, if we're executing from Windows Linux shell (WSL)
     */
    fun isWSL(): Boolean {
        try {
            val p1 = ProcessBuilder("printenv", "WSL_DISTRO_NAME").start()
            if (!p1.waitFor(20, TimeUnit.SECONDS)) throw TimeoutException()
            if (p1.exitValue() == 0 && String(p1.inputStream.readBytes()).trim().isNotEmpty()) {
                return true
            }

            val p2 = ProcessBuilder("printenv", "IS_WSL").start()
            if (!p2.waitFor(20, TimeUnit.SECONDS)) throw TimeoutException()
            if (p2.exitValue() == 0 && String(p2.inputStream.readBytes()).trim().isNotEmpty()) {
                return true
            }
        } catch (ignore: Exception) {
            // ignore
        }

        return false
    }

    fun isWindows(): Boolean {
        return OS_NAME.lowercase().startsWith("windows")
    }

    /**
     * Returns major version of Java, e.g. 8, 11, 17, 21.
     */
    fun getJavaVersion(): Int {
        // Adapted from https://stackoverflow.com/a/2591122/7009800
        val version = System.getProperty("java.version")
        return if (version.startsWith("1.")) {
            version.substring(2, 3).toInt()
        } else {
            val dot = version.indexOf(".")
            if (dot != -1) version.substring(0, dot).toInt() else 0
        }
    }

    fun getFlutterVersionAndChannel(): Pair<String?, String?> {
        val stdout = try {
             runProcess(
                "flutter",
                "--no-version-check", "--version", "--machine",
            ).joinToString(separator = "")
        } catch (e: IOException) {
            // Flutter is probably not installed
            return Pair(first = null, second = null)
        }

        val mapper = jacksonObjectMapper()
        val version = runCatching {
            val obj: Map<String, String> = mapper.readValue(stdout)
            obj["flutterVersion"].toString()
        }
        val channel = runCatching {
            val obj: Map<String, String> = mapper.readValue(stdout)
            obj["channel"].toString()
        }

        return Pair(first = version.getOrNull(), second = channel.getOrNull())
    }

    fun getMacOSArchitecture(): CPU_ARCHITECTURE {
        return determineArchitectureDetectionStrategy().detectArchitecture()
    }

    private fun determineArchitectureDetectionStrategy(): ArchitectureDetectionStrategy {
        return if (isWindows()) {
            ArchitectureDetectionStrategy.WindowsArchitectureDetection
        } else if (runProcess("uname").contains("Linux")) {
            ArchitectureDetectionStrategy.LinuxArchitectureDetection
        } else {
            ArchitectureDetectionStrategy.MacOsArchitectureDetection
        }
    }

    fun getCLIVersion(): CliVersion? {
        val props = try {
            EnvUtils::class.java.classLoader.getResourceAsStream("version.properties").use {
                Properties().apply { load(it) }
            }
        } catch (e: Exception) {
            return null
        }

        val versionString = props["version"] as? String ?: return null

        return CliVersion.parse(versionString)
    }
}

sealed interface ArchitectureDetectionStrategy {

    fun detectArchitecture(): CPU_ARCHITECTURE

    object MacOsArchitectureDetection : ArchitectureDetectionStrategy {
        override fun detectArchitecture(): CPU_ARCHITECTURE {
            fun runSysctl(property: String) = runProcess("sysctl", property).any { it.endsWith(": 1") }

            // Prefer sysctl over 'uname -m' due to Rosetta making it unreliable
            val isArm64 = runSysctl("hw.optional.arm64")
            val isX86_64 = runSysctl("hw.optional.x86_64")
            return when {
                isArm64 -> CPU_ARCHITECTURE.ARM64
                isX86_64 -> CPU_ARCHITECTURE.X86_64
                else -> CPU_ARCHITECTURE.UNKNOWN
            }
        }
    }

    object LinuxArchitectureDetection : ArchitectureDetectionStrategy {
        override fun detectArchitecture(): CPU_ARCHITECTURE {
            return when (runProcess("uname", "-m").first()) {
              "x86_64" -> CPU_ARCHITECTURE.X86_64
              "arm64" -> CPU_ARCHITECTURE.ARM64
              else -> CPU_ARCHITECTURE.UNKNOWN
            }
        }
    }

    object WindowsArchitectureDetection: ArchitectureDetectionStrategy {
        override fun detectArchitecture(): CPU_ARCHITECTURE {
            return CPU_ARCHITECTURE.X86_64
        }
    }
}

internal fun runProcess(program: String, vararg arguments: String): List<String> {
    val process = ProcessBuilder(program, *arguments).start()
    return try {
        process.inputStream.reader().use { it.readLines().map(String::trim) }
    } catch (ignore: Exception) {
        emptyList()
    }
}
