import { Component, computed, DestroyRef, inject, input, OnInit, output, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TooltipModule } from 'primeng/tooltip';
import { AuthService } from '@/app/core/auth/auth.service';
import { AtuacaoService } from '@/app/core/http/atuacao.service';
import { AcaoRele, EquipamentoRequest, EquipamentoResponse, ModeloFirmware, ModeloResponse, ParametrosComando, PerifericoRequest, PerifericoResponse, PoliticaRequest, TipoComando, TipoPeriferico } from '@/app/core/http/atuacao.models';
import { NotifyService } from '@/app/core/notificacao/notify.service';
import { dataHora, tempoRelativo } from '@/app/core/format/format';
import { jsonBonito } from '@/app/pages/monitoramento/objeto';
import { autoAtualizar, limparParametros, validarParametros } from './atuacao-util';
import { ComandoForm } from './comando-form';
import { ConfirmacaoCritica } from './confirmacao-critica';
import { enviarComando } from './envio';
import { PoliticaForm, politicaLimpa, validarPolitica } from './politica-form';

interface PerifericoForm {
    tipo: TipoPeriferico;
    canal: number | null;
    nome: string;
    gpio: number | null;
    enderecoModbus: number | null;
    critico: boolean;
}

/**
 * Aba "Controle" do dispositivo: equipamento (modelo do firmware e periféricos), relés com estado
 * desejado × reportado, acionamento rápido (ligar, desligar, alternar, pulso), envio de qualquer
 * comando suportado com política própria e o último STATUS recebido.
 */
