package br.com.adailsilva.chirpstackmanager.api.atuacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.ParametrosComando;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Comando;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Equipamento;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Periferico;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.AcaoRele;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.ModeloFirmware;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoComando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoPeriferico;
import br.com.adailsilva.chirpstackmanager.api.exception.BusinessException;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.ContextoEquipamento;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.Decodificacao;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.Desfecho;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.Downlink;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.DriverAdailSilvaV1;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.DriverRadioengeOriginal;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.protocolo.Bytes;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.protocolo.Modbus;

/**
 * Vetores de teste do protocolo ADAILSILVA_V1 (docs/protocolo/PROTOCOLO_ADAILSILVA_V1.md, seção 6),
 * quadros Modbus do PZEM-004T e comandos AT do Radioenge.
 */
class ProtocoloTests {

    private final DriverAdailSilvaV1 v1 = new DriverAdailSilvaV1();
    private final DriverRadioengeOriginal radioenge = new DriverRadioengeOriginal();

    private static Periferico periferico(TipoPeriferico t, int canal, Integer gpio) {
        Periferico p = new Periferico();
        p.setTipo(t);
        p.setCanal(canal);
        p.setNome(t + " " + canal);
        p.setGpio(gpio);
        return p;
    }

    private static ContextoEquipamento ctx(ModeloFirmware m) {
        Equipamento e = new Equipamento();
        e.setModelo(m);
        return new ContextoEquipamento(e, List.of(periferico(TipoPeriferico.RELE, 0, 3), periferico(TipoPeriferico.RELE, 1, 4),
                periferico(TipoPeriferico.PZEM004T, 0, null)));
    }

    private static String b64(Downlink d) {
        return Base64.getEncoder().encodeToString(d.dados());
    }

    @Test
    void downlinksV1() {
        ContextoEquipamento c = ctx(ModeloFirmware.ADAILSILVA_V1);
        assertThat(b64(v1.codificar(TipoComando.RELE_DEFINIR, ParametrosComando.rele(0, AcaoRele.LIGAR), c, 7))).isEqualTo("ByAAAQ==");
        assertThat(b64(v1.codificar(TipoComando.RELE_PULSO,
                new ParametrosComando(1, null, true, 5000L, null, null, null, null, null, null, null, null, null), c, 8)))
                .isEqualTo("CCEBAQAAE4g=");
        assertThat(b64(v1.codificar(TipoComando.RELE_MASCARA, ParametrosComando.mascara(3, 1), c, 9))).isEqualTo("CSIAAwAB");
        assertThat(b64(v1.codificar(TipoComando.PZEM_LER, ParametrosComando.vazio(), c, 10))).isEqualTo("CjAA");
        assertThat(b64(v1.codificar(TipoComando.INTERVALO,
                new ParametrosComando(null, null, null, null, null, null, 300L, null, null, null, null, null, null), c, 11)))
                .isEqualTo("CwIAAAEs");
        assertThat(v1.codificar(TipoComando.RELE_DEFINIR, ParametrosComando.rele(0, AcaoRele.LIGAR), c, 7).porta()).isEqualTo(10);
        // Relé inexistente
        assertThatThrownBy(() -> v1.codificar(TipoComando.RELE_DEFINIR, ParametrosComando.rele(5, AcaoRele.LIGAR), c, 1))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Relé 5");
    }

    @Test
    void telemetriaV1() {
        byte[] d = Base64.getDecoder().decode("AQADAgABEAASCPwAAAH0AAAEsAAAMDkCWF8AMQABAQ==");
        Decodificacao dec = v1.decodificar(20, d, ctx(ModeloFirmware.ADAILSILVA_V1));
        assertThat(dec.efeitos().reles).containsEntry(0, true).containsEntry(1, false);
        assertThat(dec.efeitos().boot).isTrue();
        Modbus.Medicao m = dec.efeitos().medicoes.get(0);
        assertThat(m.tensao()).isEqualByComparingTo("230.0");
        assertThat(m.corrente()).isEqualByComparingTo("0.5");
        assertThat(m.potencia()).isEqualByComparingTo("120.0");
        assertThat(m.energia()).isEqualTo(12345);
        assertThat(m.frequencia()).isEqualByComparingTo("60.0");
        assertThat(m.fp()).isEqualByComparingTo("0.95");
        assertThat(dec.objeto().path("pzem").get(0).path("potencia").decimalValue()).isEqualByComparingTo(new BigDecimal("120.0"));
        assertThat(dec.objeto().path("boot").path("motivoNome").asString()).isEqualTo("ENERGIZACAO");
        // Tipo desconhecido no meio é pulado pelo tamanho
        byte[] comNovo = Bytes.deHex("7F0002AABB" + "010003020002");
        assertThat(v1.decodificar(20, comNovo, ctx(ModeloFirmware.ADAILSILVA_V1)).efeitos().reles).containsEntry(1, true);
    }

