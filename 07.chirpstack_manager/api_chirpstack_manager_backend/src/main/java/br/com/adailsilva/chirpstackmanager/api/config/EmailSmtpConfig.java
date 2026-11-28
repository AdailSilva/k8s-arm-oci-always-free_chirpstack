package br.com.adailsilva.chirpstackmanager.api.config;

import java.util.Properties;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.adailsilva.chirpstackmanager.api.service.email.JavaMailSenderComSenhaDinamica;
import br.com.adailsilva.chirpstackmanager.api.service.email.SenhaSmtpVigente;

/**
 * Substitui o JavaMailSender da auto-configuração pelo de senha dinâmica. Os {@code spring.mail.*}
 * continuam valendo (servidor, porta, conta, timeouts); a senha passa a vir da tela quando cadastrada.
 */
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class EmailSmtpConfig {

    @Bean
    public JavaMailSenderComSenhaDinamica mailSender(MailProperties props, ObjectProvider<SenhaSmtpVigente> provedor) {
        JavaMailSenderComSenhaDinamica s = new JavaMailSenderComSenhaDinamica(provedor);
        s.setHost(props.getHost());
        if (props.getPort() != null) {
            s.setPort(props.getPort());
        }
        s.setUsername(props.getUsername());
        s.setPassword(props.getPassword());
        if (props.getProtocol() != null) {
            s.setProtocol(props.getProtocol());
        }
        if (props.getDefaultEncoding() != null) {
            s.setDefaultEncoding(props.getDefaultEncoding().name());
        }
        Properties extras = new Properties();
        extras.putAll(props.getProperties());
        s.setJavaMailProperties(extras);
        return s;
    }
}
