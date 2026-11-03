# CampusFlow — guia de apresentação e consulta

**Duração: cerca de 10 minutos • 8 slides**

Use os slides para orientar a demonstração. As falas abaixo são apoio: adapte ao seu jeito, sem ler cada palavra. O mapa de botões é para estudar e consultar se o professor perguntar; não precisa ser narrado inteiro.

## 1. Roteiro de fala

### Slide 1 — CampusFlow (0:45)

O CampusFlow foi desenvolvido para ajudar o estudante a organizar disciplinas e atividades em um único lugar. O problema é acompanhar prazos distribuídos em diferentes matérias e reduzir o trabalho de cadastrar tudo novamente. A proposta reúne organização local, lembretes opcionais e importação do Moodle. Vou mostrar o caminho do usuário e explicar o motivo das principais escolhas, começando pelo acesso e chegando ao armazenamento e à sincronização.

### Slide 2 — Login e resumo (1:15)

O login possui campos de e-mail e senha e o botão Entrar. Campos vazios recebem uma mensagem de erro, e o foco orienta onde corrigir. O e-mail recebe trim para retirar espaços das extremidades; a senha preserva os caracteres digitados. Este acesso é demonstrativo, com credenciais fixas: não é uma autenticação de produção nem o login do Moodle. Ao entrar, startActivity abre o resumo e finish encerra o login, evitando voltar a ele pelo botão Voltar. O resumo mostra contagens reais do banco e oferece os três caminhos principais. Atrasadas são parte das pendentes, e não um total adicional: cinco pendentes, sendo duas atrasadas, continuam sendo cinco atividades pendentes.

### Slide 3 — Disciplinas (1:00)

Em Ver disciplinas, o usuário encontra as matérias cadastradas. Nova disciplina abre o formulário, e o toque em um registro permite editar. O nome é obrigatório, mas o professor é opcional para não impedir um cadastro quando a informação não estiver disponível. Salvar cria o registro; Atualizar altera o mesmo ID. O ID identifica a disciplina mesmo quando duas matérias têm nomes iguais. Voltar não grava o rascunho. Excluir exige confirmação para conter erros de toque e é bloqueado quando existem atividades vinculadas. A consulta e a exclusão são coordenadas em transação, e a chave estrangeira também protege a integridade no banco.

### Slide 4 — Atividades (1:30)

As abas separam atividades pendentes e concluídas dentro da mesma tela. Tocar no cartão abre a edição; o checkbox conclui ou reabre a atividade. Uma atividade concluída não permanece atrasada. No cadastro, título, disciplina e prazo são obrigatórios; descrição é opcional, tipo começa como Tarefa e prioridade como Média. Datas passadas são aceitas para registrar tarefas que já estão atrasadas. A data manual representa um dia inteiro: só há atraso após esse dia terminar. Em importações com horário, o instante exato é respeitado. Salvar ou Atualizar confirma a gravação; Voltar sai sem gravar. O rascunho pode sobreviver à recriação da tela, mas isso não equivale a salvar. Excluir exige confirmação. Ao editar, o app relê o estado atual para preservar uma conclusão ou um prazo importado atualizado enquanto o formulário estava aberto.

### Slide 5 — Lembretes e acessibilidade (1:10)

Os lembretes ajudam o estudante a acompanhar o prazo sem escolher data e hora para cada nova atividade. Ao ativar a opção, o app planeja um aviso na véspera e outro de atraso às nove horas locais, quando pertinentes. Para prazos importados, considera o horário exato. Registros antigos com lembrete personalizado são preservados. A permissão é solicitada de forma contextual nas versões que a exigem; negar não impede o cadastro. Concluir, excluir ou desativar o lembrete ajusta os agendamentos, e o histórico reduz repetições. O WorkManager respeita as restrições do Android, então não prometemos entrega no segundo exato, especialmente em repouso. A interface tem rolagem, mensagens textuais e identificação dos controles. Fonte em duzentos por cento e os fluxos principais com TalkBack foram verificados.

### Slide 6 — Importação com confirmação (1:30)

