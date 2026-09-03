from typing import Optional, List, Any
from pydantic import BaseModel, Field

class ArtistRef(BaseModel):
    id: Optional[str] = None
    name: str

class AlbumRef(BaseModel):
    id: Optional[str] = None
    name: str

class RemoteTrack(BaseModel):
    id: str
    provider: str = "youtube_music"
    title: str
    artists: List[ArtistRef] = Field(default_factory=list)
    album: Optional[AlbumRef] = None
    durationMs: Optional[int] = None
    durationText: Optional[str] = None
    artworkUrl: Optional[str] = None
    videoId: str
    isExplicit: bool = False

class RemoteAlbumSummary(BaseModel):
    id: str
    title: str
    artists: List[ArtistRef] = Field(default_factory=list)
    year: Optional[str] = None
    artworkUrl: Optional[str] = None
    type: Optional[str] = None

class RemoteArtist(BaseModel):
    id: str
    name: str
    description: Optional[str] = None
    subscribers: Optional[str] = None
    artworkUrl: Optional[str] = None
    topSongs: List[RemoteTrack] = Field(default_factory=list)
    albums: List[RemoteAlbumSummary] = Field(default_factory=list)
    singles: List[RemoteAlbumSummary] = Field(default_factory=list)
    relatedArtists: List[ArtistRef] = Field(default_factory=list)

class RemoteAlbum(BaseModel):
    id: str
    title: str
    artists: List[ArtistRef] = Field(default_factory=list)
    year: Optional[str] = None
    artworkUrl: Optional[str] = None
    trackCount: Optional[int] = None
    durationText: Optional[str] = None
    tracks: List[RemoteTrack] = Field(default_factory=list)

class RemotePlaylistSummary(BaseModel):
    id: str
    title: str
    author: Optional[str] = None
    artworkUrl: Optional[str] = None
    itemCount: Optional[str] = None

class RemotePlaylist(BaseModel):
    id: str
    title: str
    author: Optional[str] = None
    description: Optional[str] = None
    artworkUrl: Optional[str] = None
    trackCount: Optional[int] = None
    tracks: List[RemoteTrack] = Field(default_factory=list)

class RemoteLyrics(BaseModel):
    videoId: str
    text: str
    isSynced: bool = False
    provider: str = "youtube_music"

class PlaybackSource(BaseModel):
    videoId: str
    streamUrl: str
    mimeType: Optional[str] = None
    durationMs: Optional[int] = None
    expiresAtMs: Optional[int] = None
    format: Optional[str] = None
    bitrate: Optional[int] = None

class RemoteHomeSection(BaseModel):
    title: str
    items: List[Any] = Field(default_factory=list)

class SearchResponse(BaseModel):
    query: str
    filter: Optional[str] = None
    tracks: List[RemoteTrack] = Field(default_factory=list)
    artists: List[Any] = Field(default_factory=list)
    albums: List[RemoteAlbumSummary] = Field(default_factory=list)
    playlists: List[RemotePlaylistSummary] = Field(default_factory=list)
