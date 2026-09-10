"""API REST de PRICE para práctica local: HTTP/JSON, tokens, CRUD y PDF.

Arranque: python server.py --seed-demo
"""
import argparse
import copy
import hashlib
import json
import logging
import os
import re
import secrets
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse, parse_qs
from database import Database, initialize, ROOT
from security import hash_password, verify_password, token_hash
from validation import ApiError, need, establishment, fiscalization
from pdf_acta import generate

LOG=logging.getLogger('price')


def now():
    return datetime.now(timezone.utc).isoformat(timespec='seconds')


def dumps(value):
    return json.dumps(value,ensure_ascii=False,separators=(',',':'))


def seed(demo=False):
    catalogs=json.loads((ROOT/'catalogos.json').read_text(encoding='utf-8'))
    with Database() as db:
        for product in catalogs['productos']:
            if not db.one('SELECT id FROM productos WHERE id=?',(product['id'],)):
                db.execute('INSERT INTO productos(id,nombre,unidad) VALUES(?,?,?)',tuple(product[k] for k in ['id','nombre','unidad']))
        for item in catalogs['incumplimientos']:
            if not db.one('SELECT id FROM incumplimientos_catalogo WHERE id=?',(item['id'],)):
                db.execute('INSERT INTO incumplimientos_catalogo(id,descripcion,base_legal) VALUES(?,?,?)',tuple(item[k] for k in ['id','descripcion','base_legal']))
        if demo:
            for user,name,role in [('admin','ADMINISTRADOR DE PRUEBA','ADMIN'),('fiscalizador','FISCALIZADOR DE PRUEBA','FISCALIZADOR'),('consulta','USUARIO DE CONSULTA','CONSULTA')]:
                if not db.one('SELECT id FROM usuarios WHERE usuario=?',(user,)):
                    db.execute('INSERT INTO usuarios(usuario,nombre,dni,rol,password_hash) VALUES(?,?,?,?,?)',(user,name,'00000000',role,hash_password('Price2026!')))


def public_user(user):
    return {k:user[k] for k in ['id','usuario','nombre','dni','rol']}


def audit(db,user,table,uuid,action,detail):
    db.execute('INSERT INTO historial(entidad,entidad_uuid,accion,usuario_id,fecha,detalle) VALUES(?,?,?,?,?,?)',(table,uuid,action,user['id'],now(),dumps(detail)))


def lookup(db,table,identifier):
    # table is chosen only from constant route names, never from raw SQL input.
    row=db.one('SELECT * FROM '+table+' WHERE '+('id' if identifier.isdigit() else 'uuid')+'=?',(identifier,))
    need(row is not None,'Registro no encontrado.',404)
    return row


def record(row):
    result=json.loads(row['payload'])
    result.update(id=row['id'],version=row['version'],actualizado=row['actualizado'])
    if 'usuario_id' in row:
        result.update(usuario_id=row['usuario_id'],estado=row['estado'])
    return result


def can_write(user,row=None):
    need(user['rol'] in ['ADMIN','FISCALIZADOR'],'Su rol solo permite consultar.',403)
    if row and 'usuario_id' in row:
        need(user['rol']=='ADMIN' or row['usuario_id']==user['id'],'Solo el autor o ADMIN puede modificar esta fiscalización.',403)


