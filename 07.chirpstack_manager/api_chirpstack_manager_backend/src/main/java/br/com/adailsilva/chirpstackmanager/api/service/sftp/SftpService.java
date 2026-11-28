package br.com.adailsilva.chirpstackmanager.api.service.sftp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.adailsilva.chirpstackmanager.api.config.BackupProperties;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.BackupSftpConfig;
import br.com.adailsilva.chirpstackmanager.api.exception.BusinessException;
import br.com.adailsilva.chirpstackmanager.api.repository.BackupSftpConfigRepository;
import br.com.adailsilva.chirpstackmanager.api.security.CifradorSegredos;
import br.com.adailsilva.chirpstackmanager.api.service.sftp.SftpCliente.ArquivoRemoto;
import br.com.adailsilva.chirpstackmanager.api.service.sftp.SftpCliente.Conexao;
import br.com.adailsilva.chirpstackmanager.api.service.sftp.SftpCliente.Sessao;

/**
 * Backup → SFTP, mesmo padrão dos demais sistemas: os arquivos gerados no servidor seguem para um
 * computador, NAS ou servidor da rede do cliente.
 * <ul>
 *   <li>Autenticação por senha ou por chave privada (OpenSSH/PEM, com frase-senha opcional). Os
 *       segredos ficam cifrados e nunca voltam para a tela.</li>
 *   <li>A chave do servidor é conferida ANTES da autenticação: o primeiro "Testar conexão" mostra a
 *       impressão digital e o ADMIN confirma; um envio de backup nunca aceita chave desconhecida.</li>
 *   <li>O arquivo sobe como {@code .parcial} e só é renomeado no fim.</li>
 *   <li>Retenção própria na pasta remota (dias + mínimo de arquivos por formato).</li>
 * </ul>
 */
@Service
public class SftpService {

    private static final Logger log = LoggerFactory.getLogger(SftpService.class);

    static final String FIN_SENHA = "backup-sftp.senha";
    static final String FIN_CHAVE = "backup-sftp.chave-privada";
    static final String FIN_FRASE = "backup-sftp.frase-chave";

    /** Resultado de um envio: o backup continua válido no servidor mesmo quando o envio falha. */
    public record ResultadoEnvio(boolean sucesso, String mensagem, int enviados, int removidos) {
    }

    public record ConfigResponse(boolean preenchido, boolean pronto, String host, int porta, String usuario,
                                 String autenticacao, boolean senhaDefinida, boolean chaveDefinida,
                                 boolean fraseDefinida, String pastaRemota, String fingerprint, String tipoChave,
                                 boolean enviarManuais, int retencaoDias, int manterMinimo,
                                 boolean chaveCifragemDedicada, Instant ultimoEnvioEm, String ultimoEnvioStatus,
                                 String ultimoEnvioMensagem, Instant atualizadoEm, String atualizadoPor) {
    }

    /**
     * Campos nulos mantêm o valor gravado. Senha, chave e frase vazias também mantêm (para apagar,
     * use "Remover configuração").
     */
    public record ConfigRequest(String host, Integer porta, String usuario, String autenticacao, String senha,
                                String chavePrivada, String fraseChave, String pastaRemota, Boolean enviarManuais,
                                Integer retencaoDias, Integer manterMinimo) {
    }

    /**
     * Resultado do teste. {@code chaveNova}: a chave do servidor ainda não foi confirmada; a tela mostra
     * a impressão digital e pede para conferir antes de gravar.
     */
    public record TesteResponse(boolean sucesso, String mensagem, String fingerprint, String tipoChave,
                                boolean chaveNova, boolean chaveMudou, String pastaEfetiva) {
    }

    private final BackupSftpConfigRepository repository;
    private final SftpCliente cliente;
    private final CifradorSegredos cifrador;
    private final BackupProperties backupProps;
    private final TransactionTemplate tx;

    public SftpService(BackupSftpConfigRepository repository, SftpCliente cliente, CifradorSegredos cifrador,
                       BackupProperties backupProps, TransactionTemplate tx) {
        this.repository = repository;
        this.cliente = cliente;
        this.cifrador = cifrador;
        this.backupProps = backupProps;
        this.tx = tx;
    }

    // ── Configuração ─────────────────────────────────────────────────────────

    public ConfigResponse consultar() {
        BackupSftpConfig c = config();
        return new ConfigResponse(c.isPreenchido(), c.isPronto(), c.getHost(), c.getPorta(), c.getUsuario(),
                c.getAutenticacao(), c.getSenhaCifrada() != null, c.getChavePrivadaCifrada() != null,
                c.getFraseChaveCifrada() != null, c.getPastaRemota(), c.getFingerprint(), c.getTipoChave(),
                c.isEnviarManuais(), c.getRetencaoDias(), c.getManterMinimo(), cifrador.isChaveDedicada(),
                c.getUltimoEnvioEm(), c.getUltimoEnvioStatus(), c.getUltimoEnvioMensagem(), c.getAtualizadoEm(),
                c.getAtualizadoPor());
    }

