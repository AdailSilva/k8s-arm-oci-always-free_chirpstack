import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TabsModule } from 'primeng/tabs';
import { TagModule } from 'primeng/tag';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TooltipModule } from 'primeng/tooltip';
import { AuthService } from '@/app/core/auth/auth.service';
import { MonitoramentoService } from '@/app/core/http/monitoramento.service';
import { Alerta, Dispositivo, Operador, RegraAlerta, RegraAlertaForm, Severidade, StatusAlerta, TipoRegra } from '@/app/core/http/models';
import { NotifyService } from '@/app/core/notificacao/notify.service';
import { dataHora } from '@/app/core/format/format';
import { emailValido } from '@/app/core/format/validacao';
import { ConsultaPagina, ListaPaginada } from '@/app/shared/tabela/lista-paginada';
import { TravaEdicaoJanela } from '@/app/core/edicao/trava-edicao';
import { PageHeader } from '@/app/shared/visual/page-header';

const SIMBOLO: Record<Operador, string> = { GT: '>', GTE: '≥', LT: '<', LTE: '≤', EQ: '=', NE: '≠' };

/**
 * Alertas disparados e as regras que os disparam. Todos veem; ADMIN e OPERADOR reconhecem alertas e
 * cadastram regras.
 */
@Component({
    selector: 'app-alertas',
    standalone: true,
    imports: [TravaEdicaoJanela, CommonModule, FormsModule, RouterModule, ButtonModule, DialogModule, InputNumberModule, InputTextModule, SelectModule, TableModule, TabsModule, TagModule, ToggleSwitchModule, TooltipModule, PageHeader],
    template: `
        <app-page-header title="Alertas" subtitle="Disparos das regras sobre as leituras e a comunicação dos dispositivos" icon="pi pi-bell">
            <p-button icon="pi pi-refresh" label="Atualizar" severity="secondary" [outlined]="true" (onClick)="aba() === 'alertas' ? carregar() : carregarRegras()" [loading]="carregando() || carregandoRegras()" />
            @if (aba() === 'regras' && auth.podeEscrever()) {
                <p-button icon="pi pi-plus" label="Nova regra" (onClick)="novaRegra()" />
            }
        </app-page-header>

        <div class="card">
            <p-tabs [value]="aba()" (valueChange)="aba.set($any($event))">
                <p-tablist>
                    <p-tab value="alertas"><i class="pi pi-bell mr-2"></i>Alertas</p-tab>
                    <p-tab value="regras"><i class="pi pi-sliders-h mr-2"></i>Regras <span class="ml-2 text-surface-500">({{ regras().length }})</span></p-tab>
                </p-tablist>
                <p-tabpanels>
                    <p-tabpanel value="alertas">
                        <p-table [value]="itens()" [lazy]="true" (onLazyLoad)="onLazy($event)" [paginator]="true" [rows]="rows()" [first]="first()" [totalRecords]="total()" [loading]="carregando()" [rowsPerPageOptions]="[20, 50, 100]" [showCurrentPageReport]="true" currentPageReportTemplate="{first}–{last} de {totalRecords}" [rowHover]="true" styleClass="p-datatable-sm">
                            <ng-template #caption>
                                <div class="flex justify-end">
                                    <p-select [options]="situacoes" optionLabel="label" optionValue="value" [ngModel]="status()" (ngModelChange)="status.set($event); recarregarDoInicio()" placeholder="Todas as situações" [showClear]="true" styleClass="w-full sm:w-60" />
                                </div>
                            </ng-template>
                            <ng-template #header>
                                <tr>
                                    <th>Disparado</th>
                                    <th>Situação</th>
                                    <th class="hidden md:table-cell">Dispositivo</th>
                                    <th>Mensagem</th>
                                    <th class="w-24"></th>
                                </tr>
                            </ng-template>
                            <ng-template #body let-a>
                                <tr>
                                    <td class="text-sm">
                                        {{ dh(a.disparadoEm) }}
                                        @if (a.severidade) {
                                            <div><p-tag [value]="a.severidade" [severity]="corSeveridade(a.severidade)" styleClass="text-xs" /></div>
                                        }
                                    </td>
                                    <td>
                                        <p-tag [value]="rotuloDoStatus(a.status)" [severity]="corStatus(a.status)" />
                                        @if (a.reconhecidoPor) {
                                            <div class="text-xs text-surface-500 mt-1 break-all">por {{ a.reconhecidoPor }}</div>
                                        }
                                    </td>
                                    <td class="hidden md:table-cell text-sm"><a [routerLink]="['/monitoramento/dispositivos', a.dispositivoId]" class="text-primary">{{ a.dispositivoNome || a.devEui }}</a></td>
                                    <td class="text-sm break-words">
                                        {{ a.mensagem }}
                                        @if (a.emailEnviado) {
                                            <i class="pi pi-envelope text-surface-500 ml-1" pTooltip="Notificado por e-mail"></i>
                                        }
                                    </td>
                                    <td class="text-right">
                                        @if (a.status === 'ABERTO' && auth.podeEscrever()) {
                                            <p-button label="Reconhecer" size="small" [outlined]="true" (onClick)="reconhecer(a)" />
                                        }
                                    </td>
                                </tr>
                            </ng-template>
                            <ng-template #emptymessage>
                                <tr><td colspan="5" class="text-center py-8 text-surface-500">Nenhum alerta.</td></tr>
                            </ng-template>
                        </p-table>
                    </p-tabpanel>

                    <p-tabpanel value="regras">
                        <p-table [value]="regras()" [loading]="carregandoRegras()" [rowHover]="true" styleClass="p-datatable-sm">
                            <ng-template #header>
                                <tr>
                                    <th>Regra</th>
                                    <th>Condição</th>
                                    <th class="hidden md:table-cell">Dispositivos</th>
                                    <th class="hidden lg:table-cell">E-mail</th>
                                    <th class="w-28"></th>
                                </tr>
                            </ng-template>
                            <ng-template #body let-r>
                                <tr>
                                    <td>
                                        <div class="font-medium" [class.text-surface-400]="!r.ativo">{{ r.nome }}</div>
                                        <p-tag [value]="r.severidade" [severity]="corSeveridade(r.severidade)" styleClass="text-xs" />
                                        @if (!r.ativo) {
                                            <p-tag value="Desativada" severity="secondary" styleClass="text-xs ml-1" />
                                        }
                                    </td>
                                    <td class="text-sm">{{ condicao(r) }}</td>
                                    <td class="hidden md:table-cell text-sm">{{ r.dispositivoNome || 'Todos' }}</td>
                                    <td class="hidden lg:table-cell text-sm break-all">{{ r.notificarEmail ? r.destinatarios : '—' }}</td>
                                    <td class="text-right whitespace-nowrap">
                                        @if (auth.podeEscrever()) {
                                            <p-button icon="pi pi-pencil" [text]="true" [rounded]="true" pTooltip="Editar" (onClick)="editarRegra(r)" />
                                            <p-button icon="pi pi-trash" severity="danger" [text]="true" [rounded]="true" pTooltip="Excluir" (onClick)="excluirRegra(r)" />
                                        }
                                    </td>
                                </tr>
                            </ng-template>
                            <ng-template #emptymessage>
                                <tr><td colspan="5" class="text-center py-8 text-surface-500">Nenhuma regra cadastrada.</td></tr>
                            </ng-template>
                        </p-table>
                    </p-tabpanel>
                </p-tabpanels>
            </p-tabs>
        </div>

        <p-dialog csmTravaEdicao #trava="travaEdicao" [(visible)]="dialogo" [header]="editandoId ? 'Editar regra' : 'Nova regra'" [modal]="true" [style]="{ width: '40rem' }" [breakpoints]="{ '768px': '95vw' }">
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-5">
                <div class="sm:col-span-2 min-w-0">
                    <label for="nome" class="block font-medium mb-2">Nome <span class="text-red-500">*</span></label>
                    <input pInputText id="nome" class="w-full" maxlength="120" placeholder="Ex.: Temperatura alta" [(ngModel)]="form.nome" />
                </div>
                <div class="min-w-0">
                    <label class="block font-medium mb-2">Tipo</label>
                    <p-select [options]="tipos" optionLabel="label" optionValue="value" [(ngModel)]="form.tipo" styleClass="w-full" appendTo="body" />
                </div>
                <div class="min-w-0">
                    <label class="block font-medium mb-2">Dispositivo</label>
                    <p-select [options]="dispositivos()" optionLabel="nome" optionValue="id" [(ngModel)]="form.dispositivoId" placeholder="Todos" [showClear]="true" [filter]="true" filterBy="nome,devEui" styleClass="w-full" appendTo="body" />
                </div>
                @if (form.tipo === 'VALOR') {
                    <div class="sm:col-span-2 min-w-0">
                        <label for="campo" class="block font-medium mb-2">Campo <span class="text-red-500">*</span></label>
                        <input pInputText id="campo" class="w-full" maxlength="120" placeholder="temperatura, rssi, bateria..." [(ngModel)]="form.campo" />
                        <small class="text-surface-500">Caminho no objeto decodificado (ex.: sensor.umidade) ou rssi, snr, bateria.</small>
                    </div>
                    <div class="min-w-0">
                        <label class="block font-medium mb-2">Comparação</label>
                        <p-select [options]="operadores" optionLabel="label" optionValue="value" [(ngModel)]="form.operador" styleClass="w-full" appendTo="body" />
                    </div>
                    <div class="min-w-0">
                        <label class="block font-medium mb-2">Limite <span class="text-red-500">*</span></label>
                        <p-inputnumber [(ngModel)]="form.limite" [minFractionDigits]="0" [maxFractionDigits]="4" locale="pt-BR" styleClass="w-full" inputStyleClass="w-full" />
                    </div>
                } @else {
                    <div class="sm:col-span-2 min-w-0">
                        <label class="block font-medium mb-2">Minutos sem mensagem <span class="text-red-500">*</span></label>
                        <p-inputnumber [(ngModel)]="form.limite" [min]="1" [max]="100000" suffix=" min" styleClass="w-full" inputStyleClass="w-full" />
                    </div>
                }
                <div class="min-w-0">
                    <label class="block font-medium mb-2">Severidade</label>
                    <p-select [options]="severidades" optionLabel="label" optionValue="value" [(ngModel)]="form.severidade" styleClass="w-full" appendTo="body" />
                </div>
                <label class="flex items-center gap-3 sm:mt-8">
                    <p-toggleswitch [(ngModel)]="form.ativo" />
                    <span class="font-medium">Ativa</span>
                </label>
                <label class="sm:col-span-2 flex items-center gap-3">
                    <p-toggleswitch [(ngModel)]="form.notificarEmail" />
                    <span class="font-medium">Avisar por e-mail</span>
                </label>
                @if (form.notificarEmail) {
                    <div class="sm:col-span-2 min-w-0">
                        <label for="dest" class="block font-medium mb-2">Destinatários <span class="text-red-500">*</span></label>
                        <input pInputText id="dest" class="w-full" maxlength="500" placeholder="a@empresa.com, b@empresa.com" [(ngModel)]="form.destinatarios" />
                    </div>
                }
            </div>
            <ng-template #footer>
                <p-button label="Cancelar" severity="secondary" [outlined]="true" (onClick)="trava.fechar()" />
                <p-button label="Salvar" icon="pi pi-check" [loading]="salvando()" (onClick)="salvarRegra()" />
            </ng-template>
        </p-dialog>
    `
})
export class Alertas extends ListaPaginada<Alerta> implements OnInit {
    auth = inject(AuthService);
    private api = inject(MonitoramentoService);
    private notify = inject(NotifyService);

