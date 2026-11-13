package com.smartpark.client.ui.component;

import com.smartpark.client.net.dto.ApiDtos.SlotInfo;
import com.smartpark.client.parking.SlotMapModel;
import com.smartpark.client.parking.SlotMapModel.TileState;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Graphical parking map drawn from a {@link SlotMapModel}: one section per floor, one line per
 * row, one tile per slot, coloured by state. It redraws itself whenever the model changes
 * (REST reload or real-time event), so every terminal shows the same picture.
 *
 * <pre>
 *  Floor 0
 *   A  [A-01][A-02][A-03] ...
 *   B  [B-01][B-02] ...
 * </pre>
 */
public final class SlotMapView extends VBox {

    private final SlotMapModel model;
    private final boolean selectable;
    private final boolean showOccupants;

    /** @param selectable true on the entry screen (tiles can be picked), false for read-only maps */
    public SlotMapView(SlotMapModel model, boolean selectable) {
        this(model, selectable, false);
    }

    /**
     * @param showOccupants true on the admin map: occupied tiles show the parked vehicle's number
     *                      instead of the slot type
     */
    public SlotMapView(SlotMapModel model, boolean selectable, boolean showOccupants) {
        super(18);
        this.model = model;
        this.selectable = selectable;
        this.showOccupants = showOccupants;
        getStyleClass().add("slot-map");
        model.addListener(this::render);
        render();
    }

    private void render() {
        getChildren().clear();
        if (model.isEmpty()) {
            Label empty = new Label("Loading parking map...");
            empty.getStyleClass().add("muted");
            getChildren().add(empty);
            return;
        }
        for (Map.Entry<Integer, Map<String, List<SlotInfo>>> floor : model.layout().entrySet()) {
            Label title = new Label("FLOOR " + floor.getKey());
            title.getStyleClass().add("floor-title");
            VBox rows = new VBox(8);
            for (Map.Entry<String, List<SlotInfo>> row : floor.getValue().entrySet()) {
                Label rowLabel = new Label(row.getKey());
                rowLabel.getStyleClass().add("row-label");
                rowLabel.setMinWidth(Region.USE_PREF_SIZE);
                FlowPane tiles = new FlowPane(8, 8);
                row.getValue().forEach(s -> tiles.getChildren().add(tile(s)));
                HBox line = new HBox(12, rowLabel, tiles);
                line.setAlignment(Pos.TOP_LEFT);
                rows.getChildren().add(line);
            }
            getChildren().add(new VBox(10, title, rows));
        }
    }

    private Button tile(SlotInfo s) {
        TileState state = model.stateOf(s.slotCode());
        Label code = new Label(s.slotCode());
        code.getStyleClass().add("tile-code");
        String occupant = showOccupants ? model.occupantOf(s.slotCode()).orElse(null) : null;
        Label type = new Label(occupant != null ? occupant : shortType(s.slotType()));
        type.getStyleClass().add(occupant != null ? "tile-vehicle" : "tile-type");
        VBox content = new VBox(1, code, type);
        content.setAlignment(Pos.CENTER);

        Button b = new Button();
        b.setGraphic(content);
        b.getStyleClass().addAll("slot-tile", "slot-" + state.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        if (showOccupants) {
            b.getStyleClass().add("slot-tile-wide");
        }
        b.setAccessibleText(s.slotCode());   // screen readers and UI automation see "A-01"
        b.setFocusTraversable(selectable && state == TileState.FREE);
        b.setTooltip(new Tooltip(s.slotCode() + " · " + s.slotType().replace('_', ' ') + " · " + s.status()
                + (occupant != null ? "\nVehicle: " + occupant + " · ticket " + s.currentTicketId() : "")
                + "\nAccepts: " + String.join(", ", s.supportedVehicleCategories())));
        if (selectable) {
            b.setOnAction(e -> model.select(s.slotCode()));
            b.setDisable(!(state == TileState.FREE || state == TileState.SELECTED));
        } else {
            b.setMouseTransparent(true);
        }
        return b;
    }

    private static String shortType(String type) {
        return switch (type) {
            case "STANDARD" -> "STD";
            case "LARGE" -> "LARGE";
            case "MOTORCYCLE" -> "MOTO";
            case "EV_CHARGING" -> "EV";
            case "ACCESSIBLE" -> "ACC";
            default -> type;
        };
    }

    /** The colour key shown above a map. */
    public static javafx.scene.layout.FlowPane legend(boolean withSelection) {
        javafx.scene.layout.FlowPane legend = new javafx.scene.layout.FlowPane(16, 6);
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.getStyleClass().add("map-legend");
        if (withSelection) {
            legend.getChildren().addAll(item("slot-free", "Available · fits this vehicle"),
                    item("slot-incompatible", "Available · other vehicles"));
        } else {
            legend.getChildren().add(item("slot-free", "Available"));
        }
        legend.getChildren().addAll(item("slot-occupied", "Occupied"), item("slot-disabled", "Disabled"));
        legend.getChildren().add(withSelection ? item("slot-selected", "Selected") : item("slot-highlighted", "This vehicle"));
        return legend;
    }

    private static HBox item(String style, String text) {
        Region swatch = new Region();
        swatch.getStyleClass().addAll("legend-swatch", style);
        Label label = new Label(text);
        label.getStyleClass().add("legend-text");
        HBox box = new HBox(6, swatch, label);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }
}
