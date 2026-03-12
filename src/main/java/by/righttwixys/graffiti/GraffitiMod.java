package by.righttwixys.graffiti;

import by.righttwixys.graffiti.item.GraffitiItem;
import by.righttwixys.graffiti.network.ColorPayload;
import by.righttwixys.graffiti.network.PaintPayload;
import by.righttwixys.graffiti.network.SyncGraffitiPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GraffitiMod implements ModInitializer {
    public static final String MOD_ID = "graffiti";

    // Предмет и кэш данных на стороне сервера
    public static final Item GRAFFITI_TOOL = new GraffitiItem(new Item.Settings().maxCount(1));
    public static final Map<Long, Map<Direction, int[][]>> SERVER_CACHE = new HashMap<>();

    // Регистрация творческой вкладки
    public static final RegistryKey<ItemGroup> GRAFFITI_GROUP_KEY = RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(MOD_ID, "graffiti_group"));
    public static final ItemGroup GRAFFITI_GROUP = FabricItemGroup.builder()
            .icon(() -> new ItemStack(GRAFFITI_TOOL))
            .displayName(Text.translatable("itemGroup.graffiti.group"))
            .entries((displayContext, entries) -> entries.add(GRAFFITI_TOOL))
            .build();

    @Override
    public void onInitialize() {
        // Регистрация сетевых пакетов
        PayloadTypeRegistry.playC2S().register(PaintPayload.ID, PaintPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PaintPayload.ID, PaintPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ColorPayload.ID, ColorPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncGraffitiPayload.ID, SyncGraffitiPayload.CODEC);

        // Регистрация контента
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "graffiti_tool"), GRAFFITI_TOOL);
        Registry.register(Registries.ITEM_GROUP, GRAFFITI_GROUP_KEY, GRAFFITI_GROUP);

        // Обработка рисования (получение пикселя от клиента и рассылка остальным)
        ServerPlayNetworking.registerGlobalReceiver(PaintPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                // Сохраняем в кэш сервера
                SERVER_CACHE.computeIfAbsent(payload.pos().asLong(), k -> new EnumMap<>(Direction.class))
                        .computeIfAbsent(payload.side(), k -> new int[16][16])[payload.u()][payload.v()] = payload.color();

                // Рассылаем всем игрокам в этом мире (кроме отправителя)
                for (var player : PlayerLookup.world((ServerWorld) context.player().getWorld())) {
                    if (player != context.player()) {
                        ServerPlayNetworking.send(player, payload);
                    }
                }
            });
        });

        // ЗАГРУЗКА ДАННЫХ ПРИ СТАРТЕ СЕРВЕРА
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SERVER_CACHE.clear();
            File file = new File(server.getSavePath(WorldSavePath.ROOT).toFile(), "graffiti.bin");
            if (!file.exists()) return;

            try (DataInputStream in = new DataInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(file))))) {
                int count = in.readInt();
                for (int i = 0; i < count; i++) {
                    long pos = in.readLong();
                    int sideId = in.readByte();
                    int u = in.readUnsignedByte();
                    int v = in.readUnsignedByte();
                    int color = in.readInt();

                    // Защита от ArrayIndexOutOfBoundsException
                    if (u >= 0 && u < 16 && v >= 0 && v < 16 && sideId >= 0 && sideId < 6) {
                        SERVER_CACHE.computeIfAbsent(pos, k -> new EnumMap<>(Direction.class))
                                .computeIfAbsent(Direction.byId(sideId), k -> new int[16][16])[u][v] = color;
                    }
                }
            } catch (Exception e) {
                System.err.println("[GraffitiMod] Failed to load graffiti data!");
                e.printStackTrace();
            }
        });

        // СОХРАНЕНИЕ ДАННЫХ ПРИ ОСТАНОВКЕ СЕРВЕРА
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            File file = new File(server.getSavePath(WorldSavePath.ROOT).toFile(), "graffiti.bin");
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(file))))) {
                List<long[]> data = new ArrayList<>();
                SERVER_CACHE.forEach((pos, sides) -> sides.forEach((side, grid) -> {
                    for (int u = 0; u < 16; u++) {
                        for (int v = 0; v < 16; v++) {
                            if (grid[u][v] != 0) {
                                // Пакуем данные: [BlockPos, SideID, U, V, Color]
                                data.add(new long[]{pos, side.getId(), u, v, grid[u][v]});
                            }
                        }
                    }
                }));

                out.writeInt(data.size());
                for (long[] p : data) {
                    out.writeLong(p[0]);
                    out.writeByte((int) p[1]);
                    out.writeByte((int) p[2]);
                    out.writeByte((int) p[3]);
                    out.writeInt((int) p[4]);
                }
                out.flush();
            } catch (Exception e) {
                System.err.println("[GraffitiMod] Failed to save graffiti data!");
                e.printStackTrace();
            }
        });

        // СИНХРОНИЗАЦИЯ ПРИ ВХОДЕ (Отправка всех существующих граффити новому игроку)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            server.execute(() -> {
                List<PaintPayload> syncData = new ArrayList<>();
                SERVER_CACHE.forEach((posL, sides) -> sides.forEach((side, grid) -> {
                    for (int u = 0; u < 16; u++) {
                        for (int v = 0; v < 16; v++) {
                            if (grid[u][v] != 0) {
                                syncData.add(new PaintPayload(BlockPos.fromLong(posL), side, u, v, grid[u][v], 1));
                            }
                        }
                    }
                }));

                // Если мир большой, пакет может быть тяжелым.
                // Fabric поддерживает большие пакеты, но если будут лаги, стоит разбивать на части.
                if (!syncData.isEmpty()) {
                    ServerPlayNetworking.send(handler.player, new SyncGraffitiPayload(syncData));
                }
            });
        });
    }
}