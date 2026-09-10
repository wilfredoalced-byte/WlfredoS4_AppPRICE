"""Las reglas también se aplican en el servidor, aunque se omita la interfaz."""
import base64
import io
import math
import re
from datetime import datetime
from decimal import Decimal, InvalidOperation
from PIL import Image


class ApiError(Exception):
    def __init__(self, status, message):
        self.status, self.message = status, message
        super().__init__(message)


def need(condition, message, status=422):
    if not condition:
        raise ApiError(status, message)


def text(value, field, limit=200, required=False):
    need(isinstance(value, str), field + ': debe ser texto.')
    need(len(value) <= limit, field + ': máximo ' + str(limit) + ' caracteres.')
    need(not required or value.strip(), 'Complete ' + field + '.')


def image_value(value, field, maximum=400000, max_side=1600):
    need(isinstance(value, str) and len(value) <= maximum, field + ': imagen demasiado grande.')
    if not value:
        return
    try:
        raw = base64.b64decode(value, validate=True)
        with Image.open(io.BytesIO(raw)) as im:
            need(im.format in ('PNG', 'JPEG'), field + ': use PNG o JPEG.')
            need(0 < im.width <= max_side and 0 < im.height <= max_side, field + ': resolución inválida.')
            im.verify()
    except ApiError:
        raise
    except Exception:
        raise ApiError(422, field + ': imagen inválida.')


def establishment(data):
    for key in ['uuid', 'agente', 'codigo', 'ruc', 'registro', 'direccion', 'distrito', 'provincia', 'departamento', 'telefono']:
        text(data.get(key, ''), key, 180, key in ['uuid', 'agente', 'codigo', 'ruc', 'direccion', 'distrito', 'provincia', 'departamento'])
    need(bool(re.fullmatch(r'(\d{8}|\d{11})', data['ruc'])), 'El RUC debe tener 11 dígitos o el DNI 8.')


