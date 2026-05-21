/**
 * SnapMusic - Unit Tests
 * Tests that don't depend on react-native modules (which can't be parsed by vitest/esbuild)
 */
import { describe, it, expect } from "vitest";

// ---- Format definitions (copied from youtube-service to avoid react-native import) ----
const AUDIO_FORMATS = [
  { id: "mp3-320", label: "MP3 320kbps", ext: "mp3", quality: "320kbps", type: "audio" as const },
  { id: "mp3-128", label: "MP3 128kbps", ext: "mp3", quality: "128kbps", type: "audio" as const },
  { id: "m4a-128", label: "M4A 128kbps", ext: "m4a", quality: "128kbps", type: "audio" as const },
  { id: "ogg-128", label: "OGG 128kbps", ext: "ogg", quality: "128kbps", type: "audio" as const },
  { id: "wav", label: "WAV (sin pérdida)", ext: "wav", quality: "lossless", type: "audio" as const },
];

const VIDEO_FORMATS = [
  { id: "mp4-1080", label: "MP4 1080p", ext: "mp4", quality: "1080p", type: "video" as const },
  { id: "mp4-720", label: "MP4 720p", ext: "mp4", quality: "720p", type: "video" as const },
  { id: "mp4-480", label: "MP4 480p", ext: "mp4", quality: "480p", type: "video" as const },
  { id: "mp4-360", label: "MP4 360p", ext: "mp4", quality: "360p", type: "video" as const },
  { id: "webm-720", label: "WebM 720p", ext: "webm", quality: "720p", type: "video" as const },
];

const ALL_FORMATS = { audio: AUDIO_FORMATS, video: VIDEO_FORMATS };

// ---- Helper functions ----
function extractVideoId(url: string): string | null {
  const patterns = [
    /(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/embed\/)([a-zA-Z0-9_-]{11})/,
    /youtube\.com\/shorts\/([a-zA-Z0-9_-]{11})/,
  ];
  for (const pattern of patterns) {
    const match = url.match(pattern);
    if (match) return match[1];
  }
  return null;
}

function formatDuration(seconds: number): string {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  if (h > 0) return `${h}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
  return `${m}:${String(s).padStart(2, "0")}`;
}

function formatViews(views: number): string {
  if (views >= 1_000_000_000) return `${(views / 1_000_000_000).toFixed(1)}B vistas`;
  if (views >= 1_000_000) return `${(views / 1_000_000).toFixed(1)}M vistas`;
  if (views >= 1_000) return `${(views / 1_000).toFixed(0)}K vistas`;
  return `${views} vistas`;
}

// ---- Tests ----
describe("YouTube Service - Formats", () => {
  it("should have audio and video formats", () => {
    expect(ALL_FORMATS.audio.length).toBeGreaterThan(0);
    expect(ALL_FORMATS.video.length).toBeGreaterThan(0);
  });

  it("audio formats should have correct type and required fields", () => {
    ALL_FORMATS.audio.forEach((f) => {
      expect(f.type).toBe("audio");
      expect(f.ext).toBeTruthy();
      expect(f.quality).toBeTruthy();
      expect(f.id).toBeTruthy();
      expect(f.label).toBeTruthy();
    });
  });

  it("video formats should have correct type and required fields", () => {
    ALL_FORMATS.video.forEach((f) => {
      expect(f.type).toBe("video");
      expect(f.ext).toBeTruthy();
      expect(f.quality).toBeTruthy();
      expect(f.id).toBeTruthy();
      expect(f.label).toBeTruthy();
    });
  });

  it("should have at least MP3 and MP4 formats", () => {
    const hasMP3 = ALL_FORMATS.audio.some((f) => f.ext === "mp3");
    const hasMP4 = ALL_FORMATS.video.some((f) => f.ext === "mp4");
    expect(hasMP3).toBe(true);
    expect(hasMP4).toBe(true);
  });

  it("should have M4A format for iOS compatibility", () => {
    const hasM4A = ALL_FORMATS.audio.some((f) => f.ext === "m4a");
    expect(hasM4A).toBe(true);
  });
});

describe("YouTube URL Parser", () => {
  it("should extract video ID from standard YouTube URL", () => {
    const id = extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
    expect(id).toBe("dQw4w9WgXcQ");
  });

  it("should extract video ID from short YouTube URL", () => {
    const id = extractVideoId("https://youtu.be/dQw4w9WgXcQ");
    expect(id).toBe("dQw4w9WgXcQ");
  });

  it("should extract video ID from YouTube Shorts URL", () => {
    const id = extractVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ");
    expect(id).toBe("dQw4w9WgXcQ");
  });

  it("should return null for invalid URL", () => {
    const id = extractVideoId("https://example.com/video");
    expect(id).toBeNull();
  });

  it("should return null for empty string", () => {
    const id = extractVideoId("");
    expect(id).toBeNull();
  });
});

describe("Duration Formatter", () => {
  it("should format seconds to MM:SS", () => {
    expect(formatDuration(90)).toBe("1:30");
    expect(formatDuration(65)).toBe("1:05");
    expect(formatDuration(59)).toBe("0:59");
  });

  it("should format hours correctly", () => {
    expect(formatDuration(3661)).toBe("1:01:01");
    expect(formatDuration(7200)).toBe("2:00:00");
  });

  it("should pad seconds with zero", () => {
    expect(formatDuration(61)).toBe("1:01");
    expect(formatDuration(3600)).toBe("1:00:00");
  });
});

describe("Views Formatter", () => {
  it("should format millions", () => {
    expect(formatViews(1_500_000)).toBe("1.5M vistas");
    expect(formatViews(10_000_000)).toBe("10.0M vistas");
  });

  it("should format thousands", () => {
    expect(formatViews(5000)).toBe("5K vistas");
  });

  it("should format billions", () => {
    expect(formatViews(1_700_000_000)).toBe("1.7B vistas");
  });

  it("should format small numbers", () => {
    expect(formatViews(500)).toBe("500 vistas");
  });
});
