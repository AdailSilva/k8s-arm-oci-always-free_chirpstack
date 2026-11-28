package br.com.adailsilva.chirpstackmanager.api.service.sftp;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Vector;
import java.util.function.LongConsumer;

import org.springframework.stereotype.Component;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpProgressMonitor;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;


/**
 * Cliente SFTP do backup (JSch, fork mantido {@code com.github.mwiede:jsch}).
 *
 * <h2>Chave do servidor (host key)</h2>
 * <p>A conexão só segue se a chave pública que o servidor apresenta for a GRAVADA na
 * configuração -- conferida ANTES da autenticação, ou seja, antes de a senha sair daqui.
 * Sem chave gravada, só o "Testar conexão" aceita a primeira chave (e a devolve para a tela
 * mostrar e gravar); um envio de backup nunca confia numa chave desconhecida. A impressão
 * digital segue o formato do OpenSSH ({@code ssh-keygen -lf}): {@code SHA256:<base64>}.</p>
 *
 * <h2>Envio</h2>
 * <p>O arquivo sobe como {@code <nome>.parcial} e só é renomeado no fim: uma queda no meio
 * nunca deixa no destino um backup truncado com cara de backup bom.</p>
 */
@Component
public class SftpCliente {

    private static final int TIMEOUT_CONEXAO_MS = 20_000;
    private static final int TIMEOUT_LEITURA_MS = 120_000;
    /** Sufixo do arquivo em envio; renomeado para o nome final só quando termina. */
    public static final String SUFIXO_PARCIAL = ".parcial";

    /** Dados de uma conexão, já decifrados (vivem só durante a operação). */
    public record Conexao(String host, int porta, String usuario, String senha,
                          String chavePrivada, String fraseChave, String pastaRemota,
                          String fingerprintEsperado) {
        /** Sem segredos: um log descuidado do record não pode vazar senha nem chave. */
        @Override
        public String toString() {
            return usuario + "@" + host + ":" + porta + (pastaRemota == null ? "" : " " + pastaRemota);
        }
    }

    /** Arquivo na pasta remota. */
    public record ArquivoRemoto(String nome, long tamanho, Instant modificadoEm) {}

    /**
     * Abre a sessão. {@code aceitarChaveNova} só é verdadeiro no teste de conexão: sem chave
     * gravada, a primeira apresentada é aceita e devolvida em {@link Sessao#fingerprint()}.
     */
    public Sessao abrir(Conexao c, boolean aceitarChaveNova) {
        if (c.host() == null || c.host().isBlank()) throw new SftpException("Servidor SFTP não configurado.");
        RepositorioDeChave repo = new RepositorioDeChave(c.fingerprintEsperado(), aceitarChaveNova);
        Session sessao = null;
        try {
            JSch jsch = new JSch();
            boolean porChave = c.chavePrivada() != null && !c.chavePrivada().isBlank();
            if (porChave) {
                byte[] frase = c.fraseChave() == null || c.fraseChave().isEmpty()
                        ? null : c.fraseChave().getBytes(StandardCharsets.UTF_8);
                jsch.addIdentity("chirpstack-manager-backup", normalizarChave(c.chavePrivada()).getBytes(StandardCharsets.UTF_8),
                        null, frase);
            }
            sessao = jsch.getSession(c.usuario(), c.host().trim(), c.porta() <= 0 ? 22 : c.porta());
            sessao.setHostKeyRepository(repo);
            sessao.setConfig("StrictHostKeyChecking", "yes");
            sessao.setConfig("PreferredAuthentications", porChave ? "publickey" : "password,keyboard-interactive");
            if (!porChave) {
                sessao.setPassword(c.senha() == null ? "" : c.senha());
                sessao.setUserInfo(new Credencial(c.senha()));
            }
            sessao.setTimeout(TIMEOUT_LEITURA_MS);
            sessao.setServerAliveInterval(30_000);
            sessao.connect(TIMEOUT_CONEXAO_MS);

            ChannelSftp canal = (ChannelSftp) sessao.openChannel("sftp");
            canal.connect(TIMEOUT_CONEXAO_MS);
            return new Sessao(sessao, canal, repo.visto, repo.tipoVisto, c.pastaRemota());
        } catch (JSchException e) {
            if (sessao != null) sessao.disconnect();
            throw traduzir(e, c, repo);
        }
    }

