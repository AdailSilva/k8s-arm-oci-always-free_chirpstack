import { Component, inject, input, OnInit, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TooltipModule } from 'primeng/tooltip';
import { AuthService } from '@/app/core/auth/auth.service';
import { AtuacaoService } from '@/app/core/http/atuacao.service';
import { AgendamentoRequest, AgendamentoResponse, AlvoAcao, CenaResponse, ParametrosComando, Recorrencia, TipoComando } from '@/app/core/http/atuacao.models';
import { MonitoramentoService } from '@/app/core/http/monitoramento.service';
import { Dispositivo } from '@/app/core/http/models';
import { NotifyService } from '@/app/core/notificacao/notify.service';
import { dataHora, tempoRelativo } from '@/app/core/format/format';
import { ehCargaCritica, hora, limparParametros, rotuloDias, validarParametros } from './atuacao-util';
import { ComandoForm } from './comando-form';
import { ConfirmacaoCritica } from './confirmacao-critica';
import { DiasSemana } from './dias-semana';
import { EquipamentosCache } from './equipamentos-cache';

interface FormAgendamento {
    nome: string;
    alvo: AlvoAcao;
    dispositivoId: number | null;
    tipo: TipoComando | null;
    parametros: ParametrosComando | null;
    cenaId: number | null;
    recorrencia: Recorrencia;
    executarEm: Date | null;
    horario: string;
    diasSemana: number[];
    validadeMin: number | null;
    maxTentativas: number | null;
    ativo: boolean;
}

/**
 * Agendamentos (todos ou só os de um dispositivo): lista, criação, edição, exclusão e ativar/desativar.
 * Executam um comando num dispositivo ou uma cena, uma vez ou toda semana.
 */
