package com.melodrive.youtube

import okhttp3.OkHttpClient
import okhttp3.Request as OkRequest
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

class NewPipeDownloader : Downloader() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val builder = OkRequest.Builder().url(request.url())

        // copy headers from NewPipeExtractor request
        request.headers()?.forEach { (key, values) ->
            values.forEach { value -> builder.addHeader(key, value) }
        }

        val okRequest = when (request.httpMethod()) {
            "POST" -> {
                val body = (request.dataToSend() ?: ByteArray(0))
                    .toRequestBody("application/json".toMediaType())
                builder.post(body).build()
            }
            "HEAD" -> builder.head().build()
            else -> builder.get().build()
        }

        val response = client.newCall(okRequest).execute()
        val body = response.body?.string() ?: ""
        val headers = response.headers.toMultimap()

        return Response(
            response.code,
            response.message,
            headers,
            body,
            response.request.url.toString(),
        )
    }
}
