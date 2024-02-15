package com.getcode.view.camera

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.BiFunction
import io.reactivex.rxjava3.processors.BehaviorProcessor
import kotlinx.coroutines.flow.Flow
import org.kin.sdk.base.tools.Optional
import timber.log.Timber

class KikCodeScannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var previewing = false

    private val cameraController: CameraController = LegacyCameraController(context)

    private var previewSizeSubscription: Disposable? = null
    private val onLayoutChangeSubject = BehaviorProcessor.create<Pair<Int, Int>>()

    private var previewContainer = FrameLayout(context, attrs, defStyleAttr)

    init {
        addView(previewContainer)
        previewContainer.addView(cameraController.previewView())

        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            onLayoutChangeSubject.onNext(Pair(width, height))
        }
    }

    fun startPreview() {
        Timber.d("start : $previewing")
        if (previewing) {
            return
        }

        previewing = true

        cameraController.startPreview()

        previewSizeSubscription = Flowable
            .combineLatest(
                cameraController.previewSize(),
                onLayoutChangeSubject.distinctUntilChanged()
            ) { previewSize: Optional<CameraController.PreviewSize>, _: Pair<Int, Int> -> previewSize }
            .filter { it.isPresent }
            .observeOn(UiThreadScheduler.uiThread())
            .subscribe {
                updatePreviewViewSize(it.get()!!.withoutRotation())
            }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        postDelayed({
            stopPreview()
        }, 300)
    }

    fun stopPreview() {
        Timber.d("stop : $previewing")
        if (!previewing) {
            return
        }

        previewing = false
        cameraController.stopPreview()
        removeView(cameraController.previewView())
        previewSizeSubscription?.dispose()
    }

    fun getPreviewBuffer() = cameraController.getPreviewBuffer()

    fun previewSize() = cameraController.previewSize()

    private fun updatePreviewViewSize(previewSize: CameraController.PreviewSize) {
        val previewView = cameraController.previewView()
        val layoutParams = previewView.layoutParams as FrameLayout.LayoutParams? ?: return

        val previewRatio = previewSize.width.toDouble() / previewSize.height.toDouble()
        val viewRatio = previewContainer.width.toDouble() / previewContainer.height.toDouble()

        if (previewRatio < viewRatio) {
            layoutParams.width = previewContainer.width
            layoutParams.height = (previewContainer.width.toDouble() / previewRatio).toInt()
        } else {
            layoutParams.height = previewContainer.height
            layoutParams.width = (previewContainer.height.toDouble() * previewRatio).toInt()
        }

        layoutParams.gravity = Gravity.CENTER

        previewView.layoutParams = layoutParams
    }

}
