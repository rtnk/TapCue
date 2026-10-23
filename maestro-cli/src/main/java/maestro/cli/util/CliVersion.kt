package maestro.cli.util

data class CliVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<CliVersion> {

    override fun compareTo(other: CliVersion): Int {
        return COMPARATOR.compare(this, other)
    }

    override fun toString(): String {
        return "$major.$minor.$patch"
    }

    companion object {

        private val COMPARATOR = compareBy<CliVersion>({ it.major }, { it.minor }, { it.patch })

        fun parse(versionString: String): CliVersion? {
            val parts = versionString.split('.')
            if (parts.size != 3) return null
            val major = parts[0].toIntOrNull() ?: return null
            val minor = parts[1].toIntOrNull() ?: return null
            val patch = parts[2].toIntOrNull() ?: return null
            return CliVersion(major, minor, patch)
        }
    }
}
