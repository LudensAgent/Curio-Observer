package dev.curio.observer.hud;

import java.util.ArrayList;
import java.util.List;

/** Protocol slot indices and native 1.20.1 GUI coordinates, independent of the renderer. */
record ContainerLayout(Background background, int width, int height, int storageRows, int titleX,
                       int inventoryY, String titleKey, List<Slot> slots) {
    enum Background { INVENTORY, CRAFTING_TABLE, FURNACE, BLAST_FURNACE, SMOKER, ENCHANTING_TABLE, ANVIL,
        BREWING_STAND, HOPPER, SMITHING, GRINDSTONE, STONECUTTER, LOOM, DISPENSER, SHULKER_BOX, CARTOGRAPHY_TABLE, BEACON, MERCHANT, GENERIC }

    /** playerSlot is the corresponding inventory-protocol index, or -1 for container-only slots. */
    record Slot(int index, int x, int y, int playerSlot) {}

    /** Bounds and model anchor in the same local coordinates as the slots. */
    record Preview(int x, int y, int width, int height, int centerX, int baseY, int size) {}

    ContainerLayout {
        slots = List.copyOf(slots);
    }

    /** The crafting table keeps its native texture intact beside a dedicated character panel. */
    int backgroundX() { return background == Background.CRAFTING_TABLE ? 64 : 0; }

    int inventoryX() { return background == Background.BEACON ? 36 : background == Background.MERCHANT ? 107 : backgroundX() + 8; }

    Preview playerPreview() {
        return switch (background) {
            case INVENTORY -> new Preview(26, 8, 50, 70, 51, 75, 30);
            case CRAFTING_TABLE -> new Preview(7, 8, 50, 150, 32, 132, 48);
            default -> null;
        };
    }

    static ContainerLayout forWindow(String kind, int slotCount) {
        List<Slot> slots = new ArrayList<>();
        switch (kind) {
            case "inventory" -> {
                slots.add(new Slot(0, 154, 28, -1));
                grid(slots, 1, 4, 2, 98, 18);
                for (int index = 0; index < 4; index++) slots.add(new Slot(5 + index, 8, 8 + index * 18, 5 + index));
                inventory(slots, 9, 8, 84);
                slots.add(new Slot(45, 77, 62, 45));
                return new ContainerLayout(Background.INVENTORY, 176, 166, 0, 97, 84, "container.crafting", slots);
            }
            case "crafting_table" -> {
                int contentX = 64;
                slots.add(new Slot(0, contentX + 124, 35, -1));
                grid(slots, 1, 9, 3, contentX + 30, 17);
                inventory(slots, 10, contentX + 8, 84);
                return new ContainerLayout(Background.CRAFTING_TABLE, contentX + 176, 166, 0,
                        contentX + 28, 84, "container.crafting", slots);
            }
            case "furnace", "blast_furnace", "smoker" -> {
                slots.add(new Slot(0, 56, 17, -1));
                slots.add(new Slot(1, 56, 53, -1));
                slots.add(new Slot(2, 116, 35, -1));
                inventory(slots, 3, 8, 84);
                Background background = kind.equals("blast_furnace") ? Background.BLAST_FURNACE
                        : kind.equals("smoker") ? Background.SMOKER : Background.FURNACE;
                return new ContainerLayout(background, 176, 166, 0, 8, 84, "container." + kind, slots);
            }
            case "enchanting_table" -> {
                slots.add(new Slot(0, 15, 47, -1));
                slots.add(new Slot(1, 35, 47, -1));
                inventory(slots, 2, 8, 84);
                return new ContainerLayout(Background.ENCHANTING_TABLE, 176, 166, 0, 8, 84, "container.enchant", slots);
            }
            case "anvil" -> {
                slots.add(new Slot(0, 27, 47, -1));
                slots.add(new Slot(1, 76, 47, -1));
                slots.add(new Slot(2, 134, 47, -1));
                inventory(slots, 3, 8, 84);
                return new ContainerLayout(Background.ANVIL, 176, 166, 0, 60, 84, "container.repair", slots);
            }
            case "brewing_stand" -> {
                slots.add(new Slot(0, 56, 51, -1));
                slots.add(new Slot(1, 79, 58, -1));
                slots.add(new Slot(2, 102, 51, -1));
                slots.add(new Slot(3, 79, 17, -1));
                slots.add(new Slot(4, 17, 17, -1));
                inventory(slots, 5, 8, 84);
                return new ContainerLayout(Background.BREWING_STAND, 176, 166, 0, 8, 84, "container.brewing", slots);
            }
            case "hopper" -> {
                grid(slots, 0, 5, 5, 44, 20);
                inventory(slots, 5, 8, 51);
                return new ContainerLayout(Background.HOPPER, 176, 133, 0, 8, 51, "container.hopper", slots);
            }
            case "smithing" -> {
                grid(slots, 0, 3, 3, 8, 48);
                slots.add(new Slot(3, 98, 48, -1));
                inventory(slots, 4, 8, 84);
                return new ContainerLayout(Background.SMITHING, 176, 166, 0, 8, 84, "container.upgrade", slots);
            }
            case "grindstone" -> {
                slots.add(new Slot(0, 49, 19, -1));
                slots.add(new Slot(1, 49, 40, -1));
                slots.add(new Slot(2, 129, 34, -1));
                inventory(slots, 3, 8, 84);
                return new ContainerLayout(Background.GRINDSTONE, 176, 166, 0, 8, 84, "container.grindstone_title", slots);
            }
            case "stonecutter" -> {
                slots.add(new Slot(0, 20, 33, -1));
                slots.add(new Slot(1, 143, 33, -1));
                inventory(slots, 2, 8, 84);
                return new ContainerLayout(Background.STONECUTTER, 176, 166, 0, 8, 84, "container.stonecutter", slots);
            }
            case "loom" -> {
                slots.add(new Slot(0, 13, 26, -1));
                slots.add(new Slot(1, 33, 26, -1));
                slots.add(new Slot(2, 23, 45, -1));
                slots.add(new Slot(3, 143, 58, -1));
                inventory(slots, 4, 8, 84);
                return new ContainerLayout(Background.LOOM, 176, 166, 0, 8, 84, "container.loom", slots);
            }
            case "generic_3x3" -> {
                grid(slots, 0, 9, 3, 62, 17);
                inventory(slots, 9, 8, 84);
                return new ContainerLayout(Background.DISPENSER, 176, 166, 0, 8, 84, "container.dispenser", slots);
            }
            case "shulker_box" -> {
                grid(slots, 0, 27, 9, 8, 18);
                inventory(slots, 27, 8, 84);
                return new ContainerLayout(Background.SHULKER_BOX, 176, 166, 0, 8, 84, "container.shulkerBox", slots);
            }
            case "cartography_table" -> {
                slots.add(new Slot(0, 15, 15, -1));
                slots.add(new Slot(1, 15, 52, -1));
                slots.add(new Slot(2, 145, 39, -1));
                inventory(slots, 3, 8, 84);
                return new ContainerLayout(Background.CARTOGRAPHY_TABLE, 176, 166, 0, 8, 84, "container.cartography_table", slots);
            }
            case "merchant" -> {
                slots.add(new Slot(0, 136, 37, -1));
                slots.add(new Slot(1, 162, 37, -1));
                slots.add(new Slot(2, 220, 37, -1));
                inventory(slots, 3, 108, 84);
                return new ContainerLayout(Background.MERCHANT, 276, 166, 0, 107, 84, "merchant.trades", slots);
            }
            case "beacon" -> {
                slots.add(new Slot(0, 136, 110, -1));
                inventory(slots, 1, 36, 137);
                return new ContainerLayout(Background.BEACON, 230, 219, 0, 8, 137, "container.beacon", slots);
            }
            default -> {
                int storage = switch (kind) {
                    case "chest", "generic_9x3" -> 27;
                    case "large_chest", "generic_9x6" -> 54;
                    default -> slotCount >= 36 ? slotCount - 36 : slotCount;
                };
                int rows = Math.max(1, (storage + 8) / 9);
                grid(slots, 0, storage, 9, 8, 18);
                inventory(slots, storage, 8, 18 * rows + 31);
                return new ContainerLayout(Background.GENERIC, 176, 114 + rows * 18, rows, 8,
                        rows * 18 + 31, "container.chest", slots);
            }
        }
    }

    private static void grid(List<Slot> slots, int first, int count, int columns, int x, int y) {
        for (int index = 0; index < count; index++) {
            slots.add(new Slot(first + index, x + index % columns * 18, y + index / columns * 18, -1));
        }
    }

    private static void inventory(List<Slot> slots, int first, int x, int y) {
        for (int index = 0; index < 27; index++) {
            slots.add(new Slot(first + index, x + index % 9 * 18, y + index / 9 * 18, 9 + index));
        }
        for (int index = 0; index < 9; index++) {
            slots.add(new Slot(first + 27 + index, x + index * 18, y + 58, 36 + index));
        }
    }
}
