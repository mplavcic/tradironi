CREATE USER keycloak_user WITH PASSWORD 'keycloak_password';
CREATE DATABASE keycloak OWNER keycloak_user;
GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak_user;
