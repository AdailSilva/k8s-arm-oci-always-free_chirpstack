import { Component, DestroyRef, ElementRef, afterRenderEffect, inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { EdicaoPendenteService, TEXTOS_DESCARTE } from '@/app/core/edicao/edicao-pendente.service';

/**
 * Confirmação ÚNICA de "alterações não salvas" (montada uma vez no app-root, vale para o
 * layout logado e para as telas de autenticação). Não usa p-confirmdialog de propósito: as
 * telas já têm os seus (com e sem key) e o ConfirmationService delas é local — este diálogo
 * não depende de nenhum deles e fica acima de qualquer p-dialog aberto.
 *
 * <p>Esc e clique fora = "Continuar editando" (a opção que não perde nada). A tecla Esc é
 * consumida aqui para não fechar também o p-dialog que está por baixo.</p>
 */
@Component({
    selector: 'app-edicao-pendente-dialog',
    standalone: true,
    imports: [ButtonModule],
    template: `
        @if (svc.pedido(); as p) {
            <div class="fixed inset-0 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm" style="z-index: 5000" (mousedown)="fora($event)">
                <div
                    class="w-full max-w-md bg-surface-0 dark:bg-surface-900 rounded-2xl border border-surface-200 dark:border-surface-700 shadow-xl overflow-hidden"
                    role="alertdialog"
                    aria-modal="true"
                    aria-labelledby="titulo-descarte"
                    aria-describedby="mensagem-descarte"
                >
                    <div class="px-6 py-5 flex items-start gap-4">
                        <div class="w-11 h-11 rounded-xl bg-red-100 dark:bg-red-500/15 flex items-center justify-center shrink-0">
                            <i class="pi pi-exclamation-triangle text-red-600 dark:text-red-400 text-xl"></i>
                        </div>
                        <div>
                            <h2 id="titulo-descarte" class="font-semibold text-surface-950 dark:text-surface-0">{{ p.janela ? textos.janela.titulo : textos.pagina.titulo }}</h2>
                            <p id="mensagem-descarte" class="text-sm text-surface-500 mt-1">{{ textos.mensagem }}</p>
                        </div>
                    </div>
                    <div class="px-6 py-4 border-t border-surface-200 dark:border-surface-700 flex flex-col sm:flex-row justify-end gap-2">
                        <p-button [label]="textos.continuar" severity="secondary" [outlined]="true" (click)="svc.responder(false)" />
                        <p-button class="csm-descarte-sair" [label]="p.janela ? textos.janela.botao : textos.pagina.botao" icon="pi pi-trash" severity="danger" (click)="svc.responder(true)" />
                    </div>
                </div>
            </div>
        }
    `
})
export class EdicaoPendenteDialog {
    readonly svc = inject(EdicaoPendenteService);
    readonly textos = TEXTOS_DESCARTE;

    private readonly _el = inject(ElementRef<HTMLElement>);

    constructor() {
        this.svc.registrarHost();
        const janela = inject(DOCUMENT).defaultView;
        // Captura no window: chega antes do listener de Esc do p-dialog (no document).
        const teclado = (e: KeyboardEvent) => {
            if (!this.svc.pedido() || e.key !== 'Escape') return;
            e.preventDefault();
            e.stopImmediatePropagation();
            this.svc.responder(false);
        };

        janela?.addEventListener('keydown', teclado, true);
        inject(DestroyRef).onDestroy(() => {
            janela?.removeEventListener('keydown', teclado, true);
            this.svc.removerHost();
        });

        // Foco inicial em "Continuar editando" (Enter não descarta por engano).
        afterRenderEffect(() => {
            if (!this.svc.pedido()) return;
            (this._el.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button')?.focus();
        });
    }

    fora(e: MouseEvent): void {
        if (e.target === e.currentTarget) this.svc.responder(false);
    }
}