@Component({
    selector: 'app-controle-dispositivo',
    standalone: true,
    imports: [CommonModule, FormsModule, ButtonModule, DialogModule, InputNumberModule, InputTextModule, MessageModule, SelectModule, TagModule, ToggleSwitchModule, TooltipModule, ComandoForm, ConfirmacaoCritica, PoliticaForm],
    template: `
        @if (carregando() && !eq()) {
            <div class="text-center text-surface-500 py-8"><i class="pi pi-spin pi-spinner mr-2"></i>Carregando...</div>
        } @else if (eq(); as e) {
            @if (!e.configurado) {
                <div class="flex flex-col items-center text-center gap-3 py-8 px-4">
                    <span class="w-14 h-14 rounded-2xl bg-primary-50 dark:bg-primary-900/40 text-primary flex items-center justify-center"><i class="pi pi-sliders-h text-2xl!"></i></span>
                    <div class="font-semibold text-lg">Equipamento não configurado</div>
                    <p class="m-0 text-surface-500 max-w-xl">
                        Informe o modelo do firmware e o que está ligado a ele (relés, medidor PZEM-004T). Depois disso dá para acionar os relés, ler a energia e criar cenas, agendamentos e automações com este dispositivo.
                    </p>
                    @if (auth.podeEscrever()) {
                        <p-button icon="pi pi-cog" label="Configurar equipamento" (onClick)="abrirConfig()" />
                    }
                </div>
            } @else {
                <div class="flex flex-col gap-4">
                    <div class="flex flex-col md:flex-row md:items-center justify-between gap-3">
                        <div class="min-w-0">
                            <div class="flex flex-wrap items-center gap-2">
                                <p-tag [value]="e.modeloRotulo || e.modelo || ''" icon="pi pi-microchip" />
                                @if (e.reconciliar && reles().length) {
                                    <p-tag value="Reconciliação ligada" severity="info" icon="pi pi-sync" pTooltip="Se o relé divergir do estado desejado, o servidor manda de volta (no máximo a cada 10 min)." />
                                }
                                @if (e.tarifaKwh) {
                                    <p-tag [value]="'Tarifa ' + moeda(e.tarifaKwh) + '/kWh'" severity="secondary" />
                                }
                            </div>
                            @if (e.statusEm) {
                                <div class="text-xs text-surface-500 mt-2" [pTooltip]="dh(e.statusEm)">Último STATUS {{ rel(e.statusEm) }}</div>
                            }
                        </div>
                        <div class="flex flex-wrap gap-2">
                            <p-button icon="pi pi-refresh" severity="secondary" [outlined]="true" pTooltip="Atualizar" ariaLabel="Atualizar" (onClick)="carregar()" />
                            @if (auth.podeEscrever()) {
                                @if (suporta('STATUS')) {
                                    <p-button icon="pi pi-info-circle" label="Pedir STATUS" severity="secondary" [outlined]="true" [loading]="enviando() === 'STATUS'" (onClick)="rapido('STATUS')" />
                                }
                                <p-button icon="pi pi-send" label="Enviar comando" (onClick)="abrirComando()" />
                                <p-button icon="pi pi-cog" severity="secondary" [outlined]="true" pTooltip="Configurar equipamento" ariaLabel="Configurar equipamento" (onClick)="abrirConfig()" />
                            }
                        </div>
                    </div>

                    @if (reles().length) {
                        <div>
                            <div class="font-semibold mb-3">Relés</div>
                            <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3">
                                @for (r of reles(); track r.id) {
                                    <div class="border border-surface-200 dark:border-surface-700 rounded-xl p-4 flex flex-col gap-3 min-w-0">
                                        <div class="flex items-start justify-between gap-2">
                                            <div class="min-w-0">
                                                <div class="font-semibold break-words">{{ r.nome }}</div>
                                                <div class="text-xs text-surface-500">
                                                    Canal {{ r.canal }}
                                                    @if (r.gpio !== null && r.gpio !== undefined) {
                                                        · GPIO {{ r.gpio }}
                                                    }
                                                </div>
                                            </div>
                                            @if (r.critico) {
                                                <p-tag value="Crítico" severity="danger" icon="pi pi-exclamation-triangle" />
                                            }
                                        </div>
                                        <div class="grid grid-cols-2 gap-2 text-sm">
                                            <div class="rounded-lg bg-surface-50 dark:bg-surface-800 px-3 py-2">
                                                <div class="text-surface-500 text-xs">Desejado</div>
                                                <div class="font-semibold" [class]="corEstado(r.estadoDesejado)">{{ rotuloEstado(r.estadoDesejado) }}</div>
                                            </div>
                                            <div class="rounded-lg bg-surface-50 dark:bg-surface-800 px-3 py-2">
                                                <div class="text-surface-500 text-xs">Reportado</div>
                                                <div class="font-semibold" [class]="corEstado(r.estadoReportado)">{{ rotuloEstado(r.estadoReportado) }}</div>
                                                @if (r.reportadoEm) {
                                                    <div class="text-xs text-surface-500" [pTooltip]="dh(r.reportadoEm)">{{ rel(r.reportadoEm) }}</div>
                                                }
                                            </div>
                                        </div>
                                        @if (divergente(r)) {
                                            <p-message severity="warn" size="small" icon="pi pi-exclamation-circle" text="Estado reportado diferente do desejado." />
                                        }
                                        @if (auth.podeEscrever()) {
                                            <div class="flex flex-wrap gap-2">
                                                @if (suporta('RELE_DEFINIR')) {
                                                    <p-button label="Ligar" icon="pi pi-power-off" size="small" severity="success" [loading]="ocupado(r, 'LIGAR')" (onClick)="definir(r, 'LIGAR')" />
                                                    <p-button label="Desligar" icon="pi pi-stop-circle" size="small" severity="secondary" [loading]="ocupado(r, 'DESLIGAR')" (onClick)="definir(r, 'DESLIGAR')" />
                                                    <p-button label="Alternar" icon="pi pi-arrow-right-arrow-left" size="small" severity="secondary" [outlined]="true" [loading]="ocupado(r, 'ALTERNAR')" (onClick)="definir(r, 'ALTERNAR')" />
                                                }
                                                @if (suporta('RELE_PULSO')) {
                                                    <p-button label="Pulso" icon="pi pi-stopwatch" size="small" severity="secondary" [outlined]="true" (onClick)="abrirPulso(r)" />
                                                }
                                            </div>
                                        }
                                    </div>
                                }
                            </div>
                        </div>
                    }

                    @if (medidores().length) {
                        <div class="text-sm text-surface-500">
                            <i class="pi pi-bolt mr-1"></i>{{ medidores().length === 1 ? '1 medidor de energia' : medidores().length + ' medidores de energia' }} ({{ nomesMedidores() }}): veja a aba <b>Energia</b>.
                        </div>
                    }

                    @if (e.status) {
                        <div>
                            <div class="font-semibold mb-2">Último STATUS</div>
                            <pre class="text-xs m-0 whitespace-pre-wrap break-words max-h-64 overflow-auto bg-surface-50 dark:bg-surface-800 rounded-lg p-3">{{ json(e.status) }}</pre>
                        </div>
                    }
                </div>
            }
        } @else {
            <div class="text-center text-surface-500 py-8">Não foi possível carregar o equipamento.</div>
        }

        <!-- Configuração do equipamento -->
        <p-dialog [visible]="dlgConfig()" (visibleChange)="dlgConfig.set($event)" header="Equipamento" [modal]="true" [draggable]="false" [style]="{ width: '52rem' }" [breakpoints]="{ '960px': '95vw' }">
            <div class="flex flex-col gap-5">
                <div class="min-w-0">
                    <label for="eq-modelo" class="block font-medium mb-2">Modelo do firmware <span class="text-red-500">*</span></label>
                    <p-select inputId="eq-modelo" [options]="modelos()" optionLabel="rotulo" optionValue="modelo" [ngModel]="cfgModelo()" (ngModelChange)="mudarModelo($event)" placeholder="Escolha o modelo" styleClass="w-full" appendTo="body" />
                    @if (modeloSel(); as m) {
                        <small class="text-surface-500 block mt-1">{{ m.descricao }}</small>
                        @for (o of m.observacoes; track $index) {
                            <small class="text-surface-500 block"><i class="pi pi-info-circle mr-1"></i>{{ o }}</small>
                        }
                    }
                </div>

                <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <label class="flex items-start gap-3 cursor-pointer">
                        <p-toggleswitch class="shrink-0" [(ngModel)]="cfgReconciliar" />
                        <span><span class="font-medium">Reconciliar relés</span><br /><span class="text-sm text-surface-500">Se o estado reportado divergir do desejado (reinício, acionamento local), o servidor manda de volta.</span></span>
                    </label>
                    <div class="min-w-0">
                        <label for="eq-tarifa" class="block font-medium mb-2">Tarifa de energia</label>
                        <p-inputnumber
                            inputId="eq-tarifa"
                            [(ngModel)]="cfgTarifa"
                            mode="currency"
                            currency="BRL"
                            locale="pt-BR"
                            [minFractionDigits]="2"
                            [maxFractionDigits]="5"
                            [min]="0"
                            placeholder="R$ por kWh"
                            styleClass="w-full"
                            inputStyleClass="w-full"
                        />
                        <small class="text-surface-500">Por kWh, para calcular o custo na aba Energia.</small>
                    </div>
                </div>

                <div>
                    <div class="flex flex-wrap items-center justify-between gap-2 mb-3">
                        <span class="font-medium">Periféricos</span>
                        <div class="flex flex-wrap gap-2">
                            @for (p of perifericosSuportados(); track p.tipo) {
                                <p-button [label]="'Adicionar ' + p.rotulo" icon="pi pi-plus" size="small" severity="secondary" [outlined]="true" (onClick)="adicionarPeriferico(p.tipo)" />
                            }
                        </div>
                    </div>
                    @if (cfgPerifericos.length === 0) {
                        <div class="text-sm text-surface-500 border border-dashed border-surface-300 dark:border-surface-600 rounded-lg p-4 text-center">
                            {{ cfgModelo() ? 'Nenhum periférico. Adicione os relés e medidores ligados ao equipamento.' : 'Escolha o modelo primeiro.' }}
                        </div>
                    }
                    <div class="flex flex-col gap-3">
                        @for (p of cfgPerifericos; track $index; let i = $index) {
                            <div class="border border-surface-200 dark:border-surface-700 rounded-xl p-3">
                                <div class="flex items-center justify-between gap-2 mb-3">
                                    <p-tag [value]="rotuloPeriferico(p.tipo)" [severity]="p.tipo === 'RELE' ? 'info' : 'warn'" [icon]="p.tipo === 'RELE' ? 'pi pi-power-off' : 'pi pi-bolt'" />
                                    <p-button icon="pi pi-trash" severity="danger" [text]="true" [rounded]="true" size="small" pTooltip="Remover" ariaLabel="Remover periférico" (onClick)="removerPeriferico(i)" />
                                </div>
                                <div class="grid grid-cols-1 sm:grid-cols-6 gap-3">
                                    <div class="min-w-0 sm:col-span-1">
                                        <label [for]="'per-canal-' + i" class="block text-sm font-medium mb-1">Canal *</label>
                                        <p-inputnumber [inputId]="'per-canal-' + i" [(ngModel)]="p.canal" [min]="0" [max]="15" styleClass="w-full" inputStyleClass="w-full" />
                                    </div>
                                    <div class="min-w-0 sm:col-span-3">
                                        <label [for]="'per-nome-' + i" class="block text-sm font-medium mb-1">Nome *</label>
                                        <input pInputText [id]="'per-nome-' + i" class="w-full" maxlength="80" [placeholder]="p.tipo === 'RELE' ? 'Ex.: Bomba da caixa' : 'Ex.: Quadro geral'" [(ngModel)]="p.nome" />
                                    </div>
                                    @if (p.tipo === 'RELE') {
                                        @if (cfgModelo() === 'RADIOENGE_ORIGINAL') {
                                            <div class="min-w-0 sm:col-span-1">
                                                <label [for]="'per-gpio-' + i" class="block text-sm font-medium mb-1">GPIO *</label>
                                                <p-inputnumber [inputId]="'per-gpio-' + i" [(ngModel)]="p.gpio" [min]="0" [max]="9" styleClass="w-full" inputStyleClass="w-full" />
                                            </div>
                                        }
                                        <label class="flex items-center gap-2 cursor-pointer sm:col-span-1 sm:mt-6">
                                            <p-toggleswitch [(ngModel)]="p.critico" />
                                            <span class="text-sm">Crítico</span>
                                        </label>
                                    } @else {
                                        <div class="min-w-0 sm:col-span-2">
                                            <label [for]="'per-mb-' + i" class="block text-sm font-medium mb-1">Endereço Modbus</label>
                                            <p-inputnumber [inputId]="'per-mb-' + i" [(ngModel)]="p.enderecoModbus" [min]="1" [max]="247" placeholder="1" styleClass="w-full" inputStyleClass="w-full" />
                                        </div>
                                    }
                                </div>
                            </div>
                        }
                    </div>
                    <small class="text-surface-500 block mt-2">Relé crítico (bomba, portão, aquecedor) pede confirmação dupla antes de qualquer acionamento.</small>
                </div>
            </div>
            <ng-template #footer>
                <div class="flex flex-wrap justify-between gap-2 w-full">
                    <div>
                        @if (eq()?.configurado && auth.podeEscrever()) {
                            <p-button label="Remover equipamento" icon="pi pi-trash" severity="danger" [text]="true" (onClick)="removerEquipamento()" />
                        }
                    </div>
                    <div class="flex flex-wrap gap-2">
                        <p-button label="Cancelar" severity="secondary" [outlined]="true" (onClick)="dlgConfig.set(false)" />
                        <p-button label="Salvar" icon="pi pi-check" [loading]="salvando()" (onClick)="salvarConfig()" />
                    </div>
                </div>
            </ng-template>
        </p-dialog>

        <!-- Enviar comando -->
        <p-dialog [visible]="dlgComando()" (visibleChange)="dlgComando.set($event)" header="Enviar comando" [modal]="true" [draggable]="false" [style]="{ width: '44rem' }" [breakpoints]="{ '768px': '95vw' }">
            <div class="flex flex-col gap-5">
                <app-comando-form id="ctl-cmd" [comandos]="eq()?.comandos ?? []" [perifericos]="eq()?.perifericos ?? []" [(tipo)]="cmdTipo" [(parametros)]="cmdParametros" />
                <app-politica-form id="ctl-pol" [(value)]="cmdPolitica" />
                <small class="text-surface-500">Em Classe A o comando só desce depois do próximo uplink do equipamento.</small>
            </div>
            <ng-template #footer>
                <div class="flex flex-wrap justify-end gap-2">
                    <p-button label="Cancelar" severity="secondary" [outlined]="true" (onClick)="dlgComando.set(false)" />
                    <p-button label="Enviar" icon="pi pi-send" [loading]="enviando() === 'DIALOGO'" (onClick)="enviarDialogo()" />
                </div>
            </ng-template>
        </p-dialog>

        <!-- Pulso -->
        <p-dialog [visible]="!!pulsoRele()" (visibleChange)="!$event && pulsoRele.set(null)" [header]="'Pulso: ' + (pulsoRele()?.nome ?? '')" [modal]="true" [draggable]="false" [style]="{ width: '26rem' }" [breakpoints]="{ '640px': '95vw' }">
            <div class="flex flex-col gap-4">
                <div class="min-w-0">
                    <label for="pulso-dur" class="block font-medium mb-2">Duração</label>
                    <p-inputnumber inputId="pulso-dur" [(ngModel)]="pulsoSegundos" [min]="0.1" [max]="86400" [minFractionDigits]="0" [maxFractionDigits]="1" locale="pt-BR" suffix=" s" styleClass="w-full" inputStyleClass="w-full" />
                </div>
                <label class="flex items-center gap-3 cursor-pointer">
                    <p-toggleswitch [(ngModel)]="pulsoLigado" />
                    <span
                        >Durante o pulso o relé fica <b>{{ pulsoLigado ? 'ligado' : 'desligado' }}</b></span
                    >
                </label>
                <small class="text-surface-500">Depois do tempo, o relé volta ao estado anterior.</small>
            </div>
            <ng-template #footer>
                <div class="flex flex-wrap justify-end gap-2">
                    <p-button label="Cancelar" severity="secondary" [outlined]="true" (onClick)="pulsoRele.set(null)" />
                    <p-button label="Enviar pulso" icon="pi pi-stopwatch" (onClick)="enviarPulso()" />
                </div>
            </ng-template>
        </p-dialog>

        <app-confirmacao-critica />
    `
})
export class ControleDispositivo implements OnInit {
    auth = inject(AuthService);
    private api = inject(AtuacaoService);
    private notify = inject(NotifyService);
    private destroyRef = inject(DestroyRef);
    private critico = viewChild.required(ConfirmacaoCritica);

