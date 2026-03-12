package by.righttwixys.graffiti.client.gui;

import by.righttwixys.graffiti.item.GraffitiItem;
import by.righttwixys.graffiti.client.renderer.GraffitiRenderer;
import by.righttwixys.graffiti.network.ColorPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.awt.Color;

public class GraffitiScreen extends Screen {
    private final ItemStack stack; // Ð¥Ñ€Ð°Ð½Ð¸Ð¼ ÑÑÑ‹Ð»ÐºÑƒ Ð½Ð° Ð±Ð°Ð»Ð»Ð¾Ð½Ñ‡Ð¸Ðº
    private int px, py;
    private final int size = 100;
    private float hue, saturation, brightness, alpha;
    private TextFieldWidget hexField, sizeField;

    private NativeImageBackedTexture paletteTexture;
    private Identifier textureId;

    public GraffitiScreen(ItemStack stack) {
        super(Text.translatable("screen.graffiti.title"));
        this.stack = stack;
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // ÐžÑÑ‚Ð°Ð²Ð»ÑÐµÐ¼ Ð¿ÑƒÑÑ‚Ñ‹Ð¼
    }

    // Ð£Ð±Ð¸Ñ€Ð°ÐµÑ‚ Ñ€Ð°Ð·Ð¼Ñ‹Ñ‚Ð¸Ðµ (blur) Ð¸Ð³Ñ€Ð¾Ð²Ð¾Ð³Ð¾ Ð¼Ð¸Ñ€Ð°
    @Override
    public void renderInGameBackground(DrawContext context) {
        // ÐžÑÑ‚Ð°Ð²Ð»ÑÐµÐ¼ Ð¿ÑƒÑÑ‚Ñ‹Ð¼
    }

    @Override
    protected void init() {
        this.px = width / 2 - 120;
        this.py = height / 2 - 70;

        // Ð§Ð¸Ñ‚Ð°ÐµÐ¼ Ñ†Ð²ÐµÑ‚ Ð½Ð°Ð¿Ñ€ÑÐ¼ÑƒÑŽ Ð¸Ð· Ð­Ð¢ÐžÐ“Ðž Ð±Ð°Ð»Ð»Ð¾Ð½Ñ‡Ð¸ÐºÐ°
        int itemColor = GraffitiItem.getColor(stack);

        float[] hsb = Color.RGBtoHSB((itemColor >> 16) & 0xFF, (itemColor >> 8) & 0xFF, itemColor & 0xFF, null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
        this.alpha = ((itemColor >> 24) & 0xFF) / 255f;

        hexField = new TextFieldWidget(textRenderer, px + 110, py + 15, 85, 16, Text.translatable("screen.graffiti.hex_label"));
        sizeField = new TextFieldWidget(textRenderer, px + 110, py + 45, 40, 16, Text.translatable("screen.graffiti.size_label"));

        hexField.setText(String.format("#%08X", itemColor));
        sizeField.setText(String.valueOf(GraffitiItem.brushSize));

        this.addDrawableChild(hexField);
        this.addDrawableChild(sizeField);

        updatePaletteTexture();
    }

    private void updatePaletteTexture() {
        if (paletteTexture != null) paletteTexture.close();
        NativeImage img = new NativeImage(size, size, false);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int c = Color.HSBtoRGB(hue, i / 100f, 1.0f - (j / 100f));
                int abgr = 0xFF000000 | ((c & 0xFF) << 16) | (c & 0xFF00) | ((c >> 16) & 0xFF);
                img.setColor(i, j, abgr);
            }
        }
        paletteTexture = new NativeImageBackedTexture(img);
        textureId = Identifier.of("graffiti", "palette_cache");
        client.getTextureManager().registerTexture(textureId, paletteTexture);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(px - 10, py - 10, px + size + 105, py + size + 40, 0x88000000);
        if (textureId != null) context.drawTexture(textureId, px, py, 0, 0, size, size, size, size);

