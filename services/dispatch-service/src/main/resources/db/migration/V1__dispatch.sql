CREATE TABLE drivers (
  id            UUID PRIMARY KEY,
  full_name     VARCHAR(120)     NOT NULL,
  vehicle_type  VARCHAR(16)      NOT NULL,
  status        VARCHAR(16)      NOT NULL,
  current_lat   DOUBLE PRECISION,
  current_lng   DOUBLE PRECISION,
  rating        DOUBLE PRECISION NOT NULL,
  version       BIGINT           NOT NULL DEFAULT 0,
  updated_at    TIMESTAMPTZ      NOT NULL
);

CREATE INDEX ix_drivers_status ON drivers (status);

CREATE TABLE offers (
  id              UUID PRIMARY KEY,
  shipment_id     UUID             NOT NULL,
  driver_id       UUID             NOT NULL REFERENCES drivers (id),
  status          VARCHAR(16)      NOT NULL,
  expires_at      TIMESTAMPTZ      NOT NULL,
  round           SMALLINT         NOT NULL,
  distance_meters INTEGER,
  score           DOUBLE PRECISION NOT NULL,
  version         BIGINT           NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ      NOT NULL
);

-- Una sola oferta pendiente por envio.
CREATE UNIQUE INDEX uq_offers_pending ON offers (shipment_id) WHERE status = 'CREATED';
CREATE INDEX ix_offers_driver ON offers (driver_id, status);
CREATE INDEX ix_offers_expiry ON offers (expires_at) WHERE status = 'CREATED';

CREATE TABLE assignments (
  id          UUID PRIMARY KEY,
  shipment_id UUID        NOT NULL,
  driver_id   UUID        NOT NULL REFERENCES drivers (id),
  status      VARCHAR(16) NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL,
  ended_at    TIMESTAMPTZ
);

-- Ultima linea de defensa: no puede haber dos asignaciones activas del mismo envio.
CREATE UNIQUE INDEX uq_assignments_active ON assignments (shipment_id) WHERE status = 'ACTIVE';

CREATE TABLE dispatch_state (
  shipment_id      UUID PRIMARY KEY,
  pickup_lat       DOUBLE PRECISION NOT NULL,
  pickup_lng       DOUBLE PRECISION NOT NULL,
  phase            VARCHAR(16)      NOT NULL,
  round            SMALLINT         NOT NULL DEFAULT 0,
  current_offer_id UUID,
  updated_at       TIMESTAMPTZ      NOT NULL
);

CREATE TABLE outbox (
  id           UUID PRIMARY KEY,
  aggregate_id UUID        NOT NULL,
  event_type   VARCHAR(64) NOT NULL,
  topic        VARCHAR(64) NOT NULL,
  payload      JSONB       NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL,
  published_at TIMESTAMPTZ,
  attempts     SMALLINT    NOT NULL DEFAULT 0
);

CREATE INDEX ix_outbox_pending ON outbox (created_at) WHERE published_at IS NULL;

CREATE TABLE processed_events (
  event_id     UUID        NOT NULL,
  consumer     VARCHAR(64) NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (event_id, consumer)
);

CREATE TABLE shedlock (
  name       VARCHAR(64)  PRIMARY KEY,
  lock_until TIMESTAMPTZ  NOT NULL,
  locked_at  TIMESTAMPTZ  NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);
