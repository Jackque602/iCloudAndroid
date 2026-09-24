package com.icloudandroid.photos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.UUID

/**
 * EXPERIMENTAL, best-effort native photo upload.
 *
 * Unlike [PhotosApi] (which mirrors years of working open-source prior art for
 * *reading* iCloud Photos — pyicloud, icloudpd, etc.), nobody has published a
 * confirmed-working third-party *upload* implementation for iCloud Photos. This
 * class is a from-scratch reconstruction built on two things: (1) the generic
 * CloudKit Web Services asset-upload mechanism (request an upload URL for a
 * record/field pair, POST the bytes there, then reference the result in a
 * records/modify call), which *is* publicly documented for CloudKit's developer
 * -facing API; and (2) the private CPLMaster/CPLAsset field names [PhotosApi]
 * already confirmed work for reading. Whether Apple's private Photos zone
 * accepts record creation shaped this way from a third-party client is unverified
 * — some private fields (anything suffixed "Enc") may require per-record
 * encryption keys this app has no way to obtain, which would make writes to
 * those fields fail server-side even though this code sends a request.
 *
 * Treat any failure here as the expected, safe outcome, not a bug to chase.
 * The reliable path is [com.icloudandroid.ui.upload.UploadWebViewScreen], which
 * uses Apple's own web app instead of guessing at this private write schema.
 */
class UploadApi(private val http: OkHttpClient, private val serviceRootUrl: String) {

    private val jsonMedia = "application/json".toMediaType()
    private val octetStreamMedia = "application/octet-stream".toMediaType()
    private val recordsBaseUrl = "$serviceRootUrl/database/1/com.apple.photos.cloud/production/private"

    suspend fun uploadPhoto(
        bytes: ByteArray,
        filename: String,
        uti: String,
        width: Int,
        height: Int,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val masterRecordName = "CPLMaster_${UUID.randomUUID()}"
            val assetRecordName = "CPLAsset_${UUID.randomUUID()}"

            val uploadUrl = requestUploadUrl(masterRecordName)
            val assetValue = uploadBytes(uploadUrl, bytes, filename)
            saveRecords(masterRecordName, assetRecordName, assetValue, filename, uti, width, height)
        }
    }

    private fun requestUploadUrl(recordName: String): String {
        val tokens = JSONArray().put(
            JSONObject()
                .put("recordType", "CPLMaster")
                .put("recordName", recordName)
                .put("fieldName", "resOriginalRes")
        )
        val request = Request.Builder()
            .url("$recordsBaseUrl/assets/upload")
            .post(JSONObject().put("tokens", tokens).toString().toRequestBody(jsonMedia))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw UploadException("Could not request an upload slot from iCloud (HTTP ${response.code}).")
            }
            val json = JSONObject(response.body?.string().orEmpty())
            val token = json.optJSONArray("tokens")?.optJSONObject(0)
                ?: throw UploadException("iCloud did not return an upload URL.")
            return token.optString("url").takeIf { it.isNotBlank() }
                ?: throw UploadException("iCloud did not return an upload URL.")
        }
    }

    private fun uploadBytes(url: String, bytes: ByteArray, filename: String): JSONObject {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("0", filename, bytes.toRequestBody(octetStreamMedia))
            .build()
        val request = Request.Builder().url(url).post(multipart).build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw UploadException("Uploading the photo bytes failed (HTTP ${response.code}).")
            }
            val json = JSONObject(response.body?.string().orEmpty())
            return json.optJSONObject("singleFile")
                ?: throw UploadException("iCloud did not confirm the upload.")
        }
    }

    private fun saveRecords(
        masterRecordName: String,
        assetRecordName: String,
        assetValue: JSONObject,
        filename: String,
        uti: String,
        width: Int,
        height: Int,
    ) {
        val zoneId = JSONObject().put("zoneName", "PrimarySync")
        val now = System.currentTimeMillis()

        val masterFields = JSONObject()
            .put("resOriginalRes", assetField(assetValue))
            .put("resOriginalFileType", stringField(uti))
            .put("resOriginalWidth", intField(width))
            .put("resOriginalHeight", intField(height))
            .put("itemType", stringField(uti))
            .put("filenameEnc", stringField(Base64.getEncoder().encodeToString(filename.toByteArray(Charsets.UTF_8))))
            .put("originalOrientation", intField(1))
            .put("dateCreated", timestampField(now))

        val assetFields = JSONObject()
            .put("masterRef", referenceField(masterRecordName))
            .put("assetDate", timestampField(now))
            .put("addedDate", timestampField(now))
            .put("isHidden", intField(0))
            .put("isDeleted", intField(0))
            .put("orientation", intField(1))

        val operations = JSONArray()
            .put(createOperation("CPLMaster", masterRecordName, masterFields, zoneId))
            .put(createOperation("CPLAsset", assetRecordName, assetFields, zoneId))

        val request = Request.Builder()
            .url("$recordsBaseUrl/records/modify")
            .post(JSONObject().put("operations", operations).toString().toRequestBody(jsonMedia))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw UploadException("iCloud rejected the new photo record (HTTP ${response.code}).")
            }
            val json = JSONObject(response.body?.string().orEmpty())
            val records = json.optJSONArray("records") ?: JSONArray()
            for (i in 0 until records.length()) {
                val record = records.optJSONObject(i) ?: continue
                if (record.has("serverErrorCode")) {
                    throw UploadException(record.optString("reason", "iCloud rejected the new photo record."))
                }
            }
        }
    }

    private fun createOperation(recordType: String, recordName: String, fields: JSONObject, zoneId: JSONObject) =
        JSONObject()
            .put("operationType", "create")
            .put(
                "record",
                JSONObject()
                    .put("recordType", recordType)
                    .put("recordName", recordName)
                    .put("fields", fields)
            )
            .put("zoneID", zoneId)

    private fun assetField(value: JSONObject) = JSONObject().put("value", value).put("type", "ASSETID")
    private fun stringField(value: String) = JSONObject().put("value", value).put("type", "STRING")
    private fun intField(value: Int) = JSONObject().put("value", value).put("type", "INT64")
    private fun timestampField(value: Long) = JSONObject().put("value", value).put("type", "TIMESTAMP")
    private fun referenceField(recordName: String) = JSONObject()
        .put("value", JSONObject().put("recordName", recordName).put("action", "NONE"))
        .put("type", "REFERENCE")
}

class UploadException(message: String) : Exception(message)
