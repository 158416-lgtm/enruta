CREATE TABLE notifications (
  id              UUID PRIMARY KEY,
  shipment_id     UUID,
  channel         VARCHAR(16)  NOT NULL,
  recipient       VARCHAR(255) NOT NULL,
  template        VARCHAR(64)  NOT NULL,
  variables       JSONB        NOT NULL,
  status          VARCHAR(16)  NOT NULL,
  attempts        SMALLINT     NOT NULL DEFAULT 0,
  last_error      VARCHAR(500),
  next_attempt_at TIMESTAMPTZ,
  created_at      TIMESTAMPTZ  NOT NULL,
  sent_at         TIMESTAMPTZ
);

CREATE INDEX ix_notifications_shipment ON notifications (shipment_id, created_at DESC);
CREATE INDEX ix_notifications_due ON notifications (next_attempt_at) WHERE status = 'PENDING';

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
