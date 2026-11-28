import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TabsModule } from 'primeng/tabs';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TooltipModule } from 'primeng/tooltip';
import { AuthService } from '@/app/core/auth/auth.service';
import { MonitoramentoService, Periodo as PeriodoApi } from '@/app/core/http/monitoramento.service';
import { Alerta, Dispositivo, EventoDispositivo, Leitura, Metric, Serie } from '@/app/core/http/models';
import { NotifyService } from '@/app/core/notificacao/notify.service';
import { dataHora, tempoRelativo } from '@/app/core/format/format';
import { MetricChart } from '@/app/shared/graficos/metric-chart';
import { intervaloDe, Periodo, PERIODOS_LEITURAS, PeriodSelector } from '@/app/shared/graficos/period-selector';
import { PageHeader } from '@/app/shared/visual/page-header';
import { AgendamentosLista } from '@/app/shared/atuacao/agendamentos-lista';
import { ComandosLista } from '@/app/shared/atuacao/comandos-lista';
import { ControleDispositivo } from '@/app/shared/atuacao/controle-dispositivo';
import { EnergiaDispositivo } from '@/app/shared/atuacao/energia-dispositivo';
import { jsonBonito, resumoObjeto } from './objeto';
import { TravaEdicaoJanela } from '@/app/core/edicao/trava-edicao';
import { corBateria, corSituacao, rotuloSituacao } from './situacao';

const ROTULO_CAMPO: Record<string, string> = { rssi: 'RSSI (dBm)', snr: 'SNR (dB)' };
const ABAS_ATUACAO = ['controle', 'energia', 'comandos', 'agendamentos'];

/**
 * Detalhe de um dispositivo: estado atual, gráficos dos campos numéricos, leituras, eventos e alertas,
 * e a atuação: controle (equipamento e relés), energia (PZEM), comandos e agendamentos.
 */
