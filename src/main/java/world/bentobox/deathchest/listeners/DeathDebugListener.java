package world.bentobox.deathchest.listeners;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredListener;

import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.deathchest.DeathChest;
import world.bentobox.deathchest.data.DeathChestRecord;

/**
 * Console diagnostics for deaths, so a server owner can see exactly what happens to a
 * player's drops and which plugin is responsible when a death chest does not appear.
 * <p>
 * This listener is always registered but says nothing unless {@code debug} is switched on in
 * config.yml or with {@code /<gamemode>admin deathchest debug}. It watches the same event as
 * {@link DeathListener} from three places: before any plugin has touched it, immediately
 * before DeathChest runs, and after every plugin has finished. Comparing the three lines
 * shows who took the drops.
 *
 * @author tastybento
 */
public class DeathDebugListener implements Listener {

    /** A death chest created within this many milliseconds of the event counts as this death's. */
    private static final long JUST_NOW = 2000L;

    /** How many drop removals to report per death before going quiet - one is usually enough. */
    private static final int MAX_TATTLES = 4;

    /**
     * The {@link EntityDeathEvent} field holding the drops, so the tracker can swap it for a
     * wrapper that names whoever empties it. Null when the server's event class has changed
     * shape and the field cannot be found; the checkpoints still work without it.
     */
    private static final Field DROPS_FIELD = findDropsField();

    private final DeathChest addon;

    public DeathDebugListener(DeathChest addon) {
        this.addon = addon;
    }

    /**
     * The baseline checkpoint, plus the full list of plugins listening for deaths on this
     * server. The listener dump shows exactly which plugins, if any, ran before this.
     *
     * @param e death event
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void first(PlayerDeathEvent e) {
        if (!addon.getSettings().isDebug()) {
            return;
        }
        addon.log("=== DeathChest debug: " + e.getEntity().getName() + " died ===");
        addon.log("  world: " + e.getEntity().getWorld().getName() + ", location: "
                + describe(e.getEntity().getLocation()));
        addon.log("  DeathChest active in this world: " + addon.inGameWorld(e.getEntity().getWorld()));
        report("LOWEST checkpoint", e);
        dumpListeners(addon);
        installTracker(e);
    }

    /**
     * Checkpoint between the LOWEST and LOW listeners. When something changes between two
     * checkpoints, the culprit is one of the plugins the listener dump places between them.
     *
     * @param e death event
     */
    @EventHandler(priority = EventPriority.LOW)
    public void afterLowest(PlayerDeathEvent e) {
        if (addon.getSettings().isDebug()) {
            report("LOW checkpoint", e);
        }
    }

    /**
     * Checkpoint between the LOW and NORMAL listeners.
     *
     * @param e death event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void afterLow(PlayerDeathEvent e) {
        if (addon.getSettings().isDebug()) {
            report("NORMAL checkpoint", e);
        }
    }

    /**
     * The state one priority level before DeathChest takes the drops. Anything different from
     * the NORMAL checkpoint was done by a plugin listed in the listener dump between the two.
     *
     * @param e death event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void beforeDeathChest(PlayerDeathEvent e) {
        if (addon.getSettings().isDebug()) {
            report("HIGH checkpoint (just before DeathChest runs)", e);
        }
    }

    /**
     * The final state of the event. If DeathChest took the drops but there are drops here
     * again, another plugin has put them back and that is why items land on the ground.
     *
     * @param e death event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void last(PlayerDeathEvent e) {
        if (!addon.getSettings().isDebug()) {
            return;
        }
        removeTracker(e);
        report("MONITOR checkpoint (after every plugin)", e);
        if (!e.getDrops().isEmpty() && tookTheDrops(e.getEntity().getUniqueId())) {
            addon.logWarning("  DeathChest emptied the drops but something has put " + e.getDrops().size()
                    + " stack(s) back afterwards. A plugin listening at HIGHEST or MONITOR in the list"
                    + " above is overriding DeathChest - those items will fall on the ground.");
        }
        addon.log("=== end of debug for " + e.getEntity().getName() + " ===");
    }

    /**
     * @param uuid player who died
     * @return true if DeathChest made a record for this player in the last couple of seconds
     */
    private boolean tookTheDrops(UUID uuid) {
        List<DeathChestRecord> chests = addon.getManager().getChests(uuid);
        return !chests.isEmpty() && System.currentTimeMillis() - chests.get(0).getDeathTime() < JUST_NOW;
    }

    private void report(String when, PlayerDeathEvent e) {
        addon.log("  " + when + ": drops=" + e.getDrops().size() + " stack(s)" + itemList(e) + ", droppedExp="
                + e.getDroppedExp() + ", keepInventory=" + e.getKeepInventory() + ", keepLevel=" + e.getKeepLevel());
    }

    /**
     * @param e death event
     * @return the drops as "[STONE x64, DIAMOND_SWORD x1]", or "" when there are none
     */
    private static String itemList(PlayerDeathEvent e) {
        if (e.getDrops().isEmpty()) {
            return "";
        }
        return " [" + e.getDrops().stream()
                .map(i -> i == null ? "null" : i.getType() + " x" + i.getAmount())
                .collect(Collectors.joining(", ")) + "]";
    }

