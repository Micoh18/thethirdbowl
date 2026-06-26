package com.micoh.thethirdbowl.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class CatRepository {
    private val client = SupabaseProvider.client

    suspend fun listMyCats(): List<CatRow> {
        return client
            .from("cats")
            .select()
            .decodeList<CatRow>()
    }

    suspend fun createCat(name: String): CatRow {
        return client
            .postgrest
            .rpc(
                function = "create_cat",
                parameters = buildJsonObject {
                    put("cat_name", name.trim())
                },
            ) {
                select()
            }
            .decodeSingle<CatRow>()
    }
}

@Serializable
data class CatRow(
    val id: String,
    val name: String,
    val status: String,
    @SerialName("primary_caregiver_user_id")
    val primaryCaregiverUserId: String,
)
