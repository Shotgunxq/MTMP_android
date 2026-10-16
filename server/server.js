const http = require('http');
const url = require('url');
const os = require('os');

const PORT = process.env.PORT || 3000;
const HOST = process.env.HOST || '0.0.0.0';
const GRAVITY = 9.81;

/**
 * Calculates projectile trajectory points
 * @param {number} velocity Initial velocity in m/s
 * @param {number} angleDegrees Launch angle in degrees
 * @returns {Array<{time: number, x: number, y: number}>}
 */
function calculateTrajectory(velocity, angleDegrees) {
    const angleRadians = (angleDegrees * Math.PI) / 180.0;
    const timeOfFlight = (2 * velocity * Math.sin(angleRadians)) / GRAVITY;
    const timeStep = 0.05;
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

const server = http.createServer((req, res) => {
    // Enable CORS
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') {
        res.writeHead(204);
        res.end();
        return;
    }

    const parsedUrl = url.parse(req.url, true);

    if (parsedUrl.pathname === '/trajectory' || parsedUrl.pathname === '/') {
        const handleTrajectoryRequest = (velocityVal, angleVal) => {
            const velocity = parseFloat(velocityVal);
            const angle = parseFloat(angleVal);

            console.log('\n==================================================');
            console.log(`[SERVER] ${new Date().toLocaleTimeString()} - Prijatá požiadavka od klienta`);
            console.log(`[SERVER] >>> Prijaté parametre:`);
            console.log(`         - Počiatočná rýchlosť (velocity): ${velocityVal} m/s`);
            console.log(`         - Uhol (angle):                   ${angleVal}°`);

            if (isNaN(velocity) || isNaN(angle) || velocity <= 0 || angle <= 0 || angle >= 90) {
                console.log(`[SERVER] [CHYBA] Neplatné parametre: velocity=${velocityVal}, angle=${angleVal}`);
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Invalid parameters. Velocity and angle must be positive, angle < 90.' }));
                return;
            }

            const points = calculateTrajectory(velocity, angle);
            const maxDistance = points.length > 0 ? points[points.length - 1].x : 0;
            const maxHeight = Math.max(...points.map(p => p.y));
            const flightTime = points.length > 0 ? points[points.length - 1].time : 0;

            console.log(`[SERVER] Výpočet dokončený:`);
            console.log(`         - Celkový čas letu:  ${flightTime.toFixed(2)} s`);
            console.log(`         - Maximálna výška:   ${maxHeight.toFixed(2)} m`);
            console.log(`         - Dolet:             ${maxDistance.toFixed(2)} m`);
            console.log(`         - Počet bodov:       ${points.length}`);
            console.log(`[SERVER] Odosielam ${points.length} bodov späť klientovi...`);
            console.log('==================================================');

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

server.listen(PORT, HOST, () => {
    const localIps = getLocalIpAddresses();
    console.log(`==================================================`);
    console.log(`[SERVER] Node.js server beží na porte ${PORT}`);
    console.log(`[SERVER] Dostupné adresy URL pre aplikáciu:`);
    console.log(`  -> Pre Android Emulator:  http://10.0.2.2:${PORT}`);
    console.log(`  -> Pre Desktop / Local:   http://127.0.0.1:${PORT}`);
    if (localIps.length > 0) {
        localIps.forEach(ip => {
            console.log(`  -> Pre Fyzický telefón (Wi-Fi): http://${ip}:${PORT}`);
        });
    }
    console.log(`[SERVER] Čakám na požiadavky od klienta...`);
    console.log(`==================================================`);
});
