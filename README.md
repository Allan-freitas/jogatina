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

Após o login o usuário cai no **Feed**, com saudação **"Olá, {nome}"**
(`GET users/me`) na TopAppBar, estilo newsfeed do Olympus
(só posts de texto/imagem — sem Multimídia/Blog):

- Composer no topo: texto + anexo de imagem da galeria (`POST feed/images`
  multipart → `POST feed/posts`).
- Cards com autor (**foto** + nome), tempo relativo, imagem (Coil), contador de likes e de
  comentários, e lixeira nos próprios posts.
- **Reactions** estilo LinkedIn: toque no coração alterna "Amei" (❤️),
  clique/pressão longa abre o picker (❤️ 🎉 😮 😂 💡); resumo com top-3 +
  total (`POST feed/posts/{id}/likes { reaction? }`, omitir remove).
- 401 (token expirado) desloga automaticamente para a welcome.

## Games (biblioteca + catálogo)
- Aba **Favorites → "Meus Jogos"**: biblioteca (`GET users/me/games`) com
  capa (Coil, fallback com inicial), chip de status (Quero jogar/Jogando/
  Zerado), estrela de favorito, filtro por status e remoção.
- Lupa na TopAppBar abre o **Catálogo** (`GET games` com busca + debounce,
  filtros de gênero/plataforma, grid 2 colunas, "carregar mais"): botão ＋
  abre dialog de status + favorito (`POST users/me/games`).
- Tocar num jogo da biblioteca abre edição (status/favorito) com **envio de
  capa** (`PUT games/{id}/cover` multipart — a capa vale para o catálogo
  todo).
- A API não tem update: 409 `AlreadyInLibrary` oferece **atualizar** e a
  troca de status/favorito é **DELETE + POST**.

## Notificações
- Sino com **badge de não-lidas** na TopAppBar do Feed → tela
  **Notificações**: lista com ícone por tipo (`friend_request`,
  `friend_accepted`, `new_message`), filtro Todas/Não lidas, tap marca como
  lida (`PATCH notifications/{id}/read`), "ler todas"
  (`POST notifications/read-all`), auto-refresh a cada 60 s na tela.

## Chat

Botão de conversa na TopAppBar do Feed (badge com não-lidas) → **Conversas**
(`GET chat/conversations`, selo por conversa, nova conversa via amigos em
`GET users/me/friends`) → **Conversa** (histórico `GET`, tempo real via
WebSocket `/ws/chat?access_token=` com ping, envio, "lida" automática,
reconexão). Nova conversa: primeira mensagem com `recipientId`, o app
descobre o id criado e abre o histórico.

## Perfil

Aba **Profile**: avatar circular com **borda branca** (foto via Coil ou
inicial), nome, e-mail, data de nascimento e hobbies. Botão **Editar perfil**: troca de foto (galeria),
seletor de data e campo de hobbies (`PUT users/me` + `POST users/me/photo`).

## Estrutura

