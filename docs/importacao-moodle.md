# Importação Moodle: seleção, prévia e confirmação

## Comportamento

Everton confirmou pessoalmente o acesso real à UPF e o retorno de 24 disciplinas.
A amostra inicial retornou zero assignments de uma disciplina; isso não representa
as outras disciplinas nem questionários. A etapa seguinte permite selecionar até
20 disciplinas, sem seleção inicial, e consultar os assignments de cada uma.
A prévia mostra títulos e prazos exatos, inclusive a contagem das tarefas sem prazo.
Até confirmar, nenhuma disciplina ou atividade é gravada. Não há escrita acadêmica
no Moodle, sincronização automática em segundo plano ou armazenamento de senha/token.

O aluno pode criar uma disciplina ou associar explicitamente a uma existente.
Vínculos já importados são reaproveitados e a associação fica bloqueada na seleção.
Não se tenta reconhecer disciplinas por nomes iguais. Professor não é inferido.
A confirmação importa todas as tarefas com prazo das disciplinas selecionadas;
a seleção individual de tarefas ainda não faz parte desta versão.
Disciplinas sem tarefas podem ser importadas. Tarefas sem prazo ficam fora do lote,
com aviso, pois o modelo local ainda exige um dia de vencimento. Não usamos hoje
como prazo fictício. Lembretes entram desligados e podem ser ativados no formulário.

## Por arquivo, para a apresentação

| Arquivo | Papel e explicação |
| --- | --- |
| `data/CursoImportado.kt` | Entidade/DAO que liga curso remoto a disciplina local. Chave composta origem+cursoId; origem contém host e ID da conta Moodle. A FK remove apenas o vínculo se uma disciplina sem atividades for excluída. |
| `data/CampusFlowDatabase.kt` e schema `6.json` | Migração 5→6 adiciona tabela e índice; não recria tabelas anteriores. Todas as migrações continuam registradas, sem fallback destrutivo. |
| `moodle/VerificarMoodle.kt` | Modelos e contrato do acesso; tarefas passam a conter o ID remoto. Callback entrega a sessão ao ViewModel, sem colocar credencial no resultado visual. |
| `moodle/MoodleUpf.kt` | HTTPS POST no host fixo; preserva ID e instante das tarefas, converte nomes HTML em texto. Avisos ou ausência do curso solicitado geram falha, em vez de apresentar vazio como sucesso. |
| `MoodleViewModel.kt` | Estado em memória de autenticação, seleção, consulta, prévia e resultado. Limite de 20 cursos e 500 tarefas por lote de consulta. Uma falha invalida a prévia; nenhum lote parcial é confirmado. |
| `MoodleActivity.kt` | Renderiza seleção, associação manual, prévia e botão final; mantiene FLAG_SECURE e campos sem restauração. Credenciais são limpas antes da requisição. |
| `activity_moodle.xml` e `strings.xml` | Tela rolável, botões de tamanho mínimo e textos explicando o envio, a confirmação, tarefas sem prazo e cobertura limitada a assignments. |
| `moodle/ImportadorMoodle.kt` | Uma transação valida e grava o lote. Cria/reutiliza vínculos e ignora atividades já importadas, preservando título, conclusão e edições locais. |
| `ImportacaoMoodleTest.kt` | Banco isolado: reimportação, contas distintas, sem prazo, associação explícita, lote inválido e migração real de schema 5 para 6. |
| `MoodlePreviaTest.kt` | Gateway fictício sem rede: seleção inicialmente vazia, prévia sem gravação e falha na segunda disciplina sem prévia parcial. |
| `AtividadeDatabaseTest.kt` e `LembretesTest.kt` | Caminhos históricos de migração agora incluem 5→6; expectativa de versão atual atualizada. |

## Identidade, repetição e persistência

O ID da disciplina local continua independente do ID do curso no Moodle.
A origem tem formato `https://ead.upf.br/usuario/<id>`; tarefa usa UID
`assign/<id>`. Assim, o mesmo ID numérico em duas contas não representa a mesma
importação. A reimportação não atualiza automaticamente prazos, títulos nem estado
de conclusão: registros existentes são ignorados. Exclusão local remove metadados
por FK; uma importação futura pode recriar um item excluído. Contas diferentes
podem produzir cópias locais intencionais; não há mesclagem entre contas.

A sessão fica somente em propriedades privadas do ViewModel. Rotação conserva
o ViewModel; morte do processo exige novo acesso. Não se usa SavedStateHandle,
Bundle, preferências ou Room para token. A senha existe transitoriamente nas
Strings necessárias à chamada; não há promessa de zerar memória gerenciada.
A sessão é descartada ao limpar, finalizar a importação ou destruir o ViewModel.

## Validação manual necessária

