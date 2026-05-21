// YouTube Search Service - Uses backend tRPC API for real searches
// No API keys required, searches YouTube directly via server
import { Platform } from "react-native";

export interface VideoResult {
  id: string;
  title: string;
  channel: string;
  thumbnail: string;
  duration: string;
  views: string;
  publishedAt: string;
  url: string;
}

export interface VideoInfo extends VideoResult {
  description: string;
}

export interface DownloadFormat {
  id: string;
  label: string;
  type: "audio" | "video";
  quality: string;
  ext: string;
  size?: string;
  bitrate?: string;
}

const AUDIO_FORMATS: DownloadFormat[] = [
  { id: "mp3-320", label: "MP3", type: "audio", quality: "320kbps", ext: "mp3", size: "~8MB", bitrate: "320kbps" },
  { id: "mp3-128", label: "MP3", type: "audio", quality: "128kbps", ext: "mp3", size: "~3MB", bitrate: "128kbps" },
  { id: "m4a-128", label: "M4A", type: "audio", quality: "128kbps", ext: "m4a", size: "~3MB", bitrate: "128kbps" },
  { id: "ogg-128", label: "OGG", type: "audio", quality: "128kbps", ext: "ogg", size: "~3MB", bitrate: "128kbps" },
  { id: "wav", label: "WAV", type: "audio", quality: "Lossless", ext: "wav", size: "~30MB" },
];

const VIDEO_FORMATS: DownloadFormat[] = [
  { id: "mp4-1080", label: "MP4", type: "video", quality: "1080p HD", ext: "mp4", size: "~150MB" },
  { id: "mp4-720", label: "MP4", type: "video", quality: "720p HD", ext: "mp4", size: "~80MB" },
  { id: "mp4-480", label: "MP4", type: "video", quality: "480p", ext: "mp4", size: "~40MB" },
  { id: "mp4-360", label: "MP4", type: "video", quality: "360p", ext: "mp4", size: "~20MB" },
  { id: "webm-720", label: "WEBM", type: "video", quality: "720p", ext: "webm", size: "~60MB" },
];

export const ALL_FORMATS = { audio: AUDIO_FORMATS, video: VIDEO_FORMATS };

/**
 * Get the API base URL for tRPC calls.
 *
 * Priority:
 * 1. EXPO_PUBLIC_API_BASE_URL env var (set for production/APK builds)
 * 2. On web: derive from window.location (sandbox dev mode)
 * 3. Fallback: localhost:3000 (local dev)
 */
function getApiBaseUrl(): string {
  // 1. Use explicit env var if set (required for Android APK)
  const envUrl = process.env.EXPO_PUBLIC_API_BASE_URL;
  if (envUrl && envUrl.trim()) {
    return envUrl.replace(/\/$/, "");
  }

  // 2. On web in sandbox: derive from window.location
  if (Platform.OS === "web" && typeof window !== "undefined" && window.location) {
    const { protocol, hostname } = window.location;
    const apiHostname = hostname.replace(/^8081-/, "3000-");
    if (apiHostname !== hostname) {
      return `${protocol}//${apiHostname}`;
    }
    return `${protocol}//${hostname.replace(/:8081$/, ":3000")}`;
  }

  // 3. Local dev fallback
  return "http://localhost:3000";
}

/**
 * Call a tRPC query using the batch format with superjson.
 * tRPC v11 with superjson wraps input as: { "0": { "json": { ...data } } }
 * and returns: [{ "result": { "data": { "json": ...result } } }]
 */
