/**
 * Tipos da atuação: equipamentos (modelo do firmware e periféricos), fila de comandos, cenas,
 * agendamentos, automações e energia. Datas chegam em ISO-8601 (Instant) e horários como "HH:mm[:ss]".
 */

export type ModeloFirmware = 'ADAILSILVA_V1' | 'RADIOENGE_ORIGINAL' | 'ESP32_ORIGINAL';
export type TipoPeriferico = 'RELE' | 'PZEM004T';
export type TipoComando =
    | 'STATUS'
    | 'INTERVALO'
    | 'ENVIAR_AGORA'
    | 'CONFIRMADO'
    | 'CLASSE'
    | 'REINICIAR'
    | 'RELE_DEFINIR'
    | 'RELE_PULSO'
    | 'RELE_MASCARA'
    | 'RELE_LER'
    | 'PZEM_LER'
    | 'PZEM_ZERAR_ENERGIA'
    | 'PZEM_LIMITE_POTENCIA'
    | 'RESET_REMOTO'
    | 'BRUTO';
export type StatusComando = 'PENDENTE' | 'ENFILEIRADO' | 'TRANSMITIDO' | 'EXECUTADO' | 'FALHOU' | 'EXPIRADO' | 'CANCELADO';
export type EstrategiaConfirmacao = 'RESPOSTA_V1' | 'RESPOSTA_AT' | 'RESPOSTA_MODBUS' | 'CONFIRMACAO_LORAWAN' | 'TRANSMISSAO';
export type OrigemComando = 'MANUAL' | 'AGENDAMENTO' | 'AUTOMACAO' | 'CENA' | 'RECONCILIACAO' | 'PULSO';
export type ResultadoTentativa = 'EXECUTADO' | 'SEM_RESPOSTA' | 'NAO_CONFIRMADO' | 'ERRO' | 'RECUSADO' | 'OCUPADO' | 'PERDIDO' | 'EXPIRADO' | 'CANCELADO';
export type AcaoRele = 'DESLIGAR' | 'LIGAR' | 'ALTERNAR';
export type AlvoAcao = 'COMANDO' | 'CENA';
export type Recorrencia = 'UNICA' | 'SEMANAL';
export type GatilhoAutomacao = 'ALERTA_ABERTO' | 'ALERTA_RESOLVIDO';

/** Parâmetros de um comando: cada tipo usa só os seus (ver ROTULO_PARAMETROS / ComandoForm). */
export interface ParametrosComando {
    canal?: number | null;
    acao?: AcaoRele | null;
    estado?: boolean | null;
    duracaoMs?: number | null;
    mascara?: number | null;
    valores?: number | null;
    segundos?: number | null;
    confirmado?: boolean | null;
    classe?: string | null;
    atrasoMs?: number | null;
    watts?: number | null;
    porta?: number | null;
    dadosHex?: string | null;
}

/** Política de novas tentativas (nulos = padrão do servidor). */
export interface PoliticaRequest {
    maxTentativas?: number | null;
    intervaloTentativaS?: number | null;
    fatorRecuo?: number | null;
    aguardarRespostaS?: number | null;
    validadeMin?: number | null;
    janelaInicio?: string | null;
    janelaFim?: string | null;
    diasSemana?: number[] | null;
}

// ── modelos e equipamento ──

export interface ComandoSuportado {
    tipo: TipoComando;
    rotulo: string;
    periferico?: TipoPeriferico | null;
}

export interface PerifericoSuportado {
    tipo: TipoPeriferico;
    rotulo: string;
    atuador: boolean;
}

export interface ModeloResponse {
    modelo: ModeloFirmware;
    rotulo: string;
    descricao: string;
    comandos: ComandoSuportado[];
    perifericos: PerifericoSuportado[];
    observacoes: string[];
}

export interface PerifericoRequest {
    tipo: TipoPeriferico;
    canal: number;
    nome: string;
    gpio?: number | null;
    enderecoModbus?: number | null;
    critico: boolean;
}

export interface EquipamentoRequest {
    modelo: ModeloFirmware;
    reconciliar: boolean;
    tarifaKwh?: number | null;
    perifericos: PerifericoRequest[];
}

export interface PerifericoResponse {
    id: number;
    tipo: TipoPeriferico;
    tipoRotulo: string;
    canal: number;
    nome: string;
    gpio?: number | null;
    enderecoModbus?: number | null;
    critico: boolean;
    estadoDesejado?: boolean | null;
    estadoReportado?: boolean | null;
    reportadoEm?: string | null;
    /** JSON da última medição do PZEM. */
    leitura?: string | null;
    leituraEm?: string | null;
}

export interface EquipamentoResponse {
    dispositivoId: number;
    configurado: boolean;
    modelo?: ModeloFirmware | null;
    modeloRotulo?: string | null;
    reconciliar: boolean;
    tarifaKwh?: number | null;
    /** JSON da última resposta de STATUS. */
    status?: string | null;
    statusEm?: string | null;
    perifericos: PerifericoResponse[];
    comandos: ComandoSuportado[];
}

/** Medição do PZEM-004T (energia em Wh). */
export interface MedicaoPzem {
    canal: number;
    tensao: number;
    corrente: number;
    potencia: number;
    energia: number;
    frequencia: number;
    fp: number;
    alarme: boolean;
    custoTotal?: number | null;
}

// ── comandos ──

