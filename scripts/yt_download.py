#!/usr/bin/env python3
"""
Download YouTube videos/audio using yt-dlp with tv_embedded client (no auth required).
Usage: python3 yt_download.py <video_id> <format_id> <quality> <output_dir>
Output: JSON with download result
"""
import sys
import json
import os
import re
import yt_dlp

FORMAT_MAP = {
    "mp3-320":  {"ext": "mp3",   "type": "audio", "quality": "320"},
    "mp3-128":  {"ext": "mp3",   "type": "audio", "quality": "128"},
    "m4a-128":  {"ext": "m4a",   "type": "audio", "quality": "128"},
    "ogg-128":  {"ext": "vorbis","type": "audio", "quality": "128"},
    "wav":      {"ext": "wav",   "type": "audio", "quality": "0"},
    "mp4-1080": {"ext": "mp4",   "type": "video", "quality": "1080"},
    "mp4-720":  {"ext": "mp4",   "type": "video", "quality": "720"},
    "mp4-480":  {"ext": "mp4",   "type": "video", "quality": "480"},
    "mp4-360":  {"ext": "mp4",   "type": "video", "quality": "360"},
    "webm-720": {"ext": "webm",  "type": "video", "quality": "720"},
}

# tv_embedded client bypasses bot detection without requiring cookies or login
BYPASS_ARGS = {
    "extractor_args": {
        "youtube": {
            "player_client": ["tv_embedded"],
            "player_skip": ["webpage", "configs"],
        }
    }
}


def strip_ansi(text: str) -> str:
    return re.sub(r'\x1b\[[0-9;]*m', '', text)


def download_video(video_id: str, fmt: str, quality: str, output_dir: str):
    url = f"https://www.youtube.com/watch?v={video_id}"
    os.makedirs(output_dir, exist_ok=True)

    fmt_info = FORMAT_MAP.get(fmt, {"ext": "mp4", "type": "video", "quality": quality})
    output_template = os.path.join(output_dir, "%(title)s.%(ext)s")

    base_opts = {
        "outtmpl": output_template,
        "quiet": True,
        "no_warnings": True,
        "noprogress": True,
        "logtostderr": True,
        **BYPASS_ARGS,
    }

    if fmt_info["type"] == "audio":
        ext = fmt_info["ext"]
        bitrate = fmt_info["quality"]
        codec = ext if ext != "vorbis" else "vorbis"
        ydl_opts = {
            **base_opts,
            "format": "bestaudio/best",
            "postprocessors": [{
                "key": "FFmpegExtractAudio",
                "preferredcodec": codec,
                "preferredquality": bitrate,
            }],
        }
    else:
        q = fmt_info["quality"]
        ext = fmt_info["ext"]
        ydl_opts = {
            **base_opts,
            "format": f"bestvideo[height<={q}][ext={ext}]+bestaudio/best[height<={q}]/best",
            "merge_output_format": ext,
        }

    downloaded_files = []

    def progress_hook(d):
        if d["status"] == "finished":
            downloaded_files.append(d.get("filename", ""))

    ydl_opts["progress_hooks"] = [progress_hook]

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=True)
            if not info:
                return {"success": False, "error": "No info returned from yt-dlp"}
            title = info.get("title", "video")

        # Locate the downloaded file
        filepath = None
        if downloaded_files:
            for candidate_path in reversed(downloaded_files):
                if os.path.exists(candidate_path):
                    filepath = candidate_path
                    break
                # Audio post-processing changes extension
                base = os.path.splitext(candidate_path)[0]
                for try_ext in [fmt_info["ext"], "mp3", "m4a", "ogg", "wav", "webm", "mp4"]:
                    candidate = f"{base}.{try_ext}"
                    if os.path.exists(candidate):
                        filepath = candidate
                        break
                if filepath:
                    break

        if not filepath:
            # Fallback: newest file in output_dir
            files = [
                os.path.join(output_dir, f)
                for f in os.listdir(output_dir)
                if os.path.isfile(os.path.join(output_dir, f))
            ]
            if not files:
                return {"success": False, "error": "No file found after download"}
            filepath = max(files, key=os.path.getmtime)

        if not os.path.exists(filepath):
            return {"success": False, "error": f"File not found: {filepath}"}

        size = os.path.getsize(filepath)
        filename = os.path.basename(filepath)

        return {
            "success": True,
            "filename": filename,
            "path": filepath,
            "size": size,
            "sizeFormatted": f"{size / 1024 / 1024:.2f} MB",
            "title": title,
        }

    except Exception as e:
        return {"success": False, "error": strip_ansi(str(e))}


if __name__ == "__main__":
    video_id   = sys.argv[1] if len(sys.argv) > 1 else "dQw4w9WgXcQ"
    fmt        = sys.argv[2] if len(sys.argv) > 2 else "mp3-128"
    quality    = sys.argv[3] if len(sys.argv) > 3 else "128"
    output_dir = sys.argv[4] if len(sys.argv) > 4 else "/tmp/snapmusic_downloads"

    result = download_video(video_id, fmt, quality, output_dir)
    print(json.dumps(result))
