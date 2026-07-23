// Väder & Kläder — uppstartssplash (väder-tema, glasmorfism + Groq-boot)
// Fullskärms-takeover som visas första besöket per webbläsare. En väderscen tonar från
// REGN → ÅSKA (blixt + gul flash) → SOL (gul glow), medan statusrader tickar igenom
// datakällorna och tänds gröna. Auto-injiceras av WP-pluginens footer-script.
// Återspela med ?splash=1 eller window.vkReplaySplash().
(function () {
  'use strict';

  var API = window.VK_API_URL || 'https://vaderklader-1.onrender.com';
  var SEEN_KEY = 'vk_splash_seen_v1';
  var GPS_KEY  = 'vk_gps_position';
  var FORCE = /[?&]splash=1/.test(location.search);
  var GPS_ROW = 1;

  var ROWS = [
    { ic: '🤖', t: 'Groq AI',       s: 'gpt-oss-120b \xb7 modell laddad', tag: 'ONLINE' },
    { ic: '📍', t: 'GPS-position',  kind: 'gps' },
    { ic: '🛰️', t: 'Open-Meteo',    s: 'V\xe4der-API \xb7 200 OK', tag: 'LIVE' },
    { ic: '🧩', t: 'Prompt',        s: 'V\xe4derkontext byggd f\xf6r AI', tag: 'OK' },
    { ic: '🌡️', t: 'Parametrar',    s: 'Temp, k\xe4nns-som, vind &amp; fukt' },
    { ic: '🌧️', t: 'Nederb\xf6rd',   s: 'Regn, sn\xf6 &amp; nederb\xf6rdsrisk' },
    { ic: '⏱️', t: 'Rate-limit',    s: 'API-kvot \xb7 klar', tag: 'OK' },
    { ic: '👕', t: 'Kl\xe4dr\xe5d',     s: 'AI-f\xf6rslag initieras…' }
  ];

  var BOOT_PHRASES = ['ansluter till groq api', 'GET open-meteo /forecast → 200', 'bygger v\xe4der-prompt', 'genererar ditt kl\xe4dr\xe5d'];

  // Målkoordinater: cachad GPS om den finns, annars Stockholm som default.
  var gpsTarget = readGps();

  function readGps() {
    try {
      var raw = localStorage.getItem(GPS_KEY);
      if (raw) {
        var o = JSON.parse(raw);
        var la = parseFloat(o.lat != null ? o.lat : o.latitude);
        var lo = parseFloat(o.lon != null ? o.lon : (o.lng != null ? o.lng : o.longitude));
        if (!isNaN(la) && !isNaN(lo)) return { lat: la, lon: lo };
      }
    } catch (e) {}
    return { lat: 59.3293, lon: 18.0686 };
  }

  function gpsText(e) {
    e = Math.max(0, Math.min(1, e)); // skydd mot tajmings-glitchar (negativ ease → felaktiga koordinater)
    var la = (gpsTarget.lat * e), lo = (gpsTarget.lon * e);
    var ns = gpsTarget.lat >= 0 ? 'N' : 'S', ew = gpsTarget.lon >= 0 ? 'O' : 'V';
    return '<b>' + Math.abs(la).toFixed(4) + '\xb0 ' + ns + '</b> \xb7 <b>' + Math.abs(lo).toFixed(4) + '\xb0 ' + ew + '</b>';
  }

  var animated = {};

  function injectStyles() {
    if (document.getElementById('vksp-style')) return;
    var css = document.createElement('style');
    css.id = 'vksp-style';
    css.textContent = [
      '.vksp{position:fixed;inset:0;z-index:99999;overflow:hidden;',
        'display:flex;flex-direction:column;align-items:center;justify-content:center;',
        'padding:30px 22px;text-align:center;',
        "font-family:'Segoe UI',system-ui,-apple-system,BlinkMacSystemFont,Roboto,sans-serif;",
        'background:radial-gradient(ellipse at 50% 0%,#132437,#0a141f 60%,#060d16 100%);',
        'opacity:1;transition:opacity .5s ease;}',
      '.vksp.vksp-out{opacity:0;}',
      '.vksp::before{content:"";position:absolute;inset:0;pointer-events:none;',
        'background:radial-gradient(ellipse at 74% 8%,rgba(255,196,64,.16) 0%,transparent 48%),',
          'radial-gradient(ellipse at 16% 92%,rgba(56,132,255,.16) 0%,transparent 46%);',
        'animation:vksp-aurora 7s ease-in-out infinite alternate;}',
      '.vksp-inner{position:relative;z-index:1;width:100%;max-width:400px;',
        'display:flex;flex-direction:column;align-items:center;}',
      // Glaskort — glöden ligger i background-lagret (inte ::before) så texten inte tvättas ur
      '.vksp-card{position:relative;width:100%;padding:0 22px 24px;border-radius:26px;overflow:hidden;',
        'display:flex;flex-direction:column;align-items:center;',
        'background:radial-gradient(120% 50% at 50% 120%,rgba(255,196,64,.16),transparent 70%),',
          'radial-gradient(120% 40% at 50% -6%,rgba(56,132,255,.2),transparent 66%),',
          'linear-gradient(160deg,rgba(20,34,54,.78),rgba(9,16,28,.9));',
        '-webkit-backdrop-filter:blur(26px) saturate(150%);backdrop-filter:blur(26px) saturate(150%);',
        'border:1px solid rgba(255,255,255,.14);',
        'box-shadow:0 30px 80px rgba(0,0,0,.6),inset 0 1px 0 rgba(255,255,255,.2),',
          'inset 0 0 50px rgba(56,132,255,.06),0 0 90px rgba(255,196,64,.12);',
        'animation:vksp-rise .55s ease both;}',
      // ── Väderscen ──
      '.vksp-stage{position:relative;width:calc(100% + 44px);margin:0 -22px 6px;height:150px;overflow:hidden;',
        'border-radius:26px 26px 0 0;transition:background .8s ease;',
        'background:linear-gradient(180deg,#3b4f66,#243342);}',
      '.vksp-stage.storm{background:linear-gradient(180deg,#1c2634,#141b26);}',
      '.vksp-stage.sun{background:linear-gradient(180deg,#5ab0f0,#bfe3ff 60%,#ffe6a6);}',
      // moln
      '.vksp-cloud{position:absolute;top:26px;left:50%;transform:translateX(-50%);width:120px;height:38px;',
        'background:linear-gradient(180deg,#e8eef5,#b7c4d4);border-radius:22px;opacity:.92;',
        'box-shadow:0 8px 20px rgba(0,0,0,.35);transition:opacity .7s ease,transform .9s ease,filter .7s;filter:brightness(1);}',
      '.vksp-cloud::before,.vksp-cloud::after{content:"";position:absolute;background:inherit;border-radius:50%;}',
      '.vksp-cloud::before{width:52px;height:52px;top:-22px;left:16px;}',
      '.vksp-cloud::after{width:40px;height:40px;top:-16px;right:20px;}',
      '.vksp-stage.storm .vksp-cloud{filter:brightness(.55);}',
      '.vksp-stage.sun .vksp-cloud{opacity:0;transform:translateX(-140%);}',
      // regn
      '.vksp-rain{position:absolute;inset:0;opacity:0;transition:opacity .6s ease;}',
      '.vksp-stage.rain .vksp-rain,.vksp-stage.storm .vksp-rain{opacity:1;}',
      '.vksp-drop{position:absolute;top:-14px;width:2px;height:14px;border-radius:2px;',
        'background:linear-gradient(180deg,rgba(174,214,255,0),rgba(174,214,255,.9));',
        'animation:vksp-fall .7s linear infinite;}',
      // sol
      '.vksp-sun{position:absolute;top:24px;left:50%;transform:translateX(-50%) scale(.3);opacity:0;',
        'width:56px;height:56px;border-radius:50%;transition:opacity .8s ease,transform .8s ease;',
        'background:radial-gradient(circle at 40% 35%,#fff3c4,#ffcf3f 55%,#ff9e2c);',
        'box-shadow:0 0 30px rgba(255,193,64,.85),0 0 70px rgba(255,193,64,.55);}',
      '.vksp-stage.sun .vksp-sun{opacity:1;transform:translateX(-50%) scale(1);}',
      '.vksp-rays{position:absolute;top:24px;left:50%;width:56px;height:56px;margin-left:-28px;opacity:0;',
        'transition:opacity .8s ease .1s;}',
      '.vksp-rays::before{content:"";position:absolute;inset:-22px;border-radius:50%;',
        'background:conic-gradient(rgba(255,205,80,.55) 0 8deg,transparent 8deg 30deg);',
        '-webkit-mask:radial-gradient(transparent 30px,#000 31px);mask:radial-gradient(transparent 30px,#000 31px);',
        'animation:vksp-spin 9s linear infinite;}',
      '.vksp-stage.sun .vksp-rays{opacity:1;}',
      // blixt + flash
      '.vksp-bolt{position:absolute;top:44px;left:50%;transform:translateX(-50%) scale(.9);width:26px;height:44px;opacity:0;',
        'filter:drop-shadow(0 0 8px rgba(255,224,120,.9));}',
      '.vksp-bolt svg{width:100%;height:100%;fill:#ffe07a;}',
      '.vksp-stage.storm .vksp-bolt{animation:vksp-strike 1.6s ease-in-out infinite;}',
      '.vksp-flash{position:absolute;inset:0;background:radial-gradient(circle at 50% 30%,rgba(255,240,190,.9),rgba(255,240,190,0) 60%);opacity:0;pointer-events:none;}',
      '.vksp-stage.storm .vksp-flash{animation:vksp-flash 1.6s ease-in-out infinite;}',
      // liten fas-etikett
      '.vksp-phase{position:absolute;bottom:8px;left:50%;transform:translateX(-50%);z-index:2;',
        'font-size:.56rem;font-weight:800;letter-spacing:.22em;text-transform:uppercase;',
        'padding:2px 10px;border-radius:20px;color:#fff;background:rgba(0,0,0,.28);',
        '-webkit-backdrop-filter:blur(4px);backdrop-filter:blur(4px);border:1px solid rgba(255,255,255,.16);}',
      // ── Titel / chip / boot ──
      '.vksp-title{font-size:1.3rem;font-weight:800;letter-spacing:-.4px;margin:16px 0 8px;',
        'background:linear-gradient(120deg,#fff 34%,#ffd98a 100%);',
        '-webkit-background-clip:text;background-clip:text;-webkit-text-fill-color:transparent;',
        'filter:drop-shadow(0 1px 8px rgba(255,196,64,.45));animation:vksp-rise .5s ease .05s both;}',
      '.vksp-chip{display:inline-flex;align-items:center;gap:5px;margin:0 0 14px;padding:3px 11px;',
        'border-radius:20px;background:rgba(255,196,64,.12);border:1px solid rgba(255,196,64,.42);',
        'font-size:.6rem;font-weight:800;letter-spacing:.14em;text-transform:uppercase;color:#ffd98a;',
        'animation:vksp-rise .5s ease .08s both;}',
      '.vksp-chip svg{width:11px;height:11px;fill:#ffc440;}',
      '.vksp-boot{font-family:ui-monospace,SFMono-Regular,"Cascadia Code",Consolas,monospace;',
        'font-size:.74rem;color:rgba(206,226,255,.85);margin:0 0 18px;min-height:1.2em;',
        'letter-spacing:.2px;animation:vksp-rise .5s ease .1s both;}',
      '.vksp-boot .pr{color:#4ade80;font-weight:700;margin-right:5px;}',
      '.vksp-cur{display:inline-block;width:7px;height:.95em;background:#ffc440;margin-left:3px;',
        'vertical-align:-1px;animation:vksp-blink 1s steps(1) infinite;}',
      // ── Rader ──
      '.vksp-rows{width:100%;display:flex;flex-direction:column;gap:7px;}',
      '.vksp-row{display:flex;align-items:center;gap:11px;text-align:left;padding:9px 12px;border-radius:13px;',
        'background:rgba(148,182,230,.08);border:1px solid rgba(148,182,230,.18);',
        'box-shadow:inset 0 1px 0 rgba(255,255,255,.09);',
        'opacity:0;transform:translateY(8px);transition:opacity .35s ease,transform .35s ease,border-color .3s,background .3s,box-shadow .3s;}',
      '.vksp-row.show{opacity:1;transform:translateY(0);}',
      '.vksp-row.done{border-color:rgba(52,211,153,.5);background:rgba(34,197,94,.13);',
        'box-shadow:inset 0 1px 0 rgba(255,255,255,.12),0 0 22px rgba(34,197,94,.2);}',
      '.vksp-ic{font-size:1.05rem;flex-shrink:0;width:22px;text-align:center;filter:drop-shadow(0 0 5px rgba(255,196,64,.4));}',
      '.vksp-tx{flex:1;min-width:0;display:flex;flex-direction:column;line-height:1.25;}',
      '.vksp-tx b{font-size:.83rem;font-weight:700;color:#eef4ff;display:flex;align-items:center;gap:7px;}',
      '.vksp-tx i{font-size:.69rem;font-style:normal;color:rgba(206,226,255,.78);',
        'white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}',
      '.vksp-tx i b{color:#ffd98a;font-weight:800;font-style:normal;display:inline;}',
      '.vksp-onl{display:inline-flex;align-items:center;gap:4px;padding:1px 7px 1px 5px;border-radius:20px;',
        'font-size:.52rem;font-weight:800;letter-spacing:.1em;text-transform:uppercase;',
        'color:#6ee7b7;background:rgba(34,197,94,.14);border:1px solid rgba(34,197,94,.4);box-shadow:0 0 10px rgba(34,197,94,.25);}',
      '.vksp-onl.live{color:#ffd98a;background:rgba(255,196,64,.14);border-color:rgba(255,196,64,.42);box-shadow:0 0 10px rgba(255,196,64,.25);}',
      '.vksp-onl .dot{width:5px;height:5px;border-radius:50%;background:currentColor;box-shadow:0 0 6px currentColor;animation:vksp-onlpulse 1.4s ease-in-out infinite;}',
      '.vksp-st{flex-shrink:0;width:20px;height:20px;display:flex;align-items:center;justify-content:center;}',
      '.vksp-spin{width:14px;height:14px;border-radius:50%;border:2px solid rgba(255,196,64,.2);border-top-color:#ffc440;animation:vksp-spin .6s linear infinite;}',
      '.vksp-check{width:19px;height:19px;border-radius:50%;background:rgba(34,197,94,.18);',
        'border:1px solid rgba(34,197,94,.55);color:#4ade80;font-size:11px;font-weight:900;',
        'display:flex;align-items:center;justify-content:center;animation:vksp-pop .3s ease;}',
      // progress
      '.vksp-bar{position:relative;width:100%;height:6px;border-radius:6px;margin-top:18px;overflow:hidden;background:rgba(255,255,255,.08);}',
      '.vksp-fill{height:100%;width:0;border-radius:6px;',
        'background:linear-gradient(90deg,#3884ff,#a855f7,#ffc440);transition:width .55s ease;box-shadow:0 0 12px rgba(255,196,64,.55);}',
      '.vksp-pct{margin-top:9px;font-size:.66rem;font-weight:700;letter-spacing:.08em;color:rgba(255,217,138,.75);font-family:ui-monospace,Consolas,monospace;}',
      '.vksp.vksp-ready .vksp-boot{color:#6ee7b7;}',
      '.vksp.vksp-ready .vksp-boot .pr{color:#22c55e;}',
      '.vksp.vksp-ready .vksp-fill{background:linear-gradient(90deg,#22c55e,#ffc440);box-shadow:0 0 16px rgba(255,196,64,.7);}',
      '.vksp.vksp-ready .vksp-pct{color:#6ee7b7;}',
      '.vksp-skip{position:absolute;top:14px;right:16px;z-index:2;background:rgba(255,255,255,.07);',
        'border:1px solid rgba(255,255,255,.14);color:rgba(255,255,255,.6);font-size:.68rem;font-weight:600;',
        'padding:4px 11px;border-radius:20px;cursor:pointer;transition:all .15s;font-family:inherit;letter-spacing:.02em;}',
      '.vksp-skip:hover{background:rgba(255,255,255,.15);color:#fff;}',
      // keyframes
      '@keyframes vksp-spin{to{transform:rotate(360deg);}}',
      '@keyframes vksp-rise{from{opacity:0;transform:translateY(10px);}to{opacity:1;transform:translateY(0);}}',
      '@keyframes vksp-aurora{0%{opacity:.7;}100%{opacity:1;}}',
      '@keyframes vksp-blink{0%,100%{opacity:1;}50%{opacity:0;}}',
      '@keyframes vksp-pop{0%{transform:scale(.4);opacity:0;}60%{transform:scale(1.15);}100%{transform:scale(1);opacity:1;}}',
      '@keyframes vksp-onlpulse{0%,100%{opacity:1;transform:scale(1);}50%{opacity:.45;transform:scale(1.35);}}',
      '@keyframes vksp-fall{0%{transform:translateY(0);}100%{transform:translateY(160px);}}',
      '@keyframes vksp-strike{0%,18%,100%{opacity:0;transform:translateX(-50%) scale(.9);}6%{opacity:1;transform:translateX(-50%) scale(1);}10%{opacity:.2;}13%{opacity:1;}}',
      '@keyframes vksp-flash{0%,16%,100%{opacity:0;}6%{opacity:.85;}10%{opacity:.15;}13%{opacity:.7;}}',
      '@media (max-width:520px){',
        '.vksp{justify-content:flex-start;padding:24px 12px 20px;}',
        '.vksp-card{padding:0 15px 20px;border-radius:22px;}',
        '.vksp-stage{width:calc(100% + 30px);margin:0 -15px 6px;height:128px;border-radius:22px 22px 0 0;}',
        '.vksp-title{font-size:1.16rem;margin-top:13px;}',
        '.vksp-boot{margin-bottom:14px;font-size:.72rem;}',
        '.vksp-rows{gap:6px;}.vksp-row{padding:8px 12px;gap:10px;}',
        '.vksp-tx b{font-size:.8rem;}.vksp-tx i{font-size:.67rem;}',
      '}',
      '@media (prefers-reduced-motion:reduce){.vksp *{animation:none!important;transition:none!important;}}'
    ].join('');
    document.head.appendChild(css);
  }

  var BOLT_SVG = '<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="M13 2L4.5 13.5H11L10 22L19.5 10.5H13L13 2Z"/></svg>';

  function rainHtml() {
    var s = '';
    for (var i = 0; i < 22; i++) {
      var left = Math.round((i * 4.6 + (i % 3) * 2) % 100);
      var delay = ((i * 137) % 700) / 1000;
      var dur = 0.55 + ((i % 4) * 0.09);
      s += '<span class="vksp-drop" style="left:' + left + '%;animation-delay:' + delay + 's;animation-duration:' + dur + 's"></span>';
    }
    return s;
  }

  function stageHtml() {
    return '<div class="vksp-stage rain">' +
      '<div class="vksp-cloud"></div>' +
      '<div class="vksp-rain">' + rainHtml() + '</div>' +
      '<div class="vksp-bolt">' + BOLT_SVG + '</div>' +
      '<div class="vksp-rays"></div>' +
      '<div class="vksp-sun"></div>' +
      '<div class="vksp-flash"></div>' +
      '<div class="vksp-phase">Regn</div>' +
    '</div>';
  }

  function subFor(row) {
    if (row.kind === 'gps') return 'Avl\xe4ser koordinater…';
    return row.s;
  }

  function tagHtml(tag) {
    if (!tag) return '';
    var cls = tag === 'LIVE' ? 'vksp-onl live' : 'vksp-onl';
    return '<span class="' + cls + '"><span class="dot"></span>' + tag + '</span>';
  }

  function rowsHtml() {
    return ROWS.map(function (r, i) {
      return '<div class="vksp-row" data-i="' + i + '">' +
        '<span class="vksp-ic">' + r.ic + '</span>' +
        '<span class="vksp-tx"><b>' + r.t + tagHtml(r.tag) + '</b><i class="vksp-suba">' + subFor(r) + '</i></span>' +
        '<span class="vksp-st"><span class="vksp-spin"></span></span>' +
      '</div>';
    }).join('');
  }

  function template() {
    return '' +
      '<button class="vksp-skip" type="button" aria-label="Hoppa \xf6ver">Hoppa \xf6ver ✕</button>' +
      '<div class="vksp-inner">' +
        '<div class="vksp-card">' +
          stageHtml() +
          '<h3 class="vksp-title">V\xe4der &amp; Kl\xe4der</h3>' +
          '<span class="vksp-chip">' + BOLT_SVG + ' Powered by Groq AI</span>' +
          '<p class="vksp-boot"><span class="pr">▸</span><span class="vksp-boot-tx"></span><span class="vksp-cur"></span></p>' +
          '<div class="vksp-rows">' + rowsHtml() + '</div>' +
          '<div class="vksp-bar"><div class="vksp-fill"></div></div>' +
          '<div class="vksp-pct">0%</div>' +
        '</div>' +
      '</div>';
  }

  function suba(i) { return document.querySelector('.vksp-row[data-i="' + i + '"] .vksp-suba'); }

  function animate(el, dur, render) {
    var start = performance.now();
    (function step(now) {
      var p = Math.min(1, (now - start) / dur);
      el.innerHTML = render(1 - Math.pow(1 - p, 3));
      if (p < 1) requestAnimationFrame(step);
    })(performance.now());
  }

  function animateGps() {
    if (animated.gps) return;
    var el = suba(GPS_ROW);
    if (!el) return;
    animated.gps = true;
    animate(el, 1200, function (e) { return gpsText(e); });
    // Fäst slutvärdet efter animationen (skydd mot starvad/glitchig rAF på svaga enheter).
    setTimeout(function () { var g = suba(GPS_ROW); if (g) g.innerHTML = gpsText(1); }, 1320);
  }

  function startBoot(el) {
    var pi = 0, ci = 0, mode = 'type', stopped = false;
    function set(txt) { el.innerHTML = txt; }
    function tick() {
      if (stopped) return;
      var phrase = BOOT_PHRASES[pi];
      if (mode === 'type') {
        ci++; set(phrase.slice(0, ci));
        if (ci >= phrase.length) { mode = 'hold'; ci = 0; setTimeout(tick, 900); return; }
        setTimeout(tick, 40);
      } else if (mode === 'hold') {
        mode = 'erase'; ci = phrase.length; setTimeout(tick, 30);
      } else {
        ci -= 2; if (ci < 0) ci = 0; set(phrase.slice(0, ci));
        if (ci <= 0) { pi = (pi + 1) % BOOT_PHRASES.length; mode = 'type'; }
        setTimeout(tick, 22);
      }
    }
    tick();
    return { stop: function (finalTxt) { stopped = true; set(finalTxt); } };
  }

  function markSeen() { try { localStorage.setItem(SEEN_KEY, '1'); } catch (e) {} }
  function setPct(el, p) { if (el) el.textContent = Math.round(p) + '%'; }

  function run() {
    if (document.querySelector('.vksp')) return;
    injectStyles();

    var overlay = document.createElement('div');
    overlay.className = 'vksp';
    overlay.innerHTML = template();
    document.body.appendChild(overlay);
    var prevOverflow = document.documentElement.style.overflow;
    document.documentElement.style.overflow = 'hidden';

    var stage   = overlay.querySelector('.vksp-stage');
    var phaseEl = overlay.querySelector('.vksp-phase');
    var fill    = overlay.querySelector('.vksp-fill');
    var pctEl   = overlay.querySelector('.vksp-pct');
    var bootTx  = overlay.querySelector('.vksp-boot-tx');
    var cursor  = overlay.querySelector('.vksp-cur');
    var rows    = overlay.querySelectorAll('.vksp-row');
    var timers  = [];
    var finished = false;
    var reduce  = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    var boot    = reduce ? null : startBoot(bootTx);

    function setPhase(name, label) {
      if (!stage) return;
      stage.classList.remove('rain', 'storm', 'sun');
      stage.classList.add(name);
      if (phaseEl) phaseEl.textContent = label;
    }

    function finish() {
      if (finished) return;
      finished = true;
      timers.forEach(clearTimeout);
      setPhase('sun', 'Sol');
      overlay.classList.add('vksp-ready');
      if (boot) boot.stop('kl\xe4dr\xe5d redo ✓');
      else if (bootTx) bootTx.textContent = 'kl\xe4dr\xe5d redo ✓';
      if (cursor) cursor.style.display = 'none';
      if (fill) fill.style.width = '100%';
      setPct(pctEl, 100);
      document.documentElement.style.overflow = prevOverflow;
      timers.push(setTimeout(function () {
        overlay.classList.add('vksp-out');
        setTimeout(function () { if (overlay.parentNode) overlay.parentNode.removeChild(overlay); }, 540);
      }, 850));
      markSeen();
    }

    overlay.querySelector('.vksp-skip').addEventListener('click', finish);

    if (reduce) {
      setPhase('sun', 'Sol');
      if (bootTx) bootTx.textContent = 'kl\xe4dr\xe5d redo';
      if (cursor) cursor.style.display = 'none';
      rows.forEach(function (row) {
        row.classList.add('show', 'done');
        row.querySelector('.vksp-st').innerHTML = '<span class="vksp-check">✓</span>';
      });
      animated.gps = true;
      var gEl = suba(GPS_ROW); if (gEl) gEl.innerHTML = gpsText(1);
      if (fill) fill.style.width = '100%';
      setPct(pctEl, 100);
      timers.push(setTimeout(finish, 2200));
      return;
    }

    // Väderfas-sekvens: regn → åska → sol, synkad med raderna
    timers.push(setTimeout(function () { setPhase('storm', '\xc5ska'); }, 1900));
    timers.push(setTimeout(function () { setPhase('sun', 'Sol'); }, 3500));

    var START = 420, STAGGER = 470, FLIP = 340;
    rows.forEach(function (row, i) {
      var appear = START + i * STAGGER;
      timers.push(setTimeout(function () {
        row.classList.add('show');
        if (i === GPS_ROW) animateGps();
      }, appear));
      timers.push(setTimeout(function () {
        row.classList.add('done');
        row.querySelector('.vksp-st').innerHTML = '<span class="vksp-check">✓</span>';
        if (i === GPS_ROW) { animated.gps = true; var g = suba(GPS_ROW); if (g) g.innerHTML = gpsText(1); } // fäst slutkoordinaten
        var pct = (i + 1) / rows.length * 100;
        if (fill) fill.style.width = Math.round(pct) + '%';
        setPct(pctEl, pct);
        if (i === rows.length - 1) timers.push(setTimeout(finish, 500));
      }, appear + FLIP));
    });
  }

  window.vkReplaySplash = function () {
    var o = document.querySelector('.vksp');
    if (o && o.parentNode) o.parentNode.removeChild(o);
    animated = {};
    run();
  };

  function shouldShow() {
    if (FORCE) return true;
    try { return !localStorage.getItem(SEEN_KEY); } catch (e) { return true; }
  }

  function boot() { if (shouldShow()) run(); }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
