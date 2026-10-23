package com.getcode.libs.parcelable

actual typealias Parcelable = android.os.Parcelable

actual interface ManualParcelable : Parcelable {
    actual override fun describeContents(): Int
    actual override fun writeToParcel(dest: Parcel, flags: Int)
}

actual typealias Parcel = android.os.Parcel

actual fun <T> parcelableCreator(newArray: (Int) -> Array<T?>, create: (Parcel) -> T): Any =
    object : android.os.Parcelable.Creator<T> {
        override fun createFromParcel(source: Parcel): T = create(source)
        override fun newArray(size: Int): Array<T?> = newArray(size)
    }

actual typealias Parcelize = kotlinx.parcelize.Parcelize

actual typealias IgnoredOnParcel = kotlinx.parcelize.IgnoredOnParcel
