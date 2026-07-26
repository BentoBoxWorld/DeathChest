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

    private DeathChestRecord record;
    private World gameWorld;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        gameWorld = mock(World.class);
        when(gameWorld.getName()).thenReturn("bskyblock_world");
        mockedBukkit.when(() -> Bukkit.getWorld(anyString())).thenReturn(gameWorld);
        record = new DeathChestRecord();
    }

    @Test
    void testNewRecordHasAUniqueId() {
        assertNotNull(record.getUniqueId());
        assertFalse(record.getUniqueId().isEmpty());
        assertFalse(record.getUniqueId().equals(new DeathChestRecord().getUniqueId()));
    }

    @Test
    void testNewRecordIsVirtual() {
        assertTrue(record.isVirtual());
        assertNull(record.getChestLoc());
    }

    @Test
    void testChestLocationRoundTrip() {
        record.setChestLoc(new Location(gameWorld, 12, 70, -34));

        assertFalse(record.isVirtual());
        Location back = record.getChestLoc();
        assertNotNull(back);
        assertEquals(12, back.getBlockX());
        assertEquals(70, back.getBlockY());
        assertEquals(-34, back.getBlockZ());
    }

    @Test
    void testNullChestLocationMakesItVirtualAgain() {
        record.setChestLoc(new Location(gameWorld, 1, 2, 3));
        record.setChestLoc(null);
        assertTrue(record.isVirtual());
    }

    @Test
    void testDeathLocationRoundTrip() {
        record.setDeathLoc(new Location(gameWorld, -5, -120, 7));
        Location back = record.getDeathLoc();
        assertNotNull(back);
        assertEquals(-5, back.getBlockX());
        assertEquals(-120, back.getBlockY());
        assertEquals(7, back.getBlockZ());
    }

    @Test
    void testOwnerUuidRoundTrip() {
        UUID owner = UUID.randomUUID();
        record.setOwnerUUID(owner);
        assertEquals(owner, record.getOwnerUUID());
        assertEquals(owner.toString(), record.getOwner());
    }

    @Test
    void testUnparseableOwnerIsNullNotAnException() {
        record.setOwner("this-is-not-a-uuid");
        assertNull(record.getOwnerUUID());
    }

    @Test
    void testNullOwnerIsNull() {
        record.setOwnerUUID(null);
        assertNull(record.getOwnerUUID());
    }

    @Test
    void testZeroExpiryNeverExpires() {
        record.setExpiryTime(0);
        assertFalse(record.isExpired(Long.MAX_VALUE));
    }

    @Test
    void testExpiry() {
        record.setExpiryTime(1000L);
        assertFalse(record.isExpired(999L));
        assertTrue(record.isExpired(1000L));
        assertTrue(record.isExpired(1001L));
    }
}
