
package mx.uam.ayd.proyecto.presentacion.corteCaja;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import mx.uam.ayd.proyecto.negocio.ServicioCorteCaja;
import mx.uam.ayd.proyecto.negocio.modelo.CorteCaja;
import mx.uam.ayd.proyecto.negocio.modelo.Venta;

/**
 * Controlador de la HU-15.
 * Muestra las ventas del día y registra cortes independientes.
 */
@Component
public class ControlCorteCaja {

    private final ServicioCorteCaja servicioCorteCaja;
    private final VentanaCorteCaja ventanaCorteCaja;

    /** Tabla con las ventas del día. */
    @FXML
    private TableView<Venta> tablaVentas;

    @FXML
    private TableView<CorteCaja> tablaHistorial;

    /** Columnas que muestran los datos de cada venta. */
    @FXML
    private TableColumn<Venta, LocalDateTime> colFecha;

    @FXML
    private TableColumn<Venta, Long> colVenta;

    @FXML
    private TableColumn<Venta, Long> colVendedor;

    @FXML
    private TableColumn<Venta, Double> colTotal;

    @FXML
    private TableColumn<CorteCaja, LocalDateTime> colHistorialFecha;

    @FXML
    private TableColumn<CorteCaja, String> colHistorialEmpleado;

    @FXML
    private TableColumn<CorteCaja, String> colHistorialTurno;

    @FXML
    private TableColumn<CorteCaja, Double> colHistorialTotal;

    @FXML
    private TableColumn<CorteCaja, Double> colHistorialEfectivo;

    @FXML
    private TableColumn<CorteCaja, Double> colHistorialTarjeta;

    /** Etiquetas con los importes calculados. */
    @FXML
    private Label lblTotalVentas;

    @FXML
    private Label lblEfectivoEsperado;

    @FXML
    private Label lblTotalTarjeta;

    /** Campo donde el empleado escribe su nombre. */
    @FXML
    private TextField txtNombreEmpleado;

    /** Formato legible para la fecha y hora de la venta. */
    private final DateTimeFormatter formatoFecha =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /**
     * Constructor administrado por Spring.
     *
     * @param servicioCorteCaja servicio de negocio
     * @param ventanaCorteCaja ventana gráfica
     */
    @Autowired
    public ControlCorteCaja(
            ServicioCorteCaja servicioCorteCaja,
            VentanaCorteCaja ventanaCorteCaja) {

        this.servicioCorteCaja = servicioCorteCaja;
        this.ventanaCorteCaja = ventanaCorteCaja;
    }

