package com.micoh.thethirdbowl.data

import com.micoh.thethirdbowl.BuildConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

class PlanRepository {
    private val client = SupabaseProvider.client
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getOrCreatePlan(catId: String): PlanRow {
        return client
            .postgrest
            .rpc(
                function = "get_or_create_plan",
                parameters = buildJsonObject {
                    put("target_cat_id", catId)
                },
            )
            .decodeList<PlanRow>()
            .first()
    }

    suspend fun armPlan(catId: String): PlanRow {
        return client
            .postgrest
            .rpc(
                function = "activate_continuity_plan",
                parameters = buildJsonObject {
                    put("target_cat_id", catId)
                },
            )
            .decodeList<PlanRow>()
            .first()
    }

    suspend fun completeCheckIn(catId: String): CheckInResult {
        return client
            .postgrest
            .rpc(
                function = "complete_continuity_check_in",
                parameters = buildJsonObject {
                    put("target_cat_id", catId)
                },
            )
            .decodeList<CheckInResult>()
            .first()
    }

    suspend fun triggerDeveloperMissedCheckIn(catId: String): DeveloperProcessorResult {
        return client
            .postgrest
            .rpc(
                function = "trigger_developer_missed_check_in",
                parameters = buildJsonObject {
                    put("target_cat_id", catId)
                },
            )
            .decodeList<DeveloperProcessorResult>()
            .first()
    }

    suspend fun sendPendingIncidentEmails(maxEmails: Int = 10): EmailDrainResult = withContext(Dispatchers.IO) {
        val accessToken = client.auth.currentAccessTokenOrNull()
            ?: error("signed in session required")
        val endpoint = URL("${BuildConfig.SUPABASE_URL}/functions/v1/process-due-check-ins")
        val body = """{"sendPendingOnly":true,"maxEmails":$maxEmails}"""

        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
        }

        connection.outputStream.bufferedWriter().use { writer ->
            writer.write(body)
        }

        val responseCode = connection.responseCode
        val responseBody = (if (responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        })?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()

        connection.disconnect()

        if (responseCode !in 200..299) {
            error("email processor failed: HTTP $responseCode $responseBody")
        }

        json.decodeFromString<EmailDrainEnvelope>(responseBody).emails
    }
}

@Serializable
data class PlanRow(
    val id: String,
    @SerialName("cat_id")
    val catId: String,
    val status: String,
    val timezone: String,
    @SerialName("schedule_type")
    val scheduleType: String,
    @SerialName("grace_period_minutes")
    val gracePeriodMinutes: Int,
    @SerialName("next_check_in_at")
    val nextCheckInAt: String? = null,
)

@Serializable
data class CheckInResult(
    @SerialName("cycle_id")
    val cycleId: String,
    @SerialName("plan_id")
    val planId: String,
    @SerialName("cat_id")
    val catId: String,
    @SerialName("completed_at")
    val completedAt: String,
    @SerialName("next_check_in_at")
    val nextCheckInAt: String,
)

@Serializable
data class DeveloperProcessorResult(
    @SerialName("processed_plans")
    val processedPlans: Int,
    @SerialName("incidents_created")
    val incidentsCreated: Int,
    @SerialName("assignments_created")
    val assignmentsCreated: Int,
)

@Serializable
data class EmailDrainEnvelope(
    val emails: EmailDrainResult = EmailDrainResult(),
)

@Serializable
data class EmailDrainResult(
    val configured: Boolean = false,
    val sent: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
)
