"""Dos páginas fijas. Coordenadas compartidas con el renderizador Android."""
import base64
import io
import json
from pathlib import Path
from reportlab.pdfgen import canvas
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase.pdfmetrics import stringWidth
from validation import ApiError

ROOT = Path(__file__).resolve().parent / 'plantilla'
LAYOUT = json.loads((ROOT / 'acta_layout.json').read_text(encoding='utf-8'))


def value(data, key):
    parts = key.split('.')
    if parts[0] == 'datos':
        return data.get('datos', {}).get(parts[1], '')
    if parts[0] == 'hecho':
        return next((r['hecho'] for r in data.get('incumplimientos', []) if r['incumplimiento_id'] == int(parts[1])), '')
    if parts[0] == 'check':
        return 'X' if data.get('verificaciones', {}).get(parts[1]) is (parts[2] == 'true') else ''
    if parts[0] == 'precio':
        return next((r.get(parts[3], '') for r in data.get('precios', []) if r['producto_id'] == int(parts[1]) and r.get('fila',0) == int(parts[2])), '')
    if parts[0] == 'marca':
        return next((r.get('marca', '') for r in data.get('precios', []) if r['producto_id'] >= 7 and r.get('fila',0) == int(parts[1])), '')
    return data.get(key, '')


def wrap(text, font, size, width):
    lines=[]
    for paragraph in text.split('\n'):
        line=''
        for char in paragraph:
            if line and stringWidth(line+char,font,size)>width:
                # Character wrapping also handles codes with no spaces.
                cut=line.rfind(' ')
                if cut>0 and char!=' ':
                    lines.append(line[:cut]);line=line[cut+1:]+char
                else:
                    lines.append(line);line=char
            else:
                line+=char
        lines.append(line.rstrip())
    return lines


def fitted(text, field):
    font='Helvetica-Bold' if field.get('bold') else 'Helvetica'
    size=field['size']
    minimum=min(size, 3.5 if field["key"].startswith("precio.") else 6.5)
    while True:
        lines=wrap(text,font,size,field['w']-2)
        if len(lines)*size*1.12 <= field['h']:
            return font,size,lines
        size-=0.25
        if size < minimum:
            raise ApiError(422, 'El texto de '+field['key']+' no cabe en el acta de dos páginas. Resúmalo antes de finalizar.')


def generate(data):
    target=io.BytesIO()
    w,h=LAYOUT['width'],LAYOUT['height']
    c=canvas.Canvas(target,pagesize=(w,h),pageCompression=1)
    c.setTitle('Acta PRICE '+data['expediente'])
    c.setAuthor(data.get('datos',{}).get('nombre_fiscal',''))
    for page in range(2):
        c.drawImage(str(ROOT/f'acta_price_p{page+1}.png'),0,0,w,h)
        for mask in LAYOUT['masks']:
            if mask['page']==page:
                c.setFillColorRGB(1,1,1)
                c.rect(mask['x'],h-mask['y']-mask['h'],mask['w'],mask['h'],fill=1,stroke=0)
        c.setFillColorRGB(0,0,0)
        for field in LAYOUT['fields']:
            if field['page']!=page:
                continue
            text=str(value(data,field['key']))
            if not text:
                continue
            font,size,lines=fitted(text,field)
            c.setFont(font,size)
            for i,line in enumerate(lines):
                x=field['x']+1
                if field['align']=='center':
                    x=field['x']+(field['w']-stringWidth(line,font,size))/2
                c.drawString(x,h-field['y']-size-i*size*1.12,line)
        for box in LAYOUT['signatures']:
            signature=data.get('firmas',{}).get(box['key'],'')
            if box['page']==page and signature:
                c.drawImage(ImageReader(io.BytesIO(base64.b64decode(signature))),box['x'],h-box['y']-box['h'],box['w'],box['h'],preserveAspectRatio=True,anchor='c',mask='auto')
        c.showPage()
    c.save()
    return target.getvalue()
