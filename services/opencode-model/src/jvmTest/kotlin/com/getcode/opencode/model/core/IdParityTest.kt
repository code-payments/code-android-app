package com.getcode.opencode.model.core

import com.getcode.utils.hexEncodedString
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/** RandomId and ID.description moved from java.util.UUID to kotlin.uuid; they must agree with the UUID code. */
class IdParityTest {

    @Test
    fun descriptionMatchesJavaUuidToString() {
        repeat(1_000) {
            val uuid = UUID.randomUUID()
            assertEquals(uuid.toString(), uuid.bytes.description)
            assertEquals(uuid, uuid.bytes.uuid)
        }
    }

    @Test
    fun randomIdIsAVersion4UuidInTheSameByteOrder() {
        repeat(1_000) {
            val id = RandomId
            val uuid = id.uuid!!
            assertEquals(4, uuid.version())
            assertEquals(2, uuid.variant())
            assertEquals(id, uuid.bytes)
        }
    }

    @Test
    fun anIdThatIsNotSixteenBytesDescribesAsHex() {
        val id = listOf<Byte>(1, 2, 3)
        assertEquals(id.hexEncodedString(), id.description)
    }
}
