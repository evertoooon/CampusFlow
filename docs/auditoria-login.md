# CampusFlow — auditoria do redesign do login

Data: 21/10/2026. Branch revisada: `feature/login`. Base anterior: `24f6d21`.

## Parecer e alcance

Aprovado para versionamento do protótipo acadêmico, com as limitações abaixo. A compilação e a análise estática passaram. Isso não comprova a aparência ou o funcionamento em todos os aparelhos API 26+. Não houve certificação de acessibilidade nem teste manual do login. A autenticação simulada preexistente não representa autenticação de produção.

Foram comparados HEAD, staging e arquivos em disco, incluindo todos os novos XMLs. Inicialmente, somente três drawables estavam staged: versões vazias de `bg_login_button`, `bg_login_card` e `bg_login_input`. Os conteúdos completos estavam unstaged. O commit deve conter as versões completas, além dos recursos novos referenciados. Nenhum arquivo local de `.idea` faz parte deste commit.

## Explicação por arquivo

Os caminhos abaixo são relativos à raiz do projeto.

| Arquivo | O que faz e resultado da revisão |
| --- | --- |
| `.gitignore` | Acrescenta exclusão de `.idea/deviceManager.xml`, estado local do gerenciador de aparelhos. Não apaga arquivos nem deixa de rastrear automaticamente arquivos já versionados. |
| `.idea/misc.xml` | Mudança local de configuração do JDK e escala do editor visual. Mantida no disco, fora do staging e do commit. Não houve remoção dos arquivos `.idea` já existentes no histórico. |
| `app/src/main/java/com/everton/campusflow/LoginActivity.kt` | Mantém validação de campos vazios, login simulado, Toast, abertura da MainActivity e `finish()`. Acrescenta ícones escuros nas barras para combinar com o fundo claro e considera teclado nos insets. A auditoria incluiu também `displayCutout()` para proteger o conteúdo em telas com recortes. `getInsets` com vários tipos usa os limites combinados por lado, evitando somar teclado e barra inferior. O padding do ScrollView é atualizado; o espaçamento interno fica no LinearLayout. |
| `app/src/main/res/layout/activity_login.xml` | Organiza logo, títulos, cartão, formulário e rodapé em ScrollView. Os campos passaram a usar TextInputLayout e TextInputEditText Material, com ícones e alternância de visibilidade da senha. IDs usados pelo Kotlin foram preservados; TextInputEditText é compatível com a busca por EditText. Autofill e inputType foram mantidos. A auditoria substituiu a altura fixa de 56dp dos dois campos e do botão por altura ajustável com mínimo de 56dp: com padding e fonte maior, o conteúdo pode crescer sem ficar limitado à altura fixa. |
| `app/src/main/res/drawable/bg_login_button.xml` | Selector azul normal e azul escuro pressionado, cantos de 12dp. É usado no botão com tint desabilitado. Não define estados próprios de foco/desabilitado nem ripple: confirmar o feedback real do widget em execução, pois o tema Material pode inflar um MaterialButton. Não foi feita migração de componente sem evidência visual. |
| `app/src/main/res/drawable/bg_login_card.xml` | Fundo branco, cantos de 24dp e contorno claro. O cartão recebe elevação de 6dp no layout. A cor do contorno está literal; isso é manutenção futura, não falha de compilação. |
| `app/src/main/res/drawable/bg_login_input.xml` | Shape branco com contorno e cantos de 12dp. Sem uso no formulário atual; Material desenha os campos. Preservado, sem exclusão destrutiva. |
| `app/src/main/res/drawable/bg_login_input_selector.xml` | Alternativa com borda azul de 2dp ao focar. Não está ligada aos campos atuais; não é responsável pelo foco do TextInputLayout. Preservada e sinalizada pelo lint. |
| `app/src/main/res/drawable/bg_login_screen.xml` | Layer-list com fundo claro e círculos decorativos. Usa dimensões e deslocamentos negativos para recortar os círculos nas bordas. A decoração contém offsets físicos left/right; verificar composição em RTL e paisagem. Não é elemento interativo. |
| `app/src/main/res/drawable/ic_academic_cap.xml` | Vetor do capelo usado no logo; escala sem depender de bitmaps por densidade. O ImageView tem descrição com o nome do app. Como o nome aparece logo abaixo, TalkBack pode anunciá-lo duas vezes; verificar antes de decidir remover a descrição. |
| `app/src/main/res/drawable/ic_email.xml` | Vetor de e-mail usado como ícone inicial do campo. O hint fornece o nome do campo; o desenho não precisa funcionar como um botão separado. |
| `app/src/main/res/drawable/ic_lock.xml` | Vetor de cadeado usado no campo de senha. Mesma separação entre decoração e identificação do campo. |
| `app/src/main/res/drawable/ic_visibility.xml` | Vetor novo sem uso. `password_toggle` usa o mecanismo e os recursos internos do Material; este arquivo não está ligado ao toggle. |
| `app/src/main/res/drawable/ic_visibility_off.xml` | Variante sem uso, também preservada. Não afirmar na apresentação que este vetor controla a ocultação da senha. |
| `app/src/main/res/values/colors.xml` | Centraliza a paleta azul, fundos e textos. Parte das cores permanece sem uso. Não há variantes noturnas próprias da paleta: o login continua com superfícies explícitas claras. |
| `app/src/main/res/values/themes.xml` | Mantém Material3 DayNight sem ActionBar e define cores principais, status bar e preferência por ícones escuros. É tema global: verificar também MainActivity. Com edge-to-edge, não se deve assumir que statusBarColor controla sozinho a cor final das barras. |
| `app/src/main/res/values-night/themes.xml` | Espelha as personalizações principais, mantendo o pai DayNight. Isso não implementa um redesign escuro completo: componentes que herdam cores do tema podem variar, enquanto cartão, fundo e textos explícitos continuam claros. Requer validação visual noturna. |