    /** Uma conexão aberta; fechar SEMPRE (try-with-resources). */
    public static final class Sessao implements AutoCloseable {

        private final Session sessao;
        private final ChannelSftp canal;
        private final String fingerprint;
        private final String tipoChave;
        private final String pasta;
        private boolean pastaPronta;

        private Sessao(Session sessao, ChannelSftp canal, String fingerprint, String tipoChave, String pasta) {
            this.sessao = sessao;
            this.canal = canal;
            this.fingerprint = fingerprint;
            this.tipoChave = tipoChave;
            this.pasta = pasta == null ? "" : pasta.trim().replace('\\', '/');
        }

        /** Impressão digital da chave que o servidor apresentou ({@code SHA256:...}). */
        public String fingerprint() {
            return fingerprint;
        }

        /** Tipo da chave do servidor ("ssh-ed25519", "rsa-sha2-512"...). */
        public String tipoChave() {
            return tipoChave;
        }

        /** Pasta remota efetiva (a informada, ou a pasta inicial do usuário quando vazia). */
        public String pastaEfetiva() {
            garantirPasta();
            try {
                return canal.pwd();
            } catch (com.jcraft.jsch.SftpException e) {
                return pasta;
            }
        }

        /** Envia o arquivo; {@code progresso} recebe o total de bytes já enviados. */
        public void enviar(Path arquivo, String nome, LongConsumer progresso) {
            garantirPasta();
            String parcial = nome + SUFIXO_PARCIAL;
            long[] enviados = { 0L };
            try {
                canal.put(arquivo.toString(), parcial, new SftpProgressMonitor() {
                    @Override public void init(int op, String src, String dest, long max) { }
                    @Override public boolean count(long n) {
                        enviados[0] += n;
                        progresso.accept(enviados[0]);
                        return true;
                    }
                    @Override public void end() { }
                }, ChannelSftp.OVERWRITE);
                // Servidores (o OpenSSH do Windows, por exemplo) recusam renomear por cima.
                try { canal.rm(nome); } catch (com.jcraft.jsch.SftpException ignorado) { /* não existia */ }
                canal.rename(parcial, nome);
            } catch (com.jcraft.jsch.SftpException e) {
                try { canal.rm(parcial); } catch (Exception ignorado) { /* melhor esforço */ }
                throw new SftpException("Falha ao gravar " + nome + " no servidor SFTP: " + motivo(e), e);
            }
        }

        /** Arquivos (não pastas) da pasta remota. */
        public List<ArquivoRemoto> listar() {
            garantirPasta();
            List<ArquivoRemoto> out = new ArrayList<>();
            try {
                Vector<ChannelSftp.LsEntry> itens = canal.ls(".");
                for (ChannelSftp.LsEntry e : itens) {
                    SftpATTRS a = e.getAttrs();
                    if (a == null || a.isDir() || ".".equals(e.getFilename()) || "..".equals(e.getFilename())) continue;
                    out.add(new ArquivoRemoto(e.getFilename(), a.getSize(), Instant.ofEpochSecond(a.getMTime())));
                }
            } catch (com.jcraft.jsch.SftpException e) {
                throw new SftpException("Não foi possível listar a pasta remota: " + motivo(e), e);
            }
            return out;
        }

        public void apagar(String nome) {
            garantirPasta();
            try {
                canal.rm(nome);
            } catch (com.jcraft.jsch.SftpException e) {
                throw new SftpException("Não foi possível apagar " + nome + ": " + motivo(e), e);
            }
        }

