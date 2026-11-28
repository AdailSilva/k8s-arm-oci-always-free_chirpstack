import { Component, computed, inject, OnInit, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TooltipModule } from 'primeng/tooltip';
import { AuthService } from '@/app/core/auth/auth.service';
import { AtuacaoService } from '@/app/core/http/atuacao.service';
import { AlvoAcao, AutomacaoRequest, AutomacaoResponse, CenaResponse, GatilhoAutomacao, ParametrosComando, TipoComando } from '@/app/core/http/atuacao.models';
import { MonitoramentoService } from '@/app/core/http/monitoramento.service';
import { Dispositivo, RegraAlerta } from '@/app/core/http/models';
import { NotifyService } from '@/app/core/notificacao/notify.service';
import { tempoRelativo, dataHora } from '@/app/core/format/format';
import { duracao, ehCargaCritica, limparParametros, ROTULO_GATILHO, validarParametros } from '@/app/shared/atuacao/atuacao-util';
import { ComandoForm } from '@/app/shared/atuacao/comando-form';
import { ConfirmacaoCritica } from '@/app/shared/atuacao/confirmacao-critica';
import { EquipamentosCache } from '@/app/shared/atuacao/equipamentos-cache';
import { TravaEdicaoJanela } from '@/app/core/edicao/trava-edicao';
import { PageHeader } from '@/app/shared/visual/page-header';

interface FormAutomacao {
    nome: string;
    regraId: number | null;
    gatilho: GatilhoAutomacao;
    alvo: AlvoAcao;
    mesmoDispositivo: boolean;
    dispositivoId: number | null;
    tipo: TipoComando | null;
    parametros: ParametrosComando | null;
    cenaId: number | null;
    intervaloMinimoS: number | null;
    ativo: boolean;
}

const ROTULO_OPERADOR: Record<string, string> = { GT: '>', GTE: '≥', LT: '<', LTE: '≤', EQ: '=', NE: '≠' };

/**
 * Automações: quando o alerta de uma regra abre (ou é resolvido), executa um comando ou uma cena.
 * Ex.: pzem.0.potencia > 2000 → desligar o relé 0. Há um intervalo mínimo entre disparos.
 */
