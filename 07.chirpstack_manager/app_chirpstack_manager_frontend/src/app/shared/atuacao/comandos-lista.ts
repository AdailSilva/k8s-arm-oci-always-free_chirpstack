import { Component, DestroyRef, inject, input, OnInit, output, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { MultiSelectModule } from 'primeng/multiselect';
import { SelectModule } from 'primeng/select';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { Subscription } from 'rxjs';
import { AuthService } from '@/app/core/auth/auth.service';
import { AtuacaoService } from '@/app/core/http/atuacao.service';
import { ComandoResponse, StatusComando } from '@/app/core/http/atuacao.models';
import { MonitoramentoService } from '@/app/core/http/monitoramento.service';
import { Dispositivo } from '@/app/core/http/models';
import { NotifyService } from '@/app/core/notificacao/notify.service';
import { dataHora, tempoRelativo } from '@/app/core/format/format';
import { autoAtualizar, COR_STATUS, Severidade, ehCargaCritica, ICONE_STATUS, ROTULO_ORIGEM, ROTULO_STATUS, STATUS_OPCOES, statusFinal } from './atuacao-util';
import { ComandoDetalhe } from './comando-detalhe';
import { ConfirmacaoCritica } from './confirmacao-critica';

/**
 * Histórico/fila de comandos com paginação no servidor. Com [dispositivoId] mostra só os daquele
 * dispositivo; sem ele, é a fila global (com filtro de dispositivo). Atualiza sozinho a cada 10 s.
 */
@Component({
    selector: 'app-comandos-lista',
    standalone: true,
    imports: [CommonModule, FormsModule, RouterModule, ButtonModule, MultiSelectModule, SelectModule, TableModule, TagModule, TooltipModule, ComandoDetalhe, ConfirmacaoCritica],
    template: `
        <p-table [value]="itens()" [lazy]="true" (onLazyLoad)="onLazy($event)" [paginator]="true" [rows]="rows()" [first]="first()" [totalRecords]="total()" [loading]="carregando()" [rowsPerPageOptions]="[20, 50, 100]" [showCurrentPageReport]="true" currentPageReportTemplate="{first}–{last} de {totalRecords}" [rowHover]="true" dataKey="id" styleClass="p-datatable-sm">
            <ng-template #caption>
                <div class="flex flex-col sm:flex-row sm:flex-wrap sm:items-center justify-end gap-3">
                    @if (!dispositivoId()) {
                        <p-select [options]="dispositivos()" optionLabel="nome" optionValue="id" [ngModel]="filtroDispositivo()" (ngModelChange)="filtroDispositivo.set($event); recarregar()" placeholder="Todos os dispositivos" [showClear]="true" [filter]="true" filterBy="nome,devEui" styleClass="w-full sm:w-64" appendTo="body" />
                    }
                    <p-multiselect [options]="statusOpcoes" optionLabel="label" optionValue="value" [ngModel]="filtroStatus()" (ngModelChange)="filtroStatus.set($event); recarregar()" placeholder="Todas as situações" [showClear]="true" [maxSelectedLabels]="2" selectedItemsLabel="{0} situações" styleClass="w-full sm:w-64" appendTo="body" />
                    <p-button icon="pi pi-refresh" severity="secondary" [outlined]="true" pTooltip="Atualizar" tooltipPosition="bottom" ariaLabel="Atualizar" (onClick)="carregar()" />
                </div>
            </ng-template>
            <ng-template #header>
                <tr>
                    <th>Comando</th>
                    @if (!dispositivoId()) {
                        <th class="hidden md:table-cell">Dispositivo</th>
                    }
                    <th>Situação</th>
                    <th class="hidden lg:table-cell">Origem</th>
                    <th class="hidden sm:table-cell">Tentativas</th>
                    <th class="hidden xl:table-cell">Próxima tentativa</th>
                    @if (auth.podeEscrever()) {
                        <th class="w-24"></th>
                    }
                </tr>
            </ng-template>
            <ng-template #body let-c>
                <tr class="cursor-pointer" (click)="detalhe().abrir(c.id)">
                    <td class="min-w-0">
                        <div class="font-medium break-words">{{ c.tipoRotulo }}</div>
                        @if (c.resumo) {
                            <div class="text-xs text-surface-500 break-words">{{ c.resumo }}</div>
                        }
                        <div class="text-xs text-surface-500" [pTooltip]="dh(c.criadoEm)">{{ rel(c.criadoEm) }} · #{{ c.id }}</div>
                        @if (!dispositivoId()) {
                            <div class="text-xs md:hidden break-words">{{ c.dispositivoNome || '#' + c.dispositivoId }}</div>
                        }
                    </td>
                    @if (!dispositivoId()) {
                        <td class="hidden md:table-cell text-sm break-words">
                            <a [routerLink]="['/monitoramento/dispositivos', c.dispositivoId]" class="text-primary" (click)="$event.stopPropagation()">{{ c.dispositivoNome || '#' + c.dispositivoId }}</a>
                        </td>
                    }
                    <td>
                        <p-tag [value]="rotuloStatus[c.status]" [severity]="corStatus[c.status]" [icon]="iconeStatus[c.status]" />
                        <div class="text-xs text-surface-500 mt-1 sm:hidden">{{ c.tentativas }}/{{ c.maxTentativas }} tent.</div>
                    </td>
                    <td class="hidden lg:table-cell text-sm">{{ rotuloOrigem[c.origem] || c.origem }}</td>
                    <td class="hidden sm:table-cell text-sm">{{ c.tentativas }}/{{ c.maxTentativas }}</td>
                    <td class="hidden xl:table-cell text-sm">{{ c.proximaTentativaEm && !final(c) ? dh(c.proximaTentativaEm) : '—' }}</td>
                    @if (auth.podeEscrever()) {
                        <td class="text-right whitespace-nowrap" (click)="$event.stopPropagation()">
                            @if (!final(c)) {
                                <p-button icon="pi pi-ban" severity="danger" [text]="true" [rounded]="true" pTooltip="Cancelar" ariaLabel="Cancelar" (onClick)="cancelar(c)" />
                            }
                            <p-button icon="pi pi-replay" [text]="true" [rounded]="true" pTooltip="Reenviar" ariaLabel="Reenviar" (onClick)="reenviar(c)" />
                        </td>
                    }
                </tr>
            </ng-template>
            <ng-template #emptymessage>
                <tr><td [attr.colspan]="7" class="text-center py-8 text-surface-500">Nenhum comando.</td></tr>
            </ng-template>
        </p-table>
        <app-comando-detalhe (alterado)="carregar(true); alterado.emit()" />
        <app-confirmacao-critica />
    `
})
export class ComandosLista implements OnInit {
    auth = inject(AuthService);
    private api = inject(AtuacaoService);
    private mon = inject(MonitoramentoService);
    private notify = inject(NotifyService);
    private destroyRef = inject(DestroyRef);
    detalhe = viewChild.required(ComandoDetalhe);
    private critico = viewChild.required(ConfirmacaoCritica);

    /** Só os comandos deste dispositivo (sem filtro de dispositivo na tela). */
    dispositivoId = input<number | null>(null);
    /** Situações marcadas ao abrir. */
    statusInicial = input<StatusComando[]>([]);
    /** Emite quando algo muda (cancelar/reenviar), para o pai atualizar indicadores. */
    alterado = output<void>();

    itens = signal<ComandoResponse[]>([]);
    total = signal(0);
    carregando = signal(false);
    first = signal(0);
    rows = signal(20);
    filtroStatus = signal<StatusComando[]>([]);
    filtroDispositivo = signal<number | null>(null);
    dispositivos = signal<Dispositivo[]>([]);
    private sub?: Subscription;

    statusOpcoes = STATUS_OPCOES;
    rotuloStatus: Record<string, string> = ROTULO_STATUS;
    corStatus: Record<string, Severidade> = COR_STATUS;
    iconeStatus: Record<string, string> = ICONE_STATUS;
    rotuloOrigem: Record<string, string> = ROTULO_ORIGEM;
    final = (c: ComandoResponse) => statusFinal(c.status);
    dh = dataHora;
    rel = tempoRelativo;

    ngOnInit(): void {
        this.filtroStatus.set(this.statusInicial());

        if (!this.dispositivoId()) {
            this.mon.dispositivos('', true, 0, 500).subscribe({ next: (r) => this.dispositivos.set(r.result) });
        }
        autoAtualizar(this.destroyRef, () => this.carregar(true));
    }

    onLazy(e: TableLazyLoadEvent): void {
        this.first.set(e.first ?? 0);
        this.rows.set(e.rows ?? 20);
        this.carregar();
    }

    recarregar(): void {
        this.first.set(0);
        this.carregar();
    }

    carregar(silencioso = false): void {
        const rows = this.rows();
        const disp = this.dispositivoId() ?? this.filtroDispositivo();

        if (!silencioso) {
            this.carregando.set(true);
        }
        this.sub?.unsubscribe();
        this.sub = this.api.comandos(disp, this.filtroStatus(), Math.floor(this.first() / rows), rows).subscribe({
            next: (r) => {
                this.itens.set(r.result ?? []);
                this.total.set(Number(r.totalCount));
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

    cancelar(c: ComandoResponse): void {
        this.notify.confirmar('Cancelar comando', `Cancelar "${c.tipoRotulo}" (#${c.id})?`, () =>
            this.api.cancelarComando(c.id).subscribe({
                next: () => {
                    this.notify.sucesso('Comando cancelado.');
                    this.carregar(true);
                    this.alterado.emit();
                },
                error: (e) => this.notify.erro(e)
            }),
            'Cancelar comando'
        );
    }

    reenviar(c: ComandoResponse, confirmarCritico = false): void {
        this.api.reenviarComando(c.id, confirmarCritico).subscribe({
            next: (novo) => {
                this.notify.sucesso(`Reenviado como comando #${novo.id}.`);
                this.carregar(true);
                this.alterado.emit();
            },
            error: (e) => {
                if (ehCargaCritica(e)) {
                    this.critico().pedir(NotifyService.mensagem(e), () => this.reenviar(c, true));
                } else {
                    this.notify.erro(e);
                }
            }
        });
    }
}
