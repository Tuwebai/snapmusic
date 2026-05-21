#!/usr/bin/env python3
"""
Get stream URL for a YouTube video using yt-dlp Python API.
Usage: python3 yt_stream.py <video_id> <format: audio|video>
Output: JSON with stream URL
"""
import sys
import json
import yt_dlp

def get_stream_url(video_id: str, fmt: str = "audio"):
    url = f"https://www.youtube.com/watch?v={video_id}"
    
    # Try multiple clients to bypass bot detection without cookies
    # mweb and ios clients often work without requiring sign-in
    ydl_opts = {
        "quiet": True,
        "no_warnings": True,
        "extractor_args": {
            "youtube": {
                "player_client": ["ios", "mweb", "web_creator"],
                "skip": ["webpage"],
            }
        },
        "skip_download": True,
        "http_headers": {
            "User-Agent": "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X)",
        },
    }
    
    if fmt == "audio":
        ydl_opts["format"] = "bestaudio[ext=m4a]/bestaudio/best"
    else:
        ydl_opts["format"] = "best[ext=mp4]/best"
    
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            
            if not info:
                return {"success": False, "error": "No info returned"}
            
            stream_url = info.get("url", "")
            
            # If no direct URL, try from formats
            if not stream_url and info.get("formats"):
                formats = info["formats"]
                if fmt == "audio":
                    # Find best audio-only format
                    audio_fmts = [f for f in formats if f.get("vcodec") == "none" and f.get("acodec") != "none" and f.get("url")]
                    if audio_fmts:
                        audio_fmts.sort(key=lambda x: x.get("abr", 0) or 0, reverse=True)
                        stream_url = audio_fmts[0]["url"]
                else:
                    # Find best video+audio format
                    video_fmts = [f for f in formats if f.get("vcodec") != "none" and f.get("acodec") != "none" and f.get("url")]
                    if video_fmts:
                        video_fmts.sort(key=lambda x: x.get("height", 0) or 0, reverse=True)
                        stream_url = video_fmts[0]["url"]
            
            if not stream_url:
                return {"success": False, "error": "No stream URL found"}
            
            return {
                "success": True,
                "url": stream_url,
                "title": info.get("title", ""),
                "duration": info.get("duration", 0),
                "ext": info.get("ext", "m4a" if fmt == "audio" else "mp4"),
            }
    except Exception as e:
        return {"success": False, "error": str(e)}

if __name__ == "__main__":
    video_id = sys.argv[1] if len(sys.argv) > 1 else "Cr8K88UcO0s"
    fmt = sys.argv[2] if len(sys.argv) > 2 else "audio"
    result = get_stream_url(video_id, fmt)
    print(json.dumps(result))
