package br.com.soswf.rota500.core;

public final class QuoteInput {
    public final double pickupKm;
    public final double passengerKm;
    public final double returnKm;
    public final int pickupMinutes;
    public final int passengerMinutes;
    public final int returnMinutes;
    public final int waitingMinutes;
    public final double operatingCostPerKm;
    public final double desiredNetPerHour;
    public final double expectedAppNetPerHour;
    public final double tollsAndParking;
    public final double marginPercent;

    public QuoteInput(
            double pickupKm,
            double passengerKm,
            double returnKm,
            int pickupMinutes,
            int passengerMinutes,
            int returnMinutes,
            int waitingMinutes,
            double operatingCostPerKm,
            double desiredNetPerHour,
            double expectedAppNetPerHour,
            double tollsAndParking,
            double marginPercent) {
        this.pickupKm = nonNegative(pickupKm);
        this.passengerKm = nonNegative(passengerKm);
        this.returnKm = nonNegative(returnKm);
        this.pickupMinutes = Math.max(0, pickupMinutes);
        this.passengerMinutes = Math.max(0, passengerMinutes);
        this.returnMinutes = Math.max(0, returnMinutes);
        this.waitingMinutes = Math.max(0, waitingMinutes);
        this.operatingCostPerKm = nonNegative(operatingCostPerKm);
        this.desiredNetPerHour = nonNegative(desiredNetPerHour);
        this.expectedAppNetPerHour = nonNegative(expectedAppNetPerHour);
        this.tollsAndParking = nonNegative(tollsAndParking);
        this.marginPercent = Math.max(0, marginPercent);
    }

    public double totalKm() {
        return pickupKm + passengerKm + returnKm;
    }

    public int totalMinutes() {
        return pickupMinutes + passengerMinutes + returnMinutes + waitingMinutes;
    }

    private static double nonNegative(double value) {
        return Math.max(0, value);
    }
}
