import { AtuacaoService } from '@/app/core/http/atuacao.service';
import { ComandoRequest, ComandoResponse } from '@/app/core/http/atuacao.models';
import { NotifyService } from '@/app/core/notificacao/notify.service';
import { ehCargaCritica } from './atuacao-util';
import { ConfirmacaoCritica } from './confirmacao-critica';

/**
 * Envia um comando a um dispositivo. Se o backend responder 409 "carga crítica", abre a confirmação
 * explícita e, se o usuário confirmar, reenvia com confirmarCritico = true.
 */
export function enviarComando(
    ctx: { api: AtuacaoService; notify: NotifyService; critico: ConfirmacaoCritica },
    dispositivoId: number,
    req: Omit<ComandoRequest, 'confirmarCritico'>,
    aoConcluir: (c: ComandoResponse | null) => void,
    confirmarCritico = false
): void {
    ctx.api.enviarComando(dispositivoId, { ...req, confirmarCritico }).subscribe({
        next: (c) => {
            ctx.notify.sucesso(`${c.tipoRotulo}: comando #${c.id} na fila. Acompanhe na aba Comandos.`, 'Comando enviado');
            aoConcluir(c);
        },
        error: (e) => {
            if (ehCargaCritica(e)) {
                ctx.critico.pedir(NotifyService.mensagem(e), () => enviarComando(ctx, dispositivoId, req, aoConcluir, true));
            } else {
                ctx.notify.erro(e);
            }
            aoConcluir(null);
        }
    });
}
