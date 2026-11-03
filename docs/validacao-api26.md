# Validação em Android 8.0 / API 26 — 03/11/2026

## Ambiente separado

Foi criada a AVD CampusFlow_API26, sem reutilizar a AVD Medium_Phone. Na retomada de 03/11, o usuário já havia iniciado a nova AVD e ela estava identificada como emulator-5554. O número de série não é uma identidade permanente: conferir o nome da AVD antes de executar comandos.

- Android 8.0.0, API 26, arquitetura x86_64.
- Imagem oficial AOSP default, revisão 1; perfil Nexus 5X, 1080×1920, densidade 420.
- 1 GB de RAM e dois núcleos para reduzir consumo de memória.
- Ferramentas oficiais de gerenciamento instaladas no SDK local, com tamanho/checksum do pacote conferidos a partir do repositório do Google. Foram usados os mecanismos oficiais de [gerenciamento do SDK](https://developer.android.com/tools/sdkmanager) e criação de AVD.

A nova imagem não tinha CampusFlow instalado. Nenhuma conta ou dado acadêmico do Medium_Phone foi transferido. A instalação e os testes ocorreram somente na AVD nova. As ferramentas e a imagem são instalações locais, fora do Git.

## Resultados

| Verificação | Resultado |
| --- | --- |
| assembleDebug e assembleDebugAndroidTest | Aprovados; app e testes instalaram na API 26. |
| testDebugUnitTest | Resultados dos 33 testes unitários reaproveitados pelo Gradle, sem mudança de código. |
| lintDebug | Aprovado: zero erros, 35 avisos de manutenção já documentados. |
| Bateria normal na API 26 | 57 testes registrados pelo runner, em 42,438 s: 53 aprovados, quatro ignorados por condições de execução. Zero falhas. |
| Fonte em 200%, execução separada | Dois testes aprovados em 1,814 s. Fonte restaurada ao padrão (1.0). |
| Banco do emulador de testes | Schema 7; integrity_check OK; nenhuma violação de chave estrangeira. Zero disciplinas, atividades e controles Moodle restantes após limpeza dos testes. |
| Abertura do login | Confirmada na API 26; captura de referência guardada fora do repositório. |

A contagem “OK (57 tests)” do runner inclui quatro assumptions ignoradas; não significa 57 testes aprovados. Foram ignorados inicialmente dois testes de fonte, um de reinício e um de Doze. Os dois de fonte foram executados depois com a configuração exigida. Assim, foram efetivamente aprovados 55 casos distintos nesta API; os cenários dirigidos de reinício e Doze não foram executados nela.

Entre os aprovados estão os testes existentes de persistência/migração, integridade das disciplinas, CRUD e listas de atividades, lembretes, importação ICS/Moodle, respostas e estado de envio, cofre no Keystore, acompanhamento e conflitos. O teste NotificacaoRealTest também passou com o publicador Android e WorkManager reais.

Na API 26 não existe a solicitação runtime POST_NOTIFICATIONS das versões 33+; o canal de notificações continua sendo relevante e foi usado pelo teste. Nenhuma permissão foi concedida pelo agente para fabricar o resultado.

## Explicação para apresentação

minSdk=26 define a versão mínima de instalação. compileSdk=37 permite compilar contra APIs recentes, e targetSdk=37 define o comportamento esperado nas versões novas. Esses valores não substituem executar o aplicativo em Android antigo. Esta etapa acrescentou evidência real na API mínima declarada, usando a mesma aplicação e seus testes.

Nenhuma alteração de produção ou de teste foi necessária para obter os resultados nesta API. Foram acrescentados somente documentação e registros privados das verificações; arquivos .idea locais continuam preservados fora do commit.

## Limites mantidos

A execução em uma imagem AOSP de referência não garante comportamento idêntico em todos os aparelhos Android 8 nem em versões posteriores. Não foram validados aqui:

- Autenticação, certificados/TLS e importação reais da conta Moodle na API 26; os testes de API usam respostas simuladas, sem credenciais reais.
- Reinício e Doze dirigidos nesta API. A validação anterior desses cenários ocorreu em API 37.2, com seus limites próprios.
- TalkBack, orientação horizontal, todos os textos e telas com teclado/fonte ampliada, fabricantes e repouso prolongado.
- Publicação real de tarefa pelo professor e sua chegada em uma consulta posterior.

Os registros de etapas anteriores descrevem o estado da época. A antiga pendência de executar a bateria básica em API 26 foi atendida pelos resultados acima; os demais limites permanecem explícitos.

## Conferência conjunta

Gemini conferiu o relato de resultados e os limites em modo somente leitura, sem apontar bloqueador concreto para registrar a documentação. Não editou arquivos nem executou comandos. O parecer não amplia o conjunto efetivamente testado nem substitui os cenários manuais pendentes.
