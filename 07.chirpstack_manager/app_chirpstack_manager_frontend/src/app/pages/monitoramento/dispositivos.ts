import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TooltipModule } from 'primeng/tooltip';
import { MonitoramentoService } from '@/app/core/http/monitoramento.service';
import { Dispositivo } from '@/app/core/http/models';
import { NotifyService } from '@/app/core/notificacao/notify.service';
import { dataHora, tempoRelativo } from '@/app/core/format/format';
import { ConsultaPagina, ListaPaginada } from '@/app/shared/tabela/lista-paginada';
import { PageHeader } from '@/app/shared/visual/page-header';
import { corBateria, corSituacao, rotuloSituacao } from './situacao';

/**
 * Dispositivos recebidos do ChirpStack. Nascem sozinhos na primeira mensagem MQTT; nome, aplicação
 * e perfil vêm do próprio ChirpStack.
 */
@Component({
    selector: 'app-dispositivos',
    standalone: true,
    imports: [CommonModule, FormsModule, RouterModule, ButtonModule, IconFieldModule, InputIconModule, InputTextModule, TableModule, TagModule, ToggleSwitchModule, TooltipModule, PageHeader],
    template: `
        <app-page-header title="Dispositivos" subtitle="Nome, aplicação e perfil vêm do ChirpStack; o painel guarda as leituras" icon="pi pi-microchip">
            <p-button icon="pi pi-refresh" label="Atualizar" severity="secondary" [outlined]="true" (onClick)="carregar()" [loading]="carregando()" />
        </app-page-header>

        <div class="card">
            <p-table [value]="itens()" [lazy]="true" (onLazyLoad)="onLazy($event)" [paginator]="true" [rows]="rows()" [first]="first()" [totalRecords]="total()" [loading]="carregando()" [rowsPerPageOptions]="[20, 50, 100]" [showCurrentPageReport]="true" currentPageReportTemplate="{first}–{last} de {totalRecords}" [rowHover]="true" styleClass="p-datatable-sm">
                <ng-template #caption>
                    <div class="flex flex-col sm:flex-row sm:items-center gap-3 justify-between">
                        <label class="flex items-center gap-2 text-sm">
                            <p-toggleswitch [ngModel]="inativos()" (ngModelChange)="inativos.set($event); recarregarDoInicio()" />
                            Mostrar desativados
                        </label>
                        <p-iconfield styleClass="w-full sm:w-80">
                            <p-inputicon class="pi pi-search" />
                            <input pInputText type="text" class="w-full" placeholder="Nome, DevEUI ou aplicação" [ngModel]="busca()" (ngModelChange)="pesquisar($event)" />
                        </p-iconfield>
                    </div>
                </ng-template>
                <ng-template #header>
                    <tr>
                        <th>Dispositivo</th>
                        <th class="hidden lg:table-cell">Aplicação</th>
                        <th>Situação</th>
                        <th class="hidden md:table-cell">Último contato</th>
                        <th class="hidden xl:table-cell">Sinal</th>
                        <th class="hidden sm:table-cell">Bateria</th>
                        <th class="hidden xl:table-cell text-right">Uplinks</th>
                    </tr>
                </ng-template>
                <ng-template #body let-d>
                    <tr class="cursor-pointer" (click)="abrir(d)">
                        <td>
                            <div class="font-medium" [class.text-surface-400]="!d.ativo">{{ d.nome }}</div>
                            <div class="text-xs text-surface-500 font-mono">{{ d.devEui }}</div>
                        </td>
                        <td class="hidden lg:table-cell text-sm">
                            {{ d.aplicacaoNome || '—' }}
                            @if (d.perfilNome) {
                                <div class="text-xs text-surface-500">{{ d.perfilNome }}</div>
                            }
                        </td>
                        <td>
                            <p-tag [value]="rotulo(d.situacao)" [severity]="cor(d.situacao)" />
                            @if (!d.ativo) {
                                <p-tag value="Desativado" severity="secondary" styleClass="ml-1" />
                            }
                        </td>
                        <td class="hidden md:table-cell text-sm" [pTooltip]="dh(d.ultimoContatoEm)">{{ rel(d.ultimoContatoEm) }}</td>
                        <td class="hidden xl:table-cell text-sm">{{ d.ultimoRssi ?? '—' }} dBm / {{ d.ultimoSnr ?? '—' }} dB</td>
                        <td class="hidden sm:table-cell text-sm" [class]="bat(d.ultimaBateria)">
                            {{ d.ultimaBateria !== null && d.ultimaBateria !== undefined ? d.ultimaBateria + '%' : '—' }}
                        </td>
                        <td class="hidden xl:table-cell text-sm text-right">{{ d.totalUplinks | number }}</td>
                    </tr>
                </ng-template>
                <ng-template #emptymessage>
                    <tr><td colspan="7" class="text-center py-8 text-surface-500">Nenhum dispositivo. Eles aparecem aqui assim que o ChirpStack publicar a primeira mensagem.</td></tr>
                </ng-template>
            </p-table>
        </div>
    `
})
export class Dispositivos extends ListaPaginada<Dispositivo> {
    private api = inject(MonitoramentoService);
    private notify = inject(NotifyService);
    private router = inject(Router);

    inativos = signal(false);
    rotulo = rotuloSituacao;
    cor = corSituacao;
    bat = corBateria;
    rel = tempoRelativo;
    dh = dataHora;

    constructor() {
        super();
        this.rows.set(50);
    }

    protected consultar(q: ConsultaPagina) {
        return this.api.dispositivos(q.search, this.inativos(), Math.floor(q.offset / q.limit), q.limit);
    }

    protected override aoFalhar(e: unknown): void {
        this.notify.erro(e);
    }

    abrir(d: Dispositivo): void {
        this.router.navigate(['/monitoramento/dispositivos', d.id]);
    }
}
