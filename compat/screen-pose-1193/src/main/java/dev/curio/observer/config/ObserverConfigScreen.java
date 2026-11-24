package dev.curio.observer.config;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import static dev.curio.observer.config.ObserverConfigScreenState.*;

/** Settings screen for MC 1.19.3–1.19.4 (PoseStack render + Button.builder + setX/setY). */
public final class ObserverConfigScreen extends Screen {
    private final Screen parent;
    private final ObserverConfigScreenState state;
    private final Supplier<Component> connectionStatus;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private final List<Runnable> refreshLabels = new ArrayList<>();
    private EditBox feedUrlField, hostField, portField, passwordField, pollField, hudScaleField, gammaField;
    private EditBox targetField, observeIntervalField;
    private Button doneButton, upButton, downButton;

    public ObserverConfigScreen(Screen parent, ObserverConfig config, Runnable onSave, Supplier<Component> connectionStatus) {
        super(ObserverText.text("curio-observer.config.title"));
        this.parent = parent;
        this.state = new ObserverConfigScreenState(config, onSave);
        this.connectionStatus = connectionStatus;
    }

    @Override
    protected void init() {
        widgets.clear(); labels.clear(); refreshLabels.clear();
        Button languageButton = Button.builder(state.languageLabel(), button -> {
            state.cycleLanguage();
            refreshLabels.forEach(Runnable::run);
        }).bounds(0, 0, 100, 20).build();
        widgets.add(languageButton); labels.add(null);
        refreshLabels.add(() -> languageButton.setMessage(state.languageLabel()));

        targetField = textField("default_target", 16, "[A-Za-z0-9_]*", state.config.defaultTarget);
        toggle("auto_observe", () -> state.autoObserve, v -> state.autoObserve = v);
        observeIntervalField = textField("observe_interval", 3, "[0-9]*", Integer.toString(state.config.autoObserveSeconds));
        toggle("feed_enabled", () -> state.feedEnabled, v -> state.feedEnabled = v);
        feedUrlField = textField("feed_url", 2048, null, state.config.feedUrl);
        toggle("rcon_enabled", () -> state.rconEnabled, v -> state.rconEnabled = v);
        hostField = textField("rcon_host", 256, null, state.config.rconHost);
        portField = textField("rcon_port", 5, "[0-9]*", Integer.toString(state.config.rconPort));
        passwordField = textField("rcon_password", 256, null, state.config.rconPassword == null ? "" : state.config.rconPassword);
        pollField = textField("rcon_poll", 2, "[0-9]*", Integer.toString(state.config.rconPollSeconds));
        toggle("hud_enabled", () -> state.hudEnabled, v -> state.hudEnabled = v);
        hudScaleField = textField("hud_scale", 5, "[0-9.]*", Double.toString(state.config.hudScale));
        gammaField = textField("gamma", 5, "[0-9.]*", Double.toString(state.config.gamma));
        toggle("auto_camera", () -> state.autoCamera, v -> state.autoCamera = v);
        toggle("brighten", () -> state.brighten, v -> state.brighten = v);
        toggle("show_vitals", () -> state.vitals, v -> state.vitals = v);
        toggle("show_inventory", () -> state.inventory, v -> state.inventory = v);
        toggle("show_containers", () -> state.containers, v -> state.containers = v);
        toggle("show_view", () -> state.view, v -> state.view = v);
        toggle("show_hands", () -> state.hands, v -> state.hands = v);

        for (AbstractWidget widget : widgets) addWidget(widget);
        doneButton = Button.builder(ObserverText.text("curio-observer.config.done"), b -> done()).bounds(0, 0, 120, 20).build();
        refreshLabels.add(() -> doneButton.setMessage(ObserverText.text("curio-observer.config.done")));
        upButton = Button.builder(Component.literal("▲"), b -> scrollBy(-ROW * 3)).bounds(0, 0, 40, 20).build();
        downButton = Button.builder(Component.literal("▼"), b -> scrollBy(ROW * 3)).bounds(0, 0, 40, 20).build();
        addRenderableWidget(doneButton);
        addRenderableWidget(upButton);
        addRenderableWidget(downButton);
        reposition();
    }

