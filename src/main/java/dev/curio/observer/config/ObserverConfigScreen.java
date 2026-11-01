package dev.curio.observer.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** In-game settings screen: RCON details, feature toggles and the HUD panels. Replaces hand-editing the JSON. */
public final class ObserverConfigScreen extends Screen {
    private static final int PAD = 10;
    private static final int ROW = 24;
    private static final int LABEL_W = 120;
    private static final int FIELD_W = 150;
    private static final int CONTENT_TOP = 32;
    private static final int FOOTER_HEIGHT = 44;

    private final Screen parent;
    private final ObserverConfig config;
    private final Runnable onSave;

    private final List<ClickableWidget> widgets = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private TextFieldWidget hostField, portField, passwordField, pollField, hudScaleField, gammaField;
    private ButtonWidget doneButton, upButton, downButton;
    private int scroll;

    private boolean rconEnabled, hudEnabled, autoCamera, brighten;
    private boolean vitals, inventory, view, hands;

    public ObserverConfigScreen(Screen parent, ObserverConfig config, Runnable onSave) {
        super(Text.translatable("curio-observer.config.title"));
        this.parent = parent;
        this.config = config;
        this.onSave = onSave;
        this.rconEnabled = config.rconEnabled;
        this.hudEnabled = config.hudEnabled;
        this.autoCamera = Boolean.TRUE.equals(config.autoCamera);
        this.brighten = Boolean.TRUE.equals(config.brighten);
        this.vitals = config.visible("vitals");
        this.inventory = config.visible("inventory");
        this.view = config.visible("view");
        this.hands = config.visible("hands");
    }

    @Override
    protected void init() {
        widgets.clear();
        labels.clear();

        toggle("RCON enabled", () -> rconEnabled, v -> rconEnabled = v);
        hostField = textField("RCON host", 256, null);
        portField = textField("RCON port", 5, "[0-9]*");
        passwordField = textField("RCON password", 256, null);
        pollField = textField("RCON poll (s)", 2, "[0-9]*");

        toggle("HUD enabled", () -> hudEnabled, v -> hudEnabled = v);
        hudScaleField = textField("HUD scale", 5, "[0-9.]*");
        gammaField = textField("Gamma", 5, "[0-9.]*");

        toggle("Auto camera", () -> autoCamera, v -> autoCamera = v);
        toggle("Brighten dark areas", () -> brighten, v -> brighten = v);

        toggle("Show vitals", () -> vitals, v -> vitals = v);
        toggle("Show inventory", () -> inventory, v -> inventory = v);
        toggle("Show view status", () -> view, v -> view = v);
        toggle("Show hands", () -> hands, v -> hands = v);

        hostField.setText(config.rconHost == null ? "" : config.rconHost);
        portField.setText(Integer.toString(config.rconPort));
        passwordField.setText(config.rconPassword == null ? "" : config.rconPassword);
        pollField.setText(Integer.toString(config.rconPollSeconds));
        hudScaleField.setText(Double.toString(config.hudScale));
        gammaField.setText(Double.toString(config.gamma));

        // Content is drawn and clipped manually so it stays inside the scroll region; the bottom
        // buttons remain drawable children and are rendered by the parent on top.
        for (ClickableWidget widget : widgets) addSelectableChild(widget);

        doneButton = ButtonWidget.builder(Text.translatable("gui.done"), b -> done())
                .dimensions(0, 0, 120, 20).build();
        upButton = ButtonWidget.builder(Text.literal("▲"), b -> scrollBy(-ROW * 3)).dimensions(0, 0, 40, 20).build();
        downButton = ButtonWidget.builder(Text.literal("▼"), b -> scrollBy(ROW * 3)).dimensions(0, 0, 40, 20).build();
        addDrawableChild(doneButton);
        addDrawableChild(upButton);
        addDrawableChild(downButton);

        reposition();
    }

    private void toggle(String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        ButtonWidget button = ButtonWidget.builder(toggleLabel(label, getter.getAsBoolean()), b -> {
            setter.accept(!getter.getAsBoolean());
            b.setMessage(toggleLabel(label, getter.getAsBoolean()));
        }).dimensions(0, 0, 100, 20).build();
        widgets.add(button);
        labels.add(null);
    }

