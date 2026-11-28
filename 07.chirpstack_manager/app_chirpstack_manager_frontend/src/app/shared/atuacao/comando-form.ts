import { Component, computed, input, model } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CheckboxModule } from 'primeng/checkbox';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { AcaoRele, ComandoSuportado, ParametrosComando, TipoComando, TipoPeriferico } from '@/app/core/http/atuacao.models';

/** O mínimo de um periférico para o formulário oferecer os canais por nome. */
export interface PerifericoOpcao {
    tipo: TipoPeriferico;
    canal: number;
    nome: string;
    critico?: boolean;
}

/** Parâmetros iniciais de cada comando. */
export function parametrosPadrao(t: TipoComando | null, perifericos: PerifericoOpcao[] = []): ParametrosComando {
    const rele = perifericos.find((p) => p.tipo === 'RELE')?.canal ?? 0;
    const pzem = perifericos.find((p) => p.tipo === 'PZEM004T')?.canal ?? 0;

    switch (t) {
        case 'RELE_DEFINIR':
            return { canal: rele, acao: 'LIGAR' };
        case 'RELE_PULSO':
            return { canal: rele, estado: true, duracaoMs: 5000 };
        case 'RELE_MASCARA':
            return { mascara: 0, valores: 0 };
        case 'PZEM_LER':
        case 'PZEM_ZERAR_ENERGIA':
            return { canal: pzem };
        case 'PZEM_LIMITE_POTENCIA':
            return { canal: pzem, watts: 2300 };
        case 'INTERVALO':
            return { segundos: 300 };
        case 'CONFIRMADO':
            return { confirmado: false };
        case 'CLASSE':
            return { classe: 'A' };
        case 'REINICIAR':
            return { atrasoMs: 0 };
        case 'BRUTO':
            return { porta: 1, dadosHex: '', confirmado: false };
        default:
            return {};
    }
}

/**
 * Escolha de um comando e o formulário dos parâmetros dele. Usado no dispositivo, nas cenas, nos
 * agendamentos e nas automações, para que todos falem a mesma língua.
 */