    public ConfigResponse salvar(ConfigRequest req, String por) {
        tx.executeWithoutResult(st -> {
            BackupSftpConfig c = config();
            String hostAntes = c.getHost();
            int portaAntes = c.getPorta();
            if (req.host() != null) {
                String h = req.host().trim();
                if (h.isEmpty()) {
                    throw erro("Informe o endereço do servidor SFTP (nome ou IP).");
                }
                if (h.length() > 255 || h.contains(" ") || h.contains("/") || h.contains("@")) {
                    throw erro("Endereço inválido: use só o nome ou o IP, sem usuário, porta ou caminho (ex.: 192.168.0.20).");
                }
                c.setHost(h);
            }
            if (req.porta() != null) {
                if (req.porta() < 1 || req.porta() > 65535) {
                    throw erro("Porta SFTP: de 1 a 65535 (o padrão é 22).");
                }
                c.setPorta(req.porta());
            }
            if (req.usuario() != null) {
                String u = req.usuario().trim();
                if (u.isEmpty() || u.length() > 120) {
                    throw erro("Informe o usuário do servidor SFTP (até 120 caracteres).");
                }
                c.setUsuario(u);
            }
            if (req.autenticacao() != null) {
                String a = req.autenticacao().trim().toUpperCase(Locale.ROOT);
                if (!a.equals("SENHA") && !a.equals("CHAVE")) {
                    throw erro("Autenticação inválida: use SENHA ou CHAVE.");
                }
                c.setAutenticacao(a);
            }
            if (req.senha() != null && !req.senha().isEmpty()) {
                if (req.senha().length() > 256) {
                    throw erro("Senha SFTP longa demais (máximo 256 caracteres).");
                }
                c.setSenhaCifrada(cifrador.cifrar(req.senha(), FIN_SENHA));
            }
            if (req.chavePrivada() != null && !req.chavePrivada().isBlank()) {
                String k = req.chavePrivada().trim();
                if (!k.startsWith("-----BEGIN") || k.length() > 20_000) {
                    throw erro("Chave privada inválida: cole o conteúdo completo, começando por \"-----BEGIN\" "
                            + "(ex.: o arquivo id_ed25519 gerado com ssh-keygen).");
                }
                c.setChavePrivadaCifrada(cifrador.cifrar(k, FIN_CHAVE));
            }
            if (req.fraseChave() != null && !req.fraseChave().isEmpty()) {
                c.setFraseChaveCifrada(cifrador.cifrar(req.fraseChave(), FIN_FRASE));
            }
            if (req.pastaRemota() != null) {
                String p = req.pastaRemota().trim().replace('\\', '/');
                if (p.length() > 255 || p.contains("..")) {
                    throw erro("Pasta remota inválida (sem \"..\", até 255 caracteres).");
                }
                c.setPastaRemota(p.isEmpty() ? null : p);
            }
            if (req.enviarManuais() != null) {
                c.setEnviarManuais(req.enviarManuais());
            }
            if (req.retencaoDias() != null) {
                if (req.retencaoDias() < 0 || req.retencaoDias() > 3650) {
                    throw erro("Retenção no SFTP: de 0 (nunca apagar) a 3650 dias.");
                }
                c.setRetencaoDias(req.retencaoDias());
            }
            if (req.manterMinimo() != null) {
                if (req.manterMinimo() < 1 || req.manterMinimo() > 100) {
                    throw erro("Manter no mínimo: de 1 a 100 arquivos.");
                }
                c.setManterMinimo(req.manterMinimo());
            }
            // Outro servidor: a chave confirmada era do anterior.
            if (!eq(hostAntes, c.getHost()) || portaAntes != c.getPorta()) {
                c.setFingerprint(null);
                c.setTipoChave(null);
            }
            if (c.isPorChave() && c.getChavePrivadaCifrada() == null) {
                throw erro("Cole a chave privada para autenticar por chave.");
            }
            if (!c.isPorChave() && c.getSenhaCifrada() == null) {
                throw erro("Informe a senha do usuário SFTP.");
            }
            c.setAtualizadoPor(por);
            repository.save(c);
        });
        return consultar();
    }

    /** Apaga toda a configuração (servidor, credenciais e chave confirmada). */
    public ConfigResponse remover() {
        tx.executeWithoutResult(st -> repository.findById(BackupSftpConfig.ID).ifPresent(repository::delete));
        return consultar();
    }

