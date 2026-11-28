# ChirpStack - MeshCentral

Suporte remoto self-hosted ([MeshCentral](https://github.com/Ylianst/MeshCentral)) publicado em
`https://mesh-chirpstack.adailsilva.com.br`, no namespace `chirpstack-meshcentral`: acesso remoto às máquinas dos
clientes pelo navegador, com o instalador de agente identificado como ChirpStack.

Usa a imagem oficial (`ghcr.io/ylianst/meshcentral`, multi-arch com arm64) — não há build próprio.

## Implantação

Mesmo modelo dos demais sistemas do repositório:

| Quem | O quê |
|---|---|
| **Pipeline** (`main.yml`, job *ChirpStack - MeshCentral*) | Namespace, diretório no FSS, StorageClass/PV/PVC, Secret, Service e Ingress; depois espera o ArgoCD e testa o HTTPS |
| **ArgoCD** (Application `chirpstack-meshcentral`, automática, `selfHeal`) | ConfigMap + Deployment, a partir da `kustomization.yaml` |

O job roda quando muda algo em `08.chirpstack_meshcentral/` ou em `gitops/apps/chirpstack-meshcentral.yaml`,
ou pelo *Run workflow* com `target=chirpstack-meshcentral`. Como os outros deploys, passa pela aprovação
(environment `producao`) e falha com aviso se o namespace estiver pausado
(`bash ~/Apps/OracleCloud/00.Useful/05_ns_toggle.sh chirpstack-meshcentral resume`).

**Versão / rollback:** o `newTag` em [`kubernetes/kustomization.yaml`](kubernetes/kustomization.yaml)
é o único lugar da versão (tags em https://github.com/Ylianst/MeshCentral/pkgs/container/meshcentral).

## Estrutura

```
08.chirpstack_meshcentral/
├── README.md
└── kubernetes/
    ├── 00.UsefulScripts/
    │   ├── 01.deploy_full_chirpstack-meshcentral_script.sh        # deploy à mão, na mesma ordem do pipeline
    │   ├── 02.destroy_full_chirpstack-meshcentral_script.sh       # remove app/Service/Ingress, mantém os dados
    │   ├── 03.gerar_segredos_chirpstack-meshcentral_script.sh     # Secret com o SESSION_KEY (nunca impresso)
    │   ├── 04.criar_administrador_chirpstack-meshcentral_script.sh # primeiro administrador (senha pedida no terminal)
    │   └── 05.garantir_diretorio_fss_chirpstack-meshcentral_script.sh # cria o diretório no FSS com um pod temporário
    ├── 01.create__Namespace.yaml
    ├── 02.chirpstack-meshcentral__StorageClass__PersistentVolume__PersistentVolumeClaim.yaml  # FSS, 2Gi, Retain
    ├── 03.chirpstack-meshcentral__ConfigMap.yaml       # config.json-semente (sessionKey vem do Secret)
    ├── 04.chirpstack-meshcentral__Deployment.yaml      # 1 réplica, Recreate
    ├── 05.chirpstack-meshcentral__Service.yaml
    ├── 06.chirpstack-meshcentral__Ingress.yaml         # TLS pelo cert-manager (letsencrypt-prod)
    └── kustomization.yaml                         # o que o ArgoCD aplica + versão da imagem
```

## Dados

Um único volume no OCI FSS (`/FileSystem-K8S/chirpstack-meshcentral-data`, PV com `Retain`), em subpastas:

| Subpasta | Conteúdo |
|---|---|
| `meshcentral-data` | `config.json`, banco NeDB e certificados |
| `meshcentral-files` | arquivos enviados nas sessões |
| `meshcentral-backups` | backups automáticos (a cada 24 h, mantidos por 10 dias) |

O `config.json` é gravado no volume só na **primeira subida** (o initContainer troca `__SESSION_KEY__`
pelo valor do Secret `chirpstack-meshcentral-secret`). Depois disso o MeshCentral mantém o próprio arquivo:
mudar o ConfigMap não altera uma instalação já inicializada. A exceção é o endereço público
(`cert` e `certUrl`): a cada subida o initContainer o alinha com o ConfigMap, e a anotação
`meshcentral/endereco` do Deployment reinicia o pod quando ele muda. Agentes instalados antes de uma
troca de endereço precisam ser reinstalados.

## Primeiro acesso

O auto-cadastro está desligado (`newAccounts: false`). Depois do primeiro deploy, crie o administrador:

```bash
bash 08.chirpstack_meshcentral/kubernetes/00.UsefulScripts/04.criar_administrador_chirpstack-meshcentral_script.sh
```

## Deploy e remoção à mão

```bash
bash 08.chirpstack_meshcentral/kubernetes/00.UsefulScripts/01.deploy_full_chirpstack-meshcentral_script.sh
```

```bash
bash 08.chirpstack_meshcentral/kubernetes/00.UsefulScripts/02.destroy_full_chirpstack-meshcentral_script.sh
```

O destroy mantém PVC, PV, Secret e namespace. Com a Application ativa, o ArgoCD recria ConfigMap e
Deployment: pause o namespace antes (`bash ~/Apps/OracleCloud/00.Useful/05_ns_toggle.sh chirpstack-meshcentral pause`).