@Component({
    selector: 'app-comando-form',
    standalone: true,
    imports: [NgTemplateOutlet, FormsModule, CheckboxModule, InputNumberModule, InputTextModule, SelectModule, ToggleSwitchModule],
    template: `
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div class="min-w-0 sm:col-span-2">
                <label [for]="id() + '-tipo'" class="block font-medium mb-2">Comando <span class="text-red-500">*</span></label>
                <p-select
                    [inputId]="id() + '-tipo'"
                    [options]="comandos()"
                    optionLabel="rotulo"
                    optionValue="tipo"
                    [ngModel]="tipo()"
                    (ngModelChange)="mudarTipo($event)"
                    placeholder="Escolha o comando"
                    [filter]="comandos().length > 8"
                    filterBy="rotulo"
                    [disabled]="disabled() || comandos().length === 0"
                    [emptyMessage]="semComandos()"
                    styleClass="w-full"
                    appendTo="body"
                />
                @if (comandos().length === 0) {
                    <small class="text-surface-500">{{ semComandos() }}</small>
                }
            </div>

            @switch (tipo()) {
                @case ('RELE_DEFINIR') {
                    <div class="min-w-0">
                        <label [for]="id() + '-canal'" class="block font-medium mb-2">Relé</label>
                        @if (reles().length) {
                            <p-select [inputId]="id() + '-canal'" [options]="opcoesReleTodos()" optionLabel="label" optionValue="value" [ngModel]="p().canal ?? null" (ngModelChange)="set('canal', $event)" styleClass="w-full" appendTo="body" />
                        } @else {
                            <p-inputnumber [inputId]="id() + '-canal'" [ngModel]="p().canal" (ngModelChange)="set('canal', $event)" [min]="0" [max]="15" placeholder="todos" styleClass="w-full" inputStyleClass="w-full" />
                            <small class="text-surface-500">Canal de 0 a 15; em branco, todos.</small>
                        }
                    </div>
                    <div class="min-w-0">
                        <label [for]="id() + '-acao'" class="block font-medium mb-2">Ação <span class="text-red-500">*</span></label>
                        <p-select [inputId]="id() + '-acao'" [options]="acoes" optionLabel="label" optionValue="value" [ngModel]="p().acao" (ngModelChange)="set('acao', $event)" styleClass="w-full" appendTo="body" />
                    </div>
                }
                @case ('RELE_PULSO') {
                    <div class="min-w-0">
                        <label [for]="id() + '-canal'" class="block font-medium mb-2">Relé <span class="text-red-500">*</span></label>
                        @if (reles().length) {
                            <p-select [inputId]="id() + '-canal'" [options]="opcoesRele()" optionLabel="label" optionValue="value" [ngModel]="p().canal" (ngModelChange)="set('canal', $event)" styleClass="w-full" appendTo="body" />
                        } @else {
                            <p-inputnumber [inputId]="id() + '-canal'" [ngModel]="p().canal" (ngModelChange)="set('canal', $event)" [min]="0" [max]="15" styleClass="w-full" inputStyleClass="w-full" />
                        }
                    </div>
                    <div class="min-w-0">
                        <label [for]="id() + '-dur'" class="block font-medium mb-2">Duração <span class="text-red-500">*</span></label>
                        <p-inputnumber [inputId]="id() + '-dur'" [ngModel]="segundosPulso()" (ngModelChange)="set('duracaoMs', $event === null ? null : Math.round($event * 1000))" [min]="0.1" [max]="86400" [minFractionDigits]="0" [maxFractionDigits]="1" locale="pt-BR" suffix=" s" styleClass="w-full" inputStyleClass="w-full" />
                        <small class="text-surface-500">De 0,1 s a 24 h; depois volta ao estado anterior.</small>
                    </div>
                    <label class="sm:col-span-2 flex items-center gap-3 cursor-pointer">
                        <p-toggleswitch [ngModel]="p().estado ?? true" (ngModelChange)="set('estado', $event)" />
                        <span>Durante o pulso o relé fica <b>{{ (p().estado ?? true) ? 'ligado' : 'desligado' }}</b></span>
                    </label>
                }
                @case ('RELE_MASCARA') {
                    <div class="sm:col-span-2 min-w-0">
                        <span class="block font-medium mb-2">Relés afetados</span>
                        <div class="flex flex-col gap-2">
                            @for (r of canaisMascara(); track r.canal) {
                                <div class="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-surface-200 dark:border-surface-700 px-3 py-2">
                                    <label class="flex items-center gap-2 cursor-pointer min-w-0">
                                        <p-checkbox [binary]="true" [ngModel]="bit(p().mascara, r.canal)" (ngModelChange)="setBit('mascara', r.canal, $event)" />
                                        <span class="break-words">{{ r.label }}</span>
                                    </label>
                                    @if (bit(p().mascara, r.canal)) {
                                        <label class="flex items-center gap-2 cursor-pointer">
                                            <p-toggleswitch [ngModel]="bit(p().valores, r.canal)" (ngModelChange)="setBit('valores', r.canal, $event)" />
                                            <span class="text-sm">{{ bit(p().valores, r.canal) ? 'Ligar' : 'Desligar' }}</span>
                                        </label>
                                    }
                                </div>
                            }
                        </div>
                    </div>
                }
                @case ('PZEM_LER') {
                    <ng-container *ngTemplateOutlet="medidor" />
                }
                @case ('PZEM_ZERAR_ENERGIA') {
                    <div class="min-w-0 sm:col-span-2">
                        <label [for]="id() + '-pzem'" class="block font-medium mb-2">Medidor <span class="text-red-500">*</span></label>
                        <ng-container *ngTemplateOutlet="campoMedidor" />
                        <small class="text-surface-500">Zera o contador de energia (kWh) do medidor. Não há como desfazer.</small>
                    </div>
                }
                @case ('PZEM_LIMITE_POTENCIA') {
                    <div class="min-w-0">
                        <label [for]="id() + '-pzem'" class="block font-medium mb-2">Medidor <span class="text-red-500">*</span></label>
                        <ng-container *ngTemplateOutlet="campoMedidor" />
                    </div>
                    <div class="min-w-0">
                        <label [for]="id() + '-watts'" class="block font-medium mb-2">Limite <span class="text-red-500">*</span></label>
                        <p-inputnumber [inputId]="id() + '-watts'" [ngModel]="p().watts" (ngModelChange)="set('watts', $event)" [min]="0" [max]="23000" suffix=" W" locale="pt-BR" styleClass="w-full" inputStyleClass="w-full" />
                        <small class="text-surface-500">De 0 a 23.000 W (alarme do medidor).</small>
                    </div>
                }
                @case ('INTERVALO') {
                    <div class="min-w-0">
                        <label [for]="id() + '-seg'" class="block font-medium mb-2">Intervalo de envio <span class="text-red-500">*</span></label>
                        <p-inputnumber [inputId]="id() + '-seg'" [ngModel]="p().segundos" (ngModelChange)="set('segundos', $event)" [min]="5" [max]="86400" suffix=" s" locale="pt-BR" styleClass="w-full" inputStyleClass="w-full" />
                        <small class="text-surface-500">De 5 s a 24 h.</small>
                    </div>
                }
                @case ('CONFIRMADO') {
                    <label class="sm:col-span-2 flex items-center gap-3 cursor-pointer">
                        <p-toggleswitch [ngModel]="p().confirmado ?? false" (ngModelChange)="set('confirmado', $event)" />
                        <span>Uplinks <b>{{ p().confirmado ? 'confirmados' : 'não confirmados' }}</b></span>
                    </label>
                }
                @case ('CLASSE') {
                    <div class="min-w-0">
                        <label [for]="id() + '-classe'" class="block font-medium mb-2">Classe <span class="text-red-500">*</span></label>
                        <p-select [inputId]="id() + '-classe'" [options]="classes" optionLabel="label" optionValue="value" [ngModel]="p().classe" (ngModelChange)="set('classe', $event)" styleClass="w-full" appendTo="body" />
                    </div>
                }
                @case ('REINICIAR') {
                    <div class="min-w-0">
                        <label [for]="id() + '-atraso'" class="block font-medium mb-2">Atraso</label>
                        <p-inputnumber [inputId]="id() + '-atraso'" [ngModel]="p().atrasoMs" (ngModelChange)="set('atrasoMs', $event)" [min]="0" [max]="60000" suffix=" ms" locale="pt-BR" styleClass="w-full" inputStyleClass="w-full" />
                        <small class="text-surface-500">De 0 a 60.000 ms.</small>
                    </div>
                }
                @case ('BRUTO') {
                    <div class="min-w-0">
                        <label [for]="id() + '-porta'" class="block font-medium mb-2">Porta (FPort) <span class="text-red-500">*</span></label>
                        <p-inputnumber [inputId]="id() + '-porta'" [ngModel]="p().porta" (ngModelChange)="set('porta', $event)" [min]="1" [max]="223" styleClass="w-full" inputStyleClass="w-full" />
                    </div>
                    <label class="flex items-center gap-3 cursor-pointer sm:mt-8">
                        <p-toggleswitch [ngModel]="p().confirmado ?? false" (ngModelChange)="set('confirmado', $event)" />
                        <span>Downlink confirmado</span>
                    </label>
                    <div class="min-w-0 sm:col-span-2">
                        <label [for]="id() + '-hex'" class="block font-medium mb-2">Bytes (hexadecimal) <span class="text-red-500">*</span></label>
                        <input pInputText [id]="id() + '-hex'" class="w-full font-mono" maxlength="484" placeholder="Ex.: 01 20 01 01" [ngModel]="p().dadosHex" (ngModelChange)="set('dadosHex', $event)" autocomplete="off" spellcheck="false" />
                    </div>
                }
                @case (null) {}
                @default {
                    <p class="sm:col-span-2 m-0 text-sm text-surface-500">Este comando não tem parâmetros.</p>
                }
            }
        </div>

        <ng-template #medidor>
            <div class="min-w-0 sm:col-span-2">
                <label [for]="id() + '-pzem'" class="block font-medium mb-2">Medidor <span class="text-red-500">*</span></label>
                <ng-container *ngTemplateOutlet="campoMedidor" />
            </div>
        </ng-template>
        <ng-template #campoMedidor>
            @if (medidores().length) {
                <p-select [inputId]="id() + '-pzem'" [options]="opcoesMedidor()" optionLabel="label" optionValue="value" [ngModel]="p().canal" (ngModelChange)="set('canal', $event)" styleClass="w-full" appendTo="body" />
            } @else {
                <p-inputnumber [inputId]="id() + '-pzem'" [ngModel]="p().canal" (ngModelChange)="set('canal', $event)" [min]="0" [max]="15" styleClass="w-full" inputStyleClass="w-full" />
            }
        </ng-template>
    `
})
export class ComandoForm {
    comandos = input<ComandoSuportado[]>([]);
    perifericos = input<PerifericoOpcao[]>([]);
    tipo = model<TipoComando | null>(null);
    parametros = model<ParametrosComando | null>({});
    id = input('cmd');
    disabled = input(false);
    semComandos = input('Nenhum comando disponível.');

