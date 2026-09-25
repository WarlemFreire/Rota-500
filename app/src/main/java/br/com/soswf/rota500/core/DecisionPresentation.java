package br.com.soswf.rota500.core;

import java.util.Locale;

/** Textos e sinais curtos para leitura rápida enquanto o veículo está parado. */
public final class DecisionPresentation {
    public enum Light { GREEN, YELLOW, RED }

    public final String finalAdvice;
    public final String tone;
    public final String kmValue;
    public final Light kmLight;
    public final String hourValue;
    public final Light hourLight;
    public final String ratingValue;
    public final Light ratingLight;

    private DecisionPresentation(String finalAdvice, String tone,
            String kmValue, Light kmLight, String hourValue, Light hourLight,
            String ratingValue, Light ratingLight) {
        this.finalAdvice = finalAdvice;
        this.tone = tone;
        this.kmValue = kmValue;
        this.kmLight = kmLight;
        this.hourValue = hourValue;
        this.hourLight = hourLight;
        this.ratingValue = ratingValue;
        this.ratingLight = ratingLight;
    }

    public static DecisionPresentation from(OfferData offer, RuleDecision decision, DecisionRules rules) {
        boolean accept = decision.outcome == RuleDecision.Outcome.ACCEPTABLE;
        double perKm = offer.grossPerKm();
        double perHour = offer.grossPerHour();

        Light kmLight = perKm < rules.minimumGrossPerKm
                ? Light.RED
                : perKm < rules.minimumGrossPerKm * 1.15 ? Light.YELLOW : Light.GREEN;

        Light hourLight;
        if (perHour >= rules.minimumLongRidePerHour * 1.10) hourLight = Light.GREEN;
        else if (perHour >= rules.minimumLongRidePerHour) hourLight = Light.YELLOW;
        else if (offer.totalMinutes() >= rules.longRideMinutes) hourLight = Light.RED;
        else hourLight = Light.YELLOW;

        Light ratingLight;
        if (offer.passengerRating <= 0) ratingLight = Light.YELLOW;
        else if (offer.passengerRating < rules.minimumPassengerRating) ratingLight = Light.RED;
        else if (offer.passengerRating < 4.85) ratingLight = Light.YELLOW;
        else ratingLight = Light.GREEN;

        return new DecisionPresentation(
                accept ? "ACEITAR" : "RECUSAR",
                accept ? "ACCEPT" : "REJECT",
                String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", perKm),
                kmLight,
                String.format(Locale.forLanguageTag("pt-BR"), "R$ %.0f", perHour),
                hourLight,
                offer.passengerRating > 0
                        ? String.format(Locale.forLanguageTag("pt-BR"), "%.2f", offer.passengerRating)
                        : "—",
                ratingLight);
    }

    public String compactDetails() {
        return kmValue + "/km • " + hourValue + "/h • nota " + ratingValue;
    }
}
