
package mx.uam.ayd.proyecto.datos;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import mx.uam.ayd.proyecto.negocio.modelo.CorteCaja;

/**
 * Repositorio para almacenar y consultar los cortes de caja.
 */
public interface CorteCajaRepository
        extends CrudRepository<CorteCaja, Long> {

    /**
     * Consulta los cortes realizados por un vendedor.
     *
     * @param idVendedor identificador del vendedor
     * @return lista de cortes del vendedor
     */
    List<CorteCaja> findByIdVendedor(int idVendedor);

    /**
     * Consulta los cortes de un turno.
     *
     * @param turno identificador del turno
     * @return lista de cortes del turno
     */
    List<CorteCaja> findByTurno(String turno);

    /**
     * Datos mínimos que necesita HU-15 para calcular el corte.
     * La consulta no carga la entidad Venta ni selecciona costo_envio.
     */
    @Query(value = "SELECT id_venta AS idVenta, date AS fecha, tipo_pago AS tipoPago, total AS importe "
            + "FROM venta "
            + "WHERE tipo_pago IN ('EFECTIVO', 'TARJETA')",
            nativeQuery = true)
    List<VentaCorteProjection> consultarVentasParaCorte();

    interface VentaCorteProjection {
        Long getIdVenta();

        LocalDateTime getFecha();

        String getTipoPago();

        Double getImporte();
    }
}
