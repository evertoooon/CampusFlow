# Atividades acadêmicas — base de dados e integridade

Etapa realizada em 26/10/2026, na branch `feature/activities`. O plano foi alinhado com o Gemini no Android Studio, que também revisou a implementação por leitura e não apontou falhas concretas. Essa revisão complementa os testes.

## O que mudou

| Arquivo | Responsabilidade |
| --- | --- |
| `data/Atividade.kt` | Entity da tabela atividades: ID, título, disciplinaId, tipo, prazoEpochDay, prioridade, concluída e descrição. Inclui os enums de tipo e prioridade. Recusa título vazio/espaços, ID de disciplina não positivo e data fora do intervalo de LocalDate. |
| `data/AtividadeConverters.kt` | Converte enums em nomes estáveis armazenados como texto. Os nomes persistidos não devem ser renomeados sem planejar compatibilidade/migração; os rótulos apresentados ao usuário podem ser traduzidos separadamente. |
| `data/AtividadeDao.kt` | Define inserção, atualização, consulta por ID, consulta observável e exclusão. Os formulários de atividades ainda não existem nesta etapa. |
| `data/CampusFlowDatabase.kt` | Inclui a nova Entity, registra os converters e passa à versão 2. MIGRATION_1_2 cria somente a tabela atividades e seu índice. A tabela disciplinas permanece intacta. A migração é registrada no builder; não há fallback destrutivo. |
| `data/DisciplinaDao.kt` | Acrescenta contagem de atividades e exclusão condicional dentro de uma transação, com resultado EXCLUIDA, COM_ATIVIDADES ou INEXISTENTE. |
| `DisciplinaFormViewModel.kt` | Trata COM_ATIVIDADES como bloqueio e preserva os campos. Mantém falhas inesperadas como erro genérico; não trata toda exceção como se fosse um vínculo existente. |
| `DisciplinaFormActivity.kt` e `values/strings.xml` | Exibem a mensagem de que é necessário remover ou transferir atividades antes de excluir a disciplina. |
| `app/schemas/.../2.json` | Esquema gerado pelo Room para a versão 2. O arquivo da versão 1 foi preservado sem alterações. |
| `app/build.gradle.kts` | Disponibiliza os esquemas versionados como assets dos testes de migração. Nenhuma biblioteca nova foi necessária. |

## Vínculo entre atividade e disciplina

`atividades.disciplinaId` referencia `disciplinas.id` por uma chave estrangeira com `ON DELETE RESTRICT`. O índice em disciplinaId facilita a localização dos vínculos. O banco recusa cadastrar ou atualizar uma atividade para uma disciplina inexistente e impede remover uma disciplina que ainda tenha atividades.

O fluxo de exclusão da interface usa uma transação: consulta vínculos e executa a exclusão conjuntamente. Assim, a mensagem é amigável e a chave estrangeira continua sendo uma proteção para acessos diretos ao DAO. Uma disciplina sem atividades continua podendo ser excluída.

Quando criarmos o formulário de atividades, ele deverá exigir uma seleção válida e tratar uma disciplina que deixe de existir antes do salvamento. Isso ainda não foi apresentado em uma nova tela.

## Prazo sem horário

O prazo é um dia de calendário, armazenado como `LocalDate.toEpochDay()`. Não representa milissegundos nem um instante em UTC. A regra de atraso é: não concluída e prazo menor que o dia de referência. A data de hoje não está atrasada; uma atividade concluída não é classificada como atrasada.

O método `estaAtrasada(hoje)` recebe a data como argumento para que os testes não dependam do relógio. O futuro dashboard deverá fornecer a data local atual e recalcular quando necessário; um Flow do banco sozinho não representa a passagem do tempo.

## Validação realizada

- Compilação debug e lint aprovados: 0 erros e 12 avisos preexistentes de recursos sem uso e desenho de fundos.
- 5 testes unitários aprovados: 4 de datas/validações de atividades e o teste de soma preexistente.
- O comando Gradle de testes conectados retornou sucesso, mas seu XML indicou **0 testes**. Esse resultado não foi considerado validação funcional.
- A suíte foi então executada diretamente no emulador com `am instrument -w -r com.everton.campusflow.test/androidx.test.runner.AndroidJUnitRunner`: resultado **OK (8 tests)**. Inclui os testes preexistentes e os novos testes de migração, integridade e mensagem de bloqueio. O relatório vazio do Gradle permanece como limitação da integração de relatórios nesta execução.
- O teste de migração recria um arquivo isolado versão 1 a partir do JSON versionado, insere disciplinas com IDs definidos e nomes repetidos e abre esse arquivo com o Room versão 2. O Room valida o esquema e o teste confere dados, IDs, índice e ausência de violações de FK.
- Foram testados inserção e atualização com disciplina inexistente, exclusão direta bloqueada por FK, exclusão condicional bloqueada, exclusão de disciplina vazia permitida e liberação após remover suas atividades.
- O teste de interface confirma que a mensagem de bloqueio aparece sem apagar disciplina ou atividade. Seus registros temporários são removidos ao terminar.
- Backup consistente do banco do emulador realizado antes da instalação/testes, incluindo WAL quando presente. Comparação após a execução direta confirmou os mesmos dois registros, com IDs, nomes e professores inalterados. O pacote permaneceu instalado. Backups estão nos entregáveis locais desta conversa, fora do Git.

Os testes foram executados no emulador disponível, API 37. Não houve validação em API 26 real nem revisão manual de novas telas, pois elas ainda não foram criadas. Não houve commit, push ou merge desta etapa.

## Para explicar na apresentação

“A chave estrangeira mantém a relação entre atividade e disciplina. RESTRICT impede excluir uma disciplina que ainda tenha atividades. A migração acrescenta a nova estrutura sem apagar as disciplinas existentes. Testamos essa atualização usando o esquema anterior e verificamos se os IDs e os dados foram preservados.”

## Próxima etapa

Construir a lista e o formulário de atividades, com seleção de disciplina, tipo, prioridade e data. Depois conectar o resumo do dashboard e os Fragments aos dados reais. Esta entrega conclui a base e suas proteções, não a funcionalidade completa de atividades.

## Referências

- [Room: ForeignKey e RESTRICT](https://developer.android.com/reference/androidx/room/ForeignKey).
- [Room: migrações e preservação de dados](https://developer.android.com/training/data-storage/room/migrating-db-versions).
