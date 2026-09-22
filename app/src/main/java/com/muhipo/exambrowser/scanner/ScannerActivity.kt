package com.muhipo.exambrowser.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.zxing.*
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.muhipo.exambrowser.R
import com.muhipo.exambrowser.databinding.ActivityScannerBinding
import com.muhipo.exambrowser.exam.ExamActivity
import com.muhipo.exambrowser.utils.PreferenceManager
import com.muhipo.exambrowser.utils.UrlValidator
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX QR Scanner optimized for all Android devices from Android 7.0 (API 24) to Android 16.
 * Specifically tuned for cheap/budget camera hardware (fixed-focus, low-resolution 2MP/5MP sensors).
 */
class ScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScannerBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var preferenceManager: PreferenceManager

    private var camera: Camera? = null
    private var isTorchOn = false
    private val isProcessingScan = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = PreferenceManager(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupEdgeToEdge()
        setupControls()
        setupTapToFocus()
        checkCameraPermissionAndStart()
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBars.top + 16)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomPromptLayout) { view, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBars.bottom + 32)
            insets
        }
    }

    private fun setupControls() {
        binding.btnCloseScanner.setOnClickListener {
            finish()
        }

        binding.btnToggleFlash.setOnClickListener {
            toggleTorch()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTapToFocus() {
        binding.previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val cam = camera ?: return@setOnTouchListener false
                val factory = binding.previewView.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                    .build()
                cam.cameraControl.startFocusAndMetering(action)
            }
            true
        }
    }

    private fun toggleTorch() {
        val cam = camera ?: return
        if (cam.cameraInfo.hasFlashUnit()) {
            isTorchOn = !isTorchOn
            cam.cameraControl.enableTorch(isTorchOn)
            binding.btnToggleFlash.setImageResource(
                if (isTorchOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off
            )
        }
    }

    private fun checkCameraPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(cameraProvider)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera initialization error: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @Suppress("DEPRECATION")
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        // Use standard 4:3 ratio for maximum camera sensor compatibility on cheap phones
        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
            .also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }

        val multiFormatReader = MultiFormatReader().apply {
            val hints = mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    BarcodeFormat.QR_CODE,
                    BarcodeFormat.CODE_128,
                    BarcodeFormat.CODE_39,
                    BarcodeFormat.EAN_13,
                    BarcodeFormat.DATA_MATRIX
                ),
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.CHARACTER_SET to "UTF-8"
            )
            setHints(hints)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    if (!isProcessingScan.get()) {
                        processImageProxy(imageProxy, multiFormatReader)
                    } else {
                        imageProxy.close()
                    }
                }
            }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)

            // Trigger continuous auto focus & auto exposure
            val factory = binding.previewView.meteringPointFactory
            val centerPoint = factory.createPoint(binding.previewView.width / 2f, binding.previewView.height / 2f)
            val action = FocusMeteringAction.Builder(centerPoint, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                .disableAutoCancel()
                .build()
            camera?.cameraControl?.startFocusAndMetering(action)

        } catch (e: Exception) {
            Toast.makeText(this, "Camera binding error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processImageProxy(imageProxy: ImageProxy, reader: MultiFormatReader) {
        val buffer: ByteBuffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val width = imageProxy.width
        val height = imageProxy.height
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        var source: LuminanceSource = PlanarYUVLuminanceSource(
            bytes, width, height, 0, 0, width, height, false
        )

        if (rotationDegrees != 0) {
            source = rotateLuminanceSource(source, rotationDegrees)
        }

        var decodedText: String? = null

        // Pass 1: Try HybridBinarizer (best for good lighting)
        try {
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decodeWithState(bitmap)
            decodedText = result.text?.trim()
        } catch (_: NotFoundException) {
            // Pass 2: Fallback to GlobalHistogramBinarizer for low-end cheap camera sensors
            try {
                reader.reset()
                val fallbackBitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
                val fallbackResult = reader.decodeWithState(fallbackBitmap)
                decodedText = fallbackResult.text?.trim()
            } catch (_: Exception) {}
        } catch (_: Exception) {
        } finally {
            reader.reset()
            imageProxy.close()
        }

        if (!decodedText.isNullOrEmpty() && !isProcessingScan.get()) {
            isProcessingScan.set(true)
            mainHandler.post {
                handleScanResult(decodedText)
            }
        }
    }

    private fun rotateLuminanceSource(source: LuminanceSource, degrees: Int): LuminanceSource {
        var current = source
        val steps = (degrees / 90) % 4
        for (i in 0 until steps) {
            current = rotate90Clockwise(current)
        }
        return current
    }

    private fun rotate90Clockwise(source: LuminanceSource): LuminanceSource {
        val width = source.width
        val height = source.height
        val matrix = source.matrix
        val rotated = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                rotated[x * height + (height - y - 1)] = matrix[y * width + x]
            }
        }
        return PlanarYUVLuminanceSource(rotated, height, width, 0, 0, height, width, false)
    }

    private fun handleScanResult(rawText: String) {
        if (UrlValidator.isValidHttpUrl(rawText)) {
            vibrateSuccess()

            val host = UrlValidator.extractHost(rawText)
            if (!host.isNullOrEmpty()) {
                preferenceManager.addWhitelistDomain(host)
            }

            preferenceManager.startExamSession(rawText)

            val intent = Intent(this, ExamActivity::class.java).apply {
                putExtra(ExamActivity.EXTRA_EXAM_URL, rawText)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
            finish()
        } else {
            binding.cardInvalidCode.visibility = View.VISIBLE
            binding.tvInvalidCode.text = getString(R.string.error_invalid_exam_code)

            mainHandler.postDelayed({
                binding.cardInvalidCode.visibility = View.GONE
                isProcessingScan.set(false)
            }, 2500)
        }
    }

    private fun vibrateSuccess() {
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        mainHandler.removeCallbacksAndMessages(null)
    }
}
