import { Component } from '@angular/core';
import { AgendamentosLista } from '@/app/shared/atuacao/agendamentos-lista';
import { PageHeader } from '@/app/shared/visual/page-header';

/** Agendamentos de todos os dispositivos: comando ou cena em data/hora única ou semanal. */
@Component({
    selector: 'app-agendamentos',
    standalone: true,
    imports: [AgendamentosLista, PageHeader],
    template: `
        <app-page-header title="Agendamentos" subtitle="Comando ou cena em data e hora única ou toda semana" icon="pi pi-calendar-clock" />
        <div class="card">
            <app-agendamentos-lista />
        </div>
    `
})
export class Agendamentos {}
