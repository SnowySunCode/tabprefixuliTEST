// Minimal JS to preview and upload image as data URL to /api/upload?t=<token>
let fileEl = document.getElementById('file');
let groupEl = document.getElementById('group');
let uploadBtn = document.getElementById('upload');
let preview = document.getElementById('preview');
let ctx = preview.getContext('2d');
let animatedEl = document.getElementById('animated');
let scaleEl = document.getElementById('scale');
let leftBtn = document.getElementById('left');
let rightBtn = document.getElementById('right');
let upBtn = document.getElementById('up');
let downBtn = document.getElementById('down');
let frameDelayEl = document.getElementById('framedelay');
let codeBox = document.getElementById('codebox');

let img = new Image();
let pos = {x:240, y:90, s:1};

fileEl.addEventListener('change', e => {
  let f = e.target.files[0];
  if (!f) return;
  let reader = new FileReader();
  reader.onload = ev => { img.src = ev.target.result; img.onload = draw; }
  reader.readAsDataURL(f);
});

function draw() {
  ctx.clearRect(0,0,preview.width,preview.height);
  ctx.fillStyle = "#222";
  ctx.fillRect(0,0,preview.width,preview.height);
  if (!img.width) return;
  let w = img.width * pos.s;
  let h = img.height * pos.s;
  ctx.drawImage(img, pos.x - w/2, pos.y - h/2, w, h);
}

scaleEl.addEventListener('input', () => { pos.s = parseFloat(scaleEl.value); draw(); });
leftBtn.addEventListener('click', ()=>{ pos.x -= 8; draw();});
rightBtn.addEventListener('click', ()=>{ pos.x += 8; draw();});
upBtn.addEventListener('click', ()=>{ pos.y -= 8; draw();});
downBtn.addEventListener('click', ()=>{ pos.y += 8; draw();});

uploadBtn.addEventListener('click', async () => {
  if (!fileEl.files[0]) { alert('Choose a file'); return; }
  let f = fileEl.files[0];
  let fr = new FileReader();
  fr.onload = async ev => {
    let data = ev.target.result;
    // build payload
    let payload = {
      group: groupEl.value || "default",
      filename: Date.now() + "_" + f.name,
      data: data,
      animated: animatedEl.checked,
      frameDelay: parseInt(frameDelayEl.value)||200,
      posX: pos.x,
      posY: pos.y,
      scale: pos.s
    };
    // token expected in query ?t=... ; keep it
    let url = location.href;
    if (url.indexOf('?') === -1) { alert('No token in URL (open via /?t=token)'); return; }
    let api = "/api/upload" + location.search;
    let res = await fetch(api, { method:'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify(payload)});
    let json = await res.json();
    if (json.ok) {
      codeBox.innerText = "Code: " + json.code;
    } else {
      codeBox.innerText = "Error: " + JSON.stringify(json);
    }
  };
  fr.readAsDataURL(f);
});
