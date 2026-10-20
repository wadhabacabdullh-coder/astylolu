// Jarvis local server: serves the app and relays requests to OpenRouter (AI)
// and Fish Audio (voice). Fish Audio doesn't allow calls straight from a web
// page, so the phone's browser talks to this server instead.
//
// No dependencies. Needs Node 18+.  Run:  node server.js
// Keys come from the app's settings, or from OPENROUTER_API_KEY / FISH_API_KEY
// (environment or a .env file next to this script).

const http = require("http");
const fs = require("fs");
const path = require("path");

const ROOT = __dirname;
loadDotEnv(path.join(ROOT, ".env"));

const PORT = parseInt(process.env.PORT || "8080", 10);
// Localhost only by default, so nobody else on your Wi-Fi can use your keys.
const HOST = process.env.HOST || "127.0.0.1";

const TYPES = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".webmanifest": "application/manifest+json",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".json": "application/json",
};

function loadDotEnv(file) {
  let text;
  try { text = fs.readFileSync(file, "utf8"); } catch { return; }
  for (const line of text.split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*?)\s*$/);
    if (m && !(m[1] in process.env)) process.env[m[1]] = m[2].replace(/^["']|["']$/g, "");
  }
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on("data", (c) => {
      size += c.length;
      if (size > 1e6) { reject(new Error("body too large")); req.destroy(); }
      else chunks.push(c);
    });
    req.on("end", () => resolve(Buffer.concat(chunks)));
    req.on("error", reject);
  });
}

function sendJson(res, status, obj) {
  res.writeHead(status, { "content-type": "application/json" });
  res.end(JSON.stringify(obj));
}

// Copies an upstream fetch Response to the client, streaming as it arrives.
async function pipe(upstream, res) {
  res.writeHead(upstream.status, {
    "content-type": upstream.headers.get("content-type") || "application/octet-stream",
    "cache-control": "no-store",
  });
  if (!upstream.body) return res.end();
  for await (const chunk of upstream.body) res.write(chunk);
  res.end();
}

async function handleChat(req, res) {
  const key = req.headers["x-openrouter-key"] || process.env.OPENROUTER_API_KEY;
  if (!key) return sendJson(res, 401, { error: { message: "No OpenRouter API key set." } });
  const body = await readBody(req);
  const ac = new AbortController();
  res.on("close", () => ac.abort()); // user tapped stop: stop paying for tokens
  const upstream = await fetch("https://openrouter.ai/api/v1/chat/completions", {
    method: "POST",
    signal: ac.signal,
    headers: {
      authorization: `Bearer ${key}`,
      "content-type": "application/json",
      "x-title": "Jarvis",
    },
    body,
  });
  await pipe(upstream, res);
}

async function handleTts(req, res) {
  const key = req.headers["x-fish-key"] || process.env.FISH_API_KEY;
  if (!key) return sendJson(res, 401, { error: { message: "No Fish Audio API key set." } });
  const { text, voice, model } = JSON.parse((await readBody(req)).toString() || "{}");
  if (!text) return sendJson(res, 400, { error: { message: "No text." } });
  const payload = { text, format: "mp3", mp3_bitrate: 64, latency: "balanced", normalize: true };
  if (voice) payload.reference_id = voice;
  const upstream = await fetch("https://api.fish.audio/v1/tts", {
    method: "POST",
    headers: {
      authorization: `Bearer ${key}`,
      "content-type": "application/json",
      model: model || "s2-pro",
    },
    body: JSON.stringify(payload),
  });
  await pipe(upstream, res);
}

async function handleModels(req, res) {
  const upstream = await fetch("https://openrouter.ai/api/v1/models");
  await pipe(upstream, res);
}

function serveStatic(req, res) {
  let rel = decodeURIComponent(new URL(req.url, "http://x").pathname);
  if (rel.endsWith("/")) rel += "index.html";
  const file = path.normalize(path.join(ROOT, rel));
  const ext = path.extname(file);
  if (!file.startsWith(ROOT + path.sep) || !TYPES[ext]) { res.writeHead(404); return res.end("Not found"); }
  fs.readFile(file, (err, data) => {
    if (err) { res.writeHead(404); return res.end("Not found"); }
    res.writeHead(200, { "content-type": TYPES[ext], "cache-control": "no-cache" });
    res.end(data);
  });
}

const routes = {
  "POST /api/chat": handleChat,
  "POST /api/tts": handleTts,
  "GET /api/models": handleModels,
};

http.createServer(async (req, res) => {
  const route = routes[`${req.method} ${req.url.split("?")[0]}`];
  if (!route) return serveStatic(req, res);
  try {
    await route(req, res);
  } catch (err) {
    if (err.name === "AbortError") return;
    console.error(req.url, err.message);
    if (!res.headersSent) sendJson(res, 502, { error: { message: "Can't reach the service: " + err.message } });
    else res.end();
  }
}).listen(PORT, HOST, () => {
  console.log(`\n  JARVIS is online →  http://localhost:${PORT}\n`);
  console.log("  Open that link in Chrome. Keep this window running.\n");
});