@Component({
    selector: 'app-dispositivo-detalhe',
    standalone: true,
    imports: [TravaEdicaoJanela, CommonModule, FormsModule, RouterModule, ButtonModule, DialogModule, TableModule, TabsModule, TagModule, TextareaModule, ToggleSwitchModule, TooltipModule, MetricChart, PageHeader, PeriodSelector, AgendamentosLista, ComandosLista, ControleDispositivo, EnergiaDispositivo],
    template: `
        @if (d(); as d) {
            <app-page-header [title]="d.nome" [subtitle]="'DevEUI ' + d.devEui" icon="pi pi-microchip">
                <p-button icon="pi pi-arrow-left" label="Voltar" severity="secondary" [outlined]="true" routerLink="/monitoramento/dispositivos" />
                @if (auth.podeEscrever()) {
                    <p-button icon="pi pi-pencil" label="Editar" (onClick)="editar()" />
                }
                @if (auth.isAdmin()) {
                    <p-button icon="pi pi-trash" severity="danger" [outlined]="true" pTooltip="Excluir o dispositivo e todas as leituras" tooltipPosition="bottom" (onClick)="excluir()" />
                }
            </app-page-header>

            <div class="grid grid-cols-1 lg:grid-cols-3 gap-4 mb-4">
                <div class="card mb-0! lg:col-span-2">
                    <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-3 text-sm">
                        <div class="min-w-0"><span class="text-surface-500">Situação</span><div><p-tag [value]="rotulo(d.situacao)" [severity]="cor(d.situacao)" />@if (!d.ativo) {<p-tag value="Desativado" severity="secondary" styleClass="ml-1" />}</div></div>
                        <div class="min-w-0"><span class="text-surface-500">Último contato</span><div class="font-medium" [pTooltip]="dh(d.ultimoContatoEm)">{{ rel(d.ultimoContatoEm) }} @if (d.ultimoEvento) {<span class="text-surface-500 font-normal">({{ d.ultimoEvento }})</span>}</div></div>
                        <div class="min-w-0"><span class="text-surface-500">Aplicação</span><div class="font-medium break-words">{{ d.aplicacaoNome || '—' }}</div></div>
                        <div class="min-w-0"><span class="text-surface-500">Perfil</span><div class="font-medium break-words">{{ d.perfilNome || '—' }}</div></div>
                        <div class="min-w-0"><span class="text-surface-500">Sinal (melhor gateway)</span><div class="font-medium">{{ d.ultimoRssi ?? '—' }} dBm / {{ d.ultimoSnr ?? '—' }} dB</div></div>
                        <div class="min-w-0"><span class="text-surface-500">Gateway</span><div class="font-medium font-mono break-all">{{ d.ultimoGateway || '—' }}</div></div>
                        <div class="min-w-0"><span class="text-surface-500">Bateria</span><div class="font-medium" [class]="bat(d.ultimaBateria)">{{ d.ultimaBateria !== null && d.ultimaBateria !== undefined ? d.ultimaBateria + '%' : '—' }}@if (d.alimentacaoExterna) {<span class="text-surface-500 font-normal"> · alimentação externa</span>}</div></div>
                        <div class="min-w-0"><span class="text-surface-500">Uplinks</span><div class="font-medium">{{ d.totalUplinks | number }} · FCnt {{ d.ultimoFCnt ?? '—' }}</div></div>
                        @if (d.descricao) {
                            <div class="sm:col-span-2 min-w-0"><span class="text-surface-500">Descrição</span><div class="break-words">{{ d.descricao }}</div></div>
                        }
                        @if (tags().length) {
                            <div class="sm:col-span-2 min-w-0"><span class="text-surface-500">Tags</span><div class="flex flex-wrap gap-1 mt-1">@for (t of tags(); track t) {<p-tag [value]="t" severity="secondary" />}</div></div>
                        }
                    </div>
                </div>
                <div class="card mb-0!">
                    <div class="text-surface-500 text-sm mb-2">Último valor recebido</div>
                    @if (d.ultimoObjeto) {
                        <pre class="text-xs m-0 whitespace-pre-wrap break-words max-h-64 overflow-auto">{{ json(d.ultimoObjeto) }}</pre>
                    } @else {
                        <div class="text-surface-400 text-sm">Nenhum dado decodificado. Confira o codec do perfil no ChirpStack.</div>
                    }
                </div>
            </div>

            <div class="card">
                @if (usaPeriodo()) {
                    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-2">
                        <span class="text-surface-500 text-sm">Período</span>
                        <app-period-selector [opcoes]="periodos" [(value)]="periodoValor" (changed)="mudarPeriodo($event)" />
                    </div>
                }
                <p-tabs [value]="aba()" (valueChange)="trocarAba($any($event))">
                    <p-tablist>
                        <p-tab value="controle"><i class="pi pi-sliders-h mr-2"></i>Controle</p-tab>
                        <p-tab value="energia"><i class="pi pi-bolt mr-2"></i>Energia</p-tab>
                        <p-tab value="comandos"><i class="pi pi-inbox mr-2"></i>Comandos</p-tab>
                        <p-tab value="agendamentos"><i class="pi pi-calendar-clock mr-2"></i>Agendamentos</p-tab>
                        <p-tab value="graficos"><i class="pi pi-chart-line mr-2"></i>Gráficos</p-tab>
                        <p-tab value="leituras"><i class="pi pi-list mr-2"></i>Leituras</p-tab>
                        <p-tab value="eventos"><i class="pi pi-bolt mr-2"></i>Eventos</p-tab>
                        <p-tab value="alertas"><i class="pi pi-bell mr-2"></i>Alertas</p-tab>
                    </p-tablist>
                    <p-tabpanels>
                        <p-tabpanel value="controle">
                            @if (aba() === 'controle') {
                                <app-controle-dispositivo [dispositivoId]="d.id" />
                            }
                        </p-tabpanel>

                        <p-tabpanel value="energia">
                            @if (aba() === 'energia') {
                                <app-energia-dispositivo [dispositivoId]="d.id" />
                            }
                        </p-tabpanel>

                        <p-tabpanel value="comandos">
                            @if (aba() === 'comandos') {
                                <app-comandos-lista [dispositivoId]="d.id" />
                            }
                        </p-tabpanel>

                        <p-tabpanel value="agendamentos">
                            @if (aba() === 'agendamentos') {
                                <app-agendamentos-lista [dispositivoId]="d.id" />
                            }
                        </p-tabpanel>

                        <p-tabpanel value="graficos">
                            @if (carregandoSeries()) {
                                <div class="text-center text-surface-500 py-8"><i class="pi pi-spin pi-spinner mr-2"></i>Carregando...</div>
                            } @else if (graficos().length === 0) {
                                <div class="text-center text-surface-500 py-8">Nenhuma leitura numérica no período.</div>
                            } @else {
                                <div class="grid grid-cols-1 xl:grid-cols-2 gap-4">
                                    @for (g of graficos(); track g.name) {
                                        <app-metric-chart [metric]="g" type="line" />
                                    }
                                </div>
                            }
                        </p-tabpanel>

                        <p-tabpanel value="leituras">
                            <p-table [value]="leituras()" [lazy]="true" (onLazyLoad)="lazyLeituras($event)" [paginator]="true" [rows]="50" [totalRecords]="totalLeituras()" [loading]="carregandoLeituras()" [showCurrentPageReport]="true" currentPageReportTemplate="{first}–{last} de {totalRecords}" dataKey="id" [rowHover]="true" styleClass="p-datatable-sm">
                                <ng-template #header>
                                    <tr>
                                        <th class="w-10"></th>
                                        <th>Recebida</th>
                                        <th class="hidden md:table-cell">FCnt / porta</th>
                                        <th class="hidden lg:table-cell">SF / DR</th>
                                        <th class="hidden md:table-cell">RSSI / SNR</th>
                                        <th>Dados</th>
                                    </tr>
                                </ng-template>
                                <ng-template #body let-l let-expanded="expanded">
                                    <tr>
                                        <td><p-button [pRowToggler]="l" [icon]="expanded ? 'pi pi-chevron-down' : 'pi pi-chevron-right'" [text]="true" [rounded]="true" size="small" /></td>
                                        <td class="text-sm">{{ dh(l.recebidoEm) }}</td>
                                        <td class="hidden md:table-cell text-sm">{{ l.fCnt ?? '—' }} / {{ l.fPort ?? '—' }}</td>
                                        <td class="hidden lg:table-cell text-sm">SF{{ l.spreadingFactor ?? '?' }} / DR{{ l.dr ?? '?' }}</td>
                                        <td class="hidden md:table-cell text-sm">{{ l.rssi ?? '—' }} / {{ l.snr ?? '—' }}</td>
                                        <td class="text-sm break-words">{{ resumo(l.objeto) }}</td>
                                    </tr>
                                </ng-template>
                                <ng-template #expandedrow let-l>
                                    <tr>
                                        <td colspan="6">
                                            <div class="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
                                                <div class="min-w-0">
                                                    <div class="text-surface-500 mb-1">Objeto decodificado</div>
                                                    <pre class="text-xs m-0 whitespace-pre-wrap break-words">{{ json(l.objeto) || '—' }}</pre>
                                                </div>
                                                <div class="min-w-0 space-y-1">
                                                    <div><span class="text-surface-500">Payload (Base64):</span> <span class="font-mono break-all">{{ l.dadosBase64 || '—' }}</span></div>
                                                    <div><span class="text-surface-500">Gateway:</span> <span class="font-mono">{{ l.gatewayId || '—' }}</span> ({{ l.qtdGateways ?? 0 }} ouviram)</div>
                                                    <div><span class="text-surface-500">Frequência:</span> {{ l.frequencia ? (l.frequencia / 1000000 | number: '1.1-3') + ' MHz' : '—' }}</div>
                                                    <div><span class="text-surface-500">Confirmado:</span> {{ l.confirmado ? 'sim' : 'não' }}</div>
                                                </div>
                                            </div>
                                        </td>
                                    </tr>
                                </ng-template>
                                <ng-template #emptymessage>
                                    <tr><td colspan="6" class="text-center py-8 text-surface-500">Nenhuma leitura no período.</td></tr>
                                </ng-template>
                            </p-table>
                        </p-tabpanel>

                        <p-tabpanel value="eventos">
                            <p-table [value]="eventos()" [lazy]="true" (onLazyLoad)="lazyEventos($event)" [paginator]="true" [rows]="50" [totalRecords]="totalEventos()" [loading]="carregandoEventos()" [showCurrentPageReport]="true" currentPageReportTemplate="{first}–{last} de {totalRecords}" [rowHover]="true" styleClass="p-datatable-sm">
                                <ng-template #header>
                                    <tr>
                                        <th>Recebido</th>
                                        <th>Tipo</th>
                                        <th>Descrição</th>
                                    </tr>
                                </ng-template>
                                <ng-template #body let-e>
                                    <tr>
                                        <td class="text-sm">{{ dh(e.recebidoEm) }}</td>
                                        <td><p-tag [value]="e.tipo" [severity]="corEvento(e)" /></td>
                                        <td class="text-sm break-words">{{ e.descricao || '—' }}</td>
                                    </tr>
                                </ng-template>
                                <ng-template #emptymessage>
                                    <tr><td colspan="3" class="text-center py-8 text-surface-500">Nenhum evento no período.</td></tr>
                                </ng-template>
                            </p-table>
                        </p-tabpanel>

                        <p-tabpanel value="alertas">
                            <p-table [value]="alertas()" [rowHover]="true" styleClass="p-datatable-sm" [loading]="carregandoAlertas()">
                                <ng-template #header>
                                    <tr>
                                        <th>Disparado</th>
                                        <th>Situação</th>
                                        <th>Mensagem</th>
                                    </tr>
                                </ng-template>
                                <ng-template #body let-a>
                                    <tr>
                                        <td class="text-sm">{{ dh(a.disparadoEm) }}</td>
                                        <td><p-tag [value]="a.status" [severity]="a.status === 'ABERTO' ? 'danger' : a.status === 'RECONHECIDO' ? 'warn' : 'success'" /></td>
                                        <td class="text-sm break-words">{{ a.mensagem }}</td>
                                    </tr>
                                </ng-template>
                                <ng-template #emptymessage>
                                    <tr><td colspan="3" class="text-center py-8 text-surface-500">Nenhum alerta deste dispositivo.</td></tr>
                                </ng-template>
                            </p-table>
                        </p-tabpanel>
                    </p-tabpanels>
                </p-tabs>
            </div>

            <p-dialog csmTravaEdicao #trava="travaEdicao" [(visible)]="dialogo" header="Editar dispositivo" [modal]="true" [style]="{ width: '32rem' }" [breakpoints]="{ '640px': '95vw' }">
                <div class="flex flex-col gap-5">
                    <p class="m-0 text-sm text-surface-500">Nome, aplicação e perfil são editados no ChirpStack e chegam com a próxima mensagem.</p>
                    <div class="min-w-0">
                        <label for="descricao" class="block font-medium mb-2">Descrição</label>
                        <textarea pTextarea id="descricao" class="w-full" rows="3" maxlength="300" placeholder="Onde está, o que mede..." [(ngModel)]="formDescricao"></textarea>
                    </div>
                    <label class="flex items-start gap-3">
                        <p-toggleswitch [(ngModel)]="formAtivo" />
                        <span><span class="font-medium">Ativo</span><br /><span class="text-sm text-surface-500">Desativado some do painel; a coleta continua.</span></span>
                    </label>
                </div>
                <ng-template #footer>
                    <p-button label="Cancelar" severity="secondary" [outlined]="true" (onClick)="trava.fechar()" />
                    <p-button label="Salvar" icon="pi pi-check" [loading]="salvando()" (onClick)="salvar()" />
                </ng-template>
            </p-dialog>
        } @else {
            <div class="card text-center text-surface-500 py-8"><i class="pi pi-spin pi-spinner mr-2"></i>Carregando...</div>
        }
    `
})
export class DispositivoDetalhe implements OnInit {
    /** :id da rota (withComponentInputBinding). */
    id = input.required<string>();

