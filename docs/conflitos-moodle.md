# Controle de conflitos do Moodle

A sincronização compara cada campo com a última versão remota reconhecida. O objetivo é atualizar informações sem apagar escolhas feitas no CampusFlow.

## Regras para explicar na apresentação

- Título, prazo e conclusão são comparados separadamente.
- Se apenas o Moodle mudou, título e prazo podem ser atualizados automaticamente. Uma entrega confirmada pode concluir a atividade.
- Se apenas o usuário mudou, sua edição permanece.
- Se ambos mudaram o mesmo campo, a versão local permanece até revisão.
- Uma atividade concluída nunca é reaberta automaticamente.
- Na revisão, “Manter minha versão” reconhece a proposta remota sem adotá-la. Assim, a mesma diferença não aparece repetidamente.
- “Usar Moodle” aplica somente o campo escolhido. A operação confere novamente os dados dentro de uma transação: uma proposta antiga não pode substituir uma edição mais recente.
- Ausência de informação de envio não significa atividade pendente. O estado conhecido é preservado.
- Se o Moodle remover o prazo, o prazo local obrigatório permanece; a revisão permite manter a versão local.
- Importações anteriores não têm um histórico confiável. Diferenças na primeira atualização exigem revisão; nenhum histórico é inventado.

Exemplo: o usuário muda o título para “Estudar capítulo 3” e o Moodle adia o prazo. Como são campos distintos, o título local permanece e o prazo pode ser atualizado. Se ambos alterarem o título, aparece uma pendência para o usuário decidir.

## Arquivos e responsabilidades

| Arquivo | Responsabilidade |
| --- | --- |
| data/ControleMoodle.kt | Guarda a referência anterior, a última proposta remota e as pendências por campo. |
| data/CampusFlowDatabase.kt | Migração 6→7: adiciona a tabela de controle, vinculada à atividade, sem alterar os registros existentes. |
| moodle/ConciliacaoMoodle.kt | Executa a comparação de três versões, sem interface nem chamadas de rede. |
| moodle/ImportadorMoodle.kt | Grava atividades e referências na mesma transação. |
| moodle/ConexaoMoodle.kt | Integra a conciliação à sincronização autorizada e reconcilia lembretes após mudanças. |
| moodle/ResolverConflitoMoodle.kt | Aplica a decisão por campo e rejeita versões desatualizadas. |
| ConflitosMoodleViewModel.kt | Observa pendências, processa decisões e atualiza lembretes. |
| ConflitosMoodleActivity.kt e activity_conflitos_moodle.xml | Mostram os valores local/remoto e as opções de revisão em uma tela com rolagem. |
| MoodleActivity.kt e activity_moodle.xml | Oferecem o acesso à revisão e sua contagem de atividades. |
| AtividadeFormViewModel.kt | Preserva o prazo exato atualizado enquanto um formulário antigo estava aberto. |
| ConciliacaoMoodleTest.kt e ConflitosMoodleTest.kt | Verificam as regras, gravação, rejeição de decisões antigas, migração e exclusão em cascata do controle. |

## Limitações e validação manual

Os testes dirigidos usam respostas simuladas e bancos isolados, sem modificar o Moodle. A revisão pode ser usada sem rede com a última proposta armazenada. Ainda é necessário validar visualmente conflitos reais, fonte ampliada, TalkBack, rolagem e comportamento em API 26; o emulador disponível é mais recente. O formulário de revisão não envia alterações ao Moodle. Lembretes precisam de permissão do usuário para serem exibidos.

Para testar: editar localmente um título importado; atualizar a conexão; abrir a revisão se houver uma proposta remota diferente; comparar os valores; escolher manter ou usar Moodle. Repetir a atualização deve respeitar a decisão, até que o Moodle mude novamente. Não é necessário alterar uma tarefa acadêmica real apenas para provocar um conflito; os cenários destrutivos são cobertos por dados de teste isolados.

## Validação executada em 30/10/2026

- assembleDebug, assembleDebugAndroidTest e lintDebug: concluídos com sucesso.
- 33 testes unitários aprovados, incluindo seis cenários de conciliação.
- Bateria completa no emulador: 51 testes aprovados em 96,931 segundos.
- Teste adicional PrazoSincronizadoFormTest aprovado: rascunho aberto preserva prazo exato e dia atualizado, conclusão e descrição.
- Migração 6→7 testada com banco legado e verificação de chaves estrangeiras.
- Backup e comparação após a bateria completa: quatro disciplinas e onze atividades preexistentes preservadas integralmente.
- Gemini revisou o código no Android Studio, destacando a transação contra decisões antigas, reconciliação de lembretes após gravação e proteção do formulário aberto. Não editou arquivos nesta revisão.
- Mudanças permanecem locais na branch feature/activities, sem commit, push ou merge. Arquivos locais .idea preservados.