1. Conectar pessoalmente e conferir que nenhuma disciplina está selecionada.
2. Escolher uma disciplina atual. Para uma já cadastrada, escolher associação
   explícita em vez de criar outra com nome parecido.
3. Consultar e conferir a prévia. Voltar sem confirmar: não deve haver novos dados.
4. Confirmar e verificar disciplina, títulos e horário no CampusFlow.
5. Editar/concluir uma atividade, reconectar e repetir a mesma importação:
   deve ignorar a repetida e preservar a edição/conclusão.
6. Conferir curso sem assignments, tarefa sem prazo, fonte com HTML e falha de rede.
7. Testar fonte ampliada, TalkBack e dispositivo API 26. A execução no emulador
   atual não comprova comportamento manual em todos os dispositivos suportados.

A importação autenticada real desta versão ainda depende desses testes do aluno.
Os testes automatizados não usam credenciais reais nem fazem requisições à UPF.
Não testar simultaneamente enquanto a instrumentação usa o emulador.

Sem commit, push ou merge nesta etapa.

## Resultados desta implementação

- Assemble debug e APK de testes aprovados; 27 testes unitários sem falhas.
- Lint: zero erros, 32 avisos. Sugestões não impedem a compilação e não equivalem
  a validação manual de acessibilidade ou de todos os dispositivos.
- Bateria completa: 30 testes executados, 28 aprovados na primeira execução.
  Houve uma falha na montagem do schema pelo teste novo (campo opcional `indices`)
  e um timeout de 5 segundos em um teste antigo de exclusão de disciplina.
  O helper de schema foi corrigido para aceitar ausência de índices. Nenhum código
  do produto foi alterado para contornar esses dois resultados.
- Repetição direcionada: os três testes ImportacaoMoodleTest e o teste de exclusão
  passaram juntos, `OK (4 tests)`, 6,4 segundos. Não se apresenta a primeira
  execução como uma bateria de 30 testes inteiramente verde.
- MoodlePreviaTest e MoodleUiTest passaram na bateria completa. Sem autenticação
  externa nos testes; gateway de prévia é simulado, não valida transporte real.
- Backup antes/depois: duas disciplinas e seis atividades com correspondência
  integral, incluindo campos, IDs e conclusões. Nenhum dado restaurado ou removido.
- APK instalado por atualização, sem limpar o aplicativo. Importação autenticada,
  fonte ampliada, TalkBack e execução manual em API 26 ainda pendentes.
- Gemini revisou em modo somente leitura sem apontar problemas concretos.

Logs de validação permanecem no workspace de trabalho, fora do repositório.

## Correção: avisos de módulos restritos

Após o primeiro teste real da prévia, Everton recebeu uma mensagem genérica de
acesso. A captura não permite afirmar qual aviso a UPF retornou. Foi identificado
um defeito concreto no cliente: qualquer entrada em `warnings` interrompia a consulta.
O código oficial Moodle 4.5 permite warning `item=module`, `warningcode=1` junto
com os assignments disponíveis; significa que um módulo não pôde ser consultado,
não que toda a disciplina está bloqueada.

`MoodleUpf.decodificarTarefas` agora aceita apenas esse aviso conhecido e retorna
uma contagem de módulos restritos. `TarefasMoodle`, `MoodleViewModel` e a prévia
preservam a contagem e mostram um aviso de consulta parcial. Avisos de curso,
avisos desconhecidos e ausência do curso solicitado continuam sendo falha.
Nenhum acesso restrito é contornado, nem sua tarefa é inventada. O usuário ainda
precisa repetir a consulta real para confirmar se essa correção resolve seu caso.

`MoodleRespostaTest` usa JSON local com a implementação real do parser, sem rede:
curso vazio válido; módulo restrito com tarefa disponível e HTML; todos os módulos
restritos com aviso; curso bloqueado/ausente e aviso desconhecido rejeitados.
A migração e a gravação de dados não mudaram nesta correção.

Fonte primária consultada:
https://github.com/moodle/moodle/blob/MOODLE_405_STABLE/mod/assign/externallib.php
(função get_assignments, avisos de contexto de curso e módulo).

Validação da correção: build debug/APK de testes e 27 testes unitários aprovados;
Lint sem erros. Os nove testes direcionados (MoodleRespostaTest, ImportacaoMoodleTest,
MoodlePreviaTest e MoodleUiTest) passaram, `OK (9 tests)`, 4,049 segundos.
Backup antes/depois manteve duas disciplinas e seis atividades integralmente.
APK atualizado sem limpar dados. Nenhum commit/push ou login automatizado.

## Status de envio e conclusão no planejamento

Everton confirmou a importação real e apontou uma tarefa de 14/10 enviada para
avaliação no Moodle, mas exibida como atrasada no CampusFlow. A versão anterior
importava prazo e título, mas inicializava todas as atividades como não concluídas.

