package br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo;

import static br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.Comum.exigir;
import static br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.Comum.exigirPeriferico;
import static br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.Comum.invalido;
import static br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.Comum.nomeRele;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.ParametrosComando;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Comando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.AcaoRele;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.EstrategiaConfirmacao;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.ModeloFirmware;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.ResultadoTentativa;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoComando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoPeriferico;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.protocolo.Bytes;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.protocolo.Modbus;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Protocolo ADAILSILVA_V1 (docs/protocolo/PROTOCOLO_ADAILSILVA_V1.md): comando
 * {@code seq|opcode|parâmetros} na porta 10, resposta {@code seq|opcode|status|dados} na 11 e
 * telemetria em TLV ({@code tipo|canal|tamanho|valor}) na 20. Inteiros big-endian.
 */
@Component
public class DriverAdailSilvaV1 implements DriverModelo {

    public static final int PORTA_COMANDO = 10;
    public static final int PORTA_RESPOSTA = 11;
    public static final int PORTA_TELEMETRIA = 20;

    static final Map<TipoComando, Integer> OPCODES = new EnumMap<>(TipoComando.class);
    static {
        OPCODES.put(TipoComando.STATUS, 0x01);
        OPCODES.put(TipoComando.INTERVALO, 0x02);
        OPCODES.put(TipoComando.ENVIAR_AGORA, 0x03);
        OPCODES.put(TipoComando.CONFIRMADO, 0x04);
        OPCODES.put(TipoComando.CLASSE, 0x05);
        OPCODES.put(TipoComando.REINICIAR, 0x06);
        OPCODES.put(TipoComando.RELE_DEFINIR, 0x20);
        OPCODES.put(TipoComando.RELE_PULSO, 0x21);
        OPCODES.put(TipoComando.RELE_MASCARA, 0x22);
        OPCODES.put(TipoComando.RELE_LER, 0x23);
        OPCODES.put(TipoComando.PZEM_LER, 0x30);
        OPCODES.put(TipoComando.PZEM_ZERAR_ENERGIA, 0x31);
        OPCODES.put(TipoComando.PZEM_LIMITE_POTENCIA, 0x32);
    }

    private static final String[] STATUS_NOMES = {
            "OK", "OPCODE_DESCONHECIDO", "PARAMETRO_INVALIDO", "PERIFERICO_AUSENTE", "FALHA_PERIFERICO", "OCUPADO" };
    private static final String[] MOTIVOS_BOOT = {
            "DESCONHECIDO", "ENERGIZACAO", "SOFTWARE", "WATCHDOG", "BROWNOUT", "PINO_RESET" };

    @Override
    public ModeloFirmware modelo() {
        return ModeloFirmware.ADAILSILVA_V1;
    }

    @Override
    public Set<TipoComando> comandos() {
        Set<TipoComando> s = EnumSet.copyOf(OPCODES.keySet());
        s.add(TipoComando.RESET_REMOTO);
        s.add(TipoComando.BRUTO);
        return s;
    }

    @Override
    public Set<TipoPeriferico> perifericos() {
        return EnumSet.of(TipoPeriferico.RELE, TipoPeriferico.PZEM004T);
    }

    @Override
    public List<String> observacoes() {
        return List.of(
                "Cada comando leva um número de sequência (seq): o firmware responde com o mesmo seq e não repete a ação se o comando chegar de novo.",
                "Com PZEM-004T use DR3 ou maior: o bloco do medidor não cabe em DR2.",
                "Em Classe A o comando só desce depois do próximo uplink do equipamento.");
    }

    @Override
    public boolean usaSeq(TipoComando tipo) {
        return OPCODES.containsKey(tipo);
    }

