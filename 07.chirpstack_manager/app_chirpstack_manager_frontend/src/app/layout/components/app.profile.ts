import { Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { RippleModule } from 'primeng/ripple';
import { AuthService } from '@/app/core/auth/auth.service';
import { EdicaoPendenteService } from '@/app/core/edicao/edicao-pendente.service';
import { LayoutService } from '@/app/layout/service/layout.service';
import { PERFIS_ACESSO, rotulo } from '@/app/core/format/options';

/**
 * Menu do usuário (topo direito), mesmo padrão dos demais sistemas: dados da conta, bloquear tela,
 * meu perfil, aparência, suporte, termos de uso e sair. Sair e bloquear com formulário alterado
 * perguntam antes ("Deseja realmente sair?").
 */
@Component({
    selector: '[app-profile]',
    standalone: true,
    imports: [CommonModule, RouterModule, RippleModule],
    template: `
        <div class="px-3 py-3 border-b border-surface-200 dark:border-surface-800 mb-1 flex items-center gap-3">
            <span class="w-11 h-11 shrink-0 rounded-xl overflow-hidden bg-primary text-primary-contrast flex items-center justify-center font-semibold">
                @if (auth.usuario()?.cadastro?.fotoPerfil) {
                    <img [src]="auth.usuario()?.cadastro?.fotoPerfil" alt="" class="w-full h-full object-cover" />
                } @else {
                    {{ inicial() }}
                }
            </span>
            <div class="min-w-0 flex-1">
                <div class="font-medium truncate" [title]="auth.usuario()?.nome ?? ''">{{ auth.usuario()?.nome }}</div>
                <div class="text-xs text-surface-500 truncate" [title]="auth.usuario()?.email ?? ''">{{ auth.usuario()?.email }}</div>
                <div class="text-xs text-primary font-medium">{{ perfil() }}</div>
            </div>
            <button type="button" pRipple class="w-9 h-9 shrink-0 rounded-lg flex items-center justify-center bg-transparent border border-surface-200 dark:border-surface-700 text-surface-500 hover:text-primary cursor-pointer" (click)="bloquear()" title="Bloquear tela" aria-label="Bloquear tela">
                <i class="pi pi-lock"></i>
            </button>
        </div>
        <a pRipple routerLink="/meu-perfil" [class]="buttonClass">
            <i class="pi pi-user"></i>
            <span>Meu perfil</span>
        </a>
        <button pRipple type="button" [class]="buttonClass" (click)="layout.toggleConfigSidebar()">
            <i class="pi pi-palette"></i>
            <span>Aparência</span>
        </button>
        <button pRipple type="button" [class]="buttonClass" (click)="bloquear()">
            <i class="pi pi-lock"></i>
            <span>Bloquear tela</span>
        </button>
        @if (auth.isAdmin()) {
            <a pRipple routerLink="/sistema/usuarios" [class]="buttonClass">
                <i class="pi pi-users"></i>
                <span>Usuários do painel</span>
            </a>
        }
        <div class="border-t border-surface-200 dark:border-surface-800 my-1"></div>
        <a pRipple routerLink="/suporte" [class]="buttonClass">
            <i class="pi pi-question-circle"></i>
            <span>Suporte</span>
        </a>
        <a pRipple routerLink="/termos-de-uso" [class]="buttonClass">
            <i class="pi pi-file"></i>
            <span>Termos de uso</span>
        </a>
        <div class="border-t border-surface-200 dark:border-surface-800 my-1"></div>
        <button pRipple type="button" [class]="buttonClass + ' text-red-500!'" (click)="sair()">
            <i class="pi pi-sign-out"></i>
            <span>Sair</span>
        </button>
    `,
    host: {
        class: 'block z-30 p-2 space-y-1 rounded-2xl bg-surface-0 dark:bg-surface-950 shadow-[0px_129.205px_36.493px_0px_rgba(44,54,87,0.00),_0px_82.849px_33.534px_0px_rgba(44,54,87,0.01),_0px_46.356px_27.616px_0px_rgba(44,54,87,0.03),_0px_20.712px_20.712px_0px_rgba(44,54,87,0.05),_0px_4.932px_11.836px_0px_rgba(44,54,87,0.06)] dark:shadow-stroke'
    }
})
export class AppProfile {
    readonly auth = inject(AuthService);
    readonly layout = inject(LayoutService);
    private edicao = inject(EdicaoPendenteService);
    private router = inject(Router);

    readonly buttonClass =
        'w-full px-3 py-2 rounded-lg flex items-center gap-2 font-medium text-surface-500 bg-transparent border-0 text-left hover:text-surface-950 dark:hover:text-surface-0 hover:bg-surface-100 dark:hover:bg-surface-800 transition-colors duration-150 cursor-pointer no-underline';

    readonly inicial = computed(() => (this.auth.usuario()?.nome || this.auth.usuario()?.email || '?').charAt(0).toUpperCase());
    readonly perfil = computed(() => rotulo(PERFIS_ACESSO, this.auth.perfil()));

    bloquear(): void {
        void this.auth.bloquearTela();
    }

    /** Sair: com formulário alterado e não salvo, pergunta antes ("Deseja realmente sair?"). */
    async sair(): Promise<void> {
        if (!(await this.edicao.podeSair())) {
            return;
        }
        this.auth.logout();
    }
}
