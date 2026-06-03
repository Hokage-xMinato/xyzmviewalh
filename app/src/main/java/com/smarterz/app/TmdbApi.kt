package com.smarterz.app

import android.util.Log
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class TmdbApi {

    companion object {
        private const val API_KEY = "374ed57246cdd0d51e7f9c7eb9e682f0"
        private const val PROXY = "https://proxy-api-server-woz1.onrender.com/v1/tmdb/3"
        private const val TAG = "TmdbApi"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun searchMulti(query: String, page: Int = 1): TmdbSearchResponse? {
        val url = "$PROXY/search/multi?api_key=$API_KEY&query=${query.encode()}&page=$page"
        return fetch(url) { gson.fromJson(it, TmdbSearchResponse::class.java) }
    }

    fun getMovieDetail(id: Int): TmdbMovieDetail? {
        val url = "$PROXY/movie/$id?api_key=$API_KEY"
        return fetch(url) { gson.fromJson(it, TmdbMovieDetail::class.java) }
    }

    fun getTvDetail(id: Int): TmdbTvDetail? {
        val url = "$PROXY/tv/$id?api_key=$API_KEY"
        return fetch(url) { gson.fromJson(it, TmdbTvDetail::class.java) }
    }

    private fun <T> fetch(url: String, parse: (String) -> T): T? {
        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) parse(body) else null
            } else {
                Log.e(TAG, "HTTP error: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error: ${e.message}")
            null
        }
    }

    private fun String.encode(): String = java.net.URLEncoder.encode(this, "UTF-8")
}
