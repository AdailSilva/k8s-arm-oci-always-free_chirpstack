import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MessageModule } from 'primeng/message';
import { AuthService } from '@/app/core/auth/auth.service';
import { NotifyService } from '@/app/core/notificacao/notify.service';
import { LandingPassword } from '@/app/layout/components/shared/landingpassword';
import { RegrasSenha, SENHA_FORTE } from '@/app/shared/campos/regras-senha';

/**
 * Troca de senha obrigatória no primeiro acesso (senha inicial), como nos demais sistemas.
 * Enquanto não concluir, a API recusa as outras rotas com 403 TROCA_SENHA_OBRIGATORIA e o
 * trocaSenhaGuard traz o usuário de volta para cá. Depois da troca a API encerra as sessões:
 * o usuário volta ao login e entra com a nova senha.
 */
@Component({
    selector: 'app-trocar-senha',
    standalone: true,
    imports: [FormsModule, MessageModule, LandingPassword, RegrasSenha],
    template: `
        <div>
            <p class="text-center text-xs font-semibold uppercase tracking-[0.16em] text-primary mb-3 whitespace-nowrap"><i class="pi pi-lock mr-1 text-xs"></i>Troca de senha obrigatória</p>
            <h1 class="text-4xl md:text-5xl font-medium text-center mx-auto leading-[125%]">Defina sua senha</h1>
            <p class="text-lg text-surface-500 leading-6 mt-6 text-center mx-auto max-w-[35rem]">Olá, {{ primeiroNome() }}. Por segurança, troque a senha inicial antes de continuar.</p>

            <form class="max-w-[35rem] mx-auto" (ngSubmit)="salvar()">
                <div class="space-y-4 mt-12">
                    @if (erro()) {
                        <p-message severity="error" [text]="erro()" styleClass="w-full" />
                    }
                    <landing-password placeholder="Senha atual (inicial)" [(value)]="senhaAtual" />
                    <landing-password placeholder="Nova senha" [(value)]="novaSenha" />
                    <landing-password placeholder="Confirme a nova senha" [(value)]="confirmacao" />
                    <div class="px-4 pt-1">
                        <regras-senha [senha]="novaSenha()" [confirmacao]="confirmacao()" />
                    </div>
                </div>
                <div class="space-y-4 mt-8">
                    <button type="submit" class="landing-button-primary w-full disabled:opacity-60" [disabled]="!podeSalvar() || carregando()">
                        @if (carregando()) {
                            <i class="pi pi-spin pi-spinner mr-2"></i>Salvando...
                        } @else {
                            Salvar e continuar
                        }
                    </button>
                    <button type="button" class="w-full text-surface-500 hover:text-surface-900 dark:hover:text-surface-0 transition-colors" (click)="sair()">
                        <i class="pi pi-sign-out mr-2"></i>Sair
                    </button>
                </div>
            </form>
        </div>
    `
})
export class TrocarSenha {
    private auth = inject(AuthService);
    private notify = inject(NotifyService);
    private router = inject(Router);

    readonly senhaAtual = signal('');
    readonly novaSenha = signal('');
    readonly confirmacao = signal('');
    readonly carregando = signal(false);
    readonly erro = signal('');

    readonly primeiroNome = computed(() => this.auth.usuario()?.nome?.trim().split(/\s+/)[0] ?? '');
    readonly podeSalvar = computed(
        () => !!this.senhaAtual() && SENHA_FORTE.test(this.novaSenha()) && this.novaSenha() === this.confirmacao()
    );

    salvar(): void {
        if (!this.podeSalvar() || this.carregando()) return;
        this.erro.set('');
        this.carregando.set(true);
        this.auth.trocarSenha(this.senhaAtual(), this.novaSenha()).subscribe({
            next: () => {
                this.carregando.set(false);
                this.notify.sucesso('Senha alterada. Entre novamente com a nova senha.');
                // A API encerrou as sessões. sessaoEncerrada() limpa o estado, mas não navega a partir
                // de rotas /auth/ — por isso o login é aberto aqui, com o motivo.
                this.auth.sessaoEncerrada('SENHA_ALTERADA');
                this.router.navigate(['/auth/login'], { queryParams: { motivo: 'SENHA_ALTERADA' } });
            },
            error: (e) => {
                this.carregando.set(false);
                this.erro.set(NotifyService.mensagem(e));
            }
        });
    }

    sair(): void {
        this.auth.logout();
    }
}
