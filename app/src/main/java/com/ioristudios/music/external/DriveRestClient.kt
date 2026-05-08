package com.ioristudios.music.external

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

class DriveRestClient(private val accessToken: String) {
    fun listAppDataFiles(query: String): List<DriveFile> {
        val files = mutableListOf<DriveFile>()
        var pageToken: String? = null
        
        do {
            val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
            val fields = URLEncoder.encode("nextPageToken,files(id,name,modifiedTime,size)", Charsets.UTF_8.name())
            val url = "$DRIVE_FILES?spaces=appDataFolder&q=$encodedQuery&fields=$fields&pageSize=1000" +
                    (pageToken?.let { "&pageToken=$it" } ?: "")
            
            val json = String(request("GET", url), Charsets.UTF_8)
            val root = JSONObject(json)
            val items = root.optJSONArray("files") ?: JSONArray()
            
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                files.add(
                    DriveFile(
                        id = item.getString("id"),
                        name = item.optString("name"),
                        modifiedTime = item.optString("modifiedTime"),
                        size = item.optLong("size", 0L)
                    )
                )
            }
            pageToken = root.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null)
        
        return files
    }

    fun uploadTextFile(name: String, mimeType: String, text: String): DriveFile {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return uploadMultipart(
            name = name,
            mimeType = mimeType,
            contentLength = bytes.size.toLong(),
            input = bytes.inputStream()
        )
    }

    fun uploadBinaryFile(
        name: String,
        mimeType: String,
        contentLength: Long,
        input: InputStream
    ): DriveFile = uploadMultipart(name, mimeType, contentLength, input)

    fun downloadFile(fileId: String, output: OutputStream) {
        val connection = openConnection("$DRIVE_FILES/$fileId?alt=media", "GET")
        val code = connection.responseCode
        if (code in 200..299) {
            connection.inputStream.use { input -> input.copyTo(output) }
        } else {
            val detail = connection.errorStream?.use { String(it.readAllBytesCompat()) }?.take(400)
            throw IllegalStateException("Download failed with HTTP $code: $detail")
        }
        connection.disconnect()
    }

    fun downloadText(fileId: String): String {
        val out = ByteArrayOutputStream()
        downloadFile(fileId, out)
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    private fun uploadMultipart(
        name: String,
        mimeType: String,
        contentLength: Long,
        input: InputStream
    ): DriveFile {
        val boundary = "iori-${System.currentTimeMillis()}"
        val connection = openConnection(
            url = "$DRIVE_UPLOAD?uploadType=multipart&fields=id,name,modifiedTime,size",
            method = "POST"
        )
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        connection.doOutput = true
        connection.setChunkedStreamingMode(0)

        connection.outputStream.use { output ->
            val metadata = JSONObject()
                .put("name", name)
                .put("parents", JSONArray().put("appDataFolder"))
                .toString()
            output.writeUtf8("--$boundary\r\n")
            output.writeUtf8("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            output.writeUtf8(metadata)
            output.writeUtf8("\r\n--$boundary\r\n")
            output.writeUtf8("Content-Type: $mimeType\r\n")
            if (contentLength > 0) output.writeUtf8("Content-Length: $contentLength\r\n")
            output.writeUtf8("\r\n")
            input.use { it.copyTo(output) }
            output.writeUtf8("\r\n--$boundary--\r\n")
        }

        val response = String(connection.checkedResponse(), Charsets.UTF_8)
        val json = JSONObject(response)
        return DriveFile(
            id = json.getString("id"),
            name = json.optString("name", name),
            modifiedTime = json.optString("modifiedTime"),
            size = json.optLong("size", contentLength)
        )
    }

    private fun request(method: String, url: String): ByteArray {
        val connection = openConnection(url, method)
        return connection.checkedResponse()
    }

    private fun openConnection(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }

    private fun HttpURLConnection.checkedResponse(): ByteArray {
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        val body = stream?.use { it.readAllBytesCompat() } ?: ByteArray(0)
        disconnect()
        if (code !in 200..299) {
            val detail = String(body, Charsets.UTF_8).take(400)
            throw IllegalStateException("Drive request failed with HTTP $code: $detail")
        }
        return body
    }

    private fun OutputStream.writeUtf8(value: String) {
        write(value.toByteArray(Charsets.UTF_8))
    }

    private fun InputStream.readAllBytesCompat(): ByteArray {
        val buffer = ByteArrayOutputStream()
        copyTo(buffer)
        return buffer.toByteArray()
    }

    companion object {
        private const val DRIVE_FILES = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
    }
}

data class DriveFile(
    val id: String,
    val name: String,
    val modifiedTime: String,
    val size: Long
)
