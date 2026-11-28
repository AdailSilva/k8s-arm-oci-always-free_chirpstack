import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AtuacaoService } from '@/app/core/http/atuacao.service';
import { ResumoComandos, StatusComando } from '@/app/core/http/atuacao.models';
import { autoAtualizar } from '@/app/shared/atuacao/atuacao-util';
import { ComandosLista } from '@/app/shared/atuacao/comandos-lista';
import { KpiCard } from '@/app/shared/graficos/kpi-card';
import { PageHeader } from '@/app/shared/visual/page-header';

/**
 * Fila de comandos de todos os dispositivos: indicadores (pendentes, em andamento, executados e
 * falhas nas últimas 24 h) e a lista com filtros, detalhe das tentativas, cancelar e reenviar.
 */
@Component({
    selector: 'app-fila-comandos',
    standalone: true,
    imports: [ComandosLista, KpiCard, PageHeader],
    template: `
        <app-page-header title="Fila de comandos" subtitle="PENDENTE → ENFILEIRADO → TRANSMITIDO → EXECUTADO, com cada tentativa" icon="pi pi-inbox" />

        <div class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4 mb-4">
            <app-kpi-card label="Pendentes" [value]="resumo()?.pendentes ?? '—'" icon="pi pi-clock" iconBg="bg-surface-100 text-surface-600 dark:bg-surface-800 dark:text-surface-300" detail="Aguardando a vez ou a janela" />
            <app-kpi-card label="Em andamento" [value]="resumo()?.emAndamento ?? '—'" icon="pi pi-send" iconBg="bg-blue-50 text-blue-600 dark:bg-blue-900/30 dark:text-blue-300" detail="Enfileirados ou transmitidos" />
            <app-kpi-card label="Executados (24 h)" [value]="resumo()?.executados24h ?? '—'" icon="pi pi-check" iconBg="bg-green-50 text-green-600 dark:bg-green-900/30 dark:text-green-300" />
            <app-kpi-card label="Falhas (24 h)" [value]="resumo()?.falhas24h ?? '—'" icon="pi pi-times" iconBg="bg-red-50 text-red-600 dark:bg-red-900/30 dark:text-red-300" detail="Falhou ou expirou" />
        </div>

        <div class="card">
            <app-comandos-lista [statusInicial]="statusInicial" (alterado)="carregarResumo()" />
        </div>
    `
})
export class FilaComandos implements OnInit {
    private api = inject(AtuacaoService);
    private destroyRef = inject(DestroyRef);
    private route = inject(ActivatedRoute);

    resumo = signal<ResumoComandos | null>(null);
    /** ?status=FALHOU,EXPIRADO abre a lista já filtrada. */
    statusInicial: StatusComando[] = [];

    ngOnInit(): void {
        const s = this.route.snapshot.queryParamMap.get('status');

        this.statusInicial = s ? (s.split(',').filter(Boolean) as StatusComando[]) : [];
        this.carregarResumo();
        autoAtualizar(this.destroyRef, () => this.carregarResumo());
    }

    carregarResumo(): void {
        this.api.resumoComandos().subscribe({ next: (r) => this.resumo.set(r) });
    }
}
