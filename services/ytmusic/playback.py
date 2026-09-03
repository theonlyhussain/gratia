import urllib.parse
from typing import Optional
import yt_dlp
from models import PlaybackSource

def resolve_playback_source(video_id: str) -> Optional[PlaybackSource]:
    """
    Extracts the best audio stream URL for a given YouTube video ID using yt-dlp.
    Never caches URLs permanently since streaming URLs expire after a few hours.
    """
    target_url = f"https://www.youtube.com/watch?v={video_id}"
    
    ydl_opts = {
        "format": "bestaudio/best",
        "noplaylist": True,
        "quiet": True,
        "no_warnings": True,
        "extract_flat": False,
        "skip_download": True,
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(target_url, download=False)
            if not info:
                return None
            
            stream_url = info.get("url")
            if not stream_url:
                # If formats are listed, pick the best audio format
                formats = info.get("formats", [])
                audio_formats = [f for f in formats if f.get("acodec") != "none" and f.get("vcodec") == "none"]
                if not audio_formats:
                    audio_formats = [f for f in formats if f.get("acodec") != "none"]
                if audio_formats:
                    # Sort by audio bitrate
                    audio_formats.sort(key=lambda x: x.get("abr") or x.get("tbr") or 0, reverse=True)
                    stream_url = audio_formats[0].get("url")
            
            if not stream_url:
                return None
            
            # Parse expiration timestamp from URL query param if present
            expires_at_ms: Optional[int] = None
            try:
                parsed_url = urllib.parse.urlparse(stream_url)
                qs = urllib.parse.parse_qs(parsed_url.query)
                if "expire" in qs:
                    expires_at_ms = int(qs["expire"][0]) * 1000
            except Exception:
                pass

            duration_sec = info.get("duration")
            duration_ms = int(duration_sec * 1000) if duration_sec else None
            
            mime_type = None
            ext = info.get("ext")
            if ext == "m4a":
                mime_type = "audio/mp4"
            elif ext == "webm":
                mime_type = "audio/webm"
            elif ext == "mp3":
                mime_type = "audio/mpeg"

            raw_bitrate = info.get("abr") or info.get("tbr")
            clean_bitrate = int(round(float(raw_bitrate))) if raw_bitrate else None

            return PlaybackSource(
                videoId=video_id,
                streamUrl=stream_url,
                mimeType=mime_type or "audio/mp4",
                durationMs=duration_ms,
                expiresAtMs=expires_at_ms,
                format=ext,
                bitrate=clean_bitrate
            )
    except Exception as e:
        print(f"[PlaybackResolver] Error resolving {video_id}: {e}")
        return None
