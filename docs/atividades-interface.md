# Atividades — guia para estudar e apresentar

Implementação em `feature/activities`, 26–27/10/2026. O Gemini implementou a primeira
versão da lista; o Codex revisou e ajustou seu funcionamento, implementou formulário
e resumo e executou as verificações. O Gemini revisou o formulário e o resumo por
leitura. Essas revisões complementam os testes; não equivalem a uma prova de
ausência de defeitos.

## Caminho dos dados

1. O usuário abre **Ver atividades** no resumo.
2. O Room consulta atividades junto de suas disciplinas por `INNER JOIN`.
3. O `AtividadesViewModel` separa a lista em pendentes e concluídas e publica estado.
4. Cada Fragment observa esse estado durante o ciclo de vida de sua View.
5. O adapter mostra os cartões; o ID identifica cada registro.
6. Concluir/reabrir grava somente `concluida`, sem regravar campos do formulário.
7. O Room emite a lista atualizada e a atividade passa para a aba correspondente.

## Arquivos da lista

| Arquivo | O que explicar |
| --- | --- |
| `data/AtividadeComDisciplina.kt` | Projeção de leitura que junta campos da atividade com nome/professor da disciplina. Não é outra tabela. |
| `data/AtividadeDao.kt` | Consulta com JOIN, ordenada por prazo e ID; `atualizarConcluida` altera somente o status do ID indicado e retorna quantas linhas alterou. |
| `AtividadesViewModel.kt` | Mantém carregamento, erro, listas, data de referência e IDs em gravação. Uma nova tentativa cancela a consulta anterior. Preserva cancelamentos de corrotinas. |
| `AtividadesActivity.kt` | Hospeda TabLayout e ViewPager2, cria os dois Fragments, abre cadastro e retorna ao resumo com `finish()`. Mostra o erro de atualização em um só lugar. |
| `PendentesFragment.kt` | Observa e exibe as não concluídas; abre edição por ID. |
| `ConcluidasFragment.kt` | Mesma apresentação para concluídas, permitindo reabertura. |
| `AtividadesAdapter.kt` | Usa ListAdapter/DiffUtil, mantém identidade por ID, formata rótulos/data e separa o toque de edição do checkbox. O checkbox anuncia concluir/reabrir e o título. |
| `activity_atividades.xml` | Cabeçalho, botões, abas e espaço dos Fragments. |
| `fragment_atividades_lista.xml` | RecyclerView e estados carregando/vazio/erro; nova tentativa fica separada da mensagem. |
| `item_atividade.xml` | Cartão com status textual, prazo, tipo e prioridade. Prazo e atraso ficam em linhas separadas. |

## Arquivos do formulário

| Arquivo | O que explicar |
| --- | --- |
| `AtividadeFormViewModel.kt` | Mantém rascunho no SavedStateHandle, carrega registro por ID, observa disciplinas e controla gravação/exclusão. |
| `AtividadeFormActivity.kt` | Liga os controles ao estado; abre calendário, valida título, apresenta confirmação e navega ao cadastro de disciplina quando necessário. |
| `activity_atividade_form.xml` | Formulário com rolagem, campos Material e mensagens acessíveis; respeita barras do sistema, recortes e teclado. |
| `AndroidManifest.xml` | Registra as duas novas Activities sem exportá-las a outros aplicativos. |
| `values/strings.xml` | Centraliza textos, erros e rótulos da interface. |

O título, a disciplina e o prazo são obrigatórios. Descrição é opcional. Tipo começa
como Tarefa e prioridade como Média; ambos podem ser alterados. Uma atividade nova
começa pendente. Datas passadas são aceitas, pois o aluno pode registrar algo já
atrasado. `trim()` remove espaços nas extremidades ao gravar, mantendo os espaços
entre palavras.

O calendário fornece ano, mês e dia. Usamos `LocalDate` e `toEpochDay()`; não é um
instante UTC nem um horário. O mês recebido pelo DatePicker começa em zero, por
isso há `mes + 1` na conversão.

Os rótulos do seletor exibem nome e professor. Se dois rótulos forem iguais,
acrescentam o ID. O rótulo ajuda o usuário; quem cria o vínculo no banco é o ID.
Antes de salvar, uma transação confere se a disciplina existe. Em edição, lê
também o status atual e o preserva, evitando desfazer uma conclusão feita depois
da abertura do formulário. A chave estrangeira continua protegendo o vínculo.

