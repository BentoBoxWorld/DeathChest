package world.bentobox.deathchest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.deathchest.Settings.ExpiryAction;

/**
 * Tests the settings defaults and the clamping done by the getters.
 */
class SettingsTest {

    private Settings settings;

    @BeforeEach
    void setUp() {
        settings = new Settings();
    }

    @Test
    void testDefaults() {
        assertTrue(settings.getDisabledGameModes().isEmpty());
        assertEquals("CHEST", settings.getChestMaterial());
        assertTrue(settings.isPlaceAtDeathLocation());
        assertEquals(8, settings.getSearchRadius());
        assertEquals(16, settings.getSearchDepth());
        assertEquals(60, settings.getExpiryMinutes());
        assertEquals(ExpiryAction.DROP, settings.getExpiryAction());
        assertEquals(60, settings.getExpiryCheckSeconds());
        assertEquals(3, settings.getMaxChestsPerPlayer());
        assertTrue(settings.isTeamAccess());
        assertTrue(settings.isProtectFromExplosions());
        assertTrue(settings.isStoreExperience());
        assertEquals(100, settings.getExperiencePercent());
        assertTrue(settings.isNotifyOnDeath());
        assertTrue(settings.isAllowTeleport());
    }

    @Test
    void testSearchRadiusIsClamped() {
        settings.setSearchRadius(0);
        assertEquals(1, settings.getSearchRadius());
        settings.setSearchRadius(1000);
        assertEquals(32, settings.getSearchRadius());
        settings.setSearchRadius(12);
        assertEquals(12, settings.getSearchRadius());
    }

    @Test
    void testSearchDepthIsClamped() {
        settings.setSearchDepth(0);
        assertEquals(1, settings.getSearchDepth());
        settings.setSearchDepth(1000);
        assertEquals(64, settings.getSearchDepth());
        settings.setSearchDepth(24);
        assertEquals(24, settings.getSearchDepth());
    }

    @Test
    void testExperiencePercentIsClamped() {
        settings.setExperiencePercent(-10);
        assertEquals(0, settings.getExperiencePercent());
        settings.setExperiencePercent(500);
        assertEquals(100, settings.getExperiencePercent());
    }

    @Test
    void testNegativeMinutesAndCountsAreFloored() {
        settings.setExpiryMinutes(-5);
        assertEquals(0, settings.getExpiryMinutes());
        settings.setMaxChestsPerPlayer(-1);
        assertEquals(0, settings.getMaxChestsPerPlayer());
    }

    @Test
    void testExpiryCheckHasAMinimum() {
        settings.setExpiryCheckSeconds(1);
        assertEquals(5, settings.getExpiryCheckSeconds());
    }

    @Test
    void testNullExpiryActionFallsBackToDrop() {
        settings.setExpiryAction(null);
        assertEquals(ExpiryAction.DROP, settings.getExpiryAction());
    }

    @Test
    void testSetters() {
        settings.setChestMaterial("BARREL");
        assertEquals("BARREL", settings.getChestMaterial());
        settings.setPlaceAtDeathLocation(false);
        assertFalse(settings.isPlaceAtDeathLocation());
        settings.setTeamAccess(false);
        assertFalse(settings.isTeamAccess());
        settings.setProtectFromExplosions(false);
        assertFalse(settings.isProtectFromExplosions());
        settings.setStoreExperience(false);
        assertFalse(settings.isStoreExperience());
        settings.setNotifyOnDeath(false);
        assertFalse(settings.isNotifyOnDeath());
        settings.setAllowTeleport(false);
        assertFalse(settings.isAllowTeleport());
        settings.setExpiryAction(ExpiryAction.DELETE);
        assertEquals(ExpiryAction.DELETE, settings.getExpiryAction());
    }
}
