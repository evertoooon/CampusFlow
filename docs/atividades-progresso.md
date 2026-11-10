# Atividades — plano e ponto de retomada

Plano alinhado entre Everton, Codex e Gemini no Android Studio em 26/10/2026.
Branch: `feature/activities`. Não fazer merge nem incluir `.idea` nos commits.

## Sequência combinada

1. Lista com `AtividadesActivity`, abas Pendentes/Concluídas usando Fragments,
   RecyclerView, consulta Room com JOIN para a disciplina e conclusão/reabertura
   rápida por ID. Prever carregamento, vazio, erro e nova tentativa.
2. Formulário para criar/editar: título, disciplina válida por ID, tipo, prazo por
   calendário, prioridade e descrição opcional. Preservar rascunho na recriação,
   confirmar exclusão e orientar cadastro de disciplina quando não houver nenhuma.
3. Resumo com contagens reais. Pendentes são todas as não concluídas; atrasadas
   são um subconjunto das pendentes. Não somar os três números como categorias
   independentes. A data de hoje precisa ser atualizada ao retomar e na virada do
   dia enquanto a tela estiver aberta.
4. Lembretes de atividades: oferecer ao usuário a opção, explicar o benefício e
   solicitar a permissão de notificações quando necessária. Recusar não deve
   impedir o uso. Implementar também o agendamento, cancelamento/reagendamento ao
   editar, concluir ou excluir e acesso posterior para ativação. Antes de codificar,
   usar data e horário escolhidos por atividade (preferência confirmada pelo
   Everton em 27/10/2026) e consultar a documentação Android
   vigente. A permissão sozinha não envia lembretes.

