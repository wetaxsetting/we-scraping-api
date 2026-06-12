package com.kosign.wecms.common.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Maps an {@link OffsetDateTime} entity attribute to the legacy JEX
 * audit-column shape: a {@code varchar(14)} {@code yyyyMMddHHmmss}
 * string in UTC.
 *
 * <p>Every JEX table stores {@code REG_DTM} / {@code MOD_DTM} as
 * {@code varchar(14)} (not {@code timestamptz}). {@link BaseEntity}
 * keeps the {@code OffsetDateTime} Java API for ergonomics; this
 * converter performs the translation at the JDBC boundary so Hibernate
 * {@code ddl-auto: validate} sees a {@code VARCHAR} column matching the
 * live JEX schema, and so reads/writes round-trip the legacy string
 * format.
 */
@Converter
public class LegacyDtmConverter implements AttributeConverter<OffsetDateTime, String> {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Override
    public String convertToDatabaseColumn(OffsetDateTime attribute) {
        if (attribute == null) return null;
        return attribute.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime().format(FMT);
    }

    @Override
    public OffsetDateTime convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return LocalDateTime.parse(dbData.trim(), FMT).atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            // Tolerate malformed/legacy audit strings rather than failing the read.
            return null;
        }
    }
}
