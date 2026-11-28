import { DOCUMENT } from '@angular/common';
import { Injectable, computed, inject, signal } from '@angular/core';

/** Pedido de confirmação em aberto: {@code janela} = fechar diálogo (e não sair da página). */
export interface IPedidoDescarte {
    janela: boolean;
}

/**
 * Textos da trava "alterações não salvas" (os mesmos do app mobile)
 * ({@code confirmarDescarte} em app_chirpstack_manager_mobile/lib/core/ui/dialogos.dart).
 */
export const TEXTOS_DESCARTE = {
    pagina: { titulo: 'Deseja realmente sair?', botao: 'Sair sem salvar' },
    janela: { titulo: 'Fechar sem salvar?', botao: 'Fechar sem salvar' },
    mensagem: 'As alterações não salvas serão perdidas.',
    continuar: 'Continuar editando'
} as const;

/**
 * Registro global das EDIÇÕES PENDENTES (o "EdicaoPendenteService do painel web" citado no
 * {@code EdicaoPendente} do app mobile).
 *
 * <p>Cada formulário com alterações não salvas se registra aqui ({@link registrar}) e sai ao
 * salvar, ao descartar ou ao ser destruído ({@link remover}). Quem vai descartar a tela — a
 * troca de rota (guard {@code edicaoPendenteGuard}), o "Sair" do perfil e o "Sair agora" do
 * aviso de sessão — chama {@link podeSair} antes: com algo pendente aparece a confirmação
 * "Deseja realmente sair?" (componente {@code EdicaoPendenteDialog}, montado uma única vez no
 * app-root). Recarregar, fechar a aba ou digitar outro endereço disparam a pergunta nativa do
 * navegador ({@code beforeunload}).</p>
 *
 * <p>O logout automático (sessão expirada, sessão derrubada pelo servidor) NÃO pergunta — não
 * há como esperar — e só chama {@link limpar}.</p>
 */
@Injectable({ providedIn: 'root' })
export class EdicaoPendenteService {
    /** Donos (formulários) com alterações não salvas. */
    private readonly _donos = signal<ReadonlySet<object>>(new Set());

    /** Há alguma edição pendente? */
    readonly ativa = computed(() => this._donos().size > 0);

    /** Confirmação em aberto (lida pelo diálogo global). */
    readonly pedido = signal<IPedidoDescarte | null>(null);

    private _promessa: Promise<boolean> | null = null;
    private _resolver: ((sair: boolean) => void) | null = null;

    /** Travas vivas: antes de decidir, cada uma reavalia o próprio formulário. */
    private readonly _sincronizadores = new Set<() => void>();

    /** Muda a cada {@link limpar}: as travas aceitam o estado atual como descartado. */
    private _geracao = 0;

    /** Quantos diálogos de confirmação estão montados (sem nenhum, cai no confirm nativo). */
    private _hosts = 0;

    private readonly _janela = inject(DOCUMENT).defaultView;

    constructor() {
        // Recarregar / fechar a aba / digitar outro endereço: pergunta nativa do navegador
        // (o texto é do próprio navegador — não é personalizável).
        this._janela?.addEventListener('beforeunload', (evento: BeforeUnloadEvent) => {
            this.sincronizar();
            if (!this.ativa()) return;
            evento.preventDefault();
            evento.returnValue = '';
        });
    }

    get geracao(): number {
        return this._geracao;
    }

    registrar(dono: object): void {
        if (this._donos().has(dono)) return;
        const s = new Set(this._donos());
        s.add(dono);
        this._donos.set(s);
    }

    remover(dono: object): void {
        if (!this._donos().has(dono)) return;
        const s = new Set(this._donos());
        s.delete(dono);
        this._donos.set(s);
    }

    /**
     * Descarta todas as edições (o usuário confirmou sair sem salvar, ou a sessão acabou).
     * Uma confirmação ainda aberta é fechada liberando a saída.
     */
    limpar(): void {
        this._geracao++;
        if (this._donos().size) this._donos.set(new Set());
        if (this._resolver) this.responder(true);
    }

    /**
     * Pergunta "Deseja realmente sair?" (ou "Fechar sem salvar?" com {@code janela}) quando há
     * edição pendente. Devolve true quando pode seguir — nada pendente ou o usuário confirmou —
     * e, nesse caso, limpa o registro.
     */
    async podeSair(janela = false): Promise<boolean> {
        this.sincronizar();
        if (!this.ativa()) return true;
        const sair = await this.confirmar(janela);
        if (sair) this.limpar();
        return sair;
    }

    /**
     * Mostra a confirmação de descarte e devolve a resposta (true = sair/fechar sem salvar).
     * Pedidos simultâneos (ex.: Esc no diálogo + clique no menu) compartilham a mesma pergunta.
     */
    confirmar(janela = false): Promise<boolean> {
        if (this._promessa) return this._promessa;
        if (!this._hosts) {
            // Rede de segurança: sem o diálogo montado, usa o confirm nativo.
            const t = janela ? TEXTOS_DESCARTE.janela : TEXTOS_DESCARTE.pagina;
            return Promise.resolve(this._janela?.confirm(`${t.titulo}\n${TEXTOS_DESCARTE.mensagem}`) ?? true);
        }
        this._promessa = new Promise<boolean>((resolve) => (this._resolver = resolve));
        this.pedido.set({ janela });
        return this._promessa;
    }

    /** Resposta do diálogo de confirmação. */
    responder(sair: boolean): void {
        const resolver = this._resolver;
        this._resolver = null;
        this._promessa = null;
        this.pedido.set(null);
        resolver?.(sair);
    }

    /** Reavalia agora todas as travas vivas (não espera o próximo ciclo de renderização). */
    sincronizar(): void {
        this._sincronizadores.forEach((fn) => fn());
    }

    adicionarSincronizador(fn: () => void): void {
        this._sincronizadores.add(fn);
    }

    removerSincronizador(fn: () => void): void {
        this._sincronizadores.delete(fn);
    }

    /** O diálogo global se anuncia ao montar/desmontar. */
    registrarHost(): void {
        this._hosts++;
    }

    removerHost(): void {
        this._hosts = Math.max(0, this._hosts - 1);
        if (!this._hosts && this._resolver) this.responder(false);
    }
}