    /**
     * Configura las columnas después de cargar el FXML.
     */
    @FXML
    private void initialize() {

        // Fecha y hora de la venta.
        colFecha.setCellValueFactory(celda ->
                new ReadOnlyObjectWrapper<>(
                        celda.getValue().getDate()));

        // Presenta la fecha con el formato definido.
        colFecha.setCellFactory(columna -> new TableCell<>() {
            @Override
            protected void updateItem(
                    LocalDateTime fecha, boolean vacio) {

                super.updateItem(fecha, vacio);

                setText(vacio || fecha == null
                        ? null
                        : formatoFecha.format(fecha));
            }
        });

        // Identificador de la venta.
        colVenta.setCellValueFactory(celda ->
                new ReadOnlyObjectWrapper<>(
                        celda.getValue().getIdVenta()));

        // Identificador numérico del trabajador que registró la venta.
        colVendedor.setCellValueFactory(celda ->
                new ReadOnlyObjectWrapper<>(
                        celda.getValue().getIdTrabajador()));

        // Importe total de cada venta.
        colTotal.setCellValueFactory(celda ->
                new ReadOnlyObjectWrapper<>(
                        celda.getValue().getTotal()));

        // Formato monetario con dos decimales.
        colTotal.setCellFactory(columna -> new TableCell<>() {
            @Override
            protected void updateItem(
                    Double importe, boolean vacio) {

                super.updateItem(importe, vacio);

                setText(vacio || importe == null
                        ? null
                        : String.format("MXN %.2f", importe));
            }
        });

        colHistorialFecha.setCellValueFactory(celda ->
                new ReadOnlyObjectWrapper<>(
                        celda.getValue().getFechaHora()));
        colHistorialFecha.setCellFactory(columna -> new TableCell<>() {
            @Override
            protected void updateItem(
                    LocalDateTime fecha, boolean vacio) {
                super.updateItem(fecha, vacio);
                setText(vacio || fecha == null
                        ? null
                        : formatoFecha.format(fecha));
            }
        });
        colHistorialEmpleado.setCellValueFactory(celda ->
                new ReadOnlyObjectWrapper<>(
                        celda.getValue().getNombreEmpleado()));
        colHistorialTurno.setCellValueFactory(celda ->
                new ReadOnlyObjectWrapper<>(
                        celda.getValue().getTurno()));
        colHistorialTotal.setCellValueFactory(celda ->
                new ReadOnlyObjectWrapper<>(
                        celda.getValue().getTotalVentas()));
        colHistorialEfectivo.setCellValueFactory(celda ->
                new ReadOnlyObjectWrapper<>(
                        celda.getValue().getEfectivoEsperado()));
        colHistorialTarjeta.setCellValueFactory(celda ->
                new ReadOnlyObjectWrapper<>(
                        celda.getValue().getTotalTarjeta()));
    }

    /**
     * Abre la ventana y carga las ventas del día.
     */
    public void iniciar() {
        ventanaCorteCaja.muestra();

        // Espera a que JavaFX termine de cargar los controles.
        Platform.runLater(this::cargarVentasDelDia);
        Platform.runLater(this::cargarHistorial);
    }

    /**
     * Consulta las ventas y actualiza la tabla y los totales.
     */
    private void cargarVentasDelDia() {

        try {
            List<Venta> ventas =
                    servicioCorteCaja.consultarVentasDelDia();

            tablaVentas.getItems().setAll(ventas);

            actualizarTotales(ventas);

        } catch (Exception e) {
            e.printStackTrace();

            mostrarAlerta(
                    AlertType.ERROR,
                    "No se pudieron consultar las ventas",
                    "Revisa la terminal para conocer el error.");
        }
    }

    private void cargarHistorial() {
        try {
            List<CorteCaja> cortes = servicioCorteCaja.consultarCortes();
            cortes.sort((primero, segundo) -> {
                LocalDateTime fechaPrimero = primero.getFechaHora();
                LocalDateTime fechaSegundo = segundo.getFechaHora();
                if (fechaPrimero == null) {
                    return fechaSegundo == null ? 0 : 1;
                }
                if (fechaSegundo == null) {
                    return -1;
                }
                return fechaSegundo.compareTo(fechaPrimero);
            });
            tablaHistorial.getItems().setAll(cortes);
        } catch (Exception e) {
            e.printStackTrace();
            mostrarAlerta(
                    AlertType.ERROR,
                    "No se pudo consultar el historial",
                    e.getMessage() == null
                            ? "Ocurrió un error al consultar los cortes guardados."
                            : e.getMessage());
        }
    }

    /**
     * Cancela el proceso sin guardar el corte.
     */
    public void cancelar() {
        ventanaCorteCaja.setVisible(false);
    }

