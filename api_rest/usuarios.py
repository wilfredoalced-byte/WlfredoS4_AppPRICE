"""Alta o actualización de cuentas desde la computadora que administra la API."""
import getpass
from database import Database, initialize
from security import hash_password


def main():
    initialize()
    usuario=input('Usuario: ').strip()
    nombre=input('Apellidos y nombres del fiscalizador: ').strip()
    dni=input('DNI (8 dígitos): ').strip()
    rol=input('Rol (ADMIN / FISCALIZADOR / CONSULTA): ').strip().upper()
    password=getpass.getpass('Contraseña nueva (mínimo 10 caracteres): ')
    if not usuario or len(usuario)>80 or not nombre or len(nombre)>70 or not dni.isdigit() or len(dni)!=8 or rol not in ['ADMIN','FISCALIZADOR','CONSULTA'] or len(password)<10:
        raise SystemExit('Datos inválidos. No se guardó la cuenta.')
    with Database() as db:
        current=db.one('SELECT id FROM usuarios WHERE usuario=?',(usuario,))
        if current:
            if input('La cuenta existe. ¿Actualizarla? Escriba SI: ')!='SI':
                raise SystemExit('Sin cambios.')
            db.execute('UPDATE usuarios SET nombre=?,dni=?,rol=?,password_hash=? WHERE id=?',(nombre,dni,rol,hash_password(password),current['id']))
            db.execute('DELETE FROM tokens WHERE usuario_id=?',(current['id'],))
        else:
            db.execute('INSERT INTO usuarios(usuario,nombre,dni,rol,password_hash) VALUES(?,?,?,?,?)',(usuario,nombre,dni,rol,hash_password(password)))
    print('Cuenta guardada. Vuelva a iniciar sesión en Android para cargar los datos actualizados.')


if __name__=='__main__':
    main()
