#!/usr/bin/env bash
# Se ejecuta en la EC2 (vía SSH desde GitHub Actions). Es idempotente: se puede correr las veces que sea necesario.
set -euo pipefail
: "${IMAGEN:?falta IMAGEN}" "${TAG:?falta TAG}" "${S3_BUCKET:?falta S3_BUCKET}" "${EFS_DNS:?falta EFS_DNS}"

CONTENEDOR=eventos-service
MONTAJE=/mnt/efs
DATOS=/opt/eventos-service/data
OPCIONES_NFS=nfsvers=4.1,rsize=1048576,wsize=1048576,hard,timeo=600,retrans=2,noresvport

echo "== 1. Docker y cliente NFS"
command -v docker >/dev/null || { sudo dnf install -y docker; sudo systemctl enable --now docker; }
command -v mount.nfs4 >/dev/null || sudo dnf install -y nfs-utils

echo "== 2. EFS montado en $MONTAJE"
sudo mkdir -p "$MONTAJE"
if ! mountpoint -q "$MONTAJE"; then
  sudo mount -t nfs4 -o "$OPCIONES_NFS" "$EFS_DNS:/" "$MONTAJE"
fi
# Montaje persistente: vuelve solo si el laboratorio reinicia la instancia
if ! grep -q " $MONTAJE nfs4 " /etc/fstab; then
  echo "$EFS_DNS:/ $MONTAJE nfs4 $OPCIONES_NFS,_netdev,nofail 0 0" | sudo tee -a /etc/fstab >/dev/null
fi
sudo chown 1000:1000 "$MONTAJE"
df -h "$MONTAJE"

echo "== 3. Carpeta local para la base de datos H2"
sudo mkdir -p "$DATOS"
sudo chown 1000:1000 "$DATOS"

echo "== 4. Contenedor $IMAGEN:$TAG"
if [ -n "${DH_TOKEN:-}" ]; then
  echo "$DH_TOKEN" | sudo docker login -u "$DH_USER" --password-stdin >/dev/null
fi
sudo docker pull "$IMAGEN:$TAG"
sudo docker logout >/dev/null 2>&1 || true
sudo docker rm -f "$CONTENEDOR" >/dev/null 2>&1 || true
sudo docker run -d --name "$CONTENEDOR" --restart unless-stopped -p 8080:8080 \
  -v "$MONTAJE":/app/efs \
  -v "$DATOS":/app/data \
  -e S3_BUCKET="$S3_BUCKET" \
  -e AWS_REGION=us-east-1 \
  "$IMAGEN:$TAG"

echo "== 5. Verificación"
for intento in $(seq 1 40); do
  if curl -fsS http://localhost:8080/actuator/health; then
    echo
    sudo docker ps --filter "name=$CONTENEDOR"
    sudo docker image prune -f >/dev/null
    exit 0
  fi
  sleep 3
done
echo "La aplicación no respondió a tiempo" >&2
sudo docker logs --tail 80 "$CONTENEDOR" >&2
exit 1