    dispositivoId = input.required<number>();
    /** Emite depois de enviar um comando (o pai pode atualizar a lista de comandos). */
    comandoEnviado = output<void>();
    /** Emite quando o equipamento é salvo ou removido. */
    equipamentoAlterado = output<EquipamentoResponse | null>();

    eq = signal<EquipamentoResponse | null>(null);
    carregando = signal(false);
    modelos = signal<ModeloResponse[]>([]);
    enviando = signal<string | null>(null);

    reles = computed(() => (this.eq()?.perifericos ?? []).filter((p) => p.tipo === 'RELE').sort((a, b) => a.canal - b.canal));
    medidores = computed(() => (this.eq()?.perifericos ?? []).filter((p) => p.tipo === 'PZEM004T'));
    nomesMedidores = computed(() =>
        this.medidores()
            .map((m) => m.nome)
            .join(', ')
    );

    // configuração
    dlgConfig = signal(false);
    salvando = signal(false);
    cfgModelo = signal<ModeloFirmware | null>(null);
    cfgReconciliar = true;
    cfgTarifa: number | null = null;
    cfgPerifericos: PerifericoForm[] = [];
    modeloSel = computed(() => this.modelos().find((m) => m.modelo === this.cfgModelo()) ?? null);
    perifericosSuportados = computed(() => this.modeloSel()?.perifericos ?? []);

