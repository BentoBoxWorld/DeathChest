package world.bentobox.deathchest.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import world.bentobox.bentobox.api.events.island.IslandDeleteEvent;
import world.bentobox.bentobox.api.events.island.IslandPreclearEvent;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.deathchest.CommonTestSetup;
import world.bentobox.deathchest.DeathChest;
import world.bentobox.deathchest.data.DeathChestManager;

/**
 * Death chest records must not outlive the island they sit on.
 */
class IslandListenerTest extends CommonTestSetup {

    @Mock
    private DeathChest addon;
    @Mock
    private DeathChestManager manager;

    private IslandListener listener;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        when(addon.getManager()).thenReturn(manager);
        listener = new IslandListener(addon);
    }

    @Test
    void testIslandDeleteClearsItsChests() {
        listener.onIslandDelete(new IslandDeleteEvent(island, UUID.randomUUID(), false, location));

        verify(manager).removeIslandChests(island);
    }

    @Test
    void testIslandDeleteWithNoIslandIsSafe() {
        listener.onIslandDelete(new IslandDeleteEvent(null, UUID.randomUUID(), false, location));

        verify(manager, never()).removeIslandChests(any());
    }

    @Test
    void testIslandResetClearsTheOldIslandsChests() {
        // The event copies the island it is given, so check by id rather than by identity
        Island oldIsland = new Island(location, UUID.randomUUID(), 100);

        listener.onIslandPreclear(
                new IslandPreclearEvent(island, UUID.randomUUID(), false, location, oldIsland));

        ArgumentCaptor<Island> captor = ArgumentCaptor.captor();
        verify(manager).removeIslandChests(captor.capture());
        assertEquals(oldIsland.getUniqueId(), captor.getValue().getUniqueId());
    }
}
