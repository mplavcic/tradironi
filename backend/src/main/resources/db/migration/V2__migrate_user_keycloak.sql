SET search_path TO tradironi_schema;

ALTER TABLE tradironi_user DROP COLUMN IF EXISTS username;
ALTER TABLE tradironi_user DROP COLUMN IF EXISTS surname;
ALTER TABLE tradironi_user DROP COLUMN IF EXISTS name;
ALTER TABLE tradironi_user DROP COLUMN IF EXISTS email;
ALTER TABLE tradironi_user DROP COLUMN IF EXISTS password;
ALTER TABLE tradironi_user DROP COLUMN IF EXISTS role;

ALTER TABLE tradironi_user ADD COLUMN keycloak_id UUID NOT NULL;

ALTER TABLE tradironi_user ADD CONSTRAINT uq_tradironi_user_keycloak_id UNIQUE (keycloak_id);
