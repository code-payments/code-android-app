package com.getcode.solana.encoding

import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey
import com.getcode.network.repository.toByteList
import com.getcode.solana.AccountMeta
import com.getcode.solana.Message
import com.getcode.solana.MessageHeader
import junit.framework.Assert.assertEquals
import org.junit.Test

class MessageEncoding_Test {
    @Test
    fun messageEncoding() {
        val headers = listOf(
            MessageHeader(3, 1, 4)
        )
        val hashes = listOf(
            Hash(
                listOf(
                    101, 116, 70, 63, 191, 201, 150, 31, 115, 132, 250, 155, 18, 171, 135, 213,
                    132, 223, 128, 23, 142, 119, 126, 47, 204, 233, 8, 136, 95, 8, 43, 153
                ).toByteList()
            )
        )

        val accounts = listOf(
            listOf(
                AccountMeta.payer(
                    PublicKey(
                        listOf(
                            9, 44, 14, 22, 184, 170, 97, 239, 12, 185, 70, 65, 119, 118, 114,
                            54, 255, 60, 52, 123, 82, 133, 164, 46, 40, 205, 154, 124, 39, 59,
                            89, 238
                        ).toByteList()
                    )
                ),
                AccountMeta.writable(
                    PublicKey(
                        listOf(
                            11, 188, 11, 147, 54, 247, 148, 189, 25, 169, 205, 216, 208, 118,
                            234, 39, 168, 247, 199, 188, 172, 150, 159, 153, 178, 201, 247, 3,
                            224, 207, 185, 87
                        ).toByteList()
                    ),
                    signer = true
                ),
                AccountMeta.readonly(
                    PublicKey(
                        listOf(
                            57, 88, 174, 113, 57, 253, 24, 181, 197, 67, 143, 220, 53, 132, 20,
                            11, 230, 5, 94, 238, 153, 242, 28, 230, 123, 135, 54, 69, 200, 188,
                            103, 6
                        ).toByteList()
                    ),
                    signer = true
                ),
                AccountMeta.writable(
                    PublicKey(
                        listOf(
                            188, 72, 197, 118, 240, 244, 192, 109, 13, 22, 88, 163, 208, 174,
                            63, 174, 11, 201, 26, 143, 48, 48, 193, 21, 163, 215, 115, 44, 201,
                            210, 236, 159
                        ).toByteList()
                    ),
                    signer = false
                ),
                AccountMeta.writable(
                    PublicKey(
                        listOf(
                            249, 177, 29, 97, 248, 176, 59, 124, 27, 171, 138, 129, 191, 135,
                            7, 35, 191, 253, 252, 164, 96, 202, 198, 204, 68, 206, 100, 147, 68,
                            207, 164, 239
                        ).toByteList()
                    ),
                    signer = false
                ),
                AccountMeta.readonly(
                    PublicKey(
                        listOf(
                            6, 167, 213, 23, 25, 44, 86, 142, 224, 138, 132, 95, 115, 210, 151,
                            136, 207, 3, 92, 49, 69, 178, 26, 179, 68, 216, 6, 46, 169, 64, 0, 0
                        ).toByteList()
                    ),
                    signer = false
                ),
                AccountMeta.readonly(
                    PublicKey(
                        listOf(
                            5, 74, 83, 80, 248, 93, 200, 130, 214, 20, 165, 86, 114, 120, 138,
                            41, 109, 223, 30, 171, 171, 208, 166, 6, 120, 136, 73, 50, 244, 238,
                            246, 160
                        ).toByteList()
                    ),
                    signer = false
                ),
                AccountMeta.readonly(
                    PublicKey(
                        listOf(
                            6, 221, 246, 225, 215, 101, 161, 147, 217, 203, 225, 70, 206, 235,
                            121, 172, 28, 180, 133, 237, 95, 91, 55, 145, 58, 140, 245, 133,
                            126, 255, 0, 169
                        ).toByteList()
                    ),
                    signer = false
                ),
                AccountMeta.readonly(
                    PublicKey(
                        listOf(
                            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                            0, 0, 0, 0, 0, 0, 0, 0, 0, 0
                        ).toByteList()
                    ),
                    signer = false
                ),
            )
        )

        val inputs = listOf(
            listOf(
                3, 1, 4, 9, 9, 44, 14, 22, 184, 170, 97, 239, 12, 185, 70, 65, 119, 118, 114, 54,
                255, 60, 52, 123, 82, 133, 164, 46, 40, 205, 154, 124, 39, 59, 89, 238, 11, 188,
                11, 147, 54, 247, 148, 189, 25, 169, 205, 216, 208, 118, 234, 39, 168, 247, 199,
                188, 172, 150, 159, 153, 178, 201, 247, 3, 224, 207, 185, 87, 57, 88, 174, 113,
                57, 253, 24, 181, 197, 67, 143, 220, 53, 132, 20, 11, 230, 5, 94, 238, 153, 242,
                28, 230, 123, 135, 54, 69, 200, 188, 103, 6, 188, 72, 197, 118, 240, 244, 192, 109,
                13, 22, 88, 163, 208, 174, 63, 174, 11, 201, 26, 143, 48, 48, 193, 21, 163, 215,
                115, 44, 201, 210, 236, 159, 249, 177, 29, 97, 248, 176, 59, 124, 27, 171, 138, 129,
                191, 135, 7, 35, 191, 253, 252, 164, 96, 202, 198, 204, 68, 206, 100, 147, 68, 207,
                164, 239, 6, 167, 213, 23, 25, 44, 86, 142, 224, 138, 132, 95, 115, 210, 151, 136,
                207, 3, 92, 49, 69, 178, 26, 179, 68, 216, 6, 46, 169, 64, 0, 0, 5, 74, 83, 80, 248,
                93, 200, 130, 214, 20, 165, 86, 114, 120, 138, 41, 109, 223, 30, 171, 171, 208, 166,
                6, 120, 136, 73, 50, 244, 238, 246, 160, 6, 221, 246, 225, 215, 101, 161, 147, 217,
                203, 225, 70, 206, 235, 121, 172, 28, 180, 133, 237, 95, 91, 55, 145, 58, 140, 245,
                133, 126, 255, 0, 169, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 101, 116, 70, 63, 191, 201, 150, 31, 115, 132,
                250, 155, 18, 171, 135, 213, 132, 223, 128, 23, 142, 119, 126, 47, 204, 233, 8, 136,
                95, 8, 43, 153, 3, 8, 3, 1, 5, 0, 4, 4, 0, 0, 0, 6, 0, 44, 90, 84, 65, 69, 65, 65,
                65, 65, 65, 65, 65, 65, 65, 65, 65, 65, 65, 65, 65, 65, 65, 65, 65, 65, 65, 65, 65,
                65, 65, 65, 65, 65, 65, 65, 65, 65, 65, 65, 65, 65, 65, 65, 65, 61, 7, 3, 4, 3, 2,
                9, 3, 64, 75, 76, 0, 0, 0, 0, 0
            ).toByteList(),
        )

        // --------------------------------------------------------

        val messages =
            listOf(
                Message.newInstance(inputs[0])
            )

        // --------------------------------------------------------
        headers.forEachIndexed { i, header ->
            assertEquals(header.requiredSignatures, messages[i]?.header?.requiredSignatures)
            assertEquals(header.readOnlySigners, messages[i]?.header?.readOnlySigners)
            assertEquals(header.readOnly, messages[i]?.header?.readOnly)
        }
        hashes.forEachIndexed { i, hash ->
            assertEquals(hash.bytes, messages[i]?.recentBlockhash?.bytes)
        }

        accounts.forEachIndexed { i1, account ->
            account.forEachIndexed { i2, item ->
                assertEquals(item.publicKey.bytes, messages[i1]?.accounts?.get(i2)?.publicKey?.bytes)
                assertEquals(item.isSigner, messages[i1]?.accounts?.get(i2)?.isSigner)
                assertEquals(item.isWritable, messages[i1]?.accounts?.get(i2)?.isWritable)
                assertEquals(item.isPayer, messages[i1]?.accounts?.get(i2)?.isPayer)
                assertEquals(item.isProgram, messages[i1]?.accounts?.get(i2)?.isProgram)
            }
        }
    }
}