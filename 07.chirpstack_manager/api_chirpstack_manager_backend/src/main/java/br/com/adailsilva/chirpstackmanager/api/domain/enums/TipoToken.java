package br.com.adailsilva.chirpstackmanager.api.domain.enums;

/** Finalidade de um link de uso único enviado por e-mail (tb_token_redefinicao.tipo). */
public enum TipoToken {
    /** "Esqueci a senha". */
    RECUPERACAO_SENHA,
    /** Convite: o usuário define a própria senha no primeiro acesso (confirma o e-mail junto). */
    CONVITE,
    /** Confirmação do endereço de e-mail (usuário criado com senha ou e-mail alterado). */
    VERIFICACAO_EMAIL
}
