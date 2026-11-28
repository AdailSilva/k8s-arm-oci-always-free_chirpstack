# ChirpStack na OCI · Guia de identidade visual

> Rede LoRaWAN própria (ChirpStack v3/v4) no Kubernetes ARM da OCI Always Free.
> Vale para o site chirpstack.adailsilva.com.br (`04.homepage/app_chirpstack_site_frontend`), o **ChirpStack Manager** (`07.chirpstack_manager/`, administração e monitoramento) e qualquer material novo da rede.

## 1. Marca e sistemas

- **ChirpStack** — a rede LoRaWAN (ChirpStack v3 e v4) e o site institucional.
- **ChirpStack Manager** — o sistema de administração e monitoramento da rede: tenants,
  aplicações, dispositivos, gateways, usuários, backup e os eventos MQTT em tempo real.

No texto corrido: "ChirpStack" (C e S maiúsculos, sem espaço) e "ChirpStack Manager". Nas telas o
nome curto é "Manager" ("© ano ChirpStack · Manager").

Identificadores técnicos: `chirpstack-manager` (namespace, imagens OCIR e o domínio
`app-chirpstack-manager.adailsilva.com.br`), `chirpstack_manager` nas pastas do repositório e
o prefixo **`chirpstack-manager-`** nos arquivos desta pasta.

## 2. Logotipo

Todos os arquivos saem do mesmo símbolo 40×40 usado no site (`04.homepage/app_chirpstack_site_frontend/src/app/shared/logo.ts`
e `public/favicon.svg`). Os textos dos logotipos usam a fonte **Archivo** (a mesma do site), com
`Inter`/`Arial` como reserva — **não estão convertidos em curvas**: para material impresso, abra o
SVG num editor vetorial com a fonte instalada e converta o texto antes de enviar.

| Arquivo | Uso |
|---|---|
| `chirpstack-logo-horizontal.svg` / `-1200.png` | Marca principal: símbolo à esquerda do nome, fundos claros. |
| `chirpstack-logo-horizontal-negativo.svg` / `-1200.png` | Horizontal para fundos escuros. |
| `chirpstack-logo.svg` / `-1200.png` | Marca vertical (símbolo sobre o nome), fundos claros. |
| `chirpstack-logo-negativo.svg` / `-1200.png` | Vertical para fundos escuros. |
| `chirpstack-simbolo.svg` / `-256.png` | Só o símbolo — favicon, topo do menu, marca d'água. |
| `chirpstack-simbolo-negativo.svg` / `-256.png` | Símbolo para fundos escuros (bloco branco, traço na cor escura). |
| `chirpstack-icone.svg` / `-512.png` / `-1024.png` | Ícone em fundo escuro — avatar, redes sociais. |
| `chirpstack-icone-claro.svg` / `-512.png` / `-1024.png` | Ícone em fundo branco. |

- **Símbolo**: antena LoRa (mastro com ponto) entre duas ondas de rádio de cada lado (85% e 50% de
  opacidade) sobre bloco arredondado em degradê teal.
- Área de proteção: ao redor do símbolo, no mínimo 1/4 da altura dele. Tamanho mínimo: 16 px (favicon).

### ChirpStack Manager

| Arquivo | Uso |
|---|---|
| `chirpstack-manager-logo-horizontal.svg` / `-1200.png` | "ChirpStack Manager · por ChirpStack", fundos claros. |
| `chirpstack-manager-logo-horizontal-negativo.svg` / `-1200.png` | Horizontal para fundos escuros (tela de login). |
| `chirpstack-manager-simbolo.svg` / `-256.png` | Símbolo do sistema — favicon e topo do menu do painel. |
| `chirpstack-manager-icone.svg` / `-512.png` / `-1024.png` | Ícone do sistema — app, atalhos, avatar. |
| `chirpstack-manager-icone-claro.svg` / `-512.png` / `-1024.png` | Ícone do sistema em fundo branco. |
| `chirpstack-manager-icone-foreground.svg` / `-432.png` | Primeiro plano do ícone adaptativo Android (108 dp, sem fundo). |

- **Símbolo do sistema**: a mesma antena da marca, em branco, sobre bloco **azul Manager**, com o
  **filete teal** (cor da marca ChirpStack) sob o mastro — o filete é a assinatura do sistema.
- **Ícone claro**: o símbolo da marca (bloco teal) sobre fundo branco, com o filete na cor do sistema.
- **Letreiro**: "ChirpStack" em Archivo Bold (`#0b1220`; branco no negativo) e "Manager" em Archivo
  SemiBold (azul 600; azul 300 no negativo); linha de apoio "por ChirpStack · LoRaWAN na OCI".
- Os PNGs foram gerados a partir dos SVGs com o Chrome headless; sem a fonte Archivo instalada eles
  usam a reserva (Inter/Arial).

## 3. Cores

| Papel | Cor | Uso |
|---|---|---|
| Primária clara | `#2dd4bf` | Início do degradê do símbolo, destaques no tema escuro. |
| Primária escura | `#0f766e` | Fim do degradê, botões e links no tema claro. |
| Fundo escuro | `#042f2e` | Fundo do ícone e das áreas escuras. |
| Texto | `#0b1220` / `#64748b` | Título e subtítulo nos fundos claros. |

### Azul Manager (cor do sistema)

| Papel | Cor | Uso |
|---|---|---|
| Azul 500 / 700 | `#3b82f6` → `#1d4ed8` | Degradê do símbolo e do ícone do sistema. |
| Azul 600 | `#2563eb` | Botões, links e "Manager" no letreiro claro. |
| Azul 300 / 400 | `#93c5fd` / `#60a5fa` | Destaques nos fundos escuros. |
| Azul noite | `#0b1a3a` | Fundo do painel de login. |
| Filete | `#2dd4bf` | Assinatura do sistema (teal da marca). |

É o mesmo azul do preset PrimeNG do painel (`app_chirpstack_manager_frontend/src/app.config.ts`, `blue`).

No site, a cor primária vem do preset do PrimeNG (`src/app.config.ts`), então as telas seguem a
mesma paleta sem cor fixa no código.

## 4. Padrão entre repositórios

Mesmo conjunto de arquivos e mesma nomenclatura (`<projeto>-<peça>[-variação][-tamanho].ext`) das
pastas `brand/` dos demais repositórios do cluster. Ao criar um sistema novo neste
repositório, acrescente `chirpstack-<sistema>-icone*.svg/png` e `chirpstack-<sistema>-logo-horizontal*.svg/png`.
