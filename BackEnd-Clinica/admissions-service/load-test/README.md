# Prueba de carga de admissions-service

Mide lo que el personal consulta sin parar mientras atiende: la ficha del episodio, el censo de una
ubicación, la cola de un servicio configurado y la búsqueda de episodios.

Las escrituras —admitir, mover de cama, egresar— **no** se miden aquí: ocurren una vez por episodio y
dejarían datos de prueba en la base. Lo que aprieta en una guardia son las lecturas de los tableros.

## Preparar datos

Con el stack arriba (`docker-compose.debug.yml` publica el puerto 8099), el guion de E2E deja una sede
con su cama, un servicio configurado y un episodio:

```sh
COMPOSE_PROJECT=<proyecto> sh ../../platform/e2e/admissions-e2e.sh
```

De su salida se toman los tres identificadores, o se consultan:

```sh
TOKEN=$(COMPOSE_PROJECT=<proyecto> E2E_TOKEN_TTL=3600 sh ../../platform/e2e/staff-token.sh RECEPTIONIST)
curl -s -X POST http://127.0.0.1:8099/api/v1/admissions/episodes/search -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"status":"ACTIVE"}' \
  | jq -c '.content[0] | {episode: .uuid, service: .configurationServiceUuid}'
```

## Ejecutar

```sh
k6 run -e BASE_URL=http://127.0.0.1:8099 -e TOKEN="$TOKEN" \
  -e LOCATION_UUID=<sede> -e CONFIGURED_SERVICE_UUID=<servicio> -e EPISODE_UUID=<episodio> \
  admissions-load.js
```

`MULTIPLIER=5` repite el mismo guion con cinco veces la carga (x5), que es como se compara con el resto
de servicios. `DURATION` cambia el tramo sostenido y `SUMMARY_FILE` guarda el detalle en un archivo.

## Umbrales

Menos del 1 % de peticiones fallidas y, en el percentil 95: 200 ms la ficha del episodio, 300 ms el censo
y la cola, 400 ms la búsqueda. El censo y la cola hacen dos consultas cada uno (las camas o las fases, y
después los episodios), así que se les da algo más de margen que a la ficha.
