# Lembrete após reinício real do Android — 02/11/2026

## Resultado

Preparação e verificação de ReinicioLembreteRealTest passaram, respectivamente em 0,260 s e 0,140 s. Foi executado um reinício real do Android no emulador, mantendo o Android Studio aberto. O contador BOOT_COUNT passou de 4 para 5; uptime também reiniciou. Clicar Run app reinicia/relança o aplicativo e não substitui esse procedimento.

O teste criou uma disciplina e uma atividade temporárias com lembrete personalizado para aproximadamente 14h04min05s (três minutos após a preparação), via o agendamento de produção. Um marcador privado guardou apenas os IDs dos registros, horário, contador de boot e UUID do WorkManager; não contém credenciais nem dados acadêmicos.

Após o boot, o agente não abriu o CampusFlow nem iniciou instrumentação. Às 14h04min28s, a consulta ao serviço Android de notificações encontrou o aviso com o identificador exclusivo da atividade de teste. Só então foi iniciada a fase verificar.

Essa fase confirmou:

- Contador de boot maior que o registrado na preparação.
- Existência do mesmo UUID de trabalho persistido no WorkManager.
- Notificação real correspondente e marcação lembreteEnviado no banco.
- Horário de publicação posterior ao início do boot e ao horário solicitado.

A fase final removeu os registros temporários, cancelou seus avisos/agendamentos e apagou o marcador. Backup consistente comparou todos os campos preexistentes: seis disciplinas e quatorze atividades preservadas integralmente.

APK de testes compilado e lintDebug aprovados. Nenhum código de produção foi modificado. Gemini leu o novo teste sem editar arquivos ou executar comandos, destacando o mesmo limite de escopo descrito abaixo.

## Como o teste funciona

O arquivo ReinicioLembreteRealTest.kt aceita o argumento fase: preparar, verificar ou limpar. Sem argumento, é ignorado na bateria normal para não criar registros duradouros nem esperar um reboot inexistente.

A preparação exige notificações já habilitadas pelo usuário e recusa substituir um marcador pendente. A verificação confere a identidade dos registros antes de qualquer limpeza. O modo limpar permite recuperar uma preparação interrompida. A limpeza deve ser feita antes de novo preparo; o usuário não deve editar os registros de teste entre as fases.

## Limites

Foi validado o caminho de lembrete personalizado (lembreteEpochMillis), em API 37.2, sem alteração do relógio e sem forçar JobScheduler ou desativar otimizações do Android. A entrega foi observada antes de iniciar o runner após o boot, evitando confundir o resultado com reagendamento provocado pelo startup do teste.

Isso não valida, por si só, os lembretes automáticos das 9h após reinício, Doze prolongado, API 26, force-stop, tela bloqueada por senha ou restrições de fabricantes. Não se promete execução no segundo exato. Aparência, som, gesto humano, TalkBack e fonte ampliada continuam verificações separadas.
