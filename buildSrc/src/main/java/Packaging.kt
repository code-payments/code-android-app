sealed class Packaging(
    majorVersion: String,
    minorVersion: String,
    patchVersion: String,
    suffix: String? = null,
    versionCodeOverride: Int? = null,
) {
    constructor(
        majorVersion: Int,
        minorVersion: Int,
        patchVersion: Int,
        suffix: String? = null,
        versionCodeOverride: Int? = null,
    ) : this(
        majorVersion = majorVersion.toString(),
        minorVersion = minorVersion.toString(),
        patchVersion = patchVersion.toString(),
        suffix = suffix,
        versionCodeOverride = versionCodeOverride,
    )

    private val suffixString = suffix?.let { "-$it" } ?: ""
    val versionName = "$majorVersion.$minorVersion.$patchVersion$suffixString"

    val versionCode: Int? = versionCodeOverride

    object Flipcash : Packaging(
        majorVersion = 2026, // release year
        minorVersion = 6, // release month
        patchVersion = 1, // cycle in minor version
    )

}
