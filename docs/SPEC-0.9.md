# Rota 500, especificação da versão 0.9

Decisões tomadas em entrevista (método *grilling*) em 21/11/2026. Os números Qn
são a referência de cada decisão.

## Fora desta versão

- Leitura de ofertas: nunca funcionou no aparelho real (nada aparece com a leitura
  ligada). Fica para a 0.10, começando por um modo de diagnóstico (quadro preto?
  texto lido? motivo do descarte?).

## A. Finanças e custos

| Q | Decisão |
| --- | --- |
| Q1, Q13 | O "custo total R$/km" de Ajustes deixa de existir. O custo usado nas ofertas e no líquido é combustível/km + desgaste/km. Na migração: combustível temporário R$ 1,30 + desgaste R$ 0,20 (total R$ 1,50, nenhuma decisão de oferta muda no dia). Se o custo antigo salvo for diferente de 1,50, o combustível temporário recebe (custo antigo − 0,20), nunca abaixo de zero. |
| Q10, Q12 | Desgaste cobre pneus, óleo, freios e revisões, sem depreciação. Valor manual, padrão sugerido R$ 0,20/km. |
| Q8 | Combustível real/km = valor pago ÷ km rodados, média dos últimos 3 ciclos de tanque cheio a tanque cheio, somando abastecimentos parciais do meio. Litros servem para mostrar km/l. |
| Q9 | Com 2 tanques cheios registrados (1 ciclo), o app troca sozinho do temporário para o real e avisa. Divergência acima de 30% em relação ao temporário fica destacada. |
| — | Lançamento de abastecimento: valor, litros, odômetro, tanque cheio sim/não. Em Gastos > Combustível e ao retomar uma pausa "Abastecer". |
| Q11 | O odômetro do abastecimento conta como leitura de odômetro do dia. |
| Q2 | Distância do dia pelo GPS, só fora das pausas, descartando pontos com precisão pior que 50 m e saltos acima de 150 km/h. O odômetro prevalece quando houver duas leituras válidas. |
| — | Líquido estimado = bruto − (combustível/km + desgaste/km) × km − alimentação − outros. Não desconta fixos nem manutenção paga. |
| Q14, Q18 | Painel mostra líquido estimado e "no bolso hoje" lado a lado, mesmo tamanho. |
| Q3, Q4 | Particular marcada CONCLUÍDA lança o valor (ajustável) como ganho no dia da marcação, com ou sem jornada. Sair de CONCLUÍDA anula o lançamento. |
| Q5, Q7 | Dia = jornada: ganhos, gastos, abastecimentos, odômetro e pausas ficam no dia em que a jornada começou. Sem jornada ativa, vale a data do calendário. |
| — | Na mesma jornada, depois da meia-noite, total da Uber/99 menor que o anterior: o app pergunta "O total zerou à meia-noite?". Sim soma ao anterior; Não substitui. |
| Q15, Q16 | O histórico é reprocessado para dia = jornada, depois de uma cópia completa do banco bem-sucedida (arquivo interno). |
| Q17 | Backups antigos restaurados passam pelo mesmo reprocessamento. |

## B. Buscar atualizações

| Q | Decisão |
| --- | --- |
| Q19 | Fonte: GitHub Releases do repositório (funciona quando ele for público). |
| Q21 | Botão em Mais > "Buscar atualizações": baixa o APK e abre o instalador do Android. Backup antes, se houver pasta configurada. |
| Q23 | Sem verificação automática. Só pelo botão. |
| Q25 | Ajustes > "Receber versões de teste" (ligado por padrão durante o teste da 0.9). |
| Q24, Q26 | versionCode = número da execução do build no GitHub (sempre cresce). Versão visível 0.9.0 na estável, 0.9.0-teste.N nos testes. Release estável só quando o versionName muda. |
| Q27, Q29 | Versão de teste publicada só quando o commit da branch tem `[publicar-teste]` na mensagem. |
| Q28 | Manter só as 5 versões de teste mais recentes. |
| Q20, Q30 | Chave de assinatura fixa, guardada criptografada no repositório. Um Secret no GitHub com a senha. Exige reinstalar uma vez. |
| Q22 | Áreas de risco e cidade continuam no código. |

## D. Jornada esquecida

| Q | Decisão |
| --- | --- |
| Q31 | Notificação permanente: "Tempo ativo 5:42 · R$ 320 hoje", botões Pausar/Retomar e Encerrar, estratégia na linha de baixo. |
| Q42 | Se for dispensada, volta na hora enquanto a jornada estiver aberta. |
| Q32 | Após reiniciar o celular com jornada aberta: "Jornada aberta desde 08:10 — Continuar / Encerrar". |
| Q33, Q35 | 1 h de tempo ativo sem ganho: "Ainda trabalhando?" com Sim / Registrar ganho / Encerrar. Sim reinicia a contagem de 1 h. |
| Q38, Q40 | Pausa aberta há 2 h: "Ainda em pausa?" com Retomar / Encerrar, repetindo a cada 2 h. |
| Q37, Q40 | 3 perguntas seguidas sem resposta: encerra no horário do último ganho (pausa: no início da pausa) e avisa com opção de Reabrir. |
| Q39 | Sem ganho na jornada: usa o último movimento do GPS; sem GPS, início da jornada + 1 h. |
| Q36 | Encerrar manualmente após 1 h+ sem ganho pergunta: "Agora" ou "No último ganho". |
| Q41 | Perguntas vibram uma vez, sem som. |
| Q43 | Limite automático de 18 h removido. |

## E. Importar o backup do Copiloto

O Copiloto é outro app (web), com formato `app: "copiloto"`, `versao: 1`. Mais > "Importar do Copiloto" lê esse arquivo, mostra uma prévia e só grava depois da confirmação, numa transação única, após copiar o banco.

| Q | Decisão |
| --- | --- |
| — | Saldos do Copiloto são o total do dia de cada plataforma e viram leituras de plataforma. Ganhos avulsos viram ganho particular, entrega ou outro. Jornadas de planilha somam as corridas (`valorBruto`). |
| Q44 | Jornadas com mais de 14 h ativas foram esquecidas abertas: fecham no último ganho válido. |
| — | Planilha não registra o tempo trabalhado: intervalo de mais de 2 h sem corrida conta como pausa. |
| Q45 | Gasto acima de R$ 1.000 num lançamento e odômetro final menor que o inicial (ou mais de 2.000 km) ficam de fora e aparecem na prévia. |
| Q46 | Custos importados: desgaste do Copiloto e combustível temporário medido nos abastecimentos com odômetro (valor pago ÷ km); sem dados suficientes, pelos preços e consumo configurados. |
| — | Jornada com o mesmo início de uma já gravada não é importada de novo. Ofertas, contextos, metas por período e o modelo estatístico do Copiloto não têm equivalente e ficam de fora. |

## F. Modo diagnóstico da leitura

Mais > "Diagnóstico da leitura". Não muda nenhuma decisão de oferta; só registra, quando ligado:

- quadros analisados e quantos chegaram pretos (proteção de tela da Uber/99 ou tela apagada);
- cada leitura do OCR com a etapa em que parou: quadro preto, OCR vazio, não parece oferta (e o que faltou), leitura incompleta (e o motivo: mais de um R$, número de km/min diferente de 2, R$/km divergente), sem valor, oferta repetida ou decisão mostrada;
- o recorte exato enviado ao OCR, sob pedido ("Salvar o próximo quadro lido").

Tudo fica na pasta interna do app (últimos 300 eventos) até o motorista compartilhar o relatório ou o quadro. O texto lido pode conter endereços e nomes de passageiros.
