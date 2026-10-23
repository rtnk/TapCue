package maestro.js

import maestro.utils.FileAccessScope
import maestro.utils.HttpUtils.toMultipartBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.graalvm.polyglot.HostAccess.Export
import org.graalvm.polyglot.proxy.ProxyObject
import java.net.Proxy

class GraalJsHttp(
    httpClient: OkHttpClient,
    private val scope: FileAccessScope = FileAccessScope.everything,
    private val networkPolicy: LocalNetworkPolicy = LocalNetworkPolicy(),
) {
    // Whatever client is passed in, scripts only get one that applies the policy on every hop
    // and connects directly: a proxy would make the connection on the script's behalf.
    private val httpClient = httpClient.newBuilder()
        .proxy(Proxy.NO_PROXY)
        .dns(networkPolicy.dns)
        .socketFactory(networkPolicy.socketFactory)
        .build()

    @Volatile
    private var currentScriptDir: java.io.File? = null

    fun setCurrentScriptDir(scriptDir: String?) {
      @Suppress("ForbiddenMethodCall") // Anchor directory, not the flow file path resolved through FileAccessScope.
      currentScriptDir = scriptDir?.let { java.io.File(it) }
    }

    @JvmOverloads
    @Export
    fun get(
        url: String,
        params: Map<String, Any>? = null,
    ): Any {
        return executeRequest(url, "GET", params)
    }

    @JvmOverloads
    @Export
    fun post(
        url: String,
        params: Map<String, Any>? = null,
    ): Any {
        return executeRequest(url, "POST", params)
    }

    @JvmOverloads
    @Export
    fun put(
        url: String,
        params: Map<String, Any>? = null,
    ): Any {
        return executeRequest(url, "PUT", params)
    }

    @JvmOverloads
    @Export
    fun delete(
        url: String,
        params: Map<String, Any>? = null,
    ): Any {
        return executeRequest(url, "DELETE", params)
    }

    @JvmOverloads
    @Export
    fun request(
        url: String,
        params: Map<String, Any>? = null,
    ): Any {
        val method = params?.get("method") as? String ?: "GET"
        return executeRequest(
            url,
            method,
            params,
        )
    }

    private fun executeRequest(
        url: String,
        method: String,
        params: Map<String, Any>?,
    ): Any {
        val httpUrl = url.toHttpUrl()
        networkPolicy.check(httpUrl)

        val requestBuilder = Request.Builder()
            .url(httpUrl)

        val body = params?.get("body") as? String
        val multipartForm = params?.get("multipartForm") as? Map<*, *>

        if (multipartForm == null) {
            requestBuilder.method(method, body?.toRequestBody())
        } else {
            requestBuilder.method(method, multipartForm.toMultipartBody(currentScriptDir, scope))
        }

        val headers: Map<*, *> = params?.get("headers") as? Map<*, *> ?: emptyMap<Any, Any>()

        headers.forEach { (key, value) ->
            requestBuilder.addHeader(key.toString(), value.toString())
        }

        val request = requestBuilder.build()

        val response = httpClient
            .newCall(request)
            .execute()

        return ProxyObject.fromMap(mapOf(
            "ok" to response.isSuccessful,
            "status" to response.code,
            "body" to response.body?.string(),
            "headers" to convertHeaders(response.headers)
        ))
    }

    private fun convertHeaders(headers: Headers): ProxyObject {
        val headersMap = headers.toMultimap().mapValues { (_, values) ->
            values.joinToString(",")
        }
        return ProxyObject.fromMap(headersMap)
    }

}
