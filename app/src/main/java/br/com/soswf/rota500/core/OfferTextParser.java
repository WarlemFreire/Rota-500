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
    private static final Pattern RATE_PER_KM = Pattern.compile(
            "R\\$\\s*([0-9]{1,3}(?:[.,][0-9]{1,2})?)\\s*/\\s*km", Pattern.CASE_INSENSITIVE);
    private static final Pattern KM = Pattern.compile("([0-9]{1,3}(?:[.,][0-9]{1,2})?)\\s*km", Pattern.CASE_INSENSITIVE);
    private static final Pattern MINUTES = Pattern.compile("([0-9]{1,3})\\s*min", Pattern.CASE_INSENSITIVE);
    private static final Pattern DESTINATION = Pattern.compile("(?:destino|para|sentido)[:\\s]+([^|•\\n]{3,80})", Pattern.CASE_INSENSITIVE);
    private static final Pattern RATING_WITH_COUNT = Pattern.compile(
            "(?<![0-9])([1-5](?:[.,][0-9]{1,2}))\\s*\\(\\s*[0-9.]{1,8}\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RATING_WITH_LABEL = Pattern.compile(
            "(?:nota|★|☆|⭐)\\s*:?\\s*([1-5](?:[.,][0-9]{1,2}))", Pattern.CASE_INSENSITIVE);

    /** Oferta lida e, quando a confiança cai, o motivo em português (para o diagnóstico). */
    public record Reading(OfferData offer, List<String> issues) { }

    public OfferData parse(String platform, List<String> visibleTexts) {
        return read(platform, visibleTexts).offer();
    }

    public Reading read(String platform, List<String> visibleTexts) {
        List<String> issues = new ArrayList<>();
        String joined = normalize(visibleTexts);
        FareReading reading = parseFare(joined);
        long fare = reading.value;
        List<Double> kms = parseDoubles(KM, joined);
        List<Integer> mins = parseIntegers(MINUTES, joined);
        double shownRatePerKm = parseFirstDouble(RATE_PER_KM, joined);
        double passengerRating = parseRating(joined);
        String destination = parseDestination(joined, visibleTexts);

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
        if (fare <= 0) issues.add("nenhum valor em R$ reconhecido");
        if (reading.ambiguous) issues.add("mais de um valor em R$ na tela");
        if (kms.size() != 2) issues.add(kms.size() + " distância(s) em km (esperadas 2: embarque e viagem)");
        if (mins.size() != 2) issues.add(mins.size() + " tempo(s) em min (esperados 2: embarque e viagem)");
        if (reading.ambiguous || kms.size() != 2 || mins.size() != 2) confidence = Math.min(confidence, 0.60);
        double calculatedRate = pickupKm + tripKm <= 0 ? 0 : (fare / 100.0) / (pickupKm + tripKm);
        if (shownRatePerKm > 0 && calculatedRate > 0
                && Math.abs(shownRatePerKm - calculatedRate) > Math.max(0.18, shownRatePerKm * 0.12)) {
            confidence = Math.min(confidence, 0.60);
            issues.add(String.format(Locale.ROOT, "R$/km da tela (%.2f) diferente do calculado (%.2f)",
                    shownRatePerKm, calculatedRate));
        }

        String signature = platform + "|" + fare + "|" + pickupKm + "|" + tripKm
                + "|" + pickupMinutes + "|" + tripMinutes + "|" + passengerRating + "|" + destination;

        return new Reading(new OfferData(
                platform,
                fare,
                pickupKm,
                tripKm,
                pickupMinutes,
                tripMinutes,
                passengerRating,
                destination,
                sha256(signature),
                confidence), issues);
    }

    private FareReading parseFare(String text) {
        Matcher matcher = MONEY.matcher(text);
        long amount = 0;
        boolean ambiguous = false;
        while (matcher.find()) {
            try {
                String suffix = text.substring(matcher.end());
                if (suffix.matches("(?is)^\\s*(?:/|por)\\s*(?:km|h|hora).*")) continue;
                long candidate = Math.round(Double.parseDouble(matcher.group(1).replace(',', '.')) * 100);
                if (amount != 0 && amount != candidate) ambiguous = true;
                if (amount == 0) amount = candidate;
            } catch (RuntimeException ignored) {
                ambiguous = true;
            }
        }
        return new FareReading(amount, ambiguous);
    }

    private double parseFirstDouble(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) return 0;
        try {
            return Double.parseDouble(matcher.group(1).replace(',', '.'));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private double parseRating(String text) {
        double value = parseFirstDouble(RATING_WITH_COUNT, text);
        return value > 0 ? value : parseFirstDouble(RATING_WITH_LABEL, text);
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

    private String parseDestination(String text, List<String> visibleTexts) {
        Matcher matcher = DESTINATION.matcher(text);
        if (matcher.find()) return matcher.group(1).trim();
        int routeRows = 0;
        if (visibleTexts == null) return "";
        for (int index = 0; index < visibleTexts.size(); index++) {
            String line = visibleTexts.get(index) == null ? "" : visibleTexts.get(index).trim();
            if (MINUTES.matcher(line).find() && KM.matcher(line).find()) {
                routeRows++;
                if (routeRows < 2) continue;
                for (int next = index + 1; next < visibleTexts.size() && next <= index + 2; next++) {
                    String candidate = visibleTexts.get(next) == null ? "" : visibleTexts.get(next).trim();
                    String lower = candidate.toLowerCase(Locale.ROOT);
                    if (candidate.length() >= 5 && !lower.contains("selecionar")
                            && !lower.contains("aceitar") && !lower.contains("recusar")) {
                        return candidate;
                    }
                }
            }
        }
        return "";
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
