# Plano de conexão e sincronização Moodle

Discussão autorizada por Everton com Gemini; planejamento somente, sem alterar
código, guardar credenciais ou ativar sincronização nesta etapa.

## Etapa 1: conexão opcional e atualização ao abrir

- Ação explícita Conectar e acompanhar, host UPF fixo, conta e disciplinas escolhidas.
- Token webservice cifrado com chave no Android Keystore; armazenamento privado
  fora de backup. Nunca senha, token em Room/WorkManager Data/logs ou URL.
- Indicar última tentativa, última atualização bem-sucedida e falhas parciais.
- Atualizar ao abrir com intervalo mínimo entre consultas e botão Atualizar agora.
- Desconectar apaga acesso salvo e cancela trabalhos, preservando dados locais.
  Controle de geração de sessão e verificação antes do commit impedem que uma
  consulta iniciada antes de desconectar grave dados depois. Coordenar essa
  verificação com a transação; cancelar Worker sozinho não resolve a corrida.
- Token rejeitado pede reconexão sem apagar atividades; distinguir falha de conta
  de restrição em uma disciplina. Não presumir renovação silenciosa ou prazo UPF.

## Etapa 2: atualização em segundo plano

WorkManager com trabalho periódico único, rede disponível, sugestão inicial de
uma hora e retentativas com backoff para falhas transitórias. Sem alarme exato.
Android pode atrasar a execução; não prometer entrega instantânea nem polling
contínuo. Notificação de novidades e lembretes dependem de preferências explícitas.
Nenhuma tarefa nova ativa lembrete por padrão sem opt-in.

## Identidade e conflitos

IDs remotos separados por host/conta/tipo, reaproveitando os vínculos existentes.
Novas tarefas são inseridas sem duplicar. Conclusões manuais não são reabertas;
professor de disciplina existente é preservado. Ausência no resultado remoto,
restrição temporária ou remoção no Moodle não implica exclusão local.

Para atualizar título/prazo, acrescentar baseline remoto anterior e controle de
edições locais, com migração preservando dados. Atualização automática só quando
não houver conflito; demais mudanças ficam para revisão do usuário. Registros
anteriores sem baseline não podem ser presumidos intactos. A definição de prioridade
para conclusão remota versus reabertura manual também deve ser explícita.
Manter cobertura clara (assignments) e resolver limites/parcialidade antes de
anunciar sincronização completa; hoje consultas de status são limitadas a 100.

## Validação necessária antes de liberar

Testar armazenamento/backup, chave indisponível e reconexão, queda de rede,
token inválido, restrição isolada de disciplina, repetição/concorrência, migração,
conflitos de prazo/edição e conclusão, desconectar durante consulta, reinício,
Doze e notificações opcionais. Testes em API 26 e versão atual; autenticação
real somente pelo usuário. Evitar testes que alterem dados acadêmicos remotos.

Fontes oficiais consultadas:
https://docs.moodle.org/501/en/admin/setting/mobilesettings
https://docs.moodle.org/502/en/Moodle_app_security
https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work

Moodle permite ao administrador configurar validade de tokens. A configuração
real da UPF não foi consultada. WorkManager não garante horário exato de execução.
