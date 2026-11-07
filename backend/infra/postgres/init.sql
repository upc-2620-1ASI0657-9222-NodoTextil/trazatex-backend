-- Una base de datos por servicio (database per service), cada una con su propio usuario.
-- Se ejecuta vía init.sh, que define las variables <servicio>_user y <servicio>_password desde .env.
-- Solo corre al crear el volumen: para volver a ejecutarlo hay que borrar el volumen postgres_data.

-- organization-access
CREATE ROLE :"orgaccess_user" LOGIN PASSWORD :'orgaccess_password';
CREATE DATABASE orgaccess_db OWNER :"orgaccess_user";
REVOKE ALL ON DATABASE orgaccess_db FROM PUBLIC;

-- production
CREATE ROLE :"production_user" LOGIN PASSWORD :'production_password';
CREATE DATABASE production_db OWNER :"production_user";
REVOKE ALL ON DATABASE production_db FROM PUBLIC;

-- quality (siguiente sprint)
CREATE ROLE :"quality_user" LOGIN PASSWORD :'quality_password';
CREATE DATABASE quality_db OWNER :"quality_user";
REVOKE ALL ON DATABASE quality_db FROM PUBLIC;

-- notification (siguiente sprint)
CREATE ROLE :"notification_user" LOGIN PASSWORD :'notification_password';
CREATE DATABASE notification_db OWNER :"notification_user";
REVOKE ALL ON DATABASE notification_db FROM PUBLIC;

-- analytics (siguiente sprint)
CREATE ROLE :"analytics_user" LOGIN PASSWORD :'analytics_password';
CREATE DATABASE analytics_db OWNER :"analytics_user";
REVOKE ALL ON DATABASE analytics_db FROM PUBLIC;

-- Por defecto PUBLIC puede conectarse a cualquier base: se cierra también en las bases de sistema.
-- (El superusuario no se ve afectado.)
REVOKE ALL ON DATABASE postgres FROM PUBLIC;
REVOKE ALL ON DATABASE template1 FROM PUBLIC;
