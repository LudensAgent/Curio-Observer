package dev.curio.observer.hud;

import net.minecraft.util.Identifier;

/** The legacy GUI atlases used by Minecraft 1.20.1. */
public final class HudTextures {
    public static final Identifier ICONS = minecraft("textures/gui/icons.png");
    public static final Identifier WIDGETS = minecraft("textures/gui/widgets.png");
    public static final Identifier INVENTORY = minecraft("textures/gui/container/inventory.png");
    public static final Identifier CRAFTING_TABLE = minecraft("textures/gui/container/crafting_table.png");
    public static final Identifier FURNACE = minecraft("textures/gui/container/furnace.png");
    public static final Identifier ENCHANTING_TABLE = minecraft("textures/gui/container/enchanting_table.png");
    public static final Identifier ANVIL = minecraft("textures/gui/container/anvil.png");
    public static final Identifier BLAST_FURNACE = minecraft("textures/gui/container/blast_furnace.png");
    public static final Identifier SMOKER = minecraft("textures/gui/container/smoker.png");
    public static final Identifier BREWING_STAND = minecraft("textures/gui/container/brewing_stand.png");
    public static final Identifier HOPPER = minecraft("textures/gui/container/hopper.png");
    public static final Identifier SMITHING = minecraft("textures/gui/container/smithing.png");
    public static final Identifier GRINDSTONE = minecraft("textures/gui/container/grindstone.png");
    public static final Identifier STONECUTTER = minecraft("textures/gui/container/stonecutter.png");
    public static final Identifier LOOM = minecraft("textures/gui/container/loom.png");
    public static final Identifier DISPENSER = minecraft("textures/gui/container/dispenser.png");
    public static final Identifier SHULKER_BOX = minecraft("textures/gui/container/shulker_box.png");
    public static final Identifier CARTOGRAPHY_TABLE = minecraft("textures/gui/container/cartography_table.png");
    public static final Identifier BEACON = minecraft("textures/gui/container/beacon.png");
    public static final Identifier MERCHANT = minecraft("textures/gui/container/villager2.png");
    public static final Identifier GENERIC_54 = minecraft("textures/gui/container/generic_54.png");

    private HudTextures() {
    }

    private static Identifier minecraft(String path) {
        return new Identifier("minecraft", path);
    }
}
