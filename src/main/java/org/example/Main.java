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

    private static final int NOMBRE = 0, TITULO_APP = 1, M_ARCHIVO = 2, M_OPERACIONES = 3, M_IDIOMA = 4, M_AYUDA = 5,
            SALIR = 6, ALTA = 7, BAJA = 8, CONSULTA = 9, MODIF = 10,
            F_CLAVE = 11, F_2 = 12, F_3 = 13, ACEPTAR = 14, CANCELAR = 15, ERROR = 16,
            ERR_VACIO = 17, ERR_DUP = 18, ERR_NOEXISTE = 19, INFO = 20,
            OK_ALTA = 21, OK_BAJA = 22, OK_MODIF = 23, CONFIRMAR_BAJA = 24, SIN_RESULTADOS = 25,
            SI = 26, NO = 27, TOTAL = 28, ACERCA = 29, ACERCA_TXT = 30, BUSCAR = 31, ERR_GUARDAR = 32, OTROS = 33, ERR_CARGA = 34;;

    private static final Path IDIOMAS = Path.of("idiomas.txt");
    private static final Path DATOS = Path.of("datos.tsv");
    private static final Path CONFIG = Path.of("config.txt");

    private interface Accion {
        String ejecutar(String[] v, Component padre);
    }

    private Map<String, String[]> registros = new LinkedHashMap<>();
    private Idiomas idiomas;
    private Idiomas.Idioma actual;
    private JFrame ventana;
    private JLabel estado;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main().iniciar());
    }

    private void iniciar() {
        try {
            idiomas = new Idiomas(IDIOMAS);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, IDIOMAS + " - " + e.getClass().getSimpleName() + ": " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        actual = idiomas.mapa.values().iterator().next();
        try {
            Idiomas.Idioma g = idiomas.mapa.get(Files.readString(CONFIG).strip());
            if (g != null) {
                actual = g;
            }
        } catch (IOException ignored) {
        }
        try {
            cargar();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, f(ERR_CARGA, DATOS, e.getClass().getSimpleName() + ": " + e.getMessage()),
                    t(ERROR), JOptionPane.ERROR_MESSAGE);
            return;
        }
        ventana = new JFrame();
        ventana.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        ventana.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                salir();
            }
        });
        ventana.setSize(960, 640);
        ventana.setLocationRelativeTo(null);
        construirUI();
        ventana.setVisible(true);
    }

    private String t(int i) {
        List<String> l = actual.textos();
        if (i < l.size()) {
            return l.get(i);
        }
        return "<" + i + ">";
    }

    private String f(int i, Object... args) {
        return String.format(t(i), args);
    }

    private Image imagen(int n) {
        if (n >= actual.imagenes().size()) {
            return null;
        }
        try {
            return ImageIO.read(new File("img", new File(actual.imagenes().get(n)).getName()));
        } catch (Exception e) {
            return null;
        }
    }

    private Icon icono(int n, int ancho) {
        Image im = imagen(n);
        if (im == null) {
            return null;
        }
        int alto = ancho * im.getHeight(null) / im.getWidth(null);
        BufferedImage esc = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = esc.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(im, 0, 0, ancho, alto, null);
        g2.dispose();
        return new ImageIcon(esc);
    }

    private void construirUI() {
        ventana.setTitle(t(TITULO_APP));
        UIManager.put("OptionPane.okButtonText", t(ACEPTAR));
        UIManager.put("OptionPane.cancelButtonText", t(CANCELAR));
        UIManager.put("OptionPane.yesButtonText", t(SI));
        UIManager.put("OptionPane.noButtonText", t(NO));
        ventana.setJMenuBar(crearMenu());

        final Image fondoImg = imagen(1);
        JPanel fondo = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                if (fondoImg != null) {
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    double s = Math.max((double) getWidth() / fondoImg.getWidth(null),
                            (double) getHeight() / fondoImg.getHeight(null));
                    int iw = (int) (fondoImg.getWidth(null) * s);
                    int ih = (int) (fondoImg.getHeight(null) * s);
                    g2.drawImage(fondoImg, (getWidth() - iw) / 2, (getHeight() - ih) / 2, iw, ih, null);
                } else {
                    g2.setColor(new Color(30, 58, 138));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
            }
        };
        fondo.setBorder(BorderFactory.createEmptyBorder(24, 30, 24, 30));

        JLabel titulo = new JLabel(t(TITULO_APP));
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 32));
        titulo.setForeground(Color.WHITE);

        JPanel cab = new JPanel(new BorderLayout());
        cab.setOpaque(false);
        cab.add(titulo, BorderLayout.WEST);
        Icon bandera = icono(0, 56);
        if (bandera != null) {
            cab.add(new JLabel(bandera), BorderLayout.EAST);
        }
        fondo.add(cab, BorderLayout.NORTH);

        estado = new JLabel(f(TOTAL, registros.size()));
        estado.setFont(new Font("Segoe UI", Font.BOLD, 16));
        estado.setForeground(Color.WHITE);
        estado.setOpaque(true);
        estado.setBackground(new Color(15, 23, 42));
        estado.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        JPanel sur = new JPanel(new BorderLayout());
        sur.setOpaque(false);
        sur.add(estado, BorderLayout.EAST);
        fondo.add(sur, BorderLayout.SOUTH);

        ventana.setContentPane(fondo);
        ventana.revalidate();
        ventana.repaint();
    }

    private JMenuBar crearMenu() {
        JMenuBar barra = new JMenuBar();

        JMenu archivo = new JMenu(t(M_ARCHIVO));
        archivo.add(item(t(SALIR), e -> salir()));

        JMenu ops = new JMenu(t(M_OPERACIONES));
        ops.add(item(t(ALTA), e -> alta()));
        ops.add(item(t(BAJA), e -> baja()));
        ops.add(item(t(CONSULTA), e -> consulta()));
        ops.add(item(t(MODIF), e -> modificacion()));

        JMenu idi = new JMenu(t(M_IDIOMA));
        ButtonGroup grupo = new ButtonGroup();
        for (Idiomas.Idioma i : idiomas.mapa.values()) {
            JRadioButtonMenuItem m = new JRadioButtonMenuItem(i.textos().get(NOMBRE), i == actual);
            m.addActionListener(e -> {
                actual = i;
                construirUI();
            });
            grupo.add(m);
            idi.add(m);
        }

        JMenu ayuda = new JMenu(t(M_AYUDA));
        ayuda.add(item(t(ACERCA), e -> JOptionPane.showMessageDialog(ventana, t(ACERCA_TXT), t(ACERCA),
                JOptionPane.INFORMATION_MESSAGE)));

        barra.add(archivo);
        barra.add(ops);
        barra.add(idi);
        barra.add(ayuda);
        return barra;
    }

    private JMenuItem item(String texto, ActionListener l) {
        JMenuItem m = new JMenuItem(texto);
        m.addActionListener(l);
        return m;
    }

    private void formulario(int titulo, int[] etiquetas, boolean obligatorio, boolean autoRellenar, Accion accion) {
        JDialog d = new JDialog(ventana, t(titulo), true);
        JPanel form = new JPanel(new GridLayout(etiquetas.length, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
        JTextField[] tf = new JTextField[etiquetas.length];
        for (int i = 0; i < etiquetas.length; i++) {
            tf[i] = new JTextField(20);
            form.add(new JLabel(t(etiquetas[i]) + ":"));
            form.add(tf[i]);
        }

        if (autoRellenar) {
            tf[0].addKeyListener(new KeyAdapter() {
                @Override
                public void keyReleased(KeyEvent e) {
                    String[] r = registros.get(tf[0].getText().strip().toUpperCase());
                    if (r != null) {
                        tf[1].setText(r[1]);
                        tf[2].setText(r[2]);
                    }
                }
            });
        }

        JButton ok = new JButton(t(ACEPTAR));
        JButton cancel = new JButton(t(CANCELAR));
        cancel.addActionListener(e -> d.dispose());
        ok.addActionListener(e -> {
            String[] v = new String[tf.length];
            for (int i = 0; i < v.length; i++) {
                v[i] = tf[i].getText().strip().replace('\t', ' ');
                if (obligatorio && v[i].isEmpty()) {
                    JOptionPane.showMessageDialog(d, f(ERR_VACIO, t(etiquetas[i])), t(ERROR), JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
            if (etiquetas[0] == F_CLAVE) {
                v[0] = v[0].toUpperCase();
            }
            try {
                String msg = accion.ejecutar(v, d);
                d.dispose();
                estado.setText(f(TOTAL, registros.size()));
                if (msg != null) {
                    JOptionPane.showMessageDialog(ventana, msg, t(INFO), JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(d, ex.getMessage(), t(ERROR), JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel pie = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pie.add(ok);
        pie.add(cancel);
        d.add(form, BorderLayout.CENTER);
        d.add(pie, BorderLayout.SOUTH);
        d.getRootPane().setDefaultButton(ok);
        d.pack();
        d.setLocationRelativeTo(ventana);
        d.setVisible(true);
    }

    private void alta() {
        formulario(ALTA, new int[]{F_CLAVE, F_2, F_3}, true, false, (v, padre) -> {
            if (registros.containsKey(v[0])) {
                throw new IllegalArgumentException(f(ERR_DUP, v[0]));
            }
            registros.put(v[0], v);
            return f(OK_ALTA, v[0]);
        });
    }

    private void baja() {
        formulario(BAJA, new int[]{F_CLAVE}, true, false, (v, padre) -> {
            if (!registros.containsKey(v[0])) {
                throw new IllegalArgumentException(f(ERR_NOEXISTE, v[0]));
            }
            int r = JOptionPane.showConfirmDialog(padre, f(CONFIRMAR_BAJA, v[0]), t(BAJA), JOptionPane.YES_NO_OPTION);
            if (r != JOptionPane.YES_OPTION) {
                return null;
            }
            registros.remove(v[0]);
            return f(OK_BAJA, v[0]);
        });
    }

    private JScrollPane tabla(List<String[]> filas) {
        DefaultTableModel m = new DefaultTableModel(new Object[]{t(F_CLAVE), t(F_2), t(F_3)}, 0);
        Iterator<String[]> it = filas.iterator();
        while (it.hasNext()) {
            m.addRow(it.next());
        }
        JScrollPane sp = new JScrollPane(new JTable(m));
        sp.setPreferredSize(new Dimension(540, Math.min(150, 50 + 20 * filas.size())));
        return sp;
    }

    private void consulta() {
        List<String[]> coinciden = new ArrayList<>();
        List<String[]> otros = new ArrayList<>();
        formulario(CONSULTA, new int[]{BUSCAR}, false, false, (v, padre) -> {
            coinciden.clear();
            otros.clear();
            Iterator<String[]> it = registros.values().iterator();
            while (it.hasNext()) {
                String[] r = it.next();
                if (r[0].toLowerCase().contains(v[0].toLowerCase())) {
                    coinciden.add(r);
                } else {
                    otros.add(r);
                }
            }
            if (coinciden.isEmpty()) {
                return f(SIN_RESULTADOS, v[0]);
            }
            return null;
        });

        if (coinciden.isEmpty()) {
            return;
        }
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(tabla(coinciden));
        if (!otros.isEmpty()) {
            JLabel l = new JLabel(t(OTROS));
            l.setFont(l.getFont().deriveFont(Font.BOLD));
            l.setAlignmentX(Component.CENTER_ALIGNMENT);
            l.setBorder(BorderFactory.createEmptyBorder(10, 0, 5, 0));
            p.add(l);
            p.add(tabla(otros));
        }
        JOptionPane.showMessageDialog(ventana, p, t(CONSULTA), JOptionPane.PLAIN_MESSAGE);
    }

    private void modificacion() {
        formulario(MODIF, new int[]{F_CLAVE, F_2, F_3}, true, true, (v, padre) -> {
            if (!registros.containsKey(v[0])) {
                throw new IllegalArgumentException(f(ERR_NOEXISTE, v[0]));
            }
            registros.put(v[0], v);
            return f(OK_MODIF, v[0]);
        });
    }

    private void cargar() throws IOException {
        if (!Files.exists(DATOS)) {
            return;
        }
        for (String l : Files.readAllLines(DATOS, StandardCharsets.UTF_8)) {
            String[] c = l.split("\t", -1);
            if (c.length >= 3 && !c[0].isBlank()) {
                String k = c[0].strip().toUpperCase();
                registros.put(k, new String[]{k, c[1].strip(), c[2].strip()});
            }
        }
    }

    private void salir() {
        try {
            List<String> l = new ArrayList<>();
            Iterator<String[]> it = registros.values().iterator();
            while (it.hasNext()) {
                l.add(String.join("\t", it.next()));
            }
            Files.write(DATOS, l, StandardCharsets.UTF_8);
            Files.writeString(CONFIG, actual.codigo());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(ventana, f(ERR_GUARDAR, DATOS, e.getMessage()), t(ERROR), JOptionPane.ERROR_MESSAGE);
        }
        System.exit(0);
    }
}