    auth = inject(AuthService);
    private api = inject(MonitoramentoService);
    private notify = inject(NotifyService);
    private router = inject(Router);
    private route = inject(ActivatedRoute);

    d = signal<Dispositivo | null>(null);
    aba = signal('graficos');
    periodos = PERIODOS_LEITURAS;
    periodoValor = '24h';
    private periodo: PeriodoApi = {};

    series = signal<Serie[]>([]);
    carregandoSeries = signal(false);
    leituras = signal<Leitura[]>([]);
    totalLeituras = signal(0);
    carregandoLeituras = signal(false);
    eventos = signal<EventoDispositivo[]>([]);
    totalEventos = signal(0);
    carregandoEventos = signal(false);
    alertas = signal<Alerta[]>([]);
    carregandoAlertas = signal(false);

    dialogo = false;
    formDescricao = '';
    formAtivo = true;
    salvando = signal(false);

    rotulo = rotuloSituacao;
    cor = corSituacao;
    bat = corBateria;
    rel = tempoRelativo;
    dh = dataHora;
    resumo = resumoObjeto;
    json = jsonBonito;

    tags = computed(() => {
        const t = this.d()?.tags;
        if (!t) {
            return [];
        }
        try {
            return Object.entries(JSON.parse(t) as Record<string, string>).map(([k, v]) => `${k}: ${v}`);
        } catch {
            return [];
        }
    });

