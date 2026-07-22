<?php
/**
 * Plugin Name: Väder & Kläder
 * Description: Visar väder och AI-klädförslag baserat på användarens position och färdmedel.
 * Version: 2.6
 * Author: elitrobban.se
 */

define('VADER_KLADER_API_URL', 'https://vaderklader-1.onrender.com/api/weather-outfit');

function vader_klader_shortcode() {
    $uid = 'vk_' . substr(md5(uniqid('', true)), 0, 8);
    $api_url = VADER_KLADER_API_URL;

    // JavaScript goes via wp_footer to avoid the_content filters converting && to &#038;&#038;
    add_action('wp_footer', function() use ($uid, $api_url) { ?>
    <script>
    (function() {
        var lat = null, lon = null;
        var uid = '<?php echo $uid; ?>';
        var labelMap = { 'buss': 'Buss &#128652;', 'tåg': 'Tåg &#128646;', 'spårvagn': 'Spårvagn &#128651;', 'tunnelbana': 'Tunnelbana &#128647;', 'cykel': 'Cykel &#128690;', 'bil': 'Bil &#128663;', 'gång': 'Gång &#128694;', 'flyg': 'Flyg &#9992;&#65039;' };
        var CACHE_KEY = 'vk_last_result';
        var CACHE_TTL = 30 * 60 * 1000;
        var GPS_KEY = 'vk_gps_position';
        var GPS_TTL = 6 * 60 * 60 * 1000;
        var countdownInterval = null;
        var currentTransport = null;

        function el(suffix) { return document.getElementById(uid + '-' + suffix); }

        function show(suffix) {
            ['step-start','loading-gps','step-transport','loading-outfit','result','no-gps','error']
                .forEach(function(p) { el(p).style.display = 'none'; });
            el(suffix).style.display = 'block';
        }

        function showError(msg) {
            clearInterval(countdownInterval);
            el('error').textContent = msg;
            show('error');
            el('step-start').style.display = 'block';
        }

        function showCountdown(seconds) {
            clearInterval(countdownInterval);
            var end = Date.now() + seconds * 1000;
            show('error');
            var tick = function() {
                var left = Math.ceil((end - Date.now()) / 1000);
                if (left <= 0) {
                    clearInterval(countdownInterval);
                    el('error').style.display = 'none';
                    show('step-start');
                    return;
                }
                var mins = Math.floor(left / 60);
                var secs = left % 60;
                el('error').textContent = 'Många förfrågningar. Försök igen om ' +
                    (mins > 0 ? mins + ' min ' : '') + secs + ' sek.';
            };
            tick();
            countdownInterval = setInterval(tick, 1000);
        }

        function updateRateIndicator(remaining, limit) {
            limit = isNaN(limit) ? 20 : limit;
            try { localStorage.setItem('vk_rate_remaining', remaining); localStorage.setItem('vk_rate_limit', limit); } catch(e) {}
            var elem = el('rate-info');
            if (!elem) return;
            elem.style.display = 'inline';
            var color = remaining <= 3 ? '#ff7043' : remaining <= 6 ? '#FFC107' : '#888';
            elem.style.color = color;
            elem.textContent = remaining + '/' + limit + ' anrop kvar' + (remaining <= 3 ? ' (!)' : '');
        }

        function displayResult(data, transport) {
            el('transport-label').innerHTML = labelMap[transport] || transport;
            el('temp').textContent     = data.temperature.toFixed(1);
            el('feels').textContent    = data.feelsLike != null ? data.feelsLike.toFixed(1) : '-';
            el('wind').textContent     = data.windSpeed.toFixed(1);
            el('winddir').textContent  = data.windDirection || '';
            el('humidity').textContent = Math.round(data.humidity);

            var precipText = data.precipitationDescription || '';
            if (data.precipitation != null) {
                if (data.precipitation > 0) {
                    precipText += ' (' + data.precipitation.toFixed(1) + ' mm)';
                }
            }
            el('precip').textContent = precipText;

            el('feelslike-warn').style.display = 'none';
            if (data.feelsLike != null) {
                var delta = data.temperature - data.feelsLike;
                if (delta > 4) {
                    el('feelslike-warn').textContent = 'Känns ' + delta.toFixed(0) + '°C kallare pga vind — klä dig varmare än termometern!';
                    el('feelslike-warn').style.display = 'block';
                } else if (delta < -4) {
                    el('feelslike-warn').textContent = 'Känns ' + Math.abs(delta).toFixed(0) + '°C varmare än termometern.';
                    el('feelslike-warn').style.display = 'block';
                }
            }

            el('uv-row').style.display = 'none';
            if (data.uvIndex != null) {
                if (data.uvIndex >= 3) {
                    el('uv').textContent = data.uvIndex.toFixed(0);
                    el('uv-row').style.display = 'block';
                }
            }
            el('outfit').textContent = data.outfitSuggestion;
            el('forecast-box').style.display = 'none';
            if (data.forecastWarning) {
                el('forecast-text').textContent = data.forecastWarning;
                el('forecast-box').style.display = 'block';
            }
            var strip = el('hourly-strip');
            if (data.hourlyForecast && data.hourlyForecast.length > 0) {
                var html = '';
                data.hourlyForecast.forEach(function(h) {
                    var probHtml = (h.precipitationProbability > 0)
                        ? '<span style="font-size:10px;color:#64b5f6;">' + h.precipitationProbability + '%</span>'
                        : '<span style="font-size:10px;color:transparent;">0%</span>';
                    var windHtml = (h.windSpeed >= 2)
                        ? '<span style="font-size:10px;color:#aaa;">&#128168; ' + h.windSpeed.toFixed(1) + '</span>'
                        : '<span style="font-size:10px;color:transparent;">-</span>';
                    html += '<div style="display:flex;flex-direction:column;align-items:center;gap:1px;flex:1;">' +
                            '<span style="font-size:11px;color:#90caf9;">Om ' + h.hoursFromNow + 'h</span>' +
                            '<span style="font-size:22px;line-height:1;">' + h.icon + '</span>' +
                            '<span style="font-size:12px;color:#e3f2fd;">' + Math.round(h.temperature) + '&deg;</span>' +
                            probHtml +
                            windHtml +
                            '</div>';
                });
                strip.innerHTML = html;
                strip.style.display = 'flex';
                el('hourly-label').style.display = 'block';
            } else {
                strip.style.display = 'none';
                el('hourly-label').style.display = 'none';
            }

            el('sunrise-row').style.display = 'none';
            if (data.sunrise && data.sunset) {
                el('sunrise-val').textContent = data.sunrise;
                el('sunset-val').textContent  = data.sunset;
                el('sunrise-row').style.display = 'block';
            }

            el('daily-strip').style.display = 'none';
            if (data.dailyForecast && data.dailyForecast.length > 0) {
                var dhtml = '';
                data.dailyForecast.slice(0, 5).forEach(function(d) {
                    dhtml += '<div style="display:flex;flex-direction:column;align-items:center;gap:2px;flex:1;min-width:0;">' +
                             '<span style="font-size:11px;color:#90caf9;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:100%;">' + d.dayName + '</span>' +
                             '<span style="font-size:20px;line-height:1;">' + d.icon + '</span>' +
                             '<span style="font-size:12px;color:#e3f2fd;">' + Math.round(d.tempMax) + '&deg;</span>' +
                             '<span style="font-size:11px;color:#90caf9;">' + Math.round(d.tempMin) + '&deg;</span>' +
                             (d.outfit ? '<span style="font-size:9px;color:#b0bec5;text-align:center;line-height:1.3;margin-top:2px;word-break:break-word;">' + d.outfit + '</span>' : '') +
                             '</div>';
                });
                el('daily-strip').innerHTML = dhtml;
                el('daily-strip').style.display = 'flex';
                el('daily-label').style.display = 'block';
            }

            show('result');
        }

        function saveCache(transport, data) {
            try {
                localStorage.setItem(CACHE_KEY, JSON.stringify({ transport: transport, data: data, timestamp: Date.now() }));
            } catch(e) {}
        }

        function saveGpsPosition(la, lo) {
            try { localStorage.setItem(GPS_KEY, JSON.stringify({ lat: la, lon: lo, timestamp: Date.now() })); } catch(e) {}
        }

        function loadGpsPosition() {
            try {
                var saved = JSON.parse(localStorage.getItem(GPS_KEY));
                if (saved && Date.now() - saved.timestamp < GPS_TTL) return saved;
            } catch(e) {}
            return null;
        }

        function loadCache() {
            try {
                var cached = JSON.parse(localStorage.getItem(CACHE_KEY));
                if (cached) {
                    if (Date.now() - cached.timestamp < CACHE_TTL) return cached;
                }
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
                    saveGpsPosition(lat, lon);
                    show('step-transport');
                },
                function() { show('no-gps'); }
            );
        };

        window[uid + '_refresh'] = function() {
            if (!currentTransport) return;
            try { localStorage.removeItem(CACHE_KEY); } catch(e) {}
            window[uid + '_select'](currentTransport);
        };

        // Transportscen per färdmedel — samma entiteter som labelMap
        var vkScenes = {
            'buss':{i:'&#128652;',g:'road',c:'#42a5f5'},
            'tåg':{i:'&#128646;',g:'rail',c:'#ffa726'},
            'spårvagn':{i:'&#128651;',g:'rail',c:'#ffa726'},
            'tunnelbana':{i:'&#128647;',g:'rail',c:'#ef5350'},
            'cykel':{i:'&#128690;',g:'road',c:'#66bb6a'},
            'bil':{i:'&#128663;',g:'road',c:'#42a5f5'},
            'gång':{i:'&#128694;',g:'walk',c:'#ab47bc'},
            'flyg':{i:'&#9992;&#65039;',g:'sky',c:'#29b6f6'}
        };
        function vkBuildScene(transport) {
            var s = vkScenes[transport] || { i:'&#129517;', g:'road', c:'#42a5f5' };
            var sky = s.g === 'sky';
            var clouds = sky ? '<span class="vk-cloud" style="top:16px">&#9729;&#65039;</span><span class="vk-cloud" style="top:44px;animation-delay:1.2s">&#9729;&#65039;</span>' : '';
            var ground = sky ? '' : '<div class="vk-ground ' + s.g + '" style="color:' + s.c + '"></div>';
            return '<div class="vk-scene' + (sky ? ' sky' : '') + '">' + clouds + '<div class="vk-vehicle">' + s.i + '</div>' + ground + '</div>'
                 + '<p style="margin:0;color:' + s.c + ';font-size:14px;font-weight:600;">H&auml;mtar kl&auml;df&ouml;rslag&hellip;</p>';
        }
        window[uid + '_select'] = function(transport) {
            currentTransport = transport;
            var vkLo = el('loading-outfit');
            if (vkLo) vkLo.innerHTML = vkBuildScene(transport);
            show('loading-outfit');

            var controller = new AbortController();
            var timeout = setTimeout(function() { controller.abort(); }, 30000);

            fetch('<?php echo $api_url; ?>?lat=' + lat + '&lon=' + lon + '&transport=' + encodeURIComponent(transport), { signal: controller.signal })
                .then(function(r) {
                    clearTimeout(timeout);
                    var remaining = parseInt(r.headers.get('X-RateLimit-Remaining'));
                    var limit     = parseInt(r.headers.get('X-RateLimit-Limit'));
                    return r.json().then(function(data) {
                        if (r.status === 429) {
                            if (data.retryAfterSeconds) {
                                showCountdown(data.retryAfterSeconds);
                                return null;
                            }
                        }
                        if (!r.ok) {
                            throw new Error(data.error || ('HTTP ' + r.status));
                        }
                        if (data.error) {
                            throw new Error(data.error);
                        }
                        if (!isNaN(remaining)) updateRateIndicator(remaining, limit);
                        return data;
                    });
                })
                .then(function(data) {
                    if (!data) return;
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

        window[uid + '_share'] = function() {
            var temp     = el('temp').textContent;
            var feels    = el('feels').textContent;
            var wind     = el('wind').textContent;
            var winddir  = el('winddir').textContent;
            var outfit   = el('outfit').textContent;
            var transport = el('transport-label').textContent;
            var text = 'Väder just nu: ' + temp + '°C (upplevd ' + feels + '°C), ' +
                       'Vind ' + wind + ' m/s från ' + winddir + '\n\n' +
                       'Klädförslag för ' + transport + ':\n' + outfit + '\n\n' +
                       'elitrobban.se';
            if (navigator.share) {
                navigator.share({ text: text }).catch(function() {});
            } else {
                navigator.clipboard.writeText(text).then(function() {
                    var btn = el('share-btn');
                    var orig = btn.innerHTML;
                    btn.textContent = 'Kopierat!';
                    setTimeout(function() { btn.innerHTML = orig; }, 2000);
                }).catch(function() {});
            }
        };

        window[uid + '_reset'] = function() {
            el('forecast-box').style.display = 'none';
            el('uv-row').style.display = 'none';
            el('feelslike-warn').style.display = 'none';
            el('sunrise-row').style.display = 'none';
            el('hourly-label').style.display = 'none';
            el('hourly-strip').style.display = 'none';
            el('hourly-strip').innerHTML = '';
            el('daily-label').style.display = 'none';
            el('daily-strip').style.display = 'none';
            el('daily-strip').innerHTML = '';
            show('step-transport');
        };

        window[uid + '_citySearch'] = function() {
            var input = el('city-input');
            var query = input ? input.value.trim() : '';
            if (!query) return;
            var cityErr = el('city-error');
            cityErr.style.display = 'none';
            input.disabled = true;

            fetch('https://nominatim.openstreetmap.org/search?q=' + encodeURIComponent(query) + '&format=json&limit=1', {
                headers: { 'Accept-Language': 'sv' }
            })
            .then(function(r) { return r.json(); })
            .then(function(results) {
                input.disabled = false;
                if (!results) { results = []; }
                if (results.length === 0) {
                    cityErr.textContent = 'Hittade ingen ort. Försök igen.';
                    cityErr.style.display = 'block';
                    return;
                }
                lat = parseFloat(results[0].lat);
                lon = parseFloat(results[0].lon);
                saveGpsPosition(lat, lon);
                show('step-transport');
            })
            .catch(function() {
                input.disabled = false;
                cityErr.textContent = 'Kunde inte söka efter ort. Kontrollera uppkopplingen.';
                cityErr.style.display = 'block';
            });
        };

        document.addEventListener('DOMContentLoaded', function() {
            var cached = loadCache();
            if (cached) {
                lat = cached.data.lat;
                lon = cached.data.lon;
                displayResult(cached.data, cached.transport);
                try {
                    var r = localStorage.getItem('vk_rate_remaining');
                    var l = localStorage.getItem('vk_rate_limit');
                    if (r !== null) updateRateIndicator(parseInt(r), parseInt(l) || 20);
                } catch(e) {}
            } else {
                var savedGps = loadGpsPosition();
                if (savedGps) {
                    lat = savedGps.lat;
                    lon = savedGps.lon;
                    show('step-transport');
                } else if (new URLSearchParams(window.location.search).get('autostart') === '1') {
                    window[uid + '_start']();
                }
            }
        });
    })();
    </script>
    <?php }, 99);

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
    @keyframes vk-spin {
        0%   { transform: rotate(0deg); }
        100% { transform: rotate(360deg); }
    }
    .vk-spinner {
        width: 30px;
        height: 30px;
        border: 3px solid rgba(100,181,246,0.2);
        border-top-color: #64b5f6;
        border-radius: 50%;
        animation: vk-spin 0.8s linear infinite;
        margin: 0 auto 10px;
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
    /* ── Transportscen: fordonet guppar medan marken/rälsen/molnen scrollar ── */
    .vk-scene { position: relative; height: 96px; max-width: 320px; margin: 0 auto 14px; border-radius: 14px; overflow: hidden;
        background: linear-gradient(180deg, rgba(255,255,255,0.07), rgba(255,255,255,0.02)); border: 1px solid rgba(255,255,255,0.12); }
    .vk-vehicle { position: absolute; left: 50%; top: 44%; transform: translate(-50%,-50%); font-size: 42px; z-index: 2;
        filter: drop-shadow(0 4px 9px rgba(0,0,0,0.35)); animation: vk-bob 0.6s ease-in-out infinite; }
    @keyframes vk-bob { 0%,100% { transform: translate(-50%,-50%); } 50% { transform: translate(-50%,-64%); } }
    .vk-ground { position: absolute; left: 0; right: 0; bottom: 16px; height: 4px; background-repeat: repeat-x; animation: vk-scroll 0.5s linear infinite; }
    @keyframes vk-scroll { to { background-position: -32px 0; } }
    .vk-ground.road { background-image: linear-gradient(90deg, currentColor 0 16px, transparent 16px 32px); background-size: 32px 4px; }
    .vk-ground.rail { bottom: 12px; height: 8px; background-image: repeating-linear-gradient(90deg, currentColor 0 4px, transparent 4px 14px); background-size: 14px 8px; }
    .vk-ground.walk { background-image: radial-gradient(circle, currentColor 1.5px, transparent 2px); background-size: 15px 4px; }
    .vk-scene.sky { background: linear-gradient(180deg, rgba(41,182,246,0.18), rgba(2,119,189,0.06)); }
    .vk-scene.sky .vk-vehicle { animation: vk-fly 1.4s ease-in-out infinite; }
    @keyframes vk-fly { 0%,100% { transform: translate(-50%,-46%) rotate(-4deg); } 50% { transform: translate(-50%,-58%) rotate(2deg); } }
    .vk-cloud { position: absolute; font-size: 22px; opacity: 0.55; left: -30px; animation: vk-drift 2.6s linear infinite; }
    @keyframes vk-drift { from { transform: translateX(0); } to { transform: translateX(360px); } }
    @media (prefers-reduced-motion: reduce) {
        .vk-vehicle, .vk-ground, .vk-cloud { animation: none; }
    }
    </style>

    <div id="<?php echo $uid; ?>-widget" style="font-family: sans-serif; max-width: 500px; background: transparent !important;">

        <div class="vk-header">
            <div class="vk-header-icons">
                <div class="vk-header-icons-inner">
                    <span class="vk-header-icon">&#9728;&#65039;</span>
                    <span class="vk-header-icon">&#127780;&#65039;</span>
                    <span class="vk-header-icon">&#127783;&#65039;</span>
                    <span class="vk-header-icon">&#10052;&#65039;</span>
                    <span class="vk-header-icon">&#9925;</span>
                    <span class="vk-header-icon">&#9728;&#65039;</span>
                    <span class="vk-header-icon">&#127780;&#65039;</span>
                    <span class="vk-header-icon">&#127783;&#65039;</span>
                    <span class="vk-header-icon">&#10052;&#65039;</span>
                    <span class="vk-header-icon">&#9925;</span>
                </div>
            </div>
            <div class="vk-header-text">
                <span class="vk-header-title">V&auml;der &amp; Kl&auml;der</span>
                <span class="vk-header-sub">Realtidsv&auml;der &middot; Din plats &middot; <span class="vk-groq-badge">&#9889; Groq AI</span></span>
            </div>
        </div>

        <div id="<?php echo $uid; ?>-step-start">
            <button onclick="window['<?php echo $uid; ?>_start']()" class="vk-start-btn">
                H&auml;mta kl&auml;df&ouml;rslag
            </button>
        </div>

        <div id="<?php echo $uid; ?>-loading-gps" style="display:none; text-align:center; padding:24px 0;">
            <div class="vk-spinner"></div>
            <p style="margin:0; color:#90caf9; font-size:14px;">H&auml;mtar din position...</p>
        </div>

        <div id="<?php echo $uid; ?>-step-transport" style="display:none;">
            <p style="margin-bottom:12px; font-size:15px;"><strong>Hur tar du dig fram idag?</strong></p>
            <div style="display:flex; gap:12px; flex-wrap:wrap;">
                <button onclick="window['<?php echo $uid; ?>_select']('buss')"  style="background:#fff; border:2px solid #2196F3; color:#1565C0; padding:12px 20px; border-radius:8px; cursor:pointer; font-size:15px;">&#128652; Buss</button>
                <button onclick="window['<?php echo $uid; ?>_select']('tåg')"   style="background:#fff; border:2px solid #2196F3; color:#1565C0; padding:12px 20px; border-radius:8px; cursor:pointer; font-size:15px;">&#128646; T&aring;g</button>
                <button onclick="window['<?php echo $uid; ?>_select']('spårvagn')" style="background:#fff; border:2px solid #2196F3; color:#1565C0; padding:12px 20px; border-radius:8px; cursor:pointer; font-size:15px;">&#128651; Sp&aring;rvagn</button>
                <button onclick="window['<?php echo $uid; ?>_select']('tunnelbana')" style="background:#fff; border:2px solid #2196F3; color:#1565C0; padding:12px 20px; border-radius:8px; cursor:pointer; font-size:15px;">&#128647; Tunnelbana</button>
                <button onclick="window['<?php echo $uid; ?>_select']('cykel')" style="background:#fff; border:2px solid #2196F3; color:#1565C0; padding:12px 20px; border-radius:8px; cursor:pointer; font-size:15px;">&#128690; Cykel</button>
                <button onclick="window['<?php echo $uid; ?>_select']('bil')"   style="background:#fff; border:2px solid #2196F3; color:#1565C0; padding:12px 20px; border-radius:8px; cursor:pointer; font-size:15px;">&#128663; Bil</button>
                <button onclick="window['<?php echo $uid; ?>_select']('gång')"  style="background:#fff; border:2px solid #2196F3; color:#1565C0; padding:12px 20px; border-radius:8px; cursor:pointer; font-size:15px;">&#128694; G&aring;ng</button>
                <button onclick="window['<?php echo $uid; ?>_select']('flyg')"  style="background:#fff; border:2px solid #2196F3; color:#1565C0; padding:12px 20px; border-radius:8px; cursor:pointer; font-size:15px;">&#9992;&#65039; Flyg</button>
            </div>
        </div>

        <div id="<?php echo $uid; ?>-loading-outfit" style="display:none; text-align:center; padding:24px 0;">
            <div class="vk-spinner"></div>
            <p style="margin:0; color:#90caf9; font-size:14px;">H&auml;mtar kl&auml;df&ouml;rslag...</p>
        </div>

        <div id="<?php echo $uid; ?>-result" style="display:none;">
            <div style="background:#1a3a5c; border-left:4px solid #64b5f6; padding:16px; border-radius:6px; margin-bottom:12px;">
                <h3 style="margin:0 0 8px 0; color:#90caf9;">V&auml;der just nu</h3>
                <p style="margin:4px 0; color:#e3f2fd;"><strong>Temperatur:</strong> <span id="<?php echo $uid; ?>-temp"></span>&deg;C &nbsp;<span style="color:#90caf9; font-size:13px;">(upplevd: <span id="<?php echo $uid; ?>-feels"></span>&deg;C)</span></p>
                <p style="margin:4px 0; color:#e3f2fd;"><strong>Vind:</strong> <span id="<?php echo $uid; ?>-wind"></span> m/s &nbsp;<span style="color:#90caf9; font-size:13px;">fr&aring;n <span id="<?php echo $uid; ?>-winddir"></span></span></p>
                <p style="margin:4px 0; color:#e3f2fd;"><strong>Luftfuktighet:</strong> <span id="<?php echo $uid; ?>-humidity"></span>%</p>
                <p style="margin:4px 0; color:#e3f2fd;"><strong>Nederb&ouml;rd:</strong> <span id="<?php echo $uid; ?>-precip"></span></p>
                <div id="<?php echo $uid; ?>-feelslike-warn" style="display:none; margin-top:8px; padding:6px 10px; background:rgba(100,181,246,0.12); border-left:3px solid #64b5f6; border-radius:4px; color:#90caf9; font-size:13px;"></div>
                <p id="<?php echo $uid; ?>-uv-row" style="margin:4px 0; display:none; color:#e3f2fd;"><strong>UV-index:</strong> <span id="<?php echo $uid; ?>-uv"></span></p>
                <div id="<?php echo $uid; ?>-forecast-box" style="display:none; margin-top:10px; padding:8px 12px; background:rgba(255,193,7,0.15); border-left:3px solid #FFC107; border-radius:4px; color:#FFD54F; font-size:13px;">
                    &#9888; <span id="<?php echo $uid; ?>-forecast-text"></span>
                </div>
                <p id="<?php echo $uid; ?>-sunrise-row" style="margin:4px 0; display:none; color:#e3f2fd; font-size:13px;">&#127774; <span id="<?php echo $uid; ?>-sunrise-val"></span> &nbsp;&#127762; <span id="<?php echo $uid; ?>-sunset-val"></span></p>
                <p id="<?php echo $uid; ?>-hourly-label" style="display:none; margin:10px 0 4px 0; font-size:11px; color:#90caf9; text-transform:uppercase; letter-spacing:0.5px;">N&auml;sta 6 timmar</p>
                <div id="<?php echo $uid; ?>-hourly-strip" style="display:none; flex-wrap:nowrap; gap:4px; justify-content:space-between; padding:10px 8px; background:rgba(255,255,255,0.05); border-radius:6px;"></div>
            </div>
            <div style="background:#3a2a00; border-left:4px solid #FFC107; padding:16px; border-radius:6px;">
                <h3 style="margin:0 0 8px 0; color:#FFD54F; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">AI-kl&auml;df&ouml;rslag &mdash; <span id="<?php echo $uid; ?>-transport-label"></span></h3>
                <p id="<?php echo $uid; ?>-outfit" style="margin:0 0 14px 0; line-height:1.6; color:#fff8e1;"></p>
                <a href="https://groq.com" target="_blank" rel="noopener" style="display:inline-flex;align-items:center;gap:5px;padding:5px 11px;background:rgba(245,80,54,0.15);border:1px solid rgba(245,80,54,0.4);border-radius:20px;font-size:11px;font-weight:700;color:#ff7a5c;letter-spacing:0.4px;text-decoration:none;">
                    &#9889; Drivs av Groq AI
                </a>
            </div>
            <p id="<?php echo $uid; ?>-daily-label" style="display:none; margin:12px 0 4px 0; font-size:11px; color:#90caf9; text-transform:uppercase; letter-spacing:0.5px;">Veckoprognos</p>
            <div id="<?php echo $uid; ?>-daily-strip" style="display:none; flex-wrap:nowrap; gap:4px; justify-content:space-between; padding:10px 8px; background:#1a2a3a; border-radius:6px;"></div>
            <div style="display:flex; align-items:center; justify-content:space-between; margin-top:12px; flex-wrap:wrap; gap:6px;">
                <div style="display:flex; gap:8px; flex-wrap:wrap;">
                    <button onclick="window['<?php echo $uid; ?>_reset']()" style="background:none; border:1px solid #aaa; padding:8px 16px; border-radius:6px; cursor:pointer; font-size:13px; color:#555;">
                        &#8635; V&auml;lj nytt f&auml;rdmedel
                    </button>
                    <button onclick="window['<?php echo $uid; ?>_refresh']()" style="background:none; border:1px solid #64b5f6; padding:8px 16px; border-radius:6px; cursor:pointer; font-size:13px; color:#64b5f6;">
                        &#8593; Uppdatera v&auml;der
                    </button>
                    <button id="<?php echo $uid; ?>-share-btn" onclick="window['<?php echo $uid; ?>_share']()" style="background:none; border:1px solid #81c784; padding:8px 16px; border-radius:6px; cursor:pointer; font-size:13px; color:#81c784;">
                        &#128279; Dela
                    </button>
                </div>
                <span id="<?php echo $uid; ?>-rate-info" style="display:none; font-size:11px; color:#aaa;"></span>
            </div>
        </div>

        <div id="<?php echo $uid; ?>-no-gps" style="display:none;">
            <p style="font-size:14px; margin-bottom:14px; color:#ccc;">Kunde inte h&auml;mta din position via GPS.</p>
            <div style="margin-bottom:16px;">
                <p style="font-size:14px; margin-bottom:8px; color:#ccc;"><strong>S&ouml;k efter din stad:</strong></p>
                <div style="display:flex; gap:8px;">
                    <input id="<?php echo $uid; ?>-city-input" type="text" placeholder="t.ex. Stockholm"
                        style="flex:1; padding:10px 14px; border-radius:6px; border:1px solid #555; background:#1a2030; color:#fff; font-size:14px; outline:none;"
                        onkeydown="if(event.key==='Enter') window['<?php echo $uid; ?>_citySearch']()" />
                    <button onclick="window['<?php echo $uid; ?>_citySearch']()"
                        style="background:#2196F3; color:white; border:none; padding:10px 18px; border-radius:6px; cursor:pointer; font-size:14px; font-weight:600;">
                        S&ouml;k
                    </button>
                </div>
                <p id="<?php echo $uid; ?>-city-error" style="display:none; color:#ef5350; font-size:13px; margin-top:6px;"></p>
            </div>
            <button onclick="window['<?php echo $uid; ?>_start']()"
                style="background:none; border:1px solid #666; padding:8px 16px; border-radius:6px; cursor:pointer; font-size:13px; color:#aaa;">
                F&ouml;rs&ouml;k med GPS igen
            </button>
        </div>

        <div id="<?php echo $uid; ?>-error" style="display:none; background:#3a1a1a; border-left:4px solid #ef5350; padding:14px 16px; border-radius:6px; color:#ffcdd2; font-size:14px; line-height:1.5;"></div>
    </div>
    <?php
    return ob_get_clean();
}

add_shortcode('weather_outfit', 'vader_klader_shortcode');
