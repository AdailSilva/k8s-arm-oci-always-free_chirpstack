import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DatePickerModule } from 'primeng/datepicker';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TooltipModule } from 'primeng/tooltip';
import { HorarioAcessoDia } from '@/app/core/http/models';
import { HorarioAcessoService } from '@/app/core/sessao/horario-acesso.service';
import { NotifyService } from '@/app/core/notificacao/notify.service';
import { travaEdicao } from '@/app/core/edicao/trava-edicao';
import { PageHeader } from '@/app/shared/visual/page-header';

type ModoDia = 'LIVRE' | 'HORARIO' | 'SEM_ACESSO';

interface LinhaDia {
    diaSemana: number;
    diaNome: string;
    modo: ModoDia;
    inicio: Date | null;
    fim: Date | null;
}

/**
 * SISTEMA → HORÁRIO DE ACESSO (somente ADMIN), mesmo padrão dos demais sistemas.
 *
 * Grade semanal que vale para OPERADOR e VISUALIZADOR. Fora dela o login é recusado com o motivo e
 * quando o acesso volta; 5 minutos antes do fim aparece o aviso e, no fim, a sessão é encerrada.
 * "Livre" = sem restrição no dia; "Sem acesso" = bloqueia o dia inteiro. Os sete dias livres = sem
 * restrição nenhuma.
 */
