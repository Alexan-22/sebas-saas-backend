package com.tiendasebas.tiendasebas.service;

import com.tiendasebas.tiendasebas.dto.ClientePresencialRequest;
import com.tiendasebas.tiendasebas.dto.ClientePresencialResponse;

import com.tiendasebas.tiendasebas.entity.ClientePresencial;

import com.tiendasebas.tiendasebas.exception.CompraException;

import com.tiendasebas.tiendasebas.repository.ClientePresencialRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClientePresencialService {

    private final ClientePresencialRepository
            clientePresencialRepository;

    public ClientePresencialService(
            ClientePresencialRepository clientePresencialRepository
    ) {

        this.clientePresencialRepository =
                clientePresencialRepository;
    }


    // =========================================================
    // ADMIN - LISTAR CLIENTES PRESENCIALES ACTIVOS
    // =========================================================

    @Transactional(readOnly = true)
    public List<ClientePresencialResponse> listarActivos() {

        return clientePresencialRepository
                .findByEstadoTrueAndTiendaIdOrderByNombreAscApellidoAsc(tiendaActual())
                .stream()
                .map(this::convertirResponse)
                .toList();
    }


    // =========================================================
    // ADMIN - OBTENER CLIENTE
    // =========================================================

    @Transactional(readOnly = true)
    public ClientePresencialResponse obtenerPorId(
            Integer idClientePresencial
    ) {

        return convertirResponse(
                buscarEntidadActiva(idClientePresencial)
        );
    }


    // =========================================================
    // ADMIN - REGISTRAR CLIENTE PRESENCIAL
    // =========================================================

    @Transactional
    public ClientePresencialResponse registrar(
            ClientePresencialRequest request
    ) {

        String tipoDocumento =
                normalizarTipoDocumento(
                        request.getTipoDocumento()
                );

        String numeroDocumento =
                normalizarTextoObligatorio(
                        request.getNumeroDocumento(),
                        "El número de documento es obligatorio"
                );


        if (
                clientePresencialRepository
                        .findByTipoDocumentoIgnoreCaseAndNumeroDocumentoAndTiendaId(
                                tipoDocumento,
                                numeroDocumento, tiendaActual()
                        ).isPresent()
        ) {

            throw new CompraException(
                    "Ya existe un cliente presencial con ese documento"
            );
        }


        ClientePresencial cliente =
                new ClientePresencial();
        cliente.setTiendaId(tiendaActual());


        cliente.setNombre(
                normalizarTextoObligatorio(
                        request.getNombre(),
                        "El nombre es obligatorio"
                )
        );


        cliente.setApellido(
                normalizarTextoOpcional(
                        request.getApellido()
                )
        );


        cliente.setTipoDocumento(
                tipoDocumento
        );


        cliente.setNumeroDocumento(
                numeroDocumento
        );


        cliente.setTelefono(
                normalizarTextoOpcional(
                        request.getTelefono()
                )
        );


        cliente.setCorreo(
                normalizarCorreo(
                        request.getCorreo()
                )
        );


        cliente.setEstado(
                true
        );


        cliente =
                clientePresencialRepository.save(
                        cliente
                );


        return convertirResponse(
                cliente
        );
    }


    // =========================================================
    // USO INTERNO - BUSCAR ENTIDAD ACTIVA
    // =========================================================

    @Transactional(readOnly = true)
    public ClientePresencial buscarEntidadActiva(
            Integer idClientePresencial
    ) {

        if (
                idClientePresencial == null ||
                        idClientePresencial <= 0
        ) {

            throw new CompraException(
                    "Debe seleccionar un cliente presencial válido"
            );
        }


        ClientePresencial cliente =
                clientePresencialRepository
                        .findByIdClientePresencialAndTiendaId(
                                idClientePresencial, tiendaActual()
                        )
                        .orElseThrow(
                                () -> new org.springframework.web.server.ResponseStatusException(
                                        org.springframework.http.HttpStatus.NOT_FOUND,
                                        "Cliente presencial no encontrado"
                                )
                        );


        if (
                !Boolean.TRUE.equals(
                        cliente.getEstado()
                )
        ) {

            throw new CompraException(
                    "El cliente presencial está deshabilitado"
            );
        }


        return cliente;
    }


    // =========================================================
    // CONVERTIR A RESPONSE
    // =========================================================

    private ClientePresencialResponse convertirResponse(
            ClientePresencial cliente
    ) {

        return new ClientePresencialResponse(
                cliente.getIdClientePresencial(),
                cliente.getNombre(),
                cliente.getApellido(),
                cliente.getTipoDocumento(),
                cliente.getNumeroDocumento(),
                cliente.getTelefono(),
                cliente.getCorreo(),
                cliente.getFechaRegistro(),
                cliente.getEstado()
        );
    }


    // =========================================================
    // NORMALIZACIONES
    // =========================================================

    private String normalizarTipoDocumento(
            String valor
    ) {

        return normalizarTextoObligatorio(
                valor,
                "El tipo de documento es obligatorio"
        ).toUpperCase();
    }

    private Long tiendaActual() {
        Long tienda = com.tiendasebas.tiendasebas.config.TenantContext.getCurrentTenant();
        if (tienda == null || tienda <= 0) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        return tienda;
    }

    @Transactional
    public ClientePresencialResponse actualizar(Integer id, ClientePresencialRequest request) {
        ClientePresencial cliente = buscarEntidadActiva(id);
        String tipo = normalizarTipoDocumento(request.getTipoDocumento());
        String numero = normalizarTextoObligatorio(request.getNumeroDocumento(), "Documento obligatorio");
        clientePresencialRepository.findByTipoDocumentoIgnoreCaseAndNumeroDocumentoAndTiendaId(
                tipo, numero, tiendaActual()).ifPresent(existente -> {
                    if (!existente.getIdClientePresencial().equals(id)) {
                        throw new CompraException("Ya existe un cliente presencial con ese documento");
                    }
                });
        cliente.setNombre(normalizarTextoObligatorio(request.getNombre(), "Nombre obligatorio"));
        cliente.setApellido(normalizarTextoOpcional(request.getApellido()));
        cliente.setTipoDocumento(tipo);
        cliente.setNumeroDocumento(numero);
        cliente.setTelefono(normalizarTextoOpcional(request.getTelefono()));
        cliente.setCorreo(normalizarCorreo(request.getCorreo()));
        return convertirResponse(clientePresencialRepository.save(cliente));
    }


    private String normalizarTextoObligatorio(
            String valor,
            String mensaje
    ) {

        if (
                valor == null ||
                        valor.isBlank()
        ) {

            throw new CompraException(
                    mensaje
            );
        }


        return valor.trim();
    }


    private String normalizarTextoOpcional(
            String valor
    ) {

        if (
                valor == null ||
                        valor.isBlank()
        ) {

            return null;
        }


        return valor.trim();
    }


    private String normalizarCorreo(
            String correo
    ) {

        String correoNormalizado =
                normalizarTextoOpcional(
                        correo
                );


        if (
                correoNormalizado == null
        ) {

            return null;
        }


        return correoNormalizado.toLowerCase();
    }
}