Referências consultadas para a etapa 4 (ainda sem implementação):
[permissão de notificações](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
e [agendamento de trabalho](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).
Android 13/API 33+ exige `POST_NOTIFICATIONS` para notificações comuns; o pedido
deve ter contexto e respeitar a recusa. WorkManager permite trabalho adiado, mas
a escolha de mecanismo dependerá da precisão exigida pelo lembrete. Não prometer
um horário exato sem definir e testar essa política.

## Regras de interface

- Manter identidade azul e fundo claro, fontes ampliadas, rolagem e insets.
- Cartão: título, disciplina, tipo, prazo, prioridade e atraso por texto.
- Marcar conclusão deve alterar apenas `concluida` por ID; bloquear toques
  repetidos durante gravação e informar erro sem apresentar sucesso falso.
- Nome e professor ajudam a distinguir disciplinas; o vínculo salvo usa o ID.
- Prazo é dia de calendário, sem horário. Hoje não está atrasado; datas passadas
  são permitidas. Uma atividade concluída não fica atrasada.
- Na entrega parcial da lista, não apresentar botões de cadastro/edição sem ação.

## Estado antes de iniciar a interface

A base Room v2, migração não destrutiva e bloqueio de exclusão de disciplina com
atividades já foram implementados e testados; detalhes em `atividades-base.md`.
Existem alterações locais ainda sem commit, incluindo essa base. Os dois arquivos
`.idea/gradle.xml` e `.idea/misc.xml` pertencem ao ambiente local e devem ser
preservados fora do commit.

Backup consistente criado antes desta etapa em entregáveis locais da conversa:
`campusflow-antes-exclusao-20261026-144721.db` (2 disciplinas). Não adicionar bancos
de usuário ao Git. Antes/depois de testes no emulador, verificar preservação dos
registros. Os testes Gradle conectados anteriormente geraram relatório com zero
testes; confirmar resultados reais pela instrumentação direta se isso se repetir.

## Validação e ponto final desta sessão

Atualizado em 27/10/2026:

- Etapas 1, 2 e 3 implementadas: lista/abas, CRUD de atividades e contagens reais.
- Gemini implementou a lista inicial; Codex revisou/ajustou, implementou formulário
  e resumo. Gemini revisou as etapas 2 e 3 por leitura e não apontou falhas concretas.
- Guia didático por arquivo, regras e roteiro manual em `atividades-interface.md`.
- `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest` e `lintDebug`
  passaram. Foram 5 testes unitários, 0 erros de lint e 19 avisos (recursos sem uso,
  desenho de fundo, rebind do adapter e composição de texto). Não são todos avisos
  preexistentes. Relatórios locais: `build-dashboard-atividades.txt` e lint em app/build.
- Uma suíte instrumentada intermediária passou integralmente: `OK (12 tests)` em
  `instrumentacao-form.txt`. Após acrescentar o resumo, a rodada de 13 executou e
  aprovou o teste de contagens, mas teve 3 falhas de foco de janela em testes de
  formulário/diálogo (`RootViewWithoutFocusException`); não declarar essa rodada
  inteira aprovada. Os retestes e o desfecho estão registrados abaixo.
- A primeira tentativa anterior da lista também falhou com o emulador adormecido;
  o reteste isolado passou. Emulador disponível API 37; API 26/aparelho físico,
  TalkBack, paisagem com fontes grandes e virada real do dia seguem no roteiro manual.
- Backup de 27/10 antes dos testes: `campusflow-antes-exclusao-20261027-123718.db`.
  Comparação posterior confirmou os mesmos IDs e dados das 2 disciplinas e 0
  atividades; fixtures de teste removidas. Backups ficam fora do repositório.
- Sem commit, push ou merge. Nada staged; `.idea` local preservado. Android Studio
  e emulador permanecem disponíveis para a validação do Everton.

Registro anterior: validação manual com Everton e etapa 4 (lembretes). Everton confirmou **escolher data e horário
por atividade**, sem padrão fixo de antecedência. Essa escolha foi comunicada ao
Gemini. Antes de codificar, alinhar persistência, precisão do agendamento, recusa
de permissão e cancelamento/reagendamento; não existe ainda código de notificações.

Diagnóstico adicional: reiniciar apenas o emulador fez os testes antigos de diálogo
voltarem a passar. O teste novo precisou aguardar a seleção de disciplina chegar ao
estado antes de avançar ao calendário/recriação. Com essa sincronização explícita,
o reteste do formulário passou: `OK (2 tests)` em `reteste-selecao.txt`. Nenhuma
alteração de produção foi feita para mascarar a falha. A rodada completa final
está em `instrumentacao-final-atividades.txt`: **OK (13 tests), 65,499 segundos**.
A comparação final do banco confirmou as 2 disciplinas e 0 atividades originais,
sem alterações nos IDs/dados. A suíte final não deixou registros de teste.

## Etapa 4 — atualização de 27/10/2026

Everton aprovou manualmente cadastro, edição, conclusão/reabertura, resumo,
exclusão e rascunho. Também estudou os fluxos de Activity/Fragment/ViewModel/DAO,
IDs, datas, transações, exclusão e migração e autorizou continuar.

Lembretes opcionais implementados com data/hora por atividade. Plano aprovado
pelo Gemini; Codex implementou e Gemini revisou por leitura, sem achado concreto.
WorkManager 2.12.0; sem promessa de precisão de minuto ou acesso a alarmes exatos.
Room v3 com migração 2→3 e cadeia 1→2→3 preservada. Permissão API 33+ solicitada
somente por botão contextual; a recusa não impede salvar. O canal também é
verificado. Conclusão, exclusão e desativação cancelam; troca de horário substitui
o agendamento. Detalhes por arquivo e limites estão em `lembretes.md`.

Antes desta etapa o banco do usuário tinha **2 disciplinas e 1 atividade** (dados
dos testes manuais). Backup consistente: `campusflow-antes-exclusao-20261027-174905.db`,
fora do Git. O comparador confere todos os campos que já existiam antes da migração,
sem considerar as novas colunas como perda de dados.

Build debug, APK de testes, 5 testes unitários e lint passaram (0 erros/21 avisos).
A primeira instrumentação executou 16 testes: 15 passaram e um teste de migração
falhou ao ler a chave JSON opcional `indices` de tabela sem índice. A leitura do
teste foi corrigida; o reteste passou (`OK (2 tests)`). Suíte completa final em
`instrumentacao-lembretes-final.txt`: **OK (16 tests), 32,854 s**. Comparação final
do backup confirmou 2 disciplinas e 1 atividade com todos os dados anteriores
intactos, sem fixtures. Compilação e suíte encerradas.

Próxima etapa: testes manuais de permissão, recebimento/toque de notificação real,
reinício do aparelho, API 26 e economia de bateria conforme `lembretes.md`. Os
testes automatizados usam publicador falso e não concedem permissões ao usuário.
Sem commit, push ou merge; `.idea` preservado fora do stage.

## Relato de horário próximo recusado

Investigação de 27/10: emulador GMT e computador São Paulo UTC−03, diferença de 3h.
O usuário ainda não informou data/hora tentadas; não afirmar causa específica
confirmada. Foi melhorada a referência visível de relógio/fuso, seletor 24h,
sugestão de +5 minutos e distinção de erros. A conversão continua usando o fuso do
aparelho, sem alterar configurações do Android. Detalhes em `lembretes.md`.

Build/lint aprovados, 10 unitários e 17 instrumentados passaram. O novo teste de
UI usa os pickers reais e aceita +2 minutos em GMT; recusa o minuto atual e testa
rascunho após recriação. Backup antes/depois preservou 2 disciplinas e 1 atividade.
Nova versão instalada e login aberto. Ainda aguardar reteste manual do Everton.


## 28/10/2026 — lembretes automáticos

Implementação autorizada por Everton após consenso com Gemini: véspera e primeiro dia de atraso às 9h locais, opção por atividade, cancelamento por conclusão/exclusão e histórico por prazo/etapa. Migração aditiva 3→4 mantém os lembretes personalizados antigos. Detalhes, testes e limitações em [lembretes-automaticos.md](lembretes-automaticos.md).
