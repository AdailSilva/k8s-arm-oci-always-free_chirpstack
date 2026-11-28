import { Component, input, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { PoliticaRequest } from '@/app/core/http/atuacao.models';
import { DiasSemana } from './dias-semana';

/** Valida a política; devolve a mensagem do primeiro problema ou null. */
export function validarPolitica(p: PoliticaRequest | null | undefined): string | null {
    if (!p) {
        return null;
    }

    if (!!p.janelaInicio !== !!p.janelaFim) {
        return 'Informe o início e o fim da janela de horário (ou nenhum dos dois).';
    }

    return null;
}

/** Política sem os campos vazios (null = tudo padrão do servidor). */
export function politicaLimpa(p: PoliticaRequest | null | undefined): PoliticaRequest | null {
    if (!p) {
        return null;
    }
    const out: PoliticaRequest = {};
    let algum = false;

    for (const [k, v] of Object.entries(p)) {
        const vazio = v === null || v === undefined || v === '' || (Array.isArray(v) && v.length === 0);

        if (!vazio) {
            (out as Record<string, unknown>)[k] = v;
            algum = true;
        }
    }

    return algum ? out : null;
}

/**
 * Política de novas tentativas de um comando (recolhível). Campos vazios ficam com o padrão do servidor.
 */
@Component({
    selector: 'app-politica-form',
    standalone: true,
    imports: [FormsModule, ButtonModule, InputNumberModule, DiasSemana],
    template: `
        <div class="border border-surface-200 dark:border-surface-700 rounded-xl">
            <button type="button" class="w-full flex items-center justify-between gap-3 px-4 py-3 bg-transparent border-0 cursor-pointer text-left text-color" [attr.aria-expanded]="aberto()" (click)="aberto.set(!aberto())">
                <span class="min-w-0">
                    <span class="font-medium block">Política de novas tentativas</span>
                    <span class="text-sm text-surface-500 block">Opcional. Em branco, vale o padrão do servidor.</span>
                </span>
                <i class="pi shrink-0" [class.pi-chevron-down]="!aberto()" [class.pi-chevron-up]="aberto()"></i>
            </button>
            @if (aberto()) {
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-4 px-4 pb-4 pt-1">
                    <div class="min-w-0">
                        <label [for]="id() + '-max'" class="block font-medium mb-2 text-sm">Máximo de tentativas</label>
                        <p-inputnumber [inputId]="id() + '-max'" [ngModel]="v().maxTentativas" (ngModelChange)="set('maxTentativas', $event)" [min]="1" [max]="50" placeholder="padrão" styleClass="w-full" inputStyleClass="w-full" />
                        <small class="text-surface-500">De 1 a 50.</small>
                    </div>
                    <div class="min-w-0">
                        <label [for]="id() + '-int'" class="block font-medium mb-2 text-sm">Intervalo entre tentativas</label>
                        <p-inputnumber [inputId]="id() + '-int'" [ngModel]="v().intervaloTentativaS" (ngModelChange)="set('intervaloTentativaS', $event)" [min]="10" [max]="86400" suffix=" s" placeholder="padrão" styleClass="w-full" inputStyleClass="w-full" />
                        <small class="text-surface-500">De 10 s a 24 h.</small>
                    </div>
                    <div class="min-w-0">
                        <label [for]="id() + '-rec'" class="block font-medium mb-2 text-sm">Fator de recuo</label>
                        <p-inputnumber [inputId]="id() + '-rec'" [ngModel]="v().fatorRecuo" (ngModelChange)="set('fatorRecuo', $event)" [min]="1" [max]="4" [minFractionDigits]="0" [maxFractionDigits]="2" locale="pt-BR" prefix="× " placeholder="padrão" styleClass="w-full" inputStyleClass="w-full" />
                        <small class="text-surface-500">Multiplica o intervalo a cada falha (1 a 4).</small>
                    </div>
                    <div class="min-w-0">
                        <label [for]="id() + '-agu'" class="block font-medium mb-2 text-sm">Aguardar resposta</label>
                        <p-inputnumber [inputId]="id() + '-agu'" [ngModel]="v().aguardarRespostaS" (ngModelChange)="set('aguardarRespostaS', $event)" [min]="10" [max]="86400" suffix=" s" placeholder="padrão" styleClass="w-full" inputStyleClass="w-full" />
                        <small class="text-surface-500">Espera pela confirmação (10 s a 24 h).</small>
                    </div>
                    <div class="min-w-0 sm:col-span-2">
                        <label [for]="id() + '-val'" class="block font-medium mb-2 text-sm">Validade</label>
                        <p-inputnumber [inputId]="id() + '-val'" [ngModel]="v().validadeMin" (ngModelChange)="set('validadeMin', $event)" [min]="1" [max]="43200" suffix=" min" placeholder="padrão" styleClass="w-full sm:w-1/2" inputStyleClass="w-full" />
                        <small class="text-surface-500 block">Depois disso o comando expira sem ser executado (1 min a 30 dias).</small>
                    </div>
                    <div class="min-w-0">
                        <label [for]="id() + '-ji'" class="block font-medium mb-2 text-sm">Janela: início</label>
                        <input type="time" [id]="id() + '-ji'" class="p-inputtext p-component w-full" [ngModel]="hhmm(v().janelaInicio)" (ngModelChange)="set('janelaInicio', $event || null)" />
                    </div>
                    <div class="min-w-0">
                        <label [for]="id() + '-jf'" class="block font-medium mb-2 text-sm">Janela: fim</label>
                        <input type="time" [id]="id() + '-jf'" class="p-inputtext p-component w-full" [ngModel]="hhmm(v().janelaFim)" (ngModelChange)="set('janelaFim', $event || null)" />
                    </div>
                    <small class="text-surface-500 sm:col-span-2 -mt-2">Só transmite dentro da janela (ex.: 22:00 a 06:00). Em branco, a qualquer hora.</small>
                    <div class="min-w-0 sm:col-span-2">
                        <span class="block font-medium mb-2 text-sm">Dias da semana permitidos</span>
                        <app-dias-semana [value]="v().diasSemana ?? []" (valueChange)="set('diasSemana', $event)" />
                        <small class="text-surface-500 block mt-1">Nenhum marcado = todos os dias.</small>
                    </div>
                    <div class="sm:col-span-2 flex justify-end">
                        <p-button label="Limpar política" icon="pi pi-eraser" size="small" [text]="true" severity="secondary" (onClick)="value.set({})" />
                    </div>
                </div>
            }
        </div>
    `
})
export class PoliticaForm {
    value = model<PoliticaRequest | null>({});
    id = input('pol');
    aberto = signal(false);

    v(): PoliticaRequest {
        return this.value() ?? {};
    }

    set<K extends keyof PoliticaRequest>(k: K, valor: PoliticaRequest[K]): void {
        this.value.set({ ...this.v(), [k]: valor });
    }

    hhmm(h?: string | null): string {
        return h ? h.substring(0, 5) : '';
    }
}
