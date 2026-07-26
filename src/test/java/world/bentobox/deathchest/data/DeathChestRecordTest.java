package world.bentobox.deathchest.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.deathchest.CommonTestSetup;

/**
 * Tests the death chest record's derived state - virtual, expiry and location round trips.
 */
class DeathChestRecordTest extends CommonTestSetup {

    private DeathChestRecord chest;
    private World gameWorld;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        gameWorld = mock(World.class);
        when(gameWorld.getName()).thenReturn("bskyblock_world");
        mockedBukkit.when(() -> Bukkit.getWorld(anyString())).thenReturn(gameWorld);
        chest = new DeathChestRecord();
    }

    @Test
    void testNewRecordHasAUniqueId() {
        assertNotNull(chest.getUniqueId());
        assertFalse(chest.getUniqueId().isEmpty());
        assertFalse(chest.getUniqueId().equals(new DeathChestRecord().getUniqueId()));
    }

    @Test
    void testNewRecordIsVirtual() {
        assertTrue(chest.isVirtual());
        assertNull(chest.getChestLoc());
    }

    @Test
    void testChestLocationRoundTrip() {
        chest.setChestLoc(new Location(gameWorld, 12, 70, -34));

        assertFalse(chest.isVirtual());
        Location back = chest.getChestLoc();
        assertNotNull(back);
        assertEquals(12, back.getBlockX());
        assertEquals(70, back.getBlockY());
        assertEquals(-34, back.getBlockZ());
    }

    @Test
    void testNullChestLocationMakesItVirtualAgain() {
        chest.setChestLoc(new Location(gameWorld, 1, 2, 3));
        chest.setChestLoc(null);
        assertTrue(chest.isVirtual());
    }

    @Test
    void testDeathLocationRoundTrip() {
        chest.setDeathLoc(new Location(gameWorld, -5, -120, 7));
        Location back = chest.getDeathLoc();
        assertNotNull(back);
        assertEquals(-5, back.getBlockX());
        assertEquals(-120, back.getBlockY());
        assertEquals(7, back.getBlockZ());
    }

    @Test
    void testOwnerUuidRoundTrip() {
        UUID owner = UUID.randomUUID();
        chest.setOwnerUUID(owner);
        assertEquals(owner, chest.getOwnerUUID());
        assertEquals(owner.toString(), chest.getOwner());
    }

    @Test
    void testUnparseableOwnerIsNullNotAnException() {
        chest.setOwner("this-is-not-a-uuid");
        assertNull(chest.getOwnerUUID());
    }

    @Test
    void testNullOwnerIsNull() {
        chest.setOwnerUUID(null);
        assertNull(chest.getOwnerUUID());
    }

    @Test
    void testZeroExpiryNeverExpires() {
        chest.setExpiryTime(0);
        assertFalse(chest.isExpired(Long.MAX_VALUE));
    }

    @Test
    void testExpiry() {
        chest.setExpiryTime(1000L);
        assertFalse(chest.isExpired(999L));
        assertTrue(chest.isExpired(1000L));
        assertTrue(chest.isExpired(1001L));
    }
}
