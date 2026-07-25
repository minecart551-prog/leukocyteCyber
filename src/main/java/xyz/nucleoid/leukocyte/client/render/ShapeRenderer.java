package xyz.nucleoid.leukocyte.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import xyz.nucleoid.leukocyte.client.tool.ShapeToolState;
import xyz.nucleoid.leukocyte.client.util.FaceMerger;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Environment(EnvType.CLIENT)
public class ShapeRenderer {
    private static final ShapeRenderer INSTANCE = new ShapeRenderer();

    private final ShapeRenderingContext[] ctxPool = new ShapeRenderingContext[] {
        new ShapeRenderingContext(),
        new ShapeRenderingContext()
    };
    private int activeContextIndex = 0;
    private boolean contextReady = false;

    private static Frustum currentFrustum = null;

    private static final Executor BACKGROUND_EXECUTOR = Executors.newSingleThreadExecutor(
        r -> { Thread t = new Thread(r, "Leukocyte-Shape-Build"); t.setDaemon(true); return t; }
    );

    private volatile boolean needsRebuild = true;
    private volatile CompletableFuture<Void> buildingFuture = null;
    private static final int MAX_RENDER_DISTANCE = 64;

    private static final Color FILL_SELECTED = new Color(255, 255, 0);
    private static final int FILL_ALPHA = 45;
    private static final int SELECTED_ALPHA = 65;
    private static final int FILL_ALPHA_AUTHORITY = 35;

    private ShapeRenderer() {}

    public static ShapeRenderer getInstance() { return INSTANCE; }
    public void setFrustum(Frustum frustum) { currentFrustum = frustum; }
    public void markNeedsRebuild() { this.needsRebuild = true; }