    /**
     * Valida el nombre del empleado y registra un corte nuevo.
     */
    public void confirmar() {

        String nombre = txtNombreEmpleado.getText() == null
                ? ""
                : txtNombreEmpleado.getText().trim();

        // El nombre es obligatorio.
        if (nombre.isEmpty()) {
            mostrarAlerta(
                    AlertType.WARNING,
                    "Falta el nombre",
                    "Escribe el nombre del empleado que realiza el corte.");

            txtNombreEmpleado.requestFocus();
            return;
        }

        if (tablaVentas.getItems().isEmpty()) {
            mostrarAlerta(
                    AlertType.INFORMATION,
                    "Sin ventas pendientes",
                    "No hay ventas nuevas para incluir en el corte.");
            return;
        }

        double total = sumarVentas(tablaVentas.getItems());
        double efectivo = sumarPorTipoPago(tablaVentas.getItems(), "EFECTIVO");
        double tarjeta = sumarPorTipoPago(tablaVentas.getItems(), "TARJETA");

        // Construye la entidad que se guardará.
        CorteCaja corte = new CorteCaja();
        corte.setNombreEmpleado(nombre);
        corte.setFechaHora(LocalDateTime.now());
        corte.setTotalVentas(total);
        corte.setEfectivoEsperado(efectivo);
        corte.setTotalTarjeta(tarjeta);

        try {
            // El servicio recalcula y guarda la relación con las ventas
            // dentro de una única transacción.
            CorteCaja guardado = servicioCorteCaja.registrarCorte(
                    corte, tablaVentas.getItems());
            cargarVentasDelDia();
            cargarHistorial();

            mostrarAlerta(
                    AlertType.INFORMATION,
                    "Corte guardado",
                    "Se guardó el corte de " + nombre
                    + ".\nTotal de ventas: "
                    + String.format("MXN %.2f", guardado.getTotalVentas())
                    + "\nEfectivo esperado: "
                    + String.format("MXN %.2f", guardado.getEfectivoEsperado())
                    + "\nPago con tarjeta: "
                    + String.format("MXN %.2f", guardado.getTotalTarjeta()));

            ventanaCorteCaja.setVisible(false);

        } catch (Exception e) {
            e.printStackTrace();

            mostrarAlerta(
                    AlertType.ERROR,
                    "No se pudo guardar el corte",
                    e.getMessage() == null
                            ? "Ocurrió un error inesperado."
                            : e.getMessage());
        }
    }

    private void actualizarTotales(List<Venta> ventas) {
        lblTotalVentas.setText(String.format(
                "MXN %.2f", sumarVentas(ventas)));
        lblEfectivoEsperado.setText(String.format(
                "MXN %.2f", sumarPorTipoPago(ventas, "EFECTIVO")));
        lblTotalTarjeta.setText(String.format(
                "MXN %.2f", sumarPorTipoPago(ventas, "TARJETA")));
    }

    private double sumarPorTipoPago(List<Venta> ventas, String tipoPago) {
        return ventas.stream()
                .filter(venta -> tipoPago.equalsIgnoreCase(venta.getTipoPago()))
                .mapToDouble(venta -> venta.getTotal() == null
                        ? 0.0
                        : venta.getTotal())
                .sum();
    }

    private double sumarVentas(List<Venta> ventas) {
        return ventas.stream()
                .mapToDouble(venta -> venta.getTotal() == null
                        ? 0.0
                        : venta.getTotal())
                .sum();
    }

    /**
     * Muestra una alerta reutilizable para informar al empleado.
     */
    private void mostrarAlerta(
            AlertType tipo,
            String encabezado,
            String mensaje) {

        Alert alerta = new Alert(tipo);
        alerta.setTitle("Corte de caja");
        alerta.setHeaderText(encabezado);
        alerta.setContentText(mensaje);
        alerta.showAndWait();
    }

    /** Evento del botón Cancelar del archivo FXML. */
    @FXML
    private void onCancelar() {
        cancelar();
    }

    /** Evento del botón Confirmar corte del archivo FXML. */
    @FXML
    private void onConfirmar() {
        confirmar();
    }

    @FXML
    private void onActualizarHistorial() {
        cargarHistorial();
    }

    /**
     * Permite consultar el servicio desde este controlador.
     *
     * @return servicio de corte de caja
     */
    public ServicioCorteCaja getServicioCorteCaja() {
        return servicioCorteCaja;
    }
}
