# Orbis Browser TV

Navegador Android leve, otimizado para projetores e controle remoto, com bloqueio básico de anúncios, pop-ups e redirecionamentos abusivos.

## Recursos do MVP

- WebView compatível com Android 7.0+ (API 24)
- Navegação por D-pad/controle remoto
- Barra de endereço e pesquisa
- Voltar, avançar, recarregar e página inicial
- Bloqueio por domínio e padrões de URL
- Bloqueio de múltiplas janelas e pop-ups
- Vídeo em tela cheia
- GitHub Actions para gerar APK debug

## Build

```bash
./gradlew assembleDebug
```

O APK será gerado em `app/build/outputs/apk/debug/app-debug.apk`.

## Aviso

O bloqueador é genérico. Ele não remove anúncios inseridos no mesmo fluxo do conteúdo e alguns sites podem detectar bloqueadores.
