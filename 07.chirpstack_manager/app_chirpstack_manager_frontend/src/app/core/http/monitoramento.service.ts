import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '@/environments/environment';
import { Alerta, Dispositivo, EventoDispositivo, Leitura, ListResponse, MqttStatus, Painel, RegraAlerta, RegraAlertaForm, Serie, StatusAlerta } from './models';

/** Período opcional (ISO-8601). Sem período, o backend usa as últimas 24 h. */
export interface Periodo {
    de?: string | null;
    ate?: string | null;
}

function comPeriodo(p: HttpParams, periodo?: Periodo): HttpParams {
    if (periodo?.de) {
        p = p.set('de', periodo.de);
    }
    if (periodo?.ate) {
        p = p.set('ate', periodo.ate);
    }
    return p;
}

/**
 * Monitoramento (eventos MQTT do ChirpStack v4): painel, dispositivos, leituras, eventos, regras e alertas.
 */
@Injectable({ providedIn: 'root' })
export class MonitoramentoService {
    private http = inject(HttpClient);
    private base = environment.apiUrl;

    painel(): Observable<Painel> {
        return this.http.get<Painel>(`${this.base}/painel`);
    }
    mqtt(): Observable<MqttStatus> {
        return this.http.get<MqttStatus>(`${this.base}/painel/mqtt`);
    }

    // ── dispositivos ──
    dispositivos(busca: string, inativos: boolean, pagina: number, tamanho: number): Observable<ListResponse<Dispositivo>> {
        const params = new HttpParams().set('busca', busca ?? '').set('inativos', inativos).set('pagina', pagina).set('tamanho', tamanho);
        return this.http.get<ListResponse<Dispositivo>>(`${this.base}/dispositivos`, { params });
    }
    dispositivo(id: number): Observable<Dispositivo> {
        return this.http.get<Dispositivo>(`${this.base}/dispositivos/${id}`);
    }
    atualizarDispositivo(id: number, descricao: string | null, ativo: boolean): Observable<Dispositivo> {
        return this.http.put<Dispositivo>(`${this.base}/dispositivos/${id}`, { descricao, ativo });
    }
    excluirDispositivo(id: number): Observable<void> {
        return this.http.delete<void>(`${this.base}/dispositivos/${id}`);
    }
    leituras(id: number, periodo: Periodo, pagina: number, tamanho: number): Observable<ListResponse<Leitura>> {
        const params = comPeriodo(new HttpParams().set('pagina', pagina).set('tamanho', tamanho), periodo);
        return this.http.get<ListResponse<Leitura>>(`${this.base}/dispositivos/${id}/leituras`, { params });
    }
    eventos(id: number, periodo: Periodo, pagina: number, tamanho: number): Observable<ListResponse<EventoDispositivo>> {
        const params = comPeriodo(new HttpParams().set('pagina', pagina).set('tamanho', tamanho), periodo);
        return this.http.get<ListResponse<EventoDispositivo>>(`${this.base}/dispositivos/${id}/eventos`, { params });
    }
    series(id: number, periodo: Periodo): Observable<Serie[]> {
        return this.http.get<Serie[]>(`${this.base}/dispositivos/${id}/series`, { params: comPeriodo(new HttpParams(), periodo) });
    }

    // ── regras e alertas ──
    regras(): Observable<RegraAlerta[]> {
        return this.http.get<RegraAlerta[]>(`${this.base}/regras-alerta`);
    }
    criarRegra(r: RegraAlertaForm): Observable<RegraAlerta> {
        return this.http.post<RegraAlerta>(`${this.base}/regras-alerta`, r);
    }
    atualizarRegra(id: number, r: RegraAlertaForm): Observable<RegraAlerta> {
        return this.http.put<RegraAlerta>(`${this.base}/regras-alerta/${id}`, r);
    }
    excluirRegra(id: number): Observable<void> {
        return this.http.delete<void>(`${this.base}/regras-alerta/${id}`);
    }
    alertas(status: StatusAlerta | null, dispositivoId: number | null, pagina: number, tamanho: number): Observable<ListResponse<Alerta>> {
        let params = new HttpParams().set('pagina', pagina).set('tamanho', tamanho);
        if (status) {
            params = params.set('status', status);
        }
        if (dispositivoId) {
            params = params.set('dispositivoId', dispositivoId);
        }
        return this.http.get<ListResponse<Alerta>>(`${this.base}/alertas`, { params });
    }
    reconhecer(id: number): Observable<Alerta> {
        return this.http.post<Alerta>(`${this.base}/alertas/${id}/reconhecer`, {});
    }
}
