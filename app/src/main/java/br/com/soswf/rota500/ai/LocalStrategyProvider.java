package br.com.soswf.rota500.ai;

import br.com.soswf.rota500.core.StrategyAdvice;
import br.com.soswf.rota500.core.StrategyEngine;
import br.com.soswf.rota500.core.StrategySnapshot;

public final class LocalStrategyProvider implements StrategyAiProvider {
    private final StrategyEngine engine = new StrategyEngine();

    @Override public StrategyAdvice advise(StrategySnapshot snapshot) { return engine.advise(snapshot); }
    @Override public String name() { return "motor-local"; }
}
