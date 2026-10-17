package com.maxrave.data.mapping

import com.maxrave.domain.data.model.spotify.SpotifyPlaylist
import com.maxrave.domain.data.model.spotify.SpotifyPodcast
import com.maxrave.domain.data.model.spotify.SpotifyTrack
import com.maxrave.spotify.model.response.spotify.playlist.SpotifyHomeResponse
import com.maxrave.spotify.model.response.spotify.playlist.SpotifyLibraryPlaylistsResponse
import com.maxrave.spotify.model.response.spotify.playlist.SpotifyPlaylistTracksResponse
import com.maxrave.spotify.model.response.spotify.search.SpotifySearchResponse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun SpotifyLibraryPlaylistsResponse.toSpotifyPlaylists(): List<SpotifyPlaylist> =
    data
        ?.me
        ?.libraryV3
        ?.items
        ?.mapNotNull { it.item?.data }
        ?.mapNotNull { playlist ->
            val id = playlist.id ?: return@mapNotNull null
            SpotifyPlaylist(
                id = id,
                name = playlist.name.orEmpty(),
                description = playlist.description,
                imageUrl =
                    playlist.images
                        ?.items
                        ?.firstOrNull()
                        ?.sources
                        ?.maxByOrNull { it.width ?: 0 }
                        ?.url,
                ownerName = playlist.ownerV2?.data?.name,
                trackCount = playlist.content?.totalCount,
            )
        }.orEmpty()

/**
 * Picks playlist entries out of Spotify's "home" editorial feed, skipping every other section
 * item type (album/show/track/artist) - see [SpotifyHomeResponse]'s doc comment for why this
 * is hand-walked JSON rather than a typed polymorphic model.
 */
internal fun SpotifyHomeResponse.toFeaturedPlaylists(): List<SpotifyPlaylist> =
    data
        ?.home
        ?.sectionContainer
        ?.sections
        ?.items
        ?.flatMap { it.sectionItems?.items.orEmpty() }
        ?.mapNotNull { it.content?.asSpotifyPlaylistOrNull() }
        .orEmpty()

