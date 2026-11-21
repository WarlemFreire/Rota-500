# Rota 500, MVP Android

Projeto Android nativo, em Java, para apoiar um motorista de Uber e 99. A meta inicial é R$ 500 brutos por dia. O app observa ofertas durante uma jornada iniciada pelo motorista, mostra uma avaliação na tela, guarda localização e resultados, calcula orçamentos particulares e aprende padrões de faturamento a partir dos totais informados.

Versão atual: **0.9** (finanças e custos, jornada esquecida e atualização pelo próprio app). A especificação completa, com as decisões numeradas, está em `docs/SPEC-0.9.md`.

A leitura de ofertas usa captura de tela autorizada pelo Android e OCR local. **No aparelho real ela ainda não funcionou**: com a leitura ligada, nenhum aviso aparece. O diagnóstico e a correção ficam para a 0.10.

O arquivo `CLOUD_BUILD.md` explica a compilação e a publicação de versões pelo GitHub Actions.

## Novidades da 0.9

- **Custo por km único:** combustível + desgaste. O combustível é medido pelos abastecimentos (valor pago ÷ km entre tanques cheios, média dos 3 últimos ciclos). Até haver um ciclo completo, vale um valor temporário. Na atualização, o antigo "custo total" é dividido para manter o mesmo total (R$ 1,30 + R$ 0,20 no padrão).
- **Abastecimento:** valor, litros, odômetro e tanque cheio, em Gastos > Combustível ou ao retomar a pausa "Abastecer".
- **Distância do dia pelo GPS**, sem pausas, pontos imprecisos ou saltos. O odômetro prevalece quando houver duas leituras.
- **Painel:** líquido estimado e dinheiro no bolso lado a lado.
- **Dia = jornada:** tudo o que acontece numa jornada que passa da meia-noite conta no dia em que ela começou. Se depois da meia-noite o total da Uber/99 digitado for menor, o app pergunta se a plataforma zerou. O histórico foi reprocessado com essa regra depois de uma cópia do banco.
- **Corrida particular concluída** entra no ganho do dia; desfazer o status anula o lançamento.
- **Jornada esquecida:** notificação permanente com tempo ativo e bruto; pergunta "Ainda trabalhando?" após 1 h ativa sem ganho (pausa: 2 h); três perguntas sem resposta encerram a jornada no último ganho, com opção de reabrir. Depois de reiniciar o celular, um aviso pede para continuar ou encerrar. O limite de 18 h foi removido.
- **Buscar atualizações** (Mais): baixa a versão mais nova das Releases do GitHub e abre o instalador. Ajustes > Atualizações liga o canal de teste. Funciona quando o repositório estiver público.

## Compilar novamente

O fluxo `.github/workflows/build-apk.yml` compila cada atualização da `main` e das branches de trabalho. Detalhes de versões, canal de teste e assinatura estão em `CLOUD_BUILD.md`. Para uma compilação local:

1. Instale Android Studio com JDK 17, Android SDK Platform 36 e Build Tools 36.0.0.
2. Abra a pasta do projeto no Android Studio. O projeto usa Android Gradle Plugin 9.3.3 e Gradle 9.5.0. **Não há `gradlew` nem `gradle-wrapper.jar`**: configure a distribuição Gradle 9.5.0 na IDE ou gere um wrapper.
3. Gere o APK de depuração pela IDE, ou execute `gradle :app:assembleDebug`.
4. O APK fica em `app/build/outputs/apk/debug/app-debug.apk`.

Requer Android 11 ou superior (`minSdk 30`). O aplicativo foi configurado para Android 16 (`compileSdk` e `targetSdk` 36). Não precisa ser publicado na Play Store.

O APK contém apenas bibliotecas nativas ARM de 64 bits, compatíveis com o Moto G56 5G. As versões publicadas são assinadas com a chave fixa de `signing/rota500.p12`, protegida por senha guardada só como Secret do GitHub.

## Primeira utilização

1. Abra **Regras e configurações**. Confira meta, custo total por quilômetro, régua de R$/km, jornada e áreas de risco.
2. Toque em **Iniciar jornada**, permita localização e autorize o compartilhamento da tela inteira. A imagem não é gravada: os quadros são processados na memória e descartados.
3. Para enxergar o semáforo de **KM**, **HORA** e **NOTA**, seguido de **ACEITAR** ou **RECUSAR**, toque em **Mostrar decisão sobre Uber e 99** e libere a sobreposição. Sem essa autorização, a recomendação aparece como notificação.
4. Faça a calibração com ofertas reais. Confira valor, tempo, quilometragem e destino lidos pelo aplicativo. O aceite e a recusa são manuais.
5. Registre o **total do dia da Uber e da 99** periodicamente (o valor substitui o anterior) e cada corrida particular (o valor soma). O painel soma as jornadas iniciadas no mesmo dia. O app não lê o faturamento diretamente dos extratos das plataformas.
6. Em Gastos, confira o desgaste por km e registre os abastecimentos com odômetro para o custo real de combustível.
7. Faça um backup dos dados em uma pasta escolhida pelo seletor do Android. Se o provedor Google Drive do aparelho permitir seleção de pasta e gravação, ela pode ser usada. Restauração pelo arquivo JSON é feita no painel, depois de encerrar a jornada.

