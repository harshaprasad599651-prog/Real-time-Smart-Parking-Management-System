package com.smartpark.client.ui.component;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * A dashboard KPI tile: small caption, big number, optional hint line.
 * The accent ({@code accent-blue}, {@code accent-green}, {@code accent-amber}, {@code accent-red},
 * {@code accent-slate}) colours the left border and the value.
 */
public final class StatCard extends VBox {

    private final Label value = new Label("–");
    private final Label hint = new Label();

    public StatCard(String caption, String accent) {
        super(4);
        getStyleClass().addAll("card", "stat-card", accent);
        Label title = new Label(caption.toUpperCase());
        title.getStyleClass().add("stat-caption");
        value.getStyleClass().add("stat-value");
        hint.getStyleClass().add("stat-hint");
        hint.setWrapText(true);
        getChildren().addAll(title, value, hint);
        setMinWidth(170);
        setPrefWidth(200);
    }

    public void setValue(String text) {
        value.setText(text);
    }

    public void setHint(String text) {
        hint.setText(text);
    }
}
