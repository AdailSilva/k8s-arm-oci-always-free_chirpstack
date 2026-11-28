import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MessageModule } from 'primeng/message';
import { SistemaService } from '@/app/core/http/sistema.service';
import { NotifyService } from '@/app/core/notificacao/notify.service';
import { LandingPassword } from '@/app/layout/components/shared/landingpassword';
import { RegrasSenha, SENHA_FORTE } from '@/app/shared/campos/regras-senha';

/**
 * Nova senha pelo link recebido por e-mail ({@code ?token=...}): "Esqueci a senha" ou convite
 * ({@code ?convite=1}), em que o usuário define a primeira senha (o link também confirma o e-mail).
 */
@Component({
    selector: 'app-nova-senha',
    standalone: true,
    imports: [FormsModule, RouterModule, MessageModule, LandingPassword, RegrasSenha],
    template: `
        <div>
            @if (convite) {
                <p class="text-center text-xs font-semibold uppercase tracking-[0.16em] text-primary mb-3 whitespace-nowrap"><i class="pi pi-envelope mr-1 text-xs"></i>Convite de acesso</p>
            }
            <h1 class="text-4xl md:text-5xl font-medium text-center mx-auto leading-[125%]">{{ convite ? 'Defina sua senha' : 'Criar nova senha' }}</h1>
            <p class="text-lg text-surface-500 leading-6 mt-6 text-center mx-auto max-w-[35rem]">{{ convite ? 'Bem-vindo(a)! Escolha a senha do seu primeiro acesso.' : 'Escolha uma nova senha para a sua conta.' }}</p>
            <form class="max-w-[35rem] mx-auto" (ngSubmit)="salvar()">
                <div class="space-y-4 mt-12">
                    @if (!token) {
                        <p-message severity="error" styleClass="w-full" [text]="convite ? 'Convite incompleto. Peça ao administrador para reenviar.' : 'Link incompleto. Peça um novo em &quot;Esqueci a senha&quot;.'" />
                    }
                    @if (erro()) {
                        <p-message severity="error" [text]="erro()" styleClass="w-full" />
                    }
                    <landing-password placeholder="Nova senha" [(value)]="senha" />
                    <landing-password placeholder="Confirme a nova senha" [(value)]="confirmacao" />
                    <div class="px-4 pt-1">
                        <regras-senha [senha]="senha()" [confirmacao]="confirmacao()" />
                    </div>
                </div>
                <div class="space-y-6 mt-8">
                    <button type="submit" class="landing-button-primary w-full disabled:opacity-60" [disabled]="carregando() || !token">
                        @if (carregando()) {
                            <i class="pi pi-spin pi-spinner mr-2"></i>Salvando...
                        } @else {
                            {{ convite ? 'Definir senha e entrar' : 'Salvar nova senha' }}
                        }
                    </button>
                    @if (!convite) {
                        <div class="text-center text-sm"><a routerLink="/auth/esqueci-senha" class="text-primary font-medium">Pedir um novo link</a></div>
                    } @else {
                        <div class="text-center text-sm"><a routerLink="/auth/login" class="text-primary font-medium">Voltar ao login</a></div>
                    }
                </div>
            </form>
        </div>
    `
})
export class NovaSenha implements OnInit {
    private api = inject(SistemaService);
    private notify = inject(NotifyService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);

    token = '';
    convite = false;
    senha = signal('');
    confirmacao = signal('');
    carregando = signal(false);
    erro = signal('');

    ngOnInit(): void {
        this.token = this.route.snapshot.queryParamMap.get('token') ?? '';
        this.convite = this.route.snapshot.queryParamMap.get('convite') === '1';
    }

    salvar(): void {
        this.erro.set('');
        if (!SENHA_FORTE.test(this.senha())) {
            this.erro.set('A senha deve ter de 8 a 72 caracteres, com letras e números.');
            return;
        }
        if (this.senha() !== this.confirmacao()) {
            this.erro.set('A confirmação não confere com a nova senha.');
            return;
        }
        this.carregando.set(true);
        this.api.redefinirSenha(this.token, this.senha()).subscribe({
            next: () => {
                this.notify.sucesso(this.convite ? 'Senha definida. Entre com o seu e-mail e a nova senha.' : 'Senha alterada. Entre com a nova senha.');
                this.router.navigateByUrl('/auth/login');
            },
            error: (e) => {
                this.carregando.set(false);
                this.erro.set(NotifyService.mensagem(e));
            }
        });
    }
}
