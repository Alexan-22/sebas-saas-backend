-- BORRADOR: ejecutar sólo después de validar el bootstrap completo en una base local aislada.
-- No crea cuentas ni contraseñas; no ejecutado contra ninguna base remota.
BEGIN;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM public.tiendas) THEN
  RAISE EXCEPTION 'Fixture requiere tiendas vacías; no reutilizar base con datos';
 END IF;
END $$;
INSERT INTO public.tiendas(nombre,subdominio,color_primario,color_secundario,estado)
VALUES ('Boutique Lima Ficticia','fixture-lima','#A12752','#FBE9EF','ACTIVA'),
       ('Boutique Cusco Ficticia','fixture-cusco','#235C71','#E1F5FA','ACTIVA');
COMMIT;
