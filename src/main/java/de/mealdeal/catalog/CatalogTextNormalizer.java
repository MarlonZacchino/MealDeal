package de.mealdeal.catalog;

import java.text.Normalizer;
import java.util.Locale;

/** Conservative text normalization used only for exact catalog resolution. */
public final class CatalogTextNormalizer {

    private CatalogTextNormalizer() {
    }

    /**
     * Normalizes case, Unicode representation, whitespace and common German
     * transliterations without stemming, substring or fuzzy matching.
     */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String unicode = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace("ß", "ss")
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue");
        StringBuilder normalized = new StringBuilder(unicode.length());
        boolean pendingSpace = false;
        for (int index = 0; index < unicode.length(); index++) {
            char character = unicode.charAt(index);
            if (Character.isWhitespace(character) || Character.isSpaceChar(character)) {
                pendingSpace = normalized.length() > 0;
            } else {
                if (pendingSpace) {
                    normalized.append(' ');
                    pendingSpace = false;
                }
                normalized.append(character);
            }
        }
        return normalized.toString();
    }
}