        /**
         * Entra na pasta remota, criando os níveis que faltarem. Caminho vazio = pasta inicial
         * do usuário. Aceita "/srv/backups/chirpstack-manager", "backups/chirpstack-manager" (relativo à pasta inicial)
         * e, no OpenSSH do Windows, "C:/Backups/ChirpStackManager".
         */
        private void garantirPasta() {
            if (pastaPronta) return;
            try {
                if (!pasta.isEmpty()) {
                    String[] partes = pasta.split("/");
                    StringBuilder atual = new StringBuilder(pasta.startsWith("/") ? "/" : "");
                    for (String p : partes) {
                        if (p.isEmpty()) continue;
                        if (atual.length() > 0 && atual.charAt(atual.length() - 1) != '/') atual.append('/');
                        atual.append(p);
                        String alvo = atual.toString();
                        try {
                            canal.stat(alvo);
                        } catch (com.jcraft.jsch.SftpException naoExiste) {
                            if (naoExiste.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) throw naoExiste;
                            canal.mkdir(alvo);
                        }
                    }
                    canal.cd(pasta);
                }
                pastaPronta = true;
            } catch (com.jcraft.jsch.SftpException e) {
                throw new SftpException("Não foi possível acessar ou criar a pasta remota \"" + pasta + "\": " + motivo(e), e);
            }
        }

        @Override
        public void close() {
            try { canal.disconnect(); } catch (Exception ignorado) { /* fechando */ }
            try { sessao.disconnect(); } catch (Exception ignorado) { /* fechando */ }
        }
    }

    // ── Chave do servidor ────────────────────────────────────────────────────

    /** Impressão digital no formato do OpenSSH: SHA256:&lt;base64 sem '='&gt;. */
    public static String fingerprint(byte[] chave) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(chave);
            return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(h);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    /** Tipo da chave ("ssh-ed25519", "ecdsa-sha2-nistp256"...) lido do próprio blob. */
    static String tipo(byte[] chave) {
        try {
            ByteBuffer b = ByteBuffer.wrap(chave);
            int n = b.getInt();
            if (n <= 0 || n > 64) return "?";
            byte[] t = new byte[n];
            b.get(t);
            return new String(t, StandardCharsets.US_ASCII);
        } catch (Exception e) {
            return "?";
        }
    }

    /**
     * Aceita só a chave gravada. Sem chave gravada, aceita a primeira apenas quando o
     * chamador permitiu (teste de conexão) -- e guarda qual foi.
     */
    private static final class RepositorioDeChave implements HostKeyRepository {

        private final String esperado;
        private final boolean aceitarNova;
        String visto;
        String tipoVisto;

        RepositorioDeChave(String esperado, boolean aceitarNova) {
            this.esperado = esperado == null || esperado.isBlank() ? null : esperado.trim();
            this.aceitarNova = aceitarNova;
        }

        @Override
        public int check(String host, byte[] key) {
            visto = fingerprint(key);
            tipoVisto = tipo(key);
            if (esperado == null) return aceitarNova ? OK : NOT_INCLUDED;
            return esperado.equals(visto) ? OK : CHANGED;
        }

        @Override public void add(HostKey hostkey, UserInfo ui) { /* nada é gravado por aqui */ }
        @Override public void remove(String host, String type) { }
        @Override public void remove(String host, String type, byte[] key) { }
        @Override public String getKnownHostsRepositoryID() { return "chirpstack-manager-backup-sftp"; }
        @Override public HostKey[] getHostKey() { return new HostKey[0]; }
        @Override public HostKey[] getHostKey(String host, String type) { return new HostKey[0]; }
    }

    /** Senha também para servidores que só oferecem keyboard-interactive. Nunca aceita prompts de sim/não. */
    private static final class Credencial implements UserInfo, UIKeyboardInteractive {

        private final String senha;

        Credencial(String senha) { this.senha = senha == null ? "" : senha; }

