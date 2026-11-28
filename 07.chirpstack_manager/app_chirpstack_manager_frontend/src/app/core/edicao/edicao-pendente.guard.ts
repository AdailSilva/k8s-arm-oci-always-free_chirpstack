import { inject } from '@angular/core';
import { CanDeactivateFn, Routes } from '@angular/router';
import { EdicaoPendenteService } from '@/app/core/edicao/edicao-pendente.service';

/**
 * Sair de uma tela com formulário alterado e não salvo pergunta antes
 * ("Deseja realmente sair?" — mesmos textos do app mobile). Não depende do componente: quem
 * sabe se há edição pendente é o {@link EdicaoPendenteService}, alimentado pelas travas de
 * cada formulário (inclusive diálogos abertos). Por isso vale também para rotas sem
 * {@code component} (lazy {@code loadComponent} / {@code loadChildren}).
 */
export const edicaoPendenteGuard: CanDeactivateFn<unknown> = () => inject(EdicaoPendenteService).podeSair();

/**
 * Aplica o {@link edicaoPendenteGuard} a todas as rotas da lista (e às filhas), exceto os
 * redirecionamentos — assim nenhuma tela nova do layout logado fica sem a trava.
 */
export function comTravaEdicao(rotas: Routes): Routes {
    return rotas.map((rota) => {
        if (rota.redirectTo !== undefined) return rota;
        return {
            ...rota,
            canDeactivate: [...(rota.canDeactivate ?? []), edicaoPendenteGuard],
            ...(rota.children ? { children: comTravaEdicao(rota.children) } : {})
        };
    });
}
