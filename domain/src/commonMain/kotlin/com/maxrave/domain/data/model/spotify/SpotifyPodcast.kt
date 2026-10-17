package com.maxrave.domain.data.model.spotify

/**
 * A podcast show surfaced on Spotify's own editorial home feed. Spotify playback isn't
 * implemented in this app (same constraint as [SpotifyTrack]/[SpotifyPlaylist]) - this is
 * metadata only, used to show the card and hand off to Spotify's own web page for it.
 */
data class SpotifyPodcast(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val publisherName: String?,
)
