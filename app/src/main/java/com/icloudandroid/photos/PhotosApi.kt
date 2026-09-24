package com.icloudandroid.photos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * Talks to Apple's CloudKit-backed Photos web service (the same private API
 * https://www.icloud.com/photos uses). There is no published schema for this,
 * so parsing is defensive: unexpected shapes are skipped per-record instead of
 * failing the whole page, since this endpoint can change without notice.
 */
class PhotosApi(private val http: OkHttpClient, private val serviceRootUrl: String) {

    private val jsonMedia = "application/json".toMediaType()

    suspend fun queryPage(offset: Int, pageSize: Int): Result<PhotoPage> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put(
                "query",
                JSONObject().apply {
                    put("recordType", "CPLAssetByAddedDate")
                    put(
                        "filterBy",
                        JSONArray().apply {
                            put(
                                JSONObject().apply {
                                    put("fieldName", "startRank")
                                    put("comparator", "EQUALS")
                                    put(
                                        "fieldValue",
                                        JSONObject().put("type", "INT64").put("value", offset)
                                    )
                                }
                            )
                            put(
                                JSONObject().apply {
                                    put("fieldName", "direction")
                                    put("comparator", "EQUALS")
                                    put(
                                        "fieldValue",
                                        JSONObject().put("type", "STRING").put("value", "DESCENDING")
                                    )
                                }
                            )
                        }
                    )
                }
            )
            put("resultsLimit", pageSize)
            put("desiredKeys", JSONArray(DESIRED_KEYS))
            put("zoneID", JSONObject().put("zoneName", "PrimarySync"))
        }

        val request = Request.Builder()
            .url("$serviceRootUrl/database/1/com.apple.photos.cloud/production/private/records/query")
            .post(body.toString().toRequestBody(jsonMedia))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()

        runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        PhotosApiException("iCloud Photos request failed (HTTP ${response.code}).")
                    )
                }
                val json = JSONObject(response.body?.string().orEmpty())
                val records = json.optJSONArray("records") ?: JSONArray()
                val items = mutableListOf<PhotoItem>()
                for (i in 0 until records.length()) {
                    val record = records.optJSONObject(i) ?: continue
                    parseRecord(record)?.let { items.add(it) }
                }
                Result.success(
                    PhotoPage(
                        items = items,
                        nextOffset = offset + records.length(),
                        hasMore = records.length() >= pageSize
                    )
                )
            }
        }.getOrElse { Result.failure(it) }
    }

    private fun parseRecord(record: JSONObject): PhotoItem? {
        val fields = record.optJSONObject("fields") ?: return null
        val recordName = record.optString("recordName").takeIf { it.isNotBlank() } ?: return null

        if (fieldInt(fields, "isDeleted") == 1L || fieldInt(fields, "isExpunged") == 1L) return null
        if (fieldInt(fields, "isHidden") == 1L) return null

        val thumb = firstAvailableAsset(fields, THUMB_FIELD_PRIORITY)
        val full = firstAvailableAsset(fields, FULL_FIELD_PRIORITY)
        if (thumb == null && full == null) return null

        val itemType = fieldString(fields, "itemType").orEmpty()
        val isVideo = itemType.contains("video", ignoreCase = true)

        val dateMillis = fieldInt(fields, "assetDate")
            ?: fieldInt(fields, "addedDate")
            ?: record.optJSONObject("created")?.optLong("timestamp")
            ?: 0L

        val filename = fieldString(fields, "filenameEnc")?.let { decodeBase64Utf8(it) }
            ?: "$recordName.jpg"

        return PhotoItem(
            recordName = recordName,
            filename = filename,
            isVideo = isVideo,
            dateMillis = dateMillis,
            thumbnailUrl = thumb?.downloadUrl,
            fullResUrl = full?.downloadUrl ?: thumb?.downloadUrl,
            width = (full ?: thumb)?.width ?: 0,
            height = (full ?: thumb)?.height ?: 0,
        )
    }

    private data class AssetField(val downloadUrl: String, val width: Int, val height: Int)

    private fun firstAvailableAsset(fields: JSONObject, prefixesInPriorityOrder: List<String>): AssetField? {
        for (prefix in prefixesInPriorityOrder) {
            val value = fields.optJSONObject("${prefix}Res")?.optJSONObject("value") ?: continue
            val url = value.optString("downloadURL").takeIf { it.isNotBlank() } ?: continue
            return AssetField(
                downloadUrl = url,
                width = fieldInt(fields, "${prefix}Width")?.toInt() ?: 0,
                height = fieldInt(fields, "${prefix}Height")?.toInt() ?: 0,
            )
        }
        return null
    }

    private fun fieldString(fields: JSONObject, key: String): String? =
        fields.optJSONObject(key)?.opt("value") as? String

    private fun fieldInt(fields: JSONObject, key: String): Long? {
        val value = fields.optJSONObject(key)?.opt("value") ?: return null
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    private fun decodeBase64Utf8(value: String): String? = runCatching {
        String(Base64.getDecoder().decode(value), Charsets.UTF_8)
    }.getOrNull()

    companion object {
        private val THUMB_FIELD_PRIORITY = listOf("resJPEGThumb", "resJPEGMed", "resJPEGLarge")
        private val FULL_FIELD_PRIORITY = listOf(
            "resJPEGFull", "resOriginal", "resJPEGLarge", "resJPEGMed", "resVidFull", "resVidMed"
        )

        private val DESIRED_KEYS = listOf(
            "resJPEGFullWidth", "resJPEGFullHeight", "resJPEGFullFileType", "resJPEGFullRes",
            "resJPEGLargeWidth", "resJPEGLargeHeight", "resJPEGLargeFileType", "resJPEGLargeRes",
            "resJPEGMedWidth", "resJPEGMedHeight", "resJPEGMedFileType", "resJPEGMedRes",
            "resJPEGThumbWidth", "resJPEGThumbHeight", "resJPEGThumbFileType", "resJPEGThumbRes",
            "resVidFullWidth", "resVidFullHeight", "resVidFullFileType", "resVidFullRes",
            "resVidMedWidth", "resVidMedHeight", "resVidMedFileType", "resVidMedRes",
            "resVidSmallWidth", "resVidSmallHeight", "resVidSmallFileType", "resVidSmallRes",
            "resOriginalWidth", "resOriginalHeight", "resOriginalFileType", "resOriginalRes",
            "itemType", "filenameEnc",
            "isDeleted", "isExpunged", "isHidden",
            "recordName", "recordType", "assetDate", "addedDate",
        )
    }
}

class PhotosApiException(message: String) : Exception(message)