    private TextFieldWidget textField(String label, int maxLength, String predicate) {
        TextFieldWidget field = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, 0, 0, FIELD_W, 20, Text.literal(label));
        field.setMaxLength(maxLength);
        if (predicate != null) field.setTextPredicate(value -> value.matches(predicate));
        widgets.add(field);
        labels.add(label);
        return field;
    }

    private static Text toggleLabel(String label, boolean on) {
        return Text.literal(label + ": " + (on ? "On" : "Off"));
    }

    private void reposition() {
        int contentHeight = widgets.size() * ROW + CONTENT_TOP;
        int maxScroll = Math.max(0, contentHeight - (height - FOOTER_HEIGHT));
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        int y = CONTENT_TOP - scroll;
        for (int i = 0; i < widgets.size(); i++) {
            ClickableWidget widget = widgets.get(i);
            if (labels.get(i) == null) { widget.setX(PAD); widget.setWidth(width - PAD * 2); }
            else { widget.setX(PAD + LABEL_W); widget.setWidth(FIELD_W); }
            widget.setY(y);
            y += ROW;
        }
        doneButton.setX(width / 2 - 60);
        doneButton.setY(height - 24);
        upButton.setX(width - 100);
        upButton.setY(height - 24);
        downButton.setX(width - 56);
        downButton.setY(height - 24);
    }

    private void scrollBy(int amount) {
        scroll += amount;
        reposition();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Bottom controls sit above the scrollable content and must win the click.
        if (click(doneButton, mouseX, mouseY, button)
                || click(upButton, mouseX, mouseY, button)
                || click(downButton, mouseX, mouseY, button)) return true;
        int bottom = height - FOOTER_HEIGHT;
        for (ClickableWidget widget : widgets) {
            if (widget.getY() >= bottom || widget.getY() + widget.getHeight() <= CONTENT_TOP) continue;
            if (click(widget, mouseX, mouseY, button)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scrollBy((int) (-amount * ROW * 3));
        return true;
    }

    private boolean click(ClickableWidget widget, double mouseX, double mouseY, int button) {
        if (!widget.mouseClicked(mouseX, mouseY, button)) return false;
        setFocused(widget);
        if (button == 0) setDragging(true);
        return true;
    }

    private void done() {
        config.rconEnabled = rconEnabled;
        config.hudEnabled = hudEnabled;
        config.autoCamera = autoCamera;
        config.brighten = brighten;
        config.panel("vitals").visible = vitals;
        config.panel("inventory").visible = inventory;
        config.panel("view").visible = view;
        config.panel("hands").visible = hands;
        config.rconHost = hostField.getText().trim();
        config.rconPort = parseInt(portField.getText(), 25575);
        config.rconPassword = passwordField.getText();
        config.rconPollSeconds = parseInt(pollField.getText(), 1);
        config.hudScale = parseDouble(hudScaleField.getText(), 1.0);
        config.gamma = parseDouble(gammaField.getText(), 3.0);
        config.normalize();
        onSave.run();
        MinecraftClient.getInstance().setScreen(parent);
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); } catch (NumberFormatException exception) { return fallback; }
    }

    private static double parseDouble(String value, double fallback) {
        try { return Double.parseDouble(value.trim()); } catch (NumberFormatException exception) { return fallback; }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        MinecraftClient client = MinecraftClient.getInstance();
        int bottom = height - FOOTER_HEIGHT;
        // Content is clipped to the scroll region so it cannot draw over the title or the bottom buttons.
        context.enableScissor(0, CONTENT_TOP, width, bottom);
        for (int i = 0; i < widgets.size(); i++) {
            ClickableWidget widget = widgets.get(i);
            String label = labels.get(i);
            if (label != null) {
                context.drawText(client.textRenderer, label, PAD, widget.getY() + 6, 0xFFA0A0A0, true);
            }
            widget.render(context, mouseX, mouseY, delta);
        }
        context.disableScissor();
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() { MinecraftClient.getInstance().setScreen(parent); }
}
