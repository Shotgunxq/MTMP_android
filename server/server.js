const http = require('http');
const url = require('url');
const os = require('os');
const { exec } = require('child_process');

const PORT = process.env.PORT || 3000;
const HOST = process.env.HOST || '0.0.0.0';
const GRAVITY = 9.81; // Tiažové zrýchlenie g = 9.81 m/s^2

/**
 * Fyzikálny výpočet trajektórie šikmého vrhu bez odporu vzduchu.
 *
 * Pohybové rovnice:
 *   - Horizontálna poloha (rovnomerný priamočiary pohyb):
 *       x(t) = v0 * cos(alpha) * t
 *   - Vertikálna poloha (rovnomerne spomalený/zrýchlený pohyb v gravitačnom poli):
 *       y(t) = v0 * sin(alpha) * t - 0.5 * g * t^2
 *   - Celkový čas letu (keď y = 0 pri dopade):
 *       T_dopadu = (2 * v0 * sin(alpha)) / g
 *
 * @param {number} velocity Počiatočná rýchlosť v0 [m/s]
 * @param {number} angleDegrees Elevačný uhol v stupňoch
 * @returns {Array<{time: number, x: number, y: number}>} Pole bodov trajektórie
 */
function calculateTrajectory(velocity, angleDegrees) {
    const angleRadians = (angleDegrees * Math.PI) / 180.0;
    const timeOfFlight = (2 * velocity * Math.sin(angleRadians)) / GRAVITY;
    const timeStep = 0.05; // Časový krok delta_t = 0.05 s
    const points = [];

    let currentTime = 0.0;
    while (currentTime <= timeOfFlight) {
        const x = velocity * Math.cos(angleRadians) * currentTime;
        const y = (velocity * Math.sin(angleRadians) * currentTime) - (0.5 * GRAVITY * Math.pow(currentTime, 2));

        if (y >= 0) {
            points.push({
                time: Math.round(currentTime * 1000) / 1000,
                x: Math.round(x * 1000) / 1000,
                y: Math.round(y * 1000) / 1000
            });
        }
        currentTime += timeStep;
    }

    // Dopadový koncový bod na zemi (y = 0)
    if (timeOfFlight > 0) {
        const finalX = velocity * Math.cos(angleRadians) * timeOfFlight;
        const lastPoint = points[points.length - 1];
        if (!lastPoint || lastPoint.y !== 0 || Math.abs(lastPoint.time - timeOfFlight) > 0.001) {
            points.push({
                time: Math.round(timeOfFlight * 1000) / 1000,
                x: Math.round(finalX * 1000) / 1000,
                y: 0.0
            });
        }
    }

    return points;
}

/**
 * Získa lokálne IP adresy počítača (pre Wi-Fi / LAN pripojenie)
 */
function getLocalIpAddresses() {
    const interfaces = os.networkInterfaces();
    const addresses = [];
    for (const name of Object.keys(interfaces)) {
        for (const iface of interfaces[name]) {
            if (iface.family === 'IPv4' && !iface.internal) {
                addresses.push(iface.address);
            }
        }
    }
    return addresses;
}

/**
 * Automaticky prepojí port servera s pripojeným Android emulátorom cez ADB
 */
function setupAdbReverse(port) {
    const adbCmd = `adb reverse tcp:${port} tcp:${port} 2>/dev/null || "${process.env.HOME}/Library/Android/sdk/platform-tools/adb" reverse tcp:${port} tcp:${port} 2>/dev/null`;
    exec(adbCmd, (err, stdout) => {
        if (!err && stdout) {
            console.log(`[SERVER] [ADB] Úspešne prepojené s Android emulátorom (adb reverse tcp:${port})`);
        }
    });
}

// Vytvorenie HTTP servera
const server = http.createServer((req, res) => {
    // Povolenie CORS hlavičiek
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') {
        res.writeHead(204);
        res.end();
        return;
    }

    const parsedUrl = url.parse(req.url, true);

    // Endpoint pre výpočet trajektórie: POST /trajectory alebo GET /trajectory
    if (parsedUrl.pathname === '/trajectory' || parsedUrl.pathname === '/') {
        const handleTrajectoryRequest = (velocityVal, angleVal) => {
            const velocity = parseFloat(velocityVal);
            const angle = parseFloat(angleVal);

            console.log('\n==================================================');
            console.log(`[SERVER] ${new Date().toLocaleTimeString()} - Prijatá požiadavka od klienta`);
            console.log(`[SERVER] >>> Prijaté parametre od klienta:`);
            console.log(`         - Počiatočná rýchlosť (velocity): ${velocityVal} m/s`);
            console.log(`         - Elevačný uhol (angle):          ${angleVal}°`);

            // Validácia prijatých parametrov
            if (isNaN(velocity) || isNaN(angle) || velocity <= 0 || angle <= 0 || angle >= 90) {
                console.log(`[SERVER] [CHYBA] Neplatné parametre: velocity=${velocityVal}, angle=${angleVal}`);
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Invalid parameters. Velocity and angle must be positive, angle < 90.' }));
                return;
            }

            // Fyzikálny výpočet
            const points = calculateTrajectory(velocity, angle);
            const maxDistance = points.length > 0 ? points[points.length - 1].x : 0;
            const maxHeight = Math.max(...points.map(p => p.y));
            const flightTime = points.length > 0 ? points[points.length - 1].time : 0;

            console.log(`[SERVER] Výpočet trajektórie dokončený:`);
            console.log(`         - Celkový čas letu:  ${flightTime.toFixed(2)} s`);
            console.log(`         - Maximálna výška:   ${maxHeight.toFixed(2)} m`);
            console.log(`         - Celkový dolet:     ${maxDistance.toFixed(2)} m`);
            console.log(`         - Počet bodov:       ${points.length}`);
            console.log(`[SERVER] Odosielam ${points.length} bodov späť klientovi...`);
            console.log('==================================================');

            // Odoslanie JSON odpovede klientovi
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify(points));
        };

        if (req.method === 'POST') {
            let body = '';
            req.on('data', chunk => {
                body += chunk.toString();
            });
            req.on('end', () => {
                try {
                    const data = JSON.parse(body || '{}');
                    handleTrajectoryRequest(data.velocity, data.angle);
                } catch {
                    res.writeHead(400, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ error: 'Malformed JSON payload' }));
                }
            });
        } else if (req.method === 'GET') {
            handleTrajectoryRequest(parsedUrl.query.velocity, parsedUrl.query.angle);
        } else {
            res.writeHead(405, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Method not allowed' }));
        }
    } else {
        res.writeHead(404, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Endpoint not found' }));
    }
});

// Spustenie počúvania servera
server.listen(PORT, HOST, () => {
    setupAdbReverse(PORT);
    const localIps = getLocalIpAddresses();
    console.log(`==================================================`);
    console.log(`[SERVER] Node.js server beží na porte ${PORT}`);
    console.log(`[SERVER] V aplikácii použite: http://127.0.0.1:${PORT}`);
    if (localIps.length > 0) {
        localIps.forEach(ip => {
            console.log(`  -> Alternatíva (Wi-Fi): http://${ip}:${PORT}`);
        });
    }
    console.log(`[SERVER] Čakám na požiadavky od klienta...`);
    console.log(`==================================================`);
});
