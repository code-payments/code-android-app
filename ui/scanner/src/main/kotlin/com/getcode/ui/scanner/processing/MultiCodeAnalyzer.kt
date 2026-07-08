package com.getcode.ui.scanner.processing

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.getcode.libs.code.detection.CodeDetector
import com.getcode.libs.code.detection.CodeScanResult
import com.getcode.libs.code.detection.NativeScanSample
import kotlin.time.TimeSource
import com.getcode.libs.qr.QrCodeAnalyzer
import com.kik.kikx.kikcodes.ScanQuality
import com.kik.kikx.kikcodes.implementation.KikCodeAnalyzer
import com.kik.kikx.kikcodes.implementation.KikCodeResult
import com.kik.kikx.kikcodes.implementation.KikCodeScannerImpl
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch


@Composable
fun rememberMultiCodeAnalyzer(
    onCodeScanned: (CodeScanResult) -> Unit,
    onError: (Throwable) -> Unit = {},
): MultiCodeAnalyzer {
    val currentOnCodeScanned by rememberUpdatedState(onCodeScanned)
    val currentOnError by rememberUpdatedState(onError)

    val qrCodeAnalyzer = remember { QrCodeAnalyzer() }
    val kikCodeScanner = remember { KikCodeScannerImpl() }
    val kikCodeAnalyzer = remember(kikCodeScanner) { KikCodeAnalyzer(kikCodeScanner) }

    val detectors = remember(qrCodeAnalyzer, kikCodeAnalyzer) {
        listOf(kikCodeAnalyzer, qrCodeAnalyzer)
    }

    val analyzer = remember(detectors) {
        MultiCodeAnalyzer(detectors)
    }

    DisposableEffect(analyzer) {
        analyzer.listen(object : CodeScanListener {
            override fun onCodeScanned(result: CodeScanResult) {
                currentOnCodeScanned(result)
            }

            override fun onNoCodeFound() = Unit

            override fun onError(error: Throwable) {
                currentOnError(error)
            }
        })

        onDispose {
            analyzer.cancel()
        }
    }

    return analyzer
}

interface CodeScanListener {
    fun onCodeScanned(result: CodeScanResult)
    fun onNoCodeFound()
    fun onError(error: Throwable)
}

class MultiCodeAnalyzer(
    private val detectors: List<CodeDetector<*>>,
) : ImageAnalysis.Analyzer {

    private var onCodeScanned: (CodeScanResult) -> Unit = { }
    private var onNoCodeFound: () -> Unit = { }
    private var onError: (Throwable) -> Unit = { }

    private val job = SupervisorJob()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        onError(throwable)
    }
    private val scope = CoroutineScope(Dispatchers.IO + job + exceptionHandler)

    fun listen(listener: CodeScanListener) {
        onCodeScanned = listener::onCodeScanned
        onNoCodeFound = listener::onNoCodeFound
        onError = listener::onError
    }

    fun cancel() {
        onCodeScanned = {}
        onNoCodeFound = {}
        onError = {}
        job.cancel()
    }

    override fun analyze(image: ImageProxy) {
        scope.launch {
            try {
                var result: CodeScanResult? = null

                for ((index, detector) in detectors.withIndex()) {
                    if (index == 0) {
                        // First detector is the Kik native scanner — isolate its cost and
                        // carry the measurement on the result itself (race-free; no shared holder).
                        val start = TimeSource.Monotonic.markNow()
                        val detected = detector.detect(image)
                        val elapsedMs = start.elapsedNow().inWholeMilliseconds
                        result = if (detected is KikCodeResult) {
                            detected.copy(
                                nativeScan = NativeScanSample(
                                    durationMs = elapsedMs,
                                    hit = true,
                                    width = image.width,
                                    height = image.height,
                                    quality = ScanQuality.Best.headerValue,
                                )
                            )
                        } else {
                            detected
                        }
                    } else {
                        result = detector.detect(image)
                    }
                    if (result != null) {
                        break
                    }
                }

                if (result != null) {
                    onCodeScanned(result)
                } else {
                    onNoCodeFound()
                }
            } catch (e: Exception) {
                onError(e)
            } finally {
                runCatching { image.close() }
            }
        }
    }
}