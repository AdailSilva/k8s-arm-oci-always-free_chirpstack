import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { PerfilAcesso } from '@/app/core/http/models';
import { AuthService, ROTA_TELA_BLOQUEADA, ROTA_TROCA_SENHA } from './auth.service';

/** Exige usuário autenticado. */
export const authGuard: CanActivateFn = (_route, state) => {
    const auth = inject(AuthService);
    if (auth.autenticado()) {
        return true;
    }
    return inject(Router).createUrlTree(['/auth/login'], { queryParams: { returnUrl: state.url } });
};

/** Exige um dos perfis em {@code data.perfis} da rota. */
export const perfilGuard: CanActivateFn = (route) => {
    const auth = inject(AuthService);
    const perfis = (route.data?.['perfis'] ?? []) as PerfilAcesso[];
    if (perfis.length === 0 || auth.hasRole(...perfis)) {
        return true;
    }
    return inject(Router).createUrlTree(['/auth/access-denied']);
};

/** Impede abrir o login já autenticado. */
export const anonimoGuard: CanActivateFn = () => {
    const auth = inject(AuthService);
    return auth.autenticado() ? inject(Router).createUrlTree(['/']) : true;
};

/** A tela de troca obrigatória só abre com a troca pendente; fora disso, volta ao painel. */
export const trocaSenhaPendenteGuard: CanActivateFn = (_route, state) => {
    const auth = inject(AuthService);
    if (!auth.autenticado()) {
        return inject(Router).createUrlTree(['/auth/login'], { queryParams: { returnUrl: state.url } });
    }
    return auth.trocaSenhaObrigatoria() ? true : inject(Router).createUrlTree(['/']);
};

/** Com a troca da senha inicial pendente, só a tela de troca de senha fica liberada. */
export const trocaSenhaGuard: CanActivateFn = (_route, state) => {
    const auth = inject(AuthService);
    if (!auth.trocaSenhaObrigatoria() || state.url.split(/[?#]/)[0] === ROTA_TROCA_SENHA) {
        return true;
    }
    return inject(Router).createUrlTree([ROTA_TROCA_SENHA]);
};

/** Tela bloqueada: toda rota do sistema leva à tela de desbloqueio (a sessão continua viva). */
export const telaBloqueadaGuard: CanActivateFn = (_route, state) => {
    const auth = inject(AuthService);
    if (!auth.telaBloqueada()) {
        return true;
    }
    return inject(Router).createUrlTree([ROTA_TELA_BLOQUEADA], { queryParams: { voltar: state.url } });
};

/** A tela de desbloqueio só abre com a tela bloqueada e a sessão viva. */
export const telaBloqueadaPendenteGuard: CanActivateFn = () => {
    const auth = inject(AuthService);
    if (!auth.autenticado()) {
        return inject(Router).createUrlTree(['/auth/login']);
    }
    return auth.telaBloqueada() ? true : inject(Router).createUrlTree(['/']);
};
