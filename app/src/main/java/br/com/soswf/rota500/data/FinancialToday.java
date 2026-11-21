package br.com.soswf.rota500.data;

import br.com.soswf.rota500.core.MoneyLedger;

public record FinancialToday(MoneyLedger.Balance balance, long cashSpentCents,
        long fuelCents, long foodCents, long maintenanceCents, long financingCents,
        long otherCents, long activeMinutes, Long blockGrossPerHourCents,
        Long dayGrossPerHourCents, Double distanceKm, Long estimatedNetCents, boolean distanceFromGps) {
    public long cashRemainderCents() { return balance.totalCents() - cashSpentCents; }
}