@Component({
    selector: 'app-automacoes',
    standalone: true,
    imports: [TravaEdicaoJanela, 
        CommonModule,
        FormsModule,
        RouterModule,
        ButtonModule,
        DialogModule,
        InputNumberModule,
        InputTextModule,
        MessageModule,
        SelectModule,
        SelectButtonModule,
        TableModule,
        TagModule,
        ToggleSwitchModule,
        TooltipModule,
        ComandoForm,
        ConfirmacaoCritica,
        PageHeader
    ],
    providers: [EquipamentosCache],
    template: `
        <app-page-header title="Automações" subtitle="Alerta de uma regra → comando ou cena" icon="pi pi-sitemap">
            <p-button icon="pi pi-refresh" label="Atualizar" severity="secondary" [outlined]="true" [loading]="carregando()" (onClick)="carregar()" />
            @if (auth.podeEscrever()) {
                <p-button icon="pi pi-plus" label="Nova automação" (onClick)="nova()" />
            }
        </app-page-header>

        <div class="card">
            <p-table [value]="itens()" [loading]="carregando()" [rowHover]="true" styleClass="p-datatable-sm">
                <ng-template #header>
                    <tr>
                        <th>Automação</th>
                        <th class="hidden md:table-cell">Quando</th>
                        <th class="hidden lg:table-cell">Última execução</th>
                        <th class="w-20">Ativa</th>
                        @if (auth.podeEscrever()) {
                            <th class="w-24"></th>
                        }
                    </tr>
                </ng-template>
                <ng-template #body let-a>
                    <tr>
                        <td class="min-w-0">
                            <div class="font-medium break-words" [class.text-surface-400]="!a.ativo">{{ a.nome }}</div>
                            <div class="text-xs text-surface-500 break-words">
                                @if (a.alvo === 'CENA') {
                                    <i class="pi pi-images mr-1"></i>Cena {{ a.cenaNome || '#' + a.cenaId }}
                                } @else {
                                    <i class="pi pi-send mr-1"></i>{{ a.resumo || a.tipoRotulo }} ·
                                    @if (a.mesmoDispositivo) {
                                        no dispositivo do alerta
                                    } @else {
                                        <a [routerLink]="['/monitoramento/dispositivos', a.dispositivoId]" class="text-primary">{{ a.dispositivoNome || '#' + a.dispositivoId }}</a>
                                    }
                                }
                            </div>
                            <div class="text-xs text-surface-500 md:hidden break-words">{{ quando(a) }}</div>
                        </td>
                        <td class="hidden md:table-cell text-sm break-words">
                            {{ quando(a) }}
                            <div class="text-xs text-surface-500">Intervalo mínimo {{ dur(a.intervaloMinimoS) }}</div>
                        </td>
                        <td class="hidden lg:table-cell text-sm">
                            @if (a.ultimaExecucaoEm) {
                                <span [pTooltip]="dh(a.ultimaExecucaoEm)">{{ rel(a.ultimaExecucaoEm) }}</span>
                            } @else {
                                —
                            }
                        </td>
                        <td>
                            <p-toggleswitch [ngModel]="a.ativo" (ngModelChange)="alternarAtivo(a, $event)" [disabled]="!auth.podeEscrever()" [ariaLabel]="'Ativa: ' + a.nome" />
                        </td>
                        @if (auth.podeEscrever()) {
                            <td class="text-right whitespace-nowrap">
                                <p-button icon="pi pi-pencil" [text]="true" [rounded]="true" pTooltip="Editar" ariaLabel="Editar" (onClick)="editar(a)" />
                                <p-button icon="pi pi-trash" severity="danger" [text]="true" [rounded]="true" pTooltip="Excluir" ariaLabel="Excluir" (onClick)="excluir(a)" />
                            </td>
                        }
                    </tr>
                </ng-template>
                <ng-template #emptymessage>
                    <tr>
                        <td colspan="5" class="text-center py-8 text-surface-500">Nenhuma automação. Crie uma regra em Monitoramento › Alertas e depois ligue-a a um comando aqui.</td>
                    </tr>
                </ng-template>
            </p-table>
        </div>

        <p-dialog [csmTravaEdicao]="form" #trava="travaEdicao" [visible]="dialogo()" (visibleChange)="dialogo.set($event)" [header]="editandoId ? 'Editar automação' : 'Nova automação'" [modal]="true" [draggable]="false" [style]="{ width: '46rem' }" [breakpoints]="{ '768px': '95vw' }">
            <div class="flex flex-col gap-5">
                <div class="min-w-0">
                    <label for="au-nome" class="block font-medium mb-2">Nome <span class="text-red-500">*</span></label>
                    <input pInputText id="au-nome" class="w-full" maxlength="120" placeholder="Ex.: Desligar o chuveiro acima de 2 kW" [(ngModel)]="form.nome" />
                </div>

                <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div class="min-w-0">
                        <label for="au-regra" class="block font-medium mb-2">Regra de alerta <span class="text-red-500">*</span></label>
                        <p-select
                            inputId="au-regra"
                            [options]="regrasOpcoes()"
                            optionLabel="label"
                            optionValue="value"
                            [ngModel]="form.regraId"
                            (ngModelChange)="mudarRegra($event)"
                            placeholder="Escolha a regra"
                            [filter]="regras().length > 8"
                            filterBy="label"
                            emptyMessage="Nenhuma regra cadastrada."
                            styleClass="w-full"
                            appendTo="body"
                        />
                    </div>
                    <div class="min-w-0">
                        <label for="au-gat" class="block font-medium mb-2">Gatilho</label>
                        <p-select inputId="au-gat" [options]="gatilhos" optionLabel="label" optionValue="value" [(ngModel)]="form.gatilho" styleClass="w-full" appendTo="body" />
                    </div>
                </div>
                @if (regraSel(); as r) {
                    <small class="text-surface-500 -mt-3">{{ descreverRegra(r) }}</small>
                }

                <div class="min-w-0">
                    <span class="block font-medium mb-2">Executar</span>
                    <p-selectbutton [options]="alvos" optionLabel="label" optionValue="value" [(ngModel)]="form.alvo" [allowEmpty]="false" />
                </div>

                @if (form.alvo === 'COMANDO') {
                    <label class="flex items-start gap-3 cursor-pointer">
                        <p-toggleswitch class="shrink-0" [ngModel]="form.mesmoDispositivo" (ngModelChange)="mudarMesmo($event)" />
                        <span><span class="font-medium">No dispositivo que disparou o alerta</span><br /><span class="text-sm text-surface-500">Útil quando a regra vale para todos os dispositivos.</span></span>
                    </label>
                    @if (!form.mesmoDispositivo) {
                        <div class="min-w-0">
                            <label for="au-disp" class="block font-medium mb-2">Dispositivo <span class="text-red-500">*</span></label>
                            <p-select
                                inputId="au-disp"
                                [options]="dispositivos()"
                                optionLabel="nome"
                                optionValue="id"
                                [ngModel]="form.dispositivoId"
                                (ngModelChange)="mudarDispositivo($event)"
                                placeholder="Escolha o dispositivo"
                                [filter]="true"
                                filterBy="nome,devEui"
                                styleClass="w-full"
                                appendTo="body"
                            />
                        </div>
                    } @else if (!regraSel()?.dispositivoId) {
                        <p-message severity="info" size="small" text="A regra vale para todos os dispositivos: o comando só é enviado se o dispositivo do alerta suportar." />
                    }
                    <app-comando-form
                        id="au-cmd"
                        [comandos]="comandosForm()"
                        [perifericos]="cache.perifericos(dispositivoDoForm())"
                        [semComandos]="form.mesmoDispositivo ? 'Carregando comandos...' : cache.semComandos(form.dispositivoId)"
                        [(tipo)]="form.tipo"
                        [(parametros)]="form.parametros"
                    />
                } @else {
                    <div class="min-w-0">
                        <label for="au-cena" class="block font-medium mb-2">Cena <span class="text-red-500">*</span></label>
                        <p-select
                            inputId="au-cena"
                            [options]="cenas()"
                            optionLabel="nome"
                            optionValue="id"
                            [(ngModel)]="form.cenaId"
                            placeholder="Escolha a cena"
                            [filter]="cenas().length > 8"
                            filterBy="nome"
                            emptyMessage="Nenhuma cena cadastrada."
                            styleClass="w-full"
                            appendTo="body"
                        />
                    </div>
                }

                <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div class="min-w-0">
                        <label for="au-int" class="block font-medium mb-2">Intervalo mínimo entre disparos</label>
                        <p-inputnumber inputId="au-int" [(ngModel)]="form.intervaloMinimoS" [min]="0" [max]="604800" suffix=" s" locale="pt-BR" placeholder="300 s" styleClass="w-full" inputStyleClass="w-full" />
                        <small class="text-surface-500">Evita repetir a ação se o alerta abrir e fechar várias vezes.</small>
                    </div>
                    <label class="flex items-center gap-3 cursor-pointer sm:mt-8">
                        <p-toggleswitch [(ngModel)]="form.ativo" />
                        <span class="font-medium">Ativa</span>
                    </label>
                </div>
            </div>
            <ng-template #footer>
                <div class="flex flex-wrap justify-end gap-2">
                    <p-button label="Cancelar" severity="secondary" [outlined]="true" (onClick)="trava.fechar()" />
                    <p-button label="Salvar" icon="pi pi-check" [loading]="salvando()" (onClick)="salvar()" />
                </div>
            </ng-template>
        </p-dialog>
        <app-confirmacao-critica />
    `
})
export class Automacoes implements OnInit {
    auth = inject(AuthService);
    cache = inject(EquipamentosCache);
    private api = inject(AtuacaoService);
    private mon = inject(MonitoramentoService);
    private notify = inject(NotifyService);
    private critico = viewChild.required(ConfirmacaoCritica);

