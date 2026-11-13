package com.smartpark.client.ui.component;

import com.smartpark.client.realtime.RealtimeEvent;
import com.smartpark.client.util.Formats;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Set;

/**
 * "Live activity" card: real-time events, newest first, each with a coloured tag.
 * Keeps at most {@value #MAX_ITEMS} entries.
 */
public final class ActivityFeed extends VBox {

    private static final int MAX_ITEMS = 100;

    private final ObservableList<RealtimeEvent> items = FXCollections.observableArrayList();
    private final Set<String> acceptedTypes;
    private final Label empty = new Label("Waiting for activity...");

    /** @param acceptedTypes event types to show, or empty for all */
    public ActivityFeed(String title, Set<String> acceptedTypes) {
        super(10);
        this.acceptedTypes = acceptedTypes;
        getStyleClass().addAll("card", "activity-feed");
        Label heading = new Label(title);
        heading.getStyleClass().add("card-title");

        ListView<RealtimeEvent> list = new ListView<>(items);
        list.getStyleClass().add("feed-list");
        list.setCellFactory(v -> new EventCell());
        list.setPlaceholder(empty);
        empty.getStyleClass().add("muted");
        VBox.setVgrow(list, Priority.ALWAYS);
        list.setMinHeight(180);

        getChildren().addAll(heading, list);
    }

    public void add(RealtimeEvent event) {
        if (!acceptedTypes.isEmpty() && !acceptedTypes.contains(event.type())) {
            return;
        }
        items.addFirst(event);
        if (items.size() > MAX_ITEMS) {
            items.removeLast();
        }
    }

    private static final class EventCell extends ListCell<RealtimeEvent> {
        EventCell() {
            setPrefWidth(0); // size to the list, not to the text, so long descriptions wrap instead of being clipped
        }

        @Override
        protected void updateItem(RealtimeEvent e, boolean isEmpty) {
            super.updateItem(e, isEmpty);
            if (isEmpty || e == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            Label tag = new Label(EventText.tag(e));
            tag.getStyleClass().addAll("event-tag", EventText.tagStyle(e));
            Label text = new Label(EventText.describe(e));
            text.getStyleClass().add("event-text");
            text.setWrapText(true);
            Label time = new Label(Formats.time(e.timestamp()));
            time.getStyleClass().add("event-time");
            // Tag and time keep their full width; only the description wraps.
            tag.setMinWidth(Region.USE_PREF_SIZE);
            time.setMinWidth(Region.USE_PREF_SIZE);
            HBox.setHgrow(text, Priority.ALWAYS);
            text.setMaxWidth(Double.MAX_VALUE);
            HBox row = new HBox(10, tag, text, time);
            row.setAlignment(Pos.CENTER_LEFT);
            setText(null);
            setGraphic(row);
        }
    }
}
