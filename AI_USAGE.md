1. Ferramentas e modelos
Claude.ai (Claude Opus 5.5), no chat: planejamento, geração do backend/CLAUDE.md e frontend/CLAUDE.md, explicação de conceitos e revisões.
O Plano de execução também foi gerado pelo Claude.ai, após passar opções que eu já tinha em mente, questões de arquitetura, contexto do que deveria ser feito, ferramentas que iria usar, dentre outras coisas prévias e questões levantadas/sugeridas pelo próprio chat, que discuti e tirei dúvidas para tomar decisões.
O uso do chat foi registrado manualmente com ai-log.sh snapshot/record Other-ClaudeAI, e são essas as duas primeiras entradas do log.
Claude Code (Opus 5.5): implementação etapa por etapa, esforço Médio em algumas etapas e Alto em outras; rascunhos dos ADRs e READMEs; collection do Insomnia.
Fluxo: uma etapa por vez, com revisão e commit feitos por mim, de acordo com sugestões da IA.

2. Onde a IA errou e eu percebi
E19, condição de corrida: o guard da E17 lia a solicitação, a versão atual e as etapas em 3 SELECTs separados. O teste de concorrência deu 500 em vez de 409, de forma intermitente. A causa: em READ COMMITTED, cada SELECT enxerga os commits feitos até ele, então quem perdia a corrida montava uma snapshot incoerente (solicitação pendente sem etapa pendente). Escolhi a leitura única (solicitação, versão e etapas num só SELECT) e confirmei a correção com o teste repetido 50 vezes.
E9, line_number sem UNIQUE: a IA removeu a constraint para contornar o flush do Hibernate; eu propus o DEFERRABLE INITIALLY DEFERRED.
Collection do Insomnia com JWT fixo: ao abrir a collection no Insomnia, percebi que a primeira versão tinha tokens colados, ou seja, um segredo indo para um repositório público. Pedi para o Claude Code fazer as alterações e correções antes de realizar o commit.

3. O que foi feito sem IA, de propósito, e por quê
A revisão de código e todos os commits foram feitos manualmente, para verificar o que estava sendo feito, como estava sendo feito e as alterações realizadas. 
Foram realizados testes manuais pelo Insomnia e pelo front, para não se limitar aos testes automatizados e da IA, conseguindo verificar na prática pontos específicos.
Verificação dos builds e do CI, para acompanhar o funcionamento e os testes.
Validação direta do banco (tabelas, colunas, trigger), para verificar o funcionamento e a estrutura.
Decisões de arquitetura: baseado em conhecimentos já existentes, conteúdos que assisti, conceitos que revisei e sugestões de opções através do Claude.ai.
Alguns ajustes pontuais de nomenclaturas, comentários e outros detalhes, feitos manualmente, mas commitados junto com o que foi feito pela IA.

4. Como eu validei o código gerado.
- Revisei o código gerado em cada etapa.
- Rodei o back e o front manualmente, assim como seus testes, para verificar.
- Monitorei o banco de dados, e o que estava sendo criado/salvo.
- Realizei testes manuais.
- Revisei o código gerado após finalizar o que tinha planejado no plano de execução.
- Testes dos comandos do docker e das aplicações locais.
- Tratei os avisos do compilador e do CI (API depreciada, varargs ambíguo);
- Segui os comandos do README no cmd e encontrei comandos que só funcionavam no bash.