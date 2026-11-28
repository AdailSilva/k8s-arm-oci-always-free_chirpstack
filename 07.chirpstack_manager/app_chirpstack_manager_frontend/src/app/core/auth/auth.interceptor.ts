import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService, motivoDoErro, ROTA_TROCA_SENHA, sessaoEncerradaPeloServidor, trocaSenhaPendente } from './auth.service';

/**
 * Rotas sem access token. O logout vai sem ele de propósito: o token pode já ter vencido e o
 * servidor identifica a sessão pelo cookie de renovação.
 */
const SEM_TOKEN = ['/auth/login', '/auth/refresh', '/auth/logout', '/auth/senha/esqueci', '/auth/senha/redefinir', '/auth/email/confirmar', '/auth/email/reenviar'];

function comToken(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
    return token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
}

/**
 * Anexa o access token e, se a API responder 401, renova uma vez e repete a requisição.
 * Se o 401 disser que a sessão foi ENCERRADA (pelo administrador, inatividade, restauração...),
 * não tenta renovar: leva direto ao login com o motivo.
 * Se o 403 disser que a senha inicial ainda não foi trocada, leva à tela de troca de senha.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!req.url.startsWith('/api/') || SEM_TOKEN.some((p) => req.url.endsWith(p))) {
        return next(req);
    }
    return next(comToken(req, auth.accessToken())).pipe(
        catchError((err: unknown) => {
            if (sessaoEncerradaPeloServidor(err)) {
                auth.sessaoEncerrada(motivoDoErro(err));
                return throwError(() => err);
            }
            if (trocaSenhaPendente(err)) {
                auth.trocaSenhaObrigatoria.set(true);
                if (router.url.split(/[?#]/)[0] !== ROTA_TROCA_SENHA) {
                    router.navigateByUrl(ROTA_TROCA_SENHA);
                }
                return throwError(() => err);
            }
            if (err instanceof HttpErrorResponse && err.status === 401) {
                return auth.renovar().pipe(
                    switchMap((token) => next(comToken(req, token))),
                    catchError((e) => {
                        if (sessaoEncerradaPeloServidor(e)) {
                            auth.sessaoEncerrada(motivoDoErro(e));
                        } else {
                            auth.sessaoExpirada();
                        }
                        return throwError(() => e);
                    })
                );
            }
            return throwError(() => err);
        })
    );
};
