# CampusFlow — roteiro para entender e explicar o código

Material de estudo baseado nos arquivos atuais do projeto. Os caminhos Kotlin abaixo começam em `app/src/main/java/com/everton/campusflow/`; os XML começam em `app/src/main/res/`. Abra o arquivo indicado no Android Studio enquanto lê. Os trechos de código destacados são recortes; as sequências com setas são explicações do fluxo.

Este guia é mais detalhado que uma apresentação de dez minutos. Estude os blocos em ordem e use as frases **Como explicar** para preparar sua fala. Não precisa decorar todos os nomes de métodos.

## 1. Antes dos arquivos: o caminho de uma ação

Ao cadastrar uma atividade:

1. O XML define os campos e o botão.
2. A Activity recebe a digitação e o clique.
3. O ViewModel mantém o rascunho, valida e solicita a gravação.
4. O DAO executa a operação no banco Room/SQLite.
5. O banco emite a lista atualizada por um `Flow`.
6. O ViewModel publica um novo estado; a tela mostra esse estado.
7. Depois da gravação, o app ajusta os lembretes.

Essa separação aparece principalmente nos formulários e nas atividades. O projeto não tem um ViewModel exclusivo para toda tela: `LoginActivity` faz sua validação diretamente e `DisciplinasActivity` observa o DAO diretamente.

**Como explicar:** “Dividimos a interface, o controle do estado e a persistência para que cada parte tenha uma responsabilidade compreensível. A informação confirmada vem do banco.”

## 2. Entrada e resumo acadêmico

### `LoginActivity.kt`

**Responsabilidade:** preparar o login demonstrativo e decidir se abre o resumo.

Leia `onCreate()` nesta ordem:

1. `super.onCreate(...)` executa a inicialização da classe Android da qual a tela herda.
2. `setContentView(R.layout.activity_login)` carrega o XML da tela.
3. `enableEdgeToEdge()` permite usar a área próxima às barras do sistema. O listener de insets aplica espaço para barras, recorte da câmera e teclado (IME), evitando que eles cubram o conteúdo.
4. `findViewById` encontra e-mail, senha e botão pelos IDs do XML.
5. `setOnClickListener` registra o que deve acontecer quando o botão for pressionado.

Trecho central:

```kotlin
val email = edtEmail.text.toString().trim()
val senha = edtSenha.text.toString()
```

`text` obtém o conteúdo; `toString()` converte em texto Kotlin. `trim()` remove espaços nas extremidades do e-mail, preservando espaços internos. A senha é lida sem essa transformação.

O `when` testa primeiro e-mail vazio, depois senha vazia, depois as credenciais demonstrativas e, por último, o caso inválido. Com ambos vazios, o primeiro erro apresentado é o do e-mail.

`edtEmail.error = ...` apresenta o erro; `requestFocus()` direciona o foco ao campo. É a mensagem de erro que informa o problema; foco sozinho não explica o erro.

O acesso atual compara `teste@campusflow.com` e `123456`. Não consulta um servidor de autenticação e não representa uma conta real. A conexão com o Moodle tem outro fluxo.

```kotlin
startActivity(intent)
finish()
```

`startActivity` abre `MainActivity`. `finish()` encerra a **LoginActivity atual**. Por isso o botão Voltar não revela esse login encerrado.

**Como explicar:** “O login valida os campos e demonstra a navegação. A abertura da próxima tela é feita pelo Intent; depois encerramos o login atual.”

### `MainActivity.kt`

**Responsabilidade:** mostrar pendentes, concluídas e atrasadas e dar acesso às funcionalidades.

Em `onCreate()`, liga os botões a `DisciplinasActivity`, `AtividadesActivity` e `MoodleActivity`. Obtém um `AtividadesViewModel` com `ViewModelProvider` e observa `model.estado`.

Os totais são calculados com as listas do estado: pendentes são todas as não concluídas; atrasadas são um subconjunto dessas pendentes. Assim, três pendentes dentro do prazo e uma atrasada representam **quatro pendentes**, das quais uma atrasada.

Enquanto carrega ou ocorre um erro, não apresenta zero como se fosse um total confirmado. O botão de tentar novamente chama `carregarAtividades()`.

`repeatOnLifecycle(RESUMED)` mantém a observação e a atualização do relógio enquanto o resumo está em primeiro plano. A atualização temporal evita depender de uma nova gravação para perceber um prazo vencido.

Em `onResume()`, carrega a conexão Moodle, tenta atualizar conforme as regras de intervalo e reconcilia os lembretes. Isso não significa que todo retorno à tela fará uma consulta remota: `ConexaoMoodle` controla essa decisão.

**Como explicar:** “O dashboard usa os dados persistidos para calcular os indicadores. Atraso é uma condição da atividade pendente, e não uma terceira lista independente de conclusão.”

## 3. Disciplinas

### `DisciplinasActivity.kt`

**Responsabilidade:** exibir a lista de disciplinas e abrir inclusão ou edição.

Obtém `disciplinaDao()` e coleta `observarTodas()` dentro de `repeatOnLifecycle(STARTED)`. Quando a tabela muda, o Room emite uma nova lista. A tela remove os cartões anteriores e infla `item_disciplina.xml` para cada registro em um `LinearLayout`.

