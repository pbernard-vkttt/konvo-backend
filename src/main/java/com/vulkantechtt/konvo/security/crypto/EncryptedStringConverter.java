package com.vulkantechtt.konvo.security.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Hibernate AttributeConverter that round-trips a column through
 * {@link CryptoService}. JPA converters are instantiated by the persistence
 * provider, not Spring, so the {@link CryptoService} is resolved via a
 * static holder populated at boot ({@link CryptoServiceHolder}).
 *
 * Apply via {@code @Convert(converter = EncryptedStringConverter.class)} on
 * the field.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return CryptoServiceHolder.require().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return CryptoServiceHolder.require().decrypt(dbData);
    }
}
