package com.getcode.libs.parcelable

/**
 * `android.os.Parcelable` on Android; on the desktop JVM a marker interface nothing reads.
 *
 * Common code sees no members, so a [Parcelize] class compiles on both targets. A class that
 * writes its own parcel format implements [ManualParcelable] instead.
 */
expect interface Parcelable

/**
 * A sub-interface of `android.os.Parcelable` on Android, with the two members declared so a class can
 * hand-write its parcel format in common code.
 */
expect interface ManualParcelable : Parcelable {
    fun describeContents(): Int
    fun writeToParcel(dest: Parcel, flags: Int)
}

/** `android.os.Parcel` on Android. The desktop JVM never creates one. */
expect class Parcel {
    fun writeString(value: String?)
    fun readString(): String?
}

/**
 * Builds the `CREATOR` a hand-written [Parcelable] needs on Android. Android finds the field by
 * reflection and casts it, so the declared type can stay [Any] in common code.
 */
expect fun <T> parcelableCreator(newArray: (Int) -> Array<T?>, create: (Parcel) -> T): Any

/**
 * Common stand-in for `kotlinx.parcelize.Parcelize`. The parcelize compiler plugin is told to
 * treat it as its own (`additionalAnnotation`), so it generates the Android implementation.
 */
@OptionalExpectation
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
expect annotation class Parcelize()

/** Common stand-in for `kotlinx.parcelize.IgnoredOnParcel`. */
@OptionalExpectation
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
expect annotation class IgnoredOnParcel()
