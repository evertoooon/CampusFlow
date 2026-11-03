# Fonte ampliada e Doze — 02/11/2026

## Fonte em 200%

O controlador externo registrou a escala original 1.0, configurou temporariamente 2.0 e restaurou 1.0 no finally. FonteAmpliadaTest não altera configurações: exige escala mínima 1.9 e, sem ela, é explicitamente ignorado na bateria comum.

Dois testes aprovados em 6,238 segundos no emulador API 37.2:

- Formulário de atividade: após carregar as disciplinas e rolar ao final do conteúdo, Salvar e Voltar ficam totalmente visíveis, com área de toque de pelo menos 48dp e textos sem elipse ou corte vertical.
- Lista de atividades: mantém pelo menos 96dp visíveis para navegação na lista; textos dos botões Nova atividade, Importar calendário e Voltar ao resumo não ficam truncados.

A primeira tentativa falhou no helper do teste de rolagem. O teste foi ajustado para esperar o carregamento e usar a altura real do conteúdo, em vez de Int.MAX_VALUE; o reteste passou. Nenhuma alteração foi feita no layout ou código de produção para contornar a verificação.

Essas asserções não cobrem todos os textos/campos/telas, orientação horizontal, sobreposição do teclado ou navegação pelo TalkBack. Não equivalem a uma auditoria completa de acessibilidade nem a aprovação visual humana.

## Repouso profundo (Doze)

DozeLembreteRealTest é dirigido em duas fases com argumento fase=preparar/verificar/limpar; sem argumento, é ignorado na bateria normal. A preparação cria somente uma disciplina e uma atividade temporárias com lembrete personalizado para 90 segundos adiante. Um marcador privado guarda IDs, horário e UUID do WorkManager, sem credenciais.

O controlador externo começou com o estado ACTIVE, entrou em repouso profundo pelo comando de teste do Android e confirmou IDLE. Sem abrir o CampusFlow, aguardou o horário solicitado mais dez segundos e conferiu novamente:

- Estado IDLE mantido; aviso da atividade temporária ausente.
- Ao sair de Doze, a notificação real foi encontrada pelo serviço Android antes de iniciar o runner.
- A fase verificar aprovou em 0,157 segundo: mesmo UUID do trabalho, publicação após o horário e lembreteEnviado gravado.
- Registros temporários, aviso, agendamento e marcador removidos. O finally do controlador também retirou a simulação de repouso, deixando o emulador em ACTIVE.

Não foram alterados relógio, fuso, bateria simulada, permissões, whitelist nem configuração de otimização do aplicativo. Não foi forçada a execução de um job para fabricar a entrega.

Esse resultado confirma adiamento e retomada para o lembrete personalizado, neste cenário curto em API 37.2. Não prova entrega pontual durante Doze, teste prolongado, comportamento de fabricantes, automáticos das 9h nesse modo ou API 26.

O adiamento de JobScheduler/WorkManager em Doze é esperado: [documentação oficial do Android](https://developer.android.com/training/monitoring-device-state/doze-standby). O produto não deve prometer avisos exatos nesse cenário.

## Integridade e revisão

APK de testes e lintDebug aprovados. Nenhum código de produção modificado. Os testes foram revisados em modo somente leitura pelo Gemini, que destacou os limites de acessibilidade, duração e fabricantes; não editou arquivos nem executou comandos.

Backup consistente antes/depois confirmou seis disciplinas e quatorze atividades originais preservadas integralmente. Escala de fonte final 1.0 e estado de repouso final ACTIVE foram consultados novamente.

Os 33 testes unitários e 52 instrumentados do conjunto anterior não foram repetidos aqui: foram executadas as novas verificações dirigidas e o Lint.

## Pendências reais

- Execução em API 26: só foram encontradas imagens locais de API 37.0 e 37.2; cmdline-tools/latest/bin/sdkmanager.bat também não está instalado. Não anunciar compatibilidade de execução como testada apenas pelo minSdk e Lint.
- Leitura e navegação com TalkBack, avaliação visual das demais telas e fonte ampliada com teclado/orientação horizontal.
- Repouso prolongado e aparelhos com políticas de bateria de fabricantes.
- Publicação real de uma nova tarefa no Moodle e observação da próxima sincronização, sem criar ou alterar tarefas acadêmicas reais apenas para testar.

## Continuação em 03/11/2026 — API 26

A imagem oficial Android 8.0 foi instalada em AVD separada (CampusFlow_API26). A bateria registrou 53 aprovados e quatro assumptions ignoradas; depois os dois testes de fonte 200% passaram em execução dirigida. Banco de testes íntegro e sem fixtures restantes. Nenhuma correção de produção necessária. Detalhes e limites em [validacao-api26.md](validacao-api26.md). A ausência de imagem/ferramentas descrita acima é histórica; a bateria básica na API mínima agora foi executada.
