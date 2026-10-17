package com.maxrave.data.repository

import com.maxrave.data.mapping.toFeaturedPlaylists
import com.maxrave.data.mapping.toFeaturedPodcasts
import com.maxrave.data.mapping.toSpotifyPlaylistResults
import com.maxrave.data.mapping.toSpotifyPlaylists
import com.maxrave.data.mapping.toSpotifyTrackResults
import com.maxrave.data.mapping.toSpotifyTracks
import com.maxrave.data.spotify.SpotifyTokenProvider
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.spotify.SpotifyPlaylist
import com.maxrave.domain.data.model.spotify.SpotifyPodcast
import com.maxrave.domain.data.model.spotify.SpotifyTrack
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.repository.SearchRepository
import com.maxrave.domain.repository.SpotifyLibraryRepository
import com.maxrave.domain.utils.LocalResource
import com.maxrave.domain.utils.Resource
import com.maxrave.domain.utils.buildYoutubeSearchQuery
import com.maxrave.domain.utils.rankSongMatches
import com.maxrave.domain.utils.toTrack
import com.maxrave.domain.utils.wrapResultResource
import com.maxrave.logger.Logger
import com.maxrave.spotify.Spotify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class SpotifyLibraryRepositoryImpl(
    private val spotify: Spotify,
    private val spotifyTokenProvider: SpotifyTokenProvider,
    private val dataStoreManager: DataStoreManager,
    private val searchRepository: SearchRepository,
) : SpotifyLibraryRepository {
    // In-memory only - Spotify tracks aren't persisted, so a resolved match only needs to
    // survive for the current session (re-opening the playlist next launch just re-resolves).
    private val resolvedTrackCache = mutableMapOf<String, Track?>()

    override fun isLoggedIn(): Flow<Boolean> = dataStoreManager.spdc.map { it.isNotEmpty() }

    override fun getUserPlaylists(
        offset: Int,
        limit: Int,
    ): Flow<LocalResource<List<SpotifyPlaylist>>> =
        wrapResultResource {
            val tokens =
                spotifyTokenProvider.getValidTokens(dataStoreManager)
                    ?: return@wrapResultResource Result.failure(Exception("Not logged in to Spotify"))
            spotify
                .getUserPlaylists(tokens.personalToken, tokens.clientToken, offset, limit)
                .map { it.toSpotifyPlaylists() }
        }

    override fun getFeaturedPlaylists(limit: Int): Flow<LocalResource<List<SpotifyPlaylist>>> =
        wrapResultResource {
            val spT = dataStoreManager.spT.first()
            if (spT.isEmpty()) {
                // Logged in before sp_t capture existed - nothing to show until they log in again.
                return@wrapResultResource Result.success(emptyList())
            }
            val tokens =
                spotifyTokenProvider.getValidTokens(dataStoreManager)
                    ?: return@wrapResultResource Result.failure(Exception("Not logged in to Spotify"))
            spotify
                .getHome(tokens.personalToken, tokens.clientToken, spT, limit)
                .map { it.toFeaturedPlaylists() }
        }

    override fun getFeaturedPodcasts(limit: Int): Flow<LocalResource<List<SpotifyPodcast>>> =
        wrapResultResource {
            val spT = dataStoreManager.spT.first()
            if (spT.isEmpty()) {
                return@wrapResultResource Result.success(emptyList())
            }
            val tokens =
                spotifyTokenProvider.getValidTokens(dataStoreManager)
                    ?: return@wrapResultResource Result.failure(Exception("Not logged in to Spotify"))
            spotify
                .getHome(tokens.personalToken, tokens.clientToken, spT, limit)
                .map { it.toFeaturedPodcasts() }
        }

    override fun getPlaylistTracks(
        playlistId: String,
        offset: Int,
        limit: Int,
    ): Flow<LocalResource<List<SpotifyTrack>>> =
        wrapResultResource {
            val tokens =
                spotifyTokenProvider.getValidTokens(dataStoreManager)
                    ?: return@wrapResultResource Result.failure(Exception("Not logged in to Spotify"))
            spotify
                .getPlaylistTracks(playlistId, tokens.personalToken, tokens.clientToken, offset, limit)
                .map { it.toSpotifyTracks() }
        }

    override fun searchTracks(
        query: String,
        limit: Int,
    ): Flow<LocalResource<List<SpotifyTrack>>> =
        wrapResultResource {
            val tokens =
                spotifyTokenProvider.getValidTokens(dataStoreManager)
                    ?: return@wrapResultResource Result.failure(Exception("Not logged in to Spotify"))
            spotify
                .searchTracks(query, tokens.personalToken, tokens.clientToken, limit)
                .map { it.toSpotifyTrackResults() }
        }

    override fun searchPlaylists(
        query: String,
        limit: Int,
    ): Flow<LocalResource<List<SpotifyPlaylist>>> =
        wrapResultResource {
            val tokens =
                spotifyTokenProvider.getValidTokens(dataStoreManager)
                    ?: return@wrapResultResource Result.failure(Exception("Not logged in to Spotify"))
            spotify
                .searchPlaylists(query, tokens.personalToken, tokens.clientToken, limit)
                .map { it.toSpotifyPlaylistResults() }
        }

    override suspend fun resolveTrack(track: SpotifyTrack): Track? {
        resolvedTrackCache[track.id]?.let { return it }
        if (resolvedTrackCache.containsKey(track.id)) {
            // Cached negative result (no match found last time).
            return null
        }

        val query = buildYoutubeSearchQuery(track.title, track.artists.firstOrNull())
        val candidates =
            when (val resource = searchRepository.getSearchDataSong(query).first()) {
                is Resource.Success -> resource.data
                is Resource.Error -> null
            }.orEmpty()
        val bestMatch = rankSongMatches(track, candidates)
        if (bestMatch == null) {
            Logger.w("SpotifyLibraryRepository", "No YouTube Music match for Spotify track: ${track.title}")
        }
        val resolved = bestMatch?.toTrack()
        resolvedTrackCache[track.id] = resolved
        return resolved
    }
}
