# ChirpStack Manager — API (backend)

API REST do ChirpStack Manager: Java 21 + Spring Boot 4.1.1, PostgreSQL 17.5 (H2 em arquivo em development). Backend-for-frontend com autenticação própria e controle por perfil sobre a REST API do **ChirpStack v4**; a API key do ChirpStack fica só aqui. Visão geral, variáveis de ambiente e telas no [README do Manager](../README.md).

| Item | Valor |
|---|---|
| Artefato | `br.com.adailsilva:api-chirpstack-manager-backend:1.0.0` → `target/api-chirpstack-manager-backend.jar` (`finalName`) |
| Pacote base | `br.com.adailsilva.chirpstackmanager.api` |
| Context-path | `/chirpstack-manager/` (rotas em `/chirpstack-manager/api/v1/...`; o NGINX do frontend publica em `/api/v1/...`, e o actuator na 8084 não usa o prefixo) |
| Portas | 8081 (development), 8082 (staging), 8083 (production); actuator 8084 em todos (`SERVER_PORT` / `MANAGEMENT_PORT`) |
| Imagem | `api-chirpstack-manager-backend_platform_linux-arm64` (OCIR) |
| Namespace | `chirpstack-manager` |

---

## Sumário

- [Estrutura](#estrutura)
- [Executar](#executar)
- [Segurança](#segurança)
- [Rotas da API](#rotas-da-api)
- [Tarefas agendadas](#tarefas-agendadas)
- [Banco e migrações](#banco-e-migrações)
- [Build e imagem](#build-e-imagem)
- [Manifestos Kubernetes](#manifestos-kubernetes)
- [Scripts úteis](#scripts-úteis)
- [Testes](#testes)

---

## Estrutura

```text
api_chirpstack_manager_backend/
├── src/main/java/br/com/adailsilva/chirpstackmanager/
│   └── api/                          # aplicação ativa (ChirpStackManagerApplication)
│       ├── controller/               # Auth, Usuario, ContaEmail, Sessao, Visitante, Auditoria, Backup, GoogleDrive
│       │   └── chirpstack/           # 11 controllers que repassam para o ChirpStack v4
│       ├── service/                  # regras de negócio
│       │   ├── chirpstack/           # ChirpStackClient (RestClient), Query, ChirpStackException
│       │   ├── backup/               # BackupService, agendamento, MotorH2 / MotorPostgres
│       │   ├── googledrive/          # envio de backups ao Google Drive (OAuth 2.0)
│       │   └── email/                # SMTP com a senha do banco, corpos dos e-mails, RecifradorSementes
│       ├── security/                 # Perfis, CifradorSegredos, filter/SessaoAtivaFilter, service/TokenService e LoginRateLimiter
│       ├── config/                   # SecurityConfig, WebConfig, AdminBootstrap, *Properties (app.chirpstack, app.security, app.backup, app.google-drive)
│       ├── domain/                   # entity, dto, enums (Perfil, MotivoEncerramento)
│       └── repository/, exception/, web/
├── src/main/resources/
│   ├── application.properties + application-{development,staging,production}.properties
│   ├── db/migration/{H2,PostgreSQL}/ # Flyway V01–V16 (H2 em development; PostgreSQL em staging e production)
│   └── banners/
├── data/                             # H2 em arquivo do perfil development (ignorado pelo Git)
├── database/carga/                   # carga geral (C01: conta de e-mail e Google Drive, segredos por marcadores; C02: regras de alerta padrão)
├── kubernetes/                       # manifestos 01–11 (sem 09), kustomization.yaml e 00.UsefulScripts/
├── Dockerfile
└── pom.xml
```

Dependências principais: `spring-boot-starter-webmvc`, `-restclient`, `-validation`, `-security`, `-security-oauth2-resource-server` (JWT HS256), `-data-jpa`, `-flyway` + `flyway-database-postgresql`, `postgresql`, `h2`, `-actuator`, `-mail`. Não há Swagger/springdoc.

---

## Executar

Pré-requisitos: JDK 21 e Maven 3.9+ (não há Maven Wrapper nesta pasta).

```bash
mvn spring-boot:run                 # development — H2 em ./data/chirpstack-manager, porta 8081
mvn spring-boot:run -Pstaging       # staging     — PostgreSQL local, porta 8082
mvn spring-boot:run -Pproduction    # production  — exige DB_URL/DB_PASSWORD e APP_JWT_SECRET, porta 8083
```

O `application.properties` declara `spring.profiles.active=@activatedProperties@` e o `pom.xml` filtra só esse arquivo (`development` é o padrão). `SPRING_PROFILES_ACTIVE` em tempo de execução tem prioridade.

| Perfil | Banco | Flyway | CORS padrão |
|---|---|---|---|
| `development` | `jdbc:h2:file:./data/chirpstack-manager` (`MODE=PostgreSQL`) | `db/migration/H2` | `http://localhost:4201` |
| `staging` | `DB_URL` (padrão `jdbc:postgresql://localhost:15435/chirpstack_manager`, o banco do `stack_postgresql_pgAdmin_4`) | `db/migration/PostgreSQL` | `http://localhost:4202` |
| `production` | `DB_URL` (padrão `api-chirpstack-manager-postgresql-service.chirpstack-manager.svc.cluster.local:5449/chirpstack_manager_database`) | `db/migration/PostgreSQL` | `https://app-chirpstack-manager.adailsilva.com.br` |

Em todos: `ddl-auto=validate`, threads virtuais, `server.shutdown=graceful`, upload de restauração até `APP_BACKUP_MAX_UPLOAD` (512MB), actuator `health,info` na 8084 (readiness inclui `db`).

Variáveis de ambiente (`CHIRPSTACK_BASE_URL`, `CHIRPSTACK_API_TOKEN`, `APP_JWT_SECRET`, `APP_ADMIN_*`, `APP_CHAVE_CIFRAGEM`, `APP_BACKUP_*`, `GOOGLE_DRIVE_*`…): tabela completa no [README do Manager](../README.md#configuração-variáveis-de-ambiente-do-backend).

---

## Segurança

- Login em `POST /api/v1/auth/login`; `refresh` e `logout` usam o cookie `csm_refresh` (`HttpOnly; SameSite=Strict`, restrito a `/api/v1/auth`, `Secure` por `APP_COOKIE_SECURE`). Access token JWT HS256 (`APP_ACCESS_TOKEN_TTL`, 15 min) e refresh (`APP_REFRESH_TOKEN_TTL`, 7 dias).
- `APP_JWT_SECRET` (mínimo 32 caracteres) é **obrigatório** em production; nos demais perfis, se vazio, a API usa um segredo aleatório (as sessões caem a cada reinício).
- Perfis `ADMIN` (tudo, inclusive tenants, usuários do ChirpStack, usuários do painel, auditoria e backup), `OPERADOR` (escrita em aplicações, dispositivos, gateways, perfis, multicast e integrações; edita dispositivos monitorados e regras de alerta e reconhece alertas) e `VISUALIZADOR` (somente leitura), aplicados com `@PreAuthorize`.
- Bloqueio após 5 tentativas de login por 15 min (`LoginRateLimiter`); política de sessão e encerramento remoto (`SessaoAtivaFilter`).
- Primeiro administrador criado no boot com a base vazia (`AdminBootstrap`) a partir de `APP_ADMIN_EMAIL`/`APP_ADMIN_PASSWORD`, com padrão `admin@chirpstack.com.br` / `chirpstack`. Administrador já existente não é alterado.
- Troca obrigatória da senha inicial (`SenhaInicialService`): enquanto a senha guardada for a inicial, o login devolve `trocaSenhaObrigatoria: true`, o JWT leva a claim `trocaSenha` e o `SessaoAtivaFilter` responde **403** `TROCA_SENHA_OBRIGATORIA` a tudo, menos `GET /api/v1/auth/me`, `POST /api/v1/auth/me/senha` e o sinal de atividade da sessão. A nova senha segue a regra de senha forte e não pode repetir a atual.
- Segredos gravados pelas telas (Google Drive) cifrados com `APP_CHAVE_CIFRAGEM` (`CifradorSegredos`).
- Rotas públicas: `/api/v1/auth/{login,refresh,logout}`, `/api/v1/backup/google-drive/callback` e `/actuator/health/**`; o resto de `/api/**` exige JWT e qualquer outra rota é negada.

---

## Rotas da API

| Prefixo | Controller | Função |
|---|---|---|
| `/api/v1/auth` | `AuthController` | login, refresh, logout, `me` (dados e troca de senha), `senha/esqueci` e `senha/redefinir` (públicos) |
| `/api/v1/conta-email` | `ContaEmailController` | conta de envio (ADMIN): consultar, trocar/remover a senha de app, testar, enviar e-mail de teste |
| `/api/v1/usuarios` | `UsuarioController` | usuários do painel (CRUD, desbloquear) |
| `/api/v1/sessao` | `SessaoController` | sessões do usuário, atividade, política de sessão |
| `/api/v1/visitantes` | `VisitanteController` | resumo, sessões, acessos, falhas, encerrar sessões, bloqueio |
| `/api/v1/auditoria` | `AuditoriaController` | consulta da auditoria |
| `/api/v1/backup` | `BackupController` | arquivos, executar, restaurar (upload ou do servidor), jobs, agendamento |
| `/api/v1/backup/google-drive` | `GoogleDriveController` | configuração, autorizar, callback, desconectar, testar |
| `/api/v1/chirpstack/resumo` | `ResumoController` | painel resumo |
| `/api/v1/chirpstack/tenants` | `TenantController` | CRUD, usuários do tenant, `by-devaddr-prefix-overlap` |
| `/api/v1/chirpstack/users` | `UserController` | usuários do ChirpStack (CRUD, senha) |
| `/api/v1/chirpstack/applications` | `ApplicationController` | CRUD, `device-profiles`, `device-tags`, integrações `/{id}/integrations/{tipo}`, certificado MQTT |
| `/api/v1/chirpstack/device-profiles` | `DeviceProfileController` | CRUD, `adr-algorithms`, `vendors`, `devices`, `por-perfil-fabricante` |
| `/api/v1/chirpstack/device-profile-templates` | `DeviceProfileTemplateController` | CRUD |
| `/api/v1/chirpstack/devices` | `DeviceController` | CRUD por `devEui`, chaves, ativação, fila, `dev-nonces`, métricas |
| `/api/v1/chirpstack/gateways` | `GatewayController` | CRUD, métricas, `duty-cycle-metrics`, `generate-certificate` |
| `/api/v1/chirpstack/gateways/relay-gateways` | `RelayGatewayController` | relay gateways por `{tenantId}/{relayId}` |
| `/api/v1/chirpstack/relays` | `RelayController` | relays e dispositivos do relay |
| `/api/v1/chirpstack/multicast-groups` | `MulticastGroupController` | CRUD, dispositivos, gateways, fila |
| `/api/v1/painel` | `PainelController` | monitoramento: indicadores, últimas leituras e situação do MQTT (`/mqtt`) |
| `/api/v1/dispositivos` | `DispositivoController` | dispositivos monitorados (MQTT): lista, detalhe, editar (descrição, ativo), excluir (ADMIN), `/{id}/leituras`, `/{id}/eventos`, `/{id}/series` |
| `/api/v1/regras-alerta` | `RegraAlertaController` | CRUD das regras de alerta (escrita: ADMIN e OPERADOR) |
| `/api/v1/alertas` | `AlertaController` | lista por situação e dispositivo, `/{id}/reconhecer` |

A chamada ao ChirpStack (`app.chirpstack.*`): `CHIRPSTACK_BASE_URL`, token `CHIRPSTACK_API_TOKEN`, timeouts 5s/30s, página máxima 1000.

**Monitoramento (MQTT):** `MqttConsumidor` assina `application/+/device/+/event/+`
no broker do ChirpStack v4 (`app.mqtt.*`: `MQTT_HABILITADO`, `MQTT_URL`, `MQTT_USERNAME`/`MQTT_PASSWORD`,
`MQTT_CA_FILE`). Cada `up` vira uma leitura (idempotente pelo `deduplicationId`), os demais eventos vão para
o histórico do dispositivo e o `AlertaService` avalia as regras VALOR e SEM_COMUNICACAO.

---

## Tarefas agendadas

| Classe / método | Quando | Função |
|---|---|---|
| `BackupAgendamentoJob` | a cada minuto (s=5) | dispara os backups da grade agendada (fuso `APP_TIMEZONE`) |
| `BackupService` | de hora em hora (min 17) | trata jobs órfãos e limpa o histórico (`app.backup.historico-dias=180`) |
| `SessaoService` | a cada 60 s | fecha sessões vencidas ou ociosas |
| `SessaoService` | 03:40 | apaga histórico de sessões além de `APP_SESSAO_RETENCAO_DIAS` (365) |
| `AuthService` | 03:30 | remove refresh tokens vencidos |
| `LoginRateLimiter` | a cada 10 min | limpa contadores de tentativas de login |
| `MqttConsumidor` | a cada 30 s | nova tentativa de conexão quando a primeira falhou (depois o Paho reconecta sozinho) |
| `AlertaService` | a cada minuto | regras SEM_COMUNICACAO |
| `RetencaoService` | 03:50 | apaga leituras e eventos além de `APP_RETENCAO_LEITURAS_DIAS` (180) |

---

## Banco e migrações

- Banco `chirpstack_manager_database`, role `chirpstack_manager_role`, schema `public`.
- **Quem cria as tabelas é o Flyway da API no boot** (V01–V16: usuário, refresh token, auditoria, política de sessão, sessão, conta de e-mail, backup job, agendamento, Google Drive, token de redefinição de senha, dispositivos/leituras/eventos, regras/alertas do monitoramento, equipamentos e periféricos, comandos, cenas e agendamentos, automações). `spring.flyway.out-of-order=true` aplica uma versão pendente mesmo que já exista outra de número maior aplicada. O Job 07 só confere/cria role e database e alinha a senha do role ao Secret; com `REBUILD_SCHEMA="true"` apaga e recria o schema `public` (valor atual `"false"`).
- Backup/restore da tela: `MotorPostgres` roda `pg_dump`/`pg_restore`/`psql` pela rede com o próprio role (a imagem traz `postgresql17-client`), sem `kubectl exec`; `MotorH2` atende o perfil development. Arquivos em `APP_BACKUP_DIR` com prefixo `chirpstack-manager_database`.
- Senhas no Secret `api-chirpstack-manager-secret` (`POSTGRESQL_PASSWORD`, `POSTGRES_SUPERUSER_PASSWORD`).

---

## Build e imagem

```bash
mvn clean package -DskipTests          # target/api-chirpstack-manager-backend.jar
```

```bash
docker buildx build --platform linux/arm64 \
  -t <OCI_REGISTRY_URL>/<NAMESPACE>/api-chirpstack-manager-backend_platform_linux-arm64:<versao> --push .
```

`Dockerfile` multi-stage (`maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre-alpine`): compila com `-Pproduction` (repete o download do Maven Central em 500/502/503/504 e guarda o `.m2` em cache do BuildKit), instala `postgresql17-client` e `tzdata`, roda como UID 10001, cria `/var/backups/chirpstack-manager`, expõe 8081–8084, define `SPRING_PROFILES_ACTIVE`, `APP_BACKUP_DIR` e `JAVA_OPTS` (`MaxRAMPercentage=75`, `ExitOnOutOfMemoryError`) e tem `HEALTHCHECK` em `:8084/actuator/health/liveness`.

---

## Manifestos Kubernetes

Pasta `kubernetes/`, namespace `chirpstack-manager` (compartilhado com o frontend).

| Arquivo | Recurso |
|---|---|
| `01.create__Namespace.yaml` | Namespace |
| `02.api-chirpstack-manager__Secret.yaml` | **só modelo** comentado do Secret `api-chirpstack-manager-secret` (não aplicar) |
| `03.api-chirpstack-manager-postgresql__StorageClass__PersistentVolume__PersistentVolumeClaim.yaml` | dados do Postgres (OCI FSS, 2Gi) |
| `04.api-chirpstack-manager-postgresql__ConfigMap.yaml` | init do volume (cria role e database) |
| `05.api-chirpstack-manager-postgresql__Deployment.yaml` | PostgreSQL 17.5 |
| `06.api-chirpstack-manager-postgresql__Service.yaml` | Service 5449 → 5432 |
| `07.api-chirpstack-manager-migration__Job.yaml` | ConfigMap + Job `api-chirpstack-manager-db-migration` |
| `08.api-chirpstack-manager-backend__StorageClass__PersistentVolume__PersistentVolumeClaim.yaml` | volume de backups (2Gi, montado em `/var/backups/chirpstack-manager`) |
| `10.api-chirpstack-manager-backend__Deployment.yaml` | API (1 réplica, `Recreate`, UID 10001, root FS somente leitura, probes na 8084) — **ArgoCD** |
| `11.api-chirpstack-manager-backend__Service.yaml` | Service 8083 (ClusterIP) |
| `kustomization.yaml` | só o 10 + `newTag` da imagem |

Não há 09 (RBAC do backup) nem 12–14 (cert-manager/Ingress): a API é publicada pelo NGINX do frontend (`/api` → `api-chirpstack-manager-backend-service:8083`). Comandos avulsos: [`kubernetes/00.useful_commands.txt`](./kubernetes/00.useful_commands.txt).

---

## Scripts úteis

Pasta `kubernetes/00.UsefulScripts/`:

| Script | Função |
|---|---|
| `00.create_secret_api-chirpstack-manager_script.sh` | cria o Secret restaurando o backup do 02.destroy quando ele não existe (idempotente) |
| `01.deploy_full_api-chirpstack-manager-backend_script.sh` | deploy completo na ordem (chama 00, 06 e 07; corrige vínculo PV ↔ PVC) |
| `02.destroy_full_api-chirpstack-manager-backend_script.sh` | destroy em ordem inversa; dados preservados (Retain) e Secret salvo antes |
| `03.back-up_full_api-chirpstack-manager_script.sh` / `04.restore_full_api-chirpstack-manager_script.sh` | backup/restauração manuais direto no pod do Postgres (formato `.backup`) |
| `05.carga_geral_api-chirpstack-manager_script.sh` | `--ambiente=production\|staging\|development`: aplica `database/carga/C*.sql` numa transação (idempotente). Preenche os marcadores do C01 (senha de app do SMTP, Client ID e Client Secret do Google) assim: a senha SMTP só pela variável `SMTP_SENHA_APP`; o Client ID e o Client Secret por `GOOGLE_DRIVE_CLIENT_ID`/`GOOGLE_DRIVE_CLIENT_SECRET` ou, na falta deles, pelo JSON da anotação local `NOTAS_DIR/21_google_drive_backup.txt`. Sem algum dos três, o C01 é pulado com aviso. Nunca imprime os valores. Grava sementes `v0:` que a API recifra (`v1:`) na primeira subida. O refresh token do Drive não vem da carga: falta um clique em **Conectar conta Google** |
| `06.criar_secret_senha_banco_api-chirpstack-manager_script.sh` | `POSTGRESQL_PASSWORD` e `POSTGRES_SUPERUSER_PASSWORD` no Secret |
| `07.gerar_segredos_aplicacao_api-chirpstack-manager_script.sh` | `APP_JWT_SECRET`, `APP_ADMIN_PASSWORD` (senha inicial `chirpstack` se ausente), `APP_CHAVE_CIFRAGEM`, `CHIRPSTACK_API_TOKEN`, `GOOGLE_DRIVE_*` (`--conferir`) |

---

## Testes

```bash
mvn verify
```

`ChirpStackManagerApplicationTests` sobe o contexto com H2 em memória, valida as migrações (Flyway + `ddl-auto=validate`) e a criação do primeiro administrador. `TrocaSenhaInicialTests` faz o primeiro acesso com `admin@chirpstack.com.br` / `chirpstack` e confere o bloqueio até a troca da senha. `RecuperacaoSenhaTests` confere que o pedido de redefinição não revela se o e-mail existe e que token inválido não redefine.
