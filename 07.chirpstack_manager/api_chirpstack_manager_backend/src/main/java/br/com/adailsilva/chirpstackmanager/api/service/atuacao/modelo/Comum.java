package br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo;

import java.math.BigDecimal;
import java.util.HexFormat;

import org.springframework.http.HttpStatus;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.ParametrosComando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.EstrategiaConfirmacao;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoPeriferico;
import br.com.adailsilva.chirpstackmanager.api.exception.BusinessException;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.protocolo.Bytes;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.protocolo.Modbus;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/** Partes comuns aos drivers: reset remoto, downlink livre, validação e JSON das medições. */
final class Comum {

    /** Porta e comando de 8 bytes do reset remoto dos firmwares base (AD A1 DE AD BE EF 0D 0A). */
    static final int PORTA_RESET = 101;
    static final byte[] COMANDO_RESET = HexFormat.of().parseHex("ADA1DEADBEEF0D0A");

    private Comum() {
    }

    static BusinessException invalido(String mensagem) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, mensagem);
    }

    static Downlink resetRemoto() {
        return new Downlink(PORTA_RESET, COMANDO_RESET.clone(), false, EstrategiaConfirmacao.TRANSMISSAO,
                "Reset remoto (porta 101)");
    }

    static Downlink bruto(ParametrosComando p) {
        if (p.porta() == null || p.porta() < 1 || p.porta() > 223) {
            throw invalido("Porta (fPort): de 1 a 223.");
        }
        byte[] dados;
        try {
            dados = Bytes.deHex(p.dadosHex());
        } catch (IllegalArgumentException e) {
            throw invalido("Bytes em hexadecimal inválidos (ex.: 01 A0 FF).");
        }
        if (dados.length == 0 || dados.length > 242) {
            throw invalido("Informe de 1 a 242 bytes.");
        }
        boolean conf = Boolean.TRUE.equals(p.confirmado());
        return new Downlink(p.porta(), dados, conf,
                conf ? EstrategiaConfirmacao.CONFIRMACAO_LORAWAN : EstrategiaConfirmacao.TRANSMISSAO,
                "Downlink livre na porta " + p.porta() + ": " + Bytes.hex(dados) + (conf ? " (confirmado)" : ""));
    }

    static int exigir(Integer v, String nome, int min, int max) {
        if (v == null || v < min || v > max) {
            throw invalido(nome + ": de " + min + " a " + max + ".");
        }
        return v;
    }

    static long exigir(Long v, String nome, long min, long max) {
        if (v == null || v < min || v > max) {
            throw invalido(nome + ": de " + min + " a " + max + ".");
        }
        return v;
    }

    static void exigirPeriferico(ContextoEquipamento ctx, TipoPeriferico tipo, int canal) {
        if (ctx.periferico(tipo, canal).isEmpty()) {
            throw invalido((tipo == TipoPeriferico.RELE ? "Relé " : "Medidor ") + canal + " não está cadastrado neste equipamento.");
        }
    }

    static String nomeRele(ContextoEquipamento ctx, Integer canal) {
        if (canal == null) {
            return "todos os relés";
        }
        return ctx.periferico(TipoPeriferico.RELE, canal).map(r -> "relé " + canal + " (" + r.getNome() + ")").orElse("relé " + canal);
    }

    static ObjectNode medicaoJson(int canal, Modbus.Medicao m) {
        ObjectNode o = JsonNodeFactory.instance.objectNode();
        o.put("canal", canal);
        o.put("tensao", m.tensao());
        o.put("corrente", m.corrente());
        o.put("potencia", m.potencia());
        o.put("energia", m.energia());
        o.put("frequencia", m.frequencia());
        o.put("fp", m.fp());
        o.put("alarme", m.alarme());
        return o;
    }

    static String duracao(long ms) {
        if (ms % 3_600_000 == 0) {
            return ms / 3_600_000 + " h";
        }
        if (ms % 60_000 == 0) {
            return ms / 60_000 + " min";
        }
        if (ms % 1000 == 0) {
            return ms / 1000 + " s";
        }
        return BigDecimal.valueOf(ms, 3).stripTrailingZeros().toPlainString() + " s";
    }
}
