package com.example.proyecto_acta_prais;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** POJO: Gson convierte las mismas propiedades del contrato JSON de la API. */
public final class Models {
    public static class User { public long id; public String usuario, nombre, dni, rol; }
    public static class LoginRequest {
        public String usuario, password;
        public LoginRequest(String u, String p) { usuario=u; password=p; }
    }
    public static class LoginResponse { public String token; public long expires_at; public User usuario; }
    public static class Catalog { public int id; public String descripcion, base_legal, nombre, unidad; }
    public static class Record {
        public long id=0;
        public int version=0;
        public String uuid=UUID.randomUUID().toString(), actualizado="";
    }
    public static class Establishment extends Record {
        public String agente="", codigo="", ruc="", registro="", direccion="", distrito="", provincia="", departamento="HUANUCO", telefono="";
        @Override public String toString() { return agente+" · "+codigo; }
    }
    public static class Price {
        public int producto_id, fila=0;
        public String marca="", price="", publicado="", surtidor="", descuento="";
        public Price(int id) { producto_id=id; }
    }
    public static class Finding {
        public int incumplimiento_id;
        public String hecho="", fecha="";
        public long usuario_id;
    }
    public static class Attachment { public String nombre="", contenido=""; }
    public static class Fiscalization extends Record {
        public String establecimiento_uuid="", expediente="", fecha="", hora_apertura="", hora_cierre="", estado="BORRADOR";
        public long establecimiento_id=0, usuario_id=0;
        public Map<String,String> datos=new LinkedHashMap<>();
        public Map<String,Boolean> verificaciones=new LinkedHashMap<>();
        public List<Price> precios=new ArrayList<>();
        public List<Finding> incumplimientos=new ArrayList<>();
        public Map<String,String> firmas=new LinkedHashMap<>();
        public List<Attachment> documentos=new ArrayList<>();
        public Double latitud, longitud;
        public boolean closed() { return "FINALIZADA".equals(estado)||"ACTA GENERADA".equals(estado); }
        @Override public String toString() { return expediente+" · "+estado; }
    }
}
