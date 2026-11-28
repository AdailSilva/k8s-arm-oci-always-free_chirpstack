# ChirpStack Manager

Painel web para administrar o **ChirpStack v4** pela REST API e **monitorar os dispositivos LoRaWAN** pelo MQTT (leituras, estado e alertas), com login próprio, controle de acesso por perfil, auditoria, **controle e monitoramento de visitantes**, **controle de logoff** e **backup/restauração com agendamento e envio ao Google Drive, a um servidor SFTP e a uma pasta do computador**.

| Camada | Tecnologia |
|---|---|
| Backend (BFF) | Spring Boot **4.1.1** · Java 21 · Spring Security 7 (JWT HS256) · Spring Data JPA · Flyway · PostgreSQL (staging/production) / H2 (development) |
| Frontend | Angular 21 · PrimeNG 21 · tema **Avalon** · Tailwind · Chart.js |
| Integração | REST API do ChirpStack (**chirpstack-rest-api v4.19.0**, compatível com o ChirpStack 4.19.x) · MQTT do ChirpStack v4 (Eclipse Paho, TLS na 8883) |
| Deploy | Docker multi-arquitetura (amd64/arm64) · Kubernetes (Ingress NGINX + cert-manager) |

---

## Arquitetura

```
Navegador ──HTTPS──> Ingress ──> app-chirpstack-manager-frontend-deployment (NGINX + Angular)
App mobile ─HTTPS──┘   (mesmo host: o NGINX publica /api/v1/** do backend)
                                     │  /api/*
                                     ▼
                           api-chirpstack-manager-backend-deployment (Spring Boot) ──> PostgreSQL
                                     │  Grpc-Metadata-Authorization: Bearer <API key>
                                     ▼
                     https://chirpstack-v4-rest-api.adailsilva.com.br  (ChirpStack REST v4.19)

api-chirpstack-manager-backend-deployment ──MQTT/TLS 8883──> chirpstack-v4-mosquitto-service (application/+/device/+/event/+)
```

O navegador **nunca vê a API key do ChirpStack**: ela fica só no backend (Secret do Kubernetes). O frontend fala apenas com `/api/v1/**` do backend, que autentica o usuário, confere o perfil e só então chama o ChirpStack.

---

## Credenciais iniciais

Com o banco vazio (primeira instalação, em qualquer perfil), a API cria o administrador:

| E-mail | Senha inicial | Perfil |
|---|---|---|
| `admin@chirpstack.com.br` | `chirpstack` | ADMIN |

- **Troca obrigatória no primeiro acesso.** Enquanto a senha for a inicial, o login responde `"trocaSenhaObrigatoria": true` e a API recusa todo o resto com **403** `TROCA_SENHA_OBRIGATORIA` (só ficam liberados `GET /api/v1/auth/me`, `POST /api/v1/auth/me/senha` e o sinal de atividade da sessão). O painel abre direto a tela própria **Defina sua senha** (`/auth/trocar-senha`, mesmo padrão dos demais sistemas: senha atual, nova senha, confirmação e a checklist da regra de senha) e não deixa abrir outra tela até a troca; o botão **Sair** encerra a sessão.
- A **nova** senha segue a regra de senha forte (8 a 72 caracteres, com letras e números) e não pode repetir a atual. Depois da troca, as sessões são encerradas e o acesso é feito com a nova senha.
- A marca de "troca pendente" é a própria senha: enquanto o hash guardado corresponder à senha inicial, a troca é exigida. Não há coluna extra no banco.
- O administrador só é criado quando a tabela de usuários está vazia. Um administrador já existente **não** tem a senha alterada.
- `APP_ADMIN_EMAIL`/`APP_ADMIN_PASSWORD` sobrescrevem as credenciais iniciais (o Secret do Kubernetes recebe `chirpstack` pelo script 07 quando a chave ainda não existe).
- **Esqueci a senha:** o login tem o link *Esqueci a senha*. O pedido responde sempre do mesmo jeito (não revela se o e-mail existe); havendo usuário ativo, chega um link de uso único, válido por tempo limitado, para `/auth/nova-senha`. Ao redefinir, as sessões do usuário são encerradas.
- **Convite por e-mail.** Ao cadastrar um usuário, o ADMIN pode deixar a senha em branco e marcar *Enviar convite por e-mail* (padrão). O usuário recebe um link de uso único (72 h) para `/auth/nova-senha?convite=1`, define a própria senha e já entra com o e-mail confirmado. Enquanto não aceitar, aparece a etiqueta **Convite pendente** e o botão **Reenviar convite**; "Esqueci a senha" para quem ainda tem convite pendente reenvia o convite.
- **Confirmação de e-mail.** Quando o ADMIN define a senha no cadastro, ou altera o e-mail de outro usuário, segue um link de confirmação (48 h) para `/auth/confirmar-email`. Até confirmar, o login responde **403** `EMAIL_NAO_CONFIRMADO` e a tela de login oferece **Reenviar confirmação** (`POST /api/v1/auth/email/reenviar`, resposta sempre igual). Mudar o e-mail de outro usuário encerra as sessões dele. O e-mail do próprio ADMIN continua confirmado ao ser alterado, para não trancar o único administrador. Usuários já existentes ficam como confirmados (migração V18).
- **Cadastro completo e Minha Conta.** Foto (reduzida no navegador para 512 px), cargo, telefone, CEP com preenchimento automático do endereço (`GET /api/v1/localidades/cep/{cep}`, via ViaCEP), tipo de logradouro, número, complemento, bairro, cidade, UF e observações. O diálogo de usuários tem as abas *Dados*, *Contato e endereço* e *Acesso*; a lista mostra foto, cargo e telefone. Em **Minha Conta** cada usuário edita os próprios dados e a foto, que passa a aparecer no topo da tela.
- **Horário de acesso.** Em *Sistema → Horário de acesso* o ADMIN define, para cada dia da semana, *Livre*, *Horário* (janela de início e fim) ou *Sem acesso*. Vale para Operador e Visualizador; administradores entram a qualquer hora. Fora do horário, o login responde **403** `FORA_HORARIO` dizendo o horário do dia e quando o acesso volta. Quem está conectado vê um aviso com contagem regressiva 5 minutos antes do fim e é desconectado no horário, com o motivo na tela de login (o navegador usa o relógio do servidor). O servidor também encerra essas sessões (a cada 30 s e na renovação do token) com o motivo `FORA_HORARIO`, que aparece em Visitantes como "Fim do horário de acesso". Ao salvar, a opção *Aplicar agora* desconecta na hora quem ficou fora da nova grade. Sem nenhum dia restrito, não há restrição (migração V19, tabela `tb_horario_acesso`).
- **Backup por SFTP e para uma pasta local.** Além do servidor e do Google Drive, o backup pode ir a um computador, NAS ou servidor da rede do cliente por SFTP (aba *SFTP* em Backup & Restauração) e ser gravado direto numa pasta escolhida deste computador. O destino SFTP precisa ser acessível a partir do cluster (IP público ou nome com a porta liberada, ou VPN). Se houver NetworkPolicy de saída no namespace da API, libere a porta do SFTP (22, por padrão). Migração V20: tabela `tb_backup_sftp` e colunas do SFTP em `tb_backup_job` e `tb_backup_agendamento`. Dependência nova: `com.github.mwiede:jsch` 0.2.20.
- **"Deseja realmente sair?"** Sair de uma tela com formulário alterado e não salvo pergunta antes: troca de tela, *Sair*, *Bloquear tela* e *Sair agora* do aviso de sessão. Fechar um diálogo de cadastro alterado (X, Esc, clique fora ou *Cancelar*) pergunta "Fechar sem salvar?". Recarregar ou fechar a aba usa a pergunta do próprio navegador. O logoff automático (inatividade, horário, administrador) não pergunta: descarta.
- **Preferências no servidor.** Tema, cor primária, superfície, modo escuro e modo do menu ficam no servidor (`GET/PUT /api/v1/conta/preferencias`, migração V21, tabela `tb_preferencia_usuario`) e valem em qualquer navegador. Ao sair, o navegador volta à aparência padrão.
- **Tela bloqueada.** *Bloquear tela* no menu do usuário esconde o sistema sem encerrar a sessão, em todas as abas. Para voltar, a senha é conferida no servidor (`POST /api/v1/conta/desbloquear`). Cinco erros seguidos encerram a sessão (motivo `DESBLOQUEIO_FALHOU`).
- **Suporte e Termos de uso.** Novo grupo *Conta* no menu (Meu perfil, Suporte, Termos de uso), também no menu do usuário. O chamado de suporte (`POST /api/v1/conta/suporte`) vai por e-mail pela conta de e-mail do sistema, com o e-mail do usuário como "responder para". O destino padrão pode ser trocado por `APP_SUPORTE_DESTINO`. Limite de 5 chamados a cada 30 minutos por usuário.
- **Notificações.** Sino no topo com os alertas abertos do monitoramento (contador vermelho se houver crítico), atualizado a cada minuto.
- **Página de erro.** `/auth/oops`: quando uma nova versão do painel é publicada com a página aberta, abrir uma tela leva a "Nova versão disponível", com *Recarregar*, em vez de uma tela em branco.

