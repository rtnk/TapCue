package maestro.cli.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class UnpackerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `unpack closes streams when contents differ or match`() {
        val resource = Unpacker::class.java.classLoader.getResource("unpacker/fixture.txt")!!
        for (content in listOf("", "unpacker fixture\n")) {
            val target = tempDir.resolve("fixture.txt").toFile()
            target.writeText(content)

            Unpacker.unpack("unpacker/fixture.txt", target)

            assertThat(target.readText()).isEqualTo("unpacker fixture\n")
            assertNoOpenStreams(target)
            if (resource.protocol == "file") {
                assertNoOpenStreams(File(resource.toURI()))
            }
        }
    }

    private fun assertNoOpenStreams(file: File) {
        val descriptors = Path.of("/proc/self/fd")
        if (!Files.isDirectory(descriptors)) return
        val target = file.canonicalFile.toPath()
        val openStreams = Files.list(descriptors).use { stream ->
            stream.filter { descriptor ->
                runCatching { Files.readSymbolicLink(descriptor) }.getOrNull() == target
            }.count()
        }
        assertThat(openStreams).isEqualTo(0L)
    }
}
