package br.com.adailsilva.chirpstackmanager.api.service.sftp;

/**
 * Falha numa operação SFTP, já com a mensagem pronta para a tela.
 *
 * <p>{@code chaveDoServidorMudou} separa o caso que exige AÇÃO DA PESSOA (conferir o
 * computador de destino e, se for legítimo, redefinir a chave na aba SFTP) de uma falha
 * passageira de rede. A chave diferente pode ser um computador reinstalado -- ou alguém no
 * meio do caminho; o sistema nunca a aceita sozinho.</p>
 */
public class SftpException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final boolean chaveDoServidorMudou;

    public SftpException(String mensagem) {
        this(mensagem, false, null);
    }

    public SftpException(String mensagem, Throwable causa) {
        this(mensagem, false, causa);
    }

    public SftpException(String mensagem, boolean chaveDoServidorMudou, Throwable causa) {
        super(mensagem, causa);
        this.chaveDoServidorMudou = chaveDoServidorMudou;
    }

    public boolean isChaveDoServidorMudou() {
        return chaveDoServidorMudou;
    }
}
