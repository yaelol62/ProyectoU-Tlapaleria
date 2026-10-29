package mx.uam.ayd.proyecto.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mx.uam.ayd.proyecto.datos.CorteCajaRepository;
import mx.uam.ayd.proyecto.datos.CorteCajaRepository.VentaCorteProjection;
import mx.uam.ayd.proyecto.negocio.modelo.CorteCaja;
import mx.uam.ayd.proyecto.negocio.modelo.Venta;

@ExtendWith(MockitoExtension.class)
class ServicioCorteCajaTest {

    @Mock
    private CorteCajaRepository corteCajaRepository;

    @InjectMocks
    private ServicioCorteCaja servicioCorteCaja;

    @Test
    @DisplayName("Consulta solo ventas finalizadas pendientes de hoy")
    void consultaVentasDelDia() {
        LocalDateTime hoy = LocalDate.now().atTime(10, 30);
        Venta ventaDeHoy = crearVenta(1L, hoy, "EFECTIVO", 100.0);
        Venta ventaDeAyer = crearVenta(2L, hoy.minusDays(1), "EFECTIVO", 100.0);
        Venta ventaDeManana = crearVenta(3L, hoy.plusDays(1), "EFECTIVO", 100.0);
        Venta ventaSinFecha = crearVenta(4L, null, "EFECTIVO", 100.0);
        Venta borrador = crearVenta(5L, hoy, null, 100.0);
        when(corteCajaRepository.findAll()).thenReturn(Collections.emptyList());
        when(corteCajaRepository.consultarVentasParaCorte()).thenReturn(
                Arrays.asList(proyeccion(ventaDeHoy), proyeccion(ventaDeAyer),
                        proyeccion(ventaDeManana), proyeccion(ventaSinFecha),
                        proyeccion(borrador)));

        List<Venta> resultado = servicioCorteCaja.consultarVentasDelDia();

        assertEquals(Collections.singletonList(ventaDeHoy), resultado);
    }

    @Test
    @DisplayName("Registra un corte conservando la fecha y sin inventar un turno")
    void registraCorteSinInventarTurno() {
        CorteCaja corte = crearCorte("Ana", 125.50, 125.50);
        corte.setTurno(null);
        corte.setVentasIncluidas(Collections.singleton(1L));
        when(corteCajaRepository.save(corte)).thenReturn(corte);
        LocalDateTime inicio = LocalDateTime.now();

        CorteCaja resultado = servicioCorteCaja.registrarCorte(corte);

        LocalDateTime fin = LocalDateTime.now();
        assertSame(corte, resultado);
        assertNotNull(corte.getFechaHora());
        assertTrue(!corte.getFechaHora().isBefore(inicio)
                && !corte.getFechaHora().isAfter(fin));
        assertEquals(null, corte.getTurno());
        verify(corteCajaRepository).save(corte);
    }

    @Test
    @DisplayName("Conserva la fecha y el turno proporcionados al registrar un corte")
    void conservaFechaYTurnoExistentes() {
        LocalDateTime fecha = LocalDateTime.of(2026, 1, 15, 9, 0);
        CorteCaja corte = crearCorte("Luis", 200.0, 200.0);
        corte.setFechaHora(fecha);
        corte.setTurno("MATUTINO-01");
        corte.setVentasIncluidas(Collections.singleton(1L));
        when(corteCajaRepository.save(corte)).thenReturn(corte);

        CorteCaja resultado = servicioCorteCaja.registrarCorte(corte);

        assertSame(corte, resultado);
        assertEquals(fecha, resultado.getFechaHora());
        assertEquals("MATUTINO-01", resultado.getTurno());
        verify(corteCajaRepository).save(corte);
    }

    @Test
    @DisplayName("Rechaza un corte nulo sin guardar")
    void rechazaCorteNulo() {
        assertThrows(IllegalArgumentException.class,
                () -> servicioCorteCaja.registrarCorte(null));

        verify(corteCajaRepository, never()).save(any(CorteCaja.class));
    }

