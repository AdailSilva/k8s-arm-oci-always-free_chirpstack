import { Component, computed, DestroyRef, inject, input, OnInit, output, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { AuthService } from '@/app/core/auth/auth.service';
import { AtuacaoService } from '@/app/core/http/atuacao.service';
import { EnergiaResponse, EquipamentoResponse, MedicaoPzem, PerifericoResponse } from '@/app/core/http/atuacao.models';
import { MonitoramentoService } from '@/app/core/http/monitoramento.service';
import { Metric, Serie } from '@/app/core/http/models';
import { NotifyService } from '@/app/core/notificacao/notify.service';
import { dataHora, tempoRelativo } from '@/app/core/format/format';
import { MetricChart } from '@/app/shared/graficos/metric-chart';
import { intervaloDe, Periodo, PERIODOS_LEITURAS, PeriodSelector } from '@/app/shared/graficos/period-selector';
import { autoAtualizar, medicao, moeda, numBr } from './atuacao-util';
import { ConfirmacaoCritica } from './confirmacao-critica';
import { enviarComando } from './envio';

interface Medidor {
    p: PerifericoResponse;
    m: MedicaoPzem | null;
    resumo: EnergiaResponse | null;
}

const CAMPOS_GRAFICO: { campo: string; rotulo: string }[] = [
    { campo: 'potencia', rotulo: 'Potência (W)' },
    { campo: 'tensao', rotulo: 'Tensão (V)' },
    { campo: 'corrente', rotulo: 'Corrente (A)' },
    { campo: 'fp', rotulo: 'Fator de potência' }
];

/**
 * Aba "Energia" do dispositivo: última medição de cada PZEM-004T (tensão, corrente, potência,
 * energia, frequência, fator de potência e alarme), consumo e custo no período pela tarifa,
 * gráficos e as ações ler agora, zerar energia e limite de potência.
 */
@Component({
    selector: 'app-energia-dispositivo',
    standalone: true,
    imports: [CommonModule, FormsModule, ButtonModule, DialogModule, InputNumberModule, TagModule, TooltipModule, ConfirmacaoCritica, MetricChart, PeriodSelector],
    template: `
        @if (carregando() && !eq()) {
            <div class="text-center text-surface-500 py-8"><i class="pi pi-spin pi-spinner mr-2"></i>Carregando...</div>
        } @else if (medidores().length === 0) {
            <div class="flex flex-col items-center text-center gap-2 py-8 px-4 text-surface-500">
                <i class="pi pi-bolt text-3xl!"></i>
                <div>{{ eq()?.configurado ? 'Nenhum medidor PZEM-004T neste equipamento.' : 'Configure o equipamento na aba Controle e adicione o medidor PZEM-004T.' }}</div>
            </div>
        } @else {
            <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4">
                <span class="text-surface-500 text-sm">Consumo e gráficos no período</span>
                <div class="flex flex-wrap items-center gap-2">
                    <app-period-selector [opcoes]="periodos" [(value)]="periodoValor" (changed)="mudarPeriodo($event)" />
                    <p-button icon="pi pi-refresh" severity="secondary" [outlined]="true" pTooltip="Atualizar" ariaLabel="Atualizar" (onClick)="carregar()" />
                </div>
            </div>

            <div class="flex flex-col gap-6">
                @for (x of medidores(); track x.p.id) {
                    <div class="border border-surface-200 dark:border-surface-700 rounded-xl p-4">
                        <div class="flex flex-col md:flex-row md:items-start justify-between gap-3 mb-4">
                            <div class="min-w-0">
                                <div class="font-semibold text-lg break-words">{{ x.p.nome }}</div>
                                <div class="text-xs text-surface-500">
                                    Canal {{ x.p.canal }}
                                    @if (x.p.enderecoModbus) {
                                        · Modbus {{ x.p.enderecoModbus }}
                                    }
                                    @if (x.p.leituraEm) {
                                        · medido <span [pTooltip]="dh(x.p.leituraEm)">{{ rel(x.p.leituraEm) }}</span>
                                    }
                                </div>
                            </div>
                            <div class="flex flex-wrap items-center gap-2">
                                @if (x.m?.alarme) {
                                    <p-tag value="Alarme de potência" severity="danger" icon="pi pi-exclamation-triangle" />
                                }
                                @if (auth.podeEscrever()) {
                                    @if (suporta('PZEM_LER')) {
                                        <p-button label="Ler agora" icon="pi pi-download" size="small" [loading]="enviando() === 'LER-' + x.p.canal" (onClick)="ler(x.p)" />
                                    }
                                    @if (suporta('PZEM_LIMITE_POTENCIA')) {
                                        <p-button label="Limite" icon="pi pi-sliders-v" size="small" severity="secondary" [outlined]="true" (onClick)="abrirLimite(x.p)" />
                                    }
                                    @if (suporta('PZEM_ZERAR_ENERGIA')) {
                                        <p-button label="Zerar energia" icon="pi pi-eraser" size="small" severity="danger" [outlined]="true" (onClick)="zerar(x.p)" />
                                    }
                                }
                            </div>
                        </div>

                        @if (x.m; as m) {
                            <div class="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-7 gap-3 mb-4">
                                @for (c of cartoes(m); track c.rotulo) {
                                    <div class="rounded-lg bg-surface-50 dark:bg-surface-800 px-3 py-2 min-w-0">
                                        <div class="text-xs text-surface-500">{{ c.rotulo }}</div>
                                        <div class="font-semibold text-lg break-words">{{ c.valor }}</div>
                                    </div>
                                }
                            </div>
                        } @else {
                            <div class="text-sm text-surface-500 mb-4">Nenhuma medição recebida ainda. Use "Ler agora" ou aguarde a próxima telemetria.</div>
                        }

                        <div class="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-4">
                            <div class="rounded-lg border border-surface-200 dark:border-surface-700 px-3 py-2 min-w-0">
                                <div class="text-xs text-surface-500">Consumo no período</div>
                                <div class="font-semibold text-lg">{{ x.resumo?.consumoKwh !== null && x.resumo?.consumoKwh !== undefined ? num(x.resumo?.consumoKwh, 3) + ' kWh' : '—' }}</div>
                            </div>
                            <div class="rounded-lg border border-surface-200 dark:border-surface-700 px-3 py-2 min-w-0">
                                <div class="text-xs text-surface-500">Custo no período</div>
                                <div class="font-semibold text-lg">{{ moeda(x.resumo?.custo) }}</div>
                                @if (!x.resumo?.tarifaKwh) {
                                    <div class="text-xs text-surface-500">Sem tarifa (aba Controle)</div>
                                }
                            </div>
                            <div class="rounded-lg border border-surface-200 dark:border-surface-700 px-3 py-2 min-w-0">
                                <div class="text-xs text-surface-500">Potência média</div>
                                <div class="font-semibold text-lg">{{ x.resumo?.potenciaMedia !== null && x.resumo?.potenciaMedia !== undefined ? num(x.resumo?.potenciaMedia, 1) + ' W' : '—' }}</div>
                            </div>
                            <div class="rounded-lg border border-surface-200 dark:border-surface-700 px-3 py-2 min-w-0">
                                <div class="text-xs text-surface-500">Potência máxima</div>
                                <div class="font-semibold text-lg">{{ x.resumo?.potenciaMaxima !== null && x.resumo?.potenciaMaxima !== undefined ? num(x.resumo?.potenciaMaxima, 1) + ' W' : '—' }}</div>
                                <div class="text-xs text-surface-500">{{ x.resumo?.leituras ?? 0 }} leituras</div>
                            </div>
                        </div>

                        @if (graficos(x.p.canal).length) {
                            <div class="grid grid-cols-1 xl:grid-cols-2 gap-4">
                                @for (g of graficos(x.p.canal); track g.name) {
                                    <app-metric-chart [metric]="g" type="line" />
                                }
                            </div>
                        } @else if (!carregandoSeries()) {
                            <div class="text-sm text-surface-500">Sem leituras do medidor no período.</div>
                        }
                    </div>
                }
            </div>
        }

        <p-dialog
            [visible]="!!limiteDe()"
            (visibleChange)="!$event && limiteDe.set(null)"
            [header]="'Limite de potência: ' + (limiteDe()?.nome ?? '')"
            [modal]="true"
            [draggable]="false"
            [style]="{ width: '26rem' }"
            [breakpoints]="{ '640px': '95vw' }"
        >
            <div class="flex flex-col gap-2">
                <label for="lim-w" class="block font-medium">Limite</label>
                <p-inputnumber inputId="lim-w" [(ngModel)]="limiteWatts" [min]="0" [max]="23000" suffix=" W" locale="pt-BR" styleClass="w-full" inputStyleClass="w-full" />
                <small class="text-surface-500">De 0 a 23.000 W. Acima disso o medidor liga o alarme; use uma automação para desligar um relé quando isso acontecer.</small>
            </div>
            <ng-template #footer>
                <div class="flex flex-wrap justify-end gap-2">
                    <p-button label="Cancelar" severity="secondary" [outlined]="true" (onClick)="limiteDe.set(null)" />
                    <p-button label="Enviar" icon="pi pi-send" (onClick)="enviarLimite()" />
                </div>
            </ng-template>
        </p-dialog>
        <app-confirmacao-critica />
    `
})
export class EnergiaDispositivo implements OnInit {
    auth = inject(AuthService);
    private api = inject(AtuacaoService);
    private mon = inject(MonitoramentoService);
    private notify = inject(NotifyService);
    private destroyRef = inject(DestroyRef);
    private critico = viewChild.required(ConfirmacaoCritica);

    dispositivoId = input.required<number>();
    comandoEnviado = output<void>();

    eq = signal<EquipamentoResponse | null>(null);
    carregando = signal(false);
    resumos = signal<Record<number, EnergiaResponse>>({});
    series = signal<Serie[]>([]);
    carregandoSeries = signal(false);
    enviando = signal<string | null>(null);

    periodos = PERIODOS_LEITURAS;
    periodoValor = '24h';
    private de = '';
    private ate = '';

    limiteDe = signal<PerifericoResponse | null>(null);
    limiteWatts: number | null = 2300;

    medidores = computed<Medidor[]>(() =>
        (this.eq()?.perifericos ?? [])
            .filter((p) => p.tipo === 'PZEM004T')
            .sort((a, b) => a.canal - b.canal)
            .map((p) => ({ p, m: medicao(p.leitura), resumo: this.resumos()[p.canal] ?? null }))
    );

    dh = dataHora;
    rel = tempoRelativo;
    moeda = moeda;
    num = numBr;

    ngOnInit(): void {
        this.definirPeriodo(PERIODOS_LEITURAS.find((p) => p.value === this.periodoValor)!);
        this.carregar();
        autoAtualizar(this.destroyRef, () => this.carregarEquipamento(true), 30_000);
    }

    private definirPeriodo(p: Periodo): void {
        const { start, end } = intervaloDe(p);

        this.de = start.toISOString();
        this.ate = end.toISOString();
    }

    mudarPeriodo(p: Periodo): void {
        this.definirPeriodo(p);
        this.carregarPeriodo();
    }

    carregar(): void {
        this.carregarEquipamento(false, () => this.carregarPeriodo());
    }

    private carregarEquipamento(silencioso: boolean, depois?: () => void): void {
        if (!silencioso) {
            this.carregando.set(true);
        }
        this.api.equipamento(this.dispositivoId()).subscribe({
            next: (e) => {
                this.eq.set(e);
                this.carregando.set(false);
                depois?.();
            },
            error: (err) => {
                this.carregando.set(false);

                if (!silencioso) {
                    this.notify.erro(err);
                }
            }
        });
    }

    private carregarPeriodo(): void {
        const id = this.dispositivoId();

        for (const x of this.medidores()) {
            this.api.energia(id, x.p.canal, this.de, this.ate).subscribe({
                next: (r) => this.resumos.update((m) => ({ ...m, [x.p.canal]: r })),
                error: () =>
                    this.resumos.update((m) => {
                        const c = { ...m };

                        delete c[x.p.canal];

                        return c;
                    })
            });
        }

        if (this.medidores().length) {
            this.carregandoSeries.set(true);
            this.mon.series(id, { de: this.de, ate: this.ate }).subscribe({
                next: (s) => {
                    this.series.set(s.filter((x) => x.campo.startsWith('pzem.')));
                    this.carregandoSeries.set(false);
                },
                error: () => this.carregandoSeries.set(false)
            });
        }
    }

    graficos(canal: number): Metric[] {
        const out: Metric[] = [];

        for (const c of CAMPOS_GRAFICO) {
            const s = this.series().find((x) => x.campo === `pzem.${canal}.${c.campo}`);

            if (s && s.pontos.length) {
                out.push({
                    name: c.rotulo,
                    timestamps: s.pontos.map((p) => p.t),
                    datasets: [{ label: c.rotulo, data: s.pontos.map((p) => Number(p.v)) }],
                    kind: 'GAUGE'
                } as Metric);
            }
        }

        return out;
    }

    cartoes(m: MedicaoPzem): { rotulo: string; valor: string }[] {
        const kwh = m.energia !== null && m.energia !== undefined ? Number(m.energia) / 1000 : null;

        return [
            { rotulo: 'Tensão', valor: `${numBr(m.tensao, 1)} V` },
            { rotulo: 'Corrente', valor: `${numBr(m.corrente, 3)} A` },
            { rotulo: 'Potência', valor: `${numBr(m.potencia, 1)} W` },
            { rotulo: 'Energia', valor: `${numBr(kwh, 3)} kWh` },
            { rotulo: 'Frequência', valor: `${numBr(m.frequencia, 1)} Hz` },
            { rotulo: 'Fator de potência', valor: numBr(m.fp, 2) },
            { rotulo: 'Custo acumulado', valor: moeda(m.custoTotal) }
        ];
    }

    suporta(t: string): boolean {
        return (this.eq()?.comandos ?? []).some((c) => c.tipo === t);
    }

    private ctx() {
        return { api: this.api, notify: this.notify, critico: this.critico() };
    }

    private enviar(tipo: 'PZEM_LER' | 'PZEM_ZERAR_ENERGIA' | 'PZEM_LIMITE_POTENCIA', canal: number, chave: string, extra: { watts?: number } = {}): void {
        this.enviando.set(chave);
        enviarComando(this.ctx(), this.dispositivoId(), { tipo, parametros: { canal, ...extra } }, (c) => {
            this.enviando.set(null);

            if (c) {
                this.comandoEnviado.emit();
            }
        });
    }

    ler(p: PerifericoResponse): void {
        this.enviar('PZEM_LER', p.canal, `LER-${p.canal}`);
    }

    zerar(p: PerifericoResponse): void {
        this.notify.confirmar('Zerar energia', `Zerar o contador de energia (kWh) de "${p.nome}"? Não há como desfazer.`, () => this.enviar('PZEM_ZERAR_ENERGIA', p.canal, `ZERAR-${p.canal}`), 'Zerar energia');
    }

    abrirLimite(p: PerifericoResponse): void {
        this.limiteWatts = 2300;
        this.limiteDe.set(p);
    }

    enviarLimite(): void {
        const p = this.limiteDe();

        if (!p) {
            return;
        }

        if (this.limiteWatts === null || this.limiteWatts === undefined) {
            this.notify.aviso('Informe o limite em watts.');

            return;
        }
        this.limiteDe.set(null);
        this.enviar('PZEM_LIMITE_POTENCIA', p.canal, `LIM-${p.canal}`, { watts: this.limiteWatts });
    }
}
