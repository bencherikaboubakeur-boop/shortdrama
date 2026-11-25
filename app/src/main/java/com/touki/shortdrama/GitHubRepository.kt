package com.touki.shortdrama

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GitHubRepository(
    private val owner: String,
    private val repo: String,
    private val token: String? = null,
    private val branch: String = "main"
) {
    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        authenticated: Boolean = false
    ): Pair<Int, String> {
        val url = URL("https://api.github.com$path")
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 25000
            readTimeout = 60000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Touki-Short-Drama-Android")
            if (authenticated && !token.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        if (body != null) c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.let { s ->
            BufferedReader(InputStreamReader(s)).use { it.readText() }
        } ?: ""
        c.disconnect()
        return code to text
    }

    private fun repoPath(path: String) = "/repos/$owner/$repo/contents/$path"

    suspend fun loadMovies(): List<Movie> = withContext(Dispatchers.IO) {
        val url = "https://raw.githubusercontent.com/$owner/$repo/$branch/catalog.json"
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 30000
        }
        try {
            if (c.responseCode !in 200..299) return@withContext emptyList()
            val text = c.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONObject(text).optJSONArray("movies") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (!o.optBoolean("published", false)) continue
                    add(
                        Movie(
                            id = o.optString("id"),
                            title = o.optString("title", "Untitled"),
                            description = o.optString("description"),
                            videoUrl = o.optString("videoUrl"),
                            posterUrl = o.optString("posterUrl")
                        )
                    )
                }
            }
        } finally { c.disconnect() }
    }

    suspend fun validateToken(): String = withContext(Dispatchers.IO) {
        val (code, body) = request("GET", "/user", authenticated = true)
        if (code !in 200..299) error("GitHub login failed ($code). Check your token.")
        JSONObject(body).optString("login")
    }

    suspend fun publishMovie(title: String, description: String, fileName: String, bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            require(bytes.size <= 90 * 1024 * 1024) {
                "Choose a video smaller than 90 MB. GitHub's Contents API has a 100 MB file limit."
            }
            require(title.isNotBlank()) { "Movie title is required." }
            val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val uniqueName = "${System.currentTimeMillis()}_$safeName"
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val uploadBody = JSONObject()
                .put("message", "Add Touki video: $title")
                .put("content", encoded)
                .put("branch", branch)
            val (uploadCode, uploadResponse) = request(
                "PUT", repoPath("vids/$uniqueName"), uploadBody, authenticated = true
            )
            if (uploadCode !in 200..299) {
                error("Video upload failed ($uploadCode): ${errorMessage(uploadResponse)}")
            }

            val catalogPath = repoPath("catalog.json")
            val (getCode, getBody) = request("GET", catalogPath, authenticated = true)
            val catalog: JSONObject
            var sha: String? = null
            if (getCode == 200) {
                val response = JSONObject(getBody)
                sha = response.optString("sha").takeIf { it.isNotBlank() }
                val decoded = Base64.decode(response.optString("content").replace("\n", ""), Base64.DEFAULT)
                catalog = JSONObject(String(decoded, Charsets.UTF_8))
            } else if (getCode == 404) {
                catalog = JSONObject().put("movies", JSONArray())
            } else {
                error("Could not read catalog.json ($getCode): ${errorMessage(getBody)}")
            }

            val movies = catalog.optJSONArray("movies") ?: JSONArray()
            val videoUrl = "https://raw.githubusercontent.com/$owner/$repo/$branch/vids/$uniqueName"
            val movie = JSONObject()
                .put("id", uniqueName)
                .put("title", title.trim())
                .put("description", description.trim())
                .put("videoUrl", videoUrl)
                .put("posterUrl", "")
                .put("published", true)
            movies.put(movie)
            catalog.put("movies", movies)

            val catalogBody = JSONObject()
                .put("message", "Publish Touki movie: $title")
                .put("content", Base64.encodeToString(catalog.toString(2).toByteArray(), Base64.NO_WRAP))
                .put("branch", branch)
            if (sha != null) catalogBody.put("sha", sha)
            val (saveCode, saveResponse) = request("PUT", catalogPath, catalogBody, authenticated = true)
            if (saveCode !in 200..299) {
                error("Video uploaded, but catalog update failed ($saveCode): ${errorMessage(saveResponse)}")
            }
            Movie(uniqueName, title.trim(), description.trim(), videoUrl)
        }

    private fun errorMessage(json: String): String =
        try { JSONObject(json).optString("message", json).take(300) } catch (_: Exception) { json.take(300) }
}
