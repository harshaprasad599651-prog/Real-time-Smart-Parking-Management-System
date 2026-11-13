package com.smartpark.client.ui.dashboard;

import com.smartpark.client.app.AppContext;
import com.smartpark.client.net.dto.ApiDtos.CategoryStatistics;
import com.smartpark.client.net.dto.ApiDtos.FloorStatistics;
import com.smartpark.client.net.dto.ApiDtos.ParkingStatistics;
import com.smartpark.client.net.dto.ApiDtos.SlotInfo;
import com.smartpark.client.parking.SlotMapModel;
import com.smartpark.client.realtime.RealtimeEvent;
import com.smartpark.client.ui.common.LiveView;
import com.smartpark.client.ui.component.ActivityFeed;
import com.smartpark.client.ui.component.SlotMapView;
import com.smartpark.client.ui.component.StatCard;
import com.smartpark.client.util.Formats;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Set;

import tools.jackson.core.type.TypeReference;

/**
 * Admin overview: occupancy KPIs, availability per floor and per category, and every
 * real-time event (including operators connecting and disconnecting).
 */
public class AdminDashboardController extends LiveView {

    @FXML private Label errorLabel;
    @FXML private Label updatedLabel;
    @FXML private FlowPane kpis;
    @FXML private VBox floorRows;
    @FXML private VBox categoryRows;
    @FXML private StackPane feedHolder;
    @FXML private Label mapSummary;
    @FXML private VBox legendHolder;
    @FXML private StackPane mapHolder;

    private final SlotMapModel map = new SlotMapModel();

    private final StatCard total = new StatCard("Total slots", "accent-slate");
    private final StatCard available = new StatCard("Available", "accent-green");
    private final StatCard occupied = new StatCard("Occupied", "accent-blue");
    private final StatCard occupancy = new StatCard("Occupancy", "accent-amber");
    private final StatCard entries = new StatCard("Entries today", "accent-blue");
    private final StatCard exits = new StatCard("Exits today", "accent-green");
    private final ActivityFeed feed = new ActivityFeed("Live activity", Set.of());

    public AdminDashboardController(AppContext context) {
        super(context);
    }

    @FXML
    private void initialize() {
        kpis.getChildren().addAll(total, available, occupied, occupancy, entries, exits);
        feedHolder.getChildren().add(feed);
        mapHolder.getChildren().add(new SlotMapView(map, false));
        legendHolder.getChildren().add(SlotMapView.legend(false));
        map.addListener(() -> mapSummary.setText(map.count("AVAILABLE") + " free  ·  " + map.count("OCCUPIED")
                + " occupied  ·  " + map.count("DISABLED") + " disabled"));
        hide(errorLabel);
    }

    @Override
    protected void refresh() {
        load(context.api().get("/api/statistics", ParkingStatistics.class), this::show, errorLabel);
        load(context.api().get("/api/slots", new TypeReference<List<SlotInfo>>() { }), map::load, errorLabel);
    }

    @Override
    protected void onEvent(RealtimeEvent event) {
        feed.add(event);
        if (event.payload() != null && event.payload().hasNonNull("slot")) {
            map.apply(context.api().json().treeToValue(event.payload().get("slot"), SlotInfo.class)); // instant
        }
        if (isParkingEvent(event)) {
            refreshSoon(); // statistics (and the map, if several slots changed)
        }
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    private void show(ParkingStatistics s) {
        total.setValue(String.valueOf(s.totalSlots()));
        total.setHint(s.disabledSlots() + " disabled");
        available.setValue(String.valueOf(s.availableSlots()));
        occupied.setValue(String.valueOf(s.occupiedSlots()));
        occupied.setHint(s.activeSessions() + " active sessions");
        occupancy.setValue(Formats.percent(s.occupancyRatePercent()));
        occupancy.setHint("of usable slots");
        entries.setValue(String.valueOf(s.entriesToday()));
        exits.setValue(String.valueOf(s.exitsToday()));
        exits.setHint("avg stay " + Formats.minutes(s.averageStayMinutesToday()));

        floorRows.getChildren().setAll(s.floors().stream().map(this::floorRow).toList());
        categoryRows.getChildren().setAll(s.categories().stream().map(this::categoryRow).toList());
        updatedLabel.setText("Updated " + Formats.time(s.generatedAt()));
    }

    private VBox floorRow(FloorStatistics f) {
        long usable = f.totalSlots() - f.disabledSlots();
        return UsageRow.of("Floor " + f.floor(),
                f.availableSlots() + " free · " + f.occupiedSlots() + " occupied"
                        + (f.disabledSlots() > 0 ? " · " + f.disabledSlots() + " disabled" : ""),
                usable == 0 ? 0 : (double) f.occupiedSlots() / usable);
    }

    private VBox categoryRow(CategoryStatistics c) {
        return UsageRow.of(c.categoryName().replace('_', ' '),
                c.availableSlots() + " of " + c.compatibleSlots() + " slots free · " + c.parkedVehicles() + " parked",
                c.compatibleSlots() == 0 ? 0 : 1.0 - (double) c.availableSlots() / c.compatibleSlots());
    }
}
