package de.mealdeal.persistence.sqlite;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

/** Fixed-width UTC representation whose text order is also chronological order. */
final class SqliteInstantCodec {

    private static final DateTimeFormatter FORMATTER =
            new DateTimeFormatterBuilder().appendInstant(9).toFormatter();

    private SqliteInstantCodec() {
    }

    static String format(Instant instant) {
        return FORMATTER.format(instant);
    }

    static Instant parse(String value) {
        return Instant.parse(value);
    }
}