    /**
     * List every plugin listening for player deaths, in the order they are called. A plugin
     * ahead of DeathChest that clears the drops, or sets keepInventory, is the culprit when
     * chests are not being made.
     *
     * @param addon addon to log through
     */
    public static void dumpListeners(DeathChest addon) {
        addon.log("  Plugins listening for PlayerDeathEvent, in call order:");
        RegisteredListener[] listeners = PlayerDeathEvent.getHandlerList().getRegisteredListeners();
        for (RegisteredListener listener : listeners) {
            addon.log("    " + listener.getPriority() + " - " + listener.getPlugin().getName() + " ("
                    + listener.getListener().getClass().getName() + ")");
        }
        addon.log("    DeathChest itself runs at HIGHEST. Anything above it in this list runs first.");
    }

    /**
     * Log what DeathChest is hooked into and how it is configured. Useful on its own when
     * chests are not being made in a world the owner expects to be covered.
     *
     * @param addon addon to report on
     */
    public static void dumpState(DeathChest addon) {
        addon.log("=== DeathChest state ===");
        addon.log("  Hooked game modes: " + (addon.getGameModes().isEmpty() ? "NONE - the addon will do nothing!"
                : addon.getGameModes().stream().map(gm -> gm.getDescription().getName())
                        .collect(Collectors.joining(", "))));
        for (GameModeAddon gm : addon.getGameModes()) {
            addon.log("    " + gm.getDescription().getName() + " worlds: " + worlds(gm));
        }
        if (!addon.getSettings().getDisabledGameModes().isEmpty()) {
            addon.log("  Disabled in config.yml: " + addon.getSettings().getDisabledGameModes());
        }
        addon.log("  Chest material: " + addon.getSettings().getChestMaterial() + ", place at death location: "
                + addon.getSettings().isPlaceAtDeathLocation() + ", search radius: "
                + addon.getSettings().getSearchRadius() + ", search depth: " + addon.getSettings().getSearchDepth());
        addon.log("  Stored death chests: "
                + (addon.getManager() == null ? "manager not loaded!" : addon.getManager().getAllChests().size()));
        dumpListeners(addon);
    }

    /**
     * @param gm game mode
     * @return the names of the game mode's worlds, for logging
     */
    private static String worlds(GameModeAddon gm) {
        StringBuilder sb = new StringBuilder(gm.getOverWorld() == null ? "?" : gm.getOverWorld().getName());
        if (gm.getNetherWorld() != null) {
            sb.append(", ").append(gm.getNetherWorld().getName());
        }
        if (gm.getEndWorld() != null) {
            sb.append(", ").append(gm.getEndWorld().getName());
        }
        return sb.toString();
    }

    /**
     * @return the drops field of {@link EntityDeathEvent}, made accessible, or null if the
     *         server's event class no longer has it
     */
    private static Field findDropsField() {
        try {
            Field f = EntityDeathEvent.class.getDeclaredField("drops");
            f.setAccessible(true);
            return f;
        } catch (Exception ex) {
            // Fall back to the first List field, whatever it is called on this server.
            for (Field f : EntityDeathEvent.class.getDeclaredFields()) {
                if (List.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return f;
                }
            }
            return null;
        }
    }

    /**
     * Swap the event's drops list for a wrapper that logs a stack trace naming whoever
     * removes items from it. The wrapper delegates every call to the real list, so plugins
     * and the server see exactly the contents they would have seen without it.
     *
     * @param e death event
     */
    private void installTracker(PlayerDeathEvent e) {
        if (DROPS_FIELD == null) {
            addon.log("  (drops tracker not available: the server's death event has an unexpected shape)");
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            List<ItemStack> original = (List<ItemStack>) DROPS_FIELD.get(e);
            if (!(original instanceof TattletaleDrops)) {
                DROPS_FIELD.set(e, new TattletaleDrops(addon, original));
                addon.log("  Watching the drops: whatever removes items from them will be named below.");
            }
        } catch (Exception ex) {
            addon.log("  (drops tracker could not be installed: " + ex + ")");
        }
    }

    /**
     * Put the original drops list back before the event goes back to the server. Harmless if
     * it fails: the wrapper delegates everything to the original list anyway.
     *
     * @param e death event
     */
    private void removeTracker(PlayerDeathEvent e) {
        if (DROPS_FIELD == null) {
            return;
        }
        try {
            if (DROPS_FIELD.get(e) instanceof TattletaleDrops tattletale) {
                DROPS_FIELD.set(e, tattletale.delegate);
            }
        } catch (Exception ex) {
            // Nothing to do - see above.
        }
    }

    /**
     * A drops list that tells on whoever removes items from it. Everything is delegated to
     * the real list; removal calls additionally log the calling plugin's stack frames.
     */
    private static class TattletaleDrops implements List<ItemStack> {

        private final DeathChest addon;
        private final List<ItemStack> delegate;
        private int tattles;

