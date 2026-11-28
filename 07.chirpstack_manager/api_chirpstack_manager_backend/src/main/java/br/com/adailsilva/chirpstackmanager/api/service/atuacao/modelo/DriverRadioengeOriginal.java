package br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo;

import static br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.Comum.exigir;
import static br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.Comum.exigirPeriferico;
import static br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.Comum.invalido;
import static br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.Comum.nomeRele;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.ParametrosComando;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Comando;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Periferico;
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
 * Firmware Radioenge (STM32L071 + SX1272) sem alteração:
 * <ul>
 *   <li>relé = GPIO do módulo como saída: {@code AT+GPIOC=g:1:0;AT+WPIN=g:v} na porta 103,
 *       prova = "AT OK" na resposta da mesma porta;</li>
 *   <li>PZEM-004T = Modbus pela serial transparente (porta 4), prova = resposta Modbus válida.
 *       Pré-requisito no módulo: {@code AT+GPIOC=0:3:0} (serial em GPIO0/1) e {@code AT+BAUDRATE=9600};</li>
 *   <li>classe, intervalo, confirmação e "enviar agora" = comandos binários da porta 102 (sem resposta);</li>
 *   <li>uplinks: evento de GPIO (porta 2), keepalive (5), resposta AT (103), serial (4).</li>
 * </ul>
 */
@Component
public class DriverRadioengeOriginal implements DriverModelo {

    public static final int PORTA_AT = 103;
    public static final int PORTA_BINARIO = 102;
    public static final int PORTA_SERIAL = 4;
    public static final int PORTA_GPIO = 2;
    public static final int PORTA_KEEPALIVE = 5;
    private static final int MAX_AT = 50;
    private static final Pattern WPIN = Pattern.compile("AT\\+WPIN=(\\d+):([01])");
    private static final Pattern RPIN = Pattern.compile("AT\\+RPIN=(\\d+)");

    @Override
    public ModeloFirmware modelo() {
        return ModeloFirmware.RADIOENGE_ORIGINAL;
    }

    @Override
    public Set<TipoComando> comandos() {
        return EnumSet.of(TipoComando.STATUS, TipoComando.INTERVALO, TipoComando.ENVIAR_AGORA, TipoComando.CONFIRMADO,
                TipoComando.CLASSE, TipoComando.REINICIAR, TipoComando.RELE_DEFINIR, TipoComando.RELE_PULSO,
                TipoComando.RELE_MASCARA, TipoComando.RELE_LER, TipoComando.PZEM_LER, TipoComando.PZEM_ZERAR_ENERGIA,
                TipoComando.PZEM_LIMITE_POTENCIA, TipoComando.RESET_REMOTO, TipoComando.BRUTO);
    }

    @Override
    public Set<TipoPeriferico> perifericos() {
        return EnumSet.of(TipoPeriferico.RELE, TipoPeriferico.PZEM004T);
    }

    @Override
    public List<String> observacoes() {
        return List.of(
                "Cada relé usa uma GPIO do módulo (2 a 9) configurada como saída; o nível volta a 0 quando o módulo reinicia.",
                "PZEM-004T: no módulo, AT+GPIOC=0:3:0 (serial transparente em GPIO0/1) e AT+BAUDRATE=9600 — o console passa a 9600.",
                "Resposta AT e Modbus maiores que o DR atual chegam cortadas: use DR3 ou maior.",
                "Intervalo, classe, confirmação e reinício não têm resposta: o comando termina quando o gateway transmite.",
                "O pulso é feito pelo servidor (liga e, ao fim da duração, manda desligar): a duração depende da classe e do intervalo.");
    }

    @Override
    public boolean pulsoPeloServidor() {
        return true;
    }

    @Override
    public boolean usaSeq(TipoComando tipo) {
        return false;
    }