@Component({
    selector: 'app-horario-acesso',
    standalone: true,
    imports: [CommonModule, FormsModule, RouterModule, ButtonModule, CheckboxModule, DatePickerModule, SelectButtonModule, TooltipModule, PageHeader],
    template: `
        <app-page-header title="Horário de acesso" subtitle="Janela de uso do painel por dia da semana" icon="pi pi-calendar-clock">
            <p-button icon="pi pi-stopwatch" label="Controle de sessão" severity="secondary" [outlined]="true" routerLink="/sistema/controle-sessao" />
            <p-button icon="pi pi-check" label="Salvar" (onClick)="salvar()" [loading]="salvando()" [disabled]="!carregado() || !alterado()" />
        </app-page-header>

        <div class="grid grid-cols-1 xl:grid-cols-12 gap-6">
            <div class="xl:col-span-8 flex flex-col gap-6">
                <div class="card mb-0!">
                    <div class="flex flex-col sm:flex-row sm:items-start justify-between gap-4 mb-5">
                        <div class="min-w-0">
                            <div class="font-semibold text-lg">Dias da semana</div>
                            <p class="text-surface-500 m-0 mt-1">Vale para os perfis <b>Operador</b> e <b>Visualizador</b>. Administradores entram a qualquer hora.</p>
                        </div>
                        <p-button label="Copiar segunda para os dias úteis" icon="pi pi-copy" severity="secondary" [outlined]="true" size="small" styleClass="shrink-0"
                            pTooltip="Aplica o horário de segunda-feira de terça a sexta" tooltipPosition="bottom" (onClick)="copiarSegunda()" [disabled]="!carregado()" />
                    </div>

                    <div class="flex flex-col gap-3">
                        @for (d of linhas(); track d.diaSemana) {
                            <div class="rounded-xl border border-surface-200 dark:border-surface-700 p-4 flex flex-col lg:flex-row lg:items-center gap-3 lg:gap-4"
                                 [ngClass]="d.modo === 'SEM_ACESSO' ? 'bg-surface-50 dark:bg-surface-800' : ''">
                                <div class="lg:w-36 shrink-0 font-medium flex items-center gap-2">
                                    <span class="w-2 h-2 rounded-full shrink-0" [ngClass]="{ 'bg-emerald-500': d.modo === 'LIVRE', 'bg-sky-500': d.modo === 'HORARIO', 'bg-red-500': d.modo === 'SEM_ACESSO' }"></span>
                                    <span class="whitespace-nowrap">{{ d.diaNome }}</span>
                                </div>
                                <p-selectbutton [ngModel]="d.modo" (ngModelChange)="mudarModo(d, $event)" [options]="modos" optionLabel="label" optionValue="value"
                                    [allowEmpty]="false" size="small" styleClass="shrink-0" />
                                @if (d.modo === 'HORARIO') {
                                    <div class="flex flex-wrap items-center gap-2">
                                        <span class="text-sm text-surface-500">das</span>
                                        <p-datepicker [(ngModel)]="d.inicio" (ngModelChange)="tocar()" [timeOnly]="true" hourFormat="24" [stepMinute]="5" appendTo="body"
                                            placeholder="Início, ex.: 07:00" inputStyleClass="w-40! text-center" [ariaLabel]="'Início de ' + d.diaNome" />
                                        <span class="text-sm text-surface-500">às</span>
                                        <p-datepicker [(ngModel)]="d.fim" (ngModelChange)="tocar()" [timeOnly]="true" hourFormat="24" [stepMinute]="5" appendTo="body"
                                            placeholder="Fim, ex.: 18:00" inputStyleClass="w-40! text-center" [ariaLabel]="'Fim de ' + d.diaNome" />
                                    </div>
                                    @if (erroDia(d); as erro) {
                                        <small class="text-red-500 flex items-center gap-1"><i class="pi pi-exclamation-circle text-xs!"></i>{{ erro }}</small>
                                    }
                                } @else if (d.modo === 'LIVRE') {
                                    <span class="text-sm text-surface-500">Sem restrição neste dia.</span>
                                } @else {
                                    <span class="text-sm text-red-600 dark:text-red-400">Só administradores acessam neste dia.</span>
                                }
                            </div>
                        }
                    </div>
                </div>

                <div class="card mb-0! flex flex-col md:flex-row md:items-center justify-between gap-4">
                    <label class="flex items-start gap-3 cursor-pointer">
                        <p-checkbox [(ngModel)]="aplicarAgora" [binary]="true" inputId="aplicarAgora" />
                        <span>Aplicar agora: desconectar quem já está fora do novo horário (exceto administradores)</span>
                    </label>
                </div>
            </div>

            <div class="xl:col-span-4 flex flex-col gap-6">
                <div class="card mb-0!">
                    <div class="font-semibold text-lg mb-4">Resumo</div>
                    @if (semRestricao()) {
                        <div class="flex items-start gap-3 text-sm">
                            <i class="pi pi-lock-open text-emerald-500 mt-0.5"></i>
                            <span>Todos os dias estão livres: não há restrição de horário.</span>
                        </div>
                    } @else {
                        <div class="flex flex-col gap-2 text-sm">
                            @for (d of linhas(); track d.diaSemana) {
                                <div class="flex justify-between gap-3">
                                    <span class="text-surface-500">{{ d.diaNome }}</span>
                                    <b class="tabular-nums text-right">{{ descricao(d) }}</b>
                                </div>
                            }
                        </div>
                    }
                </div>
                <div class="card mb-0!">
                    <div class="font-semibold text-lg mb-3">Como funciona</div>
                    <ul class="m-0 pl-5 text-sm text-surface-600 dark:text-surface-300 flex flex-col gap-2 list-disc">
                        <li>Fora do horário, o login é recusado com o motivo e quando o acesso volta.</li>
                        <li>5 minutos antes do fim, quem está conectado vê um aviso com a contagem regressiva.</li>
                        <li>No fim do horário a sessão é encerrada e o login explica o motivo.</li>
                        <li>O servidor também confere: uma aba esquecida aberta ou um relógio errado não estendem o acesso.</li>
                        <li>O encerramento fica no histórico de visitantes como "Fim do horário de acesso".</li>
                        <li>O horário segue o fuso do servidor (America/Fortaleza).</li>
                    </ul>
                </div>
            </div>
        </div>
    `
})
export class HorarioAcesso implements OnInit {
    private api = inject(HorarioAcessoService);
    private notify = inject(NotifyService);

    readonly linhas = signal<LinhaDia[]>([]);
    readonly salvando = signal(false);
    readonly carregado = signal(false);
    /** Muda a cada edição, para os computed relerem a grade. */
    private readonly versao = signal(0);
    private original = '';
    aplicarAgora = false;
    /** Alterações não salvas: sair da tela pergunta antes ("Deseja realmente sair?"). */
    private readonly trava = travaEdicao({ sujo: () => this.carregado() && this.serializar() !== this.original });

    readonly modos: { label: string; value: ModoDia }[] = [
        { label: 'Livre', value: 'LIVRE' },
        { label: 'Horário', value: 'HORARIO' },
        { label: 'Sem acesso', value: 'SEM_ACESSO' }
    ];

    readonly semRestricao = computed(() => {
        this.versao();
        return this.linhas().every((l) => l.modo === 'LIVRE');
    });

    ngOnInit(): void {
        this.api.listar().subscribe({
            next: (r) => this.aplicar(r ?? []),
            error: (e) => this.notify.erro(e, 'Não foi possível carregar o horário de acesso')
        });
    }