A importação reduz o preenchimento manual. Em Importar do Moodle UPF, Verificar acesso consulta a conta pelo serviço liberado pelo Moodle. O usuário seleciona disciplinas, consulta uma prévia e só grava ao confirmar. É possível criar uma disciplina ou vincular a uma existente. O professor é sugerido quando a API libera essa informação; pode ser conferido ou editado nas novas disciplinas. A identidade remota evita duplicações. Quando o envio é confirmado, a atividade pode ser concluída no planejamento; isso não significa nota ou aprovação. Rascunho continua pendente, e envio desconhecido não é tratado como entrega confirmada. O escopo atual são trabalhos e tarefas com prazo, sem questionários. Consultas parciais são informadas. Como alternativa, há importação de arquivo de calendário .ics, com seleção e associação à disciplina.

### Slide 7 — Sincronização e conflitos (1:40)

Para não exigir reimportação constante, o usuário pode manter uma conexão e acompanhar as disciplinas escolhidas. Há atualização ao abrir o resumo, com intervalo mínimo entre tentativas, botão Atualizar agora e opção de segundo plano, planejada em intervalos de uma hora. O Android pode adiar a execução, portanto isso não é recebimento instantâneo. A senha não é salva; o token, quando o usuário opta por manter a conexão, fica cifrado com chave do Android Keystore e fora do backup. Desconectar remove esse acesso e cancela o acompanhamento sem apagar os estudos locais. Para conflitos, cada campo é comparado com a referência remota anterior. Uma edição local é preservada; mudanças concorrentes ficam para revisão. Manter minha versão reconhece a proposta sem adotá-la, evitando repetir a mesma pendência. Usar Moodle aplica somente o campo escolhido. Reabrir uma concluída exige decisão explícita.

### Slide 8 — Base técnica e validação (1:10)

O XML define a organização e a aparência; Kotlin trata as interações. O ViewModel mantém o estado da interface e do rascunho. O DAO concentra as operações de dados, e o Room organiza o banco SQLite campusflow.db, que preserva os registros quando o app fecha. IDs e chaves estrangeiras mantêm os vínculos, e migrações evoluem a estrutura sem apagar os dados existentes. As telas observam mudanças do banco para atualizar listas e resumo. Foram executados testes de persistência, interface, importação e conflitos, além de verificações de notificações. Na API 26, cinquenta e três casos passaram na bateria normal e dois de fonte ampliada passaram depois. Reinício e Doze dirigidos foram verificados na imagem 37.2, nos cenários documentados. O usuário também aprovou os fluxos principais com TalkBack. O resultado atende à proposta de organizar prazos e reduzir recadastro, dentro dos limites apresentados.


## 2. Mapa dos controles: função e justificativa

