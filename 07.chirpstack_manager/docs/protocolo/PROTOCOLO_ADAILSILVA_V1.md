# Protocolo de aplicação ADAILSILVA_V1

Contrato binário entre os nós LoRaWAN com atuadores e sensores e o servidor que os
gerencia. Ele define como o servidor manda comandos (downlink), como o nó prova que
executou cada um (resposta com o mesmo `seq`) e como o nó envia as medições (telemetria
em TLV, que aceita novos tipos de sensor sem quebrar quem já decodifica a versão atual).

Primeiros periféricos: **relés** (atuadores) e **medidor de energia PZEM-004T v3**
(sensor). Outros entram como novos opcodes e novos tipos de TLV.

## 1. Regras gerais

- Inteiros **big-endian**, sem sinal, salvo quando indicado.
- Canais começam em **0** (relé 0 = primeiro relé).
- Portas (fPort):

| Porta | Sentido | Conteúdo |
|------:|---------|----------|
| 10 | downlink | comando |
| 11 | uplink | resposta a um comando |
| 20 | uplink | telemetria (TLV) |
| 101 | downlink | reset remoto (comando fixo de 8 bytes, mantido dos firmwares base) |

- Resposta e telemetria saem como **uplink não confirmado**. A garantia de execução vem
  do par comando/resposta: sem resposta, o servidor reenvia o **mesmo** comando com o
  **mesmo** `seq`.

## 2. Comando (downlink, porta 10)

```
seq(1) | opcode(1) | parâmetros(0..n)
```

- `seq` 1..255 escolhido pelo servidor, um contador por dispositivo (pula o 0).
  `seq` 0 = comando sem deduplicação (sempre executa).
- **Deduplicação:** o nó guarda em RAM o último `seq` (≠ 0) e a resposta que deu. Se chegar
  outra vez o mesmo `seq`, ele **não executa de novo**; só reenvia a resposta guardada.
  Isso torna seguro repetir um ALTERNAR ou um PULSO quando a resposta se perdeu.
- Bytes a mais depois dos parâmetros são ignorados. Comando curto demais → status
  `PARAMETRO_INVALIDO`.

## 3. Resposta (uplink, porta 11)

```
seq(1) | opcode(1) | status(1) | dados(0..n)
```

| status | Nome | Quando |
|------:|------|--------|
| 0x00 | OK | executado |
| 0x01 | OPCODE_DESCONHECIDO | opcode que o firmware não conhece |
| 0x02 | PARAMETRO_INVALIDO | parâmetro fora da faixa ou comando curto |
| 0x03 | PERIFERICO_AUSENTE | canal de relé ou de PZEM não configurado |
| 0x04 | FALHA_PERIFERICO | o periférico não respondeu (ex.: PZEM sem resposta Modbus) |
| 0x05 | OCUPADO | o nó não pôde executar agora; o servidor tenta de novo |

- O nó envia a resposta na **primeira oportunidade** depois de executar o comando
  (Classe A: agenda um uplink logo em seguida, respeitando o duty cycle).
- Se a resposta completa não couber no tamanho máximo do DR atual, o nó envia só
  `seq | opcode | status` (3 bytes); os dados seguem depois pela telemetria.

## 4. Opcodes

