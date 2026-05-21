import "dotenv/config";
import express from "express";
import { createServer } from "http";
import net from "net";
import * as fs from "fs";
import * as path from "path";
import { createExpressMiddleware } from "@trpc/server/adapters/express";
import { registerOAuthRoutes } from "./oauth";
import { registerStorageProxy } from "./storageProxy";
import { appRouter } from "../routers";
import { createContext } from "./context";

const DOWNLOAD_DIR = "/tmp/snapmusic_downloads";

function isPortAvailable(port: number): Promise<boolean> {
  return new Promise((resolve) => {
    const server = net.createServer();
    server.listen(port, () => {
      server.close(() => resolve(true));
    });
    server.on("error", () => resolve(false));
  });
}

async function findAvailablePort(startPort: number = 3000): Promise<number> {
  for (let port = startPort; port < startPort + 20; port++) {
    if (await isPortAvailable(port)) {
      return port;
    }
  }
  throw new Error(`No available port found starting from ${startPort}`);
}

async function startServer() {
  const app = express();
  const server = createServer(app);

  // Enable CORS for all routes - reflect the request origin to support credentials
  app.use((req, res, next) => {
    const origin = req.headers.origin;
    if (origin) {
      res.header("Access-Control-Allow-Origin", origin);
    }
    res.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
    res.header(
      "Access-Control-Allow-Headers",
      "Origin, X-Requested-With, Content-Type, Accept, Authorization",
    );
    res.header("Access-Control-Allow-Credentials", "true");

    // Handle preflight requests
    if (req.method === "OPTIONS") {
      res.sendStatus(200);
      return;
    }
    next();
  });

  app.use(express.json({ limit: "50mb" }));
  app.use(express.urlencoded({ limit: "50mb", extended: true }));

  registerStorageProxy(app);
  registerOAuthRoutes(app);

  app.get("/api/health", (_req, res) => {
    res.json({ ok: true, timestamp: Date.now() });
  });

  // Serve downloaded files by absolute path (secure: only /tmp/snapmusic_dl/** allowed)
  app.get("/api/download/file", (req, res) => {
    const rawPath = decodeURIComponent((req.query.path as string) || "");
    const displayName = decodeURIComponent((req.query.name as string) || path.basename(rawPath));

    // Security: only allow files inside /tmp/snapmusic_dl/
    const ALLOWED_BASE = "/tmp/snapmusic_dl";
    if (!rawPath || !rawPath.startsWith(ALLOWED_BASE)) {
      res.status(403).json({ error: "Access denied" });
      return;
    }

    if (!fs.existsSync(rawPath)) {
      res.status(404).json({ error: "File not found" });
      return;
    }

    const ext = path.extname(rawPath).toLowerCase();
    const mimeTypes: Record<string, string> = {
      ".mp3": "audio/mpeg",
      ".m4a": "audio/mp4",
      ".ogg": "audio/ogg",
      ".wav": "audio/wav",
      ".mp4": "video/mp4",
      ".webm": "video/webm",
    };

    const contentType = mimeTypes[ext] || "application/octet-stream";
    const stat = fs.statSync(rawPath);
    res.setHeader("Content-Type", contentType);
    res.setHeader("Content-Length", stat.size);
    res.setHeader("Content-Disposition", `attachment; filename="${encodeURIComponent(displayName)}"`);
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Cache-Control", "no-cache");

    const fileStream = fs.createReadStream(rawPath);
    fileStream.on("error", (err) => {
      console.error("[FileServe] Stream error:", err);
      if (!res.headersSent) res.status(500).json({ error: "Stream error" });
    });
    fileStream.pipe(res);
  });

  // Legacy endpoint (keep for backward compat)
  app.get("/api/download/:filename", (req, res) => {
    const filename = decodeURIComponent(req.params.filename);
    const filePath = path.join(DOWNLOAD_DIR, filename);
    if (!fs.existsSync(filePath)) {
      res.status(404).json({ error: "File not found" });
      return;
    }
    const ext = path.extname(filename).toLowerCase();
    const mimeTypes: Record<string, string> = {
      ".mp3": "audio/mpeg", ".m4a": "audio/mp4", ".ogg": "audio/ogg",
      ".wav": "audio/wav", ".mp4": "video/mp4", ".webm": "video/webm",
    };
    res.setHeader("Content-Type", mimeTypes[ext] || "application/octet-stream");
    res.setHeader("Content-Disposition", `attachment; filename="${filename}"`);
    res.setHeader("Access-Control-Allow-Origin", "*");
    fs.createReadStream(filePath).pipe(res);
  });

  app.use(
    "/api/trpc",
    createExpressMiddleware({
      router: appRouter,
      createContext,
    }),
  );

  const preferredPort = parseInt(process.env.PORT || "3000");
  const port = await findAvailablePort(preferredPort);

  if (port !== preferredPort) {
    console.log(`Port ${preferredPort} is busy, using port ${port} instead`);
  }

  server.listen(port, () => {
    console.log(`[api] server listening on port ${port}`);
  });
}

startServer().catch(console.error);
