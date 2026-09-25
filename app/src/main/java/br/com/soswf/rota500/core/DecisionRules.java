package br.com.soswf.rota500.core;

public final class DecisionRules {
    public final double minimumGrossPerKm;
    public final double minimumLongRidePerHour;
    public final int longRideMinutes;
    public final double minimumParserConfidence;
    public final double operatingCostPerKm;
    public final double minimumPassengerRating;

    public DecisionRules(
            double minimumGrossPerKm,
            double minimumLongRidePerHour,
            int longRideMinutes,
            double minimumParserConfidence) {
        this(minimumGrossPerKm, minimumLongRidePerHour, longRideMinutes, minimumParserConfidence, 0);
    }

    public DecisionRules(double minimumGrossPerKm, double minimumLongRidePerHour,
            int longRideMinutes, double minimumParserConfidence, double operatingCostPerKm) {
        this(minimumGrossPerKm, minimumLongRidePerHour, longRideMinutes,
                minimumParserConfidence, operatingCostPerKm, 4.70);
    }

    public DecisionRules(double minimumGrossPerKm, double minimumLongRidePerHour,
            int longRideMinutes, double minimumParserConfidence, double operatingCostPerKm,
            double minimumPassengerRating) {
        this.minimumGrossPerKm = minimumGrossPerKm;
        this.minimumLongRidePerHour = minimumLongRidePerHour;
        this.longRideMinutes = longRideMinutes;
        this.minimumParserConfidence = minimumParserConfidence;
        this.operatingCostPerKm = Math.max(0, operatingCostPerKm);
        this.minimumPassengerRating = Math.max(1, Math.min(5, minimumPassengerRating));
    }

    public static DecisionRules defaults() {
        return new DecisionRules(1.20, 35.0, 35, 0.72);
    }
}