## Arquivos de contexto também inspecionados

- `app/build.gradle.kts`: minSdk 26, compileSdk/targetSdk 37 e compatibilidade Java 11. Não alterado. O minSdk é o mínimo declarado, não comprovação de execução em Android 8.
- `gradle/libs.versions.toml`: dependências Material, AppCompat, Activity e Core já estavam declaradas; não foi necessário adicionar biblioteca. Não alterado.
- `AndroidManifest.xml`: LoginActivity é launcher; `adjustResize` já estava presente para as duas activities. Mantido para o tratamento do teclado em versões antigas.
- `values/strings.xml`: textos de interface e mensagens já estavam externalizados. Não alterado.
- `MainActivity.kt`: destino do login e tratamento próprio de barras. Não alterado; o tema global também pode afetar essa tela.
- Testes existentes: unitário verifica somente `2 + 2 = 4`; instrumentado verifica apenas o nome do pacote. Nenhum deles cobre o formulário ou navegação.

## Verificações efetivamente executadas

Comando: `gradlew.bat assembleDebug lintDebug testDebugUnitTest --console=plain`.

Executado antes e depois dos dois ajustes. A execução final terminou com `BUILD SUCCESSFUL`, 49 tarefas (21 executadas e 28 atualizadas), em aproximadamente 5 segundos.

- APK debug gerado em `app/build/outputs/apk/debug/app-debug.apk`.
- Lint: **0 erros e 9 avisos**, todos de recursos não utilizados: bg_login_input, bg_login_input_selector, ic_visibility, ic_visibility_off, black, white, cf_blue_light, cf_border e cf_error. Alguns recursos dependem apenas de outros recursos também sem uso.
- JUnit: **1 teste, 0 falhas, 0 erros, 0 ignorados**. Cobertura funcional do login continua ausente.
- `git diff --check`: sem problemas de whitespace. Avisos de normalização LF/CRLF foram observados; não são erros de compilação.
- Android Studio estava acessível. Foi solicitado salvar pelo editor e realizada saída normal por Alt+F4 e confirmação Exit. Confirmadas ausência da janela e do processo studio64, sem encerramento forçado. Não foram parados à força Gradle, ADB ou outros processos.
- ADB identificou um Motorola conectado com API 34. Não foi instalado APK, disparado teste instrumentado nem manipulado o aparelho para esta auditoria.
- As inspeções próprias da interface do Android Studio não foram executadas; o lint do Gradle foi a inspeção automatizada usada.

## Acessibilidade, IME e compatibilidade: limites práticos

O ScrollView permite rolar o conteúdo quando o teclado reduz a área disponível. O listener inclui barras, teclado e recortes. Campos e botão têm mínimo de 56dp e altura flexível, e textos usam sp. Isso melhora a estrutura, mas não substitui observar o foco e a rolagem em execução.

Permanecem pendentes: API 26 real/emulada; Android recente com edge-to-edge; navegação por gestos e três botões; modo claro e noturno; paisagem, tela estreita e recortes; fonte em 200%; TalkBack, ordem de foco e anúncio dos erros; autofill; mostrar/ocultar senha; teclado aberto/fechado e acesso ao botão; botão pressionado/focado/desabilitado; contraste final após aplicação do tema. Não houve captura da tela do app nem avaliação visual no aparelho. O lint sem erros não comprova esses itens.

Roteiro funcional para apresentação: enviar campos vazios e observar mensagem/foco; preencher somente e-mail e observar erro de senha; usar credenciais inválidas e observar Toast; usar as credenciais de demonstração definidas no Kotlin e confirmar MainActivity e comportamento de Voltar. O login é local e simulado, sem servidor, sessão persistente ou autenticação real.

## Preservação e versionamento

As alterações locais e a separação inicial staged/unstaged foram registradas em patches de apoio fora do repositório; também foi feita cópia de `.idea/misc.xml`. Não houve reset destrutivo, limpeza, exclusão de recursos, troca de branch ou merge na master. O staging final usa lista explícita de arquivos do redesign, `.gitignore` e este relatório. A modificação local em `.idea/misc.xml` deve continuar visível no status após o commit.

## Fontes técnicas consultadas

- [Android Developers: edge-to-edge e insets](https://developer.android.com/develop/ui/views/layout/edge-to-edge).
- [Android Developers: acessibilidade, contraste e alvos de toque](https://developer.android.com/guide/topics/ui/accessibility/apps).

## Fala sugerida para a apresentação

“O redesign separa a lógica da Activity da aparência em XML. O layout organiza os componentes; drawables definem fundos e vetores; colors e themes centralizam a identidade visual. TextInputLayout fornece a estrutura Material dos campos e o controle de visibilidade da senha. O tratamento de insets protege o conteúdo das barras, do teclado e de recortes. A compilação e o lint passaram, mas os testes atuais ainda não validam o fluxo de login nem substituem testes manuais de acessibilidade e de aparelhos diferentes.”
