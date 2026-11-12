package com.example.marylandfishlogger

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import java.time.LocalDate
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

/*
- Stores the editable fields of a catch record.
- Using String values allows Android text fields to
  handle user input before converting measurements.
*/
data class CatchEditForm(
    val species: String,
    val length: String,
    val weight: String,
    val date: String,
    val location: String,
    val lure: String,
    val notes: String
)

//Update an existing catch record using FastAPI
suspend fun updateCatchRecord(
    catchId: Int,
    form: CatchEditForm
) = withContext(Dispatchers.IO) {

    //Convert optional measurements into numeric values
    fun parseMeasurement(value: String, field: String): Double? {
        if (value.isBlank()) return null

        val number = value.trim().toDoubleOrNull()
            ?: error("$field must be a valid number")

        require(number.isFinite() && number > 0) {
            "$field must be greater than zero"
        }

        return number
    }

    val lengthValue = parseMeasurement(form.length, "Length")
    val weightValue = parseMeasurement(form.weight, "Weight")

    //Validate date formatting
    val dateValue = form.date.trim().takeIf { it.isNotEmpty() }

    if (dateValue != null) {
        try {
            LocalDate.parse(dateValue)
        } catch (e: Exception) {
            error("Date must use YYYY-MM-DD")
        }
    }

    //Create JSON containing only user-editable fields
    val json = JSONObject().apply {
        put(
            "species",
            form.species.trim().takeIf { it.isNotEmpty() }
                ?: JSONObject.NULL
        )
        put("length_inches", lengthValue ?: JSONObject.NULL)
        put("weight_lbs", weightValue ?: JSONObject.NULL)
        put("date_caught", dateValue ?: JSONObject.NULL)
        put("location", form.location.trim())
        put("lure", form.lure.trim())
        put("notes", form.notes.trim())
    }

    //Connect to PUT /catches/{id}
    val connection = (
            URL("$BASE_URL/catches/$catchId").openConnection()
                    as HttpURLConnection
            ).apply {
            requestMethod = "PUT"
            doOutput = true
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Content-Type", "application/json")
        }

    try {
        //Send updated catch information to FastAPI
        connection.outputStream.use { output ->
            output.write(json.toString().toByteArray(Charsets.UTF_8))
        }

        val responseCode = connection.responseCode

        if (responseCode !in 200..299) {
            val message = connection.errorStream
                ?.bufferedReader()
                ?.use { it.readText() }

            error("Update failed: HTTP $responseCode - $message")
        }

    } finally {
        connection.disconnect()
    }
}

