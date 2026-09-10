package com.example.proyecto_acta_prais;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class Validators {
    public static String establishment(Models.Establishment e) {
        for(String v:new String[]{e.agente,e.codigo,e.ruc,e.direccion,e.distrito,e.provincia,e.departamento})if(v.trim().isEmpty())return "Complete los campos obligatorios del establecimiento.";
        for(String v:new String[]{e.agente,e.codigo,e.ruc,e.registro,e.direccion,e.distrito,e.provincia,e.departamento,e.telefono})if(v.length()>180)return "Los campos del establecimiento admiten hasta 180 caracteres.";
        return e.ruc.matches("(\\d{8}|\\d{11})")?null:"El RUC debe tener 11 dígitos o el DNI 8.";
    }
    public static String fiscalization(Models.Fiscalization f,boolean complete) {
        if(f.expediente.trim().isEmpty())return "Ingrese el número de expediente.";
        if(f.expediente.length()>28)return "El expediente admite hasta 28 caracteres.";
        if(f.establecimiento_uuid.isEmpty())return "Seleccione un establecimiento.";
        if(!f.fecha.isEmpty()) {
            SimpleDateFormat df=new SimpleDateFormat("dd/MM/yyyy",Locale.ROOT);df.setLenient(false);ParsePosition pp=new ParsePosition(0);
            if(!f.fecha.matches("\\d{2}/\\d{2}/\\d{4}")||df.parse(f.fecha,pp)==null||pp.getIndex()!=10)return "Fecha inválida. Seleccione una fecha válida.";
        }else if(complete)return "Seleccione la fecha de diligencia.";
        for(String t:new String[]{f.hora_apertura,f.hora_cierre})if(!t.isEmpty()&&!t.matches("([01]\\d|2[0-3]):[0-5]\\d"))return "Ingrese las horas con formato HH:MM.";
        if(complete&&(f.hora_apertura.isEmpty()||f.hora_cierre.isEmpty()))return "Registre hora de apertura y de cierre.";
        if(!f.hora_apertura.isEmpty()&&!f.hora_cierre.isEmpty()&&f.hora_cierre.compareTo(f.hora_apertura)<0)return "La hora de cierre no puede ser anterior a la apertura.";
        String[] keys={"agente","codigo","registro","direccion","distrito","provincia","departamento","ruc","dni_fiscal","nombre_fiscal"};
        if(complete)for(String key:keys)if(f.datos.getOrDefault(key,"").trim().isEmpty())return "Complete el campo: "+key.replace('_',' ')+".";
        for(String key:new String[]{"dni_fiscal","dni_recibe"})if(!f.datos.getOrDefault(key,"").isEmpty()&&!f.datos.get(key).matches("\\d{8}"))return key.replace('_',' ')+": ingrese 8 dígitos.";
        String ruc=f.datos.getOrDefault("ruc","");if(!ruc.isEmpty()&&!ruc.matches("(\\d{8}|\\d{11})"))return "RUC/DNI inválido.";
        boolean anyPrice=false;
        for(Models.Price p:f.precios) {
            String[] labels={"Registrado en PRICE","Publicado","Surtidor/dispensador","Con descuento"};
            String[] prices={p.price,p.publicado,p.surtidor,p.descuento};
            for(int i=0;i<4;i++)if(!prices[i].isEmpty()&&!prices[i].matches("\\d{1,4}(\\.\\d{1,3})?")) {
                String donde="producto #"+p.producto_id+(p.fila>0?", fila "+(p.fila+1):"");
                return "El precio \""+labels[i]+"\" del "+donde+" no es válido (escribiste \""+prices[i]+"\"). Debe ser un número entre 0 y 9999.999, con hasta 3 decimales, sin dejar el punto suelto.";
            }
            if(p.producto_id>=7&&p.marca.trim().isEmpty())return "Ingrese la marca de los cilindros con precios.";
            if(!p.price.isEmpty()&&!p.publicado.isEmpty())anyPrice=true;
        }
        if(complete&&!anyPrice)return "Ingrese PRICE y precio publicado de al menos un producto.";
        if(complete)for(String key:new String[]{"telefono_publicado","telefono_actualizado","horario_publicado"})if(f.verificaciones.get(key)==null)return "Responda las tres verificaciones.";
        Set<Integer> seen=new HashSet<>();
        for(Models.Finding item:f.incumplimientos){if(!seen.add(item.incumplimiento_id))return "Hay un incumplimiento duplicado.";if(item.hecho.trim().isEmpty())return "Escriba el hecho verificado del incumplimiento "+item.incumplimiento_id+".";}
        if(complete) {
            if(f.firmas.getOrDefault("fiscalizador","").isEmpty())return "Registre la firma del fiscalizador.";
            if(f.datos.getOrDefault("negativa","").trim().isEmpty()) {
                for(String key:new String[]{"dni_recibe","nombre_recibe","relacion_recibe"})if(f.datos.getOrDefault(key,"").trim().isEmpty())return "Complete los datos de quien recibe el acta o registre la negativa.";
                if(f.firmas.getOrDefault("recibe","").isEmpty())return "Registre la firma de quien recibe o indique la negativa.";
            }
        }
        return null;
    }
}
