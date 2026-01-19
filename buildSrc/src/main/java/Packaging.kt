sealed class Packaging(
    majorVersion: String,
    minorVersion: String,
    patchVersion: String,
    suffix: String? = null,
) {
    constructor(
        majorVersion: Int,
        minorVersion: Int,
        patchVersion: Int,
        suffix: String? = null,
    ) : this(
        majorVersion = majorVersion.toString(),
        minorVersion = minorVersion.toString(),
        patchVersion = patchVersion.toString(),
        suffix = suffix,
    )

    private val suffixString = suffix?.let { "-$it" } ?: ""
    val versionName = "$majorVersion.$minorVersion.$patchVersion$suffixString"

    object Code : Packaging(
        majorVersion = 2,
        minorVersion = 1,
        patchVersion = 14,
    )

    object Flipcash : Packaging(
        majorVersion = 2026, // release year
        minorVersion = 1, // release month
        patchVersion = 1, // cycle in minor version
        suffix = "cash2"
    )

    object Flipchat : Packaging(
        majorVersion = 1,
        minorVersion = 0,
        patchVersion = 10,
    )
}
