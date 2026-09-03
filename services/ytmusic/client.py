import os
import re
from typing import Optional, List, Dict, Any
from ytmusicapi import YTMusic
from models import (
    ArtistRef,
    AlbumRef,
    RemoteTrack,
    RemoteAlbumSummary,
    RemoteArtist,
    RemoteAlbum,
    RemotePlaylist,
    RemotePlaylistSummary,
    RemoteLyrics,
    PlaybackSource,
    RemoteHomeSection,
    SearchResponse
)

def parse_duration_to_ms(duration_str: Optional[str]) -> Optional[int]:
    if not duration_str:
        return None
    try:
        parts = [int(p) for p in duration_str.strip().split(":")]
        if len(parts) == 2:
            return (parts[0] * 60 + parts[1]) * 1000
        elif len(parts) == 3:
            return (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
        elif len(parts) == 1:
            return parts[0] * 1000
    except Exception:
        pass
    return None

def get_best_thumbnail(thumbnails: Optional[List[Dict[str, Any]]]) -> Optional[str]:
    if not thumbnails or not isinstance(thumbnails, list):
        return None
    # Pick the largest width/height, or the last one in list
    best = thumbnails[-1]
    url = best.get("url")
    if url:
        # Some YTM thumbnails have '=w60-h60-l90-rj' or similar sizes at the end.
        # We can upscale or keep as is.
        return url
    return None

def parse_artists(artists_data: Any) -> List[ArtistRef]:
    result: List[ArtistRef] = []
    if isinstance(artists_data, list):
        for a in artists_data:
            if isinstance(a, dict):
                name = a.get("name")
                if name:
                    result.append(ArtistRef(id=a.get("id"), name=name))
            elif isinstance(a, str):
                result.append(ArtistRef(id=None, name=a))
    elif isinstance(artists_data, str):
        result.append(ArtistRef(id=None, name=artists_data))
    return result

def parse_album_ref(album_data: Any) -> Optional[AlbumRef]:
    if isinstance(album_data, dict):
        name = album_data.get("name") or album_data.get("title")
        if name:
            return AlbumRef(id=album_data.get("id"), name=name)
    elif isinstance(album_data, str) and album_data.strip():
        return AlbumRef(id=None, name=album_data.strip())
    return None

class YTMusicService:
    _instance: Optional["YTMusicService"] = None

    def __init__(self):
        auth_file = os.getenv("YTM_AUTH_FILE")
        if auth_file and os.path.exists(auth_file):
            print(f"[YTMusicService] Initializing with auth file: {auth_file}")
            self.yt = YTMusic(auth_file)
        else:
            print("[YTMusicService] Initializing unauthenticated public client")
            self.yt = YTMusic()

    @classmethod
    def get_instance(cls) -> "YTMusicService":
        if cls._instance is None:
            cls._instance = YTMusicService()
        return cls._instance

    def search(self, query: str, filter_type: Optional[str] = None, limit: int = 25) -> SearchResponse:
        results = self.yt.search(query=query, filter=filter_type, limit=limit)
        
        tracks: List[RemoteTrack] = []
        artists: List[Dict[str, Any]] = []
        albums: List[RemoteAlbumSummary] = []
        playlists: List[RemotePlaylistSummary] = []

        for item in results:
            result_type = item.get("resultType") or item.get("type")
            
            if result_type in ("song", "video") or "videoId" in item:
                video_id = item.get("videoId")
                if video_id:
                    dur_seconds = item.get("duration_seconds")
                    duration_ms = int(dur_seconds * 1000) if dur_seconds is not None else parse_duration_to_ms(item.get("duration"))
                    tracks.append(RemoteTrack(
                        id=video_id,
                        title=item.get("title") or "Unknown Title",
                        artists=parse_artists(item.get("artists")),
                        album=parse_album_ref(item.get("album")),
                        durationMs=duration_ms,
                        durationText=item.get("duration"),
                        artworkUrl=get_best_thumbnail(item.get("thumbnails")),
                        videoId=video_id,
                        isExplicit=bool(item.get("isExplicit", False))
                    ))
            elif result_type == "artist":
                channel_id = item.get("browseId")
                artist_name = item.get("artist") or item.get("title")
                if artist_name:
                    artists.append({
                        "id": channel_id,
                        "name": artist_name,
                        "artworkUrl": get_best_thumbnail(item.get("thumbnails")),
                        "subscribers": item.get("subscribers")
                    })
            elif result_type == "album":
                browse_id = item.get("browseId")
                title = item.get("title")
                if browse_id and title:
                    albums.append(RemoteAlbumSummary(
                        id=browse_id,
                        title=title,
                        artists=parse_artists(item.get("artists") or item.get("artist")),
                        year=item.get("year"),
                        artworkUrl=get_best_thumbnail(item.get("thumbnails")),
                        type=item.get("type")
                    ))
            elif result_type == "playlist":
                browse_id = item.get("browseId")
                title = item.get("title")
                if browse_id and title:
                    playlists.append(RemotePlaylistSummary(
                        id=browse_id,
                        title=title,
                        author=item.get("author"),
                        artworkUrl=get_best_thumbnail(item.get("thumbnails")),
                        itemCount=item.get("itemCount")
                    ))

        return SearchResponse(
            query=query,
            filter=filter_type,
            tracks=tracks,
            artists=artists,
            albums=albums,
            playlists=playlists
        )

    def get_song(self, video_id: str) -> Optional[RemoteTrack]:
        try:
            data = self.yt.get_song(video_id)
            if not data:
                return None
            
            video_details = data.get("videoDetails", {})
            dur_seconds = video_details.get("lengthSeconds")
            duration_ms = int(dur_seconds) * 1000 if dur_seconds else None
            
            thumbnails = video_details.get("thumbnail", {}).get("thumbnails", [])
            
            return RemoteTrack(
                id=video_id,
                title=video_details.get("title") or "Unknown Title",
                artists=[ArtistRef(id=video_details.get("channelId"), name=video_details.get("author", "Unknown Artist"))],
                album=None,
                durationMs=duration_ms,
                artworkUrl=get_best_thumbnail(thumbnails),
                videoId=video_id,
                isExplicit=False
            )
        except Exception as e:
            print(f"[YTMusicService] get_song error: {e}")
            return None

    def get_artist(self, channel_id: str) -> Optional[RemoteArtist]:
        try:
            data = self.yt.get_artist(channel_id)
            if not data:
                return None
            
            top_songs: List[RemoteTrack] = []
            songs_section = data.get("songs", {})
            for item in songs_section.get("results", []):
                vid = item.get("videoId")
                if vid:
                    dur_sec = item.get("duration_seconds")
                    dur_ms = int(dur_sec * 1000) if dur_sec else parse_duration_to_ms(item.get("duration"))
                    top_songs.append(RemoteTrack(
                        id=vid,
                        title=item.get("title") or "Unknown Title",
                        artists=parse_artists(item.get("artists")),
                        album=parse_album_ref(item.get("album")),
                        durationMs=dur_ms,
                        durationText=item.get("duration"),
                        artworkUrl=get_best_thumbnail(item.get("thumbnails")),
                        videoId=vid,
                        isExplicit=bool(item.get("isExplicit", False))
                    ))

            albums: List[RemoteAlbumSummary] = []
            for item in data.get("albums", {}).get("results", []):
                browse_id = item.get("browseId")
                if browse_id:
                    albums.append(RemoteAlbumSummary(
                        id=browse_id,
                        title=item.get("title", ""),
                        year=item.get("year"),
                        artworkUrl=get_best_thumbnail(item.get("thumbnails")),
                        type="Album"
                    ))

            singles: List[RemoteAlbumSummary] = []
            for item in data.get("singles", {}).get("results", []):
                browse_id = item.get("browseId")
                if browse_id:
                    singles.append(RemoteAlbumSummary(
                        id=browse_id,
                        title=item.get("title", ""),
                        year=item.get("year"),
                        artworkUrl=get_best_thumbnail(item.get("thumbnails")),
                        type="Single"
                    ))

            related_artists: List[ArtistRef] = []
            for item in data.get("related", {}).get("results", []):
                browse_id = item.get("browseId")
                title = item.get("title")
                if title:
                    related_artists.append(ArtistRef(id=browse_id, name=title))

            return RemoteArtist(
                id=channel_id,
                name=data.get("name") or "Unknown Artist",
                description=data.get("description"),
                subscribers=data.get("subscribers"),
                artworkUrl=get_best_thumbnail(data.get("thumbnails")),
                topSongs=top_songs,
                albums=albums,
                singles=singles,
                relatedArtists=related_artists
            )
        except Exception as e:
            print(f"[YTMusicService] get_artist error: {e}")
            return None

    def get_album(self, browse_id: str) -> Optional[RemoteAlbum]:
        try:
            data = self.yt.get_album(browse_id)
            if not data:
                return None
            
            tracks: List[RemoteTrack] = []
            for item in data.get("tracks", []):
                vid = item.get("videoId")
                if vid:
                    dur_sec = item.get("duration_seconds")
                    dur_ms = int(dur_sec * 1000) if dur_sec else parse_duration_to_ms(item.get("duration"))
                    tracks.append(RemoteTrack(
                        id=vid,
                        title=item.get("title") or "Unknown Track",
                        artists=parse_artists(item.get("artists")),
                        album=AlbumRef(id=browse_id, name=data.get("title", "")),
                        durationMs=dur_ms,
                        durationText=item.get("duration"),
                        artworkUrl=get_best_thumbnail(item.get("thumbnails")) or get_best_thumbnail(data.get("thumbnails")),
                        videoId=vid,
                        isExplicit=bool(item.get("isExplicit", False))
                    ))

            return RemoteAlbum(
                id=browse_id,
                title=data.get("title") or "Unknown Album",
                artists=parse_artists(data.get("artists")),
                year=data.get("year"),
                artworkUrl=get_best_thumbnail(data.get("thumbnails")),
                trackCount=data.get("trackCount") or len(tracks),
                durationText=data.get("duration"),
                tracks=tracks
            )
        except Exception as e:
            print(f"[YTMusicService] get_album error: {e}")
            return None

    def get_playlist(self, playlist_id: str) -> Optional[RemotePlaylist]:
        try:
            data = self.yt.get_playlist(playlist_id)
            if not data:
                return None
            
            tracks: List[RemoteTrack] = []
            for item in data.get("tracks", []):
                vid = item.get("videoId")
                if vid:
                    dur_sec = item.get("duration_seconds")
                    dur_ms = int(dur_sec * 1000) if dur_sec else parse_duration_to_ms(item.get("duration"))
                    tracks.append(RemoteTrack(
                        id=vid,
                        title=item.get("title") or "Unknown Track",
                        artists=parse_artists(item.get("artists")),
                        album=parse_album_ref(item.get("album")),
                        durationMs=dur_ms,
                        durationText=item.get("duration"),
                        artworkUrl=get_best_thumbnail(item.get("thumbnails")) or get_best_thumbnail(data.get("thumbnails")),
                        videoId=vid,
                        isExplicit=bool(item.get("isExplicit", False))
                    ))

            author_val = data.get("author")
            author_str = author_val.get("name") if isinstance(author_val, dict) else (author_val if isinstance(author_val, str) else None)

            return RemotePlaylist(
                id=playlist_id,
                title=data.get("title") or "Unknown Playlist",
                author=author_str,
                description=data.get("description"),
                artworkUrl=get_best_thumbnail(data.get("thumbnails")),
                trackCount=data.get("trackCount") or len(tracks),
                tracks=tracks
            )
        except Exception as e:
            print(f"[YTMusicService] get_playlist error: {e}")
            return None

    def get_lyrics(self, video_id: str) -> Optional[RemoteLyrics]:
        try:
            watch = self.yt.get_watch_playlist(videoId=video_id)
            lyrics_id = watch.get("lyrics")
            if not lyrics_id:
                return None
            lyrics_data = self.yt.get_lyrics(lyrics_id)
            if not lyrics_data or not lyrics_data.get("lyrics"):
                return None
            
            return RemoteLyrics(
                videoId=video_id,
                text=lyrics_data.get("lyrics"),
                isSynced=False,
                provider="YouTube Music"
            )
        except Exception as e:
            print(f"[YTMusicService] get_lyrics error: {e}")
            return None

    def get_related(self, video_id: str) -> List[RemoteTrack]:
        try:
            watch = self.yt.get_watch_playlist(videoId=video_id, limit=20)
            results: List[RemoteTrack] = []
            for item in watch.get("tracks", []):
                vid = item.get("videoId")
                if vid and vid != video_id:
                    dur_sec = item.get("duration_seconds")
                    dur_ms = int(dur_sec * 1000) if dur_sec else parse_duration_to_ms(item.get("duration"))
                    results.append(RemoteTrack(
                        id=vid,
                        title=item.get("title") or "Unknown Track",
                        artists=parse_artists(item.get("artists")),
                        album=parse_album_ref(item.get("album")),
                        durationMs=dur_ms,
                        durationText=item.get("duration"),
                        artworkUrl=get_best_thumbnail(item.get("thumbnails")),
                        videoId=vid,
                        isExplicit=bool(item.get("isExplicit", False))
                    ))
            return results
        except Exception as e:
            print(f"[YTMusicService] get_related error: {e}")
            return []

    def get_home(self, limit: int = 5) -> List[RemoteHomeSection]:
        try:
            home = self.yt.get_home(limit=limit)
            sections: List[RemoteHomeSection] = []
            for sec in home:
                title = sec.get("title", "")
                contents = sec.get("contents", [])
                mapped_items: List[Any] = []
                for c in contents:
                    vid = c.get("videoId")
                    browse_id = c.get("browseId")
                    if vid:
                        dur_sec = c.get("duration_seconds")
                        dur_ms = int(dur_sec * 1000) if dur_sec else parse_duration_to_ms(c.get("duration"))
                        mapped_items.append({
                            "type": "track",
                            "track": RemoteTrack(
                                id=vid,
                                title=c.get("title") or "Unknown Title",
                                artists=parse_artists(c.get("artists")),
                                album=parse_album_ref(c.get("album")),
                                durationMs=dur_ms,
                                artworkUrl=get_best_thumbnail(c.get("thumbnails")),
                                videoId=vid
                            ).model_dump()
                        })
                    elif browse_id:
                        mapped_items.append({
                            "type": "collection",
                            "id": browse_id,
                            "title": c.get("title"),
                            "description": c.get("description"),
                            "artworkUrl": get_best_thumbnail(c.get("thumbnails"))
                        })
                if mapped_items:
                    sections.append(RemoteHomeSection(title=title, items=mapped_items))
            
            if not sections:
                try:
                    # Fallback for unauthenticated clients using public charts & hits
                    trending = self.yt.search("Top Hits", filter="songs", limit=10)
                    trending_tracks = []
                    for item in trending:
                        vid = item.get("videoId")
                        if vid:
                            dur_sec = item.get("duration_seconds")
                            dur_ms = int(dur_sec * 1000) if dur_sec else parse_duration_to_ms(item.get("duration"))
                            trending_tracks.append({
                                "type": "track",
                                "track": RemoteTrack(
                                    id=vid,
                                    title=item.get("title") or "Unknown Title",
                                    artists=parse_artists(item.get("artists")),
                                    album=parse_album_ref(item.get("album")),
                                    durationMs=dur_ms,
                                    artworkUrl=get_best_thumbnail(item.get("thumbnails")),
                                    videoId=vid
                                ).model_dump()
                            })
                    if trending_tracks:
                        sections.append(RemoteHomeSection(title="Trending & Popular", items=trending_tracks))

                    charts = self.yt.get_charts()
                    vids_chart = charts.get("videos", [])
                    if vids_chart:
                        chart_items = []
                        for c in vids_chart[:8]:
                            playlist_id = c.get("playlistId")
                            if playlist_id:
                                chart_items.append({
                                    "type": "collection",
                                    "id": playlist_id,
                                    "title": c.get("title"),
                                    "artworkUrl": get_best_thumbnail(c.get("thumbnails"))
                                })
                        if chart_items:
                            sections.append(RemoteHomeSection(title="Top Charts", items=chart_items))
                except Exception as e_fallback:
                    print(f"[YTMusicService] get_home fallback error: {e_fallback}")

            return sections
        except Exception as e:
            print(f"[YTMusicService] get_home error: {e}")
            return []