//Delete an existing catch record using FastAPI
suspend fun deleteCatchRecord(catchId: Int) =
    withContext(Dispatchers.IO) {

        //Connect to DELETE /catches/{id}
        val connection = (
                URL("$BASE_URL/catches/$catchId").openConnection()
                        as HttpURLConnection
                ).apply {
                requestMethod = "DELETE"
                connectTimeout = 10000
                readTimeout = 10000
            }

        try {
            val responseCode = connection.responseCode

            if (responseCode !in 200..299) {
                val message = connection.errorStream
                    ?.bufferedReader()
                    ?.use { it.readText() }

                error("Delete failed: HTTP $responseCode - $message")
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

    //Track which catch is being edited
    var editingCatch by remember {
        mutableStateOf<CatchRecord?>(null)
    }

    //Track which catch is pending deletion
        var deletingCatch by remember {
            mutableStateOf<CatchRecord?>(null)
        }

    //Prevent duplicate update/delete requests
        var isSubmitting by remember {
            mutableStateOf(false)
        }

    //Display errors inside the confirmation dialogs
        var dialogError by remember {
            mutableStateOf<String?>(null)
        }

    //Display success messages in the journal
        var actionStatus by remember {
            mutableStateOf("")
        }

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

            //Display update/delete results
            if (actionStatus.isNotEmpty()) {
                Text(actionStatus)
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
                    //Actions for managing an existing catch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        //Open editing form with existing catch details
                        Button(
                            onClick = {
                                editingCatch = catch
                                dialogError = null
                                actionStatus = ""
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isSubmitting
                        ) {
                            Text("Edit")
                        }

                        //Request confirmation before deleting
                        OutlinedButton(
                            onClick = {
                                deletingCatch = catch
                                dialogError = null
                                actionStatus = ""
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isSubmitting
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }

//Display editing dialog for the selected catch
    editingCatch?.let { catch ->

        EditCatchDialog(
            catch = catch,
            isSaving = isSubmitting,
            errorMessage = dialogError,

            onDismiss = {
                if (!isSubmitting) editingCatch = null
            },

            onSave = { form ->

                scope.launch {
                    isSubmitting = true
                    dialogError = null

                    try {
                        //Update catch through FastAPI
                        updateCatchRecord(catch.id, form)

                        //Close the dialog
                        editingCatch = null

                        //Reload records to display updated values
                        loadCatches()

                        actionStatus =
                            "Catch #${catch.id} updated successfully"

                    } catch (e: Exception) {
                        dialogError = e.message ?: "Update failed"

                    } finally {
                        isSubmitting = false
                    }
                }
            }
        )
    }

//Display confirmation dialog before deleting a catch
    deletingCatch?.let { catch ->

        AlertDialog(
            onDismissRequest = {
                if (!isSubmitting) deletingCatch = null
            },

            title = {
                Text("Delete Catch?")
            },

            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Are you sure you want to delete " +
                                "Catch #${catch.id}?"
                    )

                    Text("This action cannot be undone.")

                    dialogError?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },

            confirmButton = {
                TextButton(
                    enabled = !isSubmitting,
                    onClick = {
                        scope.launch {
                            isSubmitting = true
                            dialogError = null

                            try {
                                //Delete record from SQLite through FastAPI
                                deleteCatchRecord(catch.id)

                                //Close confirmation dialog
                                deletingCatch = null

                                //Refresh journal after deletion
                                loadCatches()

                                actionStatus =
                                    "Catch #${catch.id} deleted successfully"

                            } catch (e: Exception) {
                                dialogError =
                                    e.message ?: "Delete failed"

                            } finally {
                                isSubmitting = false
                            }
                        }
                    }
                ) {
                    Text("Delete")
                }
            },

            dismissButton = {
                TextButton(
                    onClick = { deletingCatch = null },
                    enabled = !isSubmitting
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

/*
- Displays an editing form for an existing catch.
- Fields are initialized using the saved record.
- Changes are not submitted until Save Changes is pressed.
*/
@Composable
fun EditCatchDialog(
    catch: CatchRecord,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (CatchEditForm) -> Unit
) {

    //Initialize fields using existing catch data
    var species by remember(catch.id) {
        mutableStateOf(catch.species ?: "")
    }

    var length by remember(catch.id) {
        mutableStateOf(catch.length?.toString() ?: "")
    }

    var weight by remember(catch.id) {
        mutableStateOf(catch.weight?.toString() ?: "")
    }

    var date by remember(catch.id) {
        mutableStateOf(catch.date ?: "")
    }

    var location by remember(catch.id) {
        mutableStateOf(catch.location ?: "")
    }

    var lure by remember(catch.id) {
        mutableStateOf(catch.lure ?: "")
    }

    var notes by remember(catch.id) {
        mutableStateOf(catch.notes ?: "")
    }

    AlertDialog(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        },

        title = {
            Text("Edit Catch #${catch.id}")
        },

        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                OutlinedTextField(
                    value = species,
                    onValueChange = { species = it },
                    label = { Text("Species") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = length,
                    onValueChange = { length = it },
                    label = { Text("Length (inches)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Weight (lbs)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = lure,
                    onValueChange = { lure = it },
                    label = { Text("Lure / Bait") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                //Show validation or backend errors
                errorMessage?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (isSaving) {
                    CircularProgressIndicator()
                }
            }
        },

        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    //Pass the form values to the API handler
                    onSave(
                        CatchEditForm(
                            species = species,
                            length = length,
                            weight = weight,
                            date = date,
                            location = location,
                            lure = lure,
                            notes = notes
                        )
                    )
                }
            ) {
                Text("Save Changes")
            }
        },

        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving
            ) {
                Text("Cancel")
            }
        }
    )
}


