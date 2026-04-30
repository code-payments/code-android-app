package com.getcode.utils

class RpcBodyFilterPlugin : TraceLogPlugin {
    override fun process(line: String): String? {
        if ("[RpcLogging]" !in line) return line
        if ("Request:" in line || "Response:" in line) {
            return if (TraceManager.includeRpcBodies) line else null
        }
        return line
    }
}
