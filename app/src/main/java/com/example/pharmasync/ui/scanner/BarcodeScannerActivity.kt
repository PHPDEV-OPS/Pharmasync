package com.example.pharmasync.ui.scanner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.pharmasync.R
import com.example.pharmasync.databinding.ActivityBarcodeScannerBinding
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Scans one barcode with CameraX + ML Kit and returns it in [EXTRA_RESULT]. */
class BarcodeScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBarcodeScannerBinding
    private lateinit var cameraExecutor: ExecutorService
    private val delivered = AtomicBoolean(false)

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else {
            Toast.makeText(this, R.string.error_camera_permission, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBarcodeScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnClose.setOnClickListener { finish() }
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, BarcodeAnalyzer(::deliver)) }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun deliver(value: String) {
        if (!delivered.compareAndSet(false, true)) return
        runOnUiThread {
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_RESULT, value))
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
    }

    private class BarcodeAnalyzer(private val onDetected: (String) -> Unit) : ImageAnalysis.Analyzer {
        private val scanner = BarcodeScanning.getClient()

        @OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return
            }
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes -> barcodes.firstNotNullOfOrNull { it.rawValue }?.let(onDetected) }
                .addOnFailureListener { Log.w(TAG, "Barcode scan failed", it) }
                .addOnCompleteListener { imageProxy.close() }
        }
    }

    companion object {
        const val EXTRA_RESULT = "scan_result"
        private const val TAG = "BarcodeScanner"
    }
}
