package com.micoh.thethirdbowl.data

import io.github.jan.supabase.postgrest.postgrest
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
        val sections = client
            .postgrest
            .rpc(
                function = "list_cat_capsule_sections",
                parameters = buildJsonObject {
                    put("p_target_cat_id", catId)
                },
            )
            .decodeList<CapsuleSectionRow>()

        return sections.toCareCoreDraft()
    }

    suspend fun saveCareCore(catId: String, draft: CareCoreDraft): CareCoreDraft {
        val trimmed = draft.trimmed()
        client
            .postgrest
            .rpc(
                function = "upsert_cat_capsule_sections",
                parameters = buildJsonObject {
                    put("p_target_cat_id", catId)
                    put(
                        "p_care_core",
                        buildJsonObject {
                            put("feeding_and_water", trimmed.feedingAndWater)
                            put("hiding_places", trimmed.hidingPlaces)
                            put("do_not_do", trimmed.doNotDo)
                        },
                    )
                    put(
                        "p_home_access",
                        buildJsonObject {
                            put("entry_instructions", trimmed.entryInstructions)
                            put("key_location", trimmed.keyLocation)
                            put("safe_room", trimmed.safeRoom)
                        },
                    )
                    put(
                        "p_medical",
                        buildJsonObject {
                            put("medications", trimmed.medications)
                            put("vet_info", trimmed.vetInfo)
                            put("medical_warnings", trimmed.medicalWarnings)
                        },
                    )
                },
            )
            .decodeList<CapsuleSectionRow>()

        return trimmed
    }
}

private fun List<CapsuleSectionRow>.toCareCoreDraft(): CareCoreDraft {
    val careCoreContent = firstOrNull { it.scope == "CARE_CORE" }?.contentJson
    val homeAccessContent = firstOrNull { it.scope == "HOME_ACCESS" }?.contentJson
    val medicalContent = firstOrNull { it.scope == "MEDICAL" }?.contentJson
    return CareCoreDraft(
        feedingAndWater = careCoreContent.stringValue("feeding_and_water"),
        hidingPlaces = careCoreContent.stringValue("hiding_places"),
        doNotDo = careCoreContent.stringValue("do_not_do"),
        entryInstructions = homeAccessContent.stringValue("entry_instructions"),
        keyLocation = homeAccessContent.stringValue("key_location"),
        safeRoom = homeAccessContent.stringValue("safe_room"),
        medications = medicalContent.stringValue("medications"),
        vetInfo = medicalContent.stringValue("vet_info"),
        medicalWarnings = medicalContent.stringValue("medical_warnings"),
    )
}

@Serializable
data class CareCoreDraft(
    val feedingAndWater: String = "",
    val hidingPlaces: String = "",
    val doNotDo: String = "",
    val entryInstructions: String = "",
    val keyLocation: String = "",
    val safeRoom: String = "",
    val medications: String = "",
    val vetInfo: String = "",
    val medicalWarnings: String = "",
) {
    fun trimmed(): CareCoreDraft {
        return copy(
            feedingAndWater = feedingAndWater.trim(),
            hidingPlaces = hidingPlaces.trim(),
            doNotDo = doNotDo.trim(),
            entryInstructions = entryInstructions.trim(),
            keyLocation = keyLocation.trim(),
            safeRoom = safeRoom.trim(),
            medications = medications.trim(),
            vetInfo = vetInfo.trim(),
            medicalWarnings = medicalWarnings.trim(),
        )
    }
}

@Serializable
private data class CapsuleSectionRow(
    val id: String,
    @SerialName("capsule_id")
    val capsuleId: String,
    val scope: String,
    @SerialName("content_json")
    val contentJson: JsonObject,
)

private fun JsonObject?.stringValue(key: String): String {
    return this?.get(key)?.jsonPrimitive?.contentOrNull.orEmpty()
}
