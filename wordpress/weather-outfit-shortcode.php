<?php
/**
 * Plugin Name: Väder & Kläder
 * Description: Visar väder och AI-klädförslag baserat på användarens position och färdmedel.
 * Version: 2.0
 * Author: elitrobban.se
 */

define('VADER_KLADER_API_URL', 'https://vaderklader-1.onrender.com/api/weather-outfit');

function vader_klader_shortcode() {
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
    .vk-header-text {
        display: flex;
        flex-direction: column;
    }
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
        margin-top: 2px;
        letter-spacing: 0.5px;
    }

    @media (max-width: 420px) {
        .vk-header {
            padding: 10px 12px;
            gap: 10px;
            border-radius: 10px;
        }
        .vk-header-icons {
            width: 70px;
        }
        .vk-header-icon {
            font-size: 20px;
        }
        .vk-header-title {
            font-size: 16px;
        }
        .vk-header-sub {
            font-size: 11px;
        }
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

    <div id="vader-klader-widget" style="font-family: sans-serif; max-width: 500px; background: transparent !important;">

        <!-- Rubrik -->
        <div class="vk-header">
            <div class="vk-header-icons">
                <div class="vk-header-icons-inner">
                    <span class="vk-header-icon">☀️</span>
                    <span class="vk-header-icon">🌤️</span>
                    <span class="vk-header-icon">🌧️</span>
                    <span class="vk-header-icon">❄️</span>
                    <span class="vk-header-icon">⛅</span>
                    <!-- dubblerat för sömlös loop -->
                    <span class="vk-header-icon">☀️</span>
                    <span class="vk-header-icon">🌤️</span>
                    <span class="vk-header-icon">🌧️</span>
                    <span class="vk-header-icon">❄️</span>
                    <span class="vk-header-icon">⛅</span>
                </div>
            </div>
            <div class="vk-header-text">
                <span class="vk-header-title">Väderbaserade Klädförslag</span>
                <span class="vk-header-sub">AI · Realtidsväder · Din plats</span>
            </div>
        </div>

        <!-- Steg 1: Startknapp -->
        <div id="vk-step-start">
            <button onclick="vkRequestLocation()" class="vk-start-btn">
                ☀️ Hämta klädförslag för min plats
            </button>
        </div>

        <!-- Laddar GPS -->
        <div id="vk-loading-gps" style="display:none;">
            <p>📡 Hämtar din position...</p>
        </div>

        <!-- Steg 2: Välj färdmedel -->
        <div id="vk-step-transport" style="display:none;">
            <p style="margin-bottom:12px; font-size:15px;"><strong>Hur tar du dig fram idag?</strong></p>
            <div style="display:flex; gap:12px; flex-wrap:wrap;">
                <button onclick="vkSelectTransport('buss')" style="
                    background:#fff; border:2px solid #2196F3; color:#1565C0;
                    padding:12px 20px; border-radius:8px; cursor:pointer; font-size:15px;">
                    🚌 Buss
                </button>
                <button onclick="vkSelectTransport('tåg')" style="
                    background:#fff; border:2px solid #2196F3; color:#1565C0;
                    padding:12px 20px; border-radius:8px; cursor:pointer; font-size:15px;">
                    🚆 Tåg
                </button>
                <button onclick="vkSelectTransport('cykel')" style="
                    background:#fff; border:2px solid #2196F3; color:#1565C0;
                    padding:12px 20px; border-radius:8px; cursor:pointer; font-size:15px;">
                    🚲 Cykel
                </button>
                <button onclick="vkSelectTransport('bil')" style="
                    background:#fff; border:2px solid #2196F3; color:#1565C0;
                    padding:12px 20px; border-radius:8px; cursor:pointer; font-size:15px;">
                    🚗 Bil
                </button>
                <button onclick="vkSelectTransport('gång')" style="
                    background:#fff; border:2px solid #2196F3; color:#1565C0;
                    padding:12px 20px; border-radius:8px; cursor:pointer; font-size:15px;">
                    🚶 Gång
                </button>
            </div>
        </div>

        <!-- Laddar förslag -->
        <div id="vk-loading-outfit" style="display:none;">
            <p>🤖 Hämtar klädförslag...</p>
        </div>

        <!-- Resultat -->
        <div id="vk-result" style="display:none;">
            <div id="vk-weather-box" style="
                background:#1a3a5c; border-left:4px solid #64b5f6;
                padding:16px; border-radius:6px; margin-bottom:12px;">
                <h3 style="margin:0 0 8px 0; color:#90caf9;">🌡 Väder just nu</h3>
                <p style="margin:4px 0; color:#e3f2fd;"><strong>Temperatur:</strong> <span id="vk-temp"></span>°C</p>
                <p style="margin:4px 0; color:#e3f2fd;"><strong>Vind:</strong> <span id="vk-wind"></span> m/s</p>
                <p style="margin:4px 0; color:#e3f2fd;"><strong>Luftfuktighet:</strong> <span id="vk-humidity"></span>%</p>
                <p style="margin:4px 0; color:#e3f2fd;"><strong>Nederbörd:</strong> <span id="vk-precip"></span></p>
            </div>
            <div id="vk-outfit-box" style="
                background:#3a2a00; border-left:4px solid #FFC107;
                padding:16px; border-radius:6px;">
                <h3 style="margin:0 0 8px 0; color:#FFD54F;">👗 AI-klädförslag (<span id="vk-transport-label"></span>)</h3>
                <p id="vk-outfit" style="margin:0; line-height:1.6; color:#fff8e1;"></p>
            </div>
            <button onclick="vkReset()" style="
                margin-top:14px; background:none; border:1px solid #aaa;
                padding:8px 16px; border-radius:6px; cursor:pointer; font-size:13px; color:#555;">
                ↺ Välj nytt färdmedel
            </button>
        </div>

        <!-- GPS nekad -->
        <div id="vk-no-gps" style="display:none;">
            <p>Kunde inte få din position. Kontrollera att du har tillåtit platsåtkomst i webbläsaren.</p>
            <button onclick="vkRequestLocation()" style="
                background:#2196F3; color:white; border:none;
                padding:10px 20px; border-radius:4px; cursor:pointer;">
                Försök igen
            </button>
        </div>

        <!-- Fel -->
        <div id="vk-error" style="display:none; color:red;"></div>
    </div>

    <script>
    var vkLat = null;
    var vkLon = null;

    function vkShow(id) {
        var ids = ['vk-step-start','vk-loading-gps','vk-step-transport','vk-loading-outfit','vk-result','vk-no-gps','vk-error'];
        ids.forEach(function(i) { document.getElementById(i).style.display = 'none'; });
        document.getElementById(id).style.display = 'block';
    }

    function vkRequestLocation() {
        vkShow('vk-loading-gps');
        if (!navigator.geolocation) {
            vkShowError('Din webbläsare stödjer inte GPS.');
            return;
        }
        navigator.geolocation.getCurrentPosition(
            function(pos) {
                vkLat = pos.coords.latitude;
                vkLon = pos.coords.longitude;
                vkShow('vk-step-transport');
            },
            function() {
                vkShow('vk-no-gps');
            }
        );
    }

    function vkSelectTransport(transport) {
        vkShow('vk-loading-outfit');
        var labels = { 'buss': 'Buss 🚌', 'tåg': 'Tåg 🚆', 'cykel': 'Cykel 🚲', 'bil': 'Bil 🚗', 'gång': 'Gång 🚶' };
        document.getElementById('vk-transport-label').textContent = labels[transport] || transport;

        var url = '<?php echo VADER_KLADER_API_URL; ?>?lat=' + vkLat + '&lon=' + vkLon + '&transport=' + encodeURIComponent(transport);
        fetch(url)
            .then(function(r) { return r.json(); })
            .then(function(data) {
                if (data.error) { vkShowError(data.error); return; }
                document.getElementById('vk-temp').textContent = data.temperature.toFixed(1);
                document.getElementById('vk-wind').textContent = data.windSpeed.toFixed(1);
                document.getElementById('vk-humidity').textContent = Math.round(data.humidity);
                document.getElementById('vk-precip').textContent = data.precipitationDescription;
                document.getElementById('vk-outfit').textContent = data.outfitSuggestion;
                vkShow('vk-result');
            })
            .catch(function() { vkShowError('Kunde inte nå servern. Försök igen senare.'); });
    }

    function vkReset() {
        vkShow('vk-step-transport');
    }

    function vkShowError(msg) {
        document.getElementById('vk-error').textContent = msg;
        vkShow('vk-error');
        setTimeout(function() {
            document.getElementById('vk-step-start').style.display = 'block';
        }, 100);
    }

    document.addEventListener('DOMContentLoaded', function() {
        var params = new URLSearchParams(window.location.search);
        if (params.get('autostart') === '1') { vkRequestLocation(); }
    });
    </script>
    <?php
    return ob_get_clean();
}

add_shortcode('weather_outfit', 'vader_klader_shortcode');