    // comando
    dlgComando = signal(false);
    cmdTipo: TipoComando | null = null;
    cmdParametros: ParametrosComando | null = {};
    cmdPolitica: PoliticaRequest | null = {};

    // pulso
    pulsoRele = signal<PerifericoResponse | null>(null);
    pulsoSegundos: number | null = 5;
    pulsoLigado = true;

    dh = dataHora;
    rel = tempoRelativo;
    json = jsonBonito;

    ngOnInit(): void {
        this.carregar();
        autoAtualizar(this.destroyRef, () => this.carregar(true), 15_000);
    }

    carregar(silencioso = false): void {
        if (!silencioso) {
            this.carregando.set(true);
        }
        this.api.equipamento(this.dispositivoId()).subscribe({
            next: (e) => {
                this.eq.set(e);
                this.carregando.set(false);
            },
            error: (err) => {
                this.carregando.set(false);

                if (!silencioso) {
                    this.notify.erro(err);
                }
            }
        });
    }

    suporta(t: TipoComando): boolean {
        return (this.eq()?.comandos ?? []).some((c) => c.tipo === t);
    }

    rotuloEstado(v?: boolean | null): string {
        return v === true ? 'Ligado' : v === false ? 'Desligado' : '—';
    }

    corEstado(v?: boolean | null): string {
        return v === true ? 'text-green-600 dark:text-green-400' : v === false ? 'text-surface-600 dark:text-surface-300' : 'text-surface-400';
    }