| Tela / controle | O que faz e por quê |
| --- | --- |
| Login — E-mail e Senha | Recebem o acesso de demonstração. Campos vazios produzem erro; a senha não recebe trim para preservar o que foi digitado. |
| Login — Entrar | Valida, abre o resumo e encerra o login. É um protótipo de acesso local, separado da autenticação Moodle. |
| Login — ícone de senha | Alterna a visualização do campo para ajudar a conferir a digitação. Não muda a senha nem realiza autenticação. |
| Resumo — contagens | Mostram o estado do banco. Atrasadas são pendentes vencidas; não se somam como atividades extras. |
| Resumo — Ver disciplinas / Ver atividades | Abrem as áreas de organização sem exigir repetir o login. |
| Resumo — Importar do Moodle UPF | Abre o fluxo opcional de integração com a conta acadêmica. |
| Mensagens — Tentar novamente / Recarregar | Repetem uma consulta que falhou; informação desconhecida não aparece como zero confirmado. |
| Disciplinas — Nova disciplina | Abre cadastro. Somente o nome é obrigatório; o professor pode ser preenchido depois. |
| Disciplinas — toque no registro | Abre edição pelo ID. Nomes iguais não confundem a identidade dos registros. |
| Disciplinas — Salvar / Atualizar | Criam ou alteram o mesmo registro. Rascunho não equivale a gravação. |
| Disciplinas — Excluir / Cancelar | Pedem confirmação; cancelar preserva. Com atividades vinculadas, a exclusão é bloqueada. |
| Botões Voltar | Encerram a tela atual. No formulário, sair não confirma o rascunho no banco. |
| Atividades — Pendentes / Concluídas | Trocam o conteúdo exibido na mesma Activity, por Fragments. |
| Atividades — Nova atividade | Abre um formulário. Título, disciplina e prazo são obrigatórios; descrição é opcional. |
| Atividades — cartão | Abre edição. O ID preserva o vínculo com a atividade existente. |
| Atividades — checkbox | Conclui ou reabre. Atualiza somente o status, separando essa ação da edição do formulário. |
| Formulário — disciplina | Associa pelo ID; nome e professor ajudam a escolher, e o ID diferencia rótulos iguais. |
| Formulário — tipo / prioridade | Organizam o planejamento. Começam em Tarefa / Média, mas podem ser alterados. |
| Formulário — prazo | Abre o calendário. Datas passadas são aceitas; prazo manual considera o dia inteiro. O prazo exato importado é preservado e não é editado nesse campo. |
| Formulário — Cadastrar disciplina | Aparece quando não há disciplinas, permitindo preparar o vínculo necessário. |
| Formulário — Salvar / Atualizar | Valida e grava em transação; relê os dados atuais para preservar conclusão e prazo importado atualizados. |
| Formulário — Excluir | Remove a atividade após confirmação e reconcilia seus avisos. |
| Formulário — receber avisos | Ativa lembretes opcionais. Salvar sem permitir notificações continua possível. |
| Formulário — permitir notificações | Solicita permissão ou abre as configurações pertinentes. Permissão e canal podem impedir a exibição. |
| Formulário legado — escolher horário / usar automáticos | Mantém lembretes personalizados antigos; a conversão para automáticos só vale ao salvar. |
| Atividades — Importar calendário | Seleciona arquivo .ics, apresenta eventos e permite associá-los a disciplinas. |
| Calendário — selecionar / confirmar | Importa apenas escolhas válidas e evita repetir o mesmo UID. O protótipo aceita até três eventos por importação; não expande recorrências. |
| Moodle — Verificar acesso | Consulta a conta por HTTPS no host fixo da UPF. Os campos de credenciais são limpos; a senha não é salva. |
| Moodle — selecionar disciplinas / vínculo | Escolhe o que consultar e se será criada uma disciplina ou usada uma existente. |
| Moodle — Consultar selecionadas | Monta a prévia sem gravar. Permite conferir prazos, envios, docentes e restrições da consulta. |
| Moodle — professor | Sugestão dependente da API, editável para a nova disciplina. Professor de disciplina existente é preservado. |
| Moodle — Confirmar importação | Grava a prévia e registra a identidade remota para evitar duplicação. |
| Moodle — atualizar conclusões existentes | Opção explícita para concluir itens já importados que aparecem enviados, inclusive os reabertos manualmente. Não reabre itens. |
| Moodle — manter conexão e acompanhar | Opt-in para persistir token cifrado e acompanhar as disciplinas escolhidas; não salva a senha. |
| Moodle — Atualizar agora | Solicita nova consulta sem exigir preencher outra vez todo o fluxo de importação. |
| Moodle — segundo plano | Autoriza o trabalho periódico de uma hora, sujeito ao Android e à disponibilidade de rede. |
| Moodle — avisar novas atividades | Controla avisos de novidades da sincronização. É diferente dos lembretes de prazo de cada atividade. |
| Moodle — Limpar resultado e campos | Limpa a consulta/prévia aberta. Não substitui o botão Desconectar da conexão persistida. |
| Moodle — Desconectar | Apaga o acesso salvo localmente e cancela o acompanhamento; preserva os registros acadêmicos. Não revoga token remoto. |
| Moodle — Revisar alterações | Mostra propostas pendentes e os valores local/remoto por campo. |
| Conflitos — Manter minha versão | Mantém a edição local e reconhece a proposta remota; a mesma diferença não fica reaparecendo. |
| Conflitos — Usar Moodle | Aplica somente o campo escolhido, após conferir se os dados ainda correspondem à proposta exibida. |
| Conflitos — Reabrir conforme o Moodle | Exige decisão explícita; nenhuma conclusão é desfeita automaticamente. |

## 3. Regras que vale explicar em uma frase