    graficos = computed<Metric[]>(() =>
        this.series().map((s) => ({
            name: ROTULO_CAMPO[s.campo] ?? s.campo,
            timestamps: s.pontos.map((p) => p.t),
            datasets: [{ label: ROTULO_CAMPO[s.campo] ?? s.campo, data: s.pontos.map((p) => Number(p.v)) }],
            kind: 'GAUGE'
        }))
    );

    private get num(): number {
        return Number(this.id());
    }

    /** Abas com período (as de atuação têm os próprios filtros). */
    usaPeriodo = computed(() => !ABAS_ATUACAO.includes(this.aba()));

    ngOnInit(): void {
        const abaInicial = this.route.snapshot.queryParamMap.get('aba');

        if (abaInicial && [...ABAS_ATUACAO, 'graficos', 'leituras', 'eventos', 'alertas'].includes(abaInicial)) {
            this.aba.set(abaInicial);
        }
        this.mudarPeriodo(PERIODOS_LEITURAS.find((p) => p.value === this.periodoValor)!);
        this.api.dispositivo(this.num).subscribe({
            next: (d) => this.d.set(d),
            error: (e) => {
                this.notify.erro(e);
                this.router.navigate(['/monitoramento/dispositivos']);
            }
        });
    }

    mudarPeriodo(p: Periodo): void {
        const { start, end } = intervaloDe(p);
        this.periodo = { de: start.toISOString(), ate: end.toISOString() };
        this.carregarAba(this.aba());
    }

