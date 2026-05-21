import { COOKIE_NAME } from "../shared/const.js";
import { getSessionCookieOptions } from "./_core/cookies";
import { systemRouter } from "./_core/systemRouter";
import { publicProcedure, router } from "./_core/trpc";
import { z } from "zod";
import { execFile } from "child_process";
import { promisify } from "util";
import path from "path";
import fs from "fs";
import os from "os";

const execFileAsync = promisify(execFile);

// Absolute path to Python3 (avoids PATH issues in production)
const PYTHON3 = "/usr/bin/python3";
const SCRIPTS_DIR = path.join(__dirname, "..", "..", "scripts");

/**
 * Run a Python script with clean environment (no uv PYTHONHOME interference).
 * Returns parsed JSON output from the script.
 */
async function runPython(scriptName: string, args: string[], timeoutMs = 60000): Promise<any> {
  const scriptPath = path.join(SCRIPTS_DIR, scriptName);

  // Clean Python env: remove uv-injected variables that break system Python
  const cleanEnv: Record<string, string> = {};
  for (const [k, v] of Object.entries(process.env)) {
    if (v !== undefined) cleanEnv[k] = v;
  }
  delete cleanEnv.PYTHONHOME;
  delete cleanEnv.PYTHONPATH;
  delete cleanEnv.VIRTUAL_ENV;

  try {
    const { stdout, stderr } = await execFileAsync(PYTHON3, [scriptPath, ...args], {
      env: cleanEnv as NodeJS.ProcessEnv,
      timeout: timeoutMs,
      maxBuffer: 10 * 1024 * 1024, // 10MB
    });

    if (stderr && stderr.trim()) {
      console.log(`[Python:${scriptName}] stderr:`, stderr.trim().slice(0, 200));
    }

    const output = stdout.trim();
    if (!output) throw new Error("Empty output from Python script");

    return JSON.parse(output);
  } catch (err: any) {
    if (err.code === "ENOENT") {
      throw new Error(`Python3 not found at ${PYTHON3}`);
    }
    if (err.killed || err.signal === "SIGTERM") {
      throw new Error("Download timed out");
    }
    // Try to parse JSON from stdout even if there was an error
    if (err.stdout) {
      try { return JSON.parse(err.stdout.trim()); } catch (_) {}
    }
    throw err;
  }
}

/** Format seconds to MM:SS */
function formatDuration(seconds: number): string {
  if (!seconds || isNaN(seconds)) return "0:00";
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
}

/** Format view count */
function formatViews(views: string | number): string {
  const n = typeof views === "string" ? parseInt(views.replace(/\D/g, "")) : views;
  if (!n || isNaN(n)) return "N/A";
  if (n >= 1_000_000_000) return `${(n / 1_000_000_000).toFixed(1)}B vistas`;
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M vistas`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(0)}K vistas`;
  return `${n} vistas`;
}

