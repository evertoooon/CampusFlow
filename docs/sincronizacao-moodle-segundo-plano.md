# Atualização Moodle em segundo plano — etapa 2

## Resultado previsto para o usuário

A conexão continua opcional. Depois de conectar/importar com acompanhamento,
há duas preferências independentes na tela Moodle:

- Atualizar em segundo plano: consulta aproximadamente a cada hora, com rede.
- Avisar novidades: publica um resumo com a quantidade de atividades novas.

As duas começam desligadas, inclusive quando o arquivo foi salvo pela etapa 1.
O usuário ativa por escolha própria. Avisos precisam também da permissão Android
e do canal habilitado. O botão de permissão só pede acesso após um toque explícito;
o app não concede permissão por conta própria. Ativar novidades não ativa lembretes
de vencimento. O acompanhamento ao abrir e Atualizar agora continuam disponíveis.

Esta etapa continua trazendo SOMENTE novas tarefas. Não atualiza título, prazo,
professor ou conclusão de tarefas existentes. Alterações de registros existentes
continuam pelo fluxo manual de prévia/confirmação. O controle de conflitos com
snapshot remoto ainda será implementado em etapa posterior.

## Explicação por arquivo

| Arquivo | Responsabilidade |
| --- | --- |
| `moodle/CofreMoodle.kt` | Acrescenta no conteúdo cifrado segundoPlano, avisarNovas, tentativa e bloqueada. Defaults preservam a leitura dos arquivos anteriores. Usa openRead para recuperar backup AtomicFile interrompido, sem API indisponível em Android. Não modifica schema Room. |
| `moodle/AgendaMoodle.kt` | Trabalho periódico único com intervalo de uma hora, primeiro atraso de uma hora, restrição de rede e backoff exponencial de 30 segundos. Geração UUID é o único dado de entrada, sem token/conta/tarefas. Reutiliza trabalho da mesma geração e substitui o de conexão antiga. |
| `MoodleSyncWorker` | Delega ao motor comum; repete somente em falha transitória ou consulta ocupada, até três retentativas além da tentativa inicial. Retentativa ignora o throttle de 15 minutos para não virar uma operação sem efeito. Falta de geração, opt-in desligado, acesso bloqueado e sessão antiga não fazem autenticação. |
| `moodle/ConexaoMoodle.kt` | Persiste tentativa antes da rede e bloqueio quando invalidtoken é confirmado. Lê geração/opt-in antes de consultar e antes de gravar, sob o mesmo mutex usado por preferências/desconectar. Usa as preferências mais recentes ao salvar, sem restaurar valores capturados antes da rede. Retorna um resultado explícito para o Worker. |
| `moodle/AvisoMoodle.kt` | Canal próprio e notificação de contagem, sem nomes de tarefas ou disciplinas. Confere permissão/API33+, autorização global e canal. PendingIntent imutável abre resumo/lista. Visibilidade privada. Não publica se não houver novidades ou opt-in. |
| `MoodleActivity.kt`, layout e strings | Preferências independentes, mensagens de falha do agendamento e botão de permissão. Atualizações visuais não acionam listeners de preferência como se fossem toques. Desconectar também cancela o aviso de novidades. |
| `MainActivity.kt` | Recarrega/reconcilia o agendamento na retomada antes da consulta normal, sem ligar opções novas por padrão. |
| `ConexaoSegundoPlanoTest.kt` | Cofre e banco isolados: persistência de flags/tentativa/bloqueio, adulteração de cifra, recuperação AtomicFile, job antigo, opt-in, rechecagem de avisos, desligamento/desconexão durante a rede e ausência de duplicação. |
| `AgendaMoodleTest.kt` | Nomes de trabalho exclusivos de teste: não altera o agendamento real. Verifica rede, periodicidade, idempotência, troca de geração e cancelamento. Executa Worker real sem geração/com UUID antigo, sem requisição à UPF. |

## Concorrência e desligamento

Rede acontece fora do mutex de sessão. Preferências, conectar e desconectar usam
esse mutex. Antes do commit o motor relê a geração e, quando chamado pelo Worker,
confere novamente se segundo plano está ligado. Desligar durante a rede descarta
o resultado do Worker; desligar só o aviso permite salvar, mas não notificar.
Desconectar troca a situação da sessão removendo o arquivo/chave; nenhum resultado
anterior pode gravar depois que a desconexão foi concluída. Cancelar WorkManager
é uma camada adicional, não a única proteção. Dados locais nunca são apagados
por desconectar. O token não é revogado no Moodle, pois pode ser compartilhado
com o aplicativo oficial; a exclusão é apenas do acesso local.

