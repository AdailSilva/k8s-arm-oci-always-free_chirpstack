import { Component, model } from '@angular/core';
import { DIAS_SEMANA } from './atuacao-util';

/** Escolha dos dias da semana (1 = segunda ... 7 = domingo) em botões que quebram linha no celular. */
@Component({
    selector: 'app-dias-semana',
    standalone: true,
    template: `
        <div class="flex flex-wrap gap-2" role="group" aria-label="Dias da semana">
            @for (d of dias; track d.value) {
                <button
                    type="button"
                    class="min-w-12 px-3 py-1.5 rounded-full border text-sm font-medium cursor-pointer transition-colors"
                    [class]="marcado(d.value) ? 'bg-primary text-primary-contrast border-primary' : 'bg-transparent text-surface-700 dark:text-surface-200 border-surface-300 dark:border-surface-600 hover:bg-surface-100 dark:hover:bg-surface-800'"
                    [attr.aria-pressed]="marcado(d.value)"
                    [attr.title]="d.nome"
                    (click)="alternar(d.value)"
                >
                    {{ d.curto }}
                </button>
            }
        </div>
    `
})
export class DiasSemana {
    value = model<number[]>([]);
    dias = DIAS_SEMANA;

    marcado(d: number): boolean {
        return (this.value() ?? []).includes(d);
    }

    alternar(d: number): void {
        const atual = this.value() ?? [];

        this.value.set(atual.includes(d) ? atual.filter((x) => x !== d) : [...atual, d].sort((a, b) => a - b));
    }
}