    itens = signal<AutomacaoResponse[]>([]);
    carregando = signal(false);
    regras = signal<RegraAlerta[]>([]);
    dispositivos = signal<Dispositivo[]>([]);
    cenas = signal<CenaResponse[]>([]);
    dialogo = signal(false);
    salvando = signal(false);
    editandoId: number | null = null;
    form: FormAutomacao = this.vazio();
    /** Sinal só para os computeds que dependem da regra escolhida. */
    private regraIdSel = signal<number | null>(null);

    regrasOpcoes = computed(() => this.regras().map((r) => ({ label: `${r.nome}${r.ativo ? '' : ' (inativa)'}`, value: r.id })));
    regraSel = computed(() => this.regras().find((r) => r.id === this.regraIdSel()) ?? null);

    gatilhos = (Object.keys(ROTULO_GATILHO) as GatilhoAutomacao[]).map((g) => ({ label: ROTULO_GATILHO[g], value: g }));
    alvos = [
        { label: 'Comando', value: 'COMANDO' },
        { label: 'Cena', value: 'CENA' }
    ];

    rel = tempoRelativo;
    dh = dataHora;
    dur = duracao;

    ngOnInit(): void {
        this.carregar();
        this.mon.regras().subscribe({ next: (r) => this.regras.set(r) });
    }

