package maestro.cli.api

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import maestro.cli.CliError
import maestro.cli.analytics.Analytics
import maestro.cli.insights.AnalysisDebugFiles
import maestro.cli.util.EnvUtils
import maestro.utils.HttpClient
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import kotlin.time.Duration.Companion.minutes

class ApiClient(
    private val baseUrl: String,
) {

    private val client = HttpClient.build(
        name = "ApiClient",
        readTimeout = 5.minutes,
        writeTimeout = 5.minutes,
        protocols = listOf(Protocol.HTTP_1_1),
        interceptors = listOf(SystemInformationInterceptor()),
    )

    fun sendErrorReport(exception: Exception, commandLine: String) {
        post<Unit>(
            path = "/maestro/error",
            body = mapOf(
                "exception" to exception,
                "commandLine" to commandLine
            )
        )
    }

    fun sendScreenReport(maxDepth: Int) {
        post<Unit>(
            path = "/maestro/screen",
            body = mapOf(
                "maxDepth" to maxDepth
            )
        )
    }

    fun getLatestCliVersion(): CliVersion {
        val request = Request.Builder()
            .header("X-FRESH-INSTALL", if (!Analytics.hasRunBefore) "true" else "false")
            .url("$baseUrl/v2/maestro/version")
            .get()
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw ApiException(statusCode = null)
        }

        response.use {
            if (!response.isSuccessful) {
                throw ApiException(
                    statusCode = response.code
                )
            }

            return JSON.readValue(response.body?.bytes(), CliVersion::class.java)
        }
    }

    private inline fun <reified T> post(path: String, body: Any): Result<T, Response> {
        val bodyBytes = JSON.writeValueAsBytes(body)
        val request = Request.Builder()
            .post(bodyBytes.toRequestBody("application/json".toMediaType()))
            .url("$baseUrl$path")
            .build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) return Err(response)
        if (Unit is T) return Ok(Unit)
        val parsed = JSON.readValue(response.body?.bytes(), T::class.java)
        return Ok(parsed)
    }

    fun analyze(
        authToken: String,
        debugFiles: AnalysisDebugFiles,
    ): AnalyzeResponse {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = JSON.writeValueAsString(debugFiles).toRequestBody(mediaType)

        val url = "$baseUrl/v2/analyze"

        val request = Request.Builder()
            .header("Authorization", "Bearer $authToken")
            .url(url)
            .post(body)
            .build()

        val response = client.newCall(request).execute()

        response.use {
            if (!response.isSuccessful) {
                val errorMessage = response.body?.string().takeIf { it?.isNotEmpty() == true } ?: "Unknown"
                throw CliError("Analyze request failed (${response.code}): $errorMessage")
            }

            val parsed = JSON.readValue(response.body?.bytes(), AnalyzeResponse::class.java)

            return parsed;
        }
    }

    data class ApiException(
        val statusCode: Int?,
    ) : Exception("Request failed. Status code: $statusCode")

    companion object {
        private val JSON = jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
}

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

class SystemInformationInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val newRequest = chain.request().newBuilder()
            .header("X-UUID", Analytics.uuid)
            .header("X-VERSION", EnvUtils.getVersion().toString())
            .header("X-OS", EnvUtils.OS_NAME)
            .header("X-OSARCH", EnvUtils.OS_ARCH)
            .build()

        return chain.proceed(newRequest)
    }
}

data class Insight(
    val category: String,
    val reasoning: String,
)

class AnalyzeResponse(
    val htmlReport: String?,
    val output: String,
    val insights: List<Insight>
)