    @Override
    public Downlink codificar(TipoComando tipo, ParametrosComando p, ContextoEquipamento ctx, int seq) {
        if (tipo == TipoComando.RESET_REMOTO) {
            return Comum.resetRemoto();
        }
        if (tipo == TipoComando.BRUTO) {
            return Comum.bruto(p);
        }
        Integer op = OPCODES.get(tipo);
        if (op == null) {
            throw invalido("Comando não suportado pelo protocolo V1.");
        }
        Bytes.Quadro q = new Bytes.Quadro().u8(seq).u8(op);
        String resumo;
        switch (tipo) {
            case STATUS -> resumo = "Consultar status";
            case INTERVALO -> {
                long s = exigir(p.segundos(), "Intervalo em segundos", 5, 86400);
                q.u32(s);
                resumo = "Intervalo de envio: " + s + " s";
            }
            case ENVIAR_AGORA -> resumo = "Enviar leitura agora";
            case CONFIRMADO -> {
                if (p.confirmado() == null) {
                    throw invalido("Informe se a telemetria deve ser confirmada.");
                }
                q.u8(p.confirmado() ? 1 : 0);
                resumo = "Telemetria " + (p.confirmado() ? "confirmada" : "não confirmada");
            }
            case CLASSE -> {
                String c = p.classe() == null ? "" : p.classe().trim().toUpperCase();
                if (!c.equals("A") && !c.equals("C")) {
                    throw invalido("Classe: A ou C.");
                }
                q.u8(c.charAt(0));
                resumo = "Classe " + c;
            }
            case REINICIAR -> {
                int atraso = p.atrasoMs() == null ? 2000 : exigir(p.atrasoMs(), "Atraso em ms", 0, 60000);
                q.u16(atraso);
                resumo = "Reiniciar (em " + atraso + " ms)";
            }
            case RELE_DEFINIR -> {
                if (p.acao() == null) {
                    throw invalido("Informe a ação: ligar, desligar ou alternar.");
                }
                if (p.canal() != null) {
                    exigirPeriferico(ctx, TipoPeriferico.RELE, p.canal());
                } else if (ctx.reles().isEmpty()) {
                    throw invalido("Nenhum relé cadastrado neste equipamento.");
                }
                q.u8(p.canal() == null ? 0xFF : p.canal()).u8(p.acao().ordinal());
                resumo = acaoTexto(p.acao()) + " " + nomeRele(ctx, p.canal());
            }
            case RELE_PULSO -> {
                int canal = exigir(p.canal(), "Relé", 0, 15);
                exigirPeriferico(ctx, TipoPeriferico.RELE, canal);
                long ms = exigir(p.duracaoMs(), "Duração do pulso em ms", 100, 86_400_000L);
                boolean estado = p.estado() == null || p.estado();
                q.u8(canal).u8(estado ? 1 : 0).u32(ms);
                resumo = "Pulso " + (estado ? "ligado" : "desligado") + " de " + Comum.duracao(ms) + " no " + nomeRele(ctx, canal);
            }
            case RELE_MASCARA -> {
                int mascara = exigir(p.mascara(), "Máscara", 1, 0xFFFF);
                int valores = exigir(p.valores() == null ? 0 : p.valores(), "Valores", 0, 0xFFFF);
                for (int b = 0; b < 16; b++) {
                    if ((mascara & (1 << b)) != 0) {
                        exigirPeriferico(ctx, TipoPeriferico.RELE, b);
                    }
                }
                q.u16(mascara).u16(valores & mascara);
                resumo = "Relés: " + mascaraTexto(mascara, valores);
            }
            case RELE_LER -> resumo = "Ler relés";
            case PZEM_LER, PZEM_ZERAR_ENERGIA -> {
                int canal = p.canal() == null ? 0 : exigir(p.canal(), "Medidor", 0, 15);
                exigirPeriferico(ctx, TipoPeriferico.PZEM004T, canal);
                q.u8(canal);
                resumo = (tipo == TipoComando.PZEM_LER ? "Ler medidor " : "Zerar energia do medidor ") + canal;
            }
            case PZEM_LIMITE_POTENCIA -> {
                int canal = p.canal() == null ? 0 : exigir(p.canal(), "Medidor", 0, 15);
                exigirPeriferico(ctx, TipoPeriferico.PZEM004T, canal);
                int w = exigir(p.watts(), "Limite em W", 0, 23000);
                q.u8(canal).u16(w);
                resumo = "Limite de potência do medidor " + canal + ": " + w + " W";
            }
            default -> throw invalido("Comando não suportado pelo protocolo V1.");
        }
        return new Downlink(PORTA_COMANDO, q.fim(), false, EstrategiaConfirmacao.RESPOSTA_V1, resumo);
    }