    private void toggle(String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        Button button = Button.builder(toggleLabel(label, getter.getAsBoolean()), b -> {
            setter.accept(!getter.getAsBoolean());
            b.setMessage(toggleLabel(label, getter.getAsBoolean()));
        }).bounds(0, 0, 100, 20).build();
        widgets.add(button); labels.add(null);
        refreshLabels.add(() -> button.setMessage(toggleLabel(label, getter.getAsBoolean())));
    }

    private EditBox textField(String label, int maxLength, String predicate, String initial) {
        EditBox field = new EditBox(Minecraft.getInstance().font, 0, 0, FIELD_W, 20, ObserverText.text("curio-observer.config." + label));
        field.setMaxLength(maxLength);
        if (predicate != null) field.setFilter(value -> value.matches(predicate));
        field.setValue(state.editedText.getOrDefault(label, initial));
        field.setResponder(value -> state.editedText.put(label, value));
        widgets.add(field); labels.add(label);
        refreshLabels.add(() -> field.setMessage(ObserverText.text("curio-observer.config." + label)));
        return field;
    }

    private void reposition() {
        int contentHeight = widgets.size() * ROW + CONTENT_TOP;
        int maxScroll = Math.max(0, contentHeight - (height - FOOTER_HEIGHT));
        state.scroll = Math.max(0, Math.min(state.scroll, maxScroll));
        int y = CONTENT_TOP - state.scroll;
        for (int i = 0; i < widgets.size(); i++) {
            AbstractWidget widget = widgets.get(i);
            if (labels.get(i) == null) { widget.setX(PAD); widget.setWidth(width - PAD * 2); }
            else { widget.setX(PAD + LABEL_W); widget.setWidth(Math.max(40, width - PAD * 2 - LABEL_W)); }
            widget.setY(y);
            y += ROW;
        }
        doneButton.setX(width / 2 - 60); doneButton.setY(height - 24);
        upButton.setX(width - 100); upButton.setY(height - 24);
        downButton.setX(width - 56); downButton.setY(height - 24);
    }

    private void scrollBy(int amount) { state.scroll += amount; reposition(); }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (click(doneButton, mouseX, mouseY, button) || click(upButton, mouseX, mouseY, button) || click(downButton, mouseX, mouseY, button)) return true;
        int bottom = height - FOOTER_HEIGHT;
        for (AbstractWidget widget : widgets) {
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

    private boolean click(AbstractWidget widget, double mouseX, double mouseY, int button) {
        if (!widget.mouseClicked(mouseX, mouseY, button)) return false;
        setFocused(widget);
        if (button == 0) setDragging(true);
        return true;
    }

    private void done() {
        state.save(targetField.getValue(), observeIntervalField.getValue(), feedUrlField.getValue(),
                hostField.getValue(), portField.getValue(), passwordField.getValue(), pollField.getValue(),
                hudScaleField.getValue(), gammaField.getValue());
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float delta) {
        renderBackground(pose);
        super.render(pose, mouseX, mouseY, delta);
        var client = Minecraft.getInstance();
        var font = client.font;
        drawCenteredString(pose, font, getTitle(), width / 2, 4, 0xFFFFFFFF);
        font.drawShadow(pose, font.plainSubstrByWidth(connectionStatus.get().getString(), width - PAD * 2), PAD, 18, 0xFFE0E0E0);
        int bottom = height - FOOTER_HEIGHT;
        var win = client.getWindow();
        double scale = win.getGuiScale();
        RenderSystem.enableScissor(0, (int) (win.getHeight() - bottom * scale), win.getWidth(), Math.max(0, (int) ((bottom - CONTENT_TOP) * scale)));
        for (int i = 0; i < widgets.size(); i++) {
            AbstractWidget widget = widgets.get(i);
            String label = labels.get(i);
            if (label != null) {
                String caption = ObserverText.text("curio-observer.config." + label).getString();
                font.drawShadow(pose, font.plainSubstrByWidth(caption, LABEL_W - 4), PAD, widget.getY() + 6, 0xFFA0A0A0);
            }
            widget.render(pose, mouseX, mouseY, delta);
        }
        RenderSystem.disableScissor();
        if (state.autoObserve) font.drawShadow(pose,
                font.plainSubstrByWidth(ObserverText.text("curio-observer.config.observe_hint").getString(), width - PAD * 2),
                PAD, height - 39, 0xFFFFCC77);
    }

    @Override public Component getTitle() { return ObserverText.text("curio-observer.config.title"); }
    @Override public void removed() { ObserverText.setLanguage(state.config.language); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