SavedStateHandle guarda título, descrição, disciplina, tipo, prioridade e prazo.
Recriar a tela não transforma rascunho em gravação. A confirmação de exclusão
identifica o título salvo, e não uma alteração ainda não persistida.

## Resumo

`MainActivity.kt` observa uma instância própria do mesmo ViewModel da lista.
`activity_main.xml` recebe contagens reais e mensagens de carregamento/erro/vazio.
Uma alteração no banco atualiza o resumo observado. Números desconhecidos não são
apresentados como zero nem como valores antigos confirmados.

- Pendentes: todas as atividades não concluídas.
- Concluídas: todas as concluídas.
- Atrasadas: apenas as pendentes cujo prazo é anterior a hoje.

Exemplo: 5 pendentes, das quais 2 atrasadas, continuam sendo 5 pendentes. Não são
7 atividades. Prazo hoje não está atrasado; concluída também não fica atrasada.

Lista e resumo recalculam a data ao retomar e na meia-noite local enquanto estão
visíveis. Mudanças manuais de hora/fuso são percebidas em até um minuto. Esse
monitor não roda com a tela pausada e não implementa lembretes.

## Testes

| Arquivo | Evidência esperada |
| --- | --- |
| `AtividadeListaDaoTest.kt` | JOIN preserva vínculos de nomes iguais, ordena prazo/ID, reflete renomeação de disciplina e conclusão parcial preserva demais campos. Usa banco isolado. |
| `AtividadesListaUiTest.kt` | Concluir move de aba, recriação mantém aba, reabrir devolve à lista e indicação de atraso desaparece na concluída. |
| `AtividadeFormUiTest.kt` | Validação de título, seleção real de disciplina/calendário, rascunho na recriação, criação sem descrição, edição preservando conclusão e cancelamento/confirmação de exclusão. Também recusa ID inexistente e disciplina removida antes de salvar. |
| `DashboardAtividadesTest.kt` | Contagens observam banco, atraso é subconjunto das pendentes, conclusão atualiza números e recriação mantém resultado. |

Os testes de interface usam registros próprios, removidos pelos seus IDs ao
terminar. Não limpam o banco do usuário. Ainda assim, fazemos backup antes de
instalar ou testar. A documentação da base/migração está em `atividades-base.md`.

## Validação manual pendente com Everton

Compilação debug/APK de testes, 5 testes unitários e lint aprovados (0 erros,
19 avisos). A rodada instrumentada final em API 37 passou: **OK (13 tests)**,
registrada em `instrumentacao-final-atividades.txt` nos entregáveis da conversa.
Houve falhas anteriores de foco do emulador e uma sincronização insuficiente do
teste ao selecionar disciplina. O emulador foi reiniciado preservando dados, e o
teste passou a aguardar o ID selecionado antes de recriar a tela. A suíte inteira
foi repetida depois desses ajustes, sem alterar a produção para contornar falhas.
Comparações dos backups confirmaram as 2 disciplinas originais e 0 atividades.

- Cadastrar uma tarefa, uma prova e um trabalho; verificar datas e prioridades.
- Editar título/descrição/disciplina, voltar sem salvar e conferir o comportamento.
- Concluir/reabrir pela lista e verificar contagens no resumo.
- Usar fonte ampliada e paisagem; conferir espaço da lista e cabeçalho, teclado e
  alcance de botões. O cabeçalho da lista é fixo: especialmente em paisagem com
  fonte muito grande, ainda precisa de avaliação manual e possível ajuste.
- Testar leitor de tela, navegação por foco, API 26 e aparelho físico.
- Validar passagem real da meia-noite e mudança de fuso; os testes de regra de
  datas não substituem esses cenários de integração.

## Próxima etapa

Etapa de lembretes implementada após o estudo e aprovação manual do Everton.
Data e horário são escolhidos por atividade; permissão é contextual e a recusa
permite continuar usando o aplicativo. Funcionamento, arquivos, testes e roteiro
manual em `lembretes.md`. Não há commit/push/merge desta entrega parcial.
