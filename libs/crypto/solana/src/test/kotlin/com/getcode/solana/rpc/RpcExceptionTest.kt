package com.getcode.solana.rpc

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RpcExceptionTest {

    @Test
    fun simulationFailedMessageIsSimulationError() {
        val e = RpcException(
            code = -32002,
            message = "Transaction simulation failed: Error processing Instruction 6: custom program error: 0x1",
        )
        assertTrue(e.isSimulationError)
    }

    @Test
    fun customProgramErrorMessageIsSimulationError() {
        val e = RpcException(code = -32002, message = "custom program error: 0x1")
        assertTrue(e.isSimulationError)
    }

    @Test
    fun blockhashNotFoundIsNotSimulationError() {
        val e = RpcException(code = -32002, message = "Blockhash not found")
        assertTrue(e.isBlockhashNotFound)
        assertFalse(e.isSimulationError)
    }

    @Test
    fun genericRpcErrorIsNotSimulationError() {
        val e = RpcException(code = -32000, message = "Node is behind by 42 slots")
        assertFalse(e.isSimulationError)
    }
}
