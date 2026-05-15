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

    // region holder metrics
    @TypeConverter
    fun fromHolderMetrics(value: String?): HolderMetricsSerialized? {
        return value?.let { json.decodeFromString<HolderMetricsSerialized>(it) }
    }

    @TypeConverter
    fun toHolderMetrics(metrics: HolderMetricsSerialized?): String? {
        return metrics?.let { json.encodeToString(it) }
    }
    // endregion

    // region ModerationAttestations
    @TypeConverter
    fun fromModerationAttestations(value: String?): ModerationAttestationsSerialized? {
        return value?.let { json.decodeFromString<ModerationAttestationsSerialized>(it) }
    }

    @TypeConverter
    fun toModerationAttestations(attestations: ModerationAttestationsSerialized?): String? {
        return attestations?.let { json.encodeToString(it) }
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

    @Serializable
    @SerialName("tg")
    data class Telegram(val username: String) : SocialLinkSerialized

    @Serializable
    @SerialName("discord")
    data class Discord(val inviteCode: String) : SocialLinkSerialized
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

@Serializable
data class HolderMetricsSerialized(
    val currentHolders: Long,
    val deltas: List<HolderDeltaSerialized>,
)

@Serializable
data class HolderDeltaSerialized(
    val range: String,  // WindowedRange enum name
    val delta: Long,
)

@Serializable
data class ModerationAttestationSerialized(
    val rawValue: List<Byte>,
    val hashBytes: List<Byte>,
    val timestampEpochMs: Long,
    val userId: List<Byte>,
    val attestorBytes: List<Byte>,
    val signatureBytes: List<Byte>,
)

@Serializable
data class ModerationAttestationsSerialized(
    val name: ModerationAttestationSerialized,
    val icon: ModerationAttestationSerialized,
    val description: ModerationAttestationSerialized,
)