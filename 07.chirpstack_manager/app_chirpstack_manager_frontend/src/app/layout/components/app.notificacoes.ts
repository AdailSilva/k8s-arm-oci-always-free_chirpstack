import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Alerta } from '@/app/core/http/models';
import { AvisosService } from '@/app/core/notificacao/avisos.service';
import { tempoRelativo } from '@/app/core/format/format';

/**
 * Painel do sino: alertas abertos mais recentes, com a cor da severidade, e atalho para a tela
 * completa de Alertas.
 */
@Component({
    selector: '[app-notificacoes]',
    standalone: true,
    imports: [CommonModule],
    template: `
        <div class="px-4 py-3 flex items-center justify-between gap-3 border-b border-surface-200 dark:border-surface-800">
            <span class="font-semibold">Notificações</span>
            @if (avisos.total() > 0) {
                <span class="flex items-center gap-1.5 shrink-0">
                    @if (avisos.criticos() > 0) {
                        <span class="text-xs font-semibold px-2 py-0.5 rounded-full bg-red-500/15 text-red-600 dark:text-red-400 whitespace-nowrap">{{ avisos.criticos() }} crítico{{ avisos.criticos() > 1 ? 's' : '' }}</span>
                    }
                    <span class="text-xs font-semibold px-2 py-0.5 rounded-full bg-amber-500/15 text-amber-700 dark:text-amber-400 whitespace-nowrap">{{ avisos.totalLabel() }} aberto{{ avisos.total() > 1 ? 's' : '' }}</span>
                </span>
            }
        </div>
        @if (avisos.alertas().length === 0) {
            <div class="p-6 flex flex-col items-center gap-2 text-center">
                <i class="pi pi-bell-slash text-2xl! text-surface-400"></i>
                <span class="text-sm text-surface-500">Nenhum alerta aberto no momento.</span>
            </div>
        } @else {
            <ul class="list-none m-0 p-0 max-h-80 overflow-y-auto py-1">
                @for (a of avisos.alertas(); track a.id) {
                    <li>
                        <button type="button" class="w-full text-left px-4 py-3 flex gap-3 bg-transparent border-0 cursor-pointer text-inherit hover:bg-surface-100 dark:hover:bg-surface-800 transition-colors" (click)="abrir()">
                            <span class="w-8 h-8 rounded-lg flex items-center justify-center shrink-0" [ngClass]="cor(a)">
                                <i class="pi text-sm!" [ngClass]="a.severidade === 'INFO' ? 'pi-info-circle' : 'pi-exclamation-triangle'"></i>
                            </span>
                            <span class="min-w-0 flex-1">
                                <span class="flex items-start justify-between gap-2">
                                    <span class="font-medium text-sm [overflow-wrap:anywhere]">{{ a.regraNome || 'Alerta' }}</span>
                                    <span class="text-xs text-surface-500 shrink-0 whitespace-nowrap">{{ quando(a) }}</span>
                                </span>
                                <span class="block text-xs text-surface-500 [overflow-wrap:anywhere]">{{ a.dispositivoNome || a.devEui }} · {{ a.mensagem }}</span>
                            </span>
                        </button>
                    </li>
                }
            </ul>
        }
        <button type="button" class="w-full px-4 py-3 text-sm font-medium text-primary bg-transparent border-0 border-t border-surface-200 dark:border-surface-800 cursor-pointer hover:bg-surface-100 dark:hover:bg-surface-800 transition-colors" (click)="abrir()">
            Ver todos os alertas
        </button>
    `,
    host: {
        class: 'block overflow-hidden rounded-2xl bg-surface-0 dark:bg-surface-950 border border-surface-200 dark:border-surface-800 shadow-xl'
    }
})
export class AppNotificacoes {
    readonly avisos = inject(AvisosService);
    private router = inject(Router);

    cor(a: Alerta): string {
        if (a.severidade === 'CRITICO') {
            return 'bg-red-500/15 text-red-500';
        }
        if (a.severidade === 'AVISO') {
            return 'bg-amber-500/15 text-amber-500';
        }
        return 'bg-primary/15 text-primary';
    }

    quando(a: Alerta): string {
        return tempoRelativo(a.disparadoEm);
    }

    abrir(): void {
        this.router.navigate(['/monitoramento/alertas']);
    }
}
