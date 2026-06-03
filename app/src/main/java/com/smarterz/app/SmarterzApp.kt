package com.smarterz.app

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.smarterz.app.databinding.ActivityMainBinding
import kotlinx.coroutines.*

class SmarterzApp(
    private val context: Context,
    private val binding: ActivityMainBinding
) {
    companion object {
        const val EMBED_TV = "https://vidsrcme.ru/embed/tv"
        const val EMBED_MOVIE = "https://vidsrcme.ru/embed/movie"
    }

    private val api = TmdbApi()
    private val storage = RecentStorage(context)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var currentMediaType = "tv"
    private var currentId = 0
    private var currentSeason = 1
    private var currentEpisode = 1
    private var seasonsMap = mutableMapOf<Int, TmdbSeason>()
    private var searchPage = 1
    private var totalSearchPages = 1
    private var lastSearchQuery = ""

    private lateinit var recentAdapter: MediaAdapter
    private lateinit var searchAdapter: MediaAdapter

    // State flags
    private var playerOpen = false
    private var detailOpen = false
    private var searchOpen = false

    // ─── Init ────────────────────────────────────────────────────────────────

    fun initialize() {
        setupRecyclerViews()
        setupWebView()
        setupPlayerControls()
        renderRecent()
    }

    private fun setupRecyclerViews() {
        // Recent row — horizontal
        recentAdapter = MediaAdapter(
            items = emptyList(),
            showRemoveButton = true,
            onItemClick = { item ->
                loadContent(item.id, item.type, item.season, item.episode)
            },
            onRemoveClick = { item ->
                storage.remove(item.id, item.type)
                renderRecent()
            }
        )
        binding.recentRecycler.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = recentAdapter
        }

        // Search grid — vertical
        searchAdapter = MediaAdapter(
            items = emptyList(),
            showRemoveButton = false,
            onItemClick = { item ->
                loadContent(item.id, item.type, 1, 1)
            }
        )
        binding.searchRecycler.apply {
            layoutManager = GridLayoutManager(context, 3)
            adapter = searchAdapter
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val wv = binding.playerWebView
        wv.webViewClient = SmartWebViewClient(context)
        wv.webChromeClient = SmartChromeClient()

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            // Disable saving form data / passwords
            saveFormData = false
            // Prevent geolocation prompts
            setGeolocationEnabled(false)
            // Force desktop UA so player doesn't show a mobile-only error
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36"
        }

        // Accept cookies (needed for player auth) but block third-party cookies
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, false) // block 3rd-party tracking cookies
        }

        // Kill WebView's built-in download manager (ads try to trigger downloads)
        wv.setDownloadListener { _, _, _, _, _ ->
            // Block all downloads silently
        }
    }

    private fun setupPlayerControls() {
        binding.closePlayer.setOnClickListener { closePlayer() }

        binding.prevEpisodeBtn.setOnClickListener {
            if (currentMediaType != "tv") return@setOnClickListener
            if (currentEpisode > 1) {
                currentEpisode--
            } else {
                val keys = seasonsMap.keys.sorted()
                val idx = keys.indexOf(currentSeason)
                if (idx > 0) {
                    currentSeason = keys[idx - 1]
                    currentEpisode = seasonsMap[currentSeason]?.episodeCount ?: 1
                }
            }
            buildDropdowns()
            updateTVIframe()
        }

        binding.nextEpisodeBtn.setOnClickListener {
            if (currentMediaType != "tv") return@setOnClickListener
            val season = seasonsMap[currentSeason] ?: return@setOnClickListener
            if (currentEpisode < season.episodeCount) {
                currentEpisode++
            } else {
                val keys = seasonsMap.keys.sorted()
                val idx = keys.indexOf(currentSeason)
                if (idx < keys.size - 1) {
                    currentSeason = keys[idx + 1]
                    currentEpisode = 1
                }
            }
            buildDropdowns()
            updateTVIframe()
        }
    }

    // ─── Navigation ──────────────────────────────────────────────────────────

    fun showHome() {
        binding.homeSection.isVisible = true
        binding.searchSection.isVisible = false
        binding.detailSection.isVisible = false
        detailOpen = false
        searchOpen = false
        renderRecent()
    }

    fun isPlayerOpen() = playerOpen
    fun isDetailOpen() = detailOpen
    fun isSearchOpen() = searchOpen

    // ─── Recent ──────────────────────────────────────────────────────────────

    private fun renderRecent() {
        val items = storage.getAll()
        if (items.isEmpty()) {
            binding.recentEmpty.isVisible = true
            binding.recentRecycler.isVisible = false
        } else {
            binding.recentEmpty.isVisible = false
            binding.recentRecycler.isVisible = true
            recentAdapter.updateItems(items)
        }
    }

    // ─── Search ──────────────────────────────────────────────────────────────

    fun performSearch(query: String, page: Int = 1) {
        lastSearchQuery = query
        searchPage = page
        binding.homeSection.isVisible = false
        binding.detailSection.isVisible = false
        binding.searchSection.isVisible = true
        binding.searchLoading.isVisible = true
        binding.searchRecycler.isVisible = false
        binding.paginationRow.isVisible = false
        searchOpen = true
        detailOpen = false

        scope.launch {
            val result = withContext(Dispatchers.IO) { api.searchMulti(query, page) }
            binding.searchLoading.isVisible = false

            if (result == null) {
                Toast.makeText(context, "Search failed. Check internet.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            totalSearchPages = result.totalPages
            val items = (result.results ?: emptyList())
                .filter { it.mediaType == "tv" || it.mediaType == "movie" }
                .map { r ->
                    MediaItem(
                        id = r.id,
                        type = r.mediaType,
                        title = r.displayTitle,
                        detail = if (r.mediaType == "tv") "📺 TV Show" else "🎬 Movie",
                        poster = r.posterUrl,
                        season = 1,
                        episode = 1
                    )
                }

            if (items.isEmpty()) {
                Toast.makeText(context, "No results found.", Toast.LENGTH_SHORT).show()
            }

            searchAdapter.updateItems(items)
            binding.searchRecycler.isVisible = true
            binding.paginationRow.isVisible = totalSearchPages > 1

            // Pagination
            binding.prevPageBtn.isEnabled = page > 1
            binding.nextPageBtn.isEnabled = page < totalSearchPages
            binding.pageIndicator.text = "$page / $totalSearchPages"
        }
    }

    // ─── Detail ──────────────────────────────────────────────────────────────

    fun loadContent(id: Int, type: String, season: Int = 1, episode: Int = 1) {
        currentId = id
        currentMediaType = type
        currentSeason = season
        currentEpisode = episode
        seasonsMap.clear()

        binding.homeSection.isVisible = false
        binding.searchSection.isVisible = false
        binding.detailSection.isVisible = true
        binding.detailLoading.isVisible = true
        binding.detailContent.isVisible = false
        detailOpen = true
        searchOpen = false

        scope.launch {
            if (type == "movie") {
                val detail = withContext(Dispatchers.IO) { api.getMovieDetail(id) }
                binding.detailLoading.isVisible = false
                if (detail == null) {
                    Toast.makeText(context, "Failed to load details.", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                binding.detailContent.isVisible = true
                binding.detailTitle.text = detail.displayTitle
                binding.detailOverview.text = detail.overview ?: ""
                binding.detailBadge1.text = "🎬 Movie"
                binding.detailBadge2.text = "⭐ ${detail.voteAverage?.let { String.format("%.1f", it) } ?: "?"}"
                loadDetailPoster(detail.posterUrl)
                binding.playButton.setOnClickListener { openPlayer("movie") }
            } else {
                val detail = withContext(Dispatchers.IO) { api.getTvDetail(id) }
                binding.detailLoading.isVisible = false
                if (detail == null) {
                    Toast.makeText(context, "Failed to load details.", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                detail.seasons?.forEach { s ->
                    if (s.seasonNumber > 0 && s.episodeCount > 0) {
                        seasonsMap[s.seasonNumber] = s
                    }
                }
                binding.detailContent.isVisible = true
                binding.detailTitle.text = detail.displayTitle
                binding.detailOverview.text = detail.overview ?: ""
                binding.detailBadge1.text = "📺 ${detail.numberOfSeasons ?: "?"} Season(s)"
                binding.detailBadge2.text = "⭐ ${detail.voteAverage?.let { String.format("%.1f", it) } ?: "?"}"
                loadDetailPoster(detail.posterUrl)
                binding.playButton.setOnClickListener { openPlayer("tv") }
            }
        }
    }

    private fun loadDetailPoster(url: String?) {
        if (url != null) {
            com.bumptech.glide.Glide.with(context)
                .load(url)
                .placeholder(R.drawable.placeholder_poster)
                .into(binding.detailPoster)
        }
    }

    // ─── Player ──────────────────────────────────────────────────────────────

    private fun openPlayer(type: String) {
        playerOpen = true
        binding.playerModal.isVisible = true

        if (type == "movie") {
            binding.tvControls.isVisible = false
            binding.movieControls.isVisible = true
            binding.playerWebView.loadUrl("$EMBED_MOVIE/$currentId")

            storage.add(
                MediaItem(
                    id = currentId,
                    type = "movie",
                    title = binding.detailTitle.text.toString(),
                    detail = "Movie",
                    poster = null
                )
            )
        } else {
            binding.tvControls.isVisible = true
            binding.movieControls.isVisible = false
            buildDropdowns()
            updateTVIframe()
        }
    }

    fun closePlayer() {
        playerOpen = false
        binding.playerModal.isVisible = false
        binding.playerWebView.loadUrl("about:blank")
        binding.playerWebView.stopLoading()
    }

    private fun buildDropdowns() {
        val seasons = seasonsMap.values.sortedBy { it.seasonNumber }
        if (seasons.isEmpty()) return

        // Season spinner
        val seasonLabels = seasons.map { "Season ${it.seasonNumber} (${it.episodeCount}ep)" }
        val seasonAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, seasonLabels)
        seasonAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.seasonSpinner.adapter = seasonAdapter

        val currentSeasonIndex = seasons.indexOfFirst { it.seasonNumber == currentSeason }.coerceAtLeast(0)
        binding.seasonSpinner.setSelection(currentSeasonIndex, false)

        binding.seasonSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val newSeason = seasons[position].seasonNumber
                if (newSeason != currentSeason) {
                    currentSeason = newSeason
                    currentEpisode = 1
                    buildEpisodeDropdown()
                    updateTVIframe()
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        buildEpisodeDropdown()
    }

    private fun buildEpisodeDropdown() {
        val season = seasonsMap[currentSeason] ?: return
        val episodeLabels = (1..season.episodeCount).map { "Episode $it" }
        val episodeAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, episodeLabels)
        episodeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.episodeSpinner.adapter = episodeAdapter

        binding.episodeSpinner.setSelection((currentEpisode - 1).coerceAtLeast(0), false)

        binding.episodeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val newEpisode = position + 1
                if (newEpisode != currentEpisode) {
                    currentEpisode = newEpisode
                    updateTVIframe()
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun updateTVIframe() {
        val url = "$EMBED_TV/$currentId/$currentSeason/$currentEpisode"
        binding.playerWebView.loadUrl(url)
        binding.currentEpisodeLabel.text = "S$currentSeason E$currentEpisode"

        storage.add(
            MediaItem(
                id = currentId,
                type = "tv",
                title = binding.detailTitle.text.toString(),
                detail = "S$currentSeason E$currentEpisode",
                poster = null,
                season = currentSeason,
                episode = currentEpisode
            )
        )
    }

    // ─── Pagination ──────────────────────────────────────────────────────────

    fun onPrevPage() {
        if (searchPage > 1) performSearch(lastSearchQuery, searchPage - 1)
    }

    fun onNextPage() {
        if (searchPage < totalSearchPages) performSearch(lastSearchQuery, searchPage + 1)
    }

    fun destroy() {
        scope.cancel()
        binding.playerWebView.destroy()
    }
}
