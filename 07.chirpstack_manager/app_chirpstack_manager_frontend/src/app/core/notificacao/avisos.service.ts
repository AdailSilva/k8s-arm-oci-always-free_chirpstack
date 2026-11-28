import { computed, effect, inject, Injectable, signal, untracked } from '@angular/core';
import { Subscription } from 'rxjs';
import { Alerta } from '@/app/core/http/models';
import { MonitoramentoService } from '@/app/core/http/monitoramento.service';
import { AuthService } from '@/app/core/auth/auth.service';

/**
 * Alimenta o sino do topo (mesmo padrão dos demais sistemas): alertas ABERTOS do monitoramento,
 * mais recentes primeiro. Atualiza a cada 60 s enquanto houver sessão (e já ao abrir o painel);
 * para no logoff e com a tela bloqueada. Falhas são silenciosas: o sino só deixa de atualizar.
 */
@Injectable({ providedIn: 'root' })
export class AvisosService {
    private static readonly INTERVALO_MS = 60_000;
    private static readonly TAMANHO = 8;

    private api = inject(MonitoramentoService);
    private auth = inject(AuthService);

    private timer: ReturnType<typeof setInterval> | null = null;
    private req: Subscription | null = null;

    readonly alertas = signal<Alerta[]>([]);
    readonly total = signal(0);
    readonly carregando = signal(false);
    readonly totalLabel = computed(() => (this.total() > 99 ? '99+' : String(this.total())));
    readonly criticos = computed(() => this.alertas().filter((a) => a.severidade === 'CRITICO').length);

    constructor() {
        effect(() => {
            const ativo = this.auth.autenticado() && !this.auth.trocaSenhaObrigatoria() && !this.auth.telaBloqueada();
            untracked(() => (ativo ? this.iniciar() : this.parar()));
        });
    }

    atualizar(): void {
        this.req?.unsubscribe();
        this.carregando.set(true);
        this.req = this.api.alertas('ABERTO', null, 0, AvisosService.TAMANHO).subscribe({
            next: (r) => {
                this.alertas.set(r.result ?? []);
                this.total.set(Number(r.totalCount ?? 0));
                this.carregando.set(false);
            },
            error: () => this.carregando.set(false)
        });
    }

    private iniciar(): void {
        if (this.timer) {
            return;
        }
        this.atualizar();
        this.timer = setInterval(() => this.atualizar(), AvisosService.INTERVALO_MS);
    }

    private parar(): void {
        if (this.timer) {
            clearInterval(this.timer);
            this.timer = null;
        }
        this.req?.unsubscribe();
        this.req = null;
        this.alertas.set([]);
        this.total.set(0);
    }
}
