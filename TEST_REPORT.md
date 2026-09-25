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
| Compilação Android (`:app:assembleDebug`) | Bloqueada: Android SDK, Gradle e wrapper não estão instalados |
| Teste no telefone com Uber e 99 | Pendente |

Tentativas de conexão a `services.gradle.org` e `dl.google.com` expiraram neste ambiente. A aprovação dos testes Java não significa que a interface Android compilou ou que a leitura por acessibilidade funcionará com as versões instaladas da Uber e da 99. O próximo teste deve gerar o APK em Android Studio, corrigir erros de compilação que apareçam e então calibrar as telas em um telefone real.