Agora o contrato `AcessoMoodle` consulta `mod_assign_get_submission_status` apenas
se essa função constar nas funções liberadas para a sessão. Envia `assignid` por
POST e omite userid, usando o próprio usuário autenticado. Não consulta entregas
de outros alunos, não envia atividades e não altera estado acadêmico no Moodle.

`MoodleUpf.decodificarEnvio` considera somente `lastattempt`. Para tarefas de grupo,
usamos `teamsubmission` quando `teamsubmission=1` no assignment; nas demais,
`submission`. Não usamos tentativas anteriores, data de modificação ou existência
de nota como evidência de envio. Estados:

- `submitted`: enviada; entra concluída no planejamento local, sem afirmar nota,
  aprovação ou conclusão acadêmica de todo o curso.
- `draft`: rascunho, não concluída automaticamente.
- `new`/`reopened`: ainda pendente, sem reabrir conclusões locais existentes.
- Resposta ausente, aviso, função indisponível ou erro: envio não verificado;
  permanece explicitamente identificado na prévia, sem inferir conclusão.

A prévia mostra o estado por tarefa. Novas atividades com envio definitivo são
criadas com `concluida=true`. Para atividades já importadas existe uma opção
explícita inicialmente desmarcada: também atualizar como concluídas as já enviadas.
A opção pode concluir novamente uma atividade reaberta manualmente no CampusFlow,
como explicado no próprio controle. Sem essa opção, itens existentes são preservados.
Nenhum item concluído é reaberto automaticamente. Somente a coluna concluida é
atualizada; títulos, descrição, prazos, prioridades e demais campos não são substituídos.
A confirmação informa quantas conclusões existentes foram atualizadas.

Não há migração de banco nesta mudança. `ImportadorMoodle` mantém a transação e os
IDs externos; `MoodleViewModel` reconcilia lembretes após salvar, para cancelar avisos
das atividades concluídas. Falha da reconciliação não desfaz os dados já confirmados;
a retomada normal também executa a reconciliação.

A consulta de status limita-se a 100 tarefas por lote; tarefas adicionais continuam
com envio não verificado. Após uma falha de status, os restantes também ficam não
verificados para evitar repetidas esperas de rede. O limite não altera a quantidade
de assignments trazidos na prévia. A leitura é pontual: mudanças posteriores no
Moodle exigem nova consulta/confirmação. Não há sincronização contínua.

Testes adicionais: parser JSON de envio individual/grupo, draft, reopened com
histórico submitted, resposta desconhecida e avisos; banco isolado com atualização
opt-in, preservação de edição, conclusão inicial e proibição de reabertura automática.
Gemini revisou em modo somente leitura sem apontar divergências concretas.
A consulta de envio com a conta real UPF ainda depende da validação de Everton.

Para corrigir o exemplo existente: conectar novamente, escolher a disciplina,
consultar a prévia, conferir o estado Enviada, marcar a opção de atualizar conclusões
e confirmar. Não excluir nem recriar a atividade manualmente.

Fonte primária:
https://github.com/moodle/moodle/blob/MOODLE_405_STABLE/mod/assign/externallib.php
(get_submission_status e estrutura de lastattempt).

Validação desta mudança: build debug/APK de testes e testes unitários aprovados;
12 testes instrumentados direcionados passaram (`OK (12 tests)`, 4,891 segundos).
Banco antes/depois: duas disciplinas e nove atividades preservadas integralmente.
APK atualizado sem limpar dados. Nenhum commit, push ou autenticação automatizada.

## Verificação de viabilidade: professor da disciplina

Solicitação de Everton: verificar se o professor pode ser trazido ao criar a
disciplina. Nenhum código foi alterado nesta verificação.

A consulta atual core_enrol_get_users_courses não é usada pelo cliente para buscar
professor. Na API Moodle 4.5 há dois caminhos distintos:

1. core_course_get_courses_by_field (field=id/ids) devolve contacts id/fullname;
   contato configurado no curso não comprova necessariamente papel de professor.
   A estrutura externa contacts pode não publicar os papéis disponíveis internamente.
2. core_enrol_get_enrolled_users devolve fullname e roles com shortname, mas exige
   visualização dos participantes. Pode usar userfields mínimos id,fullname,roles,
   paginação e limites. O filtro withcapability exige role:review; não presumir
   que um aluno terá essa permissão. Evitar coleta de e-mails/perfis/fotos.

Implementação futura: verificar funções anunciadas na sessão, consultar somente
cursos selecionados, reconhecer papéis padrão teacher/editingteacher como docentes,
tratar papéis personalizados como não identificados até confirmação e apresentar
múltiplos candidatos para escolha explícita. Contatos podem ser sugestões rotuladas,
não docentes confirmados. Preencher professor somente em disciplina nova; manter
cadastros existentes. Falha ou ausência de acesso não deve impedir importação.
O campo atual professor é texto, portanto não exige migração só para esse preenchimento.

