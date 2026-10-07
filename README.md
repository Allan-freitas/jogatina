# Jogatina — App Android

App nativo em Kotlin + Jetpack Compose para organizar jogatinas com amigos:
criar grupos e combinar partidas dos games favoritos.

## Tela de boas-vindas

Segue o mockup: fundo gradiente azul-marinho profundo, faixas angulares
magenta neon atrás do lutador (`res/drawable/fighter_pixel.png`, PNG com
fundo transparente), título **"Conecte-se e organize suas jogatinas"**,
subtítulo e botão vermelho dividido **"Entrar com Discord"**.

Abaixo do botão há os atalhos **"Entrar com e-mail"** e **"Criar conta"**,
que abrem o dialog de autenticação por e-mail/senha.

## Estrutura

```
app/src/main/java/br/com/jogatina/
├── MainActivity.kt            # fluxo welcome -> app (via TokenStore)
├── data/auth/
│   ├── AuthModels.kt          # contratos das rotas de auth
│   ├── AuthRepository.kt      # HttpURLConnection, sem dependências extras
│   └── TokenStore.kt          # access/refresh tokens (SharedPreferences)
└── ui/
    ├── theme/                 # paleta navy + magenta (dynamicColor off)
    └── welcome/
        ├── WelcomeScreen.kt   # mockup + dialog de e-mail
        └── WelcomeViewModel.kt
```

## API

Base de produção: `https://agfapp.com` (ver `AuthRepository.DEFAULT_BASE_URL`).

| Ação              | Rota                  | Corpo |
|-------------------|-----------------------|-------|
| Login             | `POST /auth/login`    | `email`, `password` → `accessToken`, `refreshToken` |
| Registro          | `POST /auth/register` | `email`, `firstName`, `lastName`, `password` → Id (auto-login em seguida) |
| Login social      | `POST /auth/social`   | `provider`, `token`, `providerUserId?` → `accessToken`, `refreshToken` |
| Saúde             | `GET /health`         | status da API + banco |

Erros vêm em `problem+json` (`Users.NotFoundByEmail`, validações, …) e são
exibidos na própria tela. Para apontar dev/staging, passe outra `baseUrl` ao
`AuthRepository` (ex.: `http://10.0.2.2:5000` no emulador).

> O botão "Entrar com Discord" hoje abre o form de e-mail. Quando o OAuth
> Discord estiver plugado, chame `WelcomeViewModel.socialLogin(discordToken)`
> (usa `provider="discord"`, aceito pela API como string livre).

## Como rodar

Pré-requisitos: JDK 17+, Android SDK com `local.properties` (`sdk.dir`).

```powershell
.\gradlew.bat :app:assembleDebug
```

Preview da welcome: `WelcomeScreen.kt` → `WelcomePreview` no Android Studio.

## Backend

Contratos espelham `CleanArchitecture.slnx` (`src/Web.Api/Endpoints/Users/`):
`Login.cs`, `Register.cs`, `SocialLogin.cs` + `AccessTokensResponse.cs`.
