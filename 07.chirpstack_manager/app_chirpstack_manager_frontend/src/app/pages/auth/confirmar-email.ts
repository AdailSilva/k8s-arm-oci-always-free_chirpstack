import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { SistemaService } from '@/app/core/http/sistema.service';
import { NotifyService } from '@/app/core/notificacao/notify.service';

/**
 * Confirmação de e-mail pelo link recebido ({@code /auth/confirmar-email?token=...}), como nos
 * demais sistemas. Confirma ao abrir e mostra o resultado; o link vale uma vez.
 */
@Component({
    selector: 'app-confirmar-email',
    standalone: true,
    imports: [RouterModule],
    template: `
        <div class="text-center">
            @switch (estado()) {
                @case ('carregando') {
                    <i class="pi pi-spin pi-spinner text-5xl! text-primary"></i>
                    <h1 class="text-4xl md:text-5xl font-medium mx-auto leading-[125%] mt-6">Confirmando e-mail</h1>
                    <p class="text-lg text-surface-500 leading-6 mt-6 mx-auto max-w-[35rem]">Aguarde um instante.</p>
                }
                @case ('ok') {
                    <i class="pi pi-check-circle text-5xl! text-green-500"></i>
                    <h1 class="text-4xl md:text-5xl font-medium mx-auto leading-[125%] mt-6">E-mail confirmado</h1>
                    <p class="text-lg text-surface-500 leading-6 mt-6 mx-auto max-w-[35rem]">{{ mensagem() }}</p>
                }
                @case ('erro') {
                    <i class="pi pi-exclamation-triangle text-5xl! text-amber-500"></i>
                    <h1 class="text-4xl md:text-5xl font-medium mx-auto leading-[125%] mt-6">Link inválido</h1>
                    <p class="text-lg text-surface-500 leading-6 mt-6 mx-auto max-w-[35rem]">{{ mensagem() }}</p>
                }
            }
            @if (estado() !== 'carregando') {
                <div class="max-w-[35rem] mx-auto mt-10">
                    <a routerLink="/auth/login" class="landing-button-primary w-full inline-flex justify-center">Ir para o login</a>
                </div>
            }
        </div>
    `
})
export class ConfirmarEmail implements OnInit {
    private api = inject(SistemaService);
    private route = inject(ActivatedRoute);

    estado = signal<'carregando' | 'ok' | 'erro'>('carregando');
    mensagem = signal('');

    ngOnInit(): void {
        const token = this.route.snapshot.queryParamMap.get('token') ?? '';
        if (!token) {
            this.estado.set('erro');
            this.mensagem.set('Link incompleto. Peça um novo na tela de login, em "Reenviar confirmação".');
            return;
        }
        this.api.confirmarEmail(token).subscribe({
            next: (r) => {
                this.estado.set('ok');
                this.mensagem.set(r.mensagem);
            },
            error: (e) => {
                this.estado.set('erro');
                this.mensagem.set(NotifyService.mensagem(e));
            }
        });
    }
}