        for (int i = 0; i < size; i++) {
            context.fill(px + i, py + size + 5, px + i + 1, py + size + 15, 0xFF000000 | Color.HSBtoRGB(i/100f, 1f, 1f));
            int gray = (int)((i/100f)*255);
            context.fill(px + i, py + size + 20, px + i + 1, py + size + 30, 0xFF000000 | (gray << 16 | gray << 8 | gray));
        }

        context.drawBorder(px + (int)(saturation * size) - 2, py + (int)((1f - brightness) * size) - 2, 5, 5, 0xFFFFFFFF);
        context.fill(px + (int)(hue * size), py + size + 4, px + (int)(hue * size) + 2, py + size + 16, 0xFFFFFFFF);
        context.fill(px + (int)(alpha * size), py + size + 19, px + (int)(alpha * size) + 2, py + size + 31, 0xFFFFFFFF);

        context.drawTextWithShadow(textRenderer, Text.translatable("screen.graffiti.hex_alpha"), px + 110, py + 5, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.translatable("screen.graffiti.brush_size"), px + 110, py + 35, 0xFFFFFF);

        context.fill(px + 110, py + 70, px + 185, py + 95, 0xFFFFFFFF);
        context.fill(px + 111, py + 71, px + 184, py + 94, GraffitiItem.getColor(stack));

        super.render(context, mouseX, mouseY, delta);
    }

    private void handleInputs(double mx, double my) {
        boolean hueChanged = false;
        if (mx >= px && mx < px + size && my >= py && my < py + size) {
            saturation = (float)((mx - px) / size);
            brightness = 1.0f - (float)((my - py) / size);
        } else if (mx >= px && mx < px + size && my >= py + size + 5 && my < py + size + 15) {
            hue = (float)((mx - px) / size);
            hueChanged = true;
        } else if (mx >= px && mx < px + size && my >= py + size + 20 && my < py + size + 30) {
            alpha = (float)((mx - px) / size);
        }
        if (hueChanged) updatePaletteTexture();
        updateFinalColor();
    }

    private void updateFinalColor() {
        int rgb = Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
        int newColor = (Math.round(alpha * 255) << 24) | rgb;

        // 1. Ð¡Ð¾Ñ…Ñ€Ð°Ð½ÑÐµÐ¼ Ñ†Ð²ÐµÑ‚ Ð² Ð¿Ñ€ÐµÐ´Ð¼ÐµÑ‚ Ð»Ð¾ÐºÐ°Ð»ÑŒÐ½Ð¾ (Ð´Ð»Ñ ÐºÐ»Ð¸ÐµÐ½Ñ‚Ð°)
        GraffitiItem.setColor(stack, newColor);

        // 2. ÐžÑ‚Ð¿Ñ€Ð°Ð²Ð»ÑÐµÐ¼ Ð¿Ð°ÐºÐµÑ‚ Ð½Ð° ÑÐµÑ€Ð²ÐµÑ€ (Ñ‡Ñ‚Ð¾Ð±Ñ‹ ÑÐµÑ€Ð²ÐµÑ€ Ñ‚Ð¾Ð¶Ðµ Ð·Ð½Ð°Ð» Ð¾ ÑÐ¼ÐµÐ½Ðµ Ñ†Ð²ÐµÑ‚Ð°)
        ClientPlayNetworking.send(new ColorPayload(newColor));

        hexField.setText(String.format("#%08X", newColor));
        if (client != null && client.worldRenderer != null) client.worldRenderer.scheduleTerrainUpdate();
    }

    @Override public boolean mouseClicked(double mx, double my, int b) { handleInputs(mx, my); return super.mouseClicked(mx, my, b); }
    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) { handleInputs(mx, my); return super.mouseDragged(mx, my, b, dx, dy); }

    @Override
    public void close() {
        if (paletteTexture != null) paletteTexture.close();
        try { GraffitiItem.brushSize = Integer.parseInt(sizeField.getText()); } catch (Exception ignored) {}
        super.close();
    }

}