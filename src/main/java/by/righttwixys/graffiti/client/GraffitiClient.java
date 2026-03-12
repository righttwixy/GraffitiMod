package by.righttwixys.graffiti.client;

import by.righttwixys.graffiti.GraffitiMod;
import by.righttwixys.graffiti.client.gui.GraffitiHUD;
import by.righttwixys.graffiti.client.renderer.GraffitiRenderer;
import by.righttwixys.graffiti.client.renderer.PixelOutlineRenderer;
import by.righttwixys.graffiti.config.GraffitiConfig;
import by.righttwixys.graffiti.item.GraffitiItem;
import by.righttwixys.graffiti.network.PaintPayload;
import by.righttwixys.graffiti.network.SyncGraffitiPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;

public class GraffitiClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        GraffitiConfig.load();
        WorldRenderEvents.LAST.register(PixelOutlineRenderer::render);
        // === ФИКС СМЕШИВАНИЯ МИРОВ ===
        // Очищаем данные при ВХОДЕ (Join) и при ВЫХОДЕ (Disconnect)
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            clearClientCache();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            clearClientCache();
        });

        // --- 1. ПРИЕМ ПАКЕТОВ РИСОВАНИЯ ---
        ClientPlayNetworking.registerGlobalReceiver(PaintPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                // Обновляем кэш через метод в рендерере (который мы добавили ранее)
                GraffitiRenderer.addPixelToCache(payload);
            });
        });

        // --- 2. ПОЛНАЯ СИНХРОНИЗАЦИЯ ---
        ClientPlayNetworking.registerGlobalReceiver(SyncGraffitiPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                // На всякий случай очищаем еще раз перед массовой загрузкой
                GraffitiRenderer.GRAFFITI_CACHE.clear();
                for (PaintPayload p : payload.allPixels()) {
                    GraffitiRenderer.addPixelToCache(p);
                }
                System.out.println("[Graffiti] Загружено " + payload.allPixels().size() + " пикселей.");
            });
        });

        GraffitiHUD.init();
        GraffitiRenderer.init();

        // --- 3. ОБРАБОТКА КЛИКА ---
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (player.getStackInHand(hand).isOf(GraffitiMod.GRAFFITI_TOOL)) {
                if (world.isClient) {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.crosshairTarget instanceof BlockHitResult hit) {
                        GraffitiItem.handleAttack(hit);
                    }
                }
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        // --- 4. ЦВЕТ ПРЕДМЕТА ---
        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
            if (tintIndex == 1) {
                int itemColor = GraffitiItem.getColor(stack);
                return 0xFF000000 | (itemColor & 0xFFFFFF);
            }
            return 0xFFFFFFFF;
        }, GraffitiMod.GRAFFITI_TOOL);
    }

    private void clearClientCache() {
        // Очищаем и список старых пакетов, и основной кэш отрисовки
        if (GraffitiRenderer.GRAFFITI_CACHE != null) {
            GraffitiRenderer.GRAFFITI_CACHE.clear();
        }
        if (GraffitiRenderer.PIXELS != null) {
            GraffitiRenderer.PIXELS.clear();
        }
    }
}