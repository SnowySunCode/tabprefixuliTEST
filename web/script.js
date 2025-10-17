<!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width,initial-scale=1" />
  <title>TabPrefix — Local Editor</title>
  <link rel="stylesheet" href="styles.css">
</head>
<body>
  <div class="wrap">
    <header>
      <h1>TabPrefix — Local Editor</h1>
      <p class="muted">Открой URL, который плагин сгенерировал (пример: <code>https://192.168.1.5:5053/?t=abcd1234</code>)</p>
    </header>

    <section class="controls">
      <label>Группа (LuckPerms): <input id="group" value="Admin"></label>

      <label>Файл (PNG/JPG/GIF):</label>
      <input id="file" type="file" accept="image/*">
      <div class="small">
        <label><input id="animated" type="checkbox"> GIF — анимация</label>
        <label>Задержка кадра (ms): <input id="delay" type="number" value="200" min="20"></label>
      </div>

      <div class="buttons">
        <button id="btnLoad">Загрузить в превью</button>
        <button id="btnSave" class="primary">Save → получить код</button>
      </div>

      <div id="result" class="result" aria-live="polite"></div>
    </section>

    <section class="editor">
      <div class="canvas-wrap">
        <div id="previewArea" class="preview-area" tabindex="0" title="Тяни картинку мышью, WASD/стрелки для подгонки">
          <canvas id="preview" width="480" height="240"></canvas>
        </div>
        <div class="instructions">
          <p>Двигай картинку мышью, используй <b>W A S D</b> или стрелки для смещения. Колёсико — масштаб.</p>
        </div>
      </div>

      <div class="meta">
        <label>Масштаб: <input id="scale" type="range" min="0.1" max="3" step="0.05" value="1"></label>
        <label><input id="centerBtn" type="button" value="Центр" /></label>
      </div>
    </section>

    <footer class="muted">
      <small>Встроенный сервер отдаёт HTML/JS/CSS и принимает /api/upload. Если хостишь отдельно — проксируй запросы к плагину.</small>
    </footer>
  </div>

  <script src="script.js"></script>
</body>
</html>
