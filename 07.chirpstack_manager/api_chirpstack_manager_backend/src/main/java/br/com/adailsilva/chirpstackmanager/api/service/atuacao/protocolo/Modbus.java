package br.com.adailsilva.chirpstackmanager.api.service.atuacao.protocolo;

import java.math.BigDecimal;

/**
 * Quadros Modbus RTU do medidor PZEM-004T v3 (9600 8N1).
 * <p>
 * Registradores de entrada: 0 tensão (0,1 V); 1–2 corrente (0,001 A); 3–4 potência (0,1 W);
 * 5–6 energia (Wh); 7 frequência (0,1 Hz); 8 fator de potência (0,01); 9 alarme. Os valores de
 * 32 bits vêm com a palavra baixa primeiro. CRC-16 Modbus (0xA001, início 0xFFFF), byte baixo primeiro.
 */
public final class Modbus {

    public static final int ENDERECO_GENERICO = 0xF8;
    public static final int FUNCAO_LER_ENTRADAS = 0x04;
    public static final int FUNCAO_ESCREVER_REGISTRADOR = 0x06;
    public static final int FUNCAO_ZERAR_ENERGIA = 0x42;

    private Modbus() {
    }

    public static int crc(byte[] b, int len) {
        int c = 0xFFFF;
        for (int i = 0; i < len; i++) {
            c ^= b[i] & 0xFF;
            for (int k = 0; k < 8; k++) {
                c = (c & 1) != 0 ? (c >>> 1) ^ 0xA001 : c >>> 1;
            }
        }
        return c;
    }

    private static byte[] comCrc(byte[] semCrc) {
        int c = crc(semCrc, semCrc.length);
        byte[] r = java.util.Arrays.copyOf(semCrc, semCrc.length + 2);
        r[semCrc.length] = (byte) c;
        r[semCrc.length + 1] = (byte) (c >>> 8);
        return r;
    }

    public static boolean crcValido(byte[] b) {
        if (b == null || b.length < 4) {
            return false;
        }
        int c = crc(b, b.length - 2);
        return (b[b.length - 2] & 0xFF) == (c & 0xFF) && (b[b.length - 1] & 0xFF) == (c >>> 8);
    }

    public static byte[] lerMedicoes(int endereco) {
        return comCrc(new Bytes.Quadro().u8(endereco).u8(FUNCAO_LER_ENTRADAS).u16(0).u16(10).fim());
    }

    public static byte[] zerarEnergia(int endereco) {
        return comCrc(new Bytes.Quadro().u8(endereco).u8(FUNCAO_ZERAR_ENERGIA).fim());
    }

    public static byte[] limitePotencia(int endereco, int watts) {
        return comCrc(new Bytes.Quadro().u8(endereco).u8(FUNCAO_ESCREVER_REGISTRADOR).u16(1).u16(watts).fim());
    }

    /** Medição já convertida para unidades. */
    public record Medicao(BigDecimal tensao, BigDecimal corrente, BigDecimal potencia, long energia,
                          BigDecimal frequencia, BigDecimal fp, boolean alarme) {
    }

    /** Resposta da leitura (25 bytes: endereço, 0x04, 0x14, 20 bytes, CRC) ou {@code null} se não for uma. */
    public static Medicao lerResposta(byte[] b, int endereco) {
        if (b == null || b.length != 25 || Bytes.u8(b, 0) != endereco || Bytes.u8(b, 1) != FUNCAO_LER_ENTRADAS
                || Bytes.u8(b, 2) != 20 || !crcValido(b)) {
            return null;
        }
        int[] r = new int[10];
        for (int i = 0; i < 10; i++) {
            r[i] = Bytes.u16(b, 3 + i * 2);
        }
        long corrente = ((long) r[2] << 16) | r[1];
        long potencia = ((long) r[4] << 16) | r[3];
        long energia = ((long) r[6] << 16) | r[5];
        return new Medicao(BigDecimal.valueOf(r[0], 1), BigDecimal.valueOf(corrente, 3), BigDecimal.valueOf(potencia, 1),
                energia, BigDecimal.valueOf(r[7], 1), BigDecimal.valueOf(r[8], 2), r[9] != 0);
    }

    /** Resposta de exceção Modbus (função | 0x80) para este endereço. */
    public static boolean excecao(byte[] b, int endereco) {
        return b != null && b.length == 5 && Bytes.u8(b, 0) == endereco && (Bytes.u8(b, 1) & 0x80) != 0 && crcValido(b);
    }
}
