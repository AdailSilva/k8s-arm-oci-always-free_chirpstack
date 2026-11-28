package br.com.adailsilva.chirpstackmanager.api.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.adailsilva.chirpstackmanager.api.repository.ConfiguracaoEmailRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.GoogleDriveConfigRepository;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.ConfiguracaoEmail;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.GoogleDriveConfig;
import br.com.adailsilva.chirpstackmanager.api.security.CifradorSegredos;

/**
 * Troca as sementes gravadas pela carga geral ({@code v0:}) pela cifra com a chave deste cluster
 * ({@code v1:}, AES-256-GCM), na primeira subida: senha de app do SMTP e Client Secret do Google
 * Drive. Depois disso o banco guarda só a cifra. Sem semente, não faz nada. Rodar em mais de uma
 * réplica ao mesmo tempo é inofensivo: todas gravam uma cifra válida.
 */
@Component
public class RecifradorSementes {

    private static final Logger log = LoggerFactory.getLogger(RecifradorSementes.class);

    /** Mesmas finalidades (AAD) de ConfiguracaoEmailService e GoogleDriveService. */
    public static final String FIN_SMTP = "smtp.senha-app";
    public static final String FIN_DRIVE_SECRET = "google-drive.client-secret";

    private final ConfiguracaoEmailRepository email;
    private final GoogleDriveConfigRepository drive;
    private final CifradorSegredos cifrador;

    public RecifradorSementes(ConfiguracaoEmailRepository email, GoogleDriveConfigRepository drive,
                              CifradorSegredos cifrador) {
        this.email = email;
        this.drive = drive;
        this.cifrador = cifrador;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recifrar() {
        try {
            email.findById(ConfiguracaoEmail.ID_UNICO)
                    .filter(c -> CifradorSegredos.isSemente(c.getSenhaAppCifrada()))
                    .ifPresent(c -> {
                        c.setSenhaAppCifrada(cifrador.cifrar(cifrador.decifrar(c.getSenhaAppCifrada(), FIN_SMTP), FIN_SMTP));
                        email.save(c);
                        log.info("Senha SMTP da carga recifrada com a chave do cluster (v0 -> v1).");
                    });
        } catch (Exception e) {
            log.warn("Não foi possível recifrar a semente da senha SMTP: {}", e.getMessage());
        }
        try {
            drive.findById(GoogleDriveConfig.ID)
                    .filter(g -> CifradorSegredos.isSemente(g.getClientSecretCifrado()))
                    .ifPresent(g -> {
                        g.setClientSecretCifrado(cifrador.cifrar(
                                cifrador.decifrar(g.getClientSecretCifrado(), FIN_DRIVE_SECRET), FIN_DRIVE_SECRET));
                        drive.save(g);
                        log.info("Client Secret do Google Drive da carga recifrado com a chave do cluster (v0 -> v1).");
                    });
        } catch (Exception e) {
            log.warn("Não foi possível recifrar a semente do Client Secret do Google Drive: {}", e.getMessage());
        }
    }
}