O clique em “Nova disciplina” abre o formulário sem ID. O clique em um cartão envia `EXTRA_DISCIPLINA_ID` com o ID daquela disciplina. O professor fica oculto quando está vazio. Há estados separados de carregamento, erro e lista vazia.

“Voltar ao resumo” chama `finish()`, revelando o dashboard que já estava na pilha.

**Como explicar:** “A lista observa o banco. Para editar, enviamos o identificador da disciplina, porque duas disciplinas podem ter o mesmo nome.”

### `DisciplinaFormActivity.kt`

**Responsabilidade:** apresentar os campos, validar a interação e ligar os botões ao ViewModel.

Use `EXTRA_DISCIPLINA_ID` para localizar o contrato de navegação. O mesmo formulário atende cadastro e edição. Observe como a tela acompanha o estado do ViewModel, preenche os campos, evita ações durante gravações e trata sucesso ou erro.

O nome é obrigatório; professor é opcional. A exclusão exige confirmação e o resultado pode informar que há atividades vinculadas. Cancelar a edição não equivale a atualizar o registro.

**Como explicar:** “Reaproveitamos o formulário: sem ID, cadastramos; com ID, carregamos e editamos o registro existente.”

### `DisciplinaFormViewModel.kt`

**Responsabilidade:** manter o rascunho e coordenar inclusão, atualização e exclusão.

`DisciplinaSaveState` reúne texto e condições como `carregando`, `salvando`, `salva` e `exclusaoBloqueada`. A tela lê `estado`, mas a alteração interna é feita em `_estado`.

Na inicialização, consulta o ID recebido. Ao editar, só preenche o rascunho inicial quando não existe um rascunho restaurado. `atualizarRascunho()` guarda os textos no `SavedStateHandle`; isso não chama o DAO.

`salvar()` impede operações repetidas, trata nome vazio, usa `trim()` e decide entre `inserir` e `atualizar`. `excluir()` usa a operação protegida do DAO e traduz seu resultado em estado para a interface.

**Como explicar:** “Digitar muda o rascunho. A alteração permanente só ocorre quando salvar ou atualizar é executado.”

## 4. Atividades: tela, lista e formulário

### `AtividadesActivity.kt`

**Responsabilidade:** hospedar as abas Pendentes e Concluídas e os botões de navegação.

O `ViewPager2` recebe um `FragmentStateAdapter` com duas páginas. `createFragment(0)` cria `PendentesFragment`; a outra posição cria `ConcluidasFragment`. `TabLayoutMediator` conecta os nomes das abas às páginas.

Trocar a aba muda o conteúdo dentro da mesma Activity. O botão Nova atividade abre o formulário. A Activity também atualiza a referência temporal e apresenta avisos de falha ao concluir ou agendar lembretes.

**Como explicar:** “As abas são dois Fragments hospedados na mesma tela. Isso permite alternar a lista exibida sem abrir outra Activity.”

### `PendentesFragment.kt`

**Responsabilidade:** mostrar somente as atividades ainda não concluídas.

`onCreateView()` carrega `fragment_atividades_lista.xml`. Em `onViewCreated()`, usa `ViewModelProvider(requireActivity())`: o dono do ViewModel é a Activity, permitindo compartilhar o mesmo estado com a outra aba.

Configura o adapter com duas ações: abrir edição por ID e chamar `alternarConcluida()`. Coleta `estado.pendentes`, envia a lista com `submitList()` e alterna carregamento, erro, vazio e lista. Usa o ciclo de vida da **view do Fragment**, que pode ser destruída antes do próprio Fragment.

Em `onDestroyView()`, retira o adapter da RecyclerView.

**Como explicar:** “O Fragment não consulta uma lista separada no banco; ele exibe a parte pendente do estado compartilhado.”

### `ConcluidasFragment.kt`

**Responsabilidade:** apresentar `estado.concluidas`, com a mesma estrutura de lista e estados da aba pendente.

Usa o mesmo tipo de adapter e o ViewModel da Activity. O controle de conclusão permite reabrir a atividade. Reabrir altera o status; não cria uma cópia da atividade.

**Como explicar:** “Ao concluir ou reabrir, mudamos um campo do mesmo registro. O banco emite a lista e a atividade aparece na aba correspondente.”

### `AtividadesViewModel.kt`

**Responsabilidade:** observar as atividades e coordenar alterações de conclusão.

`carregarAtividades()` cancela a consulta anterior e coleta `observarTodasComDisciplina()`. Divide a lista com filtros:

```kotlin
lista.filter { !it.concluida }
lista.filter { it.concluida }
```

`it` é o item atual; `!` significa negação. O primeiro filtro seleciona quem não está concluído.

`alternarConcluida()` calcula o status oposto, bloqueia outro clique para o mesmo ID e chama `atualizarConcluida(id, novoStatus)`. Depois sincroniza os lembretes e aguarda o estado observado refletir o resultado. Há tratamento de erro e limite de espera.

Atualizar apenas a coluna `concluida` evita gravar por cima de título ou prazo que tenham sido alterados em outra operação.

**Como explicar:** “O ViewModel organiza a lista e solicita uma atualização específica do status, mantendo a informação do banco como referência.”

### `AtividadesAdapter.kt`

**Responsabilidade:** transformar cada `AtividadeComDisciplina` em um cartão visual.

`onCreateViewHolder()` infla `item_atividade.xml`; `onBindViewHolder()` chama `bind()` para preencher a view reutilizada. A RecyclerView reaproveita views em vez de criar uma tela completa para cada item.