def save_children(db, fid, data, user):
    # One transaction includes header, child rows, facts and audit.
    old={r['incumplimiento_id']:r for r in db.all('SELECT fi.incumplimiento_id,h.texto,h.fecha,h.usuario_id FROM fiscalizacion_incumplimientos fi JOIN hechos_verificados h ON h.fiscalizacion_incumplimiento_id=fi.id WHERE fi.fiscalizacion_id=?',(fid,))}
    for table in ['precios','verificaciones','fiscalizacion_incumplimientos','observaciones','firmas','documentos']:
        db.execute('DELETE FROM '+table+' WHERE fiscalizacion_id=?',(fid,))
    for p in data.get('precios',[]):
        db.execute('INSERT INTO precios(fiscalizacion_id,producto_id,fila,marca,price,publicado,surtidor,descuento) VALUES(?,?,?,?,?,?,?,?)',(fid,p['producto_id'],p.get('fila',0),p.get('marca',''),*[p.get(k) or None for k in ['price','publicado','surtidor','descuento']]))
    v=data.get('verificaciones',{})
    db.execute('INSERT INTO verificaciones(fiscalizacion_id,telefono_publicado,telefono_actualizado,horario_publicado) VALUES(?,?,?,?)',(fid,*[v.get(k) for k in ['telefono_publicado','telefono_actualizado','horario_publicado']]))
    for item in data.get('incumplimientos',[]):
        cur=db.execute('INSERT INTO fiscalizacion_incumplimientos(fiscalizacion_id,incumplimiento_id) VALUES(?,?)',(fid,item['incumplimiento_id']))
        previous=old.get(item['incumplimiento_id'])
        unchanged=previous and previous['texto']==item['hecho']
        item['fecha']=previous['fecha'] if unchanged else now()
        item['usuario_id']=previous['usuario_id'] if unchanged else user['id']
        db.execute('INSERT INTO hechos_verificados(fiscalizacion_incumplimiento_id,texto,fecha,usuario_id) VALUES(?,?,?,?)',(cur.lastrowid,item['hecho'],item['fecha'],item['usuario_id']))
    d=data.get('datos',{})
    db.execute('INSERT INTO observaciones(fiscalizacion_id,otras,documentacion,observaciones,negativa) VALUES(?,?,?,?,?)',(fid,*[d.get(k,'') for k in ['otras','documentacion','observaciones','negativa']]))
    for kind,png in data.get('firmas',{}).items():
        if png:
            db.execute('INSERT INTO firmas(fiscalizacion_id,tipo,imagen_base64,fecha) VALUES(?,?,?,?)',(fid,kind,png,now()))
    for doc in data.get('documentos',[]):
        db.execute('INSERT INTO documentos(fiscalizacion_id,nombre,contenido_base64,fecha) VALUES(?,?,?,?)',(fid,doc['nombre'],doc['contenido'],now()))


def save_establishment(db,user,data,identifier=None):
    can_write(user)
    establishment(data)
    old=lookup(db,'establecimientos',identifier) if identifier else db.one('SELECT * FROM establecimientos WHERE uuid=?',(data['uuid'],))
    if old and not identifier:
        return record(old),200  # UUID idempotente: un reintento no crea otra fila.
    if old:
        need(data['uuid']==old['uuid'],'No se puede cambiar el UUID.',409)
        need(data.get('version')==old['version'],'Conflicto de versión. Conserve sus cambios y revise el registro del servidor.',409)
    duplicate=db.one('SELECT id FROM establecimientos WHERE codigo=?',(data['codigo'],))
    need(not duplicate or old and duplicate['id']==old['id'],'Ya existe un establecimiento con ese código.',409)
    stamp=now()
    if old:
        cur=db.execute('UPDATE establecimientos SET codigo=?,ruc=?,agente=?,payload=?,version=version+1,actualizado=? WHERE id=? AND version=?',(data['codigo'],data['ruc'],data['agente'],dumps(data),stamp,old['id'],old['version']))
        need(cur.rowcount==1,'El registro cambió durante la operación.',409)
        rid=old['id']
    else:
        rid=db.execute('INSERT INTO establecimientos(uuid,codigo,ruc,agente,payload,actualizado) VALUES(?,?,?,?,?,?)',(data['uuid'],data['codigo'],data['ruc'],data['agente'],dumps(data),stamp)).lastrowid
    audit(db,user,'establecimientos',data['uuid'],'ACTUALIZAR' if old else 'CREAR',{'codigo':data['codigo']})
    return record(lookup(db,'establecimientos',str(rid))),200 if old else 201


