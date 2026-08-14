package xyz.nucleoid.leukocyte.client.tool;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import xyz.nucleoid.leukocyte.client.network.ClientPacketHandler;
import xyz.nucleoid.leukocyte.client.render.ShapeRenderer;

@Environment(EnvType.CLIENT)
public final class ShapeToolHandler {
    private static long lastLeftClickTime = 0;
    private static long lastRightClickTime = 0;
    private static final long CLICK_COOLDOWN = 150;
    private static boolean lastLeftClickPressed = false;
    private static boolean lastRightClickPressed = false;
    private static boolean lastMiddleClickPressed = false;
    private static boolean toolEquipped = false;

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(ShapeToolHandler::onClientTick);
        WorldRenderEvents.LAST.register(ShapeToolHandler::onWorldRenderLast);
    }

    private static boolean isHoldingTool(PlayerEntity player) {
        if (player == null) return false;
        return xyz.nucleoid.leukocyte.item.ToolItems.isShapeTool(player.getMainHandStack());
    }

    private static void onClientTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        boolean holdingTool = isHoldingTool(mc.player);
        ShapeToolState state = ShapeToolState.getInstance();

        if (holdingTool && !toolEquipped) {
            toolEquipped = true;
            state.setToolHeld(true);
            state.setAuthorityKeys(new java.util.ArrayList<>());
            ClientPacketHandler.requestShapeToolData();
        } else if (!holdingTool && toolEquipped) {
            toolEquipped = false;
            state.setToolHeld(false);
            state.fullReset();
            lastLeftClickPressed = false;
            lastRightClickPressed = false;
            lastMiddleClickPressed = false;
        }

        if (!holdingTool) return;

        boolean middleClickPressed = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        if (middleClickPressed && !lastMiddleClickPressed) {
            lastMiddleClickPressed = true;
            mc.setScreen(new ToolMenuScreen());
            return;
        }
        lastMiddleClickPressed = middleClickPressed;

        if (state.isPlacingCorners()) {
            BlockPos cursor = getTargetPos(mc);
            BlockPos prev = state.getPreviewPos();
            if (cursor != null) {
                if (!cursor.equals(prev)) {
                    state.setPreviewPos(cursor);
                    ShapeRenderer.getInstance().markNeedsRebuild();
                }
            } else if (prev != null) {
                state.setPreviewPos(null);
                ShapeRenderer.getInstance().markNeedsRebuild();
            }
        } else {
            if (state.getPreviewPos() != null) {
                state.setPreviewPos(null);
                ShapeRenderer.getInstance().markNeedsRebuild();
            }
        }

        ShapeToolState.Mode mode = state.getMode();
        updateHUD(mc, state, mode);

        boolean leftClickPressed = mc.options.attackKey.isPressed();
        if (leftClickPressed && !lastLeftClickPressed) {
            long now = System.currentTimeMillis();
            if (now - lastLeftClickTime > CLICK_COOLDOWN) {
                lastLeftClickTime = now;
                handleLeftClick(mc, state, mode);
            }
        }
        lastLeftClickPressed = leftClickPressed;

        boolean rightClickPressed = mc.options.useKey.isPressed();
        if (rightClickPressed && !lastRightClickPressed) {
            long now = System.currentTimeMillis();
            if (now - lastRightClickTime > CLICK_COOLDOWN) {
                lastRightClickTime = now;
                handleRightClick(mc, state, mode);
            }
        }
        lastRightClickPressed = rightClickPressed;
    }

    private static void updateHUD(MinecraftClient mc, ShapeToolState state, ShapeToolState.Mode mode) {
        if (mc.player == null) return;

        String auth = state.getSelectedAuthority();
        if (auth == null) auth = "None";

        String shapeInfo = "";
        var shape = state.getSelectedShape();
        if (shape != null) {
            shapeInfo = " | Shape: " + shape.name();
        }

        String modeInfo = switch (mode) {
            case IDLE -> "LClick: Create new | Middle: Menu";
            case SELECTED -> "LClick: Add box | RClick: Subtract | Middle: Menu";
            case CREATE_CORNER_1 -> "Left-click first corner";
            case CREATE_CORNER_2 -> "Left-click second corner to create";
            case ADD_CORNER_1 -> "Left-click first corner of box to add";
            case ADD_CORNER_2 -> "Left-click second corner to add";
            case SUB_CORNER_1 -> "Right-click first corner to subtract";
            case SUB_CORNER_2 -> "Right-click second corner to subtract";
        };

        mc.player.sendMessage(Text.of("§e§l[Tool] §r§7Auth: §f" + auth + shapeInfo + " §7| " + modeInfo), true);
    }

    private static BlockPos getTargetPos(MinecraftClient mc) {
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return null;
        return ((BlockHitResult) mc.crosshairTarget).getBlockPos();
    }

    private static void handleLeftClick(MinecraftClient mc, ShapeToolState state, ShapeToolState.Mode mode) {
        if (mc.player == null || mc.world == null) return;

        switch (mode) {
            case IDLE -> {
                BlockPos pos = getTargetPos(mc);
                if (pos != null) {
                    state.setFirstCorner(pos);
                    state.setMode(ShapeToolState.Mode.CREATE_CORNER_2);
                    ShapeRenderer.getInstance().markNeedsRebuild();
                }
            }
            case SELECTED -> {
                BlockPos pos = getTargetPos(mc);
                if (pos != null) {
                    state.setFirstCorner(pos);
                    state.setMode(ShapeToolState.Mode.ADD_CORNER_2);
                    ShapeRenderer.getInstance().markNeedsRebuild();
                }
            }
            case CREATE_CORNER_2 -> {
                BlockPos pos = getTargetPos(mc);
                if (pos != null && state.getFirstCorner() != null) {
                    state.setSecondCorner(pos);
                    createShapeFromCorners(mc, state);
                    state.setMode(ShapeToolState.Mode.IDLE);
                    state.clearCorners();
                    ShapeRenderer.getInstance().markNeedsRebuild();
                }
            }
            case ADD_CORNER_2 -> {
                BlockPos pos = getTargetPos(mc);
                if (pos != null && state.getFirstCorner() != null) {
                    state.setSecondCorner(pos);
                    addBoxToShape(mc, state);
                    state.clearCorners();
                    state.setMode(ShapeToolState.Mode.SELECTED);
                    ShapeRenderer.getInstance().markNeedsRebuild();
                }
            }
            default -> {}
        }
    }

    private static void handleRightClick(MinecraftClient mc, ShapeToolState state, ShapeToolState.Mode mode) {
        if (mc.player == null || mc.world == null) return;

        switch (mode) {
            case SELECTED -> {
                BlockPos pos = getTargetPos(mc);
                if (pos != null) {
                    state.setFirstCorner(pos);
                    state.setMode(ShapeToolState.Mode.SUB_CORNER_2);
                    ShapeRenderer.getInstance().markNeedsRebuild();
                }
            }
            case SUB_CORNER_2 -> {
                BlockPos pos = getTargetPos(mc);
                if (pos != null && state.getFirstCorner() != null) {
                    state.setSecondCorner(pos);
                    subtractBoxFromShape(mc, state);
                    state.clearCorners();
                    state.setMode(ShapeToolState.Mode.SELECTED);
                    ShapeRenderer.getInstance().markNeedsRebuild();
                }
            }
            default -> {
                state.reset();
                ShapeRenderer.getInstance().markNeedsRebuild();
            }
        }
    }

    private static void createShapeFromCorners(MinecraftClient mc, ShapeToolState state) {
        if (mc.world == null) return;
        String auth = state.getSelectedAuthority();
        if (auth == null) return;

        BlockPos a = state.getFirstCorner();
        BlockPos b = state.getSecondCorner();
        if (a == null || b == null) return;

        BlockPos min = new BlockPos(
            Math.min(a.getX(), b.getX()),
            Math.min(a.getY(), b.getY()),
            Math.min(a.getZ(), b.getZ())
        );
        BlockPos max = new BlockPos(
            Math.max(a.getX(), b.getX()),
            Math.max(a.getY(), b.getY()),
            Math.max(a.getZ(), b.getZ())
        );

        String dimId = mc.world.getRegistryKey().getValue().toString();
        ClientPacketHandler.createBoxShape(auth, dimId, min, max);
    }

    private static void addBoxToShape(MinecraftClient mc, ShapeToolState state) {
        if (mc.world == null) return;
        var shape = state.getSelectedShape();
        if (shape == null) return;

        BlockPos a = state.getFirstCorner();
        BlockPos b = state.getSecondCorner();
        if (a == null || b == null) return;

        BlockPos min = new BlockPos(
            Math.min(a.getX(), b.getX()),
            Math.min(a.getY(), b.getY()),
            Math.min(a.getZ(), b.getZ())
        );
        BlockPos max = new BlockPos(
            Math.max(a.getX(), b.getX()),
            Math.max(a.getY(), b.getY()),
            Math.max(a.getZ(), b.getZ())
        );

        String dimId = mc.world.getRegistryKey().getValue().toString();
        ClientPacketHandler.addBoxToShape(shape.authority(), shape.name(), dimId, min, max);
    }

    private static void subtractBoxFromShape(MinecraftClient mc, ShapeToolState state) {
        if (mc.world == null) return;
        var shape = state.getSelectedShape();
        if (shape == null) return;

        BlockPos a = state.getFirstCorner();
        BlockPos b = state.getSecondCorner();
        if (a == null || b == null) return;

        BlockPos min = new BlockPos(
            Math.min(a.getX(), b.getX()),
            Math.min(a.getY(), b.getY()),
            Math.min(a.getZ(), b.getZ())
        );
        BlockPos max = new BlockPos(
            Math.max(a.getX(), b.getX()),
            Math.max(a.getY(), b.getY()),
            Math.max(a.getZ(), b.getZ())
        );

        String dimId = mc.world.getRegistryKey().getValue().toString();
        ClientPacketHandler.subtractBoxFromShape(shape.authority(), shape.name(), dimId, min, max);
    }

    private static void onWorldRenderLast(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (!isHoldingTool(mc.player)) return;

        var camera = context.camera();
        double camX = camera.getPos().x;
        double camY = camera.getPos().y;
        double camZ = camera.getPos().z;

        ShapeRenderer renderer = ShapeRenderer.getInstance();
        renderer.setFrustum(context.frustum());
        renderer.render(camX, camY, camZ, context.matrixStack());
    }
}