`bind()` configura título, disciplina/professor, tipo, prioridade, prazo e indicação textual de atraso. Configura também o checkbox e sua descrição acessível de concluir ou reabrir. Durante uma gravação, desabilita ações daquele item.

O checkbox volta ao valor confirmado e a ação é encaminhada ao ViewModel. Isso evita apresentar uma alteração visual como sucesso antes da confirmação dos dados.

`AtividadeDiffCallback` compara o ID para saber se é o mesmo item e compara os dados para saber se o conteúdo mudou. Algumas mudanças auxiliares, como relógio e IDs em salvamento, também provocam atualização dos cartões.

**Como explicar:** “O adapter cuida da representação de cada atividade. Ele encaminha cliques, mas não é responsável por gravar no banco.”

### `AtividadeFormActivity.kt`

**Responsabilidade:** controlar os campos e seletores do formulário e apresentar o estado recebido.

Liga título, descrição, disciplina, tipo, prioridade, prazo e opção de lembretes ao `AtividadeFormViewModel`. O ID recebido diferencia nova atividade de edição. Os seletores mostram opções compreensíveis e enviam valores ao rascunho.

Observe no arquivo os listeners dos campos e dos botões, o seletor de data, a consulta de permissão para notificações e o tratamento de prazo importado. O formulário apresenta a referência de relógio/fuso do aparelho para ajudar a interpretar horários.

A rolagem e o tratamento do teclado permitem alcançar campos e botões em telas menores ou com fonte ampliada. Excluir exige confirmação; salvar exige validação.

**Como explicar:** “A Activity recebe as interações. A decisão de persistir e a proteção contra dados desatualizados ficam no ViewModel.”

### `AtividadeFormViewModel.kt`

**Responsabilidade:** preparar o formulário, manter o rascunho e gravar ou excluir com validação.

`AtividadeFormState` descreve os campos e estados da tela. `init` carrega a atividade quando há ID e observa as disciplinas disponíveis. Os padrões para cadastro são tipo `TAREFA` e prioridade `MEDIA`.

`rascunho()` altera `SavedStateHandle` e o estado em memória. Não grava na tabela. Em um prazo exato importado, preserva o dia em vez de aceitar uma edição comum da data.

Leia `salvar()` em três partes:

1. **Validação:** título, disciplina e prazo obrigatórios; regras de horário para o modo de lembrete personalizado legado.
2. **Transação:** confirma que a disciplina existe, relê o registro atual e insere ou atualiza. Ao editar, preserva a conclusão e o prazo exato atuais, mesmo que tenham mudado enquanto o formulário estava aberto.
3. **Após a gravação:** chama `Lembretes.sincronizar()`. Uma falha no agendamento pode gerar aviso, sem desfazer a atividade já salva.

`excluir()` remove por ID e solicita a atualização dos lembretes. A confirmação visual é feita na Activity.

**Como explicar:** “Revalidamos os dados ao salvar. Isso protege a integridade e evita que um formulário antigo desfaça uma conclusão ou um prazo sincronizado mais recentemente.”

## 5. Banco local: pasta `data/`

### `data/Disciplina.kt`

É a entidade da tabela de disciplinas. `@Entity` declara a tabela; `@PrimaryKey(autoGenerate = true)` permite gerar o ID. Guarda nome e professor. O ID identifica o registro mesmo quando os nomes se repetem.

**Como explicar:** “A entidade representa os dados persistidos de uma disciplina.”

### `data/DisciplinaDao.kt`

Reúne inserir, consultar por ID, atualizar, excluir e observar disciplinas. As anotações Room ligam os métodos às operações SQL.

`excluirSeSemAtividades()` verifica vínculos e exclui dentro de uma transação, retornando `EXCLUIDA`, `COM_ATIVIDADES` ou `INEXISTENTE`. A chave estrangeira também protege o vínculo no banco.

**Como explicar:** “O DAO concentra o acesso aos dados. A transação mantém a verificação e a exclusão como uma operação consistente.”

### `data/Atividade.kt`

Entidade da tabela `atividades`. Inclui identificação, título, disciplina, tipo, prioridade, prazo, conclusão, descrição e informações dos lembretes. O arquivo também declara os enums de tipo e prioridade.

`disciplinaId` é uma chave estrangeira com `RESTRICT`: impede excluir a disciplina enquanto atividades a referenciam. O índice ajuda as consultas pelo vínculo.

Dois campos de prazo têm significados diferentes:

- `prazoEpochDay`: número de dias desde a referência do calendário; representa uma **data**, sem horário.
- `prazoExatoEpochMillis`: instante em milissegundos, opcional, utilizado nos prazos exatos importados.

Trecho real:

```kotlin
!concluida && (prazoExatoEpochMillis?.let { agora >= it }
    ?: (prazoEpochDay < hoje.toEpochDay()))
```

Só uma atividade não concluída pode estar atrasada. Havendo instante exato, compara com o relógio; sem instante, compara os dias. Uma atividade manual com prazo hoje só atrasa no dia seguinte. Uma importada pode vencer hoje em um horário específico.

**Como explicar:** “A regra distingue prazo de calendário e prazo com hora, sem classificar como atrasada uma atividade concluída.”

### `data/AtividadeComDisciplina.kt`