| Opcode | Nome | Parâmetros | Dados da resposta |
|------:|------|-----------|-------------------|
| 0x01 | STATUS | — | ver 4.1 (19 bytes) |
| 0x02 | INTERVALO | segundos u32 (5..86400) | segundos u32 aplicados |
| 0x03 | ENVIAR_AGORA | — | — (a telemetria sai logo depois) |
| 0x04 | CONFIRMADO | u8 (0 = não confirmado, 1 = confirmado) — vale para a telemetria | — |
| 0x05 | CLASSE | u8 `0x41` ('A') ou `0x43` ('C') | — (`PARAMETRO_INVALIDO` se a classe não for suportada) |
| 0x06 | REINICIAR | atraso u16 em ms (0..60000) | — (a resposta sai **antes** de reiniciar) |
| 0x20 | RELE_DEFINIR | canal u8 (0xFF = todos), ação u8 (0 desligar, 1 ligar, 2 alternar) | qtd u8, mapa u16 |
| 0x21 | RELE_PULSO | canal u8, estado u8 (0/1, estado durante o pulso), duração u32 em ms (100..86400000) | qtd u8, mapa u16 (durante o pulso) |
| 0x22 | RELE_MASCARA | máscara u16, valores u16 (aplica `valores` só nos bits de `máscara`) | qtd u8, mapa u16 |
| 0x23 | RELE_LER | — | qtd u8, mapa u16 |
| 0x30 | PZEM_LER | canal u8 | canal u8 + bloco PZEM (18 bytes, ver 5.2) |
| 0x31 | PZEM_ZERAR_ENERGIA | canal u8 | — |
| 0x32 | PZEM_LIMITE_POTENCIA | canal u8, watts u16 (0..23000) | — |

- `mapa` u16: bit n = estado do relé n (1 = ligado). `qtd` = quantos relés o nó tem.
- Fim de um PULSO: o relé volta ao estado anterior e o nó manda uma telemetria com o
  TLV de relés.
- Os estados dos relés ficam gravados na memória não volátil e são restaurados ao ligar
  (um PULSO em andamento não é restaurado: volta ao estado anterior a ele).

### 4.1 Dados de STATUS (19 bytes)

| Offset | Tam | Campo |
|------:|----:|-------|
| 0 | 1 | versão do protocolo (= 1) |
| 1 | 3 | versão do firmware: maior, menor, correção |
| 4 | 4 | uptime em segundos |
| 8 | 4 | intervalo de telemetria em segundos |
| 12 | 1 | classe atual (`0x41` 'A' / `0x43` 'C') |
| 13 | 1 | telemetria confirmada (0/1) |
| 14 | 1 | quantidade de relés |
| 15 | 2 | mapa dos relés |
| 17 | 1 | quantidade de PZEM |
| 18 | 1 | motivo do último boot (tabela em 5.4) |

## 5. Telemetria (uplink, porta 20)

Sequência de blocos TLV:

```
tipo(1) | canal(1) | tamanho(1) | valor(tamanho)
```

Quem decodifica **pula** tipos que não conhece usando o `tamanho`. Assim um sensor novo
não quebra decodificadores antigos.

| Tipo | Nome | Canal | Tam | Valor |
|----:|------|-------|----:|-------|
| 0x01 | RELES | 0 | 3 | qtd u8, mapa u16 |
| 0x02 | ENTRADAS | 0 | 3 | qtd u8, mapa u16 (reservado para entradas digitais) |
| 0x10 | PZEM | índice do medidor | 18 | bloco PZEM (5.2) |
| 0x11 | PZEM_FALHA | índice do medidor | 0 | o medidor não respondeu nesta leitura |
| 0x30 | ALIMENTACAO | 0 | 2 | tensão de alimentação em mV |
| 0x31 | BOOT | 0 | 1 | motivo do boot (5.4) — só na primeira telemetria depois de ligar |

### 5.1 Quando sai

- A cada `intervalo` segundos; logo depois de ENVIAR_AGORA; ao terminar um PULSO; e
  quando um relé muda de estado por qualquer motivo que não seja um comando (ex.: botão
  local), para o servidor saber o estado real.
- Se os blocos não couberem em um uplink do DR atual, o nó divide em vários uplinks,
  sempre com blocos inteiros. Um bloco PZEM tem 21 bytes com o cabeçalho: **não cabe em
  DR2 (11 bytes, AU915 com dwell time)**; use DR3 ou maior quando houver PZEM.

### 5.2 Bloco PZEM (18 bytes)

| Offset | Tam | Campo | Escala |
|------:|----:|-------|--------|
| 0 | 2 | tensão | 0,1 V |
| 2 | 4 | corrente | 0,001 A |
| 6 | 4 | potência ativa | 0,1 W |
| 10 | 4 | energia acumulada | 1 Wh |
| 14 | 2 | frequência | 0,1 Hz |
| 16 | 1 | fator de potência | 0,01 |
| 17 | 1 | alarme de potência (0/1) | — |

