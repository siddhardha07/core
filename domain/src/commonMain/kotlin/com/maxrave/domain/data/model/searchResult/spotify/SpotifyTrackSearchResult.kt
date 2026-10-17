package com.maxrave.domain.data.model.searchResult.spotify

import com.maxrave.domain.data.model.spotify.SpotifyTrack
import com.maxrave.domain.data.type.SearchResultType

/**
 * Wraps a [SpotifyTrack] from Spotify's own catalog search so it can sit in
 * [SearchResultType]'s mixed-type search result list. Not playable as-is - tapping one must
 * resolve it to a YouTube Music match first (see `SpotifyLibraryRepository.resolveTrack`),
 * same as every other Spotify track in this app.
 */
data class SpotifyTrackSearchResult(
    val track: SpotifyTrack,
) : SearchResultType {
    override fun objectType(): SearchResultType.Type = SearchResultType.Type.SPOTIFY_SONG
}
