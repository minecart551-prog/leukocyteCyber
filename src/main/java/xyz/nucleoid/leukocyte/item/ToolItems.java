package xyz.nucleoid.leukocyte.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtString;

public final class ToolItems {
    private static final String NBT_KEY = "leukocyte_tool";

    public static final Item SHAPE_TOOL = Items.STICK;
    public static final Item BUILD_AREA_TOOL = Items.BLAZE_ROD;

    private ToolItems() {
    }

    public static ItemStack createShapeTool() {
        var stack = new ItemStack(SHAPE_TOOL);
        stack.setCustomName(net.minecraft.text.Text.literal("§6Shape Tool"));
        var nbt = stack.getOrCreateNbt();
        nbt.put(NBT_KEY, NbtString.of("shape"));
        return stack;
    }

    public static ItemStack createBuildAreaTool() {
        var stack = new ItemStack(BUILD_AREA_TOOL);
        stack.setCustomName(net.minecraft.text.Text.literal("§6Build Area Tool"));
        var nbt = stack.getOrCreateNbt();
        nbt.put(NBT_KEY, NbtString.of("build_area"));
        return stack;
    }

    public static boolean isShapeTool(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != SHAPE_TOOL) return false;
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.contains(NBT_KEY) && "shape".equals(nbt.getString(NBT_KEY));
    }

    public static boolean isBuildAreaTool(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != BUILD_AREA_TOOL) return false;
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.contains(NBT_KEY) && "build_area".equals(nbt.getString(NBT_KEY));
    }

    public static boolean isAnyLeukocyteTool(ItemStack stack) {
        return isShapeTool(stack) || isBuildAreaTool(stack);
    }
}
