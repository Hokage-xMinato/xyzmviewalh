package com.smarterz.app

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

// ─── Data Models ─────────────────────────────────────────────────────────────

data class MediaItem(
    val id: Int,
    val type: String,
    val title: String,
    val detail: String,
    val poster: String?,
    val season: Int = 1,
    val episode: Int = 1
)

data class TmdbSearchResponse(
    val results: List<TmdbSearchResult>?,
    @SerializedName("total_pages") val totalPages: Int = 1
)

data class TmdbSearchResult(
    val id: Int,
    val title: String?,
    val name: String?,
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("poster_path") val posterPath: String?
) {
    val displayTitle get() = title ?: name ?: "Unknown"
    val posterUrl get() = posterPath?.let { "https://image.tmdb.org/t/p/w200$it" }
}

data class TmdbMovieDetail(
    val id: Int,
    val title: String?,
    @SerializedName("poster_path") val posterPath: String?,
    val overview: String?,
    @SerializedName("vote_average") val voteAverage: Double?
) {
    val displayTitle get() = title ?: "Unknown"
    val posterUrl get() = posterPath?.let { "https://image.tmdb.org/t/p/w300$it" }
}

data class TmdbTvDetail(
    val id: Int,
    val name: String?,
    @SerializedName("poster_path") val posterPath: String?,
    val overview: String?,
    @SerializedName("vote_average") val voteAverage: Double?,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int?,
    val seasons: List<TmdbSeason>?
) {
    val displayTitle get() = name ?: "Unknown"
    val posterUrl get() = posterPath?.let { "https://image.tmdb.org/t/p/w300$it" }
}

data class TmdbSeason(
    @SerializedName("season_number") val seasonNumber: Int,
    @SerializedName("episode_count") val episodeCount: Int
)

// ─── Storage ──────────────────────────────────────────────────────────────────

class RecentStorage(context: Context) {
    private val prefs = context.getSharedPreferences("smarterz_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val KEY = "recent_v4"

    fun getAll(): List<MediaItem> = try {
        val type = object : TypeToken<List<MediaItem>>() {}.type
        gson.fromJson(prefs.getString(KEY, "[]"), type) ?: emptyList()
    } catch (e: Exception) { emptyList() }

    fun add(item: MediaItem) {
        val list = getAll().filter { !(it.id == item.id && it.type == item.type) }.toMutableList()
        list.add(0, item)
        if (list.size > 10) list.removeAt(list.size - 1)
        prefs.edit().putString(KEY, gson.toJson(list)).apply()
    }

    fun remove(id: Int, type: String) {
        prefs.edit().putString(KEY, gson.toJson(getAll().filter { !(it.id == id && it.type == type) })).apply()
    }
}

// ─── API ──────────────────────────────────────────────────────────────────────

class TmdbApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private val gson = Gson()
    private val BASE = "https://proxy-api-server-woz1.onrender.com/v1/tmdb/3"
    private val KEY = "374ed57246cdd0d51e7f9c7eb9e682f0"

    fun search(query: String, page: Int = 1): TmdbSearchResponse? =
        fetch("$BASE/search/multi?api_key=$KEY&query=${enc(query)}&page=$page") {
            gson.fromJson(it, TmdbSearchResponse::class.java)
        }

    fun movie(id: Int): TmdbMovieDetail? =
        fetch("$BASE/movie/$id?api_key=$KEY") { gson.fromJson(it, TmdbMovieDetail::class.java) }

    fun tv(id: Int): TmdbTvDetail? =
        fetch("$BASE/tv/$id?api_key=$KEY") { gson.fromJson(it, TmdbTvDetail::class.java) }

    private fun <T> fetch(url: String, parse: (String) -> T): T? = try {
        val resp = client.newCall(Request.Builder().url(url).build()).execute()
        if (resp.isSuccessful) resp.body?.string()?.let { parse(it) } else null
    } catch (e: Exception) { null }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}

// ─── Ad-blocking WebViewClient ────────────────────────────────────────────────