É o objeto de leitura utilizado na lista: reúne campos da atividade com nome e professor da disciplina. Também oferece a verificação de atraso para essa representação.

Não é uma terceira tabela duplicando as informações. Recebe o resultado do `JOIN` do DAO.

**Como explicar:** “A consulta combina duas tabelas e entrega um objeto pronto para a lista.”

### `data/AtividadeConverters.kt`

Converte os enums de tipo e prioridade para a representação textual armazenada e faz o caminho inverso. `@TypeConverter` informa ao Room como realizar essa conversão.

**Como explicar:** “Usamos enums no Kotlin e conversores para persistir esses valores no banco.”

### `data/AtividadeDao.kt`

Contém operações das atividades e registros auxiliares de importação e entrega de lembretes.

`observarTodasComDisciplina()` executa um `INNER JOIN` entre atividades e disciplinas, ordenando por dia de prazo e ID. Seu retorno `Flow<List<AtividadeComDisciplina>>` permite observar mudanças.

`atualizarConcluida()` muda somente o status. `buscarImportado()` procura origem/UID para identificar importações existentes. `etapaEntregue()` e `registrarEntrega()` apoiam o controle dos avisos automáticos.

**Como explicar:** “O DAO oferece consultas específicas para cada necessidade, inclusive uma atualização pequena para concluir ou reabrir.”

### `data/CampusFlowDatabase.kt`

Declara as seis entidades, os conversores e a versão **7** do banco. `getInstance()` compartilha uma instância no processo e abre o arquivo **`campusflow.db`** com o contexto da aplicação.

As migrações preservam os dados existentes ao evoluir a estrutura:

| Migração | Evolução |
|---|---|
| 1 → 2 | Tabela de atividades e vínculo com disciplinas |
| 2 → 3 | Campos do lembrete personalizado |
| 3 → 4 | Lembretes automáticos e histórico de entregas |
| 4 → 5 | Prazo exato e identificação de eventos importados |
| 5 → 6 | Vínculo de cursos Moodle com disciplinas locais |
| 6 → 7 | Controle de referências e conflitos Moodle |

`addMigrations(...)` registra esses caminhos. A persistência é local no armazenamento do aplicativo, não em uma variável que desaparece ao fechar a tela.

**Como explicar:** “O Room organiza o acesso ao SQLite. As migrações atualizam a estrutura sem recriar o banco e apagar o histórico.”

### `data/EntregaAutomatica.kt`

Registra uma entrega associada à atividade, prazo e etapa do aviso. Essa identidade permite consultar se aquela etapa já foi entregue. O vínculo é removido quando a atividade é excluída.

**Como explicar:** “Mantemos um histórico técnico para controlar a repetição dos avisos.”

### `data/EventoImportado.kt`

Relaciona origem, UID externo, ID local e informação de fuso original. A combinação origem/UID identifica o evento importado; o ID local aponta para a atividade criada.

Identifica tarefas importadas do Moodle. A identificação externa permite reconhecer o que já foi importado sem comparar apenas títulos.

**Como explicar:** “Guardamos a identidade externa para evitar importar a mesma atividade como se fosse nova.”

### `data/CursoImportado.kt`

Contém a entidade `CursoImportado` e o `CursoImportadoDao`. Relaciona origem e ID do curso remoto à disciplina local. Nas próximas atualizações, o app sabe a qual disciplina associar novas tarefas.

**Como explicar:** “Esse vínculo mantém a correspondência entre um curso Moodle e sua disciplina no CampusFlow.”

### `data/ControleMoodle.kt`

Contém a entidade `ControleMoodle` e seu DAO. Guarda valores de referência anteriores, valores remotos recentes e indicadores de pendência para título, prazo e conclusão.

As referências permitem comparar três versões: a última reconhecida, a local e a remota. `observarPendentes()` retorna somente controles com algum campo pendente. A exclusão da atividade remove seu controle por `CASCADE`.

**Como explicar:** “Para reconhecer um conflito, precisamos saber o que existia antes, além de comparar o que existe agora.”

## 6. Prazos compartilhados

### `prazos/Prazos.kt`

Reúne duas funções usadas pelo Moodle, pelos formulários e pelos lembretes. `formatar()` converte um instante em milissegundos para texto com data, hora e fuso atual do aparelho. `proximasNove()` encontra o dia da próxima ocorrência das 9h posterior à referência fornecida.

Essas funções são compartilhadas para manter a exibição de prazos e o planejamento dos lembretes consistentes.

**Como explicar:** “Centralizamos a interpretação dos horários para manter a apresentação e o agendamento coerentes entre as funcionalidades.”

## 7. Lembretes: pasta `lembretes/`

### `lembretes/HorarioLembrete.kt`

Valida um horário local com seu fuso e o instante atual. Retorna um resultado explícito: `Valido`, `Passado` ou `InexistenteNoFuso`. O último caso trata horários locais que a regra do fuso desloca, como em transições de horário.

Está ligado à compatibilidade com lembretes personalizados anteriores; a proposta atual prioriza os avisos automáticos.

**Como explicar:** “Uma data e hora precisam ser interpretadas com um fuso. A validação diferencia horário passado de horário inexistente naquele fuso.”

### `lembretes/Lembretes.kt`

Reúne componentes com funções diferentes:

