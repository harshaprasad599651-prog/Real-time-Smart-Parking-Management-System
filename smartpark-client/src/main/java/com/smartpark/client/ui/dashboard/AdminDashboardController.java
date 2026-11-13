package com.smartpark.client.ui.dashboard;

import com.smartpark.client.app.AppContext;
import com.smartpark.client.net.dto.ApiDtos.ParkingSession;
import com.smartpark.client.net.dto.ApiDtos.ParkingStatistics;
import com.smartpark.client.net.dto.ApiDtos.SessionPage;
import com.smartpark.client.parking.HourlyTraffic;
import com.smartpark.client.parking.SlotMapModel;
import com.smartpark.client.realtime.RealtimeEvent;
import com.smartpark.client.service.ParkingService;
import com.smartpark.client.ui.common.LiveView;
import com.smartpark.client.ui.component.ActivityFeed;
import com.smartpark.client.ui.component.SlotMapView;
import com.smartpark.client.ui.component.StatCard;
import com.smartpark.client.util.Formats;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin overview, kept live without manual refreshing:
 * <ul>
 *     <li>KPIs: total, available, occupied, disabled slots, vehicles parked, entries and exits today;</li>
 *     <li>charts: slot status (pie) and entries/exits per hour today (bars);</li>
 *     <li>live map with the vehicle number on every occupied slot;</li>
 *     <li>activity feed of every terminal.</li>
 * </ul>
 * Slot events update the map tile instantly. KPIs and charts reload (debounced) after parking events.
 */
public class AdminDashboardController extends LiveView {

    private static final Set<String> FEED_TYPES = Set.of("VEHICLE_ENTERED", "VEHICLE_EXITED", "SLOT_OCCUPIED",
            "SLOT_RELEASED", "PARKING_MAP_UPDATED", "OPERATOR_CONNECTED", "OPERATOR_DISCONNECTED");

    @FXML private Label errorLabel;
    @FXML private Label updatedLabel;
    @FXML private FlowPane kpis;
    @FXML private PieChart statusChart;
    @FXML private BarChart<String, Number> trafficChart;
    @FXML private Label mapSummary;
    @FXML private VBox legendHolder;
    @FXML private StackPane mapHolder;
    @FXML private StackPane feedHolder;

    private final ParkingService parking;
    private final SlotMapModel map = new SlotMapModel();
    private final StatCard total = new StatCard("Total slots", "accent-slate");
    private final StatCard available = new StatCard("Available", "accent-green");
    private final StatCard occupied = new StatCard("Occupied", "accent-red");
    private final StatCard disabled = new StatCard("Disabled", "accent-slate");
    private final StatCard parked = new StatCard("Vehicles parked", "accent-blue");
    private final StatCard entries = new StatCard("Entries today", "accent-blue");
    private final StatCard exits = new StatCard("Exits today", "accent-green");
    private final ActivityFeed feed = new ActivityFeed("Live activity", FEED_TYPES);

    public AdminDashboardController(AppContext context) {
        super(context);
        this.parking = context.parking();
    }

    @FXML
    private void initialize() {
        kpis.getChildren().addAll(total, available, occupied, disabled, parked, entries, exits);
        mapHolder.getChildren().add(new SlotMapView(map, false, true));
        legendHolder.getChildren().add(SlotMapView.legend(false));
        feedHolder.getChildren().add(feed);
        map.addListener(() -> mapSummary.setText(map.count("AVAILABLE") + " free  ·  " + map.count("OCCUPIED")
                + " occupied  ·  " + map.count("DISABLED") + " disabled"));
        statusChart.setLegendVisible(true);
        statusChart.setLabelsVisible(false);
        trafficChart.setAnimated(false);
        statusChart.setAnimated(false);
        hide(errorLabel);
    }

    @Override
    protected void refresh() {
        load(context.admin().statistics(), this::showStatistics, errorLabel);
        load(parking.allSlots(), map::load, errorLabel);
        load(parking.activeSessions(), sessions -> map.setOccupants(sessions.stream()
                .collect(Collectors.toMap(ParkingSession::parkingSlotCode, ParkingSession::vehicleNumber, (a, b) -> a))),
                errorLabel);
        // Sessions that entered since yesterday cover every entry and exit of today (up to 200 records).
        Instant since = LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        load(context.admin().sessions(null, null, since, null, 0, 200), this::showTraffic, errorLabel);
    }

    @Override
    protected void onEvent(RealtimeEvent e) {
        feed.add(e);
        switch (e.type()) {
            case "SLOT_OCCUPIED" -> parking.slotOf(e).ifPresent(slot -> {
                map.apply(slot);
                map.setOccupant(slot.slotCode(), e.payloadText("vehicleNumber"));
            });
            case "SLOT_RELEASED" -> parking.slotOf(e).ifPresent(slot -> {
                map.apply(slot);
                map.setOccupant(slot.slotCode(), null);
            });
            case "PARKING_MAP_UPDATED" -> parking.slotOf(e).ifPresentOrElse(map::apply, this::refreshSoon);
            default -> { }
        }
        if (isParkingEvent(e)) {
            refreshSoon(); // KPIs and charts
        }
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    private void showStatistics(ParkingStatistics s) {
        total.setValue(String.valueOf(s.totalSlots()));
        total.setHint(Formats.percent(s.occupancyRatePercent()) + " occupancy");
        available.setValue(String.valueOf(s.availableSlots()));
        occupied.setValue(String.valueOf(s.occupiedSlots()));
        disabled.setValue(String.valueOf(s.disabledSlots()));
        parked.setValue(String.valueOf(s.activeSessions()));
        entries.setValue(String.valueOf(s.entriesToday()));
        exits.setValue(String.valueOf(s.exitsToday()));
        exits.setHint("avg stay " + Formats.minutes(s.averageStayMinutesToday()));

        statusChart.setData(FXCollections.observableArrayList(
                new PieChart.Data("Available (" + s.availableSlots() + ")", s.availableSlots()),
                new PieChart.Data("Occupied (" + s.occupiedSlots() + ")", s.occupiedSlots()),
                new PieChart.Data("Disabled (" + s.disabledSlots() + ")", s.disabledSlots())));
        updatedLabel.setText("Updated " + Formats.time(s.generatedAt()));
    }

    private void showTraffic(SessionPage page) {
        HourlyTraffic traffic = HourlyTraffic.today(page.content(), ZoneId.systemDefault(),
                Instant.now().truncatedTo(ChronoUnit.SECONDS));
        XYChart.Series<String, Number> in = new XYChart.Series<>();
        in.setName("Entries");
        XYChart.Series<String, Number> out = new XYChart.Series<>();
        out.setName("Exits");
        for (Map.Entry<Integer, int[]> h : traffic.byHour().entrySet()) {
            String label = "%02d:00".formatted(h.getKey());
            in.getData().add(new XYChart.Data<>(label, h.getValue()[0]));
            out.getData().add(new XYChart.Data<>(label, h.getValue()[1]));
        }
        trafficChart.setData(FXCollections.observableArrayList(List.of(in, out)));
    }
}
