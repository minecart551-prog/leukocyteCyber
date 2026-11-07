package xyz.nucleoid.leukocyte.integration;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Optional integration with Armourer's Workshop (mod id {@code armourers_workshop}).
 *
 * <p>AW is not a compile dependency of this project and publishes no Maven artifact, so every
 * access goes through reflection against the class names AW ships in its own jar. AW's own class
 * and member names are never remapped, while the Minecraft types in those signatures are
 * intermediary-mapped exactly like this mod's own references — so the lookups resolve at runtime
 * and the returned objects can be used directly.</p>
 *
 * <p>Every entry point fails safe: when AW is absent, or its internals are not what we expect,
 * the calls degrade to no-ops so build mode keeps working on its own.</p>
 */
public final class ArmourersWorkshopIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger("leukocyte");

    // AW's SkinSlotType.DYE occupies index 80 for 16 slots. Only used if the enum lookup fails.
    private static final int FALLBACK_DYE_START = 80;
    private static final int FALLBACK_DYE_END = 96;

    private static boolean initialised;
    private static boolean available;

    private static Method wardrobeOf;
    private static Method wardrobeInventory;
    private static Method wardrobeClear;
    private static Method wardrobeBroadcast;
    private static int dyeStart = FALLBACK_DYE_START;
    private static int dyeEnd = FALLBACK_DYE_END;

    private ArmourersWorkshopIntegration() {
    }

    public static boolean isAvailable() {
        init();
        return available;
    }

    /**
     * Returns whether {@code entity} has any visible cosmetic equipped.
     *
     * <p>Dye slots are skipped: they hold paint colours rather than something the player wears,
     * so a stocked dye palette must not keep someone out of build mode.</p>
     */
    public static boolean hasCosmetics(Entity entity) {
        var wardrobe = resolveWardrobe(entity);
        if (wardrobe == null) return false;

        try {
            var inventory = (Inventory) wardrobeInventory.invoke(wardrobe);
            if (inventory == null) return false;

            int size = inventory.size();
            for (int i = 0; i < size; i++) {
                if (i >= dyeStart && i < dyeEnd) continue;
                ItemStack stack = inventory.getStack(i);
                if (stack != null && !stack.isEmpty()) return true;
            }
            return false;
        } catch (Throwable throwable) {
            // Fail open: an unrecognised AW build must not lock people out of build mode.
            LOGGER.debug("Could not read the Armourer's Workshop wardrobe", throwable);
            return false;
        }
    }

    /**
     * Removes every cosmetic from {@code entity} and syncs the result to the player and anyone
     * watching them. Returns whether there was anything to remove.
     *
     * <p>This mirrors AW's own {@code /armourers clearSkin} executor, which pairs {@code clear()}
     * with {@code broadcast()} — without the broadcast the server state and the client would
     * disagree and the cosmetics would still be rendered.</p>
     */
    public static boolean clearCosmetics(Entity entity) {
        if (!hasCosmetics(entity)) return false;

        var wardrobe = resolveWardrobe(entity);
        if (wardrobe == null) return false;

        try {
            wardrobeClear.invoke(wardrobe);
            wardrobeBroadcast.invoke(wardrobe);
            return true;
        } catch (Throwable throwable) {
            LOGGER.debug("Could not clear the Armourer's Workshop wardrobe", throwable);
            return false;
        }
    }

    /**
     * Resolves the player's wardrobe, or {@code null} when AW is unavailable, the entity has no
     * wardrobe, or anything unexpected happens along the way.
     */
    private static Object resolveWardrobe(Entity entity) {
        if (entity == null || !isAvailable()) return null;

        try {
            return wardrobeOf.invoke(null, entity);
        } catch (Throwable throwable) {
            LOGGER.debug("Could not open the Armourer's Workshop wardrobe", throwable);
            return null;
        }
    }

    private static synchronized void init() {
        if (initialised) return;
        initialised = true;

        if (!FabricLoader.getInstance().isModLoaded("armourers_workshop")) {
            return; // AW is not installed, so the integration simply stays off.
        }

        try {
            ClassLoader loader = ArmourersWorkshopIntegration.class.getClassLoader();

            Class<?> wardrobeClass = Class.forName(
                "moe.plushie.armourers_workshop.core.capability.SkinWardrobe", false, loader);
            Class<?> slotTypeClass = Class.forName(
                "moe.plushie.armourers_workshop.core.menu.SkinSlotType", false, loader);

            wardrobeOf = wardrobeClass.getMethod("of", Entity.class);
            wardrobeInventory = wardrobeClass.getMethod("inventory");
            wardrobeClear = wardrobeClass.getMethod("clear");
            wardrobeBroadcast = wardrobeClass.getMethod("broadcast");

            readDyeRange(slotTypeClass);
            available = true;
        } catch (Throwable throwable) {
            LOGGER.warn("Armourer's Workshop is installed but could not be hooked; "
                + "build mode will ignore cosmetics", throwable);
            wardrobeOf = null;
            wardrobeInventory = null;
            wardrobeClear = null;
            wardrobeBroadcast = null;
            available = false;
        }
    }

    /**
     * Reads the dye slot range from AW's own enum rather than hard-coding it, so a reshuffled
     * layout in a future AW release can't make a dye palette look like an equipped cosmetic.
     */
    private static void readDyeRange(Class<?> slotTypeClass) {
        try {
            Field dye = slotTypeClass.getField("DYE");
            Object value = dye.get(null);
            int start = (int) slotTypeClass.getMethod("index").invoke(value);
            int size = (int) slotTypeClass.getMethod("maxSize").invoke(value);
            dyeStart = start;
            dyeEnd = start + size;
        } catch (Throwable throwable) {
            dyeStart = FALLBACK_DYE_START;
            dyeEnd = FALLBACK_DYE_END;
            LOGGER.debug("Could not read SkinSlotType.DYE, falling back to slots {}-{}",
                dyeStart, dyeEnd - 1, throwable);
        }
    }
}
