import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, writeFile, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { readConfig } from '../config.js';
import { createScenarioRunner } from '../runner.js';

test('connection settings are explicit and run/prepare cannot be enabled by config', async (t) => {
  await assert.rejects(readConfig([], {}), /host/);
  const directory = await mkdtemp(path.join(os.tmpdir(), 'curio-cli-'));
  t.after(() => rm(directory, { recursive: true, force: true }));
  const file = path.join(directory, 'config.json');
  await writeFile(file, JSON.stringify({ host: 'localhost', run: true }));
  await assert.rejects(readConfig(['--config', file], {}), /explicit CLI flags/);
  const config = await readConfig(['--host', 'localhost'], {});
  assert.equal(config.run, false);
  assert.equal(config.prepare, false);
  assert.equal(config.origin, undefined);
  assert.equal(config.actionIntervalMs, 220);
  assert.equal((await readConfig(['--host', 'localhost', '--action-interval-ms', '0'], {})).actionIntervalMs, 0);
});

test('CLI overrides environment, then config, while preserving deliberately supplied arena origin', async (t) => {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'curio-cli-'));
  t.after(() => rm(directory, { recursive: true, force: true }));
  const file = path.join(directory, 'config.json');
  await writeFile(file, JSON.stringify({ host: 'config-host', port: 25565, username: 'ConfigBot', origin: { x: 1000, y: 200, z: 1000 } }));
  const config = await readConfig(['--config', file, '--host', 'cli-host', '--prepare', '--run'], { MC_HOST: 'env-host', MC_USERNAME: 'EnvBot' });
  assert.equal(config.host, 'cli-host');
  assert.equal(config.username, 'EnvBot');
  assert.equal(config.port, 25565);
  assert.deepEqual(config.origin, { x: 1000, y: 200, z: 1000 });
  assert.equal(config.prepare, true);
  assert.equal(config.run, true);
});

test('unsafe or incomplete CLI inputs fail before networking', async () => {
  await assert.rejects(readConfig(['--host', 'localhost', '--prepare'], {}), /explicit.*origin/);
  await assert.rejects(readConfig(['--host', 'localhost', '--origin', '1,2'], {}), /x,y,z/);
  await assert.rejects(readConfig(['--host', 'localhost', '--port', '0'], {}), /port/);
  await assert.rejects(readConfig(['--host', 'localhost', '--action-interval-ms=-1'], {}), /actionIntervalMs/);
  await assert.rejects(readConfig(['--host', 'localhost', '--auth', 'password'], {}), /offline or microsoft/);
  await assert.rejects(readConfig(['--host', 'localhost', '--username', 'bad name'], {}), /username/);
  await assert.rejects(readConfig(['--host', 'localhost', '--log', '../private.jsonl'], {}), /logs directory/);
  await assert.rejects(readConfig(['--host', 'localhost'], { MC_PORT: true }), /must be a number/);
  assert.deepEqual(await readConfig(['--help'], {}), { help: true });
});

test('run is serial, skipped prerequisites are reported, pause waits after the current scenario', async () => {
  let release;
  const gate = new Promise((resolve) => { release = resolve; });
  const calls = [];
  const scenarios = [
    { id: 'first', label: 'First', run: async () => { calls.push('first'); await gate; return { status: 'passed' }; } },
    { id: 'second', label: 'Second', run: async () => { calls.push('second'); return { status: 'skipped', detail: 'No nearby chest' }; } },
  ];
  const runner = createScenarioRunner({}, scenarios, { dwellMs: 1, timeoutMs: 1000, log() {} });
  const running = runner.run();
  await Promise.resolve();
  runner.pause();
  await runner.next();
  assert.deepEqual(calls, ['first']);
  release();
  await running;
  assert.equal(runner.status().next, 'second');
  await runner.run();
  assert.deepEqual(calls, ['first', 'second']);
  assert.deepEqual(runner.status().results.map((value) => value.status), ['passed', 'skipped']);
});

test('operator stop aborts dwell and closes the real window before another step', async () => {
  const calls = [];
  const bot = { currentWindow: { id: 2 }, closeWindow: (window) => calls.push(window.id), clearControlStates: () => calls.push('clear'),
    pathfinder: { setGoal: (goal) => calls.push(goal), stop: () => assert.fail('stop() would leave pathfinder unable to resume') } };
  const runner = createScenarioRunner(bot, [{ id: 'wait', label: 'Wait', run: async (_, context) => { await context.wait(5000); } }],
    { dwellMs: 1, timeoutMs: 10_000, observer: { cancelPendingActions: () => calls.push('cancelClicks') }, log() {} });
  const step = runner.next();
  await Promise.resolve();
  runner.stop();
  assert.equal((await step).status, 'stopped');
  assert.ok(calls.includes(2));
  assert.ok(calls.includes('clear'));
  assert.ok(calls.includes('cancelClicks'));
  assert.ok(calls.includes(null));
});

test('timeout is bounded and cannot start overlapping operations while a library call is unresolved', async () => {
  let release;
  const gate = new Promise((resolve) => { release = resolve; });
  let following = false;
  const runner = createScenarioRunner({}, [
    { id: 'stuck', label: 'Stuck', run: () => gate },
    { id: 'next', label: 'Next', run: async () => { following = true; } },
  ], { dwellMs: 1, timeoutMs: 15, log() {} });
  const timed = await runner.next();
  assert.equal(timed.status, 'failed');
  assert.match(timed.detail, /timed out/);
  await runner.next();
  assert.equal(following, false);
  release({ status: 'passed' });
  await Promise.resolve();
  await Promise.resolve();
  await runner.next();
  assert.equal(following, true);
});

test('shutdown drains restoration after cancellation before the connection can be closed', async () => {
  const calls = [];
  const runner = createScenarioRunner({}, [{ id: 'prepare', label: 'Prepare', run: async (_, context) => {
    try { await context.wait(10_000); }
    finally {
      await new Promise((resolve) => setTimeout(resolve, 20));
      calls.push('restore gamemode');
    }
  } }], { dwellMs: 1, timeoutMs: 20_000, log() {} });
  const prepare = runner.next();
  await Promise.resolve();
  runner.stop();
  assert.equal((await prepare).status, 'stopped');
  assert.deepEqual(calls, []);
  assert.equal(await runner.settle(), true);
  calls.push('quit');
  assert.deepEqual(calls, ['restore gamemode', 'quit']);
});

test('shutdown drain has a deadline when a library operation never resolves', async () => {
  const runner = createScenarioRunner({}, [{ id: 'stuck', label: 'Stuck', run: () => new Promise(() => {}) }],
    { dwellMs: 1, timeoutMs: 1000, log() {} });
  const operation = runner.next();
  await Promise.resolve();
  runner.stop();
  await operation;
  assert.equal(await runner.settle(10), false);
});
