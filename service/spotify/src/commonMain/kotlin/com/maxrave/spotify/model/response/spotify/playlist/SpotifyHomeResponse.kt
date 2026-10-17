package com.maxrave.spotify.model.response.spotify.playlist

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Response shape for the "home" persisted GraphQL query (Spotify's own editorial/featured
 * feed) - reverse-engineered from the same unofficial client this fork's whole Spotify
 * integration is based on. Each home section's items are themselves a polymorphic union
 * (playlist, album, show, track, artist...) keyed by a `__typename` discriminator; rather
 * than replicate that whole hierarchy (the reference implementation itself has only ever
 * decoded the playlist case), [SectionItemContent.content] is left as a raw [JsonElement]
 * and picked apart manually wherever this response is mapped - see `SpotifyMapping.kt`.
 */
@Serializable
data class SpotifyHomeResponse(
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val home: Home? = null,
    )

    @Serializable
    data class Home(
        val sectionContainer: SectionContainer? = null,
    )

    @Serializable
    data class SectionContainer(
        val sections: SectionList? = null,
    )

    @Serializable
    data class SectionList(
        val items: List<HomeSectionItem>? = null,
    )

    @Serializable
    data class HomeSectionItem(
        val uri: String? = null,
        val sectionItems: SectionItems? = null,
    )

    @Serializable
    data class SectionItems(
        val items: List<SectionItemContent>? = null,
    )

    @Serializable
    data class SectionItemContent(
        val uri: String? = null,
        val content: JsonElement? = null,
    )
}
