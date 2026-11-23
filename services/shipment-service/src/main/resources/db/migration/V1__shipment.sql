CREATE TABLE shipments (
  id              UUID PRIMARY KEY,
  customer_id     UUID         NOT NULL,
  driver_id       UUID,
  status          VARCHAR(24)  NOT NULL,
  pickup_lat      DOUBLE PRECISION NOT NULL,
  pickup_lng      DOUBLE PRECISION NOT NULL,
  dropoff_lat     DOUBLE PRECISION NOT NULL,
  dropoff_lng     DOUBLE PRECISION NOT NULL,
  pickup_address  VARCHAR(300) NOT NULL,
  dropoff_address VARCHAR(300) NOT NULL,
  pickup_pin      VARCHAR(4)   NOT NULL,
  dispatch_rounds SMALLINT     NOT NULL DEFAULT 0,
  version         BIGINT       NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ  NOT NULL,
  updated_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_shipments_customer ON shipments (customer_id, created_at DESC);
CREATE INDEX ix_shipments_status ON shipments (status);

CREATE TABLE shipment_status_history (
  id          BIGSERIAL PRIMARY KEY,
  shipment_id UUID        NOT NULL REFERENCES shipments (id) ON DELETE CASCADE,
  from_status VARCHAR(24),
  to_status   VARCHAR(24) NOT NULL,
  actor_id    UUID,
  reason      VARCHAR(300),
  occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_history_shipment ON shipment_status_history (shipment_id, occurred_at);

-- Infraestructura compartida (definida por common/outbox e idempotencia)
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

CREATE TABLE idempotency_keys (
  key             VARCHAR(128) PRIMARY KEY,
  request_hash    VARCHAR(64) NOT NULL,
  response_status INTEGER,
  response_body   JSONB,
  created_at      TIMESTAMPTZ NOT NULL,
  expires_at      TIMESTAMPTZ NOT NULL
);

CREATE TABLE shedlock (
  name       VARCHAR(64)  PRIMARY KEY,
  lock_until TIMESTAMPTZ  NOT NULL,
  locked_at  TIMESTAMPTZ  NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);
