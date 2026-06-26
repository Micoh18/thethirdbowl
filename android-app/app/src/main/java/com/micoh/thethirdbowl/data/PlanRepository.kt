package com.micoh.thethirdbowl.data

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PlanRepository {
    private val client = SupabaseProvider.client

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
                function = "arm_demo_plan",
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
                function = "complete_demo_check_in",
                parameters = buildJsonObject {
                    put("target_cat_id", catId)
                },
            )
            .decodeList<CheckInResult>()
            .first()
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
