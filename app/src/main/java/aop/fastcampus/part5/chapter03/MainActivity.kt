package aop.fastcampus.part5.chapter03

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.media.MediaScannerConnection
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
import androidx.camera.core.ImageCapture.FLASH_MODE_AUTO
import androidx.camera.core.impl.ImageOutputConfig
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import aop.fastcampus.part5.chapter03.databinding.ActivityMainBinding
import aop.fastcampus.part5.chapter03.extensions.loadCenterCrop
import aop.fastcampus.part5.chapter03.util.PathUtil
import java.io.File
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var cameraExecutor: ExecutorService
    private val cameraMainExecutor by lazy { ContextCompat.getMainExecutor(this) }

    private lateinit var imageCapture: ImageCapture
    private val cameraProviderFuture by lazy { ProcessCameraProvider.getInstance(this) } // 카메라 얻어오면 이후 실행 리스너 등록
    private val displayManager by lazy {
        getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    // Camera Config
    private var displayId: Int = -1

    private var camera: Camera? = null
    private var root: View? = null
    private var isCapturing: Boolean = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (this@MainActivity.displayId == displayId) {
                if (::imageCapture.isInitialized && root != null) {
                    imageCapture.targetRotation = root?.display?.rotation ?: ImageOutputConfig.INVALID_ROTATION
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        root = binding.root
        if (allPermissionsGranted()) {
            startCamera(binding.viewFinder)
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera(viewFinder: PreviewView) {
        displayManager.registerDisplayListener(displayListener, null)

        cameraExecutor = Executors.newSingleThreadExecutor()

        viewFinder.postDelayed({
            displayId = viewFinder.display.displayId
            bindCameraUseCase()
        }, 10)
    }

    private fun bindCameraUseCase() = with(binding) {
        val rotation = viewFinder.display.rotation // 회전 값 설정
        val cameraSelector = CameraSelector.Builder().requireLensFacing(LENS_FACING).build() // 카메라 설정(후면)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().apply {
                setTargetAspectRatio(AspectRatio.RATIO_4_3)
                setTargetRotation(rotation)
            }.build()

            // imageCapture Init
            val builder = ImageCapture.Builder()
                .setCaptureMode(CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(rotation)
                .setFlashMode(FLASH_MODE_AUTO)

            imageCapture = builder.build()

            try {
                cameraProvider.unbindAll() // 기존에 바인딩 되어 있는 카메라는 해제해주어야 함
                camera = cameraProvider.bindToLifecycle(
                    this@MainActivity, cameraSelector, preview, imageCapture
                )
                preview.setSurfaceProvider(viewFinder.surfaceProvider)
                bindCaptureListener()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, cameraMainExecutor)

    }

    private fun bindCaptureListener() = with(binding) {
        captureButton.setOnClickListener {
            if (!isCapturing) {
                isCapturing = true
                captureCamera()
            }
        }
    }

    private var contentUri: Uri? = null

    private fun captureCamera() {
        if (!::imageCapture.isInitialized) return
        val photoFile = File(
            PathUtil.getOutputDirectory(this),
            SimpleDateFormat(
                FILENAME_FORMAT, Locale.KOREA
            ).format(System.currentTimeMillis()) + ".jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val savedUri = outputFileResults.savedUri ?: Uri.fromFile(photoFile)
                val rotation = binding.viewFinder.display.rotation // 회전 값 설정
                contentUri = savedUri
                updateSavedImageContent()
            }

            override fun onError(e: ImageCaptureException) {
                e.printStackTrace()
                isCapturing = false
            }
        })

    }

    private fun updateSavedImageContent() {
        contentUri?.let {
            try {
                val file = File(PathUtil.getPath(this, it) ?: throw FileNotFoundException())
                MediaScannerConnection.scanFile(this, arrayOf(file.path), arrayOf("image/jpeg"), null)
                Handler(Looper.getMainLooper()).post {
                    binding.previewImageVIew.loadCenterCrop(url = it.toString(), corner = 4f)
                }
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }
        }
    }

    /*private var focusMeetingFuture: ListenableFuture<FocusMeteringResult>? = null
    private fun startFocus(isCapture: Boolean = false) = with(binding) {
        val factory: MeteringPointFactory = SurfaceOrientedMeteringPointFactory(
            viewFinder.width.toFloat(), viewFinder.height.toFloat()
        )
        val centerWidth = viewFinder.width.toFloat() / 2
        val centerHeight = viewFinder.height.toFloat() / 2
        //create a point on the center of the view
        val autoFocusPoint = factory.createPoint(centerWidth, centerHeight)


    }*/

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera(binding.viewFinder)
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        const val TAG = "MainActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

        private val LENS_FACING: Int = CameraSelector.LENS_FACING_BACK
    }

}
