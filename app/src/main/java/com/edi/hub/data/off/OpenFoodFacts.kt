package com.edi.hub.data.off

import com.edi.hub.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What Hub takes from a lookup. Everything else on the record is somebody else's problem. */
data class OffProduct(
    val barcode: String,
    val name: String,
    val brand: String?,
    val imageUrl: String?,
)

/**
 * One endpoint, one request, and a two-second ceiling on the whole thing. A miss and a timeout are
 * the same answer here — both mean "ask the user" — and neither is an error the user ever sees.
 */
@Singleton
class OpenFoodFacts @Inject constructor() {

    private val client = OkHttpClient.Builder()
        // callTimeout covers connect, write, read and redirects together, which is the number that
        // matters: the user is standing in a shop waiting for the next screen.
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lookUp(barcode: String): OffProduct? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$ENDPOINT/$barcode.json?fields=product_name,brands,image_url")
            // Open Food Facts asks for a descriptive agent with a contact. The contact comes from a
            // Gradle property so no personal address is committed.
            .header("User-Agent", "Hub/${BuildConfig.VERSION_NAME} (${BuildConfig.OFF_CONTACT})")
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body.string()
                val payload = json.decodeFromString<OffResponse>(body)
                // status 0 is "no such product", which is a miss rather than a failure.
                if (payload.status != 1) return@use null
                val product = payload.product ?: return@use null
                val name = product.name?.takeIf(String::isNotBlank) ?: return@use null
                OffProduct(
                    barcode = barcode,
                    name = name.trim(),
                    brand = product.brands?.split(",")?.firstOrNull()?.trim()?.takeIf(String::isNotBlank),
                    imageUrl = product.imageUrl?.takeIf(String::isNotBlank),
                )
            }
        }.getOrNull()
    }

    @Serializable
    private data class OffResponse(
        val status: Int = 0,
        val product: OffPayload? = null,
    )

    @Serializable
    private data class OffPayload(
        @SerialName("product_name") val name: String? = null,
        val brands: String? = null,
        @SerialName("image_url") val imageUrl: String? = null,
    )

    private companion object {
        const val ENDPOINT = "https://world.openfoodfacts.org/api/v2/product"
        const val TIMEOUT_SECONDS = 2L
    }
}