async function trpcQuery<T>(procedure: string, input: Record<string, unknown>): Promise<T | null> {
  try {
    const apiUrl = getApiBaseUrl();
    const encodedInput = encodeURIComponent(JSON.stringify({ "0": { json: input } }));
    const url = `${apiUrl}/api/trpc/${procedure}?batch=1&input=${encodedInput}`;

    console.log(`[tRPC] Calling ${procedure}:`, input);

    const response = await fetch(url, {
      method: "GET",
      headers: {
        "Content-Type": "application/json",
      },
    });

    if (!response.ok) {
      console.error(`[tRPC] HTTP error ${response.status} for ${procedure}`);
      return null;
    }

    const data = await response.json();

    // tRPC batch response: [{ result: { data: { json: ... } } }]
    if (Array.isArray(data) && data[0]?.result?.data?.json !== undefined) {
      return data[0].result.data.json as T;
    }

    // Fallback: direct data without superjson wrapper
    if (Array.isArray(data) && data[0]?.result?.data !== undefined) {
      return data[0].result.data as T;
    }

    console.warn(`[tRPC] Unexpected response structure for ${procedure}:`, JSON.stringify(data).slice(0, 200));
    return null;
  } catch (error) {
    console.error(`[tRPC] Error calling ${procedure}:`, error);
    return null;
  }
}

/**
 * Call a tRPC mutation using POST with batch format and superjson.
 */
async function trpcMutation<T>(procedure: string, input: Record<string, unknown>): Promise<T | null> {
  try {
    const apiUrl = getApiBaseUrl();
    const url = `${apiUrl}/api/trpc/${procedure}?batch=1`;

    console.log(`[tRPC] Mutating ${procedure}:`, input);

    const response = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ "0": { json: input } }),
    });

    if (!response.ok) {
      console.error(`[tRPC] HTTP error ${response.status} for ${procedure}`);
      return null;
    }

    const data = await response.json();

    if (Array.isArray(data) && data[0]?.result?.data?.json !== undefined) {
      return data[0].result.data.json as T;
    }

    if (Array.isArray(data) && data[0]?.result?.data !== undefined) {
      return data[0].result.data as T;
    }

    console.warn(`[tRPC] Unexpected response structure for ${procedure}:`, JSON.stringify(data).slice(0, 200));
    return null;
  } catch (error) {
    console.error(`[tRPC] Error mutating ${procedure}:`, error);
    return null;
  }
}

export async function searchVideos(query: string): Promise<VideoResult[]> {
  if (!query.trim()) return [];

  console.log(`[YouTube] Searching for: "${query}"`);
  const results = await trpcQuery<VideoResult[]>("youtube.search", { query: query.trim() });

  if (results && results.length > 0) {
    console.log(`[YouTube] Found ${results.length} results for "${query}"`);
    return results;
  }

  console.warn(`[YouTube] No results found for "${query}"`);
  return [];
}

export async function getVideoInfo(videoId: string): Promise<VideoInfo | null> {
  console.log(`[YouTube] Getting info for videoId: ${videoId}`);
  const info = await trpcQuery<VideoInfo>("youtube.videoInfo", { videoId });

  if (info) {
    console.log(`[YouTube] Got info for: ${info.title}`);
    return info;
  }

  console.warn(`[YouTube] Could not get info for videoId: ${videoId}`);
  return null;
}

export async function getStreamUrl(
  videoId: string,
  format: "audio" | "video"
): Promise<{ url: string; title: string; duration: number } | null> {
  console.log(`[YouTube] Getting stream URL for ${format}: ${videoId}`);
  const result = await trpcQuery<{ success: boolean; url: string; title: string; duration: number; error?: string }>(
    "youtube.getStreamUrl",
    { videoId, format }
  );

  if (result?.success && result.url) {
    console.log(`[YouTube] Got stream URL for ${format}: ${videoId}`);
    return { url: result.url, title: result.title, duration: result.duration };
  }

  console.warn(`[YouTube] Could not get stream URL for ${videoId}:`, result?.error);
  return null;
}

export async function downloadVideo(
  videoId: string,
  format: string,
  quality?: string
): Promise<{ success: boolean; filename?: string; downloadUrl?: string; sizeFormatted?: string; error?: string }> {
  console.log(`[YouTube] Downloading ${format} (${quality}): ${videoId}`);
  const result = await trpcMutation<{ success: boolean; filename?: string; path?: string; downloadUrl?: string; sizeFormatted?: string; error?: string }>(
    "youtube.download",
    { videoId, format, quality }
  );

  if (result) {
    return {
      success: result.success,
      filename: result.filename,
      downloadUrl: result.downloadUrl,
      sizeFormatted: result.sizeFormatted,
      error: result.error,
    };
  }

  return { success: false, error: "Error de conexión con el servidor" };
}
