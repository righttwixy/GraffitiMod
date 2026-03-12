package by.righttwixys.graffiti.client.renderer;

import by.righttwixys.graffiti.GraffitiMod;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class PixelOutlineRenderer {

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        boolean hasCan = client.player.getMainHandStack().isOf(GraffitiMod.GRAFFITI_TOOL) ||
                client.player.getOffHandStack().isOf(GraffitiMod.GRAFFITI_TOOL);

        if (!hasCan) return;

        HitResult hit = client.crosshairTarget;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            drawSelection(context, blockHit);
        }
    }

    private static void drawSelection(WorldRenderContext context, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        Direction side = hit.getSide();
        Vec3d localHit = hit.getPos().subtract(Vec3d.of(pos));

        // Жесткая привязка к осям блока (теперь квадрат всегда идеальный)
        float uRaw = 0, vRaw = 0, depth = 0;
        switch (side.getAxis()) {
            case Y -> { uRaw = (float) localHit.x; vRaw = (float) localHit.z; depth = (float) localHit.y; }
            case Z -> { uRaw = (float) localHit.x; vRaw = (float) localHit.y; depth = (float) localHit.z; }
            case X -> { uRaw = (float) localHit.z; vRaw = (float) localHit.y; depth = (float) localHit.x; }
        }

        int u = Math.max(0, Math.min(15, (int)(uRaw * 16)));
        int v = Math.max(0, Math.min(15, (int)(vRaw * 16)));

        renderPerfectLines(context, pos, side, u, v, depth);
    }

    private static void renderPerfectLines(WorldRenderContext context, BlockPos pos, Direction side, int u, int v, float depth) {
        MatrixStack matrices = context.matrixStack();
        Vec3d cameraPos = context.camera().getPos();

        float time = (MinecraftClient.getInstance().world.getTime() + context.tickCounter().getTickDelta(true)) * 0.2f;

        matrices.push();
        matrices.translate(pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z);

        // Чуть-чуть отодвигаем обводку от блока по нормали
        Vec3d normal = Vec3d.of(side.getVector());
        matrices.translate(normal.x * 0.005, normal.y * 0.005, normal.z * 0.005);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();

        // Линии не сжимаются перспективой, поэтому всегда хорошо видны
        RenderSystem.lineWidth(3.0F); // Делает линию более жирной
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        float s = 1/16f;
        float x1 = u * s, x2 = x1 + s;
        float y1 = v * s, y2 = y1 + s;

        // Рисуем 4 линии квадрата (по кругу)
        drawLine(buffer, matrix, x1, y1, x2, y1, depth, side, time, 0); // Низ
        drawLine(buffer, matrix, x2, y1, x2, y2, depth, side, time, 1); // Право
        drawLine(buffer, matrix, x2, y2, x1, y2, depth, side, time, 2); // Верх
        drawLine(buffer, matrix, x1, y2, x1, y1, depth, side, time, 3); // Лево

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.disableDepthTest(); // Рисуем строго ПОВЕРХ граффити и блоков
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.lineWidth(1.0F); // Возвращаем стандартную толщину

        matrices.pop();
    }

    private static void drawLine(BufferBuilder b, Matrix4f m, float x1, float y1, float x2, float y2, float z, Direction side, float time, int offset) {
        // Переливающиеся цвета
        float mix = (float) Math.sin(time + offset) * 0.5f + 0.5f;
        int r = (int) (255 * mix);
        int g = (int) (180 * (1 - mix));
        int bl = (int) (50 * mix + 255 * (1 - mix));

        addVertex(b, m, x1, y1, z, side, r, g, bl);
        addVertex(b, m, x2, y2, z, side, r, g, bl);
    }

    private static void addVertex(BufferBuilder b, Matrix4f m, float x, float y, float z, Direction side, int r, int g, int bl) {
        switch (side.getAxis()) {
            case Y -> b.vertex(m, x, z, y).color(r, g, bl, 255);
            case Z -> b.vertex(m, x, y, z).color(r, g, bl, 255);
            case X -> b.vertex(m, z, y, x).color(r, g, bl, 255);
        }
    }
}