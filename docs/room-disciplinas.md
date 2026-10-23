# Disciplinas: primeiro fluxo com Room

Estado desta entrega: dashboard inicial e CRUD completo de disciplinas. O usuário realizou e aprovou os testes manuais de cadastro, listagem, persistência, atualização e exclusão. O resumo de atividades do dashboard ainda usa contagens fixas; atividades acadêmicas e Fragments permanecem para etapas seguintes.

## Caminho dos dados

Formulário → validação → ViewModel → DAO → Room/SQLite → Flow → lista de disciplinas.

- `data/Disciplina.kt`: Entity; cada objeto é uma linha da tabela `disciplinas`. O ID é gerado pelo banco. Professor vazio é permitido. A obrigatoriedade de um nome não vazio é validada antes de inserir.
- `data/DisciplinaDao.kt`: contrato de acesso ao banco. `@Insert` insere um registro; `@Query` lista por nome e ID ou busca um registro específico; `@Update` atualiza pelo ID e retorna o número de linhas alteradas. `excluirPorId` executa DELETE com filtro pelo ID e retorna o número de linhas removidas. O Room gera o código concreto durante a compilação com KSP.
- `data/CampusFlowDatabase.kt`: configura o arquivo `campusflow.db`, versão 1. A instância é compartilhada no processo e usa o contexto da aplicação. Não há configuração que apague o banco automaticamente ao mudar a versão; alterações futuras de esquema exigem migração.
- `DisciplinaFormViewModel.kt`: mantém o estado de salvamento durante recriações da Activity, como rotação. O SavedStateHandle guarda o ID recebido e o rascunho dos campos. Usa uma coroutine para chamar o DAO suspenso; rejeita novos pedidos enquanto está salvando ou após sucesso. Captura falhas para permitir nova tentativa. Não é uma garantia contra duplicação após encerramento abrupto do processo durante uma gravação.
- `DisciplinaFormActivity.kt`: lê nome e professor, valida o nome, solicita a gravação e observa seu resultado. Só mostra sucesso e encerra depois do retorno da inserção. Em caso de erro, mantém os campos.
- `DisciplinasActivity.kt`: observa um `Flow<List<Disciplina>>` enquanto a Activity está pelo menos STARTED. Ao voltar do formulário, recebe a lista persistida. Mostra carregamento, lista vazia ou falha conforme o resultado.
- `item_disciplina.xml`: aparência de cada registro. Professor vazio não ocupa uma linha. A lista inicial usa LinearLayout dentro do ScrollView por simplicidade; para listas grandes, migrar para RecyclerView antes de ampliar o volume.
- `app/schemas/`: esquema JSON gerado pelo Room; deve ser versionado para apoiar futuras migrações.
- Gradle: adiciona Room e KSP para gerar o acesso ao banco, além das dependências Lifecycle para observar dados e manter o salvamento no ViewModel.

## Conceitos para explicar

`suspend` permite executar a inserção de forma assíncrona com o suporte do Room, sem bloquear a interface. Não significa que toda função suspend execute automaticamente em outra thread.

`Flow` permite receber resultados atualizados de uma consulta observável do Room. Nesta etapa ele observa disciplinas; ainda não alimenta o resumo de atividades do dashboard.

`repeatOnLifecycle` inicia a observação quando a tela fica visível e cancela quando ela sai desse estado. `viewModelScope` permite que o salvamento sobreviva à recriação da Activity; não sobrevive ao encerramento do processo.

Ao pressionar Salvar, o nome obrigatório é verificado primeiro. Depois o DAO insere uma disciplina. O ID distingue registros de mesmo nome: ainda não existe regra proibindo nomes repetidos.

## Verificações

O teste `DisciplinaPersistenceTest` usa um arquivo de banco exclusivo de teste, insere dois nomes iguais, compara os IDs e fecha/reabre o banco para verificar os dados. Ao terminar, remove somente esse banco de teste. Não usa nem limpa `campusflow.db`.

Teste manual sugerido: validar campo vazio; salvar somente nome; salvar com professor; observar a lista; sair e reabrir o app e confirmar os registros; girar a tela; tentar dois toques rápidos em Salvar. A persistência permanece após fechar o aplicativo, mas não é prometida após desinstalação ou limpeza dos dados.

## Fontes

