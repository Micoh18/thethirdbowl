package com.micoh.thethirdbowl.data

import com.micoh.thethirdbowl.BuildConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

class InvitationRepository {
    private val client = SupabaseProvider.client
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listInvitations(catId: String): List<InvitationRow> {
        return client
            .postgrest
            .rpc(
                function = "list_invitation_records",
                parameters = buildJsonObject {
                    put("target_cat_id", catId)
                },
            )
            .decodeList<InvitationRow>()
    }

    suspend fun createInvitation(
        catId: String,
        email: String,
        relationshipLabel: String,
        proposedRole: String,
        proposedScopes: List<String>,
    ): InvitationRow {
        return client
            .postgrest
            .rpc(
                function = "create_invitation_record",
                parameters = buildJsonObject {
                    put("p_target_cat_id", catId)
                    put("p_invited_email", email.trim())
                    put("p_relationship_label", relationshipLabel.trim())
                    put("p_proposed_role", proposedRole)
                    put(
                        "p_proposed_scopes",
                        JsonArray(proposedScopes.map { JsonPrimitive(it) }),
                    )
                },
            )
            .decodeList<InvitationRow>()
            .first()
    }

    suspend fun sendInvitationEmail(invitationId: String, email: String): EmailDrainResult = withContext(Dispatchers.IO) {
        val accessToken = client.auth.currentAccessTokenOrNull()
            ?: error("signed in session required")
        val endpoint = URL("${BuildConfig.SUPABASE_URL}/functions/v1/process-due-check-ins")
        val body = buildJsonObject {
            put("sendInvitation", true)
            put("invitationId", invitationId)
            put("invitedEmail", email.trim())
        }.toString()

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
            error("invitation email failed: HTTP $responseCode $responseBody")
        }

        json.decodeFromString<EmailDrainEnvelope>(responseBody).emails
    }

    suspend fun removeCareCirclePerson(invitationId: String): RemovedCareCirclePerson {
        return client
            .postgrest
            .rpc(
                function = "remove_care_circle_person",
                parameters = buildJsonObject {
                    put("p_invitation_id", invitationId)
                },
            )
            .decodeList<RemovedCareCirclePerson>()
            .first()
    }
}

@Serializable
data class InvitationRow(
    val id: String,
    @SerialName("cat_id")
    val catId: String,
    @SerialName("invited_email_masked")
    val invitedEmailMasked: String = "",
    @SerialName("relationship_label")
    val relationshipLabel: String,
    val status: String,
    @SerialName("proposed_role")
    val proposedRole: String = "",
    @SerialName("proposed_scopes")
    val proposedScopes: List<String> = emptyList(),
    @SerialName("expires_at")
    val expiresAt: String,
    @SerialName("created_at")
    val createdAt: String,
)

@Serializable
data class RemovedCareCirclePerson(
    @SerialName("invitation_id")
    val invitationId: String,
    @SerialName("cat_id")
    val catId: String,
    @SerialName("removed_status")
    val removedStatus: String,
)
