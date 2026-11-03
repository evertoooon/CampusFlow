# Validação manual com TalkBack — 03/11/2026

## Ambiente e método

A última etapa de acessibilidade foi realizada no Medium_Phone, com imagem Android API 37.2 e TalkBack 17.0.0.889642762, acompanhado pelo próprio Everton ouvindo o emulador. O serviço TalkBack e o mecanismo de voz Google estavam instalados nessa imagem. A AVD separada CampusFlow_API26 não tinha leitor de tela instalado, por isso não foi usada para a escuta.

O agente abriu a tela de Acessibilidade; Everton ativou o TalkBack e confirmou que estava ouvindo. O Android confirmou TalkBackService habilitado e vinculado, com exploração por toque ativa e retorno falado. A ativação não foi feita silenciosamente por comandos de configuração.

A escuta e a compreensão foram confirmadas pelo usuário. O agente não ouviu nem gravou o áudio do sistema. As respostas abaixo são evidências de validação humana, não asserções de um teste automatizado nem certificação universal de acessibilidade.

## Roteiro executado e respostas

| Etapa | Verificação solicitada | Confirmação de Everton |
| --- | --- | --- |
| Ativação | Ativar o TalkBack e ouvir o emulador. | “Ativado e estou ouvindo”. |
| Login | Identificar E-mail, Senha e Entrar; tentar entrar com campos vazios e ouvir “Informe seu e-mail”. | “Sim, tudo foi anunciado”. |
| Lista | Trocar entre Pendentes e Concluídas; identificar atividade, suas informações e controle de concluir/reabrir, sem alterar registros. | “Sim, sem dificuldade”. |
| Formulário | Abrir Nova atividade; percorrer campos/botões; rolar; tentar salvar vazio; ouvir o erro de título obrigatório; voltar sem salvar. | “Sim, tudo acessível”. |

Foi usado o acesso de demonstração local para navegar pelo app. Não foi solicitado login ou senha reais do Moodle, nem foram inseridas credenciais acadêmicas como parte deste roteiro.

## Como o código apoia a leitura

- LoginActivity usa erro do campo e foco para orientar o preenchimento; os campos possuem hints de e-mail/senha e o botão possui texto.
- Os TextInputLayouts do formulário fornecem nomes aos campos. Os estados informativos usam accessibilityLiveRegion polite, permitindo anúncio das atualizações sem exigir somente observação visual.
- AtividadesAdapter identifica o controle de conclusão/reabertura incluindo o título da atividade em contentDescription. Atraso também tem indicação textual, além da cor.
- A rolagem permite alcançar os controles inferiores. A capacidade de uso com fonte em 200% já foi testada separadamente nas imagens recentes e na API 26.

Esses mecanismos ajudam o leitor, mas a presença de atributos no XML não prova, sozinha, que a experiência é compreensível. Por isso a confirmação manual desta etapa complementa as verificações anteriores.

## Integridade e estado final

Backups consistentes antes/depois do Medium_Phone foram comparados em todas as tabelas de dados da aplicação, sem diferença: seis disciplinas e quatorze atividades originais preservadas. Nenhum registro temporário foi criado por este roteiro e nenhum código de produção foi alterado.

O TalkBack permaneceu ativo ao final, conforme ativado pelo usuário. Para voltar ao gesto comum de um toque, o usuário pode desativá-lo na tela de Acessibilidade. A configuração da AVD API 26 não foi alterada por esta ativação.

## Alcance da aprovação

A validação foi aprovada pelo usuário nos três fluxos percorridos: login, lista/abas e formulário com erro/rolagem/retorno. Não foram detalhadamente testados com TalkBack todos os seletores de data, diálogos de exclusão, telas de importação e decisões de conflito. Também não se conclui equivalência entre todas as versões de TalkBack, vozes, aparelhos ou políticas de bateria.

A rodada de validação planejada pode ser registrada como concluída nesse alcance, mantendo os limites dos relatórios anteriores. A preparação da apresentação e a decisão de integração da branch permanecem etapas de entrega separadas.

## Conferência do relatório

Gemini leu o relatório em modo somente leitura e reconheceu o registro da validação humana, a integridade dos dados e os limites de escopo. Não editou arquivos, executou comandos ou controlou o emulador. A conclusão deste documento permanece limitada às verificações efetivamente realizadas e às respostas do usuário.