        TattletaleDrops(DeathChest addon, List<ItemStack> delegate) {
            this.addon = addon;
            this.delegate = delegate;
        }

        /**
         * Name the caller of a removal, skipping server internals so the first line is the
         * plugin code responsible.
         *
         * @param operation what was done to the list
         */
        private void tattle(String operation) {
            if (tattles++ >= MAX_TATTLES) {
                return;
            }
            if (tattles == MAX_TATTLES) {
                addon.logWarning("  ! (more drop removals follow but will not be logged)");
                return;
            }
            addon.logWarning("  ! drops " + operation + " by:");
            int shown = 0;
            for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
                String cn = frame.getClassName();
                if (cn.startsWith("java.") || cn.startsWith("jdk.") || cn.startsWith("sun.")
                        || cn.startsWith("org.bukkit.") || cn.startsWith("io.papermc.")
                        || cn.startsWith("net.minecraft.") || cn.startsWith("com.destroystokyo.")
                        || cn.startsWith("co.aikar.") || cn.contains("TattletaleDrops")) {
                    continue;
                }
                addon.logWarning("  !     at " + frame);
                if (++shown == 4) {
                    break;
                }
            }
            if (shown == 0) {
                addon.logWarning("  !     the server itself, not a plugin");
            }
        }

        private String name(Object item) {
            return item instanceof ItemStack stack ? stack.getType() + " x" + stack.getAmount() : String.valueOf(item);
        }

        // --- removals: log, then delegate ---

        @Override
        public void clear() {
            tattle("clear() removing all " + delegate.size() + " stack(s)");
            delegate.clear();
        }

        @Override
        public boolean remove(Object o) {
            tattle("remove of " + name(o));
            return delegate.remove(o);
        }

        @Override
        public ItemStack remove(int index) {
            tattle("remove of " + name(delegate.get(index)));
            return delegate.remove(index);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            tattle("removeAll of " + c.size() + " stack(s)");
            return delegate.removeAll(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            tattle("retainAll keeping at most " + c.size() + " stack(s)");
            return delegate.retainAll(c);
        }

        @Override
        public ItemStack set(int index, ItemStack element) {
            tattle("replacement of " + name(delegate.get(index)) + " with " + name(element));
            return delegate.set(index, element);
        }

        @Override
        public Iterator<ItemStack> iterator() {
            Iterator<ItemStack> it = delegate.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public ItemStack next() {
                    return it.next();
                }

                @Override
                public void remove() {
                    tattle("iterator remove of a stack");
                    it.remove();
                }
            };
        }

        @Override
        public ListIterator<ItemStack> listIterator() {
            return listIterator(0);
        }

        @Override
        public ListIterator<ItemStack> listIterator(int index) {
            ListIterator<ItemStack> it = delegate.listIterator(index);
            return new ListIterator<>() {
                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public ItemStack next() {
                    return it.next();
                }

                @Override
                public boolean hasPrevious() {
                    return it.hasPrevious();
                }

                @Override
                public ItemStack previous() {
                    return it.previous();
                }

                @Override
                public int nextIndex() {
                    return it.nextIndex();
                }

                @Override
                public int previousIndex() {
                    return it.previousIndex();
                }

                @Override
                public void remove() {
                    tattle("iterator remove of a stack");
                    it.remove();
                }

                @Override
                public void set(ItemStack itemStack) {
                    tattle("iterator replacement of a stack");
                    it.set(itemStack);
                }

                @Override
                public void add(ItemStack itemStack) {
                    it.add(itemStack);
                }
            };
        }

        // --- everything else: plain delegation ---

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return delegate.contains(o);
        }

        @Override
        public Object[] toArray() {
            return delegate.toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return delegate.toArray(a);
        }

        @Override
        public boolean add(ItemStack itemStack) {
            return delegate.add(itemStack);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return delegate.containsAll(c);
        }

        @Override
        public boolean addAll(Collection<? extends ItemStack> c) {
            return delegate.addAll(c);
        }

        @Override
        public boolean addAll(int index, Collection<? extends ItemStack> c) {
            return delegate.addAll(index, c);
        }

        @Override
        public ItemStack get(int index) {
            return delegate.get(index);
        }

        @Override
        public void add(int index, ItemStack element) {
            delegate.add(index, element);
        }

        @Override
        public int indexOf(Object o) {
            return delegate.indexOf(o);
        }

        @Override
        public int lastIndexOf(Object o) {
            return delegate.lastIndexOf(o);
        }

        @Override
        public List<ItemStack> subList(int fromIndex, int toIndex) {
            return delegate.subList(fromIndex, toIndex);
        }

        @Override
        public boolean equals(Object o) {
            return delegate.equals(o);
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }
    }

    /**
     * @param location location to describe
     * @return a short "world x,y,z" string, or "none"
     */
    static String describe(Location location) {
        if (location == null || location.getWorld() == null) {
            return "none";
        }
        return location.getWorld().getName() + " " + location.getBlockX() + "," + location.getBlockY() + ","
                + location.getBlockZ();
    }
}