class SmartWebViewClient(private val ctx: Context) : WebViewClient() {
    private val ALLOWED = listOf(
        "vidsrcme.ru", "vidsrc.me", "vidsrc.net", "vidsrc.cc",
        "vidsrc.xyz", "vidsrc.icu", "vidsrc.nl", "vidsrc.in",
        "vidsrc.pm", "vidsrc.to", "vidsrc.su", "2embed.cc"
    )
    private val BLOCKED_SCHEMES = setOf("intent","android-app","market","tel","sms","mailto","whatsapp","tg")
    private val EMPTY = WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))

    private fun allowed(url: String): Boolean {
        return try {
            val host = Uri.parse(url).host?.lowercase() ?: return false
            ALLOWED.any { host == it || host.endsWith(".$it") }
        } catch (e: Exception) { false }
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url?.toString() ?: return EMPTY
        val scheme = request.url.scheme?.lowercase() ?: ""
        if (scheme !in listOf("https","http","data","blob")) return EMPTY
        return if (allowed(url)) null else EMPTY
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return true
        val scheme = request.url.scheme?.lowercase() ?: ""
        if (scheme in BLOCKED_SCHEMES) return true
        return !allowed(url)
    }

    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (url == null) return true
        val scheme = try { Uri.parse(url).scheme?.lowercase() ?: "" } catch (e: Exception) { "" }
        if (scheme in BLOCKED_SCHEMES) return true
        return !allowed(url)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        view?.evaluateJavascript("window.open=function(){return null;};", null)
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?) = true
}

class SmartChromeClient : WebChromeClient() {
    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?) = false
}

// ─── RecyclerView Adapter ─────────────────────────────────────────────────────

class MediaAdapter(
    private var items: List<MediaItem>,
    private val showRemove: Boolean,
    private val onClick: (MediaItem) -> Unit,
    private val onRemove: ((MediaItem) -> Unit)? = null
) : RecyclerView.Adapter<MediaAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val poster: ImageView = v.findViewById(R.id.cardPoster)
        val title: TextView = v.findViewById(R.id.cardTitle)
        val detail: TextView = v.findViewById(R.id.cardDetail)
        val removeBtn: ImageButton = v.findViewById(R.id.removeBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_media_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = items[pos]
        h.title.text = item.title
        h.detail.text = item.detail
        h.removeBtn.visibility = if (showRemove) View.VISIBLE else View.GONE
        Glide.with(h.poster).load(item.poster).placeholder(android.R.drawable.ic_menu_gallery).into(h.poster)
        h.itemView.setOnClickListener { onClick(item) }
        h.removeBtn.setOnClickListener { onRemove?.invoke(item) }
    }

    override fun getItemCount() = items.size

    fun update(newItems: List<MediaItem>) { items = newItems; notifyDataSetChanged() }
}

// ─── MainActivity ─────────────────────────────────────────────────────────────

class MainActivity : AppCompatActivity() {

    // Views
    private lateinit var homeLogo: TextView
    private lateinit var searchInput: EditText
    private lateinit var searchBtn: ImageButton
    private lateinit var homeSection: LinearLayout
    private lateinit var recentRecycler: RecyclerView
    private lateinit var recentEmpty: TextView
    private lateinit var searchSection: LinearLayout
    private lateinit var searchLoading: ProgressBar
    private lateinit var searchRecycler: RecyclerView
    private lateinit var paginationRow: LinearLayout
    private lateinit var prevPageBtn: Button
    private lateinit var nextPageBtn: Button
    private lateinit var pageIndicator: TextView
    private lateinit var detailSection: LinearLayout
    private lateinit var detailLoading: ProgressBar
    private lateinit var detailContent: LinearLayout
    private lateinit var detailPoster: ImageView
    private lateinit var detailTitle: TextView
    private lateinit var detailBadge1: TextView
    private lateinit var detailBadge2: TextView
    private lateinit var detailOverview: TextView
    private lateinit var playButton: Button
    private lateinit var playerModal: FrameLayout
    private lateinit var closePlayer: ImageButton
    private lateinit var playerWebView: WebView
    private lateinit var tvControls: LinearLayout
    private lateinit var movieControls: LinearLayout
    private lateinit var seasonSpinner: Spinner
    private lateinit var episodeSpinner: Spinner
    private lateinit var episodeLabel: TextView
    private lateinit var prevEpBtn: Button
    private lateinit var nextEpBtn: Button

