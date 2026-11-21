# Relatório de validação da versão 0.9, 21/11/2026

| Verificação | Resultado |
| --- | --- |
| Testes Java do núcleo (`./run-core-tests.sh`) | 22 aprovados |
| Custo de combustível por ciclos de tanque cheio | Coberto (ciclo único, 3 ciclos mais recentes, divergência de 30%) |
| Distância por GPS (precisão, saltos, pausas) | Coberto |
| Jornada esquecida (1 h, 2 h em pausa, 3 sem resposta, horário presumido) | Coberto |
| Total da plataforma que zera à meia-noite | Coberto (confirmação e reprocessamento do histórico) |
| Escolha da atualização por número do build e canal | Coberto |
| Sintaxe Java e XML (`sh run-static-checks.sh`) | 46 Java e 26 XML válidos |
| Compilação do app contra Android 16 (API 36) | Aprovada, com stubs do ML Kit (verificação local, sem gerar APK) |
| Chamadas acima do Android 11 (API 30) | Todas protegidas por checagem de versão |
| Compilação Android completa (`gradle :app:assemble*`) | Pendente: roda no GitHub Actions |
| Migração do banco 4 → 5 em aparelho com dados reais | Pendente: teste físico |
| Notificações, reinício do celular e instalador do Android | Pendente: teste físico |
| Leitura de ofertas da Uber e da 99 | Não funciona no aparelho (fica para a 0.10) |

Este ambiente não tem o Android SDK. A compilação local usa o `android-all` do Robolectric (Android 16) como biblioteca do framework e classes vazias no lugar do ML Kit, o que confirma tipos e assinaturas, mas não substitui o build do Gradle nem o teste no telefone.
