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

## Feed (pós-login)

Após o login o usuário cai no **Feed**, estilo newsfeed do Olympus
(só posts de texto/imagem — sem Multimídia/Blog):

- Composer no topo: texto + anexo de imagem da galeria (`POST feed/images`
  multipart → `POST feed/posts`).
- Cards com autor, tempo relativo, imagem (Coil), contador de likes e de
  comentários, e lixeira nos próprios posts.
- Like alternável (`POST feed/posts/{id}/likes`), comentários expansíveis
  (`GET/POST feed/posts/{id}/comments`) e respostas em 1 nível
  (`parentCommentId`).
- 401 (token expirado) desloga automaticamente para a welcome.

## Estrutura

```
app/src/main/java/br/com/jogatina/
├── MainActivity.kt            # fluxo welcome -> feed (via TokenStore)
├── data/api/
│   └── ApiClient.kt           # GET/POST/DELETE JSON + multipart (Bearer)
├── data/auth/
│   ├── AuthModels.kt          # contratos das rotas de auth
│   ├── AuthRepository.kt      # auth via ApiClient
│   └── TokenStore.kt          # tokens + userId (claim sub do JWT)
├── data/feed/
│   ├── FeedModels.kt          # PostDto, CommentDto, LikeResult
│   └── FeedRepository.kt      # feed/... com Bearer
└── ui/
    ├── theme/                 # paleta navy + magenta (dynamicColor off)
    ├── welcome/
    │   ├── WelcomeScreen.kt   # mockup + dialog de e-mail
    │   └── WelcomeViewModel.kt
    └── feed/
        ├── FeedScreen.kt      # composer, cards, likes, comentários
        └── FeedViewModel.kt
```

## API

Base de produção: `https://agfapp.com` (ver `AuthRepository.DEFAULT_BASE_URL`).

| Ação              | Rota                  | Corpo |
|-------------------|-----------------------|-------|
| Login             | `POST /auth/login`    | `email`, `password` → `accessToken`, `refreshToken` |
| Registro          | `POST /auth/register` | `email`, `firstName`, `lastName`, `password` → Id (auto-login em seguida) |
| Login social      | `POST /auth/social`   | `provider`, `token`, `providerUserId?` → `accessToken`, `refreshToken` |
| Feed              | `GET /feed/posts`     | `?page=&pageSize=` → posts (autor, likes, `likedByMe`, comentários) |
| Criar post        | `POST /feed/posts`    | `content?`, `imageUrl?` (relativo, ex. `/uploads/x.jpg`) → Id |
| Upload de imagem  | `POST /feed/images`   | multipart `file` (JPEG/PNG/WebP/GIF ≤ 5 MB) → `{ imageUrl }` |
| Curtir            | `POST /feed/posts/{id}/likes` | alterna → `{ liked, likeCount }` |
| Comentários       | `GET /feed/posts/{id}/comments` | 1º nível + `replies` |
| Comentar/responder| `POST /feed/posts/{id}/comments` | `content`, `parentCommentId?` → Id |
| Apagar post       | `DELETE /feed/posts/{id}` | só o autor → Id |
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

Contratos espelham `CleanArchitecture.slnx`:
- `src/Web.Api/Endpoints/Users/`: `Login.cs`, `Register.cs`, `SocialLogin.cs`.
- `src/Web.Api/Endpoints/Feed/`: `CreatePost.cs`, `UploadImage.cs`,
  `GetFeed.cs`, `ToggleLike.cs`, `AddComment.cs`, `GetComments.cs`,
  `DeletePost.cs` (+ `Domain/Posts`, `Application/Posts`, migration
  `Add_Feed`).
- Imagens salvas em `wwwroot/uploads` (`IFileStorage`/`LocalFileStorage`,
  servidas via `UseStaticFiles`).

> Deploy: a migration `Add_Feed` precisa ser aplicada no banco de produção
> (`dotnet ef database update` ou step do pipeline — `ApplyMigrations` só
> roda em Development).
