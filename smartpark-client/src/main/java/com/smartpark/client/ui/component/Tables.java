package com.smartpark.client.ui.component;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

import java.util.function.Function;

/** Small helpers for read-only TableViews (no reflection-based PropertyValueFactory). */
public final class Tables {

    private Tables() {
    }

    /**
     * A text column of preferred {@code width}. The minimum is a bit smaller so that a constrained
     * table on a narrow window shrinks its text columns (with an ellipsis) instead of clipping the
     * last columns off the right edge.
     */
    public static <T> TableColumn<T, String> column(String title, Function<T, String> value, double width) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        c.setPrefWidth(width);
        c.setMinWidth(Math.round(width * 0.7));
        return c;
    }

    /** A text column drawn as a coloured badge; {@code style} picks the CSS class from the value. */
    public static <T> TableColumn<T, String> badge(String title, Function<T, String> value,
                                                   Function<String, String> style, double width) {
        TableColumn<T, String> c = column(title, value, width);
        c.setMinWidth(width); // a badge must never be cut off
        c.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                javafx.scene.control.Label label = new javafx.scene.control.Label(item);
                label.getStyleClass().addAll("table-badge", style.apply(item));
                setGraphic(label);
                setText(null);
            }
        });
        return c;
    }
}