## Segurança

| Item | Como foi feito |
|---|---|
| API key do ChirpStack | Somente no backend (`CHIRPSTACK_API_TOKEN`). Nenhuma rota repassa o cabeçalho para o navegador. |
| Sem proxy aberto | Cada uma das 77 operações do ChirpStack tem um endpoint explícito. Não existe repasse genérico de caminho. |
| Validação de IDs | UUID, EUI-64 (DevEUI/Gateway ID), Relay ID e tipo de integração são validados por regex antes de montar a URL. |
| Login | E-mail e senha com BCrypt. A mensagem de erro é a mesma para e-mail inexistente e senha errada, e o tempo de resposta também é igual. |
| Força bruta | A conta é bloqueada por 15 min após 5 falhas seguidas. Há também um limite de 20 tentativas por IP a cada 5 min, e o Ingress limita requisições por segundo. |
| Sessão | **Access token** JWT de 15 min, guardado só em memória no navegador. **Refresh token** de 256 bits num cookie `HttpOnly; Secure; SameSite=Strict` restrito a `/api/v1/auth`. No banco fica só o hash SHA-256 do refresh token. |
| Rotação | Cada renovação gera um refresh token novo. Se um token já trocado for reutilizado (sinal de roubo), todas as sessões do usuário são encerradas. |
| Troca de senha / desativação | Todas as sessões do usuário são revogadas. |
| Redefinição por e-mail | `POST /api/v1/auth/senha/esqueci` e `/senha/redefinir` (públicos). Resposta igual para e-mail existente ou não; no banco fica só o hash do token (`tb_token_redefinicao`), de uso único e com validade curta. |
| Conta de e-mail | Senha de app do SMTP gravada cifrada (AES-256-GCM, `tb_configuracao_email`), trocada pela tela **Sistema → Conta de e-mail** (ADMIN), testada antes de gravar e nunca devolvida ao navegador. |
| Senha inicial | Primeiro acesso com `admin@chirpstack.com.br` / `chirpstack` exige a troca da senha antes de liberar o sistema (veja **Credenciais iniciais**). |
| Sessões vivas | Cada login abre uma sessão (`tb_sessao`) cujo id vai no JWT (`sid`). Toda requisição confere se ela continua aberta: encerrar pelo ADMIN, bloquear, trocar a senha ou restaurar um backup derruba a sessão **na hora**, sem esperar o token vencer. A resposta vem com `X-Sessao-Status: ENCERRADA` e o navegador vai direto ao login mostrando o motivo. |
| Logoff | Inatividade, duração máxima e sessões simultâneas definidos pelo ADMIN. Fechar o navegador sempre encerra a sessão (o cookie de renovação é de sessão, sem Max-Age), como nos demais sistemas. O navegador avisa antes e o servidor confere as mesmas regras ao renovar o token. |
| Segredos do Google | Refresh token da conta Google e Client Secret gravados cifrados (AES-256-GCM, chave em `APP_CHAVE_CIFRAGEM`, fora do banco). Escopo `drive.file`: o sistema só vê o que ele mesmo criou no Drive. |
| Perfis | `ADMIN`, `OPERADOR` e `VISUALIZADOR`, aplicados com `@PreAuthorize` em **cada** operação no backend. O frontend apenas esconde o que o perfil não pode usar. |
| Segredos de rede | Chaves OTAA, sessão ABP, configuração de integrações, chaves multicast e certificados: somente ADMIN e OPERADOR. |
| Auditoria | Logins (sucesso e falha), logout e **toda escrita** no ChirpStack e nos usuários do painel. Corpos não são gravados, pois podem conter chaves. |
| IP real | `forward-headers-strategy: native` (RemoteIpValve): o `X-Forwarded-For` só é aceito quando vem de proxy interno, então o cliente não consegue forjar o IP. |
| Cabeçalhos | API: HSTS, `nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`, CSP `default-src 'none'`. Web: CSP restritiva sem script inline. |
| Erros do ChirpStack | Os códigos gRPC viram mensagens em português. Se o ChirpStack recusar a credencial, a resposta é **502**, não 401, para o usuário não ser deslogado por engano. |
| Containers | Usuário sem root, `readOnlyRootFilesystem` e `capabilities: drop ALL`. |

