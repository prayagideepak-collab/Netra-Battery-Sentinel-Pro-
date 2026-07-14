package com.example.service

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<GeminiContent>
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "parts") val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>?
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent?
)

interface GeminiApi {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: GeminiApi = retrofit.create(GeminiApi::class.java)

    suspend fun getBatteryRecommendations(
        percentage: Int,
        temperature: Float,
        voltage: Int,
        healthPct: Int,
        healthGrade: String,
        isCharging: Boolean,
        chargingType: String,
        watt: Float,
        cycleCount: Int,
        sessionsCount: Int,
        abnormalStandbyDrain: Boolean,
        abnormalTempSpike: Boolean
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "Unable to access Gemini AI: API Key is not set or invalid in Secrets. Please configure your key in the Secrets panel."
        }

        val prompt = """
            You are Netra AI Battery Sentinel Pro Advisor, a high-fidelity offline/online intelligence and protection advisor for Android devices.
            Please analyze the following diagnostic data of this device and provide a concise, professional, and visually engaging (with Material 3 styled emojis and clear bullet points) Battery Health & Protection recommendation report.
            
            DIAGNOSTIC METRICS:
            - Battery Level: ${percentage}% (${if (isCharging) "Charging via $chargingType at ${watt}W" else "Discharging"})
            - Battery Temperature: ${temperature}°C (${if (temperature >= 40) "HIGH TEMP STRESS" else "Normal range"})
            - Current Voltage: ${voltage} mV
            - Health Capacity: ${healthPct}% (Grade: $healthGrade)
            - Registered Charge Cycles: ${if (cycleCount >= 0) cycleCount else "N/A"}
            - Total Tracked Sessions: $sessionsCount
            - Standby Drain Pattern: ${if (abnormalStandbyDrain) "High Abnormal Drain Detected" else "Excellent (Minimal Idle Drain)"}
            - Recent Temperature Spike: ${if (abnormalTempSpike) "Yes (Temperatures reached >41°C)" else "No"}

            FORMAT GUIDELINE:
            1. **Netra AI Protection Assessment**: Summarize the current condition in 2 sentences in Hindi-English (Hinglish/Hindi mixed with English, friendly, professional and expert, fitting the Netra Battery Sentinel tone).
            2. **Key Health Risks**: Identify 2 specific risks based on the values above (such as thermal degradation, cycle speed wear, voltage ripple stress).
            3. **Sentinel Action Plan**: Provide 3 precise, actionable steps the user should take right now to prolong their battery lifespan (e.g., unplug, limit charge to 80%, cool device, disable heavy apps).
            
            Be very concise, precise, and direct. Use elegant Material 3 style formatting (bold, clean bullets, no unnecessary intro or outro fluff).
        """.trimIndent()

        return try {
            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = prompt))
                    )
                )
            )
            val response = api.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "No advice generated. Please try again later."
        } catch (e: Exception) {
            "Failed to contact Gemini AI Advisor: ${e.localizedMessage}. Check your internet connection or verification status."
        }
    }
}
