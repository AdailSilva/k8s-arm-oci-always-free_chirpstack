import { HttpClient } from '@angular/common/http';
import { effect, inject, Injectable, untracked } from '@angular/core';
import { Subject } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { environment } from '@/environments/environment';
import { PreferenciasAparencia } from '@/app/core/http/models';
import { AuthService } from '@/app/core/auth/auth.service';
import { LayoutConfig, LayoutService } from '@/app/layout/service/layout.service';

/** Aparência padrão (antes de o usuário escolher e enquanto ninguém está logado). */
export const APARENCIA_PADRAO: LayoutConfig = { preset: 'Aura', primary: 'blue', surface: null, darkTheme: false, menuMode: 'static' };

/**
 * PREFERÊNCIAS NO SERVIDOR, mesmo padrão dos demais sistemas.
 *
 * - Ao entrar: carrega a aparência do usuário (GET /conta/preferencias) e aplica. Sem nada salvo,
 *   grava a aparência atual deste navegador como a dele.
 * - A cada mudança (tema, cor, superfície, modo escuro, modo do menu): grava no servidor (PUT),
 *   agrupando mudanças rápidas.
 * - Ao sair: volta ao padrão, para o próximo usuário deste computador não herdar a aparência.
 *
 * O localStorage do LayoutService fica só como cache contra o "piscar" ao recarregar: o servidor é
 * a fonte da verdade. Instanciado no AppComponent para valer desde o início.
 */
@Injectable({ providedIn: 'root' })
export class PreferenciasService {
    private http = inject(HttpClient);
    private auth = inject(AuthService);
    private layout = inject(LayoutService);
    private base = `${environment.apiUrl}/conta/preferencias`;

    private carregado = false;
    private ultimoGravado = '';
    private usuarioAtual: number | null = null;
    private readonly gravar$ = new Subject<LayoutConfig>();

    constructor() {
        this.gravar$.pipe(debounceTime(600)).subscribe((c) => this.gravar(c));

        effect(() => {
            const u = this.auth.usuario();
            const pronto = !!u && !this.auth.trocaSenhaObrigatoria();
            untracked(() => {
                if (pronto && u!.id !== this.usuarioAtual) {
                    this.usuarioAtual = u!.id;
                    this.carregar();
                } else if (!u && this.usuarioAtual !== null) {
                    this.usuarioAtual = null;
                    this.carregado = false;
                    this.ultimoGravado = '';
                    this.layout.layoutConfig.set({ ...APARENCIA_PADRAO });
                }
            });
        });

        effect(() => {
            const c = this.layout.layoutConfig();
            untracked(() => {
                if (this.carregado && this.auth.autenticado() && JSON.stringify(normalizar(c)) !== this.ultimoGravado) {
                    this.gravar$.next(c);
                }
            });
        });
    }

    private carregar(): void {
        this.carregado = false;
        this.http.get<PreferenciasAparencia | null>(this.base, { observe: 'body' }).subscribe({
            next: (p) => {
                if (p) {
                    const c: LayoutConfig = { preset: p.preset, primary: p.primary, surface: p.surface ?? null, darkTheme: p.darkTheme, menuMode: p.menuMode };
                    this.ultimoGravado = JSON.stringify(normalizar(c));
                    this.layout.layoutConfig.set(c);
                    this.carregado = true;
                } else {
                    // Primeiro acesso com preferências no servidor: a aparência atual passa a ser a do usuário.
                    this.carregado = true;
                    this.gravar(this.layout.layoutConfig());
                }
            },
            // Falha (rede, versão antiga da API): segue com a aparência local, sem gravar.
            error: () => undefined
        });
    }

    private gravar(c: LayoutConfig): void {
        const corpo = normalizar(c);
        const json = JSON.stringify(corpo);
        if (json === this.ultimoGravado) {
            return;
        }
        this.http.put<PreferenciasAparencia>(this.base, corpo).subscribe({
            next: () => (this.ultimoGravado = json),
            error: () => undefined
        });
    }
}

function normalizar(c: LayoutConfig): PreferenciasAparencia {
    return { preset: c.preset, primary: c.primary, surface: c.surface || null, darkTheme: !!c.darkTheme, menuMode: c.menuMode };
}