private fun JsonElement.asSpotifyPlaylistOrNull(): SpotifyPlaylist? {
    val wrapper = this as? JsonObject ?: return null
    val typename = wrapper["__typename"]?.jsonPrimitive?.contentOrNull
    if (typename != "PlaylistResponseWrapper") return null
    val playlist = wrapper["data"]?.jsonObject ?: return null
    val uri = playlist["uri"]?.jsonPrimitive?.contentOrNull ?: return null
    val imageUrl =
        playlist["images"]
            ?.jsonObject
            ?.get("items")
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("sources")
            ?.jsonArray
            ?.maxByOrNull { it.jsonObject["width"]?.jsonPrimitive?.intOrNull ?: 0 }
            ?.jsonObject
            ?.get("url")
            ?.jsonPrimitive
            ?.contentOrNull
    val ownerName =
        playlist["ownerV2"]
            ?.jsonObject
            ?.get("data")
            ?.jsonObject
            ?.get("name")
            ?.jsonPrimitive
            ?.contentOrNull
    return SpotifyPlaylist(
        id = uri.substringAfterLast(':'),
        name = playlist["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        description = null,
        imageUrl = imageUrl,
        ownerName = ownerName,
        trackCount = null,
    )
}

/**
 * Picks podcast shows out of Spotify's "home" editorial feed. Shows don't appear as their own
 * section items here - they ride along on episode cards (`EpisodeOrChapterResponseWrapper` ->
 * `data.podcastV2`, a nested `PodcastResponseWrapper`), e.g. the "Best episodes of the week"
 * section, so an episode's parent show is what's extracted; episodes are deliberately not
 * modeled (same reasoning as [SpotifyHomeResponse]'s doc comment), and duplicate shows sharing
 * more than one featured episode are collapsed to one card.
 */
internal fun SpotifyHomeResponse.toFeaturedPodcasts(): List<SpotifyPodcast> =
    data
        ?.home
        ?.sectionContainer
        ?.sections
        ?.items
        ?.flatMap { it.sectionItems?.items.orEmpty() }
        ?.mapNotNull { it.content?.asSpotifyPodcastOrNull() }
        ?.distinctBy { it.id }
        .orEmpty()

private fun JsonElement.asSpotifyPodcastOrNull(): SpotifyPodcast? {
    val wrapper = this as? JsonObject ?: return null
    val podcastWrapper =
        when (wrapper["__typename"]?.jsonPrimitive?.contentOrNull) {
            "PodcastResponseWrapper" -> wrapper
            "EpisodeOrChapterResponseWrapper" -> wrapper["data"]?.jsonObject?.get("podcastV2")?.jsonObject
            else -> null
        } ?: return null
    if (podcastWrapper["__typename"]?.jsonPrimitive?.contentOrNull != "PodcastResponseWrapper") return null
    val podcast = podcastWrapper["data"]?.jsonObject ?: return null
    val uri = podcast["uri"]?.jsonPrimitive?.contentOrNull ?: return null
    val imageUrl =
        podcast["coverArt"]
            ?.jsonObject
            ?.get("sources")
            ?.jsonArray
            ?.maxByOrNull { it.jsonObject["width"]?.jsonPrimitive?.intOrNull ?: 0 }
            ?.jsonObject
            ?.get("url")
            ?.jsonPrimitive
            ?.contentOrNull
    val publisherName =
        podcast["publisher"]
            ?.jsonObject
            ?.get("name")
            ?.jsonPrimitive
            ?.contentOrNull
    return SpotifyPodcast(
        id = uri.substringAfterLast(':'),
        name = podcast["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        imageUrl = imageUrl,
        publisherName = publisherName,
    )
}

/** Maps a "searchTracks" response (Spotify's whole catalog, not the user's library) to tracks. */
internal fun SpotifySearchResponse.toSpotifyTrackResults(): List<SpotifyTrack> =
    data
        ?.searchV2
        ?.tracksV2
        ?.items
        ?.mapNotNull { it.item?.data }
        ?.mapNotNull { track ->
            val id = track.id ?: return@mapNotNull null
            SpotifyTrack(
                id = id,
                title = track.name.orEmpty(),
                artists = track.artists?.items?.mapNotNull { it.profile?.name }.orEmpty(),
                albumName = track.albumOfTrack?.name,
                albumImageUrl =
                    track.albumOfTrack
                        ?.coverArt
                        ?.sources
                        ?.maxByOrNull { it.width ?: 0 }
                        ?.url,
                durationMs = track.duration?.totalMilliseconds?.toLong(),
                isExplicit = track.contentRating?.label.equals("EXPLICIT", ignoreCase = true),
            )
        }.orEmpty()

/** Maps a "searchPlaylists" response (Spotify's whole catalog, not the user's library) to playlists. */
internal fun SpotifySearchResponse.toSpotifyPlaylistResults(): List<SpotifyPlaylist> =
    data
        ?.searchV2
        ?.playlists
        ?.items
        ?.mapNotNull { it.data }
        ?.mapNotNull { playlist ->
            val id = playlist.id ?: return@mapNotNull null
            SpotifyPlaylist(
                id = id,
                name = playlist.name.orEmpty(),
                description = playlist.description,
                imageUrl =
                    playlist.images
                        ?.items
                        ?.firstOrNull()
                        ?.sources
                        ?.maxByOrNull { it.width ?: 0 }
                        ?.url,
                ownerName = playlist.ownerV2?.data?.name,
                trackCount = playlist.content?.totalCount,
            )
        }.orEmpty()

internal fun SpotifyPlaylistTracksResponse.toSpotifyTracks(): List<SpotifyTrack> =
    data
        ?.playlistV2
        ?.content
        ?.items
        ?.mapNotNull { it.itemV2?.data }
        ?.mapNotNull { track ->
            val id = track.spotifyId ?: return@mapNotNull null
            SpotifyTrack(
                id = id,
                title = track.name.orEmpty(),
                artists = track.artistNames,
                albumName = track.albumOfTrack?.name,
                albumImageUrl =
                    track.albumOfTrack
                        ?.coverArt
                        ?.sources
                        ?.maxByOrNull { it.width ?: 0 }
                        ?.url,
                durationMs = track.durationMs,
                isExplicit = track.isExplicit,
            )
        }.orEmpty()