def save_fiscalization(db,user,data,identifier=None):
    can_write(user)
    fiscalization(data)
    old=lookup(db,'fiscalizaciones',identifier) if identifier else db.one('SELECT * FROM fiscalizaciones WHERE uuid=?',(data['uuid'],))
    if old and not identifier:
        can_write(user,old)
        return record(old),200
    if old:
        can_write(user,old)
        need(data['uuid']==old['uuid'],'No se puede cambiar el UUID.',409)
        need(data.get('version')==old['version'],'Conflicto de versión. Conserve sus cambios y revise el registro del servidor.',409)
        if old['estado'] in ['FINALIZADA','ACTA GENERADA']:
            previous=record(old)
            for key in ['datos','fecha','hora_apertura','hora_cierre','precios','verificaciones','firmas','documentos','latitud','longitud','expediente','establecimiento_uuid']:
                need(data.get(key)==previous.get(key),'El acta está cerrada y no permite editar datos.',409)
            need([(i['incumplimiento_id'],i['hecho']) for i in data.get('incumplimientos',[])]==[(i['incumplimiento_id'],i['hecho']) for i in previous.get('incumplimientos',[])],'El acta está cerrada.',409)
            need(data['estado'] in ['FINALIZADA','ACTA GENERADA'] and not(old['estado']=='ACTA GENERADA' and data['estado']=='FINALIZADA'),'No se puede retroceder el estado de un acta cerrada.',409)
    est=lookup(db,'establecimientos',data['establecimiento_uuid'])
    duplicate=db.one('SELECT id FROM fiscalizaciones WHERE expediente=?',(data['expediente'],))
    need(not duplicate or old and duplicate['id']==old['id'],'Ya existe una fiscalización con ese expediente.',409)
    if user['rol']!='ADMIN':
        need(data.get('datos',{}).get('nombre_fiscal',user['nombre'])==user['nombre'] and data.get('datos',{}).get('dni_fiscal',user['dni'])==user['dni'],'Los datos del fiscalizador deben coincidir con su sesión.',403)
    if data['estado'] in ['FINALIZADA','ACTA GENERADA']:
        generate(data)  # Rechaza texto que no cabe, antes de cerrar o confirmar la transacción.
    stamp=now()
    data['establecimiento_id']=est['id']
    data['usuario_id']=old['usuario_id'] if old else user['id']
    if old:
        cur=db.execute('UPDATE fiscalizaciones SET expediente=?,establecimiento_id=?,estado=?,fecha=?,payload=?,actualizado=?,version=version+1 WHERE id=? AND version=?',(data['expediente'],est['id'],data['estado'],data.get('fecha',''),dumps(data),stamp,old['id'],old['version']))
        need(cur.rowcount==1,'El registro cambió durante la operación.',409)
        fid=old['id']
    else:
        fid=db.execute('INSERT INTO fiscalizaciones(uuid,expediente,establecimiento_id,usuario_id,estado,fecha,payload,actualizado) VALUES(?,?,?,?,?,?,?,?)',(data['uuid'],data['expediente'],est['id'],user['id'],data['estado'],data.get('fecha',''),dumps(data),stamp)).lastrowid
    save_children(db,fid,data,user)
    db.execute('UPDATE fiscalizaciones SET payload=? WHERE id=?',(dumps(data),fid))
    audit(db,user,'fiscalizaciones',data['uuid'],'ACTUALIZAR' if old else 'CREAR',{'estado':data['estado'],'expediente':data['expediente'],'hechos':data.get('incumplimientos',[])})
    return record(lookup(db,'fiscalizaciones',str(fid))),200 if old else 201


