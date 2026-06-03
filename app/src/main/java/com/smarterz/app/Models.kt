package com.smarterz.app

import com.google.gson.annotations.SerializedName

data class MediaItem(
    val id: Int,
    val type: String, // "movie" or "tv"
    val title: String,
    val detail: String,
    val poster: String?,
    val season: Int = 1,
    val episode: Int = 1
)

data class TmdbSearchResponse(
    val results: List<TmdbSearchResult>?,
    @SerializedName("total_pages") val totalPages: Int = 1,
    @SerializedName("total_results") val totalResults: Int = 0,
    val page: Int = 1
)

data class TmdbSearchResult(
    val id: Int,
    val title: String?,
    val name: String?,
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("poster_path") val posterPath: String?,
    val overview: String?,
    @SerializedName("vote_average") val voteAverage: Double?
) {
    val displayTitle: String get() = title ?: name ?: "Unknown"
    val posterUrl: String? get() = posterPath?.let { "https://image.tmdb.org/t/p/w200$it" }
}

data class TmdbMovieDetail(
    val id: Int,
    val title: String?,
    val name: String?,
    val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("vote_average") val voteAverage: Double?,
    @SerializedName("release_date") val releaseDate: String?,
    val runtime: Int?
) {
    val displayTitle: String get() = title ?: name ?: "Unknown"
    val posterUrl: String? get() = posterPath?.let { "https://image.tmdb.org/t/p/w300$it" }
}

data class TmdbTvDetail(
    val id: Int,
    val name: String?,
    val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("vote_average") val voteAverage: Double?,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int?,
    val seasons: List<TmdbSeason>?
) {
    val displayTitle: String get() = name ?: "Unknown"
    val posterUrl: String? get() = posterPath?.let { "https://image.tmdb.org/t/p/w300$it" }
}

data class TmdbSeason(
    @SerializedName("season_number") val seasonNumber: Int,
    @SerializedName("episode_count") val episodeCount: Int,
    val name: String?
)
