import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { HorarioAcessoService } from '@/app/core/sessao/horario-acesso.service';

/**
 * Aviso de FIM DO HORÁRIO DE ACESSO, 5 minutos antes, no mesmo formato do aviso de sessão. Não tem
 * "Continuar conectado": o horário é uma regra do administrador, não um prazo que se estende.
 */
@Component({
    selector: 'app-horario-aviso',
    standalone: true,
    imports: [CommonModule, ButtonModule, DialogModule],
    template: `
        <p-dialog [visible]="h.avisoVisivel()" [modal]="true" [closable]="false" [draggable]="false" [resizable]="false" [style]="{ width: '30rem' }" [breakpoints]="{ '640px': '95vw' }">
            <ng-template #header>
                <div class="flex items-center gap-3 min-w-0">
                    <span class="w-10 h-10 shrink-0 rounded-xl bg-sky-100 text-sky-700 dark:bg-sky-900/40 dark:text-sky-300 flex items-center justify-center"><i class="pi pi-calendar-clock text-lg!"></i></span>
                    <span class="font-semibold text-lg">O horário de acesso está terminando</span>
                </div>
            </ng-template>
            <p class="m-0 text-surface-600 dark:text-surface-300">
                O acesso de hoje vai até as <b>{{ h.horaFim() }}</b>, conforme o horário definido pelo administrador. Nesse momento você será desconectado.
            </p>
            <div class="flex flex-col items-center gap-1 my-6">
                <span class="text-sm text-surface-500">Tempo restante</span>
                <span class="text-5xl font-semibold tabular-nums" [ngClass]="(h.restanteMs() ?? 0) <= 60000 ? 'text-red-500' : 'text-sky-600 dark:text-sky-400'">{{ h.restanteLabel() }}</span>
            </div>
            <div class="flex items-start gap-3 rounded-xl border border-amber-200 dark:border-amber-800 bg-amber-50 dark:bg-amber-900/20 px-4 py-3">
                <i class="pi pi-save text-amber-600 dark:text-amber-400 mt-0.5"></i>
                <span class="text-sm text-amber-800 dark:text-amber-200">Salve agora o que estiver aberto: o que não for gravado até lá será perdido.</span>
            </div>
            <ng-template #footer>
                <p-button label="Entendi" icon="pi pi-check" (onClick)="h.dispensarAviso()" />
            </ng-template>
        </p-dialog>
    `
})
export class AppHorarioAviso {
    h = inject(HorarioAcessoService);
}
