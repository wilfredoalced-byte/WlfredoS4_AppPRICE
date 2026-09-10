-- Esquema de LocalStore.java. El contenido completo se guarda como JSON.
-- scope separa cada servidor y cada usuario.
CREATE TABLE registros(scope TEXT NOT NULL,tipo TEXT NOT NULL,uuid TEXT NOT NULL,payload TEXT NOT NULL,dirty INTEGER NOT NULL DEFAULT 0,revision INTEGER NOT NULL DEFAULT 0,error TEXT NOT NULL DEFAULT '',PRIMARY KEY(scope,tipo,uuid));
CREATE TABLE catalogos(scope TEXT NOT NULL,tipo TEXT NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(scope,tipo));
CREATE TABLE recuperacion(scope TEXT NOT NULL,uuid TEXT NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(scope,uuid));
