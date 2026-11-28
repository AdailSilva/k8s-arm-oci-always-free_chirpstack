package br.com.adailsilva.chirpstackmanager.api.service.email;

import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * O JavaMailSender do sistema, com a senha lida do banco a cada conexão.
 * <p>
 * O {@link JavaMailSenderImpl} chama {@link #getPassword()} a cada envio: a senha cadastrada em
 * Sistema → Conta de e-mail vale na hora, em todas as réplicas. Sem senha cadastrada (ou ilegível),
 * vale a da instalação ({@code spring.mail.password}).
 */
public class JavaMailSenderComSenhaDinamica extends JavaMailSenderImpl {

    private final ObjectProvider<SenhaSmtpVigente> provedor;

    public JavaMailSenderComSenhaDinamica(ObjectProvider<SenhaSmtpVigente> provedor) {
        this.provedor = provedor;
    }

    /** A senha da instalação, ignorando o cadastro. */
    public String getSenhaInstalacao() {
        return super.getPassword();
    }

    @Override
    public String getPassword() {
        SenhaSmtpVigente p = provedor.getIfAvailable();
        if (p != null) {
            Optional<String> cadastrada = p.senhaCadastrada();
            if (cadastrada.isPresent()) {
                return cadastrada.get();
            }
        }
        return super.getPassword();
    }
}
