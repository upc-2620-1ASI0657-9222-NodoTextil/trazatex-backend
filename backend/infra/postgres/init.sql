CREATE ROLE :"orgaccess_user" LOGIN PASSWORD :'orgaccess_password';
CREATE DATABASE orgaccess_db OWNER :"orgaccess_user";
REVOKE ALL ON DATABASE orgaccess_db FROM PUBLIC;

CREATE ROLE :"production_user" LOGIN PASSWORD :'production_password';
CREATE DATABASE production_db OWNER :"production_user";
REVOKE ALL ON DATABASE production_db FROM PUBLIC;

CREATE ROLE :"quality_user" LOGIN PASSWORD :'quality_password';
CREATE DATABASE quality_db OWNER :"quality_user";
REVOKE ALL ON DATABASE quality_db FROM PUBLIC;

CREATE ROLE :"notification_user" LOGIN PASSWORD :'notification_password';
CREATE DATABASE notification_db OWNER :"notification_user";
REVOKE ALL ON DATABASE notification_db FROM PUBLIC;

CREATE ROLE :"analytics_user" LOGIN PASSWORD :'analytics_password';
CREATE DATABASE analytics_db OWNER :"analytics_user";
REVOKE ALL ON DATABASE analytics_db FROM PUBLIC;

REVOKE ALL ON DATABASE postgres FROM PUBLIC;
REVOKE ALL ON DATABASE template1 FROM PUBLIC;