O PZEM-004T v3 entrega corrente, potência e energia como dois registradores Modbus de
16 bits com a palavra **baixa primeiro**; o firmware converte para o u32 big-endian acima.

### 5.3 PZEM-004T v3 (Modbus RTU, 9600 8N1)

| Ação | Quadro (endereço 0xF8 = genérico, um medidor só no barramento) |
|------|------|
| Ler 10 registradores de entrada | `F8 04 00 00 00 0A 64 64` → resposta de 25 bytes `F8 04 14 <20 bytes> CRC` |
| Zerar energia | `F8 42 C2 41` |
| Limite de potência (registrador 0x0001, em W) | `F8 06 00 01 <W u16> CRC` (ex.: 2300 W → `F8 06 00 01 08 FC CB E2`) |

Registradores de entrada: 0 tensão; 1–2 corrente (baixa, alta); 3–4 potência (baixa,
alta); 5–6 energia (baixa, alta); 7 frequência; 8 fator de potência; 9 alarme.
CRC = Modbus CRC-16 (polinômio 0xA001, início 0xFFFF), byte baixo primeiro.

### 5.4 Motivo do boot

| Valor | Motivo |
|----:|--------|
| 0 | desconhecido |
| 1 | energização |
| 2 | software (comando REINICIAR, reset remoto, console) |
| 3 | watchdog |
| 4 | queda de tensão (brownout) |
| 5 | pino de reset |

## 6. Vetores de teste

| Caso | Hex | Base64 |
|------|-----|--------|
| DL seq 7, RELE_DEFINIR relé 0 ligar | `07 20 00 01` | `ByAAAQ==` |
| DL seq 8, RELE_PULSO relé 1 ligado por 5000 ms | `08 21 01 01 00 00 13 88` | `CCEBAQAAE4g=` |
| DL seq 9, RELE_MASCARA máscara 0x0003, valores 0x0001 | `09 22 00 03 00 01` | `CSIAAwAB` |
| DL seq 10, PZEM_LER medidor 0 | `0A 30 00` | `CjAA` |
| DL seq 11, INTERVALO 300 s | `0B 02 00 00 01 2C` | `CwIAAAEs` |
| UL resposta seq 7: OK, 2 relés, mapa 0x0001 | `07 20 00 02 00 01` | `ByAAAgAB` |
| UL telemetria: relés (2, 0x0001) + PZEM 0 (230,0 V; 0,500 A; 120,0 W; 12345 Wh; 60,0 Hz; FP 0,95; sem alarme) + BOOT energização | `01 00 03 02 00 01 10 00 12 08 FC 00 00 01 F4 00 00 04 B0 00 00 30 39 02 58 5F 00 31 00 01 01` | `AQADAgABEAASCPwAAAH0AAAEsAAAMDkCWF8AMQABAQ==` |

## 7. Objeto decodificado (codec JavaScript do ChirpStack)

O `decodeUplink` dos firmwares devolve, conforme a porta:

```json
// porta 20
{ "reles": { "quantidade": 2, "estados": [true, false] },
  "pzem": [ { "canal": 0, "tensao": 230.0, "corrente": 0.5, "potencia": 120.0,
              "energia": 12345, "frequencia": 60.0, "fp": 0.95, "alarme": false } ],
  "boot": { "motivo": 1, "motivoNome": "ENERGIZACAO" } }
// porta 11
{ "resposta": { "seq": 7, "opcode": 32, "opcodeNome": "RELE_DEFINIR", "status": 0,
                "statusNome": "OK", "reles": { "quantidade": 2, "estados": [true, false] } } }
```

`encodeDownlink` recebe `{ "seq": 7, "comando": "RELE_DEFINIR", "canal": 0, "acao": "LIGAR" }`
(nomes dos opcodes da seção 4) e devolve os bytes da porta 10.
