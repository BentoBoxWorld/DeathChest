package world.bentobox.deathchest.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.deathchest.CommonTestSetup;
import world.bentobox.deathchest.DeathChest;

/**
 * Players see game mode names, not raw world names.
 */
class WorldNameTest extends CommonTestSetup {

    @Mock
    private DeathChest addon;
    @Mock
    private User user;

    private World gameWorld;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        when(addon.getPlugin()).thenReturn(plugin);
        when(iwm.getFriendlyName(any(World.class))).thenReturn("AcidIsland");
        when(user.getTranslation("deathchest.world.nether")).thenReturn("Nether");
        when(user.getTranslation("deathchest.world.the-end")).thenReturn("The End");
        gameWorld = mock(World.class);
        when(gameWorld.getName()).thenReturn("acidisland_world");
    }

    @Test
    void testOverworldIsJustTheGameModeName() {
        when(gameWorld.getEnvironment()).thenReturn(Environment.NORMAL);
        assertEquals("AcidIsland", WorldName.of(addon, user, gameWorld));
    }

    @Test
    void testNetherIsNamed() {
        when(gameWorld.getEnvironment()).thenReturn(Environment.NETHER);
        assertEquals("AcidIsland Nether", WorldName.of(addon, user, gameWorld));
    }

    @Test
    void testEndIsNamed() {
        when(gameWorld.getEnvironment()).thenReturn(Environment.THE_END);
        assertEquals("AcidIsland The End", WorldName.of(addon, user, gameWorld));
    }

    @Test
    void testColourCodesInAFriendlyNameAreStripped() {
        when(iwm.getFriendlyName(any(World.class))).thenReturn("§aAcid§bIsland");
        when(gameWorld.getEnvironment()).thenReturn(Environment.NORMAL);
        assertEquals("AcidIsland", WorldName.of(addon, user, gameWorld));
    }

    @Test
    void testNullWorldIsEmpty() {
        assertEquals("", WorldName.of(addon, user, null));
    }

    @Test
    void testDescribeAddsCoordinates() {
        when(gameWorld.getEnvironment()).thenReturn(Environment.NETHER);
        Location loc = new Location(gameWorld, 12, 64, -30);
        assertEquals("AcidIsland Nether 12, 64, -30", WorldName.describe(addon, user, loc));
    }

    @Test
    void testDescribeOfNothing() {
        assertEquals("-", WorldName.describe(addon, user, null));
    }
}
