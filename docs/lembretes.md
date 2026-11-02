> Atualização de 28/10/2026: novos cadastros usam lembretes automáticos. Veja [lembretes-automaticos.md](lembretes-automaticos.md). O texto abaixo registra a implementação personalizada anterior, preservada para registros existentes.

# Lembretes por atividade — guia de estudo

Etapa alinhada entre Everton, Codex e Gemini em 27/10/2026. Everton escolheu data
e horário por atividade e validou manualmente o fluxo anterior de atividades.
Codex implementou esta etapa; Gemini revisou o código por leitura, sem apontar
falha concreta. Os resultados dos testes ficam ao final deste documento.

## Comportamento na tela

No formulário existe a opção **Receber lembrete desta atividade**. Ela começa
desativada e não torna o lembrete obrigatório. Ao ativá-la, o usuário escolhe uma
data e um horário futuros, com calendário e relógio. Rascunho e seleção sobrevivem
à recriação da tela; somente Salvar persiste a configuração.

O botão **Ativar ou ajustar notificações** faz o pedido contextual no Android 13+
quando apropriado. Uma recusa não impede salvar a atividade. Após uma recusa que
não permite novo pedido, ou para rever configurações, o botão abre os ajustes de
notificações do próprio aplicativo. O status considera a permissão global e o
canal de lembretes. Não há pedido no login.

O texto informa que a entrega pode atrasar por economia de bateria. Não se trata
de um despertador com precisão de minuto. A opção usa WorkManager e não pede
acesso especial a alarmes exatos. A permissão e o recebimento real precisam da
validação manual do usuário; os testes não concedem permissões silenciosamente.

## Prazo e horário de lembrete são conceitos diferentes

- `prazoEpochDay` continua sendo um dia de calendário (`LocalDate`), sem hora.
- `lembreteEpochMillis` é um instante: data/hora escolhidas no fuso do aparelho,
  convertidas para milissegundos desde a época UTC. A tela mostra também o fuso.
- Alterar o fuso depois representa o mesmo instante em outro horário local. O
  agendamento não representa uma repetição diária nem um horário flutuante.
- Um horário inexistente durante avanço de horário de verão é recusado. Em uma
  sobreposição de horário, a conversão padrão de Java usa o primeiro offset.

Novos horários precisam estar no futuro. Uma edição de outro campo pode manter
um lembrete original já vencido; isso não recria o agendamento passado. Para
receber outro aviso, deve-se escolher um horário futuro.

## Arquivos e responsabilidades

| Arquivo/classe | Explicação para apresentação |
| --- | --- |
| `data/Atividade.kt` | Acrescenta horário opcional e indicador de lembrete enviado. |
| `data/CampusFlowDatabase.kt` | Versão 3; MIGRATION_2_3 adiciona colunas sem recriar tabelas, com horário NULL e enviado 0 para registros antigos. Mantém a cadeia 1→2→3. |
| `data/AtividadeDao.kt` | Consulta dados atuais e marca enviado apenas se ID, horário e estado ainda correspondem ao envio. |
| `AtividadeFormViewModel.kt` | Valida, restaura e salva a configuração. Conserva enviado ao editar somente texto e reinicia o indicador ao escolher outro horário. Solicita sincronização após a gravação/exclusão. |
| `AtividadeFormActivity.kt` e `activity_atividade_form.xml` | Switch opcional, calendário/relógio, explicação, status e acesso contextual à permissão. |
| `lembretes/Lembretes.kt` — `Lembretes` | Coordena trabalhos únicos por ID. Compara o horário com as tags do trabalho; conserva o mesmo agendamento e usa REPLACE quando muda. Cancela ao concluir, excluir ou desativar. Reabrir só agenda novamente se o horário ainda for futuro e o lembrete não tiver sido enviado. |
| Mesmo arquivo — `LembreteWorker` | Executa a tarefa em segundo plano. Traz apenas ID e horário esperado; reconsulta o banco em vez de usar título/status antigos. |
| Mesmo arquivo — `EntregadorLembrete` | Confere existência, conclusão, horário, indicador enviado, relógio e autorização antes de publicar. Uma transação serializa essa verificação com gravações concorrentes no banco. |
| Mesmo arquivo — `Notificacoes` | Cria o canal API 26+, usa ícone próprio, publica por tag/ID e prepara abertura da atividade correta com uma pilha de navegação. |
| `MainActivity.kt` | Reconcilia agendamentos futuros ao retornar ao resumo e remove trabalhos órfãos. |
| `AtividadesViewModel.kt` / `AtividadesActivity.kt` | Concluir/reabrir sincroniza o lembrete. Uma falha de agendamento não é apresentada como se a alteração do status tivesse falhado. |
| `AndroidManifest.xml` | Declara POST_NOTIFICATIONS; sem solicitação de alarme exato. |
| `gradle/libs.versions.toml` / `app/build.gradle.kts` | WorkManager 2.12.0, compatível com o minSdk 26 do projeto. |
| `app/schemas/.../3.json` | Esquema exportado da nova versão. As versões anteriores permanecem preservadas. |

