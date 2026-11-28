package br.com.adailsilva.chirpstackmanager.api.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.EmailDtos.AlterarSenhaEmailRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.EmailDtos.ContaEmailResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.ConfiguracaoEmail;
import br.com.adailsilva.chirpstackmanager.api.exception.BusinessException;
import br.com.adailsilva.chirpstackmanager.api.repository.ConfiguracaoEmailRepository;
import br.com.adailsilva.chirpstackmanager.api.security.CifradorSegredos;
import br.com.adailsilva.chirpstackmanager.api.service.email.EmailService;
import br.com.adailsilva.chirpstackmanager.api.service.email.JavaMailSenderComSenhaDinamica;
import br.com.adailsilva.chirpstackmanager.api.service.email.RecifradorSementes;
import br.com.adailsilva.chirpstackmanager.api.service.email.SenhaSmtpVigente;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;

/**
 * SISTEMA → CONTA DE E-MAIL. Troca a senha de app do SMTP sem nova versão do sistema. A senha fica
 * CIFRADA em {@code tb_configuracao_email} e é lida a cada envio; nunca volta para a tela nem vai para
 * o log. Por padrão só grava se o servidor aceitar a senha: uma senha errada derrubaria todos os
 * envios (alertas, redefinição de senha, teste da conta).
 */
@Service
public class ConfiguracaoEmailService implements SenhaSmtpVigente {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracaoEmailService.class);
    private static final String FINALIDADE = RecifradorSementes.FIN_SMTP;
    private static final int MIN = 8;
    private static final int MAX = 200;

    private final ConfiguracaoEmailRepository repository;
    private final CifradorSegredos cifrador;
    private final JavaMailSenderComSenhaDinamica mailSender;
    private final EmailService email;
    private final ZoneId fuso;

    public ConfiguracaoEmailService(ConfiguracaoEmailRepository repository, CifradorSegredos cifrador,
                                    JavaMailSenderComSenhaDinamica mailSender, EmailService email,
                                    @Value("${app.fuso-horario:America/Fortaleza}") String fuso) {
        this.repository = repository;
        this.cifrador = cifrador;
        this.mailSender = mailSender;
        this.email = email;
        this.fuso = ZoneId.of(fuso);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> senhaCadastrada() {
        try {
            return repository.findById(ConfiguracaoEmail.ID_UNICO).map(c -> cifrador.decifrar(c.getSenhaAppCifrada(), FINALIDADE));
        } catch (IllegalStateException e) {
            log.error("Senha SMTP cadastrada ilegível (a chave de cifragem mudou?). Usando a senha da instalação. "
                    + "Informe a senha de novo em Sistema > Conta de e-mail.");
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public ContaEmailResponse situacao() {
        Optional<ConfiguracaoEmail> linha = repository.findById(ConfiguracaoEmail.ID_UNICO);
        String origem = linha.isEmpty() ? "INSTALACAO" : legivel(linha.get()) ? "SISTEMA" : "SISTEMA_ILEGIVEL";
        return new ContaEmailResponse(mailSender.getHost(), mailSender.getPort(), mailSender.getUsername(), origem,
                linha.map(ConfiguracaoEmail::getAtualizadoEm).orElse(null),
                linha.map(ConfiguracaoEmail::getAtualizadoPor).orElse(null), cifrador.isChaveDedicada());
    }

    public void testar(String candidata) {
        conectar(candidata == null || candidata.isBlank() ? mailSender.getPassword() : normalizar(candidata));
    }

    @Transactional
    public ContaEmailResponse alterar(AlterarSenhaEmailRequest req, String usuario) {
        String senha = normalizar(req.senha());
        if (senha.length() < MIN || senha.length() > MAX) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "A senha de app deve ter entre " + MIN + " e " + MAX + " caracteres.");
        }
        if (!senha.equals(normalizar(req.confirmacao()))) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "A confirmação não confere com a nova senha.");
        }
        if (!Boolean.FALSE.equals(req.testarAntes())) {
            conectar(senha);
        }
        ConfiguracaoEmail c = repository.findById(ConfiguracaoEmail.ID_UNICO).orElseGet(() -> {
            ConfiguracaoEmail n = new ConfiguracaoEmail();
            n.setId(ConfiguracaoEmail.ID_UNICO);
            return n;
        });
        c.setSenhaAppCifrada(cifrador.cifrar(senha, FINALIDADE));
        c.setAtualizadoEm(Instant.now());
        c.setAtualizadoPor(usuario);
        repository.save(c);
        log.info("Senha de app do SMTP alterada por {}.", usuario);
        return situacao();
    }

    @Transactional
    public ContaEmailResponse remover(String usuario) {
        repository.findById(ConfiguracaoEmail.ID_UNICO).ifPresent(repository::delete);
        log.info("Senha de app do SMTP cadastrada removida por {}: volta a senha da instalação.", usuario);
        return situacao();
    }

    public String enviarTeste(String destinatario) {
        if (destinatario == null || !destinatario.contains("@")) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Seu usuário não tem um e-mail válido para o teste.");
        }
        try {
            email.enviarOuFalhar(new String[] {destinatario}, "Teste de envio de e-mail",
                    email.corpoTeste(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(fuso).format(Instant.now()),
                            mailSender.getUsername(), mailSender.getHost() + ":" + mailSender.getPort()));
            return destinatario;
        } catch (Exception e) {
            log.warn("Falha no e-mail de teste para {}: {}", destinatario, e.getMessage());
            throw new BusinessException(HttpStatus.BAD_GATEWAY, traduzir(e));
        }
    }

    private boolean legivel(ConfiguracaoEmail c) {
        try {
            cifrador.decifrar(c.getSenhaAppCifrada(), FINALIDADE);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private void conectar(String senha) {
        JavaMailSenderImpl t = new JavaMailSenderImpl();
        t.setHost(mailSender.getHost());
        t.setPort(mailSender.getPort());
        t.setUsername(mailSender.getUsername());
        t.setPassword(senha);
        t.setProtocol(mailSender.getProtocol());
        t.setJavaMailProperties(mailSender.getJavaMailProperties());
        try {
            t.testConnection();
        } catch (Exception e) {
            log.warn("Teste de conexão SMTP recusado ({}:{}): {}", t.getHost(), t.getPort(), e.getMessage());
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, traduzir(e));
        }
    }

    private String traduzir(Throwable e) {
        for (Throwable t = e; t != null && t.getCause() != t; t = t.getCause()) {
            if (t instanceof AuthenticationFailedException) {
                return "O servidor de e-mail recusou a senha da conta " + mailSender.getUsername()
                        + ". Confira se é a senha de app gerada para essa conta (e não a senha de login).";
            }
        }
        String detalhe = e instanceof MessagingException && e.getMessage() != null ? " (" + e.getMessage() + ")" : "";
        return "Não foi possível conectar ao servidor de e-mail " + mailSender.getHost() + ":" + mailSender.getPort()
                + detalhe + ".";
    }

    /** O Gmail mostra a senha de app em blocos ("abcd efgh ..."): os espaços não fazem parte dela. */
    private static String normalizar(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "");
    }
}
