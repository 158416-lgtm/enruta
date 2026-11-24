-- Antes solo podia haber UNA oferta pendiente por envio (oferta secuencial).
-- Ahora el despacho ofrece a los 3 mejores a la vez; sigue habiendo una sola por conductor.
DROP INDEX IF EXISTS uq_offers_pending;

CREATE UNIQUE INDEX uq_offers_pending_driver
  ON offers (shipment_id, driver_id)
  WHERE status = 'CREATED';