    aba = signal('alertas');
    status = signal<StatusAlerta | null>('ABERTO');
    regras = signal<RegraAlerta[]>([]);
    carregandoRegras = signal(false);
    dispositivos = signal<Dispositivo[]>([]);
    salvando = signal(false);
    dialogo = false;
    editandoId: number | null = null;
    form: RegraAlertaForm = this.vazio();
    dh = dataHora;

    rotuloStatus: Record<StatusAlerta, string> = { ABERTO: 'Aberto', RECONHECIDO: 'Reconhecido', RESOLVIDO: 'Resolvido' };
    situacoes = [
        { label: 'Abertos', value: 'ABERTO' },
        { label: 'Reconhecidos', value: 'RECONHECIDO' },
        { label: 'Resolvidos', value: 'RESOLVIDO' }
    ];
    tipos: { label: string; value: TipoRegra }[] = [
        { label: 'Valor da leitura', value: 'VALOR' },
        { label: 'Sem comunicação', value: 'SEM_COMUNICACAO' }
    ];
    operadores = (Object.keys(SIMBOLO) as Operador[]).map((o) => ({ label: SIMBOLO[o] + ' ' + { GT: 'maior que', GTE: 'maior ou igual a', LT: 'menor que', LTE: 'menor ou igual a', EQ: 'igual a', NE: 'diferente de' }[o], value: o }));
    severidades: { label: string; value: Severidade }[] = [
        { label: 'Informação', value: 'INFO' },
        { label: 'Aviso', value: 'AVISO' },
        { label: 'Crítico', value: 'CRITICO' }
    ];