        @Override public String getPassphrase() { return null; }
        @Override public String getPassword() { return senha; }
        @Override public boolean promptPassword(String message) { return true; }
        @Override public boolean promptPassphrase(String message) { return false; }
        @Override public boolean promptYesNo(String message) { return false; }
        @Override public void showMessage(String message) { }

        @Override
        public String[] promptKeyboardInteractive(String destination, String name, String instruction,
                                                  String[] prompt, boolean[] echo) {
            String[] r = new String[prompt == null ? 0 : prompt.length];
            for (int i = 0; i < r.length; i++) r[i] = senha;
            return r;
        }
    }

    // ── Mensagens ────────────────────────────────────────────────────────────

    private static SftpException traduzir(JSchException e, Conexao c, RepositorioDeChave repo) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        String l = m.toLowerCase(Locale.ROOT);
        String destino = c.host() + ":" + (c.porta() <= 0 ? 22 : c.porta());
        if (l.contains("hostkey has been changed")) {
            return new SftpException("A chave do servidor " + destino + " MUDOU (agora " + repo.visto + "; gravada "
                    + c.fingerprintEsperado() + "). Pode ser o computador reinstalado ou alguém interceptando a conexão. "
                    + "Confira no próprio computador e, se for legítimo, use \"Redefinir chave do servidor\" e teste de novo.",
                    true, e);
        }
        if (l.contains("reject hostkey")) {
            return new SftpException("A chave do servidor " + destino + " ainda não foi confirmada. Use \"Testar conexão\" "
                    + "na aba SFTP para conferir e gravar a chave (" + repo.visto + ").", e);
        }
        if (l.contains("auth fail") || l.contains("auth cancel") || l.contains("userauth fail")) {
            return new SftpException("O servidor " + destino + " recusou o usuário \"" + c.usuario() + "\" com a "
                    + (c.chavePrivada() != null && !c.chavePrivada().isBlank() ? "chave privada informada" : "senha informada")
                    + ".", e);
        }
        if (l.contains("invalid privatekey") || l.contains("invalid private key")) {
            return new SftpException("A chave privada informada não pôde ser lida (formato inválido ou frase-senha errada). "
                    + "Use uma chave OpenSSH/PEM, por exemplo gerada com ssh-keygen -t ed25519.", e);
        }
        if (l.contains("timeout") || l.contains("timed out")) {
            return new SftpException("Tempo esgotado ao conectar em " + destino + ". O computador está ligado e acessível "
                    + "a partir do servidor do sistema (porta liberada no roteador/firewall, VPN ativa)?", e);
        }
        if (l.contains("unknownhost") || l.contains("unknown host")) {
            return new SftpException("Endereço desconhecido: " + c.host() + ". Confira o nome ou IP do computador de destino.", e);
        }
        if (l.contains("connection refused")) {
            return new SftpException("Conexão recusada em " + destino + ". O serviço SSH/SFTP está ativo nessa porta?", e);
        }
        return new SftpException("Falha ao conectar em " + destino + ": " + (m.isBlank() ? e.getClass().getSimpleName() : m), e);
    }

    private static String motivo(com.jcraft.jsch.SftpException e) {
        return switch (e.id) {
            case ChannelSftp.SSH_FX_PERMISSION_DENIED -> "permissão negada para o usuário na pasta remota";
            case ChannelSftp.SSH_FX_NO_SUCH_FILE -> "caminho inexistente";
            case ChannelSftp.SSH_FX_FAILURE -> "o servidor recusou a operação (disco cheio ou nome inválido?)";
            default -> e.getMessage() == null ? ("código " + e.id) : e.getMessage();
        };
    }

    /** Aceita a chave colada com quebras do Windows ou espaços sobrando nas pontas. */
    private static String normalizarChave(String k) {
        String s = k.replace("\r\n", "\n").replace('\r', '\n').trim();
        return s.endsWith("\n") ? s : s + "\n";
    }
}