    tocar(): void {
        this.versao.update((v) => v + 1);
    }

    mudarModo(d: LinhaDia, modo: ModoDia): void {
        d.modo = modo;
        if (modo === 'HORARIO' && !d.inicio && !d.fim) {
            d.inicio = this.hora('07:00');
            d.fim = this.hora('18:00');
        }
        this.linhas.set([...this.linhas()]);
        this.tocar();
    }

    copiarSegunda(): void {
        const seg = this.linhas().find((l) => l.diaSemana === 1);
        if (!seg) {
            return;
        }
        this.linhas.set(
            this.linhas().map((l) =>
                l.diaSemana >= 2 && l.diaSemana <= 5
                    ? { ...l, modo: seg.modo, inicio: seg.inicio ? new Date(seg.inicio) : null, fim: seg.fim ? new Date(seg.fim) : null }
                    : l
            )
        );
        this.tocar();
    }

    erroDia(d: LinhaDia): string | null {
        if (d.modo !== 'HORARIO') {
            return null;
        }
        if (!d.inicio || !d.fim) {
            return 'Informe o início e o fim.';
        }
        const ini = this.minutos(d.inicio);
        const fim = this.minutos(d.fim);
        if (fim <= ini) {
            return 'O fim precisa ser depois do início.';
        }
        if (fim - ini < 15) {
            return 'A janela precisa ter pelo menos 15 minutos.';
        }
        return null;
    }

    descricao(d: LinhaDia): string {
        if (d.modo === 'LIVRE') {
            return 'Livre';
        }
        if (d.modo === 'SEM_ACESSO') {
            return 'Sem acesso';
        }
        return d.inicio && d.fim ? `${this.fmt(d.inicio)} às ${this.fmt(d.fim)}` : 'Incompleto';
    }

    alterado(): boolean {
        this.versao();
        return this.serializar() !== this.original || this.aplicarAgora;
    }

    salvar(): void {
        const invalido = this.linhas().find((l) => this.erroDia(l));
        if (invalido) {
            this.notify.aviso(`${invalido.diaNome}: ${this.erroDia(invalido)}`, 'Verifique o horário');
            return;
        }
        this.salvando.set(true);
        this.api.salvar(this.paraApi(), this.aplicarAgora).subscribe({
            next: (r) => {
                this.salvando.set(false);
                this.aplicar(r.dias);
                this.aplicarAgora = false;
                this.api.reconsultar();
                this.notify.sucesso(
                    r.sessoesEncerradas > 0
                        ? `Horário salvo. ${r.sessoesEncerradas} sessão(ões) fora do novo horário encerrada(s).`
                        : 'Horário salvo. A nova grade já vale para os próximos acessos.'
                );
            },
            error: (e) => {
                this.salvando.set(false);
                this.notify.erro(e, 'Não foi possível salvar o horário de acesso');
            }
        });
    }

    private aplicar(dias: HorarioAcessoDia[]): void {
        this.linhas.set(
            dias.map((d) => ({
                diaSemana: d.diaSemana,
                diaNome: d.diaNome ?? String(d.diaSemana),
                modo: !d.permitido ? 'SEM_ACESSO' : d.horaInicio && d.horaFim ? 'HORARIO' : 'LIVRE',
                inicio: this.hora(d.horaInicio),
                fim: this.hora(d.horaFim)
            }))
        );
        this.original = this.serializar();
        this.carregado.set(true);
        this.tocar();
    }

    private paraApi(): HorarioAcessoDia[] {
        return this.linhas().map((l) => ({
            diaSemana: l.diaSemana,
            permitido: l.modo !== 'SEM_ACESSO',
            horaInicio: l.modo === 'HORARIO' && l.inicio ? this.fmt(l.inicio) : null,
            horaFim: l.modo === 'HORARIO' && l.fim ? this.fmt(l.fim) : null
        }));
    }

    private serializar(): string {
        return JSON.stringify(this.paraApi());
    }

    private hora(h: string | null | undefined): Date | null {
        if (!h) {
            return null;
        }
        const [hh, mm] = h.split(':').map(Number);
        const d = new Date();
        d.setHours(hh || 0, mm || 0, 0, 0);
        return d;
    }

    private minutos(d: Date): number {
        return d.getHours() * 60 + d.getMinutes();
    }

    private fmt(d: Date): string {
        return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
    }
}
