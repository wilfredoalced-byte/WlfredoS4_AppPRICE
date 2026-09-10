import base64
import hashlib
import hmac
import secrets


def hash_password(password, salt=None):
    salt = salt or secrets.token_hex(16)
    digest = hashlib.pbkdf2_hmac('sha256', password.encode(), salt.encode(), 200000)
    return salt + ':' + base64.b64encode(digest).decode()


def verify_password(password, stored):
    return hmac.compare_digest(hash_password(password, stored.split(':')[0]), stored)


def token_hash(token):
    return hashlib.sha256(token.encode()).hexdigest()
