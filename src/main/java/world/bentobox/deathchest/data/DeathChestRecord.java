package world.bentobox.deathchest.data;

import java.util.UUID;

import org.bukkit.Location;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.annotations.Expose;

import world.bentobox.bentobox.database.objects.DataObject;
import world.bentobox.bentobox.database.objects.Table;
import world.bentobox.bentobox.util.Util;

/**
 * One death chest. A record always exists for a death that DeathChest handled, even if
 * no block could be placed in the world - in that case {@link #isVirtual()} is true and the
 * items live in {@link #getItems()} until the player claims them with a command.
 * <p>
 * When a chest block does exist, the block is the authoritative store for the items and
 * {@link #getItems()} holds a snapshot taken at death, used only to recover items if the
 * block is destroyed by something outside the addon's control.
 *
 * @author tastybento
 */
@Table(name = "DeathChests")
public class DeathChestRecord implements DataObject {

    @Expose
    private String uniqueId = UUID.randomUUID().toString();

    /**
     * UUID of the player who died, as a string.
     */
    @Expose
    private String owner;

    /**
     * The player's name at time of death, for admin listings when they are offline.
     */
    @Expose
    private String ownerName;

    /**
     * Serialized location of the chest block, or empty/null if no block could be placed.
     */
    @Expose
    private String chestLocation;

    /**
     * Serialized location where the player actually died. Kept for the "you died at" message
     * and so admins can tell a void death from a normal one.
     */
    @Expose
    private String deathLocation;

    /**
     * Id of the island the chest was placed on, or null if it is not on an island.
     * Used to clean up records when an island is reset or deleted.
     */
    @Expose
    private String islandId;

    @Expose
    private long deathTime;

    /**
     * Epoch millis when this chest expires, or 0 if it never expires.
     */
    @Expose
    private long expiryTime;

    /**
     * Experience stored with the chest.
     */
    @Expose
    private int experience;

    /**
     * Base64 encoded item stacks. See {@link world.bentobox.deathchest.util.ItemSerializer}.
     */
    @Expose
    private String items;

    /**
     * Required no-args constructor for the database.
     */
    public DeathChestRecord() {
        // Empty
    }

    /**
     * @return true if there is no chest block in the world for this record, so the items
     *         must be claimed with a command
     */
    public boolean isVirtual() {
        return chestLocation == null || chestLocation.isEmpty();
    }

    /**
     * @return the chest block location, or null if this record is virtual or its world is
     *         not loaded
     */
    @Nullable
    public Location getChestLoc() {
        return isVirtual() ? null : Util.getLocationString(chestLocation);
    }

    public void setChestLoc(@Nullable Location location) {
        this.chestLocation = location == null ? "" : Util.getStringLocation(location);
    }

    /**
     * @return the location the player died at, or null if its world is not loaded
     */
    @Nullable
    public Location getDeathLoc() {
        return Util.getLocationString(deathLocation);
    }

    public void setDeathLoc(@Nullable Location location) {
        this.deathLocation = location == null ? "" : Util.getStringLocation(location);
    }

    /**
     * @return owner's UUID, or null if it is unset or unparseable
     */
    @Nullable
    public UUID getOwnerUUID() {
        try {
            return owner == null ? null : UUID.fromString(owner);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setOwnerUUID(UUID uuid) {
        this.owner = uuid == null ? null : uuid.toString();
    }

    /**
     * @param now current time in epoch millis
     * @return true if this chest has an expiry time that has passed
     */
    public boolean isExpired(long now) {
        return expiryTime > 0 && now >= expiryTime;
    }

    // ------------------------------------------------------------------------
    // Plain accessors
    // ------------------------------------------------------------------------

    @Override
    public String getUniqueId() {
        return uniqueId;
    }

    @Override
    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getChestLocation() {
        return chestLocation;
    }

    public void setChestLocation(String chestLocation) {
        this.chestLocation = chestLocation;
    }

    public String getDeathLocation() {
        return deathLocation;
    }

    public void setDeathLocation(String deathLocation) {
        this.deathLocation = deathLocation;
    }

    public String getIslandId() {
        return islandId;
    }

    public void setIslandId(String islandId) {
        this.islandId = islandId;
    }

    public long getDeathTime() {
        return deathTime;
    }

    public void setDeathTime(long deathTime) {
        this.deathTime = deathTime;
    }

    public long getExpiryTime() {
        return expiryTime;
    }

    public void setExpiryTime(long expiryTime) {
        this.expiryTime = expiryTime;
    }

    public int getExperience() {
        return experience;
    }

    public void setExperience(int experience) {
        this.experience = experience;
    }

    public String getItems() {
        return items;
    }

    public void setItems(String items) {
        this.items = items;
    }
}
