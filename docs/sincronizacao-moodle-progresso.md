# Progresso da sincronização Moodle — etapa 1

## Implementado

Acompanhamento opcional, inicialmente desmarcado, na confirmação da importação.
A confirmação acompanha apenas os cursos selecionados e substitui uma conexão
anterior. Guarda token webservice e metadados, nunca senha. Não ativa lembretes.

Ao abrir/retomar o resumo, busca novidades, respeitando intervalo mínimo entre
tentativas de 15 minutos. O botão Atualizar agora na tela Moodle permite antecipar.
Não existe trabalho periódico em segundo plano nesta entrega. Se a Activity for
destruída, sua coroutine pode ser cancelada; não prometemos atualização persistente.

Somente novas tarefas com prazo são inseridas. Não altera títulos, prazos,
professores ou conclusões já salvos. Não apaga tarefas ausentes no Moodle e não
recria disciplinas cujo vínculo local foi excluído. Tarefas sem prazo continuam
fora da importação. Estado informa última atualização, número de novidades,
resultado parcial, falha e necessidade de reconexão.

## Por arquivo

- `moodle/CofreMoodle.kt`: AES-GCM com chave Android Keystore. AtomicFile em
  noBackupFilesDir exclui o arquivo do backup Android. Cada gravação usa IV novo.
  Não utiliza data class para sessão, evitando exposição acidental em toString.
  Strings ainda podem existir transitoriamente em memória gerenciada. Chave não
  é necessariamente protegida por hardware em todo aparelho; não se promete isso.
- `moodle/ConexaoMoodle.kt`: token nunca entra no estado público. Mutex de sessão
  serializa conectar/desconectar/commit. A rede fica fora dele; antes de gravar,
  confere novamente a geração UUID da sessão sob o mesmo lock. Outra conexão ou
  desconexão impede aplicar resultado antigo. Mutex de consulta evita duplicação
  simultânea. O motor aceita API, cofre e banco isolados para testes sem rede.
- `MoodleViewModel.kt`: conecta apenas após confirmação/importação. Caso os dados
  sejam salvos e o cofre falhe, mantém os dados e indica reconexão; não finge rollback
  entre SQLite e arquivo, que não compartilham transação. Mantém o fluxo anterior
  sem persistência para quem não marcar acompanhamento.
- `MoodleActivity.kt`, layout e strings: opt-in, atualização manual, desconexão e
  status da conexão. Desconectar remove arquivo/chave, mantendo dados acadêmicos.
  A remoção é local, não revoga um token remoto potencialmente compartilhado com
  o aplicativo oficial Moodle. Não altera as configurações de segurança da UPF.
- `MainActivity.kt`: aciona atualização ao retomar o resumo, sem bloquear sua UI.
- `MoodleUpf.kt` e `VerificarMoodle.kt`: distingue errorcode invalidtoken de uma
  restrição isolada de curso, sem presumir causa de expiração/revogação.
- `ConexaoMoodleTest.kt`: cofre/alias exclusivos de teste e banco em memória.
  Testa cifra/restauração/remoção, reconsulta sem duplicação, preservação de edição
  e conclusão e remoção de sessão durante consulta impedindo gravação posterior.

## Validação realizada

Build debug e APK de testes aprovados. Testes unitários aprovados. Bateria dirigida
com ConexaoMoodleTest e os testes Moodle existentes: `OK (16 tests)`, 5,63 segundos.
Nenhuma credencial real ou requisição autenticada foi usada nos testes. Gemini
revisou os arquivos em modo somente leitura sem apontar problemas concretos.

## Pendências e próximo ponto de retomada

1. Teste real pessoal: conectar, selecionar cursos e marcar Manter conexão e
   acompanhar; confirmar. Encerrar/reabrir app e verificar Atualizar agora. Nenhuma
   conexão real foi ativada pelo agente. Não criar tarefa no Moodle para testar.
2. Conferir inclusão quando o professor publicar uma tarefa; resultado depende da
   próxima consulta. Não há recebimento instantâneo, push remoto ou notificação
   de novidades nesta etapa.
3. Testes adicionais: API 26, processo morto/reinício, chave indisponível/arquivo
   adulterado, falha de disco, token inválido, troca de conta durante rede, cliques
   concorrentes e cancelamento. Dois testes atuais não esgotam esses cenários.
4. Persistir estado de sessão inválida e tentativas entre processos; atualmente a
   limitação entre tentativas é em memória e reiniciar o processo pode consultar
   novamente. Token inválido mostra reconectar, mas pode ser tentado novamente em
   nova retomada após o intervalo. Não há rotina de renovação silenciosa.
5. Acrescentar snapshots/edições locais para revisão de mudanças de prazo/título e
   conclusão. Registros antigos sem baseline não devem ser sobrescritos.
6. Etapa 2: WorkManager periódico único com rede/backoff, cancelamento ao desconectar,
   geração de sessão também no commit do Worker, notificações opcionais e testes
   de Doze/reinício. Nenhum Worker de sincronização foi criado nesta etapa.
7. Cobertura de assignments/status até 100 e total de 500 por lote continua limitada;
   não anunciar sincronização completa de todo tipo de atividade do Moodle.

Sem commit, push ou merge. As alterações locais .idea continuam preservadas fora
de qualquer commit. Trabalho salvo para continuação.

Resultado final desta etapa: 27 testes unitários sem falhas; Lint sem erros e com
33 avisos. Backup antes/depois: três disciplinas e quatro atividades integralmente
preservadas. APK atualizado sem limpar dados. Nenhuma conexão real foi ativada.

## Continuação em 30/10/2026

Etapa 2 implementada: preferências separadas para segundo plano e avisos, worker
periódico com rede e geração de sessão, tentativa e bloqueio por token inválido
persistidos. Nenhuma opção foi ativada automaticamente em conexões antigas.
Detalhes e validação em `sincronizacao-moodle-segundo-plano.md`. A pendência antiga
sobre persistência de tentativa/bloqueio foi tratada. Permanecem os testes reais,
API26/Doze/reinício e o controle de conflitos de alterações existentes.