    /**
     * Conecta, entra (ou cria) a pasta remota, grava e apaga um arquivo de teste. Sem chave confirmada,
     * aceita a chave apresentada só para este teste e a devolve para o ADMIN conferir.
     */
    public TesteResponse testar() {
        BackupSftpConfig c = config();
        if (!c.isPreenchido()) {
            return new TesteResponse(false, "Preencha e salve o servidor, o usuário e a credencial antes de testar.",
                    null, null, false, false, null);
        }
        Path tmp = null;
        boolean chaveNova = c.getFingerprint() == null;
        try (Sessao s = cliente.abrir(conexao(c), chaveNova)) {
            String pasta = s.pastaEfetiva();
            tmp = Files.createTempFile("csm-teste-sftp", ".txt");
            Files.writeString(tmp, "Teste de envio do ChirpStack Manager em " + Instant.now() + "\n");
            String nome = "teste-chirpstack-manager.txt";
            s.enviar(tmp, nome, n -> { });
            s.apagar(nome);
            String msg = chaveNova
                    ? "Conexão e gravação funcionaram. Confira a impressão digital da chave do servidor e confirme para liberar os envios."
                    : "Conexão, gravação e remoção de um arquivo de teste funcionaram em \"" + pasta + "\".";
            if (!chaveNova) {
                registrarResultado("SUCESSO", "Teste concluído.");
            }
            return new TesteResponse(true, msg, s.fingerprint(), s.tipoChave(), chaveNova, false, pasta);
        } catch (SftpException e) {
            if (!chaveNova) {
                registrarResultado("ERRO", e.getMessage());
            }
            return new TesteResponse(false, e.getMessage(), null, null, chaveNova, e.isChaveDoServidorMudou(), null);
        } catch (Exception e) {
            String msg = "Falha no teste: " + e.getMessage();
            return new TesteResponse(false, msg, null, null, chaveNova, false, null);
        } finally {
            apagarSilencioso(tmp);
        }
    }

    /** O ADMIN conferiu a impressão digital mostrada no teste: grava como a chave confiável. */
    public ConfigResponse confirmarChave(String fingerprint, String tipoChave, String por) {
        String f = fingerprint == null ? "" : fingerprint.trim();
        if (!f.matches("^SHA256:[A-Za-z0-9+/]{43}$")) {
            throw erro("Impressão digital inválida. Use \"Testar conexão\" e confirme a chave mostrada.");
        }
        tx.executeWithoutResult(st -> {
            BackupSftpConfig c = config();
            if (!c.isPreenchido()) {
                throw erro("Configure o servidor SFTP antes de confirmar a chave.");
            }
            c.setFingerprint(f);
            c.setTipoChave(tipoChave == null ? null : tipoChave.trim());
            c.setAtualizadoPor(por);
            repository.save(c);
        });
        return consultar();
    }

    /** Esquece a chave confirmada (servidor reinstalado): o próximo teste mostra a nova. */
    public ConfigResponse redefinirChave(String por) {
        tx.executeWithoutResult(st -> {
            BackupSftpConfig c = config();
            c.setFingerprint(null);
            c.setTipoChave(null);
            c.setAtualizadoPor(por);
            repository.save(c);
        });
        return consultar();
    }

    // ── Envio dos backups ────────────────────────────────────────────────────

    public boolean isPronto() {
        return config().isPronto();
    }

    public boolean isEnviarManuais() {
        BackupSftpConfig c = config();
        return c.isPronto() && c.isEnviarManuais();
    }

    /** Envia os arquivos e aplica a retenção remota. Nunca lança: o resultado diz o que houve. */
    public ResultadoEnvio enviar(List<Path> arquivos) {
        BackupSftpConfig c = config();
        if (!c.isPronto()) {
            String msg = c.isPreenchido()
                    ? "A chave do servidor SFTP ainda não foi confirmada: os arquivos ficaram só no servidor do sistema."
                    : "SFTP não configurado: os arquivos ficaram só no servidor do sistema.";
            registrarResultado("ERRO", msg);
            return new ResultadoEnvio(false, msg, 0, 0);
        }
        int enviados = 0;
        try (Sessao s = cliente.abrir(conexao(c), false)) {
            for (Path p : arquivos) {
                s.enviar(p, p.getFileName().toString(), n -> { });
                enviados++;
            }
            int removidos = aplicarRetencao(s, c);
            String msg = enviados + " arquivo(s) enviado(s) para " + c.getUsuario() + "@" + c.getHost() + ":"
                    + s.pastaEfetiva() + (removidos > 0 ? "; " + removidos + " antigo(s) removido(s) pela retenção" : "")
                    + ".";
            registrarResultado("SUCESSO", msg);
            return new ResultadoEnvio(true, msg, enviados, removidos);
        } catch (Exception e) {
            String msg = (e instanceof SftpException || e instanceof BusinessException ? e.getMessage()
                    : "Falha no envio: " + e.getMessage())
                    + (enviados > 0 ? " (" + enviados + " de " + arquivos.size() + " enviado(s))" : "");
            log.warn("Envio SFTP falhou: {}", msg);
            registrarResultado("ERRO", msg);
            return new ResultadoEnvio(false, msg, enviados, 0);
        }
    }

