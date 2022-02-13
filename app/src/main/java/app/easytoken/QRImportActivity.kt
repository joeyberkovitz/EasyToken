package app.easytoken

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.easytoken.databinding.ActivityQrImportBinding
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QRResult: ActivityResultContract<Unit, String?>() {
    companion object {
        const val EXTRA_QR_RESULT = "app.easytoken.extra.QR_RESULT"
    }

    override fun createIntent(context: Context, input: Unit?) = Intent(context, QRImportActivity::class.java)

    override fun parseResult(resultCode: Int, result: Intent?) : String? {
        if (resultCode != Activity.RESULT_OK) {
            return null
        }
        return result?.getStringExtra(EXTRA_QR_RESULT)
    }
}

class QRImportActivity: AppCompatActivity() {
    companion object {
        private const val REQUEST_CAMERA_PERMISSIONS = 10
        private const val TAG = "QRImportActivity"
    }

    private lateinit var viewBinding: ActivityQrImportBinding
    private lateinit var cameraExecutor: ExecutorService


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewBinding = ActivityQrImportBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        if(allPermissionsGranted())
            startCamera()
        else
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSIONS
            )
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun allPermissionsGranted() =
        ContextCompat.checkSelfPermission(baseContext,
            Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()
                .also {
                    it.setSurfaceProvider(viewBinding.cameraView.surfaceProvider)
                }

            val barcodeAnalyzer = ImageAnalysis.Builder().build()
                .also {
                    it.setAnalyzer(cameraExecutor, QRCodeAnalyzer())
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, barcodeAnalyzer)
            } catch (exc: Exception){
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == REQUEST_CAMERA_PERMISSIONS){
            if(allPermissionsGranted())
                startCamera()
            else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    fun submitResult(result: String){
        val resultIntent = Intent()
        resultIntent.putExtra(QRResult.EXTRA_QR_RESULT, result)
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    inner class QRCodeAnalyzer: ImageAnalysis.Analyzer {

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = BarcodeScanning.getClient(options)

        override fun analyze(imageProxy: ImageProxy) {
            @androidx.camera.core.ExperimentalGetImage
            val mediaImage = imageProxy.image
            if(mediaImage != null){
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                val result = scanner.process(image)
                    .addOnSuccessListener {
                        processBarcodes(it)
                        imageProxy.close()
                    }
                    .addOnFailureListener {
                        Log.e(TAG, "Barcode failure", it)
                        imageProxy.close()
                    }
            }
        }

        fun processBarcodes(barcodes: List<Barcode>){
            Log.d(TAG, "Processing barcodes")
            for(barcode in barcodes){
                val rawValue = barcode.rawValue
                if(rawValue != null){
                    Log.d(TAG, "Barcode value: $rawValue")
                    submitResult(rawValue)
                }
            }
        }
    }

}