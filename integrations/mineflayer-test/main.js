import { once } from 'node:events';
import { createWriteStream } from 'node:fs';
import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import readline from 'node:readline';
import { readConfig, HELP } from './config.js';
import { createScenarioRunner } from './runner.js';
import { installMineflayerObserver } from '../mineflayer/index.js';

const directory = path.dirname(fileURLToPath(import.meta.url));

/** Connect only when explicitly invoked with validated connection settings. */
export async function startAcceptance(config) {
  const [{ default: mineflayer }, pathfinderModule, { SCENARIOS }, { prepareArena }] = await Promise.all([
    import('mineflayer'), import('mineflayer-pathfinder'), import('./scenarios.js'), import('./prepare.js'),
  ]);
  const { pathfinder, Movements } = pathfinderModule.default ?? pathfinderModule;
  const unknown = config.scenarios.filter((id) => !SCENARIOS.some((scenario) => scenario.id === id));
  if (unknown.length) throw new Error(`Unknown scenario(s): ${unknown.join(', ')}. Available: ${SCENARIOS.map((value) => value.id).join(', ')}`);
  const scenarios = config.scenarios.length ? config.scenarios.map((id) => SCENARIOS.find((value) => value.id === id)) : SCENARIOS;
  let logfile;
  if (config.logFile) {
    await mkdir(path.join(directory, 'logs'), { recursive: true });
    logfile = createWriteStream(path.join(directory, 'logs', config.logFile), { flags: 'a' });
    logfile.on('error', (error) => console.error(`Log file: ${error.message}`));
  }
  const log = (event, detail = {}) => {
    const fields = typeof detail === 'string' ? { detail } : detail;
    const record = { time: new Date().toISOString(), event, ...fields };
    console.log(`[${event}] ${fields.id ? fields.id + ': ' : ''}${fields.detail ?? fields.label ?? ''}`);
    if (logfile && !logfile.writableEnded && !logfile.destroyed) logfile.write(JSON.stringify(record) + '\n');
  };
  log('connecting', { detail: `${config.username} -> ${config.host}:${config.port} (${config.auth}, Minecraft 1.20.1)` });
  const bot = mineflayer.createBot({ host: config.host, port: config.port, username: config.username,
    auth: config.auth, version: '1.20.1', profilesFolder: path.join(directory, '.auth') });
  bot.loadPlugin(pathfinder);
  let observer;
  let runner;
  let terminal;
  let closing;
  const dispose = (quit = true) => {
    if (closing) return closing;
    closing = (async () => {
      runner?.stop('Bot session closed');
      terminal?.close();
      process.removeListener('SIGINT', interrupt);
      process.removeListener('SIGTERM', interrupt);
      if (runner && !await runner.settle()) log('cleanup', { detail: 'An operation did not settle within 3 seconds; disconnecting to end it.' });
      if (quit) bot.quit('Curio Observer acceptance finished');
      await observer?.dispose();
      log('closed', { detail: 'Acceptance bot and observer feed stopped.' });
      logfile?.end();
    })();
    return closing;
  };
  const interrupt = () => { void dispose(); };
  process.once('SIGINT', interrupt);
  process.once('SIGTERM', interrupt);
  bot.on('error', (error) => log('error', { detail: error.message }));
  bot.on('kicked', (reason) => log('kicked', { detail: typeof reason === 'string' ? reason : JSON.stringify(reason) }));
  bot.once('end', (reason) => { log('disconnected', { detail: String(reason ?? 'connection ended') }); void dispose(false); });

  try {
    const waiting = new AbortController();
    const timeout = setTimeout(() => waiting.abort(new Error(`No spawn within ${config.connectTimeoutMs} ms`)), config.connectTimeoutMs);
    try {
      await Promise.race([
        once(bot, 'spawn', { signal: waiting.signal }),
        once(bot, 'end', { signal: waiting.signal }).then(([reason]) => { throw new Error(`Disconnected before spawn: ${reason}`); }),
      ]);
    } finally { clearTimeout(timeout); waiting.abort(); }
    if (closing) throw new Error('Session closed before initialization');
    // Spawn precedes nearby chunks/entities. Starting immediately can incorrectly
    // skip a villager standing next to the bot because its spawn packet is pending.
    const loading = new AbortController();
    let loadTimer;
    try {
      await Promise.race([
        bot.waitForChunksToLoad().then(() => bot.waitForTicks(5)),
        once(bot, 'end', { signal: loading.signal }).then(([reason]) => { throw new Error(`Disconnected while loading the world: ${reason}`); }),
        new Promise((_, reject) => { loadTimer = setTimeout(() => reject(new Error('World did not become ready before the connection timeout')), config.connectTimeoutMs); }),
      ]);
    } finally { clearTimeout(loadTimer); loading.abort(); }
    if (closing) throw new Error('Session closed while loading the world');
    const movements = new Movements(bot);
    movements.canDig = false;
    movements.allow1by1towers = false;
    movements.allowParkour = false;
    movements.scafoldingBlocks = [];
    bot.pathfinder.setMovements(movements);
    observer = installMineflayerObserver(bot, { actionPacing: config.actionIntervalMs > 0 ? { intervalMs: config.actionIntervalMs } : false });
    await observer.listen({ host: config.feedHost, port: config.feedPort });
    log('ready', { detail: `Feed http://${config.feedHost}:${config.feedPort}/api/observer/stream; spectate ${bot.username}` });
    log('pacing', { detail: `Inventory click interval ${config.actionIntervalMs} ms; scene dwell ${config.dwellMs} ms` });
    log('position', { detail: `${bot.game.dimension} ${bot.entity.position}` });
    const options = { dwellMs: config.dwellMs, timeoutMs: config.timeoutMs, origin: config.origin,
      prepared: false, observer, actionPacing: config.actionIntervalMs > 0, log };
    runner = createScenarioRunner(bot, scenarios, options);
    terminal = readline.createInterface({ input: process.stdin, output: process.stdout, terminal: Boolean(process.stdin.isTTY) });
    terminal.on('line', (line) => {
      const [command, id] = line.trim().split(/\s+/);
      switch (command) {
        case 'next': void runner.next(id); break;
        case 'run': void runner.run(); break;
        case 'pause': runner.pause(); break;
        case 'stop': runner.stop(); break;
        case 'status': log('status', { detail: JSON.stringify(runner.status()) }); break;
        case 'list': log('scenarios', { detail: scenarios.map((value) => `${value.id} (${value.label})`).join(', ') }); break;
        case 'quit': case 'exit': void dispose(); break;
        case '': break;
        default: log('commands', { detail: 'next [id], run, pause, stop, status, list, quit' });
      }
    });
    log('commands', { detail: 'next [id], run, pause, stop, status, list, quit' });
    if (config.prepare) {
      runner = createScenarioRunner(bot, [{ id: 'prepare', label: 'Prepare the explicitly approved test arena',
        run: (source, context) => prepareArena(source, context) }], options);
      const prepared = await runner.next();
      if (prepared?.status !== 'passed') throw new Error(`Arena preparation did not complete: ${prepared?.detail ?? 'stopped'}`);
      options.prepared = true;
      runner = createScenarioRunner(bot, scenarios, options);
    }
    if (config.run) void runner.run();
    else log('idle', { detail: 'Waiting for next/run; no scenario starts automatically.' });
    return { bot, observer, get runner() { return runner; }, dispose };
  } catch (error) {
    await dispose();
    throw error;
  }
}

async function main() {
  const config = await readConfig(process.argv.slice(2));
  if (config.help) { console.log(HELP); return; }
  await startAcceptance(config);
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  main().catch((error) => { console.error(error instanceof Error ? error.message : String(error)); process.exitCode = 1; });
}