    static String acaoTexto(AcaoRele a) {
        return switch (a) {
            case LIGAR -> "Ligar";
            case DESLIGAR -> "Desligar";
            case ALTERNAR -> "Alternar";
        };
    }

    static String mascaraTexto(int mascara, int valores) {
        StringBuilder sb = new StringBuilder();
        for (int b = 0; b < 16; b++) {
            if ((mascara & (1 << b)) != 0) {
                if (!sb.isEmpty()) {
                    sb.append(", ");
                }
                sb.append(b).append((valores & (1 << b)) != 0 ? " ligado" : " desligado");
            }
        }
        return sb.toString();
    }

    // ── uplinks ──────────────────────────────────────────────────────────────

    @Override
    public Decodificacao decodificar(int porta, byte[] dados, ContextoEquipamento ctx) {
        if (dados == null || dados.length == 0) {
            return null;
        }
        Efeitos ef = new Efeitos();
        ObjectNode obj = JsonNodeFactory.instance.objectNode();
        if (porta == PORTA_TELEMETRIA) {
            telemetria(dados, obj, ef);
        } else if (porta == PORTA_RESPOSTA && dados.length >= 3) {
            obj.set("resposta", resposta(dados, ef));
        } else {
            return null;
        }
        return new Decodificacao(obj, ef);
    }

    private static void telemetria(byte[] d, ObjectNode obj, Efeitos ef) {
        ArrayNode pzem = null;
        int i = 0;
        while (i + 3 <= d.length) {
            int tipo = Bytes.u8(d, i);
            int canal = Bytes.u8(d, i + 1);
            int tam = Bytes.u8(d, i + 2);
            int v = i + 3;
            if (v + tam > d.length) {
                break; // bloco cortado
            }
            switch (tipo) {
                case 0x01 -> {
                    if (tam >= 3) {
                        obj.set("reles", reles(Bytes.u8(d, v), Bytes.u16(d, v + 1), ef));
                    }
                }
                case 0x02 -> {
                    if (tam >= 3) {
                        obj.set("entradas", mapa(Bytes.u8(d, v), Bytes.u16(d, v + 1)));
                    }
                }
                case 0x10 -> {
                    if (tam >= 18) {
                        Modbus.Medicao m = medicao(d, v);
                        ef.medicoes.put(canal, m);
                        if (pzem == null) {
                            pzem = obj.putArray("pzem");
                        }
                        pzem.add(Comum.medicaoJson(canal, m));
                    }
                }
                case 0x11 -> {
                    ef.medidoresSemResposta.add(canal);
                    if (!obj.has("pzemFalha")) {
                        obj.putArray("pzemFalha");
                    }
                    ((ArrayNode) obj.get("pzemFalha")).add(canal);
                }
                case 0x30 -> {
                    if (tam >= 2) {
                        obj.put("alimentacaoMv", Bytes.u16(d, v));
                    }
                }
                case 0x31 -> {
                    if (tam >= 1) {
                        int m = Bytes.u8(d, v);
                        ObjectNode b = obj.putObject("boot");
                        b.put("motivo", m);
                        b.put("motivoNome", m < MOTIVOS_BOOT.length ? MOTIVOS_BOOT[m] : "DESCONHECIDO");
                        ef.boot = true;
                    }
                }
                default -> {
                    // tipo novo: pulado pelo tamanho
                }
            }
            i = v + tam;
        }
    }

