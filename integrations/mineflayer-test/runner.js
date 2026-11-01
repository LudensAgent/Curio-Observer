import { setTimeout as delay } from 'node:timers/promises';

/** Serial, cancellable acceptance steps. A timed-out library call must settle before another step can start. */
export function createScenarioRunner(bot, scenarios, options) {
  let index = 0;
  let automatic = false;
  let active = null;
  const results = [];
  const log = options.log;
  const stopMotion = () => { bot.pathfinder?.setGoal(null); bot.clearControlStates?.(); };
  const execute = async (scenario) => {
    if (active) { log('busy', { detail: 'The previous operation is still settling; wait or quit/reconnect.' }); return; }
    const controller = new AbortController();
    const startedAt = Date.now();
    const job = { id: scenario.id, controller, operation: null };
    active = job;
    log('start', { id: scenario.id, label: scenario.label });
    const context = { signal: controller.signal, dwellMs: options.dwellMs, origin: options.origin,
      prepared: options.prepared, observer: options.observer, actionPacing: options.actionPacing, log,
      wait: (ms = options.dwellMs) => delay(ms, undefined, { signal: controller.signal }),
    };
    let timer;
    let timedOut = false;
    const cancelled = new Promise((_, reject) => {
      controller.signal.addEventListener('abort', () => reject(controller.signal.reason), { once: true });
      timer = setTimeout(() => { timedOut = true; controller.abort(new Error(`Scenario timed out after ${options.timeoutMs} ms`)); }, options.timeoutMs);
    });
    const operation = Promise.resolve().then(() => scenario.run(bot, context));
    job.operation = operation;
    const settled = () => { if (active === job) active = null; };
    operation.then(settled, settled);
    try {
      const result = await Promise.race([operation, cancelled]);
      const record = { id: scenario.id, status: result?.status ?? 'passed', detail: result?.detail ?? '', elapsedMs: Date.now() - startedAt };
      results.push(record);
      log(record.status, record);
      return record;
    } catch (error) {
      automatic = false;
      options.observer?.cancelPendingActions?.();
      stopMotion();
      const record = { id: scenario.id, status: timedOut ? 'failed' : controller.signal.aborted ? 'stopped' : 'failed',
        detail: error instanceof Error ? error.message : String(error), elapsedMs: Date.now() - startedAt };
      results.push(record);
      log(record.status, record);
      return record;
    } finally { clearTimeout(timer); }
  };
  const runner = {
    async next(id) {
      automatic = false;
      if (active) { log('busy', { detail: `Currently running ${active.id}` }); return; }
      const scenario = id ? scenarios.find((value) => value.id === id) : scenarios[index++];
      if (!scenario) { log('idle', { detail: id ? `Unknown scenario ${id}` : 'No remaining scenarios. Use next <id> to repeat a step.' }); return; }
      return execute(scenario);
    },
    async run() {
      if (active) { log('busy', { detail: `Currently running ${active.id}` }); return; }
      if (index >= scenarios.length) index = 0;
      automatic = true;
      while (automatic && index < scenarios.length) {
        await execute(scenarios[index++]);
      }
      automatic = false;
      log('idle', { detail: 'Sequence paused or finished; bot remains connected for inspection.' });
    },
    pause() { automatic = false; log('paused', { detail: 'The current scenario finishes; subsequent scenarios wait for run/next.' }); },
    stop(reason = 'Stopped by operator') {
      automatic = false;
      active?.controller.abort(new Error(reason));
      options.observer?.cancelPendingActions?.();
      stopMotion();
      if (bot.currentWindow) bot.closeWindow?.(bot.currentWindow);
    },
    /** Allow scenario finally blocks (especially temporary arena gamemode restoration) to finish before quit. */
    async settle(timeoutMs = 3000) {
      const operation = active?.operation;
      if (!operation) return true;
      let timer;
      try {
        return await Promise.race([
          operation.then(() => true, () => true),
          new Promise((resolve) => { timer = setTimeout(() => resolve(false), timeoutMs); }),
        ]);
      } finally { clearTimeout(timer); }
    },
    status() { return { active: active?.id ?? null, automatic, next: scenarios[index]?.id ?? null, results: [...results] }; },
  };
  return runner;
}
