import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MessageModule } from 'primeng/message';
import { SistemaService } from '@/app/core/http/sistema.service';
import { NotifyService } from '@/app/core/notificacao/notify.service';
import { LandingInput } from '@/app/layout/components/shared/landinginput';
import { emailValido, MSG_EMAIL_INVALIDO } from '@/app/core/format/validacao';

/** "Esqueci a senha": envia o link de redefinição. A resposta é a mesma exista ou não o e-mail. */
@Component({
    selector: 'app-esqueci-senha',
    standalone: true,
    imports: [FormsModule, RouterModule, MessageModule, LandingInput],
    template: `
        <div>
            <h1 class="text-4xl md:text-5xl font-medium text-center mx-auto leading-[125%]">Esqueceu a senha?</h1>
            <p class="text-lg text-surface-500 leading-6 mt-6 text-center mx-auto max-w-[35rem]">Informe o e-mail da sua conta. Enviaremos um link para criar uma nova senha.</p>
            <form class="max-w-[35rem] mx-auto" (ngSubmit)="enviar()">
                <div class="space-y-4 mt-12">
                    @if (enviado()) {
                        <p-message severity="success" styleClass="w-full" text="Se o e-mail estiver cadastrado, o link chega em alguns minutos. Confira também a caixa de spam." />
                    }
                    @if (erro()) {
                        <p-message severity="error" [text]="erro()" styleClass="w-full" />
                    }
                    <landing-input type="email" placeholder="E-mail" [(value)]="email" />
                </div>
                <div class="space-y-6 mt-8">
                    <button type="submit" class="landing-button-primary w-full disabled:opacity-60" [disabled]="carregando()">
                        @if (carregando()) {
                            <i class="pi pi-spin pi-spinner mr-2"></i>Enviando...
                        } @else {
                            Enviar link
                        }
                    </button>
                    <div class="text-center text-sm"><a routerLink="/auth/login" class="text-primary font-medium"><i class="pi pi-arrow-left mr-1"></i>Voltar para o login</a></div>
                </div>
            </form>
        </div>
    `
})
export class EsqueciSenha {
    private api = inject(SistemaService);

    email = signal('');
    carregando = signal(false);
    enviado = signal(false);
    erro = signal('');

    enviar(): void {
        this.erro.set('');
        const email = this.email().trim();
        if (!emailValido(email)) {
            this.erro.set(MSG_EMAIL_INVALIDO);
            return;
        }
        this.carregando.set(true);
        this.api.esqueciSenha(email).subscribe({
            next: () => {
                this.carregando.set(false);
                this.enviado.set(true);
            },
            error: (e) => {
                this.carregando.set(false);
                this.erro.set(NotifyService.mensagem(e));
            }
        });
    }
}
