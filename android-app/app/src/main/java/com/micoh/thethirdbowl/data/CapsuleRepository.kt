package com.micoh.thethirdbowl.data

import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class CapsuleRepository {
    private val client = SupabaseProvider.client

    suspend fun loadCareCore(catId: String): CareCoreDraft {
        val capsule = client
            .from("capsules")
            .select {
                filter {
                    eq("cat_id", catId)
                }
            }
            .decodeSingle<CapsuleRow>()

        val sections = client
            .from("capsule_sections")
            .select {
                filter {
                    eq("capsule_id", capsule.id)
                    eq("scope", "CARE_CORE")
                }
            }
            .decodeList<CapsuleSectionRow>()

        val content = sections.firstOrNull()?.contentJson ?: return CareCoreDraft()
        return CareCoreDraft(
            feedingAndWater = content.stringValue("feeding_and_water"),
            hidingPlaces = content.stringValue("hiding_places"),
            doNotDo = content.stringValue("do_not_do"),
        )
    }

    suspend fun saveCareCore(catId: String, draft: CareCoreDraft): CareCoreDraft {
        val capsule = client
            .from("capsules")
            .select {
                filter {
                    eq("cat_id", catId)
                }
            }
            .decodeSingle<CapsuleRow>()

        val request = UpsertCapsuleSectionRequest(
            capsuleId = capsule.id,
            scope = "CARE_CORE",
            schemaVersion = 1,
            contentJson = buildJsonObject {
                put("feeding_and_water", draft.feedingAndWater.trim())
                put("hiding_places", draft.hidingPlaces.trim())
                put("do_not_do", draft.doNotDo.trim())
            },
        )

        client
            .from("capsule_sections")
            .upsert(request) {
                onConflict = "capsule_id,scope"
            }

        return draft.trimmed()
    }
}

@Serializable
data class CareCoreDraft(
    val feedingAndWater: String = "",
    val hidingPlaces: String = "",
    val doNotDo: String = "",
) {
    fun trimmed(): CareCoreDraft {
        return copy(
            feedingAndWater = feedingAndWater.trim(),
            hidingPlaces = hidingPlaces.trim(),
            doNotDo = doNotDo.trim(),
        )
    }
}

@Serializable
private data class CapsuleRow(
    val id: String,
    @SerialName("cat_id")
    val catId: String,
)

@Serializable
private data class CapsuleSectionRow(
    val id: String,
    @SerialName("capsule_id")
    val capsuleId: String,
    val scope: String,
    @SerialName("content_json")
    val contentJson: JsonObject,
)

@Serializable
private data class UpsertCapsuleSectionRequest(
    @SerialName("capsule_id")
    val capsuleId: String,
    val scope: String,
    @SerialName("schema_version")
    val schemaVersion: Int,
    @SerialName("content_json")
    val contentJson: JsonObject,
)

private fun JsonObject.stringValue(key: String): String {
    return this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
}
