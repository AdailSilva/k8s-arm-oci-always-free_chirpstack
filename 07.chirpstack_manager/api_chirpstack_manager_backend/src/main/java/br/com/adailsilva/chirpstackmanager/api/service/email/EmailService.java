package br.com.adailsilva.chirpstackmanager.api.service.email;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Envio de e-mails do sistema (alertas, redefinição de senha, teste da conta) pela conta configurada
 * em Sistema → Conta de e-mail. Os corpos usam as cores da marca.
 */
@Service
public class EmailService {

    public static final String SISTEMA = "ChirpStack Manager";
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSenderComSenhaDinamica mailSender;

    public EmailService(JavaMailSenderComSenhaDinamica mailSender) {
        this.mailSender = mailSender;
    }

    public JavaMailSenderComSenhaDinamica sender() {
        return mailSender;
    }

    /** Envia; devolve false (e registra no log) se o servidor recusar. Nunca lança. */
    public boolean enviar(String[] para, String assunto, String html) {
        try {
            enviarOuFalhar(para, assunto, html);
            return true;
        } catch (Exception e) {
            log.warn("Falha ao enviar e-mail \"{}\": {}", assunto, e.getMessage());
            return false;
        }
    }

    public void enviarOuFalhar(String[] para, String assunto, String html) throws Exception {
        enviarOuFalhar(para, assunto, html, null);
    }

    /** Com {@code responderPara}: a resposta da equipe vai direto a quem escreveu (suporte). */
    public void enviarOuFalhar(String[] para, String assunto, String html, String responderPara) throws Exception {
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(msg, false, StandardCharsets.UTF_8.name());
        h.setFrom(mailSender.getUsername(), SISTEMA);
        h.setTo(para);
        if (responderPara != null && !responderPara.isBlank()) {
            h.setReplyTo(responderPara);
        }
        h.setSubject("[" + SISTEMA + "] " + assunto);
        h.setText(html, true);
        mailSender.send(msg);
    }

    // ── corpos ───────────────────────────────────────────────────────────────

    public String corpoAlerta(String severidade, String mensagem, String dispositivo, String devEui, String quando) {
        String cor = switch (severidade) {
            case "CRITICO" -> "#dc2626";
            case "INFO" -> "#2563eb";
            default -> "#d97706";
        };
        return moldura("Alerta " + esc(severidade),
                "<p style=\"margin:0 0 12px;padding:10px 12px;border-left:4px solid " + cor + ";background:#f8fafc\">"
                        + esc(mensagem) + "</p>"
                        + tabela(new String[][] {{"Dispositivo", dispositivo}, {"DevEUI", devEui}, {"Disparado em", quando}})
                        + "<p style=\"margin:16px 0 0\">Reconheça o alerta no painel em <strong>Alertas</strong>.</p>");
    }

    public String corpoRedefinicao(String nome, String link, long minutos) {
        return moldura("Redefinição de senha",
                "<p style=\"margin:0 0 12px\">Olá, " + esc(nome) + ".</p>"
                        + "<p style=\"margin:0 0 16px\">Recebemos um pedido para redefinir a sua senha. O link vale por "
                        + minutos + " minutos e só pode ser usado uma vez.</p>"
                        + "<p style=\"margin:0 0 16px\"><a href=\"" + esc(link) + "\" style=\"display:inline-block;"
                        + "background:#0f766e;color:#ffffff;text-decoration:none;padding:10px 18px;border-radius:6px;"
                        + "font-weight:600\">Criar nova senha</a></p>"
                        + "<p style=\"margin:0;font-size:12px;color:#64748b\">Se não foi você, ignore este e-mail: "
                        + "a sua senha continua a mesma.</p>");
    }

    /** Convite: o usuário define a própria senha (o link também confirma o e-mail). */
    public String corpoConvite(String nome, String convidadoPor, String link, long horas) {
        return moldura("Convite de acesso",
                "<p style=\"margin:0 0 12px\">Olá, " + esc(nome) + ".</p>"
                        + "<p style=\"margin:0 0 16px\">"
                        + (convidadoPor == null ? "Você foi convidado(a)" : esc(convidadoPor) + " convidou você")
                        + " para acessar o <strong>" + SISTEMA
                        + "</strong>. Defina a sua senha pelo botão abaixo. O link vale por " + horas
                        + " horas e só pode ser usado uma vez.</p>"
                        + botao(link, "Definir minha senha")
                        + "<p style=\"margin:0;font-size:12px;color:#64748b\">Se você não esperava este convite, ignore "
                        + "este e-mail.</p>");
    }

