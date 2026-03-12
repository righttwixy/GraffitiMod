package by.righttwixys.graffiti.client.gui;

import by.righttwixys.graffiti.GraffitiMod;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class GraffitiHUD {
    private static final Identifier BAR = Identifier.of("graffiti", "textures/gui/bar.png");
    private static final Identifier SELECTOR = Identifier.of("graffiti", "textures/gui/selector.png");

    public static int selectedIndex = 0;
    private static float currentVisualX = 0;

    private static final float LERP_SPEED = 0.25f; // Сделал чуть быстрее для отзывчивости
    private static float hudAlpha = 0.0f;
    private static final float FADE_SPEED = 0.1f;

    private static boolean lastA = false;
    private static boolean lastD = false;

    private static final Text[] TOOL_NAMES = {
            Text.translatable("hud.graffiti.tool.pencil"),
            Text.translatable("hud.graffiti.tool.eraser"),
            Text.translatable("hud.graffiti.tool.fill"),
            Text.translatable("hud.graffiti.tool.picker")
    };

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // Проверяем, держит ли игрок баллончик
            boolean hasItem = client.player.getMainHandStack().isOf(GraffitiMod.GRAFFITI_TOOL);
            boolean isCtrlDown = InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL);

            if (hasItem && isCtrlDown) {
                // Блокируем ТОЛЬКО движение, не трогаем атаку (ЛКМ)
                unpressKey(client.options.forwardKey);
                unpressKey(client.options.backKey);
                unpressKey(client.options.leftKey);
                unpressKey(client.options.rightKey);
                unpressKey(client.options.jumpKey);

                boolean isADown = InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_A);
                boolean isDDown = InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_D);

                // Переключение влево
                if (isADown && !lastA) {
                    selectedIndex = (selectedIndex <= 0) ? 3 : selectedIndex - 1;
                    playClickSound(client);
                }
                // Переключение вправо
                if (isDDown && !lastD) {
                    selectedIndex = (selectedIndex >= 3) ? 0 : selectedIndex + 1;
                    playClickSound(client);
                }

                lastA = isADown;
                lastD = isDDown;
            } else {
                lastA = false;
                lastD = false;
            }
        });

        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden) return;

            boolean hasItem = client.player.getMainHandStack().isOf(GraffitiMod.GRAFFITI_TOOL);
            boolean isCtrlDown = InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL);

            // Плавное появление/исчезновение
            if (hasItem && isCtrlDown) hudAlpha = Math.min(1.0f, hudAlpha + FADE_SPEED);
            else hudAlpha = Math.max(0.0f, hudAlpha - FADE_SPEED);

            if (hudAlpha > 0) render(context, client);
        });
    }

    private static void unpressKey(KeyBinding key) {
        key.setPressed(false);
    }

    private static void playClickSound(MinecraftClient client) {
        if (client.player != null) {
            client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 0.4f, 1.8f);
        }
    }

    private static void render(DrawContext context, MinecraftClient client) {
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();

        context.getMatrices().push();
        float scale = 2.0f;
        context.getMatrices().scale(scale, scale, 1.0f);

        // Центрирование GUI
        int x = (int)((sw / 2f) / scale) - 32;
        int y = (int)(sh / scale) - 30; // Чуть приподнял выше

        RenderSystem.enableBlend();
        context.setShaderColor(1.0f, 1.0f, 1.0f, hudAlpha);

        // 1. Фон (Bar)
        context.drawTexture(BAR, x, y, 0, 0, 64, 16, 64, 16);

        // 2. Плавная анимация селектора
        float targetX = x + (selectedIndex * 16);
        currentVisualX += (targetX - currentVisualX) * LERP_SPEED;
        context.drawTexture(SELECTOR, (int)currentVisualX, y, 0, 0, 16, 16, 16, 16);

        // 3. Иконки
        String[] icons = {"✎", "✕", "█", "✀"}; // Заменил смайлики на более стандартные символы
        for (int i = 0; i < 4; i++) {
            int tx = x + 8 + (i * 16);
            int alpha = (int)(hudAlpha * 255);
            int textColor = (alpha << 24) | 0xFFFFFF;
            context.drawCenteredTextWithShadow(client.textRenderer, icons[i], tx, y + 4, textColor);
        }

        context.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        context.getMatrices().pop();

        // 4. Название инструмента
        Text displayText = Text.literal("— ").append(TOOL_NAMES[selectedIndex]).append(" —");
        int alpha = (int)(hudAlpha * 255);
        int textColor = (alpha << 24) | 0xFFFFFF;
        context.drawCenteredTextWithShadow(client.textRenderer, displayText, sw / 2, sh - 75, textColor);
    }
}