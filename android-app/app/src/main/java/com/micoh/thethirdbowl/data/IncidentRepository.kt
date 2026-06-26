package com.micoh.thethirdbowl.data

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class IncidentRepository {
    private val client = SupabaseProvider.client

    suspend fun getActiveIncident(catId: String): IncidentRow? {
        return client
            .postgrest
            .rpc(
                function = "get_active_incident_for_cat",
                parameters = buildJsonObject {
                    put("target_cat_id", catId)
                },
            )
            .decodeList<IncidentRow>()
            .firstOrNull()
    }
}

@Serializable
data class IncidentRow(
    @SerialName("incident_id")
    val incidentId: String,
    @SerialName("cat_id")
    val catId: String,
    @SerialName("cat_name")
    val catName: String,
    @SerialName("incident_state")
    val incidentState: String,
    @SerialName("assignment_id")
    val assignmentId: String,
    @SerialName("assignment_state")
    val assignmentState: String,
    @SerialName("assigned_relationship_label")
    val assignedRelationshipLabel: String,
    @SerialName("activated_at")
    val activatedAt: String,
    @SerialName("response_deadline_at")
    val responseDeadlineAt: String,
    @SerialName("cat_reached_at")
    val catReachedAt: String? = null,
)
