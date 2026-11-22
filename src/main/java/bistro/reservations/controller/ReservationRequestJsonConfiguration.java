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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
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

    static final class ReservationTime extends ValueDeserializer<LocalDateTime> {
        private static final Pattern LOCAL_MINUTE = Pattern.compile(
                "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}");
        private static final DateTimeFormatter FORMAT = DateTimeFormatter
                .ofPattern("uuuu-MM-dd'T'HH:mm", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() != JsonToken.VALUE_STRING) {
                return invalidTime(context);
            }
            String text = parser.getString();
            if (text.length() != 16 || !LOCAL_MINUTE.matcher(text).matches()) {
                return invalidTime(context);
            }
            try {
                return LocalDateTime.parse(text, FORMAT);
            } catch (DateTimeParseException exception) {
                return invalidTime(context);
            }
        }

        private LocalDateTime invalidTime(DeserializationContext context) {
            return context.reportInputMismatch(this, "La fecha y hora locales deben tener el formato AAAA-MM-DDTHH:mm.");
        }
    }

    static final class PartySize extends ValueDeserializer<Integer> {
        private static final int LARGE_MAGNITUDE = 100;

        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT
                    || parser.currentToken() == JsonToken.VALUE_NUMBER_FLOAT) {
                return readInteger(parser.getText(), context);
            }
            return invalidSize(context);
        }

        private Integer readInteger(String number, DeserializationContext context) {
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
                return 0;
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
            return negative ? -magnitude : magnitude;
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

        private Integer invalidSize(DeserializationContext context) {
            return context.reportInputMismatch(this, "La cantidad de comensales debe ser un número entero.");
        }
    }
}