### Perfis de acesso

| Recurso | ADMIN | OPERADOR | VISUALIZADOR |
|---|:-:|:-:|:-:|
| Painel, listas e métricas | ✅ | ✅ | ✅ |
| Aplicações, dispositivos, gateways, perfis, multicast, relays, integrações (criar/alterar/excluir) | ✅ | ✅ | — |
| Chaves OTAA, ativação ABP, segredos de integração/multicast, certificados TLS | ✅ | ✅ | — |
| Templates de perfil (escrita) | ✅ | — | — |
| Perfis globais e catálogo de dispositivos (alterar/excluir) · verificação de prefixos de DevAddr | ✅ | — | — |
| Tenants, usuários do ChirpStack, usuários do tenant | ✅ | — | — |
| Usuários do painel e auditoria | ✅ | — | — |
| Visitantes, controle de sessão, backup, Google Drive e SFTP | ✅ | — | — |
| Monitoramento: painel, dispositivos monitorados, leituras, eventos e alertas (ver) | ✅ | ✅ | ✅ |
| Monitoramento: editar dispositivo monitorado, regras de alerta, reconhecer alerta | ✅ | ✅ | — |
| Monitoramento: excluir dispositivo monitorado | ✅ | — | — |

---

## Funcionalidades (todas as 132 operações / 86 rotas da REST API v4.19)

| Tela | Operações do ChirpStack |
|---|---|
| **Painel** | Resumo do tenant: gateways online/offline, dispositivos ativos em 24 h, dispositivos por aplicação |
| **Tenants** | Listar, criar, editar, excluir · **prefixos de DevAddr** com verificação de sobreposição entre tenants (4.19) · usuários do tenant: listar, adicionar, editar permissões, remover |
| **Usuários do ChirpStack** | Listar, criar (com vínculo a tenants), editar, excluir, alterar senha |
| **Aplicações** | Listar, criar, editar, excluir · perfis e tags em uso · *measurement keys* |
| ↳ **Integrações** | HTTP, InfluxDB (1/2/3), ThingsBoard, myDevices, GCP Pub/Sub, AWS SNS, Azure Service Bus, Pilot Things, IFTTT, Blynk (criar, editar, remover) · certificado de cliente MQTT |
| ↳ **Dispositivos** | Listar (busca, ordenação e **filtro por tags**), criar, editar, excluir · métricas de enlace (pacotes, RSSI, SNR, frequência, DR, erros) e medições do codec |
| ↳ ↳ **Chaves OTAA** | Consultar, cadastrar, alterar, excluir; limpar DevNonces. Trata AppKey em LoRaWAN 1.0.x × NwkKey/AppKey em 1.1 |
| ↳ ↳ **Ativação** | Ver a sessão atual, ativar/reativar por ABP, gerar DevAddr, desativar, próximo FCntDown |
| ↳ ↳ **Fila de downlink** | Listar, enfileirar (hex, base64, texto ou JSON pelo codec), limpar |
| ↳ **Relays** (4.19) | Relays TS011 da aplicação · dispositivos atendidos por cada relay: listar, adicionar, remover |
| ↳ **Grupos multicast** | Listar (por aplicação, por tenant ou pelos grupos de um dispositivo), criar, editar, excluir · adicionar/remover dispositivos e gateways · fila (listar, enfileirar, limpar) |
| **Gateways** | Listar (estado e ordenação), cadastrar, editar (com localização e **prioridade de downlink**), excluir · métricas RX/TX por frequência, DR e status · duty-cycle · certificado TLS de cliente |
| **Relay gateways** | Listar, editar, excluir |
| **Perfis de dispositivo** | Listar (do tenant + globais, só do tenant, só globais ou por modelo do catálogo), criar, editar, excluir · **data rates de uplink suportados** e **classe B só downlink** (4.19). Abas: geral, join OTAA/ABP, classe B, classe C, codec (com exemplo JS), medições, relay (TS011), camada de aplicação (TS003/004/005) e tags · **importar de template** · algoritmos ADR |
| **Templates de perfil** | Listar, criar, editar, excluir |
| **Catálogo de dispositivos** (4.19) | Fabricantes (Vendor ID, OUIs, metadados) e modelos importados com `chirpstack import-device-profiles` · perfis globais de cada modelo · excluir (ADMIN) · busca de perfil pelo ProfileID do TR0005 (`/device-profiles/por-perfil-fabricante/{vendorId}/{vendorProfileId}`) |
| **Grupos multicast** | Todos os grupos do tenant, com a aplicação de cada um |
| **Sistema** | Usuários do painel (CRUD, desbloqueio), conta de e-mail (senha de app, testar, enviar e-mail de teste), auditoria com filtros, meu perfil e troca de senha |

### Monitoramento (MQTT)

| Tela | O que faz |
|---|---|
| **Painel de monitoramento** | Dispositivos, on-line, sem comunicação, leituras das últimas 24 h, alertas abertos/reconhecidos, situação da conexão MQTT e últimas leituras. |
| **Dispositivos monitorados** | Nascem na primeira mensagem do MQTT (nome, aplicação, perfil, tenant e tags do `deviceInfo`). Último estado (contato, RSSI/SNR, FCnt, bateria, último objeto decodificado), descrição e ativo/desativado. Detalhe com gráficos de cada campo numérico, RSSI e SNR (6 h a 31 dias), leituras e eventos (`join`, `status`, `ack`, `txack`, `log`, `location`, `integration`). |
| **Alertas** | Regras **VALOR** (campo do objeto, `rssi`, `snr` ou `bateria` comparado a um limite) e **SEM_COMUNICACAO** (minutos sem mensagem), para um dispositivo ou todos, com aviso opcional por e-mail. O alerta abre, pode ser reconhecido e se resolve sozinho quando o valor volta ao normal. |

