import { ErrorHandler, inject, Injectable, Injector } from '@angular/core';
import { Router } from '@angular/router';

/**
 * Erros não tratados. O caso que importa ao usuário é a nova versão publicada com a página aberta:
 * as telas (chunks) da versão antiga somem do servidor e abrir uma delas falha. Em vez de uma tela
 * em branco, leva à página "Nova versão disponível", que recarrega o painel.
 */
@Injectable()
export class ErroGlobalHandler implements ErrorHandler {
    private injector = inject(Injector);
    private redirecionando = false;

    handleError(erro: unknown): void {
        console.error(erro);
        const msg = String((erro as { message?: string })?.message ?? erro ?? '');
        const versaoNova = /Loading chunk|ChunkLoadError|Failed to fetch dynamically imported module|error loading dynamically imported module|Importing a module script failed/i.test(msg);
        if (!versaoNova || this.redirecionando) {
            return;
        }
        this.redirecionando = true;
        const router = this.injector.get(Router);
        const voltar = router.url && !router.url.startsWith('/auth/') ? router.url : '/';
        void router.navigate(['/auth/oops'], { queryParams: { motivo: 'versao', voltar } }).finally(() => (this.redirecionando = false));
    }
}
