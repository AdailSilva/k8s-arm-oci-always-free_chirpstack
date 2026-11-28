# =====================================================================
#  ChirpStack Manager Web - Angular + Avalon servido pelo NGINX (sem root)
#  O NGINX (nginx.conf) também encaminha /api para o backend
#  (Service api-chirpstack-manager-backend-service:8083).
#
#  Perfil escolhido no build (padrão: production):
#    docker build -t app-chirpstack-manager-frontend app_chirpstack_manager_frontend/
#    docker build --build-arg PERFIL=staging -t app-chirpstack-manager-frontend:staging app_chirpstack_manager_frontend/
#  Backend de cada perfil: development :8081 · staging :8082 · production :8083
#  (a imagem aponta para o backend de produção; nos outros perfis use o ng serve).
# =====================================================================
FROM node:22-alpine AS build
ARG PERFIL=production
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration ${PERFIL}

FROM nginxinc/nginx-unprivileged:1.27-alpine

# Metadados da imagem
LABEL maintainer="AdailSilva <adail101@hotmail.com>" \
      app="app-chirpstack-manager-frontend" \
      description="ChirpStack-Manager – Frontend Angular"

COPY --from=build /src/dist/chirpstack-manager/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf
COPY mime.types /etc/nginx/mime.types
EXPOSE 8080