    // State
    private val api = TmdbApi()
    private lateinit var storage: RecentStorage
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var recentAdapter: MediaAdapter
    private lateinit var searchAdapter: MediaAdapter
    private var seasonsMap = mutableMapOf<Int, TmdbSeason>()
    private var currentId = 0
    private var currentType = "tv"
    private var currentSeason = 1
    private var currentEpisode = 1
    private var searchPage = 1
    private var totalPages = 1
    private var lastQuery = ""

    companion object {
        const val EMBED_TV = "https://vidsrcme.ru/embed/tv"
        const val EMBED_MOVIE = "https://vidsrcme.ru/embed/movie"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        storage = RecentStorage(this)
        bindViews()
        setupWebView()
        setupAdapters()
        setupListeners()
        showHome()
    }

    private fun bindViews() {
        homeLogo = findViewById(R.id.homeLogo)
        searchInput = findViewById(R.id.searchInput)
        searchBtn = findViewById(R.id.searchButton)
        homeSection = findViewById(R.id.homeSection)
        recentRecycler = findViewById(R.id.recentRecycler)
        recentEmpty = findViewById(R.id.recentEmpty)
        searchSection = findViewById(R.id.searchSection)
        searchLoading = findViewById(R.id.searchLoading)
        searchRecycler = findViewById(R.id.searchRecycler)
        paginationRow = findViewById(R.id.paginationRow)
        prevPageBtn = findViewById(R.id.prevPageBtn)
        nextPageBtn = findViewById(R.id.nextPageBtn)
        pageIndicator = findViewById(R.id.pageIndicator)
        detailSection = findViewById(R.id.detailSection)
        detailLoading = findViewById(R.id.detailLoading)
        detailContent = findViewById(R.id.detailContent)
        detailPoster = findViewById(R.id.detailPoster)
        detailTitle = findViewById(R.id.detailTitle)
        detailBadge1 = findViewById(R.id.detailBadge1)
        detailBadge2 = findViewById(R.id.detailBadge2)
        detailOverview = findViewById(R.id.detailOverview)
        playButton = findViewById(R.id.playButton)
        playerModal = findViewById(R.id.playerModal)
        closePlayer = findViewById(R.id.closePlayer)
        playerWebView = findViewById(R.id.playerWebView)
        tvControls = findViewById(R.id.tvControls)
        movieControls = findViewById(R.id.movieControls)
        seasonSpinner = findViewById(R.id.seasonSpinner)
        episodeSpinner = findViewById(R.id.episodeSpinner)
        episodeLabel = findViewById(R.id.currentEpisodeLabel)
        prevEpBtn = findViewById(R.id.prevEpisodeBtn)
        nextEpBtn = findViewById(R.id.nextEpisodeBtn)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        playerWebView.webViewClient = SmartWebViewClient(this)
        playerWebView.webChromeClient = SmartChromeClient()
        val s = playerWebView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36"
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(playerWebView, false)
        }
        playerWebView.setDownloadListener { _, _, _, _, _ -> }
    }

    private fun setupAdapters() {
        recentAdapter = MediaAdapter(emptyList(), true,
            onClick = { loadContent(it.id, it.type, it.season, it.episode) },
            onRemove = { storage.remove(it.id, it.type); renderRecent() }
        )
        recentRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recentRecycler.adapter = recentAdapter

        searchAdapter = MediaAdapter(emptyList(), false,
            onClick = { loadContent(it.id, it.type, 1, 1) }
        )
        searchRecycler.layoutManager = GridLayoutManager(this, 3)
        searchRecycler.adapter = searchAdapter
    }

    private fun setupListeners() {
        homeLogo.setOnClickListener { searchInput.setText(""); showHome() }

        searchBtn.setOnClickListener {
            val q = searchInput.text.toString().trim()
            if (q.isNotEmpty()) { hideKeyboard(); doSearch(q) }
        }

        searchInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                val q = searchInput.text.toString().trim()
                if (q.isNotEmpty()) { hideKeyboard(); doSearch(q) }
                true
            } else false
        }

        prevPageBtn.setOnClickListener { if (searchPage > 1) doSearch(lastQuery, searchPage - 1) }
        nextPageBtn.setOnClickListener { if (searchPage < totalPages) doSearch(lastQuery, searchPage + 1) }

        closePlayer.setOnClickListener { closePlayer() }

        prevEpBtn.setOnClickListener {
            if (currentEpisode > 1) currentEpisode--
            else {
                val keys = seasonsMap.keys.sorted()
                val idx = keys.indexOf(currentSeason)
                if (idx > 0) { currentSeason = keys[idx - 1]; currentEpisode = seasonsMap[currentSeason]?.episodeCount ?: 1 }
            }
            buildSpinners(); updateTvFrame()
        }

        nextEpBtn.setOnClickListener {
            val epCount = seasonsMap[currentSeason]?.episodeCount ?: 1
            if (currentEpisode < epCount) currentEpisode++
            else {
                val keys = seasonsMap.keys.sorted()
                val idx = keys.indexOf(currentSeason)
                if (idx < keys.size - 1) { currentSeason = keys[idx + 1]; currentEpisode = 1 }
            }
            buildSpinners(); updateTvFrame()
        }
    }

    // ─── Navigation ──────────────────────────────────────────────────────────

    private fun showHome() {
        homeSection.visibility = View.VISIBLE
        searchSection.visibility = View.GONE
        detailSection.visibility = View.GONE
        renderRecent()
    }

    private fun renderRecent() {
        val items = storage.getAll()
        recentEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        recentRecycler.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        recentAdapter.update(items)
    }

    // ─── Search ──────────────────────────────────────────────────────────────

    private fun doSearch(query: String, page: Int = 1) {
        lastQuery = query; searchPage = page
        homeSection.visibility = View.GONE
        detailSection.visibility = View.GONE
        searchSection.visibility = View.VISIBLE
        searchLoading.visibility = View.VISIBLE
        searchRecycler.visibility = View.GONE
        paginationRow.visibility = View.GONE

        scope.launch {
            val result = withContext(Dispatchers.IO) { api.search(query, page) }
            searchLoading.visibility = View.GONE
            if (result == null) { Toast.makeText(this@MainActivity, "Search failed", Toast.LENGTH_SHORT).show(); return@launch }
            totalPages = result.totalPages
            val items = (result.results ?: emptyList())
                .filter { it.mediaType == "tv" || it.mediaType == "movie" }
                .map { MediaItem(it.id, it.mediaType, it.displayTitle, if (it.mediaType == "tv") "📺 TV" else "🎬 Movie", it.posterUrl) }
            searchAdapter.update(items)
            searchRecycler.visibility = View.VISIBLE
            if (totalPages > 1) {
                paginationRow.visibility = View.VISIBLE
                prevPageBtn.isEnabled = page > 1
                nextPageBtn.isEnabled = page < totalPages
                pageIndicator.text = "$page / $totalPages"
            }
        }
    }

    // ─── Detail ──────────────────────────────────────────────────────────────

    private fun loadContent(id: Int, type: String, season: Int, episode: Int) {
        currentId = id; currentType = type; currentSeason = season; currentEpisode = episode
        seasonsMap.clear()
        homeSection.visibility = View.GONE
        searchSection.visibility = View.GONE
        detailSection.visibility = View.VISIBLE
        detailLoading.visibility = View.VISIBLE
        detailContent.visibility = View.GONE

        scope.launch {
            if (type == "movie") {
                val d = withContext(Dispatchers.IO) { api.movie(id) }
                detailLoading.visibility = View.GONE
                if (d == null) { Toast.makeText(this@MainActivity, "Failed to load", Toast.LENGTH_SHORT).show(); return@launch }
                detailContent.visibility = View.VISIBLE
                detailTitle.text = d.displayTitle
                detailOverview.text = d.overview ?: ""
                detailBadge1.text = "🎬 Movie"
                detailBadge2.text = "⭐ ${d.voteAverage?.let { "%.1f".format(it) } ?: "?"}"
                d.posterUrl?.let { Glide.with(this@MainActivity).load(it).into(detailPoster) }
                playButton.setOnClickListener { openPlayer("movie") }
            } else {
                val d = withContext(Dispatchers.IO) { api.tv(id) }
                detailLoading.visibility = View.GONE
                if (d == null) { Toast.makeText(this@MainActivity, "Failed to load", Toast.LENGTH_SHORT).show(); return@launch }
                d.seasons?.forEach { s -> if (s.seasonNumber > 0 && s.episodeCount > 0) seasonsMap[s.seasonNumber] = s }
                detailContent.visibility = View.VISIBLE
                detailTitle.text = d.displayTitle
                detailOverview.text = d.overview ?: ""
                detailBadge1.text = "📺 ${d.numberOfSeasons ?: "?"} Season(s)"
                detailBadge2.text = "⭐ ${d.voteAverage?.let { "%.1f".format(it) } ?: "?"}"
                d.posterUrl?.let { Glide.with(this@MainActivity).load(it).into(detailPoster) }
                playButton.setOnClickListener { openPlayer("tv") }
            }
        }
    }

    // ─── Player ──────────────────────────────────────────────────────────────

    private fun openPlayer(type: String) {
        playerModal.visibility = View.VISIBLE
        if (type == "movie") {
            tvControls.visibility = View.GONE
            movieControls.visibility = View.VISIBLE
            playerWebView.loadUrl("$EMBED_MOVIE/$currentId")
            storage.add(MediaItem(currentId, "movie", detailTitle.text.toString(), "Movie", null))
        } else {
            tvControls.visibility = View.VISIBLE
            movieControls.visibility = View.GONE
            buildSpinners()
            updateTvFrame()
        }
    }

    private fun closePlayer() {
        playerModal.visibility = View.GONE
        playerWebView.loadUrl("about:blank")
        playerWebView.stopLoading()
    }

    private fun buildSpinners() {
        val seasons = seasonsMap.values.sortedBy { it.seasonNumber }
        if (seasons.isEmpty()) return

        val seasonLabels = seasons.map { "Season ${it.seasonNumber} (${it.episodeCount}ep)" }
        seasonSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, seasonLabels).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val sIdx = seasons.indexOfFirst { it.seasonNumber == currentSeason }.coerceAtLeast(0)
        seasonSpinner.setSelection(sIdx, false)
        seasonSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val ns = seasons[pos].seasonNumber
                if (ns != currentSeason) { currentSeason = ns; currentEpisode = 1; buildEpisodeSpinner(); updateTvFrame() }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        buildEpisodeSpinner()
    }

    private fun buildEpisodeSpinner() {
        val epCount = seasonsMap[currentSeason]?.episodeCount ?: return
        val labels = (1..epCount).map { "Episode $it" }
        episodeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        episodeSpinner.setSelection((currentEpisode - 1).coerceAtLeast(0), false)
        episodeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val ne = pos + 1
                if (ne != currentEpisode) { currentEpisode = ne; updateTvFrame() }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun updateTvFrame() {
        playerWebView.loadUrl("$EMBED_TV/$currentId/$currentSeason/$currentEpisode")
        episodeLabel.text = "S$currentSeason E$currentEpisode"
        storage.add(MediaItem(currentId, "tv", detailTitle.text.toString(), "S$currentSeason E$currentEpisode", null, currentSeason, currentEpisode))
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            playerModal.visibility == View.VISIBLE -> closePlayer()
            detailSection.visibility == View.VISIBLE -> showHome()
            searchSection.visibility == View.VISIBLE -> { searchInput.setText(""); showHome() }
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        playerWebView.destroy()
    }
}
