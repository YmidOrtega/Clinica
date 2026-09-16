# Resolución de precios

`POST /api/v1/price-quotes` responde cuánto vale un conjunto de servicios para un contrato en una fecha.
Es la única fuente de precios del sistema: billing no calcula tarifas, las pide.

```json
{"contractUuid": "…", "on": "2026-03-01", "services": [{"cupsCode": "890201", "quantity": 2}]}
```

## Orden de resolución

Para cada servicio se aplica la primera regla que corresponda:

1. **Paquete pactado** vigente en la fecha que incluya el código → el servicio va en cero y el paquete se
   cobra una sola vez, aunque cubra varios servicios de la misma consulta.
2. **Capitación o presupuesto global** (según la modalidad del contrato) → el servicio queda cubierto por
   el acuerdo, sin precio por evento.
3. **Excepción del contrato** vigente en la fecha para ese código → el precio pactado.
4. **Manual tarifario del contrato** → valor del ítem × valor de la unidad × factor del contrato.
5. Si nada aplica, el servicio se devuelve como `UNPRICED`: no hay tarifa para ese código, y quien factura
   decide qué hacer.

El contrato debe estar vigente en la fecha; si no, la consulta se rechaza con `CONTRACT_NOT_IN_FORCE`.

## Por qué la respuesta trae tanta referencia

Cada línea devuelve de dónde salió su precio: `origin`, la descripción, y el identificador de lo que lo
originó (la versión del manual, la excepción o el paquete). La cabecera trae además el manual, su versión,
su unidad, el valor de la unidad y el factor.

Eso es lo que permite explicar una factura años después, cuando el manual ya tenga otra versión y el
contrato otro factor: la factura guarda el precio **y** la referencia con la que se calculó. Por eso las
versiones de manual no se reescriben y las excepciones se revocan en vez de borrarse.

## Redondeo

Las conversiones a pesos y el precio unitario se redondean a dos decimales con redondeo al alza en el
medio (`HALF_UP`), y el total de la línea es el precio unitario ya redondeado por la cantidad.
