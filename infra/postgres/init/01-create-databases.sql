-- Crea una base por servicio. En produccion serian instancias separadas;
-- en local compartimos un contenedor para no gastar 4 x 300 MB de RAM (ver ADR-0002).
CREATE DATABASE authdb;
CREATE DATABASE shipmentdb;
CREATE DATABASE dispatchdb;
CREATE DATABASE notifdb;

GRANT ALL PRIVILEGES ON DATABASE authdb TO enruta;
GRANT ALL PRIVILEGES ON DATABASE shipmentdb TO enruta;
GRANT ALL PRIVILEGES ON DATABASE dispatchdb TO enruta;
GRANT ALL PRIVILEGES ON DATABASE notifdb TO enruta;