    @Test
    void respostaV1ProvaOComando() {
        Comando c = new Comando();
        c.setTipo(TipoComando.RELE_DEFINIR);
        c.setSeq(7);
        c.setEstrategia(br.com.adailsilva.chirpstackmanager.api.domain.enums.EstrategiaConfirmacao.RESPOSTA_V1);
        ContextoEquipamento x = ctx(ModeloFirmware.ADAILSILVA_V1);
        Desfecho ok = v1.avaliar(c, ParametrosComando.vazio(), x, 11, Base64.getDecoder().decode("ByAAAgAB"));
        assertThat(ok.tipo()).isEqualTo(Desfecho.Tipo.EXECUTADO);
        assertThat(ok.efeitos().reles).containsEntry(0, true).containsEntry(1, false);
        // Outro seq: não é deste comando
        assertThat(v1.avaliar(c, ParametrosComando.vazio(), x, 11, Bytes.deHex("08200002 0001"))).isNull();
        // Ocupado: repete; parâmetro inválido: falha
        assertThat(v1.avaliar(c, ParametrosComando.vazio(), x, 11, Bytes.deHex("072005")).tipo()).isEqualTo(Desfecho.Tipo.REPETIR);
        assertThat(v1.avaliar(c, ParametrosComando.vazio(), x, 11, Bytes.deHex("072002")).tipo()).isEqualTo(Desfecho.Tipo.FALHOU);
    }

    @Test
    void modbusPzem() {
        assertThat(Bytes.hex(Modbus.lerMedicoes(0xF8))).isEqualTo("F8040000000A6464");
        assertThat(Bytes.hex(Modbus.zerarEnergia(0xF8))).isEqualTo("F842C241");
        assertThat(Bytes.hex(Modbus.limitePotencia(0xF8, 2300))).isEqualTo("F806000108FCCBE2");
        // Resposta: 230,0 V; 0,500 A; 120,0 W; 12345 Wh; 60,0 Hz; FP 0,95 (32 bits com palavra baixa primeiro)
        byte[] sem = Bytes.deHex("F80414" + "08FC" + "01F4" + "0000" + "04B0" + "0000" + "3039" + "0000" + "0258" + "005F" + "0000");
        int crc = Modbus.crc(sem, sem.length);
        byte[] resp = java.util.Arrays.copyOf(sem, sem.length + 2);
        resp[sem.length] = (byte) crc;
        resp[sem.length + 1] = (byte) (crc >>> 8);
        Modbus.Medicao m = Modbus.lerResposta(resp, 0xF8);
        assertThat(m).isNotNull();
        assertThat(m.corrente()).isEqualByComparingTo("0.5");
        assertThat(m.energia()).isEqualTo(12345);
        resp[5] ^= 1;
        assertThat(Modbus.lerResposta(resp, 0xF8)).isNull(); // CRC errado
    }

    @Test
    void radioengeAt() {
        ContextoEquipamento c = ctx(ModeloFirmware.RADIOENGE_ORIGINAL);
        Downlink d = radioenge.codificar(TipoComando.RELE_DEFINIR, ParametrosComando.rele(1, AcaoRele.LIGAR), c, 0);
        assertThat(d.porta()).isEqualTo(103);
        assertThat(new String(d.dados())).isEqualTo("AT+GPIOC=4:1:0;AT+WPIN=4:1");
        Downlink m = radioenge.codificar(TipoComando.RELE_MASCARA, ParametrosComando.mascara(3, 2), c, 0);
        assertThat(new String(m.dados())).isEqualTo("AT+WPIN=3:0;AT+WPIN=4:1");

        Comando cmd = new Comando();
        cmd.setTipo(TipoComando.RELE_MASCARA);
        cmd.setEstrategia(m.estrategia());
        cmd.setDadosBase64(Base64.getEncoder().encodeToString(m.dados()));
        Desfecho ok = radioenge.avaliar(cmd, ParametrosComando.mascara(3, 2), c, 103, "AT OK\nAT OK".getBytes());
        assertThat(ok.tipo()).isEqualTo(Desfecho.Tipo.EXECUTADO);
        assertThat(ok.efeitos().reles).containsEntry(0, false).containsEntry(1, true);
        assertThat(radioenge.avaliar(cmd, ParametrosComando.vazio(), c, 103, "AT ERROR".getBytes()).tipo()).isEqualTo(Desfecho.Tipo.FALHOU);

        Downlink pz = radioenge.codificar(TipoComando.PZEM_LER, ParametrosComando.vazio(), c, 0);
        assertThat(pz.porta()).isEqualTo(4);
        assertThat(Bytes.hex(pz.dados())).isEqualTo("F8040000000A6464");
        // Intervalo pela porta binária (102): 0x02 + u16
        assertThat(Bytes.hex(radioenge.codificar(TipoComando.INTERVALO,
                new ParametrosComando(null, null, null, null, null, null, 600L, null, null, null, null, null, null), c, 0).dados()))
                .isEqualTo("020258");
        assertThat(Instant.now()).isNotNull();
    }
}