class Handler(BaseHTTPRequestHandler):
    server_version='PRICE/2.0'

    def log_message(self, fmt, *args):
        LOG.info('%s %s',self.address_string(),fmt % args)

    def respond(self,status,data,content_type='application/json; charset=utf-8'):
        raw=data if isinstance(data,bytes) else dumps(data).encode()
        self.send_response(status)
        self.send_header('Content-Type',content_type)
        self.send_header('Content-Length',str(len(raw)))
        self.send_header('Cache-Control','no-store')
        self.send_header('X-Content-Type-Options','nosniff')
        if content_type=='application/pdf':
            self.send_header('Content-Disposition','inline; filename="ACTA_PRICE.pdf"')
        self.end_headers()
        self.wfile.write(raw)

    def body(self):
        try:
            length=int(self.headers.get('Content-Length','0'))
            need(0<length<=8_000_000,'La solicitud está vacía o excede 8 MB.',413)
            result=json.loads(self.rfile.read(length))
            need(isinstance(result,dict),'Envíe un objeto JSON.',400)
            return result
        except (ValueError,UnicodeError):
            raise ApiError(400,'JSON inválido.')

    def user(self,db):
        authorization=self.headers.get('Authorization','')
        need(authorization.startswith('Bearer '),'Inicie sesión.',401)
        user=db.one('SELECT u.* FROM usuarios u JOIN tokens t ON t.usuario_id=u.id WHERE t.token_hash=? AND t.expires_at>?',(token_hash(authorization[7:]),int(time.time())))
        need(user,'Sesión vencida. Inicie sesión nuevamente.',401)
        return user

    def run_request(self):
        try:
            result,status,mime=self.route()
            self.respond(status,result,mime)
        except ApiError as e:
            self.respond(e.status,{'message':e.message})
        except Exception as e:
            # No se exponen consultas, rutas internas ni credenciales al cliente.
            if 'UNIQUE' in str(e).upper() or 'DUPLICATE' in str(e).upper():
                self.respond(409,{'message':'El registro ya existe. Actualice la lista.'})
            else:
                LOG.exception('Error de solicitud')
                self.respond(500,{'message':'No se pudo completar la operación. Consulte el registro del servidor.'})

    def route(self):
        uri=urlparse(self.path)
        path=uri.path.rstrip('/')
        query=parse_qs(uri.query)
        method=self.command
        mime='application/json; charset=utf-8'
        if path=='/api/health' and method=='GET':
            return {'status':'ok','api':'PRICE','database':'sqlite'},200,mime
        with Database() as db:
            if path=='/api/login' and method=='POST':
                data=self.body()
                need(isinstance(data.get('usuario'),str) and isinstance(data.get('password'),str),'Ingrese usuario y contraseña.')
                need(len(data['usuario'])<=80 and len(data['password'])<=200,'Credenciales inválidas.',401)
                user=db.one('SELECT * FROM usuarios WHERE usuario=?',(data['usuario'].strip(),))
                need(user and verify_password(data['password'],user['password_hash']),'Usuario o contraseña incorrectos.',401)
                token=secrets.token_urlsafe(32)
                expiration=int(time.time())+8*3600
                db.execute('DELETE FROM tokens WHERE expires_at<?',(int(time.time()),))
                db.execute('INSERT INTO tokens(token_hash,usuario_id,expires_at) VALUES(?,?,?)',(token_hash(token),user['id'],expiration))
                return {'token':token,'expires_at':expiration,'usuario':public_user(user)},200,mime
            user=self.user(db)
            if path=='/api/logout' and method=='POST':
                db.execute('DELETE FROM tokens WHERE token_hash=?',(token_hash(self.headers['Authorization'][7:]),))
                return {'message':'Sesión cerrada.'},200,mime
            if path=='/api/me' and method=='GET':
                return public_user(user),200,mime
            if path in ['/api/incumplimientos','/api/productos'] and method=='GET':
                table='incumplimientos_catalogo' if path.endswith('incumplimientos') else 'productos'
                return db.all('SELECT * FROM '+table+' ORDER BY id'),200,mime
            match=re.fullmatch(r'/api/(establecimientos|fiscalizaciones)(?:/([A-Za-z0-9_-]+))?(?:/(acta/pdf|incumplimientos|historial))?',path)
            need(match,'Endpoint no encontrado.',404)
            table,identifier,action=match.groups()
            if action:
                need(table=='fiscalizaciones' and identifier,'Endpoint no encontrado.',404)
                old=lookup(db,table,identifier)
                if action=='historial' and method=='GET':
                    return db.all('SELECT accion,usuario_id,fecha,detalle FROM historial WHERE entidad=? AND entidad_uuid=? ORDER BY id',(table,old['uuid'])),200,mime
                if action=='acta/pdf' and method=='GET':
                    need(old['estado'] in ['FINALIZADA','ACTA GENERADA'],'Finalice la fiscalización antes de generar el PDF.',422)
                    data=record(old)
                    fiscalization(data)
                    pdf=generate(data)
                    # CONSULTA descarga documentos ya cerrados sin modificar el estado.
                    if old['estado']=='FINALIZADA' and user['rol']!='CONSULTA':
                        can_write(user,old)
                        db.execute('UPDATE fiscalizaciones SET estado=?,version=version+1,actualizado=? WHERE id=?',('ACTA GENERADA',now(),old['id']))
                        audit(db,user,table,old['uuid'],'GENERAR PDF',{'paginas':2})
                    return pdf,200,'application/pdf'
                if action=='incumplimientos' and method=='POST':
                    item=self.body()
                    data=record(old)
                    need(not any(i['incumplimiento_id']==item.get('incumplimiento_id') for i in data.get('incumplimientos',[])),'El incumplimiento ya está registrado. Edítelo con PUT.',409)
                    data.setdefault('incumplimientos',[]).append(item)
                    result,status=save_fiscalization(db,user,data,identifier)
                    return result,status,mime
                raise ApiError(405,'Método no permitido.')
            if method=='GET':
                if identifier:
                    return record(lookup(db,table,identifier)),200,mime
                rows=[record(row) for row in db.all('SELECT * FROM '+table+' ORDER BY id DESC')]
                q=query.get('q',[''])[0].strip().casefold()
                if q:
                    rows=[r for r in rows if q in dumps({k:v for k,v in r.items() if k not in ['firmas','documentos']}).casefold()]
                return rows,200,mime
            if method in ['POST','PUT']:
                need((method=='POST' and identifier is None) or (method=='PUT' and identifier),'Use POST para crear y PUT con ID para editar.',405)
                save=save_establishment if table=='establecimientos' else save_fiscalization
                result,status=save(db,user,self.body(),identifier)
                return result,status,mime
            if method=='DELETE' and identifier:
                need(user['rol']=='ADMIN','Solo ADMIN puede eliminar registros.',403)
                old=lookup(db,table,identifier)
                supplied=self.headers.get('If-Match','')
                need(supplied==str(old['version']),'Para eliminar envíe If-Match con la versión actual.',409)
                if table=='establecimientos':
                    need(not db.one('SELECT id FROM fiscalizaciones WHERE establecimiento_id=?',(old['id'],)),'El establecimiento tiene fiscalizaciones asociadas.',409)
                else:
                    need(old['estado'] not in ['FINALIZADA','ACTA GENERADA'],'No se puede eliminar un acta cerrada.',409)
                db.execute('DELETE FROM '+table+' WHERE id=? AND version=?',(old['id'],old['version']))
                audit(db,user,table,old['uuid'],'ELIMINAR',{'id':old['id']})
                return {'message':'Registro eliminado.'},200,mime
            raise ApiError(405,'Método no permitido.')

    do_GET=run_request
    do_POST=run_request
    do_PUT=run_request
    do_DELETE=run_request


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--seed-demo',action='store_true')
    parser.add_argument('--port',type=int,default=int(os.getenv('API_PORT','8000')))
    parser.add_argument('--host',default=os.getenv('API_HOST','0.0.0.0'))
    args=parser.parse_args()
    logging.basicConfig(level=logging.INFO,format='%(asctime)s %(message)s')
    initialize()
    seed(args.seed_demo)
    print('PRICE API en http://127.0.0.1:'+str(args.port)+'/api/health',flush=True)
    if args.seed_demo:
        print('Usuarios de práctica: admin, fiscalizador, consulta. Clave inicial: Price2026!',flush=True)
    server=ThreadingHTTPServer((args.host,args.port),Handler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__=='__main__':
    main()
