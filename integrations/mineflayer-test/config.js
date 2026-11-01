import { readFile } from 'node:fs/promises';
import { parseArgs } from 'node:util';

export const HELP = `Curio Observer acceptance bot (Minecraft 1.20.1, Node 22+)

Usage: node main.js --config config.local.json [--run] [--prepare]
       node main.js --host 127.0.0.1 --username Alex [options]

Connection: --host HOST --port 25565 --username NAME --auth offline|microsoft
Observer:   --feed-host 127.0.0.1 --feed-port 3000
Sequence:   --scenarios chest,grindstone,villager --dwell-ms 2000 --timeout-ms 90000
Pacing:     --action-interval-ms 220 (0 disables real action pacing)
Modes:      --run starts the selected sequence; otherwise wait for terminal commands
Arena:      --prepare --origin 1000,200,1000 explicitly prepares the approved area
Other:      --connect-timeout-ms 30000 --log acceptance.jsonl --help

Terminal: next [scenario], run, pause, stop, status, list, quit
Preparation requires permission to place blocks and an explicitly supplied origin.
Without --prepare the bot only uses existing nearby blocks/items/entities.
`;

/** Read and validate untrusted CLI, config and environment values once. */
export async function readConfig(args, env = process.env) {
  const { values } = parseArgs({ args, strict: true, allowPositionals: false, options: {
    config: { type: 'string' }, host: { type: 'string' }, port: { type: 'string' }, username: { type: 'string' },
    auth: { type: 'string' }, 'feed-host': { type: 'string' }, 'feed-port': { type: 'string' },
    origin: { type: 'string' }, 'dwell-ms': { type: 'string' }, 'timeout-ms': { type: 'string' },
    'action-interval-ms': { type: 'string' },
    'connect-timeout-ms': { type: 'string' }, scenarios: { type: 'string' }, log: { type: 'string' },
    run: { type: 'boolean' }, prepare: { type: 'boolean' }, help: { type: 'boolean', short: 'h' },
  } });
  if (values.help) return { help: true };
  const file = values.config ? JSON.parse(await readFile(values.config, 'utf8')) : {};
  const keys = ['host', 'port', 'username', 'auth', 'feedHost', 'feedPort', 'origin', 'dwellMs',
    'timeoutMs', 'connectTimeoutMs', 'scenarios', 'logFile', 'actionIntervalMs'];
  if (!file || typeof file !== 'object' || Array.isArray(file)) throw new Error('Config must be a JSON object');
  const unknown = Object.keys(file).filter((key) => !keys.includes(key));
  if (unknown.length) throw new Error(`Unknown config field(s): ${unknown.join(', ')}; --run and --prepare are explicit CLI flags`);
  const pick = (flag, variable, key, fallback) => values[flag] ?? env[variable] ?? file[key] ?? fallback;
  const number = (value, name, min, max) => {
    if (typeof value !== 'number' && typeof value !== 'string') throw new Error(`${name} must be a number`);
    const result = Number(value);
    if (!Number.isSafeInteger(result) || result < min || result > max) throw new Error(`${name} must be an integer from ${min} to ${max}`);
    return result;
  };
  const string = (value, name) => {
    if (typeof value !== 'string' || !value.trim() || /[\r\n\0]/.test(value)) throw new Error(`${name} must be a non-empty string`);
    return value.trim();
  };
  const host = string(pick('host', 'MC_HOST', 'host'), 'host (use --host or --config)');
  const username = string(pick('username', 'MC_USERNAME', 'username', 'CurioTest'), 'username');
  const auth = pick('auth', 'MC_AUTH', 'auth', 'offline');
  if (!['offline', 'microsoft'].includes(auth)) throw new Error('auth must be offline or microsoft');
  if (auth === 'offline' && !/^[A-Za-z0-9_]{1,16}$/.test(username)) throw new Error('offline username must contain 1–16 letters, digits or underscores');
  const coordinates = pick('origin', 'MC_ORIGIN', 'origin');
  let origin;
  if (coordinates !== undefined) {
    const parsed = typeof coordinates === 'string' ? coordinates.split(',') : [coordinates?.x, coordinates?.y, coordinates?.z];
    if (parsed.length !== 3 || parsed.some((value) => value === undefined || value === null || value === '')) throw new Error('origin must supply x,y,z');
    origin = { x: number(parsed[0], 'origin.x', -29_999_970, 29_999_970),
      y: number(parsed[1], 'origin.y', -60, 300), z: number(parsed[2], 'origin.z', -29_999_970, 29_999_970) };
  }
  if (values.prepare && !origin) throw new Error('--prepare requires an explicit --origin x,y,z or origin in the config');
  const selected = pick('scenarios', 'MC_SCENARIOS', 'scenarios', []);
  const scenarios = typeof selected === 'string' ? selected.split(',').map((value) => value.trim()).filter(Boolean) : selected;
  if (!Array.isArray(scenarios) || scenarios.some((value) => typeof value !== 'string' || !/^[a-z0-9_-]+$/.test(value))) {
    throw new Error('scenarios must be an array or comma-separated scenario IDs');
  }
  const logFile = pick('log', 'MC_LOG_FILE', 'logFile');
  if (logFile !== undefined && (typeof logFile !== 'string' || !/^[A-Za-z0-9][A-Za-z0-9._-]*\.jsonl$/.test(logFile))) {
    throw new Error('log must be a .jsonl filename inside this runner\'s ignored logs directory');
  }
  return {
    host, username, auth, version: '1.20.1',
    port: number(pick('port', 'MC_PORT', 'port', 25565), 'port', 1, 65535),
    feedHost: string(pick('feed-host', 'OBSERVER_HOST', 'feedHost', '127.0.0.1'), 'feedHost'),
    feedPort: number(pick('feed-port', 'OBSERVER_PORT', 'feedPort', 3000), 'feedPort', 1, 65535),
    dwellMs: number(pick('dwell-ms', 'MC_DWELL_MS', 'dwellMs', 2000), 'dwellMs', 250, 30_000),
    actionIntervalMs: number(pick('action-interval-ms', 'MC_ACTION_INTERVAL_MS', 'actionIntervalMs', 220), 'actionIntervalMs', 0, 2000),
    timeoutMs: number(pick('timeout-ms', 'MC_TIMEOUT_MS', 'timeoutMs', 90_000), 'timeoutMs', 1000, 600_000),
    connectTimeoutMs: number(pick('connect-timeout-ms', 'MC_CONNECT_TIMEOUT_MS', 'connectTimeoutMs', 30_000), 'connectTimeoutMs', 1000, 300_000),
    origin, scenarios, logFile, run: values.run === true, prepare: values.prepare === true,
  };
}
