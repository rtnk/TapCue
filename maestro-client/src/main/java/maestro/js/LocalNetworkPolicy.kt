package maestro.js

import okhttp3.Dns
import okhttp3.HttpUrl
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketAddress
import java.net.UnknownHostException
import javax.net.SocketFactory

/**
 * Thrown when a flow script asks `http.*` for an address outside [LocalNetworkPolicy].
 * It is an [IOException] so that OkHttp passes it through unchanged when it is raised
 * from [LocalNetworkPolicy.dns] or a socket made by [LocalNetworkPolicy.socketFactory].
 */
class NetworkPolicyViolation(message: String) : IOException(message)

/**
 * Where JavaScript `http.*` in a flow may connect (owner decision D-3): loopback, private
 * networks (RFC 1918, `fc00::/7`) and link-local addresses only. A host name is resolved
 * first and every address it resolves to must be allowed. The rule is applied three times:
 * before the request is handed to OkHttp, in OkHttp's DNS lookup (every redirect hop) and
 * when a socket connects (address literals, which OkHttp does not resolve). A request the
 * policy rejects never opens a connection.
 */
class LocalNetworkPolicy(
    private val resolver: (String) -> List<InetAddress> = { host -> InetAddress.getAllByName(host).toList() },
) {

    /** Checks [url] before any connection is made; throws [NetworkPolicyViolation] if it is not allowed. */
    fun check(url: HttpUrl) {
        resolveAllowed(url.host, url.toString())
    }

    val dns: Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> = resolveAllowed(hostname, hostname)
    }

    val socketFactory: SocketFactory = object : SocketFactory() {
        override fun createSocket(): Socket = GuardedSocket()

        override fun createSocket(host: String, port: Int): Socket =
            GuardedSocket().apply { connect(InetSocketAddress(host, port)) }

        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
            GuardedSocket().apply {
                bind(InetSocketAddress(localHost, localPort))
                connect(InetSocketAddress(host, port))
            }

        override fun createSocket(host: InetAddress, port: Int): Socket =
            GuardedSocket().apply { connect(InetSocketAddress(host, port)) }

        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
            GuardedSocket().apply {
                bind(InetSocketAddress(localAddress, localPort))
                connect(InetSocketAddress(address, port))
            }
    }

    // A plain Socket() would ask the default ProxySelector for a SOCKS proxy on connect; the policy
    // is about the address this machine connects to, so the socket always connects directly.
    private inner class GuardedSocket : Socket(Proxy.NO_PROXY) {
        override fun connect(endpoint: SocketAddress?, timeout: Int) {
            val address = (endpoint as? InetSocketAddress)?.address
                ?: throw NetworkPolicyViolation("Refusing to connect to unresolved address $endpoint.")
            if (!isAllowed(address)) {
                throw violation(address.hostAddress, address)
            }
            super.connect(endpoint, timeout)
        }
    }

    private fun resolveAllowed(host: String, target: String): List<InetAddress> {
        val addresses = try {
            resolver(host.removeSurrounding("[", "]"))
        } catch (e: UnknownHostException) {
            throw NetworkPolicyViolation(
                "Cannot resolve '$host' for $target, so it cannot be checked against the local network policy. $POLICY"
            )
        }
        if (addresses.isEmpty()) {
            throw NetworkPolicyViolation("'$host' resolves to no address. $POLICY")
        }
        addresses.firstOrNull { !isAllowed(it) }?.let { throw violation(target, it) }
        return addresses
    }

    private fun violation(target: String, address: InetAddress) = NetworkPolicyViolation(
        "Blocked request to $target: ${address.hostAddress} is not a loopback, private or link-local address. $POLICY"
    )

    companion object {
        const val POLICY = "Maestro Local lets JavaScript http requests reach only loopback, private and link-local " +
            "addresses (127.0.0.0/8, ::1, 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, fc00::/7, 169.254.0.0/16, fe80::/10)."

        fun isAllowed(address: InetAddress): Boolean {
            val b = address.address.map { it.toInt() and 0xFF }
            return when (address) {
                is Inet4Address -> b[0] == 127 ||
                    b[0] == 10 ||
                    (b[0] == 172 && b[1] in 16..31) ||
                    (b[0] == 192 && b[1] == 168) ||
                    (b[0] == 169 && b[1] == 254)
                is Inet6Address -> address.isLoopbackAddress ||
                    (b[0] and 0xFE) == 0xFC ||
                    (b[0] == 0xFE && (b[1] and 0xC0) == 0x80)
                else -> false
            }
        }
    }
}