    @Override
    public Downlink codificar(TipoComando tipo, ParametrosComando p, ContextoEquipamento ctx, int seq) {
        return switch (tipo) {
            case RESET_REMOTO, REINICIAR -> Comum.resetRemoto();
            case BRUTO -> Comum.bruto(p);
            case STATUS -> at("AT+VER=?;AT+CLASS=?;AT+CFM=?", "Consultar versão, classe e confirmação");
            case INTERVALO -> {
                long s = exigir(p.segundos(), "Intervalo em segundos", 5, 65535);
                yield binario(new Bytes.Quadro().u8(0x02).u16((int) s).fim(), "Intervalo de envio: " + s + " s");
            }
            case ENVIAR_AGORA -> binario(new byte[] { 0x05 }, "Enviar leitura agora");
            case CONFIRMADO -> {
                if (p.confirmado() == null) {
                    throw invalido("Informe se os uplinks devem ser confirmados.");
                }
                yield binario(new byte[] { 0x04, (byte) (p.confirmado() ? 1 : 0) },
                        "Uplinks " + (p.confirmado() ? "confirmados" : "não confirmados"));
            }
            case CLASSE -> {
                String c = p.classe() == null ? "" : p.classe().trim().toUpperCase();
                if (!c.equals("A") && !c.equals("C")) {
                    throw invalido("Classe: A ou C.");
                }
                yield binario(new byte[] { 0x01, (byte) (c.equals("A") ? 0 : 2) }, "Classe " + c);
            }
            case RELE_DEFINIR -> {
                if (p.acao() == null) {
                    throw invalido("Informe a ação: ligar, desligar ou alternar.");
                }
                if (p.canal() != null) {
                    Periferico r = rele(ctx, p.canal());
                    boolean v = valor(p.acao(), r);
                    yield at("AT+GPIOC=" + r.getGpio() + ":1:0;AT+WPIN=" + r.getGpio() + ":" + (v ? 1 : 0),
                            DriverAdailSilvaV1.acaoTexto(p.acao()) + " " + nomeRele(ctx, p.canal())
                                    + (p.acao() == AcaoRele.ALTERNAR ? " (→ " + (v ? "ligado" : "desligado") + ")" : ""));
                }
                if (ctx.reles().isEmpty()) {
                    throw invalido("Nenhum relé cadastrado neste equipamento.");
                }
                Map<Periferico, Boolean> alvo = new LinkedHashMap<>();
                for (Periferico r : ctx.reles()) {
                    alvo.put(r, valor(p.acao(), r));
                }
                yield at(wpins(alvo), DriverAdailSilvaV1.acaoTexto(p.acao()) + " todos os relés");
            }
            case RELE_PULSO -> {
                int canal = exigir(p.canal(), "Relé", 0, 15);
                Periferico r = rele(ctx, canal);
                long ms = exigir(p.duracaoMs(), "Duração do pulso em ms", 1000, 86_400_000L);
                boolean estado = p.estado() == null || p.estado();
                yield at("AT+GPIOC=" + r.getGpio() + ":1:0;AT+WPIN=" + r.getGpio() + ":" + (estado ? 1 : 0),
                        "Pulso " + (estado ? "ligado" : "desligado") + " de " + Comum.duracao(ms) + " no " + nomeRele(ctx, canal));
            }
            case RELE_MASCARA -> {
                int mascara = exigir(p.mascara(), "Máscara", 1, 0xFFFF);
                int valores = p.valores() == null ? 0 : p.valores();
                Map<Periferico, Boolean> alvo = new LinkedHashMap<>();
                for (int b = 0; b < 16; b++) {
                    if ((mascara & (1 << b)) != 0) {
                        alvo.put(rele(ctx, b), (valores & (1 << b)) != 0);
                    }
                }
                yield at(wpins(alvo), "Relés: " + DriverAdailSilvaV1.mascaraTexto(mascara, valores));
            }
            case RELE_LER -> {
                if (ctx.reles().isEmpty()) {
                    throw invalido("Nenhum relé cadastrado neste equipamento.");
                }
                List<String> cmds = new ArrayList<>();
                for (Periferico r : ctx.reles()) {
                    cmds.add("AT+RPIN=" + gpio(r));
                }
                yield at(juntar(cmds), "Ler relés");
            }
            case PZEM_LER, PZEM_ZERAR_ENERGIA, PZEM_LIMITE_POTENCIA -> {
                int canal = p.canal() == null ? 0 : exigir(p.canal(), "Medidor", 0, 15);
                exigirPeriferico(ctx, TipoPeriferico.PZEM004T, canal);
                int end = endereco(ctx, canal);
                byte[] q;
                String resumo;
                if (tipo == TipoComando.PZEM_LER) {
                    q = Modbus.lerMedicoes(end);
                    resumo = "Ler medidor " + canal;
                } else if (tipo == TipoComando.PZEM_ZERAR_ENERGIA) {
                    q = Modbus.zerarEnergia(end);
                    resumo = "Zerar energia do medidor " + canal;
                } else {
                    int w = exigir(p.watts(), "Limite em W", 0, 23000);
                    q = Modbus.limitePotencia(end, w);
                    resumo = "Limite de potência do medidor " + canal + ": " + w + " W";
                }
                yield new Downlink(PORTA_SERIAL, q, false, EstrategiaConfirmacao.RESPOSTA_MODBUS, resumo + " (Modbus)");
            }
            default -> throw invalido("Comando não suportado pelo firmware Radioenge original.");
        };
    }

