package com.ClinicaDeYmid.billing_service.application.rips;

import java.math.BigDecimal;
import java.util.List;

public record RipsDocument(String numDocumentoIdObligado, String numFactura, String tipoNota, String numNota,
                           List<User> usuarios) {

    public record User(String tipoDocumentoIdentificacion, String numDocumentoIdentificacion, String tipoUsuario,
                       String fechaNacimiento, String codSexo, String codPaisResidencia, String codMunicipioResidencia,
                       String codZonaTerritorialResidencia, String incapacidad, int consecutivo, String codPaisOrigen,
                       String registroSIRAS, Services serviciosTecnologias) {
    }

    public record Services(List<Consultation> consultas, List<Procedure> procedimientos, List<Emergency> urgencias,
                           List<Hospitalization> hospitalizacion, List<Object> recienNacidos,
                           List<Object> medicamentos, List<OtherService> otrosServicios) {
    }

    public record Consultation(String codPrestador, String fechaInicioAtencion, String numAutorizacion,
                               String codConsulta, String modalidadGrupoServicioTecSal, String grupoServicios,
                               Integer codServicio, String finalidadTecnologiaSalud, String causaMotivoAtencion,
                               String codDiagnosticoPrincipal, String codDiagnosticoPrincipalCIE11,
                               String nomCodDiagnosticoPrincipalCIE11, String codDiagnosticoRelacionado1,
                               String codDiagnosticoRelacionado1CIE11, String nomCodDiagnosticoRelacionado1CIE11,
                               String codDiagnosticoRelacionado2, String codDiagnosticoRelacionado2CIE11,
                               String nomCodDiagnosticoRelacionado2CIE11, String codDiagnosticoRelacionado3,
                               String codDiagnosticoRelacionado3CIE11, String nomCodDiagnosticoRelacionado3CIE11,
                               String tipoDiagnosticoPrincipal, String tipoDocumentoIdentificacion,
                               String numDocumentoIdentificacion, BigDecimal vrServicio, String conceptoRecaudo,
                               BigDecimal valorPagoModerador, String numFEVPagoModerador, int consecutivo,
                               String codigoVIDA) {
    }

    public record Procedure(String codPrestador, String fechaInicioAtencion, String idMIPRES, String numAutorizacion,
                            String codProcedimiento, String viaIngresoServicioSalud,
                            String modalidadGrupoServicioTecSal, String grupoServicios, Integer codServicio,
                            String finalidadTecnologiaSalud, String tipoDocumentoIdentificacion,
                            String numDocumentoIdentificacion, String codDiagnosticoPrincipal,
                            String codDiagnosticoPrincipalCIE11, String nomCodDiagnosticoPrincipalCIE11,
                            String codDiagnosticoRelacionado, String codDiagnosticoRelacionadoCIE11,
                            String nomCodDiagnosticoRelacionadoCIE11, String codComplicacion,
                            String codComplicacionCIE11, String nomComplicacionCIE11, BigDecimal vrServicio,
                            String conceptoRecaudo, BigDecimal valorPagoModerador, String numFEVPagoModerador,
                            String codigoVIDA, int consecutivo) {
    }

    public record Emergency(String codPrestador, String fechaInicioAtencion, String causaMotivoAtencion,
                            String codDiagnosticoPrincipal, String codDiagnosticoPrincipalCIE11,
                            String nomCodDiagnosticoPrincipalCIE11, String codDiagnosticoPrincipalE,
                            String codDiagnosticoPrincipalECIE11, String nomCodDiagnosticoPrincipalECIE11,
                            String codDiagnosticoRelacionadoE1, String codDiagnosticoRelacionado1CIE11,
                            String nomCodDiagnosticoRelacionado1CIE11, String codDiagnosticoRelacionadoE2,
                            String codDiagnosticoRelacionado2CIE11, String nomCodDiagnosticoRelacionado2CIE11,
                            String codDiagnosticoRelacionadoE3, String codDiagnosticoRelacionado3CIE11,
                            String nomCodDiagnosticoRelacionado3CIE11, String condicionDestinoUsuarioEgreso,
                            String codDiagnosticoCausaMuerte, String fechaEgreso, int consecutivo,
                            String codigoVIDA) {
    }

    public record Hospitalization(String codPrestador, String viaIngresoServicioSalud, String fechaInicioAtencion,
                                  String numAutorizacion, String causaMotivoAtencion, String codDiagnosticoPrincipal,
                                  String codDiagnosticoPrincipalCIE11, String nomCodDiagnosticoPrincipalCIE11,
                                  String codDiagnosticoPrincipalE, String codDiagnosticoPrincipalECIE11,
                                  String nomCodDiagnosticoPrincipalECIE11, String codDiagnosticoRelacionadoE1,
                                  String codDiagnosticoRelacionado1CIE11, String nomCodDiagnosticoRelacionado1CIE11,
                                  String codDiagnosticoRelacionadoE2, String codDiagnosticoRelacionado2CIE11,
                                  String nomCodDiagnosticoRelacionado2CIE11, String codDiagnosticoRelacionadoE3,
                                  String codDiagnosticoRelacionado3CIE11, String nomCodDiagnosticoRelacionado3CIE11,
                                  String codComplicacion, String condicionDestinoUsuarioEgreso,
                                  String codDiagnosticoCausaMuerte, String fechaEgreso, String codigoVIDA,
                                  int consecutivo) {
    }

    public record OtherService(String codPrestador, String numAutorizacion, String idMIPRES,
                               String fechaSuministroTecnologia, String tipoOS, String codTecnologiaSalud,
                               String nomTecnologiaSalud, int cantidadOS, String tipoDocumentoIdentificacion,
                               String numDocumentoIdentificacion, BigDecimal vrUnitOS, BigDecimal vrDispensacion,
                               BigDecimal vrServicio, String conceptoRecaudo, BigDecimal valorPagoModerador,
                               String numFEVPagoModerador, String codigoVIDA, int consecutivo) {
    }
}
