package com.flipcash.app.persistence.converters


import androidx.room.TypeConverter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

class TokenTypeConverters {

    // region SocialLinks

    @TypeConverter
    fun fromSocialLinks(value: String?): List<SocialLinkSerialized>? {
        return value?.let { json.decodeFromString<List<SocialLinkSerialized>>(it) }
    }

    @TypeConverter
    fun toSocialLinks(links: List<SocialLinkSerialized>?): String? {
        return links?.let { json.encodeToString(it) }
    }

    // endregion

    // region BillCustomizations

    @TypeConverter
    fun fromBillCustomizations(value: String?): BillCustomizationsSerialized? {
        return value?.let { json.decodeFromString<BillCustomizationsSerialized>(it) }
    }

    @TypeConverter
    fun toBillCustomizations(customizations: BillCustomizationsSerialized?): String? {
        return customizations?.let { json.encodeToString(it) }
    }

    // endregion
}

@Serializable
sealed interface SocialLinkSerialized {
    @Serializable
    @SerialName("website")
    data class Website(val url: String) : SocialLinkSerialized

    @Serializable
    @SerialName("x")
    data class X(val username: String) : SocialLinkSerialized
}

@Serializable
data class BillCustomizationsSerialized(
    val background: BillBackgroundSerialized,
    val texture: BillTextureSerialized?,
    val icon: String?, // Base64-encoded ByteArray
)

@Serializable
sealed interface BillBackgroundSerialized {
    @Serializable
    @SerialName("solid")
    data class Solid(val colorHex: String) : BillBackgroundSerialized

    @Serializable
    @SerialName("gradient")
    data class Gradient(val colors: List<String>) : BillBackgroundSerialized
}

@Serializable
data class BillTextureSerialized(
    val index: Int,
    val blendMode: String,
    val strength: Float,
)