package com.getcode.libs.parcelable

import kotlin.test.Test
import kotlin.test.assertNotNull

@Parcelize
data class Sample(val name: String) : Parcelable

class ParcelizeTest {
    @Test
    fun `common Parcelize annotation generates a CREATOR`() {
        // The parcelize plugin only adds the field when it treats the common annotation as its own.
        assertNotNull(Sample::class.java.getField("CREATOR").get(null) as? android.os.Parcelable.Creator<*>)
    }
}