    /** Confirmação do endereço de e-mail (usuário criado com senha ou e-mail alterado). */
    public String corpoConfirmacao(String nome, String link, long horas) {
        return moldura("Confirmação de e-mail",
                "<p style=\"margin:0 0 12px\">Olá, " + esc(nome) + ".</p>"
                        + "<p style=\"margin:0 0 16px\">Confirme que este é o seu e-mail para acessar o <strong>" + SISTEMA
                        + "</strong>. O link vale por " + horas + " horas e só pode ser usado uma vez.</p>"
                        + botao(link, "Confirmar e-mail")
                        + "<p style=\"margin:0;font-size:12px;color:#64748b\">Se não foi você, ignore este e-mail.</p>");
    }

    /** Chamado aberto pela tela Suporte. */
    public String corpoSuporte(String assunto, String mensagem, String nome, String email, String perfil,
                               String telefone, String whatsapp, String quando) {
        String digitos = whatsapp == null ? "" : whatsapp.replaceAll("\\D", "");
        String linkWhats = digitos.isEmpty() ? "" : "<p style=\"margin:12px 0 0\"><a href=\"https://wa.me/"
                + (digitos.length() <= 11 ? "55" + digitos : digitos) + "\">Responder pelo WhatsApp</a></p>";
        return moldura("Chamado de suporte",
                tabela(new String[][] {{"Assunto", assunto}, {"De", nome + " <" + email + ">"}, {"Perfil", perfil},
                        {"Telefone", telefone}, {"WhatsApp", whatsapp}, {"Enviado em", quando}})
                        + "<div style=\"margin:16px 0 0;padding:12px;background:#f8fafc;border:1px solid #e5e7eb;"
                        + "border-radius:6px;white-space:pre-wrap\">" + esc(mensagem) + "</div>"
                        + linkWhats
                        + "<p style=\"margin:16px 0 0;font-size:12px;color:#64748b\">Responda este e-mail para falar "
                        + "direto com quem abriu o chamado.</p>");
    }

    private static String botao(String link, String texto) {
        return "<p style=\"margin:0 0 16px\"><a href=\"" + esc(link) + "\" style=\"display:inline-block;"
                + "background:#0f766e;color:#ffffff;text-decoration:none;padding:10px 18px;border-radius:6px;"
                + "font-weight:600\">" + esc(texto) + "</a></p>";
    }

    public String corpoTeste(String quando, String conta, String servidor) {
        return moldura("Teste de envio de e-mail",
                "<p style=\"margin:0 0 12px\">Se você recebeu esta mensagem, a conta de e-mail do <strong>" + SISTEMA
                        + "</strong> está configurada corretamente.</p>"
                        + tabela(new String[][] {{"Data e hora", quando}, {"Conta remetente", conta}, {"Servidor", servidor}}));
    }

    private static String moldura(String titulo, String conteudo) {
        return "<div style=\"font-family:Arial,sans-serif;font-size:14px;color:#0b1220;max-width:560px\">"
                + "<div style=\"background:#042f2e;border-top:4px solid #2dd4bf;color:#ffffff;padding:16px 20px;"
                + "border-radius:8px 8px 0 0\"><div style=\"font-size:18px;font-weight:bold\">" + SISTEMA + "</div>"
                + "<div style=\"font-size:12px;opacity:.85\">" + titulo + "</div></div>"
                + "<div style=\"border:1px solid #e5e7eb;border-top:0;padding:20px;border-radius:0 0 8px 8px\">"
                + conteudo
                + "<p style=\"margin:20px 0 0;font-size:12px;color:#64748b\">Mensagem automática do " + SISTEMA
                + ". Não é preciso responder.</p></div></div>";
    }

    private static String tabela(String[][] linhas) {
        StringBuilder sb = new StringBuilder("<table style=\"border-collapse:collapse;font-size:13px\">");
        for (String[] l : linhas) {
            sb.append("<tr><td style=\"padding:6px 12px 6px 0;color:#64748b;white-space:nowrap\">").append(esc(l[0]))
                    .append("</td><td style=\"padding:6px 0;font-weight:600\">").append(esc(l[1])).append("</td></tr>");
        }
        return sb.append("</table>").toString();
    }

    static String esc(String v) {
        if (v == null) {
            return "—";
        }
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