    carregar(): void {
        this.carregando.set(true);
        this.api.automacoes().subscribe({
            next: (l) => {
                this.itens.set(l);
                this.carregando.set(false);
            },
            error: (e) => {
                this.carregando.set(false);
                this.notify.erro(e);
            }
        });
    }

    quando(a: AutomacaoResponse): string {
        return `${ROTULO_GATILHO[a.gatilho]}: ${a.regraNome || 'regra #' + a.regraId}`;
    }

    descreverRegra(r: RegraAlerta): string {
        const alvo = r.dispositivoNome ? `em ${r.dispositivoNome}` : 'em qualquer dispositivo';

        if (r.tipo === 'SEM_COMUNICACAO') {
            return `Abre quando o dispositivo fica ${r.limite} min sem mensagem, ${alvo}.`;
        }

        return `Abre quando ${r.campo ?? 'o valor'} ${ROTULO_OPERADOR[r.operador ?? ''] ?? r.operador ?? ''} ${r.limite}, ${alvo}.`;
    }

    /** Dispositivo cujos periféricos o formulário oferece. */
    dispositivoDoForm(): number | null {
        return this.form.mesmoDispositivo ? (this.regraSel()?.dispositivoId ?? null) : this.form.dispositivoId;
    }

    comandosForm() {
        if (!this.form.mesmoDispositivo) {
            return this.cache.comandos(this.form.dispositivoId);
        }
        const d = this.regraSel()?.dispositivoId;

        return d && this.cache.equipamento(d) ? this.cache.comandos(d) : this.cache.todosComandos();
    }

    private prepararListas(): void {
        if (this.dispositivos().length === 0) {
            this.mon.dispositivos('', true, 0, 500).subscribe({ next: (r) => this.dispositivos.set(r.result) });
        }
        this.mon.regras().subscribe({ next: (r) => this.regras.set(r) });
        this.api.cenas().subscribe({ next: (c) => this.cenas.set(c) });
        this.cache.garantirModelos();
    }

    nova(): void {
        this.editandoId = null;
        this.form = this.vazio();
        this.regraIdSel.set(null);
        this.prepararListas();
        this.dialogo.set(true);
    }

    editar(a: AutomacaoResponse): void {
        this.editandoId = a.id;
        this.form = {
            nome: a.nome,
            regraId: a.regraId,
            gatilho: a.gatilho,
            alvo: a.alvo,
            mesmoDispositivo: a.mesmoDispositivo,
            dispositivoId: a.dispositivoId ?? null,
            tipo: a.tipo ?? null,
            parametros: a.parametros ?? {},
            cenaId: a.cenaId ?? null,
            intervaloMinimoS: a.intervaloMinimoS,
            ativo: a.ativo
        };
        this.regraIdSel.set(a.regraId);
        this.cache.garantir(a.dispositivoId);
        this.prepararListas();
        this.dialogo.set(true);
    }

    mudarRegra(id: number | null): void {
        this.form.regraId = id;
        this.regraIdSel.set(id);
        const r = this.regras().find((x) => x.id === id);

        if (r?.dispositivoId) {
            this.cache.garantir(r.dispositivoId);

            if (!this.form.mesmoDispositivo && !this.form.dispositivoId) {
                this.mudarDispositivo(r.dispositivoId);
            }
        }
    }

    mudarMesmo(v: boolean): void {
        this.form.mesmoDispositivo = v;
        this.form.tipo = null;
        this.form.parametros = {};
    }

