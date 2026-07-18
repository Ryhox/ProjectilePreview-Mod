package dev.duels.projectilepreview.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** User-tweakable settings, persisted as JSON in the Fabric config folder. */
public final class PreviewConfig {

    public enum ColorMode { STATIC, RAINBOW, GRADIENT }

    public enum LineStyle { SOLID, DASHED, DOTTED }

    public enum LineOrigin { HAND, EYE }

    public static final int MIN_OPACITY = 10;
    public static final int MAX_OPACITY = 100;
    public static final float MIN_LINE_WIDTH = 1.0f;
    public static final float MAX_LINE_WIDTH = 10.0f;
    public static final float MIN_RAINBOW_SPEED = 0.25f;
    public static final float MAX_RAINBOW_SPEED = 3.0f;
    public static final int MIN_TICKS = 20;
    public static final int MAX_TICKS = 200;

    public boolean enabled = true;
    public ColorMode colorMode = ColorMode.STATIC;
    public LineStyle lineStyle = LineStyle.SOLID;
    public LineOrigin lineOrigin = LineOrigin.HAND;
    public int red = 255;
    public int green = 255;
    public int blue = 255;
    public int opacity = 100;
    public float lineWidth = 3.0f;
    public float rainbowSpeed = 1.0f;
    public boolean smoothCurve = false;
    public boolean fadeOut = false;
    public boolean showHitBox = true;
    public boolean showImpactMarker = false;
    public int maxTicks = 100;

    private static final Logger LOGGER = LoggerFactory.getLogger("projectilepreview");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("projectilepreview.json");

    private static PreviewConfig instance;

    public static PreviewConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static PreviewConfig load() {
        try {
            if (Files.exists(PATH)) {
                PreviewConfig cfg = GSON.fromJson(Files.readString(PATH), PreviewConfig.class);
                if (cfg != null) {
                    cfg.sanitize();
                    return cfg;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Could not read config, using defaults", e);
        }
        return new PreviewConfig();
    }

    public void save() {
        try {
            sanitize();
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException e) {
            LOGGER.warn("Could not save config", e);
        }
    }

    public void resetToDefaults() {
        PreviewConfig d = new PreviewConfig();
        enabled = d.enabled;
        colorMode = d.colorMode;
        lineStyle = d.lineStyle;
        lineOrigin = d.lineOrigin;
        red = d.red;
        green = d.green;
        blue = d.blue;
        opacity = d.opacity;
        lineWidth = d.lineWidth;
        rainbowSpeed = d.rainbowSpeed;
        smoothCurve = d.smoothCurve;
        fadeOut = d.fadeOut;
        showHitBox = d.showHitBox;
        showImpactMarker = d.showImpactMarker;
        maxTicks = d.maxTicks;
    }

    private void sanitize() {
        if (colorMode == null) colorMode = ColorMode.STATIC;
        if (lineStyle == null) lineStyle = LineStyle.SOLID;
        if (lineOrigin == null) lineOrigin = LineOrigin.HAND;
        red = Mth.clamp(red, 0, 255);
        green = Mth.clamp(green, 0, 255);
        blue = Mth.clamp(blue, 0, 255);
        opacity = Mth.clamp(opacity, MIN_OPACITY, MAX_OPACITY);
        lineWidth = Mth.clamp(lineWidth, MIN_LINE_WIDTH, MAX_LINE_WIDTH);
        rainbowSpeed = Mth.clamp(rainbowSpeed, MIN_RAINBOW_SPEED, MAX_RAINBOW_SPEED);
        maxTicks = Mth.clamp(maxTicks, MIN_TICKS, MAX_TICKS);
    }
}