@Component({
    selector: 'app-agendamentos-lista',
    standalone: true,
    imports: [CommonModule, FormsModule, RouterModule, ButtonModule, DatePickerModule, DialogModule, InputNumberModule, InputTextModule, SelectModule, SelectButtonModule, TableModule, TagModule, ToggleSwitchModule, TooltipModule, ComandoForm, ConfirmacaoCritica, DiasSemana],
    providers: [EquipamentosCache],
    template: `
        <div class="flex flex-wrap items-center justify-end gap-2 mb-3">
            <p-button icon="pi pi-refresh" label="Atualizar" severity="secondary" [outlined]="true" [loading]="carregando()" (onClick)="carregar()" />
            @if (auth.podeEscrever()) {
                <p-button icon="pi pi-plus" label="Novo agendamento" (onClick)="novo()" />
            }
        </div>
        <p-table [value]="itens()" [loading]="carregando()" [rowHover]="true" styleClass="p-datatable-sm">
            <ng-template #header>
                <tr>
                    <th>Agendamento</th>
                    <th class="hidden md:table-cell">Quando</th>
                    <th class="hidden lg:table-cell">Próxima execução</th>
                    <th class="w-20">Ativo</th>
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
                                <i class="pi pi-send mr-1"></i>{{ a.resumo || a.tipoRotulo }}
                                @if (!dispositivoId()) {
                                    · <a [routerLink]="['/monitoramento/dispositivos', a.dispositivoId]" class="text-primary">{{ a.dispositivoNome || '#' + a.dispositivoId }}</a>
                                }
                            }
                        </div>
                        <div class="text-xs text-surface-500 md:hidden">{{ quando(a) }}</div>
                        <div class="text-xs text-surface-500 lg:hidden">Próxima: {{ a.proximaExecucaoEm ? dh(a.proximaExecucaoEm) : '—' }}</div>
                    </td>
                    <td class="hidden md:table-cell text-sm">{{ quando(a) }}</td>
                    <td class="hidden lg:table-cell text-sm">
                        {{ a.proximaExecucaoEm ? dh(a.proximaExecucaoEm) : '—' }}
                        @if (a.ultimaExecucaoEm) {
                            <div class="text-xs text-surface-500">Última: {{ rel(a.ultimaExecucaoEm) }}</div>
                        }
                    </td>
                    <td>
                        <p-toggleswitch [ngModel]="a.ativo" (ngModelChange)="alternarAtivo(a, $event)" [disabled]="!auth.podeEscrever()" [ariaLabel]="'Ativo: ' + a.nome" />
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
                <tr><td colspan="5" class="text-center py-8 text-surface-500">Nenhum agendamento.</td></tr>
            </ng-template>
        </p-table>

        <p-dialog [visible]="dialogo()" (visibleChange)="dialogo.set($event)" [header]="editandoId ? 'Editar agendamento' : 'Novo agendamento'" [modal]="true" [draggable]="false" [style]="{ width: '44rem' }" [breakpoints]="{ '768px': '95vw' }">
            <div class="flex flex-col gap-5">
                <div class="min-w-0">
                    <label for="ag-nome" class="block font-medium mb-2">Nome <span class="text-red-500">*</span></label>
                    <input pInputText id="ag-nome" class="w-full" maxlength="120" placeholder="Ex.: Ligar a bomba de manhã" [(ngModel)]="form.nome" />
                </div>

                <div class="min-w-0">
                    <span class="block font-medium mb-2">Executar</span>
                    <p-selectbutton [options]="alvos" optionLabel="label" optionValue="value" [(ngModel)]="form.alvo" [allowEmpty]="false" />
                </div>

                @if (form.alvo === 'COMANDO') {
                    <div class="min-w-0">
                        <label for="ag-disp" class="block font-medium mb-2">Dispositivo <span class="text-red-500">*</span></label>
                        <p-select inputId="ag-disp" [options]="dispositivosTodos()" optionLabel="nome" optionValue="id" [ngModel]="form.dispositivoId" (ngModelChange)="mudarDispositivo($event)" placeholder="Escolha o dispositivo" [filter]="true" filterBy="nome,devEui" [disabled]="!!dispositivoId()" styleClass="w-full" appendTo="body" />
                    </div>
                    <app-comando-form id="ag-cmd" [comandos]="cache.comandos(form.dispositivoId)" [perifericos]="cache.perifericos(form.dispositivoId)" [semComandos]="cache.semComandos(form.dispositivoId)" [(tipo)]="form.tipo" [(parametros)]="form.parametros" />
                } @else {
                    <div class="min-w-0">
                        <label for="ag-cena" class="block font-medium mb-2">Cena <span class="text-red-500">*</span></label>
                        <p-select inputId="ag-cena" [options]="cenas()" optionLabel="nome" optionValue="id" [(ngModel)]="form.cenaId" placeholder="Escolha a cena" [filter]="cenas().length > 8" filterBy="nome" emptyMessage="Nenhuma cena cadastrada." styleClass="w-full" appendTo="body" />
                    </div>
                }

                <div class="min-w-0">
                    <span class="block font-medium mb-2">Recorrência</span>
                    <p-selectbutton [options]="recorrencias" optionLabel="label" optionValue="value" [(ngModel)]="form.recorrencia" [allowEmpty]="false" />
                </div>

                @if (form.recorrencia === 'UNICA') {
                    <div class="min-w-0">
                        <label for="ag-quando" class="block font-medium mb-2">Data e hora <span class="text-red-500">*</span></label>
                        <p-datepicker inputId="ag-quando" [(ngModel)]="form.executarEm" [showTime]="true" hourFormat="24" dateFormat="dd/mm/yy" [showIcon]="true" appendTo="body" styleClass="w-full sm:w-72" inputStyleClass="w-full" />
                    </div>
                } @else {
                    <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
                        <div class="min-w-0">
                            <label for="ag-hora" class="block font-medium mb-2">Horário <span class="text-red-500">*</span></label>
                            <input type="time" id="ag-hora" class="p-inputtext p-component w-full" [(ngModel)]="form.horario" />
                        </div>
                        <div class="min-w-0 sm:col-span-2">
                            <span class="block font-medium mb-2">Dias da semana</span>
                            <app-dias-semana [(value)]="form.diasSemana" />
                            <small class="text-surface-500 block mt-1">Nenhum marcado = todos os dias.</small>
                        </div>
                    </div>
                }

                <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div class="min-w-0">
                        <label for="ag-val" class="block font-medium mb-2">Validade de cada execução</label>
                        <p-inputnumber inputId="ag-val" [(ngModel)]="form.validadeMin" [min]="1" [max]="43200" suffix=" min" placeholder="60 min" styleClass="w-full" inputStyleClass="w-full" />
                        <small class="text-surface-500">Se não executar nesse prazo, expira.</small>
                    </div>
                    <div class="min-w-0">
                        <label for="ag-tent" class="block font-medium mb-2">Máximo de tentativas</label>
                        <p-inputnumber inputId="ag-tent" [(ngModel)]="form.maxTentativas" [min]="1" [max]="50" placeholder="5" styleClass="w-full" inputStyleClass="w-full" />
                        <small class="text-surface-500">De 1 a 50.</small>
                    </div>
                </div>

                <label class="flex items-center gap-3 cursor-pointer">
                    <p-toggleswitch [(ngModel)]="form.ativo" />
                    <span class="font-medium">Ativo</span>
                </label>
            </div>
            <ng-template #footer>
                <div class="flex flex-wrap justify-end gap-2">
                    <p-button label="Cancelar" severity="secondary" [outlined]="true" (onClick)="dialogo.set(false)" />
                    <p-button label="Salvar" icon="pi pi-check" [loading]="salvando()" (onClick)="salvar()" />
                </div>
            </ng-template>
        </p-dialog>
        <app-confirmacao-critica />
    `
})
export class AgendamentosLista implements OnInit {
    auth = inject(AuthService);
    cache = inject(EquipamentosCache);
    private api = inject(AtuacaoService);
    private mon = inject(MonitoramentoService);
    private notify = inject(NotifyService);
    private critico = viewChild.required(ConfirmacaoCritica);

