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
        minorVersion = 9, // release month
        patchVersion = 1, // cycle in minor version
        // This branch is the approved 4484 build plus the Phantom deposit fix, so the commit
        // count lands at 4485 - below the 4502 already on the internal track, which Play will
        // not accept. Pin above it rather than padding history with empty commits.
        versionCodeOverride = 4503,
    )

}
