// ============================================================
//  ট্রাফিক রাশ — ঢাকার রাস্তায় টিকে থাকো!
//  হালকা 2D-ক্যানভাস এন্ডলেস ট্রাফিক-ডজ গেম (কোনো ডিপেন্ডেন্সি নেই)
//  ক্লাসিক <script> — পুরনো ব্রাউজারেও চলে (কোনো module/importmap নেই)
// ============================================================
(function () {
  'use strict';

  // ---------- পিওর লজিক (টেস্টেবল কোর) ----------
  var LANES = 4;
  var BN_DIGITS = '০১২৩৪৫৬৭৮৯';

  function toBn(n) { return String(n).replace(/\d/g, function (d) { return BN_DIGITS[+d]; }); }

  // বিপদ-ব্যান্ডে যেসব লেন ব্যস্ত (true = ব্যস্ত) — ওই লেনে spawn করা যাবে না
  function freeLanes(occupied) {
    var out = [];
    for (var i = 0; i < LANES; i++) if (!occupied[i]) out.push(i);
    return out;
  }

  // spawn-যোগ্য লেন বাছাই — সব লেন ব্যস্ত হলে -1 (কখনো অসম্ভব স্থিতি তৈরি হবে না)
  function pickSpawnLane(occupied, rng) {
    var free = freeLanes(occupied);
    if (!free.length) return -1;
    return free[Math.floor((rng || Math.random)() * free.length)];
  }

  // AABB সংঘর্ষ — margin ভগ্নাংশ (0.12 = বাক্স ১২% ছোট করে হিসাব)
  function aabb(ax, ay, aw, ah, bx, by, bw, bh, margin) {
    var m = margin || 0;
    aw *= (1 - m * 2); ah *= (1 - m * 2);
    bw *= (1 - m * 2); bh *= (1 - m * 2);
    ax += aw * m; ay += ah * m; bx += bw * m; by += bh * m;
    return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
  }

  // দূরত্ব → গতি (ক্যাপসহ, একঘেয়ে না হয়ে ধীরে ধীরে কঠিন)
  function speedFor(dist) {
    return Math.min(240 + dist * 0.08, 720);
  }

  var core = {
    LANES: LANES, toBn: toBn, freeLanes: freeLanes,
    pickSpawnLane: pickSpawnLane, aabb: aabb, speedFor: speedFor,
  };
  if (typeof module !== 'undefined' && module.exports) { module.exports = core; return; }

  // ---------- ব্রাউজার অংশ ----------
  var $ = function (id) { return document.getElementById(id); };
  var cv = $('cv'), ctx = cv.getContext('2d');
  var W = 0, H = 0, DPR = 1, laneW = 0, roadX = 0, roadW = 0;

  var STATE = { MENU: 0, PLAY: 1, OVER: 2, PAUSE: 3 };
  var state = STATE.MENU;

  // সেভ
  function load(k, d) { try { var v = localStorage.getItem(k); return v === null ? d : v; } catch (e) { return d; } }
  function save(k, v) { try { localStorage.setItem(k, v); } catch (e) {} }
  var best = +load('trafficrush-best', 0) || 0;
  var muted = load('trafficrush-mute', '0') === '1';

  // গেম-ভ্যারিয়েবল
  var dist = 0, speed = 240, score = 0;
  var player = { lane: 1, x: 0, y: 0, w: 0, h: 0 };
  var obs = [];           // ট্রাফিক: {lane,x,y,w,h,rel,color,kind,passed}
  var spawnT = 0;
  var dashOff = 0, sideOff = 0;
  var blocks = [];        // রাস্তার পাশের বিল্ডিং
  var shake = 0, flash = 0;
  var nearToast = null;   // {text, t}
  var lastT = 0;

  function resize() {
    DPR = Math.min(window.devicePixelRatio || 1, 2);
    W = window.innerWidth; H = window.innerHeight;
    cv.width = W * DPR; cv.height = H * DPR;
    cv.style.width = W + 'px'; cv.style.height = H + 'px';
    ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
    roadW = Math.min(W * 0.86, 460);
    roadX = (W - roadW) / 2;
    laneW = roadW / LANES;
    player.w = laneW * 0.58; player.h = player.w * 1.85;
    player.x = laneX(player.lane);
    player.y = H * 0.78;
    if (!blocks.length) seedBlocks();
  }

  function laneX(l) { return roadX + laneW * l + laneW / 2; }

  function seedBlocks() {
    blocks = [];
    var y = -200;
    while (y < H + 300) { y = addBlock(y); }
  }
  function addBlock(y) {
    var h = 60 + Math.random() * 140;
    var colors = ['#1d2233', '#232a3f', '#2a2038', '#1f2b2a', '#2b2333'];
    blocks.push({
      side: Math.random() < 0.5 ? 0 : 1, y: y, h: h,
      w: 26 + Math.random() * 34,
      color: colors[Math.floor(Math.random() * colors.length)],
      win: Math.random() < 0.75,
    });
    return y + h + 14 + Math.random() * 40;
  }

  // ---------- ট্রাফিক ----------
  var KINDS = [
    { kind: 'rickshaw', wF: 0.42, hF: 1.5, spd: 60,  rel: 1.0, colors: ['#2e9e6b', '#c8d23e', '#3fb8a0'] },
    { kind: 'cng',      wF: 0.46, hF: 1.6, spd: 80,  rel: 1.0, colors: ['#3fae4a', '#59c15e'] },
    { kind: 'car',      wF: 0.55, hF: 1.8, spd: 120, rel: 1.0, colors: ['#d8dee9', '#8fa7d6', '#c9747f', '#e0b04d', '#7f8c9b', '#b8647f'] },
    { kind: 'bus',      wF: 0.62, hF: 3.1, spd: 90,  rel: 1.0, colors: ['#4d7fc9', '#c96a4d', '#5fae5f'] },
    { kind: 'truck',    wF: 0.6,  hF: 2.7, spd: 85,  rel: 1.0, colors: ['#8b8f98', '#a0764d'] },
  ];

  function spawn() {
    // বিপদ-ব্যান্ড = উপরের safeGap এলাকা — কোন লেন খালি?
    var safeGap = laneW * 3.6 + speed * 0.5;
    var occupied = [false, false, false, false];
    for (var i = 0; i < obs.length; i++) {
      var o = obs[i];
      if (o.y - o.h / 2 < safeGap && o.y + o.h / 2 > -o.h) occupied[o.lane] = true;
    }
    var lane = core.pickSpawnLane(occupied);
    if (lane < 0) return; // সব ব্যস্ত — এবার স্কিপ (খেলা কখনো অসম্ভব হবে না)
    // ধীর-গতির যান বেশি হারে (রিকশা/সিএনজি/বাস), কার কম
    var pool = [0, 0, 1, 1, 2, 2, 2, 3, 3, 4];
    var k = KINDS[pool[Math.floor(Math.random() * pool.length)]];
    var w = laneW * k.wF, h = w * k.hF;
    obs.push({
      lane: lane, x: laneX(lane), y: -h / 2 - 30, w: w, h: h,
      fwd: k.spd + Math.random() * 60,      // যানের নিজের গতি
      color: k.colors[Math.floor(Math.random() * k.colors.length)],
      kind: k.kind, passed: false,
    });
  }

  // ---------- সাউন্ড (WebAudio, খুব হালকা) ----------
  var AC = null, engOsc = null, engGain = null;
  function audioInit() {
    if (AC) return;
    try {
      AC = new (window.AudioContext || window.webkitAudioContext)();
      engGain = AC.createGain(); engGain.gain.value = 0; engGain.connect(AC.destination);
      engOsc = AC.createOscillator(); engOsc.type = 'sawtooth'; engOsc.frequency.value = 80;
      var lp = AC.createBiquadFilter(); lp.type = 'lowpass'; lp.frequency.value = 240;
      engOsc.connect(lp); lp.connect(engGain); engOsc.start();
    } catch (e) { AC = null; }
  }
  function engine(spd) {
    if (!AC || muted) return;
    engGain.gain.value = 0.045;
    engOsc.frequency.value = 70 + spd * 0.08;
  }
  function engineOff() { if (engGain) engGain.gain.value = 0; }
  function blip(freq, dur, type, vol) {
    if (!AC || muted) return;
    var o = AC.createOscillator(), g = AC.createGain();
    o.type = type || 'sine'; o.frequency.value = freq;
    g.gain.value = vol || 0.15;
    g.gain.exponentialRampToValueAtTime(0.001, AC.currentTime + dur);
    o.connect(g); g.connect(AC.destination);
    o.start(); o.stop(AC.currentTime + dur);
  }
  function crashSound() {
    if (!AC || muted) return;
    var n = AC.createBufferSource(), buf = AC.createBuffer(1, 4410, 44100);
    var d = buf.getChannelData(0);
    for (var i = 0; i < 4410; i++) d[i] = (Math.random() * 2 - 1) * (1 - i / 4410);
    n.buffer = buf;
    var g = AC.createGain(); g.gain.value = 0.5;
    n.connect(g); g.connect(AC.destination); n.start();
    blip(70, 0.5, 'square', 0.3);
  }

  // ---------- গেম-প্রবাহ ----------
  function reset() {
    dist = 0; speed = 240; score = 0;
    player.lane = 1; player.x = laneX(1); player.y = H * 0.78;
    obs = []; spawnT = 0.4; shake = 0; flash = 0; nearToast = null;
    seedBlocks();
    $('score').textContent = 'স্কোর ০';
  }
  function startGame() {
    audioInit();
    if (AC && AC.state === 'suspended') AC.resume();
    reset();
    state = STATE.PLAY;
    $('startScr').classList.add('hidden');
    $('overScr').classList.add('hidden');
    $('pauseScr').classList.add('hidden');
  }
  function gameOver() {
    state = STATE.OVER;
    engineOff(); crashSound();
    var newBest = score > best;
    if (newBest) { best = score; save('trafficrush-best', best); }
    $('finalScore').textContent = 'স্কোর: ' + core.toBn(score) + ' · রেকর্ড: ' + core.toBn(best);
    $('newBest').classList.toggle('hidden', !newBest);
    $('overScr').classList.remove('hidden');
    flash = 0.5;
  }
  function setPause(on) {
    if (state === STATE.PLAY && on) { state = STATE.PAUSE; engineOff(); $('pauseScr').classList.remove('hidden'); }
    else if (state === STATE.PAUSE && !on) { state = STATE.PLAY; $('pauseScr').classList.add('hidden'); lastT = 0; }
  }
  function goMenu() {
    state = STATE.MENU; engineOff();
    $('overScr').classList.add('hidden');
    $('bestLine').textContent = best ? 'সেরা স্কোর: ' + core.toBn(best) : '';
    $('startScr').classList.remove('hidden');
  }

  // ---------- আপডেট ----------
  function update(dt) {
    if (state !== STATE.PLAY) { if (shake > 0) shake -= dt; return; }
    dist += speed * dt * 0.14;
    speed = core.speedFor(dist);
    score = Math.floor(dist);

    // ইনভেন্ট-স্ক্রল
    dashOff = (dashOff + speed * dt) % 64;
    sideOff += speed * dt;
    for (var b = 0; b < blocks.length; b++) {
      blocks[b].y += speed * dt * 0.55;
      if (blocks[b].y > H + 260) { blocks.splice(b, 1); b--; }
    }
    while (blocks.length < 26) addBlockBottom();

    // ট্রাফিক চলা
    for (var i = 0; i < obs.length; i++) {
      var o = obs[i];
      o.y += (speed - o.fwd) * dt;
      if (!o.passed && o.y - o.h / 2 > player.y + player.h / 2) {
        o.passed = true;
        if (Math.abs(o.x - player.x) < laneW * 1.12) {
          score += 5; dist += 5;
          nearToast = { text: 'ক্লোস! +৫', t: 1.1 };
          blip(880, 0.12, 'sine', 0.12);
        }
      }
      if (core.aabb(player.x, player.y, player.w, player.h, o.x, o.y, o.w, o.h, 0.12)) {
        shake = 0.35; gameOver(); return;
      }
      if (o.y - o.h / 2 > H + 80) { obs.splice(i, 1); i--; }
    }

    // spawn
    spawnT -= dt;
    if (spawnT <= 0) {
      spawn();
      spawnT = Math.max(0.32, 0.85 - speed * 0.0006) * (0.75 + Math.random() * 0.5);
    }

    // প্লেয়ার লেন-মুভ (মসৃণ লার্প)
    var tx = laneX(player.lane);
    player.x += (tx - player.x) * Math.min(1, dt * 11);

    $('score').textContent = 'স্কোর ' + core.toBn(score);
    engine(speed);
    if (nearToast) { nearToast.t -= dt; if (nearToast.t <= 0) nearToast = null; }
    if (shake > 0) shake -= dt;
    if (flash > 0) flash -= dt;
  }
  function addBlockBottom() {
    var maxY = 0;
    for (var i = 0; i < blocks.length; i++) if (blocks[i].y + blocks[i].h > maxY) maxY = blocks[i].y + blocks[i].h;
    addBlock(maxY + 14 + Math.random() * 40);
  }

  // ---------- আঁকা ----------
  function roundRect(x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }
  function drawVehicle(x, y, w, h, color, kind) {
    var r = Math.min(w, h) * 0.16;
    ctx.fillStyle = 'rgba(0,0,0,0.35)';
    roundRect(x - w / 2 + 3, y - h / 2 + 5, w, h, r); ctx.fill();
    ctx.fillStyle = color;
    roundRect(x - w / 2, y - h / 2, w, h, r); ctx.fill();
    if (kind === 'bus' || kind === 'truck') {
      ctx.fillStyle = 'rgba(10,14,26,0.8)';
      for (var i = 0; i < 3; i++) {
        roundRect(x - w / 2 + w * 0.14, y - h / 2 + h * (0.14 + i * 0.22), w * 0.72, h * 0.12, 3); ctx.fill();
      }
    } else {
      ctx.fillStyle = 'rgba(10,14,26,0.82)';
      roundRect(x - w * 0.32, y - h * 0.22, w * 0.64, h * 0.3, 4); ctx.fill();
      ctx.fillStyle = 'rgba(150,190,255,0.25)';
      roundRect(x - w * 0.32, y - h * 0.34, w * 0.64, h * 0.1, 3); ctx.fill();
    }
    if (kind === 'rickshaw') { // ক্যানোপি ছাদ
      ctx.fillStyle = 'rgba(255,255,255,0.28)';
      roundRect(x - w * 0.36, y - h * 0.42, w * 0.72, h * 0.16, 3); ctx.fill();
    }
    ctx.fillStyle = '#0c0e14'; // চাকা
    var wy = y - h * 0.3, wy2 = y + h * 0.22, ww = Math.max(3, w * 0.13), wh = h * 0.1;
    ctx.fillRect(x - w / 2 - ww * 0.35, wy, ww, wh); ctx.fillRect(x + w / 2 - ww * 0.65, wy, ww, wh);
    ctx.fillRect(x - w / 2 - ww * 0.35, wy2, ww, wh); ctx.fillRect(x + w / 2 - ww * 0.65, wy2, ww, wh);
    // হেডলাইট গ্লো
    ctx.fillStyle = 'rgba(255,230,150,0.5)';
    ctx.fillRect(x - w * 0.3, y + h / 2 - 2, w * 0.12, 3);
    ctx.fillRect(x + w * 0.18, y + h / 2 - 2, w * 0.12, 3);
  }

  function draw() {
    ctx.save();
    if (shake > 0) ctx.translate((Math.random() - 0.5) * 14 * shake * 3, (Math.random() - 0.5) * 14 * shake * 3);
    // ফুটপাত/বিল্ডিং
    ctx.fillStyle = '#12151f';
    ctx.fillRect(0, 0, W, H);
    for (var b = 0; b < blocks.length; b++) {
      var bl = blocks[b];
      var bx = bl.side === 0 ? roadX / 2 - bl.w / 2 : W - roadX / 2 - bl.w / 2;
      ctx.fillStyle = bl.color;
      ctx.fillRect(bx, bl.y, bl.w, bl.h);
      if (bl.win) {
        ctx.fillStyle = 'rgba(255,214,120,0.5)';
        for (var wy = bl.y + 8; wy < bl.y + bl.h - 8; wy += 16) {
          for (var wx = bx + 5; wx < bx + bl.w - 8; wx += 12) {
            if ((wx + wy + bl.h) % 3 < 2) ctx.fillRect(wx, wy, 5, 7);
          }
        }
      }
    }
    // রাস্তা
    ctx.fillStyle = '#1e222c';
    ctx.fillRect(roadX, 0, roadW, H);
    ctx.fillStyle = 'rgba(255,255,255,0.75)';
    ctx.fillRect(roadX + 2, 0, 3, H); ctx.fillRect(roadX + roadW - 5, 0, 3, H);
    // লেন-ড্যাশ
    ctx.fillStyle = 'rgba(255,255,255,0.35)';
    for (var l = 1; l < LANES; l++) {
      var dx = roadX + laneW * l;
      for (var y = -64 + dashOff; y < H; y += 64) ctx.fillRect(dx - 2, y, 4, 34);
    }
    // ট্রাফিক
    for (var i = 0; i < obs.length; i++) {
      var o = obs[i];
      drawVehicle(o.x, o.y, o.w, o.h, o.color, o.kind);
    }
    // প্লেয়ার (হলুদ ট্যাক্সি)
    drawVehicle(player.x, player.y, player.w, player.h, '#f2c53d', 'car');
    ctx.fillStyle = '#14161d'; // ট্যাক্সি ছাদ-চিহ্ন
    roundRect(player.x - player.w * 0.14, player.y - player.h * 0.1, player.w * 0.28, player.h * 0.09, 3); ctx.fill();

    // ক্লোস-টোস্ট
    if (nearToast) {
      ctx.fillStyle = 'rgba(120,255,170,' + Math.min(1, nearToast.t) + ')';
      ctx.font = 'bold 22px sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText(nearToast.text, W / 2, player.y - player.h);
    }
    ctx.restore();
    if (flash > 0) { ctx.fillStyle = 'rgba(255,60,40,' + flash * 0.9 + ')'; ctx.fillRect(0, 0, W, H); }
  }

  // ---------- লুপ ----------
  function frame(t) {
    var dt = lastT ? Math.min(0.05, (t - lastT) / 1000) : 0.016;
    lastT = t;
    update(dt);
    draw();
    requestAnimationFrame(frame);
  }

  // ---------- ইনপুট ----------
  function move(dir) {
    if (state !== STATE.PLAY) return;
    var nl = Math.max(0, Math.min(LANES - 1, player.lane + dir));
    if (nl !== player.lane) { player.lane = nl; blip(300 + nl * 40, 0.06, 'triangle', 0.06); }
  }
  document.addEventListener('touchstart', function (e) {
    if (state !== STATE.PLAY) return;
    var t = e.changedTouches[0];
    move(t.clientX < W / 2 ? -1 : 1);
  }, { passive: true });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'ArrowLeft' || e.key === 'a') move(-1);
    if (e.key === 'ArrowRight' || e.key === 'd') move(1);
    if (e.key === ' ' && state === STATE.MENU) startGame();
    if (e.key === 'p') setPause(state === STATE.PLAY);
  });
  document.addEventListener('visibilitychange', function () { if (document.hidden) setPause(true); });

  $('btnStart').onclick = startGame;
  $('btnRetry').onclick = startGame;
  $('btnHome').onclick = goMenu;
  $('btnResume').onclick = function () { setPause(false); };
  $('btnPause').onclick = function () { setPause(state === STATE.PLAY); };
  $('btnMute').onclick = function () {
    muted = !muted; save('trafficrush-mute', muted ? '1' : '0');
    $('btnMute').textContent = muted ? '🔇' : '🔊';
    if (muted) engineOff();
  };
  $('btnMute').textContent = muted ? '🔇' : '🔊';

  window.addEventListener('resize', resize);
  resize();
  goMenu();
  requestAnimationFrame(frame);
})();