    divergente(r: PerifericoResponse): boolean {
        return r.estadoDesejado !== null && r.estadoDesejado !== undefined && r.estadoReportado !== null && r.estadoReportado !== undefined && r.estadoDesejado !== r.estadoReportado;
    }

    ocupado(r: PerifericoResponse, acao: AcaoRele): boolean {
        return this.enviando() === `RELE-${r.canal}-${acao}`;
    }

    moeda(v: number): string {
        return Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 5 });
    }

    // ── acionamento ──

    private ctx() {
        return { api: this.api, notify: this.notify, critico: this.critico() };
    }

    private concluir(): (c: unknown) => void {
        return (c) => {
            this.enviando.set(null);

            if (c) {
                this.comandoEnviado.emit();
                this.carregar(true);
            }
        };
    }

    definir(r: PerifericoResponse, acao: AcaoRele): void {
        this.enviando.set(`RELE-${r.canal}-${acao}`);
        enviarComando(this.ctx(), this.dispositivoId(), { tipo: 'RELE_DEFINIR', parametros: { canal: r.canal, acao } }, this.concluir());
    }

    rapido(tipo: TipoComando): void {
        this.enviando.set(tipo);
        enviarComando(this.ctx(), this.dispositivoId(), { tipo, parametros: null }, this.concluir());
    }

    abrirPulso(r: PerifericoResponse): void {
        this.pulsoSegundos = 5;
        this.pulsoLigado = true;
        this.pulsoRele.set(r);
    }

    enviarPulso(): void {
        const r = this.pulsoRele();

        if (!r) {
            return;
        }

        if (!this.pulsoSegundos || this.pulsoSegundos <= 0) {
            this.notify.aviso('Informe a duração do pulso.');

            return;
        }
        const parametros: ParametrosComando = { canal: r.canal, estado: this.pulsoLigado, duracaoMs: Math.round(this.pulsoSegundos * 1000) };

        this.pulsoRele.set(null);
        this.enviando.set(`PULSO-${r.canal}`);
        enviarComando(this.ctx(), this.dispositivoId(), { tipo: 'RELE_PULSO', parametros }, this.concluir());
    }

    abrirComando(): void {
        this.cmdTipo = null;
        this.cmdParametros = {};
        this.cmdPolitica = {};
        this.dlgComando.set(true);
    }

    enviarDialogo(): void {
        const t = this.cmdTipo;
        const erro = validarParametros(t, this.cmdParametros ?? {}) ?? validarPolitica(this.cmdPolitica);

        if (erro || !t) {
            this.notify.aviso(erro ?? 'Escolha o comando.');

            return;
        }
        this.enviando.set('DIALOGO');
        enviarComando(this.ctx(), this.dispositivoId(), { tipo: t, parametros: limparParametros(t, this.cmdParametros ?? {}), politica: politicaLimpa(this.cmdPolitica) }, (c) => {
            this.concluir()(c);

            if (c) {
                this.dlgComando.set(false);
            }
        });
    }

    // ── configuração ──

    private garantirModelos(depois?: () => void): void {
        if (this.modelos().length) {
            depois?.();

            return;
        }
        this.api.modelos().subscribe({
            next: (m) => {
                this.modelos.set(m);
                depois?.();
            },
            error: (e) => this.notify.erro(e)
        });
    }

    abrirConfig(): void {
        const e = this.eq();

        this.garantirModelos(() => {
            this.cfgModelo.set(e?.configurado ? (e.modelo ?? null) : (this.modelos()[0]?.modelo ?? null));
            this.cfgReconciliar = e?.configurado ? e.reconciliar : true;
            this.cfgTarifa = e?.tarifaKwh ?? null;
            this.cfgPerifericos = (e?.perifericos ?? []).map((p) => ({
                tipo: p.tipo,
                canal: p.canal,
                nome: p.nome,
                gpio: p.gpio ?? null,
                enderecoModbus: p.enderecoModbus ?? null,
                critico: p.critico
            }));
            this.dlgConfig.set(true);
        });
    }

    mudarModelo(m: ModeloFirmware | null): void {
        this.cfgModelo.set(m);
        const aceitos = new Set(this.perifericosSuportados().map((p) => p.tipo));

        this.cfgPerifericos = this.cfgPerifericos.filter((p) => aceitos.has(p.tipo));
    }

    rotuloPeriferico(t: TipoPeriferico): string {
        return (
            this.modelos()
                .flatMap((m) => m.perifericos)
                .find((p) => p.tipo === t)?.rotulo ?? (t === 'RELE' ? 'Relé' : 'Medidor PZEM-004T')
        );
    }

    adicionarPeriferico(tipo: TipoPeriferico): void {
        const mesmos = this.cfgPerifericos.filter((p) => p.tipo === tipo);
        const usados = new Set(mesmos.map((p) => p.canal));
        let canal = 0;

        while (usados.has(canal) && canal < 15) {
            canal++;
        }
        this.cfgPerifericos = [
            ...this.cfgPerifericos,
            {
                tipo,
                canal,
                nome: tipo === 'RELE' ? `Relé ${canal + 1}` : `Medidor ${canal + 1}`,
                gpio: null,
                enderecoModbus: tipo === 'PZEM004T' ? canal + 1 : null,
                critico: false
            }
        ];
    }

    removerPeriferico(i: number): void {
        this.cfgPerifericos = this.cfgPerifericos.filter((_, j) => j !== i);
    }

    salvarConfig(): void {
        const modelo = this.cfgModelo();

        if (!modelo) {
            this.notify.aviso('Escolha o modelo do firmware.');

            return;
        }
        const vistos = new Set<string>();

        for (const p of this.cfgPerifericos) {
            if (p.canal === null || p.canal === undefined) {
                this.notify.aviso('Informe o canal de todos os periféricos.');

                return;
            }

            if (!p.nome.trim()) {
                this.notify.aviso('Informe o nome de todos os periféricos.');

                return;
            }

            if (p.tipo === 'RELE' && modelo === 'RADIOENGE_ORIGINAL' && (p.gpio === null || p.gpio === undefined)) {
                this.notify.aviso(`Informe o GPIO do relé "${p.nome}".`);

                return;
            }
            const chave = `${p.tipo}-${p.canal}`;

            if (vistos.has(chave)) {
                this.notify.aviso(`Canal ${p.canal} repetido em ${this.rotuloPeriferico(p.tipo)}.`);

                return;
            }
            vistos.add(chave);
        }
        const req: EquipamentoRequest = {
            modelo,
            reconciliar: this.cfgReconciliar,
            tarifaKwh: this.cfgTarifa,
            perifericos: this.cfgPerifericos.map(
                (p): PerifericoRequest => ({
                    tipo: p.tipo,
                    canal: p.canal!,
                    nome: p.nome.trim(),
                    gpio: p.tipo === 'RELE' ? p.gpio : null,
                    enderecoModbus: p.tipo === 'PZEM004T' ? p.enderecoModbus : null,
                    critico: p.tipo === 'RELE' ? p.critico : false
                })
            )
        };

        this.salvando.set(true);
        this.api.salvarEquipamento(this.dispositivoId(), req).subscribe({
            next: (e) => {
                this.eq.set(e);
                this.salvando.set(false);
                this.dlgConfig.set(false);
                this.notify.sucesso('Equipamento salvo.');
                this.equipamentoAlterado.emit(e);
            },
            error: (err) => {
                this.salvando.set(false);
                this.notify.erro(err);
            }
        });
    }

    removerEquipamento(): void {
        this.notify.confirmarExclusao(
            'o equipamento deste dispositivo',
            () =>
                this.api.removerEquipamento(this.dispositivoId()).subscribe({
                    next: () => {
                        this.dlgConfig.set(false);
                        this.notify.sucesso('Equipamento removido.');
                        this.equipamentoAlterado.emit(null);
                        this.carregar();
                    },
                    error: (e) => this.notify.erro(e)
                }),
            'Os periféricos deixam de existir; comandos pendentes, cenas, agendamentos e automações que usam este dispositivo podem falhar.'
        );
    }
}
