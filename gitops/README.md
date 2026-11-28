# GitOps ChirpStack (ArgoCD) — monorepo

Padrão do cluster — nomes, AppProjects, labels, modos de implantação e o catálogo dos 6 repositórios: **[PADRAO-ARGOCD.md](PADRAO-ARGOCD.md)**.

Todas as Applications deste repositório seguem `<projeto>-<sistema>-<componente>` e ficam no(s) AppProject(s) **`02-chirpstack`**. Frontends, backends e site são **aplicados pelo ArgoCD**: o pipeline só grava o `newTag` da `kustomization.yaml` (voltar versão = trocar o `newTag`, ver [ROLLBACK.md](ROLLBACK.md)). Bancos e brokers continuam **aplicados pelo pipeline** e aparecem no ArgoCD só para monitoramento (sync bloqueado pela janela `deny` do AppProject). 

Os ChirpStack v3/v4 (`05.`/`06.`) entram como `chirpstack-v3-stack` e `chirpstack-v4-stack`, com sincronização **manual**: aparecem no ArgoCD (estado, saúde, diff) e podem ser implantados pelo botão **Sync** ou pelos scripts de `00.UsefulScripts/`.

## Applications

| Application | AppProject | Pasta | Namespace | Implantação |
|---|---|---|---|---|
| `chirpstack-site-frontend` | `02-chirpstack` | `04.homepage/app_chirpstack_site_frontend/kubernetes` | `chirpstack-site` | ArgoCD (automático) |
| `chirpstack-v3-stack` | `02-chirpstack` | `05.chirpstack_v3/kubernetes` | `chirpstack-v3` | Manual (botão Sync) |
| `chirpstack-v4-stack` | `02-chirpstack` | `06.chirpstack_v4/kubernetes` | `chirpstack-v4` | Manual (botão Sync) |
| `chirpstack-manager-backend` | `02-chirpstack` | `07.chirpstack_manager/api_chirpstack_manager_backend/kubernetes` | `chirpstack-manager` | ArgoCD (automático) |
| `chirpstack-manager-frontend` | `02-chirpstack` | `07.chirpstack_manager/app_chirpstack_manager_frontend/kubernetes` | `chirpstack-manager` | ArgoCD (automático) |
| `chirpstack-manager-postgresql` | `02-chirpstack` | `07.chirpstack_manager/api_chirpstack_manager_backend/kubernetes` | `chirpstack-manager` | Pipeline — ArgoCD só monitora |
| `chirpstack-meshcentral` | `02-chirpstack` | `08.chirpstack_meshcentral/kubernetes` (kustomize) | `chirpstack-meshcentral` | ArgoCD (automático) · base (Namespace, PV/PVC, Secret, Service, Ingress) pelo `main.yml` (job `chirpstack_meshcentral`) |
| `chirpstack-app-of-apps` | `02-chirpstack` | `gitops/apps` | `argocd` | Raiz: cria o(s) AppProject(s) e as Applications acima |

## Layout (dentro do próprio monorepo)

| Caminho | Conteúdo |
|---|---|
| `gitops/apps/appproject-02-chirpstack.yaml` | AppProject `02-chirpstack` (origem: só este repositório; destinos: `chirpstack-*`) |
| `gitops/apps/app-of-apps.yaml` | Raiz `chirpstack-app-of-apps` (o `include` lista todos os arquivos acima e abaixo) |
| `gitops/apps/<application>.yaml` | Uma Application por arquivo, com o mesmo nome |
| `gitops/bootstrap/00-argocd-install.md` | Instalação do ArgoCD (uma vez por cluster) e credencial deste repositório |
| `gitops/bootstrap/01-aplicar-padrao-argocd.sh` | Aplica AppProject + app-of-apps e adota os recursos já existentes (idempotente) |
| `gitops/bootstrap/09-auditar-cluster-argocd.sh` | Audita o cluster inteiro: nomes, AppProjects, labels e workloads sem Application |
| `gitops/PADRAO-ARGOCD.md` | Padrão e catálogo (idêntico nos 6 repositórios) |

Cada `kustomization.yaml` referencia **só o Deployment** daquele app (nos backends, também o RBAC de backup, quando existe). As Applications de banco, broker e stack usam `directory.include` com a lista exata de manifestos: PV/PVC/StorageClass, Namespace, Secrets do pipeline, Jobs de migração, cert-manager e ClusterIssuer ficam de fora. Service e Ingress dos frontends/backends continuam com o pipeline.

## Colocar de pé

1. Rode o pipeline ao menos uma vez por app (`./force_deploy.sh` ou `workflow_dispatch`): ele publica a imagem `X.Y`, grava o `newTag` e aplica a infraestrutura base (Namespace, Secrets, banco, Service, Ingress).
2. ArgoCD e credencial do repositório: [bootstrap/00-argocd-install.md](bootstrap/00-argocd-install.md) (passos 1 a 3).
3. Commit + push de `gitops/` e `bash gitops/bootstrap/01-aplicar-padrao-argocd.sh`.
4. Conferir o cluster inteiro: `bash gitops/bootstrap/09-auditar-cluster-argocd.sh`.

## O que o `main.yml` faz

- Build com `--provenance=false --sbom=false`, tags `X.Y` e `latest` (tag Git `<componente>-X.Y`).
- Garante o repositório no OCIR (cria se faltar; depois traga-o para o state com `./sync_terraform_state.sh importar`).
- Aplica a infraestrutura base (Namespace, Service, Ingress e, quando houver, ConfigMap/PostgreSQL/PVC) e o `oci-registry-secret`.
- Em vez de `kubectl apply`/`rollout restart` do Deployment, o passo **"Fixar versão no GitOps"** troca o `newTag` num worktree criado a partir de `origin/master`, comita `deploy: <componente> <versão> [skip ci]` e faz push (até 5 tentativas, espera aleatória). O ArgoCD sincroniza.
- Congelar um app: acrescente na `kustomization.yaml` a linha `# chirpstack-release-congelado: <motivo>` — a imagem é publicada, mas o `newTag` não muda.
- `concurrency` com `cancel-in-progress: false`: duas execuções não disputam o mesmo push. Cada deploy avança o `origin/master`; faça `git pull --rebase` antes do próximo push (o `force_deploy.sh` já faz).
