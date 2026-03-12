package by.righttwixys.graffiti.client.renderer;

import by.righttwixys.graffiti.config.GraffitiConfig;
import by.righttwixys.graffiti.network.PaintPayload;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GraffitiRenderer {

    public static final List<PaintPayload> PIXELS = Collections.synchronizedList(new ArrayList<>());
    // Используем ConcurrentHashMap для предотвращения ошибок при работе из разных потоков
    public static final Map<Long, Map<Direction, int[][]>> GRAFFITI_CACHE = new ConcurrentHashMap<>();

    private static String lastWorldName = "";
    private static boolean needsSave = false;
    private static final Identifier WHITE_TEXTURE = Identifier.of("graffiti", "textures/misc/white.png");

    public static void init() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (!GraffitiConfig.get().enabled) return;

            var world = context.world();
            if (world == null) return;

            checkAndLoadWorldData();

            // Перенос пакетов из очереди в кэш
            synchronized (PIXELS) {
                if (!PIXELS.isEmpty()) {
                    for (PaintPayload p : PIXELS) {
                        GRAFFITI_CACHE.computeIfAbsent(p.pos().asLong(), k -> new EnumMap<>(Direction.class))
                                .computeIfAbsent(p.side(), k -> new int[16][16])[p.u()][p.v()] = p.color();
                    }
                    PIXELS.clear();
                    needsSave = true;
                }
            }

            if (GRAFFITI_CACHE.isEmpty()) return;

            MatrixStack matrices = context.matrixStack();
            VertexConsumer buffer = context.consumers().getBuffer(RenderLayer.getEntityTranslucent(WHITE_TEXTURE));
            Vec3d cameraPos = context.camera().getPos();
            Frustum frustum = context.frustum();

            double maxDistSq = Math.pow(GraffitiConfig.get().renderDistance, 2);

            matrices.push();
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            Iterator<Map.Entry<Long, Map<Direction, int[][]>>> it = GRAFFITI_CACHE.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                BlockPos pos = BlockPos.fromLong(entry.getKey());

                if (pos.getSquaredDistance(cameraPos) > maxDistSq) continue;

                if (GraffitiConfig.get().useCulling) {
                    if (!frustum.isVisible(new Box(pos))) continue;
                }

                // Очистка если блок исчез
                if (world.getBlockState(pos).isAir()) {
                    it.remove();
                    needsSave = true;
                    continue;
                }

                for (var sideEntry : entry.getValue().entrySet()) {
                    renderOptimizedFace(matrices, buffer, pos, sideEntry.getKey(), sideEntry.getValue(), world);
                }
            }

            matrices.pop();

            if (needsSave) {
                save();
                needsSave = false;
            }
        });
    }

    private static void renderOptimizedFace(MatrixStack matrices, VertexConsumer buffer, BlockPos pos, Direction side, int[][] grid, net.minecraft.world.World world) {
        boolean[][] visited = new boolean[16][16];
        float[][] depths = new float[16][16];

        for (int v = 0; v < 16; v++) {
            for (int u = 0; u < 16; u++) {
                if (grid[u][v] != 0) {
                    depths[u][v] = getPixelDepth(world, pos, side, u, v);
                }
            }
        }

        int light = WorldRenderer.getLightmapCoordinates(world, pos.offset(side));
        matrices.push();
        matrices.translate(pos.getX(), pos.getY(), pos.getZ());
        Matrix4f model = matrices.peek().getPositionMatrix();

        // Greedy Meshing алгоритм
        for (int v = 0; v < 16; v++) {
            for (int u = 0; u < 16; u++) {
                int color = grid[u][v];
                if (color != 0 && !visited[u][v]) {
                    float currentDepth = depths[u][v];

                    int w = 1;
                    while (u + w < 16 && grid[u + w][v] == color && !visited[u + w][v] && Math.abs(depths[u + w][v] - currentDepth) < 0.001f) w++;

                    int h = 1;
                    while (v + h < 16) {
                        boolean canExpand = true;
                        for (int k = 0; k < w; k++) {
                            if (grid[u + k][v + h] != color || visited[u + k][v + h] || Math.abs(depths[u + k][v + h] - currentDepth) > 0.001f) {
                                canExpand = false;
                                break;
                            }
                        }
                        if (!canExpand) break;
                        h++;
                    }

                    for (int dy = 0; dy < h; dy++) {
                        for (int dx = 0; dx < w; dx++) visited[u + dx][v + dy] = true;
                    }

                    drawQuad(model, buffer, side, u, v, w, h, color, light, currentDepth);
                }
            }
        }
        matrices.pop();
    }

    private static void drawQuad(Matrix4f m, VertexConsumer b, Direction side, int u, int v, int w, int h, int color, int light, float offset) {
        float s = 1/16f;
        float bias = (side.getDirection() == Direction.AxisDirection.POSITIVE) ? 0.005f : -0.005f;
        float z = offset + bias;

        int alpha = (color >> 24) & 0xFF;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        float u1 = u * s, u2 = (u + w) * s;
        float v1 = v * s, v2 = (v + h) * s;

        switch (side) {
            case UP ->    quad(m, b, side, u1, z, v1, u1, z, v2, u2, z, v2, u2, z, v1, red, green, blue, alpha, light);
            case DOWN ->  quad(m, b, side, u1, z, v1, u2, z, v1, u2, z, v2, u1, z, v2, red, green, blue, alpha, light);
            case NORTH -> quad(m, b, side, u1, v1, z, u1, v2, z, u2, v2, z, u2, v1, z, red, green, blue, alpha, light);
            case SOUTH -> quad(m, b, side, u1, v1, z, u2, v1, z, u2, v2, z, u1, v2, z, red, green, blue, alpha, light);
            case WEST ->  quad(m, b, side, z, v1, u1, z, v1, u2, z, v2, u2, z, v2, u1, red, green, blue, alpha, light);
            case EAST ->  quad(m, b, side, z, v1, u1, z, v2, u1, z, v2, u2, z, v1, u2, red, green, blue, alpha, light);
        }
    }

    private static float getPixelDepth(net.minecraft.world.BlockView world, BlockPos pos, Direction side, int u, int v) {
        net.minecraft.block.BlockState state = world.getBlockState(pos);
        net.minecraft.util.shape.VoxelShape shape = state.getOutlineShape(world, pos);
        if (shape.isEmpty()) return (side.getDirection() == Direction.AxisDirection.POSITIVE) ? 1.0f : 0.0f;

        // Лучевое сканирование для поддержки ступенек
        float cx = (u + 0.5f) / 16f;
        float cy = (v + 0.5f) / 16f;
        double rx = pos.getX(), ry = pos.getY(), rz = pos.getZ();

        switch (side) {
            case UP, DOWN -> { rx += cx; rz += cy; }
            case NORTH, SOUTH -> { rx += cx; ry += cy; }
            case WEST, EAST -> { rz += cx; ry += cy; }
        }

        double dx = side.getOffsetX(), dy = side.getOffsetY(), dz = side.getOffsetZ();
        Vec3d start = new Vec3d(rx + dx, ry + dy, rz + dz);
        Vec3d end = new Vec3d(rx - dx, ry - dy, rz - dz);

        net.minecraft.util.hit.BlockHitResult hit = shape.raycast(start, end, pos);
        if (hit != null) {
            Vec3d hitPos = hit.getPos();
            return (float) (switch (side.getAxis()) {
                case X -> hitPos.x - pos.getX();
                case Y -> hitPos.y - pos.getY();
                case Z -> hitPos.z - pos.getZ();
            });
        }
        return (float) (side.getDirection() == Direction.AxisDirection.POSITIVE ? shape.getMax(side.getAxis()) : shape.getMin(side.getAxis()));
    }

    private static void quad(Matrix4f m, VertexConsumer b, Direction side, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, int r, int g, int bl, int a, int light) {
        v(m, b, side, x1, y1, z1, r, g, bl, a, light);
        v(m, b, side, x2, y2, z2, r, g, bl, a, light);
        v(m, b, side, x3, y3, z3, r, g, bl, a, light);
        v(m, b, side, x4, y4, z4, r, g, bl, a, light);
    }

    private static void v(Matrix4f m, VertexConsumer b, Direction side, float x, float y, float z, int r, int g, int bl, int a, int light) {
        b.vertex(m, x, y, z).color(r, g, bl, a).texture(0f, 0f).overlay(OverlayTexture.DEFAULT_UV).light(light)
                .normal(side.getOffsetX(), side.getOffsetY(), side.getOffsetZ());
    }

    public static void save() {
        if (lastWorldName.isEmpty()) return;
        File file = getSaveFile();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(file))))) {
            List<long[]> data = new ArrayList<>();
            GRAFFITI_CACHE.forEach((pos, sides) -> sides.forEach((side, grid) -> {
                for (int u = 0; u < 16; u++) {
                    for (int v = 0; v < 16; v++) {
                        if (grid[u][v] != 0) data.add(new long[]{pos, side.getId(), u, v, grid[u][v]});
                    }
                }
            }));
            out.writeInt(data.size());
            for (long[] p : data) {
                out.writeLong(p[0]); // pos
                out.writeByte((int)p[1]); // side
                out.writeByte((int)p[2]); // u
                out.writeByte((int)p[3]); // v
                out.writeInt((int)p[4]); // color
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void load() {
        File file = getSaveFile();
        GRAFFITI_CACHE.clear();
        if (!file.exists()) return;
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(file))))) {
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                long pos = in.readLong();
                int sideId = in.readByte();
                int u = in.readUnsignedByte();
                int v = in.readUnsignedByte();
                int color = in.readInt();

                if (u < 16 && v < 16 && sideId >= 0 && sideId < 6) {
                    GRAFFITI_CACHE.computeIfAbsent(pos, k -> new EnumMap<>(Direction.class))
                            .computeIfAbsent(Direction.byId(sideId), k -> new int[16][16])[u][v] = color;
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static void checkAndLoadWorldData() {
        var client = MinecraftClient.getInstance();
        if (client.world == null) return;
        String current = client.isInSingleplayer() ? client.getServer().getSaveProperties().getLevelName() : "mp_server";
        if (!current.equals(lastWorldName)) {
            lastWorldName = current;
            load();
        }
    }

    private static File getSaveFile() {
        File dir = new File(MinecraftClient.getInstance().runDirectory, "graffiti_data/" + lastWorldName);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "cache.bin");
    }

    public static void addPixelToCache(PaintPayload p) {
        GRAFFITI_CACHE.computeIfAbsent(p.pos().asLong(), k -> new EnumMap<>(Direction.class))
                .computeIfAbsent(p.side(), k -> new int[16][16])[p.u()][p.v()] = p.color();
        needsSave = true;
    }
}