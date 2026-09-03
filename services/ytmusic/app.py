from typing import Optional, List
from fastapi import FastAPI, Query, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from client import YTMusicService
from playback import resolve_playback_source
from models import (
    SearchResponse,
    RemoteTrack,
    RemoteArtist,
    RemoteAlbum,
    RemotePlaylist,
    RemoteLyrics,
    PlaybackSource,
    RemoteHomeSection
)

app = FastAPI(
    title="Gratia YouTube Music Provider API",
    version="1.0.0",
    description="Backend service providing YouTube Music integration for Gratia Android Player via ytmusicapi and yt-dlp."
)

# Enable CORS for local Android development and any clients
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

service = YTMusicService.get_instance()

@app.get("/api/ytmusic/health")
def health_check():
    return {"status": "ok", "provider": "youtube_music", "service": "gratia-ytmusic"}

@app.get("/api/ytmusic/search", response_model=SearchResponse)
def search(
    q: str = Query(..., description="Search query string"),
    filter: Optional[str] = Query(None, description="Optional filter: songs, videos, albums, artists, playlists"),
    limit: int = Query(25, ge=1, le=50)
):
    try:
        # Map common alias names if needed
        norm_filter = filter.lower() if filter else None
        if norm_filter in ("song", "tracks"):
            norm_filter = "songs"
        elif norm_filter in ("artist",):
            norm_filter = "artists"
        elif norm_filter in ("album",):
            norm_filter = "albums"
        elif norm_filter in ("playlist",):
            norm_filter = "playlists"
        elif norm_filter in ("video",):
            norm_filter = "videos"
        elif norm_filter in ("all", ""):
            norm_filter = None

        return service.search(query=q, filter_type=norm_filter, limit=limit)
    except Exception as e:
        print(f"Search failed: {e}")
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(e))

@app.get("/api/ytmusic/songs/{video_id}", response_model=RemoteTrack)
def get_song(video_id: str):
    track = service.get_song(video_id)
    if not track:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Track not found")
    return track

@app.get("/api/ytmusic/playback/{video_id}", response_model=PlaybackSource)
def get_playback_source(video_id: str):
    source = resolve_playback_source(video_id)
    if not source:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail="Failed to resolve audio stream for track")
    return source

@app.get("/api/ytmusic/artists/{channel_id}", response_model=RemoteArtist)
def get_artist(channel_id: str):
    artist = service.get_artist(channel_id)
    if not artist:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Artist not found")
    return artist

@app.get("/api/ytmusic/albums/{browse_id}", response_model=RemoteAlbum)
def get_album(browse_id: str):
    album = service.get_album(browse_id)
    if not album:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Album not found")
    return album

@app.get("/api/ytmusic/playlists/{playlist_id}", response_model=RemotePlaylist)
def get_playlist(playlist_id: str):
    playlist = service.get_playlist(playlist_id)
    if not playlist:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Playlist not found")
    return playlist

@app.get("/api/ytmusic/lyrics/{video_id}", response_model=RemoteLyrics)
def get_lyrics(video_id: str):
    lyrics = service.get_lyrics(video_id)
    if not lyrics:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Lyrics not found")
    return lyrics

@app.get("/api/ytmusic/related/{video_id}", response_model=List[RemoteTrack])
def get_related(video_id: str):
    return service.get_related(video_id)

@app.get("/api/ytmusic/home", response_model=List[RemoteHomeSection])
def get_home(limit: int = Query(5, ge=1, le=10)):
    return service.get_home(limit=limit)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app:app", host="0.0.0.0", port=8000, reload=True)
