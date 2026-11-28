import { HttpClient } from '@angular/common/http';
import { computed, effect, inject, Injectable, signal, untracked } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '@/environments/environment';
import { HorarioAcessoDia, HorarioAcessoSalvo, SituacaoHorarioAcesso } from '@/app/core/http/models';
import { AuthService } from '@/app/core/auth/auth.service';

/** Antecedência do aviso de fim do horário. */
const AVISO_MS = 5 * 60 * 1000;
/** Nova consulta da situação (pega mudanças na grade feitas pelo ADMIN). */
const CONSULTA_MS = 60 * 1000;

/**
 * HORÁRIO DE ACESSO, mesmo padrão dos demais sistemas.
 *
 * - CRUD da grade semanal (Sistema → Horário de acesso, somente ADMIN).
 * - Acompanha a sessão de quem não é ADMIN: 5 minutos antes do fim do horário do dia abre o aviso
 *   com a contagem e, no fim, encerra a sessão e leva ao login explicando o motivo.
 * - O relógio é o do SERVIDOR: a diferença para o do computador é medida a cada consulta, para que
 *   um relógio errado não ganhe nem perca minutos. O servidor também encerra as sessões fora do
 *   horário (a cada 30 s e na renovação do token), mesmo com o navegador fechado.
 */
@Injectable({ providedIn: 'root' })
export class HorarioAcessoService {
    private http = inject(HttpClient);
    private auth = inject(AuthService);
    private base = `${environment.apiUrl}/horario-acesso`;

    /** Situação vinda do servidor. */
    readonly situacao = signal<SituacaoHorarioAcesso | null>(null);
    /** Milissegundos até o fim do horário de hoje (null = sem fim hoje). */
    readonly restanteMs = signal<number | null>(null);
    /** Aviso visível (5 minutos finais), até o usuário clicar em "Entendi". */
    readonly avisoVisivel = signal(false);

    readonly restanteLabel = computed(() => {
        const r = this.restanteMs();
        if (r === null) {
            return '--:--';
        }
        const t = Math.max(0, Math.floor(r / 1000));
        return `${String(Math.floor(t / 60)).padStart(2, '0')}:${String(t % 60).padStart(2, '0')}`;
    });

    readonly horaFim = computed(() => this.situacao()?.horaFim ?? '');

    private desvio = 0;
    private fimMs: number | null = null;
    private dispensado = false;
    private encerrando = false;
    private tick: ReturnType<typeof setInterval> | null = null;
    private consulta: ReturnType<typeof setInterval> | null = null;

    constructor() {
        effect(() => {
            const logado = this.auth.autenticado() && !this.auth.trocaSenhaObrigatoria();
            untracked(() => (logado ? this.iniciar() : this.parar()));
        });
        if (typeof document !== 'undefined') {
            document.addEventListener('visibilitychange', () => {
                if (document.visibilityState === 'visible' && this.consulta) {
                    this.consultar();
                }
            });
        }
    }

    // ── Grade (ADMIN) ────────────────────────────────────────────────────────

    listar(): Observable<HorarioAcessoDia[]> {
        return this.http.get<HorarioAcessoDia[]>(this.base);
    }

    salvar(dias: HorarioAcessoDia[], aplicarAgora: boolean): Observable<HorarioAcessoSalvo> {
        return this.http.put<HorarioAcessoSalvo>(this.base, { dias, aplicarAgora });
    }

    /** Fecha o aviso; a contagem continua e no fim a sessão acaba do mesmo jeito. */
    dispensarAviso(): void {
        this.dispensado = true;
        this.avisoVisivel.set(false);
    }

    /** Depois de salvar a grade: recalcula na hora o fim do horário da própria sessão. */
    reconsultar(): void {
        if (this.consulta) {
            this.consultar();
        }
    }

    // ── Acompanhamento da sessão ─────────────────────────────────────────────

    private iniciar(): void {
        if (this.consulta) {
            return;
        }
        this.encerrando = false;
        this.consultar();
        this.consulta = setInterval(() => this.consultar(), CONSULTA_MS);
        this.tick = setInterval(() => this.avaliar(), 1000);
    }

    private parar(): void {
        if (this.tick) {
            clearInterval(this.tick);
        }
        if (this.consulta) {
            clearInterval(this.consulta);
        }
        this.tick = this.consulta = null;
        this.fimMs = null;
        this.dispensado = false;
        this.situacao.set(null);
        this.restanteMs.set(null);
        this.avisoVisivel.set(false);
    }

    private consultar(): void {
        this.http.get<SituacaoHorarioAcesso>(`${this.base}/situacao`).subscribe({
            next: (s) => {
                if (!this.consulta) {
                    return;
                }
                if (s?.agora) {
                    this.desvio = Date.parse(s.agora) - Date.now();
                }
                const novoFim = s?.restrito && s.fimDoHorario ? Date.parse(s.fimDoHorario) : null;
                if (novoFim !== this.fimMs) {
                    this.dispensado = false;
                }
                this.fimMs = novoFim;
                this.situacao.set(s);
                if (s?.restrito && !s.permitidoAgora) {
                    this.encerrar(s.mensagem ?? undefined);
                    return;
                }
                this.avaliar();
            },
            // Falha de rede: o servidor garante o encerramento no fim do horário.
            error: () => undefined
        });
    }

    private avaliar(): void {
        if (this.fimMs === null) {
            this.restanteMs.set(null);
            this.avisoVisivel.set(false);
            return;
        }
        const restante = this.fimMs - (Date.now() + this.desvio);
        this.restanteMs.set(restante);
        if (restante <= 0) {
            const hora = this.horaFim();
            this.encerrar(hora ? `O horário de acesso de hoje terminou às ${hora}. Você foi desconectado.` : undefined);
            return;
        }
        if (restante <= AVISO_MS && !this.dispensado) {
            this.avisoVisivel.set(true);
        }
    }

    private encerrar(mensagem?: string): void {
        if (this.encerrando) {
            return;
        }
        this.encerrando = true;
        this.parar();
        this.auth.logout('FORA_HORARIO', mensagem);
    }
}
