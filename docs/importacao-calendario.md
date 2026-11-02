# Importação local de calendário — protótipo de 28/10/2026

## O que está implementado

Em Atividades, o botão “Importar calendário” abre uma tela com o seletor de
arquivos do Android. O usuário escolhe um `.ics`, confere os eventos e associa
uma disciplina existente aos eventos que quer importar (até três por lote).
O cadastro de disciplina tem atalho quando não há nenhuma disponível.

A prévia não grava atividades. Somente “Importar eventos selecionados” grava o
lote. Não importar é a seleção inicial de cada evento. Lembretes ficam desligados
por padrão e têm escolha explícita; permissão de notificações tem botão próprio.
O resultado mostra quantas atividades foram criadas e quantas já existiam.

É uma importação pontual e local: não autentica na UPF, não usa sua senha, não
busca disciplinas no servidor, não atualiza automaticamente dados do Moodle e
não envia trabalhos. A importação local não usa rede. A permissão de Internet foi acrescentada posteriormente para a tela separada de verificação do Moodle.

## Como testar manualmente

1. Abra Atividades → Importar calendário → Escolher arquivo .ics.
2. Escolha `CampusFlow-exemplo.ics`, disponibilizado na pasta Downloads do emulador.
   O original está em `docs/exemplo-calendario.ics`, com três eventos fictícios.
3. Confira título/data/hora. O início do evento será usado como prazo, conforme
   explicado na tela; isso precisa corresponder ao evento que você quer acompanhar.
4. Escolha uma disciplina nos eventos desejados. Os demais permanecem em
   “Não importar este evento”. Ative lembretes somente se desejar.
5. Confirme. Os registros entram como TAREFA/prioridade MEDIA e podem ter título,
   descrição, disciplina, tipo, prioridade e conclusão editados depois.
6. Importe novamente o mesmo arquivo: não deve duplicar ou sobrescrever os
   registros. Excluir uma atividade permite importá-la novamente no futuro.
7. Ao testar com arquivo real da UPF, confirme primeiro que o calendário exportado
   contém os eventos desejados e que horários/disciplina foram interpretados corretamente.
   Não publique arquivos pessoais nem endereços de calendário com tokens no Git.

O arquivo de exemplo contém eventos para novembro de 2026; não representa prazos
acadêmicos reais. Nenhuma atividade fictícia foi deixada no banco pelos testes.

## Prazo com hora e data de dia inteiro

Para eventos com hora, `prazoExatoEpochMillis` preserva o instante. A interface
mostra data, hora, segundos e fuso atual do aparelho. Atraso começa no instante
do prazo; o dashboard/lista atualizam enquanto visíveis em até um minuto, além
de atualizar no retorno à tela. A mudança de fuso altera a apresentação, não o
instante original. A tabela de origem também guarda o identificador de fuso
recebido do arquivo.

Eventos de dia inteiro e atividades manuais continuam usando `prazoEpochDay`:
atraso só começa depois de terminar o dia do prazo. Os comportamentos antigos
não são convertidos para vencimento à meia-noite.

Na primeira versão, a data/hora exata importada fica bloqueada para edição no
formulário, com mensagem explicativa. Alterar outros campos preserva o instante.
Reimportar não atualiza um registro já existente; portanto não é mecanismo de
atualização de prazo remoto nesta etapa.

Se escolhidos, os lembretes ficam previstos para 9h na véspera e as próximas 9h
após o prazo, sem enviar aviso de atraso antes do instante real. Cadastro tardio
usa as próximas 9h depois da ativação. Concluir/excluir cancela os próximos avisos.
WorkManager pode atrasar a entrega; os limites descritos em `lembretes-automaticos.md`
continuam valendo.

## Explicação por arquivo

| Arquivo | Responsabilidade |
| --- | --- |
| `importacao/CalendarioIcs.kt` | Leitura limitada de VEVENT, desdobramento de linhas e escapes; UTC, TZID IANA e dia inteiro. Separa eventos incompatíveis e UIDs repetidos. |
| `importacao/ImportadorIcs.kt` | Transação de importação: valida o lote, impede duplicação, grava atividade/origem e calcula os alvos iniciais dos lembretes. Também formata os prazos exatos. |
| `ImportarCalendarioViewModel.kt` | Lê até 1 MB fora da thread visual; valida UTF-8, mantém prévia/seleção em rotação e controla confirmação. Usa cópia temporária no cache privado, sem pôr o arquivo inteiro no estado salvo. |
| `ImportarCalendarioActivity.kt` e layouts | Seletor de documentos, prévia rolável, associação de disciplinas, resultado e opt-in de notificações. |
| `data/EventoImportado.kt` | Identidade `ics-local + UID`, vinculada à atividade por chave estrangeira com exclusão em cascata. UID global é o identificador definido no formato iCalendar; não usamos título para deduplicar. |
| `data/Atividade.kt` e `AtividadeComDisciplina.kt` | Campo de prazo exato opcional e regra de atraso compatível com registros antigos. |
| `data/AtividadeDao.kt` | Projeta o prazo exato e consulta/registra a identidade importada. |
| `data/CampusFlowDatabase.kt` e schema 5 | Migração 4→5 aditiva: prazo exato nulo nos dados antigos e tabela de origem, sem apagar atividades/disciplinas/histórico. |
| `AtividadeFormViewModel.kt` e Activity/XML | Preservam o instante ao editar outros campos e explicam por que a data importada está bloqueada. |
| `AtividadesViewModel`, Adapter, fragments e `MainActivity` | Atualizam o relógio da lista/resumo e mostram/contam atraso com precisão de instante quando existir hora importada. |
| `lembretes/Automaticos.kt` | Calcula os avisos considerando o instante, mantendo o histórico por prazo/etapa e as regras dos registros manuais. |
| `AtividadesActivity`, layout e manifesto | Acesso à importação e reconciliação dos lembretes ao retornar à lista. |

