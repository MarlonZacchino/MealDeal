package de.mealdeal.ui.control;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.StringConverter;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Adds reusable type-to-filter behavior to a JavaFX {@link ComboBox}. */
public final class SearchableComboBoxSupport<T> {

    private final ComboBox<T> comboBox;
    private final SearchableSelectionModel<T> model;
    private final ObservableList<T> visibleOptions = FXCollections.observableArrayList();
    private final boolean customTextAllowed;
    private final StringConverter<T> customTextConverter;
    private boolean updating;
    private T committedValue;

    private SearchableComboBoxSupport(ComboBox<T> comboBox,
                                      Collection<? extends T> options,
                                      Function<T, String> displayText,
                                      boolean customTextAllowed,
                                      StringConverter<T> customTextConverter,
                                      boolean preserveSourceOrder) {
        this.comboBox = Objects.requireNonNull(comboBox, "ComboBox must not be null.");
        this.model = new SearchableSelectionModel<>(
                options, displayText, preserveSourceOrder);
        this.customTextAllowed = customTextAllowed;
        this.customTextConverter = customTextConverter;
        T initialValue = comboBox.getValue();

        comboBox.setEditable(true);
        comboBox.getStyleClass().add("searchable-combo-box");
        comboBox.setItems(visibleOptions);
        comboBox.setConverter(converter());
        replaceVisibleOptions(model.suggestions(""), null);
        model.canonical(initialValue).ifPresent(value -> {
            committedValue = value;
            model.commit(value);
            restoreValue(value);
        });
        installListeners();
    }

    /** Installs a searchable control that only accepts values from its option list. */
    public static <T> SearchableComboBoxSupport<T> forValidValues(
            ComboBox<T> comboBox, Collection<? extends T> options,
            Function<T, String> displayText) {
        return new SearchableComboBoxSupport<>(comboBox, options, displayText,
                false, null, false);
    }

    /** Installs filtering while retaining the caller's intentional source ordering. */
    public static <T> SearchableComboBoxSupport<T> forValidValuesInSourceOrder(
            ComboBox<T> comboBox, Collection<? extends T> options,
            Function<T, String> displayText) {
        return new SearchableComboBoxSupport<>(comboBox, options, displayText,
                false, null, true);
    }

    /** Installs filtering while preserving an existing intentional custom-text workflow. */
    public static <T> SearchableComboBoxSupport<T> allowingCustomText(
            ComboBox<T> comboBox, Collection<? extends T> options,
            StringConverter<T> converter) {
        Objects.requireNonNull(converter, "Custom text converter must not be null.");
        return new SearchableComboBoxSupport<>(comboBox, options, converter::toString,
                true, converter, false);
    }

    /** Replaces the source catalog without sharing filtered state with another control. */
    public void setOptions(Collection<? extends T> options) {
        String editorText = comboBox.getEditor().getText();
        T current = comboBox.getValue();
        model.setOptions(options);
        committedValue = model.canonical(committedValue != null ? committedValue : current)
                .orElse(null);
        if (committedValue != null) {
            model.commit(committedValue);
        }
        replaceVisibleOptions(model.suggestions(""), editorText);
        if (!customTextAllowed) {
            restoreValue(committedValue);
        }
    }

    /** Clears both the visible value and the last committed catalog selection. */
    public void clearSelection() {
        committedValue = null;
        model.clearSelection();
        restoreValue(null);
    }

    private void installListeners() {
        comboBox.valueProperty().addListener((ignored, previous, selected) -> {
            if (updating || !model.contains(selected)) {
                return;
            }
            T canonical = model.canonical(selected).orElseThrow();
            committedValue = canonical;
            model.commit(canonical);
            replaceVisibleOptions(model.suggestions(""), model.display(canonical));
        });
        comboBox.getEditor().textProperty().addListener((ignored, previous, current) -> {
            if (!updating) {
                filter(current);
            }
        });
        comboBox.focusedProperty().addListener((ignored, previous, focused) -> {
            if (!focused) {
                Platform.runLater(() -> {
                    if (!comboBox.isFocused() && !comboBox.isShowing()) {
                        finishEditing();
                    }
                });
            }
        });
        comboBox.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                comboBox.hide();
                restoreValue(committedValue);
                event.consume();
            }
        });
    }

    private void filter(String query) {
        String preservedText = query == null ? "" : query;
        List<T> suggestions = model.suggestions(preservedText);
        replaceVisibleOptions(suggestions, preservedText);
        if (comboBox.getEditor().isFocused() && !suggestions.isEmpty()
                && !comboBox.isShowing()) {
            Platform.runLater(() -> {
                if (comboBox.getEditor().isFocused() && !comboBox.isShowing()) {
                    comboBox.show();
                }
            });
        }
    }

    private void finishEditing() {
        if (customTextAllowed) {
            replaceVisibleOptions(model.suggestions(""), comboBox.getEditor().getText());
            return;
        }
        T resolved = model.resolveExactOrKeep(comboBox.getEditor().getText()).orElse(null);
        committedValue = resolved;
        restoreValue(resolved);
    }

    private void restoreValue(T value) {
        runUpdating(() -> {
            visibleOptions.setAll(model.suggestions(""));
            comboBox.setValue(value);
            comboBox.getEditor().setText(model.display(value));
            comboBox.getEditor().positionCaret(comboBox.getEditor().getText().length());
        });
    }

    private void replaceVisibleOptions(Collection<? extends T> options, String editorText) {
        runUpdating(() -> {
            visibleOptions.setAll(options);
            if (editorText != null) {
                comboBox.getEditor().setText(editorText);
                comboBox.getEditor().positionCaret(editorText.length());
            }
        });
    }

    private StringConverter<T> converter() {
        if (customTextAllowed) {
            return customTextConverter;
        }
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return model.display(value);
            }

            @Override
            public T fromString(String text) {
                return model.resolveExactOrKeep(text).orElse(null);
            }
        };
    }

    private void runUpdating(Runnable action) {
        boolean previous = updating;
        updating = true;
        try {
            action.run();
        } finally {
            updating = previous;
        }
    }
}
