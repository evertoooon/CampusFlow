# Verificação de acesso ao Moodle UPF — 28/10/2026

## Objetivo desta etapa

Validar a conexão direta antes de automatizar a criação das disciplinas e atividades.
Everton confirmou que usa matrícula/usuário e senha na página do Moodle. O botão
“Verificar acesso ao Moodle UPF”, no resumo, abre uma tela própria. Não substitui
o login demonstrativo do CampusFlow.

Ao pressionar “Verificar acesso”, o app envia os dados por POST/HTTPS somente
para `https://ead.upf.br/login/token.php`, serviço `moodle_mobile_app`. O token
recebido é usado em variáveis locais para consultar informações da própria conta,
funções disponibilizadas pelo serviço, disciplinas matriculadas e tarefas de uma
disciplina. A consulta de tarefas só é tentada quando essa função está disponível.

A amostra usa a disciplina com data de início mais recente entre as retornadas.
A tela identifica a disciplina e mostra até cinco tarefas, com prazo no fuso do
aparelho. Prazo ausente continua ausente. Disciplinas vazias, função não liberada
e falha de consulta são estados diferentes. Nenhuma tarefa na amostra não prova
que a conta inteira não tenha atividades. Questionários/provas não são consultados
neste primeiro verificador.

## O que é e o que não é salvo

Não grava disciplinas, atividades ou histórico no Room. Não persiste usuário,
senha ou token em arquivo, preferências, estado salvo ou banco. Os campos são
limpos ao iniciar; rotação antes de iniciar exige digitá-los novamente. O resultado
fica somente na memória do ViewModel enquanto a tela estiver aberta. O botão
Limpar remove esse resultado. Não há repetição automática de tentativas de login.
O Moodle pode emitir ou reutilizar seu próprio token de serviço durante o login;
o verificador não tenta revogar tokens que possam ser compartilhados com outros apps.

Os campos não participam de autofill nem da restauração automática de texto, e a
janela usa FLAG_SECURE. O transporte usa TLS padrão, host/caminhos fixos, POST no
corpo, sem redirecionamentos automáticos, sem logs de dados e limite de 1 MB por
resposta. Não existe verificação de certificado relaxada ou fallback para HTTP.
Timeouts de conexão/leitura evitam espera ilimitada. A senha ainda existe
transitoriamente na memória necessária à requisição; não se promete apagar todas
as cópias de Strings da memória gerenciada do Android.

## Explicação por arquivo

| Arquivo | Responsabilidade |
| --- | --- |
| `moodle/VerificarMoodle.kt` | Contrato de leitura, modelos mínimos e fluxo com estados parciais. Não conhece Android, Room ou credenciais persistidas. |
| `moodle/MoodleUpf.kt` | POST HTTPS para o host fixo, interpretação de JSON e funções Moodle: site info, cursos e assignments. Descarta mensagens brutas do servidor ao apresentar erros. |
| `MoodleViewModel.kt` | Executa a operação fora da thread visual, mantém somente progresso/resultado/erro e permite limpeza. Não recebe SavedStateHandle. |
| `MoodleActivity.kt` e `activity_moodle.xml` | Campos sem restauração, validação, ação explícita de envio, resultado rolável, limpeza e proteção da janela. |
| `MainActivity`, `activity_main.xml`, manifesto e strings | Entrada pelo resumo, Activity privada e permissão normal de Internet. Nenhuma migração adicional de banco nesta etapa. |
| `VerificarMoodleTest.kt` | Seis testes com implementação simulada do contrato, sem rede. Verifica fluxo, funções ausentes, vazio válido e falhas parciais. |
| `MoodleUiTest.kt` | Validação de campos vazios, FLAG_SECURE e rotação sem restaurar os textos fictícios. Não aciona login com campos preenchidos. |

## Próximo teste pelo próprio Everton

1. Abrir o resumo e tocar em Verificar acesso ao Moodle UPF.
2. Digitar seu acesso real diretamente no app e tocar em Verificar acesso.
3. Conferir se o acesso é confirmado, se as disciplinas são suas e se a amostra
   corresponde à disciplina indicada. Se não houver tarefas, conferir o resultado
   sem interpretar isso como falha de login ou ausência de todas as atividades.
4. Relatar apenas a mensagem geral apresentada, sem compartilhar senha ou token.

A leitura real com conta UPF ainda não foi executada. Campos/funções disponíveis
no ambiente podem exigir ajustes. A primeira prova é de acesso/leitura, não de
importação completa, sincronização ou cobertura de todos os módulos do Moodle.

## Limites de verificação

Os testes unitários simulam a interface AcessoMoodle, não o HTTP nem o parser JSON
real. Portanto não comprovam TLS, respostas da UPF ou credenciais de aluno.
A instrumentação de UI não envia dados ao Moodle. Entrega real de notificações,
fluxo completo autenticado, rede indisponível e restrições de conta ainda precisam
de validação manual. Não confundir os 25 testes instrumentados aprovados na etapa
anterior com teste do login Moodle adicionado depois.

Fontes oficiais:
https://github.com/moodle/moodle/blob/MOODLE_405_STABLE/login/token.php
https://github.com/moodle/moodle/blob/MOODLE_405_STABLE/webservice/externallib.php
https://docs.moodle.org/dev/Creating_a_web_service_client

Sem commit, push ou merge nesta etapa.

## Resultados consolidados antes da retomada

Build debug e APK de testes aprovados. Foram aprovados 27 testes unitários;
Lint apresentou zero erros e 29 avisos. O novo teste instrumentado MoodleUiTest
passou (1 teste), sem envio de credenciais ou requisição autenticada. Os 25 testes
instrumentados anteriores pertencem à etapa de atividades/importação e não foram
executados novamente como bateria completa após a inclusão do verificador.

O backup final foi comparado com o novo baseline: duas disciplinas e seis
atividades, com correspondência integral dos dados. As três atividades originais
foram preservadas; outras três correspondem aos exemplos ICS importados.
Nenhum exemplo foi removido. Login real UPF e entrega real de notificação ainda
aguardam validação manual. Nenhum commit ou push foi realizado.

## Evolução para importação

A verificação real foi executada pessoalmente por Everton: acesso confirmado e
24 disciplinas retornadas. A amostra de uma disciplina tinha zero assignments.
O fluxo de seleção/prévia/confirmação está documentado em `importacao-moodle.md`.
A afirmação histórica de que token só existe no escopo da função aplica-se ao
verificador inicial; no fluxo novo ele permanece apenas em memória no ViewModel
até limpeza, conclusão ou destruição, sem SavedStateHandle ou persistência.