    constructor() {
        super();
        this.rows.set(50);
    }

    ngOnInit(): void {
        this.carregarRegras();
    }

    protected consultar(q: ConsultaPagina) {
        return this.api.alertas(this.status(), null, Math.floor(q.offset / q.limit), q.limit);
    }

    protected override aoFalhar(e: unknown): void {
        this.notify.erro(e);
    }

    carregarRegras(): void {
        this.carregandoRegras.set(true);
        this.api.regras().subscribe({
            next: (r) => {
                this.regras.set(r);
                this.carregandoRegras.set(false);
            },
            error: (e) => {
                this.carregandoRegras.set(false);
                this.notify.erro(e);
            }
        });
    }

    reconhecer(a: Alerta): void {
        this.api.reconhecer(a.id).subscribe({
            next: () => {
                this.notify.sucesso('Alerta reconhecido.');
                this.carregar();
            },
            error: (e) => this.notify.erro(e)
        });
    }

    rotuloDoStatus(s: StatusAlerta): string {
        return this.rotuloStatus[s] ?? s;
    }

    condicao(r: RegraAlerta): string {
        if (r.tipo === 'SEM_COMUNICACAO') {
            return `Sem mensagem há mais de ${r.limite} min`;
        }
        return `${r.campo} ${r.operador ? SIMBOLO[r.operador] : '?'} ${Number(r.limite).toLocaleString('pt-BR')}`;
    }

