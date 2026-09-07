# Lotofácil - Mapa de Falhas

Aplicativo Android com rótulo roxo e trevo branco.

## O que faz

- Escolhe um arquivo TXT/CSV de resultados da Lotofácil pelo seletor do Android.
- Lê concursos no formato `concurso + 15 dezenas`.
- Permite informar concurso inicial e concurso final.
- Monta o mapa de 25 posições do intervalo.
- Estuda as falhas (dezenas ausentes) por frequência, recência, sequência e transição.
- Testa combinações entre as candidatas e sugere 10 falhas para o concurso seguinte.
- Mostra automaticamente o jogo complementar de 15 dezenas.

## Compilar no GitHub

1. Crie um repositório novo no GitHub.
2. Envie todo o conteúdo deste projeto para a raiz do repositório.
3. Abra a aba **Actions**.
4. Execute **Compilar APK - Mapa de Falhas** (ou faça um push na branch main/master).
5. Ao terminar, abra a execução e baixe o artifact **LOTOFACIL-MAPA-FALHAS-APK**.
6. Dentro do ZIP do artifact estará `app-debug.apk`.

## Observação

O aplicativo faz análise estatística do histórico e não garante resultado futuro de sorteio.
