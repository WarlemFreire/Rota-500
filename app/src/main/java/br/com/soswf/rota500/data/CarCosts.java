package br.com.soswf.rota500.data;

import br.com.soswf.rota500.core.DecisionRules;
import br.com.soswf.rota500.core.FuelMath;

/**
 * Um único cadastro de custo por km (Q1): combustível + desgaste. O combustível usa o valor
 * real dos abastecimentos quando houver ao menos um ciclo de tanque cheio (Q8, Q9).
 */
public final class CarCosts {
    public final double fuelPerKm;
    public final double wearPerKm;
    public final boolean fuelIsReal;
    public final FuelMath.Result fuel;
    public final double temporaryFuelPerKm;

    private CarCosts(double fuelPerKm, double wearPerKm, boolean fuelIsReal,
            FuelMath.Result fuel, double temporaryFuelPerKm) {
        this.fuelPerKm = fuelPerKm;
        this.wearPerKm = wearPerKm;
        this.fuelIsReal = fuelIsReal;
        this.fuel = fuel;
        this.temporaryFuelPerKm = temporaryFuelPerKm;
    }

    public static CarCosts load(AppDatabase database, AppPreferences preferences) {
        FuelMath.Result real = FuelMath.realCost(database.refuels());
        double temporary = preferences.fuelCostPerKm();
        double wear = preferences.wearCostPerKm();
        return real == null
                ? new CarCosts(temporary, wear, false, null, temporary)
                : new CarCosts(real.costPerKm(), wear, true, real, temporary);
    }

    public double totalPerKm() { return fuelPerKm + wearPerKm; }

    public boolean divergentFromTemporary() {
        return fuelIsReal && FuelMath.divergent(fuelPerKm, temporaryFuelPerKm);
    }

    public DecisionRules decisionRules(AppPreferences preferences) {
        return preferences.decisionRules(totalPerKm());
    }
}