- **Persistência:** Room/SQLite mantém os registros no arquivo campusflow.db depois que o aplicativo fecha.
- **Integridade:** ID identifica; chave estrangeira vincula; transação coordena as verificações e a gravação.
- **Rascunho:** SavedStateHandle ajuda na recriação da tela; apenas Salvar/Atualizar confirma a alteração no banco.
- **Atraso:** data manual só vence após o fim do dia; uma importação com horário usa o instante exato. Uma concluída não fica atrasada.
- **Resumo:** cinco pendentes, sendo duas atrasadas, são cinco pendentes, não sete atividades.
- **Envio Moodle:** enviada pode ser concluída no planejamento; não significa nota ou aprovação. Rascunho ou envio desconhecido não comprova entrega.
- **Conflito:** compara local, referência remota anterior e nova proposta. Edição local é preservada até a escolha necessária.
- **Legado:** importações antigas sem histórico confiável exigem cautela; não se inventa uma versão anterior.
- **Prazo removido no Moodle:** mantém o prazo local obrigatório e encaminha a revisão; não inventa uma data.
- **Notificações:** são opcionais; o Android pode adiar os trabalhos. Não são alarmes exatos nem push instantâneo do Moodle.
- **Compatibilidade:** minSdk 26 é o mínimo; compileSdk/targetSdk 37 não significam que o app só roda na versão 37.

## 4. Perguntas prováveis do professor

**Por que Kotlin e XML?** Kotlin controla interações e regras; XML organiza a interface. Os controles Material mantêm uma apresentação consistente e estados de erro compreensíveis.

**Por que ViewModel, DAO e Room?** ViewModel mantém o estado da tela; DAO concentra operações de dados; Room organiza o SQLite e seus vínculos. Isso separa apresentação e persistência e facilita os testes.

**O que fazem startActivity e finish?** startActivity abre outra Activity. finish encerra a Activity atual; ele não abre a próxima tela.

**requestFocus é quem informa o erro?** A mensagem do campo informa o erro. requestFocus direciona o cursor para a correção; são funções complementares.

**E se houver duas disciplinas com o mesmo nome?** São diferenciadas pelo ID. Nome e professor são rótulos para o usuário, não a chave do relacionamento.

**Funciona sem internet?** Os dados já salvos podem ser consultados e organizados localmente. Consultas e atualização do Moodle precisam de rede e acesso liberado pelo serviço.

**Uma tarefa nova do Moodle aparece instantaneamente?** Não. Depende da próxima consulta autorizada; há atualização ao retomar, botão manual e trabalho periódico opcional. A tentativa automática ao retomar respeita um intervalo mínimo de 15 minutos.

**Vocês importam tudo do Moodle?** Não. O escopo são trabalhos/tarefas com prazo, sem questionários. Há limites de lote e status e possibilidade de resposta parcial, informada ao usuário. Na implementação atual: até 20 disciplinas selecionadas, 500 tarefas no lote e 100 consultas de status de envio.

**Como evitam sobrescrever o usuário?** Comparação por campo, referência remota persistida e decisão em transação. Reabrir uma concluída é explícito. Uma proposta desatualizada é recusada.

**Como foi validado?** Testes de banco, migração, interface, importação e conflitos; notificações reais; reinício e Doze nos cenários da imagem 37.2; API 26 com 53 casos aprovados e mais dois de fonte ampliada; escuta manual de login, lista e formulário com TalkBack. Há limites documentados para outras telas, fabricantes e repouso prolongado.

## 5. Demonstração sem estourar o tempo

Use os slides como roteiro e mostre no app apenas os pontos que explicam o comportamento: resumo, uma disciplina, uma atividade e as opções do Moodle. Deixe os dados de demonstração preparados. Se fizer uma demonstração maior, reduza a fala dos slides correspondentes em vez de somar tempo.

Não espere o relógio de um lembrete ou o professor publicar uma tarefa durante a apresentação: explique o mecanismo e a evidência já verificada. Na integração, prefira uma prévia já preparada e não projete a senha da conta acadêmica.

Fechamento sugerido: “O CampusFlow organiza disciplinas e atividades, reduz o recadastro por importação e mantém as decisões locais sob controle do estudante. A persistência e os testes sustentam esse funcionamento, respeitando os limites do Moodle e do Android.”