    /** Antes de restaurar o banco: a configuração atual do SFTP deve sobreviver à restauração. */
    public BackupSftpConfig instantaneo() {
        return repository.findById(BackupSftpConfig.ID).map(BackupSftpConfig::copia).orElse(null);
    }

    public void reaplicar(BackupSftpConfig copia) {
        tx.executeWithoutResult(st -> {
            if (copia == null) {
                repository.findById(BackupSftpConfig.ID).ifPresent(repository::delete);
            } else {
                repository.save(copia);
            }
        });
    }

    // ── Internos ─────────────────────────────────────────────────────────────

    private int aplicarRetencao(Sessao s, BackupSftpConfig c) {
        if (c.getRetencaoDias() <= 0) {
            return 0;
        }
        Instant limite = Instant.now().minus(Duration.ofDays(c.getRetencaoDias()));
        String prefixo = backupProps.prefixoArquivo() + "_";
        Map<String, List<ArquivoRemoto>> porFormato = s.listar().stream()
                .filter(a -> a.nome().startsWith(prefixo) && (a.nome().endsWith(".backup") || a.nome().endsWith(".sql")))
                .collect(Collectors.groupingBy(a -> a.nome().substring(a.nome().lastIndexOf('.'))));
        int removidos = 0;
        for (List<ArquivoRemoto> lista : porFormato.values()) {
            List<ArquivoRemoto> ordenada = new ArrayList<>(lista);
            ordenada.sort(Comparator.comparing(ArquivoRemoto::modificadoEm).reversed());
            for (int i = c.getManterMinimo(); i < ordenada.size(); i++) {
                ArquivoRemoto a = ordenada.get(i);
                if (a.modificadoEm().isBefore(limite)) {
                    try {
                        s.apagar(a.nome());
                        removidos++;
                    } catch (SftpException e) {
                        log.warn("Retenção SFTP: {}", e.getMessage());
                    }
                }
            }
        }
        return removidos;
    }

    private Conexao conexao(BackupSftpConfig c) {
        try {
            String senha = c.getSenhaCifrada() == null ? null : cifrador.decifrar(c.getSenhaCifrada(), FIN_SENHA);
            String chave = c.isPorChave() && c.getChavePrivadaCifrada() != null
                    ? cifrador.decifrar(c.getChavePrivadaCifrada(), FIN_CHAVE) : null;
            String frase = c.isPorChave() && c.getFraseChaveCifrada() != null
                    ? cifrador.decifrar(c.getFraseChaveCifrada(), FIN_FRASE) : null;
            return new Conexao(c.getHost(), c.getPorta(), c.getUsuario(), c.isPorChave() ? null : senha, chave, frase,
                    c.getPastaRemota(), c.getFingerprint());
        } catch (IllegalStateException e) {
            throw new SftpException("A credencial SFTP gravada não pode ser lida (a chave de cifragem mudou). "
                    + "Informe a senha ou a chave privada de novo na aba SFTP.", e);
        }
    }

    private BackupSftpConfig config() {
        return repository.findById(BackupSftpConfig.ID).orElseGet(BackupSftpConfig::new);
    }

    private void registrarResultado(String status, String mensagem) {
        try {
            tx.executeWithoutResult(st -> repository.findById(BackupSftpConfig.ID).ifPresent(c -> {
                c.setUltimoEnvioEm(Instant.now());
                c.setUltimoEnvioStatus(status);
                c.setUltimoEnvioMensagem(mensagem);
                repository.save(c);
            }));
        } catch (RuntimeException e) {
            log.warn("Não foi possível registrar o resultado do envio SFTP: {}", e.getMessage());
        }
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b == null ? "" : b);
    }

    private static BusinessException erro(String msg) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, msg);
    }

    private static void apagarSilencioso(Path p) {
        if (p != null) {
            try {
                Files.deleteIfExists(p);
            } catch (Exception ignorado) {
                // arquivo temporário
            }
        }
    }
}
