package com.getcode.view.camera

import android.os.Handler
import android.os.Looper
import android.os.Message
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import java.util.concurrent.TimeUnit

class UiThreadScheduler private constructor() : Scheduler() {
    private val _handler: Handler = Handler(Looper.getMainLooper())

    private inner class ScheduledAction constructor(action: Runnable) : Disposable, Runnable {
        private var _action: Runnable?

        override fun dispose() {
            _action = null
        }

        override fun isDisposed(): Boolean {
            return _action == null
        }

        override fun run() {
            val action = _action
            if (action != null) {
                try {
                    action.run()
                } catch (t: Throwable) {
                    try {
                        RxJavaPlugins.getErrorHandler()!!.accept(t)
                    } catch (e: Exception) {
                        // error of last resort
                        throw RuntimeException(e)
                    }
                }
            }
        }

        init {
            _action = action
        }
    }

    internal inner class UiThreadWorker(private val _handler: Handler) : Worker() {
        @Volatile
        private var _isDisposed = false
        override fun schedule(run: Runnable, delay: Long, unit: TimeUnit): Disposable {
            if (_isDisposed) {
                return Disposable.disposed()
            }
            val scheduledAction: ScheduledAction = ScheduledAction(run)
            if (Looper.myLooper() == Looper.getMainLooper()) {
                scheduledAction.run()
                return Disposable.disposed()
            }
            val message = Message.obtain(_handler, scheduledAction)
            _handler.sendMessageDelayed(message, unit.toMillis(delay))
            if (_isDisposed) {
                _handler.removeCallbacks(scheduledAction)
                scheduledAction.dispose()
                return Disposable.disposed()
            }
            return scheduledAction
        }

        override fun dispose() {
            _isDisposed = true
        }

        override fun isDisposed(): Boolean {
            return _isDisposed
        }
    }

    override fun createWorker(): Worker {
        return UiThreadWorker(_handler)
    }

    companion object {
        private val _scheduler = UiThreadScheduler()
        fun uiThread(): Scheduler {
            return _scheduler
        }
    }
}