- `PublicadorLembrete`: contrato de publicação, que também facilita testes com um publicador controlado.
- `Notificacoes`: prepara o canal, verifica se a publicação está permitida e monta o aviso e sua ação de abrir a atividade.
- `EntregadorLembrete`: relê a atividade e valida a entrega do lembrete personalizado contra os dados atuais.
- `LembreteWorker`: ponto de execução do trabalho pelo WorkManager.
- `Lembretes`: coordena `sincronizar()` por ID e `reconciliar()` para alinhar os agendamentos ao banco, incluindo o caminho automático.

Concluir, editar ou excluir exige ajustar trabalhos antigos. Por isso o app não agenda uma vez e simplesmente esquece o estado da atividade.

**Como explicar:** “Separar agendamento e entrega permite conferir novamente se o lembrete ainda faz sentido quando o Android executar o trabalho.”

### `lembretes/Automaticos.kt`

`PlanoAutomatico` calcula as etapas de véspera e atraso segundo o prazo e as regras de ativação. A referência de entrega é 9h locais; os prazos exatos importados também entram nesse planejamento.

`EntregadorAutomatico` valida os dados e o histórico antes da publicação; `AutomaticoWorker` executa o trabalho. `Automaticos` organiza os agendamentos. `ReconciliarLembretesWorker` refaz a conciliação quando necessário; `RelogioLembretesReceiver` recebe mudanças de hora/fuso e solicita esse ajuste.

WorkManager respeita restrições e economia de energia do Android. As 9h são referência de planejamento, sem garantia de execução no segundo exato. A permissão de notificações e o canal também influenciam a exibição.

**Como explicar:** “Os lembretes são opcionais, acompanham o prazo e são revalidados. Usamos trabalho persistente em segundo plano, que pode sofrer adiamento pelo sistema.”

## 8. Moodle: da conexão à sincronização

### `MoodleActivity.kt`

Tela de matrícula/usuário e senha, verificação de acesso, seleção de cursos, prévia e confirmação da importação. Também apresenta conexão persistente, atualização, preferências e acesso à revisão de conflitos.

`renderizarImportacao()` monta os controles conforme o estado. Uma disciplina nova pode receber professor consultado e editado; ao vincular a uma disciplina já existente, o professor local é preservado. A tela transforma motivos técnicos de falha em mensagens compreensíveis.

**Como explicar:** “A integração pede revisão e confirmação antes de gravar e oferece controles para acompanhar ou desconectar a conta.”

### `MoodleViewModel.kt`

Coordena as etapas da interface. `verificar()` verifica acesso; `escolher()` mantém cursos e vínculos escolhidos; `consultar()` monta a prévia das tarefas; `editarProfessor()` altera a opção para criação; `importar()` confirma a gravação e pode habilitar o acompanhamento.

Mantém token e informações da sessão necessários ao fluxo em memória, limpando suas referências quando apropriado. A persistência opcional usa o cofre; não salva a senha. A consulta de rede é coordenada fora da execução direta dos cliques da interface.

**Como explicar:** “O ViewModel organiza uma sequência: verificar, escolher, consultar, revisar e importar.”

### `moodle/VerificarMoodle.kt`

Define modelos como `CursoMoodle`, `TarefaMoodle`, `EnvioMoodle`, tipos de erro e a interface `AcessoMoodle`. A interface descreve operações, enquanto `MoodleUpf` as implementa.

`VerificarMoodle.verificar()` autentica, consulta informações do site e funções liberadas, lista cursos e consulta uma amostra. A classe não grava no banco. Uma consulta não liberada não deve ser interpretada como lista vazia de atividades.

**Como explicar:** “Diferenciamos falha, falta de autorização e consulta bem-sucedida. Isso impede tratar uma restrição como ausência de tarefas.”

### `moodle/MoodleUpf.kt`

Implementa o acesso HTTPS ao host fixo da UPF. `post()` prepara a requisição e lê a resposta; `chamada()` organiza as chamadas às funções Moodle. Os métodos de cursos, tarefas, envios e professores convertem JSON em modelos Kotlin.

Credenciais e token seguem no corpo da requisição, não em parâmetros da URL. O cliente mantém validação TLS normal, recusa redirecionamento e limita a resposta. A interpretação de envio distingue enviado, rascunho, pendente e desconhecido.

O escopo atual trata trabalhos/tarefas do Moodle, não questionários/provas do módulo de quiz. “Enviado” representa entrega para organização no app; não significa nota ou aprovação.

**Como explicar:** “O cliente concentra o protocolo da UPF e traduz as respostas para dados do aplicativo.”

### `moodle/ImportadorMoodle.kt`

Valida usuário, cursos, tarefas e vínculos e grava em `withTransaction`. Pode criar uma disciplina ou usar uma existente. Registra a relação curso/disciplina e a identidade de cada tarefa.

Para tarefa nova com prazo, cria uma atividade do tipo trabalho e prioridade média. Um envio confirmado pode entrar como concluído. Para uma tarefa já importada, usa a identidade externa e a conciliação, em vez de criar uma duplicata.

Há uma opção explícita de atualizar conclusões na reimportação manual. Ela é distinta das regras conservadoras de conciliação automática. Tarefas novas sem prazo não são inseridas como atividades sem data obrigatória.

**Como explicar:** “O importador faz o vínculo entre dados remotos e locais e grava o lote de modo consistente.”

### `moodle/CofreMoodle.kt`