    private boolean isVisibleInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (currentFrustum == null) return true;
        MinecraftClient mc = MinecraftClient.getInstance();
        double camX = mc.gameRenderer.getCamera().getPos().x;
        double camZ = mc.gameRenderer.getCamera().getPos().z;
        double closestX = Math.max(minX, Math.min(camX, maxX));
        double closestZ = Math.max(minZ, Math.min(camZ, maxZ));
        double distX = Math.abs(camX - closestX);
        double distZ = Math.abs(camZ - closestZ);
        if (distX > MAX_RENDER_DISTANCE || distZ > MAX_RENDER_DISTANCE) return false;
        return currentFrustum.isVisible(new Box(minX, minY, minZ, maxX, maxY, maxZ));
    }

    private void buildAsync(ShapeRenderingContext buildCtx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        ShapeToolState state = ShapeToolState.getInstance();
        double camX = mc.gameRenderer.getCamera().getPos().x;
        double camY = mc.gameRenderer.getCamera().getPos().y;
        double camZ = mc.gameRenderer.getCamera().getPos().z;

        buildCtx.reset(camX, camY, camZ);
        buildCtx.beginBatch();

        var currentDim = mc.world.getRegistryKey();
        List<ShapeToolState.ShapeEntry> visibleShapes = new java.util.ArrayList<>();

        if (state.getSelectedShape() != null) {
            var selected = state.getSelectedShape();
            if (selected.dimension().equals(currentDim)) {
                visibleShapes.add(selected);
            }
        }

        Map<String, Integer> authorityColorMap = new HashMap<>();
        int colorIdx = 0;
        for (ShapeToolState.ShapeEntry entry : visibleShapes) {
            if (!authorityColorMap.containsKey(entry.authority())) {
                authorityColorMap.put(entry.authority(), state.getAuthorityColor(colorIdx++));
            }
        }

        for (ShapeToolState.ShapeEntry entry : visibleShapes) {
            if (entry.min() == null || entry.max() == null) continue;

            int minX = Math.min(entry.min().getX(), entry.max().getX());
            int minY = Math.min(entry.min().getY(), entry.max().getY());
            int minZ = Math.min(entry.min().getZ(), entry.max().getZ());
            int maxX = Math.max(entry.min().getX(), entry.max().getX());
            int maxY = Math.max(entry.min().getY(), entry.max().getY());
            int maxZ = Math.max(entry.min().getZ(), entry.max().getZ());

            if (!isVisibleInFrustum(minX, minY, minZ, maxX, maxY, maxZ)) continue;

            boolean isSelected = entry == state.getSelectedShape();
            int alpha = isSelected ? SELECTED_ALPHA : FILL_ALPHA;
            Color color;

            if (state.getRenderMode() == ShapeToolState.RenderMode.AUTHORITY_COLORS) {
                int packed = authorityColorMap.getOrDefault(entry.authority(), 0xFFFFFFFF);
                color = new Color(packed, true);
                if (!isSelected) alpha = FILL_ALPHA_AUTHORITY;
            } else {
                int packed = state.getShapeColor(visibleShapes.indexOf(entry));
                color = new Color(packed, true);
            }

            for (FaceMerger.Face face : FaceMerger.extractAndMergeBoundaryFacesFromBoxes(entry.subBoxes())) {
                renderFaceAsFilledQuad(buildCtx, face, color, alpha);
            }

            if (isSelected) {
                renderWireframeBox(buildCtx, minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1,
                    new Color(255, 255, 255), 200);
            }
        }

        if (state.getFirstCorner() != null) {
            BlockPos c = state.getFirstCorner();
            renderWireframeBox(buildCtx, c.getX(), c.getY(), c.getZ(),
                c.getX() + 1, c.getY() + 1, c.getZ() + 1, FILL_SELECTED, 200);
            renderBoxAsFilledQuads(buildCtx, c.getX(), c.getY(), c.getZ(),
                c.getX() + 1, c.getY() + 1, c.getZ() + 1, FILL_SELECTED, SELECTED_ALPHA);
        }

        if (state.getFirstCorner() != null && state.getSecondCorner() != null) {
            BlockPos a = state.getFirstCorner();
            BlockPos b = state.getSecondCorner();
            int minX = Math.min(a.getX(), b.getX());
            int minY = Math.min(a.getY(), b.getY());
            int minZ = Math.min(a.getZ(), b.getZ());
            int maxX = Math.max(a.getX(), b.getX()) + 1;
            int maxY = Math.max(a.getY(), b.getY()) + 1;
            int maxZ = Math.max(a.getZ(), b.getZ()) + 1;
            renderBoxAsFilledQuads(buildCtx, minX, minY, minZ, maxX, maxY, maxZ, FILL_SELECTED, SELECTED_ALPHA);
            renderWireframeBox(buildCtx, minX, minY, minZ, maxX, maxY, maxZ, FILL_SELECTED, 200);
        }
    }

    private void renderFaceAsFilledQuad(ShapeRenderingContext ctx, FaceMerger.Face face, Color color, int alpha) {
        double x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4;
        if (face.plane == 0) {
            double px = face.planeValue;
            double y_min = face.u1, y_max = face.u2;
            double z_min = face.v1, z_max = face.v2;
            x1 = x2 = x3 = x4 = px;
            y1 = y2 = y_min; y3 = y4 = y_max;
            z1 = z4 = z_min; z2 = z3 = z_max;
        } else if (face.plane == 1) {
            double py = face.planeValue;
            double x_min = face.u1, x_max = face.u2;
            double z_min = face.v1, z_max = face.v2;
            y1 = y2 = y3 = y4 = py;
            x1 = x2 = x_min; x3 = x4 = x_max;
            z1 = z4 = z_min; z2 = z3 = z_max;
        } else {
            double pz = face.planeValue;
            double x_min = face.u1, x_max = face.u2;
            double y_min = face.v1, y_max = face.v2;
            z1 = z2 = z3 = z4 = pz;
            x1 = x2 = x_min; x3 = x4 = x_max;
            y1 = y4 = y_min; y2 = y3 = y_max;
        }
        ctx.drawFilledQuad(x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, color, alpha);
    }

    private void renderBoxAsFilledQuads(ShapeRenderingContext ctx,
                                         double minX, double minY, double minZ,
                                         double maxX, double maxY, double maxZ,
                                         Color color, int alpha) {
        boolean sameX = minX == maxX, sameY = minY == maxY, sameZ = minZ == maxZ;
        if (!sameY) {
            ctx.drawFilledQuad(minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, color, alpha);
            ctx.drawFilledQuad(minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, color, alpha);
        }
        if (!sameZ) {
            ctx.drawFilledQuad(minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, color, alpha);
            ctx.drawFilledQuad(minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, color, alpha);
        }
        if (!sameX) {
            ctx.drawFilledQuad(minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, color, alpha);
            ctx.drawFilledQuad(maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, color, alpha);
        }
    }

    private void renderWireframeBox(ShapeRenderingContext ctx,
                                     double minX, double minY, double minZ,
                                     double maxX, double maxY, double maxZ,
                                     Color color, int alpha) {
        ctx.drawLine(minX, minY, minZ, maxX, minY, minZ, color, alpha);
        ctx.drawLine(maxX, minY, minZ, maxX, minY, maxZ, color, alpha);
        ctx.drawLine(maxX, minY, maxZ, minX, minY, maxZ, color, alpha);
        ctx.drawLine(minX, minY, maxZ, minX, minY, minZ, color, alpha);

        ctx.drawLine(minX, maxY, minZ, maxX, maxY, minZ, color, alpha);
        ctx.drawLine(maxX, maxY, minZ, maxX, maxY, maxZ, color, alpha);
        ctx.drawLine(maxX, maxY, maxZ, minX, maxY, maxZ, color, alpha);
        ctx.drawLine(minX, maxY, maxZ, minX, maxY, minZ, color, alpha);

        ctx.drawLine(minX, minY, minZ, minX, maxY, minZ, color, alpha);
        ctx.drawLine(maxX, minY, minZ, maxX, maxY, minZ, color, alpha);
        ctx.drawLine(maxX, minY, maxZ, maxX, maxY, maxZ, color, alpha);
        ctx.drawLine(minX, minY, maxZ, minX, maxY, maxZ, color, alpha);
    }

    public void render(double camX, double camY, double camZ, MatrixStack matrices) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (!ShapeToolState.getInstance().isToolHeld()) return;

        if (needsRebuild && (buildingFuture == null || buildingFuture.isDone())) {
            needsRebuild = false;
            int buildIndex = (activeContextIndex + 1) % 2;
            ShapeRenderingContext buildCtx = ctxPool[buildIndex];
            buildingFuture = CompletableFuture.runAsync(() -> buildAsync(buildCtx), BACKGROUND_EXECUTOR)
                .thenRunAsync(() -> {
                    buildCtx.endBatch();
                    activeContextIndex = buildIndex;
                    contextReady = true;
                }, runnable -> {
                    if (RenderSystem.isOnRenderThread()) runnable.run();
                    else RenderSystem.recordRenderCall(runnable::run);
                });
        }

        ShapeRenderingContext activeCtx = ctxPool[activeContextIndex];
        if (!contextReady) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        matrices.push();
        matrices.translate(activeCtx.getBaseX() - camX, activeCtx.getBaseY() - camY, activeCtx.getBaseZ() - camZ);
        activeCtx.doDrawing(matrices);
        matrices.pop();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    public void cleanup() {
        for (ShapeRenderingContext c : ctxPool) c.cleanup();
    }
}