## O que funciona no código

- Ofertas: captura contínua de quadros em memória, OCR latino local e análise a cada 350 ms, sempre descartando quadros antigos. Calcula deslocamento até embarque + corrida, R$/km e, em viagens longas, R$/h. Leitura incompleta ou com preços conflitantes pede conferência.
- Segurança: nomes cadastrados de áreas proibidas; novas áreas podem ser marcadas com nome, coordenada atual e raio. A avaliação de uma rota inteira ainda não existe. A mensagem na oferta deixa isso explícito.
- Aceite e recusa manuais. A versão de instalação fácil não usa Acessibilidade e não toca nos botões da Uber ou da 99.
- Jornada: recomenda encerrar quando a meta é alcançada ou após 12 horas. A jornada esquecida é tratada pelas perguntas de inatividade. A localização fica ativa somente durante a jornada iniciada pelo motorista.
- Estratégia: motor local com regras e estimativas por medianas, faixa de 3 horas e grade geográfica aproximada. Aprende com variações de faturamento total informadas ao longo do tempo. A IA externa opcional usa um endpoint HTTPS compatível com chat completions, modelo e chave escolhidos pelo usuário. Não há fornecedor ou modelo pré-selecionado. Se falhar, a estratégia local continua.
- Custos: combustível/km (real pelos abastecimentos, ou temporário) + desgaste/km impedem recomendar corridas abaixo do próprio custo. O desgaste de R$ 0,20/km é uma sugestão grosseira a conferir no seu carro. A régua bruta de R$ 1,20/km continua configurável, mas o custo maior prevalece.
- Orçamento particular: dados do cliente e trajeto, tempo, km, espera, retorno, pedágio e estacionamento; preço sugerido pelo maior piso entre operação e oportunidade. Salva status e histórico por telefone; compartilha texto do orçamento; corrida CONCLUÍDA entra no ganho. O valor líquido alternativo nos apps deve ser informado manualmente.
- Dados: SQLite local (versão 5), backup JSON (formato v3; v1 e v2 são reprocessados ao restaurar) e restauração transacional. Antes de migrar para a versão 5, o app copia o banco para `rota500-copia-*.db` na pasta interna do app. Prints do mapa não são armazenados nem entram no backup. A chave da API fica nas preferências do aparelho e não entra no backup. O banco local não usa criptografia adicional.

## Limites antes do teste físico

- As telas de Uber e 99 mudam. O OCR e o parser precisam ser calibrados no aparelho. A meta é exibir a decisão em menos de um segundo, mas o tempo real depende do telefone e deve ser medido em ofertas reais.
- Alguns aplicativos podem impedir a captura de sua própria tela. É necessário confirmar fisicamente se as versões instaladas da Uber e da 99 permitem a leitura.
- O mapa dinâmico usa uma contagem experimental de cores da imagem. Cores de controles, trânsito e fundo podem confundir a leitura. A imagem é processada na memória e descartada; o resultado não deve sustentar sozinho uma mudança de área.
- O clima é pedido manualmente quando o app detecta o carro parado. Não há fonte automática de clima.
- A detecção de área insegura por nome e círculo local não identifica todos os trajetos, vias alternativas ou pontos de embarque. Ela não substitui sua avaliação do mapa e da segurança real.
- A distância pelo GPS tende a ficar abaixo da real (pontos a cada 15 a 30 s cortam curvas). Use o odômetro para corrigir.
- A taxa robusta depende dos totais de faturamento que você registrar ao longo da jornada. Ofertas exibidas não são dinheiro recebido. Ainda não há integração com a conclusão das corridas nos apps.
- O aprendizado ajusta estimativas com os dados coletados. **Não há garantia de atingir R$ 500 por dia** em um ou dois meses. A consistência deve ser medida em dias comparáveis, observando horas, custo e exposição a risco.

## Verificação neste ambiente

```sh
./run-core-tests.sh
./run-static-checks.sh
```

Os resultados e as restrições da verificação estão em `TEST_REPORT.md`.
