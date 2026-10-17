package com.maxrave.domain.data.model.searchResult.spotify

import com.maxrave.domain.data.model.spotify.SpotifyPlaylist
import com.maxrave.domain.data.type.SearchResultType

/**
 * Wraps a [SpotifyPlaylist] (one of the user's own saved playlists, name-matched against the
 * search query) so it can sit in [SearchResultType]'s mixed-type search result list alongside
 * YouTube Music's own songs/videos/albums/playlists. Deliberately a thin wrapper rather than
 * making [SpotifyPlaylist] itself implement [SearchResultType] - that domain model is shared
 * with the Library/Home screens, which have nothing to do with search result types.
 */
data class SpotifyPlaylistSearchResult(
    val playlist: SpotifyPlaylist,
) : SearchResultType {
    override fun objectType(): SearchResultType.Type = SearchResultType.Type.SPOTIFY_PLAYLIST
}
