package com.tiendasebas.tiendasebas.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "home_banner")
public class HomeBanner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_banner")
    private Integer idBanner;

    @Column(name = "etiqueta", length = 100)
    private String etiqueta;

    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Column(name = "texto_boton", length = 100)
    private String textoBoton;

    @Column(name = "enlace_boton", length = 255)
    private String enlaceBoton;

    @Column(name = "imagen", length = 255)
    private String imagen;

    @Column(name = "orden", nullable = false)
    private Integer orden = 1;

    @Column(name = "estado", nullable = false)
    private Boolean estado = true;

    // Identificador de la tienda SaaS
    @Column(name = "tienda_id")
    private Long tiendaId;

    public Long getTiendaId() {
        return tiendaId;
    }

    public void setTiendaId(Long tiendaId) {
        this.tiendaId = tiendaId;
    }

    public HomeBanner() {}

    public Integer getIdBanner() { return idBanner; }
    public void setIdBanner(Integer idBanner) { this.idBanner = idBanner; }

    public String getEtiqueta() { return etiqueta; }
    public void setEtiqueta(String etiqueta) { this.etiqueta = etiqueta; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getTextoBoton() { return textoBoton; }
    public void setTextoBoton(String textoBoton) { this.textoBoton = textoBoton; }

    public String getEnlaceBoton() { return enlaceBoton; }
    public void setEnlaceBoton(String enlaceBoton) { this.enlaceBoton = enlaceBoton; }

    public String getImagen() { return imagen; }
    public void setImagen(String imagen) { this.imagen = imagen; }

    public Integer getOrden() { return orden; }
    public void setOrden(Integer orden) { this.orden = orden; }

    public Boolean getEstado() { return estado; }
    public void setEstado(Boolean estado) { this.estado = estado; }
}