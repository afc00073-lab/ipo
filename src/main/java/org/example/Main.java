package org.example;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.awt.image.BufferedImage;

public class Main {

    // constantes con los indices de cada texto para no tener numeros sueltos por el codigo y saber que mensaje se pide en cada momento
    private static final int NOMBRE = 0, TITULO_APP = 1, M_ARCHIVO = 2, M_OPERACIONES = 3, M_IDIOMA = 4, M_AYUDA = 5,
            SALIR = 6, ALTA = 7, BAJA = 8, CONSULTA = 9, MODIF = 10,
            F_CLAVE = 11, F_2 = 12, F_3 = 13, ACEPTAR = 14, CANCELAR = 15, ERROR = 16,
            ERR_VACIO = 17, ERR_DUP = 18, ERR_NOEXISTE = 19, INFO = 20,
            OK_ALTA = 21, OK_BAJA = 22, OK_MODIF = 23, CONFIRMAR_BAJA = 24, SIN_RESULTADOS = 25,
            SI = 26, NO = 27, TOTAL = 28, ACERCA = 29, ACERCA_TXT = 30, BUSCAR = 31, ERR_GUARDAR = 32, OTROS = 33, ERR_CARGA = 34;

    // rutas fijas a los ficheros externos para no repetir los nombres de archivo por toda la clase
    private static final Path IDIOMAS = Path.of("idiomas.txt");
    private static final Path DATOS = Path.of("datos.tsv");
    private static final Path CONFIG = Path.of("config.txt");

    // interfaz funcional propia para poder pasarle distintas operaciones (alta, baja, etc) como lambda al mismo formulario
    private interface Accion {
        String ejecutar(String[] v, Component padre);
    }

    // mapa que guarda los registros en memoria usando LinkedHashMap para mantener el orden en el que se van metiendo
    private Map<String, String[]> registros = new LinkedHashMap<>();
    // objeto que contiene todos los idiomas cargados desde el fichero
    private Idiomas idiomas;
    // referencia al idioma que esta activo en ese momento
    private Idiomas.Idioma actual;
    // ventana principal de la aplicacion
    private JFrame ventana;
    // etiqueta de abajo a la derecha que muestra cuantos registros hay guardados
    private JLabel estado;

