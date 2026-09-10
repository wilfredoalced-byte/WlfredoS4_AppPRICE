package com.example.proyecto_acta_prais;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/** Registro de Hidrocarburos de Osinergmin (busqueda-registro-hidrocarburos), empaquetado como
 *  assets/registro_hidrocarburos.json para permitir autocompletar el formulario de establecimiento
 *  con solo ingresar el Código Osinergmin, el RUC o el Número de Registro. */
public final class RegistroHidrocarburos {
    public static class Entry {
        public String codigo, registro, ruc, agente, direccion, departamento, provincia, distrito;
    }
    private static List<Entry> cache;

    public static synchronized List<Entry> all(Context c) {
        if(cache!=null) return cache;
        try(InputStreamReader r=new InputStreamReader(c.getAssets().open("registro_hidrocarburos.json"),"UTF-8")) {
            Type type=new TypeToken<List<Entry>>(){}.getType();
            List<Entry> loaded=new Gson().fromJson(r,type);
            cache=loaded!=null?loaded:new ArrayList<>();
        } catch(Exception e) { cache=new ArrayList<>(); }
        return cache;
    }

    /** Busca una coincidencia exacta contra el código Osinergmin, el número de registro o el RUC. */
    public static Entry find(Context c,String value) {
        if(value==null) return null;
        String v=value.trim();
        if(v.isEmpty()) return null;
        for(Entry e:all(c)) if(v.equals(e.codigo)||v.equals(e.registro)||v.equals(e.ruc)) return e;
        return null;
    }
}
