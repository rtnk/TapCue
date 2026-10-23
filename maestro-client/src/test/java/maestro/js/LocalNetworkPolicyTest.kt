package maestro.js

import com.google.common.truth.Truth.assertThat
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.UnknownHostException

/** STEP-03B, owner decision D-3: which addresses JavaScript `http.*` may reach. */
class LocalNetworkPolicyTest {

    @ParameterizedTest
    @ValueSource(
        strings = [
            "127.0.0.1", "127.255.255.254", "::1",
            "10.0.0.1", "10.255.255.255",
            "172.16.0.1", "172.31.255.255",
            "192.168.0.1", "192.168.255.255",
            "169.254.1.1",
            "fc00::1", "fd12:3456:789a::1",
            "fe80::1", "febf::1",
            "::ffff:192.168.1.1",
        ]
    )
    fun `loopback, private and link-local addresses are allowed`(address: String) {
        assertThat(LocalNetworkPolicy.isAllowed(InetAddress.getByName(address))).isTrue()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "8.8.8.8", "93.184.215.14", "1.1.1.1",
            "0.0.0.0", "::",
            "172.15.255.255", "172.32.0.1",
            "192.169.0.1", "11.0.0.1",
            "100.64.0.1", "224.0.0.1", "255.255.255.255",
            "2001:4860:4860::8888", "fec0::1", "fe00::1", "ff02::1",
            "::ffff:8.8.8.8", "64:ff9b::808:808",
        ]
    )
    fun `public, unspecified, multicast and other special addresses are blocked`(address: String) {
        assertThat(LocalNetworkPolicy.isAllowed(InetAddress.getByName(address))).isFalse()
    }

    @Test
    fun `a host name is allowed only if every address it resolves to is allowed`() {
        val policy = LocalNetworkPolicy(
            resolver = fakeResolver(
                "printer.lan" to listOf("192.168.1.50"),
                "dual.lan" to listOf("10.0.0.5", "fd00::5"),
                "mixed.test" to listOf("10.0.0.5", "8.8.8.8"),
                "example.com" to listOf("93.184.215.14"),
            )
        )

        policy.check("http://printer.lan/status".toHttpUrl())
        policy.check("http://dual.lan/".toHttpUrl())
        assertThat(policy.dns.lookup("printer.lan").map { it.hostAddress }).containsExactly("192.168.1.50")

        val mixed = assertThrows<NetworkPolicyViolation> { policy.check("http://mixed.test/".toHttpUrl()) }
        assertThat(mixed.message).contains("8.8.8.8")
        assertThat(mixed.message).contains(LocalNetworkPolicy.POLICY)

        assertThrows<NetworkPolicyViolation> { policy.check("https://example.com/".toHttpUrl()) }
        assertThrows<NetworkPolicyViolation> { policy.dns.lookup("example.com") }
    }

    @Test
    fun `address literals are checked as they are`() {
        // The default resolver (InetAddress.getAllByName) parses literals without a DNS query.
        val policy = LocalNetworkPolicy()

        policy.check("http://127.0.0.1:8080/".toHttpUrl())
        policy.check("http://[::1]:8080/".toHttpUrl())
        assertThrows<NetworkPolicyViolation> { policy.check("http://8.8.8.8/".toHttpUrl()) }
        assertThrows<NetworkPolicyViolation> { policy.check("http://[2001:4860:4860::8888]/".toHttpUrl()) }
    }

    @Test
    fun `a host that cannot be resolved is reported as a policy error`() {
        val policy = LocalNetworkPolicy(resolver = { host -> throw UnknownHostException(host) })

        val error = assertThrows<NetworkPolicyViolation> { policy.check("http://nowhere.invalid/".toHttpUrl()) }
        assertThat(error.message).contains("Cannot resolve 'nowhere.invalid'")
    }

    @Test
    fun `sockets refuse a public address before connecting and connect to an allowed one`() {
        val policy = LocalNetworkPolicy()

        val blocked = policy.socketFactory.createSocket()
        assertThrows<NetworkPolicyViolation> {
            blocked.connect(InetSocketAddress(InetAddress.getByName("93.184.215.14"), 80), 1_000)
        }
        assertThat(blocked.isConnected).isFalse()
        blocked.close()

        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            policy.socketFactory.createSocket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), server.localPort), 1_000)
                assertThat(socket.isConnected).isTrue()
            }
        }
    }

    private fun fakeResolver(vararg entries: Pair<String, List<String>>): (String) -> List<InetAddress> {
        val table = entries.toMap()
        return { host ->
            val addresses = table[host] ?: throw UnknownHostException(host)
            addresses.map { InetAddress.getByAddress(host, InetAddress.getByName(it).address) }
        }
    }
}