- [Room: dependências e configuração](https://developer.android.com/jetpack/androidx/releases/room).
- [KSP: configuração do processador](https://kotlinlang.org/docs/ksp-quickstart.html).
- [Lifecycle: componentes e dependências](https://developer.android.com/jetpack/androidx/releases/lifecycle).

## Etapa seguinte: edição de disciplinas

O fluxo foi alinhado com o Gemini no Android Studio antes da implementação. Após implementar, foi solicitada revisão somente por leitura; ele não apontou problemas concretos de preservação do rascunho, construção do ViewModel ou separação entre criar e editar. Isso complementa os testes, sem substituí-los.

1. O cartão na lista envia somente `EXTRA_DISCIPLINA_ID` no Intent.
2. A mesma DisciplinaFormActivity muda seu título para “Editar disciplina” e seu botão para “Atualizar disciplina”.
3. O ViewModel consulta o registro por ID. Se já existir rascunho restaurado, ele prevalece sobre os dados iniciais do banco.
4. Os campos usam `doAfterTextChanged` para atualizar o rascunho. Uma guarda impede que o preenchimento programático seja interpretado como digitação. A restauração automática dos EditTexts foi desativada para não competir com o SavedStateHandle.
5. A validação de nome continua obrigatória. A edição chama `@Update`, preservando o ID. Sucesso exige exatamente uma linha atualizada; zero linhas não gera mensagem de sucesso nem insere um registro novo.
6. Voltar encerra o formulário sem chamar o DAO. A lista recebe as alterações pela consulta observável ao retomar a tela.

Não houve alteração de colunas ou versão do banco: apenas novos métodos no DAO e na interface. Nenhuma migração destrutiva foi adicionada.

### Resultado das verificações da edição

- `assembleDebug` e `lintDebug` passaram: 0 erros e 12 avisos (recursos sem uso e desenho de fundo sobre fundo do tema).
- `connectedDebugAndroidTest` passou no emulador API 37: 4 testes, nenhuma falha, erro ou teste ignorado. Um dos quatro é o teste de pacote preexistente; os outros três verificam persistência e edição.
- O teste de banco usa arquivo isolado, verifica atualização pelo mesmo ID, retorno zero para ID ausente, ausência de duplicatas e dados após reabertura.
- Os dois testes de interface verificam carregamento de campos, recriação da Activity com rascunho, salvamento sem aumentar a quantidade de registros, cancelamento e bloqueio para ID inexistente. Usam registros temporários com UUID e removem somente seus próprios registros ao terminar.
- A recriação automatizada exercita o ciclo de vida de uma mudança de configuração; não substitui a observação visual de uma rotação física, teclado, TalkBack ou execução em API 26.
- A primeira execução dos testes encontrou uma assinatura de método de teste incompatível com JUnit; corrigida para retorno Unit explícito. A execução final passou.

Para a apresentação: “No cadastro, o banco gera um novo ID. Na edição, usamos o ID existente para atualizar o mesmo registro. O ViewModel guarda o rascunho durante a recriação da tela e o Flow atualiza a lista consultada.”

Roteiro manual: tocar em uma disciplina; alterar nome/professor e atualizar; conferir que a quantidade de registros não aumentou; reabrir o app e verificar a alteração; voltar sem salvar e confirmar os dados anteriores; girar a tela enquanto edita; tentar salvar nome vazio.

### Incidente do executor de testes e recuperação

Apesar de os testes isolarem seus próprios registros, o executor do Gradle desinstalou o aplicativo ao terminar, removendo também os dados do emulador. Não havia backup anterior a essa execução. O incidente foi informado ao usuário.

O app foi reinstalado e foram reconstruídos os dois registros visíveis na captura enviada pelo usuário: “Desenvolvimento Mobile II”, professor “Jaqson Dalbosco”; e “Gestão de Segurança da Informação”, sem professor. Foram gerados IDs 1 e 2; não é possível comprovar os IDs antigos nem recuperar eventuais informações ausentes da captura. As cópias do banco recuperado e dos backups anteriores/posteriores à verificação foram salvas fora do repositório, junto aos entregáveis desta conversa.

Foi acrescentado `android.injected.androidTest.leaveApksInstalledAfterRun=true` em gradle.properties. Após fazer backup externo, repetiu-se somente o teste de banco isolado: aprovado; o pacote e os dois registros reconstruídos permaneceram instalados e conferidos após a execução. Essa configuração não substitui backup. Antes de novos testes instrumentados neste emulador, preservar uma cópia consistente do banco, incluindo WAL quando existir; preferir emulador exclusivo para testes automatizados.

Referência do comportamento e opção: [Google Issue Tracker, AGP e desinstalação após testes](https://issuetracker.google.com/issues/295039976).

## Exclusão: última operação do CRUD de disciplinas

O fluxo foi consultado com o Gemini no Android Studio, que aprovou a proposta e reforçou o uso do nome original e de uma consulta DELETE filtrada por ID. O botão aparece somente ao editar uma disciplina já cadastrada.

Ao tocar em Excluir disciplina, um diálogo Material mostra o nome salvo no banco. O nome não vem do rascunho ainda não gravado: assim o usuário sabe qual registro será removido. Cancelar fecha apenas a confirmação. Confirmar solicita a exclusão ao ViewModel; ele bloqueia operações concorrentes, chama o DAO e só apresenta sucesso quando uma linha é removida. Se o registro já não existir, apresenta o erro correspondente. Em falha de banco, permite nova tentativa. A lista observa os dados novamente quando volta ao estado STARTED.

Não há entidades de atividades nem relacionamentos nesta versão. Antes de associar atividades às disciplinas, será necessário definir o tratamento da exclusão de uma disciplina que possua atividades, com integridade referencial e regra explícita. Não foi adicionada remoção em cascata nesta etapa.

Verificação desta etapa: `assembleDebug lintDebug connectedDebugAndroidTest` aprovados; 5 testes no emulador API 37, 0 falhas/erros/ignorados. O lint continua com 0 erros e 12 avisos. Foram testados cancelamento com preservação do rascunho, confirmação identificando o nome salvo, exclusão somente do ID selecionado, preservação de outro registro e permanência da exclusão após reabrir o banco. Os testes de edição anteriores também passaram.

Antes dos testes, foi criado um backup consistente do banco, considerando WAL. Depois, outra cópia foi comparada: todos os IDs, nomes e professores dos dois cadastros preexistentes coincidiram, e o aplicativo permaneceu instalado. Somente registros temporários exclusivos dos testes foram removidos.

Para testar manualmente, crie uma disciplina temporária: abra sua edição, escolha Excluir, confira o nome e cancele; verifique que ela continua na lista. Repita e confirme a exclusão; verifique que só ela desapareceu. Reabra o aplicativo e confira novamente. Esse passo exige validação do usuário, além dos testes automatizados.

Para a apresentação: “Excluir é uma operação do CRUD que remove o registro identificado pelo ID. A confirmação evita uma remoção acidental, e o retorno do DAO permite verificar se a exclusão realmente ocorreu.”
