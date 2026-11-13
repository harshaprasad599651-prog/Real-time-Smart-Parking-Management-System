package com.smartpark.client.parking;

import com.smartpark.client.net.dto.ApiDtos.SlotInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * State behind the graphical parking map (no JavaFX code, so it is unit-tested directly).
 * <ul>
 *     <li>holds every slot, kept current by REST loads and by real-time slot events;</li>
 *     <li>knows the vehicle category being parked, so only <b>compatible free</b> slots are selectable;</li>
 *     <li>keeps the operator's selection, and drops it, telling the listener, if a live event shows
 *         that another terminal has just taken or disabled that slot.</li>
 * </ul>
 */
public final class SlotMapModel {

    /** How a tile is drawn. */
    public enum TileState { FREE, INCOMPATIBLE, OCCUPIED, DISABLED, SELECTED, HIGHLIGHTED }

    public interface Listener {
        /** Anything changed: redraw. */
        void onChanged();

        /** The selected slot is no longer available (taken or disabled by someone else). */
        default void onSelectionLost(SlotInfo slot) {
        }
    }

    private static final Comparator<SlotInfo> MAP_ORDER = Comparator.comparingInt(SlotInfo::floor)
            .thenComparing(SlotInfo::row).thenComparingInt(SlotInfo::column);

    private final Map<String, SlotInfo> slots = new LinkedHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private String category;
    private String selected;
    private String highlighted;

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    // ------------------------------------------------------------------- data

    /** Replaces all slots (after a REST load). Keeps the selection only if it is still selectable. */
    public void load(Collection<SlotInfo> all) {
        slots.clear();
        all.stream().sorted(MAP_ORDER).forEach(s -> slots.put(s.slotCode(), s));
        if (selected != null && !isSelectable(selected)) {
            selected = null;
        }
        changed();
    }

    /**
     * Applies the <b>server's</b> list of available slots for {@code category}
     * ({@code GET /api/slots/available?category=}).
     *
     * @return true if the local map was out of date (it showed a compatible slot as free that the
     *         server no longer lists); the caller should then reload the whole map
     */
    public boolean syncAvailability(String category, Collection<SlotInfo> serverAvailable) {
        java.util.Set<String> codes = new java.util.HashSet<>();
        for (SlotInfo s : serverAvailable) {
            codes.add(s.slotCode());
            put(s);
        }
        boolean stale = slots.values().stream()
                .anyMatch(s -> s.isAvailable() && s.accepts(category) && !codes.contains(s.slotCode()));
        changed();
        return stale;
    }

    /** Applies one slot from a real-time event. */
    public void apply(SlotInfo updated) {
        if (updated == null) {
            return;
        }
        put(updated);
        changed();
    }

    /** Stores one slot (keeping map order) and drops the selection if that slot is no longer selectable. */
    private void put(SlotInfo updated) {
        boolean isNew = !slots.containsKey(updated.slotCode());
        slots.put(updated.slotCode(), updated);
        if (isNew) { // keep map order when a slot is added
            List<SlotInfo> all = new ArrayList<>(slots.values());
            slots.clear();
            all.stream().sorted(MAP_ORDER).forEach(s -> slots.put(s.slotCode(), s));
        }
        if (updated.slotCode().equals(selected) && !isSelectable(selected)) {
            selected = null;
            listeners.forEach(l -> l.onSelectionLost(updated));
        }
    }

    // -------------------------------------------------------------- category

    /** Sets the vehicle category being parked; clears a selection the new category cannot use. */
    public void setCategory(String category) {
        this.category = category;
        if (selected != null && !isSelectable(selected)) {
            selected = null;
        }
        changed();
    }

    public String category() {
        return category;
    }

    // ------------------------------------------------------------- selection

    public boolean isSelectable(String slotCode) {
        SlotInfo s = slots.get(slotCode);
        return s != null && s.isAvailable() && s.accepts(category);
    }

    /** Selects a slot if it is selectable; selecting the selected slot again unselects it. */
    public boolean select(String slotCode) {
        if (slotCode.equals(selected)) {
            selected = null;
            changed();
            return true;
        }
        if (!isSelectable(slotCode)) {
            return false;
        }
        selected = slotCode;
        changed();
        return true;
    }

    public void clearSelection() {
        if (selected != null) {
            selected = null;
            changed();
        }
    }

    public Optional<SlotInfo> selected() {
        return Optional.ofNullable(selected).map(slots::get);
    }

    /** Marks one slot (e.g. the exit operator's vehicle) without making it selectable. */
    public void highlight(String slotCode) {
        highlighted = slotCode;
        changed();
    }

    // ---------------------------------------------------------------- views

    public TileState stateOf(String slotCode) {
        SlotInfo s = slots.get(slotCode);
        if (slotCode.equals(selected)) {
            return TileState.SELECTED;
        }
        if (slotCode.equals(highlighted)) {
            return TileState.HIGHLIGHTED;
        }
        return switch (s.status()) {
            case "OCCUPIED" -> TileState.OCCUPIED;
            case "DISABLED" -> TileState.DISABLED;
            default -> category == null || s.accepts(category) ? TileState.FREE : TileState.INCOMPATIBLE;
        };
    }

    /** Free slots for the current category (all free slots if no category is set). */
    public long freeCount() {
        return slots.values().stream().filter(s -> s.isAvailable() && (category == null || s.accepts(category))).count();
    }

    public long count(String status) {
        return slots.values().stream().filter(s -> status.equals(s.status())).count();
    }

    /** Floor -> row -> slots ordered by column, for drawing. */
    public Map<Integer, Map<String, List<SlotInfo>>> layout() {
        Map<Integer, Map<String, List<SlotInfo>>> floors = new TreeMap<>();
        for (SlotInfo s : slots.values()) {
            floors.computeIfAbsent(s.floor(), f -> new TreeMap<>())
                    .computeIfAbsent(s.row(), r -> new ArrayList<>()).add(s);
        }
        return floors;
    }

    public Optional<SlotInfo> get(String slotCode) {
        return Optional.ofNullable(slots.get(slotCode));
    }

    public boolean isEmpty() {
        return slots.isEmpty();
    }

    private void changed() {
        listeners.forEach(Listener::onChanged);
    }
}
