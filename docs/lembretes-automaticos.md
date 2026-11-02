# Lembretes automáticos — 28/10/2026

## Comportamento acordado com Everton e Gemini

No formulário novo, o usuário ativa uma opção e salva a atividade, sem escolher
uma data ou horário de lembrete. A permissão para notificações continua opcional
para o cadastro e é solicitada somente pela ação explícita do usuário.

- Véspera do prazo, às 9h locais: “Esta atividade vence amanhã”.
- Primeiro dia depois do prazo, às 9h locais: “Esta atividade está atrasada”.
- Atraso só começa depois que o dia do prazo termina.
- Se a véspera às 9h já passou ao ativar, esse aviso é pulado.
- Ao cadastrar já atrasada, o aviso de atraso fica para as próximas 9h futuras.
- Concluir ou excluir cancela os próximos trabalhos. Reabrir preserva o histórico
  daquele prazo, portanto não repete etapas já entregues.
- Editar o prazo recalcula os alvos e invalida os trabalhos antigos. Voltar a um
  prazo já usado conserva o histórico das etapas entregues para aquele prazo.
- Ao mudar o fuso, os alvos continuam sendo 9h locais. Mudança do relógio também
  força o recálculo do tempo de espera, sem apagar o histórico.
- Se o Android atrasar um aviso de véspera até o dia do prazo, ele é descartado:
  não seria correto dizer “vence amanhã”. Um aviso de atraso atrasado continua
  pertinente enquanto a atividade estiver pendente e atrasada.

## Como explicar cada arquivo na apresentação

| Arquivo | Responsabilidade |
| --- | --- |
| `data/Atividade.kt` | Guarda a escolha automática, o dia do aviso de atraso e quando a opção foi ativada. Conserva os campos dos lembretes personalizados antigos. |
| `data/EntregaAutomatica.kt` | Histórico identificado por atividade, prazo e etapa. A chave composta impede registrar a mesma etapa duas vezes; excluir a atividade elimina seu histórico por chave estrangeira. |
| `data/AtividadeDao.kt` | Consulta e registra o histórico no banco. Continua permitindo atualizar a conclusão sem sobrescrever os campos do formulário. |
| `data/CampusFlowDatabase.kt` e schema `4.json` | Migração 3→4 acrescenta campos e tabela; não apaga ou reconstrói atividades/disciplinas. Os automáticos começam desativados nos registros existentes. |
| `lembretes/Automaticos.kt` | Calcula as datas usando `java.time`, agenda dois trabalhos independentes, confere os dados atuais antes da entrega e registra o envio. Também reconcilia mudanças do relógio/fuso. |
| `lembretes/Lembretes.kt` | Integra o agendamento novo ao fluxo anterior e publica textos diferentes por etapa. Mantém canal, permissão, identidade da notificação e navegação. |
| `AtividadeFormViewModel.kt` | Mantém a opção no rascunho, salva o dia de atraso estável e preserva o estado atual de conclusão. Só converte um lembrete antigo quando o usuário escolhe isso. |
| `AtividadeFormActivity.kt` | Apresenta a opção automática sem calendário de lembrete para novos cadastros. Mostra o modo personalizado e o botão de substituição apenas para registros antigos que já o utilizam. |
| `activity_atividade_form.xml` e `strings.xml` | Controles e textos explicativos; conserva rolagem, campos acessíveis e botões com área mínima de toque. |
| `AndroidManifest.xml` | Recebe mudanças de fuso/hora do sistema para solicitar reconciliação em segundo plano. |

## Preservação dos lembretes existentes

Registros com data/hora personalizada continuam no modo anterior, inclusive ao
editar somente título ou descrição. O botão “Substituir por lembretes automáticos
ao salvar” muda apenas o rascunho; sair sem salvar não altera o banco. Desativar a
opção e salvar remove o lembrete personalizado. Nenhuma notificação automática
é ativada em massa na migração.

## Testes

- `PlanoAutomaticoTest`: cálculo das duas etapas, limite do dia do prazo,
  cadastro tardio, mensagem de véspera expirada e diferença entre fusos.
- `AutomaticosTest`: entrega com publicador simulado, recusa de permissão,
  duas etapas, repetição, conclusão/reabertura, token de prazo obsoleto,
  exclusão e histórico. No app real, criação/restauração do formulário,
  ausência de seletor de horário, dois trabalhos únicos, conservação do
  trabalho ao sincronizar e cancelamento ao concluir. Também verifica edição
  de legado e conversão explícita.
- `LembretesTest`: migra bancos isolados das versões 2 e 3 até a versão 4,
  preservando disciplina, atividade e data/hora/estado do lembrete antigo.
- Demais testes de login, disciplinas, atividades, dashboard e lembretes
  personalizados continuam executados.

## Limitações e roteiro manual

WorkManager usa um atraso mínimo e não garante execução no minuto exato:
https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
As exceções de broadcasts de relógio/fuso estão documentadas em:
https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions

A publicação no Android e o registro SQLite não formam uma transação distribuída.
Uma queda do processo entre eles pode repetir a publicação; a identidade estável
substitui a notificação visível, mas não permite prometer entrega exatamente uma
vez em qualquer falha de processo. A entrega com permissão recusada não marca a
etapa como enviada; ao permitir e reconciliar, somente etapas ainda pertinentes
podem ser entregues.

Os testes automatizados de entrega usam um publicador simulado: não provam a
aparência da notificação real, o toque e a pilha de navegação, nem entrega sob
Doze, reinício/forçar parada, aparelho de fabricante ou API 26. Não foi alterado o
relógio do emulador para acelerar os avisos, nem concedida permissão silenciosa.

Para validar manualmente: criar uma atividade com prazo pelo menos dois dias
adiante, ativar lembretes e permitir notificações pelo botão; conferir que não
existe seletor de horário; editar prazo e concluir/reabrir; conferir um registro
antigo antes/depois de usar o botão de substituição. A entrega real requer
aguardar o horário previsto ou um ambiente de teste separado com relógio controlado.

As alterações permanecem locais, sem commit, push ou merge nesta etapa.

## Resultado verificado nesta etapa

Build debug, APK de testes, testes unitários e Lint concluídos com sucesso.
14 testes unitários: zero falhas/erros. Lint: zero erros e 21 avisos.
Instrumentação final: `OK (22 tests)` em 72,825 segundos.
Backup consistente antes/depois: duas disciplinas e duas atividades do usuário,
com todos os campos previamente existentes preservados. Nenhum dado de teste
permaneceu nessas tabelas. A versão debug foi instalada por atualização, sem
limpar ou desinstalar o aplicativo. Gemini fez revisão somente leitura e não
apontou falhas concretas no código revisado. Depois foi acrescentada a
reprogramação forçada de trabalhos automáticos ao receber mudança manual do
relógio, mantendo os alvos e o histórico; o build e a instrumentação finais já
incluem esse ajuste.
