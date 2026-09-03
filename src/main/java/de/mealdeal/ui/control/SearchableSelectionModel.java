package de.mealdeal.ui.control;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** JavaFX-independent filtering and deterministic ranking for one selection control. */
final class SearchableSelectionModel<T> {

    private static final int NO_MATCH = Integer.MAX_VALUE;

    private final Function<T, String> displayText;
    private final boolean preserveSourceOrder;
    private List<T> options = List.of();
    private T committedValue;

    SearchableSelectionModel(Collection<? extends T> options,
                             Function<T, String> displayText) {
        this(options, displayText, false);
    }

    SearchableSelectionModel(Collection<? extends T> options,
                             Function<T, String> displayText,
                             boolean preserveSourceOrder) {
        this.displayText = Objects.requireNonNull(displayText,
                "Display text function must not be null.");
        this.preserveSourceOrder = preserveSourceOrder;
        setOptions(options);
    }

    void setOptions(Collection<? extends T> options) {
        Objects.requireNonNull(options, "Selection options must not be null.");
        this.options = List.copyOf(options);
        if (committedValue != null) {
            committedValue = canonical(committedValue).orElse(null);
        }
    }

    List<T> suggestions(String query) {
        String normalizedQuery = normalize(query);
        List<RankedOption<T>> ranked = new ArrayList<>();
        for (int index = 0; index < options.size(); index++) {
            T option = options.get(index);
            String text = requireDisplayText(option);
            int rank = matchRank(normalize(text), normalizedQuery);
            if (rank != NO_MATCH) {
                ranked.add(new RankedOption<>(option, text, normalize(text), rank, index));
            }
        }
        Comparator<RankedOption<T>> order;
        if (preserveSourceOrder) {
            order = Comparator.comparingInt(RankedOption<T>::sourceIndex);
        } else {
            order = Comparator.comparingInt(RankedOption<T>::rank)
                    .thenComparing(RankedOption<T>::normalizedText)
                    .thenComparing(RankedOption<T>::text)
                    .thenComparingInt(RankedOption<T>::sourceIndex);
        }
        ranked.sort(order);
        return ranked.stream().map(RankedOption::option).toList();
    }

    Optional<T> exactMatch(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return options.stream()
                .filter(option -> normalize(requireDisplayText(option)).equals(normalized))
                .findFirst();
    }

    Optional<T> canonical(T value) {
        return options.stream().filter(option -> option.equals(value)).findFirst();
    }

    boolean contains(T value) {
        return value != null && canonical(value).isPresent();
    }

    void commit(T value) {
        committedValue = canonical(Objects.requireNonNull(value,
                "Committed selection must not be null."))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Committed selection must be one of the available options."));
    }

    Optional<T> resolveExactOrKeep(String text) {
        Optional<T> exact = exactMatch(text);
        exact.ifPresent(this::commit);
        return exact.isPresent() ? exact : Optional.ofNullable(committedValue);
    }

    Optional<T> committedValue() {
        return Optional.ofNullable(committedValue);
    }

    void clearSelection() {
        committedValue = null;
    }

    String display(T value) {
        return value == null ? "" : requireDisplayText(value);
    }

    private String requireDisplayText(T option) {
        return Objects.requireNonNull(displayText.apply(Objects.requireNonNull(option,
                "Selection option must not be null.")),
                "Selection display text must not be null.");
    }

    private static int matchRank(String candidate, String query) {
        if (query.isEmpty()) {
            return 0;
        }
        if (candidate.equals(query)) {
            return 0;
        }
        if (candidate.startsWith(query)) {
            return 1;
        }
        if (candidate.contains(query)) {
            return 2;
        }
        return isSubsequence(query, candidate) ? 3 : NO_MATCH;
    }

    private static boolean isSubsequence(String query, String candidate) {
        int queryIndex = 0;
        for (int candidateIndex = 0;
             candidateIndex < candidate.length() && queryIndex < query.length();
             candidateIndex++) {
            if (candidate.charAt(candidateIndex) == query.charAt(queryIndex)) {
                queryIndex++;
            }
        }
        return queryIndex == query.length();
    }

    static String normalize(String value) {
        String prepared = value == null ? "" : value.strip().toLowerCase(Locale.GERMAN)
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss");
        return Normalizer.normalize(prepared, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private record RankedOption<T>(T option, String text, String normalizedText,
                                   int rank, int sourceIndex) {
    }
}
