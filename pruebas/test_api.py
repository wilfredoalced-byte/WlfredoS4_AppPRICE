"""Pruebas HTTP reales contra una base temporal. No modifica su base de trabajo."""
import copy
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import threading
import unittest
import urllib.request
import urllib.error
import uuid

ROOT=Path(__file__).resolve().parents[1]
temporary=tempfile.TemporaryDirectory(prefix='price-tests-')
os.environ['DB_DRIVER']='sqlite'
os.environ['SQLITE_PATH']=str(Path(temporary.name)/'test.sqlite3')
sys.path.insert(0,str(ROOT/'api_rest'))
from database import Database, initialize
from server import Handler, ThreadingHTTPServer, seed
from pypdf import PdfReader


class ApiTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        initialize();seed(True)
        cls.server=ThreadingHTTPServer(('127.0.0.1',0),Handler)
        cls.thread=threading.Thread(target=cls.server.serve_forever,daemon=True);cls.thread.start()
        cls.base='http://127.0.0.1:'+str(cls.server.server_port)+'/api/'
        cls.opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
        cls.est=json.loads((ROOT/'pruebas/establecimiento_demo.json').read_text(encoding='utf-8'))
        cls.fisc=json.loads((ROOT/'pruebas/fiscalizacion_demo.json').read_text(encoding='utf-8'))
        cls.tokens={}
        for role in ['admin','fiscalizador','consulta']:
            status,data=cls.request('POST','login',{'usuario':role,'password':'Price2026!'})
            assert status==200,(status,data)
            cls.tokens[role]=data['token']
        cls.request('POST','establecimientos',cls.est,'fiscalizador')

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown();cls.server.server_close();cls.thread.join();temporary.cleanup()

    @classmethod
    def request(cls,method,path,body=None,role=None,extra=None):
        headers={'Content-Type':'application/json'}
        if role:headers['Authorization']='Bearer '+cls.tokens[role]
        headers.update(extra or {})
        req=urllib.request.Request(cls.base+path,data=json.dumps(body,ensure_ascii=False).encode() if body is not None else None,method=method,headers=headers)
        try:
            with cls.opener.open(req,timeout=15) as response:
                raw=response.read()
                return response.status,raw if response.headers['Content-Type']=='application/pdf' else json.loads(raw)
        except urllib.error.HTTPError as e:
            return e.code,json.loads(e.read())

    def fresh(self,closed=False):
        f=copy.deepcopy(self.fisc);f['uuid']=str(uuid.uuid4());f['expediente']='TEST-'+f['uuid'][:12];f['estado']='FINALIZADA' if closed else 'EN PROCESO'
        return f

    def test_01_authentication_and_tokens(self):
        self.assertEqual(self.request('POST','login',{'usuario':'admin','password':'incorrecta'})[0],401)
        self.assertEqual(self.request('GET','establecimientos')[0],401)
        self.assertEqual(self.request('GET','me',role='admin')[1]['rol'],'ADMIN')

    def test_02_readonly_cannot_write(self):
        self.assertEqual(self.request('POST','establecimientos',self.est,'consulta')[0],403)
        self.assertEqual(self.request('POST','fiscalizaciones',self.fresh(),'consulta')[0],403)
        self.assertEqual(self.request('GET','establecimientos',role='consulta')[0],200)

    def test_03_catalogs(self):
        self.assertEqual(len(self.request('GET','incumplimientos',role='admin')[1]),6)
        self.assertEqual(len(self.request('GET','productos',role='admin')[1]),11)

    def test_04_establishment_crud(self):
        e=copy.deepcopy(self.est);e['uuid']=str(uuid.uuid4());e['codigo']='CRUD-'+e['uuid'][:8]
        status,e=self.request('POST','establecimientos',e,'admin');self.assertEqual(status,201)
        self.assertEqual(self.request('GET','establecimientos/'+str(e['id']),role='admin')[1]['uuid'],e['uuid'])
        e['telefono']='062123456';status,e=self.request('PUT','establecimientos/'+e['uuid'],e,'admin');self.assertEqual(status,200)
        self.assertEqual(e['version'],2)
        self.assertEqual(self.request('DELETE','establecimientos/'+e['uuid'],role='fiscalizador',extra={'If-Match':'2'})[0],403)
        self.assertEqual(self.request('DELETE','establecimientos/'+e['uuid'],role='admin')[0],409)
        self.assertEqual(self.request('DELETE','establecimientos/'+e['uuid'],role='admin',extra={'If-Match':'2'})[0],200)
        self.assertEqual(self.request('GET','establecimientos/'+e['uuid'],role='admin')[0],404)

    def test_05_duplicate_code_and_uuid(self):
        e=copy.deepcopy(self.est);e['uuid']=str(uuid.uuid4())
        self.assertEqual(self.request('POST','establecimientos',e,'admin')[0],409)
        a=self.request('POST','establecimientos',self.est,'admin')
        b=self.request('POST','establecimientos',self.est,'admin')
        self.assertEqual(a[1]['id'],b[1]['id'])

    def test_06_fiscalization_create_read_update_delete(self):
        f=self.fresh();status,f=self.request('POST','fiscalizaciones',f,'fiscalizador');self.assertEqual(status,201)
        self.assertEqual(self.request('GET','fiscalizaciones/'+f['uuid'],role='consulta')[1]['expediente'],f['expediente'])
        f['datos']['otras']='Texto actualizado';status,updated=self.request('PUT','fiscalizaciones/'+f['uuid'],f,'fiscalizador');self.assertEqual(status,200)
        self.assertEqual(updated['datos']['otras'],'Texto actualizado')
        self.assertEqual(self.request('PUT','fiscalizaciones/'+f['uuid'],f,'fiscalizador')[0],409)
        self.assertEqual(self.request('DELETE','fiscalizaciones/'+f['uuid'],role='admin',extra={'If-Match':str(updated['version'])})[0],200)

    def test_07_missing_expediente(self):
        f=self.fresh();f['expediente']='';self.assertEqual(self.request('POST','fiscalizaciones',f,'fiscalizador')[0],422)

    def test_08_bad_date_and_time(self):
        for key,value in [('fecha','31/02/2026'),('fecha','1/9/2026'),('hora_cierre','08:00'),('hora_apertura','25:00')]:
            with self.subTest(key=key,value=value):
                f=self.fresh();f[key]=value;self.assertEqual(self.request('POST','fiscalizaciones',f,'fiscalizador')[0],422)

    def test_09_price_validation(self):
        for value in ['-1','NaN','Infinity','10000','0.00001','no']:
            with self.subTest(value=value):
                f=self.fresh();f['precios'][0]['price']=value;self.assertEqual(self.request('POST','fiscalizaciones',f,'fiscalizador')[0],422)

    def test_10_finding_needs_evidence(self):
        f=self.fresh();f['incumplimientos'][0]['hecho']='';self.assertEqual(self.request('POST','fiscalizaciones',f,'fiscalizador')[0],422)

    def test_11_finalization_requires_checks_and_signatures(self):
        for key in ['checks','signature','name','price']:
            f=self.fresh(True)
            if key=='checks':f['verificaciones'].pop('telefono_publicado')
            if key=='signature':f['firmas']['fiscalizador']=''
            if key=='name':f['datos']['nombre_fiscal']=''
            if key=='price':f['precios']=[]
            with self.subTest(key=key):self.assertEqual(self.request('POST','fiscalizaciones',f,'fiscalizador')[0],422)

    def test_12_full_flow_pdf_two_pages(self):
        f=self.fresh(True);status,saved=self.request('POST','fiscalizaciones',f,'fiscalizador');self.assertEqual(status,201,saved)
        status,pdf=self.request('GET','fiscalizaciones/'+f['uuid']+'/acta/pdf',role='fiscalizador');self.assertEqual(status,200,pdf if status!=200 else '')
        reader=PdfReader(io.BytesIO(pdf));self.assertEqual(len(reader.pages),2)
        text='\n'.join(p.extract_text() for p in reader.pages);self.assertIn(f['expediente'],text);self.assertIn('15.50',text);self.assertIn('FISCALIZADOR DE PRUEBA',text)
        current=self.request('GET','fiscalizaciones/'+f['uuid'],role='fiscalizador')[1];self.assertEqual(current['estado'],'ACTA GENERADA')
        current['datos']['otras']='Intento de edición';self.assertEqual(self.request('PUT','fiscalizaciones/'+f['uuid'],current,'fiscalizador')[0],409)
        self.assertEqual(self.request('DELETE','fiscalizaciones/'+f['uuid'],role='admin',extra={'If-Match':str(current['version'])})[0],409)

    def test_13_no_pdf_for_draft(self):
        f=self.request('POST','fiscalizaciones',self.fresh(),'fiscalizador')[1]
        self.assertEqual(self.request('GET','fiscalizaciones/'+f['uuid']+'/acta/pdf',role='consulta')[0],422)

    def test_14_facts_saved_with_user_and_date(self):
        f=self.request('POST','fiscalizaciones',self.fresh(),'fiscalizador')[1]
        with Database() as db:
            rows=db.all('SELECT h.* FROM hechos_verificados h JOIN fiscalizacion_incumplimientos fi ON fi.id=h.fiscalizacion_incumplimiento_id WHERE fi.fiscalizacion_id=?',(f['id'],))
        self.assertEqual(len(rows),2);self.assertTrue(rows[0]['fecha']);self.assertEqual(rows[0]['usuario_id'],f['usuario_id'])
        self.assertIn('En el panel',rows[0]['texto'])
        self.assertTrue(self.request('GET','fiscalizaciones/'+f['uuid']+'/historial',role='admin')[1])

    def test_15_cannot_delete_referenced_establishment(self):
        self.request('POST','fiscalizaciones',self.fresh(),'fiscalizador')
        e=self.request('GET','establecimientos/'+self.est['uuid'],role='admin')[1]
        self.assertEqual(self.request('DELETE','establecimientos/'+e['uuid'],role='admin',extra={'If-Match':str(e['version'])})[0],409)

    def test_16_fiscalization_duplicates(self):
        f=self.fresh();a=self.request('POST','fiscalizaciones',f,'fiscalizador')[1];b=self.request('POST','fiscalizaciones',f,'fiscalizador')[1];self.assertEqual(a['id'],b['id'])
        f['uuid']=str(uuid.uuid4());self.assertEqual(self.request('POST','fiscalizaciones',f,'fiscalizador')[0],409)

    def test_17_post_finding(self):
        f=self.request('POST','fiscalizaciones',self.fresh(),'fiscalizador')[1]
        item={'incumplimiento_id':2,'hecho':'El panel no tenía la lista de precios vigente.'}
        status,f=self.request('POST','fiscalizaciones/'+f['uuid']+'/incumplimientos',item,'fiscalizador');self.assertEqual(status,200);self.assertEqual(len(f['incumplimientos']),3)
        self.assertEqual(self.request('POST','fiscalizaciones/'+f['uuid']+'/incumplimientos',item,'fiscalizador')[0],409)

    def test_18_refusal_instead_of_receiver_signature(self):
        f=self.fresh(True);f['firmas']['recibe']='';f['datos']['nombre_recibe']='';f['datos']['dni_recibe']='';f['datos']['relacion_recibe']='';f['datos']['negativa']='El participante se negó a identificarse y firmar. Caso de prueba.'
        self.assertEqual(self.request('POST','fiscalizaciones',f,'fiscalizador')[0],201)

    def test_19_bad_image_and_coordinate(self):
        f=self.fresh();f['firmas']['fiscalizador']='not a png';self.assertEqual(self.request('POST','fiscalizaciones',f,'fiscalizador')[0],422)
        f=self.fresh();f['latitud']=100;self.assertEqual(self.request('POST','fiscalizaciones',f,'fiscalizador')[0],422)

    def test_20_search_and_readonly_pdf(self):
        f=self.request('POST','fiscalizaciones',self.fresh(True),'fiscalizador')[1]
        matches=self.request('GET','fiscalizaciones?q='+f['expediente'],role='consulta')[1];self.assertEqual(len(matches),1)
        self.assertEqual(self.request('GET','fiscalizaciones/'+f['uuid']+'/acta/pdf',role='consulta')[0],200)
        self.assertEqual(self.request('GET','fiscalizaciones/'+f['uuid'],role='consulta')[1]['estado'],'FINALIZADA')

    def test_21_normalized_children_and_three_brands(self):
        f=self.fresh(True)
        for row in [1,2]:
            for pid in range(7,12):f['precios'].append(dict(producto_id=pid,fila=row,marca='MARCA '+str(row),price='9.999',publicado='9.999',surtidor='9.999',descuento='9.999'))
        status,saved=self.request('POST','fiscalizaciones',f,'fiscalizador');self.assertEqual(status,201,saved)
        with Database() as db:
            self.assertEqual(db.one('SELECT COUNT(*) n FROM precios WHERE fiscalizacion_id=?',(saved['id'],))['n'],12)
            for table in ['verificaciones','observaciones','firmas']:
                self.assertGreater(db.one('SELECT COUNT(*) n FROM '+table+' WHERE fiscalizacion_id=?',(saved['id'],))['n'],0)

    def test_22_fiscalizer_identity_enforced(self):
        f=self.fresh();f['datos']['nombre_fiscal']='OTRA PERSONA';self.assertEqual(self.request('POST','fiscalizaciones',f,'fiscalizador')[0],403)


if __name__=='__main__':
    unittest.main(verbosity=2)