## Persistência e falhas

Tentativa e token inválido passam a sobreviver à recriação do processo. Sessão
bloqueada não volta a fazer rede, mesmo em Atualizar agora; exige novo acesso.
O Worker pode continuar registrado até a próxima reconciliação, mas termina sem
rede quando bloqueado. Reabrir a tela/resumo reconcilia e cancela o agendamento.
Falha temporária de rede não apaga dados nem marca token como inválido. Restrição
em uma disciplina não é inferida como revogação da conta.

SQLite, arquivo cifrado e sistema de notificações não compartilham transação.
Se houver crash entre commit e notificação, os dados permanecem salvos mas o aviso
pode não aparecer. Repetição não duplica atividades; não se promete entrega única
nem infalível de avisos. Um resumo novo substitui o anterior na área de notificações.
Se permissão/canal estiver bloqueado, os dados ainda são importados sem aviso.
O número mostrado no aviso refere-se ao lote recém-importado, não à soma histórica.

## Limites e teste manual

Android pode adiar WorkManager por bateria, Doze e restrições do fabricante.
Não há recebimento instantâneo, WebSocket, alarme exato ou alteração remota no Moodle.
Force-stop do app pode impedir trabalhos até o usuário reabri-lo. WorkManager
restaura trabalhos após reinício conforme as condições do sistema; isso ainda
precisa ser verificado manualmente no aparelho.

Após atualizar o app: conectar se necessário, ativar segundo plano e, opcionalmente,
avisos. Conceder permissão pessoalmente pelo botão se desejar. Conferir estado e
horário de última atualização. Para validar novidade real, aguardar uma publicação
do professor; não criar/editar tarefa acadêmica apenas para o teste. Desligar opção
e desconectar devem preservar dados. Nenhuma autenticação ou permissão real foi
executada pelo agente. Testes de Doze, reboot, API26, TalkBack, fonte ampliada,
notificação visual/toque e acesso real UPF continuam dependentes de validação manual.

Fontes Android consultadas:
https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work

Sem commit, push ou merge nesta etapa. Alterações locais .idea preservadas.

## Resultados de validação — 30/10/2026

- Build debug e APK de testes aprovados. 27 testes unitários, zero falhas.
- Lint: zero erros, 34 avisos. Houve uma falha interna temporária do Lint/FIR ao
  analisar um teste durante a preparação; a execução estável posterior passou.
- 22 testes dirigidos de Moodle passaram juntos: `OK (22 tests)`, 8,219 segundos.
  Cofres/aliases/bancos desses testes são exclusivos e os gateways de rede são
  fictícios. O teste do Worker usa geração ausente/antiga e não acessa a conta.
- Bateria geral: 47 testes executados; 46 passaram, um teste antigo da lista
  excedeu o timeout. A repetição inicial reproduziu a falha.
- Diagnóstico: AtividadesListaUiTest procurava o cartão só entre views anexadas,
  sem rolar até a fixture. As atividades reais importadas podem colocá-la fora
  da área visível do RecyclerView. O teste agora rola até seu próprio registro e
  exige visibilidade real. Nenhum código de produto mudou para contornar o teste.
- Teste de lista corrigido passou isoladamente: `OK (1 test)`, 5,013 segundos.
  Não apresentar a primeira bateria de 47 como integralmente verde.
- Gemini revisou a implementação em modo somente leitura sem apontar problemas
  concretos no controle de geração, opt-in, notificações e persistência.
- Backup antes/depois da bateria geral: quatro disciplinas e onze atividades,
  com correspondência integral dos dados. Não houve limpeza/restauração de banco.
- Na bateria geral algumas Activities abrem o resumo. Se o aluno já mantiver uma
  conexão, o comportamento normal pode consultar o Moodle conforme o acompanhamento
  autorizado; nenhum novo login foi realizado nem credencial foi exposta. Os testes
  dirigidos de rede usam simulações e não comprovam o comportamento real da UPF.
- APK atualizado sem limpar dados, sem ativar preferências ou conceder permissões.
- O emulador havia retornado a GMT. Foi ajustado para America/Sao_Paulo e a troca
  automática de fuso foi desligada, conforme a preferência já solicitada pelo aluno.

Próximo passo: teste pessoal dos switches e permissão/notificação real, depois
validação de bateria/reinício/API26. Snapshots e conflitos de mudanças em atividades
existentes continuam fora desta entrega. Nenhum commit, push ou merge realizado.
