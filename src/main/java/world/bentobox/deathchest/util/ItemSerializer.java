package world.bentobox.deathchest.util;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

import org.bukkit.inventory.ItemStack;

/**
 * Converts item stacks to and from a Base64 string so they can be stored in the database.
 * <p>
 * This uses Bukkit's own NBT-based serialization, which records the data version of the
 * server that wrote it, so stacks survive Minecraft upgrades. It deliberately does not use
 * the YAML/Map form, which loses modern data components.
 *
 * @author tastybento
 */
public final class ItemSerializer {

    private ItemSerializer() {
        // Utility class
    }

    /**
     * Serialize items to a Base64 string. Null and empty stacks are dropped.
     *
     * @param items items to serialize, may be null
     * @return Base64 string, empty if there was nothing to store
     */
    public static String toBase64(Collection<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        List<ItemStack> clean = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                clean.add(item);
            }
        }
        if (clean.isEmpty()) {
            return "";
        }
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(clean));
    }

    /**
     * Deserialize items previously written by {@link #toBase64(Collection)}.
     *
     * @param base64 Base64 string, may be null or empty
     * @return mutable list of items, empty if there was nothing to read
     * @throws IllegalArgumentException if the string is not valid serialized item data
     */
    public static List<ItemStack> fromBase64(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return new ArrayList<>();
        }
        ItemStack[] stacks = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(base64));
        List<ItemStack> result = new ArrayList<>(stacks.length);
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.getType().isAir()) {
                result.add(stack);
            }
        }
        return result;
    }
}