    mudarDispositivo(id: number | null): void {
        this.form.dispositivoId = id;
        this.form.tipo = null;
        this.form.parametros = {};
        this.cache.garantir(id);
    }

    salvar(confirmarCritico = false): void {
        const f = this.form;

        if (!f.nome.trim()) {
            this.notify.aviso('Informe o nome.');

            return;
        }

        if (!f.regraId) {
            this.notify.aviso('Escolha a regra de alerta.');

            return;
        }

        if (f.alvo === 'COMANDO') {
            if (!f.mesmoDispositivo && !f.dispositivoId) {
                this.notify.aviso('Escolha o dispositivo.');

                return;
            }
            const erro = validarParametros(f.tipo, f.parametros ?? {});

            if (erro) {
                this.notify.aviso(erro);

                return;
            }
        } else if (!f.cenaId) {
            this.notify.aviso('Escolha a cena.');

            return;
        }
        const comando = f.alvo === 'COMANDO';
        const req: AutomacaoRequest = {
            nome: f.nome.trim(),
            regraId: f.regraId,
            gatilho: f.gatilho,
            alvo: f.alvo,
            mesmoDispositivo: comando && f.mesmoDispositivo,
            dispositivoId: comando && !f.mesmoDispositivo ? f.dispositivoId : null,
            tipo: comando ? f.tipo : null,
            parametros: comando && f.tipo ? limparParametros(f.tipo, f.parametros ?? {}) : null,
            cenaId: comando ? null : f.cenaId,
            intervaloMinimoS: f.intervaloMinimoS,
            ativo: f.ativo,
            confirmarCritico
        };

        this.salvando.set(true);
        const obs = this.editandoId ? this.api.atualizarAutomacao(this.editandoId, req) : this.api.criarAutomacao(req);

        obs.subscribe({
            next: () => {
                this.salvando.set(false);
                this.dialogo.set(false);
                this.notify.sucesso('Automação salva.');
                this.carregar();
            },
            error: (e) => {
                this.salvando.set(false);

                if (ehCargaCritica(e)) {
                    this.critico().pedir(NotifyService.mensagem(e), () => this.salvar(true));
                } else {
                    this.notify.erro(e);
                }
            }
        });
    }

    alternarAtivo(a: AutomacaoResponse, ativo: boolean, confirmarCritico = false): void {
        const req: AutomacaoRequest = {
            nome: a.nome,
            regraId: a.regraId,
            gatilho: a.gatilho,
            alvo: a.alvo,
            mesmoDispositivo: a.mesmoDispositivo,
            dispositivoId: a.dispositivoId ?? null,
            tipo: a.tipo ?? null,
            parametros: a.parametros ?? null,
            cenaId: a.cenaId ?? null,
            intervaloMinimoS: a.intervaloMinimoS,
            ativo,
            confirmarCritico
        };

        this.api.atualizarAutomacao(a.id, req).subscribe({
            next: (novo) => {
                this.itens.update((l) => l.map((x) => (x.id === novo.id ? novo : x)));
                this.notify.sucesso(ativo ? 'Automação ativada.' : 'Automação desativada.');
            },
            error: (e) => {
                this.itens.update((l) => l.map((x) => (x.id === a.id ? { ...x } : x)));

                if (ehCargaCritica(e)) {
                    this.critico().pedir(NotifyService.mensagem(e), () => this.alternarAtivo(a, ativo, true));
                } else {
                    this.notify.erro(e);
                }
            }
        });
    }

    excluir(a: AutomacaoResponse): void {
        this.notify.confirmarExclusao(`a automação ${a.nome}`, () =>
            this.api.excluirAutomacao(a.id).subscribe({
                next: () => {
                    this.notify.sucesso('Automação excluída.');
                    this.carregar();
                },
                error: (e) => this.notify.erro(e)
            })
        );
    }

    private vazio(): FormAutomacao {
        return {
            nome: '',
            regraId: null,
            gatilho: 'ALERTA_ABERTO',
            alvo: 'COMANDO',
            mesmoDispositivo: false,
            dispositivoId: null,
            tipo: null,
            parametros: {},
            cenaId: null,
            intervaloMinimoS: null,
            ativo: true
        };
    }
}
