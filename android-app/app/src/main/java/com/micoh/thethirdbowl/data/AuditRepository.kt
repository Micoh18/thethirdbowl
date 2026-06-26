package com.micoh.thethirdbowl.data

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuditRepository {
    private val client = SupabaseProvider.client

    suspend fun listCatEvents(catId: String): List<AuditEventRow> {
        return client
            .postgrest
            .rpc(
                function = "list_cat_audit_events",
                parameters = buildJsonObject {
                    put("target_cat_id", catId)
                },
            )
            .decodeList<AuditEventRow>()
    }
}

@Serializable
data class AuditEventRow(
    val id: String,
    @SerialName("occurred_at")
    val occurredAt: String,
    @SerialName("actor_type")
    val actorType: String,
    @SerialName("event_type")
    val eventType: String,
    @SerialName("target_type")
    val targetType: String,
    val outcome: String,
)
