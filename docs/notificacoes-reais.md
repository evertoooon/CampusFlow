# Validação de notificações reais — 02/11/2026

## Resultado observado

O teste instrumentado NotificacaoRealTest.entregaRealAbreAtividadeECancelaAoConcluir passou em 18,189 segundos no emulador Medium_Phone (API 37.2). Utiliza WorkManager, canal Android, NotificationManager e o publicador de produção reais; não usa publicador simulado.

Foram criadas uma disciplina e duas atividades temporárias identificadas como teste. Para a véspera, o prazo era amanhã; para o atraso, ontem. Como o relógio do aparelho já estava depois das 9h, ambos os avisos ainda pertinentes puderam ser entregues pelo agendamento normal do Android. O teste não altera relógio, fuso, permissões nem otimizações de bateria.

A permissão já havia sido concedida pelo usuário. Se o canal ou a permissão estiverem desabilitados, ou se ainda não forem 9h, o teste é ignorado explicitamente, sem conceder acesso automaticamente. A espera tem limite de 90 segundos; não é uma promessa de prazo de entrega em qualquer aparelho.

Foram conferidos:

- Dois avisos publicados pelo Android e os respectivos textos de véspera/atraso.
- Histórico de entrega gravado para cada etapa.
- Execução do PendingIntent da notificação abrindo AtividadeFormActivity com o ID correto.
- Remoção da notificação ao concluir a atividade.
- Histórico preservado ao reabrir, impedindo reagendamento da mesma etapa já enviada.

O teste remove seus próprios registros e cancela seus agendamentos/notificações no finally. Backup consistente antes/depois confirmou seis disciplinas e quatorze atividades preexistentes integralmente preservadas. Nenhum código de produção foi modificado.

Compilação do APK de testes e lintDebug concluídos com sucesso. A bateria anterior de 33 unitários e 52 instrumentados foi registrada na revisão anterior; não foi repetida nesta etapa sem mudanças de produção.

## O que isso ainda não prova

Executar PendingIntent.send testa o destino funcional da notificação; não equivale a tocar fisicamente na bandeja, nem verifica aparência, som, texto ampliado, TalkBack ou navegação pelo botão Voltar. Ainda faltam reinício real, Doze/economia de bateria e execução na API 26. Neste computador só foram encontradas imagens de API 37.0 e 37.2; não foi instalada uma imagem adicional nesta etapa.

A entrega foi imediata para horários de hoje já atingidos, com o aplicativo ativo. Isso não prova entrega futura com processo encerrado ou sob restrições de fabricante. O teste não faz escrita no Moodle. A abertura do resumo pelo PendingIntent pode acionar uma sincronização de leitura previamente autorizada nas preferências reais.

## Próximo roteiro

1. Validar visualmente um aviso e tocar nele pela bandeja, incluindo voltar à lista.
2. Preparar uma atividade temporária e um aviso futuro; registrar ID, horário e agendamento. Reiniciar somente o emulador, sem limpar dados, adiantar relógio ou forçar parada. Conferir persistência e entrega após inicialização; depois remover somente essa atividade e comparar o backup.
3. Validar separadamente Doze e API 26, sem interpretar um atraso permitido pelo WorkManager como perda automática do lembrete.

## Conferência com Gemini

Gemini revisou o teste somente em leitura, destacou o uso do publicador real, o histórico e o cancelamento, sem apontar falha concreta. Concordamos em separar resolução do PendingIntent de gesto humano e avaliação visual. A sugestão dele de disparar reconciliação/broadcast pode validar o reagendamento, mas não substitui reiniciar realmente o Android; esse limite permanece explícito.