Leituras e eventos com mais de `APP_RETENCAO_LEITURAS_DIAS` (180) dias são apagados às 03:50. A carga geral grava as regras padrão (sem comunicação, bateria baixa, sinal fraco).

### Comandos, relés e medidores de energia

Cada dispositivo pode ganhar um **equipamento**: o modelo do firmware e os periféricos ligados a ele. Começa com **relés** (atuadores) e o medidor **PZEM-004T v3** (sensor). Um tipo novo de sensor ou atuador entra como um `TipoPeriferico`, seus comandos em `TipoComando` e a codificação no driver do modelo (`service/atuacao/modelo`).

| Modelo | Como o servidor fala com o firmware | Prova de execução |
|---|---|---|
| `ADAILSILVA_V1` | Protocolo binário próprio ([docs/protocolo/PROTOCOLO_ADAILSILVA_V1.md](docs/protocolo/PROTOCOLO_ADAILSILVA_V1.md)): comando na porta 10, resposta na 11 com o mesmo `seq`, telemetria TLV na 20. | Resposta com o mesmo `seq` (o firmware não repete a ação se o comando chegar de novo). |
| `RADIOENGE_ORIGINAL` | Firmware Radioenge sem alteração: relé = GPIO via `AT+GPIOC`/`AT+WPIN` (porta 103); PZEM = Modbus pela serial transparente (porta 4; no módulo `AT+GPIOC=0:3:0` e `AT+BAUDRATE=9600`); classe, intervalo e confirmação pela porta 102. | `AT OK`, resposta Modbus válida ou, nos comandos sem resposta, a transmissão. |
| `ESP32_ORIGINAL` | Só o reset remoto (porta 101). | Transmissão. |

O servidor decodifica os uplinks pelo modelo (não depende de codec no ChirpStack): o objeto vira leitura, série (`pzem.0.potencia`, `reles.estados.0`...) e campo de regra de alerta.

| Tela | O que faz |
|---|---|
| **Controle** (detalhe do dispositivo) | Modelo, periféricos, relés com estado **desejado × reportado**, ligar/desligar/alternar/pulso, qualquer comando suportado com política própria de tentativas e o último STATUS. Relé marcado como **crítico** pede confirmação dupla. |
| **Energia** (detalhe do dispositivo) | Tensão, corrente, potência, energia, frequência, fator de potência e alarme de cada medidor; consumo e **custo** no período pela tarifa (R$/kWh); ler agora, zerar energia, limite de potência. |
| **Fila de comandos** | Todos os comandos: `PENDENTE → ENFILEIRADO → TRANSMITIDO → EXECUTADO` ou `FALHOU`/`EXPIRADO`/`CANCELADO`, com cada tentativa, porta, bytes, `seq` e resposta. Cancelar e reenviar. |
| **Cenas** | Vários comandos (em vários dispositivos) em um botão. |
| **Agendamentos** | Comando ou cena em data/hora única ou semanal (hora + dias). |
| **Automações** | Alerta de uma regra abriu (ou foi resolvido) → comando ou cena. Ex.: `pzem.0.potencia > 2000` desliga o relé 0. Intervalo mínimo entre disparos. |

Como a fila funciona:

- Um comando em andamento por dispositivo, na ordem em que foram pedidos; o ciclo roda a cada 15 s.
- Sem prova de execução em "aguardar resposta" (padrão 300 s) depois da transmissão (`txack`), ou se o item some da fila do ChirpStack, a tentativa falha e o comando volta para a fila em *intervalo × recuo^(n-1)*, respeitando a **janela de horário** e os **dias da semana**. Esgotadas as tentativas (`FALHOU`) ou vencida a validade (`EXPIRADO`), abre o alerta **Comando não executado** (regra criada sozinha; nela se liga o e-mail). Um comando executado depois resolve o alerta.
- Em Classe A o downlink só desce depois do próximo uplink do equipamento.
- **Reconciliação**: se o estado reportado de um relé divergir do desejado (reinício, acionamento local), o servidor manda o relé de volta ao desejado (no máximo a cada 10 minutos, só sem outro comando pendente). Desligável por equipamento.
- Padrões da política: `APP_COMANDOS_MAX_TENTATIVAS` (5), `APP_COMANDOS_INTERVALO_S` (300), `APP_COMANDOS_FATOR_RECUO` (1.5), `APP_COMANDOS_AGUARDAR_S` (300), `APP_COMANDOS_VALIDADE_MIN` (1440). Comandos encerrados seguem a mesma retenção das leituras.

### Visitantes, logoff e backup

