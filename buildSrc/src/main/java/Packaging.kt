sealed class Packaging(
    majorVersion: String,
    minorVersion: String,
    patchVersion: String,
) {
    constructor(
        majorVersion: Int,
        minorVersion: Int,
        patchVersion: Int
    ) : this(
        majorVersion = majorVersion.toString(),
        minorVersion = minorVersion.toString(),
        patchVersion = patchVersion.toString()
    )

    val versionName = "$majorVersion.$minorVersion.$patchVersion"

    object Code : Packaging(
        majorVersion = 2,
        minorVersion = 1,
        patchVersion = 14,
    )

    object Flipcash : Packaging(
        majorVersion = 2025, // release year
        minorVersion = 11, // release month
        patchVersion = 5, // cycle in minor version
    )

    object Flipchat : Packaging(
        majorVersion = 1,
        minorVersion = 0,
        patchVersion = 10,
    )
}
