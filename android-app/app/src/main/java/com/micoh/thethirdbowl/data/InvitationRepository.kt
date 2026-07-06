package com.micoh.thethirdbowl.data

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class InvitationRepository {
    private val client = SupabaseProvider.client

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
