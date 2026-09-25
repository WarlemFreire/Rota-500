# Rota 500, MVP Android

Projeto Android nativo, em Java, para apoiar um motorista de Uber e 99. A meta inicial é R$ 500 brutos por dia. O app observa ofertas durante uma jornada iniciada pelo motorista, mostra uma avaliação na tela, guarda localização e resultados, calcula orçamentos particulares e aprende padrões de faturamento a partir dos totais informados.

Na versão 0.2, a leitura de ofertas usa captura de tela autorizada pelo Android e OCR local. A permissão de Acessibilidade foi retirada para evitar o bloqueio de instalação encontrado no Android 16. A calibração em um aparelho real ainda precisa ocorrer.

O arquivo `CLOUD_BUILD.md` explica a compilação no GitHub Actions pelo celular. O fluxo de trabalho está em `.github/workflows/build-apk.yml`. Use apenas um repositório privado.

## Compilar novamente

O fluxo `.github/workflows/build-apk.yml` compila automaticamente cada atualização da branch `main` e disponibiliza o artefato `Rota500-apk-debug`. Para uma compilação local:

1. Instale Android Studio com JDK 17, Android SDK Platform 36 e Build Tools 36.0.0.
2. Abra a pasta `rota500` como projeto no Android Studio. O projeto usa Android Gradle Plugin 9.3.3 e Gradle 9.5.0. O arquivo `gradle/wrapper/gradle-wrapper.properties` registra a versão, mas **não há `gradlew` nem `gradle-wrapper.jar`** neste pacote. Configure a distribuição Gradle 9.5.0 na IDE ou gere um wrapper padrão em um ambiente que já possua Gradle.
3. Sincronize o projeto. Gere o APK de depuração pela IDE, ou execute `gradle :app:assembleDebug` com o SDK e o Gradle instalados.
4. O APK resultante deve ficar em `app/build/outputs/apk/debug/app-debug.apk`.

Requer Android 11 ou superior (`minSdk 30`). O aplicativo foi configurado para Android 16 (`compileSdk` e `targetSdk` 36). Não precisa ser publicado na Play Store.

## Primeira utilização

1. Abra **Regras e configurações**. Confira meta, custo total por quilômetro, régua de R$/km, jornada e áreas de risco.
2. Toque em **Iniciar jornada**, permita localização e autorize o compartilhamento da tela inteira. A imagem não é gravada: os quadros são processados na memória e descartados.
3. Para enxergar **ACEITAR**, **RECUSAR** ou **CONFERIR** sobre a tela da Uber e da 99, toque em **Mostrar decisão sobre Uber e 99** e libere a sobreposição. Sem essa autorização, a recomendação aparece como notificação.
4. Faça a calibração com ofertas reais. Confira valor, tempo, quilometragem e destino lidos pelo aplicativo. O aceite e a recusa são manuais.
5. Atualize o **bruto acumulado da jornada atual** periodicamente, somando Uber, 99 e corridas particulares. O painel soma as jornadas iniciadas no mesmo dia. O app ainda não lê o faturamento realizado diretamente dos extratos das plataformas.
6. Faça um backup dos dados em uma pasta escolhida pelo seletor do Android. Se o provedor Google Drive do aparelho permitir seleção de pasta e gravação, ela pode ser usada. Restauração pelo arquivo JSON é feita no painel, depois de encerrar a jornada.

## O que funciona no código

- Ofertas: captura contínua de quadros em memória, OCR latino local e análise a cada 350 ms, sempre descartando quadros antigos. Calcula deslocamento até embarque + corrida, R$/km e, em viagens longas, R$/h. Leitura incompleta ou com preços conflitantes pede conferência.
- Segurança: nomes cadastrados de áreas proibidas; novas áreas podem ser marcadas com nome, coordenada atual e raio. A avaliação de uma rota inteira ainda não existe. A mensagem na oferta deixa isso explícito.
- Aceite e recusa manuais. A versão de instalação fácil não usa Acessibilidade e não toca nos botões da Uber ou da 99.
- Jornada: recomenda encerrar quando a meta é alcançada ou após 12 horas. O acompanhamento é encerrado após até 18 horas, com checagem periódica. A localização fica ativa somente durante a jornada iniciada pelo motorista.
- Estratégia: motor local com regras e estimativas por medianas, faixa de 3 horas e grade geográfica aproximada. Aprende com variações de faturamento total informadas ao longo do tempo. A IA externa opcional usa um endpoint HTTPS compatível com chat completions, modelo e chave escolhidos pelo usuário. Não há fornecedor ou modelo pré-selecionado. Se falhar, a estratégia local continua.
- Custos: o custo operacional configurado impede recomendar corridas abaixo do próprio custo por km. O padrão de R$ 1,50/km é uma estimativa inicial a ser conferida no seu carro. A régua bruta de R$ 1,20/km continua configurável, mas o custo maior prevalece.
- Orçamento particular: dados do cliente e trajeto, tempo, km, espera, retorno, pedágio e estacionamento; preço sugerido pelo maior piso entre operação e oportunidade. Salva status e histórico por telefone; compartilha texto do orçamento. O valor líquido alternativo nos apps deve ser informado manualmente.
- Dados: SQLite local, backup JSON e restauração transacional. Prints do mapa não são armazenados nem entram no backup. A chave da API fica nas preferências do aparelho e não entra no backup. O banco local não usa criptografia adicional.

## Limites antes do teste físico

- As telas de Uber e 99 mudam. O OCR e o parser precisam ser calibrados no aparelho. A meta é exibir a decisão em menos de um segundo, mas o tempo real depende do telefone e deve ser medido em ofertas reais.
- Alguns aplicativos podem impedir a captura de sua própria tela. É necessário confirmar fisicamente se as versões instaladas da Uber e da 99 permitem a leitura.
- O mapa dinâmico usa uma contagem experimental de cores da imagem. Cores de controles, trânsito e fundo podem confundir a leitura. A imagem é processada na memória e descartada; o resultado não deve sustentar sozinho uma mudança de área.
- O clima é pedido manualmente quando o app detecta o carro parado. Não há fonte automática de clima.
- A detecção de área insegura por nome e círculo local não identifica todos os trajetos, vias alternativas ou pontos de embarque. Ela não substitui sua avaliação do mapa e da segurança real.
- A taxa robusta depende dos totais de faturamento que você registrar ao longo da jornada. Ofertas exibidas não são dinheiro recebido. Ainda não há integração com a conclusão das corridas nos apps.
- O aprendizado ajusta estimativas com os dados coletados. **Não há garantia de atingir R$ 500 por dia** em um ou dois meses. A consistência deve ser medida em dias comparáveis, observando horas, custo e exposição a risco.

## Verificação neste ambiente

```sh
./run-core-tests.sh
./run-static-checks.sh
```

Os resultados e as restrições da verificação estão em `TEST_REPORT.md`.
