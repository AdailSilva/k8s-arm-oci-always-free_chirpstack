import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MessageModule } from 'primeng/message';
import { AuthService } from '@/app/core/auth/auth.service';
import { NotifyService } from '@/app/core/notificacao/notify.service';
import { LandingPassword } from '@/app/layout/components/shared/landingpassword';

/**
 * TELA BLOQUEADA, mesmo padrão dos demais sistemas.
 *
 * "Bloquear tela" no menu do usuário esconde o sistema sem encerrar a sessão (presença, horário de
 * acesso e logoff por inatividade continuam valendo). Toda rota do sistema traz o usuário para cá
 * até a senha ser digitada; ela é conferida no servidor, e 5 erros seguidos encerram a sessão.
 * Vale para todas as abas abertas. "Entrar com outra conta" encerra a sessão.
 */
@Component({
    selector: 'app-tela-bloqueada',
    standalone: true,
    imports: [FormsModule, MessageModule, LandingPassword],
    template: `
        <div>
            <p class="text-center text-xs font-semibold uppercase tracking-[0.16em] text-primary mb-3 whitespace-nowrap"><i class="pi pi-lock mr-1 text-xs"></i>Tela bloqueada</p>
            <h1 class="text-4xl md:text-5xl font-medium text-center mx-auto leading-[125%]">Bem-vindo de volta</h1>
            <p class="text-lg text-surface-500 leading-6 mt-6 text-center mx-auto max-w-[35rem]">Digite sua senha para continuar de onde parou.</p>

            <form class="max-w-[35rem] mx-auto" (ngSubmit)="desbloquear()">
                <div class="space-y-4 mt-10">
                    <div class="flex items-center gap-4 p-4 rounded-2xl border border-surface-200 dark:border-surface-700 bg-surface-0 dark:bg-surface-900">
                        <span class="w-14 h-14 shrink-0 rounded-2xl overflow-hidden bg-primary text-primary-contrast flex items-center justify-center text-xl font-semibold">
                            @if (auth.usuario()?.cadastro?.fotoPerfil) {
                                <img [src]="auth.usuario()?.cadastro?.fotoPerfil" alt="" class="w-full h-full object-cover" />
                            } @else {
                                {{ inicial() }}
                            }
                        </span>
                        <div class="min-w-0">
                            <div class="font-medium [overflow-wrap:anywhere]">{{ auth.usuario()?.nome }}</div>
                            <div class="text-sm text-surface-500 [overflow-wrap:anywhere]">{{ auth.usuario()?.email }}</div>
                        </div>
                    </div>
                    @if (erro()) {
                        <p-message severity="error" [text]="erro()" styleClass="w-full" />
                    }
                    <landing-password placeholder="Sua senha de acesso" [(value)]="senha" />
                </div>
                <div class="space-y-4 mt-8">
                    <button type="submit" class="landing-button-primary w-full disabled:opacity-60" [disabled]="!senha() || carregando()">
                        @if (carregando()) {
                            <i class="pi pi-spin pi-spinner mr-2"></i>Conferindo...
                        } @else {
                            <i class="pi pi-lock-open mr-2"></i>Desbloquear
                        }
                    </button>
                    <button type="button" class="w-full text-surface-500 hover:text-surface-900 dark:hover:text-surface-0 transition-colors" (click)="outraConta()">
                        Não é você? <span class="text-primary font-medium">Entrar com outra conta</span>
                    </button>
                </div>
            </form>
        </div>
    `
})
export class TelaBloqueada {
    readonly auth = inject(AuthService);
    private router = inject(Router);

    readonly senha = signal('');
    readonly carregando = signal(false);
    readonly erro = signal('');
    readonly inicial = computed(() => (this.auth.usuario()?.nome || this.auth.usuario()?.email || '?').charAt(0).toUpperCase());

    desbloquear(): void {
        if (!this.senha() || this.carregando()) {
            return;
        }
        this.erro.set('');
        this.carregando.set(true);
        this.auth.desbloquear(this.senha()).subscribe({
            next: () => {
                this.carregando.set(false);
                this.router.navigateByUrl(this.auth.voltarDoBloqueio());
            },
            error: (e) => {
                this.carregando.set(false);
                this.senha.set('');
                // 401 = sessão encerrada (5 erros, administrador, horário...): o interceptor leva ao login.
                if (e?.status !== 401) {
                    this.erro.set(NotifyService.mensagem(e));
                }
            }
        });
    }

    outraConta(): void {
        this.auth.logout();
    }
}
