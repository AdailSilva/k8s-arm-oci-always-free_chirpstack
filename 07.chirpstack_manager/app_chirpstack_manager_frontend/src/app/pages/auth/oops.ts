import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute, RouterModule } from '@angular/router';

/**
 * Erro inesperado (mesmo padrão dos demais sistemas). Também usada quando uma nova versão do
 * painel é publicada com a página aberta: as telas da versão antiga já não existem no servidor e a
 * página precisa ser recarregada.
 */
@Component({
    selector: 'app-oops',
    standalone: true,
    imports: [RouterModule],
    template: `
        <div class="flex flex-col items-center justify-center text-center px-2">
            <div class="w-24 h-24 rounded-3xl flex items-center justify-center bg-primary-50 dark:bg-primary-900/30 text-primary">
                <i class="pi text-5xl!" [class]="novaVersao() ? 'pi-sync' : 'pi-exclamation-triangle'"></i>
            </div>
            <h1 class="mt-8 text-4xl md:text-5xl font-semibold leading-[125%]">{{ novaVersao() ? 'Nova versão disponível' : 'Ops! Algo deu errado' }}</h1>
            <p class="text-lg text-surface-500 leading-7 mt-4 max-w-[34rem]">
                @if (novaVersao()) {
                    O painel foi atualizado enquanto esta página estava aberta. Recarregue para usar a versão nova.
                } @else {
                    Não conseguimos concluir o que você pediu. Tente de novo; se continuar, fale com o suporte.
                }
            </p>
            <div class="flex flex-wrap justify-center gap-3 mt-8">
                <button type="button" class="landing-button-primary w-fit px-8" (click)="recarregar()"><i class="pi pi-refresh mr-2"></i>Recarregar</button>
                <a routerLink="/" class="landing-button-secondary w-fit px-8 no-underline">Voltar ao início</a>
            </div>
        </div>
    `
})
export class Oops {
    private route = inject(ActivatedRoute);
    readonly novaVersao = computed(() => this.route.snapshot.queryParamMap.get('motivo') === 'versao');

    /** Recarrega a página (de volta à tela onde estava, quando conhecida). */
    recarregar(): void {
        const destino = this.route.snapshot.queryParamMap.get('voltar');
        window.location.assign(destino && destino.startsWith('/') && !destino.startsWith('//') ? destino : '/');
    }
}