/** Scrape YouTube search results directly (no API key) */
async function searchYouTubeScrape(query: string): Promise<any[]> {
  const url = `https://www.youtube.com/results?search_query=${encodeURIComponent(query)}&sp=EgIQAQ%3D%3D`;
  const response = await fetch(url, {
    headers: {
      "User-Agent":
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
      "Accept-Language": "es-ES,es;q=0.9,en;q=0.8",
      Accept: "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    },
  });

  if (!response.ok) throw new Error(`HTTP ${response.status}`);

  const html = await response.text();
  const match = html.match(/ytInitialData\s*=\s*(\{.*?\});\s*<\/script>/s);
  if (!match) throw new Error("Could not extract ytInitialData");

  const data = JSON.parse(match[1]);
  const contents =
    data?.contents?.twoColumnSearchResultsRenderer?.primaryContents?.sectionListRenderer?.contents?.[0]
      ?.itemSectionRenderer?.contents || [];

  const results: any[] = [];
  for (const item of contents) {
    if (!item.videoRenderer) continue;
    const v = item.videoRenderer;
    if (!v.videoId) continue;
    results.push({
      id: v.videoId,
      title: v.title?.runs?.[0]?.text || "Sin título",
      channel: v.longBylineText?.runs?.[0]?.text || v.ownerText?.runs?.[0]?.text || "Canal desconocido",
      thumbnail:
        v.thumbnail?.thumbnails?.slice(-1)[0]?.url ||
        `https://img.youtube.com/vi/${v.videoId}/hqdefault.jpg`,
      duration: v.lengthText?.simpleText || "0:00",
      views: v.viewCountText?.simpleText || "N/A",
      publishedAt: v.publishedTimeText?.simpleText || "",
      url: `https://www.youtube.com/watch?v=${v.videoId}`,
    });
    if (results.length >= 20) break;
  }
  return results;
}

export const appRouter = router({
  system: systemRouter,
  auth: router({
    me: publicProcedure.query((opts) => opts.ctx.user),
    logout: publicProcedure.mutation(({ ctx }) => {
      const cookieOptions = getSessionCookieOptions(ctx.req);
      ctx.res.clearCookie(COOKIE_NAME, { ...cookieOptions, maxAge: -1 });
      return { success: true } as const;
    }),
  }),

  youtube: router({
    /** Search YouTube videos (direct scraping, no API key) */
    search: publicProcedure
      .input(z.object({ query: z.string().min(1), limit: z.number().optional() }))
      .query(async ({ input }) => {
        try {
          console.log(`[Search] Searching: "${input.query}"`);

          // Try Python ytmusicapi first for richer results
          try {
            const result = await runPython("yt_search.py", [input.query], 20000);
            if (result?.results?.length > 0) {
              console.log(`[Search] ytmusicapi: ${result.results.length} results`);
              return result.results.slice(0, input.limit || 20);
            }
          } catch (pyErr) {
            console.log("[Search] ytmusicapi failed, falling back to scraping:", (pyErr as any).message?.slice(0, 60));
          }

          // Fallback: direct YouTube scraping
          const results = await searchYouTubeScrape(input.query);
          console.log(`[Search] scraping: ${results.length} results`);
          return results.slice(0, input.limit || 20);
        } catch (error) {
          console.error("[Search] Error:", error);
          return [];
        }
      }),

    /** Get video info using oEmbed (no auth needed) */
    videoInfo: publicProcedure
      .input(z.object({ videoId: z.string().min(1) }))
      .query(async ({ input }) => {
        try {
          // Try oEmbed for basic info (always works, no auth)
          const res = await fetch(
            `https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=${input.videoId}&format=json`
          );
          if (!res.ok) throw new Error(`HTTP ${res.status}`);
          const data = await res.json();
          return {
            id: input.videoId,
            title: data.title || "Video de YouTube",
            channel: data.author_name || "Canal desconocido",
            thumbnail:
              data.thumbnail_url || `https://img.youtube.com/vi/${input.videoId}/hqdefault.jpg`,
            duration: "N/A",
            views: "N/A",
            publishedAt: "",
            description: "",
            url: `https://www.youtube.com/watch?v=${input.videoId}`,
          };
        } catch (error) {
          console.error("[VideoInfo] Error:", error);
          // Return minimal info from thumbnail URL
          return {
            id: input.videoId,
            title: "Video de YouTube",
            channel: "Canal desconocido",
            thumbnail: `https://img.youtube.com/vi/${input.videoId}/hqdefault.jpg`,
            duration: "N/A",
            views: "N/A",
            publishedAt: "",
            description: "",
            url: `https://www.youtube.com/watch?v=${input.videoId}`,
          };
        }
      }),

    /**
     * Download a video/audio using Python + yt-dlp (tv_embedded client, no login needed).
     * Returns the file path on the server for the app to download via HTTP.
     */
    download: publicProcedure
      .input(
        z.object({
          videoId: z.string().min(1),
          format: z.string(),
          quality: z.string().optional(),
        })
      )
      .mutation(async ({ input }) => {
        try {
          console.log(`[Download] Starting: ${input.format} for ${input.videoId}`);

          // Create a unique temp dir for this download
          const outputDir = path.join(os.tmpdir(), "snapmusic_dl", input.videoId);
          fs.mkdirSync(outputDir, { recursive: true });

          // Call Python yt-dlp script
          const result = await runPython(
            "yt_download.py",
            [input.videoId, input.format, input.quality || "", outputDir],
            120000 // 2 min timeout
          );

          if (!result.success) {
            console.error(`[Download] Python error: ${result.error}`);
            return { success: false, error: result.error || "Error en la descarga" };
          }

          console.log(`[Download] Done: ${result.filename} (${result.sizeFormatted})`);

          // Return the download URL pointing to our file server endpoint
          const encodedPath = encodeURIComponent(result.path);
          return {
            success: true,
            filename: result.filename,
            downloadUrl: `/api/download/file?path=${encodedPath}&name=${encodeURIComponent(result.filename)}`,
            mimeType: result.filename.endsWith(".mp3") ? "audio/mpeg"
              : result.filename.endsWith(".m4a") ? "audio/mp4"
              : result.filename.endsWith(".mp4") ? "video/mp4"
              : result.filename.endsWith(".webm") ? "video/webm"
              : "application/octet-stream",
            title: result.title,
            sizeFormatted: result.sizeFormatted,
          };
        } catch (error: any) {
          console.error("[Download] Exception:", error);
          return { success: false, error: error.message || "Error inesperado en la descarga" };
        }
      }),
  }),
});

export type AppRouter = typeof appRouter;
