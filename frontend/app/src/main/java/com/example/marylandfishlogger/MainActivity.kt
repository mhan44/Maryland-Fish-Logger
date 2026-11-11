
package com.example.marylandfishlogger

import android.os.Bundle
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

//Jetson IP
private const val BASE_URL = "http://10.200.52.53:8000"

/*
- Activity = Entry point for user interaction, usually a screen. MainActivity is the initial launch activity when the android app opens.
- MainActivity inherits from ComponentActivity() denoted by the colon :
- override fun onCreate() indicates that the code block runs automatically once android creates the current activity
- super.onCreate() calls the parent class's initialization, in this case, ComponentActivity() which is crucial for system initialization
- setContent{} sets the UI using jetpack compose
- MaterialTheme{} applies Material library designs and colors
*/
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                FishLoggerHome()
            }
        }
    }
}

//Stores the model prediction returned by the backend
data class FishPrediction(
    val species: String,
    val confidence: Double?,
    val image: Bitmap
)

//Upload selected fish image to the Jetson backend for prediction
suspend fun predictFish(context: Context, imageUri: Uri): FishPrediction =
    withContext(Dispatchers.IO) {

        //Determine the image format
        val contentType = context.contentResolver.getType(imageUri)
            ?: "image/jpeg"

        val extension = when (contentType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> error("Unsupported image format: $contentType")
        }

        //Multipart boundary separates sections in the HTTP request
        val boundary = "FishLogger${UUID.randomUUID()}"

        //Create HTTP connection to our /predict endpoint
        val connection = (
                URL("$BASE_URL/predict").openConnection()
                        as HttpURLConnection
                ).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 60000
                setRequestProperty(
                    "Content-Type",
                    "multipart/form-data; boundary=$boundary"
                )
                setChunkedStreamingMode(65536)
            }

        try {
            //Send the selected image through an HTTP POST request
            connection.outputStream.use { output ->

                val header =
                    "--$boundary\r\n" +
                            "Content-Disposition: form-data; name=\"file\"; filename=\"fish.$extension\"\r\n" +
                            "Content-Type: $contentType\r\n\r\n"

                output.write(header.toByteArray())

                context.contentResolver.openInputStream(imageUri)
                    ?.use { input ->
                        input.copyTo(output)
                    }
                    ?: error("Could not read selected image")

                output.write(
                    "\r\n--$boundary--\r\n".toByteArray()
                )
            }

            //Check whether FastAPI successfully processed the request
            val responseCode = connection.responseCode

            if (responseCode != 200) {
                val errorMessage = connection.errorStream
                    ?.bufferedReader()
                    ?.use { it.readText() }

                error("HTTP $responseCode: $errorMessage")
            }

            //Read JSON response returned by the Jetson backend
            val response = connection.inputStream
                .bufferedReader()
                .use { it.readText() }

            val json = JSONObject(response)

            val species = if (json.isNull("species")) {
                "Unidentified"
            } else {
                json.getString("species")
            }

            val confidence = if (json.isNull("confidence")) {
                null
            } else {
                json.getDouble("confidence")
            }

            //Retrieve the annotated image generated by YOLO
            val imageUrl = json.getString("image_url")
            val imageConnection = (
                    URL("$BASE_URL$imageUrl").openConnection()
                            as HttpURLConnection
                    ).apply {
                    connectTimeout = 10000
                    readTimeout = 30000
                }

            val bitmap = try {
                if (imageConnection.responseCode != 200) {
                    error("Could not retrieve annotated image")
                }

                imageConnection.inputStream.use {
                    BitmapFactory.decodeStream(it)
                } ?: error("Could not decode annotated image")
            } finally {
                imageConnection.disconnect()
            }

            //Return prediction data to the frontend
            FishPrediction(species, confidence, bitmap)

        } finally {
            connection.disconnect()
        }
    }

/*
- @Composable denotes that this function is used for the user interface, includes components like
Button(), Text(), Column(), and Row()
- A modifier changes the layout/appearance of an element; fillMaxSize() occupies free width/height,
padding(24.dp) adds spacing within the layout
- dp stands for density-independent pixels, which is a standardized unit across different pixel densities/resolutions
- var = defines variables
- remember = retains/remembers the state across recompositions (Re-executions by compose of the relevant UI code when observed state changes.)
- by = delegation, state value becomes accessible directly
- fun = function
- fun(x : y): String, where :y defines the parameter datatype of x and :String is the return type
*/
@Composable
fun FishLoggerHome() {

    var status by remember { mutableStateOf("Not connected") } //relevant UI is updated automatically when status changes
    val scope = rememberCoroutineScope() //Coroutine allows Kotlin to run asynchronous, suspendable operations.

    val context = LocalContext.current

    //Store selected image
    var selectedImage by remember {
        mutableStateOf<Uri?>(null)
    }

    //Store predicted fish information
    var prediction by remember {
        mutableStateOf<FishPrediction?>(null)
    }

    //Track prediction status
    var predictionStatus by remember {
        mutableStateOf("No prediction yet")
    }

    //Track whether prediction is processing
    var isLoading by remember {
        mutableStateOf(false)
    }

    //Open Android photo picker
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImage = uri
            prediction = null
            predictionStatus = "Image selected successfully"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize() //fill
            .verticalScroll(rememberScrollState())
            .padding(24.dp), //pad inner spacing of the column
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Maryland Fish Logger",
            style = MaterialTheme.typography.headlineMedium
        )

        Text("Identify fish and record your catches.")

        Button(
            onClick = { //When button is clicked, execute code
                scope.launch { //Launch an asynchronous connection operation since otherwise a long connection time can stall the app
                    status = "Connecting..."

                    status = try {
                        withContext(Dispatchers.IO) {
                            //Jetson IP
                            val connection = (
                                    URL("$BASE_URL/").openConnection()
                                            as HttpURLConnection
                                    ).apply {
                                    connectTimeout = 5000
                                    readTimeout = 5000
                                }

                            try {
                                connection.inputStream
                                    .bufferedReader()
                                    .use { it.readText() }
                            } finally {
                                connection.disconnect()
                            }
                        }
                    } catch (e: Exception) {
                        "Connection failed: ${e.message}"
                    }
                }
            }
        ) {
            Text("Test Backend Connection")
        }

        Text("Backend status: $status")

        Button(
            onClick = {
                photoPicker.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            },
            enabled = !isLoading
        ) {
            Text("Select Fish Photo")
        }

        Text(
            text = if (selectedImage != null)
                "Image selected successfully"
            else
                "No image selected"
        )

        //Send the selected image to FastAPI for YOLO inference
        Button(
            onClick = {
                selectedImage?.let { uri ->
                    scope.launch {
                        isLoading = true
                        prediction = null
                        predictionStatus = "Identifying fish..."

                        try {
                            prediction = predictFish(context, uri)
                            predictionStatus = "Prediction complete"
                        } catch (e: Exception) {
                            predictionStatus = "Error: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                }
            },
            enabled = selectedImage != null && !isLoading
        ) {
            Text("Identify Fish")
        }

        //Show progress indicator while waiting for the Jetson
        if (isLoading) {
            CircularProgressIndicator()
        }

        Text(predictionStatus)

        //Display the predicted species, confidence score, and annotated image
        prediction?.let { result ->

            Text(
                text = "Species: ${result.species}",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = "Confidence: " +
                        (result.confidence?.let {
                            "%.1f%%".format(it * 100)
                        } ?: "N/A")
            )

            Image(
                bitmap = result.image.asImageBitmap(),
                contentDescription = "Fish image with bounding box and confidence",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}
