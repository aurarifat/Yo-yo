const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 3000;

const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>ApexBoost Game Engine — Android & GitHub Actions APK Builder</title>
  <style>
    :root {
      --bg: #090d16;
      --surface: #111827;
      --card: #182235;
      --cyan: #00e5ff;
      --green: #00e676;
      --amber: #ffd600;
      --text: #f8fafc;
      --muted: #94a3b8;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: var(--bg);
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      padding: 24px;
      line-height: 1.5;
    }
    .container { max-width: 960px; margin: 0 auto; }
    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      background: var(--surface);
      border: 1px solid rgba(0, 229, 255, 0.3);
      border-radius: 16px;
      padding: 20px 24px;
      margin-bottom: 20px;
    }
    .title { font-size: 20px; font-weight: 800; letter-spacing: 0.5px; color: var(--cyan); }
    .subtitle { font-size: 13px; color: var(--muted); font-family: monospace; margin-top: 4px; }
    .badge {
      background: rgba(0, 230, 118, 0.15);
      color: var(--green);
      border: 1px solid rgba(0, 230, 118, 0.5);
      padding: 6px 14px;
      border-radius: 999px;
      font-size: 12px;
      font-weight: 700;
      font-family: monospace;
    }
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 16px;
      margin-bottom: 20px;
    }
    .card {
      background: var(--card);
      border: 1px solid rgba(148, 163, 184, 0.2);
      border-radius: 14px;
      padding: 18px;
    }
    .card h3 {
      font-size: 14px;
      text-transform: uppercase;
      letter-spacing: 0.6px;
      color: var(--cyan);
      margin-bottom: 10px;
    }
    .card p, .card li {
      font-size: 13px;
      color: var(--muted);
      margin-bottom: 6px;
    }
    .card ul { padding-left: 18px; }
    code {
      font-family: monospace;
      color: var(--text);
      background: rgba(0, 0, 0, 0.35);
      padding: 2px 6px;
      border-radius: 4px;
    }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <div>
        <div class="title">APEXBOOST ADB &amp; GAME ENGINE</div>
        <div class="subtitle">Android Kotlin / Jetpack Compose • AGP 9.1.1 • Gradle 9.3.1 • Shizuku 13.1.5</div>
      </div>
      <div class="badge">✓ READY FOR GITHUB APK BUILD</div>
    </div>

    <div class="grid">
      <div class="card">
        <h3>GitHub Actions APK Workflow</h3>
        <ul>
          <li>Workflow: <code>.github/workflows/build-apk.yml</code></li>
          <li>Java: <code>Temurin 17</code> (verified >= 17)</li>
          <li>Gradle: <code>9.3.1</code> with <code>AGP 9.1.1</code></li>
          <li>Keystore: Auto-generates <code>./debug.keystore</code></li>
          <li>Publishes artifact <code>app-debug-apk</code> &amp; GitHub Release</li>
        </ul>
      </div>

      <div class="card">
        <h3>Shizuku &amp; ShellExecutor Engine</h3>
        <ul>
          <li><code>ShellExecutor.kt</code>: Allowlisted <code>Shizuku.newProcess()</code> execution with <code>PermissionDeniedException</code> handling</li>
          <li><code>ShizukuManager.kt</code>: Real binder detection, permission flow &amp; Wireless Debugging setup</li>
          <li><code>PerformanceEngine.kt</code>: Fixed performance mode, refresh rate, resolution (<code>wm size</code>), animations &amp; DND</li>
        </ul>
      </div>

      <div class="card">
        <h3>Telemetry, Room DB &amp; Overlays</h3>
        <ul>
          <li><code>DeviceTelemetryProvider.kt</code>: Real RAM, Storage, Battery, Thermal, CPU &amp; Choreographer FPS</li>
          <li><code>HardwareOverlayService.kt</code>: Real floating Crosshair &amp; HUD overlay via <code>WindowManager</code></li>
          <li><code>BoosterDatabase.kt</code>: Room persistence for per-game profiles &amp; automatic setting restore</li>
        </ul>
      </div>
    </div>
  </div>
</body>
</html>`;

const server = http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end(html);
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(\`Server listening on http://0.0.0.0:\${PORT}\`);
});