| Tela | O que faz |
|---|---|
| **Visitantes** | Indicadores (on-line agora, acessos e visitantes únicos de hoje e da semana, recusas de login, bloqueados), acessos por dia, horário dos acessos, dispositivos e como as sessões terminaram. Abas: **On-line agora** (atualiza a cada 10 s; situação on-line/ausente, dispositivo, IP, tempo sem interação), **Por usuário** (frequência, último acesso), **Histórico de acessos** (filtros por usuário, situação, período e texto) e **Tentativas recusadas**. Ações: encerrar uma sessão, encerrar as sessões de um usuário, **desconectar todos** (menos você), **bloquear acesso** temporário (até data/hora, com motivo exibido no login) ou permanente (desativa) e desbloquear. |
| **Controle de sessão** | Política de logoff: tempo sem uso (com aviso e contagem regressiva), duração máxima desde o login, limite de sessões simultâneas por usuário (fechar o navegador sempre exige novo login). Opção de aplicar na hora encerrando as sessões abertas. |
| **Horário de acesso** | Grade semanal (Livre / Horário / Sem acesso) para Operador e Visualizador, botão para copiar segunda-feira aos dias úteis e opção de aplicar na hora. Aviso de 5 minutos antes do fim, com contagem regressiva, e logoff no fim do horário. |
| **Topo da tela** | Indicador com o tempo até o logoff por inatividade; amarelo no aviso e vermelho no último minuto. Diálogo "Sua sessão vai expirar" com *Continuar conectado* / *Sair agora*; aviso do tempo máximo. A inatividade é contada em todas as abas abertas e o logoff em uma aba vale para todas. |
| **Backup & Restauração** | **Backup**: gerar `.backup` (pg_dump custom) e/ou `.sql`, enviar opcionalmente ao Drive, verificação do ambiente (utilitários, banco, volume, Drive), lista de arquivos com baixar / restaurar / excluir. **Restaurar**: upload com confirmação digitada, backup de segurança automático antes, transação única (se falhar, nada muda), migrações aplicadas depois e todos desconectados ao final. **Agendamento**: grade semanal com vários horários por dia, formatos, destinos (servidor e/ou Drive), retenção por dias com mínimo garantido, *Executar agora*, próximas e últimas execuções. **Google Drive**: credenciais OAuth, conectar/reconectar/desconectar a conta, testar envio, pasta (com níveis), retenção no Drive. **SFTP**: computador, NAS ou servidor da rede do cliente (senha ou chave privada OpenSSH/PEM com frase-senha), pasta remota criada se faltar, *Testar conexão* com gravação de um arquivo de teste, confirmação da impressão digital da chave do servidor (conferida antes da senha sair; chave diferente suspende os envios até *Redefinir chave*), envio como `.parcial` renomeado no fim e retenção própria; destino do backup pela tela e do agendamento. **Pasta deste computador**: escolher uma pasta (Chrome, Edge, Opera) para os downloads e para gravar ali os backups gerados pela tela; nos outros navegadores, download comum. **Histórico** de todas as execuções, com as colunas Google Drive e SFTP. |

Como o backup funciona:

- `pg_dump`/`pg_restore`/`psql` rodam **dentro do pod da API** (pacote `postgresql17-client` na imagem), direto no serviço do PostgreSQL: sem `kubectl` e sem RBAC extra.
- Arquivos em `/var/backups/chirpstack-manager/{manuais,agendados,seguranca}` (PVC próprio). Nomes validados; nenhum caminho vem do navegador.
- Uma operação por vez no cluster; o progresso fica em `tb_backup_job` (consultável de qualquer réplica). Execuções interrompidas por reinício viram erro.
- O relógio do agendamento roda a cada minuto em todas as réplicas, mas só uma executa cada horário (UPDATE condicional em `ultimo_disparo`). Horários perdidos por até 10 minutos ainda rodam; se já houver outra operação, o horário é registrado como **Ignorado**.
- Envio ao Drive por upload *resumable* em blocos de 8 MiB (retoma após queda de rede). Se o único destino for o Drive e o envio falhar, os arquivos ficam guardados em `agendados/`.
- No perfil `development` (H2) o backup usa `SCRIPT`/`RUNSCRIPT` e só o formato `.sql`.
- O backup cobre o **banco do painel** (usuários, sessões, auditoria, configurações e o monitoramento: dispositivos monitorados, leituras, eventos, regras e alertas). Os dados da rede LoRaWAN ficam no próprio ChirpStack.

---

## Configuração (variáveis de ambiente do backend)

| Variável | Obrigatória | Descrição |
|---|:-:|---|
| `CHIRPSTACK_BASE_URL` | | URL da chirpstack-rest-api (padrão: produção) |
| `CHIRPSTACK_API_TOKEN` | ✅ | API key **de administrador** do ChirpStack |
| `APP_JWT_SECRET` | ✅ production | Segredo HS256 com ≥ 32 caracteres (`openssl rand -base64 48`) |
| `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD` | | Primeiro administrador, criado só se a base estiver vazia. Padrão: `admin@chirpstack.com.br` / `chirpstack`, com troca obrigatória no primeiro acesso |
| `APP_CORS_ALLOWED_ORIGINS` | | Origens permitidas (padrão `http://localhost:4201` em development e `:4202` em staging; só é necessário se o frontend estiver em outro domínio) |
| `APP_COOKIE_SECURE` | | `true` em produção (HTTPS) |
| `APP_ACCESS_TOKEN_TTL` / `APP_REFRESH_TOKEN_TTL` | | Padrão: `15m` / `7d` |
| `SPRING_PROFILES_ACTIVE` | | `development`, `staging` ou `production` (veja **Perfis**) |
| `SERVER_PORT` / `MANAGEMENT_PORT` | | Sobrescrevem a porta do perfil (8081/8082/8083) e a do actuator (8084) |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | staging/production | PostgreSQL (o usuário precisa poder recriar o schema `public` na restauração; o `POSTGRES_USER` da imagem oficial já pode) |
| `APP_CHAVE_CIFRAGEM` | recomendado | Chave AES-256 dos segredos do Google Drive e do SFTP (`openssl rand -base64 32`). Sem ela, deriva do `APP_JWT_SECRET` |
| `APP_TIMEZONE` | | Fuso da grade de backup, dos nomes de arquivo e dos indicadores (padrão `America/Fortaleza`) |
| `APP_BACKUP_DIR` | | Volume dos backups (imagem: `/var/backups/chirpstack-manager`; development: `./data/backups`; staging: `./data-staging/backups`) |
| `APP_BACKUP_MAX_UPLOAD` / `APP_BACKUP_TEMPO_LIMITE` | | Maior arquivo aceito na restauração (`512MB`) e tempo máximo de um dump/restore (`30m`) |
| `GOOGLE_DRIVE_REDIRECT_URI` | Drive | Endereço público do callback (`https://<host>/api/v1/backup/google-drive/callback`); cadastre-o igual no cliente OAuth |
| `GOOGLE_DRIVE_CLIENT_ID` / `GOOGLE_DRIVE_CLIENT_SECRET` | | Opcionais: também podem ser cadastrados na tela (que tem prioridade) |
| `APP_SESSAO_RETENCAO_DIAS` | | Por quanto tempo o histórico de visitantes é mantido (padrão 365) |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` | | Servidor SMTP e conta de envio (padrão `smtp.gmail.com:587`, `codeinserver@gmail.com`). A senha de app fica no banco, cifrada (tela **Conta de e-mail** ou carga geral) |
| `APP_URL_PUBLICA` | | Endereço público do painel, usado nos links de redefinição de senha (production: `https://app-chirpstack-manager.adailsilva.com.br`) |
| `MQTT_HABILITADO` | | Liga a recepção dos eventos (padrão `true` em production, `false` em development/staging) |
| `MQTT_URL` | | Broker do ChirpStack v4 (production: `ssl://chirpstack-v4-mosquitto-service.chirpstack-v4.svc.cluster.local:8883`) |
| `MQTT_USERNAME` / `MQTT_PASSWORD` | monitoramento | Usuário do broker, copiado de `chirpstack-v4-mqtt-credentials` (namespace `chirpstack-v4`) pelo script 07 |
| `MQTT_CA_FILE` | | CA do broker (PEM). No cluster: `/etc/chirpstack-manager/mqtt/ca.crt`, do Secret `api-chirpstack-manager-mqtt-ca` |
| `MQTT_CLIENT_ID` / `MQTT_TOPICO` | | Padrão `chirpstack-manager` e `application/+/device/+/event/+` |
| `APP_RETENCAO_LEITURAS_DIAS` / `APP_SEM_COMUNICACAO_MINUTOS` | | Retenção das leituras (180) e minutos sem mensagem para "sem comunicação" (60) |

