package com.kosign.wecms.common.web;

/**
 * Per-screen sort whitelist entry. Each domain that exposes table sorting
 * declares its own enum implementing this interface — the enum constant
 * name MUST match the View Setting {@code FIELD_ID} (uppercase semantic
 * key, e.g. {@code BANK}, {@code BALANCE}) and {@link #entityProperty()}
 * resolves to the corresponding JPA entity property.
 *
 * <p>The whitelist is the only sanctioned way to translate a wire {@code
 * sortField} into a Spring Data {@code Sort.Order}. Never concatenate the
 * raw wire string into a JPQL / native query — every sortable column
 * passes through an enum lookup so an unknown / typo / malicious value
 * resolves to {@code null} and the service falls back to the default
 * ORDER BY.
 */
public interface SortableField {

    /**
     * JPA entity property the wire {@code FIELD_ID} maps to (e.g. {@code
     * "bal"}, {@code "acctNickNm"}). Spring Data {@code Sort.by(property)}
     * validates the property against the entity metamodel — a typo here
     * fails fast at query time rather than reaching the database.
     */
    String entityProperty();
}