    protected Math = Math;

    acoes: { label: string; value: AcaoRele }[] = [
        { label: 'Ligar', value: 'LIGAR' },
        { label: 'Desligar', value: 'DESLIGAR' },
        { label: 'Alternar', value: 'ALTERNAR' }
    ];
    classes = [
        { label: 'Classe A (economia de energia)', value: 'A' },
        { label: 'Classe C (sempre ouvindo)', value: 'C' }
    ];

    reles = computed(() => this.perifericos().filter((p) => p.tipo === 'RELE'));
    medidores = computed(() => this.perifericos().filter((p) => p.tipo === 'PZEM004T'));
    opcoesRele = computed(() => this.reles().map((r) => ({ label: rotulo(r), value: r.canal })));
    opcoesReleTodos = computed(() => [{ label: 'Todos os relés', value: null as number | null }, ...this.opcoesRele()]);
    opcoesMedidor = computed(() => this.medidores().map((r) => ({ label: rotulo(r), value: r.canal })));
    canaisMascara = computed(() => {
        const r = this.reles();

        return r.length ? r.map((x) => ({ canal: x.canal, label: rotulo(x) })) : Array.from({ length: 8 }, (_, i) => ({ canal: i, label: `Canal ${i}` }));
    });
    segundosPulso = computed(() => {
        const ms = this.p().duracaoMs;

        return ms === null || ms === undefined ? null : ms / 1000;
    });

    p(): ParametrosComando {
        return this.parametros() ?? {};
    }

    mudarTipo(t: TipoComando | null): void {
        this.tipo.set(t);
        this.parametros.set(parametrosPadrao(t, this.perifericos()));
    }

    set<K extends keyof ParametrosComando>(k: K, v: ParametrosComando[K]): void {
        this.parametros.set({ ...this.p(), [k]: v });
    }

    bit(mascara: number | null | undefined, canal: number): boolean {
        return ((mascara ?? 0) & (1 << canal)) !== 0;
    }

    setBit(campo: 'mascara' | 'valores', canal: number, ligado: boolean): void {
        const atual = this.p()[campo] ?? 0;
        const novo = ligado ? atual | (1 << canal) : atual & ~(1 << canal);

        this.set(campo, novo);
    }
}

function rotulo(p: PerifericoOpcao): string {
    return `${p.canal} · ${p.nome}${p.critico ? ' (crítico)' : ''}`;
}
