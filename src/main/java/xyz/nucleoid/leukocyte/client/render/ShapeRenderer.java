package xyz.nucleoid.leukocyte.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import xyz.nucleoid.leukocyte.client.tool.BuildAreaToolState;
import xyz.nucleoid.leukocyte.client.tool.ShapeToolState;
import xyz.nucleoid.leukocyte.client.util.FaceMerger;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class ShapeRenderer {
    private static final ShapeRenderer INSTANCE = new ShapeRenderer();

    private final ShapeRenderingContext ctx = new ShapeRenderingContext();

    private static Frustum currentFrustum = null;

    private static final int MAX_RENDER_DISTANCE = 64;

    private static final Color FILL_SELECTED = new Color(255, 255, 0);
    private static final int SELECTED_ALPHA = 65;
    private static final int FILL_ALPHA = 45;
    private static final int FILL_ALPHA_AUTHORITY = 35;

    private ShapeRenderer() {}

    public static ShapeRenderer getInstance() { return INSTANCE; }
    public void setFrustum(Frustum frustum) { currentFrustum = frustum; }
    public void markNeedsRebuild() {}

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

    public void render(double camX, double camY, double camZ, MatrixStack matrices) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        boolean holdingShapeTool = xyz.nucleoid.leukocyte.item.ToolItems.isShapeTool(mc.player.getMainHandStack());
        boolean holdingBuildTool = xyz.nucleoid.leukocyte.item.ToolItems.isBuildAreaTool(mc.player.getMainHandStack());
        if (!holdingShapeTool && !holdingBuildTool) return;

        var currentDim = mc.world.getRegistryKey();

        ctx.reset(camX, camY, camZ);
        ctx.beginBatch();

        if (holdingBuildTool) {
            renderBuildTool(ctx, mc, currentDim);
        } else {
            renderShapeTool(ctx, mc, currentDim);
        }

        ctx.endBatch();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        matrices.push();
        matrices.translate(ctx.getBaseX() - camX, ctx.getBaseY() - camY, ctx.getBaseZ() - camZ);
        ctx.doDrawing(matrices);
        matrices.pop();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private void renderBuildTool(ShapeRenderingContext ctx, MinecraftClient mc, net.minecraft.registry.RegistryKey<net.minecraft.world.World> currentDim) {
        BuildAreaToolState state = BuildAreaToolState.getInstance();

        var selected = state.getSelectedArea();
        if (selected != null && selected.dimension() != null && selected.dimension().equals(currentDim)) {
            BlockPos selMin = selected.min();
            BlockPos selMax = selected.max();

            if (isVisibleInFrustum(selMin.getX(), selMin.getY(), selMin.getZ(), selMax.getX(), selMax.getY(), selMax.getZ())) {
                for (FaceMerger.Face face : FaceMerger.extractAndMergeBoundaryFacesFromBoxes(selected.subBoxes())) {
                    renderFaceAsFilledQuad(ctx, face, new Color(0x55FF55), SELECTED_ALPHA);
                }
                renderWireframeBox(ctx, selMin.getX(), selMin.getY(), selMin.getZ(),
                    selMax.getX() + 1, selMax.getY() + 1, selMax.getZ() + 1,
                    new Color(255, 255, 255), 200);
            }
        }

        if (state.getFirstCorner() != null) {
            BlockPos a = state.getFirstCorner();
            BlockPos preview = state.getPreviewPos();

            if (preview != null) {
                int bMinX = Math.min(a.getX(), preview.getX());
                int bMinY = Math.min(a.getY(), preview.getY());
                int bMinZ = Math.min(a.getZ(), preview.getZ());
                int bMaxX = Math.max(a.getX(), preview.getX()) + 1;
                int bMaxY = Math.max(a.getY(), preview.getY()) + 1;
                int bMaxZ = Math.max(a.getZ(), preview.getZ()) + 1;
                renderBoxAsFilledQuads(ctx, bMinX, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ, FILL_SELECTED, SELECTED_ALPHA);
                renderWireframeBox(ctx, bMinX, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ, FILL_SELECTED, 200);
            } else {
                renderWireframeBox(ctx, a.getX(), a.getY(), a.getZ(),
                    a.getX() + 1, a.getY() + 1, a.getZ() + 1, FILL_SELECTED, 200);
                renderBoxAsFilledQuads(ctx, a.getX(), a.getY(), a.getZ(),
                    a.getX() + 1, a.getY() + 1, a.getZ() + 1, FILL_SELECTED, SELECTED_ALPHA);
            }
        } else if (state.getPreviewPos() != null) {
            BlockPos preview = state.getPreviewPos();
            renderWireframeBox(ctx, preview.getX(), preview.getY(), preview.getZ(),
                preview.getX() + 1, preview.getY() + 1, preview.getZ() + 1, FILL_SELECTED, 200);
            renderBoxAsFilledQuads(ctx, preview.getX(), preview.getY(), preview.getZ(),
                preview.getX() + 1, preview.getY() + 1, preview.getZ() + 1, FILL_SELECTED, SELECTED_ALPHA);
        }
    }

    private void renderShapeTool(ShapeRenderingContext ctx, MinecraftClient mc, net.minecraft.registry.RegistryKey<net.minecraft.world.World> currentDim) {
        ShapeToolState state = ShapeToolState.getInstance();

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
                renderFaceAsFilledQuad(ctx, face, color, alpha);
            }

            if (isSelected) {
                renderWireframeBox(ctx, minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1,
                    new Color(255, 255, 255), 200);
            }
        }

        if (state.getFirstCorner() != null) {
            BlockPos a = state.getFirstCorner();
            BlockPos preview = state.getPreviewPos();

            if (preview != null) {
                int bMinX = Math.min(a.getX(), preview.getX());
                int bMinY = Math.min(a.getY(), preview.getY());
                int bMinZ = Math.min(a.getZ(), preview.getZ());
                int bMaxX = Math.max(a.getX(), preview.getX()) + 1;
                int bMaxY = Math.max(a.getY(), preview.getY()) + 1;
                int bMaxZ = Math.max(a.getZ(), preview.getZ()) + 1;
                renderBoxAsFilledQuads(ctx, bMinX, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ, FILL_SELECTED, SELECTED_ALPHA);
                renderWireframeBox(ctx, bMinX, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ, FILL_SELECTED, 200);
            } else {
                renderWireframeBox(ctx, a.getX(), a.getY(), a.getZ(),
                    a.getX() + 1, a.getY() + 1, a.getZ() + 1, FILL_SELECTED, 200);
                renderBoxAsFilledQuads(ctx, a.getX(), a.getY(), a.getZ(),
                    a.getX() + 1, a.getY() + 1, a.getZ() + 1, FILL_SELECTED, SELECTED_ALPHA);
            }
        } else if (state.getPreviewPos() != null) {
            BlockPos preview = state.getPreviewPos();
            renderWireframeBox(ctx, preview.getX(), preview.getY(), preview.getZ(),
                preview.getX() + 1, preview.getY() + 1, preview.getZ() + 1, FILL_SELECTED, 200);
            renderBoxAsFilledQuads(ctx, preview.getX(), preview.getY(), preview.getZ(),
                preview.getX() + 1, preview.getY() + 1, preview.getZ() + 1, FILL_SELECTED, SELECTED_ALPHA);
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

    public void cleanup() {
        ctx.cleanup();
    }
}