def fiscalization(data):
    text(data.get('uuid', ''), 'uuid', 100, True)
    text(data.get('establecimiento_uuid', ''), 'establecimiento', 100, True)
    text(data.get('expediente', ''), 'expediente', 28, True)
    state = data.get('estado', '')
    need(state in ['BORRADOR', 'EN PROCESO', 'FINALIZADA', 'ACTA GENERADA'], 'Estado inválido.')
    complete = state in ['FINALIZADA', 'ACTA GENERADA']
    d = data.get('datos', {})
    need(isinstance(d, dict) and len(d) <= 40, 'Datos generales inválidos.')
    limits = {'agente':120,'codigo':35,'registro':35,'direccion':150,'distrito':45,'provincia':45,
              'departamento':35,'ruc':11,'telefono':30,'dni_fiscal':8,'nombre_fiscal':70,
              'dni_recibe':8,'nombre_recibe':70,'relacion_recibe':45,
              'otras':300,'documentacion':300,'observaciones':300,'negativa':300}
    for key, value in d.items():
        need(key in limits, 'Campo no reconocido: ' + str(key))
        text(value, key, limits[key])
    for key in ['agente','codigo','registro','direccion','distrito','provincia','departamento','ruc','dni_fiscal','nombre_fiscal']:
        if complete:
            need(d.get(key, '').strip(), 'Complete ' + key + '.')
    for key in ['dni_fiscal','dni_recibe']:
        need(not d.get(key) or bool(re.fullmatch(r'\d{8}', d[key])), key + ': ingrese 8 dígitos.')
    need(not d.get('ruc') or bool(re.fullmatch(r'(\d{8}|\d{11})',d['ruc'])), 'RUC/DNI inválido.')
    date = data.get('fecha', '')
    text(date, 'fecha', 10, complete)
    if date:
        try:
            parsed = datetime.strptime(date, '%d/%m/%Y')
            need(parsed.strftime('%d/%m/%Y') == date, 'Fecha inválida. Use DD/MM/AAAA.')
        except ValueError:
            raise ApiError(422, 'Fecha inválida. Use DD/MM/AAAA.')
    for key in ['hora_apertura','hora_cierre']:
        value = data.get(key, '')
        text(value,key,5,complete)
        need(not value or bool(re.fullmatch(r'([01]\d|2[0-3]):[0-5]\d',value)), 'Hora inválida. Use HH:MM.')
    if data.get('hora_apertura') and data.get('hora_cierre'):
        need(data['hora_cierre'] >= data['hora_apertura'], 'El cierre no puede ser anterior a la apertura.')
    prices = data.get('precios', [])
    need(isinstance(prices,list) and len(prices) <= 21, 'Precios inválidos.')
    seen=set()
    for price in prices:
        pid=price.get('producto_id')
        need(type(pid) is int and 1 <= pid <= 11, 'Producto inválido.')
        brand=price.get('marca','').strip()
        text(brand,'marca',18)
        row=price.get('fila',0)
        need(type(row) is int and 0 <= row <= 2, 'Fila de marca inválida.')
        need(pid>=7 or row==0,'Solo los cilindros admiten varias marcas.')
        need((pid,row) not in seen, 'Producto duplicado en la misma fila.')
        seen.add((pid,row))
        if pid>=7:
            need(bool(brand),'Ingrese la marca de los cilindros.')
        for key in ['price','publicado','surtidor','descuento']:
            value=price.get(key,'')
            text(value,'precio '+key,9)
            if value:
                need(bool(re.fullmatch(r'\d{1,4}(\.\d{1,3})?',value)), 'Use precios entre 0 y 9999.999, con hasta 3 decimales.')
    if complete:
        need(any(p.get('price') and p.get('publicado') for p in prices), 'Ingrese precio PRICE y publicado de al menos un producto.')
    checks=data.get('verificaciones',{})
    need(isinstance(checks,dict), 'Verificaciones inválidas.')
    for key in ['telefono_publicado','telefono_actualizado','horario_publicado']:
        need(checks.get(key) is None or type(checks[key]) is bool,'Respuesta de verificación inválida.')
        if complete:
            need(type(checks.get(key)) is bool, 'Responda todas las verificaciones.')
    violations=data.get('incumplimientos',[])
    need(isinstance(violations,list) and len(violations)<=6,'Incumplimientos inválidos.')
    seen=set()
    for item in violations:
        code=item.get('incumplimiento_id')
        need(type(code) is int and code in range(1,7) and code not in seen,'Incumplimiento inválido o duplicado.')
        seen.add(code)
        text(item.get('hecho',''),'hecho verificado '+str(code),{1:650,2:650,3:350,4:300,5:350,6:450}[code],True)
    signatures=data.get('firmas',{})
    need(isinstance(signatures,dict),'Firmas inválidas.')
    for key in ['fiscalizador','recibe']:
        image_value(signatures.get(key,''),'Firma '+key)
    if complete:
        need(signatures.get('fiscalizador'), 'Registre la firma del fiscalizador.')
        if not d.get('negativa','').strip():
            for key in ['dni_recibe','nombre_recibe','relacion_recibe']:
                need(d.get(key,'').strip(), 'Complete ' + key + ' o indique la negativa.')
            need(signatures.get('recibe'), 'Registre la firma de quien recibe o indique la negativa.')
    docs=data.get('documentos',[])
    need(isinstance(docs,list) and len(docs)<=4,'Adjunte como máximo 4 fotografías.')
    for doc in docs:
        text(doc.get('nombre',''),'nombre de fotografía',120,True)
        image_value(doc.get('contenido',''),'Fotografía',1800000,1800)
        need(doc.get('contenido'),'Fotografía vacía.')
    for key,lo,hi in [('latitud',-90,90),('longitud',-180,180)]:
        n=data.get(key)
        need(n is None or type(n) in (float,int) and math.isfinite(n) and lo<=n<=hi, 'Coordenada inválida.')
