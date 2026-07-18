package dev.duels.projectilepreview.client;

import dev.duels.projectilepreview.client.projectile.RenderUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

public final class ConfigScreen extends Screen {

    private static final int COL_WIDTH = 150;
    private static final int COL_GAP = 10;
    private static final int WIDGET_HEIGHT = 20;
    private static final int ROW_STEP = 22;
    private static final int TOP = 28;
    private static final int TITLE_Y = 10;
    private static final int FOOTER_MARGIN = 26;
    private static final int PREVIEW_MIN_HEIGHT = 30;
    private static final int PREVIEW_SAMPLES = 96;
    private static final int SWATCH_SIZE = 20;

    private final Screen parent;
    private final PreviewConfig cfg = PreviewConfig.get();

    private final List<AbstractSliderButton> rgbSliders = new ArrayList<>();
    private AbstractSliderButton rainbowSlider;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("projectilepreview.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rgbSliders.clear();

        int leftX = width / 2 - COL_WIDTH - COL_GAP / 2;
        int rightX = width / 2 + COL_GAP / 2;
        int y = TOP;

        addRenderableWidget(CycleButton.onOffBuilder(cfg.enabled)
                .create(leftX, y, COL_WIDTH, WIDGET_HEIGHT, label("enabled"), (b, v) -> cfg.enabled = v));
        addRenderableWidget(CycleButton.builder(ConfigScreen::colorModeName, cfg.colorMode)
                .withValues(PreviewConfig.ColorMode.values())
                .create(leftX, y += ROW_STEP, COL_WIDTH, WIDGET_HEIGHT, label("colormode"), (b, v) -> {
                    cfg.colorMode = v;
                    updateSliderStates();
                }));
        addRenderableWidget(CycleButton.builder(ConfigScreen::lineStyleName, cfg.lineStyle)
                .withValues(PreviewConfig.LineStyle.values())
                .create(leftX, y += ROW_STEP, COL_WIDTH, WIDGET_HEIGHT, label("linestyle"), (b, v) -> cfg.lineStyle = v));
        addRenderableWidget(CycleButton.builder(ConfigScreen::lineOriginName, cfg.lineOrigin)
                .withValues(PreviewConfig.LineOrigin.values())
                .create(leftX, y += ROW_STEP, COL_WIDTH, WIDGET_HEIGHT, label("lineorigin"), (b, v) -> cfg.lineOrigin = v))
                .setTooltip(Tooltip.create(Component.translatable("projectilepreview.config.lineorigin.tooltip")));
        addRenderableWidget(CycleButton.onOffBuilder(cfg.smoothCurve)
                .create(leftX, y += ROW_STEP, COL_WIDTH, WIDGET_HEIGHT, label("smooth"), (b, v) -> cfg.smoothCurve = v))
                .setTooltip(Tooltip.create(Component.translatable("projectilepreview.config.smooth.tooltip")));
        addRenderableWidget(CycleButton.onOffBuilder(cfg.fadeOut)
                .create(leftX, y += ROW_STEP, COL_WIDTH, WIDGET_HEIGHT, label("fade"), (b, v) -> cfg.fadeOut = v));
        addRenderableWidget(CycleButton.onOffBuilder(cfg.showHitBox)
                .create(leftX, y += ROW_STEP, COL_WIDTH, WIDGET_HEIGHT, label("hitbox"), (b, v) -> cfg.showHitBox = v));
        addRenderableWidget(CycleButton.onOffBuilder(cfg.showImpactMarker)
                .create(leftX, y += ROW_STEP, COL_WIDTH, WIDGET_HEIGHT, label("marker"), (b, v) -> cfg.showImpactMarker = v));

        y = TOP;

        addRenderableWidget(slider(rightX, y, "width",
                PreviewConfig.MIN_LINE_WIDTH, PreviewConfig.MAX_LINE_WIDTH, cfg.lineWidth,
                v -> cfg.lineWidth = (float) v,
                v -> Component.literal(String.format("%.1f", v))));
        addRenderableWidget(slider(rightX, y += ROW_STEP, "opacity",
                PreviewConfig.MIN_OPACITY, PreviewConfig.MAX_OPACITY, cfg.opacity,
                v -> cfg.opacity = (int) Math.round(v),
                v -> Component.literal(Math.round(v) + "%")));

        AbstractSliderButton r = slider(rightX, y += ROW_STEP, "red", 0, 255, cfg.red,
                v -> cfg.red = (int) Math.round(v), v -> Component.literal(String.valueOf(Math.round(v))));
        AbstractSliderButton g = slider(rightX, y += ROW_STEP, "green", 0, 255, cfg.green,
                v -> cfg.green = (int) Math.round(v), v -> Component.literal(String.valueOf(Math.round(v))));
        AbstractSliderButton b = slider(rightX, y += ROW_STEP, "blue", 0, 255, cfg.blue,
                v -> cfg.blue = (int) Math.round(v), v -> Component.literal(String.valueOf(Math.round(v))));
        rgbSliders.add(addRenderableWidget(r));
        rgbSliders.add(addRenderableWidget(g));
        rgbSliders.add(addRenderableWidget(b));

        rainbowSlider = addRenderableWidget(slider(rightX, y += ROW_STEP, "rainbowspeed",
                PreviewConfig.MIN_RAINBOW_SPEED, PreviewConfig.MAX_RAINBOW_SPEED, cfg.rainbowSpeed,
                v -> cfg.rainbowSpeed = (float) v,
                v -> Component.literal(String.format("%.2fx", v))));

        addRenderableWidget(slider(rightX, y + ROW_STEP, "simlength",
                PreviewConfig.MIN_TICKS, PreviewConfig.MAX_TICKS, cfg.maxTicks,
                v -> cfg.maxTicks = (int) Math.round(v),
                v -> Component.translatable("projectilepreview.config.simlength.value", Math.round(v))))
                .setTooltip(Tooltip.create(Component.translatable("projectilepreview.config.simlength.tooltip")));

        addRenderableWidget(Button.builder(Component.translatable("projectilepreview.config.reset"), btn -> {
                    cfg.resetToDefaults();
                    rebuildWidgets();
                })
                .bounds(width / 2 - COL_WIDTH - COL_GAP / 2, height - FOOTER_MARGIN, COL_WIDTH, WIDGET_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> onClose())
                .bounds(width / 2 + COL_GAP / 2, height - FOOTER_MARGIN, COL_WIDTH, WIDGET_HEIGHT)
                .build());

        updateSliderStates();
    }

