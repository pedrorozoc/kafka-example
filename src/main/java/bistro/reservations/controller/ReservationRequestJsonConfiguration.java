package bistro.reservations.controller;

import bistro.reservations.model.ReservationStart;
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
    private static final int MAX_NUMBER_LENGTH = 131_136;

    @Bean
    JsonFactoryBuilderCustomizer allowNumericPrecision() {
        return builder -> builder.streamReadConstraints(StreamReadConstraints.builder()
                .maxNumberLength(MAX_NUMBER_LENGTH)
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

    static final class ReservationTime extends ValueDeserializer<ReservationStart> {
        private static final Pattern RFC_3339 = Pattern.compile(
                "([0-9]{4}-[0-9]{2}-[0-9]{2}[Tt][0-9]{2}:[0-9]{2}:[0-9]{2})"
                        + "(?:\\.([0-9]+))?([Zz]|[+-](?:[01][0-9]|2[0-3]):[0-5][0-9])");

        @Override
        public ReservationStart deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() == JsonToken.VALUE_STRING) {
                var match = RFC_3339.matcher(parser.getString());
                if (match.matches()) {
                    try {
                        String fraction = match.group(2);
                        String local = match.group(1);
                        String remainder = "";
                        if (fraction != null) {
                            int end = Math.min(fraction.length(), 6);
                            local += "." + fraction.substring(0, end);
                            remainder = fraction.substring(end);
                        }
                        Instant instant = LocalDateTime.parse(local).toInstant(ZoneOffset.UTC);
                        return new ReservationStart(instant.minusSeconds(offsetSeconds(match.group(3))), remainder);
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

        private ReservationStart invalidTime(DeserializationContext context) {
            return context.reportInputMismatch(this, "La fecha y hora deben tener un desplazamiento UTC explícito.");
        }
    }

    static final class PartySize extends ValueDeserializer<BigInteger> {
        private static final int LARGE_MAGNITUDE = 100;

        @Override
        public BigInteger deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT
                    || parser.currentToken() == JsonToken.VALUE_NUMBER_FLOAT) {
                return readInteger(parser.getText(), context);
            }
            return invalidSize(context);
        }

        private BigInteger readInteger(String number, DeserializationContext context) {
            boolean negative = number.charAt(0) == '-';
            int start = negative ? 1 : 0;
            int exponentAt = Math.max(number.indexOf('e'), number.indexOf('E'));
            int mantissaEnd = exponentAt < 0 ? number.length() : exponentAt;
            int pointAt = number.indexOf('.');
            int digitsBeforePoint = (pointAt < 0 ? mantissaEnd : pointAt) - start;
            int firstNonzero = -1;
            int lastNonzero = -1;
            int digitIndex = 0;
            int prefix = 0;
            for (int index = start; index < mantissaEnd; index++) {
                char digit = number.charAt(index);
                if (digit == '.') {
                    continue;
                }
                if (digit != '0') {
                    if (firstNonzero < 0) {
                        firstNonzero = digitIndex;
                    }
                    lastNonzero = digitIndex;
                }
                if (firstNonzero >= 0 && digitIndex - firstNonzero < 2) {
                    prefix = prefix * 10 + digit - '0';
                }
                digitIndex++;
            }
            if (firstNonzero < 0) {
                return BigInteger.ZERO;
            }

            // Desplaza el punto decimal sin expandir el exponente ni construir enteros enormes.
            long decimalPoint = digitsBeforePoint + boundedExponent(number, exponentAt);
            if (lastNonzero >= decimalPoint) {
                return invalidSize(context);
            }
            long integerDigits = decimalPoint - firstNonzero;
            // Los enteros de tres o más cifras conservan su signo para la validación del DTO.
            int magnitude = LARGE_MAGNITUDE;
            if (integerDigits == 1) {
                magnitude = prefix >= 10 ? prefix / 10 : prefix;
            } else if (integerDigits == 2) {
                magnitude = prefix < 10 ? prefix * 10 : prefix;
            }
            return BigInteger.valueOf(negative ? -magnitude : magnitude);
        }

        private long boundedExponent(String number, int exponentAt) {
            if (exponentAt < 0) {
                return 0;
            }
            int start = exponentAt + 1;
            try {
                return Integer.parseInt(number, start, number.length(), 10);
            } catch (NumberFormatException exception) {
                return number.charAt(start) == '-' ? Integer.MIN_VALUE : Integer.MAX_VALUE;
            }
        }

        private BigInteger invalidSize(DeserializationContext context) {
            return context.reportInputMismatch(this, "La cantidad de comensales debe ser un número entero.");
        }
    }
}
