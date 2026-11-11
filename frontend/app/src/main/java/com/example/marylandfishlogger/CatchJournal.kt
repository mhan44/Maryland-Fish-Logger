package com.example.marylandfishlogger

import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

//Stores a catch record retrieved from SQLite
data class CatchRecord(
    val id: Int,
    val species: String?,
    val predictedSpecies: String?,
    val confidence: Double?,
    val length: Double?,
    val weight: Double?,
    val date: String?,
    val location: String?,
    val lure: String?,
    val notes: String?,
    val imageUrl: String?
)

//Read an optional JSON string
private fun JSONObject.optionalText(key: String): String? {
    return if (isNull(key)) null else getString(key)
}

//Read an optional JSON number
private fun JSONObject.optionalNumber(key: String): Double? {
    return if (isNull(key)) null else getDouble(key)
}

//Retrieve saved catches from the Jetson database
suspend fun fetchCatches(): List<CatchRecord> =
    withContext(Dispatchers.IO) {

        val connection = (
                URL("$BASE_URL/catches").openConnection()
                        as HttpURLConnection
                ).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
            }

        try {
            if (connection.responseCode != 200) {
                error("HTTP ${connection.responseCode}")
            }

            //Read the JSON array returned by FastAPI
            val response = connection.inputStream
                .bufferedReader()
                .use { it.readText() }

            val jsonArray = JSONArray(response)

            //Convert JSON objects into Kotlin CatchRecord objects
            (0 until jsonArray.length()).map { index ->
                val item = jsonArray.getJSONObject(index)

                CatchRecord(
                    id = item.getInt("id"),
                    species = item.optionalText("species"),
                    predictedSpecies = item.optionalText("predicted_species"),
                    confidence = item.optionalNumber("confidence"),
                    length = item.optionalNumber("length_inches"),
                    weight = item.optionalNumber("weight_lbs"),
                    date = item.optionalText("date_caught"),
                    location = item.optionalText("location"),
                    lure = item.optionalText("lure"),
                    notes = item.optionalText("notes"),
                    imageUrl = item.optionalText("image_url")
                )
            }

        } finally {
            connection.disconnect()
        }
    }

@Composable
fun CatchJournalScreen(onBack: () -> Unit) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    //Store the retrieved catch records
    var catches by remember {
        mutableStateOf<List<CatchRecord>>(emptyList())
    }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    //Retrieve catches from the Jetson
    suspend fun loadCatches() {
        isLoading = true
        errorMessage = null

        try {
            catches = fetchCatches()
        } catch (e: Exception) {
            errorMessage = "Failed to load catches: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    //Automatically load catches when opening this screen
    LaunchedEffect(Unit) {
        loadCatches()
    }

    //Display catches in a vertically scrollable list
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "My Catch Journal",
                style = MaterialTheme.typography.headlineMedium
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onBack) {
                    Text("Back")
                }

                Button(
                    onClick = {
                        scope.launch { loadCatches() }
                    },
                    enabled = !isLoading
                ) {
                    Text("Refresh")
                }
            }
        }

        item {
            if (isLoading) {
                CircularProgressIndicator()
            }

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            if (!isLoading && errorMessage == null && catches.isEmpty()) {
                Text("No catches saved yet.")
            }
        }

        //Create a card for each saved catch
        items(catches, key = { it.id }) { catch ->

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        catch.species ?: "Unidentified Fish",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text("Catch #${catch.id}")

                    catch.date?.let {
                        Text("Date: $it")
                    }

                    catch.length?.let {
                        Text("Length: $it inches")
                    }

                    catch.weight?.let {
                        Text("Weight: $it lbs")
                    }

                    catch.location?.let {
                        Text("Location: $it")
                    }

                    catch.lure?.let {
                        Text("Lure: $it")
                    }

                    catch.notes?.takeIf { it.isNotBlank() }?.let {
                        Text("Notes: $it")
                    }


                    catch.imageUrl?.takeIf { it.isNotBlank() }?.let { imagePath ->

                        //Retrieve annotated image from the Jetson backend
                        val imageUrl = if (
                            imagePath.startsWith("http://") ||
                            imagePath.startsWith("https://")
                        ) {
                            imagePath
                        } else {
                            "$BASE_URL$imagePath"
                        }

                        //Display fish photograph directly in the catch journal
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = "Photograph of ${catch.species ?: "unidentified fish"}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentScale = ContentScale.Fit
                        )

                        //Open full image in the browser
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(imageUrl)
                                )
                                context.startActivity(intent)
                            }
                        ) {
                            Text("View Full Image")
                        }
                    }
                }
            }
        }
    }
}

