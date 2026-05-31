<?php
/**
 * Plugin Name: Väder & Kläder
 * Description: Visar väder och AI-klädförslag baserat på användarens position.
 * Version: 1.0
 * Author: elitrobban.se
 */

// Ändra denna URL till din server om backenden körs på annan port/sökväg
define('VADER_KLADER_API_URL', 'https://elitrobban.se:8081/api/weather-outfit');

function vader_klader_shortcode() {
    ob_start(); ?>
    <div id="vader-klader-widget">
        <div id="vk-loading" style="display:none;">
            <p>🌤 Hämtar väder och klädförslag...</p>
        </div>
        <div id="vk-error" style="display:none; color:red;"></div>
        <div id="vk-result" style="display:none;">
            <div id="vk-weather-box" style="
                background: #f0f8ff;
                border-left: 4px solid #2196F3;
                padding: 16px;
                border-radius: 6px;
                margin-bottom: 12px;
                font-family: sans-serif;">
                <h3 style="margin:0 0 8px 0; color:#1565C0;">🌡 Väder just nu</h3>
                <p style="margin:4px 0;"><strong>Temperatur:</strong> <span id="vk-temp"></span>°C</p>
                <p style="margin:4px 0;"><strong>Vind:</strong> <span id="vk-wind"></span> m/s</p>
                <p style="margin:4px 0;"><strong>Luftfuktighet:</strong> <span id="vk-humidity"></span>%</p>
                <p style="margin:4px 0;"><strong>Nederbörd:</strong> <span id="vk-precip"></span></p>
            </div>
            <div id="vk-outfit-box" style="
                background: #fff8e1;
                border-left: 4px solid #FFC107;
                padding: 16px;
                border-radius: 6px;
                font-family: sans-serif;">
                <h3 style="margin:0 0 8px 0; color:#F57F17;">👗 AI-klädförslag</h3>
                <p id="vk-outfit" style="margin:0; line-height:1.6;"></p>
            </div>
        </div>
        <div id="vk-no-gps" style="display:none;">
            <p>Kunde inte få din position. Kontrollera att du har tillåtit platsåtkomst i webbläsaren.</p>
            <button onclick="vkRequestLocation()" style="
                background:#2196F3; color:white; border:none;
                padding:10px 20px; border-radius:4px; cursor:pointer;">
                Försök igen
            </button>
        </div>
        <button id="vk-btn" onclick="vkRequestLocation()" style="
            background:#2196F3; color:white; border:none;
            padding:12px 24px; border-radius:6px; cursor:pointer; font-size:16px;">
            📍 Hämta klädförslag för min plats
        </button>
    </div>

    <script>
    function vkRequestLocation() {
        document.getElementById('vk-btn').style.display = 'none';
        document.getElementById('vk-loading').style.display = 'block';
        document.getElementById('vk-error').style.display = 'none';
        document.getElementById('vk-result').style.display = 'none';
        document.getElementById('vk-no-gps').style.display = 'none';

        if (!navigator.geolocation) {
            vkShowError('Din webbläsare stödjer inte GPS.');
            return;
        }

        navigator.geolocation.getCurrentPosition(
            function(pos) {
                vkFetchOutfit(pos.coords.latitude, pos.coords.longitude);
            },
            function(err) {
                document.getElementById('vk-loading').style.display = 'none';
                document.getElementById('vk-no-gps').style.display = 'block';
            }
        );
    }

    function vkFetchOutfit(lat, lon) {
        var url = '<?php echo VADER_KLADER_API_URL; ?>?lat=' + lat + '&lon=' + lon;
        fetch(url)
            .then(function(r) { return r.json(); })
            .then(function(data) {
                if (data.error) { vkShowError(data.error); return; }
                document.getElementById('vk-temp').textContent = data.temperature.toFixed(1);
                document.getElementById('vk-wind').textContent = data.windSpeed.toFixed(1);
                document.getElementById('vk-humidity').textContent = Math.round(data.humidity);
                document.getElementById('vk-precip').textContent = data.precipitationDescription;
                document.getElementById('vk-outfit').textContent = data.outfitSuggestion;
                document.getElementById('vk-loading').style.display = 'none';
                document.getElementById('vk-result').style.display = 'block';
            })
            .catch(function(e) { vkShowError('Kunde inte nå servern. Försök igen senare.'); });
    }

    function vkShowError(msg) {
        document.getElementById('vk-loading').style.display = 'none';
        document.getElementById('vk-error').style.display = 'block';
        document.getElementById('vk-error').textContent = msg;
        document.getElementById('vk-btn').style.display = 'inline-block';
    }
    </script>
    <?php
    return ob_get_clean();
}

add_shortcode('weather_outfit', 'vader_klader_shortcode');
