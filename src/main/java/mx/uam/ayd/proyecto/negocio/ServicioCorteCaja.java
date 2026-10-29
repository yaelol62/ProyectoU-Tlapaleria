
package mx.uam.ayd.proyecto.negocio;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mx.uam.ayd.proyecto.datos.CorteCajaRepository;
import mx.uam.ayd.proyecto.datos.CorteCajaRepository.VentaCorteProjection;
import mx.uam.ayd.proyecto.negocio.modelo.CorteCaja;
import mx.uam.ayd.proyecto.negocio.modelo.Venta;

/**
 * Servicio de la HU-15.
 * Consulta ventas y registra cortes sin modificar el módulo de ventas.
 */
@Service
public class ServicioCorteCaja {

    /** Repositorio de los cortes de caja. */
    private final CorteCajaRepository corteCajaRepository;

    /**
     * Constructor administrado por Spring.
     *
     * @param corteCajaRepository repositorio propio de HU-15
     */
    @Autowired
    public ServicioCorteCaja(CorteCajaRepository corteCajaRepository) {
        this.corteCajaRepository = corteCajaRepository;
    }

    /**
     * Consulta las ventas finalizadas de hoy que aún no pertenecen a un corte.
     * Las ventas en proceso no tienen tipo de pago y no forman parte del corte.
     *
     * @return ventas válidas pendientes de corte
     */
    public List<Venta> consultarVentasDelDia() {

        LocalDate hoy = LocalDate.now();
        Set<Long> ventasCortadas = obtenerIdsVentasCortadas();

        List<Venta> ventas = corteCajaRepository.consultarVentasParaCorte().stream()
                .map(this::crearVentaCorte)
                .collect(Collectors.toList());

        return ventas.stream()
                .filter(this::esVentaFinalizada)
                .filter(venta -> venta.getDate().toLocalDate().equals(hoy))
                .filter(venta -> !ventasCortadas.contains(venta.getIdVenta()))
                .collect(Collectors.toList());
    }

    /**
     * Registra el corte con una instantánea calculada de las ventas pendientes.
     * La relación persistida con las ventas se guarda en la misma transacción.
     *
     * @param corte información que se desea guardar
     * @param ventas ventas pendientes que aparecen en la interfaz
     * @return corte guardado en la base de datos
     */
    @Transactional
    public CorteCaja registrarCorte(CorteCaja corte, List<Venta> ventas) {

        validarCorte(corte);

        if (ventas == null || ventas.isEmpty()) {
            throw new IllegalStateException(
                    "No hay ventas pendientes para registrar el corte.");
        }

        List<Venta> pendientes = consultarVentasDelDia();
        Set<Long> idsSolicitados = ventas.stream()
                .filter(this::esVentaFinalizada)
                .map(Venta::getIdVenta)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> idsPendientes = pendientes.stream()
                .map(Venta::getIdVenta)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (idsSolicitados.size() != ventas.size()
                || !idsSolicitados.equals(idsPendientes)) {
            throw new IllegalStateException(
                    "Las ventas cambiaron mientras se mostraba el corte. Actualiza la ventana e intenta de nuevo.");
        }

        calcularTotales(corte, pendientes);
        corte.setVentasIncluidas(idsPendientes);

        return guardarCorte(corte);
    }

    /**
     * Conserva compatibilidad con usos existentes que ya proporcionaron
     * el identificador de las ventas incluidas.
     */
    @Transactional
    public CorteCaja registrarCorte(CorteCaja corte) {
        validarCorte(corte);

        if (corte.getVentasIncluidas() == null
                || corte.getVentasIncluidas().isEmpty()) {
            throw new IllegalArgumentException(
                    "El corte debe identificar las ventas incluidas.");
        }

        return guardarCorte(corte);
    }

    private void validarCorte(CorteCaja corte) {
        if (corte == null) {
            throw new IllegalArgumentException(
                    "El corte de caja no puede ser nulo.");
        }

        if (corte.getNombreEmpleado() == null
                || corte.getNombreEmpleado().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Escribe el nombre del empleado.");
        }

        if (corte.getTotalVentas() < 0
                || corte.getEfectivoEsperado() < 0
                || corte.getTotalTarjeta() < 0) {
            throw new IllegalArgumentException(
                    "Los importes no pueden ser negativos.");
        }
    }

    private CorteCaja guardarCorte(CorteCaja corte) {
        if (corte.getFechaHora() == null) {
            corte.setFechaHora(LocalDateTime.now());
        }

        // No se inventa un turno: solo puede identificarse si viene de
        // la sesión o la información real que proporciona el proyecto.
        return corteCajaRepository.save(corte);
    }

    private void calcularTotales(CorteCaja corte, List<Venta> ventas) {
        double efectivo = ventas.stream()
                .filter(venta -> "EFECTIVO".equalsIgnoreCase(venta.getTipoPago()))
                .mapToDouble(this::importeSeguro)
                .sum();
        double tarjeta = ventas.stream()
                .filter(venta -> "TARJETA".equalsIgnoreCase(venta.getTipoPago()))
                .mapToDouble(this::importeSeguro)
                .sum();

        corte.setEfectivoEsperado(efectivo);
        corte.setTotalTarjeta(tarjeta);
        corte.setTotalVentas(efectivo + tarjeta);
    }

    private Venta crearVentaCorte(VentaCorteProjection datos) {
        Venta venta = new Venta();
        venta.setIdVenta(datos.getIdVenta() == null ? 0L : datos.getIdVenta());
        venta.setDate(datos.getFecha());
        venta.setTipoPago(datos.getTipoPago());
        venta.setTotal(datos.getImporte());
        return venta;
    }

    private double importeSeguro(Venta venta) {
        return venta.getTotal() == null ? 0.0 : venta.getTotal();
    }

    private boolean esVentaFinalizada(Venta venta) {
        if (venta == null
                || venta.getIdVenta() <= 0
                || venta.getDate() == null
                || venta.getTipoPago() == null) {
            return false;
        }

        return "EFECTIVO".equalsIgnoreCase(venta.getTipoPago())
                || "TARJETA".equalsIgnoreCase(venta.getTipoPago());
    }

    private Set<Long> obtenerIdsVentasCortadas() {
        List<CorteCaja> cortes = new ArrayList<>();
        corteCajaRepository.findAll().forEach(cortes::add);

        return cortes.stream()
                .filter(corte -> corte.getVentasIncluidas() != null)
                .flatMap(corte -> corte.getVentasIncluidas().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Consulta todos los cortes registrados.
     *
     * @return lista de cortes guardados
     */
    public List<CorteCaja> consultarCortes() {

        List<CorteCaja> cortes = new ArrayList<>();
        corteCajaRepository.findAll().forEach(cortes::add);

        return cortes;
    }

    /**
     * Consulta los cortes por identificador numérico del vendedor.
     *
     * @param idVendedor identificador del vendedor
     * @return cortes encontrados
     */
    public List<CorteCaja> consultarCortesPorVendedor(
            int idVendedor) {

        return corteCajaRepository.findByIdVendedor(idVendedor);
    }
}
