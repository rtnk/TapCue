package maestro.cli.mcp

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class PendingRequestsTest {

    @Test
    fun `end of input waits until every request read so far is answered`() {
        val pending = PendingRequests()
        val input = pending.input(stream(request(1, "initialize") + request(2, "tools/list") + notification("notifications/initialized")))
        val output = pending.output(ByteArrayOutputStream())

        val eof = readToEndInBackground(input)
        awaitPending(pending, 2)
        assertThat(eof.await(300, TimeUnit.MILLISECONDS)).isFalse()

        output.write(response(1).toByteArray())
        output.flush()
        assertThat(eof.await(300, TimeUnit.MILLISECONDS)).isFalse()

        output.write(response(2).toByteArray())
        assertThat(eof.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(pending.pendingCount()).isEqualTo(0)
    }

    @Test
    fun `a cancelled request is not waited for`() {
        val pending = PendingRequests()
        val input = pending.input(stream(
            request("abc", "tools/call") +
                """{"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":"abc"}}""" + "\n"
        ))

        assertThat(readToEndInBackground(input).await(5, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    fun `an error response clears the request and output passes through unchanged`() {
        val pending = PendingRequests()
        val input = pending.input(stream(request(7, "no/such/method")))
        val sink = ByteArrayOutputStream()
        val output = pending.output(sink)

        val eof = readToEndInBackground(input)
        awaitPending(pending, 1)
        val error = """{"jsonrpc":"2.0","id":7,"error":{"code":-32601,"message":"Method not found"}}""" + "\n"
        // Split mid-line, as a buffered writer may.
        output.write(error.substring(0, 10).toByteArray())
        output.write(error.substring(10).toByteArray())

        assertThat(eof.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(sink.toString(Charsets.UTF_8)).isEqualTo(error)
    }

    @Test
    fun `an unterminated line or a line that is not JSON is not a request`() {
        val pending = PendingRequests()
        val input = pending.input(stream("not json\n" + request(1, "initialize").trimEnd()))

        assertThat(readToEndInBackground(input).await(5, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    fun `server-to-client requests on the output do not clear client requests`() {
        val pending = PendingRequests()
        val input = pending.input(stream(request(1, "tools/call")))
        val output = pending.output(ByteArrayOutputStream())

        val eof = readToEndInBackground(input)
        awaitPending(pending, 1)
        output.write(request(1, "roots/list").toByteArray())
        assertThat(eof.await(300, TimeUnit.MILLISECONDS)).isFalse()

        output.write(response(1).toByteArray())
        assertThat(eof.await(5, TimeUnit.SECONDS)).isTrue()
    }

    // A response can only follow the request it answers, so wait until the reader has seen the requests.
    private fun awaitPending(pending: PendingRequests, count: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (pending.pendingCount() != count) {
            check(System.nanoTime() < deadline) { "expected $count pending requests, have ${pending.pendingCount()}" }
            Thread.sleep(10)
        }
    }

    private fun readToEndInBackground(input: InputStream): CountDownLatch {
        val eof = CountDownLatch(1)
        thread(isDaemon = true) {
            val buffer = ByteArray(16)
            while (input.read(buffer, 0, buffer.size) != -1) {
                // keep reading
            }
            eof.countDown()
        }
        return eof
    }

    private fun stream(text: String) = ByteArrayInputStream(text.toByteArray())

    private fun request(id: Any, method: String): String {
        val idJson = if (id is String) "\"$id\"" else id.toString()
        return """{"jsonrpc":"2.0","id":$idJson,"method":"$method","params":{}}""" + "\n"
    }

    private fun notification(method: String) = """{"jsonrpc":"2.0","method":"$method"}""" + "\n"

    private fun response(id: Int) = """{"jsonrpc":"2.0","id":$id,"result":{}}""" + "\n"
}
