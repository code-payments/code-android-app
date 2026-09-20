package com.getcode.ui.scanner.processing

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.getcode.libs.code.detection.CodeDetector
import com.getcode.libs.code.detection.CodeScanResult
import com.getcode.libs.qr.QrCodeAnalyzer
import com.getcode.media.StaticImageAnalyzerImpl
import com.kik.kikx.kikcodes.implementation.KikCodeAnalyzer
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

    val context = LocalContext.current

    val qrCodeAnalyzer = remember { QrCodeAnalyzer() }
    val kikCodeScanner = remember { KikCodeScannerImpl() }
    // `staticImageAnalyzer` used to be an `@Inject lateinit var` on `KikCodeAnalyzer`, which field
    // injection never filled in because this composable builds the analyzer itself -- the first
    // `detect(uri)` would have thrown. It is a constructor argument now, built the same way as
    // everything else here.
    val staticImageAnalyzer = remember(kikCodeScanner) {
        StaticImageAnalyzerImpl(context.applicationContext, kikCodeScanner)
    }
    val kikCodeAnalyzer = remember(kikCodeScanner, staticImageAnalyzer) {
        KikCodeAnalyzer(kikCodeScanner, staticImageAnalyzer)
    }

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

                for (detector in detectors) {
                    result = detector.detect(image)
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