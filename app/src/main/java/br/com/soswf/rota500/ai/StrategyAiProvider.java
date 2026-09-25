package br.com.soswf.rota500.ai;

import br.com.soswf.rota500.core.StrategyAdvice;
import br.com.soswf.rota500.core.StrategySnapshot;

public interface StrategyAiProvider {
    StrategyAdvice advise(StrategySnapshot snapshot) throws Exception;
    String name();
}
