package com.example.appusagetracker

import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Captures one still photo from the FRONT camera, with no on-screen preview.
 *
 * This is bound to a LifecycleOwner (the lock screen Activity), which is why it's allowed
 * to use the camera — the Activity is in the foreground. Android forbids using the camera
 * from a pure background service, which is exactly why covert "capture while another app is
 * open" isn't possible; here the capture happens on our own visible lock screen.
 */
object CameraCapture {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Binds the front camera and takes one picture into [outputFile].
     * Returns true on success. Must be called from the main thread (CameraX requirement).
     * Fails quietly (returns false) if no camera, no permission, or any error.
     */
    suspend fun capture(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        outputFile: File
    ): Boolean {
        if (!hasPermission(context)) return false

        val provider = try {
            getProvider(context)
        } catch (e: Exception) {
            return false
        }

        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val selector = if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        return try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, imageCapture)
            takePicture(context, imageCapture, outputFile)
        } catch (e: Exception) {
            false
        } finally {
            try { provider.unbindAll() } catch (_: Exception) {}
        }
    }

    private suspend fun getProvider(context: Context): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                try {
                    cont.resume(future.get())
                } catch (e: Exception) {
                    if (cont.isActive) cont.cancel(e)
                }
            }, ContextCompat.getMainExecutor(context))
        }

    private suspend fun takePicture(
        context: Context,
        imageCapture: ImageCapture,
        outputFile: File
    ): Boolean = suspendCancellableCoroutine { cont ->
        val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onError(exception: ImageCaptureException) {
                    if (cont.isActive) cont.resume(false)
                }
            }
        )
    }
}