    private static ObjectNode resposta(byte[] d, Efeitos ef) {
        int seq = Bytes.u8(d, 0);
        int op = Bytes.u8(d, 1);
        int st = Bytes.u8(d, 2);
        ObjectNode r = JsonNodeFactory.instance.objectNode();
        r.put("seq", seq);
        r.put("opcode", op);
        r.put("opcodeNome", OPCODES.entrySet().stream().filter(e -> e.getValue() == op).map(e -> e.getKey().name())
                .findFirst().orElse("DESCONHECIDO"));
        r.put("status", st);
        r.put("statusNome", st < STATUS_NOMES.length ? STATUS_NOMES[st] : "DESCONHECIDO");
        int n = d.length - 3;
        if (st != 0 || n == 0) {
            return r;
        }
        int v = 3;
        switch (op) {
            case 0x01 -> {
                if (n >= 19) {
                    ObjectNode s = r.putObject("dispositivo");
                    s.put("protocolo", Bytes.u8(d, v));
                    s.put("firmware", Bytes.u8(d, v + 1) + "." + Bytes.u8(d, v + 2) + "." + Bytes.u8(d, v + 3));
                    s.put("uptimeS", Bytes.u32(d, v + 4));
                    s.put("intervaloS", Bytes.u32(d, v + 8));
                    s.put("classe", String.valueOf((char) Bytes.u8(d, v + 12)));
                    s.put("confirmado", Bytes.u8(d, v + 13) != 0);
                    s.set("reles", reles(Bytes.u8(d, v + 14), Bytes.u16(d, v + 15), ef));
                    s.put("medidores", Bytes.u8(d, v + 17));
                    int m = Bytes.u8(d, v + 18);
                    s.put("motivoBoot", m < MOTIVOS_BOOT.length ? MOTIVOS_BOOT[m] : "DESCONHECIDO");
                    ef.status = s;
                }
            }
            case 0x02 -> {
                if (n >= 4) {
                    r.put("intervaloS", Bytes.u32(d, v));
                }
            }
            case 0x20, 0x21, 0x22, 0x23 -> {
                if (n >= 3) {
                    r.set("reles", reles(Bytes.u8(d, v), Bytes.u16(d, v + 1), ef));
                }
            }
            case 0x30 -> {
                if (n >= 19) {
                    int canal = Bytes.u8(d, v);
                    Modbus.Medicao m = medicao(d, v + 1);
                    ef.medicoes.put(canal, m);
                    r.set("pzem", Comum.medicaoJson(canal, m));
                }
            }
            default -> {
                // sem dados
            }
        }
        return r;
    }

    private static ObjectNode reles(int qtd, int mapa, Efeitos ef) {
        ObjectNode o = mapa(qtd, mapa);
        for (int b = 0; b < Math.min(qtd, 16); b++) {
            ef.reles.put(b, (mapa & (1 << b)) != 0);
        }
        return o;
    }

    private static ObjectNode mapa(int qtd, int mapa) {
        ObjectNode o = JsonNodeFactory.instance.objectNode();
        o.put("quantidade", qtd);
        ArrayNode est = o.putArray("estados");
        for (int b = 0; b < Math.min(qtd, 16); b++) {
            est.add((mapa & (1 << b)) != 0);
        }
        return o;
    }

    static Modbus.Medicao medicao(byte[] d, int v) {
        return new Modbus.Medicao(BigDecimal.valueOf(Bytes.u16(d, v), 1), BigDecimal.valueOf(Bytes.u32(d, v + 2), 3),
                BigDecimal.valueOf(Bytes.u32(d, v + 6), 1), Bytes.u32(d, v + 10), BigDecimal.valueOf(Bytes.u16(d, v + 14), 1),
                BigDecimal.valueOf(Bytes.u8(d, v + 16), 2), Bytes.u8(d, v + 17) != 0);
    }

    @Override
    public Desfecho avaliar(Comando c, ParametrosComando p, ContextoEquipamento ctx, int porta, byte[] dados) {
        if (c.getEstrategia() != EstrategiaConfirmacao.RESPOSTA_V1 || porta != PORTA_RESPOSTA || dados == null
                || dados.length < 3 || c.getSeq() == null) {
            return null;
        }
        Integer op = OPCODES.get(c.getTipo());
        if (op == null || Bytes.u8(dados, 0) != c.getSeq() || Bytes.u8(dados, 1) != op) {
            return null;
        }
        Efeitos ef = new Efeitos();
        String json = resposta(dados, ef).toString();
        int st = Bytes.u8(dados, 2);
        String nome = st < STATUS_NOMES.length ? STATUS_NOMES[st] : "status " + st;
        return switch (st) {
            case 0 -> Desfecho.executado("Resposta OK (seq " + c.getSeq() + ")", json, ef);
            case 5 -> Desfecho.repetir(ResultadoTentativa.OCUPADO, "Equipamento ocupado; nova tentativa", json);
            default -> Desfecho.falhou("Recusado pelo firmware: " + nome, json);
        };
    }
}