## Segurança contra lembretes obsoletos e duplicação comum

O horário funciona como uma versão do agendamento. Um trabalho antigo encontra
um horário diferente no banco e não envia. Excluídas, concluídas, desativadas e
já enviadas também são descartadas. A notificação usa uma identidade estável por
atividade, e o indicador enviado evita novas publicações no fluxo normal.

A publicação no Android e o commit SQLite são sistemas diferentes: a transação
do banco não torna ambos atomicamente indivisíveis. Uma queda de processo entre
publicar e confirmar o indicador pode repetir o aviso na recuperação; a mesma
tag/ID substitui a notificação visível. Não afirmar garantia absoluta de entrega
exatamente uma vez.

WorkManager conserva trabalhos já enfileirados entre reinícios. A reconciliação
recupera agendamentos futuros se houver interrupção entre salvar e enfileirar.
Ela não recria horários vencidos sem trabalho pendente. Se um aviso foi bloqueado
por permissão/canal na hora da execução, não é disparado retroativamente ao
permitir notificações: escolher outro horário futuro para um novo lembrete.

## Testes automatizados

- `LembretesTest`: migração real a partir do esquema 2, preservando IDs/campos;
  envio por publicador de teste, recusa de permissão, horário antecipado, token
  obsoleto, duplicação, conclusão, desativação, alteração e exclusão; falha de
  publicação não marca enviado.
- `LembreteFormTest`: horário obrigatório quando ativado, recusa de horário passado,
  rascunho na recriação, persistência, conservação do mesmo trabalho, troca por
  novo horário, cancelamento, reabertura e exclusão. Usa apenas fixtures futuras.
- A suíte anterior continua cobrindo atividades, disciplinas, datas e resumo.

O publicador falso valida as decisões sem publicar avisos no aparelho do usuário.
Não substitui a observação de uma notificação real e de seu toque.

## Roteiro manual com Everton

1. Abrir Nova atividade e conferir que lembrete é opcional. Salvar sem ativá-lo.
2. Ativar lembrete e tentar salvar sem horário; conferir mensagem de validação.
3. Escolher data/hora futuras e girar o aparelho; conferir preservação.
4. Usar Ativar ou ajustar notificações. Recusar, salvar e conferir que o app continua
   funcionando; depois voltar aos ajustes e permitir se desejar.
5. Com notificações permitidas, salvar um horário próximo e aguardar (pode atrasar).
   Conferir título/ícone; tocar e verificar atividade correta e navegação de volta.
6. Alterar horário antes do envio e conferir que o anterior não publica. Repetir
   concluindo, desativando e excluindo a atividade antes do horário.
7. Fechar o aplicativo e, separadamente, reiniciar o aparelho com lembrete futuro;
   verificar a entrega. Testar canal bloqueado, API 26, modo de economia de bateria
   e aparelho físico. Evitar confundir Fechar com Forçar parada do pacote.

## Referências oficiais consultadas

