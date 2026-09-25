package br.com.soswf.rota500.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OfferTextParser {
    private static final Pattern MONEY = Pattern.compile("R\\$\\s*([0-9]{1,4}(?:[.,][0-9]{2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern KM = Pattern.compile("([0-9]{1,3}(?:[.,][0-9]{1,2})?)\\s*km", Pattern.CASE_INSENSITIVE);
    private static final Pattern MINUTES = Pattern.compile("([0-9]{1,3})\\s*min", Pattern.CASE_INSENSITIVE);
    private static final Pattern DESTINATION = Pattern.compile("(?:destino|para|sentido)[:\\s]+([^|•\\n]{3,80})", Pattern.CASE_INSENSITIVE);

    public OfferData parse(String platform, List<String> visibleTexts) {
        String joined = normalize(visibleTexts);
        FareReading reading = parseFare(joined);
        long fare = reading.value;
        List<Double> kms = parseDoubles(KM, joined);
        List<Integer> mins = parseIntegers(MINUTES, joined);
        String destination = parseDestination(joined);

        double pickupKm = kms.size() > 1 ? kms.get(0) : 0;
        double tripKm = kms.isEmpty() ? 0 : kms.get(kms.size() > 1 ? 1 : 0);
        int pickupMinutes = mins.size() > 1 ? mins.get(0) : 0;
        int tripMinutes = mins.isEmpty() ? 0 : mins.get(mins.size() > 1 ? 1 : 0);

        double confidence = 0;
        if (fare > 0 && !reading.ambiguous) confidence += 0.40;
        if (kms.size() == 2) confidence += 0.25;
        if (mins.size() == 2) confidence += 0.25;
        if (platform != null && !platform.isBlank()) confidence += 0.10;
        // Uma oferta sem embarque ou com outros preços na mesma tela exige conferência manual.
        if (reading.ambiguous || kms.size() != 2 || mins.size() != 2) confidence = Math.min(confidence, 0.60);

        String signature = platform + "|" + fare + "|" + pickupKm + "|" + tripKm
                + "|" + pickupMinutes + "|" + tripMinutes + "|" + destination;

        return new OfferData(
                platform,
                fare,
                pickupKm,
                tripKm,
                pickupMinutes,
                tripMinutes,
                destination,
                sha256(signature),
                confidence);
    }

    private FareReading parseFare(String text) {
        Matcher matcher = MONEY.matcher(text);
        long amount = 0;
        boolean ambiguous = false;
        while (matcher.find()) {
            try {
                long candidate = Math.round(Double.parseDouble(matcher.group(1).replace(',', '.')) * 100);
                if (amount != 0 && amount != candidate) ambiguous = true;
                if (amount == 0) amount = candidate;
            } catch (RuntimeException ignored) {
                ambiguous = true;
            }
        }
        return new FareReading(amount, ambiguous);
    }

    private List<Double> parseDoubles(Pattern pattern, String text) {
        List<Double> values = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find() && values.size() < 4) {
            try {
                values.add(Double.parseDouble(matcher.group(1).replace(',', '.')));
            } catch (RuntimeException ignored) {
                // Ignore malformed screen fragments.
            }
        }
        return values;
    }

    private List<Integer> parseIntegers(Pattern pattern, String text) {
        List<Integer> values = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find() && values.size() < 4) {
            try {
                values.add(Integer.parseInt(matcher.group(1)));
            } catch (RuntimeException ignored) {
                // Ignore malformed screen fragments.
            }
        }
        return values;
    }

    private String parseDestination(String text) {
        Matcher matcher = DESTINATION.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String normalize(List<String> texts) {
        StringBuilder builder = new StringBuilder();
        if (texts != null) {
            for (String text : texts) {
                if (text == null || text.isBlank()) continue;
                if (builder.length() > 0) builder.append(" | ");
                builder.append(text.trim().replaceAll("\\s+", " "));
            }
        }
        return builder.toString();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : digest) result.append(String.format(Locale.ROOT, "%02x", b));
            return result.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static final class FareReading {
        final long value;
        final boolean ambiguous;
        FareReading(long value, boolean ambiguous) { this.value = value; this.ambiguous = ambiguous; }
    }
}