    private void updateSliderStates() {
        boolean staticColor = cfg.colorMode != PreviewConfig.ColorMode.RAINBOW;
        for (AbstractSliderButton s : rgbSliders) {
            s.active = staticColor;
        }
        rainbowSlider.active = cfg.colorMode == PreviewConfig.ColorMode.RAINBOW;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);

        g.centeredText(font, title.getString(), width / 2, TITLE_Y, 0xFFFFFFFF);
        drawColorSwatch(g);
        drawPreviewStrip(g);
    }

    private void drawColorSwatch(GuiGraphicsExtractor g) {
        int x = width / 2 + COL_GAP / 2 + COL_WIDTH + 6;
        if (x + SWATCH_SIZE > width - 4) return;

        int y = TOP + 2 * ROW_STEP;
        int color = ARGB.color(255, RenderUtils.colorAt(cfg, 0.0f, RenderUtils.animationTime()));

        g.fill(x - 1, y - 1, x + SWATCH_SIZE + 1, y + SWATCH_SIZE + 1, 0xFF000000);
        g.fill(x, y, x + SWATCH_SIZE, y + SWATCH_SIZE, color);
    }

    /** Live sample arc rendered with the current settings. */
    private void drawPreviewStrip(GuiGraphicsExtractor g) {
        int top = TOP + 8 * ROW_STEP + 4;
        int bottom = height - FOOTER_MARGIN - 8;
        if (bottom - top < PREVIEW_MIN_HEIGHT) return;

        int x1 = width / 2 - COL_WIDTH - COL_GAP / 2;
        int x2 = width / 2 + COL_GAP / 2 + COL_WIDTH;

        g.fill(x1, top, x2, bottom, 0x66000000);

        int pad = 10;
        int innerW = x2 - x1 - 2 * pad;
        int innerH = bottom - top - 2 * pad;
        if (innerW <= 0 || innerH <= 0) return;

        float time = RenderUtils.animationTime();
        int dot = Math.max(2, Math.round(cfg.lineWidth / 2.0f));

        for (int i = 0; i < PREVIEW_SAMPLES; i++) {
            if (previewSkip(cfg.lineStyle, i)) continue;

            float t = (float) i / (PREVIEW_SAMPLES - 1);
            float arc = 4.0f * t * (1.0f - t);

            int px = x1 + pad + Math.round(t * innerW);
            int py = bottom - pad - Math.round(arc * innerH);

            int alpha = cfg.opacity * 255 / 100;
            if (cfg.fadeOut) alpha = (int) (alpha * (1.0f - 0.85f * t));

            int color = ARGB.color(Mth.clamp(alpha, 0, 255), RenderUtils.colorAt(cfg, t, time));
            g.fill(px - dot / 2, py - dot / 2, px + dot / 2 + 1, py + dot / 2 + 1, color);
        }
    }

    private static boolean previewSkip(PreviewConfig.LineStyle style, int sample) {
        return switch (style) {
            case SOLID -> false;
            case DASHED -> (sample % 12) >= 7;
            case DOTTED -> (sample % 6) >= 3;
        };
    }

    @Override
    public void onClose() {
        cfg.save();
        minecraft.gui.setScreen(parent);
    }

    private static Component label(String key) {
        return Component.translatable("projectilepreview.config." + key);
    }

    private static Component colorModeName(PreviewConfig.ColorMode mode) {
        return Component.translatable("projectilepreview.config.colormode." + mode.name().toLowerCase());
    }

    private static Component lineStyleName(PreviewConfig.LineStyle style) {
        return Component.translatable("projectilepreview.config.linestyle." + style.name().toLowerCase());
    }

    private static Component lineOriginName(PreviewConfig.LineOrigin origin) {
        return Component.translatable("projectilepreview.config.lineorigin." + origin.name().toLowerCase());
    }

    private AbstractSliderButton slider(int x, int y, String key, double min, double max, double initial,
                                        DoubleConsumer setter, DoubleFunction<Component> formatter) {
        Component name = label(key);
        double normalized = (Mth.clamp(initial, min, max) - min) / (max - min);

        return new AbstractSliderButton(x, y, COL_WIDTH, WIDGET_HEIGHT, Component.empty(), normalized) {
            {
                updateMessage();
            }

            private double current() {
                return min + value * (max - min);
            }

            @Override
            protected void updateMessage() {
                setMessage(Component.empty().append(name).append(": ").append(formatter.apply(current())));
            }

            @Override
            protected void applyValue() {
                setter.accept(current());
            }
        };
    }
}
