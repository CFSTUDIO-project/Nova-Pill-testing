package app.novapill.android.util

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TorchProblem { NONE, NO_FLASH, IN_USE, ERROR }

/**
 * Flashlight through CameraManager.setTorchMode (no camera permission is needed). The on/off state shown
 * in the UI comes from the TorchCallback, not from the last tap, so it is always what the hardware reports.
 */
class Flashlight(private val context: Context) {
    private val manager: CameraManager? = context.getSystemService(CameraManager::class.java)
    private var cameraId: String? = null
    private var registered = false

    val hasFlash: Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)

    private val _on = MutableStateFlow(false)
    val on: StateFlow<Boolean> = _on.asStateFlow()

    private val _problem = MutableStateFlow(if (hasFlash) TorchProblem.NONE else TorchProblem.NO_FLASH)
    val problem: StateFlow<TorchProblem> = _problem.asStateFlow()

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == this@Flashlight.cameraId) {
                _on.value = enabled
                _problem.value = TorchProblem.NONE
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == this@Flashlight.cameraId) _problem.value = TorchProblem.IN_USE
        }
    }

    fun start() {
        if (registered || !hasFlash) return
        val m = manager ?: return
        try {
            cameraId = findFlashCamera(m)
            if (cameraId == null) {
                _problem.value = TorchProblem.NO_FLASH
                return
            }
            m.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
            registered = true
        } catch (_: Exception) {
            _problem.value = TorchProblem.ERROR
        }
    }

    fun stop() {
        if (!registered) return
        try {
            manager?.unregisterTorchCallback(callback)
        } catch (_: Exception) {
            // nothing to undo
        }
        registered = false
    }

    /** Only called from an explicit tap. Returns false when the hardware refused. */
    fun toggle(): Boolean {
        val m = manager ?: return false
        val id = cameraId ?: run {
            start()
            cameraId
        } ?: return false
        return try {
            m.setTorchMode(id, !_on.value)
            true
        } catch (e: CameraAccessException) {
            _problem.value = TorchProblem.IN_USE
            false
        } catch (e: Exception) {
            _problem.value = TorchProblem.ERROR
            false
        }
    }

    /** Turns the torch off when the island stops, so it is never left on by accident. */
    fun turnOff() {
        val id = cameraId ?: return
        if (!_on.value) return
        try {
            manager?.setTorchMode(id, false)
        } catch (_: Exception) {
            // best effort
        }
    }

    private fun findFlashCamera(m: CameraManager): String? {
        var fallback: String? = null
        for (id in m.cameraIdList) {
            val chars = m.getCameraCharacteristics(id)
            if (chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) != true) continue
            if (chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) return id
            if (fallback == null) fallback = id
        }
        return fallback
    }
}