**Gerar a API key:** no ChirpStack, entre como administrador e vá em *Network Server → API keys → Add API key*. Copie o token gerado para `CHIRPSTACK_API_TOKEN`; ele só aparece uma vez.

---

## Perfis (development, staging e production)

Cada perfil tem a sua porta, no backend e no frontend, então os três podem rodar ao mesmo tempo na mesma máquina.

| Perfil | Uso | Backend | Actuator | Frontend (`ng serve`) | Banco | Cookie `Secure` |
|---|---|:-:|:-:|:-:|---|:-:|
| `development` | máquina do desenvolvedor | **8081** | 8084 | **4201** | H2 em arquivo (`./data`) | não |
| `staging` | homologação | **8082** | 8084 | **4202** | PostgreSQL local (`chirpstack_manager` do `stack_postgresql_pgAdmin_4`, porta 15435) | não |
| `production` | Kubernetes / Docker | **8083** | 8084 | **4203** | PostgreSQL do cluster | sim |

- **Backend:** em `src/main/resources`:
  - `application.properties`: só escolhe o perfil (`spring.profiles.active=@activatedProperties@`, preenchido pelo perfil Maven), o banner e `application.title`/`application.version`;
  - `application-development.properties`, `application-staging.properties` e `application-production.properties`: a configuração **completa** de cada perfil, em seções (servidor, datasource/Hikari, JPA, Flyway, actuator, ChirpStack, JWT, CORS, admin inicial, sessões, backup, Google Drive, loggers). Ficam em ISO-8859-1 (padrão do `java.util.Properties`), sem acentos;
  - `banners/chirpstack-manager-banner.txt`: banner da inicialização, com versão, perfil ativo e endereço;
  - `db/migration/H2` (development) e `db/migration/PostgreSQL` (staging e production).

  Perfis Maven de mesmo nome (`-Pdevelopment` é o padrão). Em container, `SPRING_PROFILES_ACTIVE` tem prioridade sobre o perfil gravado no jar (a imagem é gerada com o perfil `production`, como nos demais backends do repositório).
- **Actuator:** `/actuator/health/*` sai da porta da aplicação e fica na **8084** em todos os perfis. As probes do Kubernetes usam essa porta, que não entra no Service nem no Ingress. Liveness = só a JVM; readiness = JVM + banco.
- **Frontend:** `environment.<perfil>.ts`, `src/proxy-config.<perfil>.json` (encaminha `/api` para a porta do backend do mesmo perfil) e configurações `development`/`staging`/`production` no `angular.json`. Em desenvolvimento e homologação aparece um selo no topo da tela.

## App mobile (Flutter)

`app_chirpstack_manager_mobile/` — Android e iOS (`br.com.chirpstack.manager`), com a identidade do
sistema (`brand/chirpstack-manager-*`): login no mesmo padrão do web, **Painel**, **Dispositivos**
(com detalhe e últimas leituras), **Gateways**, **Alertas** (reconhecer, com contador de abertos na aba)
e **Conta** (tema, meu perfil, bloquear tela, troca de senha, suporte, Termos de Uso, sair). Cadastros e
configurações completos ficam no painel web.

As funcionalidades genéricas do painel web também valem no app:

- **Horário de acesso:** fora da janela o login é recusado com o motivo; 5 minutos antes do fim aparece o
  aviso com a contagem (relógio do servidor) e, no fim, a sessão é encerrada (`FORA_HORARIO`).
- **E-mail não confirmado:** o login oferece *Reenviar e-mail de confirmação*.
- **Meu perfil:** nome, cargo, telefone e endereço com busca do CEP (a foto aparece no app e é trocada no
  painel web).
- **Tela bloqueada:** *Bloquear tela* em Conta; a senha é conferida no servidor, 5 erros encerram a
  sessão. Fechar e reabrir o app (sessão lembrada) volta bloqueado.
- **"Deseja realmente sair?":** voltar de um formulário alterado (cenas, automações, agendamentos, meu
  perfil, suporte, troca de senha) pergunta antes.
- **Preferências:** o modo claro/escuro escolhido em Conta é o mesmo do painel web (salvo no servidor).
- **Suporte:** chamado por e-mail à equipe, com telefone e WhatsApp opcionais.
- **Erro inesperado:** no lugar da tela cinza do Flutter, a mensagem "Ops! Algo deu errado".

- **Perfis** por `--dart-define=AMBIENTE=development|staging|production` (`lib/core/config/ambiente.dart`):
  development fala com o backend local (`http://10.0.2.2:8081/chirpstack-manager`); staging e production,
  com `https://app-chirpstack-manager.adailsilva.com.br` (o NGINX do frontend encaminha `/api`).
  `--dart-define=API_URL=...` sobrepõe a URL.
- **Sessão:** o refresh token vem no cookie `csm_refresh`; o app o lê do `Set-Cookie`, guarda no cofre
  cifrado (com "Lembre de mim") e o reenvia no cabeçalho `Cookie` para renovar. A cada minuto, com o app
  aberto, envia `POST /api/v1/sessao/atividade` — sem isso a sessão cairia por inatividade.
- **Testes:** `flutter test` cobre a leitura do cookie, a formatação e **todas as telas (inclusive tela
  bloqueada, meu perfil e suporte) em 320×568,
  360×740 e 412×915, temas claro e escuro, texto a 115%** — qualquer campo cortado ou colado falha o teste.