    @Test
    @DisplayName("Rechaza un corte sin nombre de empleado")
    void rechazaNombreNulo() {
        CorteCaja corte = crearCorte(null, 10.0, 10.0);

        assertThrows(IllegalArgumentException.class,
                () -> servicioCorteCaja.registrarCorte(corte));

        verify(corteCajaRepository, never()).save(any(CorteCaja.class));
    }

    @Test
    @DisplayName("Rechaza un nombre de empleado vacío o compuesto por espacios")
    void rechazaNombreEnBlanco() {
        CorteCaja corte = crearCorte("   ", 10.0, 10.0);

        assertThrows(IllegalArgumentException.class,
                () -> servicioCorteCaja.registrarCorte(corte));

        verify(corteCajaRepository, never()).save(any(CorteCaja.class));
    }

    @Test
    @DisplayName("Rechaza un total de ventas negativo")
    void rechazaTotalNegativo() {
        CorteCaja corte = crearCorte("Ana", -0.01, 0.0);

        assertThrows(IllegalArgumentException.class,
                () -> servicioCorteCaja.registrarCorte(corte));

        verify(corteCajaRepository, never()).save(any(CorteCaja.class));
    }

    @Test
    @DisplayName("Rechaza un efectivo esperado negativo")
    void rechazaEfectivoEsperadoNegativo() {
        CorteCaja corte = crearCorte("Ana", 0.0, -0.01);

        assertThrows(IllegalArgumentException.class,
                () -> servicioCorteCaja.registrarCorte(corte));

        verify(corteCajaRepository, never()).save(any(CorteCaja.class));
    }

    @Test
    @DisplayName("Consulta todos los cortes registrados")
    void consultaHistorialDeCortes() {
        CorteCaja primero = crearCorte("Ana", 50.0, 50.0);
        CorteCaja segundo = crearCorte("Luis", 75.0, 75.0);
        when(corteCajaRepository.findAll()).thenReturn(Arrays.asList(primero, segundo));

        List<CorteCaja> resultado = servicioCorteCaja.consultarCortes();

        assertEquals(Arrays.asList(primero, segundo), resultado);
        verify(corteCajaRepository).findAll();
    }

    @Test
    @DisplayName("Consulta el historial de cortes por identificador de vendedor")
    void consultaHistorialPorVendedor() {
        int idVendedor = 7;
        List<CorteCaja> cortes = Collections.singletonList(
                crearCorte("Ana", 125.0, 125.0));
        when(corteCajaRepository.findByIdVendedor(idVendedor)).thenReturn(cortes);

        List<CorteCaja> resultado =
                servicioCorteCaja.consultarCortesPorVendedor(idVendedor);

        assertSame(cortes, resultado);
        verify(corteCajaRepository).findByIdVendedor(idVendedor);
    }

    private Venta crearVenta(
            long idVenta,
            LocalDateTime fecha,
            String tipoPago,
            double total) {
        Venta venta = new Venta();
        venta.setIdVenta(idVenta);
        venta.setDate(fecha);
        venta.setTipoPago(tipoPago);
        venta.setTotal(total);
        return venta;
    }

    private VentaCorteProjection proyeccion(Venta venta) {
        return new VentaCorteProjection() {
            @Override
            public Long getIdVenta() {
                return venta.getIdVenta();
            }

            @Override
            public LocalDateTime getFecha() {
                return venta.getDate();
            }

            @Override
            public String getTipoPago() {
                return venta.getTipoPago();
            }

            @Override
            public Double getImporte() {
                return venta.getTotal();
            }
        };
    }

    private CorteCaja crearCorte(
            String nombreEmpleado,
            double totalVentas,
            double efectivoEsperado) {
        CorteCaja corte = new CorteCaja();
        corte.setNombreEmpleado(nombreEmpleado);
        corte.setTotalVentas(totalVentas);
        corte.setEfectivoEsperado(efectivoEsperado);
        return corte;
    }
}
