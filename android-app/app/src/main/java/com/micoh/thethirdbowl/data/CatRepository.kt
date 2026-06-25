package com.micoh.thethirdbowl.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
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
        val userId = requireNotNull(client.auth.currentSessionOrNull()?.user?.id) {
            "A signed-in session is required."
        }

        return client
            .from("cats")
            .insert(
                CreateCatRequest(
                    primaryCaregiverUserId = userId,
                    name = name.trim(),
                )
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

@Serializable
private data class CreateCatRequest(
    @SerialName("primary_caregiver_user_id")
    val primaryCaregiverUserId: String,
    val name: String,
)
