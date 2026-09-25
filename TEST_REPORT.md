# Relatório de validação da versão 0.2, 25/09/2026

| Verificação | Resultado |
| --- | --- |
| Testes Java do núcleo (`./run-core-tests.sh`) | 11 aprovados |
| Parser de tela com preço ambíguo e embarque ausente | Faz revisão manual |
| Régua econômica e piso de custo | Aplicados |
| Segurança, meta e guarda da estratégia externa | Cobertos por testes do núcleo |
| Sintaxe Java (`./run-static-checks.sh`) | 32 arquivos válidos |
| XML do projeto | 5 arquivos válidos |
| Modelo proibido no código do aplicativo | Nenhuma ocorrência |
| Serviço ou permissão de Acessibilidade | Ausente |
| Captura de tela | Serviço `mediaProjection` declarado |
| Compilação Android (`:app:assembleDebug`) | Aprovada na execução 36132649861 |
| Integridade do APK | Arquivo ZIP interno válido e APK com bloco de assinatura |
| Arquitetura nativa | ARM 64 bits, compatível com o Moto G56 5G |
| Tamanho do APK | 22.334.169 bytes |
| SHA-256 | `0e5db8f19c44eec8e77013300a32060c1f645e732a54ad1442fc509851cf61b4` |
| Teste no telefone com Uber e 99 | Pendente |

O fluxo de compilação confirmou Java 17, Gradle 9.5, Android SDK 36 e Build Tools 36.0.0. Os 11 testes do núcleo passaram novamente antes da montagem do APK. O OCR latino está incluído no pacote e analisa o quadro mais recente a cada 350 ms, sem salvar a imagem. A próxima etapa depende de teste físico para conferir permissões, interface, coleta de localização, leitura das telas instaladas da Uber e da 99 e tempo total até a recomendação.