    trocarAba(aba: string): void {
        this.aba.set(aba);
        this.router.navigate([], { relativeTo: this.route, queryParams: { aba }, queryParamsHandling: 'merge', replaceUrl: true });
        this.carregarAba(aba);
    }

    private carregarAba(aba: string): void {
        if (aba === 'graficos') {
            this.carregandoSeries.set(true);
            this.api.series(this.num, this.periodo).subscribe({
                next: (s) => {
                    this.series.set(s);
                    this.carregandoSeries.set(false);
                },
                error: (e) => {
                    this.carregandoSeries.set(false);
                    this.notify.erro(e);
                }
            });
        } else if (aba === 'leituras') {
            this.lazyLeituras({ first: 0, rows: 50 });
        } else if (aba === 'eventos') {
            this.lazyEventos({ first: 0, rows: 50 });
        } else if (aba === 'alertas') {
            this.carregandoAlertas.set(true);
            this.api.alertas(null, this.num, 0, 100).subscribe({
                next: (r) => {
                    this.alertas.set(r.result);
                    this.carregandoAlertas.set(false);
                },
                error: (e) => {
                    this.carregandoAlertas.set(false);
                    this.notify.erro(e);
                }
            });
        }
    }

    lazyLeituras(e: TableLazyLoadEvent): void {
        const rows = e.rows ?? 50;
        this.carregandoLeituras.set(true);
        this.api.leituras(this.num, this.periodo, Math.floor((e.first ?? 0) / rows), rows).subscribe({
            next: (r) => {
                this.leituras.set(r.result);
                this.totalLeituras.set(Number(r.totalCount));
                this.carregandoLeituras.set(false);
            },
            error: (err) => {
                this.carregandoLeituras.set(false);
                this.notify.erro(err);
            }
        });
    }

    lazyEventos(e: TableLazyLoadEvent): void {
        const rows = e.rows ?? 50;
        this.carregandoEventos.set(true);
        this.api.eventos(this.num, this.periodo, Math.floor((e.first ?? 0) / rows), rows).subscribe({
            next: (r) => {
                this.eventos.set(r.result);
                this.totalEventos.set(Number(r.totalCount));
                this.carregandoEventos.set(false);
            },
            error: (err) => {
                this.carregandoEventos.set(false);
                this.notify.erro(err);
            }
        });
    }

    corEvento(e: EventoDispositivo): 'danger' | 'warn' | 'info' | 'secondary' | 'success' {
        if (e.tipo === 'log') {
            return e.nivel === 'ERROR' ? 'danger' : e.nivel === 'WARNING' ? 'warn' : 'info';
        }
        return e.tipo === 'join' ? 'success' : e.tipo === 'status' ? 'info' : 'secondary';
    }

    editar(): void {
        const d = this.d();
        if (!d) {
            return;
        }
        this.formDescricao = d.descricao ?? '';
        this.formAtivo = d.ativo;
        this.dialogo = true;
    }

    salvar(): void {
        this.salvando.set(true);
        this.api.atualizarDispositivo(this.num, this.formDescricao.trim() || null, this.formAtivo).subscribe({
            next: (d) => {
                this.d.set(d);
                this.salvando.set(false);
                this.dialogo = false;
                this.notify.sucesso('Dispositivo atualizado.');
            },
            error: (e) => {
                this.salvando.set(false);
                this.notify.erro(e);
            }
        });
    }

    excluir(): void {
        const d = this.d();
        if (!d) {
            return;
        }
        this.notify.confirmarExclusao(
            `o dispositivo ${d.nome}`,
            () =>
                this.api.excluirDispositivo(d.id).subscribe({
                    next: () => {
                        this.notify.sucesso('Dispositivo excluído.');
                        this.router.navigate(['/monitoramento/dispositivos']);
                    },
                    error: (e) => this.notify.erro(e)
                }),
            'Apaga também todas as leituras, eventos e alertas dele. Se o sensor continuar enviando, ele volta a aparecer.'
        );
    }
}