Define `SessaoPersistida` e o armazenamento da conexão opcional. Usa chave do AndroidKeyStore, criptografia AES-GCM e `AtomicFile`, em `noBackupFilesDir`.

O token permite consultar o serviço sem pedir a senha em toda atualização. A senha não é persistida. Não se deve afirmar que a chave é sempre protegida por hardware: isso depende do dispositivo. Remover referências em memória também não equivale a garantir a eliminação física de todas as cópias de uma String.

**Como explicar:** “Guardamos opcionalmente o acesso por token com proteção local e sem salvar a senha do Moodle.”

### `moodle/ConexaoMoodle.kt`

Controla carregar conexão, preferências, atualizar e desconectar. Publica `ConexaoMoodleState` para a interface e distingue atualização concluída, ignorada, ocupada ou com falha.

Usa exclusão mútua para coordenar operações. A atualização comum respeita intervalo mínimo de tentativa; a ação manual pode forçar consulta. Uma geração identifica a sessão para impedir que uma consulta antiga grave depois de uma troca ou desconexão.

A consulta acontece antes da confirmação final; a geração é revalidada sob a mesma proteção da sessão antes de gravar. Desconectar apaga o acesso local e cancela trabalhos, mas mantém disciplinas e atividades. Não equivale a revogar o token no servidor Moodle.

**Como explicar:** “A conexão controla quando atualizar e impede que uma resposta de uma sessão antiga altere os dados após a desconexão.”

### `moodle/AgendaMoodle.kt`

`AgendaMoodle` mantém um trabalho periódico único, opcional, com intervalo configurado de uma hora e exigência de rede conectada. O Android pode adiar a execução.

`MoodleSyncWorker` chama a atualização com a geração esperada e decide tentativas limitadas em falhas transitórias ou ocupação. O token não é colocado nos dados ou tags do WorkManager; o trabalho recebe a geração.

**Como explicar:** “O acompanhamento em segundo plano é uma consulta periódica autorizada pelo usuário. Não é recebimento instantâneo por push.”

### `moodle/AvisoMoodle.kt`

Publica aviso de quantidade de novas atividades quando essa preferência e a permissão permitem. É uma notificação diferente dos lembretes de prazo. Seu conteúdo é genérico para reduzir exposição de detalhes acadêmicos.

**Como explicar:** “O usuário escolhe separadamente receber novidades da sincronização e lembretes de prazo.”

## 9. Conflitos Moodle: preservar a decisão do usuário

### `moodle/ConciliacaoMoodle.kt`

Contém a comparação entre referência anterior, valor local e valor remoto, por campo. O método `decidir()` concentra a regra; `conciliar()` aplica a decisão a título, prazo e conclusão.

Exemplo com título:

| Referência anterior | Local | Moodle atual | Decisão em modo automático |
|---|---|---|---|
| Trabalho A | Trabalho A | Trabalho B | Pode aplicar o novo título |
| Trabalho A | Meu título | Trabalho A | Preserva a alteração local |
| Trabalho A | Meu título | Trabalho B | Preserva local e pede revisão |

Sem referência confiável, a regra é conservadora. A conclusão pode avançar automaticamente para concluída quando há informação adequada; não reabre automaticamente uma atividade concluída. Estado de envio desconhecido não equivale a pendente.

`comPrazo()` mantém coerência entre instante exato, dia local e planejamento do aviso de atraso.

**Como explicar:** “Não sobrescrevemos uma edição do usuário só porque chegou uma resposta nova. Comparamos três versões para identificar mudanças concorrentes.”

### `ConflitosMoodleViewModel.kt`

Observa controles pendentes, reúne atividade e disciplina para apresentação e mantém a condição de operação em andamento. `resolver()` chama o resolvedor e ajusta os lembretes após a aplicação.

**Como explicar:** “O ViewModel prepara os conflitos para a tela e coordena a decisão selecionada.”

### `ConflitosMoodleActivity.kt`

Apresenta os valores locais e remotos por campo e oferece manter minha versão ou usar Moodle. Parte dos componentes é criada no Kotlin sobre o contêiner do XML.

Se o prazo remoto não existe, não permite aplicar esse valor como prazo vazio a uma atividade que exige data.

**Como explicar:** “A revisão é por campo, permitindo aceitar um título sem obrigar a aceitar também outra alteração.”

### `moodle/ResolverConflitoMoodle.kt`

`resolver()` abre uma transação, relê atividade e controle e verifica se ainda correspondem aos valores apresentados. Se mudaram, retorna `DESATUALIZADA` para evitar decidir sobre uma proposta antiga.

Ao usar Moodle, altera somente o campo escolhido. Ao manter minha versão, preserva o valor local e registra o remoto como nova referência reconhecida. Assim, a mesma alteração remota não gera o mesmo conflito indefinidamente.

**Como explicar:** “A decisão é aplicada sobre dados revalidados e atualiza a referência da sincronização.”

## 10. XML: onde fica a estrutura visual

O XML declara os componentes e seus atributos. Kotlin encontra esses componentes pelos IDs e define as ações. `@string/...`, `@color/...` e `@drawable/...` referenciam outros recursos.