```
app/src/main/java/br/com/jogatina/
├── MainActivity.kt            # fluxo welcome -> feed/biblioteca/perfil
├── data/api/
│   └── ApiClient.kt           # GET/POST/PUT/PATCH/DELETE JSON + multipart (Bearer)
├── data/auth/
│   ├── AuthModels.kt          # contratos das rotas de auth
│   ├── AuthRepository.kt      # auth via ApiClient
│   └── TokenStore.kt          # tokens + userId (claim sub do JWT)
├── data/feed/
│   ├── FeedModels.kt          # PostDto, CommentDto, LikeResult
│   └── FeedRepository.kt      # feed/... com Bearer
├── data/games/
│   ├── GameModels.kt          # GameDto, MyGameDto, GameStatus
│   └── GamesRepository.kt     # games + users/me/games com Bearer
├── data/notifications/
│   ├── NotificationModels.kt  # NotificationDto
│   └── NotificationsRepository.kt
├── data/chat/
│   ├── ChatModels.kt          # ConversationDto, ChatMessageDto
│   ├── ChatRepository.kt      # REST do chat
│   └── ChatSocket.kt          # WebSocket (OkHttp): send/read/ping
├── data/social/
│   └── FriendsRepository.kt   # amigos p/ nova conversa
└── ui/
    ├── theme/                 # paleta navy + magenta (dynamicColor off)
    ├── welcome/
    │   ├── WelcomeScreen.kt   # mockup + dialog de e-mail
    │   └── WelcomeViewModel.kt
    └── feed/
        ├── FeedScreen.kt      # composer, cards, likes, comentários
        └── FeedViewModel.kt
    └── games/
        ├── GamesScreens.kt    # biblioteca, catálogo, dialog de status
        ├── LibraryViewModel.kt
        └── CatalogViewModel.kt
    └── notifications/
        ├── NotificationsScreen.kt
        └── NotificationsViewModel.kt
    └── profile/
        ├── ProfileScreen.kt       # foto, nascimento, hobbies + edição
        └── ProfileViewModel.kt
    └── chat/
        ├── ChatScreens.kt         # lista, conversa, nova conversa
        ├── ChatListViewModel.kt
        └── ConversationViewModel.kt
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
| Curtir            | `POST /feed/posts/{id}/likes` | `{ reaction? }`: heart, celebrate, wow, haha, insightful (omitir remove) |
| Comentários       | `GET /feed/posts/{id}/comments` | 1º nível + `replies` |
| Comentar/responder| `POST /feed/posts/{id}/comments` | `content`, `parentCommentId?` → Id |
| Apagar post       | `DELETE /feed/posts/{id}` | só o autor → Id |
| Catálogo          | `GET /games`          | `?search=&genre=&platform=&minPopularity=&page=&pageSize=` (anônimo) |
| Minha biblioteca  | `GET /users/me/games` | jogos salvos (status, favorito, capa) |
| Adicionar         | `POST /users/me/games` | `gameId`, `status` (Wishlist/Playing/Completed), `isFavorite` |
| Capa do jogo      | `PUT /games/{id}/cover` | multipart `file` (JPEG/PNG/WebP/GIF ≤ 5 MB) → `{ coverImageUrl }` |
| Remover           | `DELETE /users/me/games/{id}` | 204 (troca de status = DELETE + POST) |
| Notificações      | `GET /notifications`  | `?onlyUnread=` → lista (pedidos, aceites, mensagens) |
| Ler uma           | `PATCH /notifications/{id}/read` | marca como lida |
| Ler todas         | `POST /notifications/read-all` | retorna a quantidade marcada |
| Conversas         | `GET /chat/conversations` | participantes, última msg, não-lidas |
| Histórico         | `GET /chat/conversations/{id}/messages` | cronológico, `?page=&pageSize=` |
| Tempo real        | `WS /ws/chat?access_token=` | frames send/read/ping ↔ message/read_ok/pong/error |
| Amigos            | `GET /users/me/friends` | base da nova conversa |
| Saúde             | `GET /health`         | status da API + banco |
| Meu perfil        | `GET /users/me`       | perfil do logado (nome do "Olá") |
| Editar perfil     | `PUT /users/me`       | `{ birthDate?, hobbies?, country? }` (ISO yyyy-MM-dd, ISO alpha-2) |
| Foto de perfil    | `POST /users/me/photo` | multipart `file` (JPEG/PNG/WebP/GIF ≤ 5 MB) |

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
  `GetFeed.cs` (`totalReactions`, `reactionCounts`, `myReaction`),
  `ToggleLike.cs` (reactions), `AddComment.cs`, `GetComments.cs`,
  `DeletePost.cs` (+ `Domain/Posts`, `Application/Posts`, migrations
  `Add_Feed`, `Add_PostReactionKind`).
- `src/Web.Api/Endpoints/Notifications/`: `Notifications.cs` (lista),
  `MarkAsRead.cs`, `MarkAllAsRead.cs` (sem migration — sem mudança de modelo).
- Chat: `Endpoints/Chat/GetMessages.cs` (histórico) + `Chat/WsChat.cs`
  (tempo real, auth `sub`/`NameIdentifier` + `HttpContext.User` no escopo).
- Perfil: `User.PhotoUrl/BirthDate/Hobbies/Country` + migrations
  `Add_UserProfileFields`, `Add_UserCountry`; endpoints `UpdateProfile.cs`
  (`PUT users/me`), `UploadPhoto.cs` (`POST users/me/photo`), `GetMe.cs`.
- Imagens salvas em `wwwroot/uploads` (`IFileStorage`/`LocalFileStorage`,
  servidas via `UseStaticFiles`).
- Capas dos jogos em `wwwroot/game-covers` (geradas como placeholder neon em
  `gen_covers.py`; troque pelos JPGs finais mantendo os nomes), campo
  `Game.CoverImageUrl` + migration `Add_GameCovers`.

> Deploy: a migration `Add_Feed` precisa ser aplicada no banco de produção
> (`dotnet ef database update` ou step do pipeline — `ApplyMigrations` só
> roda em Development).
