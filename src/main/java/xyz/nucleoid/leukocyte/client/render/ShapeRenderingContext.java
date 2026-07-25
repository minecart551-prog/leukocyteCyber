package xyz.nucleoid.leukocyte.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;

import java.awt.*;

public class ShapeRenderingContext {
    private final BufferBuilder quadBuffer = new BufferBuilder(2097152);
    private final BufferBuilder lineBuffer = new BufferBuilder(2097152);

    private VertexBuffer quadUploaded = null;
    private boolean quadEmpty = true;
    private VertexBuffer lineUploaded = null;
    private boolean lineEmpty = true;

    private double baseX;
    private double baseY;
    private double baseZ;

    private long quadCount;
    private long lineCount;

    public void reset(double camX, double camY, double camZ) {
        this.baseX = camX;
        this.baseY = camY;
        this.baseZ = camZ;
        this.quadCount = 0;
        this.lineCount = 0;
    }

    public void beginBatch() {
        quadBuffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        lineBuffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
    }

    public void drawFilledQuad(double x1, double y1, double z1,
                                double x2, double y2, double z2,
                                double x3, double y3, double z3,
                                double x4, double y4, double z4,
                                Color color, int alpha) {
        quadCount++;
        quadBuffer.vertex((float)(x1 - baseX), (float)(y1 - baseY), (float)(z1 - baseZ))
                .color(color.getRed(), color.getGreen(), color.getBlue(), alpha).next();
        quadBuffer.vertex((float)(x2 - baseX), (float)(y2 - baseY), (float)(z2 - baseZ))
                .color(color.getRed(), color.getGreen(), color.getBlue(), alpha).next();
        quadBuffer.vertex((float)(x3 - baseX), (float)(y3 - baseY), (float)(z3 - baseZ))
                .color(color.getRed(), color.getGreen(), color.getBlue(), alpha).next();
        quadBuffer.vertex((float)(x4 - baseX), (float)(y4 - baseY), (float)(z4 - baseZ))
                .color(color.getRed(), color.getGreen(), color.getBlue(), alpha).next();
    }

    public void drawLine(double x1, double y1, double z1,
                          double x2, double y2, double z2,
                          Color color, int alpha) {
        lineCount++;
        lineBuffer.vertex((float)(x1 - baseX), (float)(y1 - baseY), (float)(z1 - baseZ))
                .color(color.getRed(), color.getGreen(), color.getBlue(), alpha).next();
        lineBuffer.vertex((float)(x2 - baseX), (float)(y2 - baseY), (float)(z2 - baseZ))
                .color(color.getRed(), color.getGreen(), color.getBlue(), alpha).next();
    }

    private void ensureBuffers() {
        if (quadUploaded == null) {
            quadUploaded = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        }
        if (lineUploaded == null) {
            lineUploaded = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        }
    }

    public void endBatch() {
        ensureBuffers();

        BufferBuilder.BuiltBuffer quadBuilt = quadBuffer.end();
        quadEmpty = quadBuilt.isEmpty();
        if (!quadEmpty) {
            quadUploaded.bind();
            quadUploaded.upload(quadBuilt);
            VertexBuffer.unbind();
        } else {
            quadBuilt.release();
        }

        BufferBuilder.BuiltBuffer lineBuilt = lineBuffer.end();
        lineEmpty = lineBuilt.isEmpty();
        if (!lineEmpty) {
            lineUploaded.bind();
            lineUploaded.upload(lineBuilt);
            VertexBuffer.unbind();
        } else {
            lineBuilt.release();
        }
    }

    public void doDrawing(MatrixStack stack) {
        if (quadUploaded == null && lineUploaded == null) return;

        MatrixStack.Entry top = stack.peek();
        RenderSystem.depthMask(true);

        if (!lineEmpty && lineUploaded != null) {
            lineUploaded.bind();
            lineUploaded.draw(top.getPositionMatrix(), RenderSystem.getProjectionMatrix(), GameRenderer.getPositionColorProgram());
        }
        if (!quadEmpty && quadUploaded != null) {
            quadUploaded.bind();
            quadUploaded.draw(top.getPositionMatrix(), RenderSystem.getProjectionMatrix(), GameRenderer.getPositionColorProgram());
        }

        VertexBuffer.unbind();
        RenderSystem.depthMask(true);
    }

    public double getBaseX() { return baseX; }
    public double getBaseY() { return baseY; }
    public double getBaseZ() { return baseZ; }

    public void cleanup() {
        if (quadUploaded != null) {
            quadUploaded.close();
            quadUploaded = null;
        }
        if (lineUploaded != null) {
            lineUploaded.close();
            lineUploaded = null;
        }
    }
}