    corStatus(s: StatusAlerta): 'danger' | 'warn' | 'success' {
        return s === 'ABERTO' ? 'danger' : s === 'RECONHECIDO' ? 'warn' : 'success';
    }

    corSeveridade(s: Severidade): 'danger' | 'warn' | 'info' {
        return s === 'CRITICO' ? 'danger' : s === 'AVISO' ? 'warn' : 'info';
    }

    novaRegra(): void {
        this.editandoId = null;
        this.form = this.vazio();
        this.abrirDialogo();
    }

    editarRegra(r: RegraAlerta): void {
        this.editandoId = r.id;
        this.form = {
            nome: r.nome,
            tipo: r.tipo,
            dispositivoId: r.dispositivoId ?? null,
            campo: r.campo ?? null,
            operador: r.operador ?? 'GT',
            limite: Number(r.limite),
            severidade: r.severidade,
            notificarEmail: r.notificarEmail,
            destinatarios: r.destinatarios ?? null,
            ativo: r.ativo
        };
        this.abrirDialogo();
    }

    private abrirDialogo(): void {
        if (this.dispositivos().length === 0) {
            this.api.dispositivos('', true, 0, 500).subscribe({ next: (r) => this.dispositivos.set(r.result) });
        }
        this.dialogo = true;
    }

    salvarRegra(): void {
        const f = this.form;
        if (!f.nome?.trim()) {
            this.notify.aviso('Informe o nome.');
            return;
        }
        if (f.limite === null || f.limite === undefined) {
            this.notify.aviso(f.tipo === 'VALOR' ? 'Informe o limite.' : 'Informe os minutos sem mensagem.');
            return;
        }
        if (f.tipo === 'VALOR' && !f.campo?.trim()) {
            this.notify.aviso('Informe o campo.');
            return;
        }
        if (f.notificarEmail) {
            const lista = (f.destinatarios ?? '').split(/[,;\s]+/).filter((x) => x);
            if (lista.length === 0 || lista.some((x) => !emailValido(x))) {
                this.notify.aviso('Informe e-mails válidos, separados por vírgula.');
                return;
            }
        }
        this.salvando.set(true);
        const obs = this.editandoId ? this.api.atualizarRegra(this.editandoId, f) : this.api.criarRegra(f);
        obs.subscribe({
            next: () => {
                this.salvando.set(false);
                this.dialogo = false;
                this.notify.sucesso('Regra salva.');
                this.carregarRegras();
            },
            error: (e) => {
                this.salvando.set(false);
                this.notify.erro(e);
            }
        });
    }

    excluirRegra(r: RegraAlerta): void {
        this.notify.confirmarExclusao(`a regra ${r.nome}`, () =>
            this.api.excluirRegra(r.id).subscribe({
                next: () => {
                    this.notify.sucesso('Regra excluída.');
                    this.carregarRegras();
                    this.carregar();
                },
                error: (e) => this.notify.erro(e)
            }),
            'Os alertas disparados por ela também são apagados.'
        );
    }

    private vazio(): RegraAlertaForm {
        return { nome: '', tipo: 'VALOR', dispositivoId: null, campo: '', operador: 'GT', limite: null, severidade: 'AVISO', notificarEmail: false, destinatarios: null, ativo: true };
    }
}
