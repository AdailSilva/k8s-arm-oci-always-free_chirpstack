import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DialogModule } from 'primeng/dialog';

/**
 * Confirmação explícita de carga crítica. O backend responde 409 ("Carga crítica...") quando um
 * comando, cena, agendamento ou automação mexe num relé marcado como crítico; a tela mostra este
 * diálogo e, só depois de o usuário marcar a caixa, reenvia com confirmarCritico = true.
 */
@Component({
    selector: 'app-confirmacao-critica',
    standalone: true,
    imports: [FormsModule, ButtonModule, CheckboxModule, DialogModule],
    template: `
        <p-dialog [visible]="visivel()" (visibleChange)="visivel.set($event)" [modal]="true" [draggable]="false" [closable]="true" [style]="{ width: '30rem' }" [breakpoints]="{ '640px': '95vw' }" (onHide)="aoFechar()">
            <ng-template #header>
                <div class="flex items-center gap-3 min-w-0">
                    <span class="w-10 h-10 shrink-0 rounded-xl bg-red-50 text-red-600 dark:bg-red-900/30 dark:text-red-300 flex items-center justify-center"><i class="pi pi-exclamation-triangle text-lg!"></i></span>
                    <span class="font-semibold text-lg">Carga crítica</span>
                </div>
            </ng-template>
            <div class="flex flex-col gap-4">
                <p class="m-0 break-words">{{ mensagem() }}</p>
                <p class="m-0 text-sm text-surface-500">Este relé foi marcado como crítico (ex.: bomba, portão, aquecedor). Confira se é seguro acioná-lo agora, à distância.</p>
                <label class="flex items-start gap-3 cursor-pointer">
                    <p-checkbox [ngModel]="ciente()" (ngModelChange)="ciente.set($event)" [binary]="true" inputId="ciente-critico" />
                    <span class="font-medium">Entendo o risco e quero acionar a carga crítica.</span>
                </label>
            </div>
            <ng-template #footer>
                <div class="flex flex-wrap justify-end gap-2">
                    <p-button label="Cancelar" severity="secondary" [outlined]="true" (onClick)="visivel.set(false)" />
                    <p-button label="Confirmar acionamento" icon="pi pi-bolt" severity="danger" [disabled]="!ciente()" (onClick)="confirmar()" />
                </div>
            </ng-template>
        </p-dialog>
    `
})
export class ConfirmacaoCritica {
    visivel = signal(false);
    ciente = signal(false);
    mensagem = signal('');
    private acao: (() => void) | null = null;

    /** Abre o diálogo; {@code acao} roda só se o usuário confirmar. */
    pedir(mensagem: string, acao: () => void): void {
        this.mensagem.set(mensagem);
        this.acao = acao;
        this.ciente.set(false);
        this.visivel.set(true);
    }

    confirmar(): void {
        const a = this.acao;

        this.acao = null;
        this.visivel.set(false);
        a?.();
    }

    aoFechar(): void {
        this.acao = null;
        this.ciente.set(false);
    }
}
