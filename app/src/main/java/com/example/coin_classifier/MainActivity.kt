package com.example.coin_classifier

import android.util.Log
import java.util.concurrent.TimeUnit
import okhttp3.logging.HttpLoggingInterceptor
import android.content.Intent
import android.provider.MediaStore
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.coin_classifier.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class BankNoteResponse(
    val class_name: String,
    val confidence: Float,
    val all_probabilities: Map<String, Float>
)

interface AzureMLService {
    @retrofit2.http.POST("score")
    suspend fun classifyBankNote(
        @retrofit2.http.Header("Authorization") authHeader: String,
        @retrofit2.http.Body requestBody: okhttp3.RequestBody
    ): retrofit2.Response<BankNoteResponse>
}

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var currentPhotoPath: String? = null
    private var selectedImageUri: Uri? = null
    private lateinit var azureMLService: AzureMLService

    private val API_KEY = "1X0I4TjTPiDz2ot3UZNj0uktPVCEIbYZGRfhIZXD4NYQswmI09V3JQQJ99BEAAAAAAAAAAAAINFRAZMLGYmK"
    private val BASE_URL = "https://coin-classifier-ws-rzjve.southeastasia.inference.ml.azure.com/"

    // Register activity results
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            currentPhotoPath?.let { path ->
                selectedImageUri = Uri.fromFile(File(path))
                loadImage(selectedImageUri)
            }
        }
    }

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                loadImage(selectedImageUri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAzureMLService()
        setupClickListeners()
    }

    private fun setupAzureMLService() {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        azureMLService = retrofit.create(AzureMLService::class.java)
    }

    private fun setupClickListeners() {
        binding.cameraButton.setOnClickListener {
            if (checkCameraPermission()) {
                startCamera()
            } else {
                requestCameraPermission()
            }
        }

        binding.galleryButton.setOnClickListener {
            openImagePicker()
        }

        binding.submitButton.setOnClickListener {
            selectedImageUri?.let { uri ->
                classifyImage(uri)
            }
        }
    }

    private fun openImagePicker() {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                // Add multiple paths to search
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png"))
            }

            // Create a chooser to show all available options
            val chooserIntent = Intent.createChooser(intent, "Select Image")
            pickImage.launch(chooserIntent)
        } catch (e: Exception) {
            showError("Error opening image picker: ${e.message}")
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val photoFile = createImageFile()
        photoFile.also {
            val photoURI: Uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                it
            )
            takePicture.launch(photoURI)
        }
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(null)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun loadImage(uri: Uri?) {
        uri?.let {
            try {
                Glide.with(this)
                    .load(it)
                    .centerCrop()
                    .error(R.drawable.image_placeholder_background) // Make sure this drawable exists
                    .into(binding.imageView)
                binding.submitButton.isEnabled = true
            } catch (e: Exception) {
                showError("Error loading image: ${e.message}")
                binding.submitButton.isEnabled = false
            }
        }
    }

    private fun classifyImage(uri: Uri) {
        lifecycleScope.launch {
            try {
                binding.submitButton.isEnabled = false
                binding.progressBar.visibility = View.VISIBLE

                // Log the image size
                val imageSize = ImageUtils.getImageSize(this@MainActivity, uri)
                Log.d("ImageDebug", "Image size: $imageSize bytes")

                val base64Image = ImageUtils.uriToBase64(this@MainActivity, uri)
                Log.d("ImageDebug", "Base64 length: ${base64Image.length}")

                val jsonObject = JSONObject()
                jsonObject.put("image", base64Image)

                val requestBody = jsonObject.toString()
                    .toRequestBody("application/json".toMediaType())

                Log.d("APIDebug", "Sending request to: $BASE_URL")

                val response = azureMLService.classifyBankNote(
                    authHeader = "Bearer $API_KEY",
                    requestBody = requestBody
                )

                if (response.isSuccessful) {
                    response.body()?.let { result ->
                        showResult(result)
                    } ?: showError("Empty response body")
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    Log.e("APIError", "Error response: $errorBody")
                    showError("Classification failed: $errorBody")
                }
            } catch (e: Exception) {
                Log.e("APIError", "Exception during classification", e)
                showError("Error: ${e.message}\nCause: ${e.cause?.message ?: "unknown"}")
            } finally {
                binding.submitButton.isEnabled = true
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showResult(result: BankNoteResponse) {
        val message = """
            Detected: ${result.class_name}
            Confidence: ${(result.confidence * 100).toInt()}%
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Classification Result")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}

object ImageUtils {
    fun uriToBase64(context: android.content.Context, uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes()
        inputStream?.close()

        // If image is too large, compress it
        if ((bytes?.size ?: 0) > 1024 * 1024) { // If larger than 1MB
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes?.size ?: 0)
            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, outputStream)
            return android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)
        }

        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    fun getImageSize(context: android.content.Context, uri: Uri): Long {
        val inputStream = context.contentResolver.openInputStream(uri)
        val size = inputStream?.available()?.toLong() ?: 0
        inputStream?.close()
        return size
    }
}