A permissão real e o retorno da conta UPF não foram verificados nesta etapa.
A sessão é transitória; não foi recuperado token de memória, navegador ou arquivos.
Será necessária uma consulta dentro do app após novo acesso feito pelo próprio aluno.

Fontes oficiais:
https://github.com/moodle/moodle/blob/MOODLE_405_STABLE/course/externallib.php
https://github.com/moodle/moodle/blob/MOODLE_405_STABLE/enrol/externallib.php

## Implementação da consulta de professores

Após autorização de Everton, a prévia passou a consultar professores, apenas se
`core_enrol_get_enrolled_users` for anunciada na sessão. A permissão real da UPF
continua dependente do teste pessoal de login e consulta dentro do app.

Por arquivo:

- `VerificarMoodle.kt`: acrescenta ProfessoresMoodle e estados ENCONTRADOS,
  SEM_IDENTIFICACAO, PARCIAL, NAO_LIBERADO e FALHOU ao contrato; os fakes anteriores
  continuam funcionando com implementação padrão sem rede.
- `MoodleUpf.kt`: pede somente id,fullname,roles, páginas de 50 participantes
  ordenadas por ID, no máximo cinco páginas por disciplina. Não pede fotos, e-mail,
  perfil nem filtro withcapability. Retém somente docentes teacher/editingteacher,
  deduplica IDs e nomes e interpreta nomes HTML como texto. Outros papéis não são
  considerados professores automaticamente. No limite de 250 registros, a prévia
  avisa que as sugestões podem estar incompletas; isso não significa que não há
  professores nas páginas não consultadas. Falhas são opcionais e não impedem
  a consulta de atividades/importação.
- `MoodleViewModel.kt`: coordena a consulta, mantém o texto editado do professor
  somente na memória do ViewModel e captura esse texto ao confirmar. Não emite
  estado a cada caractere, evitando reconstruir o editor durante a digitação.
  Recriação da Activity mantém o ViewModel; morte do processo exige novo acesso.
  Limpar, novo login ou nova consulta descarta os rascunhos anteriores.
- `MoodleActivity.kt`, layout e strings: mostram o resultado da consulta para cada
  disciplina na prévia. Para disciplina nova, campo opcional editável permite
  conferir, corrigir ou apagar os nomes sugeridos. Vários docentes aparecem
  separados por ponto-e-vírgula. Limite de 500 caracteres; se a sugestão conjunta
  ultrapassar esse limite, o campo inicia vazio para preenchimento manual.
  Campo sem restauração automática/auto-preenchimento; rascunho fica no ViewModel.
- `ImportadorMoodle.kt`: grava o professor apenas no INSERT de nova disciplina.
  Associação explícita e vínculo remoto já existente preservam o professor local,
  inclusive quando ele está vazio. Não atualiza disciplinas anteriores e não
  exige migração: a coluna professor já existia.
- `MoodleRespostaTest`: JSON local cobre docentes padrão, aluno, gestor, papel
  personalizado, papéis ausentes, nome HTML e ID repetido.
- `ImportacaoMoodleTest`: banco isolado comprova preenchimento de disciplina nova,
  preservação na reimportação e preservação de professor de disciplina associada.
- `MoodlePreviaTest`: gateway fictício cobre sugestão, edição em memória e consulta
  de professor bloqueada sem impedir a prévia. Não autentica na UPF.

Para testar: novo acesso no app, selecionar uma disciplina ainda não importada,
Criar nova disciplina e Consultar. Ao final da prévia aparece a seção Professor(a).
Conferir nome, editar se necessário e confirmar. Em disciplinas já vinculadas,
o campo atual será preservado; não excluir disciplinas ou atividades para testar.
Se a UPF negar a consulta, a mensagem explica isso e permite digitação manual.

Sem commit, push, merge ou login automatizado. A leitura real do professor,
TalkBack, fonte ampliada e interação do novo campo após rotação ainda devem ser
validados manualmente. A existência da função anunciada não comprova permissão
para visualizar todos os participantes ou docentes em cada disciplina.

Validação da consulta de professores: build debug/APK de testes aprovados; 14 testes
instrumentados direcionados passaram (`OK (14 tests)`, 5,097 segundos). Gemini
revisou em modo somente leitura sem apontar problemas concretos. Backup antes/depois
preservou integralmente as três disciplinas e quatro atividades presentes no início
desta etapa. APK instalado por atualização, sem limpar dados. Consulta autenticada
real de professores continua pendente do teste pessoal do aluno.
