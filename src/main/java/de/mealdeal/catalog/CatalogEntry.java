package de.mealdeal.catalog;

import java.util.List;

/**
 * Common read-only contract for an official MealDeal catalog term.
 *
 * <p>Catalog identity is language-independent and never replaces the UUID of a
 * local user-owned domain entity.</p>
 */
public interface CatalogEntry {

    String catalogId();

    String displayName();

    List<String> aliases();
}
