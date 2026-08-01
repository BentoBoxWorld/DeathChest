package world.bentobox.deathchest.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.database.Database;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.deathchest.DeathChest;
import world.bentobox.deathchest.Settings;
import world.bentobox.deathchest.util.ChestPlacer;
import world.bentobox.deathchest.util.ItemSerializer;

/**
 * Owns every death chest: the database records, the blocks in the world, and the rules for
 * creating, refilling and expiring them.
 * <p>
 * Items live in the chest block when there is one. Anything that would not fit, or every item
 * if no block could be placed at all, is held in the record and handed back either when the
 * player empties the chest or when they claim it with the command.
 *
 * @author tastybento
 */
public class DeathChestManager {

    private final DeathChest addon;
    private final Database<DeathChestRecord> handler;
    private final ChestPlacer placer;

    /** Records by their unique id. */
    private final Map<String, DeathChestRecord> cache = new HashMap<>();

    /** Block location key to record unique id, so a click on a block is a map lookup. */
    private final Map<String, String> byLocation = new HashMap<>();

    public DeathChestManager(DeathChest addon) {
        this.addon = addon;
        this.handler = new Database<>(addon, DeathChestRecord.class);
        this.placer = new ChestPlacer(addon);
    }

    /**
     * A stable map key for a block location. Deliberately not
     * {@code Util.getStringLocation} because that includes yaw and pitch, which vary
     * depending on how the location was obtained.
     *
     * @param location location to key
     * @return key of the form {@code world:x:y:z}
     */
    public static String key(@NonNull Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":"
                + location.getBlockZ();
    }

    /**
     * Load every record from the database into the cache. Records whose world is not loaded
     * are kept - the world may be loaded later by a multiverse-style plugin.
     */
    public void load() {
        cache.clear();
        byLocation.clear();
        for (DeathChestRecord chest : handler.loadObjects()) {
            cache.put(chest.getUniqueId(), chest);
            indexLocation(chest);
        }
        addon.log("Loaded " + cache.size() + " death chest" + (cache.size() == 1 ? "" : "s"));
    }

    private void indexLocation(@NonNull DeathChestRecord chest) {
        if (!chest.isVirtual()) {
            Location loc = chest.getChestLoc();
            if (loc != null) {
                byLocation.put(key(loc), chest.getUniqueId());
            }
        }
    }

