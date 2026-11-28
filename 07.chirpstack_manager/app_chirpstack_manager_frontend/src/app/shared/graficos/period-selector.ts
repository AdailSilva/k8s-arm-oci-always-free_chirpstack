import { Component, input, model, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SelectButtonModule } from 'primeng/selectbutton';
import { Aggregation } from '@/app/core/http/models';

export interface Periodo {
    label: string;
    value: string;
    horas: number;
    agregacao: Aggregation;
}

export const PERIODOS: Periodo[] = [
    { label: '24 h', value: '24h', horas: 24, agregacao: 'HOUR' },
    { label: '7 dias', value: '7d', horas: 24 * 7, agregacao: 'DAY' },
    { label: '31 dias', value: '31d', horas: 24 * 31, agregacao: 'DAY' },
    { label: '12 meses', value: '12m', horas: 24 * 365, agregacao: 'MONTH' }
];

/** Períodos das leituras do monitoramento (MQTT): janelas mais curtas, sem 12 meses. */
export const PERIODOS_LEITURAS: Periodo[] = [
    { label: '6 h', value: '6h', horas: 6, agregacao: 'HOUR' },
    { label: '24 h', value: '24h', horas: 24, agregacao: 'HOUR' },
    { label: '7 dias', value: '7d', horas: 24 * 7, agregacao: 'DAY' },
    { label: '31 dias', value: '31d', horas: 24 * 31, agregacao: 'DAY' }
];

/** Seletor de período das métricas (ou das leituras, com [opcoes]="PERIODOS_LEITURAS"). */
@Component({
    selector: 'app-period-selector',
    standalone: true,
    imports: [FormsModule, SelectButtonModule],
    template: `<p-selectbutton [options]="opcoes()" optionLabel="label" optionValue="value" [ngModel]="value()" (ngModelChange)="mudar($event)" [allowEmpty]="false" size="small" />`
})
export class PeriodSelector {
    value = model<string>('24h');
    changed = output<Periodo>();
    opcoes = input<Periodo[]>(PERIODOS);

    mudar(v: string): void {
        this.value.set(v);
        this.changed.emit(this.opcoes().find((p) => p.value === v)!);
    }
}

export function intervaloDe(p: Periodo): { start: Date; end: Date } {
    const end = new Date();
    const start = new Date(end.getTime() - p.horas * 3600_000);
    return { start, end };
}
