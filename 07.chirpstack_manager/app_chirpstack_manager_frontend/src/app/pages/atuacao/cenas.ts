import { Component, inject, OnInit, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { TooltipModule } from 'primeng/tooltip';
import { AuthService } from '@/app/core/auth/auth.service';
import { AtuacaoService } from '@/app/core/http/atuacao.service';
import { CenaRequest, CenaResponse, ParametrosComando, TipoComando } from '@/app/core/http/atuacao.models';
import { MonitoramentoService } from '@/app/core/http/monitoramento.service';
import { Dispositivo } from '@/app/core/http/models';
import { NotifyService } from '@/app/core/notificacao/notify.service';
import { tempoRelativo } from '@/app/core/format/format';
import { ehCargaCritica, limparParametros, validarParametros } from '@/app/shared/atuacao/atuacao-util';
import { ComandoForm } from '@/app/shared/atuacao/comando-form';
import { ConfirmacaoCritica } from '@/app/shared/atuacao/confirmacao-critica';
import { EquipamentosCache } from '@/app/shared/atuacao/equipamentos-cache';
import { TravaEdicaoJanela } from '@/app/core/edicao/trava-edicao';
import { PageHeader } from '@/app/shared/visual/page-header';

interface ItemForm {
    dispositivoId: number | null;
    tipo: TipoComando | null;
    parametros: ParametrosComando | null;
}

/**
 * Cenas: vários comandos (em um ou vários dispositivos) disparados por um botão. Também podem ser
 * o alvo de agendamentos e automações.
 */
@Component({
    selector: 'app-cenas',
    standalone: true,
    imports: [TravaEdicaoJanela, CommonModule, FormsModule, RouterModule, ButtonModule, DialogModule, InputTextModule, SelectModule, TagModule, TextareaModule, TooltipModule, ComandoForm, ConfirmacaoCritica, PageHeader],
    providers: [EquipamentosCache],
    template: `
        <app-page-header title="Cenas" subtitle="Vários comandos, em vários dispositivos, em um botão" icon="pi pi-images">
            <p-button icon="pi pi-refresh" label="Atualizar" severity="secondary" [outlined]="true" [loading]="carregando()" (onClick)="carregar()" />
            @if (auth.podeEscrever()) {
                <p-button icon="pi pi-plus" label="Nova cena" (onClick)="nova()" />
            }
        </app-page-header>

        @if (carregando() && itens().length === 0) {
            <div class="card text-center text-surface-500 py-8"><i class="pi pi-spin pi-spinner mr-2"></i>Carregando...</div>
        } @else if (itens().length === 0) {
            <div class="card flex flex-col items-center text-center gap-2 py-10 text-surface-500">
                <i class="pi pi-images text-3xl!"></i>
                <div>Nenhuma cena. Ex.: "Sair de casa" desliga a iluminação e liga o alarme.</div>
            </div>
        } @else {
            <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
                @for (c of itens(); track c.id) {
                    <div class="card mb-0! flex flex-col gap-3 min-w-0">
                        <div class="flex items-start justify-between gap-2">
                            <div class="min-w-0">
                                <div class="font-semibold text-lg break-words">{{ c.nome }}</div>
                                @if (c.descricao) {
                                    <div class="text-sm text-surface-500 break-words">{{ c.descricao }}</div>
                                }
                            </div>
                            @if (c.temCritico) {
                                <p-tag value="Crítica" severity="danger" icon="pi pi-exclamation-triangle" pTooltip="Aciona ao menos um relé crítico" />
                            }
                        </div>
                        <ol class="m-0 pl-5 flex flex-col gap-1 text-sm">
                            @for (i of c.itens; track i.id) {
                                <li class="break-words">
                                    <span class="font-medium">{{ i.resumo || i.tipoRotulo }}</span>
                                    <span class="text-surface-500"> · </span>
                                    <a [routerLink]="['/monitoramento/dispositivos', i.dispositivoId]" class="text-primary">{{ i.dispositivoNome || '#' + i.dispositivoId }}</a>
                                </li>
                            }
                        </ol>
                        <div class="text-xs text-surface-500 mt-auto">
                            Atualizada {{ rel(c.atualizadoEm) }}
                            @if (c.atualizadoPor) {
                                por {{ c.atualizadoPor }}
                            }
                        </div>
                        @if (auth.podeEscrever()) {
                            <div class="flex flex-wrap items-center justify-between gap-2 pt-2 border-t border-surface-200 dark:border-surface-700">
                                <p-button label="Executar" icon="pi pi-play" size="small" [loading]="executando() === c.id" (onClick)="executar(c)" />
                                <div class="flex gap-1">
                                    <p-button icon="pi pi-pencil" [text]="true" [rounded]="true" pTooltip="Editar" ariaLabel="Editar" (onClick)="editar(c)" />
                                    <p-button icon="pi pi-trash" severity="danger" [text]="true" [rounded]="true" pTooltip="Excluir" ariaLabel="Excluir" (onClick)="excluir(c)" />
                                </div>
                            </div>
                        }
                    </div>
                }
            </div>
        }

        <p-dialog [csmTravaEdicao]="[nome, descricao, form]" #trava="travaEdicao" [visible]="dialogo()" (visibleChange)="dialogo.set($event)" [header]="editandoId ? 'Editar cena' : 'Nova cena'" [modal]="true" [draggable]="false" [style]="{ width: '48rem' }" [breakpoints]="{ '768px': '95vw' }">
            <div class="flex flex-col gap-5">
                <div class="min-w-0">
                    <label for="cena-nome" class="block font-medium mb-2">Nome <span class="text-red-500">*</span></label>
                    <input pInputText id="cena-nome" class="w-full" maxlength="120" placeholder="Ex.: Sair de casa" [(ngModel)]="nome" />
                </div>
                <div class="min-w-0">
                    <label for="cena-desc" class="block font-medium mb-2">Descrição</label>
                    <textarea pTextarea id="cena-desc" class="w-full" rows="2" maxlength="300" [(ngModel)]="descricao"></textarea>
                </div>

                <div>
                    <div class="flex items-center justify-between gap-2 mb-3">
                        <span class="font-medium">Comandos <span class="text-red-500">*</span></span>
                        <p-button label="Adicionar comando" icon="pi pi-plus" size="small" severity="secondary" [outlined]="true" (onClick)="adicionarItem()" />
                    </div>
                    <div class="flex flex-col gap-4">
                        @for (it of form; track $index; let i = $index) {
                            <div class="border border-surface-200 dark:border-surface-700 rounded-xl p-4">
                                <div class="flex items-center justify-between gap-2 mb-3">
                                    <span class="font-semibold text-sm">{{ i + 1 }}º comando</span>
                                    <div class="flex gap-1">
                                        <p-button icon="pi pi-arrow-up" [text]="true" [rounded]="true" size="small" [disabled]="i === 0" ariaLabel="Subir" (onClick)="mover(i, -1)" />
                                        <p-button icon="pi pi-arrow-down" [text]="true" [rounded]="true" size="small" [disabled]="i === form.length - 1" ariaLabel="Descer" (onClick)="mover(i, 1)" />
                                        <p-button icon="pi pi-trash" severity="danger" [text]="true" [rounded]="true" size="small" ariaLabel="Remover" (onClick)="removerItem(i)" />
                                    </div>
                                </div>
                                <div class="flex flex-col gap-4">
                                    <div class="min-w-0">
                                        <label [for]="'cena-disp-' + i" class="block font-medium mb-2">Dispositivo <span class="text-red-500">*</span></label>
                                        <p-select
                                            [inputId]="'cena-disp-' + i"
                                            [options]="dispositivos()"
                                            optionLabel="nome"
                                            optionValue="id"
                                            [ngModel]="it.dispositivoId"
                                            (ngModelChange)="mudarDispositivo(it, $event)"
                                            placeholder="Escolha o dispositivo"
                                            [filter]="true"
                                            filterBy="nome,devEui"
                                            styleClass="w-full"
                                            appendTo="body"
                                        />
                                    </div>
                                    <app-comando-form
                                        [id]="'cena-cmd-' + i"
                                        [comandos]="cache.comandos(it.dispositivoId)"
                                        [perifericos]="cache.perifericos(it.dispositivoId)"
                                        [semComandos]="cache.semComandos(it.dispositivoId)"
                                        [(tipo)]="it.tipo"
                                        [(parametros)]="it.parametros"
                                    />
                                </div>
                            </div>
                        }
                    </div>
                    <small class="text-surface-500 block mt-2">Os comandos entram na fila de cada dispositivo na ordem acima.</small>
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
export class Cenas implements OnInit {
    auth = inject(AuthService);
    cache = inject(EquipamentosCache);
    private api = inject(AtuacaoService);
    private mon = inject(MonitoramentoService);
    private notify = inject(NotifyService);
    private critico = viewChild.required(ConfirmacaoCritica);

    itens = signal<CenaResponse[]>([]);
    carregando = signal(false);
    dispositivos = signal<Dispositivo[]>([]);
    dialogo = signal(false);
    salvando = signal(false);
    executando = signal<number | null>(null);

    editandoId: number | null = null;
    nome = '';
    descricao = '';
    form: ItemForm[] = [];

    rel = tempoRelativo;

    ngOnInit(): void {
        this.carregar();
    }

    carregar(): void {
        this.carregando.set(true);
        this.api.cenas().subscribe({
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

    private prepararDispositivos(): void {
        if (this.dispositivos().length === 0) {
            this.mon.dispositivos('', true, 0, 500).subscribe({ next: (r) => this.dispositivos.set(r.result) });
        }
    }

    nova(): void {
        this.editandoId = null;
        this.nome = '';
        this.descricao = '';
        this.form = [{ dispositivoId: null, tipo: null, parametros: {} }];
        this.prepararDispositivos();
        this.dialogo.set(true);
    }

    editar(c: CenaResponse): void {
        this.editandoId = c.id;
        this.nome = c.nome;
        this.descricao = c.descricao ?? '';
        this.form = [...c.itens].sort((a, b) => a.ordem - b.ordem).map((i) => ({ dispositivoId: i.dispositivoId, tipo: i.tipo, parametros: i.parametros ?? {} }));
        this.form.forEach((i) => this.cache.garantir(i.dispositivoId));
        this.prepararDispositivos();
        this.dialogo.set(true);
    }

    adicionarItem(): void {
        const ultimo = this.form[this.form.length - 1]?.dispositivoId ?? null;

        this.form = [...this.form, { dispositivoId: ultimo, tipo: null, parametros: {} }];
        this.cache.garantir(ultimo);
    }

    removerItem(i: number): void {
        this.form = this.form.filter((_, j) => j !== i);
    }

    mover(i: number, d: number): void {
        const j = i + d;

        if (j < 0 || j >= this.form.length) {
            return;
        }
        const f = [...this.form];

        [f[i], f[j]] = [f[j], f[i]];
        this.form = f;
    }

    mudarDispositivo(it: ItemForm, id: number | null): void {
        it.dispositivoId = id;
        it.tipo = null;
        it.parametros = {};
        this.cache.garantir(id);
    }

    salvar(): void {
        if (!this.nome.trim()) {
            this.notify.aviso('Informe o nome.');

            return;
        }

        if (this.form.length === 0) {
            this.notify.aviso('Adicione ao menos um comando.');

            return;
        }

        for (const [i, it] of this.form.entries()) {
            if (!it.dispositivoId) {
                this.notify.aviso(`Escolha o dispositivo do ${i + 1}º comando.`);

                return;
            }
            const erro = validarParametros(it.tipo, it.parametros ?? {});

            if (erro) {
                this.notify.aviso(`${i + 1}º comando: ${erro}`);

                return;
            }
        }
        const req: CenaRequest = {
            nome: this.nome.trim(),
            descricao: this.descricao.trim() || null,
            itens: this.form.map((it) => ({ dispositivoId: it.dispositivoId!, tipo: it.tipo!, parametros: limparParametros(it.tipo!, it.parametros ?? {}) }))
        };

        this.salvando.set(true);
        const obs = this.editandoId ? this.api.atualizarCena(this.editandoId, req) : this.api.criarCena(req);

        obs.subscribe({
            next: () => {
                this.salvando.set(false);
                this.dialogo.set(false);
                this.notify.sucesso('Cena salva.');
                this.carregar();
            },
            error: (e) => {
                this.salvando.set(false);
                this.notify.erro(e);
            }
        });
    }

    executar(c: CenaResponse, confirmarCritico = false): void {
        const rodar = () => {
            this.executando.set(c.id);
            this.api.executarCena(c.id, confirmarCritico).subscribe({
                next: (r) => {
                    this.executando.set(null);
                    this.notify.sucesso(`${r.comandos.length} comando(s) na fila. Acompanhe em Atuação › Fila de comandos.`, `Cena "${c.nome}"`);
                },
                error: (e) => {
                    this.executando.set(null);

                    if (ehCargaCritica(e)) {
                        this.critico().pedir(NotifyService.mensagem(e), () => this.executar(c, true));
                    } else {
                        this.notify.erro(e);
                    }
                }
            });
        };

        if (confirmarCritico) {
            rodar();
        } else {
            this.notify.confirmar('Executar cena', `Executar "${c.nome}" (${c.itens.length} comando(s))?`, rodar, 'Executar');
        }
    }

    excluir(c: CenaResponse): void {
        this.notify.confirmarExclusao(
            `a cena ${c.nome}`,
            () =>
                this.api.excluirCena(c.id).subscribe({
                    next: () => {
                        this.notify.sucesso('Cena excluída.');
                        this.carregar();
                    },
                    error: (e) => this.notify.erro(e)
                }),
            'Agendamentos e automações que usam esta cena deixam de funcionar.'
        );
    }
}