    private static Downlink at(String texto, String resumo) {
        byte[] b = texto.getBytes(StandardCharsets.US_ASCII);
        if (b.length > MAX_AT) {
            throw invalido("Comando AT longo demais para um downlink (" + b.length + " de " + MAX_AT
                    + " bytes): divida em mais comandos ou use uma cena.");
        }
        return new Downlink(PORTA_AT, b, false, EstrategiaConfirmacao.RESPOSTA_AT, resumo);
    }

    private static Downlink binario(byte[] b, String resumo) {
        return new Downlink(PORTA_BINARIO, b, false, EstrategiaConfirmacao.TRANSMISSAO, resumo);
    }

    private static Periferico rele(ContextoEquipamento ctx, int canal) {
        exigirPeriferico(ctx, TipoPeriferico.RELE, canal);
        Periferico r = ctx.periferico(TipoPeriferico.RELE, canal).orElseThrow();
        gpio(r);
        return r;
    }

    private static int gpio(Periferico r) {
        if (r.getGpio() == null || r.getGpio() < 2 || r.getGpio() > 9) {
            throw invalido("Informe a GPIO (2 a 9) do relé " + r.getCanal() + " na configuração do equipamento.");
        }
        return r.getGpio();
    }

    private static int endereco(ContextoEquipamento ctx, int canal) {
        Integer e = ctx.periferico(TipoPeriferico.PZEM004T, canal).map(Periferico::getEnderecoModbus).orElse(null);
        return e == null ? Modbus.ENDERECO_GENERICO : e;
    }

    private static boolean valor(AcaoRele acao, Periferico r) {
        return switch (acao) {
            case LIGAR -> true;
            case DESLIGAR -> false;
            case ALTERNAR -> {
                Boolean atual = r.getEstadoReportado() != null ? r.getEstadoReportado() : r.getEstadoDesejado();
                yield atual == null || !atual;
            }
        };
    }

    private static String wpins(Map<Periferico, Boolean> alvo) {
        List<String> cmds = new ArrayList<>();
        alvo.forEach((r, v) -> cmds.add("AT+WPIN=" + gpio(r) + ":" + (v ? 1 : 0)));
        return juntar(cmds);
    }

    private static String juntar(List<String> cmds) {
        return String.join(";", cmds);
    }

    // ── uplinks ──────────────────────────────────────────────────────────────

    @Override
    public Decodificacao decodificar(int porta, byte[] d, ContextoEquipamento ctx) {
        if (d == null || d.length == 0) {
            return null;
        }
        Efeitos ef = new Efeitos();
        ObjectNode o = JsonNodeFactory.instance.objectNode();
        switch (porta) {
            case PORTA_GPIO -> {
                if (d.length < 8) {
                    return null;
                }
                o.put("evento", "GPIO");
                o.put("nivelBateria", Bytes.u8(d, 0));
                o.put("gpio", Bytes.u8(d, 2));
                o.set("entradas", entradas(Bytes.u16(d, 3)));
                o.put("snrDownlink", (int) d[5]);
                o.put("rssiDownlink", (short) Bytes.u16(d, 6));
            }
            case PORTA_KEEPALIVE -> {
                if (d.length < 16) {
                    return null;
                }
                o.put("evento", "KEEPALIVE");
                o.put("nivelBateria", Bytes.u8(d, 0));
                o.set("entradas", entradas(Bytes.u16(d, 3)));
                for (int i = 0; i < 4; i++) {
                    o.put("ad" + i, Bytes.u16(d, 5 + i * 2));
                }
                o.put("snrDownlink", (int) d[13]);
                o.put("rssiDownlink", (short) Bytes.u16(d, 14));
            }
            case PORTA_AT -> {
                ArrayNode linhas = o.putArray("at");
                for (String l : linhas(d)) {
                    linhas.add(l);
                }
            }
            case PORTA_SERIAL -> {
                boolean medicao = false;
                for (Periferico m : ctx.medidores()) {
                    int end = m.getEnderecoModbus() == null ? Modbus.ENDERECO_GENERICO : m.getEnderecoModbus();
                    Modbus.Medicao med = Modbus.lerResposta(d, end);
                    if (med != null) {
                        ef.medicoes.put(m.getCanal(), med);
                        o.putArray("pzem").add(Comum.medicaoJson(m.getCanal(), med));
                        medicao = true;
                        break;
                    }
                }
                if (!medicao) {
                    o.put("serialHex", Bytes.hex(d));
                }
            }
            default -> {
                return null;
            }
        }
        return new Decodificacao(o, ef);
    }

