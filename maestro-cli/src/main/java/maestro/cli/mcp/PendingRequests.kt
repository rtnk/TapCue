package maestro.cli.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Holds back end-of-input on the MCP STDIO channel until every request read so far has been answered.
 *
 * The SDK's `StdioServerTransport` closes the session as soon as STDIN reaches EOF and cancels the requests it has read
 * but not answered yet, so a client that writes its requests and closes its end right away (`printf '…' | tapcue mcp`)
 * got no response at all. [input] and [output] wrap the two streams: requests are recorded by id as their lines pass
 * in, responses clear them as their lines pass out, and EOF reaches the transport only when nothing is pending.
 * A request the client cancels (`notifications/cancelled`) is no longer waited for, since the server does not answer it.
 */
internal class PendingRequests {

    private val lock = Object()
    private val pending = mutableSetOf<String>()

    fun input(stream: InputStream): InputStream = object : FilterInputStream(stream) {
        private val line = LineSplitter(::onIncoming)

        override fun read(): Int {
            val b = super.read()
            if (b == -1) awaitAnswered() else line.accept(b)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n == -1) awaitAnswered() else for (i in off until off + n) line.accept(b[i].toInt())
            return n
        }
    }

    fun output(stream: OutputStream): OutputStream = object : FilterOutputStream(stream) {
        private val line = LineSplitter(::onOutgoing)

        override fun write(b: Int) {
            out.write(b)
            line.accept(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            for (i in off until off + len) line.accept(b[i].toInt())
        }
    }

    fun pendingCount(): Int = synchronized(lock) { pending.size }

    private fun awaitAnswered() {
        synchronized(lock) {
            while (pending.isNotEmpty()) lock.wait()
        }
    }

    private fun onIncoming(line: String) {
        messages(line).forEach { message ->
            val method = message["method"]
            val id = message["id"]
            synchronized(lock) {
                when {
                    method != null && id != null -> pending += id.toString()
                    method.isString("notifications/cancelled") ->
                        message["params"]?.let { it as? JsonObject }?.get("requestId")?.let { pending -= it.toString() }
                }
                lock.notifyAll()
            }
        }
    }

    private fun onOutgoing(line: String) {
        messages(line).forEach { message ->
            val id = message["id"]
            if (id != null && message["method"] == null) {
                synchronized(lock) {
                    pending -= id.toString()
                    lock.notifyAll()
                }
            }
        }
    }

    private fun messages(line: String): List<JsonObject> {
        val element = runCatching { Json.parseToJsonElement(line) }.getOrNull() ?: return emptyList()
        return when (element) {
            is JsonObject -> listOf(element)
            is JsonArray -> element.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            else -> emptyList()
        }
    }

    private fun JsonElement?.isString(value: String): Boolean = this?.toString() == "\"$value\""

    /** Collects bytes into UTF-8 lines; a line without its terminating newline is never a complete message. */
    private class LineSplitter(private val onLine: (String) -> Unit) {
        private val buffer = ByteArrayOutputStream()

        fun accept(b: Int) {
            if (b == '\n'.code) {
                val text = buffer.toString(Charsets.UTF_8).trim()
                buffer.reset()
                if (text.isNotEmpty()) onLine(text)
            } else {
                buffer.write(b)
            }
        }
    }
}
