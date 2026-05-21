#!/usr/bin/env python3
"""
YouTube Music search using ytmusicapi - no API keys required.
Usage: python3 yt_search.py <query> [limit]
Output: JSON array of video results
"""
import sys
import json
from ytmusicapi import YTMusic

def format_duration(seconds):
    if not seconds:
        return "0:00"
    m, s = divmod(int(seconds), 60)
    h, m = divmod(m, 60)
    if h > 0:
        return f"{h}:{m:02d}:{s:02d}"
    return f"{m}:{s:02d}"

def format_views(views):
    if not views:
        return "N/A"
    if isinstance(views, str):
        return views
    if views >= 1_000_000_000:
        return f"{views/1_000_000_000:.1f}B views"
    if views >= 1_000_000:
        return f"{views/1_000_000:.1f}M views"
    if views >= 1_000:
        return f"{views/1_000:.1f}K views"
    return f"{views} views"

def search_videos(query, limit=15):
    try:
        yt = YTMusic()
        results = yt.search(query, filter="videos", limit=limit)
        
        videos = []
        for item in results:
            if item.get("resultType") != "video":
                continue
            
            video_id = item.get("videoId", "")
            if not video_id:
                continue
            
            title = item.get("title", "Sin título")
            
            # Get channel name
            artists = item.get("artists", [])
            channel = artists[0].get("name", "Canal desconocido") if artists else "Canal desconocido"
            
            # Get thumbnail - use highest quality available
            thumbnails = item.get("thumbnails", [])
            if thumbnails:
                thumbnail = thumbnails[-1].get("url", f"https://img.youtube.com/vi/{video_id}/hqdefault.jpg")
            else:
                thumbnail = f"https://img.youtube.com/vi/{video_id}/hqdefault.jpg"
            
            # Duration
            duration_seconds = item.get("duration_seconds", 0)
            duration_str = item.get("duration", format_duration(duration_seconds))
            
            # Views
            views = item.get("views", "N/A")
            if isinstance(views, int):
                views = format_views(views)
            elif not views:
                views = "N/A"
            
            # Published
            published = item.get("year", "") or ""
            
            videos.append({
                "id": video_id,
                "title": title,
                "channel": channel,
                "thumbnail": thumbnail,
                "duration": duration_str,
                "views": str(views),
                "publishedAt": str(published),
                "url": f"https://www.youtube.com/watch?v={video_id}"
            })
        
        return videos
    except Exception as e:
        print(json.dumps({"error": str(e)}), file=sys.stderr)
        return []

if __name__ == "__main__":
    query = sys.argv[1] if len(sys.argv) > 1 else "bad bunny"
    limit = int(sys.argv[2]) if len(sys.argv) > 2 else 15
    results = search_videos(query, limit)
    print(json.dumps(results))
