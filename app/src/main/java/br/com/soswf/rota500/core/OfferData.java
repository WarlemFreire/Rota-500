package br.com.soswf.rota500.core;

import java.util.Locale;

public final class OfferData {
    public final String platform;
    public final long fareCents;
    public final double pickupKm;
    public final double tripKm;
    public final int pickupMinutes;
    public final int tripMinutes;
    public final double passengerRating;
    public final String destination;
    public final String sourceHash;
    public final double confidence;

    public OfferData(
            String platform,
            long fareCents,
            double pickupKm,
            double tripKm,
            int pickupMinutes,
            int tripMinutes,
            String destination,
            String sourceHash,
            double confidence) {
        this(platform, fareCents, pickupKm, tripKm, pickupMinutes, tripMinutes,
                0, destination, sourceHash, confidence);
    }

    public OfferData(
            String platform,
            long fareCents,
            double pickupKm,
            double tripKm,
            int pickupMinutes,
            int tripMinutes,
            double passengerRating,
            String destination,
            String sourceHash,
            double confidence) {
        this.platform = platform == null ? "DESCONHECIDA" : platform;
        this.fareCents = Math.max(0, fareCents);
        this.pickupKm = Math.max(0, pickupKm);
        this.tripKm = Math.max(0, tripKm);
        this.pickupMinutes = Math.max(0, pickupMinutes);
        this.tripMinutes = Math.max(0, tripMinutes);
        this.passengerRating = passengerRating >= 1 && passengerRating <= 5 ? passengerRating : 0;
        this.destination = destination == null ? "" : destination.trim();
        this.sourceHash = sourceHash == null ? "" : sourceHash;
        this.confidence = Math.max(0, Math.min(1, confidence));
    }

    public double fareReais() {
        return fareCents / 100.0;
    }

    public double totalKm() {
        return pickupKm + tripKm;
    }

    public int totalMinutes() {
        return pickupMinutes + tripMinutes;
    }

    public double grossPerKm() {
        return totalKm() <= 0 ? 0 : fareReais() / totalKm();
    }

    public double grossPerHour() {
        return totalMinutes() <= 0 ? 0 : fareReais() * 60.0 / totalMinutes();
    }

    public String compactSummary() {
        return String.format(
                Locale.forLanguageTag("pt-BR"),
                "%s • R$ %.2f • %.2f/km • %.2f/h",
                platform,
                fareReais(),
                grossPerKm(),
                grossPerHour());
    }
}
