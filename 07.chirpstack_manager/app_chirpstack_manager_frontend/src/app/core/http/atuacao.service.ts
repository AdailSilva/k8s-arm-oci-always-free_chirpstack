import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '@/environments/environment';
import { ListResponse } from './models';
import {
    AgendamentoRequest,
    AgendamentoResponse,
    AutomacaoRequest,
    AutomacaoResponse,
    CenaRequest,
    CenaResponse,
    ComandoDetalheResponse,
    ComandoRequest,
    ComandoResponse,
    EnergiaResponse,
    EquipamentoRequest,
    EquipamentoResponse,
    ExecucaoResponse,
    ModeloResponse,
    ResumoComandos,
    StatusComando
} from './atuacao.models';

/**
 * Atuação sobre os dispositivos: equipamento (modelo e periféricos), energia, fila de comandos,
 * cenas, agendamentos e automações.
 */
@Injectable({ providedIn: 'root' })
export class AtuacaoService {
    private http = inject(HttpClient);
    private base = environment.apiUrl;

    // ── equipamento ──
    modelos(): Observable<ModeloResponse[]> {
        return this.http.get<ModeloResponse[]>(`${this.base}/equipamentos/modelos`);
    }
    equipamento(dispositivoId: number): Observable<EquipamentoResponse> {
        return this.http.get<EquipamentoResponse>(`${this.base}/dispositivos/${dispositivoId}/equipamento`);
    }
    salvarEquipamento(dispositivoId: number, req: EquipamentoRequest): Observable<EquipamentoResponse> {
        return this.http.put<EquipamentoResponse>(`${this.base}/dispositivos/${dispositivoId}/equipamento`, req);
    }
    removerEquipamento(dispositivoId: number): Observable<void> {
        return this.http.delete<void>(`${this.base}/dispositivos/${dispositivoId}/equipamento`);
    }
    energia(dispositivoId: number, canal: number, de?: string | null, ate?: string | null): Observable<EnergiaResponse> {
        let params = new HttpParams().set('canal', canal);
        if (de) {
            params = params.set('de', de);
        }
        if (ate) {
            params = params.set('ate', ate);
        }
        return this.http.get<EnergiaResponse>(`${this.base}/dispositivos/${dispositivoId}/energia`, { params });
    }

    // ── comandos ──
    enviarComando(dispositivoId: number, req: ComandoRequest): Observable<ComandoResponse> {
        return this.http.post<ComandoResponse>(`${this.base}/dispositivos/${dispositivoId}/comandos`, req);
    }
    comandos(dispositivoId: number | null, status: StatusComando[] | null, pagina: number, tamanho: number): Observable<ListResponse<ComandoResponse>> {
        let params = new HttpParams().set('pagina', pagina).set('tamanho', tamanho);
        if (status?.length) {
            params = params.set('status', status.join(','));
        }
        if (dispositivoId) {
            params = params.set('dispositivoId', dispositivoId);
        }
        return this.http.get<ListResponse<ComandoResponse>>(`${this.base}/comandos`, { params });
    }
    resumoComandos(): Observable<ResumoComandos> {
        return this.http.get<ResumoComandos>(`${this.base}/comandos/resumo`);
    }
    comando(id: number): Observable<ComandoDetalheResponse> {
        return this.http.get<ComandoDetalheResponse>(`${this.base}/comandos/${id}`);
    }
    cancelarComando(id: number): Observable<ComandoResponse> {
        return this.http.post<ComandoResponse>(`${this.base}/comandos/${id}/cancelar`, {});
    }
    reenviarComando(id: number, confirmarCritico: boolean): Observable<ComandoResponse> {
        return this.http.post<ComandoResponse>(`${this.base}/comandos/${id}/reenviar`, { confirmarCritico });
    }

    // ── cenas ──
    cenas(): Observable<CenaResponse[]> {
        return this.http.get<CenaResponse[]>(`${this.base}/cenas`);
    }
    cena(id: number): Observable<CenaResponse> {
        return this.http.get<CenaResponse>(`${this.base}/cenas/${id}`);
    }
    criarCena(req: CenaRequest): Observable<CenaResponse> {
        return this.http.post<CenaResponse>(`${this.base}/cenas`, req);
    }
    atualizarCena(id: number, req: CenaRequest): Observable<CenaResponse> {
        return this.http.put<CenaResponse>(`${this.base}/cenas/${id}`, req);
    }
    excluirCena(id: number): Observable<void> {
        return this.http.delete<void>(`${this.base}/cenas/${id}`);
    }
    executarCena(id: number, confirmarCritico: boolean): Observable<ExecucaoResponse> {
        return this.http.post<ExecucaoResponse>(`${this.base}/cenas/${id}/executar`, { confirmarCritico });
    }

    // ── agendamentos ──
    agendamentos(dispositivoId?: number | null): Observable<AgendamentoResponse[]> {
        const params = dispositivoId ? new HttpParams().set('dispositivoId', dispositivoId) : new HttpParams();
        return this.http.get<AgendamentoResponse[]>(`${this.base}/agendamentos`, { params });
    }
    criarAgendamento(req: AgendamentoRequest): Observable<AgendamentoResponse> {
        return this.http.post<AgendamentoResponse>(`${this.base}/agendamentos`, req);
    }
    atualizarAgendamento(id: number, req: AgendamentoRequest): Observable<AgendamentoResponse> {
        return this.http.put<AgendamentoResponse>(`${this.base}/agendamentos/${id}`, req);
    }
    excluirAgendamento(id: number): Observable<void> {
        return this.http.delete<void>(`${this.base}/agendamentos/${id}`);
    }

    // ── automações ──
    automacoes(): Observable<AutomacaoResponse[]> {
        return this.http.get<AutomacaoResponse[]>(`${this.base}/automacoes`);
    }
    criarAutomacao(req: AutomacaoRequest): Observable<AutomacaoResponse> {
        return this.http.post<AutomacaoResponse>(`${this.base}/automacoes`, req);
    }
    atualizarAutomacao(id: number, req: AutomacaoRequest): Observable<AutomacaoResponse> {
        return this.http.put<AutomacaoResponse>(`${this.base}/automacoes/${id}`, req);
    }
    excluirAutomacao(id: number): Observable<void> {
        return this.http.delete<void>(`${this.base}/automacoes/${id}`);
    }
}
