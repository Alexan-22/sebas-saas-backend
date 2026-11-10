package com.tiendasebas.tiendasebas.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "home_colecciones")
public class HomeColeccion {

    @jakarta.persistence.Column(name = "tienda_id")
    private Long tiendaId;

    public Long getTiendaId() {
        return tiendaId;
    }

    public void setTiendaId(Long tiendaId) {
        this.tiendaId = tiendaId;
    }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_coleccion")
    private Integer idColeccion;

    @Column(name = "titulo", nullable = true, length = 100)
    private String titulo;

    @Column(name = "etiqueta", length = 100)
    private String etiqueta;

    @Column(name = "texto_boton", length = 100)
    private String textoBoton;

    @Column(name = "enlace", length = 255)
    private String enlace;

    @Column(name = "imagen", length = 255)
    private String imagen;

    @Column(name = "orden", nullable = false)
    private Integer orden;

    @Column(name = "estado", nullable = false)
    private Boolean estado = true;

    public HomeColeccion() {}

    public Integer getIdColeccion() { return idColeccion; }
    public void setIdColeccion(Integer idColeccion) { this.idColeccion = idColeccion; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getEtiqueta() { return etiqueta; }
    public void setEtiqueta(String etiqueta) { this.etiqueta = etiqueta; }

    public String getTextoBoton() { return textoBoton; }
    public void setTextoBoton(String textoBoton) { this.textoBoton = textoBoton; }

    public String getEnlace() { return enlace; }
    public void setEnlace(String enlace) { this.enlace = enlace; }

    public String getImagen() { return imagen; }
    public void setImagen(String imagen) { this.imagen = imagen; }

    public Integer getOrden() { return orden; }
    public void setOrden(Integer orden) { this.orden = orden; }

    public Boolean getEstado() { return estado; }
    public void setEstado(Boolean estado) { this.estado = estado; }
}