## Limites explícitos do protótipo

Subconjunto da RFC 5545, não um leitor universal de calendários. Limites de 1 MB,
200 VEVENT e três seleções por lote. Datas/horas sem fuso, fusos desconhecidos,
horários ambíguos/inexistentes de transição de fuso, recorrências e eventos
cancelados são ignorados. Não processa VTODO nem expande séries. Fusos IANA são
interpretados pelas regras disponíveis no Android, sem implementar definições
personalizadas de VTIMEZONE. O horário considerado é DTSTART, não uma tentativa
de deduzir qual entre abertura, encerramento e entrega representa um prazo.

Nenhuma disciplina/professor é inventado a partir do título do evento. Eventos
não suportados aparecem no contador; arquivo inválido apaga a prévia anterior
para impedir confirmação acidental de outro arquivo. URLs/anexos não são abertos.

Ainda é necessário validar uma exportação real da UPF. A leitura pública anterior
confirmou serviços web/mobile habilitados, não uma integração autenticada. API,
feed automático, contas múltiplas, atualização/exclusão remota e envio de trabalho
ficam fora desta etapa.

## Verificação e limites dos testes

Testes unitários verificam UTC atravessando o dia local, TZID, dia inteiro,
desdobramento/escapes, recorrência, fuso ausente/desconhecido, datas inválidas,
transições de horário, UID repetido, cancelamento, atraso exato e aviso posterior
ao vencimento. Instrumentação verifica importação/reimportação, preservação de
edição/conclusão, validação integral do lote, exclusão em cascata, prévia sem
gravação, seleção/rotação, arquivo inválido e confirmação. Migrações de versões
1, 2, 3 e 4 chegam ao schema 5 com dados e histórico preservados.

O teste da prévia usa arquivo sintético local através do mesmo método de leitura
do ViewModel; o seletor de documentos e uma exportação UPF exigem teste manual.
A rotação foi testada; encerramento do processo e recuperação de cache removido
não foram simulados. Entrega visual de notificação real, Doze, API 26 e aparelhos
físicos continuam sem validação manual nesta etapa.

Fontes: https://www.rfc-editor.org/rfc/rfc5545 e
https://docs.moodle.org/en/Calendar_export.

As mudanças permanecem locais, sem commit, push ou merge.

## Resultado da execução

Build debug e APK de instrumentação concluídos. 21 testes unitários aprovados;
Lint sem erros e com 27 avisos (incluindo sugestões de estilo e sobreposição de
fundo). Bateria final: `OK (25 tests)`, 153,311 segundos.
Na verificação inicial houve falhas de localização/foco em testes antigos de
calendário: a inspeção mostrou o diálogo aberto, e os testes passaram depois de
usar explicitamente a raiz do diálogo nos seletores. O código do produto não foi
alterado para contornar essa falha de teste. A prévia teve captura inspecionada.
Gemini revisou a implementação em modo somente leitura sem apontar falhas concretas.

Após a implementação, Everton esclareceu que a prioridade é automatizar também
a criação das disciplinas, com conexão ao Moodle. Este protótipo local fica
preservado como alternativa; ele não resolve a vinculação completa e não deve
ser apresentado como integração autenticada à UPF.

A comparação de backup após a bateria final não pôde ser executada nesta etapa:
o emulador foi desligado após os testes e deixou de aparecer no adb. O backup
anterior contém duas disciplinas e três atividades. Não afirmar preservação
integral conferida após a migração real até reabrir o mesmo emulador e comparar.
Os testes de migração em bancos isolados e os blocos de limpeza da instrumentação
passaram. O emulador não foi reiniciado automaticamente.

Atualização da conferência de dados: a pendência de backup acima foi resolvida
após reabrir o emulador. As três atividades originais mantiveram todos os campos;
foram encontrados também os três eventos do arquivo de exemplo, preservados.
Um novo baseline com duas disciplinas e seis atividades correspondeu integralmente
ao backup posterior ao teste de interface do Moodle. Nenhum dado foi restaurado
ou apagado para produzir essa comparação.
