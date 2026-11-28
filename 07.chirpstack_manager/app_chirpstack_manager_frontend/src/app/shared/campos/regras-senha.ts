import { Component, computed, input } from '@angular/core';

/** Regra de senha forte da API: 8 a 72 caracteres, com letras e números. */
export const SENHA_FORTE = /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/;

interface Regra {
    rotulo: string;
    ok: boolean;
    /** Ocupa a linha inteira no layout de duas colunas (texto mais longo). */
    largo?: boolean;
}

/**
 * Checklist visual da política de senha, atualizado enquanto o usuário digita (mesmo componente
 * dos BackOffices). Nenhum item quebra linha: "Confirmação igual à nova senha" ocupa a linha inteira.
 */
@Component({
    selector: 'regras-senha',
    standalone: true,
    template: `
        <ul class="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-1.5 text-sm m-0 p-0 list-none">
            @for (r of regras(); track r.rotulo) {
                <li class="flex items-center gap-2 whitespace-nowrap" [class]="(r.ok ? 'text-green-600 dark:text-green-400' : 'text-surface-500') + (r.largo ? ' sm:col-span-2' : '')">
                    <i class="pi text-xs" [class]="r.ok ? 'pi-check-circle' : 'pi-circle'"></i>
                    <span>{{ r.rotulo }}</span>
                </li>
            }
        </ul>
    `
})
export class RegrasSenha {
    senha = input<string>('');
    confirmacao = input<string | undefined>(undefined);

    readonly regras = computed<Regra[]>(() => {
        const s = this.senha() ?? '';
        const c = this.confirmacao();
        const lista: Regra[] = [
            { rotulo: 'De 8 a 72 caracteres', ok: s.length >= 8 && s.length <= 72 },
            { rotulo: 'Contém letras', ok: /[A-Za-z]/.test(s) },
            { rotulo: 'Contém números', ok: /\d/.test(s) }
        ];
        if (c !== undefined) {
            lista.push({ rotulo: 'Confirmação igual à nova senha', ok: !!s && s === c, largo: true });
        }
        return lista;
    });
}
