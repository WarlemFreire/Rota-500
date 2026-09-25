# Relatório de validação, 24/09/2026

| Verificação | Resultado |
| --- | --- |
| Testes Java do núcleo (`./run-core-tests.sh`) | 11 aprovados |
| Parser de tela com preço ambíguo e embarque ausente | Faz revisão manual |
| Régua econômica e piso de custo | Aplicados |
| Segurança, meta e guarda da estratégia externa | Cobertos por testes do núcleo |
| Sintaxe Java (`./run-static-checks.sh`) | 31 arquivos válidos |
| XML do projeto | 6 arquivos válidos |
| Modelo proibido no código do aplicativo | Nenhuma ocorrência |
| Compilação Android (`:app:assembleDebug`) | Aprovada no GitHub Actions em 25/09/2026 |
| Integridade do APK | Arquivo ZIP interno válido e APK com bloco de assinatura |
| Teste no telefone com Uber e 99 | Pendente |

O fluxo de compilação confirmou Java 17, Gradle 9.5, Android SDK 36 e Build Tools 36.0.0. Os 11 testes do núcleo passaram novamente antes da montagem do APK. A próxima etapa depende de teste físico para conferir permissões, interface, coleta de localização e leitura das telas instaladas da Uber e da 99.
