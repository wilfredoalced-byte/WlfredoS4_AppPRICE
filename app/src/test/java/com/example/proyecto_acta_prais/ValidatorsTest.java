package com.example.proyecto_acta_prais;

import org.junit.Test;
import static org.junit.Assert.*;

public class ValidatorsTest {
    private Models.Fiscalization draft(){Models.Fiscalization f=new Models.Fiscalization();f.expediente="TEST-01";f.establecimiento_uuid="est-01";f.fecha="08/09/2026";f.hora_apertura="09:00";f.hora_cierre="09:30";return f;}
    @Test public void rejectsImpossibleDateAndTime(){Models.Fiscalization f=draft();f.fecha="31/02/2026";assertNotNull(Validators.fiscalization(f,false));f.fecha="08/09/2026";f.hora_cierre="08:59";assertNotNull(Validators.fiscalization(f,false));}
    @Test public void acceptsLeapDayAndMidnight(){Models.Fiscalization f=draft();f.fecha="29/02/2024";f.hora_apertura="00:00";f.hora_cierre="00:10";assertNull(Validators.fiscalization(f,false));}
    @Test public void rejectsNegativeOrNonFinitePrice(){Models.Fiscalization f=draft();Models.Price p=new Models.Price(1);f.precios.add(p);for(String v:new String[]{"-1","NaN","Infinity","10000","1.0001"}){p.price=v;assertNotNull(v,Validators.fiscalization(f,false));}p.price="0.001";assertNull(Validators.fiscalization(f,false));}
    @Test public void selectedFindingRequiresUserEvidence(){Models.Fiscalization f=draft();Models.Finding i=new Models.Finding();i.incumplimiento_id=1;f.incumplimientos.add(i);assertNotNull(Validators.fiscalization(f,false));i.hecho="Se comparó el precio del panel con PRICE.";assertNull(Validators.fiscalization(f,false));}
    @Test public void draftCannotBeFinalizedWithoutRequiredSections(){Models.Fiscalization f=draft();assertNull(Validators.fiscalization(f,false));assertNotNull(Validators.fiscalization(f,true));}
}
