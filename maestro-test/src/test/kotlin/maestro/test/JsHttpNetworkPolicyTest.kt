package maestro.test

import com.google.common.truth.Truth.assertThat
import maestro.js.GraalJsEngine
import maestro.js.JsEvaluationException
import maestro.js.LocalNetworkPolicy
import maestro.js.NetworkPolicyViolation
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.UnknownHostException

/**
 * STEP-03B, owner decision D-3: JavaScript `http.*` in a flow reaches loopback and private
 * networks only. A request outside the policy fails the script with a clear error and no
 * connection is attempted: every OkHttp call and connection attempt is recorded, and the
 * default [ProxySelector] records (and fails) anything that would try to go through a proxy.
 */
class JsHttpNetworkPolicyTest {

    private val server = MockWebServer()
    private val calls = mutableListOf<String>()
    private val connects = mutableListOf<InetSocketAddress>()
    private val connected = mutableListOf<InetSocketAddress>()
    private val connectFailures = mutableListOf<IOException>()
    private val proxied = mutableListOf<URI>()
    private var originalProxySelector: ProxySelector? = null

    private val resolver: (String) -> List<InetAddress> = { host ->
        val table = mapOf(
            "example.com" to listOf("93.184.215.14"),
            "intranet.test" to listOf("127.0.0.1"),
            "mixed.test" to listOf("127.0.0.1", "8.8.8.8"),
        )
        val addresses = table[host] ?: listOf(InetAddress.getByName(host).hostAddress)
        addresses.map { InetAddress.getByAddress(host, InetAddress.getByName(it).address) }
    }

    private lateinit var engine: GraalJsEngine

    @BeforeEach
    fun setUp() {
        server.start(InetAddress.getByName("127.0.0.1"), 0)

        originalProxySelector = ProxySelector.getDefault()
        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI): List<Proxy> {
                synchronized(proxied) { proxied.add(uri) }
                throw IOException("Network access is not allowed in this test: $uri")
            }

            override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) = Unit
        })

        val httpClient = OkHttpClient.Builder()
            .eventListener(object : EventListener() {
                override fun callStart(call: Call) {
                    synchronized(calls) { calls.add(call.request().url.toString()) }
                }

                override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
                    synchronized(connects) { connects.add(inetSocketAddress) }
                }

                override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
                    synchronized(connected) { connected.add(inetSocketAddress) }
                }

                override fun connectFailed(
                    call: Call,
                    inetSocketAddress: InetSocketAddress,
                    proxy: Proxy,
                    protocol: Protocol?,
                    ioe: IOException,
                ) {
                    synchronized(connectFailures) { connectFailures.add(ioe) }
                }
            })
            .build()
        engine = GraalJsEngine(httpClient = httpClient, networkPolicy = LocalNetworkPolicy(resolver))
    }

    @AfterEach
    fun tearDown() {
        engine.close()
        ProxySelector.setDefault(originalProxySelector)
        server.shutdown()
    }

    @Test
    fun `a request to loopback works`() {
        server.enqueue(MockResponse().setBody("hello"))

        val body = engine.evaluateScript("http.get('http://127.0.0.1:${server.port}/ping').body").toString()

        assertThat(body).isEqualTo("hello")
        assertThat(server.takeRequest().path).isEqualTo("/ping")
        assertThat(proxied).isEmpty()
    }

    @Test
    fun `a request to a public host fails before any connection`() {
        val error = assertThrows<JsEvaluationException> {
            engine.evaluateScript("http.get('https://example.com')")
        }

        assertThat(error.message).contains("Blocked request to https://example.com/")
        assertThat(error.message).contains("93.184.215.14 is not a loopback, private or link-local address")
        assertThat(error.error.isHostException).isTrue()
        assertNoAttempts()
    }

    @Test
    fun `every http method is checked`() {
        listOf("get", "post", "put", "delete", "request").forEach { method ->
            assertThrows<JsEvaluationException>(method) {
                engine.evaluateScript("http.$method('http://8.8.8.8/', { body: 'x' })")
            }
        }
        assertNoAttempts()
    }

    @Test
    fun `a host name that resolves to a private address works`() {
        server.enqueue(MockResponse().setBody("intranet"))

        val body = engine.evaluateScript("http.get('http://intranet.test:${server.port}/').body").toString()

        assertThat(body).isEqualTo("intranet")
        assertThat(server.takeRequest().getHeader("Host")).isEqualTo("intranet.test:${server.port}")
    }

    @Test
    fun `a host name with any public address is blocked`() {
        val error = assertThrows<JsEvaluationException> {
            engine.evaluateScript("http.get('http://mixed.test:${server.port}/')")
        }

        assertThat(error.message).contains("8.8.8.8")
        assertNoAttempts()
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `a host that cannot be resolved is reported`() {
        val engine = GraalJsEngine(networkPolicy = LocalNetworkPolicy { host -> throw UnknownHostException(host) })

        val error = assertThrows<JsEvaluationException> {
            engine.use { it.evaluateScript("http.get('http://nowhere.invalid/')") }
        }

        assertThat(error.message).contains("Cannot resolve 'nowhere.invalid'")
    }

    @Test
    fun `a redirect to a public address literal is blocked before connecting to it`() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "http://93.184.215.14/next"))

        val error = assertThrows<JsEvaluationException> {
            engine.evaluateScript("http.get('http://127.0.0.1:${server.port}/start')")
        }

        assertThat(error.message).contains("93.184.215.14")
        assertThat(server.requestCount).isEqualTo(1)
        // OkHttp does not resolve literals, so it gets as far as asking for a socket to 93.184.215.14;
        // the socket refuses before connecting, and the only established connection is the local one.
        assertThat(connected.map { it.address.hostAddress }).containsExactly("127.0.0.1")
        assertThat(connectFailures).isNotEmpty()
        assertThat(connectFailures.all { it is NetworkPolicyViolation }).isTrue()
        assertThat(proxied).isEmpty()
    }

    @Test
    fun `a redirect to a public host name is blocked`() {
        server.enqueue(MockResponse().setResponseCode(301).setHeader("Location", "https://example.com/"))

        val error = assertThrows<JsEvaluationException> {
            engine.evaluateScript("http.get('http://127.0.0.1:${server.port}/start')")
        }

        assertThat(error.message).contains("93.184.215.14")
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(connects.map { it.address.hostAddress }).containsExactly("127.0.0.1")
        assertThat(proxied).isEmpty()
    }

    @Test
    fun `a redirect within the local network is followed`() {
        server.enqueue(
            MockResponse().setResponseCode(302).setHeader("Location", "http://intranet.test:${server.port}/next")
        )
        server.enqueue(MockResponse().setBody("followed"))

        val body = engine.evaluateScript("http.get('http://127.0.0.1:${server.port}/start').body").toString()

        assertThat(body).isEqualTo("followed")
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `the default engine applies the policy as well`() {
        val engine = GraalJsEngine()

        val error = assertThrows<JsEvaluationException> {
            engine.use { it.evaluateScript("http.get('http://1.1.1.1/')") }
        }

        assertThat(error.message).contains(LocalNetworkPolicy.POLICY)
        assertThat(proxied).isEmpty()
    }

    private fun assertNoAttempts() {
        assertThat(calls).isEmpty()
        assertThat(connects).isEmpty()
        assertThat(proxied).isEmpty()
    }
}
