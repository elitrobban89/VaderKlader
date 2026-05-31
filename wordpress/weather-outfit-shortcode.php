<?php
/**
 * Plugin Name: Väder & Kläder
 * Description: Visar väder och AI-klädförslag baserat på användarens position och färdmedel.
 * Version: 2.0
 * Author: elitrobban.se
 */

define('VADER_KLADER_API_URL', 'https://elitrobban.se:8081/api/weather-outfit');

function vader_klader_shortcode() {
    ob_start(); ?>
    <div id="vader-klader-widget" style="font-family: sans-serif; max-width: 500px;">

        <!-- Steg 1: Startknapp -->
        <div id="vk-step-start">
            <button onclick="vkRequestLocation()" style="
                background:#2196F3; color:white; border:none;
                padding:14px 28px; border-radius:8px; cursor:pointer; font-size:16px;">
                📍 Hämta klädförslag för min plats
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
            </div>
        </div>

        <!-- Laddar förslag -->
        <div id="vk-loading-outfit" style="display:none;">
            <p>🤖 Hämtar klädförslag...</p>
        </div>

        <!-- Resultat -->
        <div id="vk-result" style="display:none;">
            <div id="vk-weather-box" style="
                background:#f0f8ff; border-left:4px solid #2196F3;
                padding:16px; border-radius:6px; margin-bottom:12px;">
                <h3 style="margin:0 0 8px 0; color:#1565C0;">🌡 Väder just nu</h3>
                <p style="margin:4px 0;"><strong>Temperatur:</strong> <span id="vk-temp"></span>°C</p>
                <p style="margin:4px 0;"><strong>Vind:</strong> <span id="vk-wind"></span> m/s</p>
                <p style="margin:4px 0;"><strong>Luftfuktighet:</strong> <span id="vk-humidity"></span>%</p>
                <p style="margin:4px 0;"><strong>Nederbörd:</strong> <span id="vk-precip"></span></p>
            </div>
            <div id="vk-outfit-box" style="
                background:#fff8e1; border-left:4px solid #FFC107;
                padding:16px; border-radius:6px;">
                <h3 style="margin:0 0 8px 0; color:#F57F17;">👗 AI-klädförslag (<span id="vk-transport-label"></span>)</h3>
                <p id="vk-outfit" style="margin:0; line-height:1.6;"></p>
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
        var labels = { 'buss': 'Buss 🚌', 'tåg': 'Tåg 🚆', 'cykel': 'Cykel 🚲', 'bil': 'Bil 🚗' };
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
        if (params.get('autostart') === '1') {
            vkRequestLocation();
        }
    });
    </script>
    <?php
    return ob_get_clean();
}

add_shortcode('weather_outfit', 'vader_klader_shortcode');
