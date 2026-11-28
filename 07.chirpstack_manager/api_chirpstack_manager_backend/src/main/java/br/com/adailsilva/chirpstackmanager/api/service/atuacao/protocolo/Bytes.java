package br.com.adailsilva.chirpstackmanager.api.service.atuacao.protocolo;

import java.util.HexFormat;

/** Apoio para montar e ler quadros binários (big-endian). */
public final class Bytes {

    private static final HexFormat HEX = HexFormat.of();

    private Bytes() {
    }

    public static int u8(byte[] b, int i) {
        return b[i] & 0xFF;
    }

    public static int u16(byte[] b, int i) {
        return (u8(b, i) << 8) | u8(b, i + 1);
    }

    public static long u32(byte[] b, int i) {
        return ((long) u16(b, i) << 16) | u16(b, i + 2);
    }

    public static String hex(byte[] b) {
        return b == null ? "" : HEX.formatHex(b).toUpperCase();
    }

    public static byte[] deHex(String s) {
        String limpo = s == null ? "" : s.replaceAll("[\\s:]", "");
        return HEX.parseHex(limpo);
    }

    /** Construtor de quadro em big-endian. */
    public static final class Quadro {
        private final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

        public Quadro u8(int v) {
            out.write(v & 0xFF);
            return this;
        }

        public Quadro u16(int v) {
            return u8(v >>> 8).u8(v);
        }

        public Quadro u32(long v) {
            return u16((int) (v >>> 16)).u16((int) v);
        }

        public Quadro bytes(byte[] b) {
            out.writeBytes(b);
            return this;
        }

        public byte[] fim() {
            return out.toByteArray();
        }
    }
}