| Arquivo em `res/layout/` | O que explicar |
|---|---|
| `activity_login.xml` | Rolagem, campos Material de e-mail/senha e botão Entrar. `inputType` orienta o teclado; `endIconMode="password_toggle"` fornece mostrar/ocultar senha. |
| `activity_main.xml` | Resumo, contagens, estado de carregamento/erro e atalhos. |
| `activity_disciplinas.xml` | Contêiner da lista, estado vazio e botões de navegação. |
| `item_disciplina.xml` | Estrutura de um cartão com nome e professor opcional. |
| `activity_disciplina_form.xml` | Campos de nome/professor e ações do formulário. |
| `activity_atividades.xml` | Abas, ViewPager2 e botões; as listas ficam nos Fragments. |
| `fragment_atividades_lista.xml` | RecyclerView, mensagens de estado e tentar novamente; compartilhado pelas duas abas. |
| `item_atividade.xml` | Checkbox, informações e rótulo textual de atraso de cada cartão. |
| `activity_atividade_form.xml` | Campos, seletores e ações; rolagem para alcançar todo o formulário. |
| `activity_moodle.xml` | Entrada e contêineres da integração; controles adicionais são montados na Activity. |
| `activity_conflitos_moodle.xml` | Estrutura que recebe os campos de revisão criados pela Activity. |

**Medidas:** `dp` dimensiona elementos; `sp` dimensiona texto respeitando a preferência de fonte. Rolagem ajuda quando mais espaço é ocupado por textos ampliados. Aparência Material depende de componentes, tema e configuração; não implica automaticamente acessibilidade completa.

**Barras e teclado:** `isAppearanceLightStatusBars = true` solicita ícones escuros para fundo claro. Insets reservam espaço; `adjustResize` no Manifest participa do comportamento quando o teclado abre. A função exata dos insets varia conforme a tela.

**Acessibilidade:** rótulos dos campos, descrições de ações, mensagens de erro, estados textuais e áreas de toque ajudam o TalkBack e o uso com fonte ampliada. “ATRASADA” em texto evita depender somente da cor vermelha.

### Outros recursos

- `values/strings.xml`: textos, erros, descrições acessíveis e formatos com parâmetros. Evita espalhar mensagens literais pelo código.
- `values/colors.xml`: paleta compartilhada.
- `values/themes.xml` e `values-night/themes.xml`: tema e configuração visual; não afirmar que há uma interface escura completa apenas por existir a pasta night.
- `drawable/bg_login_*.xml`: fundos, formas e estados visuais do login. Um recurso só influencia a tela se for referenciado diretamente ou por outro recurso.
- `drawable/ic_*.xml`: vetores de ícones, incluindo os usados em campos, marca e notificações. A alternância da senha do login é fornecida pela configuração Material.
- `mipmap-*`: ícones de lançamento em diferentes configurações/densidades.
- `xml/backup_rules.xml` e `xml/data_extraction_rules.xml`: regras declaradas de backup/transferência. Os arquivos atuais contêm principalmente estruturas padrão; não representam backup próprio da aplicação nem comprovam restauração testada. O cofre usa `noBackupFilesDir`.

**Como explicar:** “Centralizamos textos, cores e tema para manter consistência; o XML estrutura a tela e o Kotlin conecta seus eventos.”

## 11. Configuração, arquivos gerados e testes

### `app/src/main/AndroidManifest.xml`

Declara Activities, tema, ícones e permissões `INTERNET` e `POST_NOTIFICATIONS`. O login é a entrada `MAIN/LAUNCHER`; as demais Activities não são exportadas. Registra o receiver para mudança de hora e fuso.

Declarar a permissão de notificação não substitui o pedido em tempo de execução nas versões que o exigem. O receiver de relógio não é um serviço que fica verificando o tempo continuamente.

**Como explicar:** “O Manifest informa ao Android quais componentes o app possui e quais recursos do sistema ele solicita.”

### `app/build.gradle.kts`

Configura o módulo: identificação, SDKs, compilação, dependências, testes e geração Room por KSP. O mínimo é API 26; `compileSdk` e `targetSdk` estão em 37. Ter mínimo 26 não prova, sozinho, que todos os comportamentos foram testados nessa versão.

Room fornece a persistência, Lifecycle/ViewModel apoiam o estado, Material os componentes visuais e WorkManager os trabalhos persistentes. A opção `room.schemaLocation` exporta a estrutura do banco para `app/schemas`.

### `build.gradle.kts`, `settings.gradle.kts` e `gradle/libs.versions.toml`

Configuram o projeto, seus módulos/repositórios e o catálogo de dependências e versões. Um alias como `libs.room.runtime` aponta para uma definição do catálogo. Não é código executado ao tocar um botão.

### `app/schemas/`, `R` e implementações Room

Os JSON de schema documentam a estrutura versionada e apoiam testes de migração. `R` reúne identificadores gerados dos recursos. Classes Room com sufixo `_Impl` são geradas a partir de entidades e DAOs. Corrigimos as fontes e reconstruímos; não editamos os arquivos gerados para alterar a regra do app.

### `app/src/test/` e `app/src/androidTest/`

Testes locais verificam regras que podem ser executadas fora do aparelho; testes instrumentados usam Android e podem verificar banco, interface e integração com o sistema. Consulte os arquivos de teste para observar entradas e resultados esperados, sem confundi-los com funcionalidades do usuário.

Este roteiro foi feito por leitura de código e não é uma nova execução dos testes. As validações anteriores e seus limites estão nos documentos de API 26, TalkBack, notificações, reinício e Doze da pasta `docs`.

