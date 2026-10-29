package mx.uam.ayd.proyecto.negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mx.uam.ayd.proyecto.datos.CorteCajaRepository;
import mx.uam.ayd.proyecto.datos.CorteCajaRepository.VentaCorteProjection;
import mx.uam.ayd.proyecto.negocio.modelo.CorteCaja;
import mx.uam.ayd.proyecto.negocio.modelo.Venta;

@ExtendWith(MockitoExtension.class)
class ServicioCorteCajaFlujoTest {

    @Mock
    private CorteCajaRepository corteCajaRepository;

    private ServicioCorteCaja servicio;

    @BeforeEach
    void configurar() {
        servicio = new ServicioCorteCaja(corteCajaRepository);
    }

    @Test
    void separaEfectivoTarjetaYNoRepiteVentasEnCortesSucesivos() {
        Venta efectivoA = venta(1L, "EFECTIVO", 100.0);
        Venta tarjetaB = venta(2L, "TARJETA", 75.0);
        Venta efectivoC = venta(3L, "EFECTIVO", 25.0);
        Venta efectivoD = venta(4L, "EFECTIVO", 40.0);
        Venta tarjetaE = venta(5L, "TARJETA", 60.0);

        when(corteCajaRepository.save(any(CorteCaja.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
                when(corteCajaRepository.consultarVentasParaCorte()).thenReturn(proyecciones(
                efectivoA, tarjetaB, efectivoC));
        when(corteCajaRepository.findAll()).thenReturn(Collections.emptyList());

        List<Venta> primerPendiente = servicio.consultarVentasDelDia();
        CorteCaja primerCorte = servicio.registrarCorte(corte("Ana", ""), primerPendiente);

        assertEquals(200.0, primerCorte.getTotalVentas(), 0.001);
        assertEquals(125.0, primerCorte.getEfectivoEsperado(), 0.001);
        assertEquals(75.0, primerCorte.getTotalTarjeta(), 0.001);

                when(corteCajaRepository.consultarVentasParaCorte()).thenReturn(proyecciones(
                efectivoA, tarjetaB, efectivoC, efectivoD, tarjetaE));
        when(corteCajaRepository.findAll()).thenReturn(Collections.singletonList(primerCorte));
        List<Venta> segundoPendiente = servicio.consultarVentasDelDia();
        CorteCaja segundoCorte = servicio.registrarCorte(corte("Ana", ""), segundoPendiente);

        assertEquals(Arrays.asList(efectivoD, tarjetaE), segundoPendiente);
        assertEquals(100.0, segundoCorte.getTotalVentas(), 0.001);
        assertEquals(40.0, segundoCorte.getEfectivoEsperado(), 0.001);
        assertEquals(60.0, segundoCorte.getTotalTarjeta(), 0.001);
        assertEquals(0, primerCorte.getVentasIncluidas().stream()
                .filter(id -> id == efectivoD.getIdVenta()).count());
    }

    @Test
    void tercerCorteIncluyeSoloVentaPosterior() {
        Venta ventaA = venta(1L, "EFECTIVO", 10.0);
        Venta ventaB = venta(2L, "TARJETA", 20.0);
        Venta ventaC = venta(3L, "EFECTIVO", 30.0);
        Venta ventaD = venta(4L, "TARJETA", 40.0);
                when(corteCajaRepository.consultarVentasParaCorte()).thenReturn(proyecciones(ventaA, ventaB));
        when(corteCajaRepository.findAll()).thenReturn(Collections.emptyList());
        when(corteCajaRepository.save(any(CorteCaja.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        CorteCaja corte1 = servicio.registrarCorte(corte("Ana", ""), Arrays.asList(ventaA, ventaB));
                when(corteCajaRepository.consultarVentasParaCorte()).thenReturn(proyecciones(ventaA, ventaB, ventaC));
        when(corteCajaRepository.findAll()).thenReturn(Collections.singletonList(corte1));

        List<Venta> pendientesSegundo = servicio.consultarVentasDelDia();
        CorteCaja corte2 = servicio.registrarCorte(corte("Ana", ""), pendientesSegundo);

        assertEquals(Collections.singletonList(ventaC), pendientesSegundo);
        assertEquals(30.0, corte2.getTotalVentas(), 0.001);

                when(corteCajaRepository.consultarVentasParaCorte()).thenReturn(proyecciones(ventaA, ventaB, ventaC, ventaD));
        when(corteCajaRepository.findAll()).thenReturn(Arrays.asList(corte1, corte2));

        List<Venta> pendientesTercero = servicio.consultarVentasDelDia();
        CorteCaja corte3 = servicio.registrarCorte(corte("Ana", ""), pendientesTercero);

        assertEquals(Collections.singletonList(ventaD), pendientesTercero);
        assertEquals(40.0, corte3.getTotalVentas(), 0.001);
        assertEquals(0.0, corte3.getEfectivoEsperado(), 0.001);
        assertEquals(40.0, corte3.getTotalTarjeta(), 0.001);
    }

    @Test
    void rechazaCorteSinVentasPendientes() {
        assertThrows(IllegalStateException.class,
                () -> servicio.registrarCorte(corte("Ana", ""),
                        Collections.emptyList()));
    }

    @Test
    void falloGuardadoNoMarcaVentasComoCortadas() {
        Venta venta = venta(1L, "EFECTIVO", 50.0);
                when(corteCajaRepository.consultarVentasParaCorte()).thenReturn(proyecciones(venta));
        when(corteCajaRepository.findAll()).thenReturn(Collections.emptyList());
        when(corteCajaRepository.save(any(CorteCaja.class)))
                .thenThrow(new IllegalStateException("Fallo persistencia"));

        assertThrows(IllegalStateException.class,
                () -> servicio.registrarCorte(corte("Ana", ""),
                        Collections.singletonList(venta)));

        List<Venta> pendientes = servicio.consultarVentasDelDia();
        assertEquals(Collections.singletonList(venta), pendientes);
    }

    @Test
    void rechazaListaSiCambioMientrasSeMostrabaElCorte() {
        Venta ventaInicial = venta(1L, "EFECTIVO", 50.0);
        Venta ventaNueva = venta(2L, "EFECTIVO", 25.0);
                when(corteCajaRepository.consultarVentasParaCorte()).thenReturn(proyecciones(ventaInicial, ventaNueva));
        when(corteCajaRepository.findAll()).thenReturn(Collections.emptyList());

        assertThrows(IllegalStateException.class,
                () -> servicio.registrarCorte(corte("Ana", ""),
                        Collections.singletonList(ventaInicial)));
    }

    private CorteCaja corte(String empleado, String turno) {
        CorteCaja corte = new CorteCaja();
        corte.setNombreEmpleado(empleado);
        corte.setTurno(turno.isBlank() ? null : turno);
        return corte;
    }

    private List<VentaCorteProjection> proyecciones(Venta... ventas) {
        List<VentaCorteProjection> resultado = new java.util.ArrayList<>();
        for (Venta venta : ventas) {
            resultado.add(new VentaCorteProjection() {
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
            });
        }
        return resultado;
    }

    private Venta venta(long id, String tipoPago, double total) {
        Venta venta = new Venta();
        venta.setIdVenta(id);
        venta.setDate(LocalDate.now().atTime(10, 0));
        venta.setTipoPago(tipoPago);
        venta.setTotal(total);
        return venta;
    }
}