export interface ComandoRequest {
    tipo: TipoComando;
    parametros?: ParametrosComando | null;
    politica?: PoliticaRequest | null;
    confirmarCritico: boolean;
}

export interface ComandoResponse {
    id: number;
    dispositivoId: number;
    dispositivoNome?: string | null;
    tipo: TipoComando;
    tipoRotulo: string;
    resumo?: string | null;
    parametros?: ParametrosComando | null;
    fPort: number;
    dadosHex?: string | null;
    confirmado: boolean;
    seq?: number | null;
    estrategia?: EstrategiaConfirmacao | null;
    status: StatusComando;
    origem: OrigemComando;
    origemId?: number | null;
    tentativas: number;
    maxTentativas: number;
    intervaloTentativaS: number;
    fatorRecuo: number;
    aguardarRespostaS: number;
    validadeEm?: string | null;
    janelaInicio?: string | null;
    janelaFim?: string | null;
    diasSemana?: number[] | null;
    proximaTentativaEm?: string | null;
    enfileiradoEm?: string | null;
    transmitidoEm?: string | null;
    concluidoEm?: string | null;
    resposta?: string | null;
    detalhe?: string | null;
    criadoPor?: string | null;
    criadoEm: string;
}

export interface TentativaResponse {
    numero: number;
    chirpstackItemId?: string | null;
    enfileiradoEm?: string | null;
    transmitidoEm?: string | null;
    concluidaEm?: string | null;
    resultado?: ResultadoTentativa | null;
    detalhe?: string | null;
}

export interface ComandoDetalheResponse {
    comando: ComandoResponse;
    tentativas: TentativaResponse[];
}

export interface ResumoComandos {
    pendentes: number;
    emAndamento: number;
    executados24h: number;
    falhas24h: number;
}

// ── cenas ──

export interface CenaItemRequest {
    dispositivoId: number;
    tipo: TipoComando;
    parametros?: ParametrosComando | null;
}

export interface CenaRequest {
    nome: string;
    descricao?: string | null;
    itens: CenaItemRequest[];
}

export interface CenaItemResponse {
    id: number;
    ordem: number;
    dispositivoId: number;
    dispositivoNome?: string | null;
    tipo: TipoComando;
    tipoRotulo: string;
    parametros?: ParametrosComando | null;
    resumo?: string | null;
}

export interface CenaResponse {
    id: number;
    nome: string;
    descricao?: string | null;
    itens: CenaItemResponse[];
    temCritico: boolean;
    atualizadoEm: string;
    atualizadoPor?: string | null;
}

export interface ExecucaoResponse {
    comandos: number[];
}

// ── agendamentos ──

export interface AgendamentoRequest {
    nome: string;
    alvo: AlvoAcao;
    dispositivoId?: number | null;
    tipo?: TipoComando | null;
    parametros?: ParametrosComando | null;
    cenaId?: number | null;
    recorrencia: Recorrencia;
    executarEm?: string | null;
    horario?: string | null;
    diasSemana?: number[] | null;
    validadeMin?: number | null;
    maxTentativas?: number | null;
    ativo: boolean;
    confirmarCritico: boolean;
}

export interface AgendamentoResponse {
    id: number;
    nome: string;
    alvo: AlvoAcao;
    dispositivoId?: number | null;
    dispositivoNome?: string | null;
    tipo?: TipoComando | null;
    tipoRotulo?: string | null;
    parametros?: ParametrosComando | null;
    cenaId?: number | null;
    cenaNome?: string | null;
    resumo?: string | null;
    recorrencia: Recorrencia;
    executarEm?: string | null;
    horario?: string | null;
    diasSemana?: number[] | null;
    validadeMin: number;
    maxTentativas: number;
    ativo: boolean;
    proximaExecucaoEm?: string | null;
    ultimaExecucaoEm?: string | null;
    atualizadoEm: string;
    atualizadoPor?: string | null;
}

// ── automações ──

export interface AutomacaoRequest {
    nome: string;
    regraId: number;
    gatilho: GatilhoAutomacao;
    alvo: AlvoAcao;
    mesmoDispositivo: boolean;
    dispositivoId?: number | null;
    tipo?: TipoComando | null;
    parametros?: ParametrosComando | null;
    cenaId?: number | null;
    intervaloMinimoS?: number | null;
    ativo: boolean;
    confirmarCritico: boolean;
}

export interface AutomacaoResponse {
    id: number;
    nome: string;
    regraId: number;
    regraNome?: string | null;
    gatilho: GatilhoAutomacao;
    alvo: AlvoAcao;
    mesmoDispositivo: boolean;
    dispositivoId?: number | null;
    dispositivoNome?: string | null;
    tipo?: TipoComando | null;
    tipoRotulo?: string | null;
    parametros?: ParametrosComando | null;
    cenaId?: number | null;
    cenaNome?: string | null;
    resumo?: string | null;
    intervaloMinimoS: number;
    ativo: boolean;
    ultimaExecucaoEm?: string | null;
    atualizadoEm: string;
    atualizadoPor?: string | null;
}

// ── energia ──

export interface EnergiaResponse {
    canal: number;
    nome?: string | null;
    de: string;
    ate: string;
    energiaInicialWh?: number | null;
    energiaFinalWh?: number | null;
    consumoKwh?: number | null;
    tarifaKwh?: number | null;
    custo?: number | null;
    potenciaMedia?: number | null;
    potenciaMaxima?: number | null;
    leituras: number;
}