## 12. Kotlin suficiente para ler estes arquivos

| Expressão | Significado no projeto |
|---|---|
| `val` / `var` | Referência sem reatribuição / referência reatribuível. Um objeto guardado em `val` ainda pode ter estado mutável. |
| `data class` | Modelo de dados com comparação e `copy()` gerados. |
| `.copy(campo = valor)` | Cria um objeto com alteração de campo; só isso não grava no banco. |
| `Long?` | Número que também pode ser nulo. |
| `?.` / `?:` | Executar se não nulo / escolher alternativa se nulo. |
| `!!` | Exigir não nulo, com falha se a suposição estiver errada; não é validação. |
| `when` | Selecionar um caminho conforme condições ou valores. |
| `{ item -> ... }` / `it` | Bloco passado como função, com parâmetro explícito ou implícito. |
| `suspend` | Função que pode suspender sem bloquear a coroutine; não significa automaticamente executar em outra thread. |
| `launch` | Inicia uma coroutine no escopo indicado. |
| `Dispatchers.IO` | Contexto apropriado para operações bloqueantes de entrada/saída. |
| `Flow` / `collect` | Fluxo de valores ao longo do tempo / observação desses valores. |
| `MutableStateFlow` / `asStateFlow()` | Estado alterável internamente / exposição somente para leitura externa. |
| `SavedStateHandle` | Apoio à restauração do estado de tela; não substitui persistência definitiva no Room. |
| `withTransaction` | Agrupa operações de banco para confirmar ou desfazer o conjunto. Não inclui automaticamente rede, notificações ou cofre de arquivos. |

`ViewModel` sobrevive à recriação comum da tela por configuração. Ele não é uma instância eterna e não garante, sozinho, recuperação após encerramento do processo. O banco guarda o que foi confirmado; o estado salvo apoia a restauração do rascunho quando o Android a disponibiliza.

## 13. Três percursos para ensaiar com o código aberto

### A. Criar uma atividade

Abra `AtividadesActivity` → clique encaminhado a `AtividadeFormActivity` → digitação chega a `rascunho()` → Salvar chama `salvar()` → validação e transação → `AtividadeDao.inserir()` → Room emite lista → `AtividadesViewModel` separa pendentes/concluídas → Fragment envia `submitList()` → adapter preenche o cartão.

**Fala:** “A atividade só passa a existir no banco depois de salvar. A lista reflete essa gravação e o agendamento é feito em seguida.”

### B. Concluir uma atividade

Checkbox no adapter → callback do Fragment → `alternarConcluida()` → `atualizarConcluida()` → fluxo do banco atualizado → atividade muda de aba → reconciliação dos lembretes.

**Fala:** “Alteramos o status do mesmo ID. Não apagamos nem recriamos a atividade para mudar de aba.”

### C. Atualizar uma tarefa Moodle alterada nos dois lados

`ConexaoMoodle` consulta o cliente → `ImportadorMoodle` identifica a tarefa já vinculada → `ConciliacaoMoodle` compara base/local/remoto → preserva local e registra pendência → ViewModel e Activity de conflitos mostram a proposta → `ResolverConflitoMoodle` revalida e aplica a escolha por campo.

**Fala:** “Uma atualização externa não substitui silenciosamente uma edição concorrente do usuário.”

## 14. O que priorizar na apresentação de dez minutos

Não leia este guia inteiro. Mostre o app e use os arquivos como apoio:

1. **1 minuto:** objetivo, XML e `LoginActivity`; deixar claro que o login é demonstrativo.
2. **1 minuto:** `MainActivity` e os totais, explicando que atrasadas continuam pendentes.
3. **2 minutos:** entidades, DAOs e banco; ID, vínculo e persistência.
4. **2 minutos:** formulário, ViewModel e rascunho; percorrer uma gravação.
5. **2 minutos:** importação Moodle, identidade externa e preservação de alterações.
6. **1 minuto:** lembretes opcionais, WorkManager e acessibilidade.
7. **1 minuto:** testes realizados, limites e fechamento.

Se o professor pedir detalhes, use as seções específicas. Explique a função e a decisão de projeto antes de ler uma linha de código.

## 15. Perguntas para confirmar que você entendeu

Tente responder antes de olhar as respostas:

1. Em qual ponto digitar se transforma em gravação permanente?
2. Por que editar por ID e não pelo nome?
3. Por que concluir atualiza somente uma coluna?
4. Por que um prazo manual de hoje e um prazo importado com hora podem ter atrasos diferentes?
5. Por que consultar o Moodle não é o mesmo que confirmar uma importação?
6. Como o app percebe que houve edição local e remota do mesmo campo?
7. Por que uma falha de notificação não significa que a atividade não foi salva?
8. Qual a diferença entre `startActivity()` e `finish()`?

**Respostas:** 1) Ao salvar e executar a operação no DAO/banco. 2) O ID identifica o registro mesmo com nomes iguais. 3) Para preservar outros campos atualizados por outras operações. 4) Um representa dia de calendário; o outro, um instante exato. 5) A consulta prepara dados para revisão; a confirmação autoriza a gravação. 6) Comparando referência anterior, local e remoto. 7) A transação do banco já pode ter terminado antes do agendamento. 8) O primeiro abre uma Activity; o segundo encerra a Activity atual.
