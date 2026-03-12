package by.righttwixys.graffiti.item;

import by.righttwixys.graffiti.client.gui.GraffitiHUD;
import by.righttwixys.graffiti.client.gui.GraffitiScreen;
import by.righttwixys.graffiti.client.renderer.GraffitiRenderer;
import by.righttwixys.graffiti.network.ColorPayload;
import by.righttwixys.graffiti.network.PaintPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class GraffitiItem extends Item {
    public static int brushSize = 1;

    public GraffitiItem(Settings s) { super(s); }

    // ====== РАБОТА С NBT (Data Components 1.21+) ======

    public static int getColor(ItemStack stack) {
        NbtComponent nbtComponent = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (nbtComponent != null) {
            NbtCompound nbt = nbtComponent.copyNbt();
            if (nbt.contains("GraffitiColor")) {
                return nbt.getInt("GraffitiColor");
            }
        }
        return 0xFFFFFFFF; // Белый по умолчанию
    }

    public static void setColor(ItemStack stack, int color) {
        NbtCompound nbt = new NbtCompound();
        NbtComponent existing = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (existing != null) {
            nbt = existing.copyNbt();
        }
        nbt.putInt("GraffitiColor", color);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    // ====== ОПИСАНИЕ ПРЕДМЕТА ======

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        int color = getColor(stack);
        String hexString = String.format("#%06X", color & 0xFFFFFF);
        tooltip.add(Text.translatable("tooltip.graffiti.color_hex")
                .append(": ")
                .append(Text.literal(hexString).formatted(Formatting.GRAY)));
        super.appendTooltip(stack, context, tooltip, type);
    }

    // ====== ИСПОЛЬЗОВАНИЕ (ПКМ) ======

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            MinecraftClient.getInstance().setScreen(new GraffitiScreen(stack));
        }
        return TypedActionResult.success(stack);
    }

    // ====== ЛОГИКА РИСОВАНИЯ (ЛКМ / handleAttack) ======

    public static void handleAttack(BlockHitResult hit) {
        var client = MinecraftClient.getInstance();
        if (client.player != null) {
            ItemStack stack = client.player.getMainHandStack();
            int itemColor = getColor(stack);

            BlockPos pos = hit.getBlockPos();
            Direction side = hit.getSide();
            Vec3d r = hit.getPos().subtract(Vec3d.of(pos));

            int u = Math.min(15, Math.max(0, getCoord(r, side, true)));
            int v = Math.min(15, Math.max(0, getCoord(r, side, false)));

            int toolIndex = GraffitiHUD.selectedIndex;

            switch (toolIndex) {
                case 0 -> { // Карандаш
                    draw(pos, side, u, v, itemColor);
                    client.world.playSound(client.player, pos, net.minecraft.sound.SoundEvents.BLOCK_AZALEA_LEAVES_BREAK, net.minecraft.sound.SoundCategory.PLAYERS, 0.5f, 1.5f);
                }
                case 1 -> { // Ластик
                    draw(pos, side, u, v, 0); // Отправляем 0 для стирания
                    client.world.playSound(client.player, pos, net.minecraft.sound.SoundEvents.BLOCK_AZALEA_LEAVES_BREAK, net.minecraft.sound.SoundCategory.PLAYERS, 0.4f, 0.8f);
                }
                case 2 -> { // Заливка
                    floodFill(pos, side, u, v, itemColor);
                    client.world.playSound(client.player, pos, net.minecraft.sound.SoundEvents.BLOCK_AZALEA_LEAVES_PLACE, net.minecraft.sound.SoundCategory.PLAYERS, 0.7f, 1.2f);
                }
                case 3 -> { // Пипетка
                    pickColor(pos, side, u, v, stack);
                    client.world.playSound(client.player, pos, net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), net.minecraft.sound.SoundCategory.PLAYERS, 0.8f, 1.0f);
                }
            }
        }
    }

    private static void pickColor(BlockPos pos, Direction side, int u, int v, ItemStack stack) {
        int newColor = getPixelAt(pos, side, u, v);

        // Если на граффити пусто — берем цвет самого блока
        if (newColor == 0) {
            var client = MinecraftClient.getInstance();
            int blockColor = client.getBlockColors().getColor(client.world.getBlockState(pos), client.world, pos, 0);
            if (blockColor == -1) {
                blockColor = client.world.getBlockState(pos).getMapColor(client.world, pos).color;
            }
            newColor = 0xFF000000 | blockColor;
        }

        if (newColor != 0) {
            setColor(stack, newColor);
            // СИНХРОНИЗАЦИЯ: Чтобы сервер тоже узнал о новом цвете в баллончике
            ClientPlayNetworking.send(new ColorPayload(newColor));
        }
    }

    private static void draw(BlockPos pos, Direction side, int uC, int vC, int color) {
        int rad = brushSize - 1;
        for (int x = -rad; x <= rad; x++) {
            for (int y = -rad; y <= rad; y++) {
                int u = uC + x, v = vC + y;
                if (u >= 0 && u < 16 && v >= 0 && v < 16) {
                    setPixel(pos, side, u, v, color);
                }
            }
        }
    }

    private static void floodFill(BlockPos pos, Direction side, int startU, int startV, int targetColor) {
        int startColor = getPixelAt(pos, side, startU, startV);
        if (startColor == targetColor) return;

        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{startU, startV});
        boolean[][] visited = new boolean[16][16];

        while (!queue.isEmpty()) {
            int[] curr = queue.poll();
            int u = curr[0], v = curr[1];
            if (u < 0 || u >= 16 || v < 0 || v >= 16 || visited[u][v]) continue;
            if (getPixelAt(pos, side, u, v) != startColor) continue;

            visited[u][v] = true;
            setPixel(pos, side, u, v, targetColor);

            queue.add(new int[]{u + 1, v});
            queue.add(new int[]{u - 1, v});
            queue.add(new int[]{u, v + 1});
            queue.add(new int[]{u, v - 1});
        }
    }

    private static int getPixelAt(BlockPos pos, Direction side, int u, int v) {
        // Проверяем сначала кэш (там все текущие граффити)
        var sides = GraffitiRenderer.GRAFFITI_CACHE.get(pos.asLong());
        if (sides != null) {
            int[][] grid = sides.get(side);
            if (grid != null) return grid[u][v];
        }
        // Затем проверяем временную очередь PIXELS
        synchronized (GraffitiRenderer.PIXELS) {
            for (PaintPayload p : GraffitiRenderer.PIXELS) {
                if (p.pos().equals(pos) && p.side() == side && p.u() == u && p.v() == v) return p.color();
            }
        }
        return 0;
    }

    private static void setPixel(BlockPos pos, Direction side, int u, int v, int color) {
        // Создаем пакет
        PaintPayload p = new PaintPayload(pos, side, u, v, color, 1);

        // Обновляем локально для рендера
        GraffitiRenderer.addPixelToCache(p);

        // Отправляем на сервер (если цвет 0 — сервер удалит пиксель у себя)
        ClientPlayNetworking.send(p);
    }

    private static int getCoord(Vec3d r, Direction s, boolean isU) {
        if (isU) {
            return (int) (switch (s) {
                case UP, DOWN, NORTH, SOUTH -> r.x;
                case WEST, EAST -> r.z;
            } * 16);
        } else {
            return (int) (switch (s) {
                case UP, DOWN -> r.z;
                default -> r.y;
            } * 16);
        }
    }
}