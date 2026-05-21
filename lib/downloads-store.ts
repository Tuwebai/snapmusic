import AsyncStorage from "@react-native-async-storage/async-storage";
import * as FileSystem from "expo-file-system/legacy";
import { Platform } from "react-native";
import { VideoResult, DownloadFormat, downloadVideo } from "./youtube-service";

export type DownloadStatus = "pending" | "downloading" | "completed" | "error";

export interface Download {
  id: string;
  videoId: string;
  title: string;
  channel: string;
  thumbnail: string;
  format: DownloadFormat;
  status: DownloadStatus;
  progress: number; // 0-100
  filePath?: string;
  localUri?: string;
  fileSize?: string;
  createdAt: number;
  completedAt?: number;
  error?: string;
}

const STORAGE_KEY = "snapmusic_downloads_v3";

let listeners: Array<() => void> = [];
let downloads: Download[] = [];
let initialized = false;

async function init() {
  if (initialized) return;
  initialized = true;
  try {
    const stored = await AsyncStorage.getItem(STORAGE_KEY);
    if (stored) {
      downloads = JSON.parse(stored);
      // Reset any interrupted downloads
      downloads = downloads.map((d) =>
        d.status === "downloading"
          ? { ...d, status: "error" as DownloadStatus, error: "Descarga interrumpida" }
          : d
      );
    }
  } catch {
    downloads = [];
  }
  notify();
}

async function save() {
  try {
    await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(downloads));
  } catch {
    // ignore
  }
}

function notify() {
  listeners.forEach((fn) => fn());
}

export function subscribe(fn: () => void) {
  listeners.push(fn);
  return () => {
    listeners = listeners.filter((l) => l !== fn);
  };
}

export function getDownloads(): Download[] {
  return downloads;
}

export async function startDownload(video: VideoResult, format: DownloadFormat): Promise<string> {
  await init();

  const downloadId = `dl_${Date.now()}_${Math.random().toString(36).slice(2)}`;

  const newDownload: Download = {
    id: downloadId,
    videoId: video.id,
    title: video.title,
    channel: video.channel,
    thumbnail: video.thumbnail,
    format,
    status: "downloading",
    progress: 0,
    createdAt: Date.now(),
  };

  downloads = [newDownload, ...downloads];
  await save();
  notify();

  // Start download in background
  performDownload(downloadId, video, format);

  return downloadId;
}

async function performDownload(downloadId: string, video: VideoResult, format: DownloadFormat) {
  try {
    // Show initial progress
    updateProgress(downloadId, 5);

    // Ask server for the direct stream URL
    const result = await downloadVideo(video.id, format.id);

    if (!result.success || !result.downloadUrl) {
      throw new Error(result.error || "No se pudo obtener la URL de descarga");
    }

    updateProgress(downloadId, 15);

    const streamUrl = result.downloadUrl;
    const filename = result.filename || `${video.title}.${format.ext}`;

    if (Platform.OS !== "web") {
      // Native: download directly from YouTube CDN to device storage
      const dir = `${FileSystem.documentDirectory}SnapMusic/`;
      const dirInfo = await FileSystem.getInfoAsync(dir);
      if (!dirInfo.exists) {
        await FileSystem.makeDirectoryAsync(dir, { intermediates: true });
      }

      const localUri = `${dir}${filename}`;

      // Use expo-file-system to download directly from the stream URL
      // The download happens on the device, not the server — no IP restrictions
      const downloadResumable = FileSystem.createDownloadResumable(
        streamUrl,
        localUri,
        {
          headers: {
            // YouTube requires a valid User-Agent for CDN downloads
            "User-Agent":
              "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
          },
        },
        (downloadProgress) => {
          const total = downloadProgress.totalBytesExpectedToWrite;
          const written = downloadProgress.totalBytesWritten;
          if (total > 0) {
            const pct = Math.round((written / total) * 100);
            // Scale to 15-100 range
            updateProgress(downloadId, 15 + Math.round(pct * 0.85));
          } else {
            // Unknown total size — animate progress
            const current = downloads.find((d) => d.id === downloadId)?.progress || 15;
            if (current < 90) updateProgress(downloadId, current + 2);
          }
        }
      );

      const downloadResult = await downloadResumable.downloadAsync();

      if (!downloadResult?.uri) {
        throw new Error("La descarga no produjo un archivo");
      }

      // Get file size
      let fileSize = "N/A";
      try {
        const fileInfo = await FileSystem.getInfoAsync(downloadResult.uri);
        if (fileInfo.exists && (fileInfo as any).size) {
          const bytes = (fileInfo as any).size as number;
          fileSize =
            bytes >= 1024 * 1024
              ? `${(bytes / (1024 * 1024)).toFixed(1)} MB`
              : `${(bytes / 1024).toFixed(0)} KB`;
        }
      } catch {
        // ignore
      }

      downloads = downloads.map((d) =>
        d.id === downloadId
          ? {
              ...d,
              status: "completed" as DownloadStatus,
              progress: 100,
              completedAt: Date.now(),
              filePath: filename,
              localUri: downloadResult.uri,
              fileSize,
            }
          : d
      );
    } else {
      // Web: open the stream URL in a new tab for browser download
      if (typeof window !== "undefined") {
        const a = document.createElement("a");
        a.href = streamUrl;
        a.download = filename;
        a.target = "_blank";
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
      }

      downloads = downloads.map((d) =>
        d.id === downloadId
          ? {
              ...d,
              status: "completed" as DownloadStatus,
              progress: 100,
              completedAt: Date.now(),
              filePath: filename,
            }
          : d
      );
    }

    await save();
    notify();
  } catch (error: any) {
    console.error("[Download] Error:", error);
    downloads = downloads.map((d) =>
      d.id === downloadId
        ? {
            ...d,
            status: "error" as DownloadStatus,
            progress: 0,
            error: error?.message || String(error),
          }
        : d
    );
    await save();
    notify();
  }
}

function updateProgress(downloadId: string, progress: number) {
  downloads = downloads.map((d) =>
    d.id === downloadId ? { ...d, progress } : d
  );
  notify();
}

export async function removeDownload(downloadId: string) {
  await init();
  // Also delete local file if it exists
  const dl = downloads.find((d) => d.id === downloadId);
  if (dl?.localUri) {
    try {
      const info = await FileSystem.getInfoAsync(dl.localUri);
      if (info.exists) await FileSystem.deleteAsync(dl.localUri, { idempotent: true });
    } catch {
      // ignore
    }
  }
  downloads = downloads.filter((d) => d.id !== downloadId);
  await save();
  notify();
}

export async function clearCompleted() {
  await init();
  downloads = downloads.filter((d) => d.status !== "completed");
  await save();
  notify();
}

// Initialize on module load
init();
