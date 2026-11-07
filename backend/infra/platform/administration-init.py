import base64
import json
import os
import re
import ssl
import time
import urllib.error
import urllib.request


def request(url, data=None, token=None, credentials=None, method=None):
    headers={"Content-Type":"application/json"}
    if token:
        headers["Authorization"]="Bearer "+token
    if credentials:
        headers["Authorization"]="Basic "+base64.b64encode((credentials[0]+":"+credentials[1]).encode()).decode()
    req=urllib.request.Request(url,data=None if data is None else json.dumps(data).encode(),headers=headers,method=method)
    # El certificado propio de Portainer se acepta solo para su DNS interno fijo.
    context=ssl._create_unverified_context() if url.startswith("https://portainer:9443/") else None
    with urllib.request.urlopen(req,context=context,timeout=10) as response:
        return json.load(response)


def neo4j_user():
    target=os.environ.get("NEO4J_USER","neo4j")
    password=os.environ["NEO4J_PASSWORD"]
    if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*",target):
        raise ValueError("Nombre de usuario Neo4j no válido")
    url="http://neo4j:7474/db/system/tx/commit"
    def query(user, statement, parameters=None):
        result=request(url,{"statements":[{"statement":statement,"parameters":parameters or {}}]},credentials=(user,password))
        if result.get("errors"):
            raise RuntimeError("Neo4j rechazó la configuración de usuario")
        return result
    try:
        query(target,"SHOW CURRENT USER")
        if target!="neo4j":
            query(target,"DROP USER neo4j IF EXISTS")
        return
    except urllib.error.HTTPError as error:
        if error.code!=401:
            raise
    query("neo4j","CREATE USER `"+target+"` IF NOT EXISTS SET PASSWORD $password CHANGE NOT REQUIRED",{"password":password})
    query(target,"SHOW CURRENT USER")
    if target!="neo4j":
        query(target,"DROP USER neo4j IF EXISTS")


def portainer_user():
    target=os.environ.get("ADMIN_USERNAME","admin")
    password=os.environ["PORTAINER_PASSWORD"]
    url="https://portainer:9443/api"
    def login(user):
        return request(url+"/auth",{"Username":user,"Password":password})["jwt"]
    try:
        login(target)
        return
    except urllib.error.HTTPError as error:
        if error.code not in (400,401,422):
            raise
    token=login("admin")
    users=request(url+"/users",token=token)
    current=next(user for user in users if user["Username"]=="admin")
    request(url+"/users/"+str(current["Id"]),{"Username":target,"Role":1},token=token,method="PUT")
    login(target)


if __name__=="__main__":
    for configure in (neo4j_user,portainer_user):
        for attempt in range(30):
            try:
                configure()
                print(configure.__name__+": configuración comprobada",flush=True)
                break
            except (urllib.error.URLError,TimeoutError):
                if attempt==29:
                    raise SystemExit(configure.__name__+": no disponible; revisa las credenciales privadas y el servicio")
                time.sleep(2)
            except Exception:
                raise SystemExit(configure.__name__+": no se pudo aplicar la configuración; no se modificaron datos operativos")
