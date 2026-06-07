<?php
/**
 * Plugin Name: Väder & Kläder
 * Description: Visar väder och AI-klädförslag baserat på användarens position och färdmedel.
 * Version: 2.1
 * Author: elitrobban.se
 */

define('VADER_KLADER_API_URL', 'https://vaderklader-1.onrender.com/api/weather-outfit');

function vader_klader_shortcode() {
    $uid = 'vk_' . substr(md5(uniqid('', true)), 0, 8);

    ob_start(); ?>
    <style>
    .wp-block-navigation__responsive-container-open { display: none !important; }

    @keyframes vk-pulse {
        0%, 100% { box-shadow: 0 4px 18px rgba(255,193,7,0.5), 0 0 0 0 rgba(255,193,7,0.4); }
        50%       { box-shadow: 0 4px 28px rgba(255,193,7,0.8), 0 0 0 12px rgba(255,193,7,0); }
    }
    @keyframes vk-float {
        0%, 100% { transform: translateY(0px); }
        50%       { transform: translateY(-4px); }
    }
    @keyframes vk-shimmer {
        0%   { left: -100%; }
        100% { left: 160%; }
    }
    @keyframes vk-weather-scroll {
        0%   { transform: translateX(0); }
        100% { transform: translateX(-50%); }
    }
    @keyframes vk-groq-pulse {
        0%, 100% { opacity: 1; }
        50%       { opacity: 0.7; }
    }

    .vk-header {
        display: flex;
        align-items: center;
        gap: 14px;
        margin-bottom: 22px;
        padding: 14px 18px;
        background: linear-gradient(135deg, #0d2137 0%, #1a3a5c 60%, #0d2137 100%);
        border-radius: 14px;
        border: 1px solid rgba(100,181,246,0.25);
        box-shadow: 0 4px 18px rgba(0,0,0,0.35);
        overflow: hidden;
        position: relative;
    }
    .vk-header-icons {
        display: flex;
        gap: 0;
        overflow: hidden;
        width: 110px;
        flex-shrink: 0;
    }
    .vk-header-icons-inner {
        display: flex;
        gap: 6px;
        animation: vk-weather-scroll 8s linear infinite;
        white-space: nowrap;
    }
    .vk-header-icon {
        font-size: 28px;
        line-height: 1;
        filter: drop-shadow(0 0 6px rgba(255,220,80,0.6));
    }
    .vk-header-text { display: flex; flex-direction: column; }
    .vk-header-title {
        font-size: 20px;
        font-weight: 800;
        color: #ffffff;
        letter-spacing: 0.2px;
        line-height: 1.2;
        text-shadow: 0 1px 8px rgba(100,181,246,0.5);
    }
    .vk-header-sub {
        font-size: 12px;
        color: #90caf9;
        margin-top: 4px;
        letter-spacing: 0.5px;
    }
    .vk-groq-badge {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        padding: 2px 8px;
        background: rgba(245,80,54,0.15);
        border: 1px solid rgba(245,80,54,0.4);
        border-radius: 20px;
        font-size: 11px;
        font-weight: 700;
        color: #ff7a5c !important;
        letter-spacing: 0.4px;
        text-decoration: none !important;
        animation: vk-groq-pulse 3s ease-in-out infinite;
        cursor: default;
    }

    @media (max-width: 420px) {
        .vk-header { padding: 10px 12px; gap: 10px; border-radius: 10px; }
        .vk-header-icons { width: 70px; }
        .vk-header-icon { font-size: 20px; }
        .vk-header-title { font-size: 16px; }
        .vk-header-sub { font-size: 11px; }
    }

    .vk-start-btn {
        position: relative;
        overflow: hidden;
        background: linear-gradient(135deg, #FFD600, #FFA000);
        color: #1a1a1a;
        border: none;
        padding: 18px 36px;
        border-radius: 14px;
        cursor: pointer;
        font-size: 18px;
        font-weight: 700;
        letter-spacing: 0.3px;
        display: inline-flex;
        align-items: center;
        gap: 10px;
        animation: vk-pulse 2.4s ease-in-out infinite, vk-float 3.5s ease-in-out infinite;
        transition: transform 0.15s ease, box-shadow 0.15s ease;
    }
    .vk-start-btn::after {
        content: '';
        position: absolute;
        top: -20%;
        left: -100%;
        width: 50%;
        height: 140%;
        background: linear-gradient(105deg, transparent 30%, rgba(255,255,255,0.45) 50%, transparent 70%);
        animation: vk-shimmer 2.8s ease-in-out infinite;
        pointer-events: none;
    }
    .vk-start-btn:hover {
        transform: translateY(-3px) scale(1.04);
        box-shadow: 0 10px 32px rgba(255,193,7,0.8);
        animation: none;
    }
    .vk-start-btn:active {
        transform: translateY(0) scale(0.97);
        box-shadow: 0 2px 10px rgba(255,193,7,0.4);
    }
    </style>

    <div id="<?php echo $uid; ?>-widget" style="font-family: sans-serif; max-width: 500px; background: transparent !important;">

        <div class="vk-header">
            <div class="vk-header-icons">
                <div class="vk-header-icons-inner">
                    <span class="vk-header-icon">☀️</span>
                    <span class="vk-header-icon">🌤️</span>
                    <span class="vk-header-icon">🌧️</span>
                    <span class="vk-header-icon">❄️</span>
                    <span class="vk-header-icon">⛅</span>
                    <span class="vk-header-icon">☀️</span>
                    <span class="vk-header-icon">🌤️</span>
                    <span class="vk-header-icon">🌧️</span>
                    <span class="vk-header-icon">❄️</span>
                    <span class="vk-header-icon">⛅</span>
                </div>
            </div>
            <div class="vk-header-text">
                <span class="vk-header-title">Väder &amp; Kläder</span>
                <span class="vk-header-sub">Realtidsväder · Din plats · <span class="vk-groq-badge">⚡ Groq AI</span></span>
            </div>
        </div>

        <div id="<?php echo $uid; ?>-step-start">
            <button onclick="window['<?php echo $uid; ?>_start']()" class="vk-start-btn">
                ☀️ Hämta klädförslag för min plats
            </button>
        </div>

        <div id="<?php echo $uid; ?>-loading-gps" style="display:none;">
            <p>📡 Hämtar din position...</p>
        </div>

        <div id="<?php echo $uid; ?>-step-transport" style="display:none;">
            <p style="margin-bottom:12px; font-size:15px;"><strong>Hur tar du dig fram idag?</strong></p>
            <div style="display:flex; gap:12px; flex-wrap:wrap;">
                <button onclick="window['<?php echo $uid; ?>_select']('buss')"  style="background:#fff; border:2px solid #2196F3; color:#1565C0; padding:12px 20px; border-radius:8px; cursor:pointer; font-size:15px;">🚌 Buss</button>
                <button onclick="window['<?php echo $uid; ?>_select']('tåg')"   style="background:#fff; border:2px solid #2196F3; color:#1565C0; padding:12px 20px; border-radius:8px; cursor:pointer; font-size:15px;">🚆 Tåg</button>
                <button onclick="window['<?php echo $uid; ?>_select']('cykel')" style="background:#fff; border:2px solid #2196F3; color:#1565C0; padding:12px 20px; border-radius:8px; cursor:pointer; font-size:15px;">🚲 Cykel</button>
                <button onclick="window['<?php echo $uid; ?>_select']('bil')"   style="background:#fff; border:2px solid #2196F3; color:#1565C0; padding:12px 20px; border-radius:8px; cursor:pointer; font-size:15px;">🚗 Bil</button>
                <button onclick="window['<?php echo $uid; ?>_select']('gång')"  style="background:#fff; border:2px solid #2196F3; color:#1565C0; padding:12px 20px; border-radius:8px; cursor:pointer; font-size:15px;">🚶 Gång</button>
            </div>
        </div>

        <div id="<?php echo $uid; ?>-loading-outfit" style="display:none;">
            <p>🤖 Hämtar klädförslag...</p>
        </div>

        <div id="<?php echo $uid; ?>-result" style="display:none;">
            <div style="background:#1a3a5c; border-left:4px solid #64b5f6; padding:16px; border-radius:6px; margin-bottom:12px;">
                <h3 style="margin:0 0 8px 0; color:#90caf9;">🌡 Väder just nu</h3>
                <p style="margin:4px 0; color:#e3f2fd;"><strong>Temperatur:</strong> <span id="<?php echo $uid; ?>-temp"></span>°C &nbsp;<span style="color:#90caf9; font-size:13px;">(upplevd: <span id="<?php echo $uid; ?>-feels"></span>°C)</span></p>
                <p style="margin:4px 0; color:#e3f2fd;"><strong>Vind:</strong> <span id="<?php echo $uid; ?>-wind"></span> m/s &nbsp;<span style="color:#90caf9; font-size:13px;">från <span id="<?php echo $uid; ?>-winddir"></span></span></p>
                <p style="margin:4px 0; color:#e3f2fd;"><strong>Luftfuktighet:</strong> <span id="<?php echo $uid; ?>-humidity"></span>%</p>
                <p style="margin:4px 0; color:#e3f2fd;"><strong>Nederbörd:</strong> <span id="<?php echo $uid; ?>-precip"></span></p>
                <p id="<?php echo $uid; ?>-uv-row" style="margin:4px 0; display:none; color:#e3f2fd;"><strong>UV-index:</strong> <span id="<?php echo $uid; ?>-uv"></span></p>
                <div id="<?php echo $uid; ?>-forecast-box" style="display:none; margin-top:10px; padding:8px 12px; background:rgba(255,193,7,0.15); border-left:3px solid #FFC107; border-radius:4px; color:#FFD54F; font-size:13px;">
                    ⚠️ <span id="<?php echo $uid; ?>-forecast-text"></span>
                </div>
            </div>
            <div style="background:#3a2a00; border-left:4px solid #FFC107; padding:16px; border-radius:6px;">
                <h3 style="margin:0 0 8px 0; color:#FFD54F; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">👗 AI-klädförslag — <span id="<?php echo $uid; ?>-transport-label"></span></h3>
                <p id="<?php echo $uid; ?>-outfit" style="margin:0 0 14px 0; line-height:1.6; color:#fff8e1;"></p>
                <a href="https://groq.com" target="_blank" rel="noopener" style="display:inline-flex;align-items:center;gap:5px;padding:5px 11px;background:rgba(245,80,54,0.15);border:1px solid rgba(245,80,54,0.4);border-radius:20px;font-size:11px;font-weight:700;color:#ff7a5c;letter-spacing:0.4px;text-decoration:none;">
                    ⚡ Drivs av Groq AI
                </a>
            </div>
            <button onclick="window['<?php echo $uid; ?>_reset']()" style="margin-top:12px; background:none; border:1px solid #aaa; padding:8px 16px; border-radius:6px; cursor:pointer; font-size:13px; color:#555;">
                ↺ Välj nytt färdmedel
            </button>
        </div>

        <div id="<?php echo $uid; ?>-no-gps" style="display:none;">
            <p>Kunde inte få din position. Kontrollera att du har tillåtit platsåtkomst i webbläsaren.</p>
            <button onclick="window['<?php echo $uid; ?>_start']()" style="background:#2196F3; color:white; border:none; padding:10px 20px; border-radius:4px; cursor:pointer;">
                Försök igen
            </button>
        </div>

        <div id="<?php echo $uid; ?>-error" style="display:none; background:#3a1a1a; border-left:4px solid #ef5350; padding:14px 16px; border-radius:6px; color:#ffcdd2; font-size:14px; line-height:1.5;"></div>
    </div>

    <script>
    (function() {
        var lat = null, lon = null;
        var uid = '<?php echo $uid; ?>';
        var labelMap = { 'buss': 'Buss 🚌', 'tåg': 'Tåg 🚆', 'cykel': 'Cykel 🚲', 'bil': 'Bil 🚗', 'gång': 'Gång 🚶' };
        var CACHE_KEY = 'vk_last_result';
        var CACHE_TTL = 30 * 60 * 1000;

        function el(suffix) { return document.getElementById(uid + '-' + suffix); }

        function show(suffix) {
            ['step-start','loading-gps','step-transport','loading-outfit','result','no-gps','error']
                .forEach(function(p) { el(p).style.display = 'none'; });
            el(suffix).style.display = 'block';
        }

        function showError(msg) {
            el('error').textContent = msg;
            show('error');
            setTimeout(function() { el('step-start').style.display = 'block'; }, 100);
        }

        function displayResult(data, transport) {
            el('transport-label').textContent = labelMap[transport] || transport;
            el('temp').textContent     = data.temperature.toFixed(1);
            el('feels').textContent    = data.feelsLike != null ? data.feelsLike.toFixed(1) : '–';
            el('wind').textContent     = data.windSpeed.toFixed(1);
            el('winddir').textContent  = data.windDirection || '';
            el('humidity').textContent = Math.round(data.humidity);
            el('precip').textContent   = data.precipitationDescription;
            el('uv-row').style.display = 'none';
            if (data.uvIndex != null && data.uvIndex >= 3) {
                el('uv').textContent = data.uvIndex.toFixed(0);
                el('uv-row').style.display = 'block';
            }
            el('outfit').textContent = data.outfitSuggestion;
            el('forecast-box').style.display = 'none';
            if (data.forecastWarning) {
                el('forecast-text').textContent = data.forecastWarning;
                el('forecast-box').style.display = 'block';
            }
            show('result');
        }

        function saveCache(transport, data) {
            try {
                localStorage.setItem(CACHE_KEY, JSON.stringify({ transport: transport, data: data, timestamp: Date.now() }));
            } catch(e) {}
        }

        function loadCache() {
            try {
                var cached = JSON.parse(localStorage.getItem(CACHE_KEY));
                if (cached && Date.now() - cached.timestamp < CACHE_TTL) return cached;
            } catch(e) {}
            return null;
        }

        window[uid + '_start'] = function() {
            show('loading-gps');
            if (!navigator.geolocation) { showError('Din webbläsare stödjer inte GPS.'); return; }
            navigator.geolocation.getCurrentPosition(
                function(pos) {
                    lat = pos.coords.latitude;
                    lon = pos.coords.longitude;
                    show('step-transport');
                },
                function() { show('no-gps'); }
            );
        };

        window[uid + '_select'] = function(transport) {
            show('loading-outfit');

            var controller = new AbortController();
            var timeout = setTimeout(function() { controller.abort(); }, 30000);

            fetch('<?php echo VADER_KLADER_API_URL; ?>?lat=' + lat + '&lon=' + lon + '&transport=' + encodeURIComponent(transport), { signal: controller.signal })
                .then(function(r) {
                    clearTimeout(timeout);
                    return r.json().then(function(data) {
                        if (!r.ok || data.error) {
                            throw new Error(data.error || ('HTTP ' + r.status));
                        }
                        return data;
                    });
                })
                .then(function(data) {
                    saveCache(transport, data);
                    displayResult(data, transport);
                })
                .catch(function(err) {
                    clearTimeout(timeout);
                    if (err.name === 'AbortError') {
                        showError('Servern svarade inte inom 30 sekunder. Försök igen.');
                    } else {
                        showError(err.message || 'Kunde inte nå servern. Försök igen senare.');
                    }
                });
        };

        window[uid + '_reset'] = function() {
            el('forecast-box').style.display = 'none';
            el('uv-row').style.display = 'none';
            show('step-transport');
        };

        document.addEventListener('DOMContentLoaded', function() {
            var cached = loadCache();
            if (cached) {
                lat = cached.data.lat;
                lon = cached.data.lon;
                displayResult(cached.data, cached.transport);
            } else if (new URLSearchParams(window.location.search).get('autostart') === '1') {
                window[uid + '_start']();
            }
        });
    })();
    </script>
    <?php
    return ob_get_clean();
}

add_shortcode('weather_outfit', 'vader_klader_shortcode');