    private static ObjectNode entradas(int mapa) {
        ObjectNode e = JsonNodeFactory.instance.objectNode();
        e.put("mapa", mapa);
        for (int g = 0; g < 10; g++) {
            e.put("gpio" + g, (mapa >> g) & 1);
        }
        return e;
    }

    private static List<String> linhas(byte[] d) {
        String t = new String(d, StandardCharsets.US_ASCII);
        return Arrays.stream(t.split("[\\r\\n]+")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @Override
    public Desfecho avaliar(Comando c, ParametrosComando p, ContextoEquipamento ctx, int porta, byte[] dados) {
        if (dados == null || dados.length == 0) {
            return null;
        }
        byte[] enviado = Base64.getDecoder().decode(c.getDadosBase64());
        if (c.getEstrategia() == EstrategiaConfirmacao.RESPOSTA_AT && porta == PORTA_AT) {
            String texto = new String(dados, StandardCharsets.US_ASCII).trim();
            String up = texto.toUpperCase();
            if (up.contains("BUSY")) {
                return Desfecho.repetir(ResultadoTentativa.OCUPADO, "Módulo ocupado (AT BUSY ERROR)", texto);
            }
            if (up.contains("ERROR")) {
                return Desfecho.falhou("Recusado pelo módulo: " + texto.replace('\n', ' '), texto);
            }
            Efeitos ef = new Efeitos();
            String cmd = new String(enviado, StandardCharsets.US_ASCII);
            Matcher w = WPIN.matcher(cmd);
            while (w.find()) {
                int g = Integer.parseInt(w.group(1));
                canalDaGpio(ctx, g).ifPresent(canal -> ef.reles.put(canal, w.group(2).equals("1")));
            }
            Matcher r = RPIN.matcher(cmd);
            List<String> valores = linhas(dados);
            int i = 0;
            while (r.find() && i < valores.size()) {
                int g = Integer.parseInt(r.group(1));
                String v = valores.get(i++);
                if (v.equals("0") || v.equals("1")) {
                    canalDaGpio(ctx, g).ifPresent(canal -> ef.reles.put(canal, v.equals("1")));
                }
            }
            return Desfecho.executado("Resposta do módulo: " + texto.replace('\n', ' '), texto, ef);
        }
        if (c.getEstrategia() == EstrategiaConfirmacao.RESPOSTA_MODBUS && porta == PORTA_SERIAL) {
            int end = Bytes.u8(enviado, 0);
            if (Modbus.excecao(dados, end)) {
                return Desfecho.falhou("Exceção Modbus do medidor: código " + Bytes.u8(dados, 2), Bytes.hex(dados));
            }
            if (c.getTipo() == TipoComando.PZEM_LER) {
                Modbus.Medicao m = Modbus.lerResposta(dados, end);
                if (m == null) {
                    return null;
                }
                Efeitos ef = new Efeitos();
                int canal = p.canal() == null ? 0 : p.canal();
                ef.medicoes.put(canal, m);
                return Desfecho.executado("Medição recebida", Comum.medicaoJson(canal, m).toString(), ef);
            }
            if (Arrays.equals(dados, enviado)) {
                return Desfecho.executado("Medidor confirmou (eco Modbus)", Bytes.hex(dados), new Efeitos());
            }
        }
        return null;
    }

    private static java.util.Optional<Integer> canalDaGpio(ContextoEquipamento ctx, int gpio) {
        return ctx.reles().stream().filter(r -> r.getGpio() != null && r.getGpio() == gpio).map(Periferico::getCanal).findFirst();
    }
}
