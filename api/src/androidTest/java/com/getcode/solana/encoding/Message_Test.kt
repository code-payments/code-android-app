package com.getcode.solana.encoding


import com.getcode.ed25519.Ed25519
import com.getcode.keys.Hash
import com.getcode.network.repository.encodeBase64
import com.getcode.solana.AccountMeta
import com.getcode.solana.Instruction
import com.getcode.solana.Message
import com.getcode.solana.MessageHeader
import com.getcode.solana.keys.PublicKey
import junit.framework.Assert
import org.junit.Test

class Message_Test {
    @Test
    fun testMessageHeader() {
        val header = MessageHeader(
            requiredSignatures = 2,
            readOnlySigners = 1,
            readOnly = 3
        )
        val data = header.encode()
        val decodedHeader = MessageHeader.fromList(data.toList())
        Assert.assertEquals(2, decodedHeader.requiredSignatures)
        Assert.assertEquals(1, decodedHeader.readOnlySigners)
        Assert.assertEquals(3, decodedHeader.readOnly)
    }


    @Test
    fun testMessageEncodeDecodeCycle() {
        fun randomPublicKey() = PublicKey(Ed25519.createKeyPair().publicKeyBytes.toList())

        val program = randomPublicKey()
        val program2 = randomPublicKey()

        val accounts = listOf(
            AccountMeta.payer(publicKey = randomPublicKey()),
            AccountMeta.writable(publicKey = randomPublicKey()),
            AccountMeta.readonly(publicKey = randomPublicKey())
        )

        val accounts2 = listOf(
            AccountMeta.writable(publicKey = randomPublicKey()),
            AccountMeta.readonly(publicKey = randomPublicKey()),
            AccountMeta.writable(publicKey = randomPublicKey()),
            AccountMeta.readonly(publicKey = randomPublicKey())
        )

        val instructions = listOf(
            Instruction(
                program = program,
                accounts = accounts,
                data = listOf(85, 73, 81, 94, 90, 23, 54, 12)
            ),
            Instruction(
                program = program2,
                accounts = accounts2,
                data = listOf(81, 77, 95, 71, 86, 13, 34, 17)
            ),
        )

        val blockhash = Hash(randomPublicKey().bytes)

        val allAccounts = mutableListOf(
            AccountMeta.readonly(program),
            AccountMeta.readonly(program2)
        )
        allAccounts.addAll(accounts)
        allAccounts.addAll(accounts2)


        val message = Message.newInstance(
            accounts = allAccounts,
            recentBlockhash = blockhash,
            instructions = instructions
        )

        val data = message.encode()
        val decodedMessage = Message.newInstance(data.toList())

        Assert.assertEquals(message.header, decodedMessage?.header)
        Assert.assertEquals(allAccounts.sorted(), decodedMessage?.accounts)
        Assert.assertEquals(blockhash, decodedMessage?.recentBlockhash)
        Assert.assertEquals(instructions, decodedMessage?.instructions)
    }
}