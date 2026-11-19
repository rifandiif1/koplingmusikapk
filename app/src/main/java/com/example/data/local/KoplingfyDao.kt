package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class PlaylistWithCountRow(
    val id: Long,
    val name: String,
    val description: String,
    val coverUrl: String?,
    val createdAt: Long,
    val trackCount: Int
)

@Dao
interface KoplingfyDao {

    // ---- Tracks & Likes ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrack(track: SavedTrackEntity)

    @Query("SELECT * FROM saved_tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: String): SavedTrackEntity?

    @Query("SELECT * FROM saved_tracks WHERE isLiked = 1 ORDER BY likedAt DESC")
    fun observeLikedTracks(): Flow<List<SavedTrackEntity>>

    @Query("SELECT id FROM saved_tracks WHERE isLiked = 1")
    fun observeLikedTrackIds(): Flow<List<String>>

    @Query("SELECT * FROM saved_tracks WHERE isLocal = 1 ORDER BY addedAt DESC")
    fun observeLocalTracks(): Flow<List<SavedTrackEntity>>

    @Query("UPDATE saved_tracks SET isLiked = :isLiked, likedAt = :likedAt WHERE id = :trackId")
    suspend fun updateLikeStatus(trackId: String, isLiked: Boolean, likedAt: Long)

    @Query("DELETE FROM saved_tracks WHERE id = :trackId AND isLocal = 1")
    suspend fun deleteLocalTrack(trackId: String)

    // ---- Playlists ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, description = :description WHERE id = :playlistId")
    suspend fun updatePlaylist(playlistId: Long, name: String, description: String)

    @Query("UPDATE playlists SET coverUrl = :coverUrl WHERE id = :playlistId")
    suspend fun updatePlaylistCover(playlistId: Long, coverUrl: String?)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query(
        """
        SELECT p.id, p.name, p.description, p.coverUrl, p.createdAt,
               COUNT(pt.trackId) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_tracks pt ON p.id = pt.playlistId
        GROUP BY p.id
        ORDER BY p.createdAt DESC
        """
    )
    fun observePlaylistsWithCount(): Flow<List<PlaylistWithCountRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrackToPlaylist(crossRef: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String)

    @Query(
        """
        SELECT st.* FROM saved_tracks st
        INNER JOIN playlist_tracks pt ON st.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.addedAt DESC
        """
    )
    fun observeTracksForPlaylist(playlistId: Long): Flow<List<SavedTrackEntity>>

    // ---- Play History & Stats ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayHistory(entry: PlayHistoryEntity)

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT 100")
    fun observePlayHistory(): Flow<List<PlayHistoryEntity>>

    @Query("DELETE FROM play_history")
    suspend fun clearPlayHistory()

    // ---- Search History ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(item: SearchHistoryEntity)

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 12")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearchQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()
}
