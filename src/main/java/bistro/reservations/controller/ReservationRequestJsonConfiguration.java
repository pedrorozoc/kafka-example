package bistro.reservations.controller;

import org.springframework.boot.jackson.autoconfigure.JsonFactoryBuilderCustomizer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ValueDeserializer;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

@Configuration(proxyBeanMethods = false)
class ReservationRequestJsonConfiguration {
    // Máximo de dígitos enteros que admite la columna numeric de PostgreSQL.
    private static final int MAX_INTEGER_DIGITS = 131_072;

    @Bean
    JsonFactoryBuilderCustomizer allowNumericPrecision() {
        return builder -> builder.streamReadConstraints(StreamReadConstraints.builder()
                .maxNumberLength(MAX_INTEGER_DIGITS + 64)
                .build());
    }

    @Bean
    JsonMapperBuilderCustomizer rejectTrailingJson() {
        return builder -> builder.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    static final class StrictString extends ValueDeserializer<String> {
        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() != JsonToken.VALUE_STRING) {
                return context.reportInputMismatch(this, "Se esperaba un valor de texto.");
            }
            return parser.getString();
        }
    }

    static final class ReservationTime extends ValueDeserializer<Instant> {
        private static final Pattern RFC_3339 = Pattern.compile(
                "([0-9]{4}-[0-9]{2}-[0-9]{2}[Tt][0-9]{2}:[0-9]{2}:[0-9]{2})"
                        + "(?:\\.([0-9]+))?([Zz]|[+-](?:[01][0-9]|2[0-3]):[0-5][0-9])");

        @Override
        public Instant deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() == JsonToken.VALUE_STRING) {
                var match = RFC_3339.matcher(parser.getString());
                if (match.matches()) {
                    try {
                        String fraction = match.group(2);
                        String local = fraction == null ? match.group(1)
                                : match.group(1) + "." + fraction.substring(0, Math.min(fraction.length(), 6));
                        Instant instant = LocalDateTime.parse(local).toInstant(ZoneOffset.UTC);
                        return instant.minusSeconds(offsetSeconds(match.group(3)));
                    } catch (DateTimeParseException exception) {
                        return invalidTime(context);
                    }
                }
            }
            return invalidTime(context);
        }

        private int offsetSeconds(String offset) {
            if (offset.length() == 1) {
                return 0;
            }
            int hours = Integer.parseInt(offset.substring(1, 3));
            int minutes = Integer.parseInt(offset.substring(4));
            return (hours * 3600 + minutes * 60) * (offset.charAt(0) == '-' ? -1 : 1);
        }

        private Instant invalidTime(DeserializationContext context) {
            return context.reportInputMismatch(this, "La fecha y hora deben tener un desplazamiento UTC explícito.");
        }
    }

    static final class PartySize extends ValueDeserializer<BigInteger> {
        @Override
        public BigInteger deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT
                    || parser.currentToken() == JsonToken.VALUE_NUMBER_FLOAT) {
                var decimal = parser.getDecimalValue();
                if (decimal.signum() == 0) {
                    return BigInteger.ZERO;
                }
                long integerDigits = (long) decimal.precision() - decimal.scale();
                if (integerDigits > 0 && integerDigits <= MAX_INTEGER_DIGITS) {
                    try {
                        return decimal.toBigIntegerExact();
                    } catch (ArithmeticException exception) {
                        return invalidSize(context);
                    }
                }
            }
            return invalidSize(context);
        }

        private BigInteger invalidSize(DeserializationContext context) {
            return context.reportInputMismatch(this, "La cantidad de comensales debe ser un número entero representable.");
        }
    }
}
