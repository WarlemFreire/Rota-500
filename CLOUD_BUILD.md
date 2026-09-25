# Compilação pelo celular

O projeto inclui `.github/workflows/build-apk.yml`. Em um repositório **privado** do GitHub com GitHub Actions ativo, uma atualização no ramo `main` inicia a compilação. O servidor de compilação fornece Android SDK 36, Build Tools 36 e Java 17. O fluxo instala Gradle 9.5, roda os testes do motor local, gera um APK de depuração e guarda o arquivo por sete dias.

Depois de uma execução bem-sucedida, no celular: abra o repositório no GitHub, toque em **Actions**, abra a execução **Gerar APK do Rota 500**, role até **Artifacts** e baixe **Rota500-apk-debug**. Extraia o ZIP de artefato para obter `app-debug.apk`. O Android pode pedir permissão para instalar um aplicativo baixado pelo navegador.

Esse APK é uma versão de teste. A primeira instalação e leitura de Uber/99 ainda exigem calibração no seu telefone. Deixe a recusa automática desativada. Guarde o código no repositório privado, pois o projeto e seu histórico de alterações não precisam ficar públicos.

O plano gratuito do GitHub tem cotas mensais para minutos e armazenamento de Actions em repositórios privados. Verifique o saldo da conta antes de repetir muitas compilações. O fluxo limita cada execução a 30 minutos e mantém o artefato por sete dias.
