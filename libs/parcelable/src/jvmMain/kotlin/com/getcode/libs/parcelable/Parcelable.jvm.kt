package com.getcode.libs.parcelable

actual interface Parcelable {
    actual fun describeContents(): Int
    actual fun writeToParcel(dest: Parcel, flags: Int)
}

actual class Parcel private constructor() {
    actual fun writeString(value: String?): Unit = throw UnsupportedOperationException()
    actual fun readString(): String? = throw UnsupportedOperationException()
}

actual fun <T> parcelableCreator(newArray: (Int) -> Array<T?>, create: (Parcel) -> T): Any = Unit
