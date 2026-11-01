import type { EventEmitter } from 'node:events';
import type { IncomingMessage, ServerResponse, Server } from 'node:http';

/** Prismarine's typed tag tree, not simplified JSON. */
export interface NbtTag { type: string; name?: string; value: unknown }

/** The fields read from Mineflayer/prismarine Item instances. */
export interface ObserverItemSource {
  name?: string;
  id?: string;
  count: number;
  durabilityUsed?: number;
  damage?: number;
  maxDurability?: number;
  maxDamage?: number;
  enchants?: readonly unknown[];
  enchanted?: boolean;
  customName?: unknown;
  nbt?: NbtTag | null;
}

/** Emits updateSlot when contents change; preserves every slot including nulls. */
export interface ObserverWindowSource extends EventEmitter {
  id: number;
  type: string | number;
  title?: unknown;
  slots: (ObserverItemSource | null)[];
  inventoryStart?: number;
  selectedItem?: ObserverItemSource | null;
  acceptClick?(click: object): unknown;
  totalFuel?: number;
  fuelSeconds?: number;
  totalProgress?: number;
  progressSeconds?: number;
  enchantments?: { level: number; expected?: { enchant: number; level: number } }[];
}

/**
 * Minimal structural bot interface. Optional raw protocol access is needed for
 * full cursor, workstation attributes and merchant offers. See the protocol docs
 * for event names; a vanilla inventory-only source cannot supply those features.
 */
export interface ObserverBotSource extends EventEmitter {
  username: string;
  version?: string;
  inventory: ObserverWindowSource;
  currentWindow?: ObserverWindowSource | null;
  quickBarSlot?: number;
  health?: number;
  absorption?: number;
  food?: number;
  foodSaturation?: number;
  oxygenLevel?: number;
  experience?: { level: number; progress: number };
  entity?: { metadata?: Record<number, unknown> | unknown[] };
  registry?: { items: Record<number, { name: string; stackSize: number; maxDurability?: number }> };
  _client?: EventEmitter & { write(name: string, params: object): unknown };
  craft?(recipe: object, count: number, table?: object | null): Promise<unknown>;
  trade?(window: ObserverWindowSource, index: number, count?: number): Promise<unknown>;
}

export interface ObserverBridge {
  handler(request: IncomingMessage, response: ServerResponse): boolean;
  listen(options?: { port?: number; host?: string }): Promise<Server>;
  openInventory(): void;
  closeInventory(): void;
  /** Cancel clicks that have not been sent yet; already sent server actions cannot be undone. */
  cancelPendingActions(): void;
  dispose(): Promise<void>;
}

export const STREAM_PATH: '/api/observer/stream';
export function installMineflayerObserver(
  bot: ObserverBotSource,
  options?: { heartbeatMs?: number; stateIntervalMs?: number; actionPacing?: boolean | { intervalMs?: number } },
): ObserverBridge;

export interface ObserverItemData {
  id: string;
  count: number;
  damage?: number;
  maxDamage?: number;
  enchanted?: boolean;
  name?: string | null;
  nbt?: string | null;
}

export interface PacingItemSource extends ObserverItemSource {
  type: number;
  metadata: number;
  stackSize: number;
  slot: number;
}

export interface PacingWindowSource extends ObserverWindowSource {
  slots: (PacingItemSource | null)[];
  inventoryStart: number;
  inventoryEnd: number;
  craftingResultSlot: number;
  selectedItem: PacingItemSource | null;
}

/** Real Mineflayer inventory plugin contract needed only when action pacing is enabled. */
export interface PacingBotSource extends ObserverBotSource {
  inventory: PacingWindowSource;
  currentWindow?: PacingWindowSource | null;
  clickWindow(slot: number, mouseButton: number, mode: number): Promise<void>;
}

export interface ActionPacingOptions {
  /** Delay before every real click; default 220, integer 0..2000 ms. */
  intervalMs?: number;
  onPointer?(event: { windowId: number; slot: number; durationMs: number; item: ObserverItemData | null }): void;
  onTransfer?(event: { windowId: number; slot: number; kind: 'take' | 'put' | 'drop'; item: ObserverItemData }): void;
}

export interface ActionPacing {
  readonly intervalMs: number;
  cancelPending(): void;
  dispose(): void;
}

/** Opt-in controller; use the observer's actionPacing option to include its pointer/transfer SSE events. */
export function installMineflayerActionPacing(bot: PacingBotSource, options?: ActionPacingOptions): ActionPacing;
