package com.smartpark.client.parking;

import com.smartpark.client.net.dto.ApiDtos.SlotInfo;
import com.smartpark.client.parking.SlotMapModel.TileState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotMapModelTest {

    private final SlotMapModel model = new SlotMapModel();
    private final List<SlotInfo> lost = new ArrayList<>();

    @BeforeEach
    void setUp() {
        model.addListener(new SlotMapModel.Listener() {
            @Override
            public void onChanged() {
            }

            @Override
            public void onSelectionLost(SlotInfo slot) {
                lost.add(slot);
            }
        });
        model.load(List.of(
                slot("A-02", "STANDARD", List.of("CAR", "ELECTRIC_CAR"), "AVAILABLE", 0, "A", 2),
                slot("A-01", "STANDARD", List.of("CAR", "ELECTRIC_CAR"), "OCCUPIED", 0, "A", 1),
                slot("M-01", "MOTORCYCLE", List.of("MOTORCYCLE"), "AVAILABLE", 0, "M", 1),
                slot("C-01", "STANDARD", List.of("CAR"), "DISABLED", 1, "C", 1)));
    }

    @Test
    void layoutIsGroupedByFloorAndRowInColumnOrder() {
        var layout = model.layout();
        assertEquals(List.of(0, 1), List.copyOf(layout.keySet()));
        assertEquals(List.of("A", "M"), List.copyOf(layout.get(0).keySet()));
        assertEquals(List.of("A-01", "A-02"), layout.get(0).get("A").stream().map(SlotInfo::slotCode).toList());
    }

    @Test
    void onlyCompatibleFreeSlotsAreSelectable() {
        model.setCategory("CAR");

        assertTrue(model.isSelectable("A-02"));
        assertFalse(model.isSelectable("A-01"));   // occupied
        assertFalse(model.isSelectable("M-01"));   // motorcycle slot
        assertFalse(model.isSelectable("C-01"));   // disabled
        assertEquals(TileState.INCOMPATIBLE, model.stateOf("M-01"));
        assertEquals(TileState.OCCUPIED, model.stateOf("A-01"));
        assertEquals(TileState.DISABLED, model.stateOf("C-01"));
        assertEquals(1, model.freeCount());
    }

    @Test
    void selectingToggles() {
        model.setCategory("CAR");
        assertTrue(model.select("A-02"));
        assertEquals(TileState.SELECTED, model.stateOf("A-02"));
        assertTrue(model.select("A-02"));
        assertTrue(model.selected().isEmpty());
        assertFalse(model.select("M-01"));
    }

    @Test
    void changingCategoryDropsAnIncompatibleSelection() {
        model.setCategory("CAR");
        model.select("A-02");
        model.setCategory("MOTORCYCLE");
        assertTrue(model.selected().isEmpty());
    }

    @Test
    void liveEventTakingTheSelectedSlotClearsItAndNotifies() {
        model.setCategory("CAR");
        model.select("A-02");

        model.apply(slot("A-02", "STANDARD", List.of("CAR", "ELECTRIC_CAR"), "OCCUPIED", 0, "A", 2));

        assertTrue(model.selected().isEmpty());
        assertEquals(List.of("A-02"), lost.stream().map(SlotInfo::slotCode).toList());
        assertEquals(TileState.OCCUPIED, model.stateOf("A-02"));
    }

    @Test
    void releasedSlotBecomesSelectableAgain() {
        model.setCategory("CAR");
        model.apply(slot("A-01", "STANDARD", List.of("CAR", "ELECTRIC_CAR"), "AVAILABLE", 0, "A", 1));
        assertTrue(model.isSelectable("A-01"));
        assertEquals(2, model.freeCount());
    }

    @Test
    void serverAvailabilityIsAuthoritative() {
        model.setCategory("CAR");
        // Server says: no CAR slot is free any more (A-02 was taken and we missed it).
        boolean stale = model.syncAvailability("CAR", List.of());
        assertTrue(stale, "local map showed A-02 free; the caller must reload");

        // Server lists A-01 as free again (released at the exit gate).
        boolean stale2 = model.syncAvailability("CAR", List.of(
                slot("A-01", "STANDARD", List.of("CAR", "ELECTRIC_CAR"), "AVAILABLE", 0, "A", 1),
                slot("A-02", "STANDARD", List.of("CAR", "ELECTRIC_CAR"), "AVAILABLE", 0, "A", 2)));
        assertFalse(stale2);
        assertTrue(model.isSelectable("A-01"));
        assertEquals(2, model.freeCount());
    }

    @Test
    void highlightMarksASlotWithoutSelectingIt() {
        model.highlight("A-01");
        assertEquals(TileState.HIGHLIGHTED, model.stateOf("A-01"));
        assertTrue(model.selected().isEmpty());
    }

    private static SlotInfo slot(String code, String type, List<String> cats, String status, int floor, String row, int col) {
        return new SlotInfo(code, type, cats, status, floor, row, col, null);
    }
}
