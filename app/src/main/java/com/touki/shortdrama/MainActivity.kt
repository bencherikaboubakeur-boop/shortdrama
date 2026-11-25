package com.touki.shortdrama

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("touki", MODE_PRIVATE) }
    private var owner = ""
    private var repo = ""
    private var branch = "main"
    private var adminToken: String? = null
    private var movies: List<Movie> = emptyList()
    private var showSaved = false
    private lateinit var root: LinearLayout
    private lateinit var list: LinearLayout
    private val savedIds get() = prefs.getStringSet("saved", emptySet()) ?: emptySet()
    private val pickVideo = 401

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        owner = prefs.getString("owner", "") ?: ""
        repo = prefs.getString("repo", "") ?: ""
        branch = prefs.getString("branch", "main") ?: "main"
        buildScreen()
        refreshMovies()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        }
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setOnClickListener { action() }
    }

    private fun buildScreen() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setBackgroundColor(0xFF09090F.toInt())
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        brand.addView(text("TOUKI", 28f, 0xFFFFFFFF.toInt(), true))
        brand.addView(text("SHORT DRAMA", 11f, 0xFFB7A4FF.toInt(), true))
        header.addView(brand)
        header.addView(button("ADMIN") { adminSetup() })
        root.addView(header)
        root.addView(text("Stories worth staying up for.", 14f, 0xFFAAAAB8.toInt()).apply {
            setPadding(0, dp(12), 0, dp(14))
        })
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabs.addView(button("Discover") { showSaved = false; renderMovies() }, LinearLayout.LayoutParams(0, -2, 1f))
        tabs.addView(button("★ Watch later") { showSaved = true; renderMovies() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(tabs)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun repository(): GitHubRepository? {
        if (owner.isBlank() || repo.isBlank()) return null
        return GitHubRepository(owner, repo, adminToken, branch)
    }

    private fun refreshMovies() {
        if (owner.isBlank() || repo.isBlank()) {
            list.removeAllViews()
            list.addView(text("Connect the public GitHub movie repository using ADMIN → Settings.", 15f, 0xFFFFFFFF.toInt()))
            return
        }
        lifecycleScope.launch {
            try {
                movies = repository()?.loadMovies() ?: emptyList()
                renderMovies()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Could not load movies: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderMovies() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        val visible = if (showSaved) movies.filter { savedIds.contains(it.id) } else movies
        if (visible.isEmpty()) {
            list.addView(text(if (showSaved) "No saved movies yet." else "No published movies yet. Check back soon.", 15f, 0xFFBBBBCC.toInt()).apply {
                setPadding(0, dp(24), 0, dp(12))
            })
        }
        visible.forEach { movie ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setBackgroundColor(0xFF191925.toInt())
            }
            card.addView(text("▶  ${movie.title}", 18f, 0xFFFFFFFF.toInt(), true))
            if (movie.description.isNotBlank()) {
                card.addView(text(movie.description, 13f, 0xFFB8B8C8.toInt()).apply { setPadding(0, dp(5), 0, dp(8)) })
            }
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(button("WATCH") {
                startActivity(Intent(this@MainActivity, PlayerActivity::class.java).putExtra("url", movie.videoUrl).putExtra("title", movie.title))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(button(if (savedIds.contains(movie.id)) "★ Saved" else "☆ Watch later") {
                val updated = savedIds.toMutableSet()
                if (!updated.add(movie.id)) updated.remove(movie.id)
                prefs.edit().putStringSet("saved", updated).apply()
                renderMovies()
            }, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(actions)
            val outer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(8), 0, dp(4))
                addView(card)
            }
            list.addView(outer)
        }
    }

    private fun adminSetup() {
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val ownerInput = EditText(this).apply { hint = "GitHub username / organization"; setText(owner); singleLine = true }
        val repoInput = EditText(this).apply { hint = "Repository name"; setText(repo); singleLine = true }
        val branchInput = EditText(this).apply { hint = "Branch (main)"; setText(branch); singleLine = true }
        val tokenInput = EditText(this).apply {
            hint = "Fine-grained GitHub token"; inputType = 0x00000081
        }
        fields.addView(ownerInput); fields.addView(repoInput); fields.addView(branchInput); fields.addView(tokenInput)
        AlertDialog.Builder(this)
            .setTitle("Admin access")
            .setMessage("Enter a GitHub token with Contents: Read and write access to your repository. The token stays in memory and is not saved.")
            .setView(fields)
            .setPositiveButton("Connect") { _, _ ->
                owner = ownerInput.text.toString().trim()
                repo = repoInput.text.toString().trim()
                branch = branchInput.text.toString().trim().ifBlank { "main" }
                val token = tokenInput.text.toString().trim()
                if (owner.isBlank() || repo.isBlank() || token.isBlank()) {
                    toast("Enter the repository and token.")
                } else {
                    lifecycleScope.launch {
                        try {
                            val candidate = GitHubRepository(owner, repo, token, branch)
                            candidate.validateToken()
                            adminToken = token
                            prefs.edit().putString("owner", owner).putString("repo", repo).putString("branch", branch).apply()
                            toast("Admin connected.")
                            showAdminMenu()
                            refreshMovies()
                        } catch (e: Exception) { toast("Admin login failed: ${e.message}") }
                    }
                }
            }
            .setNeutralButton("Public repository settings") { _, _ ->
                owner = ownerInput.text.toString().trim()
                repo = repoInput.text.toString().trim()
                branch = branchInput.text.toString().trim().ifBlank { "main" }
                prefs.edit().putString("owner", owner).putString("repo", repo).putString("branch", branch).apply()
                refreshMovies()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAdminMenu() {
        AlertDialog.Builder(this)
            .setTitle("Touki Admin")
            .setMessage("Connected to $owner/$repo")
            .setPositiveButton("Add & publish movie") { _, _ -> chooseVideo() }
            .setNeutralButton("Disconnect") { _, _ -> adminToken = null; toast("Admin disconnected.") }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun chooseVideo() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
        }
        startActivityForResult(intent, pickVideo)
    }

    @Deprecated("Legacy activity result used for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickVideo || resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        val name = getFileName(uri)
        val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0) }
        val titleInput = EditText(this).apply { hint = "Movie title"; setText(name.substringBeforeLast(".")) }
        val descInput = EditText(this).apply { hint = "Short description" }
        fields.addView(titleInput); fields.addView(descInput)
        AlertDialog.Builder(this).setTitle("Publish movie").setView(fields)
            .setMessage("The selected video will be uploaded to vids/ in your GitHub repository.")
            .setPositiveButton("Upload & publish") { _, _ ->
                val title = titleInput.text.toString().trim()
                val description = descInput.text.toString().trim()
                uploadVideo(uri, name, title, description)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun uploadVideo(uri: Uri, fileName: String, title: String, description: String) {
        val token = adminToken ?: run { toast("Reconnect as admin first."); return }
        lifecycleScope.launch {
            try {
                toast("Reading video…")
                val bytes = withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
                val movie = GitHubRepository(owner, repo, token, branch).publishMovie(title, description, fileName, bytes)
                toast("Published: ${movie.title}")
                refreshMovies()
            } catch (e: Exception) { toast("Publish failed: ${e.message}") }
        }
    }

    private fun getFileName(uri: Uri): String {
        var result = "video.mp4"
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val index = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && index >= 0) result = c.getString(index) ?: result
        }
        return result
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