- **Build:** pipeline `.github/workflows/mobile.yml` (runner `flutter` no notebook), APK por ABI + AAB
  assinados com o keystore do ChirpStack (`ANDROID_*` nos secrets do repositório).

```bash
cd app_chirpstack_manager_mobile && flutter run                                   # development
flutter build apk --release --split-per-abi --dart-define=AMBIENTE=production    # como o pipeline
```

## Desenvolvimento local

```bash
# Backend
cd api_chirpstack_manager_backend
export CHIRPSTACK_API_TOKEN='<sua API key>'
# Admin inicial: admin@chirpstack.com.br / chirpstack (troca obrigatória no primeiro acesso)
mvn spring-boot:run                 # development -> http://localhost:8081
mvn spring-boot:run -Pstaging       # staging     -> http://localhost:8082 (suba antes o stack_postgresql_pgAdmin_4)
mvn spring-boot:run -Pproduction    # production  -> http://localhost:8083 (exige DB_URL/DB_PASSWORD e APP_JWT_SECRET)

# Frontend
cd app_chirpstack_manager_frontend
npm install
npm start                           # development -> http://localhost:4201 (proxy /api -> :8081)
npm run start:staging               # staging     -> http://localhost:4202 (proxy /api -> :8082)
npm run start:production            # production  -> http://localhost:4203 (proxy /api -> :8083)
npm run build:staging               # build de um perfil (development | staging | production)
```

Testes do backend: `mvn verify` (sobe o contexto com H2, valida as migrations e a criação do admin).

## Docker Compose

```bash
cp .env.example .env           # preencha os segredos
cd stack_chirpstack-manager && docker compose --env-file ../.env up -d --build   # http://localhost:8080 (perfil production)
# Só PostgreSQL + pgAdmin locais (perfil staging): cd stack_postgresql_pgAdmin_4 && docker compose up -d   # 15435 / 16545
```

## Kubernetes (cluster OCI ARM64)

```bash
# Imagens: publicadas pelo pipeline (jobs api_chirpstack_manager_backend / app_chirpstack_manager_frontend
# do .github/workflows/main.yml) em api-/app-chirpstack-manager-*_platform_linux-arm64:X.Y; o ArgoCD aplica
# o Deployment (Applications chirpstack-manager-backend / -frontend). Build manual, se precisar:
docker buildx build --platform linux/arm64 -t gru.ocir.io/griszz3l82u1/api-chirpstack-manager-backend_platform_linux-arm64:latest --push api_chirpstack_manager_backend/
docker buildx build --platform linux/arm64 -t gru.ocir.io/griszz3l82u1/app-chirpstack-manager-frontend_platform_linux-arm64:latest --push app_chirpstack_manager_frontend/

# Secret único api-chirpstack-manager-secret: o 01.deploy_full cria/completa
# as chaves sozinho (scripts 00, 06 e 07 de 00.UsefulScripts). O arquivo 02 é só o MODELO comentado.
# Token do ChirpStack fora da pipeline:
CHIRPSTACK_API_TOKEN='<API key>' bash api_chirpstack_manager_backend/kubernetes/00.UsefulScripts/07.gerar_segredos_aplicacao_api-chirpstack-manager_script.sh

# Deploy: primeiro o backend, depois o frontend (cada um espera o próprio rollout)
api_chirpstack_manager_backend/kubernetes/00.UsefulScripts/01.deploy_full_api-chirpstack-manager-backend_script.sh
app_chirpstack_manager_frontend/kubernetes/00.UsefulScripts/01.deploy_full_app-chirpstack-manager-frontend_script.sh
```

Cada módulo tem a sua pasta `kubernetes/` com `00.UsefulScripts/` e um `kustomization.yaml` (que renderiza só o Deployment, para ArgoCD/pipeline). Nomes dos objetos no cluster: `api-chirpstack-manager-backend-deployment`/`-service`, `api-chirpstack-manager-postgresql-deployment`/`-service`, `app-chirpstack-manager-frontend-deployment`/`-service`/`-ingress`.

| Backend (`api_chirpstack_manager_backend/kubernetes/`) | Frontend (`app_chirpstack_manager_frontend/kubernetes/`) |
|---|---|
| 01 Namespace · 02 Secret (modelo) · 03–06 PostgreSQL (StorageClass/PV/PVC, ConfigMap init-db, Deployment, Service :5449) · 07 Job (role, database, dono do schema) · 08 volume dos backups · 10 Deployment · 11 Service — 09 (RBAC) e 12–14 (Ingress da API) não existem aqui | 01 Namespace · 02 Deployment · 03 Service · 04 cert-manager e 05 ClusterIssuer (referência) · 06 Ingress · 07 Ingress da restauração (512 MB) |

| Script | O que faz |
|---|---|
| backend `01.deploy_full_…` | Confere o Secret e as chaves obrigatórias, aplica 01 e 03 a 09 na ordem, espera o PostgreSQL e a API |
| backend `02.destroy_full_…` | Remove a API e o PostgreSQL **mantendo** banco, backups, Secret e namespace. `--tudo` apaga também os dados, com confirmação |
| backend `03.back-up_…` | `pg_dump` direto do pod do PostgreSQL para esta máquina (`~/Backups/chirpstack-manager`), sem depender da API |
| backend `05.carga_geral_…` | Carga geral (`database/carga/C01..`): conta de e-mail e cliente OAuth do Google Drive (pasta `ChirpStack/Manager`). Os segredos não ficam no Git: vêm de variáveis de ambiente (a senha SMTP só por `SMTP_SENHA_APP`; o cliente OAuth também pode vir da anotação local do Google Drive) e entram como semente `v0:`, cifrada pela API na primeira subida. Depois, falta só um clique em **Conectar conta Google** |
| backend `04.restore_…` | Restaura um `.backup` desta máquina: para a API, restaura em transação única num schema limpo, encerra as sessões e sobe a API de novo |
| frontend `01.deploy_full_…` | Aplica Deployment, Service e os dois Ingress e espera o rollout |
| frontend `02.destroy_full_…` | Remove Ingress, Service e Deployment (o namespace fica: ele guarda o banco) |