- [WorkManager e persistência](https://developer.android.com/develop/background-work/background-tasks/persistent).
- [Atraso inicial e configuração do trabalho](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).
- [Versão do WorkManager](https://developer.android.com/jetpack/androidx/releases/work).
- [Permissão de notificações](https://developer.android.com/develop/ui/compose/notifications/notification-permission).
- [Canais de notificação](https://developer.android.com/develop/ui/compose/notifications/channels).

## Resultado desta execução

`assembleDebug`, `assembleDebugAndroidTest`, 5 testes unitários e lint passaram
(0 erros, 21 avisos). A rodada final em API 37 passou: **OK (16 tests)**, 32,854 s,
em `instrumentacao-lembretes-final.txt` nos entregáveis locais da conversa.
Uma primeira rodada falhou apenas na leitura de um campo opcional do JSON do
teste de migração; a correção foi feita no teste e a suíte inteira repetida.

Backup antes/depois confirmou os mesmos IDs e todos os campos preexistentes das
2 disciplinas e 1 atividade do usuário. A migração acrescentou apenas as colunas
de lembrete. Não restaram fixtures. Sem commit, push ou merge nesta etapa.
Permissões e notificação real, API 26, aparelho físico, economia de bateria e
reinício do aparelho continuam pendentes de validação manual.

## Verificação de horário próximo — 27/10/2026

Everton relatou recusa ao escolher um horário próximo. Na investigação, o emulador
estava com `persist.sys.timezone=GMT`: 21:08:57 no Android e 18:08:59 no computador
em São Paulo (UTC−03). A data/hora exatas que o usuário tentou selecionar ainda
não foram informadas; a diferença é uma hipótese concreta para esse caso, não uma
causa confirmada apenas pelo relato.

Foi melhorada a indicação de contexto, sem mudar o fuso do sistema nem gravar
horários passados como se fossem futuros:

- Formulário mostra referência de data, hora e ID do fuso do aparelho.
- Seletor usa 24 horas, coerente com a exibição HH:mm; seu título informa o relógio
  do aparelho ao abrir. A referência não é um relógio animado durante o diálogo.
- Nova seleção começa cerca de 5 minutos à frente quando não há horário futuro
  salvo. Uma escolha antiga vencida não é reutilizada como sugestão.
- Erro de horário já passado é separado de horário inexistente na mudança de fuso.
- `lembretes/HorarioLembrete.kt` concentra a conversão testável com data, fuso e
  instante de referência explícitos; nenhuma regra fixa o aplicativo ao Brasil.

O seletor tem precisão de minuto: se agora são 21:08:57, escolher 21:08 significa
21:08:00 e deve ser recusado; 21:09 é futuro e pode ser aceito, inclusive com apenas
3 segundos de antecedência. Se o usuário permanecer preenchendo por muito tempo,
o horário pode vencer antes de Salvar e precisa ser escolhido novamente.

`HorarioLembreteTest` cobre próximo minuto, minuto atual, GMT versus São Paulo,
virada de dia e horário inexistente em transição de fuso. `LembreteHorarioUiTest`
exercita calendário e relógio reais do Android, formato 24h, recusa de minuto
atual e aceitação de +2 minutos, incluindo recriação do formulário sem salvar.
Resultado: build debug/APK de testes e lint aprovados (0 erros/21 avisos),
**10 testes unitários e 17 instrumentados aprovados**. A instrumentação registrou
`OK (17 tests)` em 51,729 s em `instrumentacao-horario-lembrete.txt`. O seletor real
aceitou +2 minutos no fuso GMT do emulador e preservou a escolha após recriação.
O teste unitário também aceita o próximo minuto com 3 segundos de antecedência.
O backup final manteve as 2 disciplinas e 1 atividade anteriores intactas.
Aplicativo atualizado instalado, sem commit/push/merge.

### Verificação da roda do mouse — 28/10/2026

Relato: roda do mouse não movimentava o formulário de criar atividade no emulador.
O teste instrumentado `AtividadeMouseScrollTest` abre o formulário sem salvar dados,
confirma conteúdo fora da área visível e envia eventos Android `ACTION_SCROLL`,
`SOURCE_MOUSE` e `AXIS_VSCROLL` pela Activity nos dois sentidos. Resultado: 1 teste
aprovado; o ScrollView mudou sua posição para baixo e retornou para cima.
Isso valida a resposta do formulário a eventos Android, mas não reproduz o caminho
do mouse físico através da janela do Android Studio/emulador. A causa do relato
permanece não confirmada; nenhum ajuste de rolagem foi aplicado em produção.
O fuso do emulador foi ajustado e confirmado como `America/Sao_Paulo` (UTC−3),
sem fixar o fuso no aplicativo nem alterar os horários persistidos dos lembretes.
