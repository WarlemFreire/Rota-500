# Compilação e publicação pelo GitHub

O fluxo `.github/workflows/build-apk.yml` roda a cada push na `main` e nas branches de trabalho (`ccr-*`). Ele usa Android SDK 36, Build Tools 36, Java 17 e Gradle 9.5, roda os testes do motor local e gera o APK.

## Versões e canais

| Onde | Versão visível | Publicação |
| --- | --- | --- |
| `main` | `0.9.0` | Release estável, só quando o versionName muda |
| branch com `[publicar-teste]` no commit | `0.9.0-teste.N` | Release de teste (pre-release); ficam só as 5 mais recentes |
| branch sem a marca | `0.9.0-dev.N` | Só artefato, por sete dias |

O versionCode é o número da execução do build no GitHub (`N`), que sempre cresce. Por isso qualquer versão mais nova instala por cima da anterior, seja teste ou estável. O APK publicado se chama `rota500-<versão>-b<N>.apk`; o botão **Buscar atualizações** lê esse número.

O repositório é público, então o botão consulta as Releases sem login. Se ele voltar a ser privado, o botão responde "Sem acesso".

## Assinatura (passo único)

As versões publicadas são assinadas com a chave fixa `signing/rota500.p12`. Ela é protegida por uma senha aleatória de 40 caracteres que **não** está no repositório.

1. No GitHub, abra o repositório > **Settings** > **Secrets and variables** > **Actions** > **New repository secret**.
2. Nome: `ROTA500_KEYSTORE_PASSWORD`. Valor: a senha entregue fora do repositório.
3. Guarde a senha também em outro lugar seguro. Sem ela não é possível publicar atualizações que instalem por cima.

Sem o Secret, o build continua funcionando, mas gera um APK assinado com chave de depuração e **não publica** nada (o resumo da execução avisa).

## Reinstalação única da 0.9

A 0.8 foi assinada com uma chave de depuração que ficava em cache no GitHub e que provavelmente já expirou. A 0.9 usa a chave fixa, então o Android não aceita instalar por cima da 0.8:

1. Na 0.8: **Mais > Salvar backup** e confira o arquivo na pasta.
2. Desinstale o Rota 500.
3. Instale a 0.9 (pela Release ou pelo artefato).
4. **Mais > Restaurar backup** com o arquivo salvo. Reconfigure a pasta de backup e a chave da IA externa, que não vão no backup.

Depois disso, as próximas versões instalam pelo botão **Buscar atualizações**.

## Baixar o APK pelo celular sem o botão

Repositório > **Actions** > execução **Gerar APK do Rota 500** > **Artifacts** > **Rota500-apk**. Extraia o ZIP para obter o APK. O Android pode pedir permissão para instalar um aplicativo baixado pelo navegador.

O plano gratuito do GitHub tem cotas mensais de minutos de Actions em repositórios privados; em repositórios públicos os minutos não são cobrados.
