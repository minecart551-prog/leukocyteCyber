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
import xyz.nucleoid.leukocyte.client.network.ClientBuildPacketHandler;
import xyz.nucleoid.leukocyte.client.render.ShapeRenderer;
import xyz.nucleoid.leukocyte.item.LeukocyteBuildAreaTool;

@Environment(EnvType.CLIENT)
public final class BuildAreaToolHandler {
    private static long lastLeftClickTime = 0;
    private static long lastRightClickTime = 0;
    private static final long CLICK_COOLDOWN = 150;
    private static boolean lastLeftClickPressed = false;
    private static boolean lastRightClickPressed = false;
    private static boolean lastMiddleClickPressed = false;
    private static boolean toolEquipped = false;

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(BuildAreaToolHandler::onClientTick);
        WorldRenderEvents.LAST.register(BuildAreaToolHandler::onWorldRenderLast);
    }

    private static boolean isHoldingTool(PlayerEntity player) {
        if (player == null) return false;
        return player.getMainHandStack().getItem() instanceof LeukocyteBuildAreaTool;
    }

    private static void onClientTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        boolean holdingTool = isHoldingTool(mc.player);
        BuildAreaToolState state = BuildAreaToolState.getInstance();

        if (holdingTool && !toolEquipped) {
            toolEquipped = true;
            state.setToolHeld(true);
            ClientBuildPacketHandler.requestBuildAreaData();
            ShapeRenderer.getInstance().markNeedsRebuild();
        } else if (!holdingTool && toolEquipped) {
            toolEquipped = false;
            state.setToolHeld(false);
            state.fullReset();
            lastLeftClickPressed = false;
            lastRightClickPressed = false;
            lastMiddleClickPressed = false;
            ShapeRenderer.getInstance().markNeedsRebuild();
        }

        if (!holdingTool) return;

        boolean middleClickPressed = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        if (middleClickPressed && !lastMiddleClickPressed) {
            lastMiddleClickPressed = true;
            mc.setScreen(new BuildAreaMenuScreen());
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
        }

        BuildAreaToolState.Mode mode = state.getMode();
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

    private static void updateHUD(MinecraftClient mc, BuildAreaToolState state, BuildAreaToolState.Mode mode) {
        if (mc.player == null) return;

        String areaInfo = "";
        var area = state.getSelectedArea();
        if (area != null) {
            areaInfo = " | Area: " + area.name();
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

        mc.player.sendMessage(Text.of("§e§l[Build Tool] §r§7Area: §f" + (area != null ? area.name() : "None") + areaInfo + " §7| " + modeInfo), true);
    }

    private static BlockPos getTargetPos(MinecraftClient mc) {
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return null;
        return ((BlockHitResult) mc.crosshairTarget).getBlockPos();
    }

    private static void handleLeftClick(MinecraftClient mc, BuildAreaToolState state, BuildAreaToolState.Mode mode) {
        if (mc.player == null || mc.world == null) return;

        switch (mode) {
            case IDLE -> {
                BlockPos pos = getTargetPos(mc);
                if (pos != null) {
                    state.setFirstCorner(pos);
                    state.setMode(BuildAreaToolState.Mode.CREATE_CORNER_2);
                    ShapeRenderer.getInstance().markNeedsRebuild();
                }
            }
            case SELECTED -> {
                BlockPos pos = getTargetPos(mc);
                if (pos != null) {
                    state.setFirstCorner(pos);
                    state.setMode(BuildAreaToolState.Mode.ADD_CORNER_2);
                    ShapeRenderer.getInstance().markNeedsRebuild();
                }
            }
            case CREATE_CORNER_2 -> {
                BlockPos pos = getTargetPos(mc);
                if (pos != null && state.getFirstCorner() != null) {
                    state.setSecondCorner(pos);
                    createAreaFromCorners(mc, state);
                    state.setMode(BuildAreaToolState.Mode.IDLE);
                    state.clearCorners();
                    ShapeRenderer.getInstance().markNeedsRebuild();
                }
            }
            case ADD_CORNER_2 -> {
                BlockPos pos = getTargetPos(mc);
                if (pos != null && state.getFirstCorner() != null) {
                    state.setSecondCorner(pos);
                    var selected = state.getSelectedArea();
                    if (selected != null) {
                        ClientBuildPacketHandler.addBoxToArea(selected.name(), state.getFirstCorner(), pos);
                    }
                    state.clearCorners();
                    state.setMode(BuildAreaToolState.Mode.SELECTED);
                    ShapeRenderer.getInstance().markNeedsRebuild();
                }
            }
            default -> {}
        }
    }

    private static void handleRightClick(MinecraftClient mc, BuildAreaToolState state, BuildAreaToolState.Mode mode) {
        if (mc.player == null || mc.world == null) return;

        switch (mode) {
            case SELECTED -> {
                BlockPos pos = getTargetPos(mc);
                if (pos != null) {
                    state.setFirstCorner(pos);
                    state.setMode(BuildAreaToolState.Mode.SUB_CORNER_2);
                    ShapeRenderer.getInstance().markNeedsRebuild();
                }
            }
            case SUB_CORNER_2 -> {
                BlockPos pos = getTargetPos(mc);
                if (pos != null && state.getFirstCorner() != null) {
                    state.setSecondCorner(pos);
                    var selected = state.getSelectedArea();
                    if (selected != null) {
                        ClientBuildPacketHandler.subtractBoxFromArea(selected.name(), state.getFirstCorner(), pos);
                    }
                    state.clearCorners();
                    state.setMode(BuildAreaToolState.Mode.SELECTED);
                    ShapeRenderer.getInstance().markNeedsRebuild();
                }
            }
            default -> {
                state.reset();
                ShapeRenderer.getInstance().markNeedsRebuild();
            }
        }
    }

    private static void createAreaFromCorners(MinecraftClient mc, BuildAreaToolState state) {
        if (mc.world == null) return;

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
        String name = "build_" + System.currentTimeMillis();
        ClientBuildPacketHandler.createBuildArea(name, dimId, min, max);
        BuildAreaToolState.setPendingAutoSelect(name);
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
