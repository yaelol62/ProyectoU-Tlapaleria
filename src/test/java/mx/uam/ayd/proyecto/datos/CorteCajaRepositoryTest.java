package mx.uam.ayd.proyecto.datos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

import jakarta.persistence.EntityManager;
import mx.uam.ayd.proyecto.negocio.modelo.CorteCaja;

@DataJpaTest
@ContextConfiguration(classes = CorteCajaRepositoryTest.TestConfiguration.class)
class CorteCajaRepositoryTest {

    @Autowired
    private CorteCajaRepository corteCajaRepository;

    @Autowired
    private EntityManager entityManager;

    @Configuration(proxyBeanMethods = false)
    @EnableJpaRepositories(basePackageClasses = CorteCajaRepository.class)
    @EntityScan(basePackageClasses = CorteCaja.class)
    static class TestConfiguration {
    }

    @Test
    void persisteVentasIncluidasAlGuardarYReleerCorte() {
        CorteCaja corte = new CorteCaja();
        corte.setNombreEmpleado("Ana");
        corte.setTurno(null);
        corte.setFechaHora(LocalDateTime.of(2026, 10, 28, 10, 30));
        corte.setEfectivoEsperado(125.0);
        corte.setTotalVentas(200.0);
        corte.setTotalTarjeta(75.0);
        corte.setVentasIncluidas(Set.of(11L, 12L, 13L));

        CorteCaja guardado = corteCajaRepository.save(corte);
        entityManager.flush();
        Long id = guardado.getIdCorte();
        entityManager.clear();

        CorteCaja releido = corteCajaRepository.findById(id).orElseThrow();
        assertNotNull(releido.getFechaHora());
        assertEquals(Set.of(11L, 12L, 13L), releido.getVentasIncluidas());
        assertEquals(125.0, releido.getEfectivoEsperado(), 0.001);
        assertEquals(75.0, releido.getTotalTarjeta(), 0.001);
    }

    @Test
    void consultaCorteHistoricoConTotalTarjetaNuloComoCero() {
        CorteCaja corte = new CorteCaja();
        corte.setNombreEmpleado("Ana");
        corte.setTotalVentas(100.0);
        corte.setEfectivoEsperado(100.0);
        corte.setVentasIncluidas(Set.of(21L));

        CorteCaja guardado = corteCajaRepository.save(corte);
        entityManager.flush();
        Long id = guardado.getIdCorte();
        entityManager.createNativeQuery(
                "UPDATE corte_caja SET total_tarjeta = NULL WHERE id_corte = :id")
                .setParameter("id", id)
                .executeUpdate();
        entityManager.clear();

        CorteCaja historico = corteCajaRepository.findById(id).orElseThrow();

        assertEquals(0.0, historico.getTotalTarjeta(), 0.001);
        assertNull(entityManager.createNativeQuery(
                "SELECT total_tarjeta FROM corte_caja WHERE id_corte = :id")
                .setParameter("id", id)
                .getSingleResult());
    }

    @Test
    void nuevoCorteGuardaCeroCuandoNoHayPagosConTarjeta() {
        CorteCaja corte = new CorteCaja();
        corte.setNombreEmpleado("Ana");
        corte.setTotalVentas(100.0);
        corte.setEfectivoEsperado(100.0);
        corte.setVentasIncluidas(Set.of(31L));

        CorteCaja guardado = corteCajaRepository.save(corte);
        entityManager.flush();
        Long id = guardado.getIdCorte();
        entityManager.clear();

        CorteCaja releido = corteCajaRepository.findById(id).orElseThrow();
        assertEquals(0.0, releido.getTotalTarjeta(), 0.001);
        assertEquals(0.0, ((Number) entityManager.createNativeQuery(
                "SELECT total_tarjeta FROM corte_caja WHERE id_corte = :id")
                .setParameter("id", id)
                .getSingleResult()).doubleValue(), 0.001);
    }
}