    /**
     * @param location block location
     * @return the death chest record for this block, if there is one
     */
    public Optional<DeathChestRecord> getChestAt(@Nullable Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byLocation.get(key(location))).map(cache::get);
    }

    /**
     * @param owner player uuid
     * @return that player's death chests, newest first
     */
    public List<DeathChestRecord> getChests(@NonNull UUID owner) {
        return cache.values().stream().filter(r -> owner.equals(r.getOwnerUUID()))
                .sorted(Comparator.comparingLong(DeathChestRecord::getDeathTime).reversed()).toList();
    }

    /**
     * @return all records, for admin listings and tests
     */
    public List<DeathChestRecord> getAllChests() {
        return List.copyOf(cache.values());
    }

    /**
     * Take a player's dropped items and experience and turn them into a death chest.
     *
     * @param player player who died
     * @param drops  items they dropped - the caller is responsible for clearing these from the
     *               death event so they are not also dropped on the ground
     * @param xp     experience to store, already scaled by the configured percentage
     * @return the new record, never null. It may be virtual if nowhere could be found to put
     *         a chest.
     */
    @NonNull
    public DeathChestRecord createChest(@NonNull Player player, @NonNull List<ItemStack> drops, int xp) {
        Settings settings = addon.getSettings();
        DeathChestRecord chest = new DeathChestRecord();
        chest.setOwnerUUID(player.getUniqueId());
        chest.setOwnerName(player.getName());
        chest.setDeathLoc(player.getLocation());
        chest.setDeathTime(System.currentTimeMillis());
        chest.setExperience(xp);
        if (settings.getExpiryMinutes() > 0) {
            chest.setExpiryTime(chest.getDeathTime() + settings.getExpiryMinutes() * 60_000L);
        }

        List<ItemStack> remaining = new ArrayList<>(drops);
        placer.findSpot(player.getUniqueId(), player.getLocation()).ifPresent(placement -> {
            chest.setIslandId(placement.island().getUniqueId());
            if (placeBlock(placement.location(), remaining)) {
                chest.setChestLoc(placement.location());
            }
        });
        // Whatever is left over is held by the addon until the player makes room for it.
        chest.setItems(ItemSerializer.toBase64(remaining));

        cache.put(chest.getUniqueId(), chest);
        indexLocation(chest);
        addon.getHolograms().spawn(chest);
        handler.saveObjectAsync(chest);
        enforceMaxChests(player.getUniqueId());
        return chest;
    }

    /**
     * Put the chest block in the world and fill it with as many items as it will hold.
     *
     * @param location  where to place the block
     * @param items     items to store - those that fit are removed from this list
     * @return true if a container block was placed
     */
    private boolean placeBlock(@NonNull Location location, @NonNull List<ItemStack> items) {
        Material material = getChestMaterial();
        if (material == null) {
            return false;
        }
        Block block = location.getBlock();
        // No physics: a chest placed next to another chest must not merge into a double chest,
        // and nothing above it should fall or update.
        block.setType(material, false);
        BlockState state = block.getState();
        if (!(state instanceof Container container)) {
            addon.logError("Configured chest material " + material + " is not a container. Using virtual storage.");
            block.setType(Material.AIR, false);
            return false;
        }
        // getInventory() on a placed block state is the live tile entity inventory, so adding to
        // it takes effect immediately. Do NOT call container.update() afterwards: update() writes
        // the snapshot captured by getState() back over the block, and that snapshot was taken
        // when the chest was empty, so it wipes everything just added.
        List<ItemStack> leftovers = new ArrayList<>(
                container.getInventory().addItem(items.toArray(new ItemStack[0])).values());
        items.clear();
        items.addAll(leftovers);
        return true;
    }

    /**
     * @return the configured chest material, or null if it is not a real material
     */
    @Nullable
    public Material getChestMaterial() {
        String name = addon.getSettings().getChestMaterial();
        Material material = name == null ? null
                : Registry.MATERIAL.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ENGLISH)));
        if (material == null) {
            addon.logError("Unknown chest material '" + name + "' in config.yml. Using virtual storage.");
        }
        return material;
    }

    /**
     * Top a chest up from the addon's overflow store, then tidy the chest away if it and the
     * store are both empty. Called after a player closes a death chest.
     *
     * @param chest record to refill
     */
    public void refill(@NonNull DeathChestRecord chest) {
        Block block = getBlock(chest);
        if (block == null || !(block.getState() instanceof Container container)) {
            // The block is gone. Everything the addon still holds becomes a virtual chest.
            addon.getHolograms().remove(chest);
            chest.setChestLoc(null);
            byLocation.values().remove(chest.getUniqueId());
            if (isEmpty(chest)) {
                delete(chest);
                return;
            }
            handler.saveObjectAsync(chest);
            return;
        }
        List<ItemStack> stored = readItems(chest);
        if (!stored.isEmpty()) {
            // Live inventory - see the note in placeBlock about not calling update() here.
            stored = new ArrayList<>(container.getInventory().addItem(stored.toArray(new ItemStack[0])).values());
            chest.setItems(ItemSerializer.toBase64(stored));
        }
        if (stored.isEmpty() && isInventoryEmpty(container.getInventory()) && chest.getExperience() == 0) {
            delete(chest);
            block.setType(Material.AIR, false);
            return;
        }
        handler.saveObjectAsync(chest);
    }

    /**
     * Read the items the addon is holding for this record.
     *
     * @param chest record to read
     * @return mutable list of items, empty if there are none or the data is unreadable
     */
    @NonNull
    public List<ItemStack> readItems(@NonNull DeathChestRecord chest) {
        try {
            return ItemSerializer.fromBase64(chest.getItems());
        } catch (Exception e) {
            addon.logError("Could not read stored items for death chest " + chest.getUniqueId() + ": "
                    + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * @param chest record to check
     * @return true if the addon is holding nothing for this record
     */
    private boolean isEmpty(@NonNull DeathChestRecord chest) {
        return chest.getExperience() == 0 && readItems(chest).isEmpty();
    }

    private boolean isInventoryEmpty(@NonNull Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param chest record to locate
     * @return the chest block, or null if the record is virtual or its world is not loaded
     */
    @Nullable
    public Block getBlock(@NonNull DeathChestRecord chest) {
        Location loc = chest.getChestLoc();
        return loc == null || loc.getWorld() == null ? null : loc.getBlock();
    }

    /**
     * Hand everything the addon is holding for a record to a player. Items that do not fit in
     * the player's inventory are dropped at their feet. The record is left in place - the
     * caller decides whether it is finished with.
     *
     * @param player player to give to
     * @param chest record to claim
     * @return number of item stacks handed over
     */
    public int claim(@NonNull Player player, @NonNull DeathChestRecord chest) {
        List<ItemStack> items = readItems(chest);
        int given = items.size();
        if (!items.isEmpty()) {
            player.getInventory().addItem(items.toArray(new ItemStack[0])).values()
                    .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
            chest.setItems("");
        }
        if (chest.getExperience() > 0) {
            player.giveExp(chest.getExperience());
            chest.setExperience(0);
        }
        handler.saveObjectAsync(chest);
        return given;
    }

    /**
     * Give a player the experience held by a chest they have just opened.
     *
     * @param player player opening the chest
     * @param chest record they opened
     */
    public void giveExperience(@NonNull Player player, @NonNull DeathChestRecord chest) {
        if (chest.getExperience() > 0) {
            player.giveExp(chest.getExperience());
            chest.setExperience(0);
            handler.saveObjectAsync(chest);
        }
    }

    /**
     * Remove a record from the cache and the database, and take down its hologram.
     * Does not touch any blocks.
     *
     * @param chest record to delete
     */
    public void delete(@NonNull DeathChestRecord chest) {
        addon.getHolograms().remove(chest);
        cache.remove(chest.getUniqueId());
        byLocation.values().remove(chest.getUniqueId());
        handler.deleteObject(chest);
    }

    /**
     * Save a record.
     *
     * @param chest record to save
     */
    public void save(@NonNull DeathChestRecord chest) {
        handler.saveObjectAsync(chest);
    }

    /**
     * Expire a chest, following the configured expiry action.
     *
     * @param chest record to expire
     */
    public void expire(@NonNull DeathChestRecord chest) {
        boolean drop = addon.getSettings().getExpiryAction() == Settings.ExpiryAction.DROP;
        Block block = getBlock(chest);
        if (block != null && block.getState() instanceof Container container) {
            if (drop) {
                Location dropAt = block.getLocation().add(0.5, 0.5, 0.5);
                for (ItemStack item : container.getInventory().getContents()) {
                    if (item != null && !item.getType().isAir()) {
                        block.getWorld().dropItem(dropAt, item);
                    }
                }
                readItems(chest).forEach(item -> block.getWorld().dropItem(dropAt, item));
                spawnExperience(dropAt, chest.getExperience());
            }
            // Live inventory - see the note in placeBlock about not calling update() here.
            container.getInventory().clear();
            block.setType(Material.AIR, false);
        } else if (drop && chest.getDeathLoc() != null && chest.getDeathLoc().getWorld() != null) {
            // Virtual chest with nowhere sensible to drop. Nothing to do but let it go.
            addon.log("Death chest " + chest.getUniqueId() + " for " + chest.getOwnerName()
                    + " expired with no block to drop from.");
        }
        delete(chest);
    }

    private void spawnExperience(@NonNull Location location, int amount) {
        if (amount > 0 && location.getWorld() != null) {
            location.getWorld().spawn(location, ExperienceOrb.class, orb -> orb.setExperience(amount));
        }
    }

    /**
     * Expire every chest whose time is up. Called on a repeating task.
     *
     * @return number of chests expired
     */
    public int checkExpiry() {
        long now = System.currentTimeMillis();
        List<DeathChestRecord> expired = cache.values().stream().filter(r -> r.isExpired(now)).toList();
        expired.forEach(this::expire);
        return expired.size();
    }

    /**
     * Expire a player's oldest chests until they are within the configured limit.
     *
     * @param owner player uuid
     */
    private void enforceMaxChests(@NonNull UUID owner) {
        int max = addon.getSettings().getMaxChestsPerPlayer();
        if (max <= 0) {
            return;
        }
        List<DeathChestRecord> chests = getChests(owner);
        for (int i = max; i < chests.size(); i++) {
            expire(chests.get(i));
        }
    }

    /**
     * Delete every record for an island. Called when an island is reset or deleted, because
     * its blocks - death chests included - are about to be wiped.
     *
     * @param island island being reset or deleted
     */
    public void removeIslandChests(@NonNull Island island) {
        cache.values().stream().filter(r -> island.getUniqueId().equals(r.getIslandId())).toList()
                .forEach(this::delete);
    }

    /**
     * Close the database handler.
     */
    public void close() {
        handler.close();
    }
}