    // funcion principal que arranca el programa, mete el inicio en invokeLater porque swing no es seguro entre hilos y toda la interfaz debe crearse en el hilo de eventos
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main().iniciar());
    }

    // funcion que prepara toda la aplicacion antes de enseñar la ventana
    private void iniciar() {
        try {
            // carga el fichero de idiomas al arrancar porque sin textos la interfaz no puede mostrarse
            idiomas = new Idiomas(IDIOMAS);
        } catch (Exception e) {
            // si falla la carga de idiomas saca un aviso en ingles/fijo porque aun no hay textos traducidos y corta la ejecucion
            JOptionPane.showMessageDialog(null, IDIOMAS + " - " + e.getClass().getSimpleName() + ": " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // coge el primer idioma del mapa por defecto por si no hay configuracion guardada
        actual = idiomas.mapa.values().iterator().next();
        try {
            // intenta leer el ultimo idioma usado desde config.txt limpiando espacios y saltos de linea
            Idiomas.Idioma g = idiomas.mapa.get(Files.readString(CONFIG).strip());
            // si el codigo leido existe en el mapa lo pone como actual
            if (g != null) {
                actual = g;
            }
        } catch (IOException ignored) {
            // si no existe config.txt no pasa nada, se queda con el idioma por defecto y sigue adelante
        }
        try {
            // lee los registros guardados en datos.tsv para tenerlos listos en memoria
            cargar();
        } catch (IOException e) {
            // si falla al leer los datos avisa con el mensaje traducido y cierra para no machacar luego el fichero con datos vacios
            JOptionPane.showMessageDialog(null, f(ERR_CARGA, DATOS, e.getClass().getSimpleName() + ": " + e.getMessage()),
                    t(ERROR), JOptionPane.ERROR_MESSAGE);
            return;
        }
        // crea el marco principal de la ventana
        ventana = new JFrame();
        // evita que la X cierre el programa de golpe para poder guardar los datos antes en salir()
        ventana.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        // engancha el evento de cierre de ventana para redirigirlo a nuestra funcion salir()
        ventana.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                salir();
            }
        });
        // fija el tamaño inicial de la ventana en pixeles
        ventana.setSize(960, 640);
        // centra la ventana en mitad de la pantalla pasando null
        ventana.setLocationRelativeTo(null);
        // monta todos los componentes visuales dentro de la ventana
        construirUI();
        // hace visible la ventana una vez esta todo montado para evitar parpadeos
        ventana.setVisible(true);
    }

    // funcion que saca un texto del idioma actual recibiendo su posicion
    private String t(int i) {
        // obtiene la lista de textos del idioma activo
        List<String> l = actual.textos();
        // comprueba que el indice exista para evitar un IndexOutOfBoundsException si falta una linea en el fichero
        if (i < l.size()) {
            return l.get(i);
        }
        // si falta el texto devuelve el numero entre signos para detectar rapido que linea falta por traducir
        return "<" + i + ">";
    }

    // funcion que rellena variables dentro de un texto traducido usando String.format para no andar concatenando cadenas
    private String f(int i, Object... args) {
        return String.format(t(i), args);
    }

    // funcion que lee una imagen del disco segun el idioma actual
    private Image imagen(int n) {
        // si el idioma no tiene tantas imagenes definidas devuelve null sin romper nada
        if (n >= actual.imagenes().size()) {
            return null;
        }
        try {
            // carga la imagen desde la carpeta img quedandose solo con el nombre del archivo por seguridad
            return ImageIO.read(new File("img", new File(actual.imagenes().get(n)).getName()));
        } catch (Exception e) {
            // si la imagen no esta en la carpeta o esta corrupta devuelve null para que la interfaz use un respaldo
            return null;
        }
    }

    // funcion que convierte una imagen en un icono escalado para la cabecera
    private Icon icono(int n, int ancho) {
        // recupera la imagen original
        Image im = imagen(n);
        // si no se pudo cargar corta y devuelve null
        if (im == null) {
            return null;
        }
        // calcula el alto manteniendo la proporcion original para que la bandera no salga deformada
        int alto = ancho * im.getHeight(null) / im.getWidth(null);
        // crea un lienzo en memoria con transparencia (ARGB) del tamaño exacto del icono
        BufferedImage esc = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        // saca el pincel 2D para dibujar sobre ese lienzo
        Graphics2D g2 = esc.createGraphics();
        // activa interpolacion bicubica para que al reducir la imagen no se pixelen los bordes
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        // prioriza la calidad visual frente a la velocidad de pintado
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        // dibuja la imagen original redimensionada dentro del lienzo
        g2.drawImage(im, 0, 0, ancho, alto, null);
        // libera los recursos graficos del sistema operativo
        g2.dispose();
        // envuelve la imagen resultante en un ImageIcon listo para meterlo en un JLabel
        return new ImageIcon(esc);
    }

    // funcion que construye o refresca toda la interfaz cada vez que arranca o se cambia el idioma
    private void construirUI() {
        // pone el titulo de la ventana en el idioma seleccionado
        ventana.setTitle(t(TITULO_APP));
        // traduce los botones por defecto de los cuadros de dialogo de swing porque si no salen en el idioma del sistema operativo
        UIManager.put("OptionPane.okButtonText", t(ACEPTAR));
        UIManager.put("OptionPane.cancelButtonText", t(CANCELAR));
        UIManager.put("OptionPane.yesButtonText", t(SI));
        UIManager.put("OptionPane.noButtonText", t(NO));
        // regenera y coloca la barra de menu con los textos actualizados
        ventana.setJMenuBar(crearMenu());

        // carga la imagen de fondo (indice 1) antes de crear el panel para no leerla de disco cada vez que se repinta la ventana
        final Image fondoImg = imagen(1);
        // crea el panel principal sobreescribiendo paintComponent para poder dibujar un fondo personalizado
        JPanel fondo = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                if (fondoImg != null) {
                    // aplica suavizado para que el fondo se vea nitido al estirar la ventana
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    // calcula la escala maxima para cubrir todo el panel sin dejar franjas vacias (efecto cover)
                    double s = Math.max((double) getWidth() / fondoImg.getWidth(null),
                            (double) getHeight() / fondoImg.getHeight(null));
                    // calcula el ancho y alto finales multiplicados por esa escala
                    int iw = (int) (fondoImg.getWidth(null) * s);
                    int ih = (int) (fondoImg.getHeight(null) * s);
                    // dibuja la imagen centrada recortando lo que sobre por los lados
                    g2.drawImage(fondoImg, (getWidth() - iw) / 2, (getHeight() - ih) / 2, iw, ih, null);
                } else {
                    // si no hay imagen de fondo pinta un azul oscuro plano para que las letras blancas sigan leyendose bien
                    g2.setColor(new Color(30, 58, 138));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
            }
        };
        // añade margenes interiores para que los textos no queden pegados a los bordes de la ventana
        fondo.setBorder(BorderFactory.createEmptyBorder(24, 30, 24, 30));

        // crea la etiqueta grande con el titulo de la aplicacion
        JLabel titulo = new JLabel(t(TITULO_APP));
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 32));
        titulo.setForeground(Color.WHITE);

        // crea el panel de cabecera para alinear el titulo a la izquierda y la bandera a la derecha
        JPanel cab = new JPanel(new BorderLayout());
        // lo hace transparente para que se vea la imagen de fondo por detras
        cab.setOpaque(false);
        cab.add(titulo, BorderLayout.WEST);
        // carga la bandera del idioma (indice 0) a 56 pixeles de ancho
        Icon bandera = icono(0, 56);
        // solo añade la etiqueta de la imagen si la bandera se cargo bien
        if (bandera != null) {
            cab.add(new JLabel(bandera), BorderLayout.EAST);
        }
        // coloca la cabecera en la parte superior del panel principal
        fondo.add(cab, BorderLayout.NORTH);

        // crea la etiqueta inferior que muestra el recuento actual de registros
        estado = new JLabel(f(TOTAL, registros.size()));
        estado.setFont(new Font("Segoe UI", Font.BOLD, 16));
        estado.setForeground(Color.WHITE);
        // le pone fondo solido oscuro para que el numero se lea siempre aunque el fondo tenga partes claras
        estado.setOpaque(true);
        estado.setBackground(new Color(15, 23, 42));
        estado.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        // crea el contenedor inferior transparente para empujar la etiqueta de estado hacia la esquina derecha
        JPanel sur = new JPanel(new BorderLayout());
        sur.setOpaque(false);
        sur.add(estado, BorderLayout.EAST);
        fondo.add(sur, BorderLayout.SOUTH);

        // reemplaza el contenido anterior de la ventana por el nuevo panel
        ventana.setContentPane(fondo);
        // recalcula las posiciones de los componentes tras el cambio
        ventana.revalidate();
        // fuerza el redibujado visual de toda la ventana
        ventana.repaint();
    }

    // funcion que monta la barra de menu superior con todas sus opciones
    private JMenuBar crearMenu() {
        JMenuBar barra = new JMenuBar();

        // crea el menu archivo con la opcion de salir
        JMenu archivo = new JMenu(t(M_ARCHIVO));
        archivo.add(item(t(SALIR), e -> salir()));

        // crea el menu operaciones conectando cada opcion con su metodo correspondiente
        JMenu ops = new JMenu(t(M_OPERACIONES));
        ops.add(item(t(ALTA), e -> alta()));
        ops.add(item(t(BAJA), e -> baja()));
        ops.add(item(t(CONSULTA), e -> consulta()));
        ops.add(item(t(MODIF), e -> modificacion()));

        // crea el menu de idiomas dinamicamente segun los que haya cargados en el mapa
        JMenu idi = new JMenu(t(M_IDIOMA));
        // usa ButtonGroup para que solo pueda haber un idioma marcado a la vez
        ButtonGroup grupo = new ButtonGroup();
        for (Idiomas.Idioma i : idiomas.mapa.values()) {
            // crea un boton de radio marcando como seleccionado el que coincida con el idioma actual
            JRadioButtonMenuItem m = new JRadioButtonMenuItem(i.textos().get(NOMBRE), i == actual);
            // al hacer clic cambia el idioma activo y reconstruye toda la interfaz para traducirla al vuelo
            m.addActionListener(e -> {
                actual = i;
                construirUI();
            });
            grupo.add(m);
            idi.add(m);
        }

        // crea el menu de ayuda con la ventana emergente de acerca de
        JMenu ayuda = new JMenu(t(M_AYUDA));
        ayuda.add(item(t(ACERCA), e -> JOptionPane.showMessageDialog(ventana, t(ACERCA_TXT), t(ACERCA),
                JOptionPane.INFORMATION_MESSAGE)));

        // añade los cuatro menus principales a la barra
        barra.add(archivo);
        barra.add(ops);
        barra.add(idi);
        barra.add(ayuda);
        return barra;
    }

    // funcion auxiliar para crear items de menu en una linea sin repetir codigo cada vez
    private JMenuItem item(String texto, ActionListener l) {
        JMenuItem m = new JMenuItem(texto);
        m.addActionListener(l);
        return m;
    }

    // funcion auxiliar para activar o desactivar la edicion de origen y destino visualmente
    private void habilitarCamposEdicion(JTextField[] tf, boolean editable) {
        Color fondo = editable ? Color.WHITE : new Color(235, 235, 235);
        for (int i = 1; i < tf.length; i++) {
            tf[i].setEditable(editable);
            tf[i].setBackground(fondo);
        }
    }

    // funcion reutilizable que monta cualquier ventana de formulario para no duplicar ventanas en alta, baja, consulta y modificacion
    private void formulario(int titulo, int[] etiquetas, boolean obligatorio, boolean autoRellenar, Accion accion) {
        // crea un dialogo modal (true) para bloquear la ventana principal hasta que el usuario termine o cancele
        JDialog d = new JDialog(ventana, t(titulo), true);
        // usa GridLayout de dos columnas (etiqueta a la izquierda, caja de texto a la derecha) con separacion de 8px
        JPanel form = new JPanel(new GridLayout(etiquetas.length, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
        // crea un array de cajas de texto segun cuantos campos se hayan pedido
        JTextField[] tf = new JTextField[etiquetas.length];
        for (int i = 0; i < etiquetas.length; i++) {
            tf[i] = new JTextField(20);
            form.add(new JLabel(t(etiquetas[i]) + ":"));
            form.add(tf[i]);
        }

        // si esta activado el autorrellenado (modificacion), bloquea origen y destino hasta que se ponga un vuelo valido
        if (autoRellenar) {
            habilitarCamposEdicion(tf, false);
            tf[0].addKeyListener(new KeyAdapter() {
                @Override
                public void keyReleased(KeyEvent e) {
                    String clave = tf[0].getText().strip().toUpperCase();
                    // si el campo de numero de vuelo esta vacio, bloquea y limpia los demas campos
                    if (clave.isEmpty()) {
                        habilitarCamposEdicion(tf, false);
                        tf[1].setText("");
                        tf[2].setText("");
                        return;
                    }
                    // busca si lo que se ha escrito coincide con alguna clave del mapa
                    String[] r = registros.get(clave);
                    // si existe, desbloquea origen y destino y rellena automaticamente sus valores
                    if (r != null) {
                        habilitarCamposEdicion(tf, true);
                        tf[1].setText(r[1]);
                        tf[2].setText(r[2]);
                    } else {
                        // si el vuelo escrito no existe todavia, mantiene bloqueados y limpios origen y destino
                        habilitarCamposEdicion(tf, false);
                        tf[1].setText("");
                        tf[2].setText("");
                    }
                }
            });
        }

        // crea los botones de aceptar y cancelar
        JButton ok = new JButton(t(ACEPTAR));
        JButton cancel = new JButton(t(CANCELAR));
        // cerrar el dialogo sin hacer nada si pulsa cancelar
        cancel.addActionListener(e -> d.dispose());
        // logica al pulsar aceptar
        ok.addActionListener(e -> {
            String[] v = new String[tf.length];
            for (int i = 0; i < v.length; i++) {
                // limpia espacios sobrantes y cambia tabuladores por espacios para no corromper el formato .tsv al guardar
                v[i] = tf[i].getText().strip().replace('\t', ' ');
                // si los campos son obligatorios y alguno esta vacio avisa y frena el guardado
                if (obligatorio && v[i].isEmpty()) {
                    JOptionPane.showMessageDialog(d, f(ERR_VACIO, t(etiquetas[i])), t(ERROR), JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
            // si el primer campo es una clave primaria la pasa a mayusculas para unificar todas las claves en el mapa
            if (etiquetas[0] == F_CLAVE) {
                v[0] = v[0].toUpperCase();
            }
            try {
                // ejecuta la accion especifica que se paso por parametro (alta, baja, consulta o modificacion)
                String msg = accion.ejecutar(v, d);
                // cierra el formulario si la operacion ha ido bien
                d.dispose();
                // actualiza el contador de registros de la ventana principal por si se añadio o borro algo
                estado.setText(f(TOTAL, registros.size()));
                // si la accion devolvio un mensaje informativo lo enseña en pantalla
                if (msg != null) {
                    JOptionPane.showMessageDialog(ventana, msg, t(INFO), JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (IllegalArgumentException ex) {
                // captura errores de validacion (clave duplicada o inexistente) y los muestra sin cerrar el formulario
                JOptionPane.showMessageDialog(d, ex.getMessage(), t(ERROR), JOptionPane.ERROR_MESSAGE);
            }
        });

        // panel inferior para alinear los botones a la derecha
        JPanel pie = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pie.add(ok);
        pie.add(cancel);
        d.add(form, BorderLayout.CENTER);
        d.add(pie, BorderLayout.SOUTH);
        // asigna el boton aceptar a la tecla Enter para mayor comodidad al escribir
        d.getRootPane().setDefaultButton(ok);
        // ajusta el tamaño del dialogo automaticamente al contenido
        d.pack();
        // centra el dialogo respecto a la ventana principal
        d.setLocationRelativeTo(ventana);
        d.setVisible(true);
    }

    // funcion que gestiona el alta pidiendo los tres campos obligatorios y sin autorrellenado
    private void alta() {
        formulario(ALTA, new int[]{F_CLAVE, F_2, F_3}, true, false, (v, padre) -> {
            // comprueba que la clave no exista ya para no pisar un registro anterior por error
            if (registros.containsKey(v[0])) {
                throw new IllegalArgumentException(f(ERR_DUP, v[0]));
            }
            // guarda el nuevo registro en el mapa
            registros.put(v[0], v);
            // devuelve el mensaje de confirmacion formateado con la clave
            return f(OK_ALTA, v[0]);
        });
    }

    // funcion que gestiona la baja pidiendo solo la clave del registro a borrar
    private void baja() {
        formulario(BAJA, new int[]{F_CLAVE}, true, false, (v, padre) -> {
            // verifica que el registro exista antes de intentar borrarlo
            if (!registros.containsKey(v[0])) {
                throw new IllegalArgumentException(f(ERR_NOEXISTE, v[0]));
            }
            // pide confirmacion con Si/No para evitar borrados accidentales
            int r = JOptionPane.showConfirmDialog(padre, f(CONFIRMAR_BAJA, v[0]), t(BAJA), JOptionPane.YES_NO_OPTION);
            // si el usuario dice que no devuelve null para que no salga ningun mensaje de exito
            if (r != JOptionPane.YES_OPTION) {
                return null;
            }
            // elimina el registro del mapa
            registros.remove(v[0]);
            return f(OK_BAJA, v[0]);
        });
    }

    // funcion que monta una tabla visual con barras de desplazamiento a partir de una lista de filas
    private JScrollPane tabla(List<String[]> filas) {
        // crea el modelo de tabla con las tres cabeceras traducidas y 0 filas iniciales
        DefaultTableModel m = new DefaultTableModel(new Object[]{t(F_CLAVE), t(F_2), t(F_3)}, 0);
        // recorre las filas con un iterador y las va añadiendo al modelo
        Iterator<String[]> it = filas.iterator();
        while (it.hasNext()) {
            m.addRow(it.next());
        }
        // mete la tabla dentro de un JScrollPane para que salgan los encabezados y el scroll si hay muchos datos
        JScrollPane sp = new JScrollPane(new JTable(m));
        // ajusta la altura dinamicamente segun el numero de filas con un tope de 150px para que el dialogo no se haga gigante
        sp.setPreferredSize(new Dimension(540, Math.min(150, 50 + 20 * filas.size())));
        return sp;
    }

    // funcion que busca registros por coincidencia parcial en la clave y enseña resultados y resto por separado
    private void consulta() {
        // listas para separar los registros que coinciden con la busqueda de los que no
        List<String[]> coinciden = new ArrayList<>();
        List<String[]> otros = new ArrayList<>();
        // abre el formulario con obligatorio en false para permitir buscar en blanco y listar todos los registros
        formulario(CONSULTA, new int[]{BUSCAR}, false, false, (v, padre) -> {
            coinciden.clear();
            otros.clear();
            // recorre todos los registros comparando en minusculas para que no importe si escriben en mayusculas o minusculas
            Iterator<String[]> it = registros.values().iterator();
            while (it.hasNext()) {
                String[] r = it.next();
                if (r[0].toLowerCase().contains(v[0].toLowerCase())) {
                    coinciden.add(r);
                } else {
                    otros.add(r);
                }
            }
            // si no hay ninguna coincidencia devuelve el aviso de sin resultados para que lo muestre el formulario
            if (coinciden.isEmpty()) {
                return f(SIN_RESULTADOS, v[0]);
            }
            // devuelve null porque los resultados se enseñan justo debajo en tablas y no en un cuadro de texto simple
            return null;
        });

        // si se cancelo el dialogo o no hubo coincidencias corta aqui para no abrir una ventana vacia
        if (coinciden.isEmpty()) {
            return;
        }
        // crea un panel vertical para apilar la tabla de coincidencias y debajo la del resto
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(tabla(coinciden));
        // si sobraron registros que no coincidian los añade debajo separados por una etiqueta
        if (!otros.isEmpty()) {
            JLabel l = new JLabel(t(OTROS));
            l.setFont(l.getFont().deriveFont(Font.BOLD));
            l.setAlignmentX(Component.CENTER_ALIGNMENT);
            l.setBorder(BorderFactory.createEmptyBorder(10, 0, 5, 0));
            p.add(l);
            p.add(tabla(otros));
        }
        // muestra el panel completo con las tablas dentro de un cuadro de dialogo
        JOptionPane.showMessageDialog(ventana, p, t(CONSULTA), JOptionPane.PLAIN_MESSAGE);
    }

    // funcion que modifica un registro existente activando el autorrellenado al escribir la clave
    private void modificacion() {
        formulario(MODIF, new int[]{F_CLAVE, F_2, F_3}, true, true, (v, padre) -> {
            // comprueba que la clave exista porque modificar no debe crear registros nuevos
            if (!registros.containsKey(v[0])) {
                throw new IllegalArgumentException(f(ERR_NOEXISTE, v[0]));
            }
            // sobreescribe el array de valores asociado a esa clave en el mapa
            registros.put(v[0], v);
            return f(OK_MODIF, v[0]);
        });
    }

    // funcion que carga los registros del fichero datos.tsv al arrancar el programa
    private void cargar() throws IOException {
        // si es la primera vez que se abre y aun no existe el fichero sale sin dar error
        if (!Files.exists(DATOS)) {
            return;
        }
        // lee todas las lineas del fichero en UTF-8 para conservar tildes y caracteres especiales
        for (String l : Files.readAllLines(DATOS, StandardCharsets.UTF_8)) {
            // parte cada linea por tabuladores usando -1 para no perder columnas vacias al final
            String[] c = l.split("\t", -1);
            // comprueba que la linea tenga las 3 columnas y clave valida para ignorar lineas en blanco o corruptas
            if (c.length >= 3 && !c[0].isBlank()) {
                String k = c[0].strip().toUpperCase();
                registros.put(k, new String[]{k, c[1].strip(), c[2].strip()});
            }
        }
    }

    // funcion que guarda los cambios en disco y cierra el programa de forma limpia
    private void salir() {
        try {
            List<String> l = new ArrayList<>();
            // convierte cada registro del mapa en una linea de texto uniendo sus columnas con tabuladores
            Iterator<String[]> it = registros.values().iterator();
            while (it.hasNext()) {
                l.add(String.join("\t", it.next()));
            }
            // vuelca todas las lineas al fichero datos.tsv sobreescribiendo el anterior
            Files.write(DATOS, l, StandardCharsets.UTF_8);
            // guarda el codigo del idioma activo en config.txt para recordarlo la proxima vez
            Files.writeString(CONFIG, actual.codigo());
        } catch (IOException e) {
            // si falla el guardado avisa al usuario antes de cerrar
            JOptionPane.showMessageDialog(ventana, f(ERR_GUARDAR, DATOS, e.getMessage()), t(ERROR), JOptionPane.ERROR_MESSAGE);
        }
        // termina la maquina virtual de java cerrando todo el proceso
        System.exit(0);
    }
}