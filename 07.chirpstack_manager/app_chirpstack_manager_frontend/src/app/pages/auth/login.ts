import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AuthService, MENSAGENS_ENCERRAMENTO, ROTA_TROCA_SENHA } from '@/app/core/auth/auth.service';
import { NotifyService } from '@/app/core/notificacao/notify.service';
import { SistemaService } from '@/app/core/http/sistema.service';
import { emailValido, MSG_EMAIL_INVALIDO } from '@/app/core/format/validacao';

/** Chave do e-mail lembrado ("Lembre de mim"). Só o e-mail: senha e tokens nunca vão para o navegador. */
const CHAVE_EMAIL_LEMBRADO = 'chirpstack-manager.email-lembrado';

/**
 * Login do ChirpStack Manager — tela cheia com a identidade do sistema (brand/BRAND.md):
 * painel da marca à esquerda (ilustração com dados fictícios) e o cartão do formulário à direita.
 */
@Component({
    selector: 'app-login',
    standalone: true,
    imports: [FormsModule, RouterModule],
    templateUrl: './login.html',
    styleUrl: './login.css'
})
export class Login implements OnInit, OnDestroy {
    private auth = inject(AuthService);
    private router = inject(Router);
    private route = inject(ActivatedRoute);
    private sistema = inject(SistemaService);

    email = signal('');
    senha = signal('');
    lembrar = signal(false);
    mostrarSenha = signal(false);
    carregando = signal(false);
    erro = signal('');
    aviso = signal('');
    /** O login recusou porque o e-mail ainda não foi confirmado: oferece reenviar o link. */
    emailNaoConfirmado = signal(false);
    reenviando = signal(false);

    readonly anoAtual = new Date().getFullYear();

    // ── Ilustração do painel da marca (dados fictícios) ──────────────────────
    /** Uplinks recebidos hoje; avança sozinho para dar vida à tela. */
    private readonly uplinks = signal(1284093);
    readonly digitos = computed(() => String(this.uplinks()).padStart(7, '0').split(''));
    readonly gateways = [
        { eui: 'a84041ffff1f2a01', nome: 'Gateway Sede', estado: 'online', rssi: '-61 dBm', uplinks: '148' },
        { eui: 'a84041ffff1f2a07', nome: 'Gateway Galpão 2', estado: 'online', rssi: '-78 dBm', uplinks: '93' },
        { eui: 'a84041ffff1f2a0c', nome: 'Gateway Torre Norte', estado: 'offline', rssi: '—', uplinks: '0' },
        { eui: 'a84041ffff1f2a12', nome: 'Gateway Campo Sul', estado: 'online', rssi: '-97 dBm', uplinks: '41' }
    ];
    private relogio?: ReturnType<typeof setInterval>;

    ngOnInit(): void {
        const motivo = this.route.snapshot.queryParamMap.get('motivo') ?? this.auth.ultimoMotivo();
        if (motivo && MENSAGENS_ENCERRAMENTO[motivo]) {
            // Fim do horário de acesso: o texto detalhado traz a hora do fim e quando o acesso volta.
            this.aviso.set((motivo === 'FORA_HORARIO' && this.auth.mensagemEncerramento()) || MENSAGENS_ENCERRAMENTO[motivo]);
        } else if (this.route.snapshot.queryParamMap.get('expirada')) {
            this.aviso.set('Sua sessão expirou. Entre novamente.');
        }

        const lembrado = localStorage.getItem(CHAVE_EMAIL_LEMBRADO);
        if (lembrado) {
            this.email.set(lembrado);
            this.lembrar.set(true);
        }

        // Contador ilustrativo: +1 a +4 uplinks a cada 1,8 s (respeita "reduzir movimento").
        const reduzir = typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
        if (!reduzir) {
            this.relogio = setInterval(() => this.uplinks.update((v) => v + 1 + Math.floor(Math.random() * 4)), 1800);
        }
    }

    /** Reenvia o link de confirmação (ou o convite) para o e-mail digitado. */
    reenviarConfirmacao(): void {
        const email = this.email().trim();
        if (!emailValido(email)) {
            this.erro.set(MSG_EMAIL_INVALIDO);
            return;
        }
        this.reenviando.set(true);
        this.sistema.reenviarConfirmacao(email).subscribe({
            next: () => {
                this.reenviando.set(false);
                this.emailNaoConfirmado.set(false);
                this.erro.set('');
                this.aviso.set('Se o e-mail estiver cadastrado e sem confirmação, enviamos um novo link. Confira a caixa de entrada.');
            },
            error: (e) => {
                this.reenviando.set(false);
                this.erro.set(NotifyService.mensagem(e));
            }
        });
    }

    ngOnDestroy(): void {
        if (this.relogio) clearInterval(this.relogio);
    }

    entrar(): void {
        this.erro.set('');
        this.emailNaoConfirmado.set(false);
        const email = this.email().trim();
        if (!email || !this.senha()) {
            this.erro.set('Informe o e-mail e a senha.');
            return;
        }
        if (!emailValido(email)) {
            this.erro.set(MSG_EMAIL_INVALIDO);
            return;
        }
        this.carregando.set(true);
        this.auth.login(email, this.senha()).subscribe({
            next: () => {
                if (this.lembrar()) {
                    localStorage.setItem(CHAVE_EMAIL_LEMBRADO, email);
                } else {
                    localStorage.removeItem(CHAVE_EMAIL_LEMBRADO);
                }
                if (this.auth.trocaSenhaObrigatoria()) {
                    this.router.navigateByUrl(ROTA_TROCA_SENHA);
                    return;
                }
                const destino = this.route.snapshot.queryParamMap.get('returnUrl') || '/painel';
                this.router.navigateByUrl(destino.startsWith('/') && !destino.startsWith('//') ? destino : '/painel');
            },
            error: (e) => {
                this.carregando.set(false);
                this.senha.set('');
                this.erro.set(NotifyService.mensagem(e));
                this.emailNaoConfirmado.set(e instanceof HttpErrorResponse && (e.error as { erro?: string } | null)?.erro === 'EMAIL_NAO_CONFIRMADO');
            }
        });
    }
}
