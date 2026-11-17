CREATE SEQUENCE public.saas_editor_revision_seq;

CREATE TABLE public.saas_editor_borradores (
    tienda_id bigint PRIMARY KEY REFERENCES public.tiendas (id),
    revision bigint NOT NULL CHECK (revision > 0),
    contenido jsonb NOT NULL
);

CREATE TABLE public.saas_editor_versiones (
    tienda_id bigint NOT NULL REFERENCES public.tiendas (id),
    version bigint NOT NULL,
    contenido jsonb NOT NULL,
    publicado_en timestamptz NOT NULL DEFAULT now(),
    autor varchar(150) NOT NULL,
    PRIMARY KEY (tienda_id, version)
);

ALTER TABLE public.saas_editor_borradores ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.saas_editor_versiones ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.saas_editor_borradores, public.saas_editor_versiones FROM PUBLIC;
