import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { MonitoramentoService } from '@/app/core/http/monitoramento.service';
import { Painel as PainelDados } from '@/app/core/http/models';
import { NotifyService } from '@/app/core/notificacao/notify.service';
import { dataHora, tempoRelativo } from '@/app/core/format/format';
import { KpiCard } from '@/app/shared/graficos/kpi-card';
import { PageHeader } from '@/app/shared/visual/page-header';
import { resumoObjeto } from '@/app/pages/monitoramento/objeto';

/**
 * Painel: indicadores dos dispositivos, alertas, conexão MQTT e as últimas leituras.
 * Atualiza sozinho a cada 15 segundos.
 */
@Component({
    selector: 'app-painel',
    standalone: true,
    imports: [CommonModule, RouterModule, ButtonModule, TableModule, TagModule, TooltipModule, KpiCard, PageHeader],
    template: `
        <app-page-header title="Painel" subtitle="Dispositivos LoRaWAN recebidos do ChirpStack" icon="pi pi-chart-bar">
            <p-button icon="pi pi-refresh" label="Atualizar" severity="secondary" [outlined]="true" (onClick)="carregar()" [loading]="carregando()" />
        </app-page-header>

        @if (d(); as p) {
            @if (p.mqtt.habilitado && !p.mqtt.conectado) {
                <div class="card mb-4! flex items-start gap-3 border-l-4 border-red-500">
                    <i class="pi pi-exclamation-triangle text-red-500 text-xl! mt-1"></i>
                    <div class="min-w-0">
                        <div class="font-semibold">Sem conexão com o MQTT do ChirpStack</div>
                        <div class="text-sm text-surface-500 break-words">{{ p.mqtt.ultimoErro || 'Tentando reconectar...' }}</div>
                    </div>
                </div>
            }
            <div class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4 mb-4">
                <app-kpi-card label="Dispositivos" [value]="p.dispositivos" [detail]="p.online + ' on-line · ' + p.semComunicacao + ' sem comunicação'" icon="pi pi-microchip" />
                <app-kpi-card label="Leituras (24 h)" [value]="p.leiturasUltimas24h" detail="uplinks recebidos" icon="pi pi-download" iconBg="bg-cyan-50 text-cyan-600 dark:bg-cyan-900/30 dark:text-cyan-300" />
                <app-kpi-card label="Alertas abertos" [value]="p.alertasAbertos" [detail]="p.alertasReconhecidos + ' reconhecido(s)'" [detailClass]="p.alertasAbertos > 0 ? 'text-red-500' : 'text-surface-500'" icon="pi pi-bell" [iconBg]="p.alertasAbertos > 0 ? 'bg-red-50 text-red-600 dark:bg-red-900/30 dark:text-red-300' : 'bg-green-50 text-green-600 dark:bg-green-900/30 dark:text-green-300'" />
                <app-kpi-card label="MQTT" [value]="!p.mqtt.habilitado ? 'Desligado' : p.mqtt.conectado ? 'Conectado' : 'Desconectado'" [detail]="'Última mensagem: ' + rel(p.mqtt.ultimaMensagemEm)" icon="pi pi-wifi" [iconBg]="p.mqtt.conectado ? 'bg-green-50 text-green-600 dark:bg-green-900/30 dark:text-green-300' : 'bg-surface-100 text-surface-500 dark:bg-surface-800'" />
            </div>

            <div class="card">
                <div class="flex items-center justify-between gap-2 mb-4">
                    <span class="font-semibold text-lg">Últimas leituras</span>
                    <p-button label="Dispositivos" icon="pi pi-arrow-right" iconPos="right" [text]="true" routerLink="/monitoramento/dispositivos" />
                </div>
                <p-table [value]="p.ultimasLeituras" [rowHover]="true" styleClass="p-datatable-sm">
                    <ng-template #header>
                        <tr>
                            <th>Dispositivo</th>
                            <th class="hidden md:table-cell">Recebida</th>
                            <th class="hidden lg:table-cell">RSSI / SNR</th>
                            <th>Dados</th>
                        </tr>
                    </ng-template>
                    <ng-template #body let-l>
                        <tr class="cursor-pointer" [routerLink]="['/monitoramento/dispositivos', l.dispositivoId]">
                            <td>
                                <div class="font-medium">{{ l.dispositivoNome || '—' }}</div>
                                <div class="text-xs text-surface-500 md:hidden">{{ rel(l.recebidoEm) }}</div>
                            </td>
                            <td class="hidden md:table-cell text-sm" [pTooltip]="dh(l.recebidoEm)">{{ rel(l.recebidoEm) }}</td>
                            <td class="hidden lg:table-cell text-sm">{{ l.rssi ?? '—' }} dBm / {{ l.snr ?? '—' }} dB</td>
                            <td class="text-sm break-words">{{ resumo(l.objeto) }}</td>
                        </tr>
                    </ng-template>
                    <ng-template #emptymessage>
                        <tr><td colspan="4" class="text-center py-8 text-surface-500">Nenhuma leitura recebida ainda.</td></tr>
                    </ng-template>
                </p-table>
            </div>
        } @else if (carregando()) {
            <div class="card text-center text-surface-500 py-8"><i class="pi pi-spin pi-spinner mr-2"></i>Carregando...</div>
        }
    `
})
export class Painel implements OnInit {
    private api = inject(MonitoramentoService);
    private notify = inject(NotifyService);
    private destroyRef = inject(DestroyRef);

    d = signal<PainelDados | null>(null);
    carregando = signal(false);
    rel = tempoRelativo;
    dh = dataHora;
    resumo = resumoObjeto;

    ngOnInit(): void {
        this.carregar();
        const t = setInterval(() => this.carregar(true), 15_000);
        this.destroyRef.onDestroy(() => clearInterval(t));
    }

    carregar(silencioso = false): void {
        if (!silencioso) {
            this.carregando.set(true);
        }
        this.api.painel().subscribe({
            next: (p) => {
                this.d.set(p);
                this.carregando.set(false);
            },
            error: (e) => {
                this.carregando.set(false);
                if (!silencioso) {
                    this.notify.erro(e);
                }
            }
        });
    }
}
