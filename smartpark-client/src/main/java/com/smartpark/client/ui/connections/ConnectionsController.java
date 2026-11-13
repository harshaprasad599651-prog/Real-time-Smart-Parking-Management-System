package com.smartpark.client.ui.connections;

import com.smartpark.client.app.AppContext;
import com.smartpark.client.net.dto.ApiDtos.ConnectionInfo;
import com.smartpark.client.net.dto.ApiDtos.RealtimeStatus;
import com.smartpark.client.realtime.RealtimeEvent;
import com.smartpark.client.ui.common.LiveView;
import com.smartpark.client.ui.component.ActivityFeed;
import com.smartpark.client.ui.component.StatCard;
import com.smartpark.client.util.Formats;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Admin screen: which terminals are connected to the real-time server right now ({@code GET /api/realtime/status}). */
public class ConnectionsController extends LiveView {

    @FXML private Label errorLabel;
    @FXML private Label updatedLabel;
    @FXML private FlowPane kpis;
    @FXML private TableView<ConnectionInfo> clientTable;
    @FXML private StackPane feedHolder;

    private final ObservableList<ConnectionInfo> clients = FXCollections.observableArrayList();
    private final StatCard connected = new StatCard("Connected terminals", "accent-green");
    private final StatCard sequence = new StatCard("Events broadcast", "accent-blue");
    private final StatCard instance = new StatCard("Server instance", "accent-slate");
    private final ActivityFeed feed = new ActivityFeed("Connection log", Set.of("OPERATOR_CONNECTED", "OPERATOR_DISCONNECTED"));

    public ConnectionsController(AppContext context) {
        super(context);
    }

    @FXML
    private void initialize() {
        kpis.getChildren().addAll(connected, sequence, instance);
        feedHolder.getChildren().add(feed);
        hide(errorLabel);
        clientTable.setItems(clients);
        clientTable.getColumns().setAll(List.of(
                column("Operator", ConnectionInfo::operator, 80),
                column("Role", c -> c.role() == null ? "" : c.role().name().replace("_OPERATOR", ""), 70),
                column("Terminal", ConnectionInfo::terminal, 115),
                column("Since", c -> Formats.time(c.connectedAt()), 75),
                column("Heartbeat", c -> Formats.time(c.lastSeenAt()), 85),
                column("Address", c -> c.remoteAddress() == null ? "" : c.remoteAddress().replaceFirst("^/", ""), 115)));
        clientTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        clientTable.setPlaceholder(new Label("No terminals connected"));
    }

    @Override
    protected void refresh() {
        load(context.api().get("/api/realtime/status", RealtimeStatus.class), this::show, errorLabel);
    }

    @Override
    protected void onEvent(RealtimeEvent event) {
        feed.add(event);
        sequence.setValue(String.valueOf(event.sequence()));
        if (event.type().startsWith("OPERATOR_")) {
            refreshSoon();
        }
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    private void show(RealtimeStatus s) {
        clients.setAll(s.clients());
        connected.setValue(String.valueOf(s.connectedClients()));
        sequence.setValue(String.valueOf(s.currentSequence()));
        instance.setValue(s.serverInstanceId());
        instance.setHint("changes when the server restarts");
        updatedLabel.setText("Updated " + Formats.time(java.time.Instant.now()));
    }

    private static TableColumn<ConnectionInfo, String> column(String title, Function<ConnectionInfo, String> value,
                                                              double width) {
        TableColumn<ConnectionInfo, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        c.setPrefWidth(width);
        c.setMinWidth(width); // never truncate; the last column absorbs extra space
        return c;
    }
}