    /** Só os agendamentos deste dispositivo (e novos já apontam para ele). */
    dispositivoId = input<number | null>(null);

    itens = signal<AgendamentoResponse[]>([]);
    carregando = signal(false);
    dispositivosTodos = signal<Dispositivo[]>([]);
    cenas = signal<CenaResponse[]>([]);
    dialogo = signal(false);
    salvando = signal(false);
    editandoId: number | null = null;
    form: FormAgendamento = this.vazio();

    alvos = [
        { label: 'Comando', value: 'COMANDO' },
        { label: 'Cena', value: 'CENA' }
    ];
    recorrencias = [
        { label: 'Uma vez', value: 'UNICA' },
        { label: 'Toda semana', value: 'SEMANAL' }
    ];
    dh = dataHora;
    rel = tempoRelativo;

    ngOnInit(): void {
        this.carregar();
    }

    carregar(): void {
        this.carregando.set(true);
        this.api.agendamentos(this.dispositivoId()).subscribe({
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

    quando(a: AgendamentoResponse): string {
        return a.recorrencia === 'UNICA' ? `Uma vez, ${dataHora(a.executarEm)}` : `${hora(a.horario)}, ${rotuloDias(a.diasSemana)}`;
    }

    private prepararListas(): void {
        if (this.dispositivosTodos().length === 0) {
            this.mon.dispositivos('', true, 0, 500).subscribe({ next: (r) => this.dispositivosTodos.set(r.result) });
        }
        this.api.cenas().subscribe({ next: (c) => this.cenas.set(c) });
    }

    novo(): void {
        this.editandoId = null;
        this.form = this.vazio();
        this.cache.garantir(this.form.dispositivoId);
        this.prepararListas();
        this.dialogo.set(true);
    }

    editar(a: AgendamentoResponse): void {
        this.editandoId = a.id;
        this.form = {
            nome: a.nome,
            alvo: a.alvo,
            dispositivoId: a.dispositivoId ?? this.dispositivoId(),
            tipo: a.tipo ?? null,
            parametros: a.parametros ?? {},
            cenaId: a.cenaId ?? null,
            recorrencia: a.recorrencia,
            executarEm: a.executarEm ? new Date(a.executarEm) : null,
            horario: hora(a.horario),
            diasSemana: a.diasSemana ?? [],
            validadeMin: a.validadeMin,
            maxTentativas: a.maxTentativas,
            ativo: a.ativo
        };
        this.cache.garantir(this.form.dispositivoId);
        this.prepararListas();
        this.dialogo.set(true);
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

        if (f.alvo === 'COMANDO') {
            if (!f.dispositivoId) {
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

        if (f.recorrencia === 'UNICA' && !f.executarEm) {
            this.notify.aviso('Informe a data e a hora da execução.');

            return;
        }

        if (f.recorrencia === 'SEMANAL' && !f.horario) {
            this.notify.aviso('Informe o horário.');

            return;
        }
        const req: AgendamentoRequest = {
            nome: f.nome.trim(),
            alvo: f.alvo,
            dispositivoId: f.alvo === 'COMANDO' ? f.dispositivoId : null,
            tipo: f.alvo === 'COMANDO' ? f.tipo : null,
            parametros: f.alvo === 'COMANDO' && f.tipo ? limparParametros(f.tipo, f.parametros ?? {}) : null,
            cenaId: f.alvo === 'CENA' ? f.cenaId : null,
            recorrencia: f.recorrencia,
            executarEm: f.recorrencia === 'UNICA' && f.executarEm ? f.executarEm.toISOString() : null,
            horario: f.recorrencia === 'SEMANAL' ? f.horario : null,
            diasSemana: f.recorrencia === 'SEMANAL' ? f.diasSemana : null,
            validadeMin: f.validadeMin,
            maxTentativas: f.maxTentativas,
            ativo: f.ativo,
            confirmarCritico
        };

        this.salvando.set(true);
        const obs = this.editandoId ? this.api.atualizarAgendamento(this.editandoId, req) : this.api.criarAgendamento(req);

        obs.subscribe({
            next: () => {
                this.salvando.set(false);
                this.dialogo.set(false);
                this.notify.sucesso('Agendamento salvo.');
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

    alternarAtivo(a: AgendamentoResponse, ativo: boolean, confirmarCritico = false): void {
        const req: AgendamentoRequest = {
            nome: a.nome,
            alvo: a.alvo,
            dispositivoId: a.dispositivoId ?? null,
            tipo: a.tipo ?? null,
            parametros: a.parametros ?? null,
            cenaId: a.cenaId ?? null,
            recorrencia: a.recorrencia,
            executarEm: a.executarEm ?? null,
            horario: a.horario ? hora(a.horario) : null,
            diasSemana: a.diasSemana ?? null,
            validadeMin: a.validadeMin,
            maxTentativas: a.maxTentativas,
            ativo,
            confirmarCritico
        };

        this.api.atualizarAgendamento(a.id, req).subscribe({
            next: (novo) => {
                this.itens.update((l) => l.map((x) => (x.id === novo.id ? novo : x)));
                this.notify.sucesso(ativo ? 'Agendamento ativado.' : 'Agendamento desativado.');
            },
            error: (e) => {
                // Volta o interruptor para o valor salvo.
                this.itens.update((l) => l.map((x) => (x.id === a.id ? { ...x } : x)));

                if (ehCargaCritica(e)) {
                    this.critico().pedir(NotifyService.mensagem(e), () => this.alternarAtivo(a, ativo, true));
                } else {
                    this.notify.erro(e);
                }
            }
        });
    }

    excluir(a: AgendamentoResponse): void {
        this.notify.confirmarExclusao(`o agendamento ${a.nome}`, () =>
            this.api.excluirAgendamento(a.id).subscribe({
                next: () => {
                    this.notify.sucesso('Agendamento excluído.');
                    this.carregar();
                },
                error: (e) => this.notify.erro(e)
            })
        );
    }

    private vazio(): FormAgendamento {
        return {
            nome: '',
            alvo: 'COMANDO',
            dispositivoId: this.dispositivoId(),
            tipo: null,
            parametros: {},
            cenaId: null,
            recorrencia: 'SEMANAL',
            executarEm: null,
            horario: '08:00',
            diasSemana: [],
            validadeMin: null,
            maxTentativas: null,
            ativo: true
        };
    }
}
