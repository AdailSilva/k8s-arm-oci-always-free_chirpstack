package br.com.adailsilva.chirpstackmanager.api.service.email;

import java.util.Optional;

/** Quem sabe a senha SMTP cadastrada na tela (vazio = usar a da instalação). */
public interface SenhaSmtpVigente {

    Optional<String> senhaCadastrada();
}