O NGINX do frontend usa `nginx.conf` e `mime.types` próprios (raiz do frontend) e encaminha `/api` para o Service `api-chirpstack-manager-backend-service:8083`.

Antes de aplicar, ajuste nos manifests:

- o host do Ingress (`app-chirpstack-manager.adailsilva.com.br`);
- o nome do `ClusterIssuer`;
- o `storageClassName` do PostgreSQL (backend `04…PersistentVolumeClaim.yaml`) e do volume de backups (backend `07…PersistentVolumeClaim.yaml`);
- `APP_CORS_ALLOWED_ORIGINS`, `APP_URL_PUBLICA` e `GOOGLE_DRIVE_REDIRECT_URI` no Deployment do backend (todos no host do Ingress);
- o `imagePullSecrets` do OCIR (`oci-registry-secret`, criado pelo pipeline em cada deploy).

---

## Estrutura

```
07.chirpstack_manager/
├── api_chirpstack_manager_backend/ Spring Boot 4.1.1 (Java 21)
│   ├── database/carga/             carga geral (C01: conta de e-mail e Google Drive, com marcadores no lugar dos segredos;
│   │                               C02: regras de alerta padrão)
│   ├── kubernetes/                 manifests do backend + 00.UsefulScripts/ + kustomization.yaml
│   └── src/main/
│       ├── java/br/com/adailsilva/chirpstackmanager/api/
│       │   ├── config/             segurança, propriedades, bootstrap do admin, CORS
│       │   ├── controller/         auth (inclui esqueci/redefinir senha), usuários, conta de e-mail, auditoria,
│       │   │                       sessões, visitantes, backup, Google Drive
│       │   │   └── chirpstack/     proxies da REST API: tenants, users, applications, device-profiles
│       │   │                       (+ catálogo), templates, devices, gateways, relay-gateways, relays,
│       │   │                       multicast-groups, resumo
│       │   ├── service/            auth, usuários, auditoria, sessões, visitantes, conta de e-mail, recuperação de senha
│       │   │   ├── email/          envio SMTP com a senha do banco, corpos dos e-mails, recifrador das sementes v0:
│       │   │   ├── backup/         backup/restauração (PostgreSQL e H2), agendamento
│       │   │   ├── googledrive/    OAuth e upload resumable
│       │   │   └── chirpstack/     cliente REST, Query, exceção gRPC
│       │   ├── repository/         Spring Data JPA
│       │   ├── domain/             entity/, enums/, dto/ (+ dto/response: ApiError, Pagina)
│       │   ├── exception/          GlobalExceptionHandler e exceções de negócio
│       │   ├── security/           Ids, Perfis, cifrador · filter/ (sessão ativa) · service/ (JWT, rate limit)
│       │   └── web/                interceptor de auditoria, IP/navegador do cliente
│       └── resources/              application.properties + 3 perfis, banners/, db/migration/{H2,PostgreSQL}
├── app_chirpstack_manager_frontend/ Angular 21 + Avalon
│   ├── kubernetes/                 manifests do frontend + 00.UsefulScripts/ + kustomization.yaml
│   ├── nginx.conf · mime.types     NGINX da imagem (SPA + proxy de /api)
│   └── src/app/
│       ├── core/                   auth/ (guards, interceptor, serviço), http/ (cliente da API, modelos),
│       │                           sessao/, format/, i18n/, notificacao/, tenant/
│       ├── pages/                  uma pasta por tela: aplicacoes, dispositivos, gateways, perfis-dispositivo,
│       │                           catalogo, multicast, tenants, usuarios, visitantes, backup, conta-email,
│       │                           auth (login, esqueci a senha, nova senha)...
│       ├── shared/                 campos/, graficos/, tabela/, visual/
│       └── layout/                 tema Avalon adaptado (menu, topo, rodapé)
├── app_chirpstack_manager_mobile/  Flutter (Android/iOS) — build pelo .github/workflows/mobile.yml
├── backups/ · deploy/ · planos_implantacao/ · scripts_back-up/   pastas de apoio do módulo
├── stack_chirpstack-manager/       docker-compose da aplicação completa (local)
├── stack_postgresql_pgAdmin_4/     PostgreSQL + pgAdmin locais (perfil staging)
├── useful_commands_backup_and_restore/ · useful_commands_local_deploy/
├── docs/protocolo/                 protocolo de aplicação dos dispositivos (PROTOCOLO_ADAILSILVA_V1.md)
└── docs/telas/                     capturas de tela
```

## Próximos passos sugeridos

- Envio em tempo real ao navegador (WebSocket/SSE) dos eventos que a API já recebe pela integração MQTT.
- Mapa de gateways e dispositivos (Leaflet/OpenStreetMap).
- Importação de dispositivos em lote (CSV).
- Login único com os usuários do ChirpStack (via gRPC `InternalService`, que a REST API não expõe).

## Telas

As capturas em [`docs/telas/`](docs/telas) foram feitas com o frontend real, rodando contra uma simulação do backend e do ChirpStack 4.15 (as telas da 4.19 seguem o mesmo padrão) com dados de exemplo.

| | |
|---|---|
| ![Login](docs/telas/01-login.png) | ![Painel](docs/telas/02-painel.png) |
| ![Dispositivo](docs/telas/04-dispositivo-metricas.png) | ![Gateway](docs/telas/08-gateway.png) |
| ![Perfil](docs/telas/09-perfil-dispositivo.png) | ![Integrações](docs/telas/07-integracoes.png) |

### Visitantes, logoff e backup

| | |
|---|---|
| ![Visitantes](docs/telas/16-visitantes.png) | ![Por usuário](docs/telas/17-visitantes-usuarios.png) |
| ![Bloqueio](docs/telas/18-visitantes-bloqueio.png) | ![Histórico de acessos](docs/telas/19-visitantes-historico.png) |
| ![Controle de sessão](docs/telas/21-controle-sessao.png) | ![Aviso de inatividade](docs/telas/22-aviso-inatividade.png) |
| ![Login após encerramento](docs/telas/20-login-encerrada-admin.png) | ![Backup](docs/telas/25-backup.png) |
| ![Agendamento](docs/telas/26-backup-agendamento.png) | ![Google Drive](docs/telas/27-backup-google-drive.png) |
| ![Histórico de backups](docs/telas/28-backup-historico.png) | ![Restaurar](docs/telas/29-backup-restaurar.png) |
